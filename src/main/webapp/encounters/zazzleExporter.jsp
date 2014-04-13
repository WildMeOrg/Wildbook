

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.awt.*,java.io.*, java.net.URL, java.net.URLConnection, java.util.ArrayList" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>

<%

String context="context0";
context=ServletUtilities.getContext(request);


  String num = request.getParameter("number");
//int number=(new Integer(num)).intValue();
  Shepherd myShepherd = new Shepherd(context);
  boolean proceed = true;
  String side = "Left";
  
  String individualID="";
  String nickname="";

 
     //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
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


  	
  	//allowed width
  	//int allowedWidth=325;
  	//int offsetLeft=1350;
  	//int offsetRight=445;
 
  	
  	//int leftAdjustmentFactor=0;
  	//int topAdjustmentFactor=0;
  	


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

  	<di:image x="0" y="0" srcurl="../images/zazzle/shirt_horizontal-sid3.png" width="2100" height="1800"  />
  	<%
  	String sharkURL="http://www.whaleshark.org/GenerateQRCodeImage?number="+individualID;
  	%>
  	<di:image x="271" y="1091" srcurl="<%=sharkURL %>" width="238" height="238"  />
  	
  	 <!-- indie ID and nickname rendering -->
  	<di:text x="710" y="1106" font="Dakota Regular-plain-65" fillPaint="#000000" ><%=individualID %></di:text>
  	<di:text x="710" y="1236" font="Dakota Regular-plain-65" fillPaint="#000000" ><%=nickname %></di:text>
 
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
  	//double xMultiple=1;
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
    

    
    int xmin = (int)enc.getLeftmostSpot();
    int xmax = (int)enc.getRightmostSpot();
    int ymin = (int)enc.getHighestSpot();
    int ymax = (int)enc.getLowestSpot();

    double origxcenter = (xmin+xmax)/2.0;
    double origycenter = (ymin+ymax)/2.0;
    double origaspect = (ymax-ymin)/(xmax-xmin);

    // My estimation of where spots can go without running outside shark
    // silhouette in image file from James Weyenberg
    int boxleft = 275;
    int boxright = 1775;
    
    //this is actually the bottom limit of your region for spot definition, measured down from top of graphic
    int boxtop = 1000;
    		
   //this is actually the top limit for spot definition, measured down in pixels from the top of the graphic 		
    int boxbot = 375;
    
    
    double boxxcenter = (boxleft+boxright)/2.0;
    double boxycenter = (boxtop+boxbot)/2.0;
    double boxaspect = Math.abs((boxtop-boxbot)/(boxright-boxleft));

    double factor=1.0;
    String visualAspect="horizontal";
    
    if (boxaspect > origaspect) {
       // original image fills output box horizontally; vertical scales accordingly
       int outwidth = Math.abs(boxright-boxleft);
       int origwidth = Math.abs(xmax-xmin);
       factor = (float)outwidth/origwidth;
     } 
    else {
       // original image fills output box vertically; horizontal scales accordingly
       int outheight = Math.abs(boxtop-boxbot);
       int origheight = Math.abs(ymax-ymin);
       factor = (float)outheight/origheight;
       
       if(request.getParameter("debug")!=null){
       %>
       <!-- helpers -->
 	<di:text x="1490" y="160" font="Dakota Regular-plain-20" fillPaint="#000000" >outheight: <%=outheight %></di:text>
 	<di:text x="1490" y="200" font="Dakota Regular-plain-20" fillPaint="#000000" >origheight: <%=origheight %></di:text>
 	 <%
       }
       visualAspect="vertical";
     }

    //int xout = (int)((xorig-origxcenter)*factor+boxxcenter); // array operation
    //int yout = (yorig-origycenter)*factor+boxycenter; // array operation
    if(request.getParameter("debug")!=null){
  	 %>
 <!-- helpers -->
 	<di:text x="1490" y="240" font="Dakota Regular-plain-20" fillPaint="#000000" >factor: <%=factor %></di:text>
 	<di:text x="1490" y="280" font="Dakota Regular-plain-20" fillPaint="#000000" >aspect: <%=visualAspect %></di:text>
 <%
    }
    int currentSpotNum=0;
    int secondarySpotNum=2;
    for (int numIter2 = 0; numIter2 < numSpots; numIter2++) {
      //int theX = (int) ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidX();
      //theX=(int)(theX*xMultiple);
      //int theY = (int) ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidY();
      //theY=(int)(theY*xMultiple);
      
      int myX=(int) ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidX();
          int theX = (int)((myX-origxcenter)*factor+boxxcenter); // array operation
    	int myY=(int) ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidY();
          int theY = (int)((myY-origycenter)*factor+boxycenter); // array operation
      
  %>
   <di:circle x="<%=theX %>" y="<%=theY %>" radius="52" fillPaint="#000000"></di:circle>
   
 <di:circle x="<%=theX %>" y="<%=theY %>" radius="48" fillPaint="<%=colors[secondarySpotNum] %>"></di:circle>
   <di:circle x="<%=theX %>" y="<%=theY %>" radius="44" fillPaint="#000000"></di:circle>
  
<di:circle x="<%=theX %>" y="<%=theY %>" radius="40" fillPaint="<%=colors[currentSpotNum] %>"></di:circle>
 
  <%
  if(request.getParameter("debug")!=null){
  	if(myX==xmin){%>
  		<di:text x="<%=(theX+11) %>" y="<%=theY %>" font="Dakota Regular-plain-20" fillPaint="<%=colors[currentSpotNum] %>" >xmin</di:text>
  	<%}
  	if(myX==xmax){%>
		<di:text x="<%=(theX+11) %>" y="<%=theY %>" font="Dakota Regular-plain-20" fillPaint="<%=colors[currentSpotNum] %>" >xmax</di:text>
	<%}
  	if(myY==ymin){%>
		<di:text x="<%=(theX+11) %>" y="<%=theY %>" font="Dakota Regular-plain-20" fillPaint="<%=colors[currentSpotNum] %>" >ymin</di:text>
	<%}
  	if(myY==ymax){%>
		<di:text x="<%=(theX+11) %>" y="<%=theY %>" font="Dakota Regular-plain-20" fillPaint="<%=colors[currentSpotNum] %>" >ymax</di:text>
	<%}
  }
  
  	//track spot number to set from the color palette
  	currentSpotNum++;
  	secondarySpotNum++;
  	if(currentSpotNum==9){currentSpotNum=0;}
  	if(secondarySpotNum==9){secondarySpotNum=0;}
    } //end for now


    
    
%>
</di:img>

<!-- Put the image URL in now -->
<img src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=(num+"/"+side+"Zazzle.jpg")%>" border="0" align="left" valign="left">





<% 


}


} else {%>
<p>There is no encounter <%=request.getParameter("number")%> in the
  database. Please double-check the encounter number and try again.</p>



<%
  }
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
%>




