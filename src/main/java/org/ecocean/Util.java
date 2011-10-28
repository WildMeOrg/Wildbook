package org.ecocean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Util {
  
  private static final String MEASUREMENT = "measurement";
  private static final String UNITS = MEASUREMENT + "Units";
  
  public static List<MeasurementCollectionEventDesc> findMeasurementCollectionEventDescs(String langCode) {
    List<MeasurementCollectionEventDesc> list = new ArrayList<MeasurementCollectionEventDesc>();
    List<String> types = CommonConfiguration.getIndexedValues(MEASUREMENT);
    if (types.size() > 0) {
      List<String> units = CommonConfiguration.getIndexedValues(UNITS);
      for (int i = 0; i < types.size() && i < units.size(); i++) {
        String type = types.get(i);
        String unit = units.get(i);
        String typeLabel = findLabel(type, langCode);
        String unitsLabel = findLabel(unit, langCode);
        list.add(new MeasurementCollectionEventDesc(type, typeLabel, unit, unitsLabel));
      }
    }
    return list;
  }
  
  private static String findLabel(String key, String langCode) {
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
    return key;
  }
  
  public static class MeasurementCollectionEventDesc {
    private String type;
    private String label;
    private String units;
    private String unitsLabel;
    
    private MeasurementCollectionEventDesc(String type, String label, String units, String unitsLabel) {
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

}
