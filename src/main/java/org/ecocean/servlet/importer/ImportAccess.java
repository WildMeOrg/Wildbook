package org.ecocean.servlet.importer;

import org.ecocean.*;
import org.ecocean.genetics.BiologicalMeasurement;
import org.ecocean.genetics.SexAnalysis;
import org.ecocean.genetics.TissueSample;
import org.ecocean.servlet.*;
import org.ecocean.tag.SatelliteTag;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.ecocean.identity.*;
import org.ecocean.media.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


import org.json.JSONObject;

import com.healthmarketscience.jackcess.Column;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

import org.json.JSONArray;

import java.io.*;
import java.math.BigDecimal;

public class ImportAccess extends HttpServlet {
  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private static PrintWriter out;
  private static String context;
  
  // Okay, we might need to build a hashmap out of every line in this table, so we can create multiple encounters 
  // for the date/sighting number pairs that occure multiple times. 
  HashMap<String,Integer> duplicatePairsMap = new HashMap<String,Integer>();
  ArrayList<String> failedEncs = new ArrayList<String>();

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    context = ServletUtilities.getContext(request);
    out = response.getWriter();

    Shepherd myShepherd = new Shepherd(context);

    // Check if we have created and asset store yet, and if not create one.
    myShepherd.beginDBTransaction();
    myShepherd.setAction("AccessImport.class");

    ServletUtilities.importJsp("header.jsp", request, response);
    // for alignment after header
    out.println("<div class=\"container\"><div class=\"row\">");

    if (!CommonConfiguration.isWildbookInitialized(myShepherd)) {
      out.println("-- Wildbook not initialized. Starting Wildbook. --");
      StartupWildbook.initializeWildbook(request, myShepherd);
    }
    myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();
      
    
    String dbName = "omanData2016.04.20.accdb";
    if (request.getParameter("file") != null) {
      dbName = request.getParameter("file");
    }
    
    String dbLocation = "/data/";
    if (request.getParameter("location") != null) {
      dbLocation = request.getParameter("location");
    }

    Database db = null;  
    try {
      db =  DatabaseBuilder.open(new File(dbLocation + dbName));
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Error grabbing the .mdb file to process.");
    }
    
    
    out.println("***** Beginning Access Database Import. *****\n");
    
    Set<String> tables = db.getTableNames();
    out.println("********************* Here's the tables : "+tables.toString()+"\n");
    
    myShepherd.beginDBTransaction();


    out.println("<h3> Preprocessing IDPhotos table</h3>");
    out.println("<p>");
    if (!tables.contains("IDPhotos")) throw new IOException("Formatting Exception: No IDphotos table!");
    out.println("Table present: "+tables.contains("IDPhotos"));
    Table idPhotos = db.getTable("IDPhotos");
    out.println("Table present: "+tables.contains("IDPhotos"));
    processIDPhotosTable(idPhotos, out);
    out.println("</p>");

    out.println("<p>");
    if (!tables.contains("SightingHistory")) throw new IOException("Formatting Exception: No SightingHistory table!");
    out.println("<h3> Preprocessing SightingHistory table</h3>");
    out.println("Table present: "+tables.contains("SightingHistory"));
    Table sightingHistory = db.getTable("SightingHistory");
    processSightingHistoryTable(sightingHistory, out);
    out.println("</p>");



        /*
    // These switches allow you to work on different tables without doing the whole import a bunch of times.
    boolean dumlTableSwitch = true;
    if (dumlTableSwitch) {    
      try {
        out.println("********************* Let's process the DUML Table!\n");
        // Hit the SIGHTINGS table to find out whether we need to create multiple encounters for a given occurrence.
        buildEncounterDuplicationMap(db.getTable("SIGHTINGS"), myShepherd);
        
        processDUML(db.getTable("DUML"), myShepherd);
      } catch (Exception e) {
        out.println(e);
        out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! Could not process DUML table!!!");
      }
    }  
    
    boolean sightingsTableSwitch = true;
    if (sightingsTableSwitch) {
      try {
        out.println("********************* Let's process the SIGHTINGS Table!\n");
        processSightings(db.getTable("SIGHTINGS"), myShepherd);
      } catch (Exception e) {
        out.println(e);
        e.printStackTrace();
        out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! Could not process SIGHTINGS table!!!");
      }      
    }
    
    boolean effortTableSwitch = true;
    if (effortTableSwitch) {
      try {
        out.println("********************* Let's process the EFFORT Table!\n");
        processEffortTable(db.getTable("EFFORT"), myShepherd);
      } catch (Exception e) {
        out.println(e);
        e.printStackTrace();
        out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! Could not process Effort table!!!");
      }      
    }
    
    boolean biopsyTableSwitch = true;
    if (biopsyTableSwitch) {
      try {
        out.println("********************* Let's process the BiopsySamples Table!\n");
        processBiopsyTable(db.getTable("Biopsy Samples"), myShepherd, db.getTable("DUML"));
      } catch (Exception e) {
        out.println(e);
        e.printStackTrace();
        out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! Could not process BiopsySamples table!!!");
      }      
    }
    
    */
    myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();
    db.close(); 
    // for alignment after header
    out.println("</div></div>");
    ServletUtilities.importJsp("footer.jsp", request, response);
  }  
  
  private ArrayList<String> getColumnMasterList(Table table) {
    // This is a list of column names. We are gonna take them out as we process them so we know if we missed any at the end. 
    ArrayList<String> columnMasterList = new ArrayList<String>();
    List<? extends Column> columns = table.getColumns();
    for (int i=0;i<columns.size();i++) {
      columnMasterList.add(columns.get(i).getName());
    }
    //out.println("All of the columns in this Table : "+columnMasterList.toString()+"\n");
    return columnMasterList;
  }


  private boolean processIDPhotosTable(Table idPhotos, java.io.PrintWriter out) {
  	out.println("    processing IDPhotos table");
    printTable(idPhotos, out);
  	return true;
  }

  public void printTable(Table table, java.io.PrintWriter out) {
    List<String> colNames = getColumnMasterList(table);
    out.println("<table>");
    for (String colName : colNames) {
      out.println("<tr><td>");
      out.print(colName);
      out.print("</td></tr>");
    } 
    out.println("</table>");

  }

  private boolean processSightingHistoryTable(Table sightingHistory, java.io.PrintWriter out) {
  	out.println("    processing SightingHistory table");
    printTable(sightingHistory, out);
  	return true;
  }

  /*
  private void processDUML(Table table, Shepherd myShepherd) {
    
  
    out.println("DUML Table has "+table.getRowCount()+" Rows!\n");
    
    int errors = 0;
    
    int newOccs = 0;
    int newEncs = 0;
    
    int locations = 0;
    int dates = 0;
    int projects = 0;
    int speciesIds = 0;
    int behaviors = 0;
    int depths = 0;
    int lats = 0;
    int lons = 0;
    int endTimes = 0;
    int sightNos = 0;
    
    ArrayList<String> columnMasterList = getColumnMasterList(table);
       
    Row thisRow = null;
    Encounter newEnc = null;
    int totalInSightingsArray = 0;
    for (int i=0;i<table.getRowCount();i++) {
      newEnc = new Encounter();
      try {
        thisRow = table.getNextRow();
      } catch (IOException io) {
        io.printStackTrace();
        out.println("!!!!!!!!!!!!!! Could not get next Row in DUML table...");
      }
      //Might as well give it an ID here.
      newEnc.setCatalogNumber(Util.generateUUID());
      newEnc.setDWCDateAdded();
      newEnc.setDWCDateLastModified();
      newEnc.setState("approved");
      // Get the date. 
      out.println("---------------- ROW : "+i); 
      try {
        String date = null;
        String startTime = null;
        if (thisRow.get("DATE") != null) {
          if (thisRow.get("StartTime") != null) {
            startTime = thisRow.get("StartTime").toString();
          }
          date = thisRow.get("DATE").toString();   
          if (startTime==null) {
            out.println("No startTime found in DUML Access table for Date : "+date+" SightNo : "+thisRow.get("SIGHTNO"));
          } 
          String verbatimDate = processDateString(date, startTime);
          
          DateTime dateTime = dateStringToDateTime(verbatimDate, "EEE MMM dd hh:mm a yyyy");
          
          newEnc.setVerbatimEventDate(dateTime.toString());          
          newEnc.setDateInMilliseconds(dateTime.getMillis());  
          dates += 1;
          //out.println("--------------------------------------------- DateTime String : "+dateTime.toString()+" Stored startTime : "+startTime);
          //out.println("--------------------------------------- .getDate() produces....  "+newEnc.getDate());
          //  out.println("--- ++++++++ ENTIRE ROW STRING :"+thisRow.toString()+"\n\n");
          if (columnMasterList.contains("DATE") || columnMasterList.contains("StartTime")) {
            columnMasterList.remove("DATE");  
            columnMasterList.remove("StartTime");
          }
        } 
        // Lets crush that into a DateTime for milli's and stuff.. 
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a date for row "+i+" in DUML");
        e.printStackTrace();
        errors +=1;
      }
      
      //get the End Date
      try {
        String et = null;
        String date = null;
        if (thisRow.get("EndTime") != null) {
          date = thisRow.get("DATE").toString();
          et = thisRow.get("EndTime").toString();
          String dateString = processDateString(date, et);
          DateTime dateTime = dateStringToDateTime(dateString, "EEE MMM dd hh:mm a yyyy");
          newEnc.setEndDateInMilliseconds(dateTime.getMillis());
          endTimes += 1;
          //out.println("---------------- End Time : "+et);
          if (columnMasterList.contains("EndTime")) {
            columnMasterList.remove("EndTime");
          }
        }
      } catch (Exception e) {
        //out.println("!!!!!!!!!!!!!! Could not process an endTime for row "+i+" in DUML");
        out.println("Here's the offending date : "+thisRow.get("EndTime").toString());
        e.printStackTrace();
        errors +=1;
      }
      
      // Get SIGHTNO... 
      
      try {
        String sn = null;
        if (thisRow.get("SIGHTNO") != null) {
          sn = thisRow.get("SIGHTNO").toString();
          out.println("SN ---------------- "+sn);
          if (sn.contains("-") && sn.contains("0")) {
            //out.println("SN2 ---------------- "+sn);
            sn = sn.replace("0", "");
            sn = sn.replace("-", "");
          }
          newEnc.setSightNo(sn);    
          sightNos += 1;
          //out.println("---------------- SIGHTNO : "+sn);
          if (columnMasterList.contains("SIGHTNO")) {
            columnMasterList.remove("SIGHTNO");
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a SIGHTNO for row "+i+" in DUML");
        e.printStackTrace();
        errors +=1;
      }
      
      //get the Location
      try {
        String location = null;
        if (thisRow.get("Location") != null) {
          location = thisRow.get("Location").toString();          
          newEnc.setVerbatimLocality(location);    
          // newEnc.setLocationID(location);
          newEnc.setLocation(location);
          newEnc.setLocationID(location);
          locations += 1;
          //out.println("---------------- Location : "+location);
          if (columnMasterList.contains("Location")) {
            columnMasterList.remove("Location");
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a location for row "+i+" in DUML");
        e.printStackTrace();
        errors +=1;
      }
      
      //get the Project
      try {
        String project = null;
        if (thisRow.get("Project") != null) {
          project = thisRow.get("Project").toString();          
          //out.println("---------------- Project : "+project);
          newEnc.setSubmitterProject(project);    
          projects += 1;
          if (columnMasterList.contains("Project")) {
            columnMasterList.remove("Project");
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a project for row "+i+" in DUML");
        e.printStackTrace();
        errors +=1;
      }
      
      // Get the Species ID
      try {
        String speciesId = null;
        if (thisRow.get("SPECIES_ID") != null) {
          speciesId = thisRow.get("SPECIES_ID").toString();          
          //out.println("---------------- Species_ID : "+speciesId);
          newEnc.setGenus(speciesId);
          //newEnc.setSpecificEpithet(speciesId);
          speciesIds += 1;
          if (columnMasterList.contains("SPECIES_ID")) {
            columnMasterList.remove("SPECIES_ID");
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a speciesId for row "+i+" in DUML");
        e.printStackTrace();
        errors +=1;
      }
      
      // Get the Behavior
      try {
        String behavior = null;
        if (thisRow.get("BEHAV STATE") != null) {
          behavior = thisRow.get("BEHAV STATE").toString();          
          //out.println("---------------- Behavior : "+behavior);
          if (Double.parseDouble(behavior) < 9.99) {
            newEnc.setBehavior(behavior);    
            behaviors += 1;            
          }
          if (columnMasterList.contains("BEHAV STATE")) {
            columnMasterList.remove("BEHAV STATE");
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a behavior for row "+i+" in DUML");
        e.printStackTrace();
        errors +=1;
      }
      
      // Get the Comments
      try {
        String comments = null;
        if (thisRow.get("COMMENTS") != null) {
          comments = thisRow.get("COMMENTS").toString();          
          //out.println("---------------- Comments : "+comments);
          newEnc.setComments(comments);    
          comments += 1;
          if (columnMasterList.contains("COMMENTS")) {
            columnMasterList.remove("COMMENTS");
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process comments for row "+i+" in DUML");
        e.printStackTrace();
        errors +=1;
      }
      
      // Get the maximum depth.
      try {
        String depth = null;
        if (thisRow.get("DEPTH") != null) {
          depth = thisRow.get("DEPTH").toString();          
          //out.println("---------------- DEPTH : "+depth);
          Double depthLong = Double.parseDouble(depth);
          if (depthLong < 9.99) {
            newEnc.setMaximumDepthInMeters(depthLong);                
            depths += 1;
          }
          if (columnMasterList.contains("DEPTH")) {
            columnMasterList.remove("DEPTH");
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a MaxDepth for row "+i+" in DUML");
        e.printStackTrace();
        errors +=1;
      }
      
      // Get the decimal latitude..
      try {
        String lat = null;
        if (thisRow.get("LAT") != null) {
          lat = thisRow.get("LAT").toString();          
          //out.println("---------------- Lat : "+lat);
          //Double latDouble = Double.parseDouble(lat);
          BigDecimal bd = new BigDecimal(lat);
          Double db = bd.doubleValue();
          
          newEnc.setDecimalLatitude(db);    
          lats += 1;
          if (columnMasterList.contains("LAT")) {
            columnMasterList.remove("LAT");
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a LAT for row "+i+" in DUML");
        e.printStackTrace();
        errors +=1;
      }
      
      // Get the decimal longitude..
      try {
        String lon = null;
        if (thisRow.get("LONG") != null) {
          lon = thisRow.get("LONG").toString();          
          //out.println("---------------- Lon : "+lon);
          //Double lonDouble = Double.parseDouble(lon);
          BigDecimal bd = new BigDecimal(lon);
          Double db = bd.doubleValue();
          
          newEnc.setDecimalLongitude(db);    
          lons += 1;
          if (columnMasterList.contains("LONG")) {
            columnMasterList.remove("LONG");
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a LONG for row "+i+" in DUML");
        e.printStackTrace();
        errors +=1;
      }
      
      // Get the ending decimal latitude..
      try {
        String lat = null;
        if (thisRow.get("END LAT") != null && !thisRow.get("END LAT").equals("null")) {
          lat = thisRow.get("END LAT").toString();          
          //out.println("---------------- END LAT : "+lat);
          
          if (lat != null && !lat.equals("null") && !lat.equals("")) {
            //Double latDouble = Double.parseDouble(lat);
            
            BigDecimal bd = new BigDecimal(lat);
            Double db = bd.doubleValue();
            
            newEnc.setEndDecimalLatitude(db);    
          }
          
          if (columnMasterList.contains("END LAT")) {
            columnMasterList.remove("END LAT");
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a END LAT for row "+i+" in DUML");
        //e.printStackTrace();
        //errors +=1;
      }
      
      // Get the ending decimal longitude..
      try {
        String lon = null;
        if (thisRow.get("END LONG") != null && !thisRow.get("END LONG").equals("null")) {
          lon = thisRow.get("END LONG").toString();          
          //out.println("---------------- END LON : "+lon);
          
          if (lon != null && !lon.equals("null") && !lon.equals("")) {
            //Double lonDouble = Double.parseDouble(lon);
            BigDecimal bd = new BigDecimal(lon);
            Double db = bd.doubleValue();
            
            newEnc.setEndDecimalLongitude(db);               
          }
          
          if (columnMasterList.contains("END LONG")) {
            columnMasterList.remove("END LONG");
          }
        }
      } catch (Exception e) {
         
        out.println("!!!!!!!!!!!!!! Could not process a END LONG for row "+i+" in DUML");
        //e.printStackTrace();
        //errors +=1;
      }
      
      //Beauscale Measurement
      try {
       Double bs = null;
       Measurement bsm = null;
       if (thisRow.getDouble("BEAUSCALE") != null) {
         bs = thisRow.getDouble("BEAUSCALE");
         if (bs < 9.0 && bs != null) {
           bsm = new Measurement(newEnc.getCatalogNumber(),"BEAUSCALE",bs,"","");
           bsm.setDatasetName("BEAUSCALE");
           bsm.setEventStartDate(newEnc.getDate());
           myShepherd.getPM().makePersistent(bsm);
           columnMasterList.remove("BEAUSCALE");
           newEnc.setMeasurement(bsm, myShepherd);           
         }
         //out.println("---------------- BEAUSCALE : "+bsm.getValue());
       } 
      } catch (Exception e) {
        errors +=1;
        e.printStackTrace();
        out.println("!!!!!!!!!!!!!! Could not process a BEAUSCALE measurement for row "+i+" in DUML");
      }
      
      //Salinity Measurement
      try {
       Double sl = null;
       Measurement slm = null;
       if (thisRow.getDouble("SALINITY") != null) {
         sl = thisRow.getDouble("SALINITY");
         if (sl < 9.99 && sl != null) {
           slm = new Measurement(newEnc.getCatalogNumber(),"SALINITY",sl,"","");
           slm.setDatasetName("SALINITY");
           slm.setEventStartDate(newEnc.getDate());
           myShepherd.getPM().makePersistent(slm);
           columnMasterList.remove("SALINITY");
           newEnc.setMeasurement(slm, myShepherd);           
         }
         //out.println("---------------- BEAUSCALE : "+bsm.getValue());
       } 
      } catch (Exception e) {
        errors +=1;
        e.printStackTrace();
        out.println("!!!!!!!!!!!!!! Could not process a BEAUSCALE measurement for row "+i+" in DUML");
      }
      
      //WaterTemp Measurement
      try {
        Double wt = null;
        Measurement wtm = null;
        if (thisRow.get("WATERTEMP") != null) {
          wt = Double.valueOf(thisRow.get("WATERTEMP").toString());   
          if (wt < 99.9 && wt != null) {
            wtm = new Measurement(newEnc.getCatalogNumber(),"WATERTEMP",wt,"","");
            wtm.setDatasetName("WATERTEMP");
            wtm.setEventStartDate(newEnc.getDate());
            //out.println("---------------- WATERTEMP TEST STRING: "+wt.toString());
            myShepherd.getPM().makePersistent(wtm);
            columnMasterList.remove("WATERTEMP");
            newEnc.setMeasurement(wtm, myShepherd);            
          }
        } 
      } catch (Exception e) {
        errors += 1;
        e.printStackTrace();
        out.println("!!!!!!!!!!!!!! Could not process a WATERTEMP measurement for row "+i+" in DUML");
      }
      
      try {
        ArrayList<Encounter> duplicateEncs = new ArrayList<Encounter>();
        String sightNo = newEnc.getSightNo().toUpperCase().trim();
        String dateForKey = newEnc.getDate().substring(0,11).trim();
        String pairKey = sightNo + dateForKey;
        int duplicates = 0;
        try {
          if (duplicatePairsMap.containsKey(pairKey)) {
            duplicates = duplicatePairsMap.get(pairKey).intValue();
            totalInSightingsArray += duplicates;
          } else {
            duplicates = 1;
          }
        } catch (Exception e) {
          e.printStackTrace(out);
          out.println("Failed to retrieve duplicate number for pairKey : "+pairKey);
        }
        
        out.println("Creating "+duplicates+" encounters for the occurrence with this date/number match.");
        while (duplicateEncs.size() < duplicates ) {
          Encounter dup = (Encounter) deepCopy(newEnc);
          dup.setCatalogNumber(Util.generateUUID());
          duplicateEncs.add(dup);
        }
        
        // Take care of business by generating an ID for the encounter object(s) and persisting it (them). 
        Occurrence occ = null;
        if (duplicateEncs.size() > 0) {
          for (Encounter dups : duplicateEncs) {
            try {
              myShepherd.getPM().makePersistent(dups);  
              myShepherd.commitDBTransaction();
              myShepherd.beginDBTransaction();
              newEncs += 1;
            } catch (Exception e) {
              out.println("Failed to store new Encounter with catalog number : "+dups.getCatalogNumber());
              e.printStackTrace();
            }        
          }          
          // Gonna need an occurrence for all this stuff too. Each of these sightings is technically a group sighting. 
          try {
            occ = new Occurrence(Util.generateUUID(), duplicateEncs.get(0));
            myShepherd.getPM().makePersistent(occ);  
            // What the heck, where did this come from? It's the method that add all the remaining columns as observations, of course!
            processRemainingColumnsAsObservations(occ,columnMasterList,thisRow);
            duplicateEncs.get(0).setOccurrenceID(occ.getOccurrenceID());
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
            newOccs +=1;
          } catch (Exception e) {
            e.printStackTrace(out);
            out.println("Failed to create and store an occurrence for this sighting number.");
          }
        }
                
        if (duplicateEncs.size() > 1) {
          for (int dups=1;dups<duplicateEncs.size();dups++) {
            occ.addEncounter(duplicateEncs.get(dups));
            duplicateEncs.get(dups).setOccurrenceID(occ.getOccurrenceID());
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
          }
        }        
      } catch (Exception e) {
        out.println("Here's where your code Broke:\n\n");
        e.printStackTrace(out); 
      }
    }         
    out.println("There are a total of "+totalInSightingsArray+" valid sightings (have a date and sighting number) to match against.");
    
    
    out.println("Created "+newEncs+" new Encounters and "+newOccs+" new Occurrences.");
    out.println("\n\n************** LAT's vs rows: "+lats+"/"+table.getRowCount());
    out.println("************** LONG's vs rows: "+lons+"/"+table.getRowCount());
    out.println("************** Species ID's vs rows: "+speciesIds+"/"+table.getRowCount());
    out.println("************** Behaviors vs rows: "+behaviors+"/"+table.getRowCount());
    out.println("************** Depths vs rows: "+depths+"/"+table.getRowCount());
    out.println("************** Locations vs rows: "+locations+"/"+table.getRowCount());
    out.println("************** Dates vs rows: "+dates+"/"+table.getRowCount());
    out.println("************** Projects vs rows: "+projects+"/"+table.getRowCount());
    out.println("************** EndTimes vs rows: "+endTimes+"/"+table.getRowCount());
    out.println("************** SIGHTNOS vs rows: "+sightNos+"/"+table.getRowCount());
    out.println("************** Behaviors vs rows: "+behaviors+"/"+table.getRowCount()+"\n\n");
    if (errors > 0) {
      out.println("!!!!!!!!!!!!!!  You got "+errors+" problems and all of them are because of your code.   !!!!!!!!!!!!!!\n\n");
    } 
    out.println("--------------================  REMAINING COLUMNS : "+columnMasterList+"  ================--------------\n\n");
    out.println("******* !!!! TOTALLY CRUSHED IT !!!! *******\n\n");
  }
  
  private void processRemainingColumnsAsObservations(Object obj, ArrayList<String> columnMasterList, Row thisRow) {
    //Lets grab every other little thing in the Column master list and try to process it without the whole thing blowing up.
    // Takes an Encounter, or an Occurrence! Whoa! Even a TissueSample! 
    
    // Lets make this work for the new obs added to the DataCollectionEvent...
    Encounter enc = null;
    Occurrence occ = null;
    TissueSample ts = null;
    String id = null;
    if (obj.getClass().getSimpleName().equals("Encounter")) {
      enc = (Encounter) obj;
      id = ((Encounter) obj).getPrimaryKeyID();
    } 
    if (obj.getClass().getSimpleName().equals("Occurrence")) {
      occ = (Occurrence) obj;
      id = ((Occurrence) obj).getPrimaryKeyID();
    }
    if (obj.getClass().getSimpleName().equals("TissueSample")) {
      ts = (TissueSample) obj;
      id = ((TissueSample) obj).getDataCollectionEventID();
    }
    
    ArrayList<Observation> newObs = new ArrayList<Observation>();
    for (String column : columnMasterList) {
      String value = null;
      try {
        if (thisRow.get(column) != null) {
          value = thisRow.get(column.trim()).toString().trim();
          if (value.length() > 0) {
            Observation ob = new Observation(column.toString(), value, obj, id);
            newObs.add(ob);           
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        out.println("Failed to create and store Observation "+column+" with value "+value+" for encounter "+id);
      }
    }
    if (newObs.size() > 0) {
      try {
        if (enc != null) {
          enc.addBaseObservationArrayList(newObs);
          enc.getBaseObservationArrayList().toString();
        }
        if (occ != null) {
          occ.addBaseObservationArrayList(newObs); 
          occ.getBaseObservationArrayList().toString();
        }
        if (ts != null) {
          ts.addBaseObservationArrayList(newObs); 
          ts.getBaseObservationArrayList().toString();
        }
        out.println("YEAH!!! added "+newObs.size()+" observations to Encounter "+id+" : ");
      } catch (Exception e) {
        e.printStackTrace();
        out.println("Failed to add the array of observations to this object.");
      }        
    }
  }
  
  private void processEffortTable(Table table, Shepherd myShepherd) {
    
    if (!myShepherd.getPM().currentTransaction().isActive()) {
      myShepherd.beginDBTransaction();
    }
    
    out.println("Effort Table has "+table.getRowCount()+" Rows!\n");
    
    int matchedNum = 0;
    int success = 0;
    Row thisRow = null;
    
    for (int i=0;i<table.getRowCount();i++) {
      Survey sv = null;
      SurveyTrack st = null;
      try {
        thisRow = table.getNextRow();
      } catch (IOException io) {
        io.printStackTrace();
        out.println("!!!!!!!!!!!!!! Could not get next Row in SIGHTINGS table...");
      }
      
      String date = null;
      try {
        if (thisRow.get("DATE") != null) {
          date = thisRow.get("DATE").toString();          
          String verbatimDate = date.substring(0, 11) + date.substring(date.length() - 5);
          DateTime dateTime = dateStringToDateTime(verbatimDate, "EEE MMM dd yyyy");
          date = dateTime.toString().substring(0,10);
          sv = new Survey(date);
          myShepherd.getPM().makePersistent(sv);
          myShepherd.commitDBTransaction();
          myShepherd.beginDBTransaction();
          
          st = new SurveyTrack(sv);
          myShepherd.getPM().makePersistent(st);
          myShepherd.commitDBTransaction();
          myShepherd.beginDBTransaction();
          
          sv.addSurveyTrack(st);
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a DATE for row "+i+" in EFFORT");
        e.printStackTrace();
      }
      
      try {
        if (thisRow.get("SurveyHrs") != null) {
          String es = thisRow.getString("SurveyHrs");
          es = es.replace("/","").toUpperCase();
          if (!es.equals("NA")) {
            System.out.println("SurveyHrs resulting string : "+es);
            Double effort = Double.valueOf(es);
            Measurement effortMeasurement = new Measurement();
            effortMeasurement.setUnits("Hours");
            effortMeasurement.setValue(effort);
            myShepherd.getPM().makePersistent(effortMeasurement);
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
            sv.setEffort(effortMeasurement);            
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process SurveyHrs and create a measurement for row "+i+" in EFFORT");
        out.println(thisRow.toString());
        e.printStackTrace();
      }
      
      try {
        if (thisRow.get("Vessel") != null) {
          String effort = thisRow.getString("Vessel");
          st.setVesselID(effort);
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process Vessel for row "+i+" in EFFORT");
        e.printStackTrace();
      }
      
      String project = null;
      try {
        if (thisRow.get("PROJECT") != null) {
          project = thisRow.getString("PROJECT");
          sv.setProjectName(project);
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process PROJECT for row "+i+" in EFFORT");
        e.printStackTrace();
      }
      
      try {
        if (thisRow.get("COMMENTS") != null) {
          String comments = thisRow.getString("COMMENTS");
          sv.addComments(comments);
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process COMMENTS for row "+i+" in EFFORT");
        e.printStackTrace();
      }
      
      ArrayList<Encounter> encsOnThisDate = null;
      try {
        if (thisRow.get("SURVEY AREA") != null) {
          String surveyArea = thisRow.getString("SURVEY AREA");
          // Set this on associated encounters too.  
          st.setLocationID(surveyArea.trim());
          
          encsOnThisDate = myShepherd.getEncounterArrayWithShortDate(date);
          
          out.println("Can we find an enc for this Survey and Track? We have "+encsOnThisDate.size()+" encs to check.");
          boolean matched = false;
          for (Encounter enc : encsOnThisDate) {
            
            String encLoc = null;
            if (enc.getLocation()!=null) {
              encLoc = enc.getLocation().trim();
            }
            String encProj = null;
            if (enc.getObservationByName("Project")!=null) {
              encProj = enc.getSubmitterProject().trim();
            }
             
            if (encLoc != null || encProj != null) {
              out.println("(enc:surveyTrack) Location : "+encLoc+" = "+st.getLocationID()+" Project : "+encProj+" = "+project);
              if (encProj != null && project != null)  {
                if (encProj.contains(project) || project.contains(encProj)) {
                  out.println("MATCH!!! At least on project name... (enc:surveyTrack) Project : "+enc.getObservationByName("Project").getValue()+" = "+project);
                  st.addOccurence(myShepherd.getOccurrence(enc.getOccurrenceID()));
                  sv.addSurveyTrack(st);
                  success++;
                  matched = true;
                } else {
                  out.println("Nope...");
                } 
              } else if (encLoc != null && surveyArea != null) {
                if (enc.getLocationID().contains(surveyArea) || surveyArea.contains(enc.getLocationID())) {
                  out.println("MATCH!!! At least on location ID... (enc:surveyTrack) Location : "+enc.getLocationID()+" = "+st.getLocationID()+" Project : "+enc.getSubmitterProject()+" = "+sv.getProjectName());
                  st.addOccurence(myShepherd.getOccurrence(enc.getOccurrenceID()));
                  sv.addSurveyTrack(st);
                  success++;
                  matched = true;
                } else {
                  out.println("Nope...");
                }
              } 
            } else {
              System.out.println("Location ID for this enc is null!");
            }    
          }
          if (matched==true) {
            matchedNum ++;
          }
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process SURVEY AREA for row "+i+" in EFFORT");
        out.println(thisRow.toString());
        e.printStackTrace(out);
      }
      
    }
    out.println("+++++++++++++ I created surveys and tracks for "+success+" encounters out of "+table.getRowCount()+" lines in the EFFORT table. +++++++++++++");
    out.println("+++++++++++++ There were "+matchedNum+" out of "+table.getRowCount()+" effort table entries connected to an encounter.");
  }
  
  private void processSightings(Table table, Shepherd myShepherd) {
    out.println("Sightings Table has "+table.getRowCount()+" Rows!");    
    String date = null;
    String sightNo = null;
    String idCode = null;
    
    MarkedIndividual indy = null;
    
    int noEnc = 0;
    int addedToExisting = 0;
    int newEnc = 0;
    int rowsProcessed = 0;
    
    Row thisRow = null;
    for (int i=0;i<table.getRowCount();i++) {
      try {
        thisRow = table.getNextRow();
        rowsProcessed += 1;
      } catch (IOException io) {
        io.printStackTrace();
        out.println("!!!!!!!!!!!!!! Could not get next Row in SIGHTINGS table...");
      }
      
      try {
        if (thisRow.get("DATE") != null) {
          date = thisRow.get("DATE").toString();          
          
          String verbatimDate = date.substring(0, 11) + date.substring(date.length() - 5);
          DateTime dateTime = dateStringToDateTime(verbatimDate, "EEE MMM dd yyyy");
          date = dateTime.toString().substring(0,10);
          //out.println("---------------- DATE : "+date);
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a DATE for row "+i+" in SIGHTINGS");
        //e.printStackTrace();
      }
      
      try {
        if (thisRow.get("SIGHTNO") != null) {
          sightNo = thisRow.get("SIGHTNO").toString();          
          //out.println("---------------- SIGHTNO : "+sightNo);          
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a SIGHTNO for row "+i+" in SIGHTINGS");
        //e.printStackTrace();
      }
      
      try {
        if (thisRow.get("ID CODE") != null) {
          idCode = thisRow.get("ID CODE").toString().trim();          
          //out.println("---------------- ID CODE : "+idCode);          
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a ID CODE for row "+i+" in SIGHTINGS");
        //e.printStackTrace();  
      }
      
      // Each sightNo/date pair should have only one encounter with information relevant to all encounters on this occurrence.
      // We need to see how many rows there are on the sightings table that contain this pair, and make a deep copy of the 
      // encounter for each.
      ArrayList<Encounter> encs = null;
      try {
        encs = myShepherd.getEncounterArrayWithShortDate(date);
        if (encs != null) {
          if (encs.size() == 0) {
            noEnc +=1;            
            failedEncs.add(sightNo+date);
            System.out.println("No Encounter for this date! "+date);
            //continue;
          } else {
            for (int e=0;e<encs.size();e++) {
              if (!encs.get(e).getSightNo().equals(sightNo)) {
                encs.remove(encs.get(e));
              }
            }    
            System.out.println("There be "+encs.size()+" Encs for this pair.");            
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        System.out.println("Failed to retrieve an encounter list for ID Number : "+idCode);
      }
      if (encs != null) {
        for (int j=0;j<encs.size();j++) {
          Encounter enc = encs.get(j);
          if (!enc.hasMarkedIndividual() && idCode != null && !idCode.equals("")) {
            try {
              if (!myShepherd.isMarkedIndividual(idCode)) {
                System.out.println("Making new Indy With ID code  : "+idCode);
                indy = new MarkedIndividual(idCode, enc);
                enc.assignToMarkedIndividual(indy.getIndividualID());
                myShepherd.getPM().makePersistent(indy);
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
                newEnc += 1;
                break;
              } else {
                indy = myShepherd.getMarkedIndividual(idCode);
                indy.addEncounter(enc, context);
                enc.assignToMarkedIndividual(indy.getIndividualID());
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
                System.out.println("Adding this encounter to existing Indy : "+indy.getIndividualID()+" Incoming ID : "+idCode);
                addedToExisting += 1; 
                break;
              }
            } catch (Exception e) {
              e.printStackTrace();
              System.out.println("Failed to persist a new Indy for ID Number : "+idCode +" and shortDate "+date);
            }
          }             
        }        
      } else {
        myShepherd.rollbackDBTransaction();
        continue;
      } 
    }
    System.out.println("Dates without attached encounters : "+failedEncs);
    System.out.println("No Encounters to retrieve for date : "+noEnc);
    System.out.println("New Indy created for this encounter sighting number pair : "+newEnc);
    System.out.println("Existing Indy's added to encounters from lists retrieved by date  : "+addedToExisting);
    System.out.println("Rows Processed : "+rowsProcessed);
  }
    
  // Okay, lets try this again.
  private void processBiopsyTable(Table table,Shepherd myShepherd,Table tableDUML) {
    
    ArrayList<String> columnMasterList = getColumnMasterList(table);
    int success = 0;
    out.println("Biopsy Samples Table has "+table.getRowCount()+" Rows!");
    Row thisRow = null;
    
    for (int i=0;i<table.getRowCount();i++) {
      try {
        thisRow = table.getNextRow();
      } catch (IOException io) {
        io.printStackTrace();
        out.println("\n!!!!!!!!!!!!!! Could not get next Row in Biopsy Sample table...\n");
      }
      
      String date = null;
      String time = null;
      String sightNo = null;
      try {
        if (thisRow.get("DateCreated") != null && thisRow.get("SightNo") != null && thisRow.get("Time") != null) {
          date = thisRow.get("DateCreated").toString(); 
          time = thisRow.get("Time").toString();
          sightNo = thisRow.get("SightNo").toString().trim(); 
          columnMasterList.remove("DateCreated");
          columnMasterList.remove("Time");
          columnMasterList.remove("SightNo");
          
          String verbatimDate = date.substring(0, 11) + time.substring(11, time.length() - 5) + date.substring(date.length() - 5);
          DateTime dateTime = dateStringToDateTime(verbatimDate, "EEE MMM dd hh:mm:ss z yyyy");
          date = dateTime.toString().substring(0,10);      
          out.println("Simple Date for this biopsy : "+date);
        }
      } catch (Exception e) {
        e.printStackTrace(out);
        out.println("**********  Failed to grab date and time info from biopsy table.");
      }
      Occurrence occ = null;
      //Encounter thisEnc = null;
      try {
        ArrayList<Encounter> encArr = myShepherd.getEncounterArrayWithShortDate(date);
        if (!encArr.isEmpty()) {
          out.println("Iterating through array of "+encArr.size()+" encounterss to find a  match...");
          for (Encounter enc : encArr) {
            if (enc.getSightNo().equals(sightNo)) {
              occ = myShepherd.getOccurrence(enc.getOccurrenceID());
              //thisEnc = enc;
              out.println("------ MATCH! "+sightNo+" = "+enc.getSightNo()+" Breaking the loop. ------");
              break;
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        out.println("Failed to retrieve Occurrence for this encounter.");
      }
      if (occ != null) {
        out.println("Found a date match for this biopsy! Occurrence:"+occ.getPrimaryKeyID()+". Processing Biopsy...");
        boolean created = processBiopsyRow(thisRow, occ, myShepherd, columnMasterList); 
        if (created) {
          success += 1;          
        }
      }
       
    } 
    out.println("Successfully created "+success+" tissue samples."); 
  }
  
  private boolean processBiopsyRow(Row thisRow, Occurrence occ, Shepherd myShepherd, ArrayList<String> columnMasterList) {
    String sampleId = null;
    // The name sampleID is kinda deceptive for internal wildbook purposes. This ID is only unique for successful biopsy attempts..
    // Unsuccessful biopsys are still recorded as a TissueSample object, as requested. It belongs in the STATE column of the sample.
    TissueSample ts = null;
    try {
      if (occ != null) { 
        try {
          ts = new TissueSample("", sampleId );
          // And load it up.
          try {
            if (!myShepherd.getPM().currentTransaction().isActive()) {
              myShepherd.beginDBTransaction();
            }
            
            String permit = null;
            String sex = null;
            String sampleID = null;
            
            // These fields are the anchors for the tissue sample. Minimum data needed for an entry.
            columnMasterList.remove("Permit");
            if (thisRow.get("Permit") != null) {
              permit = thisRow.getString("Permit").toString();
              ts.setPermit(permit);
            }
            columnMasterList.remove("Sample_ID");
            if (thisRow.get("Sample_ID") != null) {
              sampleID = thisRow.get("Sample_ID").toString();
              
              if (sampleID.toLowerCase().contains("miss")) {
                ts.setState("Miss");
              }
              if (sampleID.toLowerCase().contains("hit no sample")) {
                ts.setState("Hit - No Sample");
              } else {
                ts.setState("Sampled");
              }
            }
            if (thisRow.get("Vessel") != null) {
              String vessel = null;
              vessel = thisRow.getString("Vessel").toString();
              // Lets store this on the occurrence. It needs to be part of the process connecting Surveys-->Tracks-->Occurrences
              Observation v = new Observation("Vessel",vessel,occ,occ.getPrimaryKeyID());
              myShepherd.getPM().makePersistent(v);
              myShepherd.commitDBTransaction();
              myShepherd.beginDBTransaction();
              occ.addObservation(v);
            }
              
            // This should grab physical and satellite tags. Separated for clarity.
            processTags(thisRow, myShepherd, occ);
            columnMasterList.remove("DTAG_ID");
            columnMasterList.remove("SatTag_ID");
            
            // This does exactly what it sounds like it does.
            processRemainingColumnsAsObservations(ts, columnMasterList, thisRow);
            
            if (thisRow.get("Conf_sex") != null) {
              // One of the fields will be a SexAnalysis/BiologicalMeasurement stored on the tissue sample.
              sex = thisRow.getString("Conf_sex").toString();
              SexAnalysis sexAnalysis = new SexAnalysis(Util.generateUUID(), sex,occ.getPrimaryKeyID(),sampleID);
              myShepherd.getPM().makePersistent(sexAnalysis);
              myShepherd.commitDBTransaction();
              myShepherd.beginDBTransaction();
              ts.addGeneticAnalysis(sexAnalysis);
            }
            myShepherd.getPM().makePersistent(ts);
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
            occ.addBaseTissueSample(ts);
          } catch (Exception e) {
            e.printStackTrace();
            out.println("\n Failed to save created tissue sample to occurrence.");
          }
          
          myShepherd.commitDBTransaction();
          System.out.println("Created a Tissue Sample for Occ"+occ.getPrimaryKeyID());
          return true;
        } catch (Exception e) {
          e.printStackTrace();
          out.println("\nFailed to make the tissue sample.");
        }        
      }        
    } catch (Exception e) {  
      out.println("\nFailed to validate Occ ID : "+occ.getPrimaryKeyID()+" and sampleID : "+sampleId+" for TissueSample creation.");
      
    }
     
    occ.getBaseTissueSampleArrayList().toString();
    return false;
  }
  
  private void processTags(Row thisRow, Shepherd myShepherd, Occurrence occ) {
    String satTagID = null;
    String dTagID = null;
    
    if (thisRow.get("SatTag_ID") != null || thisRow.get("DTAG_ID") != null) { 
      if (occ != null) {
        try {
          System.out.println("Gonna try to make a tag for this Enc.");
          if (thisRow.get("SatTag_ID") != null) {
            satTagID = thisRow.get("SatTag_ID").toString();
            SatelliteTag st = new SatelliteTag();
            st.setName(satTagID);
            st.setId(Util.generateUUID());
            occ.addBaseSatelliteTag(st);
            System.out.println("Created a SatTag for occurrence "+occ.getPrimaryKeyID());
          }
          if (thisRow.get("DTAG_ID") != null) {
            dTagID = thisRow.get("DTAG_ID").toString();
            DigitalArchiveTag dt = new DigitalArchiveTag();
            dt.setDTagID(dTagID);
            dt.setId(Util.generateUUID());
            occ.addBaseDigitalArchiveTag(dt);
            System.out.println("Created a DTag for occurrence "+occ.getPrimaryKeyID());
          }       
        } catch (Exception e) {
          e.printStackTrace();
          out.println("Caught exception while creating tags for biopsy.");
        }       
      } else {
        System.out.println("Didn't find an encounter to add this tag ");
      }           
    }
  }
  	
  private void buildEncounterDuplicationMap(Table table, Shepherd myShepherd) {
    out.println("Building map of duplicate encounters...");
    String sightNo = null;
    String date = null;
    
    int sumOfValues = 0;
    int rowsProcessed = 0;
    Row thisRow = null;
    for (int i=0;i<table.getRowCount();i++) {
      try {
        thisRow = table.getNextRow();
        rowsProcessed += 1;
      } catch (IOException io) {
        io.printStackTrace();
        out.println("!!!!!!!!!!!!!! Failed to retrieve row while building duplicate Encounter map.");
      }
      
      try {
        if (thisRow.get("DATE") != null) {
          date = thisRow.get("DATE").toString();          
          
          String verbatimDate = date.substring(0, 11) + date.substring(date.length() - 5);
          DateTime dateTime = dateStringToDateTime(verbatimDate, "EEE MMM dd yyyy");
          date = dateTime.toString().substring(0,10);
          //out.println("---------------- DATE : "+date);
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a DATE for row "+i+" in SIGHTINGS");
        e.printStackTrace();
      }
      
      try {
        if (thisRow.get("SIGHTNO") != null) {
          sightNo = thisRow.get("SIGHTNO").toString();          
          //out.println("---------------- SIGHTNO : "+sightNo);          
        }
      } catch (Exception e) {
        out.println("!!!!!!!!!!!!!! Could not process a SIGHTNO for row "+i+" in SIGHTINGS");
        e.printStackTrace();
      }
      
      String pairKey = sightNo+date;
      if (!duplicatePairsMap.containsKey(pairKey)) {
        duplicatePairsMap.put(pairKey.trim(), 1);
        sumOfValues +=1;
      } else {        
        Integer thisVal = duplicatePairsMap.get(pairKey) + 1;
        duplicatePairsMap.replace(pairKey.trim(),thisVal);
        sumOfValues +=1;
      }
    }
    
    out.println("Duplicate Pairs : "+duplicatePairsMap.toString());
    out.println("Sum of Duplicate Pair HashMap Values : "+sumOfValues);
    out.println("Total duplicate repairs recorded : "+duplicatePairsMap.size());
    out.println("Actual rows processed : "+rowsProcessed);
  }
  */
  
  private DateTime dateStringToDateTime(String verbatim, String format) {
    DateFormat fm = new SimpleDateFormat(format);
    Date d = null;
    try {
      d = (Date)fm.parse(verbatim);    
    } catch (ParseException pe) {
      pe.printStackTrace();
      out.println("Barfed Parsing a Datestring... Format : "+format);
    }
    DateTime dt = new DateTime(d);
    
    return dt;
  }
  
  private String processDateString(String date, String startTime) {
    String justDate = date.substring(0,11);
    String years = date.substring(date.length() - 5);
    String formattedStartTime = formatMilitaryTime(startTime);
    String finalDateTimeString = justDate + formattedStartTime + years;
    
    return finalDateTimeString;
  }
  
  private String formatMilitaryTime(String mt) {
    // The parsing breaks on military time formatted like "745" instead of "0745"
    // Stupid timey stuff. Sometimes there are colons, sometimes not. Hence all the if's.
    try {
      if (mt.contains(":")) {
        mt = mt.replace(":", "");
      }
      if (mt.length() < 3 || mt.equals(null) || mt.equals("") || mt == null || Integer.parseInt(mt) > 2400) {
        mt = "0000";
      }
      if (mt.length() < 4) {
        mt = "0" + mt;
      }      
    } catch (Exception e) {
      // Is it weird and malformed? Lets just auto set it. 
      System.out.println("Couldn't find startTime : "+mt+", setting to midnight.");
      mt = "0000";
      //e.printStackTrace();
    }
    DateTimeFormatter in = DateTimeFormat.forPattern("HHmm"); 
    DateTimeFormatter out = DateTimeFormat.forPattern("hh:mm a"); 
    DateTime mtFormatted = in.parseDateTime(mt); 
    String standard = out.print(mtFormatted.getMillis());
    
    return standard;
  }
    
  public static Object deepCopy(Object orig) {
    Object obj = null;
    try {
        // Write the object out to a byte array
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(orig);
        out.flush();
        out.close();

        ObjectInputStream in = new ObjectInputStream(
            new ByteArrayInputStream(bos.toByteArray()));
        obj = in.readObject();
    }
    catch(IOException e) {
      e.printStackTrace();
      System.out.println("Failed to clone this object.");
    }
    catch(ClassNotFoundException cnfe) {
      System.out.println("Failed to clone this object - Class Not Found.");
      cnfe.printStackTrace();
    }
    return obj;
  }
}


  
  
  
  
  
  