package org.ecocean;

import java.util.ArrayList;
import org.json.JSONArray;

// unenhanced comment

/**
 * <code>superSpot</code> stores the data for a single spot.
 *
 * @author Jason Holmberg (me!) Another comment another comment
 */
public class SuperSpot implements java.io.Serializable {
    static final long serialVersionUID = -4522794707253880096L;

    // the x-coordinate of the spot within the image dimensions
    private double centroidX;

    // the y-coordinate of the spot within the image dimensions
    private double centroidY;

    private Double type;

    /**
     * empty constructor used by JDO Enhancer - DO NOT USE
     */
    public SuperSpot() {}

    /**
     * the constructor to be used for quick and dirty pattern matching in encounter.filterTrianglesByCentroidPatternMatching()
     */
    public SuperSpot(Spot theSpot) {
        this.centroidX = theSpot.getCentroidX();
        this.centroidY = theSpot.getCentroidY();
    }

    public SuperSpot(double x, double y) {
        this.centroidX = x;
        this.centroidY = y;
    }

    public SuperSpot(double x, double y, Double type) {
        this.centroidX = x;
        this.centroidY = y;
        this.type = type;
    }

    /**
     * Returns the central spot which the neighboring spots surround
     *
     * @return the individual spot around which the nearest neighbors are ordered
     */
    public Spot getTheSpot() {
        return (new Spot(0, centroidX, centroidY));
    }

    public double getCentroidX() {
        return centroidX;
    }

    public double getCentroidY() {
        return centroidY;
    }

    public void setCentroidX(double x) {
        this.centroidX = x;
    }

    public void setCentroidY(double y) {
        this.centroidY = y;
    }

    public void setType(Double type) { this.type = type; }
    public Double getType() { return type; }

    public JSONArray toJSONObject() {
        JSONArray arr = new JSONArray();

        arr.put(centroidX);
        arr.put(centroidY);
        arr.put(type);
        return arr;
    }

    public static JSONArray listToJSONArray(ArrayList<SuperSpot> spots) {
        if (spots == null) return null;
        JSONArray arr = new JSONArray();
        for (SuperSpot s : spots) {
            arr.put(s.toJSONObject());
        }
        return arr;
    }

    public static ArrayList<SuperSpot> listFromJSONArray(JSONArray arr) {
        if (arr == null) return null;
        ArrayList<SuperSpot> s = new ArrayList<SuperSpot>();
        for (int i = 0; i < arr.length(); i++) {
            // not a lot of graceful recovery from incorrect values here....
            double x = arr.getJSONArray(i).getDouble(0);
            double y = arr.getJSONArray(i).getDouble(1);
            double t = arr.getJSONArray(i).getDouble(2);
            s.add(new SuperSpot(x, y, t));
        }
        return s;
    }
}
