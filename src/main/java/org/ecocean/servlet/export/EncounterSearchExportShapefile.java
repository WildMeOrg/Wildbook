package org.ecocean.servlet.export;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;















import java.util.zip.ZipEntry;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipOutputStream;
//import java.io.FileWriter;
import java.io.Serializable;

import org.geotools.data.*;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.*;
import org.geotools.data.simple.*;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.*;
import org.geotools.geometry.GeometryBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.awt.PointShapeFactory.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.simple.*;
import org.opengis.geometry.PositionFactory;
import org.opengis.geometry.coordinate.PointArray;
import org.geotools.data.*;


//import java.sql.Date;
//import java.net.URI;

//adds spots to a new encounter
public class EncounterSearchExportShapefile extends HttpServlet{
  
  private static final int BYTES_DOWNLOAD = 1024;

  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }
    


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    
    //set the response
    String context="context0";
    context=ServletUtilities.getContext(request);
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    //if(!encountersDir.exists()){encountersDir.mkdirs();}
    
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterSearchExportShapefile.class");
    Vector rEncounters = new Vector();
    
    //set up the files
    String gisZipFilename = "exportGISShapefiles_" + request.getRemoteUser() + ".zip";
    //File gisFile = new File(getServletContext().getRealPath(("/encounters/" + gisZipFilename)));


    /*
    * We create a FeatureCollection into which we will put each Feature created from a record
    * in the input csv data file
    */
    //DefaultFeatureCollection collection = new DefaultFeatureCollection();
    /*
    * GeometryFactory will be used to create the geometry attribute of each feature (a Point
    * object for the location)
    */
    //org.locationtech.jts.geom.GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
    //shapefile
    String shapeFilename = "ShapefileExport_" + request.getRemoteUser() + ".shp";




    
    
    myShepherd.beginDBTransaction();
      
      try{
        
        
        /*
         * We use the DataUtilities class to create a FeatureType that will 
         * describe the data in our
         * shapefile.
         * 
         * See also the createFeatureType method below for another, 
         * more flexible approach.
         */
        final SimpleFeatureType TYPE = DataUtilities.createType(
            "Location",
            (
            "the_geom:Point:srid=4326," + // <- the geometry attribute: Point type
            "Date:java.util.Date," + // <- a String attribute
            "Encounter:String," + // a number attribute
            "Individual:String," + // a number attribute
            "Sex:String," + // a number attribute
            "Haplotype:String," + // a number attribute
            "URL:String," + // a number attribute
            "Taxonomy:String" // a number attribute
            )
            );
        
      
      
        EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");
        rEncounters = queryResult.getResult();

				Vector blocked = Encounter.blocked(rEncounters, request);
				if (blocked.size() > 0) {
					response.setContentType("text/html");
					PrintWriter out = response.getWriter();
					out.println(ServletUtilities.getHeader(request));  
					out.println("<html><body><p><strong>Access denied.</strong></p>");
					out.println(ServletUtilities.getFooter(context));
					out.close();
			    myShepherd.rollbackDBTransaction();
			    myShepherd.closeDBTransaction();
					return;
				}
      
      
        int numMatchingEncounters=rEncounters.size();
        
        /*
         * A list to collect features as we create them.
         */
        List<SimpleFeature> features = new ArrayList<SimpleFeature>();

        /*
         * GeometryFactory will be used to create the geometry attribute of each feature,
         * using a Point object for the location.
         */
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);

        
        

        for(int i=0;i<numMatchingEncounters;i++){
        
          Encounter enc=(Encounter)rEncounters.get(i);
          
          if ((enc.getDecimalLongitudeAsDouble()!=null) && (enc.getDecimalLatitudeAsDouble() != null)) {
            
            /* Longitude (= x coord) first ! */
            org.locationtech.jts.geom.Point point = geometryFactory.createPoint(new Coordinate(enc.getDecimalLongitudeAsDouble().doubleValue(),enc.getDecimalLatitudeAsDouble().doubleValue()));
            featureBuilder.set("the_geom",point);
            
            System.out.println("Added point: "+point.getX()+","+point.getY());
            
            

            if(enc.getDateInMilliseconds()!=null){
              featureBuilder.set("Date",new java.sql.Date(enc.getDateInMilliseconds()));
            }
            featureBuilder.set("Encounter",enc.getCatalogNumber());
            featureBuilder.set("Individual",ServletUtilities.handleNullString(enc.getIndividualID()));
            if(enc.getSex()!=null){
              featureBuilder.set("Sex",enc.getSex());
            }
            String haploString="";
            if(enc.getTissueSamples().size()>0){
              List<TissueSample> samples=enc.getTissueSamples();
              int sampleSize=samples.size();
              for(int sample=0;sample<sampleSize;sample++){
                TissueSample thisSample=samples.get(sample);
                if(thisSample.getNumAnalyses()>0){
                  List<GeneticAnalysis> analyses=thisSample.getGeneticAnalyses();
                  int numAnalyses=analyses.size();
                  for(int analy=0;analy<numAnalyses;analy++){
                    GeneticAnalysis thisAnalysis=analyses.get(analy);
                    if(thisAnalysis.getAnalysisType().equals("MitochondrialDNA")){
                      MitochondrialDNAAnalysis thisDNA=(MitochondrialDNAAnalysis)thisAnalysis;
                      haploString=thisDNA.getHaplotype();
                    }
                  }
                }
              }
            
            }
            featureBuilder.set("Haplotype",haploString);
            featureBuilder.set("URL",("https://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+enc.getCatalogNumber()));
            
            //featureBuilder.add(enc.getDecimalLatitudeAsDouble());
            //featureBuilder.add(enc.getDecimalLongitudeAsDouble());
            
            String genusSpeciesString="";
            if((enc.getGenus()!=null)&&(enc.getSpecificEpithet()!=null)){
              genusSpeciesString = enc.getGenus()+" "+enc.getSpecificEpithet();
            }
            featureBuilder.set("Taxonomy",genusSpeciesString);
            
            SimpleFeature feature = featureBuilder.buildFeature(enc.getCatalogNumber());
            
            
            //collection.add(feature);
            //System.out.println(".....collection size is now: "+collection.size());
            
            features.add(feature);
            
            
          }
        }
        

        System.out.println("Generated thus many Shapefile Point features: "+features.size());
        
      //write out the shapefile
        File shapeFile = new File(encountersDir.getAbsolutePath()+"/" + shapeFilename);
        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put("url", shapeFile.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);
        ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
        

        if(newDataStore==null)System.out.println("newDataStore is null!");

        /*
         * TYPE is used as a template to describe the file contents
         */
        newDataStore.createSchema(TYPE);
        
        /*
         * Write the features to the shapefile
         */
        Transaction transaction = new DefaultTransaction("create");

        String typeName = newDataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
        SimpleFeatureType SHAPE_TYPE = featureSource.getSchema();
         
        /*
         * The Shapefile format has a couple limitations:
         * - "the_geom" is always first, and used for the geometry attribute name
         * - "the_geom" must be of type Point, MultiPoint, MuiltiLineString, MultiPolygon
         * - Attribute names are limited in length
         * - Not all data types are supported (example Timestamp represented as Date)
         *
         * Each data store has different limitations so check the resulting SimpleFeatureType.
         */
        System.out.println("SHAPE:" + SHAPE_TYPE);
        
        
        
        

        if (featureSource instanceof SimpleFeatureStore) {
            SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
            /*
             * SimpleFeatureStore has a method to add features from a
             * SimpleFeatureCollection object, so we use the ListFeatureCollection
             * class to wrap our list of features.
             */
            SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
            System.out.println("collection size is: "+collection.size());
            
            int featSize=features.size();
            for(int i=0;i<featSize;i++){
              System.out.println("feature "+i+": "+features.get(i).toString());
            }
            
            
            featureStore.setTransaction(transaction);
            try {
                featureStore.addFeatures(collection);
                transaction.commit();
            } catch (Exception problem) {
                problem.printStackTrace();
                transaction.rollback();
            } finally {
                transaction.close();
            }
            System.out.println("...wrote out shapeFile successfully!");
            
            //zip the results
            // These are the files to include in the ZIP file
      String[] filenames = new String[]{
       shapeFile.getAbsolutePath(),
       shapeFile.getAbsolutePath().replaceAll(".shp",".shx"),
       shapeFile.getAbsolutePath().replaceAll(".shp",".dbf"),
       shapeFile.getAbsolutePath().replaceAll(".shp",".fix"),
       shapeFile.getAbsolutePath().replaceAll(".shp",".prj"),
       //shapeFile.getAbsolutePath().replaceAll(".shp",".qix")
      };
      
      // Create a buffer for reading the files
      byte[] buf = new byte[1024];
      
      try {
          // Create the ZIP file
          String outFilename = shapeFile.getParentFile().getAbsolutePath()+File.separator+gisZipFilename;
          //System.out.println(outFilename);
          ZipOutputStream zipout = new ZipOutputStream(new FileOutputStream(outFilename));
      
          // Compress the files
          for (int i=0; i<filenames.length; i++) {
              FileInputStream in = new FileInputStream(filenames[i]);
         System.out.println(filenames[i]);
              // Add ZIP entry to output stream.
              File file2add=new File(filenames[i]);
              zipout.putNextEntry(new ZipEntry(file2add.getName()));
      
              // Transfer bytes from the file to the ZIP file
              int len;
              while ((len = in.read(buf)) > 0) {
                  zipout.write(buf, 0, len);
              }
      
              // Complete the entry
              zipout.closeEntry();
              in.close();
          }
      
          // Complete the ZIP file
          zipout.close();
          
          //now write out the file
          response.setContentType("application/zip");
          response.setHeader("Content-Disposition","attachment;filename="+gisZipFilename);
          ServletContext ctx = getServletContext();
          //InputStream is = ctx.getResourceAsStream("/encounters/"+gisZipFilename);
          InputStream is=new FileInputStream(outFilename);
          
          int read=0;
          byte[] bytes = new byte[BYTES_DOWNLOAD];
          OutputStream os = response.getOutputStream();
         
          while((read = is.read(bytes))!= -1){
            os.write(bytes, 0, read);
          }
          os.flush();
          os.close(); 
          
          System.out.println("...zipped up shapeFile successfully!");
          
          
      } 
      catch (IOException e) {
       e.printStackTrace();
      }
        } else {
            System.out.println(typeName + " does not support read/write access");
            
        }

        


        
      }
      catch(Exception ioe){
        ioe.printStackTrace();
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println(ServletUtilities.getHeader(request));
        out.println("<html><body><p><strong>Error encountered</strong> with file writing. Check the relevant log.</p>");
        out.println("<p>Please let the webmaster know you encountered an error at: "+this.getServletName()+" servlet</p></body></html>");
        out.println(ServletUtilities.getFooter(context));
      }
      


    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();


  
      
      
      
    }
  
  /**
   * Here is how you can use a SimpleFeatureType builder to create the schema for your shapefile
   * dynamically.
   * <p>
   * This method is an improvement on the code used in the main method above (where we used
   * DataUtilities.createFeatureType) because we can set a Coordinate Reference System for the
   * FeatureType and a a maximum field length for the 'name' field dddd
   */
  
  /*
   * private static SimpleFeatureType createFeatureType(String context) {

      SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
      builder.setName(CommonConfiguration.getHTMLTitle(context));
      builder.setCRS(DefaultGeographicCRS.WGS84); // <- Coordinate reference system

      // add attributes in order
      builder.add("Location", org.locationtech.jts.geom.Point.class);
      builder.add("Date", java.sql.Date.class);
      builder.add("Encounter", String.class); 
      builder.add("Individual", String.class); 
      builder.add("Sex", String.class);
      builder.add("Haplotype", String.class);
      builder.add("URL", String.class);
      builder.add("Latitude", Double.class);
      builder.add("Longitude", Double.class);
      builder.add("Taxonomy", String.class); 
      builder.setDefaultGeometry("Location");
      

      // build the type
      final SimpleFeatureType LOCATION = builder.buildFeatureType();

      return LOCATION;
  }
  
  */
  

  

  }
