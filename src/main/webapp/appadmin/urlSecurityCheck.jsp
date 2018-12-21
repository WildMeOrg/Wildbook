
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, 
java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,
org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, 
java.lang.NumberFormatException,
javax.xml.parsers.DocumentBuilder,javax.xml.parsers.DocumentBuilderFactory,
org.w3c.dom.*,
javax.xml.xpath.*,java.util.regex.*,
org.apache.commons.io.FileUtils,
org.apache.commons.io.filefilter.*
"%>

<%!
public String getSecurityMappings(String servletName,String paramValues){
	
	String servletSecurity="";
	
	Matcher m = Pattern.compile("\\/("+servletName+").*(\n|\r)").matcher(paramValues);
	
	while(m.find()){
		servletSecurity+=m.group()+" ";
	}
	
	return servletSecurity;
	
}

%>

<jsp:include page="../header.jsp" flush="true"/>


<style>
table {
  border-collapse: collapse;
  width: 100%;
}

th, td {
  text-align: left;
  padding: 8px;
}

tr:nth-child(even) {background-color: #f2f2f2;}
</style>


<script>
function hideMapped () {
	//alert("Calling hideMapped!");
	$("[name='mapped']").hide();

}
function showMapped () {
	//alert("Calling showMapped!");
	$("[name='mapped']").show();

}

$(document).ready(function() {
    //set initial state.
    $('#hideMapped').val(this.checked);
    hideMapped();

    $('#hideMapped').change(function() {
        if(this.checked) {
            hideMapped();
        }
        else{
        	showMapped();
        }
        $('#hideMapped').val(this.checked);        
    });
});

</script>

<div class="container maincontent">

<h1>URL Security Review</h1>

<p><input type="checkbox" name="hideChecked" id="hideMapped" checked="checked"> Hide secured mapped web.xml entries</p>


<h2>Servlets</h2>

<table>
	<thead>
		<tr>
			<th>Servlet Name</th>
			<th>Class</th>
			<th>Mapping</th>
			<th>Security Rules</th>
		</tr>
	</thead>
<%



try{
	
	//START SERVLETS
	
	//first, load web.xml
	InputStream input = getServletContext().getResourceAsStream("/WEB-INF/web.xml");
	
	//read XML to get servlets
	DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

	Document doc = builder.parse(input);
	XPath xpath = XPathFactory.newInstance().newXPath();

	//XPathExpression expr = xpath.compile("/web-app/servlet/servlet-name[text()='MyServlet']");
	XPathExpression expr = xpath.compile("/web-app/servlet");
	
	String paramValues="";
	
	NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
	if(nl!=null){
		int numNodes=nl.getLength();
		for(int i=0;i<numNodes;i++){
			Node n=nl.item(i);
			NodeList children=n.getChildNodes();
			if(children!=null){
				String servletName="";
				String servletClass="";
				String servletMapping="";
				String servletSecurity="";
				
				String mappedElementName="mapped";
				
				int numChildren=children.getLength();
				for(int j=0;j<numChildren;j++){
					Node child=children.item(j);
					if(child.getNodeName().equals("servlet-name")){
							servletName=child.getTextContent();
							
							//and let's get its mapping
							XPathExpression mapExpr = xpath.compile("/web-app/servlet-mapping[servlet-name/text()='"+servletName+"']");
							NodeList mappingNames = (NodeList) mapExpr.evaluate(doc, XPathConstants.NODESET);
							int numMappings=mappingNames.getLength();
							for(int k=0;k<numMappings;k++){
								Node map=mappingNames.item(k);
								NodeList mapChildren=map.getChildNodes();
								int numMapChildren=mapChildren.getLength();
								for(int l=0;l<numMapChildren;l++){
									Node mapChild=mapChildren.item(l);
									if(mapChild.getNodeName().equals("url-pattern")){
										servletMapping+=mapChild.getTextContent()+"<br>";
									}
								}
							}
							
							//let's get its security rules
							XPathExpression secExpr = xpath.compile("/web-app/filter[filter-name/text()='ShiroFilter']/init-param[param-name/text()='config']/param-value");
							NodeList secNodes = (NodeList) secExpr.evaluate(doc, XPathConstants.NODESET);
							//String paramValues="";
							if(secNodes.getLength()>0){
								//get the first node
								Node paramValue=secNodes.item(0);
								paramValues=paramValue.getTextContent();
								
								//now regex for servletName
								//String[] tokens=paramValues.split("\\/("+servletName+").*]");
								servletSecurity = getSecurityMappings(servletName,paramValues);
								
							}
							
							
							
							
					}
					if(child.getNodeName().equals("servlet-class")){servletClass=child.getTextContent();}
				}
				
				if(servletSecurity.equals("")){mappedElementName="unmapped";}
			
			%>
			<tr name="<%=mappedElementName %>">
				<td><%=servletName %></td><td><%=servletClass %></td><td><%=servletMapping %></td><td><%=servletSecurity %></td>
			</tr>
			
			
			
			
			<%
				} //end if
			}
	} //end if not null
	%>
	</table>
	
	<%
	
	//END SERVLETS
	
	//START JSP FILES
	//first, load web.xml
	File rootDir = new File(getServletContext().getRealPath("/"));
	ArrayList<File> jspFiles = new ArrayList(FileUtils.listFilesAndDirs(
						rootDir,
						new SuffixFileFilter("jsp"),
						FileFilterUtils.directoryFileFilter()
	));
	
	%>
	
	
	<h2>JSP Files</h2>
	<table>
	 <thead>
		<tr>
			<th>JSP File Name</th>
			<th>Security Rules</th>
		</tr>
	</thead>
	<%
	int numJSPFiles=jspFiles.size();
	for(int n=0;n<numJSPFiles;n++){
		String mappedElementName="mapped";
		File jFile=jspFiles.get(n);
		String servletSecurity=getSecurityMappings(jFile.getName(),paramValues);
		
		//see if it's parent is mapped
		if(servletSecurity.equals(""))servletSecurity=getSecurityMappings(jFile.getParentFile().getName(),paramValues);
		
		//if it's still unmapped,it's unmapped totally!
		if(servletSecurity.equals("")){mappedElementName="unmapped";}
		//ignore WEB-INF diretory, otherwise print
		if((jFile.getAbsolutePath().indexOf("WEB-INF")==-1)&&(jFile.getAbsolutePath().indexOf("META-INF")==-1)){
			%>
			<tr name="<%=mappedElementName %>"><td><%=jFile.getAbsolutePath() %></td><td><%=servletSecurity %></td></tr>
			<%
		}
	}
	
	
	%>
	
	</table>
	
	<%
	
	
}
catch(Exception e){
	//myShepherd.rollbackDBTransaction();
	%>
	<p>Reported error: <%=e.getMessage() %> <%=e.getStackTrace().toString() %></p>
	<%
	e.printStackTrace();
}
finally{
	//myShepherd.rollbackDBTransaction();
	//myShepherd.closeDBTransaction();

}

%>


</div>
</body>
</html>
