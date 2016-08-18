package org.ecocean.datacollection;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.IOException;


import org.ecocean.*;

import org.apache.commons.lang3.StringUtils;



// a DataSheet is a DataCollectionEvent attached to a set of DataPoints
// this way we can organize a set of measurements, counts,
// and observations that were taken by researchers in one
// time and place.
public class DataSheet extends DataCollectionEvent {

  private String id;

  private List<DataPoint> data;

  public DataSheet() {
  }

  public DataSheet(String id) {
    this.id = id;
    this.data = new ArrayList<DataPoint>();
  }
  public DataSheet(List<DataPoint> data) {
    this.id = Util.generateUUID();
    this.data = data;
  }

  public static DataSheet fromCommonConfig(String className, String context) throws IOException {
    List<String> dpNames = CommonConfiguration.getIndexedPropertyValues("datapoint",context);
    List<String> dpUnits = CommonConfiguration.getIndexedPropertyValues("datapointUnit",context);
    List<String> dpClasses = CommonConfiguration.getIndexedPropertyValues("datapointClass",context);
    List<String> dpTypes = CommonConfiguration.getIndexedPropertyValues("datapointType",context);

    if (dpNames.size()!=dpUnits.size() || dpNames.size()!= dpClasses.size() || dpNames.size() != dpTypes.size()){
      throw new IOException("datapoint, datapointUnit, datapointClass, and/or datapointType lists are unequal lengths in commonConfiguration.properties");
    }


    List<DataPoint> data = new ArrayList<DataPoint>();
    DataPoint dp;
    for (int i=0; i<dpNames.size(); i++) {
      if (!classIsInConfigClassList(className, dpClasses.get(i))) continue;

      dp = null;
      String dpType = dpTypes.get(i);
      String dpName = dpNames.get(i);
      String dpUnit = dpUnits.get(i);
      if (dpUnit==null || dpUnit.equals("") || dpUnit.equals("nounits")) dpUnit = null;
      if (dpType.equals("observation")) {
        dp = new Observation(dpName, (String) null);
      } else if (dpType.equals("count")) {
        dp = new Count(dpName, (Integer) null, dpUnit);
      } else if (dpType.equals("amount")) {
        dp = new Amount(dpName, (Double) null, dpUnit);
      }
      if (dp!=null) data.add(dp);

    }
    return new DataSheet(data);
  }
  private static boolean classIsInConfigClassList(String className, String classList) {
    List<String> classNames = Arrays.asList(className.split(","));
    return classNames.contains(className);
  }



  public void addData(DataPoint datom) {
    this.data.add(datom);
  }

  public List<DataPoint> getData() {
    return data;
  }

  public String getID() {
    return id;
  }

  public String toString() {
    return("DataSheet "+id+": ["+StringUtils.join(data, ", ")+ "]");

  }

}
