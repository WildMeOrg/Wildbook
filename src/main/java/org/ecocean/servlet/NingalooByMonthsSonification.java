package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;

import javax.jdo.*;

import java.lang.StringBuffer;


//robust design
public class NingalooByMonthsSonification extends HttpServlet{

	//Shepherd myShepherd;


	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
    	//myShepherd=new Shepherd();
  	}


	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    	doPost(request, response);
	}



	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{

		//set the response
		response.setContentType("text/html");

		//vector of dates for the study
		Vector dates=new Vector();

		String context="context0";
    context=ServletUtilities.getContext(request);
    
    Shepherd myShepherd = new Shepherd(context);


		PrintWriter out = response.getWriter();
		try {


		int startYear=(new Integer(request.getParameter("startYear"))).intValue();
		int startMonth=(new Integer(request.getParameter("startMonth"))).intValue();
		int endMonth=(new Integer(request.getParameter("endMonth"))).intValue();
		int endYear=(new Integer(request.getParameter("endYear"))).intValue();
		String locCode="1a1";
		if(request.getParameter("locCode")!=null) {
			locCode=request.getParameter("locCode");
		}



	    myShepherd.beginDBTransaction();
		Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
		Query query=myShepherd.getPM().newQuery(sharkClass);

		//now, let's print out our capture histories
		out.println("<br><br>Sonification histories for Program Mark robust model:<br><br><pre>");

		//let's set up our columns


		//date column
		ArrayList<String> dateColumn = new ArrayList<String>();
		dateColumn.add("Date");
							for(int m_year=startYear;m_year<(endYear+1);m_year++){
								for (int m_month=startMonth;m_month<(endMonth+1);m_month++) {
									for(int m_day=1;m_day<32;m_day++){
										String year=(new Integer(m_year)).toString();
										String month=(new Integer(m_month)).toString();
										String day=(new Integer(m_day)).toString();
										if((m_month==4)&&(m_day==31)){}
										else if((m_month==6)&&(m_day==31)){}
										else if((m_month==9)&&(m_day==31)){}
										else if((m_month==11)&&(m_day==31)){}
										else{
											dateColumn.add((year+"-"+m_month+"-"+day));
										}
									}
								}
							}


		ArrayList<ArrayList> allColumns = new ArrayList<ArrayList>();
		allColumns.add(dateColumn);

		Iterator it2=myShepherd.getAllMarkedIndividuals(query);

		while(it2.hasNext()) {
			MarkedIndividual s=(MarkedIndividual)it2.next();

			ArrayList<String> thisSharkColumn = new ArrayList<String>();
			thisSharkColumn.add(s.getName());
			allColumns.add(thisSharkColumn);





							for(int m_year=startYear;m_year<(endYear+1);m_year++){
								for (int m_month=startMonth;m_month<(endMonth+1);m_month++) {
									for(int m_day=1;m_day<32;m_day++){
			String year=(new Integer(m_year)).toString();
										String month=(new Integer(m_month)).toString();
										String day=(new Integer(m_day)).toString();
										if((m_month==4)&&(m_day==31)){}
										else if((m_month==6)&&(m_day==31)){}
										else if((m_month==9)&&(m_day==31)){}
										else if((m_month==11)&&(m_day==31)){}
										else{

											if(s.wasSightedInPeriod(m_year, m_month,m_day, m_year, m_month, m_day, locCode)){
												thisSharkColumn.add(("1"));
											}
											else{
												thisSharkColumn.add(("0"));
											}

										}
									}
								}
							}






		} //end while

		for(int i=0;i<allColumns.size();i++){
			ArrayList thisColumn = allColumns.get(i);
			if(!thisColumn.contains("1")){
				allColumns.remove(i);
				i--;
			}
		}

		allColumns.add(0,dateColumn);

		//now we print these out

		int numRows=allColumns.get(0).size();

		for(int q=0;q<numRows;q++){

			for(int r=0;r<allColumns.size();r++){
				if(r==(allColumns.size()-1)){
					out.print(allColumns.get(r).get(q));
				}
				else{
					out.print(allColumns.get(r).get(q)+",");
				}
			}
			out.println("<br />");

		}



		query.closeAll();
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();


	}
	catch(Exception e) {
		out.println("You really screwed this one up!");
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();

	}
	out.close();
}
}