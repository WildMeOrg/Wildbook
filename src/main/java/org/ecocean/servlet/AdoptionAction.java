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

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;
import org.ecocean.Adoption;
import org.ecocean.CommonConfiguration;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Calendar;
import java.util.Random;


public class AdoptionAction extends Action {

  String mailList = "no";

  Random ran = new Random();


  private String adopterName = "";
  private String adopterAddress = "";
  private String adopterEmail = "";
  private String adopterImage = "";
  private String adoptionStartDate = "";
  private String adoptionEndDate = "";
  private String adopterQuote = "";
  private String adoptionManager = "";
  private String shark = "";
  private String encounter = "";
  private String notes = "";
  private String adoptionType = "";
  private String number = "";


  Shepherd myShepherd;

  public ActionForward execute(ActionMapping mapping,
                               ActionForm form,
                               HttpServletRequest request,
                               HttpServletResponse response)
    throws Exception {


    boolean adoptionSuccess = true;
    String failureMessage = "";
    myShepherd = new Shepherd();
    System.out.println("Starting adoptionAction...");
    if (form instanceof AdoptionForm) {

      //get the form to read data from
      AdoptionForm theForm = (AdoptionForm) form;

      System.out.println("Starting an adoption submission...");
      Calendar date = Calendar.getInstance();
      number = theForm.getNumber();
      boolean isEdit = false;
      if ((number != null) && (!number.equals(""))) {
        isEdit = true;
        myShepherd.beginDBTransaction();
      }
      String id = "";
      if (isEdit) {
        id = number;
      } else {
        id = "adpt" + (new Integer(date.get(Calendar.DAY_OF_MONTH))).toString() + (new Integer(date.get(Calendar.MONTH) + 1)).toString() + (new Integer(date.get(Calendar.YEAR))).toString() + (new Integer(date.get(Calendar.HOUR_OF_DAY))).toString() + (new Integer(date.get(Calendar.MINUTE))).toString() + (new Integer(date.get(Calendar.SECOND))).toString();
      }

      String encoding = request.getCharacterEncoding();
      if ((encoding != null) && (encoding.equalsIgnoreCase("utf-8"))) {
        response.setContentType("text/html; charset=utf-8");
      }


      adopterName = theForm.getAdopterName();
      adopterAddress = theForm.getAdopterAddress();
      adopterEmail = theForm.getAdopterEmail();
      //adopterImage=theForm.getAdopterImage();
      adoptionStartDate = theForm.getAdoptionStartDate();
      adoptionEndDate = theForm.getAdoptionEndDate();
      adopterQuote = theForm.getAdopterQuote();
      adoptionManager = theForm.getAdoptionManager();
      shark = theForm.getShark();
      encounter = theForm.getEncounter();


      notes = theForm.getNotes();
      adoptionType = theForm.getAdoptionType();


      String text = theForm.getTheText();
      //retrieve the query string value
      String queryValue = theForm.getQueryParam();
      //retrieve the file representation
      FormFile[] file = new FormFile[1];
      file[0] = theForm.getTheFile1();

      //retrieve the file name
      String[] fileName = new String[1];
      try {
        fileName[0] = ServletUtilities.cleanFileName(file[0].getFileName());
      } catch (NullPointerException npe) {
        fileName[0] = null;
      }


      //retrieve the content type
      String[] contentType = new String[1];
      try {
        contentType[0] = file[0].getContentType();
      } catch (NullPointerException npe) {
        contentType[0] = null;
      }


      boolean writeFile = theForm.getWriteFile();
      //retrieve the file size
      String[] fileSize = new String[1];
      try {
        fileSize[0] = (file[0].getFileSize() + " bytes");
      } catch (NullPointerException npe) {
        fileSize[0] = null;
      }

      String data = null;

      //File thisAdoptionDir=new File(CommonConfiguration.getAdoptionDirectory()+File.separator+id);
      File thisAdoptionDir = new File(getServlet().getServletContext().getRealPath(("/" + CommonConfiguration.getAdoptionDirectory() + "/" + id)));
      //File thisAdoptionDir=new File(getServlet().getServletContext().getRealPath("/encounters"));


      //System.out.println(thisEncounterDir.getCanonicalPath());
      boolean created = false;
      try {
        if ((!thisAdoptionDir.exists()) && adoptionSuccess) {
          created = thisAdoptionDir.mkdir();
        }
        ;
      } catch (SecurityException sec) {
        System.out.println("Security exception thrown while trying to created the directory for a new encounter!");
      }
      //System.out.println("Created?: "+created);
      for (int iter = 0; iter < 1; iter++) {
        if ((fileName[iter] != null) && adoptionSuccess) {
          try {

            //retrieve the file data
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream stream = file[iter].getInputStream();
            //System.out.println(writeFile);
            if (!writeFile) {
              //only write files out that are less than 9MB
              if ((file[iter].getFileSize() < (4 * 9216000)) && (file[iter].getFileSize() > 0)) {

                byte[] buffer = new byte[8192];
                int bytesRead = 0;
                while ((bytesRead = stream.read(buffer, 0, 8192)) != -1) {
                  baos.write(buffer, 0, bytesRead);
                }
                data = new String(baos.toByteArray());
              } else {
                data = new String("The file is greater than 4MB or less than 1 byte, " +
                  " and has not been written to stream." +
                  " File Size: " + file[iter].getFileSize() + " bytes. This is a" +
                  " limitation of this particular web application, hard-coded" +
                  " in org.apache.struts.webapp.upload.UploadAction");
              }
            } else if ((!(file[iter].getFileName().equals(""))) && (file[iter].getFileSize() > 0)) {
              //write the file to the file specified
              //String writeName=file[iter].getFileName().replace('#', '_').replace('-', '_').replace('+', '_').replaceAll(" ", "_");
              //String writeName=forHTMLTag(file[iter].getFileName());
              String writeName = "adopter.jpg";

              //String writeName=URLEncoder.encode(file[iter].getFileName(), "UTF-8");
              while (writeName.indexOf(".") != writeName.lastIndexOf(".")) {
                writeName = writeName.replaceFirst("\\.", "_");
              }
              //System.out.println(writeName);
              OutputStream bos = new FileOutputStream(new File(thisAdoptionDir, writeName));
              int bytesRead = 0;
              byte[] buffer = new byte[8192];
              while ((bytesRead = stream.read(buffer, 0, 8192)) != -1) {
                bos.write(buffer, 0, bytesRead);
              }
              bos.close();
              data = "The file has been written to \"" + id + "\\" + writeName + "\"";
              adopterImage = writeName;
            }
            //close the stream
            stream.close();
            baos.close();
          } catch (FileNotFoundException fnfe) {
            System.out.println("File not found exception.\n");
            fnfe.printStackTrace();
            return null;
          } catch (IOException ioe) {
            System.out.println("IO Exception.\n");
            ioe.printStackTrace();
            return null;
          }
        } //end if fileName[iter]!=null

      } //end for iter


      //now let's add our encounter to the database

      Adoption ad = new Adoption(id, adopterName, adopterEmail, adoptionStartDate, adoptionEndDate);
      if (isEdit) {
        ad = myShepherd.getAdoption(number);
        ad.setAdopterName(adopterName);
        ad.setAdopterEmail(adopterEmail);
        ad.setAdoptionEndDate(adoptionEndDate);
        ad.setAdoptionStartDate(adoptionStartDate);
      }

      ad.setAdopterImage(adopterImage);
      ad.setAdopterQuote(adopterQuote);
      ad.setAdoptionManager(adoptionManager);
      ad.setIndividual(shark);
      ad.setEncounter(encounter);
      ad.setNotes(notes);
      ad.setAdoptionType(adoptionType);
      ad.setAdopterAddress(adopterAddress);

      if (adoptionSuccess && !isEdit) {
        try {
          myShepherd.storeNewAdoption(ad, id);
        } catch (Exception e) {
          adoptionSuccess = false;
          failureMessage += "Failed to presist the new adoption.<br>";
        }
      } else if (adoptionSuccess && isEdit) {
        myShepherd.commitDBTransaction();

      }


      //destroy the temporary file created
      if (fileName[0] != null) {
        file[0].destroy();
      }
      file = null;


      //return a forward to display.jsp
      System.out.println("Ending adoption data submission.");
      //if((submitterID!=null)&&(submitterID.equals("deepblue"))) {
      if (adoptionSuccess) {
        response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/" + CommonConfiguration.getAdoptionDirectory() + "/adoptionSuccess.jsp?id=" + id);
      } else {
        response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/" + CommonConfiguration.getAdoptionDirectory() + "/adoptionFailure.jsp?message=" + failureMessage);
      }

    }

    myShepherd.closeDBTransaction();
    return null;
  }

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

}