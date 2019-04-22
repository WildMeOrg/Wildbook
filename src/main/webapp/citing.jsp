<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.apache.shiro.crypto.*,org.apache.shiro.util.*,org.apache.shiro.crypto.hash.*,org.ecocean.*,org.ecocean.servlet.ServletUtilities, java.util.Properties,java.util.ArrayList" %>


<%

String context="context0";
context=ServletUtilities.getContext(request);

  

  //Shepherd myShepherd = new Shepherd(context);
  //myShepherd.setAction("userAgreement.jsp");
  
  	

//setup our Properties object to hold all properties

  //language setup
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

  Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/overview.properties"));
  props = ShepherdProperties.getProperties("overview.properties", langCode,context);



%>

<jsp:include page="header.jsp" flush="true"/>

  <style type="text/css">
    <!--


    .style2 {
      font-size: x-small;
      color: #000000;
    }


h3 {
    border-bottom: solid 2px #888;
}

ul.example li {
    font-size: 0.9em;
    color: #888;
}

    -->
  </style>




<div class="container maincontent">

<h1>How to Cite GiraffeSpotter and Collaborative Data</h1>

<p>
GiraffeSpotter requests you to contact the data provider(s) for use of any and all
data to be used in any publication, product, or commercial application.  Any such use must adhere to the terms of the
<a href="userAgreement.jsp">GiraffeSpotter User Agreement</a>.
</p>

<p>
By using GiraffeSpotter the user agrees to the following:

<ol>

<li>
Not to use data contained in GiraffeSpotter in any publication, product, or commercial application without prior written consent of the original data provider.
</li>

<li>
To cite both the data provider(s) and GiraffeSpotter appropriately after approval of use is obtained.

<ul>

<li>
Collaborative datasets should list all data contributors in descending order of quantity of encounters contributed. The year should be listed as the year when the dataset was pooled and downloaded.
</li>

<li>
Example citation for a dataset:

    <ul class="example">
    <li>S. Smith, R.E. Searcher, and B. Iology. (2019). Collaborative dataset from: GiraffeSpotter.org. Consolidated and downloaded: June 15, 2016.</li>
    </ul>

</li>

<li>
General citation for GiraffeSpotter:

    <ul class="example">
    <li>S. Smith, R.E. Searcher, and B. Iology. (2019) GiraffeSpotter: a cloud-based photo-identification analysis tools for giraffe research. Accessible at: https://giraffespotter.org</li>
    </ul>

</li>

</ul>

<li>
To forward the citation of any publication / report that made use of the data / tools provided by GiraffeSpotter for inclusion in our list of references. Submit here: info(at)giraffespotter.org
</li>

<li>
Not to hold GiraffeSpotter or the original data providers liable for errors in the data. While we have made every effort to ensure the quality of the database, we cannot guarantee the accuracy of these datasets.
</li>

</ol>


</div>

    <jsp:include page="footer.jsp" flush="true"/>
