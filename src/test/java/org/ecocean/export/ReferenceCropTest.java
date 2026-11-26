package org.ecocean.export;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ReferenceCropTest {
    private static final int RED = Color.RED.getRGB();
    private static final int BLUE = Color.BLUE.getRGB();
    private static final int WHITE = Color.WHITE.getRGB();

    /** 40x20 white raster with a 4x4 red square at sensor x=30..33, y=2..5. */
    private static BufferedImage raster() {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        fill(image, 0, 0, 40, 20, WHITE);
        fill(image, 30, 2, 4, 4, RED);
        return image;
    }

    private static void fill(BufferedImage image, int x, int y, int w, int h, int rgb) {
        for (int i = x; i < x + w; i++) for (int j = y; j < y + h; j++) image.setRGB(i, j, rgb);
    }

    private static double fraction(BufferedImage image, int rgb) {
        int hits = 0;
        for (int i = 0; i < image.getWidth(); i++)
            for (int j = 0; j < image.getHeight(); j++)
                if (image.getRGB(i, j) == rgb) hits++;
        return hits / (double)(image.getWidth() * image.getHeight());
    }

    @Test void cropsInTheUprightFrameForRotatedAssets() {
        BufferedImage upright = ReferenceCrop.upright(raster(), "Right side, top (Rotate 90 CW)");
        assertEquals(20, upright.getWidth());
        assertEquals(40, upright.getHeight());
        for (int i = 14; i <= 17; i++) for (int j = 30; j <= 33; j++) assertEquals(RED, upright.getRGB(i, j));
        assertEquals(WHITE, upright.getRGB(13, 30));
        assertEquals(WHITE, upright.getRGB(14, 29));
        BufferedImage crop = ReferenceCrop.crop(raster(), "Right side, top (Rotate 90 CW)", new int[] {14, 30, 4, 4}, 0);
        assertEquals(4, crop.getWidth());
        assertEquals(4, crop.getHeight());
        assertEquals(1.0, fraction(crop, RED));
    }

    @Test void rotate270PutsTheSquareBottomLeft() {
        BufferedImage upright = ReferenceCrop.upright(raster(), "Left side, bottom (Rotate 270 CW)");
        assertEquals(20, upright.getWidth());
        assertEquals(40, upright.getHeight());
        for (int i = 2; i <= 5; i++) for (int j = 6; j <= 9; j++) assertEquals(RED, upright.getRGB(i, j));
        assertEquals(WHITE, upright.getRGB(6, 6));
        assertEquals(WHITE, upright.getRGB(2, 10));
    }

    @Test void nullRotationLeavesTheRasterAlone() {
        BufferedImage source = raster();
        assertSame(source, ReferenceCrop.upright(source, null));
        BufferedImage crop = ReferenceCrop.crop(source, null, new int[] {30, 2, 4, 4}, 0);
        assertEquals(1.0, fraction(crop, RED));
    }

    @Test void thetaRotatesAboutTheBoxCentre() {
        BufferedImage image = new BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB);
        fill(image, 0, 0, 60, 60, WHITE);
        fill(image, 27, 20, 6, 20, RED); // vertical bar centred at (30, 30)
        BufferedImage crop = ReferenceCrop.crop(image, null, new int[] {20, 27, 20, 6}, Math.PI / 2);
        assertEquals(20, crop.getWidth());
        assertEquals(6, crop.getHeight());
        assertTrue(fraction(crop, RED) >= 0.9, "red fraction " + fraction(crop, RED));
    }

    @Test void thetaRotatesCounterClockwiseLikeMlService() {
        BufferedImage image = new BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB);
        fill(image, 0, 0, 60, 60, WHITE);
        fill(image, 27, 20, 6, 10, RED);  // top half of the bar
        fill(image, 27, 30, 6, 10, BLUE); // bottom half of the bar
        // cv2 positive theta is counter-clockwise: the top of the bar lands on the LEFT of the window.
        BufferedImage crop = ReferenceCrop.crop(image, null, new int[] {20, 27, 20, 6}, Math.PI / 2);
        assertEquals(RED, crop.getRGB(4, 3));
        assertEquals(BLUE, crop.getRGB(15, 3));
    }

    @Test void clampsOverhangingAxisAlignedBoxes() {
        BufferedImage crop = ReferenceCrop.crop(raster(), null, new int[] {35, 15, 10, 10}, 0);
        assertEquals(5, crop.getWidth());
        assertEquals(5, crop.getHeight());
    }

    @Test void invalidBoxReturnsTheWholeUprightImage() {
        BufferedImage crop = ReferenceCrop.crop(raster(), "Rotate 90 CW", null, 0);
        assertEquals(20, crop.getWidth());
        assertEquals(40, crop.getHeight());
        assertEquals(40, ReferenceCrop.crop(raster(), null, new int[] {0, 0, 0, 5}, 0).getWidth());
    }

    @Test void fitNeverUpscales() {
        BufferedImage small = ReferenceCrop.fit(new BufferedImage(20, 40, BufferedImage.TYPE_INT_ARGB), 640);
        assertEquals(20, small.getWidth());
        assertEquals(40, small.getHeight());
        assertEquals(BufferedImage.TYPE_INT_RGB, small.getType());
        BufferedImage large = ReferenceCrop.fit(new BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB), 640);
        assertEquals(640, large.getWidth());
        assertEquals(320, large.getHeight());
    }
}
