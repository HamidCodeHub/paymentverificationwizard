package com.pantea.receiptgate.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pantea.receiptgate.clip.ClipImageEncoder;
import com.pantea.receiptgate.config.GateProperties;
import com.pantea.receiptgate.image.Augmenter;
import com.pantea.receiptgate.image.ImageLoader;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Embeds the reference images once, caches the vectors, and trains the receipt probe on them.
 *
 * <pre>
 * references/
 *   receipts/          real transfer receipts   (optionally grouped: receipts/blu/..., receipts/mellat/...)
 *   non-receipts/      anything else a customer might upload by mistake (non-receipts/chats/..., /rooms/...)
 * </pre>
 *
 * Adding an example is a file drop plus {@code POST /api/v1/reindex}: only new or changed files are embedded,
 * the rest come from the cache keyed on model fingerprint + file size + mtime.
 */
@Component
public class ReferenceIndex {

    private static final Logger log = LoggerFactory.getLogger(ReferenceIndex.class);
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "bmp", "gif");
    public static final String ORIGINAL = "original";

    public enum Label { RECEIPT, NOT_RECEIPT }

    /** One embedded image: an original reference file or one degraded variant of it. */
    public record Reference(String path, Label label, String group, String variant, float[] vector) {
        public boolean isOriginal() {
            return ORIGINAL.equals(variant);
        }
    }

    record CacheEntry(long size, long mtime, float[] vector) {
    }

    record CacheFile(String fingerprint, Map<String, CacheEntry> entries) {
    }

    private record Snapshot(List<Reference> references, LogisticProbe probe) {
    }

    private final GateProperties props;
    private final ClipImageEncoder encoder;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile Snapshot snapshot = new Snapshot(List.of(), null);

    public ReferenceIndex(GateProperties props, ClipImageEncoder encoder) {
        this.props = props;
        this.encoder = encoder;
    }

    @PostConstruct
    public void init() throws IOException {
        rebuild();
    }

    public List<Reference> references() {
        return snapshot.references();
    }

    public boolean isProbeTrained() {
        return snapshot.probe() != null;
    }

    /** Receipt probability of a vector, or NaN when there are not enough references to train a probe. */
    public double probability(float[] vector) {
        LogisticProbe probe = snapshot.probe();
        return probe == null ? Double.NaN : probe.probability(vector);
    }

    public long count(Label label, boolean originalsOnly) {
        return snapshot.references().stream()
                .filter(r -> r.label() == label && (!originalsOnly || r.isOriginal()))
                .count();
    }

    public Path root() {
        return Path.of(props.getReferencesDir()).toAbsolutePath().normalize();
    }

    /** Trains a probe on the subset of references the predicate keeps. Used by --verify to hold groups out. */
    public LogisticProbe trainProbe(Predicate<Reference> include) {
        return trainOn(snapshot.references(), include);
    }

    private LogisticProbe trainOn(List<Reference> references, Predicate<Reference> include) {
        List<float[]> xs = new ArrayList<>();
        List<Boolean> ys = new ArrayList<>();
        for (Reference r : references) {
            if (include.test(r)) {
                xs.add(r.vector());
                ys.add(r.label() == Label.RECEIPT);
            }
        }
        GateProperties.Probe p = props.getProbe();
        return LogisticProbe.train(xs, ys, p.getL2(), p.getIterations(), p.getLearningRate());
    }

    public synchronized void rebuild() throws IOException {
        long t0 = System.currentTimeMillis();
        Path root = root();
        if (!Files.isDirectory(root)) {
            log.warn("References folder {} does not exist. Every upload without receipt words will be blocked "
                    + "until it holds receipts/ and non-receipts/.", root);
        }

        String fingerprint = encoder.getModelFingerprint() + "/aug" + Augmenter.VERSION
                + "/mc" + props.getClip().isMultiCrop();
        Map<String, CacheEntry> cache = loadCache(fingerprint);
        Map<String, CacheEntry> fresh = new HashMap<>();
        List<Reference> refs = new ArrayList<>();
        int embedded = 0;

        for (Label label : Label.values()) {
            Path dir = root.resolve(label == Label.RECEIPT ? "receipts" : "non-receipts");
            if (!Files.isDirectory(dir)) {
                continue;
            }
            for (Path file : listImages(dir)) {
                String rel = root.relativize(file).toString().replace('\\', '/');
                String group = groupOf(dir, file);
                long size = Files.size(file);
                long mtime = Files.getLastModifiedTime(file).toMillis();

                List<String> variants = new ArrayList<>(List.of(ORIGINAL));
                if (label == Label.RECEIPT && props.getClip().isAugmentReceipts()) {
                    Augmenter.VARIANTS.forEach(v -> variants.add(v.name()));
                }

                BufferedImage image = null;
                for (String variant : variants) {
                    String key = rel + "#" + variant;
                    CacheEntry hit = cache.get(key);
                    float[] vector;
                    if (hit != null && hit.size() == size && hit.mtime() == mtime
                            && hit.vector().length == encoder.getDimensions()) {
                        vector = hit.vector();
                    } else {
                        try {
                            if (image == null) {
                                image = ImageLoader.read(Files.readAllBytes(file), props.getDecision().getMaxPixels());
                            }
                            BufferedImage input = ORIGINAL.equals(variant) ? image : Augmenter.apply(variant, image);
                            vector = encoder.embed(input);
                            embedded++;
                        } catch (Exception e) {
                            log.warn("Skipping reference {} ({}): {}", rel, variant, e.getMessage());
                            break;
                        }
                    }
                    fresh.put(key, new CacheEntry(size, mtime, vector));
                    refs.add(new Reference(rel, label, group, variant, vector));
                }
            }
        }

        LogisticProbe probe = null;
        boolean hasBoth = refs.stream().anyMatch(r -> r.label() == Label.RECEIPT)
                && refs.stream().anyMatch(r -> r.label() == Label.NOT_RECEIPT);
        if (hasBoth) {
            probe = trainOn(refs, r -> true);
        } else {
            log.warn("The probe needs at least one receipt and one non-receipt reference; CLIP is disabled and "
                    + "decisions rest on receipt words alone.");
        }
        snapshot = new Snapshot(List.copyOf(refs), probe); // swapped in one step: requests never see a half-built index
        saveCache(new CacheFile(fingerprint, fresh));

        log.info("Reference index: {} receipts (+{} degraded copies), {} non-receipts; {} newly embedded; "
                        + "probe {}; {} ms",
                count(Label.RECEIPT, true), count(Label.RECEIPT, false) - count(Label.RECEIPT, true),
                count(Label.NOT_RECEIPT, true), embedded, probe == null ? "NOT trained" : "trained",
                System.currentTimeMillis() - t0);
    }

    /** First sub-folder under receipts/ or non-receipts/, or the file's own name when it sits directly there. */
    static String groupOf(Path labelDir, Path file) {
        Path rel = labelDir.relativize(file);
        if (rel.getNameCount() > 1) {
            return rel.getName(0).toString();
        }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static List<Path> listImages(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        int dot = n.lastIndexOf('.');
                        return dot > 0 && IMAGE_EXTENSIONS.contains(n.substring(dot + 1));
                    })
                    .sorted()
                    .toList();
        }
    }

    private Map<String, CacheEntry> loadCache(String fingerprint) {
        Path file = Path.of(props.getClip().getIndexCache());
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            CacheFile cf = mapper.readValue(file.toFile(), CacheFile.class);
            if (!fingerprint.equals(cf.fingerprint()) || cf.entries() == null) {
                log.info("Embedding cache was built with a different model or settings; rebuilding it.");
                return Map.of();
            }
            return cf.entries();
        } catch (IOException e) {
            log.warn("Ignoring unreadable embedding cache {}: {}", file, e.getMessage());
            return Map.of();
        }
    }

    private void saveCache(CacheFile cf) {
        Path file = Path.of(props.getClip().getIndexCache()).toAbsolutePath();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writeValue(tmp.toFile(), cf);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Could not write embedding cache {}: {}", file, e.getMessage());
        }
    }
}
