<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.media.MediaAsset,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>



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


%><p>Committing = <%=committing%>.</p><%

HashMap<String,String> hm=new HashMap<String,String>();

try{

  List<Encounter> allEncs=myShepherd.getEncountersByField("submitterID","neaq");

  	//first, build a hashmap of filename, uuid for NEAQ MediaAssets
	for (Encounter enc: allEncs) {
		ArrayList<MediaAsset> al=enc.getMedia();
		for(int i=0;i<al.size();i++){
			MediaAsset ma=al.get(i);
			ArrayList<Keyword> kws=ma.getKeywords();
	        LinkedHashSet<Keyword> hashSet = new LinkedHashSet<Keyword>(kws);
	        
	        ArrayList<Keyword> listWithoutDuplicates = new ArrayList<Keyword>(hashSet);
	        %>
	        <li>old: <%=kws.toString() %> vs. new <em><%=listWithoutDuplicates.toString() %></em></li>
	        <%
	        if(committing){
	        	ma.setKeywords(listWithoutDuplicates);
	        	myShepherd.updateDBTransaction();
	        }
			
		}
	}
	
  	
  	//now, lets load in the CSV file
  	/**
  	     String csvFile = "/data/ViewDirectionMapping.csv";
        BufferedReader br = null;
        String line = "";
        String cvsSplitBy = ",";
        
        try {

        	
            br = new BufferedReader(new FileReader(csvFile));
            while ((line = br.readLine()) != null) {

                // use comma as separator
                String[] vals = line.split(cvsSplitBy);
				if(vals[0]!=null && vals[1]!=null){
					String filename=vals[0];
					String kwName=vals[1];
					if(hm.containsKey(filename)){
						System.out.println("ooking for: "+filename + "and found "+hm.get(filename) );
						MediaAsset ma=myShepherd.getMediaAsset(hm.get(filename));
						if(ma!=null){
							%>
							<li>Found <%=ma.getFilename() %> and should add keyword: <%=kwName %></li>
							<%
							
							if (committing) {
								Keyword kw = myShepherd.getOrCreateKeyword(kwName);
								ma.addKeyword(kw);
								myShepherd.updateDBTransaction();
							}
						}
						
					}
					
				}
                
                

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
        **/
	
	

	

}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}


%>

</ul>


</body>
</html>
