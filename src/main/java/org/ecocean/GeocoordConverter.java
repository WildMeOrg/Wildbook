package org.ecocean;

import org.ecocean.*;
import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;


// the following nine includes grant us one single capability: translating coords from utm to gps and back
// what a complete and utter mess. shame on you, geotools. shame on you.
import org.geotools.referencing.operation.*;
import org.geotools.referencing.*;
import org.geotools.referencing.factory.ReferencingFactoryContainer;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.cs.CartesianCS;
import org.opengis.referencing.crs.*;
import org.opengis.referencing.operation.*;
import org.opengis.geometry.DirectPosition;
import org.geotools.geometry.GeneralDirectPosition;

public class GeocoordConverter {

  public static final int DEFAULT_UTM_ZONE_NUMBER = 30;
  public static final String DEFAULT_EPSG_CODE_STRING = "EPSG:23030";
  public static final double[] ERROR_dOUBLE_TUPLE = new double[]{-10000000,10000000};

  public static Map<Integer,String> utmZoneToEpsgCode = new HashMap<Integer,String>();
  static {
     utmZoneToEpsgCode.put(10, "EPSG:32610"); // Pacific NW
     // "historical" lynx populations on following two projections:
     utmZoneToEpsgCode.put(29, "EPSG:23029" ); // Western Spain/Britain
     utmZoneToEpsgCode.put(30, "EPSG:23030" ); // Eastern Spain/Britain
     // next two projections are currently used
     utmZoneToEpsgCode.put(302, "EPSG:25830" ); // Western Spain/Britain
     utmZoneToEpsgCode.put(31, "EPSG:25831" ); // Western Spain/Britain
     // this is a pan-european projection used
     utmZoneToEpsgCode.put(300, "EPSG:25830" ); // Western Spain/Britain

  }
  // because the mapping is multi-zone to single code
  public static Map<String, Integer> epsgCodeToUtmZone = new HashMap<String, Integer>();
  static {
    // next two projections are currently used
    epsgCodeToUtmZone.put("EPSG:25830", 30);
    epsgCodeToUtmZone.put("EPSG:25831", 31); // Western Spain/Britain
     //epsgCodeToUtmZone.put("EPSG:32610", 10); // Pacific NW
     // "historical" lynx populations on following two projections:
     epsgCodeToUtmZone.put("EPSG:23029", 29); // Western Spain/Britain
     epsgCodeToUtmZone.put("EPSG:23030", 30); // Eastern Spain/Britain
     // this is a pan-european projection used
     epsgCodeToUtmZone.put("EPSG:3035", null); // Western Spain/Britain
  }



  public static void testAllProjections() {
    double x = 446738;
    double y = 4114793;
    System.out.println("GeocoordConverter: about to test all projections for x,y ("+x+", "+y+")");

    double[] zone30 = utmToGps(x,y,30);
    System.out.println("Zone 30 ("+utmZoneToEpsgCode.get(30)+"): ("+zone30[0]+", "+zone30[1]+")");
    double[] zone302 = utmToGps(x,y,302);
    System.out.println("Zone 30 ("+utmZoneToEpsgCode.get(302)+"): ("+zone302[0]+", "+zone302[1]+")");

    for (Integer zoneN : utmZoneToEpsgCode.keySet()) {

      double[] result = utmToGps(x,y,zoneN);
      System.out.println("Zone "+zoneN+"("+utmZoneToEpsgCode.get(zoneN)+"): ("+result[0]+", "+result[1]+")");

    }


  }



  // Returns the center longitude of a UTM zone numbered 1-60
  private static double zoneCenterLongitude(int utmZoneNum) {
    if (utmZoneNum < 1 || utmZoneNum > 60) throw new IllegalArgumentException("UTM Zone Number must be between 1-60");
    return -183.0 + utmZoneNum*6;
  }

  public static double[] utmToGps(double easting, double northing) {
    return utmToGps(easting, northing, DEFAULT_EPSG_CODE_STRING);
  }

  public static double[] utmToGps(double easting, double northing, int zoneNumber) {
    return utmToGps(easting, northing, utmZoneToEpsgCode.get(zoneNumber));
  }


  public static double[] utmToGps(double easting, double northing, String epsgProjCode) {

    // I suspect that this is very bad practice, the "pokemon catch"  ¯\_(ツ)_/¯
    try {

      System.out.println("========= BEGIN CONVERTING UTM to GPS ==========");
      System.out.println("        with EPSG code: "+epsgProjCode);
      // Create the translation operator between two coordinate systems
      CRSAuthorityFactory crsFac = ReferencingFactoryFinder.getCRSAuthorityFactory("EPSG", null);
      CoordinateReferenceSystem utmCrs   = crsFac.createCoordinateReferenceSystem(epsgProjCode);
      CoordinateReferenceSystem wgs84crs = crsFac.createCoordinateReferenceSystem("4326");
      CoordinateOperation translationOp = new DefaultCoordinateOperationFactory().createOperation(utmCrs, wgs84crs);
      // load the coords into a geotools object, then sent to the translator.
      DirectPosition eastNorth = new GeneralDirectPosition((int) easting, (int) northing);
      DirectPosition latLng = translationOp.getMathTransform().transform(eastNorth, eastNorth);

      double latitude  = latLng.getOrdinate(0);
      double longitude = latLng.getOrdinate(1);

      System.out.println("  (easting, northing): ("+easting+", "+northing+")");
      System.out.println("          (lat, long): ("+latitude+", "+longitude+")");
      System.out.println("========== DONE CONVERTING GEOCOORDS ==========");

      return new double[]{latitude, longitude};

    } catch (Exception probablyFromGeoTools) {
      probablyFromGeoTools.printStackTrace();
    }
    // hopefully this is clearly an error
    return ERROR_dOUBLE_TUPLE;
  }


  public static double[] gpsToUtm(double latitude, double longitude) {

    try {
      System.out.println("========= BEGIN CONVERTING GPS to UTM ==========");

      int zoneNumber = DEFAULT_UTM_ZONE_NUMBER;
      double utmZoneCenterLongitude = zoneCenterLongitude(zoneNumber);
      System.out.println("  Zone "+zoneNumber+" w/ center longitude "+utmZoneCenterLongitude);

      MathTransformFactory mtFactory = ReferencingFactoryFinder.getMathTransformFactory(null);
      ReferencingFactoryContainer factories = new ReferencingFactoryContainer(null);

      GeographicCRS geoCRS = org.geotools.referencing.crs.DefaultGeographicCRS.WGS84;
      CartesianCS cartCS = org.geotools.referencing.cs.DefaultCartesianCS.GENERIC_2D;

      ParameterValueGroup parameters = mtFactory.getDefaultParameters("Transverse_Mercator");
      parameters.parameter("central_meridian").setValue(utmZoneCenterLongitude);
      parameters.parameter("latitude_of_origin").setValue(0.0);
      parameters.parameter("scale_factor").setValue(0.9996);
      parameters.parameter("false_easting").setValue(500000.0);
      parameters.parameter("false_northing").setValue(0.0);

      Map properties = Collections.singletonMap("name", "WGS 84 / UTM Zone " + zoneNumber);
      ProjectedCRS projCRS = factories.createProjectedCRS(properties, geoCRS, null, parameters, cartCS);

      MathTransform transform = CRS.findMathTransform(geoCRS, projCRS);

      double[] result = new double[2];
      transform.transform(new double[] {longitude, latitude}, 0, result, 0, 1);

      int easting  = (int)Math.round(result[0]);
      int northing = (int)Math.round(result[1]);

      System.out.println("          (lat, long): ("+latitude+", "+longitude+")");
      System.out.println("  (easting, northing): ("+easting+", "+northing+")");
      System.out.println("========== DONE CONVERTING GEOCOORDS ==========");

      return(result);

    } catch (Exception probablyFromGeoTools) {
      probablyFromGeoTools.printStackTrace();
    }
    return ERROR_dOUBLE_TUPLE;
  }

}
