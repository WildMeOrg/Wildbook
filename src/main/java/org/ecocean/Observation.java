

/* This class is intended to stay very simple. It is the successor to the DynamicProperties  

 * available on the encounter. It has it's own table, and links back to the parent object
 * to allow it to be used as a searchable tag in the future.
 * 
 * 
 *@Author Colin Kingen 
 */

package org.ecocean;

import org.joda.time.DateTime;

public class Observation implements java.io.Serializable {

  private static final long serialVersionUID = -7934850478287322048L;
  
  private String observationID;
  
  private String name;  
  private String value;
  
  private String parentObjectID;
  private String parentObjectClass;
  
  private Long dateAddedMilli = null;
  private Long dateModifiedMilli = null;
  
  public Observation() {
  }
  
  public Observation(String newName, String newValue, Object parentObject, String parentID) {
    value = newValue;
    
    name = newName;
    
    parentObjectClass = parentObject.getClass().toString();
     
    parentObjectID = parentID;
    
    observationID = Util.generateUUID();
    
    setDateAddedMilli();
    setDateLastModifiedMilli();
  }
  
  public String getParentObjectClass() {
    return parentObjectClass;
  }
  
  public String getParentObjectID() {
    return parentObjectID;
  }

  public String getID() {
    return observationID;
  }

  public void setID(String newID) {
    observationID = newID;
  }
  
  public String getName() {
    return name;
  }

  public void setName(String newName) {
    if(newName==null){
      name=null;
    }
    else{
      name = newName;
    }
  }
  
  public String getValue() {
    return value;
  }
  
  public void setValue(String newValue) {
    if(newValue==null){
      value=null;
    }
    else{
      value= newValue;}
  }
  
  private void setDateAddedMilli() {
    long added = new DateTime().getMillis();
    dateAddedMilli = added;
  }
  
  public void setDateLastModifiedMilli() {
    long modified = new DateTime().getMillis();
    dateAddedMilli = modified;
  }
  
  public Long getDateAddedMilli() {
    return dateAddedMilli;
  }
  
  public Long getDateLastModifiedMilli() {
    return dateModifiedMilli;
  }
 
}