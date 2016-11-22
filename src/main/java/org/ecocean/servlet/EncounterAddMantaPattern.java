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
import org.ecocean.mmutil.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
//import javax.imageio.spi.ImageReaderWriterSpi;

import org.ecocean.servlet.*;




/**
 * 
 * This servlet allows the user to upload a processed patterning file for use with the MantaMatcher algorithm.
 * @author jholmber
 * @author Giles Winstanley
 *
 */
public class EncounterAddMantaPattern extends HttpServlet {

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
    
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterAddMantaPattern.class");
    myShepherd.beginDBTransaction();
    
    //setup data dir
    File shepherdDataDir = CommonConfiguration.getDataDirectory(getServletContext(), context);
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    //if(!encountersDir.exists()){encountersDir.mkdir();}
    
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    String encounterNumber = "";
    SinglePhotoVideo spv = null;
    Map<String, File> mmFiles = null;
    String action = "imageadd";
    
    StringBuilder resultComment = new StringBuilder();
    
    if (request.getParameter("action") != null && request.getParameter("action").equals("imageremove")) {
      action = "imageremove";
    }
    else if (request.getParameter("action") != null && request.getParameter("action").equals("rescan")) {
      action = "rescan";
    }
    else if (request.getParameter("action") != null && request.getParameter("action").equals("rescanRegional")) {
      action = "rescanRegional";
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
          Encounter enc = myShepherd.getEncounter(encounterNumber);
          spv = myShepherd.getSinglePhotoVideo(request.getParameter("dataCollectionEventID"));
          MantaMatcherUtilities.removeMatcherFiles(spv);

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
          Encounter enc = myShepherd.getEncounter(encounterNumber);
          //File encDir = new File(encountersDir, enc.getEncounterNumber());
          
          File encDir = new File(Encounter.dir(shepherdDataDir, encounterNumber));
          
          
          spv = myShepherd.getSinglePhotoVideo(request.getParameter("dataCollectionEventID"));
          File spvFile = new File(encDir, spv.getFilename());
          mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
          // Delete previous matching files.
          mmFiles.get("TXT").delete();
          mmFiles.get("CSV").delete();

          // Prepare temporary algorithm input file.
          File mmaInputFile = mmFiles.get("MMA-INPUT");
          if (mmaInputFile.exists())
            mmaInputFile.delete();
          String inputText = MantaMatcherUtilities.collateAlgorithmInput(myShepherd, encountersDir, enc, spv);
          PrintWriter pw = null;
          try {
            pw = new PrintWriter(mmaInputFile);
            pw.print(inputText);
            pw.flush();
          }
          finally {
            if (pw != null)
              pw.close();
          }

          // Run algorithm.
          List<String> procArg = ListHelper.create("/usr/bin/mmatch")
                  .add(mmaInputFile.getAbsolutePath())
                  .add("0").add("0").add("2").add("1")
                  .add("-o").add(mmFiles.get("TXT").getName())
                  .add("-c").add(mmFiles.get("CSV").getName())
                  .asList();
          ProcessBuilder pb2 = new ProcessBuilder(procArg);
          pb2.directory(encDir);
          pb2.redirectErrorStream();
          
          String procArgStr = ListHelper.toDelimitedStringQuoted(procArg, " ");
          resultComment.append("<br />").append(procArgStr).append("<br /><br />");
          System.out.println(procArgStr);
          
          Process proc = pb2.start();
          // Read ouput from process.
          resultComment.append("mmatch reported the following when trying to match image files:<br />");
          BufferedReader brProc = new BufferedReader(new InputStreamReader(proc.getInputStream()));
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
          proc.waitFor();

          // Delete temporary algorithm input file.
          mmaInputFile.delete();
        }
        catch (SecurityException sx) {
          sx.printStackTrace();
          System.out.println("Error attempting to rescan manta feature image!!!!");
          resultComment.append("I hit a security error trying to rescan manta feature image. Please check your file system permissions.");
        }
      }
      // ====================================================================
      else if (action.equals("rescanRegional")){

        encounterNumber = request.getParameter("number");
        try {
          Encounter enc = myShepherd.getEncounter(encounterNumber);
          //File dirEnc = new File(encountersDir, enc.getEncounterNumber());
          
          File dirEnc = new File(Encounter.dir(shepherdDataDir, encounterNumber));
          
          
          spv = myShepherd.getSinglePhotoVideo(request.getParameter("dataCollectionEventID"));
          mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
          // Delete previous matching files.
          mmFiles.get("TXT-REGIONAL").delete();
          mmFiles.get("CSV-REGIONAL").delete();

          // Prepare temporary algorithm input file.
          File mmaInputFile = mmFiles.get("MMA-INPUT-REGIONAL");
          if (mmaInputFile.exists())
            mmaInputFile.delete();
          String inputText = MantaMatcherUtilities.collateAlgorithmInputRegional(myShepherd, encountersDir, enc, spv);
          PrintWriter pw = null;
          try {
            pw = new PrintWriter(mmaInputFile);
            pw.print(inputText);
            pw.flush();
          }
          finally {
            if (pw != null)
              pw.close();
          }

          // Run algorithm.
          List<String> procArg = ListHelper.create("/usr/bin/mmatch")
                  .add(mmaInputFile.getAbsolutePath())
                  .add("0").add("0").add("2").add("1")
                  .add("-o").add(mmFiles.get("TXT-REGIONAL").getName())
                  .add("-c").add(mmFiles.get("CSV-REGIONAL").getName())
                  .asList();
          ProcessBuilder pb2 = new ProcessBuilder(procArg);
          pb2.directory(dirEnc);
          pb2.redirectErrorStream();

          String procArgStr = ListHelper.toDelimitedStringQuoted(procArg, " ");
          resultComment.append("<br />").append(procArgStr).append("<br /><br />");
          System.out.println(procArgStr);

          Process proc = pb2.start();
          // Read ouput from process.
          resultComment.append("mmatch reported the following when trying to match image files:<br />");
          BufferedReader brProc = new BufferedReader(new InputStreamReader(proc.getInputStream()));
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
          proc.waitFor();

          // Delete temporary algorithm input file.
          mmaInputFile.delete();
        }
        catch (SecurityException sx) {
          sx.printStackTrace();
          System.out.println("Error attempting to rescan manta feature image!!!!");
          resultComment.append("I hit a security error trying to rescan manta feature image. Please check your file system permissions.");
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
              spv = myShepherd.getSinglePhotoVideo(value);
              mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
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
              MantaMatcherUtilities.removeMatcherFiles(spv);
            }
            catch (SecurityException sx) {
              sx.printStackTrace();
              System.out.println("Error attempting to delete the old version of a submitted manta data image!!!!");
              resultComment.append("I hit a security error trying to delete the old feature image. Please check your file system permissions.");
              locked=true;
            }

            // Save new image to file ready for processing.
            File write2me = mmFiles.get("CR");
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
            Encounter enc = null;
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
        
				encounterNumber = request.getParameter("encounterID");
				spv = myShepherd.getSinglePhotoVideo(request.getParameter("photoNumber"));
				mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
				String matchFilename = request.getParameter("matchFilename");
				String errorMessage = null;

        assert mmFiles != null;
        try {
          // Attempt to delete existing MM algorithm files.
          // (Shouldn't exist, but just a precaution.)
          MantaMatcherUtilities.removeMatcherFiles(spv);
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

					Encounter enc = null;
					File write2me = null;

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

                // Set MMA-compatible flag if appropriate.
                if (enc != null && MantaMatcherUtilities.checkEncounterHasMatcherFiles(enc, shepherdDataDir)) {
                  enc.setMmaCompatible(true);
                  myShepherd.commitDBTransaction();
                }

							}
      }
      // ====================================================================

      myShepherd.beginDBTransaction();
      System.out.println("    I see encounterNumber as: "+encounterNumber);
      if ((myShepherd.isEncounter(encounterNumber))&&!locked) {
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
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Confirmed:</strong> I have successfully added your mantamatcher data image file.");
            out.println("<p><strong>Additional comments from the operation</strong><br />"+resultComment.toString()+"</p>");
            
            out.println("<p><a href=\"" + request.getScheme() + "://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
          }
          else if (action.equals("rescan")) {
            String resultsURL = request.getContextPath() + "/MantaMatcher/displayResults?spv=" + spv.getDataCollectionEventID();
            response.sendRedirect(resultsURL);
          }
          else if (action.equals("rescanRegional")) {
            String resultsURL = request.getContextPath() + "/MantaMatcher/displayResultsRegional?spv=" + spv.getDataCollectionEventID();
            response.sendRedirect(resultsURL);
          }
          else {
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Confirmed:</strong> I have successfully removed your mantamatcher data image file.");
            out.println("<p><a href=\"" + request.getScheme() + "://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
            out.println("<p><strong>Additional comments from the operation</strong><br />"+resultComment.toString()+"</p>");
            out.println(ServletUtilities.getFooter(context));
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
  
