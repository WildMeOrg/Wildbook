package org.ecocean.servlet;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.NullPointerException;
//import java.io.FileInputStream;
import java.util.Properties;
import java.io.File;
import java.lang.Exception;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;
import java.util.Calendar;
import java.util.Vector;
//import java.text.CharacterIterator;
//import java.text.StringCharacterIterator;
import java.util.Random;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import org.ecocean.*;

import java.lang.SecurityException;




/**
 * This class takes the SubmitForm and retrieves the text value
 * and file attributes and puts them in the request for the display.jsp
 * page to display them
 * Test
 */


public class SubmitAction extends Action{
	
	String mailList="no";
	Calendar date=Calendar.getInstance();
	Random ran=new Random();
	String uniqueID=(new Integer(date.get(Calendar.DAY_OF_MONTH))).toString()+(new Integer(date.get(Calendar.MONTH)+1)).toString()+(new Integer(date.get(Calendar.YEAR))).toString()+(new Integer(date.get(Calendar.HOUR_OF_DAY))).toString()+(new Integer(date.get(Calendar.MINUTE))).toString()+(new Integer(date.get(Calendar.SECOND))).toString()+(new Integer(ran.nextInt(99))).toString();
	double size=0, depth=-1000;
	String measureUnits="", location="", sex="unknown", comments="", primaryImageName="", guess="no estimate provided";
	String submitterName="", submitterEmail="", submitterPhone="", submitterAddress="";
	String photographerName="", photographerEmail="", photographerPhone="", photographerAddress="";
	Vector additionalImageNames=new Vector();
	int encounterNumber=0;
	int day=1, month=1, year=2003, hour=12;
	String lat="", longitude="", latDirection="", longDirection="", scars="None";
	String minutes="00", gpsLongitudeMinutes="", gpsLongitudeSeconds="", gpsLatitudeMinutes="", gpsLatitudeSeconds="", submitterID="N/A";
	String locCode="", informothers="";
	String livingStatus="";
	Shepherd myShepherd;
	
    public ActionForward execute(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
        throws Exception {
        myShepherd=new Shepherd();

        if (form instanceof SubmitForm) {
			System.out.println("Starting data submission...");
            //this line is here for when the input page is upload-utf8.jsp,
            //it sets the correct character encoding for the response
            String encoding = request.getCharacterEncoding();
            if ((encoding != null) && (encoding.equalsIgnoreCase("utf-8")))
            {
                response.setContentType("text/html; charset=utf-8");
            }
			
			//get the form to read data from
            SubmitForm theForm = (SubmitForm) form;

			mailList=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getMailList());
			date=theForm.getDate();
			uniqueID=theForm.getUniqueID();
			size=theForm.getSize(); 
			depth=theForm.getDepth();
			measureUnits=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getMeasureUnits()); 
			location=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLocation()); 
			System.out.println("SubmitAction location: "+location);
			sex=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSex());
			comments=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getComments());
			primaryImageName=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getPrimaryImageName());
			guess=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getGuess());
			submitterName=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSubmitterName());
			submitterEmail=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSubmitterEmail().replaceAll(";", ",").replaceAll(" ",""));
			submitterPhone=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSubmitterPhone()); 
			submitterAddress=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getSubmitterAddress());
			photographerName=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getPhotographerName()); 
			photographerEmail=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getPhotographerEmail().replaceAll(";", ",").replaceAll(" ",""));
			photographerPhone=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getPhotographerPhone());
			photographerAddress=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getPhotographerAddress());
			additionalImageNames=theForm.getAdditionalImageNames();
			encounterNumber=theForm.getEncounterNumber();
			livingStatus=theForm.getLivingStatus();
			informothers = ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getInformothers().replaceAll(";", ",").replaceAll(" ",""));
			//check for spamBots
			boolean spamBot=false;
			StringBuffer spamFields=new StringBuffer();
			spamFields.append(theForm.getSubmitterPhone());
			spamFields.append(theForm.getSubmitterName());
			spamFields.append(theForm.getPhotographerPhone());
			spamFields.append(theForm.getPhotographerName());
			spamFields.append(theForm.getLocation());
			//if(spamFields.toString().toLowerCase().indexOf("buy")!=-1){spamBot=true;}
			if(spamFields.toString().toLowerCase().indexOf("porn")!=-1){spamBot=true;}
			spamFields.append(theForm.getComments());
			if(spamFields.toString().toLowerCase().indexOf("href")!=-1){spamBot=true;}
			//else if(spamFields.toString().toLowerCase().indexOf("[url]")!=-1){spamBot=true;}
			//else if(spamFields.toString().toLowerCase().indexOf("url=")!=-1){spamBot=true;}
			//else if(spamFields.toString().toLowerCase().trim().equals("")){spamBot=true;}
			//else if((theForm.getSubmitterID()!=null)&&(theForm.getSubmitterID().equals("N%2FA"))) {spamBot=true;}
			
			
			//see if the location code can be determined and set based on the location String reported
			locCode="";
			String locTemp=location.toLowerCase().trim();
			Properties props=new Properties();
			try{
				//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/en/submitActionClass.properties"));
				
				
				//props.load(propsInputStream);
				props.load(getClass().getResourceAsStream("/bundles/en/submitActionClass.properties"));
				
				Enumeration m_enum=props.propertyNames();
				while(m_enum.hasMoreElements()) {
					String aLocationSnippet=((String)m_enum.nextElement()).trim();
					if(locTemp.indexOf(aLocationSnippet)!=-1) {locCode=props.getProperty(aLocationSnippet);}
				}
			}
			catch(Exception props_e) {props_e.printStackTrace();}
			//end location code setter
			
			
			day=theForm.getDay(); 
			month=theForm.getMonth(); 
			year=theForm.getYear();
			hour=theForm.getHour();
			submitterID=theForm.getSubmitterID();
			lat=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLat());
			longitude=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLongitude()); 
			latDirection=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLatDirection()); 
			longDirection=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getLongDirection());
			scars=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getScars());
			if(scars.equals("1")) {scars="Tail (caudal) fin";}
			else if(scars.equals("0")){scars="None";}
          		else if(scars.equals("2")){scars="1st dorsal fin";}
          		else if(scars.equals("3")){scars="2nd dorsal fin";}
          		else if(scars.equals("4")){scars="Left pectoral fin";}
          		else if(scars.equals("5")){scars="Right pectoral fin";}
          		else if(scars.equals("6")){scars="Head";}
          		else if(scars.equals("7")){scars="Body";}
			
			
			minutes=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getMinutes());
			//hour=theForm.getHour();
			gpsLongitudeMinutes=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getGpsLongitudeMinutes()); 
			gpsLongitudeSeconds=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getGpsLongitudeSeconds());
			gpsLatitudeMinutes=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getGpsLatitudeMinutes()); 
			gpsLatitudeSeconds=ServletUtilities.preventCrossSiteScriptingAttacks(theForm.getGpsLatitudeSeconds());
            //retrieve the text data
            String text = theForm.getTheText();
            //retrieve the query string value
            String queryValue = theForm.getQueryParam();
            //retrieve the file representation
            FormFile[] file=new FormFile[4];
            file[0] = theForm.getTheFile1();
            file[1] = theForm.getTheFile2();
            file[2] = theForm.getTheFile3();
            file[3] = theForm.getTheFile4();
            //retrieve the file name
            String[] fileName= new String[4];
            try{
            	fileName[0]= ServletUtilities.preventCrossSiteScriptingAttacks(file[0].getFileName());
            }catch(NullPointerException npe){fileName[0]=null;}
            try{
            	fileName[1]= ServletUtilities.preventCrossSiteScriptingAttacks(file[1].getFileName());
            }catch(NullPointerException npe){fileName[1]=null;}
            try{
            	fileName[2]= ServletUtilities.preventCrossSiteScriptingAttacks(file[2].getFileName());
        	}catch(NullPointerException npe){fileName[2]=null;}
        	try{
        		fileName[3]= ServletUtilities.preventCrossSiteScriptingAttacks(file[3].getFileName());
    		}catch(NullPointerException npe){fileName[3]=null;}
            //retrieve the content type
            String[] contentType = new String[4];
            try{
            	contentType[0] = file[0].getContentType();
            }catch(NullPointerException npe){contentType[0]=null;}
            try{
            	contentType[1] = file[1].getContentType();
            }catch(NullPointerException npe){contentType[1]=null;}
            try{
            	contentType[2] = file[2].getContentType();
            }catch(NullPointerException npe){contentType[2]=null;}
            try{
            	contentType[3] = file[3].getContentType();
            }catch(NullPointerException npe){contentType[3]=null;}
            boolean writeFile = theForm.getWriteFile();
            //retrieve the file size
            String[] fileSize=new String[4];
            try{
            	fileSize[0] = (file[0].getFileSize() + " bytes");
            }catch(NullPointerException npe){fileSize[0]=null;}
            try{
            	fileSize[1] = (file[1].getFileSize() + " bytes");
            }catch(NullPointerException npe){fileSize[1]=null;}
            try{
            	fileSize[2] = (file[2].getFileSize() + " bytes");
            }catch(NullPointerException npe){fileSize[2]=null;}
            try{
            	fileSize[3] = (file[3].getFileSize() + " bytes");
            }catch(NullPointerException npe){fileSize[3]=null;}
            String data = null;

			File encountersDir=new File(getServlet().getServletContext().getRealPath("/encounters"));
            File thisEncounterDir=new File(encountersDir, uniqueID);
			
			boolean created=false;
            try{
            	if ((!thisEncounterDir.exists())&&(!spamBot)) {created=thisEncounterDir.mkdir();};
			} 
			catch(SecurityException sec) {System.out.println("Security exception thrown while trying to created the directory for a new encounter!");}
			//System.out.println("Created?: "+created);
			for(int iter=0;iter<4;iter++) {
			if((!spamBot)&&(fileName[iter]!=null)) {
            try {

                //retrieve the file data
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream stream = file[iter].getInputStream();
                //System.out.println(writeFile);
                if (!writeFile) {
                    //only write files out that are less than 9MB
                    if ((file[iter].getFileSize() < (4*9216000))&&(file[iter].getFileSize()>0)) {

                        byte[] buffer = new byte[8192];
                        int bytesRead = 0;
                        while ((bytesRead = stream.read(buffer, 0, 8192)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }
                        data = new String(baos.toByteArray());
                    }
                    else {
                        data = new String("The file is greater than 4MB or less than 1 byte, " +
                                " and has not been written to stream." +
                                " File Size: " + file[iter].getFileSize() + " bytes. This is a" +
                                " limitation of this particular web application, hard-coded" +
                                " in org.apache.struts.webapp.upload.UploadAction");
                    }
                }
                else if((!(file[iter].getFileName().equals("")))&&(file[iter].getFileSize()>0)){
                    //write the file to the file specified
                    //String writeName=file[iter].getFileName().replace('#', '_').replace('-', '_').replace('+', '_').replaceAll(" ", "_");
                    String writeName=ServletUtilities.cleanFileName(file[iter].getFileName());
       
                    //String writeName=URLEncoder.encode(file[iter].getFileName(), "UTF-8");
                    while(writeName.indexOf(".")!=writeName.lastIndexOf(".")) {
                    	writeName=writeName.replaceFirst("\\.","_");
                    }
                    //System.out.println(writeName);
                    additionalImageNames.add(writeName);
                    OutputStream bos = new FileOutputStream(new File(thisEncounterDir,writeName));
                    int bytesRead = 0;
                    byte[] buffer = new byte[8192];
                    while ((bytesRead = stream.read(buffer, 0, 8192)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                    }
                    bos.close();
                    data = "The file has been written to \"" + encounterNumber+"\\"+writeName + "\"";
                }
                //close the stream
                stream.close();
                baos.close();
            }
            catch (FileNotFoundException fnfe) {
            	System.out.println("File not found exception.\n");
            	fnfe.printStackTrace();
                return null;
            }
            catch (IOException ioe) {
            	System.out.println("IO Exception.\n");
            	ioe.printStackTrace();
                return null;
            }
		} //end if fileName[iter]!=null
            
        } //end for iter
		

	   //now let's add our encounter to the database
		Encounter enc=new Encounter(day, month, year, hour, minutes, guess, location, submitterName, submitterEmail, additionalImageNames);
		enc.setComments(comments.replaceAll("\n", "<br>"));
		enc.setSex(sex);
		enc.setLivingStatus(livingStatus);
		enc.setDistinguishingScar(scars);
		if (measureUnits.equals("Feet")) {
			String truncSize=(new Double(size/3.3)).toString();
			int sizePeriod=truncSize.indexOf(".");
			truncSize=truncSize.substring(0, sizePeriod+2);
			size=(new Double(truncSize)).doubleValue();
			String truncDepth=(new Double(depth/3.3)).toString();
			sizePeriod=truncDepth.indexOf(".");
			truncDepth=truncDepth.substring(0, sizePeriod+2);
			depth=(new Double(truncDepth)).doubleValue();
			}
		if (size!=0) {enc.setSize(size);};
		if (depth!=-1000) {enc.setDepth(depth);};

		//let's handle the GPS
			if (!(lat.equals(""))) {
				enc.setGPSLatitude(lat+"&deg; "+gpsLatitudeMinutes+"\' "+gpsLatitudeSeconds+"\" "+latDirection);
			
				
					try {
						double degrees=(new Double(lat)).doubleValue();
						double position=degrees;
							if(!gpsLatitudeMinutes.equals("")){
								double minutes=((new Double(gpsLatitudeMinutes)).doubleValue())/60;
								position+=minutes;
							}
							if(!gpsLatitudeSeconds.equals("")){
								double seconds = ((new Double(gpsLatitudeSeconds)).doubleValue())/3600;
								position+=seconds;
							}
							if(latDirection.toLowerCase().equals("south")) {
								position=position*-1;
							}
							enc.setDWCDecimalLatitude(position);

						
					}
					catch(Exception e) {
						System.out.println("EncounterSetGPS: problem setting decimal latitude!");
						e.printStackTrace();
					}
				
				
			}
			if (!(longitude.equals(""))) {
				enc.setGPSLongitude(longitude+"&deg; "+gpsLongitudeMinutes+"\' "+gpsLongitudeSeconds+"\" "+longDirection);
			
				try {
					double degrees=(new Double(longitude)).doubleValue();
					double position=degrees;
					if(!gpsLongitudeMinutes.equals("")){
						double minutes=((new Double(gpsLongitudeMinutes)).doubleValue())/60;
						position+=minutes;
					}
					if(!gpsLongitudeSeconds.equals("")){
						double seconds = ((new Double(gpsLongitudeSeconds)).doubleValue())/3600;
						position+=seconds;
					}
						if(longDirection.toLowerCase().equals("west")) {
							position=position*-1;
						}
						enc.setDWCDecimalLongitude(position);

					
				}
				catch(Exception e) {
					System.out.println("EncounterSetGPS: problem setting decimal longitude!");
					e.printStackTrace();
				}
			}
			
			//if one is not set, set all to null
			if((longitude.equals(""))||(lat.equals(""))){
				enc.setGPSLongitude("");
				enc.setGPSLongitude("");
				enc.setDecimalLatitude("");
				enc.setDecimalLongitude("");
				enc.setDWCDecimalLatitude(-9999.0);
				enc.setDWCDecimalLongitude(-9999.0);
			}
		//finish the GPS
			
			
			
		enc.setMeasureUnits("Meters");
		enc.setSubmitterPhone(submitterPhone);
		enc.setSubmitterAddress(submitterAddress);
		enc.setPhotographerPhone(photographerPhone);
		enc.setPhotographerAddress(photographerAddress);
		enc.setPhotographerName(photographerName);
		enc.setPhotographerEmail(photographerEmail);
		enc.addComments("<p>Submitted on "+(new java.util.Date()).toString()+" from address: "+request.getRemoteHost()+"</p>");
		enc.approved=false;
		if(request.getRemoteUser()!=null){
			enc.setSubmitterID(request.getRemoteUser());
		}
		else if(submitterID!=null) {
			enc.setSubmitterID(submitterID);
		}
		else{
			enc.setSubmitterID("N/A");
		}
		if(!locCode.equals("")) {enc.setLocationCode(locCode);}
		if(!informothers.equals("")) {enc.setInformOthers(informothers);}
		String guid=CommonConfiguration.getGlobalUniqueIdentifierPrefix()+uniqueID;
		
		//new additions for DarwinCore
		enc.setDWCGlobalUniqueIdentifier(guid);
		enc.setDWCImageURL(("http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+uniqueID));
		
		//populate DarwinCore dates
		DateTime dt=new DateTime();
		DateTimeFormatter fmt = ISODateTimeFormat.date();
		String strOutputDateTime = fmt.print(dt);
		enc.setDWCDateAdded(strOutputDateTime);
		enc.setDWCDateLastModified(strOutputDateTime);	
		
		String newnum="";
		if(!spamBot) {
			newnum=myShepherd.storeNewEncounter(enc, uniqueID);
		}
		
		if(newnum.equals("fail")) {
			request.setAttribute("number", "fail");
			return null;
		}
		
        //place the data into the request for retrieval from display.jsp
        request.setAttribute("number", Integer.toString(encounterNumber));
		

            //destroy the temporary file created
            if(fileName[0]!=null) {
            	file[0].destroy();
            }
            if(fileName[1]!=null) {
            	file[1].destroy();
            }
            if(fileName[2]!=null) {
            	file[2].destroy();
            }
            if(fileName[3]!=null) {
            	file[3].destroy();
            }
            file=null;


            //return a forward to display.jsp
            System.out.println("Ending data submission.");
            if((!spamBot)&&(submitterID!=null)&&(submitterID.equals("deepblue"))) {
            	response.sendRedirect("http://"+CommonConfiguration.getURLLocation()+"/participate/deepblue/confirmSubmit.jsp?number="+uniqueID);
            }
            else if(!spamBot){
            	response.sendRedirect("http://"+CommonConfiguration.getURLLocation()+"/confirmSubmit.jsp?number="+uniqueID);
        	}
            else {
            	response.sendRedirect("http://"+CommonConfiguration.getURLLocation()+"/spambot.jsp");
            }
        }

        myShepherd.closeDBTransaction();
        return null;
    }


  
}