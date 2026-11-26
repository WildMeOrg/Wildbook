package org.ecocean.export;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Crops annotations the way Wildbook stores them. For assets whose EXIF orientation is 90 or 270
 * ({@link org.ecocean.media.MediaAsset#getRotationInfo()}), stored boxes are pixel coordinates of the
 * upright frame, not of the raw raster that ImageIO decodes. A rotated box (theta, radians) is cut the
 * way ml-service's crop_rect does: rotate the image about the box centre, then take the axis-aligned
 * window. Contains no database or asset-store dependencies.
 */
public final class ReferenceCrop {
    private ReferenceCrop() {}

    /** Rotates a decoded raster to the upright frame; null or non-quadrant rotation info returns it unchanged. */
    public static BufferedImage upright(BufferedImage raster, String rotationInfo) {
        if (rotationInfo == null) return raster;
        boolean clockwise270 = rotationInfo.contains("270");
        if (!clockwise270 && !rotationInfo.contains("90")) return raster;
        int width = raster.getWidth();
        int height = raster.getHeight();
        BufferedImage out = new BufferedImage(height, width, BufferedImage.TYPE_INT_RGB);
        AffineTransform transform = new AffineTransform();
        if (clockwise270) {
            transform.translate(0, width);
            transform.rotate(-Math.PI / 2);
        } else {
            transform.translate(height, 0);
            transform.rotate(Math.PI / 2);
        }
        Graphics2D graphics = out.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(raster, transform, null);
        } finally {
            graphics.dispose();
        }
        return out;
    }

    /**
     * bbox is {x, y, width, height} in the upright frame; theta in radians as stored on the Feature.
     * A missing or empty box, or one entirely outside the image, yields the whole upright image.
     */
    public static BufferedImage crop(BufferedImage raster, String rotationInfo, int[] bbox, double theta) {
        BufferedImage image = upright(raster, rotationInfo);
        if (bbox == null || bbox.length != 4 || bbox[2] <= 0 || bbox[3] <= 0) return image;
        int width = bbox[2];
        int height = bbox[3];
        if (theta == 0.0) {
            int x = Math.max(0, bbox[0]);
            int y = Math.max(0, bbox[1]);
            int clampedWidth = Math.min(bbox[0] + width, image.getWidth()) - x;
            int clampedHeight = Math.min(bbox[1] + height, image.getHeight()) - y;
            if (clampedWidth <= 0 || clampedHeight <= 0) return image;
            return image.getSubimage(x, y, clampedWidth, clampedHeight);
        }
        // cv2's positive angle is counter-clockwise while AWT's is clockwise, hence -theta. Drawing the
        // whole source through the transform into the window pads overhang with white like crop_rect.
        double centreX = bbox[0] + width / 2.0;
        double centreY = bbox[1] + height / 2.0;
        BufferedImage window = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = window.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform transform = new AffineTransform();
            transform.translate(width / 2.0 - centreX, height / 2.0 - centreY);
            transform.rotate(-theta, centreX, centreY);
            graphics.drawImage(image, transform, null);
        } finally {
            graphics.dispose();
        }
        return window;
    }

    /** Scales so the longer edge is at most maxEdge (never upscales) and returns an RGB image fit for JPEG. */
    public static BufferedImage fit(BufferedImage image, int maxEdge) {
        double scale = Math.min(1.0, maxEdge / (double)Math.max(image.getWidth(), image.getHeight()));
        if (scale == 1.0 && image.getType() == BufferedImage.TYPE_INT_RGB) return image;
        int width = Math.max(1, (int)Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int)Math.round(image.getHeight() * scale));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return out;
    }
}
