/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2012 Jason Holmberg
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

package org.ecocean.servlet;

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;
import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;


/**
 * 
 * This servlet allows the user to upload a processed patterning file for use with the MantaMatcher algorithm.
 * @author jholmber
 *
 */
public class EncounterAddMantaPattern extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Shepherd myShepherd = new Shepherd();
    myShepherd.beginDBTransaction();
    
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    //if(!encountersDir.exists()){encountersDir.mkdir();}
    
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean removedJPEG = false, locked = false;
    String fileName = "mantaProcessedImage_CR.jpg"; 
    String enhancedFileName = "mantaProcessedImage_EH.jpg"; 
    String encounterNumber = "";
    String action="imageadd";
    
    StringBuffer resultComment=new StringBuffer();
    
    if((request.getParameter("action")!=null)&&(request.getParameter("action").equals("imageremove"))){action="imageremove";}

    try {
      if(action.equals("imageremove")){
        //eliminate the previous JPG version of this file if it existed                         //eliminate the previous JPG if it existed
        encounterNumber = request.getParameter("number");
        
        
        try {

          Encounter enc=myShepherd.getEncounter(encounterNumber);
          List<SinglePhotoVideo> myphots=enc.getSinglePhotoVideo();
          int myPhotsSize=myphots.size();
          File thisEncounterDir = new File(encountersDir, request.getParameter("number"));
          for(int t=0;t<myPhotsSize;t++){
            SinglePhotoVideo spv=myphots.get(t);
            String spvName=spv.getFilename().replaceAll(".jpg", "_CR.jpg").replaceAll(".JPG","_CR.JPG");

            File spvCRFile=new File(thisEncounterDir,spvName);
            if(spvCRFile.exists()){
              //test comment
              File jpegVersion = new File(thisEncounterDir, spvName);
              File enhancedVersion = new File(thisEncounterDir, spvName.replaceAll("_CR", "_EH"));
              File matchFile= new File(thisEncounterDir, "matchOutput.xhtml");
              File FEATFile = new File(thisEncounterDir, spv.getFilename().replaceAll(".jpg", ".FEAT").replaceAll(".JPG", ".FEAT").replaceAll(".jpeg", ".FEAT").replaceAll(".JPEG", ".FEAT"));
              File FTFile = new File(thisEncounterDir, spvName.replaceAll("_CR", "_FT"));
           if (jpegVersion.exists() && (!jpegVersion.getName().equals(spv.getFilename()))) {
                removedJPEG = jpegVersion.delete();
              }
          if (enhancedVersion.exists()  && (!enhancedVersion.getName().equals(spv.getFilename())) ) {
                removedJPEG = enhancedVersion.delete();
              }
              if (matchFile.exists() ) {
                removedJPEG = matchFile.delete();
              }
              if (FEATFile.exists()  && (!FEATFile.getName().equals(spv.getFilename()))) {
                removedJPEG = FEATFile.delete();
              }
              if (FTFile.exists()  && (!FTFile.getName().equals(spv.getFilename()))) {
                removedJPEG = FTFile.delete();
              }
              jpegVersion=null;
              enhancedVersion=null;
            }
          }
          
          
          
          
          
          


          
          
          
          

        } 
        catch (SecurityException thisE) {
          thisE.printStackTrace();
          System.out.println("Error attempting to delete the old JPEG version of a submitted manta data image!!!!");
          removedJPEG = false;
          resultComment.append("I hit a security error trying to delete the old feature image. Please check your file system permissions.");
        }
      }
      else if(action.equals("imageadd")){
        
        MultipartParser mp = new MultipartParser(request, 10 * 1024 * 1024); // 2MB
        Part part;
        while ((part = mp.readNextPart()) != null) {
          String name = part.getName();
          if (part.isParam()) {


            // it's a parameter part
            ParamPart paramPart = (ParamPart) part;
            String value = paramPart.getStringValue();

            //determine which variable to assign the param to
            if (name.equals("number")) {
              encounterNumber = value;
              System.out.println("Setting encounterNumber to: "+encounterNumber);
            } 
            
            //let's detect our filename
            if(name.equals("photoNumber")){
              //test comment
              //Integer photoNumber=new Integer(value);
              //Encounter enc=myShepherd.getEncounter(encounterNumber);
              
              SinglePhotoVideo spv=myShepherd.getSinglePhotoVideo(value);
              fileName=spv.getFilename().replaceAll(".jpg", "_CR.jpg").replaceAll(".JPG", "_CR.JPG").replaceAll(".jpeg", "_CR.jpeg").replaceAll(".JPEG", "_CR.JPEG");
            }

          }

          File thisEncounterDir = new File(encountersDir, encounterNumber);
        
       
          if (part.isFile()) {
            FilePart filePart = (FilePart) part;
            String uploadedFileName=ServletUtilities.cleanFileName(filePart.getFileName());
            if((uploadedFileName.trim().toLowerCase().endsWith("jpg"))||(uploadedFileName.trim().toLowerCase().endsWith("jpeg"))){
              //fileName = ServletUtilities.cleanFileName(filePart.getFileName());

              //eliminate the previous JPG version of this file if it existed                         //eliminate the previous JPG if it existed
              try {



                File jpegVersion = new File(thisEncounterDir, fileName);
                File enhancedVersion = new File(thisEncounterDir, enhancedFileName);
                if (jpegVersion.exists()) {
                  removedJPEG = jpegVersion.delete();
                }
                if (enhancedVersion.exists()) {
                  removedJPEG = enhancedVersion.delete();
                }
                jpegVersion=null;
                enhancedVersion=null;

              } 
              catch (SecurityException thisE) {
                thisE.printStackTrace();
                System.out.println("Error attempting to delete the old JPEG version of a submitted manta data image!!!!");
                removedJPEG = false;
                resultComment.append("I hit a security error trying to delete the old feature image. Please check your file system permissions.");
                locked=true;
              }

              File write2me=new File(thisEncounterDir, fileName);
              long file_size = filePart.writeTo(
                  write2me
              );
              resultComment.append("Successfully saved the new feature image.<br />");
              filePart=null;
              

              
              //OK, if the file has uploaded successfully, then it's time to get a process from the 
              //command line to execute it.
              //String[] execArray=new String[5];
              //execArray[0]="/usr/bin/mmprocess";
              //execArray[1]=write2me.getAbsolutePath().replaceAll("_CR", "");
              //execArray[2]="4";
              //execArray[3]="1";
              //execArray[4]="2";
              
              ProcessBuilder pb = new ProcessBuilder("/usr/bin/mmprocess", write2me.getAbsolutePath().replaceAll("_CR", ""), "4","1","2");
                  
              System.out.println("I am trying to execute the command:<br/>"+"/usr/bin/mmprocess "+ write2me.getAbsolutePath().replaceAll("_CR", "")+ " 4"+" 1"+" 2"+"<br />");
              resultComment.append("I am trying to execute the command:<br/>"+"/usr/bin/mmprocess "+ write2me.getName().replaceAll("_CR", "")+ " 4"+" 1"+" 2"+"<br />");
              
              //test comment
              
              String ls_str;
              write2me.setWritable(true, false);
              //Process ls_proc = Runtime.getRuntime().exec(execArray); 
              Process ls_proc=pb.start();
             

              // get its output (your input) stream 

              BufferedReader ls_in= new BufferedReader(new InputStreamReader(ls_proc.getInputStream()));
     
              //DataInputStream ls_in = new DataInputStream(ls_proc.getInputStream()); 
              resultComment.append("mmprocess reported the following when trying to create the enhanced image file:<br />");
              
              try { 
                while ((ls_str = ls_in.readLine()) != null) { 
                  //System.out.println(ls_str); 
                  resultComment.append(ls_str+"<br />");
                } 
              } 
              catch (IOException e) { 
                e.printStackTrace();
                locked=true;
                resultComment.append("I hit an IOException while trying to execute mmprocess from the command line.");
                resultComment.append(e.getStackTrace().toString());
                
              } 
              
              if(!locked){
                //if we've made it here, we have an enhanced image and can kick off a scan.
                
                //String execString2="/usr/bin/mmatch "+encountersDir.getAbsolutePath()+" "+winQuote(write2me.getAbsolutePath().replaceAll("_CR", ""))+" 0 0 1 0 -o "+thisEncounterDir.getAbsolutePath()+"/matchOutput.xhtml";
                
                ProcessBuilder pb2 = new ProcessBuilder("/usr/bin/mmatch", encountersDir.getAbsolutePath(), write2me.getAbsolutePath().replaceAll("_CR", ""),"0","0","1","0","-o","matchOutput.xhtml");
                pb2.directory(thisEncounterDir);
                
                
                resultComment.append("<br /><br />I am trying to execute the matching command:<br/>"+pb2.toString()+"<br />");
                System.out.println("<br /><br />I am trying to execute the matching command:<br/>"+"/usr/bin/mmatch "+ encountersDir.getAbsolutePath()+" "+write2me.getAbsolutePath().replaceAll("_CR", "")+" 0"+" 0"+" 1"+" 0"+" -o" + " matchOutput.xhtml"+"<br />");
                
                String ls_str2;
                 //Process ls_proc2 = Runtime.getRuntime().exec(execString2); 
                pb2.start();
                
              }
              

            }
            else{
              locked = true;
            }
      
          }
        }
  
      }
      
  


      //System.out.println(encounterNumber);
      //System.out.println(fileName);
      myShepherd.beginDBTransaction();
      System.out.println("    I see encounterNumber as: "+encounterNumber);
      if ((myShepherd.isEncounter(encounterNumber))&&!locked) {
        Encounter add2shark = myShepherd.getEncounter(encounterNumber);
        try {


          String user = "Unknown User";
          if (request.getRemoteUser() != null) {
            user = request.getRemoteUser();
          }
          if(action.equals("imageadd")){
            
            add2shark.addComments("<p><em>" + user + " on " + (new java.util.Date()).toString() + "</em><br>" + "Submitted new mantamatcher data image.</p>");
          }
          else {
            add2shark.addComments("<p><em>" + user + " on " + (new java.util.Date()).toString() + "</em><br>" + "Removed mantamatcher data image.</p>");
            
          }
        } 
        catch (Exception le) {
          locked = true;
          myShepherd.rollbackDBTransaction();
          le.printStackTrace();
        }

        //we're getting to the response phase
        

        if (!locked) {
          myShepherd.commitDBTransaction();
          myShepherd.closeDBTransaction();
          

   
          
          if(action.equals("imageadd")){
            
            String resultsURL = ("/" + CommonConfiguration.getDataDirectoryName() + "/encounters/"+encounterNumber+"/matchOutput.xhtml");
            response.sendRedirect(resultsURL);
            
            //out.println("<strong>Confirmed:</strong> I have successfully uploaded your mantamatcher data image file.");
            
          }
          else {
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Confirmed:</strong> I have successfully removed your mantamatcher data image file.");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
            out.println("<p><strong>Additional comments from the operation</strong><br />"+resultComment.toString()+"</p>");
            
            out.println(ServletUtilities.getFooter());
            
          }
          
        } else {
          out.println(ServletUtilities.getHeader(request));
          
          if(action.equals("imageadd")){
            
            out.println("<strong>Step 2 Failed:</strong> I could not upload this patterning file. There may be a database error, or a non-JPG file type may have been uploaded.");
           }
          else {
            out.println("<strong>Step 2 Failed:</strong> I could not remove this patterning file. There may be a database error.");
          }
          
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
          out.println("<p><strong>Additional comments from the operation</strong><br />"+resultComment.toString()+"</p>");
          out.println(ServletUtilities.getFooter());
        }
      } 
      else {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I was unable to execute this action. I cannot find the encounter that you intended it for in the database.");
        out.println("<p><strong>Additional comments from the operation</strong><br />"+resultComment.toString()+"</p>");
        
        out.println(ServletUtilities.getFooter());
      }
    } 
    catch (IOException lEx) {
      lEx.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to execute the action.");
      out.println("<p><strong>Additional comments from the operation</strong><br />"+resultComment.toString()+"</p>");
      
      out.println(ServletUtilities.getFooter());
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    out.close();
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
  }

  


}
  
  
