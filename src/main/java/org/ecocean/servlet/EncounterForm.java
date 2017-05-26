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
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.ecocean.CommonConfiguration;
import org.ecocean.Util;
import org.ecocean.Encounter;
import org.ecocean.Measurement;
import org.ecocean.Shepherd;
import org.ecocean.media.*;
import org.ecocean.ShepherdProperties;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.tag.AcousticTag;
import org.ecocean.tag.MetalTag;
import org.ecocean.tag.SatelliteTag;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;
//import java.lang.*;
//import java.util.List;
/*
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.User;
*/


import org.apache.shiro.web.util.WebUtils;
//import org.ecocean.*;
import org.ecocean.security.SocialAuth;
import org.ecocean.Annotation;

import org.ecocean.CommonConfiguration;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.oauth.client.FacebookClient;
//import org.pac4j.oauth.client.YahooClient;
import org.pac4j.oauth.credentials.OAuthCredentials;
import org.pac4j.oauth.profile.facebook.FacebookProfile;

import org.ecocean.mmutil.FileUtilities;

/**
 * Uploads a new image to the file system and associates the image with an Encounter record
 *
 * @author jholmber
 */
public class EncounterForm extends HttpServlet {

  @Override
public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  @Override
public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

private final String UPLOAD_DIRECTORY = "/tmp";

    //little helper function for pulling values as strings even if null (not set via form)
    private String getVal(HashMap fv, String key) {
        if (fv.get(key) == null) {
            return "";
        }
        return fv.get(key).toString();
    }

  private SatelliteTag getSatelliteTag(HashMap fv) {
    String argosPttNumber =  getVal(fv, "satelliteTagArgosPttNumber");
    String satelliteTagName = getVal(fv, "satelliteTagName");
    String tagSerial = getVal(fv, "satelliteTagSerial");
    if (argosPttNumber.length() > 0 || tagSerial.length() > 0) {
      return new SatelliteTag(satelliteTagName, tagSerial, argosPttNumber);
    }
    return null;
  }

  private AcousticTag getAcousticTag(HashMap fv) {
    String acousticTagId = getVal(fv, "acousticTagId");
    String acousticTagSerial = getVal(fv, "acousticTagSerial");
    if (acousticTagId.length() > 0 || acousticTagSerial.length() > 0) {
      return new AcousticTag(acousticTagSerial, acousticTagId);
    }
    return null;
  }


  private List<MetalTag> getMetalTags(HashMap fv) {
    List<MetalTag> list = new ArrayList<MetalTag>();
        List<String> keys = Arrays.asList("left", "right");  //TODO programatically build from form

    for (String key : keys) {
      // The keys are the location
      String value = getVal(fv, "metalTag(" + key + ")");
      if (value.length() > 0) {
        list.add(new MetalTag(value, key));
      }
    }
    return list;
  }


  private List<Measurement> getMeasurements(HashMap fv, String encID, String context) {
    List<Measurement> list = new ArrayList<Measurement>();
        //List<String> keys = Arrays.asList("weight", "length", "height");  //TODO programatically build from form

    //dynamically adapt to project-specific measurements
        List<String> keys=CommonConfiguration.getIndexedPropertyValues("measurement", context);

    for (String key : keys) {
      String value = getVal(fv, "measurement(" + key + ")");
      String units = getVal(fv, "measurement(" + key + "units)");
      String samplingProtocol = getVal(fv, "measurement(" + key + "samplingProtocol)");
            if (value.length() > 0) {
                try {
                    Double doubleVal = Double.valueOf(value);
                    list.add(new Measurement(encID, key, doubleVal, units, samplingProtocol));
                }
                catch(Exception ex) {
                    //TODO was reporting via comments, but now how to handle?
                }
            }
    }
    return list;
  }
/*
got regular field (measurement(weight))=(111)
got regular field (measurement(weightunits))=(kilograms)
got regular field (measurement(weightsamplingProtocol))=(samplingProtocol1)
got regular field (measurement(length))=(222)
got regular field (measurement(lengthunits))=(meters)
got regular field (measurement(lengthsamplingProtocol))=(samplingProtocol0)
got regular field (measurement(height))=(333)
got regular field (measurement(heightunits))=(meters)
got regular field (measurement(heightsamplingProtocol))=(samplingProtocol0)
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
*/



  public static final String ERROR_PROPERTY_MAX_LENGTH_EXCEEDED = "The maximum upload length has been exceeded by the client.";

  @Override
public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        HashMap fv = new HashMap();

        //IMPORTANT - processingNotes can be used to add notes on data handling (e.g., poorly formatted dates) that can be reconciled later by the reviewer
        //Example usage: processingNotes.append("<p>Error encountered processing this date submitted by user: "+getVal(fv, "datepicker")+"</p>");
        StringBuffer processingNotes=new StringBuffer();

        HttpSession session = request.getSession(true);
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterForm.class");
System.out.println("in context " + context);
        //request.getSession()getServlet().getServletContext().getRealPath("/"));
        String rootDir = getServletContext().getRealPath("/");
System.out.println("rootDir=" + rootDir);

/*
    Vector<String> fbImages = new Vector<String>();
    int fbi = 0;
    while (request.getParameter("socialphoto_" + fbi) != null) {
        fbImages.add(request.getParameter("socialphoto_" + fbi));
        fbi++;
    }
System.out.println(fbImages);
    if (fbImages.size() > 0) {
        FacebookClient fbclient = null;
        try {
            fbclient = SocialAuth.getFacebookClient(context);
        } catch (Exception ex) {
            System.out.println("SocialAuth.getFacebookClient threw exception " + ex.toString());
        }
            WebContext ctx = new J2EContext(request, response);
            //String callbackUrl = "http://localhost.wildme.org/a/SocialConnect?type=facebook";
            String callbackUrl = "http://" + CommonConfiguration.getURLLocation(request) + "/XXXSocialConnect?type=facebook";
            if (request.getParameter("disconnect") != null) callbackUrl += "&disconnect=1";
            fbclient.setCallbackUrl(callbackUrl);
            OAuthCredentials credentials = null;
            try {
                credentials = fbclient.getCredentials(ctx);
            } catch (Exception ex) {
                System.out.println("caught exception on facebook credentials: " + ex.toString());
            }
            if (credentials != null) {
                FacebookProfile facebookProfile = fbclient.getUserProfile(credentials, ctx);
                User fbuser = myShepherd.getUserBySocialId("facebook", facebookProfile.getId());
                System.out.println("getId() = " + facebookProfile.getId() + " -> user = " + fbuser);
if (fbuser != null) System.out.println("user = " + user.getUsername() + "; fbuser = " + fbuser.getUsername());
                if ((fbuser != null) && (fbuser.getUsername().equals(user.getUsername())) && (request.getParameter("disconnect") != null)) {
                    fbuser.unsetSocial("facebook");
                    //myShepherd.getPM().makePersistent(user);
                    session.setAttribute("message", "disconnected from facebook");
                    response.sendRedirect("myAccount.jsp");
                    return;
                } else if (fbuser != null) {
                    session.setAttribute("error", "looks like this account is already connected to an account");
                    response.sendRedirect("myAccount.jsp");
                    return;
                } else {  //lets do this
                    user.setSocial("facebook", facebookProfile.getId());
                    //myShepherd.getPM().makePersistent(user);
                    session.setAttribute("message", "connected to facebook");
                    response.sendRedirect("myAccount.jsp");
                    return;
                }
            } else {
System.out.println("*** trying redirect?");
                try {
                    fbclient.redirect(ctx, false, false);
                } catch (Exception ex) {
                    System.out.println("caught exception on facebook processing: " + ex.toString());
                }
                return;
            }
    }
*/
      //private Map<String, Object> measurements = new HashMap<String, Object>();
      //Map<String, Object> metalTags = new HashMap<String, Object>();

/*
      private String acousticTagSerial = "";
      private String acousticTagId = "";
      private String satelliteTagSerial = "";
      private String satelliteTagArgosPttNumber = "";
      private String satelliteTagName = "";
*/


    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String fileName = "None";
    String username = "None";
    String fullPathFilename="";

        boolean fileSuccess = false;  //kinda pointless now as we just build sentFiles list now at this point (do file work at end)
        String doneMessage = "";
        List<String> filesOK = new ArrayList<String>();
        HashMap<String, String> filesBad = new HashMap<String, String>();

        List<FileItem> formFiles = new ArrayList<FileItem>();
        List<File> socialFiles = new ArrayList<File>();

      //Calendar date = Calendar.getInstance();

        long maxSizeMB = CommonConfiguration.getMaxMediaSizeInMegabytes(context);
        long maxSizeBytes = maxSizeMB * 1048576;

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
                    } else if (item.getName().startsWith("socialphoto_")) {
                        System.out.println(item.getName() + ": " + item.getString("UTF-8"));
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

        if (fv.get("social_files_id") != null) {
          System.out.println("BBB: Social_files_id: "+fv.get("social_files_id"));
          
            //TODO better checking of files (size, type etc)
            File socDir = new File(ServletUtilities.dataDir(context, rootDir) + "/social_files/" + fv.get("social_files_id"));
            for (File sf : socDir.listFiles()) {
                socialFiles.add(sf);
                System.out.println("BBB: Adding social file : "+sf.getName());
                
                filesOK.add(sf.getName());
            }
            filesBad = new HashMap<String, String>();
            fileSuccess = true;
        }

        session.setAttribute("filesOKMessage", (filesOK.isEmpty() ? "none" : Arrays.toString(filesOK.toArray())));
        String badmsg = "";
        for (String key : filesBad.keySet()) {
            badmsg += key + " (" + getVal(filesBad, key) + ") ";
        }
        if (badmsg.equals("")) { badmsg = "none"; }
        session.setAttribute("filesBadMessage", badmsg);

        if (fileSuccess) {

//////////////////////////////////////////// START


//{submitterID=tomcat, submitterProject=, photographerEmail=, metalTag(left)=, sex=unknown, measurement(weight)=34234, location=, acousticTagId=, behavior=yow behavior..., measurement(weightunits)=kilograms, acousticTagSerial=, photographerName=, lifeStage=sub-adult, submitterAddress=, satelliteTagSerial=, releaseDate=, photographerPhone=, measurement(lengthunits)=meters, measurement(weightsamplingProtocol)=samplingProtocol0, measurement(length)=, submitterOrganization=, photographerAddress=, longitude=, year=2014, lat=, measurement(lengthsamplingProtocol)=samplingProtocol0, submitterEmail=, minutes=00, elevation=, measurement(height)=, measurement(heightsamplingProtocol)=samplingProtocol0, scars=None, submitterPhone=, submitterName=tomcat, hour=-1, livingStatus=alive, depth=, country=, satelliteTagName=Wild Life Computers, metalTag(right)=, month=1, measurement(heightunits)=meters, Submit=Send encounter report, informothers=, day=0, satelliteTagArgosPttNumber=, comments=}

      //check for spamBots   TODO possibly move this to Util for general/global usage?
      boolean spamBot = false;
            String[] spamFieldsToCheck = new String[]{"submitterPhone", "submitterName", "photographerName", "photographerPhone", "location", "comments", "behavior"};
      StringBuffer spamFields = new StringBuffer();
            for (int i = 0 ; i < spamFieldsToCheck.length ; i++) {
          spamFields.append(getVal(fv, spamFieldsToCheck[i]));
            }

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


      String locCode = "";
      System.out.println(" **** here is what i think locationID is: " + fv.get("locationID"));
            if ((fv.get("locationID") != null) && !fv.get("locationID").toString().equals("")) {
                locCode = fv.get("locationID").toString();
            }
        //see if the location code can be determined and set based on the location String reported
            else if (fv.get("location") != null) {
          String locTemp = getVal(fv, "location").toLowerCase();
          Properties props = new Properties();

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
        fv.put("locCode", locCode);

        //TODO this should live somewhere else as constant? (e.g. to build in form as well)
        String[] scarType = new String[]{"None", "Tail (caudal) fin", "1st dorsal fin", "2nd dorsal fin", "Left pectoral fin", "Right pectoral fin", "Head", "Body"};
        int scarNum = -1;
        try {
            scarNum = Integer.parseInt(getVal(fv, "scars"));
        } catch (NumberFormatException e) {
            scarNum = -1;
        }
        if ((scarNum < 0) || (scarNum > 7)) {
            scarNum = -1;
        }
        if (scarNum >= 0) {
            fv.put("scars", scarType[scarNum]);
        }


//System.out.println("about to do int stuff");

            //need some ints for day/month/year/hour (other stuff seems to be strings)
            int day = 0, month = -1, year = 0, hour = 0;
            String minutes="";
            //try { day = Integer.parseInt(getVal(fv, "day")); } catch (NumberFormatException e) { day = 0; }
            //try { month = Integer.parseInt(getVal(fv, "month")); } catch (NumberFormatException e) { month = 0; }
            //try { year = Integer.parseInt(getVal(fv, "year")); } catch (NumberFormatException e) { year = 0; }

            //switch to datepicker

            LocalDateTime dt = new LocalDateTime();

            if((getVal(fv, "datepicker")!=null)&&(!getVal(fv, "datepicker").trim().equals(""))){
              //System.out.println("Trying to read date: "+getVal(fv, "datepicker").replaceAll(" ", "T"));
              //boolean badDate=false;
              try{
                DateTimeFormatter parser1 = ISODateTimeFormat.dateOptionalTimeParser();

                LocalDateTime reportedDateTime=new LocalDateTime(parser1.parseMillis(getVal(fv, "datepicker").replaceAll(" ", "T")));
                StringTokenizer str=new StringTokenizer(getVal(fv, "datepicker").replaceAll(" ", "T"),"-");

          int numTokens=str.countTokens();


          if(numTokens>=1){
            //try {
            year=reportedDateTime.getYear();
              if(year>(dt.getYear()+1)){
                //badDate=true;
                year=0;
                throw new Exception("    An unknown exception occurred during date processing in EncounterForm. The user may have input an improper format: "+year+" > "+dt.getYear());
              }

           //} catch (Exception e) { year=-1;}
          }
          if(numTokens>=2){
            try { month=reportedDateTime.getMonthOfYear(); } catch (Exception e) { month=-1;}
          }
          else{month=-1;}
          //see if we can get a day, because we do want to support only yyy-MM too
          if(str.countTokens()>=3){
            try { day=reportedDateTime.getDayOfMonth(); } catch (Exception e) { day=0; }
          }
          else{day=0;}



          //see if we can get a time and hour, because we do want to support only yyy-MM too
          StringTokenizer strTime=new StringTokenizer(getVal(fv, "datepicker").replaceAll(" ", "T"),"T");
          if(strTime.countTokens()>1){
            try { hour=reportedDateTime.getHourOfDay(); } catch (Exception e) { hour=-1; }
            try {minutes=(new Integer(reportedDateTime.getMinuteOfHour()).toString()); } catch (Exception e) {}
          }
          else{hour=-1;}


                  //System.out.println("At the end of time processing I see: "+year+"-"+month+"-"+day+" "+hour+":"+minutes);

              }
              catch(Exception e){
                System.out.println("    An unknown exception occurred during date processing in EncounterForm. The user may have input an improper format.");
                e.printStackTrace();
                processingNotes.append("<p>Error encountered processing this date submitted by user: "+getVal(fv, "datepicker")+"</p>");

              }
         }



            String guess = "no estimate provided";
            if ((fv.get("guess") != null) && !fv.get("guess").toString().equals("")) {
                guess = fv.get("guess").toString();
            }


      //let's handle genus and species for taxonomy
              String genus = null;
              String specificEpithet = null;

      try {

              //now we have to break apart genus species
                if (fv.get("genusSpecies") != null) {
                  StringTokenizer tokenizer=new StringTokenizer(fv.get("genusSpecies").toString()," ");
                  if(tokenizer.countTokens()>=2){

                        genus = tokenizer.nextToken();
                      //enc.setGenus(tokenizer.nextToken());
                      specificEpithet = tokenizer.nextToken().replaceAll(",","").replaceAll("_"," ");
                      //enc.setSpecificEpithet(tokenizer.nextToken().replaceAll(",","").replaceAll("_"," "));

                  }
              //handle malformed Genus Species formats
                  else{throw new Exception("The format of the submitted genusSpecies parameter did not have two tokens delimited by a space (e.g., \"Rhincodon typus\"). The submitted value was: "+fv.get("genusSpecies"));}
                }

            } catch (Exception le) {

            }


System.out.println("about to do enc()");

            Encounter enc = new Encounter(day, month, year, hour, minutes, guess, getVal(fv, "location"), getVal(fv, "submitterName"), getVal(fv, "submitterEmail"), null);
            boolean llSet = false;
            //Encounter enc = new Encounter();
            //System.out.println("Submission detected date: "+enc.getDate());
            String encID = enc.generateEncounterNumber();
            enc.setEncounterNumber(encID);
System.out.println("hey, i think i may have made an encounter, encID=" + encID);
System.out.println("enc ?= " + enc.toString());

            AssetStore astore = AssetStore.getDefault(myShepherd);
            ArrayList<Annotation> newAnnotations = new ArrayList<Annotation>();

            //for directly uploaded files
            for (FileItem item : formFiles) {
              //convert each FileItem into a MediaAsset
              makeMediaAssetsFromJavaFileItemObject(item, encID, astore, enc, newAnnotations, genus, specificEpithet);
            }

            ///////////////////TODO social files also!!!
            System.out.println("BBB: Checking if we have social files...");
            
            if(socialFiles.size()>0){
              int numSocialFiles=socialFiles.size();
              System.out.println("BBB: Trying to persist social files: "+numSocialFiles);
              
              DiskFileItemFactory factory = new DiskFileItemFactory();
              
              for(int q=0;q<numSocialFiles;q++){
                File item=socialFiles.get(q);
                makeMediaAssetsFromJavaFileObject(item, encID, astore, enc, newAnnotations, genus, specificEpithet);
                
              }
              
            }
            

            if (fv.get("mediaAssetSetId") != null) {
                MediaAssetSet maSet = ((MediaAssetSet) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(MediaAssetSet.class, fv.get("mediaAssetSetId")), true)));
                if ((maSet != null) && (maSet.getMediaAssets() != null) && (maSet.getMediaAssets().size() > 0)) {
                    int num = maSet.getMediaAssets().size();
                    for (MediaAsset ma : maSet.getMediaAssets()) {
                        newAnnotations.add(new Annotation(Util.taxonomyString(genus, specificEpithet), ma));
                    }
                    session.setAttribute("filesOKMessage", num + " " + ((num == 1) ? "file" : "files"));
                }
            }

            enc.setAnnotations(newAnnotations);


            enc.setGenus(genus);
            enc.setSpecificEpithet(specificEpithet);


/*
            String baseDir = ServletUtilities.dataDir(context, rootDir);
            ArrayList<SinglePhotoVideo> images = new ArrayList<SinglePhotoVideo>();
            for (FileItem item : formFiles) {
                // this will actually write file to filesystem (or [FUTURE] wherever)
                //  TODO: either (a) undo this if any failure of writing encounter; or (b) dont write til success of enc.
                try {
                    //SinglePhotoVideo spv = new SinglePhotoVideo(encID, item, context, encDataDir);
                    SinglePhotoVideo spv = new SinglePhotoVideo(enc, item, context, baseDir);
                    //images.add(spv);
                    enc.addSinglePhotoVideo(spv);
                } catch (Exception ex) {
                    System.out.println("failed to save " + item.toString() + ": " + ex.toString());
                }
            }
            for (File sf : socialFiles) {
								File encDir = new File(enc.dir(baseDir));
								if (!encDir.exists()) encDir.mkdirs();
                File targetFile = new File(encDir, sf.getName());
System.out.println("socialFile copy: " + sf.toString() + " ---> " + targetFile.toString());
                Files.copy(sf.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                SinglePhotoVideo spv = new SinglePhotoVideo(encID, targetFile);
                enc.addSinglePhotoVideo(spv);
            }
*/


      //now let's add our encounter to the database

      enc.setComments(getVal(fv, "comments").replaceAll("\n", "<br>"));
      if (fv.get("releaseDate") != null && fv.get("releaseDate").toString().length() > 0) {
        String dateFormatPattern = CommonConfiguration.getProperty("releaseDateFormat",context);
        try {
          SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormatPattern);
          enc.setReleaseDate(simpleDateFormat.parse(fv.get("releaseDate").toString()).getTime());
        } catch (Exception e) {
          enc.addComments("<p>Reported release date was problematic: " + fv.get("releaseDate") + "</p>");
        }
      }
      if (fv.get("behavior") != null && fv.get("behavior").toString().length() > 0) {
              enc.setBehavior(fv.get("behavior").toString());
          }
      if (fv.get("alternateID") != null && fv.get("alternateID").toString().length() > 0) {
        enc.setAlternateID(fv.get("alternateID").toString());
      }
      if (fv.get("lifeStage") != null && fv.get("lifeStage").toString().length() > 0) {
              enc.setLifeStage(fv.get("lifeStage").toString());
          }



      List<MetalTag> metalTags = getMetalTags(fv);
      for (MetalTag metalTag : metalTags) {
        enc.addMetalTag(metalTag);
      }

      List<Measurement> measurements = getMeasurements(fv, encID, context);
      for (Measurement measurement : measurements) {
        enc.setMeasurement(measurement, myShepherd);
      }


      enc.setAcousticTag(getAcousticTag(fv));
      enc.setSatelliteTag(getSatelliteTag(fv));
      enc.setSex(getVal(fv, "sex"));
      enc.setLivingStatus(getVal(fv, "livingStatus"));


      if(fv.get("scars")!=null){
        enc.setDistinguishingScar(fv.get("scars").toString());
      }


      int sizePeriod=0;
      if ((fv.get("measureUnits") != null) && fv.get("measureUnits").toString().equals("Feet")) {

        if((fv.get("depth") != null) && !fv.get("depth").toString().equals("")){
            try{
                double tempDouble=(new Double(fv.get("depth").toString())).doubleValue()/3.3;
                String truncDepth = (new Double(tempDouble)).toString();
                sizePeriod = truncDepth.indexOf(".");
                truncDepth = truncDepth.substring(0, sizePeriod + 2);
                fv.put("depth", (new Double(truncDepth)).toString());
            }
            catch(java.lang.NumberFormatException nfe){
                enc.addComments("<p>Reported depth was problematic: " + fv.get("depth").toString() + "</p>");
                fv.put("depth", "");
            }
            catch(NullPointerException npe){
                fv.put("depth", "");
            }
        }
System.out.println("depth --> " + fv.get("depth").toString());

        if ((fv.get("elevation") != null) && !fv.get("elevation").toString().equals("")) {
            try{
                double tempDouble=(new Double(fv.get("elevation").toString())).doubleValue()/3.3;
                String truncElev = (new Double(tempDouble)).toString();
                //String truncElev = ((new Double(elevation)) / 3.3).toString();
                sizePeriod = truncElev.indexOf(".");
                truncElev = truncElev.substring(0, sizePeriod + 2);
                fv.put("elevation", (new Double(truncElev)).toString());
            }
            catch(java.lang.NumberFormatException nfe){
                enc.addComments("<p>Reported elevation was problematic: " + fv.get("elevation").toString() + "</p>");
                fv.put("elevation", "");
            }
            catch(NullPointerException npe){
                fv.put("elevation", "");
            }
        }

        if ((fv.get("size") != null) && !fv.get("size").toString().equals("")) {

            try{
                    double tempDouble=(new Double(fv.get("size").toString())).doubleValue()/3.3;
                    String truncSize = (new Double(tempDouble)).toString();
                    //String truncSize = ((new Double(size)) / 3.3).toString();
                    sizePeriod = truncSize.indexOf(".");
                    truncSize = truncSize.substring(0, sizePeriod + 2);
                    fv.put("size", (new Double(truncSize)).toString());
            }
            catch(java.lang.NumberFormatException nfe){

                enc.addComments("<p>Reported size was problematic: " + fv.get("size").toString() + "</p>");
                fv.put("size", "");
            }
            catch(NullPointerException npe){
                fv.put("size", "");
            }
        }
      }  //measureUnits

        if ((fv.get("size") != null) && !fv.get("size").toString().equals("")) {
            try {
                enc.setSize(new Double(fv.get("size").toString()));
            }
            catch(java.lang.NumberFormatException nfe){
                enc.addComments("<p>Reported size was problematic: " + fv.get("size").toString() + "</p>");
                fv.put("size", "");
            }
            catch(NullPointerException npe){
                fv.put("size", "");
            }
         }


        if ((fv.get("elevation") != null) && !fv.get("elevation").toString().equals("")) {
            try {
                enc.setMaximumElevationInMeters(new Double(fv.get("elevation").toString()));
            }
            catch(java.lang.NumberFormatException nfe){
                enc.addComments("<p>Reported elevation was problematic: " + fv.get("elevation").toString() + "</p>");
                fv.put("elevatoin", "");
            }
            catch(NullPointerException npe){
                fv.put("elevation", "");
            }
         }

        if ((fv.get("depth") != null) && !fv.get("depth").toString().equals("")) {
            try {
                enc.setDepth(new Double(fv.get("depth").toString()));
            }
            catch(java.lang.NumberFormatException nfe){
                enc.addComments("<p>Reported depth was problematic: " + fv.get("depth").toString() + "</p>");
                fv.put("depth", "");
            }
            catch(NullPointerException npe){
                fv.put("depth", "");
            }
         }


        //let's handle the GPS
        if ((fv.get("lat") != null) && (fv.get("longitude") != null) && !fv.get("lat").toString().equals("") && !fv.get("longitude").toString().equals("")) {
        //enc.setGPSLatitude(lat + "&deg; " + gpsLatitudeMinutes + "\' " + gpsLatitudeSeconds + "\" " + latDirection);


        try {
          double degrees = (new Double(fv.get("lat").toString())).doubleValue();
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

          double degrees2 = (new Double(fv.get("longitude").toString())).doubleValue();
          double position2 = degrees2;
          enc.setDWCDecimalLongitude(position2);
            llSet = true;


        } catch (Exception e) {
          System.out.println("EncounterSetGPS: problem!");
          e.printStackTrace();
        }


      }

      //enc.setMeasureUnits("Meters");
      enc.setSubmitterPhone(getVal(fv, "submitterPhone"));
      enc.setSubmitterAddress(getVal(fv, "submitterAddress"));
      enc.setSubmitterOrganization(getVal(fv, "submitterOrganization"));
      enc.setSubmitterProject(getVal(fv, "submitterProject"));

      enc.setPhotographerPhone(getVal(fv, "photographerPhone"));
      enc.setPhotographerAddress(getVal(fv, "photographerAddress"));
      enc.setPhotographerName(getVal(fv, "photographerName"));
      enc.setPhotographerEmail(getVal(fv, "photographerEmail"));
      enc.addComments("<p>Submitted on " + (new java.util.Date()).toString() + " from address: " + request.getRemoteHost() + "</p>");
      //enc.approved = false;

      enc.addComments(processingNotes.toString());


      if(CommonConfiguration.getProperty("encounterState0",context)!=null){
        enc.setState(CommonConfiguration.getProperty("encounterState0",context));
      }
      if (request.getRemoteUser() != null) {
        enc.setSubmitterID(request.getRemoteUser());
      } else {
        enc.setSubmitterID("N/A");
      }
      if (!getVal(fv, "locCode").equals("")) {
        enc.setLocationCode(locCode);
      }
      if (!getVal(fv, "country").equals("")) {
        enc.setCountry(getVal(fv, "country"));
      }
      if (!getVal(fv, "informothers").equals("")) {
        enc.setInformOthers(getVal(fv, "informothers"));
      }

      // xxxxxxx
      //add research team for GAq
      if (!getVal(fv, "researchTeam").equals("")) {
        enc.setDynamicProperty("Research Team", (getVal(fv, "researchTeam")));
      }
      if (!getVal(fv, "vessel").equals("")) {
        enc.setDynamicProperty("Vessel", (getVal(fv, "vessel")));
      }
      if (!getVal(fv, "conditions").equals("")) {
        enc.setDynamicProperty("Conditions", (getVal(fv, "conditions")));
      }

      if (!getVal(fv, "camera").equals("")) {
        enc.setDynamicProperty("Camera", (getVal(fv, "camera")));
      }
      if (!getVal(fv, "lens").equals("")) {
        enc.setDynamicProperty("Lens", (getVal(fv, "lens")));
      }
      if (!getVal(fv, "card").equals("")) {
        enc.setDynamicProperty("Card", (getVal(fv, "card")));
      }
      if (!getVal(fv, "folder").equals("")) {
        enc.setDynamicProperty("Folder", (getVal(fv, "folder")));
      }

      if (!getVal(fv, "numberOfBoats").equals("")) {
        enc.setDynamicProperty("Number of boats", (getVal(fv, "numberOfBoats")));
      }

      if (!getVal(fv, "startTime").equals("")) {
        enc.setDynamicProperty("Start Time", (getVal(fv, "startTime")));
      }

      if (!getVal(fv, "endTime").equals("")) {
        enc.setDynamicProperty("End Time", (getVal(fv, "endTime")));
      }


      if (!getVal(fv, "endLongitude").equals("")) {
        enc.setDynamicProperty("End Longitude", (getVal(fv, "endLongitude")));
      }
      if (!getVal(fv, "endLatitude").equals("")) {
        enc.setDynamicProperty("End Latitude", (getVal(fv, "endLatitude")));
      }

      if (!getVal(fv, "startLongitude").equals("")) {
        enc.setDynamicProperty("Start Longitude", (getVal(fv, "startLongitude")));
      }
      if (!getVal(fv, "startLatitude").equals("")) {
        enc.setDynamicProperty("Start Latitude", (getVal(fv, "startLatitude")));
      }

      if (!getVal(fv, "beginWaypoint").equals("")) {
        enc.setDynamicProperty("Begin Waypoint", (getVal(fv, "beginWaypoint")));
      }
      if (!getVal(fv, "endWaypoint").equals("")) {
        enc.setDynamicProperty("End Waypoint", (getVal(fv, "endWaypoint")));
      }



      //xxxxxxxx


      String guid = CommonConfiguration.getGlobalUniqueIdentifierPrefix(context) + encID;

      //new additions for DarwinCore
      enc.setDWCGlobalUniqueIdentifier(guid);
      enc.setDWCImageURL((request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encID));

      //populate DarwinCore dates

      DateTimeFormatter fmt = ISODateTimeFormat.date();
      String strOutputDateTime = fmt.print(dt);
      enc.setDWCDateAdded(strOutputDateTime);
      enc.setDWCDateAdded(new Long(dt.toDateTime().getMillis()));
      //System.out.println("I set the date as a LONG to: "+enc.getDWCDateAddedLong());
      enc.setDWCDateLastModified(strOutputDateTime);


        //this will try to set from MediaAssetMetadata -- ymmv
        if (!llSet) enc.setLatLonFromAssets();
        if (enc.getYear() < 1) enc.setDateFromAssets();

            String newnum = "";
            if (!spamBot) {
                newnum = myShepherd.storeNewEncounter(enc, encID);
                //enc.refreshAssetFormats(context, ServletUtilities.dataDir(context, rootDir));
                enc.refreshAssetFormats(myShepherd);

                Logger log = LoggerFactory.getLogger(EncounterForm.class);
                log.info("New encounter submission: <a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encID+"\">"+encID+"</a>");
System.out.println("ENCOUNTER SAVED???? newnum=" + newnum);
            }

      if (newnum.equals("fail")) {
        request.setAttribute("number", "fail");
        return;
      }





      //return a forward to display.jsp
      System.out.println("Ending data submission.");
      if (!spamBot) {
        response.sendRedirect(request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/confirmSubmit.jsp?number=" + encID);
      } else {
        response.sendRedirect(request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/spambot.jsp");
      }


    }  //end "if (fileSuccess)

    myShepherd.closeDBTransaction();
    //return null;
  }
  
  private void makeMediaAssetsFromJavaFileItemObject(FileItem item, String encID, AssetStore astore, Encounter enc, ArrayList<Annotation> newAnnotations, String genus, String specificEpithet){
    JSONObject sp = astore.createParameters(new File(enc.subdir() + File.separator + item.getName()));
    sp.put("key", Util.hashDirectories(encID) + "/" + item.getName());
    MediaAsset ma = new MediaAsset(astore, sp);
    File tmpFile = ma.localPath().toFile();  //conveniently(?) our local version to save ma.cacheLocal() from having to do anything?
    File tmpDir = tmpFile.getParentFile();
    if (!tmpDir.exists()) tmpDir.mkdirs();
//System.out.println("attempting to write uploaded file to " + tmpFile);
    try {
      item.write(tmpFile);
    } catch (Exception ex) {
        System.out.println("Could not write " + tmpFile + ": " + ex.toString());
    }
    if (tmpFile.exists()) {
      
      try{
        ma.addLabel("_original");
        ma.copyIn(tmpFile);
        ma.updateMetadata();
        newAnnotations.add(new Annotation(Util.taxonomyString(genus, specificEpithet), ma));
      }
      catch(IOException ioe){
        System.out.println("Hit an IOException trying to transform file "+item.getName()+" into a MediaAsset in EncounterFom.class.");
        ioe.printStackTrace();
      }
        
        
    } 
    else {
        System.out.println("failed to write file " + tmpFile);
    }
  }
  
  private void makeMediaAssetsFromJavaFileObject(File item, String encID, AssetStore astore, Encounter enc, ArrayList<Annotation> newAnnotations, String genus, String specificEpithet){
    
    System.out.println("Entering makeMediaAssetsFromJavaFileObject");
    
    JSONObject sp = astore.createParameters(new File(enc.subdir() + File.separator + item.getName()));
    sp.put("key", Util.hashDirectories(encID) + "/" + item.getName());
    MediaAsset ma = new MediaAsset(astore, sp);
    File tmpFile = ma.localPath().toFile();  //conveniently(?) our local version to save ma.cacheLocal() from having to do anything?
    File tmpDir = tmpFile.getParentFile();
    if (!tmpDir.exists()) tmpDir.mkdirs();
//System.out.println("attempting to write uploaded file to " + tmpFile);
    try {
      FileUtilities.copyFile(item, tmpFile);
      //item.write(tmpFile);
    } catch (Exception ex) {
        System.out.println("Could not write " + tmpFile + ": " + ex.toString());
    }
    if (tmpFile.exists()) {
      
      try{
        ma.addLabel("_original");
        ma.copyIn(tmpFile);
        ma.updateMetadata();
        newAnnotations.add(new Annotation(Util.taxonomyString(genus, specificEpithet), ma));
        System.out.println("Added new annotation for: "+item.getName());
      }
      catch(IOException ioe){
        System.out.println("Hit an IOException trying to transform file "+item.getName()+" into a MediaAsset in EncounterFom.class.");
        ioe.printStackTrace();
      }
        
        
    } 
    else {
        System.out.println("failed to write file " + tmpFile);
    }
  }


}