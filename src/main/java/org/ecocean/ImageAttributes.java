/* a mildly useful way for normalize qualities of an image (e.g. for Annotations) */
package org.ecocean;


import org.ecocean.media.MediaAsset;


public class ImageAttributes {
    private double width;
    private double height;
    private double xOffset;
    private double yOffset;
    private String extension = null;


    public ImageAttributes(double w, double h, double x, double y, String ext) {
        this.width = w;
        this.height = h;
        this.xOffset = x;
        this.yOffset = y;
        this.extension = ext;
    }

    public ImageAttributes(double w, double h, String ext) {
        this(w, h, 0.0, 0.0, ext);
    }


    public double getWidth() {
        return width;
    }
    public double getHeight() {
        return height;
    }
    public double getXOffset() {
        return xOffset;
    }
    public double getYOffset() {
        return yOffset;
    }
    public String getExtension() {
        return extension;
    }
}
