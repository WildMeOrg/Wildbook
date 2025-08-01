package org.ecocean;

/**
 * <code>spot</code> stores the data for a single blob(spot) thresholded by the EasyObject library from a cropped digital image of a whale shark
 * encounter. The data within a spot is collected by the constructor only. No
 * "setter" methods are allowed in order to preserve data integrity.
 * <p/>
 *
 * @author Jason Holmberg
 * @version 0.1 (alpha)
 */

// unenhanced comment

public class Spot implements java.io.Serializable {
    /**
     * empty constructor required by JDO Enhancer. DO NOT USE.
     */
    public Spot() {}

    static final long serialVersionUID = 6508806733115088087L;
    private int area, NbRuns, largestRun;
    private double gravCenterX, gravCenterY, limitCenterX, limitCenterY, limitWidth, limitHeight;
    private double lim45CenterX, lim45CenterY, lim45Width, lim45Height;
    private int countourX, contourY, pixelMin, pixelMax;
    private double sigmaX, sigmaY, sigmaXX, sigmaXY, sigmaYY;
    private double elipseWidth, ellipseHeight, ellipseAngle, centroidX, centroidY;
    private double pixGrayAvg, pixGrayVar, lim22CenterX, lim22CenterY, lim22Width, lim22Height;
    private double lim68CenterX, lim68CenterY, lim68Width, lim68Height, feretCenterX, feretCenterY;
    private double feretWidth, feretHeight, feretAngle;

    /**
     * This constructor is for initial testing only - REMOVE ME AND REPLACE WITH A CONSTRUCTOR THAT REQUIRES ALL DATA
     */
    public Spot(int area, double centroidX, double centroidY) {
        this.area = area;
        this.centroidX = centroidX;
        this.centroidY = centroidY;
    }

    /**
     * Retrieves the area in pixels of the thresholded spot.
     *
     * @return an <code>int</code> that represents the number of pixels that make up the spot
     */
    public int getArea() {
        return area;
    }

    /**
     * Retrieves the X coordinate of the centroid found for the thresholded blob that represents this spot in the original image.
     *
     * @return a <code>double</code> value for the X coordinate of the centroid
     */
    public double getCentroidX() {
        return centroidX;
    }

    /**
     * Retrieves the Y coordinate of the centroid found for the thresholded blob that represents this spot in the original image.
     *
     * @return a <code>double</code> value for the Y coordinate of the centroid
     */
    public double getCentroidY() {
        return centroidY;
    }

    public Spot getClone() {
        return (new Spot(area, centroidX, centroidY));
    }
}
