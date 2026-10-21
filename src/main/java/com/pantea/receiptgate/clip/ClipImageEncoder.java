package com.pantea.receiptgate.clip;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.pantea.receiptgate.config.GateProperties;
import com.pantea.receiptgate.image.ImageOps;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Wraps an ONNX CLIP model and produces one L2-normalised vector per image. Same model file, same
 * preprocessing and same graph-signature detection as the style-match service.
 */
@Component
public class ClipImageEncoder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClipImageEncoder.class);

    /** OpenAI CLIP normalisation constants. These must match the exported model. */
    private static final float[] MEAN = {0.48145466f, 0.4578275f, 0.40821073f};
    private static final float[] STD = {0.26862954f, 0.26130258f, 0.27577711f};

    /** BOS/EOS of the CLIP BPE vocabulary - an empty prompt for exports that demand text input. */
    private static final long[] EMPTY_PROMPT = {49406L, 49407L};

    private final GateProperties.Clip props;
    private final OrtEnvironment env;
    private final OrtSession session;

    private final String pixelInput;
    private final boolean needsTextInput;
    private final String embeddingOutput;
    private final String modelFingerprint;
    private final int dimensions;

    public ClipImageEncoder(GateProperties properties) throws OrtException, IOException {
        this.props = properties.getClip();
        Path model = Path.of(props.getModelPath()).toAbsolutePath();
        if (!Files.isRegularFile(model)) {
            throw new IllegalStateException("CLIP model not found at " + model
                    + ". Run scripts/download-models.ps1 (Windows) or scripts/download-models.sh first.");
        }

        this.env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(Math.max(1, props.getIntraOpThreads()));
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        this.session = env.createSession(model.toString(), opts);

        Set<String> inputs = new HashSet<>(session.getInputNames());
        Set<String> outputs = new HashSet<>(session.getOutputNames());

        this.pixelInput = inputs.contains("pixel_values") ? "pixel_values"
                : inputs.stream().findFirst().orElseThrow(
                        () -> new IllegalStateException("ONNX model exposes no inputs"));
        this.needsTextInput = inputs.contains("input_ids");
        this.embeddingOutput = outputs.contains("image_embeds") ? "image_embeds"
                : outputs.contains("pooler_output") ? "pooler_output"
                : outputs.stream().findFirst().orElseThrow(
                        () -> new IllegalStateException("ONNX model exposes no outputs"));

        this.modelFingerprint = fingerprint(model);
        this.dimensions = encode(new float[3 * props.getInputSize() * props.getInputSize()]).length;

        log.info("CLIP model loaded: {} (output='{}', dim={}, fingerprint={})",
                model.getFileName(), embeddingOutput, dimensions, modelFingerprint);
    }

    public int getDimensions() {
        return dimensions;
    }

    /** Changes whenever the model file changes, so a stale embedding cache is discarded. */
    public String getModelFingerprint() {
        return modelFingerprint;
    }

    /** Centre crop + full-frame squash, averaged (when multi-crop is on), L2-normalised. */
    public float[] embed(BufferedImage image) throws OrtException {
        int size = props.getInputSize();
        float[] vector = encode(centerCropTensor(image, size));
        if (props.isMultiCrop()) {
            float[] squashed = encode(toTensor(ImageOps.scale(image, size, size), size));
            for (int i = 0; i < vector.length; i++) {
                vector[i] += squashed[i];
            }
            normalize(vector);
        }
        return vector;
    }

    /** Standard CLIP preprocessing: shortest side to {@code size}, then centre crop. */
    static float[] centerCropTensor(BufferedImage src, int size) {
        double scale = (double) size / Math.min(src.getWidth(), src.getHeight());
        int w = Math.max(size, (int) Math.ceil(src.getWidth() * scale));
        int h = Math.max(size, (int) Math.ceil(src.getHeight() * scale));
        BufferedImage resized = ImageOps.scale(src, w, h);
        return toTensor(resized.getSubimage((w - size) / 2, (h - size) / 2, size, size), size);
    }

    static float[] toTensor(BufferedImage img, int size) {
        float[] data = new float[3 * size * size];
        int plane = size * size;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = img.getRGB(x, y);
                int idx = y * size + x;
                data[idx] = (((rgb >> 16) & 0xFF) / 255f - MEAN[0]) / STD[0];
                data[plane + idx] = (((rgb >> 8) & 0xFF) / 255f - MEAN[1]) / STD[1];
                data[2 * plane + idx] = ((rgb & 0xFF) / 255f - MEAN[2]) / STD[2];
            }
        }
        return data;
    }

    private float[] encode(float[] chw) throws OrtException {
        int size = props.getInputSize();
        Map<String, OnnxTensor> feeds = new HashMap<>();
        try {
            feeds.put(pixelInput, OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), new long[]{1, 3, size, size}));
            if (needsTextInput) {
                long[] shape = {1, EMPTY_PROMPT.length};
                feeds.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(EMPTY_PROMPT), shape));
                if (session.getInputNames().contains("attention_mask")) {
                    feeds.put("attention_mask",
                            OnnxTensor.createTensor(env, LongBuffer.wrap(new long[]{1L, 1L}), shape));
                }
            }
            try (OrtSession.Result result = session.run(feeds)) {
                OnnxValue value = result.get(embeddingOutput)
                        .orElseThrow(() -> new IllegalStateException("Missing output " + embeddingOutput));
                float[] vector = ((float[][]) value.getValue())[0].clone();
                normalize(vector);
                return vector;
            }
        } finally {
            feeds.values().forEach(OnnxTensor::close);
        }
    }

    private static void normalize(float[] v) {
        double sum = 0;
        for (float f : v) {
            sum += (double) f * f;
        }
        double norm = Math.sqrt(sum);
        if (norm < 1e-9) {
            return;
        }
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
    }

    private static String fingerprint(Path model) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(model.getFileName().toString().getBytes());
            md.update(Long.toString(Files.size(model)).getBytes());
            md.update(Long.toString(Files.getLastModifiedTime(model).toMillis()).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 16);
        } catch (Exception e) {
            throw new IOException("Cannot fingerprint model", e);
        }
    }

    @Override
    @PreDestroy
    public void close() throws OrtException {
        session.close();
    }
}
