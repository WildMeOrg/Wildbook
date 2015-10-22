<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">

<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,java.util.Vector,java.util.Properties,org.ecocean.genetics.*,java.util.*,java.net.URI, org.ecocean.*, java.io.*" %>



<html>
<head>



  <%
  String context="context0";
  context = ServletUtilities.getContext(request);
	String rootWebappPath = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootWebappPath);
/*
File webappsDir = new File(rootWebappPath).getParentFile();
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
File encounterDir = new File(encountersDir, num);


    //let's load encounterSearch.properties
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
    Properties map_props = new Properties();
    //map_props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/exportSearchResults.properties"));
    map_props=ShepherdProperties.getProperties("exportSearchResults.properties", langCode, context);
*/

    
    //get our Shepherd
    Shepherd myShepherd = new Shepherd(context);

		String cmdWatermark = CommonConfiguration.getProperty("imageWatermarkCommand", context);
		String cmdResize = CommonConfiguration.getProperty("imageResizeCommand", context);

		String message = "must have <b>imageWatermarkCommand</b> and <b>imageResizeCommand</b> set in common configuration";

		String scriptFile = "/tmp/redo.sh";

		String script = "stamp=`/bin/date +%s`\necho datestamp=$stamp\n\n";
//note: we currently do not know the source of the thumb.jpg, so we punt and pick 0th image.  :(
		if ((cmdWatermark != null) && (cmdResize != null) && !cmdWatermark.equals("") && !cmdResize.equals("")) {
			Iterator<Encounter> it = myShepherd.getAllEncounters();
			int total = 0;
			while (it.hasNext()) {
				Encounter enc = it.next();
				List<SinglePhotoVideo> spvs = enc.getImages();
				total++;
				script += "\n\n#################### encounter " + enc.getEncounterNumber() + "\necho " + total + ". " + enc.getEncounterNumber() + "\n";
				String epath = enc.dir(baseDir);
				int count = 0;
				for (SinglePhotoVideo spv : spvs) {
					String imageSource = epath + File.separator + spv.getFilename();
					script += "\n## image " + count + "\n";
					if (count == 0) {
						script += "cp " + epath + File.separator + "thumb.jpg " + epath + File.separator + "thumb-$stamp.jpg\n";
						script += cmdResize.replaceAll("%width", "100").replaceAll("%height", "75").replaceAll("%imagesource", imageSource).replaceAll("%imagetarget", epath + File.separator + "thumb.jpg") + "\n";
					}

					script += cmdWatermark.replaceAll("%width", "250").replaceAll("%height", "200").replaceAll("%imagesource", imageSource).replaceAll("%imagetarget", epath + File.separator + spv.getDataCollectionEventID() + ".jpg") + "\n";
					script += cmdResize.replaceAll("%width", "1024").replaceAll("%height", "768").replaceAll("%imagesource", imageSource).replaceAll("%imagetarget", epath + File.separator + spv.getDataCollectionEventID() + "-mid.jpg") + "\n";
					count++;
				}
				script += "\nchown tomcat:tomcat " + epath + File.separator + "*\n";
			}
			script = "echo total count = " + total + "\n" + script;


			try {
				PrintWriter scriptOut = new PrintWriter(scriptFile);
				scriptOut.println(script);
				scriptOut.close();
				message = "wrote <b>" + scriptFile + "</b>";

			} catch (Exception ex) {
				message = "could not write <b>" + scriptFile + "</b>: " + ex.toString();
			}

//message += "<xmp>" + script + "</xmp>";
		}

    		
  %>

  <title><%=CommonConfiguration.getHTMLTitle(context)%>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description" content="<%=CommonConfiguration.getHTMLDescription(context)%>"/>
  <meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords(context)%>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context)%>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context)%>" rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon(context)%>"/>


    <style type="text/css">
      body {
        margin: 0;
        padding: 10px 20px 20px;
        font-family: Arial;
        font-size: 16px;
      }



      #map {
        width: 600px;
        height: 400px;
      }

    </style>
  

<style type="text/css">
  #tabmenu {
    color: #000;
    border-bottom: 2px solid black;
    margin: 12px 0px 0px 0px;
    padding: 0px;
    z-index: 1;
    padding-left: 10px
  }

  #tabmenu li {
    display: inline;
    overflow: hidden;
    list-style-type: none;
  }

  #tabmenu a, a.active {
    color: #DEDECF;
    background: #000;
    font: bold 1em "Trebuchet MS", Arial, sans-serif;
    border: 2px solid black;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #FFFFFF;
    color: #000000;
    border-bottom: 2px solid #FFFFFF;
  }

  #tabmenu a:hover {
    color: #ffffff;
    background: #7484ad;
  }

  #tabmenu a:visited {
    color: #E8E9BE;
  }

  #tabmenu a.active:hover {
    background: #7484ad;
    color: #DEDECF;
    border-bottom: 2px solid #000000;
  }
  
  
</style>
  


    
  </head>
 <body onunload="GUnload()">
 <div id="wrapper">
 <div id="page">
<jsp:include page="../header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
 <div id="main">
 
	<p><%=message%></p>
 
 <jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>
