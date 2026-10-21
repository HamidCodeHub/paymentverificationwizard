package com.pantea.receiptgate.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Small, dependency-free image helpers shared by the CLIP, OCR and augmentation code. */
public final class ImageOps {

    private ImageOps() {
    }

    /** Opaque RGB copy. Transparent PNG screenshots are flattened onto white, as a viewer would show them. */
    public static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Clockwise rotation by a multiple of 90 degrees. Exact: no interpolation, no cropping. */
    public static BufferedImage rotate(BufferedImage src, int degrees) {
        int d = ((degrees % 360) + 360) % 360;
        if (d == 0) {
            return src;
        }
        if (d % 90 != 0) {
            throw new IllegalArgumentException("Only quarter turns are supported, got " + degrees);
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = d != 180;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h,
                src.getType() == BufferedImage.TYPE_BYTE_GRAY ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB);
        AffineTransform t = new AffineTransform();
        switch (d) {
            case 90 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 180 -> { t.translate(w, h); t.rotate(Math.PI); }
            default -> { t.translate(0, w); t.rotate(-Math.PI / 2); }
        }
        Graphics2D g = out.createGraphics();
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    /** Resize with progressive halving first, so big downscales do not alias. */
    public static BufferedImage scale(BufferedImage src, int targetW, int targetH) {
        BufferedImage current = src;
        int w = src.getWidth();
        int h = src.getHeight();
        while (w / 2 >= targetW && h / 2 >= targetH && w / 2 > 0 && h / 2 > 0) {
            w /= 2;
            h /= 2;
            current = draw(current, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        Object hint = (targetW > current.getWidth()) ? RenderingHints.VALUE_INTERPOLATION_BICUBIC
                : RenderingHints.VALUE_INTERPOLATION_BILINEAR;
        return draw(current, targetW, targetH, hint);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h, Object interpolation) {
        int type = src.getType() == BufferedImage.TYPE_BYTE_GRAY ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), type);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    public static BufferedImage grayscale(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Mean brightness 0..255 of a grayscale image, sampled (at most ~40k pixels read). */
    public static double meanGray(BufferedImage gray) {
        WritableRaster r = gray.getRaster();
        int w = gray.getWidth();
        int h = gray.getHeight();
        int step = (int) Math.max(1, Math.sqrt((double) w * h / 40_000));
        long sum = 0;
        long n = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                sum += r.getSample(x, y, 0);
                n++;
            }
        }
        return n == 0 ? 255 : (double) sum / n;
    }

    /** In place. */
    public static void invertGray(BufferedImage gray) {
        WritableRaster r = gray.getRaster();
        for (int y = 0; y < gray.getHeight(); y++) {
            for (int x = 0; x < gray.getWidth(); x++) {
                r.setSample(x, y, 0, 255 - r.getSample(x, y, 0));
            }
        }
    }

    /** Separable Gaussian blur, comparable to PIL's GaussianBlur(radius=sigma). */
    public static BufferedImage gaussianBlur(BufferedImage src, double sigma) {
        int radius = (int) Math.ceil(3 * sigma);
        int size = 2 * radius + 1;
        float[] k = new float[size];
        float sum = 0;
        for (int i = 0; i < size; i++) {
            double x = i - radius;
            k[i] = (float) Math.exp(-(x * x) / (2 * sigma * sigma));
            sum += k[i];
        }
        for (int i = 0; i < size; i++) {
            k[i] /= sum;
        }
        BufferedImage rgb = toRgb(src);
        BufferedImage pass1 = new ConvolveOp(new Kernel(size, 1, k), ConvolveOp.EDGE_NO_OP, null).filter(rgb, null);
        return new ConvolveOp(new Kernel(1, size, k), ConvolveOp.EDGE_NO_OP, null).filter(pass1, null);
    }

    /** Encode as JPEG at the given quality (0..1) and decode again: simulates a messenger-compressed photo. */
    public static BufferedImage jpegRoundTrip(BufferedImage src, float quality) {
        try {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(out);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                writer.write(null, new IIOImage(toRgb(src), null, null), param);
            } finally {
                writer.dispose();
            }
            return toRgb(ImageIO.read(new ByteArrayInputStream(bytes.toByteArray())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Detached copy of a region (getSubimage shares the parent's raster). */
    public static BufferedImage crop(BufferedImage src, int x, int y, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src.getSubimage(x, y, w, h), 0, 0, null);
        g.dispose();
        return out;
    }
}
