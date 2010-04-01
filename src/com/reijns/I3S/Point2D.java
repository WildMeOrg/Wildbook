
package com.reijns.I3S;

// In the C++ source, protected vars and methods could be accessible from a 
// friend classes; in Java we get similar behavior by including
// classes in the same package.  But see comments below.
//TBD
public class Point2D implements java.io.Serializable {

	static final long serialVersionUID = 9122107317335010239L;
	
	/** 
	 *  Point2D Variables
	 *  
	 *  Comments: In original C++ source the protected x and y values are
	 *  treated as properties accessible from within Friend classes (there are
	 *  no setter methods).  Beware of bending OO concepts by maintaining 
	 *  this system.  The DBL_INIT constant is initiated here and referenced
	 *  in the entire package as a Point2D immutable value.
	 */	
	public double x, y;
	public final static int DBL_INIT = -1000000000;

	/**
	 * Point2D Constructor
	 * 
	 * Comments: Second constructor method added.
	 */	
	public Point2D(){
		x = DBL_INIT;
		y = DBL_INIT;
	}
	
	public Point2D(double x, double y){
		this.x = x;
		this.y = y;		
	}
		
	public Point2D(Point2D orig) {
		this.x = orig.getX();
		this.y = orig.getY();
	}

	/**
	 * Point2D Public Methods
	 * 
	 * Comments: Normally getter methods bend Encapsulation, but since the 
	 * Point2D class can be considered as only a Value Object access to the 
	 * data is required by the processor objects.  The getters are actually not 
	 * needed since the protected x and y values are accessible to all classes
	 * in the ecocean package, but to maintain the code base they are 
	 * retained.  However we should restrict data getting only to within the 
	 * ecocean package.  Thus the getter methods are set for package 
	 * level access (i.e. the default for undeclared method types) rather than 
	 * public.
	 */
	public double getX() 
	{ 
		return x; 
	}
		
	public double getY() 
	{ 
		return y; 
	}
	
	void setX(double x) 
	{ 
		this.x = x; 
	}
		
	void setY(double y) 
	{ 
		this.y = y; 
	}
		
} 

