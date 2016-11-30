package org.ecocean.datacollection;

import org.ecocean.Util;

import org.joda.time.DateTime;


public class Instant extends DataPoint {

  private DateTime value;

  public Instant() {
  }

  public Instant(DateTime value) {
    super.setID(Util.generateUUID());
    this.value = value;
  }

  public Instant(String name, DateTime value, String units) {
    super.setID(Util.generateUUID());
    super.setName(name);
    super.setUnits(units);
    this.value = value;
  }

  public DateTime getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = (DateTime) value;
  }

  // NOTE: to make cross-talk with javascript as painless as possible,\
  //   all string-based communication assumes we're dealing with stringified
  //   milliseconds-since-epoch
  public void setValueFromString(String str) {
    System.out.println("Beginning Instant.setValueFromString("+str+")");
    if (str==null) setValue(null);
    else {
    try {
      DateTime dt = new DateTime(new Long(str));
      System.out.println("  parsed DateTime = "+dt+"");
      this.value = dt;
      System.out.println("  this.value = "+this.value);
    } catch (Exception e) {
      System.out.println("Pokecatch! Unable to parse the datetime!");
    }
    }
  }

  public String getValueString() {
    if (value==null) return "";
    return (Util.prettyPrintDateTime(value));
  }

  public DateTime[] getCategories(String context) {
    String[] strings = super.getCategoriesAsStrings(context);
    DateTime[] res = new DateTime[strings.length];
    for (int i=0; i<strings.length; i++) {
      res[i] = DateTime.parse(strings[i]);
    }
    return res;
  }

  public String toString() {
    return ((this.getName()+": "+value+super.getUnits()).replaceAll("null",""));
  }

  public String toLabeledString() {
    return ("instant-"+(this.getName()+": "+value+super.getUnits()).replaceAll("null",""));
  }


}
