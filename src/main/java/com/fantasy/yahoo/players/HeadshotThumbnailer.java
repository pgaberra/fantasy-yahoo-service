package com.fantasy.yahoo.players;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Turns a full-resolution Yahoo cutout into the small square PNG the player table draws.
 * The square is a centre crop, which is what the browser's {@code object-fit: cover} did to
 * the source image before, so the thumbnail frames the player exactly as it always has.
 */
public final class HeadshotThumbnailer {

    public static final int SIZE = 64;

    /**
     * Decoding a source at full size is the expensive part — Yahoo's cutouts are around eight
     * megapixels, or 33 MB each once decoded, and several run at a time during a sync. The
     * reader subsamples instead, down to the smallest step that still leaves comfortably more
     * detail than the thumbnail needs, so the scale below has something to average over.
     */
    private static final int MIN_DECODED_SIZE = SIZE * 4;

    private HeadshotThumbnailer() {
    }

    public static byte[] toThumbnail(byte[] source) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) {
                throw new IOException("Unreadable image data");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return encodePng(scaleToSquare(centreCrop(decodeSubsampled(reader))));
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage decodeSubsampled(ImageReader reader) throws IOException {
        int shortestSide = Math.min(reader.getWidth(0), reader.getHeight(0));
        int step = Math.max(1, shortestSide / MIN_DECODED_SIZE);
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(step, step, 0, 0);
        return reader.read(0, param);
    }

    private static BufferedImage centreCrop(BufferedImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        return image.getSubimage(
                (image.getWidth() - side) / 2,
                (image.getHeight() - side) / 2,
                side,
                side);
    }

    private static BufferedImage scaleToSquare(BufferedImage image) {
        BufferedImage current = image;
        // Halving repeatedly before the final step: a single bilinear jump from a few hundred
        // pixels to 64 samples too sparsely and leaves the edges ragged.
        while (current.getWidth() > SIZE * 2) {
            current = drawScaled(current, Math.max(SIZE, current.getWidth() / 2));
        }
        return current.getWidth() == SIZE ? current : drawScaled(current, SIZE);
    }

    private static BufferedImage drawScaled(BufferedImage image, int side) {
        BufferedImage scaled = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, side, side, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("No PNG writer available");
        }
        return out.toByteArray();
    }
}
