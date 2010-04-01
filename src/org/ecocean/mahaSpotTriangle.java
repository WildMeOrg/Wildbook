package org.ecocean;

import java.awt.geom.Point2D;
import java.lang.Math;


public class mahaSpotTriangle implements java.io.Serializable{
	
static final long serialVersionUID = -5400879461028598731L;
//3 spots of the triangle
//public spot s1, s2, s3;
public double Dij, Dik, Djk, D12, D13, D23;
public double Dxl, Dyl, Dxs, Dys;
public double CrossProd, VarFact;
public spot v1, v2, v3;	
public spot v1_maha, v2_maha, v3_maha;	
public double logPerimeter;
public boolean clockwise;
public double ratioLong2Short, toleranceRatioLong2Short;
public double cosineAtVertex1, toleranceInCosineAtVertex1;
public double R, C, S2, tR2, tC2;


public double r2, r3;


	mahaSpotTriangle(spot i, spot j, spot k, double mahaDist_i, double mahaDist_j, double mahaDist_k, double epsilon, double newClusterCentroidX, double newClusterCentroidY, double stdDevX, double stdDevY) {
//mahaSpotTriangle(spot i, spot j, spot k, double epsilon, double newClusterCentroidX, double newClusterCentroidY, double stdDevX, double stdDevY) {	
		
		//calculate these distances based on the Mahalanobis distance
		//the angle between the spots is calculated with the cartesian coordinates
		//distances are calc'd using lengths of sides of triangle where lengths are Mahalanobis distances.
		
	
		double[] p0=new double[3];
		p0[0]=newClusterCentroidX;
		p0[1]=newClusterCentroidY;
		p0[2]=0;
		
		double[] pi=new double[3];
		pi[0]=i.getCentroidX();
		pi[1]=i.getCentroidY();
		pi[2]=0;
		
		double[] pj=new double[3];
		pj[0]=j.getCentroidX();
		pj[1]=j.getCentroidY();
		pj[2]=0;
		
		double[] pk=new double[3];
		pk[0]=k.getCentroidX();
		pk[1]=k.getCentroidY();
		pk[2]=0;

		//next, we need new point coordinates for i, j, and k
		
		double[] p0i=new double[3];
		p0i[0]=pi[0]-p0[0];
		p0i[1]=pi[1]-p0[1];
		p0i[2]=0;
		double thetai=Math.atan2(p0i[1],p0i[0]);
		//double i_mahaX=mahaDist_i*Math.cos(thetai);
		//double i_mahaY=mahaDist_i*Math.sin(thetai);
		//spot i_maha=new spot(0,(i_mahaX+newClusterCentroidX),(i_mahaY+newClusterCentroidY));
		double i_mahaX=pi[0]*stdDevY/stdDevX;
		double i_mahaY=pi[1];
		spot i_maha=new spot(0,i_mahaX,i_mahaY);
		
		double[] p0j=new double[3];
		p0j[0]=pj[0]-p0[0];
		p0j[1]=pj[1]-p0[1];
		p0j[2]=0;
		double thetaj=Math.atan2(p0j[1],p0j[0]);
		//double j_mahaX=mahaDist_j*Math.cos(thetaj);
		//double j_mahaY=mahaDist_j*Math.sin(thetaj);
		//spot j_maha=new spot(0,(j_mahaX+newClusterCentroidX),(j_mahaY+newClusterCentroidY));
		double j_mahaX=pj[0]*stdDevY/stdDevX;
		double j_mahaY=pj[1];
		spot j_maha=new spot(0,j_mahaX,j_mahaY);
		
		
		double[] p0k=new double[3];
		p0k[0]=pk[0]-p0[0];
		p0k[1]=pk[1]-p0[1];
		p0k[2]=0;
		double thetak=Math.atan2(p0k[1],p0k[0]);
		//double k_mahaX=mahaDist_k*Math.cos(thetak);
		//double k_mahaY=mahaDist_k*Math.sin(thetak);
		//spot k_maha=new spot(0,(k_mahaX+newClusterCentroidX),(k_mahaY+newClusterCentroidY));
		double k_mahaX=pk[0]*stdDevY/stdDevX;
		double k_mahaY=pk[1];
		spot k_maha=new spot(0,k_mahaX,k_mahaY);
		
		
		//old way now
		this.Dij=Point2D.distance(i_maha.getCentroidX(), i_maha.getCentroidY(), j_maha.getCentroidX(), j_maha.getCentroidY());
		this.Dik=Point2D.distance(i_maha.getCentroidX(), i_maha.getCentroidY(), k_maha.getCentroidX(), k_maha.getCentroidY());
		this.Djk=Point2D.distance(j_maha.getCentroidX(), j_maha.getCentroidY(), k_maha.getCentroidX(), k_maha.getCentroidY());
		
		
		// We now know the lengths of all three sides in Mahalanobis distances, so
		// can figure out short, middle, long -- will get
		// six cases.
		if ((Dik >= Djk) && (Dik >= Dij)) {
		    v2=j;
		    v2_maha=j_maha;
		    D13=Dik;
		    if (Djk >= Dij) {
				// ik = long, jk = middle, ij = short
				Dxl=i_maha.getCentroidX()-k_maha.getCentroidX();
				Dyl=i_maha.getCentroidY()-k_maha.getCentroidY();
				Dxs=j_maha.getCentroidX()-i_maha.getCentroidX();
				Dys=j_maha.getCentroidY()-i_maha.getCentroidY();
				D12=Dij;
				D23=Djk;
				v1=i;
				v1_maha=i_maha;
				v3=k;
				v3_maha=k_maha;
		    } else {
				// ik = long, ij = middle, jk = short
				Dxl=-i_maha.getCentroidX()+k_maha.getCentroidX();
				Dyl=-i_maha.getCentroidY()+k_maha.getCentroidY();
				Dxs=-k_maha.getCentroidX()+j_maha.getCentroidX();
				Dys=-k_maha.getCentroidY()+j_maha.getCentroidY();
				D12=Djk;
				D23=Dij;
				v1=k;
				v1_maha=k_maha;
				v3=i;
				v3_maha=i_maha;
		    }
		} else if ((Djk > Dik) && (Djk >= Dij)) {
		    v2=i;
		    v2_maha=i_maha;
		    D13=Djk;
		    if (Dik >= Dij) {
			// jk = long, ik = middle, ij = short
			Dxl=-k_maha.getCentroidX()+j_maha.getCentroidX();
			Dyl=-k_maha.getCentroidY()+j_maha.getCentroidY();
			Dxs=-j_maha.getCentroidX()+i_maha.getCentroidX();
			Dys=-j_maha.getCentroidY()+i_maha.getCentroidY();
			D12=Dij;
			D23=Dik;
			v1=j;
			v1_maha=j_maha;
			v3=k;
			v3_maha=k_maha;
		    } else {
			// jk = long, ij = middle, ik = short
			Dxl=k_maha.getCentroidX()-j_maha.getCentroidX();
			Dyl=k_maha.getCentroidY()-j_maha.getCentroidY();
			Dxs=i_maha.getCentroidX()-k_maha.getCentroidX();
			Dys=i_maha.getCentroidY()-k_maha.getCentroidY();
			D12=Dik;
			D23=Dij;
			v1=k;
			v1_maha=k_maha;
			v3=j;
			v3_maha=j_maha;
		    }
		} else {
		    v2=k;
		    v2_maha=k_maha;
		    D13=Dij;
		    if (Dik >= Djk) {
			// ij = long, ik = middle, jk = short
			Dxl=j_maha.getCentroidX()-i_maha.getCentroidX();
			Dyl=j_maha.getCentroidY()-i_maha.getCentroidY();
			Dxs=k_maha.getCentroidX()-j_maha.getCentroidX();
			Dys=k_maha.getCentroidY()-j_maha.getCentroidY();
			D12=Djk;
			D23=Dik;
			v1=j;
			v1_maha=j_maha;
			v3=i;
			v3_maha=i_maha;
		    } else {
			// ij = long, jk = middle, ik = short
			Dxl=-j_maha.getCentroidX()+i_maha.getCentroidX();
			Dyl=-j_maha.getCentroidY()+i_maha.getCentroidY();
			Dxs=-i_maha.getCentroidX()+k_maha.getCentroidX();
			Dys=-i_maha.getCentroidY()+k_maha.getCentroidY();
			D12=Dik;
			D23=Djk;
			v1=i;
			v1_maha=i_maha;
			v3=j;
			v3_maha=j_maha;
		    }
		}
		r3=D13;
		r2=D12;
		R=r3/r2;
		C=-(Dxl*Dxs+Dyl*Dys)/(r2*r3);
		S2=1.0-(C*C);
		VarFact=1.0/(r3*r3)-C/(r3*r2)+1.0/(r2*r2);
		tR2=R*R*epsilon*epsilon*2*VarFact;
		tC2=2*S2*epsilon*epsilon*VarFact+3*C*C*Math.pow(epsilon, 4)*VarFact*VarFact;
		logPerimeter=Math.log(D12+D13+D23);
		CrossProd = Dxs*Dyl-Dxl*Dys;
		if (CrossProd <= 0.0) {
		    clockwise=false;
		} else {
		    clockwise=true;
		}
	}

	public boolean containsSpot(spot x) {
	
			if((x.getCentroidX()==v1.getCentroidX())&&(x.getCentroidY()==v1.getCentroidY())) {return true;}
			else if((x.getCentroidX()==v2.getCentroidX())&&(x.getCentroidY()==v2.getCentroidY())) {return true;}
			else if((x.getCentroidX()==v3.getCentroidX())&&(x.getCentroidY()==v3.getCentroidY())) {return true;}
			return false;
	}
	
	public spot getVertex(int x) {
	
		if(x==1) {return v1;}
		else if(x==2) {return v2;}
		else {return v3;}
		
	}
	
	public double getMyVertexOneRotationInRadians() {
		double x1=v1_maha.getCentroidX();
		double y1=v1_maha.getCentroidY();
		double x2=v2_maha.getCentroidX();
		double y2=v2_maha.getCentroidY();
		double x3=v3_maha.getCentroidX();
		double y3=v3_maha.getCentroidY();

		
		//now calculate the centroid
		double centroidX=(x1+x2+x3)/3; 
		double centroidY=(y1+y2+y3)/3;
		
		//let's use vertex one to measure angle of rotation
		//first we must normalize vertex one with repect to the center. in other words, rotation
		//of the triangle is about the centroid at (0,0)
		
		x1=x1-centroidX;
		y1=y1-centroidY;
		double theta;
		
		theta=Math.atan2(y1, x1);
		return theta;
	}
	
	public double getTriangleCentroidX() {
		double x1=v1_maha.getCentroidX();
		//double y1=v1.getCentroidY();
		double x2=v2_maha.getCentroidX();
		//double y2=v2.getCentroidY();
		double x3=v3_maha.getCentroidX();
		//double y3=v3.getCentroidY();
		//now calculate the centroid
		return ((x1+x2+x3)/3); 

		}
		
	public double getTriangleCentroidY() {
		//double x1=v1.getCentroidX();
		double y1=v1_maha.getCentroidY();
		//double x2=v2.getCentroidX();
		double y2=v2_maha.getCentroidY();
		//double x3=v3.getCentroidX();
		double y3=v3_maha.getCentroidY();
		//now calculate the centroid
		return ((y1+y2+y3)/3); 

		}
	
}
