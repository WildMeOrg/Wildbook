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
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import javax.servlet.http.HttpServletRequest;

public class LabeledKeyword extends Keyword{

  // ex. values are "Quality", "Distinctiveness", "Viewpoint"
  private String label;

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

  // this returns a map from label to values so we can display a label editor to the user
  public static Map<String,List<String>> labelUIMap(Shepherd myShepherd, HttpServletRequest request) {
    // should we check if the user uses custom properties? I think not--- if we can't find a map that way we'll try the db
    return labelUIMapFromProperties(myShepherd, request);
  }

  public static Map<String,List<String>> labelUIMap(HttpServletRequest request) {
    try {
      Shepherd readOnlyShep = Shepherd.newActiveShepherd(request, "labelUIMap");
      Map<String,List<String>> ans = labelUIMap(readOnlyShep, request);
      readOnlyShep.rollbackAndClose();
      return ans;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public static Map<String,List<String>> labelUIMapFromProperties(Shepherd myShepherd, HttpServletRequest request) {
    Map<String,List<String>> propLabels = new LinkedHashMap<String,List<String>>(); //linkedHashMap to preserve order
    List<String> labels = CommonConfiguration.getIndexedPropertyValues("kwLabel", request);
    if (!Util.isEmpty(labels)) {
      System.out.println(" got labels in labelUIMapFromProperties : "+labels.size());
      for (String label: labels) {
        List<String> values = CommonConfiguration.getIndexedPropertyValues(label, request);
        if (!Util.isEmpty(values)) propLabels.put(label, values);
      }
    }
    
    Map<String,List<String>> dbLabels = labelUIMapFromDB(myShepherd, request);
    System.out.println(" got labels in labelUIMapFromDB : "+dbLabels.size());

    for (String key : propLabels.keySet()) {
      if (!dbLabels.containsKey(key)) {
        dbLabels.put(key, propLabels.get(key));
        // new key to the db, add it and all found values the return and persist
        for (String eachVal : propLabels.get(key)) {
          myShepherd.getOrCreateLabeledKeyword(key, eachVal, true);
        }
      }
      
      //adding new values for a key from props to the db if key already exists in db, updating return values for key also
      if (dbLabels.containsKey(key)&&propLabels.containsKey(key)&&!dbLabels.get(key).containsAll(propLabels.get(key))) {
        List<String> tempVals = dbLabels.get(key);
        for (String value : propLabels.get(key)) {
          if (!tempVals.contains(value)) {
            tempVals.add(value);
            dbLabels.get(key).add(value);
            myShepherd.getOrCreateLabeledKeyword(key, value, true);
          }
        }
      }
    }
    return dbLabels;
  }

  public static Map<String,List<String>> labelUIMapFromDB(Shepherd myShepherd, HttpServletRequest request) {
    myShepherd.beginDBTransaction();
    List<LabeledKeyword> lkws = myShepherd.getAllLabeledKeywords();

    Map<String, Set<String>> labelsToValues = new HashMap<String, Set<String>>();
    for (LabeledKeyword lkw: lkws) {
      Util.addToMultimap(labelsToValues, lkw.getLabel(), lkw.getValue());
    }
    myShepherd.rollbackDBTransaction();
    // now sort them for returning
    Map<String,List<String>> sorted = new LinkedHashMap<String,List<String>>(); //linkedHashMap to preserve order
    for (String label: Util.asSortedList(labelsToValues.keySet())) { // sort the labels
      sorted.put(label, Util.asSortedList(labelsToValues.get(label))); // sort the values
    }
    return sorted;
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

  @Override
  public boolean isLabeledKeyword() {
    return true;
  }

}
