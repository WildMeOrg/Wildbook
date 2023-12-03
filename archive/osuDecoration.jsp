<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.media.MediaAsset,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%!
private void fixIndividual(MarkedIndividual indy, String defaultID, String unifiedID, String noaaID, String osuID, Shepherd myShepherd,int lineCount){
	
	//set new names
	org.ecocean.MultiValue mv = new org.ecocean.MultiValue();
	mv.addValuesByKey(MultiValue.DEFAULT_KEY_VALUE,defaultID);
	mv.addValuesByKey("Unified ID",unifiedID);
	mv.addValuesByKey("NOAA ID",noaaID);
	mv.addValuesByKey("OSU ID",osuID);
	//if(lineCount==1){
		indy.setNames(mv);
		myShepherd.updateDBTransaction();
	//}
	
}

%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("csvDecoration.jsp");


%>

<html>
<head>
<title>CSV Decoration</title>

</head>


<body>


<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
int numOrphanAssets=0;	
int numDatalessAssets=0;	
int numDatasFixed=0;	
int numAssetsFixed=0;	
int numAssetsWithoutStore=0;	

boolean committing=true;

String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && proj.encounters.contains(enc) && ( proj.id == '097f4931-2377-4209-9065-c2743542e948' ) VARIABLES org.ecocean.Encounter enc; org.ecocean.Project proj";
Query q=myShepherd.getPM().newQuery(filter);
Collection c = (Collection) (q.execute());
ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>(c);
q.closeAll();

ArrayList<MarkedIndividual> found = new ArrayList<MarkedIndividual>();

HashMap<String,String> hm=new HashMap<String,String>();

int lineCount=0;
StringBuffer outputter=new StringBuffer("");

try{

  	
  	//now, lets load in the CSV file

  	    String csvFile = "/data/individual-ids-by-lab-v10-Jason.csv";
        BufferedReader br = null;
        String line = "";
        String cvsSplitBy = ",";
        
        try {

        	
            br = new BufferedReader(new FileReader(csvFile));
            while ((line = br.readLine()) != null) {

                // use comma as separator
                String[] vals = line.split(cvsSplitBy);
                String matchedIndy="";
				if(lineCount>0 && vals[0]!=null){	
					
					String defaultID = "";
					if(vals.length>1)defaultID =vals[1].trim();
					String unifiedID = "";
					if(vals.length>3)unifiedID =vals[3].trim();
					String noaaID = "";
					if(vals.length>5)noaaID =vals[5].trim();
					String osuID = "";
					if(vals.length>7)osuID =vals[7].trim();
					
					boolean f=false;
					
					//search by defaultID
					if(!defaultID.equals("")){
						//System.out.println("Searching for: "+defaultID);
						for(MarkedIndividual indy:indies){
							if(indy.getDefaultName().equals(defaultID)) {
								found.add(indy);
								f=true;
								fixIndividual(indy, defaultID, unifiedID, noaaID, osuID,myShepherd,lineCount);
								matchedIndy=indy.getIndividualID();
							}
						}
					}
					
					//search by unifiedID
					if(!f && !unifiedID.equals("")){
						//System.out.println("Searching for: "+defaultID);
						for(MarkedIndividual indy:indies){
							if(!found.contains(indy) && indy.hasName(unifiedID)) {
								found.add(indy);
								f=true;
								fixIndividual(indy, defaultID, unifiedID, noaaID, osuID,myShepherd,lineCount);
								matchedIndy=indy.getIndividualID();
							}
						}
					}
					
					//search by noaaID
					if(!f && !noaaID.equals("")){
						//System.out.println("Searching for: "+defaultID);
						for(MarkedIndividual indy:indies){
							if(!found.contains(indy) && indy.hasName(noaaID)) {
								found.add(indy);
								f=true;
								fixIndividual(indy, defaultID, unifiedID, noaaID, osuID,myShepherd,lineCount);
								matchedIndy=indy.getIndividualID();
							}
						}
					}
					
					//search by osuID
					if(!f && !osuID.equals("")){
						//System.out.println("Searching for: "+defaultID);
						for(MarkedIndividual indy:indies){
							if(!found.contains(indy) && indy.hasName(osuID)) {
								found.add(indy);
								f=true;
								fixIndividual(indy, defaultID, unifiedID, noaaID, osuID,myShepherd,lineCount);
								matchedIndy=indy.getIndividualID();
							}
						}
					}
					

					if(f)outputter.append(defaultID+","+unifiedID+","+noaaID+","+osuID+","+matchedIndy+"\n");
					
				}
                
                lineCount++;

            }
            

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
     
	
	
        myShepherd.rollbackDBTransaction();
	

}
catch(Exception e){
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}


%>
<li>Matching individuals: <%=indies.size() %></li>
<li>Found.size(): <%=found.size() %></li>
</ul>

<pre>
<%=outputter.toString() %>
</pre>


</body>
</html>
