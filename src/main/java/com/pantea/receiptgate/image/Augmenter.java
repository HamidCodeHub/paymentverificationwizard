package com.pantea.receiptgate.image;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Degraded copies of a reference receipt: the ways a real customer's upload differs from a clean
 * screenshot. Training the probe on these is what keeps a blurry Telegram-compressed photo of a receipt
 * from being blocked. Names match the Python bench in {@code bench/} so results can be compared.
 */
public final class Augmenter {

    /** Bump when a variant changes, so cached embeddings of the old variant are discarded. */
    public static final int VERSION = 1;

    public record Variant(String name, UnaryOperator<BufferedImage> transform) {
    }

    public static final List<Variant> VARIANTS = List.of(
            new Variant("top-60%", im -> ImageOps.crop(im, 0, 0, im.getWidth(), (int) (im.getHeight() * 0.6))),
            new Variant("bottom-60%", im -> {
                int y = (int) (im.getHeight() * 0.4);
                return ImageOps.crop(im, 0, y, im.getWidth(), im.getHeight() - y);
            }),
            new Variant("rotated-90", im -> ImageOps.rotate(im, 90)),
            new Variant("blur+lowq", im -> ImageOps.jpegRoundTrip(ImageOps.gaussianBlur(im, 2.0), 0.35f)),
            new Variant("small-240px", im -> ImageOps.scale(im, 240,
                    Math.max(1, (int) Math.round(240.0 * im.getHeight() / im.getWidth()))))
    );

    private Augmenter() {
    }

    public static BufferedImage apply(String name, BufferedImage original) {
        return VARIANTS.stream()
                .filter(v -> v.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown variant " + name))
                .transform().apply(original);
    }
}
