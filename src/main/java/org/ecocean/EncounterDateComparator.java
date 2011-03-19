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

package org.ecocean;

import java.util.Comparator;
import java.util.GregorianCalendar;

public class EncounterDateComparator implements Comparator {

  EncounterDateComparator() {
  }

  public int compare(Object a, Object b) {
    Encounter a_enc = (Encounter) a;
    Encounter b_enc = (Encounter) b;
    GregorianCalendar a1 = new GregorianCalendar(a_enc.getYear(), a_enc.getMonth(), a_enc.getDay());
    GregorianCalendar b1 = new GregorianCalendar(b_enc.getYear(), b_enc.getMonth(), b_enc.getDay());

    if (a1.getTimeInMillis() > b1.getTimeInMillis()) {
      return -1;
    } else if (a1.getTimeInMillis() < b1.getTimeInMillis()) {
      return 1;
    }
    return 0;

    /**
     if (a1.get(Calendar.YEAR)>b1.get(Calendar.YEAR)) {return -1;}
     if (a1.get(Calendar.YEAR)==b1.get(Calendar.YEAR)) {
     if (a1.get(Calendar.MONTH)>b1.get(Calendar.MONTH)) {return -1;}
     if(a1.get(Calendar.MONTH)==b1.get(Calendar.MONTH)) {
     if (a1.get(Calendar.DAY_OF_MONTH)>b1.get(Calendar.DAY_OF_MONTH)) {return -1;}
     if (a1.get(Calendar.DAY_OF_MONTH)<b1.get(Calendar.DAY_OF_MONTH)) {return 1;}
     return 0;
     }
     return 1;
     }
     return 1;
     */
  }


}