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


public class NewI3SMatchComparator implements Comparator {

  public int compare(Object a, Object b) {
    MatchObject a1 = (MatchObject) a;
    MatchObject b1 = (MatchObject) b;

    double a1_adjustedValue = a1.getI3SMatchValue();
    double b1_adjustedValue = b1.getI3SMatchValue();

    if (a1_adjustedValue < b1_adjustedValue) {
      return -1;
    } else if (a1_adjustedValue == b1_adjustedValue) {
      return 0;
    } else {
      return 1;
    }


  }


}

