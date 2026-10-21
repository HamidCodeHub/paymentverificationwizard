package com.pantea.receiptgate.verify;

import com.pantea.receiptgate.config.GateProperties;
import com.pantea.receiptgate.gate.GateResult.Verdict;
import com.pantea.receiptgate.gate.ReceiptGateService;
import com.pantea.receiptgate.gate.ReceiptGateService.Decision;
import com.pantea.receiptgate.gate.ReceiptGateService.OcrOutcome;
import com.pantea.receiptgate.image.Augmenter;
import com.pantea.receiptgate.image.ImageLoader;
import com.pantea.receiptgate.index.LogisticProbe;
import com.pantea.receiptgate.index.ReferenceIndex;
import com.pantea.receiptgate.index.ReferenceIndex.Label;
import com.pantea.receiptgate.index.ReferenceIndex.Reference;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Measures the gate on your own reference images before customers see it.
 *
 * <p>Run with {@code --verify} (originals only, ~2 min) or {@code --verify=full} (also every degraded
 * receipt copy, several minutes). Each image is judged by a probe trained <em>without its whole group</em>:
 * a receipt from a bank layout the probe has never seen, a kind of screenshot it has never seen. That is the
 * honest version of "will a new customer's receipt pass?". Keyword scoring needs no training, so it is
 * measured directly. The final verdict uses exactly the API's decision rule.
 */
@Component
public class ReferenceVerifier implements ApplicationRunner {

    private final ReferenceIndex index;
    private final ReceiptGateService gate;
    private final GateProperties props;
    private final ConfigurableApplicationContext context;

    public ReferenceVerifier(ReferenceIndex index, ReceiptGateService gate, GateProperties props,
                             ConfigurableApplicationContext context) {
        this.index = index;
        this.gate = gate;
        this.props = props;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("verify")) {
            return;
        }
        boolean full = args.getOptionValues("verify").contains("full");

        if (!index.isProbeTrained()) {
            System.out.println("Need at least one receipt and one non-receipt under " + index.root());
            context.close();
            return;
        }

        Map<String, List<Reference>> groups = new LinkedHashMap<>();
        for (Reference r : index.references()) {
            groups.computeIfAbsent(r.label() + "/" + r.group(), k -> new ArrayList<>()).add(r);
        }

        Map<Label, Map<Verdict, Integer>> originals = new EnumMap<>(Label.class);
        Map<Verdict, Integer> degraded = new EnumMap<>(Verdict.class);
        List<String> problems = new ArrayList<>();

        System.out.println();
        System.out.println("=== Receipt gate check: " + groups.size() + " groups held out one at a time"
                + (full ? ", degraded copies included" : "") + " ===");
        System.out.printf("%-12s %-22s %-14s %5s %6s %4s  %-12s %s%n",
                "LABEL", "GROUP", "VARIANT", "WORDS", "CLIP", "ROT", "VERDICT", "FILE");
        System.out.println("-".repeat(110));

        for (List<Reference> group : groups.values()) {
            Reference first = group.get(0);
            LogisticProbe probe = index.trainProbe(
                    r -> !(r.label() == first.label() && r.group().equals(first.group())));

            Map<String, BufferedImage> decoded = new LinkedHashMap<>();
            for (Reference r : group) {
                if (!full && !r.isOriginal()) {
                    continue;
                }
                BufferedImage original = decoded.computeIfAbsent(r.path(), this::load);
                if (original == null) {
                    continue;
                }
                BufferedImage image = r.isOriginal() ? original : Augmenter.apply(r.variant(), original);
                double p = probe.probability(r.vector());
                OcrOutcome ocr = gate.readKeywords(image, p);
                Decision d = gate.decide(ocr, p);

                if (r.isOriginal()) {
                    originals.computeIfAbsent(r.label(), k -> new EnumMap<>(Verdict.class)).merge(d.verdict(), 1, Integer::sum);
                } else {
                    degraded.merge(d.verdict(), 1, Integer::sum);
                }
                boolean bad = (r.label() == Label.RECEIPT && d.verdict() == Verdict.NOT_RECEIPT)
                        || (r.label() == Label.NOT_RECEIPT && d.verdict() == Verdict.RECEIPT);
                if (bad) {
                    problems.add(String.format("%s %s (%s): %s, words %d, clip %.3f", r.label(), r.path(),
                            r.variant(), d.verdict(), ocr.keywords().score(), p));
                }
                System.out.printf("%-12s %-22s %-14s %5d %6.3f %4d  %-12s %s%s%n",
                        r.label(), truncate(r.group(), 22), r.variant(), ocr.keywords().score(), p, ocr.rotation(),
                        d.verdict(), truncate(r.path(), 40), bad ? "   <-- WRONG" : "");
            }
        }

        System.out.println("-".repeat(110));
        System.out.printf("Thresholds: words >= %d -> RECEIPT; else CLIP >= %.2f -> UNSURE; else NOT_RECEIPT%n",
                props.getDecision().getMinKeywordScore(), props.getDecision().getUnsureProbability());
        for (Label label : Label.values()) {
            System.out.printf("%-12s originals -> %s%n", label, originals.getOrDefault(label, Map.of()));
        }
        if (full) {
            System.out.printf("%-12s degraded  -> %s%n", Label.RECEIPT, degraded);
        }
        if (problems.isEmpty()) {
            System.out.println("No receipt was blocked and no non-receipt passed as a receipt.");
        } else {
            System.out.println("Worth a look (a blocked receipt, or a non-receipt accepted as RECEIPT):");
            problems.forEach(p -> System.out.println("  " + p));
        }
        System.out.println();
        context.close();
    }

    private BufferedImage load(String relPath) {
        try {
            return ImageLoader.read(Files.readAllBytes(index.root().resolve(relPath)),
                    props.getDecision().getMaxPixels());
        } catch (Exception e) {
            System.out.println("  cannot read " + relPath + ": " + e.getMessage());
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
