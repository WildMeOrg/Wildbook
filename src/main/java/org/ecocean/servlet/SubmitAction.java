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
import org.ecocean.*;
import org.ecocean.tag.AcousticTag;
import org.ecocean.tag.MetalTag;
import org.ecocean.tag.SatelliteTag;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class takes the SubmitForm and retrieves the text value
 * and file attributes and puts them in the request for the display.jsp
 * page to display them
 * Test
 */


public class SubmitAction extends Action {



  public ActionForward execute(ActionMapping mapping,
                               ActionForm form,
                               HttpServletRequest request,
                               HttpServletResponse response)
    throws Exception {

		  String mailList = "no";
		  Calendar date = Calendar.getInstance();
		  Random ran = new Random();
		  String uniqueID = (new Integer(date.get(Calendar.DAY_OF_MONTH))).toString() + (new Integer(date.get(Calendar.MONTH) + 1)).toString() + (new Integer(date.get(Calendar.YEAR))).toString() + (new Integer(date.get(Calendar.HOUR_OF_DAY))).toString() + (new Integer(date.get(Calendar.MINUTE))).toString() + (new Integer(date.get(Calendar.SECOND))).toString() + (new Integer(ran.nextInt(99))).toString();
		  String size = "";
		  String elevation = "";
		  String depth = "";
		  String behavior="";
		  String lifeStage="";
		  String measureUnits = "", location = "", sex = "unknown", comments = "", primaryImageName = "", guess = "no estimate provided";
		  String submitterName = "", submitterEmail = "", submitterPhone = "", submitterAddress = "", submitterOrganization="", submitterProject="";
		  String photographerName = "", photographerEmail = "", photographerPhone = "", photographerAddress = "";
		  //Vector additionalImageNames = new Vector();
		  ArrayList<SinglePhotoVideo> images=new ArrayList<SinglePhotoVideo>();

		  int encounterNumber = 0;
		  int day = 1, month = 1, year = 2003, hour = 12;
		  String lat = "", longitude = "", latDirection = "", longDirection = "", scars = "None";
		  String minutes = "00", gpsLongitudeMinutes = "", gpsLongitudeSeconds = "", gpsLatitudeMinutes = "", gpsLatitudeSeconds = "", submitterID = "N/A";
		  String locCode = "", informothers = "";
		  String livingStatus = "";
		  String genusSpecies="";
		  String country="";
		  String locationID="";
		  
		  
	    String context="context0";
	    context=ServletUtilities.getContext(request);  
	    Shepherd myShepherd=myShepherd = new Shepherd(context);

    if (form instanceof SubmitForm) {
      System.out.println("Starting data submission...");
      //this line is here for when the input page is upload-utf8.jsp,
      //it sets the correct character encoding for the response
      String encoding = request.getCharacterEncoding();
      if ((encoding != null) && (encoding.equalsIgnoreCase("utf-8"))) {
        response.setContentType("text/html; charset=utf-8");
      }

      //get the form to read data from
      SubmitForm theForm = (SubmitForm) form;

      mailList = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getMailList());
      date = theForm.getDate();
      uniqueID = theForm.getUniqueID();

      if((theForm.getSize()!=null)&&(!theForm.getSize().equals(""))){size = theForm.getSize();}


      if((theForm.getDepth()!=null)&&(!theForm.getDepth().equals(""))){
      	depth = theForm.getDepth();
  	  }

      if((theForm.getElevation()!=null)&&(!theForm.getElevation().equals(""))){

      	elevation = theForm.getElevation();
  	  }

      measureUnits = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getMeasureUnits());
      location = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLocation());
      System.out.println("SubmitAction location: " + location);
      sex = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSex());
      comments = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getComments());
      if(theForm.getBehavior()!=null){
      	behavior = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getBehavior());
  	  }
      if(theForm.getLifeStage()!=null){
        lifeStage = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLifeStage());
      }
      primaryImageName = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getPrimaryImageName());
      guess = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getGuess());
      submitterName = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSubmitterName());
      submitterEmail = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSubmitterEmail().replaceAll(";", ",").replaceAll(" ", ""));
      submitterPhone = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSubmitterPhone());
      submitterAddress = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSubmitterAddress());
      submitterOrganization = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSubmitterOrganization());
      submitterProject = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSubmitterProject());

      photographerName = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getPhotographerName());
      photographerEmail = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getPhotographerEmail().replaceAll(";", ",").replaceAll(" ", ""));
      photographerPhone = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getPhotographerPhone());
      photographerAddress = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getPhotographerAddress());
      //additionalImageNames = theForm.getAdditionalImageNames();
      encounterNumber = theForm.getEncounterNumber();
      livingStatus = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLivingStatus());
      genusSpecies = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getGenusSpecies());
      informothers = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getInformothers().replaceAll(";", ",").replaceAll(" ", ""));
      country = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getCountry());
	  locationID = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLocationID());

      //check for spamBots
      boolean spamBot = false;
      StringBuffer spamFields = new StringBuffer();
      spamFields.append(theForm.getSubmitterPhone());
      spamFields.append(theForm.getSubmitterName());
      spamFields.append(theForm.getPhotographerPhone());
      spamFields.append(theForm.getPhotographerName());
      spamFields.append(theForm.getLocation());
      spamFields.append(theForm.getComments());
      if(theForm.getBehavior()!=null){spamFields.append(theForm.getBehavior());}


      if (spamFields.toString().toLowerCase().indexOf("porn") != -1) {
        spamBot = true;
      }

      if (spamFields.toString().toLowerCase().indexOf("href") != -1) {
        spamBot = true;
      }
      //else if(spamFields.toString().toLowerCase().indexOf("[url]")!=-1){spamBot=true;}
      //else if(spamFields.toString().toLowerCase().indexOf("url=")!=-1){spamBot=true;}
      //else if(spamFields.toString().toLowerCase().trim().equals("")){spamBot=true;}
      //else if((theForm.getSubmitterID()!=null)&&(theForm.getSubmitterID().equals("N%2FA"))) {spamBot=true;}



      locCode = "";
      if((locationID!=null)&&(!locationID.trim().equals(""))){
		locCode=locationID;
	  }
	  //see if the location code can be determined and set based on the location String reported
      else{
      	String locTemp = location.toLowerCase().trim();
      	Properties props = new Properties();


      	int numAllowedPhotos = 4;


      	try {
        	props=ShepherdProperties.getProperties("submitActionClass.properties", "",context);

        	Enumeration m_enum = props.propertyNames();
        	while (m_enum.hasMoreElements()) {
          	String aLocationSnippet = ((String) m_enum.nextElement()).trim();
          	if (locTemp.indexOf(aLocationSnippet) != -1) {
            	locCode = props.getProperty(aLocationSnippet);
          	}
        	}
      	}
      	catch (Exception props_e) {
        	props_e.printStackTrace();
      	}

  	} //end else
	//end location code setter

      day = theForm.getDay();
      month = theForm.getMonth();
      year = theForm.getYear();
      hour = theForm.getHour();
      submitterID = theForm.getSubmitterID();
      lat = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLat());
      longitude = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLongitude());
      latDirection = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLatDirection());
      longDirection = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLongDirection());
      scars = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getScars());
      if (scars.equals("1")) {
        scars = "Tail (caudal) fin";
      } else if (scars.equals("0")) {
        scars = "None";
      } else if (scars.equals("2")) {
        scars = "1st dorsal fin";
      } else if (scars.equals("3")) {
        scars = "2nd dorsal fin";
      } else if (scars.equals("4")) {
        scars = "Left pectoral fin";
      } else if (scars.equals("5")) {
        scars = "Right pectoral fin";
      } else if (scars.equals("6")) {
        scars = "Head";
      } else if (scars.equals("7")) {
        scars = "Body";
      }


      minutes = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getMinutes());
      //hour=theForm.getHour();
      gpsLongitudeMinutes = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getGpsLongitudeMinutes());
      gpsLongitudeSeconds = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getGpsLongitudeSeconds());
      gpsLatitudeMinutes = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getGpsLatitudeMinutes());
      gpsLatitudeSeconds = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getGpsLatitudeSeconds());
      //retrieve the text data
      String text = theForm.getTheText();
      //retrieve the query string value
      String queryValue = theForm.getQueryParam();
      //retrieve the file representation
      FormFile[] file = new FormFile[4];
      file[0] = theForm.getTheFile1();
      file[1] = theForm.getTheFile2();
      file[2] = theForm.getTheFile3();
      file[3] = theForm.getTheFile4();
      //retrieve the file name
      String[] fileName = new String[4];
      try {
        fileName[0] = ServletUtilities.preventCrossSiteScriptingAttacks(file[0].getFileName());
      } catch (NullPointerException npe) {
        fileName[0] = null;
      }
      try {
        fileName[1] = ServletUtilities.preventCrossSiteScriptingAttacks(file[1].getFileName());
      } catch (NullPointerException npe) {
        fileName[1] = null;
      }
      try {
        fileName[2] = ServletUtilities.preventCrossSiteScriptingAttacks(file[2].getFileName());
      } catch (NullPointerException npe) {
        fileName[2] = null;
      }
      try {
        fileName[3] = ServletUtilities.preventCrossSiteScriptingAttacks(file[3].getFileName());
      } catch (NullPointerException npe) {
        fileName[3] = null;
      }
      //retrieve the content type
      String[] contentType = new String[4];
      try {
        contentType[0] = file[0].getContentType();
      } catch (NullPointerException npe) {
        contentType[0] = null;
      }
      try {
        contentType[1] = file[1].getContentType();
      } catch (NullPointerException npe) {
        contentType[1] = null;
      }
      try {
        contentType[2] = file[2].getContentType();
      } catch (NullPointerException npe) {
        contentType[2] = null;
      }
      try {
        contentType[3] = file[3].getContentType();
      } catch (NullPointerException npe) {
        contentType[3] = null;
      }
      boolean writeFile = theForm.getWriteFile();
      //retrieve the file size
      String[] fileSize = new String[4];
      try {
        fileSize[0] = (file[0].getFileSize() + " bytes");
      } catch (NullPointerException npe) {
        fileSize[0] = null;
      }
      try {
        fileSize[1] = (file[1].getFileSize() + " bytes");
      } catch (NullPointerException npe) {
        fileSize[1] = null;
      }
      try {
        fileSize[2] = (file[2].getFileSize() + " bytes");
      } catch (NullPointerException npe) {
        fileSize[2] = null;
      }
      try {
        fileSize[3] = (file[3].getFileSize() + " bytes");
      } catch (NullPointerException npe) {
        fileSize[3] = null;
      }
      String data = null;

      //File encountersDir = new File(getServlet().getServletContext().getRealPath("/encounters"));

      String rootWebappPath = getServlet().getServletContext().getRealPath("/");
      File webappsDir = new File(rootWebappPath).getParentFile();
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
      if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}

      File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
      if(!encountersDir.exists()){encountersDir.mkdir();}
      File thisEncounterDir = new File(encountersDir, uniqueID);

      boolean created = false;
      try {
        if ((!thisEncounterDir.exists()) && (!spamBot)) {
          created = thisEncounterDir.mkdir();
        }
        ;
      } catch (SecurityException sec) {
        System.out.println("Security exception thrown while trying to created the directory for a new encounter!");
      }
      //System.out.println("Created?: "+created);
      for (int iter = 0; iter < 4; iter++) {
        if ((!spamBot) && (fileName[iter] != null)) {
          try {

            //retrieve the file data
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream stream = file[iter].getInputStream();
            //System.out.println(writeFile);
            if (!writeFile) {
              //only write files out that are less than 9MB
              if ((file[iter].getFileSize() < (CommonConfiguration.getMaxMediaSizeInMegabytes(context) * 1048576)) && (file[iter].getFileSize() > 0)) {

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
              String writeName = ServletUtilities.cleanFileName(file[iter].getFileName());

              //String writeName=URLEncoder.encode(file[iter].getFileName(), "UTF-8");
              while (writeName.indexOf(".") != writeName.lastIndexOf(".")) {
                writeName = writeName.replaceFirst("\\.", "_");
              }
              //System.out.println(writeName);
              //additionalImageNames.add(writeName);
              File writeMe=new File(thisEncounterDir, writeName);
              images.add(new SinglePhotoVideo(uniqueID,writeMe));
              OutputStream bos = new FileOutputStream(writeMe);
              int bytesRead = 0;
              byte[] buffer = new byte[8192];
              while ((bytesRead = stream.read(buffer, 0, 8192)) != -1) {
                bos.write(buffer, 0, bytesRead);
              }
              bos.close();
              data = "The file has been written to \"" + encounterNumber + "\\" + writeName + "\"";
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
      Encounter enc = new Encounter(day, month, year, hour, minutes, guess, location, submitterName, submitterEmail, images);
      enc.setComments(comments.replaceAll("\n", "<br>"));
      if (theForm.getReleaseDate() != null && theForm.getReleaseDate().length() > 0) {
        String dateStr = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getReleaseDate());
        String dateFormatPattern = CommonConfiguration.getProperty("releaseDateFormat",context);
        try {
          SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormatPattern);
          enc.setReleaseDate(simpleDateFormat.parse(dateStr));
        } catch (Exception e) {
          enc.addComments("<p>Reported release date was problematic: " + dateStr + "</p>");
        }
      }
      if(theForm.getBehavior()!=null){
  			enc.setBehavior(behavior);
  		}
      if(theForm.getLifeStage()!=null){
        enc.setLifeStage(lifeStage);
      }
      Map<String, Object> measurements = theForm.getMeasurements();
      for (String key : measurements.keySet()) {
        if (!key.endsWith("units") && !key.endsWith("samplingProtocol")) {
          String value = ((String) measurements.get(key)).trim();
          if (value.length() > 0) {
            try {
              Double doubleVal = Double.valueOf(value);
              String units = (String) measurements.get(key + "units");
              String samplingProtocol = (String) measurements.get(key + "samplingProtocol");
              Measurement measurement = new Measurement(enc.getEncounterNumber(), key, doubleVal, units, samplingProtocol);
              enc.addMeasurement(measurement);
            }
            catch(Exception ex) {
              enc.addComments("<p>Reported measurement " + key + " was problematic: " + value + "</p>");
            }
          }
        }
      }
      List<MetalTag> metalTags = getMetalTags(theForm);
      for (MetalTag metalTag : metalTags) {
        enc.addMetalTag(metalTag);
      }
      enc.setAcousticTag(getAcousticTag(theForm));
      enc.setSatelliteTag(getSatelliteTag(theForm));
      enc.setSex(sex);
      enc.setLivingStatus(livingStatus);

      //let's handle genus and species for taxonomy
      try {

	  		String genus="";
	  		String specificEpithet = "";

	  		//now we have to break apart genus species
	  		StringTokenizer tokenizer=new StringTokenizer(genusSpecies," ");
	  		if(tokenizer.countTokens()>=2){

	          	enc.setGenus(tokenizer.nextToken());
	          	enc.setSpecificEpithet(tokenizer.nextToken().replaceAll(",","").replaceAll("_"," "));

	  	    }
	  	    //handle malformed Genus Species formats
	  	    else{throw new Exception("The format of the submitted genusSpecies parameter did not have two tokens delimited by a space (e.g., \"Rhincodon typus\"). The submitted value was: "+genusSpecies);}

	   }
	   catch (Exception le) {

       }


      enc.setDistinguishingScar(scars);
      int sizePeriod=0;
      if ((measureUnits.equals("Feet"))) {

        if(!depth.equals("")){
			try{
				double tempDouble=(new Double(depth)).doubleValue()/3.3;
        		String truncDepth = (new Double(tempDouble)).toString();
        		sizePeriod = truncDepth.indexOf(".");
        		truncDepth = truncDepth.substring(0, sizePeriod + 2);
        		depth = (new Double(truncDepth)).toString();
			}
			catch(java.lang.NumberFormatException nfe){
				enc.addComments("<p>Reported depth was problematic: " + depth + "</p>");
				depth="";
			}
			catch(NullPointerException npe){
				depth="";
			}
		}

		if(!elevation.equals("")){
			try{
				double tempDouble=(new Double(elevation)).doubleValue()/3.3;
        		String truncElev = (new Double(tempDouble)).toString();
				//String truncElev = ((new Double(elevation)) / 3.3).toString();
		    	sizePeriod = truncElev.indexOf(".");
				truncElev = truncElev.substring(0, sizePeriod + 2);
        		elevation = (new Double(truncElev)).toString();
			}
			catch(java.lang.NumberFormatException nfe){
				enc.addComments("<p>Reported elevation was problematic: " + elevation + "</p>");
				elevation="";
			}
			catch(NullPointerException npe){
				elevation="";
			}
		}
		if(!size.equals("")){



			try{
					double tempDouble=(new Double(size)).doubleValue()/3.3;
        			String truncSize = (new Double(tempDouble)).toString();
					//String truncSize = ((new Double(size)) / 3.3).toString();
				    sizePeriod = truncSize.indexOf(".");
					truncSize = truncSize.substring(0, sizePeriod + 2);
		        	size = (new Double(truncSize)).toString();
			}
			catch(java.lang.NumberFormatException nfe){

				enc.addComments("<p>Reported size was problematic: " + size + "</p>");
				size="";
			}
			catch(NullPointerException npe){
				size="";
			}
		}
      }

      if (!size.equals("")) {
        try{
        	enc.setSize(new Double(size));
        }
					catch(java.lang.NumberFormatException nfe){

						enc.addComments("<p>Reported size was problematic: " + size + "</p>");
						size="";
					}
					catch(NullPointerException npe){
						size="";
			}
      }

		//System.out.println("Depth in SubmitForm is:"+depth);
      if (!depth.equals("")) {
		try{
        	enc.setDepth(new Double(depth));
		}
					catch(java.lang.NumberFormatException nfe){
						enc.addComments("<p>Reported depth was problematic: " + depth + "</p>");
						depth="";
					}
					catch(NullPointerException npe){
						depth="";
			}
      }

      if (!elevation.equals("")) {
		try{
	    	enc.setMaximumElevationInMeters(new Double(elevation));
	    }
					catch(java.lang.NumberFormatException nfe){
						enc.addComments("<p>Reported elevation was problematic: " + elevation + "</p>");
						elevation="";
					}
					catch(NullPointerException npe){
						elevation="";
			}
      }


      //let's handle the GPS
      if (!lat.equals("") && !longitude.equals("")) {
        //enc.setGPSLatitude(lat + "&deg; " + gpsLatitudeMinutes + "\' " + gpsLatitudeSeconds + "\" " + latDirection);


        try {
          double degrees = (new Double(lat)).doubleValue();
          double position = degrees;
          /*
          if (!gpsLatitudeMinutes.equals("")) {
            double minutes2 = ((new Double(gpsLatitudeMinutes)).doubleValue()) / 60;
            position += minutes2;
          }
          if (!gpsLatitudeSeconds.equals("")) {
            double seconds2 = ((new Double(gpsLatitudeSeconds)).doubleValue()) / 3600;
            position += seconds2;
          }
          if (latDirection.toLowerCase().equals("south")) {
            position = position * -1;
          }*/
          enc.setDWCDecimalLatitude(position);

          double degrees2 = (new Double(longitude)).doubleValue();
          double position2 = degrees2;
          enc.setDWCDecimalLongitude(position2);


        } catch (Exception e) {
          System.out.println("EncounterSetGPS: problem!");
          e.printStackTrace();
        }


      }
      //if (!(longitude.equals(""))) {
        //enc.setGPSLongitude(longitude + "&deg; " + gpsLongitudeMinutes + "\' " + gpsLongitudeSeconds + "\" " + longDirection);

        //try {


          /*
          if (!gpsLongitudeMinutes.equals("")) {
            double minutes2 = ((new Double(gpsLongitudeMinutes)).doubleValue()) / 60;
            position += minutes2;
          }
          if (!gpsLongitudeSeconds.equals("")) {
            double seconds = ((new Double(gpsLongitudeSeconds)).doubleValue()) / 3600;
            position += seconds;
          }
          if (longDirection.toLowerCase().equals("west")) {
            position = position * -1;
          }
          */



        //} catch (Exception e) {
        //  System.out.println("EncounterSetGPS: problem setting decimal longitude!");
         // e.printStackTrace();
        //}
      //}

      //if one is not set, set all to null
      /*
      if ((longitude.equals("")) || (lat.equals(""))) {
        enc.setGPSLongitude("");
        enc.setGPSLongitude("");
      //let's handle the GPS
        if (!(lat.equals(""))) {


            try {
                enc.setDWCDecimalLatitude(new Double(lat));
            }
            catch(Exception e) {
              System.out.println("EncounterSetGPS: problem setting decimal latitude!");
              e.printStackTrace();
            }


        }
        if (!(longitude.equals(""))) {

          try {
            enc.setDWCDecimalLongitude(new Double(longitude));
          }
          catch(Exception e) {
            System.out.println("EncounterSetGPS: problem setting decimal longitude!");
            e.printStackTrace();
          }
        }
        enc.setDWCDecimalLatitude(-9999.0);
        enc.setDWCDecimalLongitude(-9999.0);
      }
      */
      //finish the GPS


      //enc.setMeasureUnits("Meters");
      enc.setSubmitterPhone(submitterPhone);
      enc.setSubmitterAddress(submitterAddress);
      enc.setSubmitterOrganization(submitterOrganization);
      enc.setSubmitterProject(submitterProject);

      enc.setPhotographerPhone(photographerPhone);
      enc.setPhotographerAddress(photographerAddress);
      enc.setPhotographerName(photographerName);
      enc.setPhotographerEmail(photographerEmail);
      enc.addComments("<p>Submitted on " + (new java.util.Date()).toString() + " from address: " + request.getRemoteHost() + "</p>");
      //enc.approved = false;
      if(CommonConfiguration.getProperty("encounterState0",context)!=null){
        enc.setState(CommonConfiguration.getProperty("encounterState0",context));
      }
      if (request.getRemoteUser() != null) {
        enc.setSubmitterID(request.getRemoteUser());
      } else if (submitterID != null) {
        enc.setSubmitterID(submitterID);
      } else {
        enc.setSubmitterID("N/A");
      }
      if (!locCode.equals("")) {
        enc.setLocationCode(locCode);
      }
      if (!country.equals("")) {
        enc.setCountry(country);
      }
      if (!informothers.equals("")) {
        enc.setInformOthers(informothers);
      }
      String guid = CommonConfiguration.getGlobalUniqueIdentifierPrefix(context) + uniqueID;

      //new additions for DarwinCore
      enc.setDWCGlobalUniqueIdentifier(guid);
      enc.setDWCImageURL(("http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + uniqueID));

      //populate DarwinCore dates
      DateTime dt = new DateTime();
      DateTimeFormatter fmt = ISODateTimeFormat.date();
      String strOutputDateTime = fmt.print(dt);
      enc.setDWCDateAdded(strOutputDateTime);
      enc.setDWCDateLastModified(strOutputDateTime);

      String newnum = "";
      if (!spamBot) {
        newnum = myShepherd.storeNewEncounter(enc, uniqueID);

        Logger log = LoggerFactory.getLogger(SubmitAction.class);
	    log.info("New encounter submission: <a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + uniqueID+"\">"+uniqueID+"</a>");


      }

      if (newnum.equals("fail")) {
        request.setAttribute("number", "fail");
        return null;
      }

      //place the data into the request for retrieval from display.jsp
      request.setAttribute("number", Integer.toString(encounterNumber));


      //destroy the temporary file created
      if (fileName[0] != null) {
        file[0].destroy();
      }
      if (fileName[1] != null) {
        file[1].destroy();
      }
      if (fileName[2] != null) {
        file[2].destroy();
      }
      if (fileName[3] != null) {
        file[3].destroy();
      }
      file = null;


      //return a forward to display.jsp
      System.out.println("Ending data submission.");
      if (!spamBot) {
        response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/confirmSubmit.jsp?number=" + uniqueID);
      } else {
        response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/spambot.jsp");
      }
    }

    myShepherd.closeDBTransaction();
    return null;
  }

  private SatelliteTag getSatelliteTag(SubmitForm theForm) {
    String argosPttNumber =  ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSatelliteTagArgosPttNumber()).trim();
    String satelliteTagName = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSatelliteTagName()).trim();
    String tagSerial = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSatelliteTagSerial()).trim();
    if (argosPttNumber.length() > 0 || tagSerial.length() > 0) {
      return new SatelliteTag(satelliteTagName, tagSerial, argosPttNumber);
    }
    return null;
  }

  private AcousticTag getAcousticTag(SubmitForm theForm) {
    String acousticTagId = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getAcousticTagId()).trim();
    String acousticTagSerial = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getAcousticTagSerial()).trim();
    if (acousticTagId.length() > 0 || acousticTagSerial.length() > 0) {
      return new AcousticTag(acousticTagSerial, acousticTagId);
    }
    return null;
  }

  private List<MetalTag> getMetalTags(SubmitForm theForm) {
    List<MetalTag> list = new ArrayList<MetalTag>();
    for (String key : theForm.getMetalTags().keySet()) {
      // The keys are the location
      String value = (String) theForm.getMetalTag(key);
      if (value != null) {
        value = ServletUtilities.preventCrossSiteScriptingAttacks(value).trim();
        if (value.length() > 0) {
          list.add(new MetalTag(value, key));
        }
      }
    }
    return list;
  }


}