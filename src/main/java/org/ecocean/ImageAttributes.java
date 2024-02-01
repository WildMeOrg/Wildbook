/* a mildly useful way for normalize qualities of an image (e.g. for Annotations) */
package org.ecocean;


import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.media.MediaAsset;


public class ImageAttributes {
    private double width;
    private double height;
    private double xOffset;
    private double yOffset;
    private String fileType = null;


    public ImageAttributes(double w, double h, double x, double y, String type) {
        this.width = w;
        this.height = h;
        this.xOffset = x;
        this.yOffset = y;
        this.fileType = type;
    }

    public ImageAttributes(double w, double h, String type) {
        this(w, h, 0.0, 0.0, type);
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
    public String getFileType() {
        return fileType;
    }


    public String toString() {
        return new ToStringBuilder(this)
                .append("width", width)
                .append("height", height)
                .append("xOffset", xOffset)
                .append("yOffset", yOffset)
                .append("fileType", fileType)
                .toString();
    }
}
