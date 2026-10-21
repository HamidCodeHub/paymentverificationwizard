package com.pantea.receiptgate.ocr;

import com.pantea.receiptgate.image.ImageOps;

import java.awt.image.BufferedImage;

/**
 * Makes a receipt image easier for Tesseract: grayscale, upscale small screenshots, and invert dark-mode
 * banking apps (Tesseract's Persian model reads dark-on-light far better than light-on-dark).
 * Thresholds are the ones measured in the bench.
 */
public final class OcrPreprocessor {

    /** Narrower images are doubled. Most phone screenshots are 465-700 px wide. */
    static final int UPSCALE_BELOW_WIDTH = 900;
    /** Mean gray below this = dark mode. */
    static final int DARK_MEAN = 110;

    private OcrPreprocessor() {
    }

    public static BufferedImage prepare(BufferedImage src, int rotationDegrees) {
        BufferedImage gray = ImageOps.grayscale(ImageOps.rotate(src, rotationDegrees));
        if (gray.getWidth() < UPSCALE_BELOW_WIDTH) {
            gray = ImageOps.scale(gray, gray.getWidth() * 2, gray.getHeight() * 2);
        }
        if (ImageOps.meanGray(gray) < DARK_MEAN) {
            ImageOps.invertGray(gray);
        }
        return gray;
    }
}
