<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.Adoption,org.ecocean.CommonConfiguration,org.ecocean.Shepherd,org.ecocean.servlet.ServletUtilities, org.joda.time.DateTime, org.joda.time.format.DateTimeFormatter, org.joda.time.format.ISODateTimeFormat, javax.jdo.Extent,javax.jdo.Query,java.util.Iterator, java.util.Properties" %>


<%
String context="context0";
context=ServletUtilities.getContext(request);
  //set up dateTime
  DateTime dt = new DateTime();
  DateTimeFormatter fmt = ISODateTimeFormat.date();
  String strOutputDateTime = fmt.print(dt);

//setup our Properties object to hold all properties
 // Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("allAdoptions.jsp");
  String currentSort = "nosort";
  String displaySort = "";
  if (request.getParameter("sort") != null) {
    currentSort = request.getParameter("sort");
    if (request.getParameter("sort").startsWith("name")) {
      displaySort = " sorted by Name";
    }
  }
  currentSort = "&amp;sort=" + currentSort;
  int lowCount = 1, highCount = 10;
  if ((request.getParameter("start") != null) && (request.getParameter("end") != null)) {
    lowCount = (new Integer(request.getParameter("start"))).intValue();
    highCount = (new Integer(request.getParameter("end"))).intValue();
    if (highCount > (lowCount + 9)) {
      highCount = lowCount + 9;
    }
  }


%>

    <jsp:include page="../header.jsp" flush="true" />

        <div class="container maincontent">
        
          <%

            myShepherd.beginDBTransaction();
//build a query
            Extent encClass = myShepherd.getPM().getExtent(Adoption.class, true);
            Query query = myShepherd.getPM().newQuery(encClass);
            String order = "adoptionStartDate descending";
            query.setOrdering(order);

            int totalCount = 0;

            totalCount = myShepherd.getNumAdoptions();

          %>
          <table id="results" border="0">
            <tr>
              <td colspan="4">
                <h1>View All Adoptions</h1>
              </td>
            </tr>
            <tr>
              <th class="caption" colspan="4">Below are some of the <strong><%=totalCount%>
              </strong>
                adoptions currently stored in the database. Click <strong>Next</strong>
                to view the next set of encounters. Click <strong>Previous</strong> to
                see the previous set of encounters.
                </td>
            </tr>
          </table>


          <table id="results" border="0" width="100%">
            <tr class="paging">
              <td align="left">
                <%

                  String rejectsLink = "";
                  String unapprovedLink = "";
                  String userLink = "";

                  if (highCount < totalCount) {%> <a
                href="//<%=CommonConfiguration.getURLLocation(request)%>/adoptions/allAdoptions.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Next</a>
                <%
                  }
                  if ((lowCount - 10) >= 1) {
                %> | <a
                href="//<%=CommonConfiguration.getURLLocation(request)%>/adoptions/allAdoptions.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Previous</a>
                <%}%>
              </td>
              <td colspan="6" align="right">
                <%
                  String startNum = "1";
                  String endNum = "10";


                  if (request.getParameter("start") != null) {
                    startNum = request.getParameter("start");
                  }
                  if ((request.getParameter("end") != null) && (!request.getParameter("end").equals("99999"))) {
                    endNum = request.getParameter("end");
                  } else if ((request.getParameter("end") != null) && (request.getParameter("end").equals("99999"))) {
                    endNum = (new Integer(totalCount)).toString();
                  }

                  if (((new Integer(endNum)).intValue()) > totalCount) {
                    endNum = (new Integer(totalCount)).toString();
                  }


                %> Viewing: <%=lowCount%> - <%=highCount%><%=displaySort%>
              </td>
            </tr>

            <tr class="lineitem">
              <td bgcolor="#99CCFF" class="lineitem">&nbsp;</td>
              <td align="left" valign="top" bgcolor="#99CCFF" class="lineitem">
                <strong>Number</strong></td>

              <td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong>Name</strong>
              </td>
              <td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong>Type</strong>
              </td>
              <td align="left" valign="top" bgcolor="#99CCFF" class="lineitem">
                <strong>Adopted</strong></td>
              <td width="60" align="left" valign="top" bgcolor="#99CCFF"
                  class="lineitem"><strong>Start date</strong></td>
              <td width="60" align="left" valign="top" bgcolor="#99CCFF"
                  class="lineitem"><strong>End date</strong></td>

            </tr>
            <%

              Iterator<Adoption> allAdoptions;

              int total = totalCount;
              int iterTotal = totalCount;
              query = ServletUtilities.setRange(query, iterTotal, highCount, lowCount);

              allAdoptions = myShepherd.getAllAdoptionsWithQuery(query);

              int countMe = 0;
              while (allAdoptions.hasNext()) {
                countMe++;
                Adoption enc = allAdoptions.next();


            %>
            <tr class="lineitems">
              <td width="102" height="60" class="lineitems"><a
                href="//<%=CommonConfiguration.getURLLocation(request) %>/adoptions/adoption.jsp?individual=<%=enc.getID()%>"><img
                src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=(enc.getID()+"/thumb.jpg")%>"
                width="100" height="75" alt="adopter photo" border="0"/></a></td>

              <td class="lineitems"><a
                href="//<%=CommonConfiguration.getURLLocation(request) %>/adoptions/adoption.jsp?number=<%=enc.getID()%>"><%=enc.getID()%>
              </a></td>
              <td class="lineitems"><%=enc.getAdopterName()%>
              </td>
              <td class="lineitems"><%=enc.getAdoptionType()%>
              </td>
              <td class="lineitems"><%=enc.getMarkedIndividual()%>
              </td>
              <td class="lineitems"><%=enc.getAdoptionStartDate()%>
              </td>
              <td class="lineitems"><%=enc.getAdoptionEndDate()%>
              </td>


            </tr>
            <%
              }
            %>


            <tr class="paging">
              <td align="left">
                <%
                  if (highCount < totalCount) {%> <a
                href="//<%=CommonConfiguration.getURLLocation(request)%>/adoptions/allAdoptions.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Next</a>
                <%
                  }
                  if ((lowCount - 10) >= 0) {
                %> | <a
                href="//<%=CommonConfiguration.getURLLocation(request)%>/adoptions/allAdoptions.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Previous</a>
                <%}%>
              </td>
              <td colspan="6" align="right">Viewing: <%=lowCount%> - <%=highCount%><%=displaySort%>

              </td>
            </tr>
          </table>

          <%
            query.closeAll();

            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            encClass = null;
            query = null;
            myShepherd = null;
          %>
        

        </div>

    <jsp:include page="../footer.jsp" flush="true"/>
