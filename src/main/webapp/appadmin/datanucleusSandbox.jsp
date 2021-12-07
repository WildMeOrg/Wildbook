<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.Util,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, 
java.io.FileNotFoundException, org.datanucleus.api.rest.orgjson.JSONObject, 
org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, 
java.util.Vector, java.util.Iterator, java.lang.NumberFormatException,
java.io.IOException,
java.io.UnsupportedEncodingException,
java.net.URLDecoder,
java.util.Collection,
java.util.List,
java.util.StringTokenizer,
org.ecocean.ShepherdPMF,
org.ecocean.Util,
java.lang.reflect.Method,
javax.jdo.JDOHelper,
javax.jdo.PersistenceManager,
javax.jdo.PersistenceManagerFactory,
javax.jdo.Query,
javax.servlet.ServletConfig,
javax.servlet.ServletException,
javax.servlet.http.HttpServlet,
javax.servlet.http.HttpServletRequest,
javax.servlet.http.HttpServletResponse,
org.datanucleus.ClassLoaderResolver,
org.datanucleus.ExecutionContext,
org.datanucleus.PersistenceNucleusContext,
org.datanucleus.api.jdo.JDOPersistenceManager,
org.datanucleus.api.jdo.JDOPersistenceManagerFactory,
org.datanucleus.api.rest.orgjson.JSONArray,
org.datanucleus.api.rest.orgjson.JSONException,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.exceptions.ClassNotResolvedException,
org.datanucleus.exceptions.NucleusException,
org.datanucleus.exceptions.NucleusObjectNotFoundException,
org.datanucleus.exceptions.NucleusUserException,
org.datanucleus.identity.IdentityUtils,
org.datanucleus.metadata.AbstractClassMetaData,
org.datanucleus.metadata.IdentityType,
org.datanucleus.util.NucleusLogger,
org.datanucleus.state.ObjectProvider,
org.datanucleus.store.fieldmanager.FieldManager,
java.util.zip.*,
java.io.OutputStream,
org.datanucleus.ClassLoaderResolver,
org.datanucleus.ExecutionContext,
org.datanucleus.api.rest.orgjson.JSONArray,
org.datanucleus.api.rest.orgjson.JSONException,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.exceptions.NucleusException,
org.datanucleus.metadata.AbstractClassMetaData,
org.datanucleus.metadata.AbstractMemberMetaData,
org.datanucleus.metadata.RelationType,
org.datanucleus.store.fieldmanager.AbstractFieldManager,
org.datanucleus.api.jdo.metadata.JDOMetaDataManager,
org.datanucleus.api.rest.RESTUtils,
org.datanucleus.api.rest.fieldmanager.ToJSONFieldManager
"%>



<%!
private String getNextTokenAfterSlash(HttpServletRequest req)
{
    String path = req.getRequestURI().substring(req.getContextPath().length() + req.getServletPath().length());
    StringTokenizer tokenizer = new StringTokenizer(path, "/");
    return tokenizer.nextToken();
}
%>

<%!

boolean restAccessCheck(Object obj, HttpServletRequest req, JSONObject jsonobj) {
  System.out.println(jsonobj.toString());
  System.out.println(obj);
  System.out.println(obj.getClass());
              boolean ok = true;
              Method restAccess = null;
              try {
                  restAccess = obj.getClass().getMethod("restAccess", new Class[] { HttpServletRequest.class, JSONObject.class });
              } catch (NoSuchMethodException nsm) {
  System.out.println("no such method??????????");
                  //nothing to do
              }
              if (restAccess == null) return true;  //if method doesnt exist, counts as good

  System.out.println("<<<<<<<<<< we have restAccess() on our object.... invoking!\n");
              //when .restAccess() is called, it should throw an exception to signal not allowed
              try {
                  restAccess.invoke(obj, req, jsonobj);
              } catch (Exception ex) {
                  ok = false;
                  ex.printStackTrace();
                  System.out.println("got Exception trying to invoke restAccess: " + ex.toString());
              }
              return ok;
}
%>

<%!
Object filterResult(Object result, HttpServletRequest req) throws NucleusUserException {
System.out.println("filterResult! thisRequest");
System.out.println(req);
    Class cls = null;
    Object out = result;
    if (result instanceof Collection) {
        for (Object obj : (Collection)result) {
            cls = obj.getClass();
            if (cls.getName().equals("org.ecocean.User")) throw new NucleusUserException("Cannot access org.ecocean.User objects at this time");
            else if (cls.getName().equals("org.ecocean.Role")) throw new NucleusUserException("Cannot access org.ecocean.Role objects at this time");
            
        }
    } else {
        cls = result.getClass();
        if (cls.getName().equals("org.ecocean.User")) throw new NucleusUserException("Cannot access org.ecocean.User objects at this time");
        else  if (cls.getName().equals("org.ecocean.Role")) throw new NucleusUserException("Cannot access org.ecocean.Role objects at this time");
    }
    return out;
}
%>

<%!
JSONObject convertToJson(HttpServletRequest req, Object obj, ExecutionContext ec, StringBuffer sb, PersistenceNucleusContext nucCtx) {
	sb.append("\n\nconvertToJson(non-Collection) trying class=" + obj.getClass()+"\n\n");
    sb.append("\n\nconvert2jsonObj:115\n\n");
	JSONObject jobj = getJSONObjectFromPOJO(obj, ec,sb);
    if(true)return jobj;
    Method sj = null;
    try {
        sj = obj.getClass().getMethod("sanitizeJson", new Class[] { HttpServletRequest.class, JSONObject.class });
    } catch (NoSuchMethodException nsm) { //do nothing
//System.out.println("i guess " + obj.getClass() + " does not have sanitizeJson() method");
    }
    if (sj != null) {
//System.out.println("trying sanitizeJson!");
        try {
            jobj = (JSONObject)sj.invoke(obj, req, jobj);
        } catch (Exception ex) {
          ex.printStackTrace();
          System.out.println("got Exception trying to invoke sanitizeJson: " + ex.toString());
        }
    }
    return jobj;
}
%>

<%!
JSONArray convertToJson(HttpServletRequest req, Collection coll, ExecutionContext ec, StringBuffer sb, PersistenceNucleusContext nucCtx) {
	 sb.append("\n\nconvert2jsonColl:115\n\n");
	JSONArray jarr = new JSONArray();
    for (Object o : coll) {
        if (o instanceof Collection) {
            jarr.put(convertToJson(req, (Collection)o, ec, sb, nucCtx));
        } else {  //TODO can it *only* be an JSONObject-worthy object at this point?
            jarr.put(convertToJson(req, o, ec,sb,nucCtx));
        }
    }
    return jarr;
}
%>

<%!

private Object getId(HttpServletRequest req, PersistenceNucleusContext nucCtx)
{
    ClassLoaderResolver clr = nucCtx.getClassLoaderResolver(RestServlet.class.getClassLoader());
    String path = req.getRequestURI().substring(req.getContextPath().length() + req.getServletPath().length());
    StringTokenizer tokenizer = new StringTokenizer(path, "/");
    String className = tokenizer.nextToken();
    AbstractClassMetaData cmd = nucCtx.getMetaDataManager().getMetaDataForClass(className, clr);

    String id = null;
    if (tokenizer.hasMoreTokens())
    {
        // "id" single-field specified in URL
        id = tokenizer.nextToken();
        if (id == null || cmd == null)
        {
            return null;
        }

        Object identity = RESTUtils.getIdentityForURLToken(cmd, id, nucCtx);
        if (identity != null)
        {
            return identity;
        }
    }

    // "id" must have been specified in the content of the request
    try
    {
        if (id == null && req.getContentLength() > 0)
        {
            char[] buffer = new char[req.getContentLength()];
            req.getReader().read(buffer);
            id = new String(buffer);
        }
    }
    catch (IOException e)
    {
        throw new RuntimeException(e);
    }

    if (id == null || cmd == null)
    {
        return null;
    }

    try
    {
        // assume it's a JSONObject
        id = URLDecoder.decode(id, "UTF-8");
        JSONObject jsonobj = new JSONObject(id);
        return RESTUtils.getNonPersistableObjectFromJSONObject(jsonobj, clr.classForName(cmd.getObjectidClass()), nucCtx);
    }
    catch (JSONException ex)
    {
        // not JSON syntax
    }
    catch (UnsupportedEncodingException e)
    {
        //LOGGER_REST.error("Exception caught when trying to determine id", e);
    }

    return id;
}
%>



<%!
private String getJSON(PersistenceManager pm,HttpServletRequest req, HttpServletResponse resp, PersistenceManagerFactory pmf, PersistenceNucleusContext nucCtx,ExecutionContext ec,String queryString, StringBuffer sb){
	   
	resp.setHeader("Access-Control-Allow-Origin", "*");
	        // Retrieve any fetch group that needs applying to the fetch
	        String fetchParam = req.getParameter("fetch");
	        sb.append("success:244\n");
	        
	        Object jsonobj=null;
	        boolean collection=true;

	        //String encodings = req.getHeader("Accept-Encoding");
	        //boolean useCompression = ((encodings != null) && (encodings.indexOf("gzip") > -1));

	        try
	        {
	            //String token = getNextTokenAfterSlash(req);
	            if (true)
	            {
	                // GET "/query?the_query_details" or GET "/jdoql?the_query_details" where "the_query_details" is "SELECT FROM ... WHERE ... ORDER BY ..."
	                //String queryString = URLDecoder.decode(req.getQueryString(), "UTF-8");
	                
	                String servletID=Util.generateUUID();
	                //ShepherdPMF.setShepherdState("RestServlet.class"+"_"+servletID, "new");
	                sb.append("success:262");
	                
	                try
	                {
	                    //pm.currentTransaction().begin();
	                    ShepherdPMF.setShepherdState("RestServlet.class"+"_"+servletID, "begin");
	                    

	                    Query query = pm.newQuery("JDOQL", queryString);
	                    //query.addExtension("datanucleus.query.results.cached", "true");
	                    //query.addExtension("datanucleus.query.resultCache.validateObjects", "false");
	                    //query.getFetchPlan().setFetchSize(-1);
	                    //query.getFetchPlan().setMaxFetchDepth(100);
	                    
	                    
	                    
	                    
	                    
	                    if (fetchParam != null)
	                    {
	                        query.getFetchPlan().addGroup(fetchParam);
	                    }
	                    Object result = filterResult(query.execute(),req);
	                    sb.append("success:276");
	                    if (result instanceof Collection)
	                    {
	                        jsonobj = convertToJson(req, (Collection)result, ec,sb,nucCtx);
	                        sb.append("success:267");
	                        //JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)result,
	                            //((JDOPersistenceManager)pm).getExecutionContext());
	                        //tryCompress(req, resp, jsonobj, useCompression);
	                    }
	                    else
	                    {
	                        jsonobj = convertToJson(req, result, ec,sb,nucCtx);
	                        //JSONObject jsonobj = RESTUtils.getJSONObjectFromPOJO(result,
	                            //((JDOPersistenceManager)pm).getExecutionContext());
	                        //tryCompress(req, resp, jsonobj, useCompression);
	                    }
	                    query.closeAll();

	                    //pm.currentTransaction().rollback();
	                    ShepherdPMF.setShepherdState("RestServlet.class"+"_"+servletID, "commit");
	                    sb.append("success:295");
	                }
	                finally
	                {
	                    if (pm.currentTransaction().isActive())
	                    {
	                        //pm.currentTransaction().rollback();
	                        ShepherdPMF.setShepherdState("RestServlet.class"+"_"+servletID, "rollback");
	                        
	                    }
	                    //pm.close();
	                    //ShepherdPMF.setShepherdState("RestServlet.class"+"_"+servletID, "close");
	                    ShepherdPMF.removeShepherdState("RestServlet.class"+"_"+servletID);
	                    
	                    
	                }
	               
	            }
	      
	            else
	            {
	                // GET "/{candidateclass}..."
	                String className = "";
	                ClassLoaderResolver clr = nucCtx.getClassLoaderResolver(RestServlet.class.getClassLoader());
	                AbstractClassMetaData cmd = nucCtx.getMetaDataManager().getMetaDataForEntityName(className);
	                try
	                {
	                    if (cmd == null)
	                    {
	                        cmd = nucCtx.getMetaDataManager().getMetaDataForClass(className, clr);
	                    }
	                }
	                catch (ClassNotResolvedException ex)
	                {
	                    JSONObject error = new JSONObject();
	                    error.put("exception", ex.getMessage());
	                  
	                }

	                Object id = getId(req, nucCtx);
	                if (id == null)
	                {
	                    // Find objects by type or by query
	                    try
	                    {
	                    	sb.append("success:340");
	                        // get the whole extent for this candidate
	                        /*
	                        String queryString = "SELECT FROM " + cmd.getFullClassName();
	                        if (req.getQueryString() != null)
	                        {
	                            // query by filter for this candidate
	                            queryString += " WHERE " + URLDecoder.decode(req.getQueryString(), "UTF-8");
	                        }
	                        */
	                        
	                        
	                        //PersistenceManager pm = pmf.getPersistenceManager();
	                        if (fetchParam != null)
	                        {
	                            pm.getFetchPlan().addGroup(fetchParam);
	                        }
	                        try
	                        {
	                            //pm.currentTransaction().begin();
	                            Query query = pm.newQuery("JDOQL", queryString);
	                            List result = (List)filterResult(query.execute(), req);
	                            jsonobj = convertToJson(req, result, ec,sb,nucCtx);
	                            jsonobj = getJSONArrayFromCollection(result,ec,sb);
	                            //tryCompress(req, resp, jsonobj, useCompression);
	                            query.closeAll();
	                            sb.append("success:365");
	                            //pm.currentTransaction().commit();
	                        }
	                        finally
	                        {
	                            if (pm.currentTransaction().isActive())
	                            {
	                                //pm.currentTransaction().rollback();
	                            }
	                            //pm.close();
	                        }
	                       
	                    }
	                    catch (NucleusUserException e)
	                    {
	                        JSONObject error = new JSONObject();
	                        error.put("exception", e.getMessage());
	                        sb.append("error:382");
	                  
	                    }
	                    catch (NucleusException ex)
	                    {
	                        JSONObject error = new JSONObject();
	                        error.put("exception", ex.getMessage());
	                        sb.append("error:389");
	                  
	                    }
	                    catch (RuntimeException ex)
	                    {
	                        // errors from the google appengine may be raised when running queries
	                        JSONObject error = new JSONObject();
	                        error.put("exception", ex.getMessage());
	                        sb.append("error:397");
	                    }
	                }

	                // GET "/{candidateclass}/id" - Find object by id
	                //PersistenceManager pm = pmf.getPersistenceManager();
	                if (fetchParam != null)
	                {
	                    pm.getFetchPlan().addGroup(fetchParam);
	                }
	                try
	                {
	                    pm.currentTransaction().begin();
	                    Object result = filterResult(pm.getObjectById(id),req);
	                    jsonobj = convertToJson(req, result, ec,sb,nucCtx);
	                    //JSONObject jsonobj = RESTUtils.getJSONObjectFromPOJO(result,
	                        //((JDOPersistenceManager)pm).getExecutionContext());
	                    //tryCompress(req, resp, jsonobj, useCompression);
	                    //resp.getWriter().write(jsonobj.toString());
	                    resp.setHeader("Content-Type","application/json");
	                    //pm.currentTransaction().commit();
	                    //return;
	                }
	                catch (NucleusObjectNotFoundException ex)
	                {
	                    //resp.setContentLength(0);
	                    //resp.setStatus(404);
	                    sb.append("error:423");
	                    //return;
	                }
	                catch (NucleusException ex)
	                {
	                    JSONObject error = new JSONObject();
	                    error.put("exception", ex.getMessage());
	                    //resp.getWriter().write(error.toString());
	                    //resp.setStatus(404);
	                    //resp.setHeader("Content-Type", "application/json");
	                    //return;
	                    sb.append("error:433");
	                }
	                finally
	                {
	                    if (pm.currentTransaction().isActive())
	                    {
	                        //pm.currentTransaction().rollback();
	                    }
	                    //pm.close();
	                }
	            }
	        }
	        catch (Exception e)
	        {
	            try
	            {
	                JSONObject error = new JSONObject();
	                error.put("exception", e.getMessage());
	               // resp.getWriter().write(error.toString());
	                //resp.setStatus(404);
	                //resp.setHeader("Content-Type", "application/json");
	                sb.append("success:365"+e.getMessage());
	            }
	            catch (JSONException e1)
	            {
	                e1.printStackTrace();
	            }
	        }
	        
	        if(jsonobj!=null){
	        	if(collection){
	        		sb.append("462"+((JSONArray)jsonobj).toString());
	        	}
	        	else{
	        		//jsonobj=(JSONObject)jsonobj;
	        		sb.append("466"+((JSONObject)jsonobj).toString());
	        	}
	
	        }
	        sb.append( "nothing!");
	        return sb.toString();
	        
	    }
%>

<%!
/**
 * Method to convert the provided POJO into its equivalent JSONObject.
 * @param coll Collection of POJOs
 * @param ec ExecutionContext
 * @return The JSONObject
 */
public static JSONArray getJSONArrayFromCollection(final Collection coll, ExecutionContext ec, StringBuffer sb)
{
    JSONArray arr = new JSONArray();
    int i = 0;
    for (Object elem : coll)
    {
        try
        {
            arr.put(i++, getJSONObjectFromPOJO(elem, ec, sb));
        }
        catch (JSONException e)
        {
        }
    }
    return arr;
}
%>

<%!

/**
 * Method to convert the provided POJO into its equivalent JSONObject.
 * @param obj The object
 * @param ec ExecutionContext
 * @return The JSONObject
 */
public static JSONObject getJSONObjectFromPOJO(final Object obj, ExecutionContext ec, StringBuffer sb)
{
    ClassLoaderResolver clr = ec.getClassLoaderResolver();
    AbstractClassMetaData cmd = ec.getMetaDataManager().getMetaDataForClass(obj.getClass(), clr);

    // Create JSONObject
    JSONObject jsonobj = new JSONObject();
    try
    {
        jsonobj.put("class", cmd.getFullClassName());
        sb.append("getJSONObjectFromPOJO: "+cmd.getFullClassName()+"\n\n");
        if (cmd.getIdentityType() == IdentityType.DATASTORE)
        {
            jsonobj.put("_id", IdentityUtils.getTargetKeyForDatastoreIdentity(ec.getApiAdapter().getIdForObject(obj)));
        }
        if (ec.getApiAdapter().getVersionForObject(obj) != null)
        {
            jsonobj.put("_version", ec.getApiAdapter().getVersionForObject(obj));
        }
    }
    catch (JSONException e)
    {
    }

    // Copy all FetchPlan fields into the object
    ObjectProvider op = ec.findObjectProvider(obj);
    FieldManager fm = new ToJSONFieldManager(jsonobj, cmd, ec);
    
    sb.append("\nFetch plan for class send to ObjectProvider: "+ec.getFetchPlan().toString()+" "+ec.getFetchPlan().getFetchPlanForClass(cmd)+"\n\n");
    sb.append("\nFetch depth: "+ec.getFetchPlan().getMaxFetchDepth()+" "+"\n\n");
    
    //op.loadUnloadedFields();
    op.provideFields(ec.getFetchPlan().getFetchPlanForClass(cmd).getMemberNumbers(), fm);

    return jsonobj;
}

%>


<%

String context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

//Properties dnProperties=ShepherdProperties.getProperties("jdoconfig.properties", "");

Properties dnProperties=new Properties();
dnProperties.put("datanucleus.ConnectionDriverName", "org.postgresql.Driver");
dnProperties.put("datanucleus.ConnectionURL", "jdbc:postgresql://localhost:5432/whaleshark");
dnProperties.put("javax.jdo.PersistenceManagerFactoryClass", "org.datanucleus.api.jdo.JDOPersistenceManagerFactory");
dnProperties.put("datanucleus.ConnectionUserName", "wildbook");
dnProperties.put("datanucleus.ConnectionPassword", "sp0tz");
dnProperties.put("datanucleus.schema.autoCreateAll", "true");
dnProperties.put("datanucleus.NontransactionalRead", "true");
dnProperties.put("datanucleus.Multithreaded", "true");
dnProperties.put("datanucleus.RestoreValues", "true");
dnProperties.put("datanucleus.storeManagerType", "rdbms");
dnProperties.put("datanucleus.maxFetchDepth", "-1");
dnProperties.put("datanucleus.cache.collections.lazy", "false");
dnProperties.put("datanucleus.connectionPoolingType", "dbcp2");
dnProperties.put("datanucleus.connectionPool.maxIdle", "10");
dnProperties.put("datanucleus.connectionPool.minIdle", "5");
dnProperties.put("datanucleus.connectionPool.maxActive", "30");
dnProperties.put("datanucleus.connectionPool.maxWait", "7");
dnProperties.put("datanucleus.connectionPool.testSQL", "SELECT 1");
dnProperties.put("datanucleus.connectionPool.timeBetweenEvictionRunsMillis", "240000");
dnProperties.put("datanucleus.cache.collections", "true");
dnProperties.put("datanucleus.cache.collections.lazy", "false");
//dnProperties.put("datanucleus.query.useFetchPlan", "false");





PersistenceManagerFactory pmf=JDOHelper.getPersistenceManagerFactory(dnProperties);
PersistenceManager pm=pmf.getPersistenceManager();

PersistenceNucleusContext nucCtx=((JDOPersistenceManagerFactory)pmf).getNucleusContext();

//Create a FetchGroup on the PMF called "TestGroup" for MyClass
//FetchGroup grp1 = pmf.getFetchGroup(MarkedIndividual.class, "default");
//grp1.addMember("encounters");
//FetchGroup grp2 = pmf.getFetchGroup(org.ecocean.Encounter.class, "default");
//grp2.addMember("annotations");
//grp1.setRecursionDepth("encounters", 5);


//grp.addMember("encounters").addMember("field2");
//pm.getFetchPlan().addGroup("annots");
//pm.getFetchPlan().clearGroups().addGroup("TestGroup1");
pm.getFetchPlan().setMaxFetchDepth(-1);
//pm.getFetchPlan().setGroup(FetchGroup.ALL);


//Add this group to the fetch plan (using its name)




%>

<html>
<head>
<title>Datnucleus Test</title>

</head>


<body>

<p>maxFetchDepth <%=pm.getFetchPlan().getMaxFetchDepth() %></p>
<p>Fetch groups<%=pm.getFetchPlan().getGroups().toString() %></p>

<%



//myShepherd.beginDBTransaction();


String queryString="SELECT FROM org.ecocean.MarkedIndividual WHERE individualID == \'KOREA-001\'";

%>
<h3>Datanucleus Caching Test</h3>
<%
pm.currentTransaction().begin();
pm.setIgnoreCache(true);
pm.getFetchPlan().setFetchSize(FetchPlan.FETCH_SIZE_GREEDY);

final MarkedIndividual tempShark = ((org.ecocean.MarkedIndividual) (pm.getObjectById(pm.newObjectIdInstance(MarkedIndividual.class, "KOREA-001"), true)));
/*
Vector encs=tempShark.getEncounters();
int numEncs=encs.size();
for(int i=0;i<numEncs;i++){
	Encounter enc=(Encounter)encs.get(i);
	List<Annotation> annots=enc.getAnnotations();
}
*/

//((Encounter)tempShark.getEncounters().get(0)).getAnnotations();


try {

	
	ExecutionContext ec = ((JDOPersistenceManager)pm).getExecutionContext();
	StringBuffer sb=new StringBuffer(); 
	%>

	<p>Straight POJO: <%=(getJSONObjectFromPOJO(tempShark,ec,sb)).toString() %></p>
	<%

	  
	String theJSON = getJSON(pm,request, response, pmf, nucCtx,ec,queryString,sb);
	
	%>
	
	<p><%=theJSON.replaceAll("\n","<br />") %></p>
	<%


}
catch(Exception e){
	%>
	<p>Some kind of nasty error.<br><%=e.getMessage() %></br></p>
	<%
	e.printStackTrace();
}
finally{
	//myShepherd.rollbackDBTransaction();
	//myShepherd.closeDBTransaction();
	pm.currentTransaction().rollback();
	pm.close();
	pmf.close();
}

%>

</body>
</html>
