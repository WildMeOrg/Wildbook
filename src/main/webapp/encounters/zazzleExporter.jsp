

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.*,java.awt.*,java.io.*, java.net.URL, java.net.URLConnection, java.util.ArrayList" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>

<%
  String num = request.getParameter("number");
//int number=(new Integer(num)).intValue();
  Shepherd myShepherd = new Shepherd();
  boolean proceed = true;
  String side = "Left";
  
  String individualID="";
  String nickname="";

 
     //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    //if(!encountersDir.exists()){encountersDir.mkdir();}

  if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
    side = "Right";
  }


  myShepherd.beginDBTransaction();
if (myShepherd.isEncounter(num)) {
    Encounter enc = myShepherd.getEncounter(num);
    
    if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().equals("Unassigned"))){
    	MarkedIndividual indie=myShepherd.getMarkedIndividual(enc.getIndividualID());
    	if(indie!=null){
    		individualID=indie.getIndividualID();
    		if((indie.getNickName()!=null)&&(!indie.getNickName().trim().equals(""))){
    			nickname=indie.getNickName();
    		}
    	}
    }
    
    //now let's build our individualID
    if(individualID.trim().equals("")){
    	individualID=enc.getCatalogNumber();
    }
    else{
    	if(!nickname.trim().equals("")){
    		individualID=nickname.trim()+" ()"+individualID+")";
    	}
    }
    

  if ((side.equals("Right")) && (enc.getRightSpots() == null)) {
%>
<p>No right-side spot data is available for this encounter.</p>
<%
} else if ((side.equals("Left")) && (enc.getSpots() == null)) {
%>
<p>No left-side spot data is available for this encounter.</p>


<%
} else if ((side.equals("Right")) && (enc.rightSpotImageFileName.equals(""))) {
%>
<p>No right-side spot extraction image has been defined for this
  encounter.</p>

<%
} else if ((side.equals("Left")) && (enc.spotImageFileName.equals(""))) {
%>
<p>No spot extraction image has been defined for this encounter.</p>
<%
} else {


  //now let's set up the image mapping variables as needed
  String fileloc = "";
  if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
    fileloc = (enc.getEncounterNumber() + "/extractRight" + num + ".jpg");
  } else {
    fileloc = (enc.getEncounterNumber() + "/extract" + num + ".jpg");
  }
  InputStream encStream = null;
  boolean canDirectMap = true;
  Dimension imageDimensions = null;
  FileInputStream fip=new FileInputStream(new File(encountersDir.getAbsolutePath()+"/" + fileloc));
  try {
    //connEnc = encURL.openConnection();
    //System.out.println("Opened new encounter connection");
    //encStream = connEnc.getInputStream();
    imageDimensions = org.apache.sanselan.Sanselan.getImageSize(fip, ("extract" + num + ".jpg"));

  } 
  catch (IOException ioe) {
    System.out.println("I failed to get the image input stream while using the spotVisualizer");
    canDirectMap = false;
	%>
	<p>I could not connect to and find the spot image at: <%=(encountersDir.getAbsolutePath()+"/" + fileloc) %></p>

	<%
  }
  fip.close();
  fip=null;

  if (canDirectMap) {
  	int encImageWidth = (int) imageDimensions.getWidth();
  	int encImageHeight = (int) imageDimensions.getHeight();
  	
  	int allowedWidth=1400;
  	int allowedHeight=1200;
  	
  	int leftAdjustmentFactor=0;
  	int topAdjustmentFactor=0;
  	
  	if((request.getParameter("allowedWidth")!=null)&&(request.getParameter("allowedHeight")!=null)){
  		String maxWidthString=request.getParameter("allowedWidth");
  		String maxHeightString=request.getParameter("allowedHeight");
  		try{
  			allowedWidth=(new Integer(maxWidthString)).intValue();
  			allowedHeight=(new Integer(maxWidthString)).intValue();
  		}
  		catch(Exception e){e.printStackTrace();}
  	}

  	int numSpots = 0;
 	 if (side.equals("Right")) {
    	numSpots = enc.getRightSpots().size();
  	} 
 	else {
    	numSpots = enc.getSpots().size();
  	}
  	//StringBuffer xmlData = new StringBuffer();

  	String thumbLocation = "file-" + encountersDir.getAbsolutePath()+"/"+ num + "/" + side + "Zazzle.jpg";
	
	%>
	<di:img width="2100"
        height="1800"
        imgParams="rendering=quality,quality=high" border="0" expAfter="0"
        threading="limited" fillPaint="#FFFFFF" align="top" valign="left"
        output="<%=thumbLocation %>"
        >
        <%
        
        String src_url="..";
        %>
  	<di:image x="1800" y="1500" srcurl="../images/wild-me-logo-high-resolution.png" width="300" height="300"  />
  	
  	  <di:text font="Arial-bold-120" fillPaint="#0000FF" >These Spots Run Deep</di:text>
  	
  	<di:text x="0" y="200" font="Arial-bold-90" fillPaint="#000000" >Whale Shark: <%=individualID %></di:text>
  	
  	<di:text x="950" y="1500" font="Arial-bold-30" fillPaint="#000000" >Help us track this shark at whaleshark.org.</di:text>
  	
  	
  	<%


  //now calculate the multiples
  	double xMultiple=1;
  	//double yMultiple=1;

    ArrayList spots = enc.getSpots();


    ArrayList refSpots = null;
    try {
      refSpots = enc.getLeftReferenceSpots();
    } catch (Exception e) {
    }
    if (side.equals("Right")) {
      spots = enc.getRightSpots();
      try {
        refSpots = enc.getRightReferenceSpots();
      } catch (Exception e) {
      }
    }
    
    //now map reference spots if they exist
    try {
      if (refSpots != null) {
        
    	//5th top
    	int theX1 = ((int) (((SuperSpot) refSpots.get(0)).getTheSpot().getCentroidX()));
        int theY1 = ((int) (((SuperSpot) refSpots.get(0)).getTheSpot().getCentroidY()));
       
        //posterior pectoral
        int theX2 = ((int) (((SuperSpot) refSpots.get(1)).getTheSpot().getCentroidX()));
        int theY2 = ((int) (((SuperSpot) refSpots.get(1)).getTheSpot().getCentroidY()));
        
        //5th bottom
        int theX3 = ((int) (((SuperSpot) refSpots.get(2)).getTheSpot().getCentroidX()));
        int theY3 = ((int) (((SuperSpot) refSpots.get(2)).getTheSpot().getCentroidY()));
  
        leftAdjustmentFactor=theX1;
        if (side.equals("Right")) {topAdjustmentFactor=(int)enc.getHighestRightSpot();}
        else{topAdjustmentFactor=(int)enc.getHighestSpot();}
        
        
        xMultiple=allowedWidth/(theX2-theX1);
        
      }
    } catch (Exception e) {
    	e.printStackTrace();
    }
  	
    
    
    for (int numIter2 = 0; numIter2 < numSpots; numIter2++) {
      int theX = (int) ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidX();
      theX=(int)(theX*xMultiple);
      int theY = (int) ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidY();
      theY=(int)(theY*xMultiple);
      
  %>
  <di:circle x="<%=(theX-((int)(leftAdjustmentFactor*xMultiple))+100)%>" y="<%=(theY-((int)(topAdjustmentFactor*xMultiple))+400)%>" radius="40" fillPaint="#000000"></di:circle>

  <%
    } //end for now


    
    
%>
</di:img>

<!-- Put the image URL in now -->
<img src="/<%=CommonConfiguration.getDataDirectoryName() %>/encounters/<%=(num+"/"+side+"Zazzle.jpg")%>" border="0" align="left" valign="left">





<% }


}


} else {%>
<p>There is no encounter <%=request.getParameter("number")%> in the
  database. Please double-check the encounter number and try again.</p>



<%
  }
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
%>




