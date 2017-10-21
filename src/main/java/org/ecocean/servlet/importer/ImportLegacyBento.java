package org.ecocean.servlet.importer;

import org.json.JSONObject;

import com.healthmarketscience.jackcess.Row;
import com.opencsv.*;
import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.ecocean.*;
import org.ecocean.genetics.TissueSample;
import org.ecocean.servlet.*;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.ecocean.media.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.ss.usermodel.DataFormatter;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class ImportLegacyBento extends HttpServlet {
  /**
   * 
   */
  
  private static final long serialVersionUID = 1L;
  private static PrintWriter out;
  private static String context; 
  //private String messages;
  
  private ArrayList<Survey> masterSurveyArr = new ArrayList<Survey>();
  
  
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request,  HttpServletResponse response) throws ServletException,  IOException {
    doPost(request,  response);
  }
  
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,  IOException { 
    out = response.getWriter();
    context = ServletUtilities.getContext(request);
    out.println("=========== Preparing to import legacy bento CSV file. ===========");
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("ImportLegacyBento.class");
    
    out.println("Grabbing all CSV files... ");
    // Grab each CSV file, make switches similar to access import.
    // Stub a method for each one. 
    // Find the DUML equivalent -or- start with surveys. 
    String dir = "/opt/dukeImport/DUML Files for Colin-NEW/Raw Data files/tables_170303/";
    File rootFile = new File(dir);
  
    if (rootFile.exists()) {
      out.println("File path: "+rootFile.getAbsolutePath());
    
      CSVReader effortCSV = grabReader(new File (rootFile, "efforttable_final.csv"));
      CSVReader biopsyCSV = grabReader(new File (rootFile, "biopsytable_final.csv"));
      CSVReader followsCSV = grabReader(new File (rootFile, "followstable_final.csv"));
      CSVReader sightingsCSV = grabReader(new File (rootFile, "sightingstable_final.csv"));
      CSVReader surveyLogCSV = grabReader(new File (rootFile, "surveylogtable_final.csv"));
      CSVReader tagCSV = grabReader(new File (rootFile, "tagtable_final.csv"));
      
      if (true) {
        processSurveyLogFile(myShepherd, surveyLogCSV);
        surveyLogCSV.close();
      }
      if (true) {
        processEffortFile(myShepherd, effortCSV);
        effortCSV.close();
      }
      if (true) {
        processBiopsy(myShepherd, biopsyCSV);
        biopsyCSV.close();
      }
      if (true) {
        processFollows(myShepherd, followsCSV);
        followsCSV.close();
      }
      if (true) {
        processSightings(myShepherd, sightingsCSV);
        sightingsCSV.close();
      }
      if (true) {
        processTags(myShepherd, tagCSV);
        tagCSV.close();
      } 
      
      clearMasterArrs();
    
    } else {
      out.println("The Specified Directory Doesn't Exist.");
    }   
    myShepherd.closeDBTransaction();
    out.close();
  }
  
  private void clearMasterArrs() {
    // We can empty these after all objects are created. 
    // Assists with multiple runs while importing.
    masterSurveyArr.clear();
  }
 
  private CSVReader grabReader(File file) {
    CSVReader reader = null;
    try {
      reader = new CSVReader(new FileReader(file));
    } catch (FileNotFoundException e) {
      System.out.println("Failed to retrieve CSV file at "+file.getPath());
      e.printStackTrace();
    }
    return reader;
  }
  
  private void processSurveyLogFile(Shepherd myShepherd, CSVReader surveyLogCSV) {
    System.out.println("Processing SURVEYLOG? "+surveyLogCSV.verifyReader());
    int totalSurveys = 0;
    int totalRows = 0;
    Iterator<String[]> rows = surveyLogCSV.iterator();
    String[] columnNameArr = rows.next();
    Survey sv = null;
    
    while (rows.hasNext()) {
      totalRows += 1;
      String[] rowString = rows.next();
      sv = processSurveyLogRow(columnNameArr,rowString);
      if (sv!=null) {
        myShepherd.beginDBTransaction();    
        try {
          myShepherd.getPM().makePersistent(sv);
          myShepherd.commitDBTransaction();
          masterSurveyArr.add(sv);
          //System.out.println(sv.getID());
          totalSurveys += 1;
        } catch (Exception e) {
          myShepherd.rollbackDBTransaction();
          e.printStackTrace();
          out.println("Could not persist this Survey from SURVEYLOG : "+Arrays.toString(rowString));
        }        
      }
    }
    out.println("Created "+totalSurveys+" surveys out of "+totalRows+" rows in SURVEYLOG file.");
  }
  
  public Survey processSurveyLogRow(String[] names, String[] values ) {
    ArrayList<String> obsColumns = new ArrayList<String>();
    Survey sv = null;
    // explicit column for date in surveylog is #34 
    if (sv==null) {
      sv = surveyInstantiate(values[34]);
    }
    
    for (int i=0;i<names.length;i++) {
      // Make if val=N/A a precursor to all processing, not a check for each.
      if (values[i]!=null&&!values[i].equals("N/A")&&!values[i].equals("")) {
        if (names[i]!=null) {
          if (names[i].equals("Project")) {
            sv.setProjectName(values[i]);
            obsColumns.remove("Project");
          }        
          if (names[i].equals("Comments")) {
            try {
              sv.addComments(values[i]);            
              obsColumns.remove("Comments");
            } catch (NullPointerException npe) {
              npe.printStackTrace();
              System.out.println(values[i]);
            }          
          }        
        }        
      }
    }
    return sv;
  }
  
  private void processEffortFile(Shepherd myShepherd, CSVReader effortCSV) {
    System.out.println("Processing EFFORT? "+effortCSV.verifyReader());
    // Why stop now?
    int totalSurveys = 0;
    int totalRows = 0;
    Iterator<String[]> rows = effortCSV.iterator();
    // Just grab the first one. It has all the column names, and theoretically the maximum length of each row. 
    String[] columnNameArr = rows.next();
    Survey sv = null;
    while (rows.hasNext()) {
      totalRows += 1;
      String[] rowString = rows.next();
      sv = processEffortRow(columnNameArr,rowString);
      myShepherd.beginDBTransaction();        
      out.println("Survey returned to processEffort method :"+sv.getID());
      try {
        out.println("Next survey to save: "+sv.toString()+" Total number: "+totalRows);
        myShepherd.getPM().makePersistent(sv);
        myShepherd.commitDBTransaction();
        masterSurveyArr.add(sv);
        totalSurveys += 1;
      } catch (Exception e) {
        myShepherd.rollbackDBTransaction();
        e.printStackTrace();
        out.println("Could not persist this Survey from EFFORT : "+Arrays.toString(rowString));
      }
      out.println("Created "+totalSurveys+" surveys out of "+totalRows+" rows in EFFORT file.");
    }
  }
  
  private Survey surveyInstantiate(String date) {
    Survey sv = null;
    try {
      date = formatDate(date);           
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (date!=null) {
      sv = new Survey(date);          
    } else {
      sv = new Survey();
      sv.setID(Util.generateUUID());
      sv.setDWCDateLastModified();
    }
    return sv;
  }
  
  private Survey checkMasterArrForSurvey(String[] names, String[] values) {
    //explicit column # for date in surveylog is 38 ("" project "" vessel)
    //The names and values are from the effort table.
    //The only surveys available in the arr should be from the survey log table. 
    String date = formatDate(values[38]);
    String project = values[28].trim();
    String vessel = values[36].trim();
    for (Survey arrSv : masterSurveyArr) {
      if (arrSv.getDate()!=null&&arrSv.getDate().equals(date)) {
        if (arrSv.getProjectName()!=null&&arrSv.getProjectName().equals(project)) {
          out.println("Found match in Array: "+arrSv.getProjectName()+" = "+project);
          return arrSv;
        }
        if (arrSv.getObservationByName("Vessel")!=null&&arrSv.getObservationByName("Vessel").getValue().equals(vessel)) {
          out.println("Found match in Array: "+arrSv.getObservationByName("Vessel").getValue()+" = "+vessel);
          return arrSv;
        }
      }
    }
    return null;
  }
  
  private Survey processEffortRow(String[] names, String[] values) {
    out.println("_________________________________________________________________________");
    ArrayList<String> obsColumns = new ArrayList<String>();
    Survey sv = null;
    // Explicit column index for date in effort is #38.
    if (names[38].equals("Date Created")) {
      try {
        sv = checkMasterArrForSurvey(names, values);          
      } catch (Exception e) {
        //out.println(sv.getID()+" "+sv.getDate()+" "+sv.getProjectName()+" "+sv.getComments());
        e.printStackTrace();
      }
      if (sv==null) {
        try {
          sv = surveyInstantiate(values[38]);          
        } catch (NullPointerException npe) {
          System.out.println("NPE while trying to instantiate survey.");
          npe.printStackTrace();
        }
      }
    }
    for (int i=0;i<names.length;i++) {
      // Make if val=N/A a precursor to all processing, not a check for each.
      if (values[i]!=null&&!values[i].equals("N/A")&&!values[i].equals("")) {
        if (names[i]!=null) {
          if (names[i].equals("Comments")&&values[i]!=null&&!values[i].equals("")) {
            try {
              sv.addComments(values[i]);            
              obsColumns.remove("Comments");
              out.println("Comments? "+values[i]);
            } catch (NullPointerException npe) {
              npe.printStackTrace();
              System.out.println(values[i]);
            }          
          }
          if (names[i].equals("Project")&&values[i]!=null&&!values[i].equals("")) {    
            try {
              sv.setProjectName(values[i]);
              out.println("Project? "+values[i]);
              obsColumns.remove("Project");
            } catch (NullPointerException npe) {
              npe.printStackTrace();
              System.out.println(values[i]);
            }            
          }        
          if (names[i].equals("Filename")&&values[i]!=null&&!values[i].equals("")) {
            try {
              out.println("Filename as comment? "+values[i]+" Existing? "+sv.getComments());
              sv.addComments("Comments to add..."); 
              out.println("--1--");
              obsColumns.remove("Filename");
              out.println("--2--");
            } catch (NullPointerException npe) {
              npe.printStackTrace();
              System.out.println(values[i]);
            }          
          }
        }        
      }
    }
    out.println("--3--");
    out.println(sv.getID());
    return sv;
  }

  private void processFollows(Shepherd myShepherd, CSVReader followsCSV) {
    System.out.println(followsCSV.verifyReader());
    
  }
  private void processBiopsy(Shepherd myShepherd, CSVReader biopsyCSV) {
    System.out.println(biopsyCSV.verifyReader());
    
  }
  private void processSightings(Shepherd myShepherd, CSVReader sightingsCSV) {
    System.out.println(sightingsCSV.verifyReader());
  
  }
  private void processTags(Shepherd myShepherd, CSVReader tagCSV) {
    System.out.println(tagCSV.verifyReader());
  }
  
  private void processRemainingColumnsAsObservations(Object obj, ArrayList<String> columnList, Row thisRow) {
    Encounter enc = null;
    Occurrence occ = null;
    TissueSample ts = null;
    Survey sv = null;
    
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
      id = ((TissueSample) obj).getSampleID();
    }
    if (obj.getClass().getSimpleName().equals("Survey")) {
      sv = (Survey) obj;
      id = ((Survey) obj).getID();
    }
    
    ArrayList<Observation> newObs = new ArrayList<Observation>();
    for (String column : columnList) {
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
        if (ts != null) {
          sv.addBaseObservationArrayList(newObs); 
          sv.getBaseObservationArrayList().toString();
        }
        out.println("Added "+newObs.size()+" observations to "+obj.getClass().getSimpleName()+" "+id+" : ");
      } catch (Exception e) {
        e.printStackTrace();
        out.println("Failed to add the array of observations to this object.");
      }        
    }
  }
  
  private String formatDate(String rawDate) {
    String date = null;
    DateTime dt = null;
    //out.println("Raw Date Created : "+rawDate);
    if (rawDate!=null&&rawDate.length()>16) {
      try {
        if (rawDate.endsWith("AM")||(rawDate.endsWith("PM"))){
          dt = dateStringToDateTime(rawDate,"MMM d, yyyy, h:m a");
        } else if (String.valueOf(rawDate.charAt(3)).equals(" ")) {
          dt = dateStringToDateTime(rawDate,"MMM dd, yyyy, h:m");          
        } else if (String.valueOf(rawDate.charAt(4)).equals("-")) {
          dt = dateStringToDateTime(rawDate,"yyyy-MM-dd'T'kk:mm:ss"); 
        }
        date = dt.toString().substring(0,10);        
      } catch (Exception e) {
        out.println("*** Here's an unparseable date: "+rawDate+" ***");
      }
    } 
    return date;
  }  
  
  private DateTime dateStringToDateTime(String verbatim, String format) {
    DateFormat fm = new SimpleDateFormat(format);
    Date d = null;
    try {
      d = (Date)fm.parse(verbatim);    
    } catch (ParseException pe) {
      pe.printStackTrace();
      out.println("Barfed Parsing a Datestring... Format : "+format+", Verbatim : "+verbatim);
    }
    DateTime dt = new DateTime(d);
    return dt;
  }
}
  
  
  
  
  
  
  
  