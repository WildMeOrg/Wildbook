package org.ecocean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Util {
  
  private static final String MEASUREMENT = "measurement";
  private static final String UNITS = MEASUREMENT + "Units";
  
  public static List<MeasurementDesc> findMeasurementDescs(String langCode) {
    List<MeasurementDesc> list = new ArrayList<MeasurementDesc>();
    List<String> types = CommonConfiguration.getIndexedValues(MEASUREMENT);
    if (types.size() > 0) {
      List<String> units = CommonConfiguration.getIndexedValues(UNITS);
      for (int i = 0; i < types.size() && i < units.size(); i++) {
        String type = types.get(i);
        String unit = units.get(i);
        String typeLabel = findLabel(type, langCode);
        String unitsLabel = findLabel(unit, langCode);
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
  public static List<OptionDesc> findSamplingProtocols(String langCode) {
    List<String> values = CommonConfiguration.getIndexedValues("samplingProtocol");
    List<OptionDesc> list = new ArrayList<OptionDesc>();
    for (String key : values) {
      String label = findLabel(key, langCode);
      list.add(new OptionDesc(key, label));
    }
    return list;
  }
  
  public static String getLocalizedSamplingProtocol(String samplingProtocol, String langCode) {
    if (samplingProtocol != null) {
      List<OptionDesc> samplingProtocols = findSamplingProtocols(langCode);
      for (OptionDesc optionDesc : samplingProtocols) {
        if (optionDesc.name.equals(samplingProtocol)) {
          return optionDesc.display;
        }
      }
    }
    return null;
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

}
