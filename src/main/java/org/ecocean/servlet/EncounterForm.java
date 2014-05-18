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

//////
//import java.io.*;
import java.util.*;
//import java.lang.*;
//import java.util.List;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.output.*;
/////

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.User;
import org.ecocean.tag.AcousticTag;
import org.ecocean.tag.MetalTag;
import org.ecocean.tag.SatelliteTag;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Uploads a new image to the file system and associates the image with an Encounter record
 *
 * @author jholmber
 */
public class EncounterForm extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }
 
private final String UPLOAD_DIRECTORY = "/tmp";

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




  public static final String ERROR_PROPERTY_MAX_LENGTH_EXCEEDED = "The maximum upload length has been exceeded by the client.";

  //private Map<String, Object> measurements = new HashMap<String, Object>();
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		HashMap fv = new HashMap();

    String context="context0";
    //context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
System.out.println("in context " + context);

    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/users");
    if(!encountersDir.exists()){encountersDir.mkdir();}
    
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String fileName = "None";
    String username = "None";
    String fullPathFilename="";

		boolean fileSuccess = false;
		String doneMessage = "";
  	Calendar date = Calendar.getInstance();

		if (ServletFileUpload.isMultipartContent(request)) {
			try {
				List<FileItem> multiparts = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
							
				for(FileItem item : multiparts){
					if (item.isFormField()) {  //plain field
						fv.put(item.getFieldName(), ServletUtilities.preventCrossSiteScriptingAttacks(item.getString()));
System.out.println("got regular field (" + item.getFieldName() + ")=(" + item.getString() + ")");

					} else {  //file
						String name = new File(item.getName()).getName();
System.out.println("file name = " + name);
						item.write( new File(UPLOAD_DIRECTORY + File.separator + name));
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

		if (fileSuccess) {

        ///request.getRequestDispatcher("/result.jsp").forward(request, response);

      //Encounter enc = new Encounter(day, month, year, hour, minutes, guess, location, submitterName, submitterEmail, images);

    //myShepherd.rollbackDBTransaction();
    //myShepherd.closeDBTransaction();

//////////////////////////////////////////// START

      //date = theForm.getDate();
      //uniqueID = theForm.getUniqueID();


/*
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

*/


/*
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
*/

		//TODO this should live somewhere else as constant? (e.g. to build in form as well)
		String[] scarType = new String[]{"None", "Tail (caudal) fin", "1st dorsal fin", "2nd dorsal fin", "Left pectoral fin", "Right pectoral fin", "Head", "Body"};
		int scarNum = 0;
		try {
			scarNum = Integer.parseInt(fv.get("scars").toString());
		} catch (NumberFormatException e) {
			scarNum = 0;
		}
		if ((scarNum < 0) || (scarNum > 7)) {
			scarNum = 0;
		}
		fv.put("scars", scarType[scarNum]);


/*
      String queryValue = theForm.getQueryParam();
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
*/

      //File encountersDir = new File(getServlet().getServletContext().getRealPath("/encounters"));

/*
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
  


*/

      //now let's add our encounter to the database
      ///////////////////Encounter enc = new Encounter(day, month, year, hour, minutes, guess, location, submitterName, submitterEmail, images);

/*
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
          }********* end comment for real
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
          ************** end for real



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
      **************** end for real
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
*/


  //private Map<String, Object> metalTags = new HashMap<String, Object>();

/////////////////////////////////////////////END
		}

System.out.println("hey what is fv hashMap now? -> " + fv.toString());

    out.println(ServletUtilities.getHeader(request));
    out.println("something happened! -> " + doneMessage);
    out.println(ServletUtilities.getFooter(context));
    out.close();
System.out.println("done??????");
  }


}
  
  
