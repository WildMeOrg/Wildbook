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

import java.util.Set;
import java.util.HashSet;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class LabeledKeyword extends Keyword{

  // ex. values are "Quality", "Distinctiveness", "Viewpoint"
  private String label;

  // just a handy list of standard label values. Might make a .properties later
  public static final Set<String> LABELS;
  static {
      Set<String> labs = new HashSet<String>();
      labs.add("feature");
      labs.add("flukeType");
      labs.add("quality");
      LABELS = labs;
  }

  // Goes between label and value when displaying a LabeledKeyword
  public static final String LABEL_SEPERATOR = ": ";

  // empty constructor required by JDO Enhancer
  public LabeledKeyword() {
  }

  //use this constructor for new keywords
  public LabeledKeyword(String label, String readableName) {
    this.label = label;
    this.readableName = readableName;
  }

  public String getDisplayName() {
    return (label+LABEL_SEPERATOR+readableName);
  }

  public void setLabel(String label) {
    this.label = label;
  }
  public String getLabel() {
    return this.label;
  }

  public void setValue(String value) {
    setReadableName(value);
  }
  public String getValue() {
    return getReadableName();
  }


  public String toString() {
    return new ToStringBuilder(this)
            .append("(labeled keyword) ")
            .append(indexname)
            .append(" label: "+label)
            .append(" value: "+readableName)
            .append(" displayName: "+getDisplayName())
            .toString();
  }


}
