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

import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.Keyword;
import org.ecocean.Annotation;
import org.ecocean.ActionResult_Encounter;
import org.ecocean.ActionResult;
import org.ecocean.CommonConfiguration;
import org.ecocean.media.*;
import org.ecocean.mmutil.ListHelper;
import org.ecocean.mmutil.MantaMatcherScan;
import org.ecocean.mmutil.MantaMatcherUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

import org.json.JSONObject;

//import javax.imageio.spi.ImageReaderWriterSpi;


/**
 *
 * This servlet allows the user to upload a processed patterning file for use with the MantaMatcher algorithm.
 * @author jholmber
 * @author Giles Winstanley
 *
 */
public class EncounterAddMantaPattern extends HttpServlet {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(EncounterAddMantaPattern.class);

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {


    String context="context0";
    context=ServletUtilities.getContext(request);

    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);

    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterAddMantaPattern.class");
    myShepherd.beginDBTransaction();

    //setup data dir
    File shepherdDataDir = CommonConfiguration.getDataDirectory(getServletContext(), context);
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
    //File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    //if(!encountersDir.exists()){encountersDir.mkdir();}

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    String encounterNumber = "";
    //SinglePhotoVideo spv = null;
    MediaAsset ma = null;
    Map<String, File> mmFiles = null;
    MantaMatcherScan scan = null;
    String action = "imageadd";

    StringBuilder resultComment = new StringBuilder();

    // we'll write any new image file here
    File write2me = null;
    String newFilePath = null;

    Encounter enc = null;

    if (request.getParameter("action") != null && request.getParameter("action").equals("imageremove")) {
      action = "imageremove";
    }
    else if (request.getParameter("action") != null && request.getParameter("action").equals("rescan")) {
      action = "rescan";
    }
    else if (request.getParameter("action") != null && request.getParameter("action").equals("removeScan")) {
      action = "removeScan";
    }
    //imageadd2 is added for the new candidate region selection tool and should eventually replace the original imageadd action
    else if (request.getParameter("action") != null && request.getParameter("action").equals("imageadd2")) {
      action = "imageadd2";
    }

    try {
      // ====================================================================
      if (action.equals("imageremove")){

        encounterNumber = request.getParameter("number");
        try {
          enc = myShepherd.getEncounter(encounterNumber);
          ma = myShepherd.getMediaAsset(request.getParameter("dataCollectionEventID"));
          MantaMatcherUtilities.removeMatcherFiles(context,ma,enc );

          // Clear MMA-compatible flag if appropriate for encounter.
          boolean hasCR = MantaMatcherUtilities.checkEncounterHasMatcherFiles(enc, shepherdDataDir);
          if (!hasCR) {
            enc.setMmaCompatible(false);
            myShepherd.commitDBTransaction();
          }
        }
        catch (SecurityException thisE) {
          thisE.printStackTrace();
          System.out.println("Error attempting to delete the old version of a submitted manta data image!!!!");
          resultComment.append("I hit a security error trying to delete the old feature image. Please check your file system permissions.");
        }
      }
      // ====================================================================
      else if (action.equals("rescan")){

        encounterNumber = request.getParameter("number");
        try {
          enc = myShepherd.getEncounter(encounterNumber);
          //File encDir = new File(encountersDir, enc.getEncounterNumber());
          //File encDir = new File(Encounter.dir(shepherdDataDir, encounterNumber));

          ma = myShepherd.getMediaAsset(request.getParameter("dataCollectionEventID"));
          //File file=new File(enc.subdir()+File.separator+ma.getFilename());
          File spvFile = ma.localPath().toFile();
          mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spvFile);

          String[] locIDs = request.getParameterValues("locationID");
          List<String> locationIDs = (locIDs == null) ? null : Arrays.asList(locIDs);

          String scanId = request.getParameter("scanId");
          // If refreshing a scan, delete previous results and update time.
          if (scanId != null) {
            scan = MantaMatcherUtilities.findMantaMatcherScan(context, ma, scanId);
            // Remove this old scan from those already saved.
            Set<MantaMatcherScan> mmaScans = MantaMatcherUtilities.loadMantaMatcherScans(context, ma);
            mmaScans.remove(scan);
            MantaMatcherUtilities.saveMantaMatcherScans(context, ma,enc, mmaScans);
            // Now remove old data and update time for resuse.
            scan.deleteScanFiles();
            scan.setDateTime(new Date());
          } else {
            scan = new MantaMatcherScan(ma,enc, locationIDs, new Date());
          }

          // Prepare temporary algorithm input file.
          String rootDir = getServletContext().getRealPath("/");
          String dataDir=ServletUtilities.dataDir(context, rootDir);
          String inputText = MantaMatcherUtilities.collateAlgorithmInput(myShepherd, enc, ma, locationIDs, dataDir);
          File scanInput=scan.getScanInput();
          System.out.println("EncounterAddMantaPattern.scanInput file is: "+scanInput.getAbsolutePath());
          System.out.println("...and I am trying to write the following:\n: "+inputText);
          FileWriter pw = new FileWriter(scanInput);
          BufferedWriter bw=new BufferedWriter(pw);
          try {

            bw.write(inputText);
            bw.flush();


          }
          catch(Exception e){
            e.printStackTrace();
          }
          finally{
            bw.close();
          }

          if(scanInput.exists()){System.out.println("...Hooray! The file was written! ");}
          else{System.out.println("...Uh oh! The file was NOT written! ");}



          // Run algorithm.
          List<String> procArg = ListHelper.create("/usr/bin/mmatch")
                  .add(scan.getScanInput().getAbsolutePath())
                  .add("0").add("0").add("2").add("1")
                  .add("-o").add(scan.getScanOutputTXT().getName())
                  .add("-c").add(scan.getScanOutputCSV().getName())
                  .asList();
          System.out.println("...rescan1");
          ProcessBuilder pb2 = new ProcessBuilder(procArg);
          System.out.println("...rescan2");
          pb2.directory(ma.localPath().toFile().getParentFile());
          System.out.println("...rescan3");
          pb2.redirectErrorStream();
          System.out.println("...rescan4");

          String procArgStr = ListHelper.toDelimitedStringQuoted(procArg, " ");
          System.out.println("...rescan5");
          resultComment.append("<br />").append(procArgStr).append("<br /><br />");
          System.out.println(procArgStr);
          System.out.println("...rescan6");
          Process proc = pb2.start();
          System.out.println("...rescan7");
          // Read output from process.
          resultComment.append("mmatch reported the following when trying to match image files:<br />");
          System.out.println("...rescan8");
          BufferedReader brProc = new BufferedReader(new InputStreamReader(proc.getInputStream()));
          try {
            String temp = null;
            while ((temp = brProc.readLine()) != null) {
              resultComment.append(temp).append("<br />");
              //System.out.println("...rescan9");
            }
          }
          catch (IOException iox) {
            iox.printStackTrace();
            locked = true;
            resultComment.append("I hit an IOException while trying to execute mmatch from the command line.");
            resultComment.append(iox.getStackTrace().toString());
          }
          proc.waitFor();
          System.out.println("...rescan10");
          // Delete temporary algorithm input file.
          //scan.getScanInput().delete();

          // Resave MMA scans after including new one.
          Set<MantaMatcherScan> mmaScans = MantaMatcherUtilities.loadMantaMatcherScans(context, ma);
          System.out.println("...rescan11");
          mmaScans.add(scan);
          System.out.println("...rescan12");
          MantaMatcherUtilities.saveMantaMatcherScans(context, ma,enc, mmaScans);
          System.out.println("...rescan13");
        }
        catch (SecurityException sx) {
          log.error(sx.getMessage(), sx);
          System.out.println("Error attempting to rescan via MantaMatcher algorithm");
          resultComment.append("I hit a security error trying to rescan via MantaMatcher algorithm. Please check file system permissions.");
        }
      }
      // ====================================================================
      else if (action.equals("removeScan")){

        encounterNumber = request.getParameter("number");
        try {
          enc = myShepherd.getEncounter(encounterNumber);
          File encDir = new File(Encounter.dir(shepherdDataDir, encounterNumber));

          ma = myShepherd.getMediaAsset(request.getParameter("dataCollectionEventID"));
          File spvFile = new File(encDir, ma.getFilename());
          mmFiles = MantaMatcherUtilities.getMatcherFilesMap(ma);

          String[] locIDs = request.getParameterValues("locationID");
          List<String> locationIDs = (locIDs == null) ? null : Arrays.asList(locIDs);

          String scanId = request.getParameter("scanId");

          // Resave MMA scans after including new one.
          Set<MantaMatcherScan> mmaScans = MantaMatcherUtilities.loadMantaMatcherScans(context, ma);
          scan = MantaMatcherUtilities.findMantaMatcherScan(context, ma, scanId);
          if (scan != null) {
            mmaScans.remove(scan);
            scan.deleteScanFiles();
            MantaMatcherUtilities.saveMantaMatcherScans(context, ma,enc, mmaScans);
          }
        }
        catch (SecurityException sx) {
          log.error(sx.getMessage(), sx);
          System.out.println("Error attempting to remove MantaMatcher algorithm scan data");
          resultComment.append("I hit a security error trying to remove MantaMatcher algorithm scan data. Please check file system permissions.");
        }
      }
      // ====================================================================
      else if (action.equals("imageadd")) {

        MultipartParser mp = new MultipartParser(request, CommonConfiguration.getMaxMediaSizeInMegabytes(context) * 1048576);
        Part part = null;
        while ((part = mp.readNextPart()) != null) {
          String name = part.getName();
          if (part.isParam()) {
            ParamPart paramPart = (ParamPart)part;
            String value = paramPart.getStringValue();

            // Determine encounter to which to assign new CR image.
            if (name.equals("number")) {
              encounterNumber = value;
            }
            // Determine existing image to which to assign new CR image.
            if (name.equals("photoNumber")){
              ma = myShepherd.getMediaAsset(value);
              mmFiles = MantaMatcherUtilities.getMatcherFilesMap(ma);
            }
          }

          // Check for FilePart is done after other Part types.
          // NOTE: "number" and "photoNumber" must come first in JSP form
          // to ensure correct association with encounter/photo.
          if (part.isFile()) {
            FilePart filePart = (FilePart)part;
            assert mmFiles != null;
            try {
              // Attempt to delete existing MM algorithm files.
              // (Shouldn't exist, but just a precaution.)
              MantaMatcherUtilities.removeMatcherFiles(context, ma,myShepherd.getEncounter(encounterNumber));
            }
            catch (SecurityException sx) {
              sx.printStackTrace();
              System.out.println("Error attempting to delete the old version of a submitted manta data image!!!!");
              resultComment.append("I hit a security error trying to delete the old feature image. Please check your file system permissions.");
              locked=true;
            }

            // Save new image to file ready for processing.
            write2me = mmFiles.get("CR");
            filePart.writeTo(write2me);
            resultComment.append("Successfully saved the new feature image.<br />");

            // Run 'mmprocess' for image enhancement & to create feature files.
            List<String> procArg = ListHelper.create("/usr/bin/mmprocess")
                    .add(mmFiles.get("O").getAbsolutePath())
                    .add("4").add("1").add("2").asList();
            ProcessBuilder pb = new ProcessBuilder(procArg);
            pb.redirectErrorStream();

            String procArgStr = ListHelper.toDelimitedStringQuoted(procArg, " ");
            System.out.println("I am trying to execute the command: " + procArgStr);
            resultComment.append("I am trying to execute the command:<br/>").append(procArgStr).append("<br />");

            Process process = pb.start();
            // Read ouput from process.
            resultComment.append("mmprocess reported the following when trying to create the enhanced image file:<br />");
            BufferedReader brProc = new BufferedReader(new InputStreamReader(process.getInputStream()));
            try {
              String temp = null;
              while ((temp = brProc.readLine()) != null) {
                resultComment.append(temp).append("<br />");
              }
            }
            catch (IOException iox) {
              iox.printStackTrace();
              locked = true;
              resultComment.append("I hit an IOException while trying to execute mmprocess from the command line.");
              resultComment.append(iox.getStackTrace().toString());
            }
            process.waitFor();


            // Set MMA-compatible flag if appropriate.
            String encID = request.getParameter("encounterID");
            enc = null;
            if (encID != null)
              enc = myShepherd.getEncounter(encID);
            if (enc != null && MantaMatcherUtilities.checkEncounterHasMatcherFiles(enc, shepherdDataDir)) {
              enc.setMmaCompatible(true);
              myShepherd.commitDBTransaction();
            }
          }
        }
      }
      // ====================================================================

      // ====================================================================
    //imageadd2 is added for the new candidate region selection tool and should eventually replace the original imageadd action

      else if (action.equals("imageadd2")) {

        System.out.println("STarting EncounterAddMantaPattern.imageadd2");

				encounterNumber = request.getParameter("encounterID");
				System.out.println("...encounter number: "+encounterNumber);
				ma = myShepherd.getMediaAsset(request.getParameter("photoNumber"));
				System.out.println("...mediassetID: "+request.getParameter("photoNumber"));
				if(myShepherd.getMediaAsset(request.getParameter("photoNumber"))!=null)System.out.println("......it's a real media asset!");
				//mmFiles = MantaMatcherUtilities.getMatcherFilesMap(ma,myShepherd.getEncounter(encounterNumber));
				mmFiles = MantaMatcherUtilities.getMatcherFilesMap(ma.localPath().toFile());
				String matchFilename = request.getParameter("matchFilename");
				System.out.println("...matchFilename is: "+matchFilename);
				String errorMessage = null;

        assert mmFiles != null;
        try {
          // Attempt to delete existing MM algorithm files.
          // (Shouldn't exist, but just a precaution.)
          MantaMatcherUtilities.removeMatcherFiles(context, ma,myShepherd.getEncounter(encounterNumber));
        }
        catch (SecurityException sx) {
          sx.printStackTrace();
          System.out.println("Error attempting to delete the old version of a submitted manta data image!!!!");
          resultComment.append("I hit a security error trying to delete the old feature image. Please check your file system permissions.");
          locked=true;
        }

				String pngData = request.getParameter("pngData");

				byte[] rawPng = null;
				try {
					rawPng = DatatypeConverter.parseBase64Binary(pngData);
				} catch (IllegalArgumentException ex) {
					errorMessage = "could not parse image data";
				}

					enc = null;
					write2me = null;

					if (rawPng != null) {
						// rawPng may be jpeg or png; so lets find out
						String crImageFormat = null;
						ByteArrayInputStream bis = new ByteArrayInputStream(rawPng);
						Object source = bis;
						ImageInputStream iis = ImageIO.createImageInputStream(source);
						Iterator<?> readers = ImageIO.getImageReaders(iis);
						ImageReader useReader = null;
						while (readers.hasNext()) {
							useReader = (ImageReader) readers.next();
    					crImageFormat = useReader.getFormatName();  // JPEG, png
						}

						String encID = request.getParameter("encounterID");
						myShepherd.beginDBTransaction();

						if (encID != null) enc = myShepherd.getEncounter(encID);
						if (enc == null) {
							errorMessage = "invalid encounter " + encID;
						} else {
							//note: matchFilename (and extension) should be the same as mmFiles, i think -jon
							int dot = matchFilename.lastIndexOf('.');
							String extension = matchFilename.substring(dot+1, matchFilename.length());  //TODO get actual format from file magic instead?
							String targetFormat = null;
							if (extension.equalsIgnoreCase("jpg") || extension.equalsIgnoreCase("jpeg")) {
								targetFormat = "JPEG";
							} else if (extension.equalsIgnoreCase("png")) {
								targetFormat = "PNG";
							} else {
								targetFormat = extension.toUpperCase();  //hope for the best?
							}
							write2me = mmFiles.get("CR");
System.out.println("write2me -> " + write2me.toString());

							if (!crImageFormat.equalsIgnoreCase(targetFormat)) {
System.out.println("cr format (" + crImageFormat + ") differs from target format (" + targetFormat + "), attempting conversion");
								if ((useReader != null) && (iis != null)) {
									try {
										useReader.setInput(iis, true);
										ImageReadParam param = useReader.getDefaultReadParam();
										Image image = useReader.read(0, param);
										BufferedImage bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
										Graphics2D g2 = bufferedImage.createGraphics();
										g2.drawImage(image, null, null);  //might need bgcolor if ever transparency a problem?   http://stackoverflow.com/a/1545417
										ImageIO.write(bufferedImage, targetFormat, write2me);
									} catch (Exception e) {
										errorMessage = "problem converting/saving image: " + e.toString();
									}
								} else {
									errorMessage = "did not have a valid image reader or image stream";
								}

							} else {  //formats the same, easy
System.out.println("looks like cr format and target format are the same! -> " + targetFormat);
                Files.write(write2me.toPath(), rawPng);
							}

						}

					}
System.out.println("A: write2me -> " + write2me.toString());

						if (errorMessage != null) {  //had a problem
							resultComment.append("error: " + errorMessage);

						} else {
                //myShepherd.commitDBTransaction();

                resultComment.append("Successfully saved the new feature image: "+write2me.getAbsolutePath()+"<br />");

                // Run 'mmprocess' for image enhancement & to create feature files.
                List<String> procArg = ListHelper.create("/usr/bin/mmprocess")
                    .add(mmFiles.get("O").getAbsolutePath())
                    .add("4").add("1").add("2").asList();
                ProcessBuilder pb = new ProcessBuilder(procArg);
                pb.redirectErrorStream();

                String procArgStr = ListHelper.toDelimitedStringQuoted(procArg, " ");
                System.out.println("I am trying to execute the command: " + procArgStr);
                resultComment.append("I am trying to execute the command:<br/>").append(procArgStr).append("<br />");

                Process process = pb.start();
                // Read ouput from process.
                resultComment.append("mmprocess reported the following when trying to create the enhanced image file:<br />");
                BufferedReader brProc = new BufferedReader(new InputStreamReader(process.getInputStream()));
                try {
                  String temp = null;
                  while ((temp = brProc.readLine()) != null) {
                    resultComment.append(temp).append("<br />");
                  }
                }
                catch (IOException iox) {
                    iox.printStackTrace();
                    locked = true;
                    resultComment.append("I hit an IOException while trying to execute mmprocess from the command line.");
                    resultComment.append(iox.getStackTrace().toString());
                }
                process.waitFor();
System.out.println("B: write2me -> " + write2me.toString());

                // Set MMA-compatible flag if appropriate.
                if (enc != null && MantaMatcherUtilities.checkEncounterHasMatcherFiles(enc, shepherdDataDir)) {
                  enc.setMmaCompatible(true);
/*

[WB-285]

we need to:

* use write2me (a File) to create a proper MediaAsset
    see for example: servlet/SubmitSpotsAndImages.java; goes something like:
      JSONObject params = store.createParameters(FILE);
      MediaAsset ma = store.create(params);
* i have been adding the keyword "CR Image" and label "CR" to the assets i make, so lets continue that
      Keyword crKeyword = myShepherd.getOrCreateKeyword("CR Image");  ma.addKeyword(crKeyword);
      ma.addLabel("CR")
* create trival annot, set matchAgainst=T, and attach under encounter

* test.   :)

*/

                  // we should only make a new MA if we've made the write2me file above
                  if (write2me!=null && enc!=null) {
System.out.println("C: write2me -> " + write2me.toString());
                    System.out.println("EncounterAddMantaPattern: making CR media asset and trivial annot");
                    AssetStore astore = AssetStore.getDefault(myShepherd);

                    // the problem here is that write2me is the original file, not the new one that was written

System.out.println("D: write2me -> " + write2me.toString());

                    JSONObject params = astore.createParameters(write2me);
System.out.println("params.get(\"path\") -> " + params.get("path"));

                    boolean alreadyExists = (astore.find(params, myShepherd) != null);
                    if (!alreadyExists) {

                      MediaAsset crMa = new MediaAsset(astore, params);
                      System.out.println("    + just made media asset w fpath "+crMa.getFilename());
                      Keyword crKeyword = myShepherd.getOrCreateKeyword("CR Image");
                      String crParentId = request.getParameter("dataCollectionEventID");
                      crMa.addDerivationMethod("crParentId", crParentId);
                      crMa.addLabel("CR");
                      crMa.addKeyword(crKeyword);
                      crMa.updateMinimalMetadata();
                      System.out.println("    + updated made media asset");
                      MediaAssetFactory.save(crMa, myShepherd);
                      System.out.println("    + saved media asset "+crMa.toString());

                      String speciesString = enc.getTaxonomyString();
                      Annotation ann = new Annotation(speciesString, crMa);
                      ann.setMatchAgainst(true);
                      String iaClass = "mantaCR"; // should we change this?
                      ann.setIAClass(iaClass);
                      enc.addAnnotation(ann);
                      System.out.println("    + made annotation "+ann.toString());
                      myShepherd.getPM().makePersistent(ann);
                      System.out.println("    + saved annotation");
                    }

                  }
                  myShepherd.commitDBTransaction();
                }

							}
      }
      // ====================================================================

      myShepherd.beginDBTransaction();
      System.out.println("    I see encounterNumber as: "+encounterNumber);
      if ((myShepherd.isEncounter(encounterNumber))&&!locked) {
        System.out.println("...This is a valid encounter number!");
        Encounter add2shark = myShepherd.getEncounter(encounterNumber);
        try {
          String user = "Unknown User";
          if (request.getRemoteUser() != null) {
            user = request.getRemoteUser();
          }
          if (action.equals("imageadd") || action.equals("imageadd2")){
            add2shark.addComments("<p><em>" + user + " on " + (new java.util.Date()).toString() + "</em><br>" + "Submitted new mantamatcher data image.</p>");
          }
          else if (action.equals("rescan")){
            add2shark.addComments("<p><em>" + user + " on " + (new java.util.Date()).toString() + "</em><br>" + "Performed global matching scan of mantamatcher feature data.</p>");
          }
          else if (action.equals("rescanRegional")){
            add2shark.addComments("<p><em>" + user + " on " + (new java.util.Date()).toString() + "</em><br>" + "Performed regional matching scan of mantamatcher feature data.</p>");
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

        // Send response to user.

        if (!locked) {
          myShepherd.commitDBTransaction();

          if ((action.equals("imageadd"))||(action.equals("imageadd2"))) {
            String link = ServletUtilities.getEncounterURL(request, context, encounterNumber);
            ActionResult actRes = new ActionResult_Encounter(locale, "encounter.mma.addCR", true, link)
                    .setLinkParams(encounterNumber)
                    .setDetailParams(resultComment.toString())
                    .setDetailTextPreformatted(true);
            request.getSession().setAttribute(ActionResult.SESSION_KEY, actRes);
            getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);
          }
          else if (action.equals("rescan")) {
            System.out.println("...building resultsURL...");
            String paramSPV = String.format("%s=%s", MantaMatcher.PARAM_KEY_SPV, URLEncoder.encode(new Integer(ma.getId()).toString()));
            String paramScanId = String.format("%s=%s", MantaMatcher.PARAM_KEY_SCANID, URLEncoder.encode(scan.getId()));
            String encN = String.format("%s=%s", "encounterNumber", URLEncoder.encode(encounterNumber));

            String resultsURL = String.format("%s/MantaMatcher/displayResults?%s&%s&%s", request.getContextPath(), paramSPV, paramScanId, encN);
            System.out.println("...successfully trying to redirect user to: "+resultsURL);

            response.sendRedirect(resultsURL);
          }
          else if (action.equals("removeScan")) {
            response.sendRedirect(ServletUtilities.getEncounterURL(request, context, encounterNumber));
          }
          else {
            String link = ServletUtilities.getEncounterURL(request, context, encounterNumber);
            ActionResult actRes = new ActionResult_Encounter(locale, "encounter.mma.removeCR", true, link)
                    .setLinkParams(encounterNumber)
                    .setDetailParams(resultComment.toString())
                    .setDetailTextPreformatted(true);
            request.getSession().setAttribute(ActionResult.SESSION_KEY, actRes);
            getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);
          }
        }
        else {
          out.println(ServletUtilities.getHeader(request));
          if (action.equals("imageadd")) {
            out.println("<strong>Step 2 Failed:</strong> I could not upload this patterning file. There may be a database error, or a incompatible image file format may have been uploaded.");
          }
          else {
            out.println("<strong>Step 2 Failed:</strong> I could not remove this patterning file. There may be a database error.");
          }
          out.println("<p><a href=\"" + request.getScheme() + "://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
          out.println("<p><strong>Additional comments from the operation</strong><br />"+resultComment.toString()+"</p>");
          out.println(ServletUtilities.getFooter(context));
        }
      }
      else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I was unable to execute this action. I cannot find the encounter that you intended it for in the database.");
        out.println("<p><strong>Additional comments from the operation</strong><br />"+resultComment.toString()+"</p>");
        out.println(ServletUtilities.getFooter(context));
      }
    }
    catch (IOException lEx) {
      lEx.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to execute the action.");
      out.println("<p><strong>Additional comments from the operation</strong><br />"+resultComment.toString()+"</p>");
      out.println(ServletUtilities.getFooter(context));
    }
    catch (InterruptedException ex) {
      ex.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> Algorithm scanning process was unexpectedly interrupted.");
      out.println("<p><strong>Additional comments from the operation</strong><br />"+resultComment.toString()+"</p>");
      out.println(ServletUtilities.getFooter(context));
    }
    finally {
      out.close();
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
  }

}

