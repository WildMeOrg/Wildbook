<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.ListIterator,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, org.apache.commons.lang3.StringEscapeUtils" %>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode=ServletUtilities.getLanguageCode(request);
	
	String context="context0";
	context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
	
	//set up the file input stream
	//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
	//props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/whoweare.properties"));
	props=ShepherdProperties.getProperties("whoweare.properties", langCode, context);

        request.setAttribute("pageTitle", "Kitizen Science &gt; Who We Are");
%>

<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">


<h2><img src="images/cat_browntabby.jpg" alt="" width="375" height="242" hspace="10" vspace="10" align="right" />Who We Are</h2>
<p>Follow us on social media: <a href="https://www.facebook.com/kitizenscience/" target="_blank">Facebook</a>, <a href="https://twitter.com/kitizenscience" target="_blank">Twitter</a>, <a href="https://www.instagram.com/kitizenscience/" target="_blank">Instagram</a>. </p>
<p>Join our mailing list for news and updates:</p>

<div align="center">
  <table width="400" border="0" align="left" cellpadding="0" cellspacing="0">
    <tr>
      <td>
  <!-- Begin Mailchimp Signup Form -->
  <link href="//cdn-images.mailchimp.com/embedcode/classic-10_7.css" rel="stylesheet" type="text/css">
  <style type="text/css">
	#mc_embed_signup{background:#fff; clear:left; width:500px;}
	/* Add your own Mailchimp form style overrides in your site stylesheet or in this style block.
	   We recommend moving this block and the preceding CSS link to the HEAD of your HTML file. */
</style>
  <div id="mc_embed_signup">
  <form action="https://gmail.us20.list-manage.com/subscribe/post?u=abd67dc212c12b5072c70a518&amp;id=bd017987f4" method="post" id="mc-embedded-subscribe-form" name="mc-embedded-subscribe-form" class="validate" target="_blank" novalidate>
    <div id="mc_embed_signup_scroll">
      
  <div class="mc-field-group">
    <label for="mce-EMAIL"><span class="style2">Email Address </span></label>
    <div align="center">
      <input type="email" value="" name="EMAIL" class="required email" id="mce-EMAIL">
      </div>
  </div>
	  <div id="mce-responses" class="clear">
	    <div class="response" id="mce-error-response" style="display:none"></div>
		  <div class="response" id="mce-success-response" style="display:none"></div>
	  </div>    <!-- real people should not fill this in and expect good things - do not remove this or risk form bot signups-->
      <div style="position: absolute; left: -5000px;" aria-hidden="true"><input type="text" name="b_abd67dc212c12b5072c70a518_bd017987f4" tabindex="-1" value=""></div>
      <div class="clear">
        <div align="center">
          <input type="submit" value="Subscribe" name="subscribe" id="mc-embedded-subscribe" class="button">
        </div>
      </div>
      </div>
  </form>
  </div>
  <script type='text/javascript' src='//s3.amazonaws.com/downloads.mailchimp.com/js/mc-validate.js'></script><script type='text/javascript'>(function($) {window.fnames = new Array(); window.ftypes = new Array();fnames[0]='EMAIL';ftypes[0]='email';fnames[3]='ADDRESS';ftypes[3]='address';fnames[4]='PHONE';ftypes[4]='phone';}(jQuery));var $mcj = jQuery.noConflict(true);</script>
  <!--End mc_embed_signup-->      </td>
    </tr>
  </table>
</div>


<%= NoteField.buildHtmlDiv("32ce5f32-fb60-491b-a537-787fa806b3c3", request, myShepherd) %>

</div>

<jsp:include page="footer.jsp" flush="true" />

