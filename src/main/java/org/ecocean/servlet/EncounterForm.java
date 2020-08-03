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
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ThreadPoolExecutor;
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
import org.ecocean.MailThreadExecutorService;
import org.ecocean.Util;
import org.ecocean.MarkedIndividual;
import org.ecocean.Keyword;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.Measurement;
import org.ecocean.Shepherd;
import org.ecocean.media.*;
import org.ecocean.ia.Task;
import org.ecocean.NotificationMailer;
import org.ecocean.ShepherdProperties;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.tag.AcousticTag;
import org.ecocean.tag.MetalTag;
import org.ecocean.tag.SatelliteTag;
import org.ecocean.identity.IBEISIA;
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
    private String getVal(Map formValues, String key) {
        if (formValues.get(key) == null) {
            return "";
        }
        return formValues.get(key).toString();
    }

  private SatelliteTag getSatelliteTag(Map formValues) {
    String argosPttNumber =  getVal(formValues, "satelliteTagArgosPttNumber");
    String satelliteTagName = getVal(formValues, "satelliteTagName");
    String tagSerial = getVal(formValues, "satelliteTagSerial");
    if (argosPttNumber.length() > 0 || tagSerial.length() > 0) {
      return new SatelliteTag(satelliteTagName, tagSerial, argosPttNumber);
    }
    return null;
  }

  private AcousticTag getAcousticTag(Map formValues) {
    String acousticTagId = getVal(formValues, "acousticTagId");
    String acousticTagSerial = getVal(formValues, "acousticTagSerial");
    if (acousticTagId.length() > 0 || acousticTagSerial.length() > 0) {
      return new AcousticTag(acousticTagSerial, acousticTagId);
    }
    return null;
  }


  private List<MetalTag> getMetalTags(Map formValues) {
    List<MetalTag> list = new ArrayList<MetalTag>();
        List<String> keys = Arrays.asList("left", "right");  //TODO programatically build from form

    for (String key : keys) {
      // The keys are the location
      String value = getVal(formValues, "metalTag(" + key + ")");
      if (value.length() > 0) {
        list.add(new MetalTag(value, key));
      }
    }
    return list;
  }


  private List<Measurement> getMeasurements(Map formValues, String encID, String context) {
    List<Measurement> list = new ArrayList<Measurement>();
        //List<String> keys = Arrays.asList("weight", "length", "height");  //TODO programatically build from form

    //dynamically adapt to project-specific measurements
        List<String> keys=CommonConfiguration.getIndexedPropertyValues("measurement", context);

    for (String key : keys) {
      String value = getVal(formValues, "measurement(" + key + ")");
      String units = getVal(formValues, "measurement(" + key + "units)");
      String samplingProtocol = getVal(formValues, "measurement(" + key + "samplingProtocol)");
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

        Map formValues = new HashMap();

        //IMPORTANT - processingNotes can be used to add notes on data handling (e.g., poorly formatted dates) that can be reconciled later by the reviewer
        //Example usage: processingNotes.append("<p>Error encountered processing this date submitted by user: "+getVal(formValues, "datepicker")+"</p>");
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
                        formValues.put(item.getFieldName(), ServletUtilities.preventCrossSiteScriptingAttacks(item.getString("UTF-8").trim()));  //TODO do we want trim() here??? -jon
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
            System.out.println("Not a multi-part form submission!");
        }



        if (formValues.get("social_files_id") != null) {
          System.out.println("BBB: Social_files_id: "+formValues.get("social_files_id"));

            //TODO better checking of files (size, type etc)
            File socDir = new File(ServletUtilities.dataDir(context, rootDir) + "/social_files/" + formValues.get("social_files_id"));
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
            String[] spamFieldsToCheck = new String[]{"submitterPhone", "submitterName", "photographerName", ""
                + "Phone", "location", "comments", "behavior"};
      StringBuffer spamFields = new StringBuffer();
            for (int i = 0 ; i < spamFieldsToCheck.length ; i++) {
          spamFields.append(getVal(formValues, spamFieldsToCheck[i]));
            }

      if (spamFields.toString().toLowerCase().indexOf("porn") != -1) {
        spamBot = true;
      }
      if (spamFields.toString().toLowerCase().indexOf("href") != -1) {
        spamBot = true;
      }

      System.out.println("spambot: "+spamBot);
      //else if(spamFields.toString().toLowerCase().indexOf("[url]")!=-1){spamBot=true;}
      //else if(spamFields.toString().toLowerCase().indexOf("url=")!=-1){spamBot=true;}
      //else if(spamFields.toString().toLowerCase().trim().equals("")){spamBot=true;}
      //else if((theForm.getSubmitterID()!=null)&&(theForm.getSubmitterID().equals("N%2FA"))) {spamBot=true;}


      String locCode = "";
      System.out.println(" **** here is what i think locationID is: " + formValues.get("locationID"));
            if ((formValues.get("locationID") != null) && !formValues.get("locationID").toString().equals("")) {
                locCode = formValues.get("locationID").toString();
            }
        //see if the location code can be determined and set based on the location String reported
            else if (formValues.get("location") != null) {
          String locTemp = getVal(formValues, "location").toLowerCase();
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
        formValues.put("locCode", locCode);

        //TODO this should live somewhere else as constant? (e.g. to build in form as well)
        String[] scarType = new String[]{"None", "Tail (caudal) fin", "1st dorsal fin", "2nd dorsal fin", "Left pectoral fin", "Right pectoral fin", "Head", "Body"};
        int scarNum = -1;
        try {
            scarNum = Integer.parseInt(getVal(formValues, "scars"));
        } catch (NumberFormatException e) {
            scarNum = -1;
        }
        if ((scarNum < 0) || (scarNum > 7)) {
            scarNum = -1;
        }
        if (scarNum >= 0) {
            formValues.put("scars", scarType[scarNum]);
        }


//System.out.println("about to do int stuff");

            //need some ints for day/month/year/hour (other stuff seems to be strings)
            int day = 0, month = -1, year = 0, hour = 0;
            String minutes="";
            //try { day = Integer.parseInt(getVal(formValues, "day")); } catch (NumberFormatException e) { day = 0; }
            //try { month = Integer.parseInt(getVal(formValues, "month")); } catch (NumberFormatException e) { month = 0; }
            //try { year = Integer.parseInt(getVal(formValues, "year")); } catch (NumberFormatException e) { year = 0; }

            //switch to datepicker

            LocalDateTime dt = new LocalDateTime();

            if((getVal(formValues, "datepicker")!=null)&&(!getVal(formValues, "datepicker").trim().equals(""))){
              //System.out.println("Trying to read date: "+getVal(formValues, "datepicker").replaceAll(" ", "T"));
              //boolean badDate=false;
              try{
                DateTimeFormatter parser1 = ISODateTimeFormat.dateOptionalTimeParser();

                LocalDateTime reportedDateTime=new LocalDateTime(parser1.parseMillis(getVal(formValues, "datepicker").replaceAll(" ", "T")));
                StringTokenizer str=new StringTokenizer(getVal(formValues, "datepicker").replaceAll(" ", "T"),"-");

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
          StringTokenizer strTime=new StringTokenizer(getVal(formValues, "datepicker").replaceAll(" ", "T"),"T");
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
                processingNotes.append("<p>Error encountered processing this date submitted by user: "+getVal(formValues, "datepicker")+"</p>");

              }
         }



            String guess = "no estimate provided";
            if ((formValues.get("guess") != null) && !formValues.get("guess").toString().equals("")) {
                guess = formValues.get("guess").toString();
            }


      //let's handle genus and species for taxonomy
              String genus = null;
              String specificEpithet = null;

      try {

              //now we have to break apart genus species
                if (formValues.get("genusSpecies") != null) {
                  StringTokenizer tokenizer=new StringTokenizer(formValues.get("genusSpecies").toString()," ");
                  if(tokenizer.countTokens()>=2){

                        genus = tokenizer.nextToken();
                      //enc.setGenus(tokenizer.nextToken());
                      specificEpithet = tokenizer.nextToken().replaceAll(",","").replaceAll("_"," ");
                      //enc.setSpecificEpithet(tokenizer.nextToken().replaceAll(",","").replaceAll("_"," "));

                  }
              //handle malformed Genus Species formats
                  else{throw new Exception("The format of the submitted genusSpecies parameter did not have two tokens delimited by a space (e.g., \"Rhincodon typus\"). The submitted value was: "+formValues.get("genusSpecies"));}
                }

            } catch (Exception le) {
                le.printStackTrace();
            }


System.out.println("about to do enc()");

            Encounter enc = new Encounter(day, month, year, hour, minutes, guess, getVal(formValues, "location"));
            boolean llSet = false;
            //Encounter enc = new Encounter();
            //System.out.println("Submission detected date: "+enc.getDate());

            String encID = enc.generateEncounterNumber();
            if ((formValues.get("catalogNumber") != null)&&(!formValues.get("catalogNumber").toString().trim().equals(""))) {
              if((!myShepherd.isEncounter(formValues.get("catalogNumber").toString()))){
                encID=formValues.get("catalogNumber").toString().trim();
              }
            }
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


            if (formValues.get("mediaAssetSetId") != null) {
                MediaAssetSet maSet = ((MediaAssetSet) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(MediaAssetSet.class, formValues.get("mediaAssetSetId")), true)));
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


            //User management
            String subN=getVal(formValues, "submitterName");
            String subE=getVal(formValues, "submitterEmail");
            subE = subE.toLowerCase();
            String subO=getVal(formValues, "submitterOrganization");
            if (Util.stringExists(subO)) enc.setSubmitterOrganization(subO);
            String subP=getVal(formValues, "submitterProject");
            if (Util.stringExists(subP)) enc.setSubmitterOrganization(subP);
            //User user=null;
            List<User> submitters=new ArrayList<User>();
            if((subE!=null)&&(!subE.trim().equals(""))) {

              StringTokenizer str=new StringTokenizer(subE,",");
              int numTokens=str.countTokens();
              for(int y=0;y<numTokens;y++) {
                String tok=str.nextToken().trim();
                String hashedTok=ServletUtilities.hashString(tok);
                if(myShepherd.getUserByHashedEmailAddress(hashedTok)!=null) {
                  User user=myShepherd.getUserByHashedEmailAddress(hashedTok);
                  submitters.add(user);
                }
                else {
                  User user=new User(tok,Util.generateUUID()); //TODO delete TODO comment if this is still here
                  user.setAffiliation(subO);
                  user.setUserProject(subP);
                  if((numTokens==1)&&(subN!=null)){user.setFullName(subN);}
                  myShepherd.getPM().makePersistent(user);
                  myShepherd.commitDBTransaction();
                  myShepherd.beginDBTransaction();

                  submitters.add(user);
                }
              }
            }
            enc.setSubmitters(submitters);
            //end submitter-user processing

            //User management - photographer processing
            String photoN=getVal(formValues, "photographerName");
            String photoE=getVal(formValues, "photographerEmail");
            photoE = photoE.toLowerCase();
            List<User> photographers=new ArrayList<User>();
            if((photoE!=null)&&(!photoE.trim().equals(""))) {

              StringTokenizer str=new StringTokenizer(photoE,",");
              int numTokens=str.countTokens();
              for(int y=0;y<numTokens;y++) {
                String tok=str.nextToken().trim();
                if(myShepherd.getUserByEmailAddress(tok.trim())!=null) {
                  User user=myShepherd.getUserByEmailAddress(tok);
                  if((numTokens==1)&&(photoN!=null)&&(user.getFullName()==null)){user.setFullName(photoN);}
                  photographers.add(user);
                }
                else {
                  User user=new User(tok,Util.generateUUID()); //TODO delete this TODO if still here
                  if((numTokens==1)&&(photoN!=null)){user.setFullName(photoN);}
                  myShepherd.getPM().makePersistent(user);
                  myShepherd.commitDBTransaction();
                  myShepherd.beginDBTransaction();
                  photographers.add(user);
                }
              }
            }
            enc.setPhotographers(photographers);
            //end photographer-user processing


            //User management - informOthers processing
            String othersString=getVal(formValues, "informothers");
            othersString = othersString.toLowerCase();
            List<User> informOthers=new ArrayList<User>();
            if((othersString!=null)&&(!othersString.trim().equals(""))) {

              StringTokenizer str=new StringTokenizer(othersString,",");
              int numTokens=str.countTokens();
              for(int y=0;y<numTokens;y++) {
                String tok=str.nextToken().trim();
                if(myShepherd.getUserByEmailAddress(tok.trim())!=null) {
                  User user=myShepherd.getUserByEmailAddress(tok);
                  informOthers.add(user);
                }
                else {
                  User user=new User(tok,Util.generateUUID());
                  myShepherd.getPM().makePersistent(user);
                  myShepherd.commitDBTransaction();
                  myShepherd.beginDBTransaction();
                  informOthers.add(user);
                }
              }
            }
            enc.setInformOthers(informOthers);
            //end informOthers-user processing



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

      enc.setComments(getVal(formValues, "comments").replaceAll("\n", "<br>"));
      if (formValues.get("releaseDate") != null && formValues.get("releaseDate").toString().length() > 0) {
        String dateFormatPattern = CommonConfiguration.getProperty("releaseDateFormat",context);
        try {
          SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormatPattern);
          enc.setReleaseDate(simpleDateFormat.parse(formValues.get("releaseDate").toString()).getTime());
        } catch (Exception e) {
          enc.addComments("<p>Reported release date was problematic: " + formValues.get("releaseDate") + "</p>");
        }
      }
      if (formValues.get("behavior") != null && formValues.get("behavior").toString().length() > 0) {
              enc.setBehavior(formValues.get("behavior").toString());
          }
      if (formValues.get("alternateID") != null && formValues.get("alternateID").toString().length() > 0) {
        enc.setAlternateID(formValues.get("alternateID").toString());
      }
      if (formValues.get("lifeStage") != null && formValues.get("lifeStage").toString().length() > 0) {
              enc.setLifeStage(formValues.get("lifeStage").toString());
          }


      if (formValues.get("flukeType") != null && formValues.get("flukeType").toString().length() > 0) {
        System.out.println("        ENCOUNTERFORM:");
        System.out.println("        ENCOUNTERFORM:");
        System.out.println("        ENCOUNTERFORM:");
        String kwName = formValues.get("flukeType").toString();
        Keyword kw = myShepherd.getOrCreateKeyword(kwName);
        for (Annotation ann: enc.getAnnotations()) {
            MediaAsset ma = ann.getMediaAsset();
            if (ma!=null) {
              ma.addKeyword(kw);
              System.out.println("ENCOUNTERFORM: added flukeType keyword to encounter: "+kwName);
            }
        }
          System.out.println("        ENCOUNTERFORM:");
          System.out.println("        ENCOUNTERFORM:");
      }

      if (formValues.get("manualID") != null && formValues.get("manualID").toString().length() > 0) {
            String indID = formValues.get("manualID").toString();
            MarkedIndividual ind = myShepherd.getMarkedIndividualQuiet(indID);
            if (ind==null) {
                ind = new MarkedIndividual(enc);
                ind.addName(request, indID); // we don't just create the individual using the encounter+indID bc this request might key the name off of the logged-in user
                myShepherd.storeNewMarkedIndividual(ind);
                ind.refreshNamesCache();
                System.out.println("        ENCOUNTERFORM: created new individual "+indID);
            } else {
                ind.addEncounter(enc);
                ind.addName(request, indID); // adds the just-entered name to the individual
                System.out.println("        ENCOUNTERFORM: added enc to individual "+indID);
            }
            if (ind!=null) enc.setIndividual(ind);
            enc.setFieldID(indID);
        }

      if (formValues.get("occurrenceID") != null && formValues.get("occurrenceID").toString().length() > 0) {
        String occID = formValues.get("occurrenceID").toString();
        System.out.println("there is an occurenceID, and it is: " + occID);
        enc.setOccurrenceID(occID);
        createOccurrenceIfMissingAndAddEncounter(occID, enc, myShepherd);
      }else{
        System.out.println("OccurrenceID isn't getting fetched from the form");
        String occID = Util.generateUUID();
        enc.setOccurrenceID(occID);
        createOccurrenceIfMissingAndAddEncounter(occID, enc, myShepherd);
      }

      List<MetalTag> metalTags = getMetalTags(formValues);
      for (MetalTag metalTag : metalTags) {
        enc.addMetalTag(metalTag);
      }

      List<Measurement> measurements = getMeasurements(formValues, encID, context);
      for (Measurement measurement : measurements) {
        enc.setMeasurement(measurement, myShepherd);
      }
      enc.setAcousticTag(getAcousticTag(formValues));
      enc.setSatelliteTag(getSatelliteTag(formValues));
      enc.setSex(getVal(formValues, "sex"));
      enc.setLivingStatus(getVal(formValues, "livingStatus"));
      if(formValues.get("scars")!=null){
        enc.setDistinguishingScar(formValues.get("scars").toString());
      }
      int sizePeriod=0;
      if ((formValues.get("measureUnits") != null) && formValues.get("measureUnits").toString().equals("Feet")) {

        if((formValues.get("depth") != null) && !formValues.get("depth").toString().equals("")){
            try{
                double tempDouble=(new Double(formValues.get("depth").toString())).doubleValue()/3.3;
                String truncDepth = (new Double(tempDouble)).toString();
                sizePeriod = truncDepth.indexOf(".");
                truncDepth = truncDepth.substring(0, sizePeriod + 2);
                formValues.put("depth", (new Double(truncDepth)).toString());
            }
            catch(java.lang.NumberFormatException nfe){
                enc.addComments("<p>Reported depth was problematic: " + formValues.get("depth").toString() + "</p>");
                formValues.put("depth", "");
            }
            catch(NullPointerException npe){
                formValues.put("depth", "");
            }
        }
System.out.println("depth --> " + formValues.get("depth").toString());

        if ((formValues.get("elevation") != null) && !formValues.get("elevation").toString().equals("")) {
            try{
                double tempDouble=(new Double(formValues.get("elevation").toString())).doubleValue()/3.3;
                String truncElev = (new Double(tempDouble)).toString();
                //String truncElev = ((new Double(elevation)) / 3.3).toString();
                sizePeriod = truncElev.indexOf(".");
                truncElev = truncElev.substring(0, sizePeriod + 2);
                formValues.put("elevation", (new Double(truncElev)).toString());
            }
            catch(java.lang.NumberFormatException nfe){
                enc.addComments("<p>Reported elevation was problematic: " + formValues.get("elevation").toString() + "</p>");
                formValues.put("elevation", "");
            }
            catch(NullPointerException npe){
                formValues.put("elevation", "");
            }
        }

        if ((formValues.get("size") != null) && !formValues.get("size").toString().equals("")) {

            try{
                    double tempDouble=(new Double(formValues.get("size").toString())).doubleValue()/3.3;
                    String truncSize = (new Double(tempDouble)).toString();
                    //String truncSize = ((new Double(size)) / 3.3).toString();
                    sizePeriod = truncSize.indexOf(".");
                    truncSize = truncSize.substring(0, sizePeriod + 2);
                    formValues.put("size", (new Double(truncSize)).toString());
            }
            catch(java.lang.NumberFormatException nfe){

                enc.addComments("<p>Reported size was problematic: " + formValues.get("size").toString() + "</p>");
                formValues.put("size", "");
            }
            catch(NullPointerException npe){
                formValues.put("size", "");
            }
        }
      }  //measureUnits

        if ((formValues.get("size") != null) && !formValues.get("size").toString().equals("")) {
            try {
                enc.setSize(new Double(formValues.get("size").toString()));
            }
            catch(java.lang.NumberFormatException nfe){
                enc.addComments("<p>Reported size was problematic: " + formValues.get("size").toString() + "</p>");
                formValues.put("size", "");
            }
            catch(NullPointerException npe){
                formValues.put("size", "");
            }
         }


        if ((formValues.get("elevation") != null) && !formValues.get("elevation").toString().equals("")) {
            try {
                enc.setMaximumElevationInMeters(new Double(formValues.get("elevation").toString()));
            }
            catch(java.lang.NumberFormatException nfe){
                enc.addComments("<p>Reported elevation was problematic: " + formValues.get("elevation").toString() + "</p>");
                formValues.put("elevatoin", "");
            }
            catch(NullPointerException npe){
                formValues.put("elevation", "");
            }
         }

        if ((formValues.get("depth") != null) && !formValues.get("depth").toString().equals("")) {
            try {
                enc.setDepth(new Double(formValues.get("depth").toString()));
            }
            catch(java.lang.NumberFormatException nfe){
                enc.addComments("<p>Reported depth was problematic: " + formValues.get("depth").toString() + "</p>");
                formValues.put("depth", "");
            }
            catch(NullPointerException npe){
                formValues.put("depth", "");
            }
         }


        //let's handle the GPS
        if ((formValues.get("lat") != null) && (formValues.get("longitude") != null) && !formValues.get("lat").toString().equals("") && !formValues.get("longitude").toString().equals("")) {
        //enc.setGPSLatitude(lat + "&deg; " + gpsLatitudeMinutes + "\' " + gpsLatitudeSeconds + "\" " + latDirection);


        try {
          double degrees = (new Double(formValues.get("lat").toString())).doubleValue();
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

          double degrees2 = (new Double(formValues.get("longitude").toString())).doubleValue();
          double position2 = degrees2;
          enc.setDWCDecimalLongitude(position2);
            llSet = true;


        } catch (Exception e) {
          System.out.println("EncounterSetGPS: problem!");
          e.printStackTrace();
        }


      }

      //enc.setMeasureUnits("Meters");
     // enc.setSubmitterPhone(getVal(formValues, "submitterPhone"));
      //enc.setSubmitterAddress(getVal(formValues, "submitterAddress"));


     // enc.setPhotographerPhone(getVal(formValues, "photographerPhone"));
     // enc.setPhotographerAddress(getVal(formValues, "photographerAddress"));
     // enc.setPhotographerName(getVal(formValues, "photographerName"));
     // enc.setPhotographerEmail(getVal(formValues, "photographerEmail"));
      enc.addComments("<p>Submitted on " + (new java.util.Date()).toString() + " from address: " + ServletUtilities.getRemoteHost(request) + "</p>");
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
      if (!getVal(formValues, "locCode").equals("")) {
        enc.setLocationCode(locCode);
      }
      if (!getVal(formValues, "country").equals("")) {
        enc.setCountry(getVal(formValues, "country"));
      }


      // xxxxxxx
      //add research team for GAq
      if (!getVal(formValues, "researchTeam").equals("")) {
        enc.setDynamicProperty("Research Team", (getVal(formValues, "researchTeam")));
      }
      if (!getVal(formValues, "vessel").equals("")) {
        enc.setDynamicProperty("Vessel", (getVal(formValues, "vessel")));
      }
      if (!getVal(formValues, "conditions").equals("")) {
        enc.setDynamicProperty("Conditions", (getVal(formValues, "conditions")));
      }

      if (!getVal(formValues, "camera").equals("")) {
        enc.setDynamicProperty("Camera", (getVal(formValues, "camera")));
      }
      if (!getVal(formValues, "lens").equals("")) {
        enc.setDynamicProperty("Lens", (getVal(formValues, "lens")));
      }
      if (!getVal(formValues, "card").equals("")) {
        enc.setDynamicProperty("Card", (getVal(formValues, "card")));
      }
      if (!getVal(formValues, "folder").equals("")) {
        enc.setDynamicProperty("Folder", (getVal(formValues, "folder")));
      }

      if (!getVal(formValues, "numberOfBoats").equals("")) {
        enc.setDynamicProperty("Number of boats", (getVal(formValues, "numberOfBoats")));
      }

      if (!getVal(formValues, "startTime").equals("")) {
        enc.setDynamicProperty("Start Time", (getVal(formValues, "startTime")));
      }

      if (!getVal(formValues, "endTime").equals("")) {
        enc.setDynamicProperty("End Time", (getVal(formValues, "endTime")));
      }


      if (!getVal(formValues, "endLongitude").equals("")) {
        enc.setDynamicProperty("End Longitude", (getVal(formValues, "endLongitude")));
      }
      if (!getVal(formValues, "endLatitude").equals("")) {
        enc.setDynamicProperty("End Latitude", (getVal(formValues, "endLatitude")));
      }

      if (!getVal(formValues, "startLongitude").equals("")) {
        enc.setDynamicProperty("Start Longitude", (getVal(formValues, "startLongitude")));
      }
      if (!getVal(formValues, "startLatitude").equals("")) {
        enc.setDynamicProperty("Start Latitude", (getVal(formValues, "startLatitude")));
      }

      if (!getVal(formValues, "beginWaypoint").equals("")) {
        enc.setDynamicProperty("Begin Waypoint", (getVal(formValues, "beginWaypoint")));
      }
      if (!getVal(formValues, "endWaypoint").equals("")) {
        enc.setDynamicProperty("End Waypoint", (getVal(formValues, "endWaypoint")));
      }



      //xxxxxxxx


      String guid = CommonConfiguration.getGlobalUniqueIdentifierPrefix(context) + encID;

      //new additions for DarwinCore
      enc.setDWCGlobalUniqueIdentifier(guid);
      enc.setDWCImageURL(("//" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encID));

      //populate DarwinCore dates

      DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
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
                enc.refreshAssetFormats(myShepherd);

                //*after* persisting this madness, then lets kick MediaAssets to IA for whatever fate awaits them
                //  note: we dont send Annotations here, as they are always(forever?) trivial annotations, so pretty disposable

                // might want to set detection status here (on the main thread)

                for (MediaAsset ma: enc.getMedia()) {
                    ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
                }

                Task parentTask = null;  //this is *not* persisted, but only used so intakeMediaAssets will inherit its params
                if (locCode != null) {
                    parentTask = new Task();
                    JSONObject tp = new JSONObject();
                    JSONObject mf = new JSONObject();
                    mf.put("locationId", locCode);
                    tp.put("matchingSetFilter", mf);
                    parentTask.setParameters(tp);
                }
                Task task = org.ecocean.ia.IA.intakeMediaAssets(myShepherd, enc.getMedia(), parentTask);  //TODO are they *really* persisted for another thread (queue)
                myShepherd.storeNewTask(task);
                Logger log = LoggerFactory.getLogger(EncounterForm.class);
                log.info("New encounter submission: <a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encID+"\">"+encID+"</a>");
System.out.println("ENCOUNTER SAVED???? newnum=" + newnum + "; IA => " + task);
                org.ecocean.ShepherdPMF.getPMF(context).getDataStoreCache().evictAll();
            }

      if (newnum.equals("fail")) {
        request.setAttribute("number", "fail");
        return;
      }









      //return a forward to display.jsp
      System.out.println("Ending data submission.");
      if (!spamBot) {

        //send submitter on to confirmSubmit.jsp
        //response.sendRedirect(request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/confirmSubmit.jsp?number=" + encID);
        WebUtils.redirectToSavedRequest(request, response, ("/confirmSubmit.jsp?number=" + encID));

        //start email appropriate parties
        if(CommonConfiguration.sendEmailNotifications(context)){
          myShepherd.beginDBTransaction();
          try{
            // Retrieve background service for processing emails
            ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
            Properties submitProps= ShepherdProperties.getProperties("submit.properties", ServletUtilities.getLanguageCode(request),context);
            // Email new submission address(es) defined in commonConfiguration.properties
            Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, enc);
            List<String> mailTo = NotificationMailer.splitEmails(CommonConfiguration.getNewSubmissionEmail(context));
            String mailSubj = submitProps.getProperty("newEncounter") + enc.getCatalogNumber();
            for (String emailTo : mailTo) {
              NotificationMailer mailer = new NotificationMailer(context, ServletUtilities.getLanguageCode(request), emailTo, "newSubmission-summary", tagMap);
              mailer.setUrlScheme(request.getScheme());
              es.execute(mailer);
            }

            // Email those assigned this location code
            if(enc.getLocationID()!=null) {
              String informMe=null;
              try {
                informMe=myShepherd.getAllUserEmailAddressesForLocationID(enc.getLocationID(),context);
              }
              catch(Exception ef) {ef.printStackTrace();}
              if (informMe != null) {
                List<String> cOther = NotificationMailer.splitEmails(informMe);
                for (String emailTo : cOther) {
                  NotificationMailer mailer = new NotificationMailer(context, null, emailTo, "newSubmission-summary", tagMap);
                  mailer.setUrlScheme(request.getScheme());
                    es.execute(mailer);
                }
              }
            }

            // Add encounter dont-track tag for remaining notifications (still needs email-hash assigned).
            tagMap.put(NotificationMailer.EMAIL_NOTRACK, "number=" + enc.getCatalogNumber());


            // Email submitter and photographer
            if ((enc.getPhotographerEmails()!=null)&&(enc.getPhotographerEmails().size()>0)) {
              List<String> cOther = enc.getPhotographerEmails();
              for (String emailTo : cOther) {
                String msg = CommonConfiguration.appendEmailRemoveHashString(request, "", emailTo, context);
                tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
                NotificationMailer mailer=new NotificationMailer(context, null, emailTo, "newSubmission", tagMap);
                mailer.setUrlScheme(request.getScheme());
                es.execute(mailer);
              }
            }
            if ((enc.getSubmitterEmails()!=null)&&(enc.getSubmitterEmails().size()>0)) {
              List<String> cOther = enc.getSubmitterEmails();
              for (String emailTo : cOther) {

                String msg = CommonConfiguration.appendEmailRemoveHashString(request, "", emailTo, context);
                tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
                NotificationMailer mailer=new NotificationMailer(context, null, emailTo, "newSubmission", tagMap);
                mailer.setUrlScheme(request.getScheme());
                es.execute(mailer);
              }
            }

            // Email interested others
            if ((enc.getInformOthersEmails()!=null)&&(enc.getInformOthersEmails().size()>0)) {
              List<String> cOther = enc.getInformOthersEmails();
              for (String emailTo : cOther) {

                String msg = CommonConfiguration.appendEmailRemoveHashString(request, "", emailTo, context);
                tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
                NotificationMailer mailer=new NotificationMailer(context, null, emailTo, "newSubmission", tagMap);
                mailer.setUrlScheme(request.getScheme());
                es.execute(mailer);
              }
            }
            /*
            if ((enc.getInformOthers() != null) && (!enc.getInformOthers().trim().equals(""))) {
              List<String> cOther = NotificationMailer.splitEmails(enc.getInformOthers());
              for (String emailTo : cOther) {
                String msg = CommonConfiguration.appendEmailRemoveHashString(request, "", emailTo, context);
                tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
                NotificationMailer mailer=new NotificationMailer(context, null, emailTo, "newSubmission", tagMap);
                mailer.setUrlScheme(request.getScheme());
                es.execute(mailer);
              }
            }
            */


            es.shutdown();
          }
          catch(Exception e){
            e.printStackTrace();
          }
          finally{
            myShepherd.rollbackDBTransaction();

          }
        } //end email appropriate parties




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
        ma.validateSourceImage();
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

  private void createOccurrenceIfMissingAndAddEncounter(String occID, Encounter enc, Shepherd myShepherd){
    Occurrence occ = myShepherd.getOccurrence(occID);
    if (occ==null) {
        occ = new Occurrence(occID, enc);
        myShepherd.storeNewOccurrence(occ);
        System.out.println("        ENCOUNTERFORM: created new Occurrence "+occID);
    } else {
        occ.addEncounter(enc);
        System.out.println("        ENCOUNTERFORM: added enc to Occurrence "+occID);
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
        ma.validateSourceImage();
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
