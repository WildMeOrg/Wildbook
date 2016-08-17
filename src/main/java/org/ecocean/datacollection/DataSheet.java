package org.ecocean.datacollection;

import java.util.List;
import java.util.ArrayList;
import org.ecocean.*;


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


  public void addData(DataPoint datom) {
    this.data.add(datom);
  }

  public List<DataPoint> getData() {
    return data;
  }

  public String getID() {
    return id;
  }

}
