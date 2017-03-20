package org.ecocean.servlet.importer;

import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.ecocean.*;
import org.ecocean.servlet.*;
import org.joda.time.DateTime;
import org.ecocean.media.*;

//import org.apache.poi.hssf.usermodel.*;
//import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ImportBass extends HttpServlet {
  static PrintWriter out;
  static String context; 
  private static ArrayList<String> unmatchedFiles = new ArrayList<String>();
  
  
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }
  
  public void doGet(HttpServletRequest request,  HttpServletResponse response) throws ServletException,  IOException {
    doPost(request,  response);
  }
  
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,  IOException {
    out = response.getWriter();
    context = ServletUtilities.getContext(request);
   
    Shepherd myShepherd = null;
    myShepherd = new Shepherd(context);
    if (!CommonConfiguration.isWildbookInitialized(myShepherd)) {
      myShepherd.beginDBTransaction();
      System.out.println("WARNING: Wildbook not initialized. Starting Wildbook");    
      StartupWildbook.initializeWildbook(request, myShepherd);
      myShepherd.commitDBTransaction();
    }
    
    boolean committing = (request.getParameter("commit")!=null && !request.getParameter("commit").toLowerCase().equals("false"));
    
    myShepherd.beginDBTransaction();
    AssetStore assetStore = AssetStore.getDefault(myShepherd);
    myShepherd.commitDBTransaction();
    
    String excelDir = "/opt/bass_excel/";
    if (request.getParameter("exceldir") != null) excelDir = request.getParameter("exceldir");
    File[] excelFileList = null;
    try {
      excelFileList = getFiles(exceldir);
      try {
        for (File file : excelFileList) {
          out.println("\n++ Processing Excel File: "+file.getName()+" at "+ file.getAbsolutePath());
          processExcel(file, response, request, committing);
        }              
      } catch (Exception e) {
        e.printStackTrace();
        out.println("Choked on the next file.");
      }
    } catch (Exception e) {
      e.printStackTrace();
      out.println("!!!! Exception While Grabbing File List from "+exceldir+" !!!!");
    }
    unmatchedFilenames("", "get");
    out.close();  
    myShepherd.closeDBTransaction();
  }
  
  public void processExcel(File dataFile, HttpServletResponse response, HttpServletRequest request, boolean committing) throws IOException {
    FileInputStream fs = new FileInputStream(dataFile);
    XSSFWorkbook wb = new XSSFWorkbook(fs);
    XSSFSheet sheet = wb.getSheetAt(0);
    XSSFRow row;
    
    if (wb.getNumberOfSheets() < 1) {  
      out.println("!!! XSSFWorkbook did not find any sheets. !!!");
    } else if (sheet.getClass() == null) {
      out.println("!!! Sheet was not successfully extracted. !!!");
    } else {
      out.println("+++ Success creating FileInputStream and XSSF Worksheet +++");
    }
    int numSheets = wb.getNumberOfSheets();
    int rows = sheet.getPhysicalNumberOfRows();
    int cols = sheet.getRow(0).getPhysicalNumberOfCells(); 
    
    out.println("Num Sheets = "+numSheets);
    out.println("Num Rows = "+rows);
    out.println("Num Columns = "+cols);
    out.println("Committing ? = "+String.valueOf(committing));
    
    
  }
  
  
  public File[] getFiles(String path) {
    File[] arr;
    try {
      File folder = new File(path);
      arr = folder.listFiles();
      return arr;
    } catch (Exception e) {
      System.out.println("+++++ ERROR: Failed to get list of files from folder. +++++");
      e.printStackTrace();
      return null;
    }
  }
  
  public Integer getInteger(XSSFRow row, int i) {
    try {
      double val = row.getCell(i).getNumericCellValue();
      return new Integer( (int) val );
    }
    catch (Exception e){}
    return null;
  }
  
  public Double getDouble(XSSFRow row, int i ) {
    try {
      double val = row.getCell(i).getNumericCellValue();
      return new Double( (double) val );
    } catch (Exception e) {
      return null;
    }
  }

  public String getString(XSSFRow row, int i) {
    try {
      String str = row.getCell(i).getStringCellValue();
      if (str.equals("")) return null;
      return str;
    }
    catch (Exception e) {}
    return null;
  }
  
  public String getStringOrIntString(XSSFRow row, int i) {
    try {
      String str = row.getCell(i).getStringCellValue();
      if (str.equals("")) return null;
      return str;
    }
    catch (Exception e) { try {
      return getInteger(row, i).toString();
    }
    catch (Exception e2) {} }

    return null;
  }
  
  public void unmatchedFilenames(String name, String flag) {
    if (flag.equals("store")) {
      unmatchedFiles.add(name);
    }
    if (flag.equals("get")) {
      for (String file : unmatchedFiles) {
        out.println("Unmatched File : "+file);
      }
      out.println("Total of "+Integer.toString(unmatchedFiles.size())+" Files Not Matched.");
    } 
  }
}