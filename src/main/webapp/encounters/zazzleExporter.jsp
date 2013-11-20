

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
    		individualID=indie.getIndividualID().trim();
    		if((indie.getNickName()!=null)&&(!indie.getNickName().trim().equals(""))){
    			nickname=indie.getNickName().trim();
    		}
    	}
    }
    
    //now let's build our individualID
    if(individualID.trim().equals("")){
    	individualID=enc.getCatalogNumber();
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
  	
  	//allowed width
  	int allowedWidth=325;
  	int offsetLeft=1350;
  	int offsetRight=445;
 
  	
  	int leftAdjustmentFactor=0;
  	int topAdjustmentFactor=0;
  	
  	/**
  	if((request.getParameter("allowedWidth")!=null)&&(request.getParameter("allowedHeight")!=null)){
  		String maxWidthString=request.getParameter("allowedWidth");
  		String maxHeightString=request.getParameter("allowedHeight");
  		try{
  			allowedWidth=(new Integer(maxWidthString)).intValue();
  			allowedHeight=(new Integer(maxWidthString)).intValue();
  		}
  		catch(Exception e){e.printStackTrace();}
  	}
  	*/

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

  	<di:image x="0" y="0" srcurl="../images/zazzle/shirt_horizontal-side-right.png" width="2100" height="1800"  />
  	<%
  	String sharkURL="http://www.whaleshark.org/GenerateQRCodeImage?number="+individualID;
  	%>
  	<di:image x="260" y="966" srcurl="<%=sharkURL %>" width="250" height="250"  />
  	
  	 <!-- indie ID and nickname rendering -->
  	<di:text x="710" y="975" font="Dakota Regular-plain-90" fillPaint="#000000" ><%=individualID %></di:text>
  	<di:text x="710" y="1095" font="Dakota Regular-plain-90" fillPaint="#000000" ><%=nickname %></di:text>
 
  	<%

  	
  	//lets prep the spot colors
  	String[] colors=new String[9];
  	colors[0]="#5F2C91";
  	colors[1]="#85318B";
  	colors[2]="#EC1C23";
  	colors[3]="#F05A21";
  	colors[4]="#FFC50A";
  	colors[5]="#FFF100";
  	colors[6]="#B1D235";
  	colors[7]="#39B549";
  	colors[8]="#2C9937";
  

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
        
        
        double maxDiff=Math.abs(theX2-theX1);
        if((Math.abs(theY2-theY1))>maxDiff){maxDiff=maxDiff*maxDiff/Math.abs(theY2-theY1);}
        
        xMultiple=allowedWidth/(maxDiff);
        
      }
    } catch (Exception e) {
    	e.printStackTrace();
    }
  	
    
    int currentSpotNum=0;
    for (int numIter2 = 0; numIter2 < numSpots; numIter2++) {
      int theX = (int) ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidX();
      theX=(int)(theX*xMultiple);
      int theY = (int) ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidY();
      theY=(int)(theY*xMultiple);
      
  %>
  <di:circle x="<%=(theX-((int)(leftAdjustmentFactor*xMultiple))+offsetLeft)%>" y="<%=(theY-((int)(topAdjustmentFactor*xMultiple))+offsetRight)%>" radius="10" fillPaint="<%=colors[currentSpotNum] %>"></di:circle>
 
  <%
  	currentSpotNum++;
  	if(currentSpotNum==9){currentSpotNum=0;}
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




