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

/**
 * Uploads an SRGD CSV file for data import
 *
 * @author jholmber
 */
public class ImportSRGD extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Shepherd myShepherd = new Shepherd();

    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
    File tempSubdir = new File(webappsDir, "temp");
    if(!tempSubdir.exists()){tempSubdir.mkdir();}

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String fileName = "None";
    
    StringBuffer messages=new StringBuffer();
    
    boolean successfullyWroteFile=false;

    File finalFile=new File(tempSubdir,"temp.csv");
    
    try {
      MultipartParser mp = new MultipartParser(request, (CommonConfiguration.getMaxMediaSizeInMegabytes() * 1048576));
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

            //File thisSharkDir = new File(encountersDir.getAbsolutePath() +"/"+ encounterNumber);
            //if(!thisSharkDir.exists()){thisSharkDir.mkdir();}
            finalFile=new File(tempSubdir, fileName);
            filePart.writeTo(finalFile);
            successfullyWroteFile=true;
          }
        }
      }


      

        try {
          if(successfullyWroteFile){
            
            //OK, we have our CSV file
            //let's import
            CSVReader reader = new CSVReader(new FileReader(finalFile));
            List<String[]> allLines = reader.readAll();
            
            //let's detect the size of this array by reading the number of header columns in row 0
            String[] headerNames=allLines.get(0);
            int numColumns = headerNames.length;
            int numRows = allLines.size();
           
            
            for(int i=1;i<numRows;i++){
              
              String[] line=allLines.get(i);
              
              Encounter enc=new Encounter();
              
              myShepherd.beginDBTransaction();
              
              //line[0] is the sample_ID
              String encNumber=line[0].trim();
              if(myShepherd.isEncounter(encNumber)){
                enc=myShepherd.getEncounter(encNumber);
              }
              else{
                enc.setCatalogNumber(encNumber);
              }
              
              //line[1] is the IndividualID
              String individualID=line[1].trim();
              if(!individualID.equals("")){
                enc.setIndividualID(individualID);
              }
              
              //line[2] is the latitude
              String latitude=line[2].trim();
              if(!latitude.equals("")){
                try{
                  Double lat=new Double(latitude);
                  enc.setDecimalLatitude(lat);
                }
                catch(NumberFormatException nfe){
                  messages.append(enc.getCatalogNumber()+": Latitude hit a NumberFormatException and could not be imported. The listed value was: "+latitude);
                }
              }
              
              //line[3] is the latitude
              String longitude=line[3].trim();
              if(!longitude.equals("")){
                try{
                  Double longie=new Double(longitude);
                  enc.setDecimalLatitude(longie);
                }
                catch(NumberFormatException nfe){
                  messages.append(enc.getCatalogNumber()+": Longitude hit a NumberFormatException and could not be imported. The listed value was: "+longitude);
                }
              }
              
              //line[4] is the date_time
              
              myShepherd.commitDBTransaction();
              
             
              if(!individualID.equals("")){
                MarkedIndividual indie=new MarkedIndividual();
                myShepherd.beginDBTransaction();
                
                Encounter enc2=myShepherd.getEncounter(encNumber);
                
                if(myShepherd.isMarkedIndividual(individualID)){
                  indie=myShepherd.getMarkedIndividual(individualID);
                }
                else{
                  indie.setIndividualID(individualID);
                }
                indie.addEncounter(enc2);
                
                myShepherd.commitDBTransaction();
              }
              
            }
            
            
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
        }


        if (!locked) {
          myShepherd.commitDBTransaction();
          myShepherd.closeDBTransaction();
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Success!</strong> I have successfully uploaded and imported your SRGD CSV file.");
          
          if(messages.equals("")){messages.toString().equals("None");}
          out.println("The following error messages were reported during the import process:<br />"+messages );

          out.println(ServletUtilities.getFooter());
          } 
      
    } 
    catch (IOException lEx) {
      lEx.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to upload your SRGD CSV. Please contact the webmaster about this message.");
      out.println(ServletUtilities.getFooter());
    } 
    catch (NullPointerException npe) {
      npe.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to import SRGD data as no file was specified.");
      out.println(ServletUtilities.getFooter());
    }
    out.close();
  }


}


