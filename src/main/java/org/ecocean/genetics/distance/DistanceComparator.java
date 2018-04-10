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

package org.ecocean.genetics.distance;

import java.util.*;

public class DistanceComparator implements Comparator {
  

  
  public DistanceComparator() {}

  public int compare(Object a, Object b) {
    Map.Entry a_enc = (Map.Entry) a;
    Map.Entry b_enc = (Map.Entry) b;
    
    double ad=(new Double((String)a_enc.getValue())).doubleValue();
    double bd=(new Double((String)b_enc.getValue())).doubleValue();

     if (ad < bd) {
        return -1;
      } else if (ad > bd) {
        return 1;
      }
      return 0;
      


  }


}