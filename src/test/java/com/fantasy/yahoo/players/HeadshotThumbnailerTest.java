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
        BufferedImage thumbnail = thumbnailOf(cutoutShapedSource());

        assertThat(thumbnail.getWidth()).isEqualTo(HeadshotThumbnailer.SIZE);
        assertThat(thumbnail.getHeight()).isEqualTo(HeadshotThumbnailer.SIZE);
    }

    /**
     * The point of the whole class. A centre crop of a 3:2 cutout keeps the full height, so the
     * head — narrow, and centred near the top — comes out filling less than a third of the
     * square. Framing on the head gives it appreciably more of the picture.
     */
    @Test
    void framesTheHeadRatherThanTheMiddleOfThePicture() throws IOException {
        BufferedImage thumbnail = thumbnailOf(cutoutShapedSource());

        assertThat(colourAtCentre(thumbnail)).isEqualTo(HEAD);
        assertThat(shareOf(thumbnail, HEAD)).isGreaterThan(headShareOfACentreCrop());
    }

    /**
     * The measurement reads the cutout's transparency. A source that has none — or one whose
     * opaque part is too small to be a player — must not be cropped on whatever it happened to
     * find; it falls back to the middle of the picture.
     */
    @Test
    void fallsBackToTheCentreWhenThereIsNoCutoutToMeasure() throws IOException {
        BufferedImage thumbnail = thumbnailOf(opaqueSource());

        assertThat(colourAtCentre(thumbnail)).isEqualTo(HEAD);
        assertThat(containsAny(thumbnail, MARGIN)).isFalse();
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
    private static final int HEAD_LEFT = 1200;
    private static final int HEAD_RIGHT = 2300;
    private static final int HEAD_TOP = 40;
    private static final int HEAD_BOTTOM = 1500;
    private static final Color HEAD = Color.GREEN;
    private static final Color SHOULDERS = Color.BLUE;
    private static final Color MARGIN = Color.RED;

    private static BufferedImage thumbnailOf(byte[] source) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(HeadshotThumbnailer.toThumbnail(source)));
    }

    private static Color colourAtCentre(BufferedImage image) {
        return new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
    }

    private static double shareOf(BufferedImage image, Color colour) {
        int matched = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (isNear(new Color(image.getRGB(x, y), true), colour)) {
                    matched++;
                }
            }
        }
        return (double) matched / (image.getWidth() * image.getHeight());
    }

    private static boolean containsAny(BufferedImage image, Color colour) {
        return shareOf(image, colour) > 0;
    }

    /** Scaling blends edges, so an exact match would count only the interior of each block. */
    private static boolean isNear(Color actual, Color expected) {
        return actual.getAlpha() > 0
                && Math.abs(actual.getRed() - expected.getRed()) < 40
                && Math.abs(actual.getGreen() - expected.getGreen()) < 40
                && Math.abs(actual.getBlue() - expected.getBlue()) < 40;
    }

    /**
     * The head's share of the square a centre crop would take: the crop is as tall as the source
     * and the head is neither as wide nor as tall as that, so it comes out under a third.
     */
    private static double headShareOfACentreCrop() {
        int side = Math.min(WIDTH, HEIGHT);
        return (double) (HEAD_RIGHT - HEAD_LEFT) / side * (HEAD_BOTTOM - HEAD_TOP) / side;
    }

    /**
     * A stand-in with the proportions of a real cutout: transparent everywhere except a narrow
     * head near the top and shoulders spreading across the bottom.
     */
    private static BufferedImage cutout() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(HEAD);
        graphics.fillRect(HEAD_LEFT, HEAD_TOP, HEAD_RIGHT - HEAD_LEFT, HEAD_BOTTOM - HEAD_TOP);
        graphics.setColor(SHOULDERS);
        graphics.fillRect(300, 1800, 2900, HEIGHT - 1800);
        graphics.dispose();
        return image;
    }

    private static byte[] cutoutShapedSource() throws IOException {
        return png(cutout());
    }

    /**
     * The same proportions with nothing transparent to measure, and colour only in the side
     * margins a centre crop drops — so a centre crop is visible in the result.
     */
    private static byte[] opaqueSource() throws IOException {
        int side = Math.min(WIDTH, HEIGHT);
        int left = (WIDTH - side) / 2;

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(MARGIN);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setColor(HEAD);
        graphics.fillRect(left, 0, side, HEIGHT);
        graphics.dispose();
        return png(image);
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
