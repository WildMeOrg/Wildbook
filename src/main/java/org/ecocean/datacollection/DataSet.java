package org.ecocean.datacollection;

import javax.servlet.http.HttpServletRequest;
import org.joda.time.DateTime;

import java.util.List;
import java.util.ArrayList;


// a time- and location-stamped collection of DataCollectionEvents

public class DataSet {

  private Double latitude;
  private Double longitude;
  private DateTime datetime;
  List<DataCollectionEvent> data;

  public DataSet() {
  }

  public DataSet(Double lat, Double lon, DateTime dt) {
    latitude=lat;
    longitude=lon;
    datetime=dt;
  }

  public DataSet(List<DataCollectionEvent> data, Double lat, Double lon, DateTime dt) {
    this.data = data;
    latitude=lat;
    longitude=lon;
    datetime=dt;
  }

  public List<DataCollectionEvent> getData() {
    return data;
  }

  public void add(DataCollectionEvent dp) {
    data.add(dp);
  }


  public void setLatitude(Double lat) {
    latitude=lat;
  }
  public Double getLatitude() {
    return latitude;
  }
  public void setLongitude(Double lon) {
    longitude=lon;
  }
  public Double getLongitude() {
    return longitude;
  }
  public void setDatetime(DateTime dt) {
    datetime=dt;
  }
  public DateTime getDatetime() {
    return datetime;
  }

}
