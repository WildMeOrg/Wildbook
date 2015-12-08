/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.servlet.importer;

import com.oreilly.servlet.multipart.*;

import org.ecocean.*;
import org.ecocean.servlet.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;

import au.com.bytecode.opencsv.CSVReader;

import java.util.List;
import java.util.Iterator;

import org.joda.time.*;
import org.joda.time.format.*;

import java.lang.IllegalArgumentException;

import org.ecocean.genetics.*;

import java.util.ArrayList;
import java.util.StringTokenizer;

/* imports for dealing with spreadsheets and .xlsx files */
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Uploads an Excel file for data import
 *
 * @author drewblount
 */

/**
 * Built initially from a copy of Jason Holmberg's org.ecocean.servlet.importer.ImportSRGD
 * @author drewblount
 *
 */
public class ImportExcel extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    System.out.println("\n\nStarting ImportExcel servlet...");

    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File tempSubdir = new File(webappsDir, "temp");
    if(!tempSubdir.exists()){tempSubdir.mkdirs();}
    System.out.println("\n\n     Finished directory creation...");


    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String fileName = "None";

    StringBuffer messages=new StringBuffer();

    boolean successfullyWroteFile=false;

    File finalFile=new File(tempSubdir,"temp.csv");

    try {
      MultipartParser mp = new MultipartParser(request, (CommonConfiguration.getMaxMediaSizeInMegabytes(context) * 1048576));
      Part part;
      while ((part = mp.readNextPart()) != null) {
        String name = part.getName();
        if (part.isParam()) {


          // it's a parameter part
          ParamPart paramPart = (ParamPart) part;
          String value = paramPart.getStringValue();


        }

        if (part.isFile()) {
          FilePart filePart = (FilePart) part;
          fileName = ServletUtilities.cleanFileName(filePart.getFileName());
          if (fileName != null) {
            System.out.println("     Trying to upload file: "+fileName);
            //File thisSharkDir = new File(encountersDir.getAbsolutePath() +"/"+ encounterNumber);
            //if(!thisSharkDir.exists()){thisSharkDir.mkdirs();}
            finalFile=new File(tempSubdir, fileName);
            filePart.writeTo(finalFile);
            successfullyWroteFile=true;
            System.out.println("\n\n     I successfully uploaded the file!");
          }
        }
      }      

      try {
        if (successfullyWroteFile) {

          System.out.println("\n\n     Starting file content import...");

          // this line assumes there is an .xlsx file, but a better version would inspect the extension of the uploaded file and handle accordingly 
          FileInputStream excelInput = new FileInputStream(finalFile);
          //Create Workbook instance holding reference to .xlsx file
          XSSFWorkbook workbook = new XSSFWorkbook(excelInput);

          //Get first/desired sheet from the workbook
          XSSFSheet sheet = workbook.getSheetAt(0);

          //Iterate through each rows one by one
          Iterator<Row> rowIterator = sheet.iterator();

          while (rowIterator.hasNext())
          {
            Row row = rowIterator.next();
            //For each row, iterate through all the columns
            Iterator<Cell> cellIterator = row.cellIterator();

            while (cellIterator.hasNext())
            {
              Cell cell = cellIterator.next();
              //Check the cell type and format accordingly
              switch (cell.getCellType())
              {
                case Cell.CELL_TYPE_NUMERIC:
                  System.out.print(cell.getNumericCellValue() + "t");
                  break;
                case Cell.CELL_TYPE_STRING:
                  System.out.print(cell.getStringCellValue() + "t");
                  break;
              }
            }
            System.out.println("");
          }
          System.out.println("The excel file has been imported.");
          }
          else{
            locked=true;
            System.out.println("ImportSRGD: For some reason the import failed without exception.");
          }


        } 
        catch (Exception le) {
          locked = true;
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          le.printStackTrace();
        }


        if (!locked) {
          myShepherd.commitDBTransaction();
          myShepherd.closeDBTransaction();
          out.println(ServletUtilities.getHeader(request));
          out.println("<p><strong>Success!</strong> I have successfully uploaded and imported your SRGD CSV file.</p>");

          if(messages.toString().equals("")){messages.append("None");}
          out.println("<p>The following error messages were reported during the import process:<br /><ul>"+messages+"</ul></p>" );

          out.println("<p><a href=\"appadmin/import.jsp\">Return to the import page</a></p>" );


          out.println(ServletUtilities.getFooter(context));
        } 

      } 
      catch (IOException lEx) {
        lEx.printStackTrace();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I was unable to upload your SRGD CSV. Please contact the webmaster about this message.");
        out.println(ServletUtilities.getFooter(context));
      } 
      catch (NullPointerException npe) {
        npe.printStackTrace();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I was unable to import SRGD data as no file was specified.");
        out.println(ServletUtilities.getFooter(context));
      }
      out.close();
    }


  }


