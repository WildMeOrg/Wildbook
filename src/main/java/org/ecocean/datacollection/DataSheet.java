package org.ecocean.datacollection;

import java.util.List;
import java.util.ListIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.IOException;

import org.ecocean.*;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import javax.servlet.http.HttpServletRequest;


// a DataSheet is a DataCollectionEvent attached to a set of DataPoints
// this way we can organize a set of measurements, counts,
// and observations that were taken by researchers in one
// time and place.
public class DataSheet extends DataCollectionEvent {

  private String id;
  private String name; // informal e.g. "emergence data collection sheet"

  // top level fields get special treatment in e.g. UI. DataPoints are more generic
  private Double latitude;
  private Double longitude;
  private DateTime dateTime;
  private Long dateInMilliseconds; // redundant fields because dateInMilliseconds is easily queriable by JDO

  private List<DataPoint> data;

  public DataSheet() {
  }

  public DataSheet(String id) {
    this.setID(id);
    this.data = new ArrayList<DataPoint>();
  }

  // uses DataCollectionEvent initializer to init those inherited fields
  public DataSheet(HttpServletRequest request) {
    super((String)null, "DataSheet", request);
    System.out.println("DataSheet.java: initializing a DataSheet from request");
    System.out.println("DataSheet.java: getDataCollectionEventID = "+this.getDataCollectionEventID());
    //this.setID(this.getDataCollectionEventID());
    ///this.setID(Util.generateUUID());
    this.data = new ArrayList<DataPoint>();
    System.out.println("DataSheet.java: done initializing DataSheet "+this.getID()+" with super.DataCollectionEventID = "+this.getDataCollectionEventID());

  }

  public DataSheet(List<DataPoint> data) {
    this.setID(Util.generateUUID());
    this.data = data;
  }

  public static DataSheet fromCommonConfig(String className, String context) throws IOException {
    return new DataSheet(datapointsFromCommonConfig(className, context));
  }

  public boolean addConfigDataPoints(String className, String context) throws IOException {
    int initialSize = this.size();
    this.data.addAll(datapointsFromCommonConfig(className, context));
    return (this.size() > initialSize);
  }

  public static List<DataPoint> datapointsFromCommonConfig(String className, String context) throws IOException {
    System.out.println("DataSheet.datapointsFromCommonConfig called on class "+className+" in context "+context);
    List<String> dpNames = CommonConfiguration.getIndexedPropertyValues("datapoint",context);
    List<String> dpUnits = CommonConfiguration.getIndexedPropertyValues("datapointUnits",context);
    List<String> dpClasses = CommonConfiguration.getIndexedPropertyValues("datapointClasses",context);
    List<String> dpTypes = CommonConfiguration.getIndexedPropertyValues("datapointType",context);

    if (dpNames.size()!=dpUnits.size() || dpNames.size()!= dpClasses.size() || dpNames.size() != dpTypes.size()){
      System.out.println("dpNames.size(): "+dpNames.size());
      System.out.println("dpUnits.size(): "+dpUnits.size());
      System.out.println("dpClasses.size(): "+dpClasses.size());
      System.out.println("dpTypes.size(): "+dpTypes.size());
      throw new IOException("datapoint, datapointUnit, datapointClass, and/or datapointType lists are unequal lengths in commonConfiguration.properties");
    }

    System.out.println("Number of datapoint config files: "+dpNames.size());

    List<DataPoint> data = new ArrayList<DataPoint>();
    DataPoint dp;
    for (int i=0; i<dpNames.size(); i++) {
      String dpClass = dpClasses.get(i);
      System.out.println("    dp "+i+" classes = "+dpClass);
      if (!classIsInConfigClassList(className, dpClass)) continue;
      dp = null;
      String dpType = dpTypes.get(i);
      String dpName = dpNames.get(i);
      String dpUnit = dpUnits.get(i);
      System.out.println("          (type,name,unit) = ("+dpType+", "+dpName+", "+dpUnit+")");
      if (dpUnit==null || dpUnit.equals("") || dpUnit.equals("nounits")) dpUnit = null;
      if (dpType.equals("observation")) {
        dp = new Observation(dpName, (String) null);
      } else if (dpType.equals("count")) {
        dp = new Count(dpName, (Integer) null, dpUnit);
      } else if (dpType.equals("amount")) {
        dp = new Amount(dpName, (Double) null, dpUnit);
      } else if (dpType.equals("longcount")) {
        dp = new LongCount(dpName, (Long) null, dpUnit);
      } else if (dpType.equals("instant")) {
        dp = new Instant(dpName, (DateTime) null, dpUnit);
      } else if (dpType.equals("check")) {
        dp = new Check(dpName, (Boolean) null, dpUnit);
      }
      if (dp!=null) {
        dp.setConfigNo(i);
        data.add(dp);
        if (dp.isCategorical(context)) {
          System.out.println("DataPoint "+dp.getName()+" is categorical!");
          System.out.println("           its possible values are "+StringUtils.join(dp.getCategoriesAsStrings(context), ", "));
        }
        if (dp.isSequential(context)) {
          System.out.println("SEQUENCE: It's sequential, all right!");
          dp.setNumber(0);
        }
      }
    }
    return data;
  }


  private static boolean classIsInConfigClassList(String className, String classList) {
    List<String> classNames = Arrays.asList(classList.split(","));
    boolean ans = classNames.contains(className);
    return ans;
  }

  public int size() {
    return data.size();
  }

  public void add(DataPoint datom) {
    this.data.add(datom);
  }

  // copies all the datapoints from one datasheet into this one
  public void copyFrom(DataSheet ds) {
    for (DataPoint dp : ds.getData()) {

    }
  }

  public int countPoints(String namePrefix) {
    int count = 0;
    for (DataPoint dp: data) {
      if (dp.getName().indexOf(namePrefix)==0) count++;
    }
    return count;
  }


  public List<DataPoint> getData() {
    return data;
  }

  public DataPoint get(int i) {
    return data.get(i);
  }

  private void setID(String id) {
    this.DataCollectionEventID = id;
  }
  public String getID() {
    return this.getDataCollectionEventID();
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public DateTime getDateTime() {
    return dateTime;
  }
  public void setDateTime(DateTime dt) {
    this.dateTime = dt;
    if (dt!=null) this.dateInMilliseconds = dt.getMillis();
  }
  public Long getDateInMilliseconds() {
    return dateInMilliseconds;
  }
  public void setDateInMilliseconds(Long millis) {
    this.dateInMilliseconds = millis;
    if (millis != null) this.dateTime = new DateTime(millis);
  }


  public String toString() {
    if (data==null) return ("DataSheet "+id+": null data");
    return("DataSheet "+id+": ["+StringUtils.join(data, ", ")+ "]");

  }
  public String toLabeledString() {
    if (data==null) return ("DataSheet "+id+": null data");
    List<String> labeledNames = new ArrayList<String>();
    for (DataPoint dp: data) {
      labeledNames.add(dp.toLabeledString());
    }
    return("DataSheet "+id+": ["+StringUtils.join(labeledNames, ", ")+ "]");
  }

  public int getLastNumber(String dataName) {

    for (ListIterator revData = data.listIterator(data.size()); revData.hasPrevious();) {
      DataPoint dp = (DataPoint) revData.previous();

      int nameIndex = dp.getName().toLowerCase().indexOf(dataName.toLowerCase());
      if (nameIndex>=0) return nameIndex;

    }

    return -1;


    /*
    DataPoint lastDP = this.get(this.size()-1);
    String lastName = lastDP.getName();
    System.out.println("   lastName = "+lastName);
    System.out.println("   indexOfEgg = " + lastName.toLowerCase().indexOf("egg"));
    System.out.println("   indexOfHam = " + lastName.toLowerCase().indexOf("ham"));
    boolean lastDPAnEgg = (lastName.toLowerCase().indexOf("egg") > -1);
    if (!lastDPAnEgg) return 0;
    String intFromLastName = lastName.replaceAll("[^-?0-9]+", "");
    System.out.println("   intFromLastName = "+intFromLastName);
    return (Integer.parseInt(intFromLastName) + 1);*/
  }

  public String findUnitsForName(String dpName) {
    for (DataPoint dp : data) {
      if (dp.getName().equals(dpName)) {
        return dp.getUnits();
      }
    }
    return null;
  }




}
