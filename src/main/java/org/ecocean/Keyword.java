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

import java.util.Vector;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Keyword {

  //the primary key of the keyword
  private String indexname;

  //the visible descriptor of the keyword
  private String readableName;

  //a Vector of String relative paths to the photo file that the keyword applies to
  public Vector photos;

  // hackey! used to remove zombie Keywords. Probs not worth merging to master but we will need to do this cleanup
  // on Flukebook.
  private boolean isUsed=false;

  /**
   * empty constructor required by JDO Enhancer
   */
  public Keyword() {
  }



  //use this constructor for new keywords
  public Keyword(String readableName) {
    this.readableName = readableName;
    //photos = new Vector();
  }

  /*
  public void removeImageName(String imageFile) {
    for (int i = 0; i < photos.size(); i++) {
      String thisName = (String) photos.get(i);
      if (thisName.equals(imageFile)) {
        photos.remove(i);
        i--;
      }
    }
  }
*/
  public boolean getIsUsed() {return isUsed;}
  public void setIsUsed(boolean isUsed) {this.isUsed=isUsed;}

  public String getReadableName() {
    return readableName;
  }

  public void setReadableName(String name) {
    this.readableName = name;
  }

  public String getIndexname() {
    return indexname;
  }

  /*
  public void addImageName(String photoName) {
    if (!isMemberOf(photoName)) {
      photos.add(photoName);
    }
  }
*/
 
  public boolean isMemberOf(String photoName) {
    //boolean truth=false;
    for (int i = 0; i < photos.size(); i++) {
      String thisName = (String) photos.get(i);
      if (thisName.equals(photoName)) {
        return true;
      }
    }
    return false;
  }

  // convenience method for removing duplicate keywords
  public boolean isDuplicateOf(Keyword kw) {
    return (this.readableName.equals(kw.getReadableName()) && !this.indexname.equals(kw.getIndexname()));
  }


  /*
  public boolean isMemberOf(Encounter enc) {
    //boolean truth=false;
    Vector photos = enc.getAdditionalImageNames();
    int photoSize = photos.size();
    for (int i = 0; i < photoSize; i++) {
      String thisName = enc.getEncounterNumber() + "/" + (String) photos.get(i);
      if (isMemberOf(thisName)) {
        return true;
      }
    }
    return false;
  }
  */

  /*
  public Vector getMembers() {
    return photos;
  }
*/


    public String toString() {
        return new ToStringBuilder(this)
                .append(indexname)
                .append(readableName)
                .toString();
    }

}
