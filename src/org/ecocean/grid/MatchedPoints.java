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

/**
 * ToDO: comment here
 */

import org.ecocean.Spot;

import java.util.ArrayList;

public class MatchedPoints extends ArrayList {

  public MatchedPoints() {
    super();
  }

  public int hasMatchedPair(Spot A, Spot B) {
    if (size() > 0) {
      for (int i = 0; i < size(); i++) {
        if (
          (((VertexPointMatch) get(i)).newX == A.getCentroidX()) &&
            (((VertexPointMatch) get(i)).newY == A.getCentroidY()) &&
            (((VertexPointMatch) get(i)).oldX == B.getCentroidX()) &&
            (((VertexPointMatch) get(i)).oldY == B.getCentroidY())
          )

        {
          return i;
        }
      }
    }
    return -1;

  }

}