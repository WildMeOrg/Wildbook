package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.ecocean.*;


//Set alternateID for this encounter/sighting
public class EncounterSetDynamicProperty extends HttpServlet {
  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {

    doPost(request, response);
  }
  
  private void setDateLastModified(Encounter enc){

    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }
    

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    Shepherd myShepherd=new Shepherd();
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;
    
    //-------------------------------
    //set a dynamic property

    if ((request.getParameter("number")!=null)&&(request.getParameter("name")!=null)) {
        myShepherd.beginDBTransaction();
        Encounter changeMe=myShepherd.getEncounter(request.getParameter("number"));
        
        String name=request.getParameter("name");
        String newValue="null";
        String oldValue="null";
        
        if(changeMe.getDynamicPropertyValue(name)!=null){
          oldValue=changeMe.getDynamicPropertyValue(name);
        }
        
        
        if ((request.getParameter("value")!=null)&&(!request.getParameter("value").equals(""))){
          newValue=request.getParameter("value");
        }
        

              
              
              
              try{
              
                  if(newValue.equals("null")){
                    changeMe.removeDynamicProperty(name);
                    changeMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Removed dynamic property <em>"+name+"</em>. The old Value was <em>"+oldValue+".</em></p>");
                  }
                  else{
                    changeMe.setDynamicProperty(name, newValue);
                    changeMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Set dynamic property <em>"+name+"</em> from <em>"+oldValue+"</em> to <em>"+newValue+"</em>.</p>");
                    
                  }

                
              
              
              }catch(Exception le){
                System.out.println("Hit locked exception.");
                locked=true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
          
              }
              
              
              
              if(!locked){
                setDateLastModified(changeMe);
                myShepherd.commitDBTransaction();
                out.println(ServletUtilities.getHeader());
                
                if(!newValue.equals("")){
                  out.println("<strong>Success:</strong> Encounter dynamic property "+name+" has been updated from <i>"+oldValue+"</i> to <i>"+newValue+"</i>.");
                }
                else{
                  out.println("<strong>Success:</strong> Encounter dynamic property "+name+" was removed. The old value was <i>"+oldValue+"</i>.");
                  
                }
                
                out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter "+request.getParameter("number")+"</a></p>\n");
                out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
                    out.println("<p><a href=\"allIndividuals.jsp\">View all marked individuals</a></font></p>");
                    out.println(ServletUtilities.getFooter());
                String message="Encounter "+request.getParameter("number")+" dynamic property "+name+" has been updated from \""+oldValue+"\" to \""+newValue+"\".";
                ServletUtilities.informInterestedParties(request.getParameter("number"), message);
              }else {
                out.println(ServletUtilities.getHeader());
                out.println("<strong>Failure:</strong> Encounter dynamic property "+name+" was NOT updated because another user is currently modifying this reconrd. Please try to reset the value again in a few seconds.");
                out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter "+request.getParameter("number")+"</a></p>\n");
                out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
                    out.println("<p><a href=\"allIndividuals.jsp\">View all marked individuals</a></font></p>");
                    out.println(ServletUtilities.getFooter());
                
              }
            }   
              
            else {
              out.println(ServletUtilities.getHeader());
              out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
              out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
              out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
                  out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
                  out.println(ServletUtilities.getFooter());  
                
              }
            
    
                out.close();
                myShepherd.closeDBTransaction();
              }

    
    
  

    }
  
  
