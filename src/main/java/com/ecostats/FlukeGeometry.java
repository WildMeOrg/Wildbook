package com.ecostats;

/*
 * Does image rotation. TODO 
 */

public class FlukeGeometry {

  public FlukeGeometry() {
    // TODO 
  }

  /*
   * 
  public void flnogeom(ArrayList tracing, TracePoint[] line_segment, int[] curled_notch_info){
    
    ArrayRealVector mark_types_newfluke = new ArrayRealVector();
    ArrayRealVector newfluke_sdist = new ArrayRealVector(); // sdist = standardized distances
    
    int line_length=tracing.size();
    //w is fluke width squared
    double w=sqrt((tracing[0].x-tracing[line_length].x)^2+(tracing[0].y-tracing[line_length].y)^2);
    //xmeet,y meet are where sides meet    
    double xmeet=((tracing[0].x*line_segment[1].y-tracing[0].y*line_segment_newfluke[1])*(tracing[line_length].x-line_segment[2].x)-(tracing[line_length].x*line_segment_y(2)-tracing[line_length].y*line_segment[2].x)*(tracing[0].x-line_segment_newfluke[1]))/((tracing[0].x-line_segment_newfluke[1])*(tracing[line_length].y-line_segment_y(2))-(tracing[line_length].x-line_segment[2].x)*(tracing[0].y-line_segment[1].y));
    double ymeet=((tracing[0].y*line_segment_newfluke[1]-tracing[0].x*line_segment[1].y)*(tracing[line_length].y-line_segment_y(2))-(tracing[line_length].y*line_segment[2].x-tracing[line_length].x*line_segment_y(2))*(tracing[0].y-line_segment[1].y))/((tracing[0].y-line_segment[1].y)*(tracing[line_length].x-line_segment[2].x)-(tracing[line_length].y-line_segment_y(2))*(tracing[0].x-line_segment_newfluke[1]));
    //ad1,ad2 length of sides
    double ad1=(tracing[0].x-xmeet)^2+(tracing[0].y-ymeet)^2;
    double ad2=(tracing[line_length].x-xmeet)^2+(tracing[line_length].y-ymeet)^2;
    //d is offset of notch from perp. above meeting of sides
    double d=(ad1-ad2)/(2*w*w);
    ad1=Math.sqrt(ad1);
    ad2=Math.sqrt(ad2);
    //fluke_height is height of fluke
    double fluke_height=Math.sqrt((ad1+ad2+w)*(ad1+ad2-w)*(ad1+w-ad2)*(ad2+w-ad1))/(2*w*w);
    fluke_height=fluke_height*fluke_height/(this.ALPHAS*this.ALPHAS);
    d=d*d/(this.ALPHAS*this.ALPHAS);
    //image_twist is theta in coordinate transformation functions
    double image_twist=(1+fluke_height+d-sqrt((1+fluke_height+d)^2-4*fluke_height))/(2*fluke_height);
    //image_tilt is psi in coordinate transformation functions
    double image_tilt=image_twist*fluke_height;
    image_twist=Math.atan(Math.sqrt((1-image_twist)/image_twist))*180/Math.PI;
    image_tilt=Math.atan(Math.sqrt((1-image_tilt)/image_tilt))*180/Math.PI;
    
    double[] qualinfx={image_twist, image_tilt, this.RESOLUTION};
    
    //figures out position along fluke for a notch (finds all occurances where point_types is zero)
    int notch=this.find(points_type,0);
    
    //
    newfluke_sdist.append(this.tracingPositions(leftfluke.x,leftfluke.y,0));
    newfluke_sdist.append(this.tracingPositions(rightfluke.x,rightfluke.y,0.5));
    
    // get the type list of each point for each fluke and merger them
    RealVector v = leftfluke.typesVector();
    mark_types_newfluke.append(v.getSubVector(1,v.getDimension()-1));
    v = this.reverseVector(rightfluke.typesVector());
    mark_types_newfluke.append(v.getSubVector(1,v.getDimension()-1));
    
    //
    // ptx = value code of fluke based on:
    // curled_notch_info: 0 or 1 vector of right/left fluke curled, notch open closed (since a notch can not be opened AND closed, sum(curled_notch_info) can never be more than 3).
    ptx=sum(vvl(mark_types_newfluke))+this.HALF_VALUE*sum(curled_notch_info);

  }

*/
}
