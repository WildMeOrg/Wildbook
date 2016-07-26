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

import java.util.ArrayList;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.datanucleus.store.types.wrappers.backed.Collection;

/**
 * Flukes
 * <p/>
 * ArrayList extension container for many Fluke classes from a data source.  
 */
public class Flukes extends ArrayList<Fluke> implements java.io.Serializable {
    
  private static final long serialVersionUID = 6545935230490848417L;
  
  /**
   * TracePoint Constructor
   * <p/>
   * Comments: Basic constructor method.
   */
  public Flukes() {
     super();     
     ArrayList<Fluke> a = new ArrayList<>();
  }

  /**
   * TracePoints Constructor
   * <p/>
   * Second constructor method with Collection of TracePoint objects
   * @param c : Collection of TracePoint objects
   */
  public Flukes(Collection c) {
    super();
    this.addAll(c);
  }

  
} 

