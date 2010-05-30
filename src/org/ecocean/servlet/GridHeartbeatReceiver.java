package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import org.ecocean.grid.*;
import java.io.IOException;
import java.io.PrintWriter;


public class GridHeartbeatReceiver extends HttpServlet {
	

	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
  }

	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
		doPost(request, response);
	}
		

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		
		PrintWriter out = null;
		String statusText="received";
		
		//get the gridManager and populate needed values
		GridManager gm=GridManagerFactory.getGridManager();
		String supportedAppletVersion=gm.getSupportedAppletVersion();
		
		if((request.getParameter("version")!=null)&&(request.getParameter("version").equals(supportedAppletVersion))&&(request.getParameter("nodeIdentifier")!=null)){
			gm.processHeartbeat(request);
		}
		else{
			statusText="rejected";
		}
		
		
		try{
			//setup the heartbeat response
			response.setContentType ("text/plain"); 
			out = response.getWriter();
			out.println (statusText);
			out.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		
	}

		
		
	

		}
	
	
