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
import java.util.Iterator;
import java.util.StringTokenizer;

public class ThumbnailKeywordComparator implements Comparator {

  public ThumbnailKeywordComparator() {
  }

  public int compare(Object a, Object b) {

    //System.out.println("\n\nStarting new comparison!");

    String a_enc = (String) a;
    String b_enc = (String) b;

    StringTokenizer stzr = new StringTokenizer(a_enc, "BREAK");
    String thumbLink = stzr.nextToken();
    String encNum = stzr.nextToken();
    int fileNamePos = a_enc.lastIndexOf("BREAK") + 5;
    String fileName = a_enc.substring(fileNamePos);
    a_enc = encNum + "/" + fileName;
    //System.out.println("Image a is: "+a_enc);

    StringTokenizer stzr2 = new StringTokenizer(b_enc, "BREAK");
    String thumbLink2 = stzr2.nextToken();
    String encNum2 = stzr2.nextToken();
    int fileNamePos2 = b_enc.lastIndexOf("BREAK") + 5;
    String fileName2 = b_enc.substring(fileNamePos2);
    b_enc = encNum2 + "/" + fileName2;
    //System.out.println("Image b is: "+b_enc);

    String aKeyword = "ZZZZZZZZZZ";
    String bKeyword = "ZZZZZZZZZZ";

    Shepherd myShepherd = new Shepherd();
    myShepherd.beginDBTransaction();
    Iterator keywords = myShepherd.getAllKeywords();
    //System.out.println("Starting to iterate keywords");
    while (keywords.hasNext()) {
      Keyword keyword = (Keyword) keywords.next();
      String readableName = keyword.getReadableName();
      //System.out.println("     Looking at keyword: "+readableName);

      //a keyword
      if (keyword.isMemberOf(a_enc)) {
        // System.out.println("          Image a is a meber of this keyword!");
        if (aKeyword.equals("ZZZZZZZZZZ")) {
          aKeyword = readableName;
        } else {
          if (aKeyword.compareTo(readableName) > 0) {
            aKeyword = readableName;
          }
        }
        //System.out.println("          aKeyword is now: "+aKeyword);
      }

      //b keyword
      if (keyword.isMemberOf(b_enc)) {
        //System.out.println("          Image b is a meber of this keyword!");
        if (bKeyword.equals("ZZZZZZZZZZ")) {
          bKeyword = readableName;
        } else {
          if (bKeyword.compareTo(readableName) > 0) {
            bKeyword = readableName;
          }
        }
        //System.out.println("          bKeyword is now: "+bKeyword);
      }


    }

    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    //System.out.println("aKeyword is now: "+aKeyword);
    //System.out.println("bKeyword is now: "+bKeyword);
    //System.out.println("Attempting a comparison of a to b: "+aKeyword.compareTo(bKeyword));
    return aKeyword.compareTo(bKeyword);

  }


}