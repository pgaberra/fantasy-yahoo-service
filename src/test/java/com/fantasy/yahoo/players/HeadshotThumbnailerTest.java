package com.fantasy.yahoo.players;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeadshotThumbnailerTest {

    @Test
    void producesASquarePngAtTheThumbnailSize() throws IOException {
        BufferedImage thumbnail = thumbnailOf(yahooShapedSource());

        assertThat(thumbnail.getWidth()).isEqualTo(HeadshotThumbnailer.SIZE);
        assertThat(thumbnail.getHeight()).isEqualTo(HeadshotThumbnailer.SIZE);
    }

    /**
     * Yahoo's cutouts are wide, and the table drew them with {@code object-fit: cover} — a centre
     * crop. Cropping the same way here keeps every existing headshot framed as it was, rather
     * than squashing in the margins the browser used to leave out.
     */
    @Test
    void cropsToTheCentreRatherThanSquashingTheMarginsIn() throws IOException {
        BufferedImage thumbnail = thumbnailOf(yahooShapedSource());

        assertThat(colourAtCentre(thumbnail)).isEqualTo(Color.GREEN);
        assertThat(containsAnyRed(thumbnail)).isFalse();
    }

    @Test
    void keepsTheCutoutTransparent() throws IOException {
        BufferedImage thumbnail = thumbnailOf(yahooShapedSource());

        assertThat(thumbnail.getColorModel().hasAlpha()).isTrue();
        assertThat(new Color(thumbnail.getRGB(0, 0), true).getAlpha()).isZero();
    }

    @Test
    void rejectsSomethingThatIsNotAnImage() {
        assertThatThrownBy(() -> HeadshotThumbnailer.toThumbnail("not a png".getBytes()))
                .isInstanceOf(IOException.class);
    }

    private static BufferedImage thumbnailOf(byte[] source) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(HeadshotThumbnailer.toThumbnail(source)));
    }

    private static Color colourAtCentre(BufferedImage image) {
        return new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
    }

    private static boolean containsAnyRed(BufferedImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                Color colour = new Color(image.getRGB(x, y), true);
                if (colour.getAlpha() > 0 && colour.getRed() > colour.getGreen()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A stand-in with the proportions and transparency of a real cutout: red only in the side
     * margins a centre crop drops, an opaque green subject in the middle, and a transparent
     * border around that subject.
     */
    private static byte[] yahooShapedSource() throws IOException {
        int width = 3504;
        int height = 2336;
        int side = Math.min(width, height);
        int left = (width - side) / 2;
        int inset = side / 4;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, left, height);
        graphics.fillRect(left + side, 0, width - left - side, height);
        graphics.setColor(Color.GREEN);
        graphics.fillRect(left + inset, inset, side - 2 * inset, side - 2 * inset);
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
