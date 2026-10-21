package com.pantea.receiptgate.gate;

import ai.onnxruntime.OrtException;
import com.pantea.receiptgate.clip.ClipImageEncoder;
import com.pantea.receiptgate.config.GateProperties;
import com.pantea.receiptgate.gate.GateResult.Reason;
import com.pantea.receiptgate.gate.GateResult.Verdict;
import com.pantea.receiptgate.image.ImageLoader;
import com.pantea.receiptgate.index.ReferenceIndex;
import com.pantea.receiptgate.ocr.KeywordScorer;
import com.pantea.receiptgate.ocr.OcrPreprocessor;
import com.pantea.receiptgate.ocr.TesseractCli;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * The gate. Two free signals that fail in opposite places:
 * <ul>
 *   <li><b>Receipt words</b> (Tesseract, Persian): separates a receipt from every other phone screenshot -
 *       a chat, a bank app's home screen, the shop's own order page. Fails when the image is too blurry,
 *       cropped or small to read.</li>
 *   <li><b>CLIP probe</b>: tells a receipt-looking image from a room photo or a document even when nothing
 *       can be read. Cannot tell a receipt from other banking screenshots.</li>
 * </ul>
 * Words decide first; CLIP only rescues unreadable receipts into UNSURE, it never makes something a RECEIPT.
 */
@Service
public class ReceiptGateService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptGateService.class);

    /** What OCR found, at which rotation, after how many Tesseract runs. */
    public record OcrOutcome(KeywordScorer.Result keywords, int rotation, int passes, boolean failed) {
    }

    public record Decision(Verdict verdict, Reason reason) {
    }

    private final GateProperties props;
    private final ClipImageEncoder encoder;
    private final ReferenceIndex index;
    private final TesseractCli tesseract;
    private final KeywordScorer scorer;

    public ReceiptGateService(GateProperties props, ClipImageEncoder encoder, ReferenceIndex index,
                              TesseractCli tesseract, KeywordScorer scorer) {
        this.props = props;
        this.encoder = encoder;
        this.index = index;
        this.tesseract = tesseract;
        this.scorer = scorer;
    }

    public GateResult check(byte[] bytes) throws IOException, OrtException {
        long t0 = System.currentTimeMillis();
        GateProperties.Decision d = props.getDecision();
        String sha256 = sha256(bytes);
        BufferedImage image = ImageLoader.read(bytes, d.getMaxPixels());

        if (Math.min(image.getWidth(), image.getHeight()) < d.getMinSide()) {
            return log(new GateResult(Verdict.NOT_RECEIPT, Reason.IMAGE_TOO_SMALL, 0, d.getMinKeywordScore(),
                    List.of(), null, d.getUnsureProbability(), 0, 0, sha256, image.getWidth(), image.getHeight(),
                    System.currentTimeMillis() - t0));
        }

        double p = index.probability(encoder.embed(image));
        OcrOutcome ocr = readKeywords(image, p);
        Decision decision = decide(ocr, p);

        return log(new GateResult(decision.verdict(), decision.reason(), ocr.keywords().score(),
                d.getMinKeywordScore(), ocr.keywords().found(), Double.isNaN(p) ? null : round(p),
                d.getUnsureProbability(), ocr.rotation(), ocr.passes(), sha256, image.getWidth(), image.getHeight(),
                System.currentTimeMillis() - t0));
    }

    /**
     * Upright first; if too few words were found, try the configured quarter turns (sideways photos of a
     * printed receipt) - unless CLIP is confident the image is not a receipt at all, which spares every
     * room photo two extra Tesseract runs.
     */
    public OcrOutcome readKeywords(BufferedImage image, double clipProbability) {
        GateProperties.Decision d = props.getDecision();
        KeywordScorer.Result best = new KeywordScorer.Result(0, List.of());
        int bestRotation = 0;
        int passes = 0;

        List<Integer> rotations = new java.util.ArrayList<>();
        rotations.add(0);
        boolean worthRetrying = Double.isNaN(clipProbability) || clipProbability >= d.getRotationRetryMinProbability();
        if (worthRetrying) {
            rotations.addAll(props.getOcr().getRetryRotations());
        }

        for (int rotation : rotations) {
            if (best.score() >= d.getMinKeywordScore()) {
                break;
            }
            try {
                passes++;
                KeywordScorer.Result r = scorer.score(tesseract.read(OcrPreprocessor.prepare(image, rotation)));
                if (r.score() > best.score()) {
                    best = r;
                    bestRotation = rotation;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new OcrOutcome(best, bestRotation, passes, true);
            } catch (IOException | RuntimeException e) {
                log.warn("OCR pass at {} degrees failed: {}", rotation, e.getMessage());
                return new OcrOutcome(best, bestRotation, passes, true);
            }
        }
        return new OcrOutcome(best, bestRotation, passes, false);
    }

    /** Pure decision rule; also used by --verify so the report and the API can never disagree. */
    public Decision decide(OcrOutcome ocr, double clipProbability) {
        GateProperties.Decision d = props.getDecision();
        boolean looksLikeReceipt = !Double.isNaN(clipProbability) && clipProbability >= d.getUnsureProbability();

        if (ocr.keywords().score() >= d.getMinKeywordScore()) {
            return new Decision(Verdict.RECEIPT, Reason.RECEIPT_TEXT_FOUND);
        }
        if (ocr.failed()) {
            // OCR is down: do not block a paying customer on a broken dependency. Anything CLIP does not
            // rule out goes to a human; clear non-receipts (room photos score ~0.03) are still blocked.
            boolean plausible = Double.isNaN(clipProbability)
                    || clipProbability >= d.getRotationRetryMinProbability();
            return new Decision(plausible ? Verdict.UNSURE : Verdict.NOT_RECEIPT, Reason.OCR_UNAVAILABLE);
        }
        if (looksLikeReceipt) {
            return new Decision(Verdict.UNSURE, Reason.RECEIPT_LIKE_BUT_TEXT_UNCLEAR);
        }
        return new Decision(Verdict.NOT_RECEIPT, Reason.NOT_A_RECEIPT);
    }

    private static GateResult log(GateResult r) {
        // Verdict and scores only: never the OCR text, which holds names, IBANs and national ID numbers.
        log.info("check verdict={} reason={} words={} clip={} rotation={} passes={} {}x{} {} ms sha={}",
                r.verdict(), r.reason(), r.keywordScore(), r.clipProbability(), r.ocrRotation(), r.ocrPasses(),
                r.width(), r.height(), r.elapsedMs(), r.sha256().substring(0, 12));
        return r;
    }

    private static double round(double p) {
        return Math.round(p * 10_000) / 10_000.0;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
