<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*" %>



<%

String context="context0";
context=ServletUtilities.getContext(request);


//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
%>

    <jsp:include page="../header.jsp" flush="true" />

  
<div class="container maincontent">
     

      <h1>Library Administration</h1>
     
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1">Restore a Deleted Encounter</font></p>

            <form name="restoreEncounter" method="post"
                  action="../ResurrectDeletedEncounter">
              <p>Encounter number: <input name="number" type="text" id="number"
                                          size="20" maxlength="50"> <br> <input name="Restore"
                                                                                type="submit"
                                                                                id="Restore"
                                                                                value="Restore"></p>
            </form>
          </td>
        </tr>
      </table>
      <p></p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1">Update Email Address of Submitter or
              Photographer Across the Entire Library</font></p>

            <form name="updateEmail" method="post" action="../UpdateEmailAddress">
              <p>Old Email Address: <input name="findEmail" type="text"
                                           id="findEmail" size="25" maxlength="50">

              <p>New Email Address: <input name="replaceEmail" type="text"
                                           id="replaceEmail" size="25" maxlength="50"> <br> <input
                name="Update" type="submit" id="Update" value="Update"></p>
            </form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1">Swap old location code for new across
              all encounters</font></p>

            <p class="style1"><em><strong>WARNING</strong></em>: This changes
              the location code for encounters from an old to a new value. This is a
              non-trivial change and should only be done after significant
              deliberation.</p>

            <form name="massSwapLocCode" method="post" action="../MassSwapLocationCode">
              <p>Old location code: <input name="oldLocCode" type="text"
                                           id="oldLocCode" size="10" >

              <p>New location code: <input name="newLocCode" type="text"
                                           id="newLocCode" size="10" > <br/>
                <br> <input name="Update" type="submit" id="Update"
                            value="Update"></p>
            </form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1">Expose all approved encounters to the
              GBIF. </font></p>

            <form name="exposeGBIF" method="post" action="../MassExposeGBIF">

              <input name="Expose to GBIF" type="submit" id="Expose to GBIF"
                     value="Expose to GBIF">
              </p></form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1">Set the location code for all encounters
              matching a string</font></p>

            <p class="style1"><em><strong>WARNING</strong></em>: This changes
              the location code for encounters from an old to a new value. This is a
              non-trivial change and should only be done after significant
              deliberation.</p>

            <form name="massSetLocationCodeFromLocationString" method="post"
                  action="../MassSetLocationCodeFromLocationString">
              <p>Text string to match (case insensitive): <input
                name="matchString" type="text" id="matchString" size="50"
                maxlength="999">

              <p>Location code to assign: <input name="locCode" type="text"
                                                 id="locCode" size="10" maxlength="10"> <br/>
                <br> <input name="Update" type="submit" id="Update"
                            value="Update"></p>
            </form>
          </td>
        </tr>
      </table>



      
</div>


      <jsp:include page="../footer.jsp" flush="true"/>



