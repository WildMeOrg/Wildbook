package org.ecocean.servlet.importer;

import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.Project;
import org.ecocean.security.Collaboration;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.social.SocialUnit;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DeleteImportTask extends HttpServlet {
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
        myShepherd.setAction("DeleteImportTask.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;

        myShepherd.beginDBTransaction();
        if (request.getParameter("taskID") != null &&
            myShepherd.getImportTask(request.getParameter("taskID")) != null &&
            Collaboration.canUserAccessImportTask(myShepherd.getImportTask(request.getParameter(
                "taskID")), request)) {
            try {
                ImportTask task = myShepherd.getImportTask(request.getParameter("taskID"));
                List<Encounter> allEncs = new ArrayList<Encounter>(task.getEncounters());
                int total = allEncs.size();
                for (int i = 0; i < allEncs.size(); i++) {
                    Encounter enc = allEncs.get(i);
                    Occurrence occ = myShepherd.getOccurrence(enc);
                    MarkedIndividual mark = myShepherd.getMarkedIndividualQuiet(
                        enc.getIndividualID());
                    List<Project> projects = myShepherd.getProjectsForEncounter(enc);
                    ArrayList<Annotation> anns = enc.getAnnotations();
                    for (Annotation ann : anns) {
                        enc.removeAnnotation(ann);
                        myShepherd.updateDBTransaction();
                        List<Task> iaTasks = Task.getTasksFor(ann, myShepherd);
                        if (iaTasks != null && !iaTasks.isEmpty()) {
                            for (Task iaTask : iaTasks) {
                                iaTask.removeObject(ann);
                                myShepherd.updateDBTransaction();
                            }
                        }
                        myShepherd.throwAwayAnnotation(ann);
                        myShepherd.updateDBTransaction();
                    }
                    // handle occurrences
                    if (occ != null) {
                        occ.removeEncounter(enc);
                        myShepherd.updateDBTransaction();
                        if (occ.getEncounters().size() == 0) {
                            myShepherd.throwAwayOccurrence(occ);
                            myShepherd.updateDBTransaction();
                        }
                    }
                    // handle markedindividual
                    if (mark != null) {
                        mark.removeEncounter(enc);
                        myShepherd.updateDBTransaction();
                        if (mark.getEncounters().size() == 0) {
                            // check for social unit membership and remove
                            List<SocialUnit> units =
                                myShepherd.getAllSocialUnitsForMarkedIndividual(mark);
                            if (units != null && units.size() > 0) {
                                for (SocialUnit unit : units) {
                                    boolean worked = unit.removeMember(mark, myShepherd);
                                    if (worked) myShepherd.updateDBTransaction();
                                }
                            }
                            myShepherd.throwAwayMarkedIndividual(mark);
                            myShepherd.updateDBTransaction();
                        }
                    }
                    // handle projects
                    if (projects != null && projects.size() > 0) {
                        for (Project project : projects) {
                            project.removeEncounter(enc);
                            myShepherd.updateDBTransaction();
                        }
                    }
                    if (task != null) {
                        task.removeEncounter(enc);
                        task.addLog("Servlet DeleteImportTask removed Encounter: " +
                            enc.getCatalogNumber());
                        myShepherd.updateDBTransaction();
                    }
                    try {
                        myShepherd.throwAwayEncounter(enc);
                    } catch (Exception e) {
                        System.out.println("Exception on throwAwayEncounter!!");
                        e.printStackTrace();
                    }
                    myShepherd.updateDBTransaction();
                }
                myShepherd.getPM().deletePersistent(task);
                myShepherd.commitDBTransaction();
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            } finally {
                myShepherd.closeDBTransaction();
            }
            if (!locked) {
                out.println(ServletUtilities.getHeader(request));
                out.println("<strong>Success!</strong> I have successfully removed ImportTask " +
                    request.getParameter("taskID") + ".");
                out.println("<p><a href=\"//" + CommonConfiguration.getURLLocation(request) +
                    "/imports.jsp\">Return to Imports</a></p>\n");

                response.setStatus(HttpServletResponse.SC_OK);
                out.println(ServletUtilities.getFooter(context));
            } else {
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure!</strong> I failed to delete this ImportTask. Check the logs for more details.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Error:</strong> I was unable to remove your ImportTask. I cannot find the task that you intended it for in the database.");
            out.println(ServletUtilities.getFooter(context));
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
        out.close();
    }
}
