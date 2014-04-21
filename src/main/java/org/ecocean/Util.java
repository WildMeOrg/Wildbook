package org.ecocean;

//import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
//import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;


//import javax.jdo.JDOException;
//import javax.jdo.JDOHelper;
import javax.jdo.Query;
//import javax.jdo.PersistenceManagerFactory;


import org.ecocean.tag.MetalTag;


//use Point2D to represent cached GPS coordinates
import com.reijns.I3S.Point2D;

public class Util {
  
  //Measurement static values
  private static final String MEASUREMENT = "measurement";
  private static final String BIOLOGICALMEASUREMENT = "biologicalMeasurementType";
  private static final String UNITS = MEASUREMENT + "Units";
  private static final String BIOLOGICALMEASUREMENTUNITS = BIOLOGICALMEASUREMENT.replaceAll("Type", "Units");
  private static final String METAL_TAG_LOCATION = "metalTagLocation";
  private static final String SATELLITE_TAG_NAME = "satelliteTagName";
  
  //GPS coordinate caching for Encounter Search and Individual Search
  private static ArrayList<Point2D> coords;
  
  public static List<MeasurementDesc> findMeasurementDescs(String langCode,String context) {
    List<MeasurementDesc> list = new ArrayList<MeasurementDesc>();
    List<String> types = CommonConfiguration.getIndexedValues(MEASUREMENT,context);
    if (types.size() > 0) {
      List<String> units = CommonConfiguration.getIndexedValues(UNITS,context);
      for (int i = 0; i < types.size() && i < units.size(); i++) {
        String type = types.get(i);
        String unit = units.get(i);
        String typeLabel = findLabel(type, langCode,context);
        String unitsLabel = findLabel(unit, langCode,context);
        list.add(new MeasurementDesc(type, typeLabel, unit, unitsLabel));
      }
    }
    return list;
  }
  
  public static List<MeasurementDesc> findBiologicalMeasurementDescs(String langCode, String context) {
    List<MeasurementDesc> list = new ArrayList<MeasurementDesc>();
    List<String> types = CommonConfiguration.getIndexedValues(BIOLOGICALMEASUREMENT,context);
    if (types.size() > 0) {
      List<String> units = CommonConfiguration.getIndexedValues(BIOLOGICALMEASUREMENTUNITS,context);
      for (int i = 0; i < types.size() && i < units.size(); i++) {
        String type = types.get(i);
        String unit = units.get(i);
        String typeLabel = findLabel(type, langCode, context);
        String unitsLabel = findLabel(unit, langCode, context);
        list.add(new MeasurementDesc(type, typeLabel, unit, unitsLabel));
      }
    }
    return list;
  }
  
  /**
   * Returns a map of sampling protocols, where the key is the name of protocol, and the
   * value is the user-friendly, localized label.
   * @param langCode
   * @return
   */
  public static List<OptionDesc> findSamplingProtocols(String langCode,String context) {
    List<String> values = CommonConfiguration.getIndexedValues("samplingProtocol",context);
    List<OptionDesc> list = new ArrayList<OptionDesc>();
    for (String key : values) {
      String label = findLabel(key, langCode,context);
      list.add(new OptionDesc(key, label));
    }
    return list;
  }
  
  public static String getLocalizedSamplingProtocol(String samplingProtocol, String langCode, String context) {
    if (samplingProtocol != null) {
      List<OptionDesc> samplingProtocols = findSamplingProtocols(langCode,context);
      for (OptionDesc optionDesc : samplingProtocols) {
        if (optionDesc.name.equals(samplingProtocol)) {
          return optionDesc.display;
        }
      }
    }
    return null;
  }
  
  public static List<MetalTagDesc> findMetalTagDescs(String langCode,String context) {
    List<String> metalTagLocations = CommonConfiguration.getIndexedValues(METAL_TAG_LOCATION,context);
    List<MetalTagDesc> list = new ArrayList<MetalTagDesc>();
    for (String location : metalTagLocations) {
      String locationLabel = findLabel(location, langCode,context);
      list.add(new MetalTagDesc(location, locationLabel));
    }
    return list;
  }
  
  /**
   * Find the MetalTag instance belonging to an Encounter that is described by the MetalTagDesc.
   * @param metalTagDesc
   * @param langCode
   * @param encounter
   * @return
   */
  public static MetalTag findMetalTag(MetalTagDesc metalTagDesc, Encounter encounter) {
    List<MetalTag> metalTags = encounter.getMetalTags();
    if (metalTags != null) {
      for (MetalTag metalTag : metalTags) {
        if (metalTag.getLocation().equals(metalTagDesc.getLocation())) {
          return metalTag;
        }
      }
    }
    return null;
  }
  
  public static List<String> findSatelliteTagNames(String context) {
    return CommonConfiguration.getIndexedValues(SATELLITE_TAG_NAME,context);
  }
  
  private static String findLabel(String key, String langCode, String context) {
    /*
    Locale locale = Locale.US;
    if (langCode != null) {
      locale = new Locale(langCode);
    }
    try {
      ResourceBundle bundle = ResourceBundle.getBundle("bundles.commonConfigurationLabels", locale);
      return bundle.getString(key + ".label");
    }
    catch (MissingResourceException ex) {
      System.out.println("Error finding bundle or key for key: " + key);
    }
    return key;*/
    
    Properties myProps = ShepherdProperties.getProperties("commonConfigurationLabels.properties", langCode, context);
    return myProps.getProperty(key+".label");
    
    
  }
  
  public static String quote(String arg) {
    StringBuilder sb = new StringBuilder(arg.length() + 2);
    sb.append('"');
    sb.append(arg);
    sb.append('"');
    return sb.toString();
  }
  
  public static class MeasurementDesc {
    private String type;
    private String label;
    private String units;
    private String unitsLabel;
    
    private MeasurementDesc(String type, String label, String units, String unitsLabel) {
      this.type = type;
      this.label = label;
      this.units = units;
      this.unitsLabel = unitsLabel;
    }
    
    public String getType() {
      return type;
    }
    public String getLabel() {
      return label;
    }
    public String getUnits() {
      return units;
    }
    public String getUnitsLabel() {
      return unitsLabel;
    }
  }
  
  public static class OptionDesc {
    private String name;
    private String display;
    private OptionDesc(String name, String display) {
      this.name = name;
      this.display = display;
    }
    public String getName() {
      return name;
    }
    public String getDisplay() {
      return display;
    }
    
  }
  
  public static class MetalTagDesc {
    private String location;
    private String locationLabel;
    
    private MetalTagDesc(String location, String locationLabel) {
      this.location = location;
      this.locationLabel = locationLabel;
    }

    public String getLocation() {
      return location;
    }

    public String getLocationLabel() {
      return locationLabel;
    }
    
  }
  
  public synchronized static ArrayList<Point2D> getCachedGPSCoordinates(boolean refresh,String context) {
    try {
      if ((coords == null)||(refresh)) {

        //execute the JDOQL
        Shepherd myShepherd=new Shepherd(context);
        Query query=myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Encounter WHERE decimalLatitude != null && decimalLongitude != null");
        Collection<Encounter> c = (Collection<Encounter>) (query.execute());
        ArrayList<Encounter> encs=new ArrayList<Encounter>(c);
        int encsSize=encs.size();
        
        //populate coords
        coords=new ArrayList<Point2D>(encsSize);
        for(int i=0;i<encsSize;i++){
          Encounter e=encs.get(i);
          int lat=(int)e.getDecimalLatitudeAsDouble();
          int longie=(int)e.getDecimalLongitudeAsDouble();
          Point2D myPoint=new Point2D(lat,longie);
          if(!coords.contains(myPoint)){
            coords.add(myPoint);
          }
        }

        query.closeAll();
      }
      
      return coords;
    } 
    catch (Exception jdo) {
      jdo.printStackTrace();
      System.out.println("I hit an error trying to populate the cached GPS coordinates in Util.java.");
      return new ArrayList<Point2D>();
    }
  }

}
