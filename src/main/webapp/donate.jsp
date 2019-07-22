<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,java.util.Properties, org.ecocean.servlet.ServletUtilities" %>
<%

  //setup our Properties object to hold all properties
  
  String langCode = ServletUtilities.getLanguageCode(request);

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);
  
  

  
  String context=ServletUtilities.getContext(request);

%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">

<h1 class="intro">Donate</h1>

<p>If you represent a foundation and think our endeavor fits within the mission of any of your grants, please contact us at kitizenscience@gmail.com, as we would love to hear about you. </p>

<p>We also welcome and are grateful for support from individuals.  Kitizen Science is an official tax-exempt 501(c)3 nonprofit organization, and your donations are tax-deductible.  Our federal tax ID is 83-4432917. </p>

<p><form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
<input type="hidden" name="cmd" value="_s-xclick" />
<input type="hidden" name="hosted_button_id" value="ZUPNYWQ7YRGGE" />
<input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" title="PayPal - The safer, easier way to pay online!" alt="Donate with PayPal button" />
<img alt="" border="0" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1" />
</form>
</p>


</div>


