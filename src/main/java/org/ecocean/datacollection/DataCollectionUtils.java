
package org.ecocean.datacollection;

import javax.servlet.http.HttpServletRequest;
import org.joda.time.DateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import org.ecocean.CommonConfiguration;


/**
 *
 *
 * @author jholmber
 */
public class DataCollectionUtils {

  public static String[] getDataCollectionEventsForClass(String className, String context) {
    List<String> measurements = CommonConfiguration.getIndexedPropertyValues("measurement", context);
    List<String> units = CommonConfiguration.getIndexedPropertyValues("measurementUnits", context);
    List<String> classes = CommonConfiguration.getIndexedPropertyValues("measurementClasses", context);


    System.out.println("number units = "+units.size());

    List<String> measurementsForThisClass = new ArrayList<String>();




    for (int i=0; i<measurements.size(); i++) {
      if (classes.get(i)!= null && classes.get(i).toLowerCase().contains(className.toLowerCase())) {
        measurementsForThisClass.add(measurements.get(i));
        measurementsForThisClass.add(units.get(i));
      }
    }

    return (measurementsForThisClass.toArray( new String[measurements.size()] ) );
  }

  public static boolean classHasMeasurementEvent(String className, int measurementNum, String context) {
    //String classNamesCommaDelim = CommonConfiguration.getProperty("className"+"Classes")
    return true;
  }

  public static String[] getClassNamesFor(int measurementNum, String context) {
    String classNamesCommaDelim = CommonConfiguration.getProperty("measurementClasses"+measurementNum, context);
    return classNamesCommaDelim.split(",");
  }

}
