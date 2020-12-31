if(userProjects==null || userProjects.size()<1){
  %>
  <h4>You don't have any projects yet</h4>
  <%
}else{
  %>
  <table class="row clickable-row hoverRow project-style">
    <thead>
        <tr>
          <th class="project-style"><%=props.getProperty("ProjectName") %></th>
          <th class="project-style"><%=props.getProperty("PercentAnnotations") %></th>
          <th class="project-style"><%=props.getProperty("NumEncounters") %></th>
        </tr>
    </thead>
    <tbody>
  <%
  for(int j=0; j<userProjects.size(); j++){
    if(userProjects.size()>0){
      %>
        <tr onclick="window.location='<%=urlLoc%>/projects/project.jsp?id=<%=userProjects.get(j).getId()%>'" class="project-style">
          <td class="clickable-row"><%=userProjects.get(j).getResearchProjectName()%></td>
          <td class="clickable-row"><%=userProjects.get(j).getPercentWithIncrementalIds()%> %</td>
          <td class="clickable-row"><%=userProjects.get(j).getEncounters().size()%></td>
        </tr>
      <%
    }
  }
}
}
