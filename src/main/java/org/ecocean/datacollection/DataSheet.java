package org.ecocean.datacollection;

import java.util.List;
import java.util.ArrayList;
import org.ecocean.*;


// a DataSheet is a DataPoint attached to a set of DataAtoms
// this way we can organize a set of measurements, counts,
// and observations that were taken by researchers in one
// time and place.
public class DataSheet extends DataPoint {

  private String id;

  private List<DataAtom> data;

  public DataSheet() {
  }

  public DataSheet(String id) {
    this.id = id;
    this.data = new ArrayList<DataAtom>();
  }
  public DataSheet(List<DataAtom> data) {
    this.id = Util.generateUUID();
    this.data = data;
  }


  public void addData(DataAtom datom) {
    this.data.add(datom);
  }

  public List<DataAtom> getData() {
    return data;
  }

  public String getID() {
    return id;
  }

}
