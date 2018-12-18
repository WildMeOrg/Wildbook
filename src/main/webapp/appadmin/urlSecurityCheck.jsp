
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
javax.xml.xpath.*,java.util.regex.*
"%>

<%

//String context="context0";
//context=ServletUtilities.getContext(request);
//Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>URL Security Check</title>
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
</head>


<body>
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
	
	//first, load web.xml
	InputStream input = getServletContext().getResourceAsStream("/WEB-INF/web.xml");
	
	//read XML to get servlets
	DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

	Document doc = builder.parse(input);
	XPath xpath = XPathFactory.newInstance().newXPath();

	//XPathExpression expr = xpath.compile("/web-app/servlet/servlet-name[text()='MyServlet']");
	XPathExpression expr = xpath.compile("/web-app/servlet");
	
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
							if(secNodes.getLength()>0){
								//get the first node
								Node paramValue=secNodes.item(0);
								String paramValues=paramValue.getTextContent();
								
								//now regex for servletName
								//String[] tokens=paramValues.split("\\/("+servletName+").*]");
								Matcher m = Pattern.compile("\\/("+servletName+").*]").matcher(paramValues);
								
								while(m.find()){
									servletSecurity+=m.group()+" ";
								}
								
							}
							
							/*
							  <filter>
        <filter-name>ShiroFilter</filter-name>
        <filter-class>org.apache.shiro.web.servlet.IniShiroFilter</filter-class>
        <init-param>
            <param-name>config</param-name>
            <param-value>
                #See Shiro API http://shiro.apache.org/static/current/apidocs/org/apache/shiro/web/servlet/IniShiroFilter.html

							*/
							
							
							
					}
					if(child.getNodeName().equals("servlet-class")){servletClass=child.getTextContent();}
				}
			
			%>
			<tr>
				<td><%=servletName %></td><td><%=servletClass %></td><td><%=servletMapping %></td><td><%=servletSecurity %></td>
			</tr>
			
			
			
			
			<%
				} //end if
			}
	} //end if not null
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

</table>

</body>
</html>
