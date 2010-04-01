package org.ecocean;

//unenhanced comment


/**
 *<code>superSpot</code> stores the data for a single spot.
 *@author	Jason Holmberg (me!)
 *Another comment
 *another comment
*/
public class SuperSpot implements java.io.Serializable {
	
	static final long serialVersionUID = -4522794707253880096L;

	//the x-coordinate of the spot within the image dimensions
	private double centroidX;
	
	//the y-coordinate of the spot within the image dimensions
	private double centroidY;
	
	
	
	/**
	 *empty constructor used by JDO Enhancer - DO NOT USE
	 */
	public SuperSpot(){}
	

		
	/**
	 *the constructor to be used for quick and dirty pattern matching in encounter.filterTrianglesByCentroidPatternMatching()
	 */
	public SuperSpot(Spot theSpot) {
		this.centroidX=theSpot.getCentroidX();
		this.centroidY=theSpot.getCentroidY();
		}
	
	public SuperSpot(double x, double y) {
		this.centroidX=x;
		this.centroidY=y;
	}

   /**
    *Returns the central spot which the neighboring spots surround
    *@return the individual spot around which the nearest neighbors are ordered
    */
	public Spot getTheSpot() {
		return (new Spot(0,centroidX,centroidY));
	}


	
	public double getCentroidX(){return centroidX;}
	public double getCentroidY(){return centroidY;}
	public void setCentroidX(double x){this.centroidX=x;}
	public void setCentroidY(double y){this.centroidY=y;}
	
}
