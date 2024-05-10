package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.grid.GridManager;
import org.ecocean.grid.GridManagerFactory;
import org.ecocean.ia.Task;
import org.ecocean.servlet.importer.ImportTask;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.*;
import java.util.ArrayList;
// import java.util.Iterator;
import java.util.List;
import java.util.Map;
// import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EncounterDelete extends HttpServlet {
    /** SLF4J logger instance for writing log entries. */
    public static Logger log = LoggerFactory.getLogger(EncounterDelete.class);

    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    private void setDateLastModified(Encounter enc) {
        String strOutputDateTime = ServletUtilities.getDate();

        enc.setDWCDateLastModified(strOutputDateTime);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        // String langCode = ServletUtilities.getLanguageCode(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterDelete.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;

        // setup data dir
        String rootWebappPath = getServletContext().getRealPath("/");
        File webappsDir = new File(rootWebappPath).getParentFile();
        File shepherdDataDir = new File(webappsDir,
            CommonConfiguration.getDataDirectoryName(context));
        if (!shepherdDataDir.exists()) { shepherdDataDir.mkdirs(); }
        File encountersDir = new File(shepherdDataDir.getAbsolutePath() + "/encounters");
        if (!encountersDir.exists()) { encountersDir.mkdirs(); }
        // boolean isOwner = true;

        myShepherd.beginDBTransaction();
        if ((request.getParameter("number") != null) &&
            (myShepherd.isEncounter(request.getParameter("number")))) {
            String message = "Encounter " + request.getParameter("number") +
                " was deleted from the database.";
            ServletUtilities.informInterestedParties(request, request.getParameter("number"),
                message, context);
            Encounter enc2trash = myShepherd.getEncounter(request.getParameter("number"));
            setDateLastModified(enc2trash);
            if (enc2trash.getIndividualID() == null) {
                // myShepherd.beginDBTransaction();

                try {
                    Encounter backUpEnc = myShepherd.getEncounterDeepCopy(
                        enc2trash.getEncounterNumber());
                    String savedFilename = request.getParameter("number") + ".dat";
                    File thisEncounterDir = new File(Encounter.dir(shepherdDataDir,
                        request.getParameter("number")));
                    if (!thisEncounterDir.exists()) {
                        thisEncounterDir.mkdirs();
                        System.out.println(
                            "Trying to create the folder to store a dat file in EncounterDelete2: "
                            + thisEncounterDir.getAbsolutePath());
                        File serializedBackup = new File(thisEncounterDir, savedFilename);
                        FileOutputStream fout = new FileOutputStream(serializedBackup);
                        ObjectOutputStream oos = new ObjectOutputStream(fout);
                        oos.writeObject(backUpEnc);
                        oos.close();
                    }
                } catch (NotSerializableException nse) {
                    System.out.println("[WARN]: The encounter " + enc2trash.getCatalogNumber() +
                        " could not be serialized.");
                    nse.printStackTrace();
                }
                try {
                    Occurrence occ = myShepherd.getOccurrenceForEncounter(enc2trash.getID());
                    if (occ == null && (enc2trash.getOccurrenceID() != null) &&
                        (myShepherd.isOccurrence(enc2trash.getOccurrenceID()))) {
                        occ = myShepherd.getOccurrence(enc2trash.getOccurrenceID());
                    }
                    if (occ != null) {
                        occ.removeEncounter(enc2trash);
                        enc2trash.setOccurrenceID(null);
                        // delete Occurrence if it's last encounter has been removed.
                        if (occ.getNumberEncounters() == 0) {
                            myShepherd.throwAwayOccurrence(occ);
                        }
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                    }
                    List<Project> projects = myShepherd.getProjectsForEncounter(enc2trash);
                    if (projects != null && !projects.isEmpty()) {
                        for (Project project : projects) {
                            project.removeEncounter(enc2trash);
                            myShepherd.updateDBTransaction();
                        }
                    }
                    // Remove it from an ImportTask if needed
                    ImportTask task = myShepherd.getImportTaskForEncounter(
                        enc2trash.getCatalogNumber());
                    if (task != null) {
                        task.removeEncounter(enc2trash);
                        task.addLog("Servlet EncounterDelete removed Encounter: " +
                            enc2trash.getCatalogNumber());
                        myShepherd.updateDBTransaction();
                    }
                    if (myShepherd.getImportTaskForEncounter(enc2trash) != null) {
                        ImportTask itask = myShepherd.getImportTaskForEncounter(enc2trash);
                        itask.removeEncounter(enc2trash);
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                    }
                    // Set all associated annotations matchAgainst to false
                    enc2trash.useAnnotationsForMatching(false);
                    // break association with User object submitters
                    if (enc2trash.getSubmitters() != null) {
                        enc2trash.setSubmitters(null);
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                    }
                    // break asociation with User object photographers
                    if (enc2trash.getPhotographers() != null) {
                        enc2trash.setPhotographers(null);
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                    }
                    // record who deleted this encounter
                    enc2trash.addComments("<p><em>" + request.getRemoteUser() + " on " +
                        (new java.util.Date()).toString() + "</em><br>" +
                        "Deleted this encounter from the database.");
                    myShepherd.commitDBTransaction();

                    ArrayList<Annotation> anns = enc2trash.getAnnotations();
                    for (Annotation ann : anns) {
                        myShepherd.beginDBTransaction();
                        enc2trash.removeAnnotation(ann);
                        myShepherd.updateDBTransaction();
                        List<Task> iaTasks = Task.getTasksFor(ann, myShepherd);
                        if (iaTasks != null && !iaTasks.isEmpty()) {
                            for (Task iaTask : iaTasks) {
                                iaTask.removeObject(ann);
                                myShepherd.updateDBTransaction();
                            }
                        }
                        myShepherd.throwAwayAnnotation(ann);
                        myShepherd.commitDBTransaction();
                    }
                    // now delete for good
                    myShepherd.beginDBTransaction();
                    myShepherd.throwAwayEncounter(enc2trash);

                    // remove from grid too
                    GridManager gm = GridManagerFactory.getGridManager();
                    gm.removeMatchGraphEntry(request.getParameter("number"));

                    myShepherd.commitDBTransaction();

                    // log it
                    Logger log = LoggerFactory.getLogger(EncounterDelete.class);
                    log.info("Click to restore deleted encounter: <a href=\"" +
                        request.getScheme() + "://" + CommonConfiguration.getURLLocation(request) +
                        "/ResurrectDeletedEncounter?number=" + request.getParameter("number") +
                        "\">" + request.getParameter("number") + "</a>");

                    out.println(ServletUtilities.getHeader(request));
                    out.println("<strong>Success:</strong> I have removed encounter " +
                        request.getParameter("number") +
                        " from the database. If you have deleted this encounter in error, please contact the webmaster and reference encounter "
                        + request.getParameter("number") + " to have it restored.");
                    List<String> allStates = CommonConfiguration.getIndexedPropertyValues(
                        "encounterState", context);
                    int allStatesSize = allStates.size();
                    if (allStatesSize > 0) {
                        for (int i = 0; i < allStatesSize; i++) {
                            String stateName = allStates.get(i);
                            out.println("<p><a href=\"encounters/searchResults.jsp?state=" +
                                stateName + "\">View all " + stateName +
                                " encounters</a></font></p>");
                        }
                    }
                    out.println(ServletUtilities.getFooter(context));
                } catch (Exception edel) {
                    locked = true;
                    // log.warn("Failed to serialize encounter: " + request.getParameter("number"), edel);
                    edel.printStackTrace();
                    myShepherd.rollbackDBTransaction();
                }
                // Notify new-submissions address
                Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request,
                    enc2trash);
                tagMap.put("@USER@", request.getRemoteUser());
                tagMap.put("@ENCOUNTER_ID@", request.getParameter("number"));
                String mailTo = CommonConfiguration.getNewSubmissionEmail(context);
                NotificationMailer mailer = new NotificationMailer(context, null, mailTo,
                    "encounterDelete", tagMap);
                ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
                es.execute(mailer);
                es.shutdown();

                /*
                   else {
                   out.println(ServletUtilities.getHeader(request));
                   out.println("<strong>Failure:</strong> I have NOT removed encounter " + request.getParameter("number") + " from the database. An
                      exception occurred in the deletion process.");
                   out.println("<p><a href=\"//" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                      request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + "</a>.</p>\n");

                   List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
                   int allStatesSize=allStates.size();
                   if(allStatesSize>0){
                    for(int i=0;i<allStatesSize;i++){
                      String stateName=allStates.get(i);
                      out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");
                    }
                   }
                   out.println(ServletUtilities.getFooter(context));
                   response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                   }
                 */
            } else {
                myShepherd.rollbackDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                out.println("Encounter " + request.getParameter("number") +
                    " is assigned to a Marked Individual and cannot be deleted until it has been removed from that individual.");
                out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) +
                    "/encounters/encounter.jsp?number=" + request.getParameter("number") +
                    "\">Return to encounter " + request.getParameter("number") + "</a>.</p>\n");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I don't know which encounter you're trying to remove.");
            out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        myShepherd.closeDBTransaction();
        out.close();
    }
}
