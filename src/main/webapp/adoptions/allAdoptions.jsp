<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.*" %>
<%@ page import="javax.jdo.Extent" %>
<%@ page import="javax.jdo.Query" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ page import="org.joda.time.DateTime" %>
<%@ page import="org.joda.time.format.DateTimeFormatter" %>
<%@ page import="org.joda.time.format.ISODateTimeFormat" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Locale locale = new Locale(langCode);
  Properties props = ShepherdProperties.getProperties("allAdoptions.properties", langCode, context);
  //set up dateTime
  DateTime dt = new DateTime();
  DateTimeFormatter fmt = ISODateTimeFormat.date();
  String strOutputDateTime = fmt.print(dt);

  Shepherd myShepherd = new Shepherd(context);
  String currentSort = "nosort";
  List<String> displaySortList = new ArrayList<String>();
  if (request.getParameter("sort") != null) {
    currentSort = request.getParameter("sort");
    if (request.getParameter("sort").startsWith("name")) {
      displaySortList.add("sort.name");
    }
  }
  String displaySort = "";
  if (!displaySortList.isEmpty()) {
    displaySort = StringUtils.collateStrings(displaySortList, props, "sort.%s", null, null, ",");
    displaySort = StringUtils.format(locale, props.getProperty("sortedBy"), displaySort);
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
                <h1><%=props.getProperty("title")%></h1>
              </td>
            </tr>
            <tr>
              <th class="caption" colspan="4"><%=StringUtils.format(locale, props.getProperty("subtitle"), totalCount)%></th>
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
                href="http://<%=CommonConfiguration.getURLLocation(request)%>/adoptions/allAdoptions.jsp?start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Next</a>
                <%
                  }
                  if ((lowCount - 10) >= 1) {
                %> | <a
                href="http://<%=CommonConfiguration.getURLLocation(request)%>/adoptions/allAdoptions.jsp?start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Previous</a>
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


                %> <%=StringUtils.format(locale, props.getProperty("viewing"), lowCount, highCount, displaySort)%>
              </td>
            </tr>

            <tr class="lineitem">
              <td bgcolor="#99CCFF" class="lineitem">&nbsp;</td>
              <td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=props.getProperty("column.number")%></strong></td>
              <td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=props.getProperty("column.name")%></strong></td>
              <td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=props.getProperty("column.type")%></strong></td>
              <td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=props.getProperty("column.adopted")%></strong></td>
              <td width="60" align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=props.getProperty("column.startDate")%></strong></td>
              <td width="60" align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=props.getProperty("column.endDate")%></strong></td>
            </tr>
            <%
              Iterator allAdoptions;
              int total = totalCount;
              int iterTotal = totalCount;
              query = ServletUtilities.setRange(query, iterTotal, highCount, lowCount);

              allAdoptions = myShepherd.getAllAdoptionsWithQuery(query);

              int countMe = 0;
              while (allAdoptions.hasNext()) {
                countMe++;
                Adoption enc = (Adoption) allAdoptions.next();
            %>
            <tr class="lineitems">
              <td width="102" height="60" class="lineitems"><a
                href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptions/adoption.jsp?individual=<%=enc.getID()%>"><img
                src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=(enc.getID()+"/thumb.jpg")%>"
                width="100" height="75" alt="<%=props.getProperty("adopterPhoto")%>" border="0"/></a></td>

              <td class="lineitems"><a
                href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptions/adoption.jsp?number=<%=enc.getID()%>"><%=enc.getID()%>
              </a></td>
              <td class="lineitems"><%=enc.getAdopterName()%></td>
              <td class="lineitems"><%=enc.getAdoptionType()%></td>
              <td class="lineitems"><%=enc.getMarkedIndividual()%></td>
              <td class="lineitems"><%=enc.getAdoptionStartDate()%></td>
              <td class="lineitems"><%=enc.getAdoptionEndDate()%></td>
            </tr>
            <%
              }
            %>


            <tr class="paging">
              <td align="left">
                <%
                  if (highCount < totalCount) {%> <a
                href="http://<%=CommonConfiguration.getURLLocation(request)%>/adoptions/allAdoptions.jsp?start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Next</a>
                <%
                  }
                  if ((lowCount - 10) >= 0) {
                %> | <a
                href="http://<%=CommonConfiguration.getURLLocation(request)%>/adoptions/allAdoptions.jsp?start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Previous</a>
                <%}%>
              </td>
              <td colspan="6" align="right"><%=StringUtils.format(locale, props.getProperty("viewing"), lowCount, highCount, displaySort)%>

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
