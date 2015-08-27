/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
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
 */

package org.ecocean.grid;

import java.util.Comparator;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.ecocean.neural.TrainNetwork;

import javax.servlet.http.HttpServletRequest;


public class FlukeMatchComparator implements Comparator {
  
  private HttpServletRequest request;
  
  public FlukeMatchComparator(HttpServletRequest request){this.request=request;}

  public int compare(Object a, Object b) {
    
    SummaryStatistics intersectionStats=GridManager.getIntersectionStats(request);
    SummaryStatistics dtwStats=GridManager.getDTWStats(request);
    SummaryStatistics proportionStats=GridManager.getProportionStats(request);
    SummaryStatistics i3sStats=GridManager.getI3SStats(request);
    
    double intersectionStdDev=0.05;
    if(request.getParameter("intersectionStdDev")!=null){intersectionStdDev=(new Double(request.getParameter("intersectionStdDev"))).doubleValue();}
    double dtwStdDev=0.41;
    if(request.getParameter("dtwStdDev")!=null){dtwStdDev=(new Double(request.getParameter("dtwStdDev"))).doubleValue();}
    double i3sStdDev=0.01;
    if(request.getParameter("i3sStdDev")!=null){i3sStdDev=(new Double(request.getParameter("i3sStdDev"))).doubleValue();}
    double proportionStdDev=0.01;
    if(request.getParameter("proportionStdDev")!=null){proportionStdDev=(new Double(request.getParameter("proportionStdDev"))).doubleValue();}
    double intersectHandicap=0;
    if(request.getParameter("intersectHandicap")!=null){intersectHandicap=(new Double(request.getParameter("intersectHandicap"))).doubleValue();}
    double dtwHandicap=0;
    if(request.getParameter("dtwHandicap")!=null){dtwHandicap=(new Double(request.getParameter("dtwHandicap"))).doubleValue();}
    double i3sHandicap=0;
    if(request.getParameter("i3sHandicap")!=null){i3sHandicap=(new Double(request.getParameter("i3sHandicap"))).doubleValue();}
    double proportionHandicap=0;
    if(request.getParameter("proportionHandicap")!=null){proportionHandicap=(new Double(request.getParameter("proportionHandicap"))).doubleValue();}

    
    MatchObject a1 = (MatchObject) a;
    MatchObject b1 = (MatchObject) b;

    double a1_adjustedValue=TrainNetwork.getOverallFlukeMatchScore(request, a1.getIntersectionCount(), a1.getLeftFastDTWResult().doubleValue(), a1.getI3SMatchValue(), new Double(a1.getProportionValue()),intersectionStats,dtwStats,i3sStats, proportionStats, intersectionStdDev,dtwStdDev,i3sStdDev,proportionStdDev,intersectHandicap, dtwHandicap,i3sHandicap,proportionHandicap);
    
    double b1_adjustedValue=TrainNetwork.getOverallFlukeMatchScore(request, b1.getIntersectionCount(), b1.getLeftFastDTWResult().doubleValue(), b1.getI3SMatchValue(), new Double(b1.getProportionValue()),intersectionStats,dtwStats,i3sStats, proportionStats, intersectionStdDev,dtwStdDev,i3sStdDev,proportionStdDev,intersectHandicap, dtwHandicap,i3sHandicap,proportionHandicap);
    

    if (a1_adjustedValue > b1_adjustedValue) {
      return -1;
    } else if (a1_adjustedValue == b1_adjustedValue) {
      
      //if a tie, sort on I3S score
      if(a1.getI3SMatchValue()<b1.getI3SMatchValue()){return -1;}
      else if(a1.getI3SMatchValue()<b1.getI3SMatchValue()){return 1;}
      
      return 0;
    
    
    } else {
      return 1;
    }
  }
}