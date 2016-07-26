/**
 * @author Ecological Software Solutions LLC
 * @version 0.1 Alpha
 * @copyright 2014 
 * @license This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package com.ecostats.flukes;

import java.lang.reflect.Array;

import org.ecocean.*;
import org.ecocean.grid.*;

import java.util.ArrayList;


/**
 * FinTrace
 * <p/>
 * Class for points along a fin tracing. Each point can be assigned 
 * an type that defines the type of feature on the fin the point represents. 
 * All points between a start and end point of any type should also be assigned
 * that type to define the extent of that type on the fin. This also allows
 * for extracting an exclusive subset of any type from a TreeHash containing
 *  many TracePoints.  
 */
public class FinTrace implements java.io.Serializable {
    
  private static final long serialVersionUID = -2795322614586425845L;
  
  /**
   * FinTrace Variables
   * <p/>
   * Comments: The constants initiated here and referenced
   * in the entire package as a FinTrace immutable value
   * for each point type.
   */
  public final static int DBL_INIT = -1000000000; // default XY point location if one is not provided at construction
  
  
  /* Non-computed Mark Types */
  public final static int POINT = -2;         // normal point, no special notation
  public final static int TIP = -1;           // fin tip
  public final static int NOTCH = 0;          // fluke notch
  
  /*computed mark types */
  public final static int NICK = 1;           // small nick
  public final static int GOUGE = 2;          // large nick, start=2 end=3
  public final static int GOUGE_END= 3;       // large nick, start=2 end=3
  public final static int SCALLOP = 4;        // scallop, start=4 end=5
  public final static int SCALLOP_END = 5;    // scallop, start=4 end=5
  public final static int WAVE = 6;           // wave points, start=6 end=7
  public final static int WAVE_END = 7;       // wave points, start=6 end=7
  public final static int MISSING = 8;        // missing section, start=8 end=9
  public final static int MISSING_END = 9;    // missing section, start=8 end=9
  public final static int SCAR = 10;          // tooth scars on fin
  public final static int HOLE = 11;          // hole in fin
  public final static int INVISIBLE = 12;     // invisible parts, start=12 end=13  
  public final static int INVISIBLE_END = 13; // invisible parts, start=12 end=13
  
  
  /* Trace Type */
  public final static int FEATURE_POINTS = 1;   // only points are recorded at features of interest, no actual tracing
  public final static int COUNTOUR_TRACING = 2; // the fin countour is fully traced
  /* Private Variables */
  private double[] x;
  private double[] y;
  private double[] mark_types;
  private double[] mark_positions;
  private boolean notch_open;
  private boolean curled;
  private int trace_type;  // of type FEATURE_POINTS (default) or COUNTOUR_TRACING
  private String transform; // affine transform maxtrix done on image as space and semicolon separated values. 

  /**
   * FinTrace Constructor
   * <p/>
   * Comments: Basic constructor method.
   */
  public FinTrace() {
    this.notch_open=false;
    this.curled=false;
    this.trace_type=this.FEATURE_POINTS;
    this.transform=new String("0 0 0;0 0 0;0 0 0");
  }

  /**
   * FinTrace Constructor
   * <p/>
   * Comments: Basic constructor method.
   * @param int size : size of the tracing arrays, values to be filled in later
   */
  public FinTrace(int size) {
    this.notch_open=false;
    this.curled=false;
    this.x = new double[size];
    this.y = new double[size];
    this.mark_types = new double[size];
    this.mark_positions = new double[size];
    this.trace_type=this.FEATURE_POINTS;
    this.transform=new String("0 0 0;0 0 0;0 0 0");
  }
  
  /**
   * FinTrace Constructor
   * <p/>
   * Second constructor method with X and Y values provided.
   * @param x double[] : The point location X values
   * @param y double[] : The point location Y values
   * @param mark_types double[] : The type of point at X,Y 
   */
  public FinTrace(double[] x, double y[], double[] mark_types) {
    this.notch_open=false;
    this.curled=false;
    this.x = this.copyarray(x,null);
    this.y = this.copyarray(y,null);
    this.mark_types = this.copyarray(mark_types,null);
    this.mark_positions = new double[x.length];
    this.trace_type=this.FEATURE_POINTS;
    this.transform=new String("0 0 0;0 0 0;0 0 0");
  }
  
  /**
   * FinTrace Constructor
   * <p/>
   * Comments: Fourth constructor method creating a FinTrace from an 
   * original FinTrace class.
   * @param orig : The original TracePoint to copy the value from. 
   */
  public FinTrace(FinTrace orig) {
    this.notch_open = orig.notch_open;
    this.curled = orig.getCurled();
    this.trace_type = orig.getTraceType();
    this.transform = orig.getTransform();
    this.x = this.copyarray(orig.getX());
    this.y = this.copyarray(orig.getY());
    this.mark_types = this.copyarray(orig.getTypes());
    this.mark_positions = this.copyarray(orig.getPositions());
  }
  
  
  public FinTrace(EncounterLite enc, String side) {
    
    ArrayList<SuperSpot> allSpots=enc.getRightSpots();
    SuperSpot[] refSpots=enc.getRightReferenceSpots();
    SuperSpot divet=refSpots[1];
    ArrayList<SuperSpot> spots=new ArrayList<SuperSpot>();
    int numAllSpots=allSpots.size();
    
    for(int i=0;i<numAllSpots;i++){
      SuperSpot thisSpot=(SuperSpot)allSpots.get(i);
      if(side.equals("right")){
        if(thisSpot.getCentroidX()>divet.getCentroidX()){spots.add(thisSpot);}
      }
      else if(side.equals("left")){
        if(thisSpot.getCentroidX()<divet.getCentroidX()){spots.add(thisSpot);}
      }
    
    }
    
    System.out.println("     Fin Trace "+side+" created: "+spots.toString());
    
    int numSpots=spots.size();
    this.x=new double[numSpots];
    this.y=new double[numSpots];
    this.mark_types=new double[numSpots];
    for(int i=0;i<numSpots;i++){
      SuperSpot theSpot=spots.get(i);
      this.x[i]=theSpot.getCentroidX();
      this.y[i]=theSpot.getCentroidY();
      if(theSpot.getType()!=null){
        this.mark_types[i]=theSpot.getType();
      }
      else{this.mark_types[i]=POINT;}
      
    }
    
    if((side.equals("left"))&&(enc.getDynamicPropertyValue("leftCurled")!=null)){
      if(enc.getDynamicPropertyValue("leftCurled").equals("true")){curled=true;}
      else{curled=false;}
    }
    else if((side.equals("right"))&&(enc.getDynamicPropertyValue("rightCurled")!=null)){
      if(enc.getDynamicPropertyValue("rightCurled").equals("true")){curled=true;}
      else{curled=false;}
    }
    else{curled=false;}
    this.mark_positions = new double[x.length];
    this.trace_type=this.FEATURE_POINTS;
    this.transform=new String("0 0 0;0 0 0;0 0 0");

  }
  
  public FinTrace(Encounter enc, String side) {
    
    ArrayList<SuperSpot> allSpots=enc.getRightSpots();
    ArrayList<SuperSpot> refSpots=enc.getRightReferenceSpots();
    SuperSpot divet=refSpots.get(1);
    ArrayList<SuperSpot> spots=new ArrayList<SuperSpot>();
    int numAllSpots=allSpots.size();
    
    for(int i=0;i<numAllSpots;i++){
      SuperSpot thisSpot=allSpots.get(i);
      if(side.equals("right")){
        if(thisSpot.getCentroidX()>divet.getCentroidX()){spots.add(thisSpot);}
      }
      else if(side.equals("left")){
        if(thisSpot.getCentroidX()<divet.getCentroidX()){spots.add(thisSpot);}
      }
    
    }
    int numSpots=spots.size();
    this.x=new double[numSpots];
    this.y=new double[numSpots];
    this.mark_types=new double[numSpots];
    for(int i=0;i<numSpots;i++){
      SuperSpot theSpot=spots.get(i);
      this.x[i]=theSpot.getCentroidX();
      this.y[i]=theSpot.getCentroidY();
      if(theSpot.getType()!=null){
        this.mark_types[i]=theSpot.getType();
      }
      else{this.mark_types[i]=POINT;}
      
    }
    
    if((side.equals("left"))&&(enc.getDynamicPropertyValue("leftCurled")!=null)){
      if(enc.getDynamicPropertyValue("leftCurled").equals("true")){curled=true;}
      else{curled=false;}
    }
    else if((side.equals("right"))&&(enc.getDynamicPropertyValue("rightCurled")!=null)){
      if(enc.getDynamicPropertyValue("rightCurled").equals("true")){curled=true;}
      else{curled=false;}
    }
    else{curled=false;}
    this.mark_positions = new double[x.length];
    this.trace_type=this.FEATURE_POINTS;
    this.transform=new String("0 0 0;0 0 0;0 0 0");

  }
  
  private double[] copyarray(double[] a){
    return this.copyarray(a, null, false);
  }

  private double[] copyarray(double[] a, double[] r){
    return this.copyarray(a, r, false);
  }

  /**
   * Copies and array, and reverses the values if requested. This is the main work method called by other overloaded methods.
   * @param a double[] : the array to copy
   * @param r double[] : an optional existing return array; if set to null a new array will be created and returned
   * @param reverse boolean : set to true to reverse the array values to return
   * @return double[] : the array copy returned
   */
  private double[] copyarray(double[] a, double[] r, boolean reverse){
    if (r==null || r.length==0){
      r = new double[a.length];
    }
    if (reverse){
      int s=0;
      for (int i=a.length-1;i>=0;i--){
        Array.setDouble(r,s,a[i]);
        s+=1;
      }      
    }else{
      for (int i=0;i<a.length;i++){
        Array.setDouble(r,i,a[i]);
      }
    }
    return r;
  }
  
  /**
   * TracePoint Public Methods
   */  
  
  
  /**
   * Returns a new FinTrace with only mark_type points retained
   * @param mark_type int : The type of mark to retain
   * @return FinTrace : new FinTrace object with the mark_type points retained
   */
  public FinTrace returnMarkType(int mark_type){
    int[] m = {mark_type};
    return this.returnMarkType(m);
  }
  
  /**
   * Returns a new FinTrace with only mark_types points retained
   * @param mark_type int : The type of marks to retain as an array of values
   * @return FinTrace : new FinTrace object with the mark_type points retained
   */
  public FinTrace returnMarkType(int[] mark_types){
    int d = 0;
    int[] m = new int[this.mark_types.length];   
    for (int i=0;i<m.length;i++){
      m[i]=0;
      for (int j=0;j<mark_types.length;j++){
        if (this.mark_types[i]==mark_types[j]){
          m[i]=1;
          d+=1;
          break;
        }
      }
    }
    return this.removeTypes(d,m);    
  }
    
  /**
   * Returns a new FinTrace with the mark_type points removed
   * @param mark_type int : The type of mark to remove
   * @return FinTrace : new FinTrace object with the mark_type points removed
   */
  public FinTrace removeMarkType(int mark_type){
    int[] m = {mark_type};
    return this.removeMarkType(m);
  }
    
  /**
   * Returns a new FinTrace with the mark_type points removed
   * @param mark_type int : The type of mark to remove as an array of values
   * @return FinTrace : new FinTrace object with the mark_type points removed
   */
  public FinTrace removeMarkType(int[] mark_types){
    int d = 0;
    int[] m = new int[this.mark_types.length];   
    for (int i=0;i<m.length;i++){
      m[i]=1;
      for (int j=0;j<mark_types.length;j++){
        if (this.mark_types[i]==mark_types[j]){
          m[i]=0;
          d+=1;
          break;
        }
      }
    }
    return this.removeTypes(d,m);    
  }
  
  /**
   * Remove all parts of the FinTrace based on the array values in m
   * @param size int : Size of the new FinTrace
   * @param m int[] : array of 0/1 values, where a value set to 1 means to keep that records.
   * @return FinTrace : The new reduced FinTrace object
   */
  private FinTrace removeTypes(int size, int[] m){
    FinTrace t = new FinTrace(size);
    int s = 0;
    for (int i=0;i<m.length;i++){
      if (m[i]==1){
        t.x[s] = this.x[i];
        t.y[s] = this.y[i];
        t.mark_types[s] = this.mark_types[i];
        t.mark_positions[s] = this.mark_positions[i];
        s+=1;
      }
    }
    return t;
  }
  
  /**
   * Appends a FinTrace to an existing fin trace (this) and returns a new FinTrace
   * @param trace FinTrace : the other FinTrace to append
   * @return FinTrace : the new FinTrace to return
   */
  public FinTrace append(FinTrace trace){
    FinTrace ft = new FinTrace(this);
    ft.combine(trace);
    return ft;    
  }
  
  /**
   * Combines a FinTrace to an existing trace (this) in place, thus changing original FinTrace.
   * @param trace FinTrace : the other FinTrace to combine to this
   * @return FinTrace : returns the "this" FinTrace which has been altered
   */
  public FinTrace combine(FinTrace trace){
    double[] fx = new double[x.length+trace.x.length];
    double[] fy = new double[x.length+trace.x.length];
    double[] fm = new double[x.length+trace.x.length];
    double[] fp = new double[x.length+trace.x.length];
    fx = this.copyarray(this.getX(),fx);
    fy = this.copyarray(this.getY(),fy);
    fm = this.copyarray(this.getTypes(),fm);
    fp = this.copyarray(this.getPositions(),fp);
    int s = this.x.length;
    for (int i=0;i<trace.x.length;i++){
      fx[s]=trace.getX(i);
      fy[s]=trace.getY(i);
      fm[s]=trace.getType(i);
      fp[s]=trace.getPosition(i);
      s+=1;
    }
    this.setX(fx);
    this.setY(fy);
    this.setTypes(fm);
    this.setPositions(fp);
    return this;    
  }
  
  /**
   * Reverses the data in order without changing the order of the original instance
   * @return FinTrace : a new FinTrace with data reversed in order
   */
  public FinTrace reverse(){
    FinTrace t = new FinTrace();
    t.x = this.copyarray(this.getX(),null,true);
    t.y = this.copyarray(this.getY(),null,true);
    t.mark_types = this.copyarray(this.getTypes(),null,true);
    t.mark_positions = this.copyarray(this.getPositions(),null,true);
    return t;
  }
  
  /**
   * Simple basic get/set public methods 
   */

  /**
   * Gets the X coordinate tracing points
   * @return double[] of X coordinate point values
   */
  public double[] getX() {
    return x;
  }

  /**
   * Gets the X coordinate tracing point at point "i"
   * @param i int: the array index of which point to return
   * @return double of X coordinate point value
   */
  public double getX(int i) {
    return x[i];
  }

  /**
   * Gets the Y coordinate tracing points
   * @return double[] of Y coordinate point values
   */
  public double[] getY() {
    return y;
  }
  
  /**
   * Gets the Y coordinate tracing point at point "i"
   * @param i int: the array index of which point to return
   * @return double of Y coordinate point value
   */
  public double getY(int i) {
    return y[i];
  }

  /**
   * Get an array of feature types (i.e. TIP, NOTCH, NICK etc.) at each coordinate point 
   * @return double[] of mark types
   */
  public double[] getTypes() {
    return mark_types;
  }

  /**
   * Get a feature type (i.e. TIP, NOTCH, NICK etc.) at a coordinate point by index "i"
   * @param i int : index of the point to return
   * @return double as the mark type
   */
  public double getType(int i) {
    return mark_types[i];
  }

  /**
   * Get the position values along the fin tracing
   * @return double[] of the position values
   */
  public double[] getPositions() {
    return mark_positions;
  }
  
  /**
   * Get a position at a coordinate point by index "i"
   * @param i int : index of the point to return
   * @return double as the position value
   */  
  public double getPosition(int i) {
    return mark_positions[i];
  }

  /**
   * Sets all the X coordinate values as a single double array
   * @param x double[] : Array of X coordinate locations
   */
  public void setX(double[] x) {
    this.x = x;
  }

  /**
   * Sets a single X coordinate value at index
   * @param index int : index of the value to set or replace
   * @param x double : X coordinate value to set or replace
   */
  public void setX(int index, double x) {
    this.x[index] = x;
  }
  
  /**
   * Sets all the Y coordinate values as a single double array
   * @param y double[] : Array of Y coordinate locations
   */
  public void setY(double[] y) {
    this.y = y;
  }
  
  /**
   * Sets a single Y coordinate value at index
   * @param index int : index of the value to set or replace
   * @param y double : Y coordinate value to set or replace
   */
  public void setY(int index, double y) {
    this.y[index] = y;
  }
  
  /**
   * Sets all the mark types values as a single double array
   * @param mark_types double[] : Array of mark types (one for each XY coordinate point)
   */
  public void setTypes(double[] mark_types){
    this.mark_types = mark_types;
  }

  /**
   * Sets a single mark type value at index
   * @param index int : index of the value to set or replace
   * @param d double : mark type value to set or replace
   */
  public void setType(int index, double d){
    this.mark_types[index] = d;
  }

  /**
   * Sets all the relative mark position values as a single double array
   * @param mark_positions double[] : Arry of positions values to set
   */
  public void setPositions(double[] mark_positions){
    this.mark_positions = mark_positions;
  }
  
  /**
   * Sets a single position value at index
   * @param index int : index of the value to set or replace 
   * @param mark_positions double : position value to set or replace
   */
  public void setPosition(int index, double mark_positions){
    this.mark_positions[index] = mark_positions;
  }  
  
  /**
   * Returns the numer of XY points in the tracing 
   * @return int as the size of the tracing
   */
  public int size(){
    return this.x.length;
  }
  
  /**
   * Returns true if the notch is open, false otherwise
   * @return boolean value if the notch is open or not
   */
  public boolean getNotchOpen(){
    return this.notch_open;
  }
  
  /**
   * Sets if the fine is a fluke and has an open notch or not
   * @param notch_open boolean: value if the notch is open or not
   */
  public void setNotchOpen(boolean notch_open){
    this.notch_open = notch_open;
  }
  
  /**
   * Returns true if the fin is curled, false if not
   * @return boolean value if the fin is curled or not
   */
  public boolean getCurled(){
    return this.curled;
  }
  
  /**
   * Setf it the fin is curled or not
   * @param curled boolean : value if the fin is curled or not
   */
  public void setCurled(boolean curled){
    this.curled = curled;
  }  
  
  /**
   * Gets the type of "tracing" type being entered for this dataset
   * @return int value of either COUNTOUR_TRACING or FEATURE_POINTS
   */
  public int getTraceType(){
    return this.trace_type;
  }
  
  /**
   * Sets the type of "tracing" type being entered for this dataset
   * @param ttype int: either COUNTOUR_TRACING or .FEATURE_POINTS
   */
  public void setTraceType(int ttype){
    if (ttype==this.COUNTOUR_TRACING || ttype==this.FEATURE_POINTS){
      this.trace_type=ttype;
    }
  }
  
  /**
   * Gets the transform string used to rotate, scale, translate and sheer an image relative to the tracing points
   * @return String of space and semicolon delinated numbers representing a 3x3 translation matrix. 
   */
  public String getTransform(){
    return this.transform;
  }
  
  /**
   * Sets the transform string used to rotate, scale, translate and sheer an image relative to the tracing points
   * @param stransform String: space and semicolon delinated numbers representing a 3x3 translation matrix. 
   */
  public void setTransform(String stransform){
    // This assumes the string is correctly formated. 
    // Should add checks to prove this is so, and return errors if not.
    this.transform = new String(stransform);
  }
  
} 

