package org.ecocean.servlet;

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Uploads a new image to the file system and associates the image with an Encounter record
 *
 * @author jholmber
 */
public class EncounterAddImage extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterAddImage.class");

        // setup data dir
        String rootWebappPath = getServletContext().getRealPath("/");
        File webappsDir = new File(rootWebappPath).getParentFile();
        File shepherdDataDir = new File(webappsDir,
            CommonConfiguration.getDataDirectoryName(context));
        if (!shepherdDataDir.exists()) { shepherdDataDir.mkdirs(); }
        File encountersDir = new File(shepherdDataDir.getAbsolutePath() + "/encounters");
        if (!encountersDir.exists()) { encountersDir.mkdirs(); }
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String fileName = "None";
        String encounterNumber = "None";
        String fullPathFilename = "";

        try {
            MultipartParser mp = new MultipartParser(request,
                (CommonConfiguration.getMaxMediaSizeInMegabytes(context) * 1048576));
            Part part;
            while ((part = mp.readNextPart()) != null) {
                String name = part.getName();
                if (part.isParam()) {
                    // it's a parameter part
                    ParamPart paramPart = (ParamPart)part;
                    String value = paramPart.getStringValue();
                    // determine which variable to assign the param to
                    if (name.equals("number")) {
                        encounterNumber = value;
                    }
                }
////TODO this will need to be generified for offsite storage prob via SinglePhotoVideo? as in EncounterForm?
                if (part.isFile()) {
                    FilePart filePart = (FilePart)part;
                    fileName = ServletUtilities.cleanFileName(filePart.getFileName());
                    if (fileName != null) {
                        // fileName = Util.generateUUID() + "-orig." + FilenameUtils.getExtension(fileName);
                        // File thisSharkDir = new File(encountersDir.getAbsolutePath() +"/"+ Encounter.subdir(encounterNumber));
                        File thisSharkDir = new File(Encounter.dir(shepherdDataDir,
                            encounterNumber));
                        if (!thisSharkDir.exists()) { thisSharkDir.mkdirs(); }
                        File finalFile = new File(thisSharkDir, fileName);
                        fullPathFilename = finalFile.getCanonicalPath();
                        long file_size = filePart.writeTo(finalFile);
                    }
                }
            }
            // File thisEncounterDir = new File(encountersDir, Encounter.subdir(encounterNumber));
            File thisEncounterDir = new File(Encounter.dir(shepherdDataDir, encounterNumber));

            myShepherd.beginDBTransaction();
            if (myShepherd.isEncounter(encounterNumber)) {
                int positionInList = 10000;
                Encounter enc = myShepherd.getEncounter(encounterNumber);
                try {
                    SinglePhotoVideo newSPV = new SinglePhotoVideo(encounterNumber,
                        (new File(fullPathFilename)));
                    enc.addSinglePhotoVideo(newSPV);
                    ///// NOT YET -->  enc.refreshAssetFormats(myShepherd);
                    // enc.refreshAssetFormats(context, ServletUtilities.dataDir(context, rootWebappPath), newSPV, false);
                    enc.addComments("<p><em>" + request.getRemoteUser() + " on " +
                        (new java.util.Date()).toString() + "</em><br>" +
                        "Submitted new encounter image graphic: " + fileName + ".</p>");
                    positionInList = enc.getAdditionalImageNames().size();
                } catch (Exception le) {
                    locked = true;
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                }
                if (!locked) {
                    myShepherd.commitDBTransaction();
                    myShepherd.closeDBTransaction();
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.println(ServletUtilities.getHeader(request));
                    out.println(
                        "<strong>Success!</strong> I have successfully uploaded your new encounter image file.");
                    if (positionInList == 1) {
                        out.println(
                            "<p><i>You should also reset the thumbnail image for this encounter. You can do so by <a href=\"http://"
                            + CommonConfiguration.getURLLocation(request) +
                            "/resetThumbnail.jsp?number=" + encounterNumber +
                            "\">clicking here.</a></i></p>");
                    }
                    out.println("<p><a href=\"http://" +
                        CommonConfiguration.getURLLocation(request) +
                        "/encounters/encounter.jsp?number=" + encounterNumber +
                        "\">Return to encounter " + encounterNumber + "</a></p>\n");
                    out.println(ServletUtilities.getFooter(context));
                    String message = "An additional image file has been uploaded for encounter #" +
                        encounterNumber + ".";
                    ServletUtilities.informInterestedParties(request, encounterNumber, message,
                        context);
                } else {
                    out.println(ServletUtilities.getHeader(request));
                    out.println(
                        "<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to add this image again.");
                    out.println("<p><a href=\"http://" +
                        CommonConfiguration.getURLLocation(request) +
                        "/encounters/encounter.jsp?number=" + encounterNumber +
                        "\">Return to encounter " + encounterNumber + "</a></p>\n");
                    out.println(ServletUtilities.getFooter(context));
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
            } else {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Error:</strong> I was unable to upload your image file. I cannot find the encounter that you intended it for in the database.");
                out.println(ServletUtilities.getFooter(context));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (IOException lEx) {
            lEx.printStackTrace();
            out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I was unable to upload your image file. Please contact the web master about this message.");
            out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } catch (NullPointerException npe) {
            npe.printStackTrace();
            out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I was unable to upload an image as no file was specified.");
            out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.close();
    }
}
