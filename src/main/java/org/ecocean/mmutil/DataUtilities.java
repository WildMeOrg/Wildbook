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

package org.ecocean.mmutil;

import java.util.Calendar;
import org.ecocean.Util;

/**
 * Class providing useful generic data-oriented utility methods.
 *
 * @author Giles Winstanley
 */
public class DataUtilities {

  private DataUtilities() {}

  /**
   * Creates a unique ID string for encounters.
   * Unique to millisecond precision.
   * TODO: Further improve UID generation algorithm (UUID class?)
   * @return unique ID string
   */
  public static String createUniqueId() {
    StringBuilder sb = new StringBuilder();
    Calendar cal = Calendar.getInstance();
    sb.append(String.format("%02d", cal.get(Calendar.YEAR)- 2000));
    sb.append(String.format("%02d", cal.get(Calendar.MONTH) + 1));
    sb.append(String.format("%02d", cal.get(Calendar.DAY_OF_MONTH)));
    sb.append(String.format("%02d", cal.get(Calendar.HOUR_OF_DAY)));
    sb.append(String.format("%02d", cal.get(Calendar.MINUTE)));
    sb.append(String.format("%02d", cal.get(Calendar.SECOND)));
    sb.append(String.format("%03d", cal.get(Calendar.MILLISECOND)));
    return sb.toString();
  }

  /**
   * Creates a unique ID string for encounters.
   * @return unique ID string
   */
  public static String createUniqueEncounterId() {
    return Util.generateUUID();
  }

  /**
   * Creates a unique ID string for adoptions.
   * @return unique ID string
   */
  public static String createUniqueAdoptionId() {
    return "adpt" + createUniqueId();
  }
}
