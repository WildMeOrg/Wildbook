package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.*;
import org.json.JSONArray;
import org.json.JSONObject;
 public class PublicationNote extends javax.servlet.http.HttpServlet implements javax.servlet.Servlet {

	public PublicationNote() {
		super();
	}   	
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}  	
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		String Remarks = request.getParameter("Remarks").trim();
		JSONArray rtn = new JSONArray();
		String context=ServletUtilities.getContext(request);
		Shepherd myShepherd=new Shepherd(context);
		myShepherd.beginDBTransaction();
		Publication user = null ;
		try {
		  Publication pub= new Publication();
		  pub.setPUBLICATION_REMARK(Remarks);
		   user = myShepherd.storePulicatioRemarks(pub);
	    myShepherd.commitDBTransaction();
		}
		catch(Exception e){
		  myShepherd.rollbackDBTransaction();
		}
     JSONObject oj = new JSONObject();
     oj.put("remarks", user.getPUBLICATION_REMARK());
     rtn.put(oj);
     PrintWriter out = response.getWriter();
     response.setContentType("text/json");
     out.println(rtn.toString());
     out.close();
	}   	  	    
}
