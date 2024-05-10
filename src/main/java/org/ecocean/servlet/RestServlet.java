/**********************************************************************
   Copyright (c) 2009 Erik Bengtson and others. All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
      WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations
      under the License.


   Contributors:
    ...
 **********************************************************************/
package org.ecocean.servlet;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import org.ecocean.ShepherdPMF;
import org.ecocean.Util;

import java.lang.reflect.Method;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.datanucleus.api.jdo.JDOPersistenceManager;
import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;
import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.RESTUtils;
import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.exceptions.ClassNotResolvedException;
import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.exceptions.NucleusObjectNotFoundException;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.ExecutionContext;
import org.datanucleus.identity.IdentityUtils;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.IdentityType;
import org.datanucleus.PersistenceNucleusContext;
import org.datanucleus.util.NucleusLogger;

import java.io.OutputStream;
import java.util.zip.*;

/**
 * This servlet exposes persistent class via RESTful HTTP requests. Supports the following
 * <ul>
 * <li>GET (retrieve/query)</li>
 * <li>POST (update/insert)</li>
 * <li>PUT (update/insert)</li>
 * <li>DELETE (delete)</li>
 * <li>HEAD (validate)</li>
 * </ul>
 */
public class RestServlet extends HttpServlet {
    private static final long serialVersionUID = -4445182084242929362L;

    public static final NucleusLogger LOGGER_REST = NucleusLogger.getLoggerInstance(
        "DataNucleus.REST");
    PersistenceManagerFactory pmf;
    PersistenceNucleusContext nucCtx;
    HttpServletRequest thisRequest;

    /* (non-Javadoc)
     * @see javax.servlet.GenericServlet#destroy()
     */
    public void destroy() {
        if (pmf != null && !pmf.isClosed()) {
            LOGGER_REST.info("REST : Closing PMF");
            pmf.close();
        }
        super.destroy();
    }

    public void init(ServletConfig config)
    throws ServletException {
        /*
           String factory = config.getInitParameter("persistence-context");
           if (factory == null)
           {
               throw new ServletException("You haven't specified \"persistence-context\" property defining the persistence unit");
           }


           try
           {
               LOGGER_REST.info("REST : Creating PMF for factory=" + factory);
               pmf = JDOHelper.getPersistenceManagerFactory(factory);
               this.nucCtx = ((JDOPersistenceManagerFactory)pmf).getNucleusContext();
           }
           catch (Exception e)
           {
               LOGGER_REST.error("Exception creating PMF", e);
               throw new ServletException("Could not create internal PMF. See nested exception for details", e);
           }
         */

        super.init(config);
    }

    /**
     * Convenience method to get the next token after a "/".
     * @param req The request
     * @return The next token
     */
    private String getNextTokenAfterSlash(HttpServletRequest req) {
        try {
            String path = req.getRequestURI().substring(req.getContextPath().length() +
                req.getServletPath().length());
            StringTokenizer tokenizer = new StringTokenizer(path, "/");
            return tokenizer.nextToken();
        } catch (Exception e) {}
        return null;
    }

    /**
     * Convenience accessor to get the id, following a "/".
     * @param req The request
     * @return The id (or null if no slash)
     */
    private Object getId(HttpServletRequest req) {
        ClassLoaderResolver clr = nucCtx.getClassLoaderResolver(RestServlet.class.getClassLoader());
        String path = req.getRequestURI().substring(req.getContextPath().length() +
            req.getServletPath().length());
        StringTokenizer tokenizer = new StringTokenizer(path, "/");
        String className = tokenizer.nextToken();
        AbstractClassMetaData cmd = nucCtx.getMetaDataManager().getMetaDataForClass(className, clr);
        String id = null;

        if (tokenizer.hasMoreTokens()) {
            // "id" single-field specified in URL
            id = tokenizer.nextToken();
            if (id == null || cmd == null) {
                return null;
            }
            Object identity = RESTUtils.getIdentityForURLToken(cmd, id, nucCtx);
            if (identity != null) {
                return identity;
            }
        }
        // "id" must have been specified in the content of the request
        try {
            if (id == null && req.getContentLength() > 0) {
                char[] buffer = new char[req.getContentLength()];
                req.getReader().read(buffer);
                id = new String(buffer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (id == null || cmd == null) {
            return null;
        }
        try {
            // assume it's a JSONObject
            id = URLDecoder.decode(id, "UTF-8");
            JSONObject jsonobj = new JSONObject(id);
            return RESTUtils.getNonPersistableObjectFromJSONObject(jsonobj,
                    clr.classForName(cmd.getObjectidClass()), nucCtx);
        } catch (JSONException ex) {
            // not JSON syntax
        } catch (UnsupportedEncodingException e) {
            LOGGER_REST.error("Exception caught when trying to determine id", e);
        }
        return id;
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        String servletID = Util.generateUUID();
        getPMF(req, servletID);
        // Retrieve any fetch group that needs applying to the fetch
        String[] fetchParams = req.getParameterValues("fetch");
        String fetchDepth = req.getParameter("fetchDepth");
        String encodings = req.getHeader("Accept-Encoding");
        boolean useCompression = ((encodings != null) && (encodings.indexOf("gzip") > -1));
        String queryString = "";
        try {
            String token = getNextTokenAfterSlash(req);
            if (req.getParameter("query") != null || token != null &&
                (token.equalsIgnoreCase("query") || token.equalsIgnoreCase("jdoql"))) {
                if (req.getParameter("query") != null) {
                    queryString = URLDecoder.decode(req.getParameter("query"), "UTF-8");
                } else {
                    // GET "/query?the_query_details" or GET "/jdoql?the_query_details" where "the_query_details" is "SELECT FROM ... WHERE ... ORDER
                    // BY ..."
                    queryString = URLDecoder.decode(req.getQueryString(), "UTF-8");
                }
                PersistenceManager pm = pmf.getPersistenceManager();
                ShepherdPMF.setShepherdState("RestServlet.class" + "_" + servletID, "new");
                if (fetchParams != null) {
                    int numParams = fetchParams.length;
                    for (int g = 0; g < numParams; g++) {
                        if (g == 0) {
                            pm.getFetchPlan().setGroup(fetchParams[g]);
                            // System.out.println("Setting fetch group: "+fetchParams[g]);
                        } else {
                            pm.getFetchPlan().addGroup(fetchParams[g]);
                            // System.out.println("Adding fetch group: "+fetchParams[g]);
                        }
                    }
                    // check fetchDepth
                    if (req.getParameter("fetchDepth") != null) {
                        try {
                            int value = Integer.parseInt(req.getParameter("fetchDepth").trim());
                            pm.getFetchPlan().setMaxFetchDepth(value);
                            System.out.println("Setting fetch depth: " + value);
                        } catch (Exception nfe) {
                            nfe.printStackTrace();
                        }
                    }
                    this.nucCtx = ((JDOPersistenceManagerFactory)pmf).getNucleusContext();
                }
                try {
                    pm.currentTransaction().begin();
                    ShepherdPMF.setShepherdState("RestServlet.class" + "_" + servletID, "begin");

                    Query query = pm.newQuery("JDOQL", queryString);
                    if (fetchParams != null) {
                        int numParams = fetchParams.length;
                        for (int g = 0; g < numParams; g++) {
                            if (g == 0) {
                                query.getFetchPlan().setGroup(fetchParams[g]);
                            } else {
                                query.getFetchPlan().addGroup(fetchParams[g]);
                            }
                        }
                    }
                    System.out.println("Fetch plan class: " +
                        query.getFetchPlan().getGroups().toString());

                    Object result = filterResult(query.execute());
                    if (result instanceof Collection) {
                        JSONArray jsonobj = convertToJson(req, (Collection)result,
                            ((JDOPersistenceManager)pm).getExecutionContext());
                        // JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)result,
                        // ((JDOPersistenceManager)pm).getExecutionContext());
                        tryCompress(req, resp, jsonobj, useCompression);
                    } else {
                        JSONObject jsonobj = convertToJson(req, result,
                            ((JDOPersistenceManager)pm).getExecutionContext());
                        // JSONObject jsonobj = RESTUtils.getJSONObjectFromPOJO(result,
                        // ((JDOPersistenceManager)pm).getExecutionContext());
                        tryCompress(req, resp, jsonobj, useCompression);
                    }
                    query.closeAll();
                    resp.setHeader("Content-Type", "application/json");
                    resp.setStatus(200);
                    pm.currentTransaction().commit();
                    ShepherdPMF.setShepherdState("RestServlet.class" + "_" + servletID, "commit");
                } finally {
                    if (pm.currentTransaction().isActive()) {
                        pm.currentTransaction().rollback();
                        ShepherdPMF.setShepherdState("RestServlet.class" + "_" + servletID,
                            "rollback");
                    }
                    pm.close();
                    ShepherdPMF.removeShepherdState("RestServlet.class" + "_" + servletID);
                }
                return;
            }
            /*
               else if (token.equalsIgnoreCase("jpql"))
               {
                // GET "/jpql?the_query_details" where "the_query_details" is "SELECT ... FROM ... WHERE ... ORDER BY ..."
                String queryString = URLDecoder.decode(req.getQueryString(), "UTF-8");
                PersistenceManager pm = pmf.getPersistenceManager();
                try
                {
                    pm.currentTransaction().begin();
                    Query query = pm.newQuery("JPQL", queryString);
                    if (fetchParam != null)
                    {
                        query.getFetchPlan().addGroup(fetchParam);
                    }
                    Object result = filterResult(query.execute());
                    if (result instanceof Collection)
                    {
                        JSONArray jsonobj = convertToJson(req, (Collection)result, ((JDOPersistenceManager)pm).getExecutionContext());
                        //JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)result,
                            //((JDOPersistenceManager)pm).getExecutionContext());
                        tryCompress(req, resp, jsonobj, useCompression);
                    }
                    else
                    {
                        JSONObject jsonobj = convertToJson(req, result, ((JDOPersistenceManager)pm).getExecutionContext());
                        //JSONObject jsonobj = RESTUtils.getJSONObjectFromPOJO(result,
                            //((JDOPersistenceManager)pm).getExecutionContext());
                        tryCompress(req, resp, jsonobj, useCompression);
                    }
                    query.closeAll();
                    resp.setHeader("Content-Type", "application/json");
                    resp.setStatus(200);
                    pm.currentTransaction().commit();
                }
                finally
                {
                    if (pm.currentTransaction().isActive())
                    {
                        pm.currentTransaction().rollback();
                    }
                    pm.close();
                }
                return;
               }
             */
            else {
                // GET "/{candidateclass}..."
                String className = token;
                ClassLoaderResolver clr = nucCtx.getClassLoaderResolver(
                    RestServlet.class.getClassLoader());
                AbstractClassMetaData cmd = nucCtx.getMetaDataManager().getMetaDataForEntityName(
                    className);
                try {
                    if (cmd == null) {
                        cmd = nucCtx.getMetaDataManager().getMetaDataForClass(className, clr);
                    }
                } catch (ClassNotResolvedException ex) {
                    JSONObject error = new JSONObject();
                    error.put("exception", ex.getMessage());
                    resp.getWriter().write(error.toString());
                    resp.setStatus(404);
                    resp.setHeader("Content-Type", "application/json");
                    ShepherdPMF.removeShepherdState("RestServlet.class" + "_" + servletID);

                    return;
                }
                Object id = getId(req);
                if (id == null) {
                    if (req.getRemoteUser() != null) {
                        // Find objects by type or by query
                        try {
                            // get the whole extent for this candidate
                            queryString = "SELECT FROM " + cmd.getFullClassName();
                            if (req.getQueryString() != null) {
                                // query by filter for this candidate
                                queryString += " WHERE " + URLDecoder.decode(req.getQueryString(),
                                    "UTF-8");
                            }
                            PersistenceManager pm = pmf.getPersistenceManager();
                            if (fetchParams != null) {
                                int numParams = fetchParams.length;
                                for (int g = 0; g < numParams; g++) {
                                    if (g == 0) {
                                        pm.getFetchPlan().setGroup(fetchParams[g]);
                                    } else {
                                        pm.getFetchPlan().addGroup(fetchParams[g]);
                                    }
                                }
                            }
                            try {
                                pm.currentTransaction().begin();
                                ShepherdPMF.setShepherdState("RestServlet.class" + "_" + servletID,
                                    "begin");

                                Query query = pm.newQuery("JDOQL", queryString);
                                List result = (List)filterResult(query.execute());
                                JSONArray jsonobj = convertToJson(req, result,
                                    ((JDOPersistenceManager)pm).getExecutionContext());
                                // JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection(result,
                                // ((JDOPersistenceManager)pm).getExecutionContext());
                                tryCompress(req, resp, jsonobj, useCompression);
                                query.closeAll();
                                resp.setHeader("Content-Type", "application/json");
                                resp.setStatus(200);
                                // pm.currentTransaction().commit();
                            } finally {
                                if (pm.currentTransaction().isActive()) {
                                    pm.currentTransaction().rollback();
                                }
                                pm.close();
                                ShepherdPMF.removeShepherdState("RestServlet.class" + "_" +
                                    servletID);
                            }
                            return;
                        } catch (NucleusUserException e) {
                            JSONObject error = new JSONObject();
                            error.put("exception", e.getMessage());
                            resp.getWriter().write(error.toString());
                            resp.setStatus(400);
                            resp.setHeader("Content-Type", "application/json");
                            ShepherdPMF.removeShepherdState("RestServlet.class" + "_" + servletID);

                            return;
                        } catch (NucleusException ex) {
                            JSONObject error = new JSONObject();
                            error.put("exception", ex.getMessage());
                            resp.getWriter().write(error.toString());
                            resp.setStatus(404);
                            resp.setHeader("Content-Type", "application/json");
                            ShepherdPMF.removeShepherdState("RestServlet.class" + "_" + servletID);
                            return;
                        } catch (RuntimeException ex) {
                            // errors from the google appengine may be raised when running queries
                            JSONObject error = new JSONObject();
                            error.put("exception", ex.getMessage());
                            resp.getWriter().write(error.toString());
                            resp.setStatus(404);
                            resp.setHeader("Content-Type", "application/json");
                            ShepherdPMF.removeShepherdState("RestServlet.class" + "_" + servletID);
                            return;
                        }
                    } else {
                        JSONObject error = new JSONObject();
                        error.put("exception",
                            "You have to log in to GET a full class list of objects.");
                        resp.getWriter().write(error.toString());
                        resp.setStatus(400);
                        resp.setHeader("Content-Type", "application/json");
                        ShepherdPMF.removeShepherdState("RestServlet.class" + "_" + servletID);
                        return;
                    }
                }
                // GET "/{candidateclass}/id" - Find object by id
                PersistenceManager pm = pmf.getPersistenceManager();
                if (fetchParams != null) {
                    int numParams = fetchParams.length;
                    for (int g = 0; g < numParams; g++) {
                        if (g == 0) {
                            pm.getFetchPlan().setGroup(fetchParams[g]);
                        } else {
                            pm.getFetchPlan().addGroup(fetchParams[g]);
                        }
                    }
                }
                try {
                    pm.currentTransaction().begin();
                    ShepherdPMF.setShepherdState("RestServlet.class" + "_" + servletID, "begin");
                    Object result = filterResult(pm.getObjectById(id));
                    JSONObject jsonobj = convertToJson(req, result,
                        ((JDOPersistenceManager)pm).getExecutionContext());
                    // JSONObject jsonobj = RESTUtils.getJSONObjectFromPOJO(result,
                    // ((JDOPersistenceManager)pm).getExecutionContext());
                    tryCompress(req, resp, jsonobj, useCompression);
                    // resp.getWriter().write(jsonobj.toString());
                    resp.setHeader("Content-Type", "application/json");

                    return;
                } catch (NucleusObjectNotFoundException ex) {
                    resp.setContentLength(0);
                    resp.setStatus(404);
                    return;
                } catch (NucleusException ex) {
                    JSONObject error = new JSONObject();
                    error.put("exception", ex.getMessage());
                    resp.getWriter().write(error.toString());
                    resp.setStatus(404);
                    resp.setHeader("Content-Type", "application/json");
                    return;
                } finally {
                    if (pm.currentTransaction().isActive()) {
                        pm.currentTransaction().rollback();
                    }
                    pm.close();
                    ShepherdPMF.removeShepherdState("RestServlet.class" + "_" + servletID);
                }
            }
        } catch (JSONException e) {
            try {
                JSONObject error = new JSONObject();
                error.put("exception", e.getMessage());
                resp.getWriter().write(error.toString());
                resp.setStatus(404);
                resp.setHeader("Content-Type", "application/json");
            } catch (JSONException e1) {
                e1.printStackTrace();
            }
        }
    }

    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        doPost(req, resp);
    }

    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        ServletUtilities.doOptions(req, resp);
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        String servletID = Util.generateUUID();
        getPMF(req, servletID);
        if (req.getContentLength() < 1) {
            resp.setContentLength(0);
            resp.setStatus(400); // bad request
            return;
        }
        char[] buffer = new char[req.getContentLength()];
        req.getReader().read(buffer);
        String str = new String(buffer);
        JSONObject jsonobj;
        PersistenceManager pm = pmf.getPersistenceManager();
        ExecutionContext ec = ((JDOPersistenceManager)pm).getExecutionContext();
        try {
            pm.currentTransaction().begin();
            jsonobj = new JSONObject(str);
            String className = getNextTokenAfterSlash(req);
            jsonobj.put("class", className);

            // Process any id info provided in the URL
            AbstractClassMetaData cmd = ec.getMetaDataManager().getMetaDataForClass(className,
                ec.getClassLoaderResolver());
            String path = req.getRequestURI().substring(req.getContextPath().length() +
                req.getServletPath().length());
            StringTokenizer tokenizer = new StringTokenizer(path, "/");
            tokenizer.nextToken(); // className
            if (tokenizer.hasMoreTokens()) {
                String idToken = tokenizer.nextToken();
                Object id = RESTUtils.getIdentityForURLToken(cmd, idToken, nucCtx);
                if (id != null) {
                    if (cmd.getIdentityType() == IdentityType.APPLICATION) {
                        if (cmd.usesSingleFieldIdentityClass()) {
                            jsonobj.put(cmd.getPrimaryKeyMemberNames()[0],
                                IdentityUtils.getTargetKeyForSingleFieldIdentity(id));
                        }
                    } else if (cmd.getIdentityType() == IdentityType.DATASTORE) {
                        jsonobj.put("_id", IdentityUtils.getTargetKeyForDatastoreIdentity(id));
                    }
                }
            }
            Object pc = RESTUtils.getObjectFromJSONObject(jsonobj, className, ec);
            // boolean restAccessOk = restAccessCheck(pc, req, jsonobj);
            boolean restAccessOk = false;              // TEMPORARILY disable ALL access to POST/PUT until we really test things  TODO
/*
   System.out.println(jsonobj);
   System.out.println("+++++");

                        Method restAccess = null;
                        boolean restAccessOk = true;
                        try {
                            restAccess = pc.getClass().getMethod("restAccess", new Class[] { HttpServletRequest.class });
                        } catch (NoSuchMethodException nsm) {
                            //nothing to do
                        }

                        if (restAccess != null) {
                            try {
                                restAccess.invoke(pc, req);
                            } catch (Exception ex) {
                                restAccessOk = false;
                                //this is the expected result when we are blocked (user not allowed) System.out.println("got Exception trying to
                                   invoke restAccess: " + ex.toString());
                            }
                        }
 */
            if (restAccessOk) {
                Object obj = pm.makePersistent(pc);
                JSONObject jsonobj2 = convertToJson(req, obj, ec);
                // JSONObject jsonobj2 = RESTUtils.getJSONObjectFromPOJO(obj, ec);
                resp.getWriter().write(jsonobj2.toString());
                resp.setHeader("Content-Type", "application/json");
                pm.currentTransaction().commit();
            } else {
                throw new NucleusUserException("Access denied");              // seems like what we should throw.  does it matter?
            }
        } catch (ClassNotResolvedException e) {
            try {
                JSONObject error = new JSONObject();
                error.put("exception", e.getMessage());
                resp.getWriter().write(error.toString());
                resp.setStatus(500);
                resp.setHeader("Content-Type", "application/json");
                LOGGER_REST.error(e.getMessage(), e);
            } catch (JSONException e1) {
                throw new RuntimeException(e1);
            }
        } catch (NucleusUserException e) {
            try {
                JSONObject error = new JSONObject();
                error.put("exception", e.getMessage());
                resp.getWriter().write(error.toString());
                resp.setStatus(400);
                resp.setHeader("Content-Type", "application/json");
                LOGGER_REST.error(e.getMessage(), e);
            } catch (JSONException e1) {
                throw new RuntimeException(e1);
            }
        } catch (NucleusException e) {
            try {
                JSONObject error = new JSONObject();
                error.put("exception", e.getMessage());
                resp.getWriter().write(error.toString());
                resp.setStatus(500);
                resp.setHeader("Content-Type", "application/json");
                LOGGER_REST.error(e.getMessage(), e);
            } catch (JSONException e1) {
                throw new RuntimeException(e1);
            }
        } catch (JSONException e) {
            try {
                JSONObject error = new JSONObject();
                error.put("exception", e.getMessage());
                resp.getWriter().write(error.toString());
                resp.setStatus(500);
                resp.setHeader("Content-Type", "application/json");
                LOGGER_REST.error(e.getMessage(), e);
            } catch (JSONException e1) {
                throw new RuntimeException(e1);
            }
        } finally {
            if (pm.currentTransaction().isActive()) {
                pm.currentTransaction().rollback();
            }
            pm.close();
        }
        resp.setStatus(201); // created
    }

    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        String servletID = Util.generateUUID();

        getPMF(req, servletID);
        PersistenceManager pm = pmf.getPersistenceManager();
        try {
            String className = getNextTokenAfterSlash(req);
            ClassLoaderResolver clr = nucCtx.getClassLoaderResolver(
                RestServlet.class.getClassLoader());
            AbstractClassMetaData cmd = nucCtx.getMetaDataManager().getMetaDataForEntityName(
                className);
            try {
                if (cmd == null) {
                    cmd = nucCtx.getMetaDataManager().getMetaDataForClass(className, clr);
                }
            } catch (ClassNotResolvedException ex) {
                try {
                    JSONObject error = new JSONObject();
                    error.put("exception", ex.getMessage());
                    resp.getWriter().write(error.toString());
                    resp.setStatus(404);
                    resp.setHeader("Content-Type", "application/json");
                } catch (JSONException e) {
                    // will not happen
                }
                return;
            }
            Object id = getId(req);
            if (id == null) {
                // Delete all objects of this type
                pm.currentTransaction().begin();
                Query q = pm.newQuery("SELECT FROM " + cmd.getFullClassName());
                q.deletePersistentAll();
                pm.currentTransaction().commit();
                q.closeAll();
            } else {
                // we disable any delete for now, until permission testing complete   TODO
                throw new NucleusUserException("DELETE access denied");
/*
                // Delete the object with the supplied id pm.currentTransaction().begin();
                Object obj = pm.getObjectById(id);
                pm.deletePersistent(obj);
                pm.currentTransaction().commit();
 */
            }
        } catch (NucleusObjectNotFoundException ex) {
            try {
                JSONObject error = new JSONObject();
                error.put("exception", ex.getMessage());
                resp.getWriter().write(error.toString());
                resp.setStatus(400);
                resp.setHeader("Content-Type", "application/json");
                return;
            } catch (JSONException e) {
                // will not happen
            }
        } catch (NucleusUserException e) {
            try {
                JSONObject error = new JSONObject();
                error.put("exception", e.getMessage());
                resp.getWriter().write(error.toString());
                resp.setStatus(400);
                resp.setHeader("Content-Type", "application/json");
                return;
            } catch (JSONException e1) {
                // ignore
            }
        } catch (NucleusException e) {
            try {
                JSONObject error = new JSONObject();
                error.put("exception", e.getMessage());
                resp.getWriter().write(error.toString());
                resp.setStatus(500);
                resp.setHeader("Content-Type", "application/json");
                LOGGER_REST.error(e.getMessage(), e);
            } catch (JSONException e1) {
                // ignore
            }
        } finally {
            if (pm.currentTransaction().isActive()) {
                pm.currentTransaction().rollback();
            }
            pm.close();
        }
        resp.setContentLength(0);
        resp.setStatus(204); // created
    }

    protected void doHead(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        String servletID = Util.generateUUID();

        getPMF(req, servletID);
        String className = getNextTokenAfterSlash(req);
        ClassLoaderResolver clr = nucCtx.getClassLoaderResolver(RestServlet.class.getClassLoader());
        AbstractClassMetaData cmd = nucCtx.getMetaDataManager().getMetaDataForEntityName(className);
        try {
            if (cmd == null) {
                cmd = nucCtx.getMetaDataManager().getMetaDataForClass(className, clr);
            }
        } catch (ClassNotResolvedException ex) {
            resp.setStatus(404);
            return;
        }
        Object id = getId(req);
        if (id == null) {
            // no id provided
            try {
                // get the whole extent
                String queryString = "SELECT FROM " + cmd.getFullClassName();
                if (req.getQueryString() != null) {
                    // query by filter
                    queryString += " WHERE " + URLDecoder.decode(req.getQueryString(), "UTF-8");
                }
                PersistenceManager pm = pmf.getPersistenceManager();
                try {
                    pm.currentTransaction().begin();
                    Query query = pm.newQuery("JDOQL", queryString);
                    query.execute();
                    resp.setStatus(200);
                    pm.currentTransaction().commit();
                    query.closeAll();
                } finally {
                    if (pm.currentTransaction().isActive()) {
                        pm.currentTransaction().rollback();
                    }
                    pm.close();
                }
                return;
            } catch (NucleusUserException e) {
                resp.setStatus(400);
                return;
            } catch (NucleusException ex) {
                resp.setStatus(404);
                return;
            } catch (RuntimeException ex) {
                resp.setStatus(404);
                return;
            }
        }
        PersistenceManager pm = pmf.getPersistenceManager();
        try {
            pm.currentTransaction().begin();
            pm.getObjectById(id);
            resp.setStatus(200);
            pm.currentTransaction().commit();
            return;
        } catch (NucleusException ex) {
            resp.setStatus(404);
            return;
        } finally {
            if (pm.currentTransaction().isActive()) {
                pm.currentTransaction().rollback();
            }
            pm.close();
        }
    }

    boolean restAccessCheck(Object obj, HttpServletRequest req, JSONObject jsonobj) {
        // System.out.println(jsonobj.toString());
        // System.out.println(obj);
        // System.out.println(obj.getClass());
        boolean ok = true;
        Method restAccess = null;

        try {
            restAccess = obj.getClass().getMethod("restAccess",
                new Class[] { HttpServletRequest.class, JSONObject.class });
        } catch (NoSuchMethodException nsm) {
            System.out.println("no such method??????????");
            // nothing to do
        }
        if (restAccess == null) return true;                // if method doesnt exist, counts as good

        System.out.println("<<<<<<<<<< we have restAccess() on our object.... invoking!\n");
        // when .restAccess() is called, it should throw an exception to signal not allowed
        try {
            restAccess.invoke(obj, req, jsonobj);
        } catch (Exception ex) {
            ok = false;
            ex.printStackTrace();
            System.out.println("got Exception trying to invoke restAccess: " + ex.toString());
        }
        return ok;
    }

    Object filterResult(Object result)
    throws NucleusUserException {
        System.out.println("filterResult! thisRequest");
        System.out.println(thisRequest);
        Class cls = null;
        Object out = result;
        if (result instanceof Collection) {
            for (Object obj : (Collection)result) {
                cls = obj.getClass();
                if (cls.getName().equals("org.ecocean.User"))
                    throw new NucleusUserException(
                              "Cannot access org.ecocean.User objects at this time");
                else if (cls.getName().equals("org.ecocean.Role"))
                    throw new NucleusUserException(
                              "Cannot access org.ecocean.Role objects at this time");
                else if (cls.getName().equals("org.ecocean.Adoption"))
                    throw new NucleusUserException(
                              "Cannot access org.ecocean.Adoption objects at this time");
            }
        } else {
            cls = result.getClass();
            if (cls.getName().equals("org.ecocean.User"))
                throw new NucleusUserException(
                          "Cannot access org.ecocean.User objects at this time");
            else if (cls.getName().equals("org.ecocean.Role"))
                throw new NucleusUserException(
                          "Cannot access org.ecocean.Role objects at this time");
            else if (cls.getName().equals("org.ecocean.Adoption"))
                throw new NucleusUserException(
                          "Cannot access org.ecocean.Adoption objects at this time");
        }
        return out;
    }

    JSONObject convertToJson(HttpServletRequest req, Object obj, ExecutionContext ec) {
        JSONObject jobj = RESTUtils.getJSONObjectFromPOJO(obj, ec);
        Method sj = null;

        // call decorateJson on object
        if (req.getParameter("noDecorate") == null) {
            try {
                sj = obj.getClass().getMethod("decorateJson",
                    new Class[] { HttpServletRequest.class, JSONObject.class });
            } catch (NoSuchMethodException nsm) { // do nothing
                // System.out.println("i guess " + obj.getClass() + " does not have decorateJson() method");
            }
            if (sj != null) {
                // System.out.println("trying decorateJson on "+obj.getClass());
                try {
                    jobj = (JSONObject)sj.invoke(obj, req, jobj);
                    // System.out.println("decorateJson");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    // System.out.println("got Exception trying to invoke decorateJson: " + ex.toString());
                }
            }
        }
        // System.out.println(jobj.toString());

        // call sanitizeJson on object

        sj = null;
        try {
            sj = obj.getClass().getMethod("sanitizeJson",
                new Class[] { HttpServletRequest.class, JSONObject.class });
        } catch (NoSuchMethodException nsm) {     // do nothing
            // System.out.println("i guess " + obj.getClass() + " does not have sanitizeJson() method");
        }
        if (sj != null) {
            // System.out.println("trying sanitizeJson on "+obj.getClass());
            try {
                jobj = (JSONObject)sj.invoke(obj, req, jobj);
                // System.out.println("sanitizeJson result: " +jobj.toString());
            } catch (Exception ex) {
                ex.printStackTrace();
                // System.out.println("got Exception trying to invoke sanitizeJson: " + ex.toString());
            }
        }
        return jobj;
    }

    JSONArray convertToJson(HttpServletRequest req, Collection coll, ExecutionContext ec) {
        JSONArray jarr = new JSONArray();

        for (Object o : coll) {
            if (o instanceof Collection) {
                jarr.put(convertToJson(req, (Collection)o, ec));
            } else {      // TODO can it *only* be an JSONObject-worthy object at this point?
                jarr.put(convertToJson(req, o, ec));
            }
        }
        return jarr;
    }

/*
        //jo can be either JSONObject or JSONArray Object scrubJson(HttpServletRequest req, Object jo) throws JSONException {
   System.out.println("scrubJson");
            if (jo instanceof JSONArray) {
                JSONArray newArray = new JSONArray();
                JSONArray ja = (JSONArray)jo;
   System.out.println("- JSON Array " + ja);
                for (int i = 0 ; i < ja.length() ; i++) {
                    newArray.put(scrubJson(req, ja.getJSONObject(i)));
                }
                return newArray;

            } else {
                JSONObject jobj = (JSONObject)jo;
   System.out.println("- JSON Object " + jobj);
   System.out.println("- scrubJson reporting class=" + jobj.get("class").toString());
                return jobj;
            }
        }
 */
    void tryCompress(HttpServletRequest req, HttpServletResponse resp, Object jo, boolean useComp)
    throws IOException, JSONException {
// System.out.println("??? TRY COMPRESS ??");
        // String s = scrubJson(req, jo).toString();
        String s = jo.toString();

        if (!useComp || (s.length() < 3000)) {      // kinda guessing on size here, probably doesnt matter
            resp.getWriter().write(s);
        } else {
            resp.setHeader("Content-Encoding", "gzip");
            OutputStream o = resp.getOutputStream();
            GZIPOutputStream gz = new GZIPOutputStream(o);
            gz.write(s.getBytes());
            gz.flush();
            gz.close();
            o.close();
        }
    }

    private void getPMF(HttpServletRequest req, String servletID) {
        String context = "context0";

        context = ServletUtilities.getContext(req);
        ShepherdPMF.setShepherdState("RestServlet.class" + "_" + servletID, "new");
        pmf = ShepherdPMF.getPMF(context);
        this.nucCtx = ((JDOPersistenceManagerFactory)pmf).getNucleusContext();
        thisRequest = req;
    }
}
