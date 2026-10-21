package com.pantea.receiptgate.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Decodes an upload safely: dimensions are read from the header and checked before any pixels are
 * allocated (a 100 KB PNG can declare 50,000 x 50,000), EXIF rotation is applied, and the result is
 * always opaque RGB. Every exception message here is safe to show a customer verbatim.
 */
public final class ImageLoader {

    public static final String UNREADABLE =
            "That file is not a readable image. Please upload a JPG or PNG screenshot of your receipt.";

    /** Thrown for images we understood but refuse; the message is customer-safe. */
    public static class ImageRejectedException extends IOException {
        public ImageRejectedException(String message) {
            super(message);
        }
    }

    private ImageLoader() {
    }

    public static BufferedImage read(byte[] bytes, long maxPixels) throws IOException {
        BufferedImage img;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new ImageRejectedException(UNREADABLE);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long w = reader.getWidth(0);
                long h = reader.getHeight(0);
                if (w * h > maxPixels) {
                    throw new ImageRejectedException(String.format(
                            "That image is too large (%d x %d). Please upload a screenshot or a smaller photo.", w, h));
                }
                img = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (ImageRejectedException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Truncated or malformed files surface as assorted IOException/IllegalArgumentException subtypes.
            throw new ImageRejectedException(UNREADABLE);
        }
        if (img == null) {
            throw new ImageRejectedException(UNREADABLE);
        }
        return applyExifOrientation(ImageOps.toRgb(img), bytes);
    }

    /** ImageIO ignores EXIF, so a phone photo of a printed receipt would arrive sideways. */
    static BufferedImage applyExifOrientation(BufferedImage img, byte[] bytes) {
        int orientation;
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            Metadata meta = ImageMetadataReader.readMetadata(in);
            ExifIFD0Directory dir = meta.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (dir == null || !dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return img;
            }
            orientation = dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
        } catch (Exception e) {
            return img; // no/unreadable metadata is normal (every PNG screenshot), not an error
        }
        if (orientation <= 1 || orientation > 8) {
            return img;
        }

        int w = img.getWidth();
        int h = img.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);

        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { return img; }
        }

        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, t, null);
        g.dispose();
        return out;
    }
}
