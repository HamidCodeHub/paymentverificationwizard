package com.pantea.receiptgate.image;

import com.pantea.receiptgate.ocr.OcrPreprocessor;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageHandlingTest {

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage filled(int w, int h, Color c) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    @Test
    void garbageBytesGiveACustomerSafeMessage() {
        assertThatThrownBy(() -> ImageLoader.read("not an image".getBytes(StandardCharsets.UTF_8), 1_000_000))
                .isInstanceOf(ImageLoader.ImageRejectedException.class)
                .hasMessage(ImageLoader.UNREADABLE);
    }

    @Test
    void pixelLimitIsCheckedFromTheHeader() throws IOException {
        byte[] big = png(filled(2000, 2000, Color.WHITE));
        assertThatThrownBy(() -> ImageLoader.read(big, 1_000_000))
                .isInstanceOf(ImageLoader.ImageRejectedException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void transparentPngIsFlattenedOntoWhite() throws IOException {
        BufferedImage argb = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB); // fully transparent
        BufferedImage read = ImageLoader.read(png(argb), 1_000_000);
        assertThat(read.getRGB(5, 5) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }

    @Test
    void quarterTurnsSwapDimensionsAndMovePixelsClockwise() {
        BufferedImage img = filled(4, 2, Color.WHITE);
        img.setRGB(0, 0, Color.RED.getRGB()); // top-left
        BufferedImage cw = ImageOps.rotate(img, 90);
        assertThat(cw.getWidth()).isEqualTo(2);
        assertThat(cw.getHeight()).isEqualTo(4);
        assertThat(cw.getRGB(1, 0) & 0xFFFFFF).isEqualTo(0xFF0000); // top-left -> top-right
        assertThat(ImageOps.rotate(img, 360)).isSameAs(img);
    }

    @Test
    void darkModeIsInvertedAndSmallScreenshotsAreUpscaled() {
        BufferedImage dark = filled(500, 1000, new Color(20, 24, 33));
        BufferedImage prepared = OcrPreprocessor.prepare(dark, 0);
        assertThat(prepared.getWidth()).isEqualTo(1000);
        assertThat(ImageOps.meanGray(prepared)).isGreaterThan(200);
    }

    @Test
    void everyAugmentationProducesAnImage() {
        BufferedImage img = filled(540, 1170, Color.LIGHT_GRAY);
        for (Augmenter.Variant v : Augmenter.VARIANTS) {
            BufferedImage out = v.transform().apply(img);
            assertThat(out.getWidth()).as(v.name()).isPositive();
        }
        assertThat(Augmenter.apply("small-240px", img).getWidth()).isEqualTo(240);
        assertThat(Augmenter.apply("rotated-90", img).getWidth()).isEqualTo(1170);
    }
}
