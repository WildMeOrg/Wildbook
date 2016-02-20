package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;

import java.lang.StringBuffer;

import javax.jdo.*;


//adds spots to a new encounter
public class MitFeed extends HttpServlet{


	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
	}


	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
		doPost(request, response);
	}



	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{

	  String context="context0";
    context=ServletUtilities.getContext(request);
    
		//open a shepherd
		Shepherd myShepherd=new Shepherd(context);

		response.setContentType("application/xml; charset=UTF-8");
		boolean madeChanges=false;
		PrintWriter out = response.getWriter();
		myShepherd.beginDBTransaction();
		Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
		Query encQuery=myShepherd.getPM().newQuery(encClass);
		Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
		Query sharkQuery=myShepherd.getPM().newQuery(sharkClass);
		try {
			String action="";
			String qualifier="";
			action=request.getParameter("action");
			qualifier=request.getParameter("qualifier");

			//now let's process
			if((action!=null)&&(!action.equals(""))&&(qualifier!=null)&&(!qualifier.equals(""))){

					//GetList, L
					if((action.equals("GetList"))&&(qualifier.equals("L"))){
						response.setContentType("text/html; charset=UTF-8");
						Iterator it=myShepherd.getAllEncounters(encQuery);
						while(it.hasNext()) {
							Encounter tempEnc=(Encounter)it.next();
							if((tempEnc.getSpots()!=null)&&(tempEnc.getSpots().size()>0)){
								out.println(tempEnc.getEncounterNumber());
							}
						}
					}
					//GetList, R
					else if((action.equals("GetList"))&&(qualifier.equals("R"))){
						response.setContentType("text/html; charset=UTF-8");
						Iterator it2=myShepherd.getAllEncounters(encQuery);
						while(it2.hasNext()) {
							Encounter tempEnc2=(Encounter)it2.next();
							if((tempEnc2.getRightSpots()!=null)&&(tempEnc2.getRightSpots().size()>0)){
								out.println(tempEnc2.getEncounterNumber());
							}
						}
					}
					//GetData, encounter number
					else if(action.equals("GetData")){
						Encounter dataEnc=myShepherd.getEncounter(qualifier);
						StringBuffer xmlData=new StringBuffer();

						//xml root
						xmlData.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<encounter number=\""+qualifier+"\" year=\""+dataEnc.getYear()+"\" assignedToShark=\""+dataEnc.getIndividualID()+"\">\n");

						//left side
						if((dataEnc.getSpots()!=null)&&(dataEnc.getSpots().size()>0)){
							ArrayList<SuperSpot> spots=dataEnc.getSpots();
							xmlData.append("<spots side=\"left\" image=\"http://www.whaleshark.org/shepherd_data_dir/encounters/"+qualifier+"/"+dataEnc.getSpotImageFileName()+"\">\n");
							for (int numIter2=0;numIter2<spots.size();numIter2++) {
								xmlData.append("     <spot centroidX=\""+spots.get(numIter2).getTheSpot().getCentroidX()+"\" centroidY=\""+spots.get(numIter2).getTheSpot().getCentroidY()+"\"/>\n");
							}

							//write out the fiducial points
							if((dataEnc.getLeftReferenceSpots()!=null)&&(dataEnc.getLeftReferenceSpots().size()>0)){
								xmlData.append("     <fids>\n");

								ArrayList<SuperSpot> refSpots=dataEnc.getLeftReferenceSpots();
								for(int fid=0;fid<3;fid++){
									SuperSpot fido = refSpots.get(fid);
									xmlData.append("          <fid cx=\""+fido.getCentroidX()+"\" cy=\""+fido.getCentroidY()+"\"/>\n");
								}
								xmlData.append("     </fids>\n");

							}


							xmlData.append("</spots>\n");
						}

						//right side
						if((dataEnc.getRightSpots()!=null)&&(dataEnc.getRightSpots().size()>0)){
							ArrayList<SuperSpot> spots=dataEnc.getRightSpots();
							xmlData.append("<spots side=\"right\" image=\"http://www.whaleshark.org/shepherd_data_dir/encounters/"+qualifier+"/"+dataEnc.getRightSpotImageFileName()+"\">\n");
							for (int numIter3=0;numIter3<spots.size();numIter3++) {
								xmlData.append("     <spot centroidX=\""+spots.get(numIter3).getTheSpot().getCentroidX()+"\" centroidY=\""+spots.get(numIter3).getTheSpot().getCentroidY()+"\"/>\n");
							}

							//write out the fiducial points
							if((dataEnc.getRightReferenceSpots()!=null)&&(dataEnc.getRightReferenceSpots().size()>0)){
								xmlData.append("     <fids>\n");

								ArrayList<SuperSpot> refSpots=dataEnc.getRightReferenceSpots();
								for(int fid=0;fid<3;fid++){
									SuperSpot fido = refSpots.get(fid);
									xmlData.append("          <fid cx=\""+fido.getCentroidX()+"\" cy=\""+fido.getCentroidY()+"\"/>\n");
								}
								xmlData.append("     </fids>\n");

							}


							xmlData.append("</spots>\n");
						}
						//end xml root
						xmlData.append("</encounter>\n");
						out.println(xmlData.toString());

					}
					//GetMatches, encounter number
					else if(action.equals("GetMatches")){
						Encounter dataEnc=myShepherd.getEncounter(qualifier);
						StringBuffer xmlData=new StringBuffer();

						//xml root
						xmlData.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<encounter number=\""+qualifier+"\" assignedToShark=\""+dataEnc.getIndividualID()+"\">\n");
						xmlData.append("<matches>\n");

						//logic
						if(dataEnc.getIndividualID()!=null) {
							MarkedIndividual tempShark=myShepherd.getMarkedIndividual(dataEnc.getIndividualID());
							Vector encs=tempShark.getEncounters();
							for(int s=0;s<encs.size();s++) {
								Encounter matchEnc=(Encounter)encs.get(s);
								if(!matchEnc.getEncounterNumber().equals(qualifier)) {
									xmlData.append("<match number=\""+matchEnc.getEncounterNumber()+"\"/>\n");
								}
							}
						}

						//end xml root
						xmlData.append("</matches>\n");
						xmlData.append("</encounter>\n");
						out.println(xmlData.toString());
					}




			} //end if all parameters OK
			else{
				out.println("<p>Invalid parameters requested. Check your request and try again.\nRequestsed parameters:\n");
				out.println("action: "+action+"\n");
				out.println("qualifier: "+qualifier+"\n</p>");

			}



			//wrap up
			encQuery.closeAll();
			sharkQuery.closeAll();
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			encQuery=null;
			sharkQuery=null;

	}
	catch(Exception e) {
		e.printStackTrace();
		out.println("<p>I hit an error. Have Jason check the logs.</p>");
		encQuery.closeAll();
		encQuery=null;
		sharkQuery.closeAll();
		sharkQuery=null;
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();

	}
	out.close();
	myShepherd=null;
}
}