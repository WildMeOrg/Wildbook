<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
java.io.*,java.util.*, java.io.FileInputStream, 

org.json.*,
org.ecocean.identity.*,
org.joda.time.DateTime,
org.ecocean.media.*,
java.lang.reflect.Method,
java.security.NoSuchAlgorithmException,
java.security.InvalidKeyException,
org.ecocean.social.*,
org.datanucleus.api.jdo.JDOPersistenceManager,
org.ecocean.cache.*,
org.ecocean.acm.AcmUtil,
org.ecocean.ia.plugin.*,
java.util.zip.GZIPOutputStream,
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%

response.setHeader("Access-Control-Allow-Origin", "*"); 

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("taskQueryTest.jsp.jsp");

long current = System.currentTimeMillis();
long last24 = current-(1000*60*60*24*2);


String filter="SELECT count(this) FROM org.ecocean.ia.Task where id !=null";


//ID tasks
String filter3="SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) ";

//shell ID tasks
//ID tasks
//String filter3a="SELECT count(this) FROM org.ecocean.ia.Task where parent == null && children.size()>0";




//String filter3="SELECT count(this) FROM org.ecocean.ia.Task where parameters.indexOf('\"skipIdent\":true')==-1 ";

//Hotspotter tasks
String filter4="SELECT count(this) FROM org.ecocean.ia.Task where children == null && parameters.indexOf('\"sv_on\"')>-1";

//PieTwo tasks
String filter5="SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('PieTwo')>-1";

//PieOne tasks
String filter5a="SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('Pie')>-1 && parameters.indexOf('PieTwo')==-1";

//CurvRankTwoDorsal tasks
String filter5b="SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('CurvRankTwoDorsal')>-1";

//CurvRankTwoFluke tasks
String filter5c="SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('CurvRankTwoFluke')>-1";

//OC_WDTW
String filter5d="SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('OC_WDTW')>-1";

//Findfindr
String filter5e="SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('Finfindr')>-1";

//deepsense
String filter5f="SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.toLowerCase().indexOf('deepsense')>-1";


//detection tasks
String filter6="SELECT count(this) FROM org.ecocean.ia.Task where parameters.indexOf('ibeis.detection') > -1  && (children == null || (children.contains(child) && child.parameters.indexOf('ibeis.detection') == -1)) VARIABLES org.ecocean.ia.Task child";

//parent tasks
String filter7="SELECT count(this) FROM org.ecocean.ia.Task where parent==null && parameters.indexOf('ibeis.detection') > -1";
String filter7a="SELECT count(this) FROM org.ecocean.ia.Task where parent==null && parameters.indexOf('ibeis.identification') > -1";


//tasks neither detection nor ID
String filter8="SELECT count(this) FROM org.ecocean.ia.Task where parameters.indexOf('ibeis.detection') == -1 && parameters.indexOf('ibeis.identification') == -1 && parameters.indexOf('pipeline_root') == -1";


try {
	
	Long myValue=new Long(0);
	Query q2=myShepherd.getPM().newQuery(filter);
	myValue=(Long) q2.execute();
	q2.closeAll();
	%>
	<p>Number tasks: <%=myValue %></p>
	<ol>
	<%
	
	Long myValue6=new Long(0);
	Query q6=myShepherd.getPM().newQuery(filter6);
	myValue6=(Long) q6.execute();
	q6.closeAll();
	%>
	<li>Number detection tasks: <%=myValue6 %></li>
	

	<%
	Long myValue3=new Long(0);
	Query q3=myShepherd.getPM().newQuery(filter3);
	myValue3=(Long) q3.execute();
	q3.closeAll();  
	
	%>
	<li>Number ID tasks: <%=myValue3 %></li>
	<%
	Long myValue8=new Long(0);
	Query q8=myShepherd.getPM().newQuery(filter8);
	myValue8=(Long) q8.execute();
	q8.closeAll();
	%>
	<li>Number non-detection and non-ID shell tasks: <%=myValue8 %></li>
	
	</ol>

	
	<p>Let's breakdown our ID tasks total: <%=myValue3 %></p>
	<ol>
	<%
	

	

	
	Long myValue4=new Long(0);
	Query q4=myShepherd.getPM().newQuery(filter4);
	myValue4=(Long) q4.execute();
	q4.closeAll();  
	%>
	<li>HotSpotter tasks: <%=myValue4 %></li>
	<%
	
	Long myValue5=new Long(0);
	Query q5=myShepherd.getPM().newQuery(filter5);
	myValue5=(Long) q5.execute();
	q5.closeAll();  
	%>
	<li>PieTwo tasks: <%=myValue5 %></li>
	
	<%
    Long myValue5a=new Long(0);
	Query q5a=myShepherd.getPM().newQuery(filter5a);
	myValue5a=(Long) q5a.execute();
	q5a.closeAll();  
	%>
	<li>PieOne tasks: <%=myValue5a %></li>
	
	<%
	    Long myValue5b=new Long(0);
	Query q5b=myShepherd.getPM().newQuery(filter5b);
	myValue5b=(Long) q5b.execute();
	q5b.closeAll();  
	%>
	<li>CurvRankTwoDorsal tasks: <%=myValue5b %></li>
	
		<%
	    Long myValue5c=new Long(0);
	Query q5c=myShepherd.getPM().newQuery(filter5c);
	myValue5c=(Long) q5c.execute();
	q5c.closeAll();  
	%>
	<li>CurvRankTwoFluke tasks: <%=myValue5c %></li>
	
			<%
	    Long myValue5d=new Long(0);
	Query q5d=myShepherd.getPM().newQuery(filter5d);
	myValue5d=(Long) q5d.execute();
	q5d.closeAll();  
	%>
	<li>OC_WDTW tasks: <%=myValue5d %></li>
	
				<%
	    Long myValue5e=new Long(0);
	Query q5e=myShepherd.getPM().newQuery(filter5e);
	myValue5e=(Long) q5e.execute();
	q5e.closeAll();  
	%>
	<li>Findfindr tasks: <%=myValue5e %></li>
	
					<%
	    Long myValue5f=new Long(0);
	Query q5f=myShepherd.getPM().newQuery(filter5f);
	myValue5f=(Long) q5f.execute();
	q5f.closeAll();  
	%>
	<li>deepsense tasks: <%=myValue5f %></li>
	
	</ol>
	<%

	
	Long myValue9=new Long(0);
	Query q9=myShepherd.getPM().newQuery(filter+" && created > "+last24);
	myValue9=(Long) q9.execute();
	q9.closeAll();  
	%>
	<p>Tasks last 48 hours: <%=myValue9 %></p>
	<ol>
	<%
	Long myValue10=new Long(0);
	Query q10=myShepherd.getPM().newQuery(filter6.replaceAll("VARIABLES"," && created > "+last24+" VARIABLES"));
	myValue10=(Long) q10.execute();
	q10.closeAll();  
	%>
	<li>Detection tasks last 48 hours: <%=myValue10 %></li>
		<%
	Long myValue11=new Long(0);
	Query q11=myShepherd.getPM().newQuery(filter3+" && created > "+last24);
	myValue11=(Long) q11.execute();
	q11.closeAll();  
	%>
	<li>ID tasks last 48 hours: <%=myValue11 %></li>
			<%
	Long myValue12=new Long(0);
	Query q12=myShepherd.getPM().newQuery(filter8+" && created > "+last24);
	myValue12=(Long) q12.execute();
	q12.closeAll();  
	%>
	<li>Non-detect and non-ID tasks last 48 hours: <%=myValue12 %></li>
	</ol>
	
	
	<p>ID task breakdown by species<p/>
	<ul>
	<%
	IAJsonProperties iaConfig = new IAJsonProperties();
	List<Taxonomy> taxes=iaConfig.getAllTaxonomies(myShepherd);
	
	for(Taxonomy tax:taxes){
		
		List<String> iaClasses=iaConfig.getValidIAClassesIgnoreRedirects(tax);
		if(iaClasses!=null && iaClasses.size()>0){
		
			String allowedIAClasses="&& ( ";
			for(String str:iaClasses){
				if(allowedIAClasses.indexOf("iaClass")==-1){
					allowedIAClasses+=" annot.iaClass == '"+str+"' ";
				}
				else{
					allowedIAClasses+=" || annot.iaClass == '"+str+"' ";
				}
			}		
			allowedIAClasses+=" )";
			String speciesFilter=filter3+" && objectAnnotations.contains(annot) "+allowedIAClasses+" VARIABLES org.ecocean.Annotation annot";
			System.out.println(speciesFilter);
			Long myValue13=new Long(0);
			Query q13=myShepherd.getPM().newQuery(speciesFilter);
			myValue13=(Long) q13.execute();
			q13.closeAll();  
			
			%>
			<li><%=tax.getScientificName() %>: <%=myValue13 %></li>
			<%
		}
	}
	
	%>
	</ul>
	<%

}
catch(Exception e){
	e.printStackTrace();
}
finally{

	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}
%>