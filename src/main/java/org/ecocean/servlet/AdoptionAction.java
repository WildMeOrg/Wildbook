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

package org.ecocean.servlet;


import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.ecocean.Adoption;
import org.ecocean.CommonConfiguration;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.*;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Random;


public class AdoptionAction extends HttpServlet {
  
  

  String mailList = "no";

  Random ran = new Random();
  
  private final String UPLOAD_DIRECTORY = "/tmp";
  
  //little helper function for pulling values as strings even if null (not set via form)
  private String getVal(HashMap fv, String key) {
    if (fv.get(key) == null) {
      return "";
    }
    return fv.get(key).toString();
  }





  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    doPost(request, response);
}
  

public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
 

  String adopterName = "";
  String adopterAddress = "";
  String adopterEmail = "";
  String adopterImage;
  String adoptionStartDate = "";
  String adoptionEndDate = "";
  String adopterQuote = "";
  String adoptionManager = "";
  String shark = "";
  String encounter = "";
  String notes = "";
  String adoptionType = "";
  String number = "";
  String text="";

  boolean adoptionSuccess = true;
  String failureMessage = "";
    
  //set UTF-8
  request.setCharacterEncoding("UTF-8");


    HttpSession session = request.getSession(true);
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    System.out.println("in context " + context);
    //request.getSession()getServlet().getServletContext().getRealPath("/"));
    String rootDir = getServletContext().getRealPath("/");
    System.out.println("rootDir=" + rootDir);
      
      //setup data dir
      String rootWebappPath = getServletContext().getRealPath("/");
      File webappsDir = new File(rootWebappPath).getParentFile();
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
      //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
      File adoptionsDir=new File(shepherdDataDir.getAbsolutePath()+"/adoptions");
      if(!adoptionsDir.exists()){adoptionsDir.mkdirs();}  

      //get the form to read data from
     // AdoptionForm theForm = (AdoptionForm) form;
      
    //set up for response
      response.setContentType("text/html");
      PrintWriter out = response.getWriter();
      boolean locked = false;
      
      String fileName = "None";
      String username = "None";
      String fullPathFilename="";
      
      String id = "";

      boolean fileSuccess = false;  //kinda pointless now as we just build sentFiles list now at this point (do file work at end)
      String doneMessage = "";
      List<String> filesOK = new ArrayList<String>();
      HashMap<String, String> filesBad = new HashMap<String, String>();

      List<FileItem> formFiles = new ArrayList<FileItem>();

      Calendar date = Calendar.getInstance();

      long maxSizeMB = CommonConfiguration.getMaxMediaSizeInMegabytes(context);
      long maxSizeBytes = maxSizeMB * 1048576;
      
      
      //set form value hashmap
      HashMap fv = new HashMap();
      

      
       //else {
        id = "adpt" + (new Integer(date.get(Calendar.DAY_OF_MONTH))).toString() + (new Integer(date.get(Calendar.MONTH) + 1)).toString() + (new Integer(date.get(Calendar.YEAR))).toString() + (new Integer(date.get(Calendar.HOUR_OF_DAY))).toString() + (new Integer(date.get(Calendar.MINUTE))).toString() + (new Integer(date.get(Calendar.SECOND))).toString();
      //}
      



      System.out.println("Starting an adoption submission...");
      Calendar todayDate = Calendar.getInstance();
      
      
      if (ServletFileUpload.isMultipartContent(request)) {
        try {
          ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
          upload.setHeaderEncoding("UTF-8");
          List<FileItem> multiparts = upload.parseRequest(request);
          //List<FileItem> multiparts = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);

          for(FileItem item : multiparts){
            if (item.isFormField()) {  //plain field
              fv.put(item.getFieldName(), ServletUtilities.preventCrossSiteScriptingAttacks(item.getString("UTF-8").trim()));  //TODO do we want trim() here??? -jon
  //System.out.println("got regular field (" + item.getFieldName() + ")=(" + item.getString("UTF-8") + ")");

            } else {  //file
  //System.out.println("content type???? " + item.getContentType());   TODO note, the helpers only check extension
              if (item.getSize() > maxSizeBytes) {
                filesBad.put(item.getName(), "file is larger than " + maxSizeMB + "MB");
              } else if (myShepherd.isAcceptableImageFile(item.getName()) || myShepherd.isAcceptableVideoFile(item.getName()) ) {
                formFiles.add(item);
                filesOK.add(item.getName());
                
  
                
                
              } else {
                filesBad.put(item.getName(), "invalid type of file");
              }
            }
          }

          doneMessage = "File Uploaded Successfully";
          fileSuccess = true;

        } catch (Exception ex) {
          doneMessage = "File Upload Failed due to " + ex;
        }

      } else {
        doneMessage = "Sorry this Servlet only handles file upload request";
      }
      
      session.setAttribute("filesOKMessage", (filesOK.isEmpty() ? "none" : Arrays.toString(filesOK.toArray())));
      String badmsg = "";
      for (String key : filesBad.keySet()) {
        badmsg += key + " (" + getVal(filesBad, key) + ") ";
      }
      if (badmsg.equals("")) { badmsg = "none"; }
      session.setAttribute("filesBadMessage", badmsg);
      
      boolean isEdit = false;
      
      if (fileSuccess) {
        
        if ((fv.get("number") != null) && !fv.get("number").toString().equals("")) {
          
          //handle adoption number processing
          number = fv.get("number").toString();
          if ((number != null) && (!number.equals(""))) {
            isEdit = true;
            //myShepherd.beginDBTransaction();
          }
          

          //end adoption number/id processing
        }
          
          if ((fv.get("adopterName") != null) && !fv.get("adopterName").toString().equals("")) {
            adopterName = fv.get("adopterName").toString().trim();
          }
          if ((fv.get("adopterAddress") != null) && !fv.get("adopterAddress").toString().equals("")) {
            adopterAddress = fv.get("adopterAddress").toString().trim();
          }
          if ((fv.get("adopterEmail") != null) && !fv.get("adopterEmail").toString().equals("")) {
            adopterEmail = fv.get("adopterEmail").toString().trim();
          }
          
          if ((fv.get("adoptionStartDate") != null) && !fv.get("adoptionStartDate").toString().equals("")) {
            adoptionStartDate = fv.get("adoptionStartDate").toString().trim();
          }
          
          if ((fv.get("adoptionEndDate") != null) && !fv.get("adoptionEndDate").toString().equals("")) {
            adoptionEndDate = fv.get("adoptionEndDate").toString().trim();
          }
          
          if ((fv.get("adopterQuote") != null) && !fv.get("adopterQuote").toString().equals("")) {
            adopterQuote = fv.get("adopterQuote").toString().trim();
          }
           
          if ((fv.get("adoptionManager") != null) && !fv.get("adoptionManager").toString().equals("")) {
            adoptionManager = fv.get("adoptionManager").toString().trim();
          }
          
          if ((fv.get("shark") != null) && !fv.get("shark").toString().equals("")) {
            shark = fv.get("shark").toString().trim();
          }
          
          if ((fv.get("encounter") != null) && !fv.get("encounter").toString().equals("")) {
            encounter = fv.get("encounter").toString().trim();
          }
          

          if ((fv.get("notes") != null) && !fv.get("notes").toString().equals("")) {
            notes = fv.get("notes").toString().trim();
          }
          
          if ((fv.get("adoptionType") != null) && !fv.get("adoptionType").toString().equals("")) {
            adoptionType = fv.get("adoptionType").toString().trim();
          }

     


          if ((fv.get("text") != null) && !fv.get("text").toString().equals("")) {
            text = fv.get("text").toString().trim();
          }
          
          if (isEdit) {
            id = number;
          }
          
          File thisAdoptionDir = new File(adoptionsDir.getAbsolutePath() + "/" + id);
          if(!thisAdoptionDir.exists()){thisAdoptionDir.mkdirs();}
          
          
          String baseDir = ServletUtilities.dataDir(context, rootDir);
          ArrayList<SinglePhotoVideo> images = new ArrayList<SinglePhotoVideo>();
          for (FileItem item : formFiles) {
            /* this will actually write file to filesystem (or [FUTURE] wherever)
               TODO: either (a) undo this if any failure of writing encounter; or (b) dont write til success of enc. */
            //try {
              //SinglePhotoVideo spv = new SinglePhotoVideo(encID, item, context, encDataDir);
              //SinglePhotoVideo spv = new SinglePhotoVideo(enc, item, context, baseDir);
              
            try {

              //retrieve the file data
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              InputStream stream = item.getInputStream();
              //System.out.println(writeFile);
              //if ((!(file[iter].getFileName().equals(""))) && (file[iter].getFileSize() > 0)) {
                //write the file to the file specified
                //String writeName=file[iter].getFileName().replace('#', '_').replace('-', '_').replace('+', '_').replaceAll(" ", "_");
                //String writeName=forHTMLTag(file[iter].getFileName());
                String writeName = "adopter.jpg";

                //String writeName=URLEncoder.encode(file[iter].getFileName(), "UTF-8");
                //while (writeName.indexOf(".") != writeName.lastIndexOf(".")) {
                //  writeName = writeName.replaceFirst("\\.", "_");
               // }
                //System.out.println(writeName);
                
                
                OutputStream bos = new FileOutputStream(new File(thisAdoptionDir, writeName));
                int bytesRead = 0;
                byte[] buffer = new byte[8192];
                while ((bytesRead = stream.read(buffer, 0, 8192)) != -1) {
                  bos.write(buffer, 0, bytesRead);
                }
                bos.close();
                //data = "The file has been written to \"" + id + "\\" + writeName + "\"";
                adopterImage = writeName;
             // }
              //close the stream
              stream.close();
              baos.close();
            } 
            catch (FileNotFoundException fnfe) {
              System.out.println("File not found exception.\n");
              fnfe.printStackTrace();
              //return null;
            } catch (IOException ioe) {
              System.out.println("IO Exception.\n");
              ioe.printStackTrace();
              //return null;
            }
            
            
          }
          

        
          
          
          
          Adoption ad = new Adoption(id, adopterName, adopterEmail, adoptionStartDate, adoptionEndDate);
          if (isEdit) {
            ad = myShepherd.getAdoption(number);
            ad.setAdopterName(adopterName);
            ad.setAdopterEmail(adopterEmail);
            ad.setAdoptionEndDate(adoptionEndDate);
            ad.setAdoptionStartDate(adoptionStartDate);
          }


          
          ad.setAdopterQuote(adopterQuote);
          ad.setAdoptionManager(adoptionManager);
          ad.setIndividual(shark);
          ad.setEncounter(encounter);
          ad.setNotes(notes);
          ad.setAdoptionType(adoptionType);
          ad.setAdopterAddress(adopterAddress);
          
          
          if((filesOK!=null)&&(filesOK.size()>0)){
            ad.setAdopterImage(filesOK.get(0));
          }
          
          
          myShepherd.beginDBTransaction();

          
          if (adoptionSuccess && !isEdit) {
            try {
              myShepherd.storeNewAdoption(ad, id);
            } 
            catch (Exception e) {
              adoptionSuccess = false;
              failureMessage += "Failed to presist the new adoption.<br>";
            }
          } 
          else if (adoptionSuccess && isEdit) {
            myShepherd.commitDBTransaction();

          }

          
        
        
      }
   




    

     



      //return a forward to display.jsp
      System.out.println("Ending adoption data submission.");
      //if((submitterID!=null)&&(submitterID.equals("deepblue"))) {
      if (adoptionSuccess) {
        response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/adoptions/adoptionSuccess.jsp?id=" + id);
      } else {
        response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/adoptions/adoptionFailure.jsp?message=" + failureMessage);
      }

    //}

    myShepherd.closeDBTransaction();
  
    
    
  } //end doPOST

/*
  public static String forHTMLTag(String aTagFragment) {
    final StringBuffer result = new StringBuffer();

    final StringCharacterIterator iterator = new StringCharacterIterator(aTagFragment);
    char character = iterator.current();
    while (character != CharacterIterator.DONE) {
      if (character == '<') {
        result.append("_");
      } else if (character == '>') {
        result.append("_");
      } else if (character == '\"') {
        result.append("_");
      } else if (character == '\'') {
        result.append("_");
      } else if (character == '\\') {
        result.append("_");
      } else if (character == '&') {
        result.append("_");
      } else if (character == ' ') {
        result.append("_");
      } else {
        //the char is not a special one
        //add it to the result as is
        result.append(character);
      }
      character = iterator.next();
    }
    return result.toString();
  }
*/
}