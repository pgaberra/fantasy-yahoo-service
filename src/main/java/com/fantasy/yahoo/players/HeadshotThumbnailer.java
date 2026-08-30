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
 * Scales a full-resolution Yahoo cutout down to something small enough to store and to serve.
 *
 * <p>Scaling is all it does. It used to crop the picture to a square framed on the player's head,
 * but the BFF now does that framing for every pool it serves — Yahoo's and ESPN's alike — because
 * that is the one place both meet, and the rule cannot sensibly live in two services that cannot
 * see each other. Cropping here as well would mean deciding the framing twice, in the service that
 * is no longer the one deciding it.
 *
 * <p>What is kept is the part that is genuinely this service's problem: Yahoo's sources are around
 * eight megapixels and well over a megabyte each, which is not a thing to hold in a database or to
 * hand across the network per avatar. The shape is left alone — the BFF needs the whole frame to
 * find the head in, and a picture already cropped square would give it nothing to work from.
 */
public final class HeadshotThumbnailer {

    /**
     * How tall the stored picture is; the width follows from the source's own proportions. Three
     * times the 64px avatar the BFF renders from it, which leaves it detail to scale down from
     * after it crops, and matches what it asks ESPN for so both pools arrive in the same shape.
     */
    public static final int HEIGHT = 192;

    /**
     * How this thumbnail was rendered, stored beside it. A change to the shape or the size leaves
     * every stored image stale while its source URL is untouched, so the sync has nothing to
     * notice; comparing the recipe gives it something. Bump this whenever the output changes.
     */
    public static final String RECIPE = "plain-192";

    /**
     * Decoding a source at full size is the expensive part — Yahoo's cutouts are around eight
     * megapixels, or 33 MB each once decoded, and several run at a time during a sync. The reader
     * subsamples instead, down to the smallest step that still leaves comfortably more detail than
     * the output needs, so the scale below has something to average over.
     */
    private static final int MIN_DECODED_HEIGHT = HEIGHT * 2;

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
                return encodePng(scaleToHeight(decodeSubsampled(reader)));
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage decodeSubsampled(ImageReader reader) throws IOException {
        int step = Math.max(1, reader.getHeight(0) / MIN_DECODED_HEIGHT);
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(step, step, 0, 0);
        return reader.read(0, param);
    }

    /** A source already at or below the target height is left as it is rather than blown up. */
    private static BufferedImage scaleToHeight(BufferedImage image) {
        BufferedImage current = image;
        // Halving repeatedly before the final step: a single bilinear jump from a few hundred
        // pixels to the target samples too sparsely and leaves the edges ragged.
        while (current.getHeight() > HEIGHT * 2) {
            current = drawScaled(current, current.getHeight() / 2);
        }
        return current.getHeight() <= HEIGHT ? current : drawScaled(current, HEIGHT);
    }

    private static BufferedImage drawScaled(BufferedImage image, int height) {
        int width = Math.max(1, Math.round((float) image.getWidth() * height / image.getHeight()));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
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
