package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.util.List;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.Project;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;

public class UserHome extends ApiBase {
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.UserHome");
        myShepherd.beginDBTransaction();

        JSONObject home = new JSONObject();
        User currentUser = myShepherd.getUser(request);
        if (currentUser == null) {
            response.setStatus(401);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": false}");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }
        home.put("user", currentUser.infoJSONObject(context, true));

        // TODO: Replace with OpenSearch

        JSONArray encountersArr = new JSONArray();
        int count = 0;
        for (Encounter enc : myShepherd.getEncountersForSubmitter(currentUser)) {
            JSONObject ej = new JSONObject();
            ej.put("id", enc.getId());
            ej.put("date", enc.getDate());
            ej.put("numberAnnotations", Util.collectionSize(enc.getAnnotations()));
            ej.put("taxonomy", enc.getTaxonomyString());
            encountersArr.put(ej);
            count++;
            if (count > 2) break;
        }
        home.put("latestEncounters", encountersArr);

        JSONObject itaskJson = null;
        List<ImportTask> itasks = myShepherd.getImportTasksForUser(currentUser);
        if (itasks.size() > 0) {
            itaskJson = new JSONObject();
            itaskJson.put("id", itasks.get(0).getId());
            itaskJson.put("dateTimeCreated", itasks.get(0).getCreated());
            itaskJson.put("numberEncounters", Util.collectionSize(itasks.get(0).getEncounters()));
            itaskJson.put("numberMediaAssets", Util.collectionSize(itasks.get(0).getMediaAssets()));
        }
        home.put("latestBulkImportTask", Util.jsonNull(itaskJson));

        JSONObject latestIndivJson = null;
        for (Encounter enc : myShepherd.getEncountersForSubmitter(currentUser, "modified DESC")) {
            if (enc.getIndividual() != null) {
                latestIndivJson = new JSONObject();
                latestIndivJson.put("id", enc.getIndividual().getId());
                latestIndivJson.put("dateTime", enc.getModified());
                break;
            }
        }
        home.put("latestIndividual", Util.jsonNull(latestIndivJson));

        // match result: if within 2 weeks, match result page; if older, the encounter page
        JSONObject matchJson = null;
        List<Task> tasks = myShepherd.getIdentificationTasksForUser(currentUser);
        if (!Util.collectionIsEmptyOrNull(tasks)) {
            matchJson = new JSONObject();
            matchJson.put("id", tasks.get(0).getId());
            matchJson.put("dateTimeCreated", new DateTime(tasks.get(0).getCreatedLong()));
            matchJson.put("encounterId", JSONObject.NULL);
            List<Annotation> anns = tasks.get(0).getObjectAnnotations();
            if (!Util.collectionIsEmptyOrNull(anns))
                for (Annotation ann : anns) {
                    Encounter enc = ann.findEncounter(myShepherd);
                    if (enc != null) {
                        matchJson.put("encounterId", enc.getId());
                        break;
                    }
                }
        }
        home.put("latestMatchTask", Util.jsonNull(matchJson));

        JSONArray projArr = new JSONArray();
        count = 0;
        for (Project proj : currentUser.getProjects(myShepherd)) {
            JSONObject pj = new JSONObject();
            pj.put("id", proj.getId());
            pj.put("name", proj.getResearchProjectName());
            pj.put("percentComplete", proj.getPercentWithIncrementalIds());
            pj.put("numberEncounters", proj.getEncounters().size());
            projArr.put(pj);
            count++;
            if (count > 2) break;
        }
        home.put("projects", projArr);

        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(home.toString());
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    }
}