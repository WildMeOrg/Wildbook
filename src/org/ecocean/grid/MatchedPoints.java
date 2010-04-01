package org.ecocean.grid;

/**
 * ToDO: comment here
 */
import java.util.ArrayList;

import org.ecocean.Spot;

public class MatchedPoints extends ArrayList {

	public MatchedPoints() {super();}	
	
	public int hasMatchedPair(Spot A, Spot B) {
		if(size()>0) {
			for(int i=0;i<size();i++) {
				if(
					(((VertexPointMatch)get(i)).newX==A.getCentroidX()) &&
					(((VertexPointMatch)get(i)).newY==A.getCentroidY()) &&
					(((VertexPointMatch)get(i)).oldX==B.getCentroidX()) &&
					(((VertexPointMatch)get(i)).oldY==B.getCentroidY())
					) 

					{return i;}
			}
		} 
		return -1;
		
	}
	
}