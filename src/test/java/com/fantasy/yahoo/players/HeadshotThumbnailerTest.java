package com.fantasy.yahoo.players;

import org.assertj.core.data.Offset;
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
    void scalesTheSourceDownToTheStoredHeight() throws IOException {
        BufferedImage thumbnail = thumbnailOf(cutoutShapedSource());

        assertThat(thumbnail.getHeight()).isEqualTo(HeadshotThumbnailer.HEIGHT);
    }

    /**
     * The shape is left alone on purpose. The BFF frames these on the player's head, and it needs
     * the whole frame to find the head in — a picture cropped square here would leave it nothing
     * to work from, and would mean this service deciding a framing it no longer owns.
     */
    @Test
    void keepsTheSourcesProportionsRatherThanCroppingItSquare() throws IOException {
        BufferedImage thumbnail = thumbnailOf(cutoutShapedSource());

        assertThat((double) thumbnail.getWidth() / thumbnail.getHeight())
                .isCloseTo((double) WIDTH / HEIGHT, Offset.offset(0.02));
    }

    /** The whole point of storing a rendering at all: the source is megapixels, this is not. */
    @Test
    void isFarSmallerThanTheSourceItCameFrom() throws IOException {
        byte[] source = cutoutShapedSource();

        assertThat(HeadshotThumbnailer.toThumbnail(source).length).isLessThan(source.length / 4);
    }

    /** Blowing a small source up would invent detail and charge bytes for it. */
    @Test
    void leavesASourceThatIsAlreadySmallerAlone() throws IOException {
        BufferedImage thumbnail = thumbnailOf(png(blank(120, 90)));

        assertThat(thumbnail.getWidth()).isEqualTo(120);
        assertThat(thumbnail.getHeight()).isEqualTo(90);
    }

    @Test
    void keepsTheCutoutTransparent() throws IOException {
        BufferedImage thumbnail = thumbnailOf(cutoutShapedSource());

        assertThat(thumbnail.getColorModel().hasAlpha()).isTrue();
        assertThat(new Color(thumbnail.getRGB(0, 0), true).getAlpha()).isZero();
    }

    @Test
    void rejectsSomethingThatIsNotAnImage() {
        assertThatThrownBy(() -> HeadshotThumbnailer.toThumbnail("not a png".getBytes()))
                .isInstanceOf(IOException.class);
    }

    private static final int WIDTH = 3504;
    private static final int HEIGHT = 2336;

    private static BufferedImage thumbnailOf(byte[] source) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(HeadshotThumbnailer.toThumbnail(source)));
    }

    /**
     * A stand-in with the proportions of a real cutout: transparent everywhere except a narrow
     * head near the top and shoulders spreading across the bottom.
     */
    private static byte[] cutoutShapedSource() throws IOException {
        BufferedImage image = blank(WIDTH, HEIGHT);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(1200, 40, 1100, 1460);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(300, 1800, 2900, HEIGHT - 1800);
        graphics.dispose();
        return png(image);
    }

    private static BufferedImage blank(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
