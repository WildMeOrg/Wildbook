<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,javax.jdo.*,org.ecocean.servlet.importer.*,
org.ecocean.grid.*,java.util.Collection,org.ecocean.ia.Task,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%!
  // ideally modifying this servlet should reduce to modifying the below method
  boolean needsToBeFixed(Encounter enc, Shepherd myShepherd) {
    String occID = enc.getOccurrenceID();
    String country = enc.getCountry();
    String submitter = enc.getSubmitterID();
    String guid = enc.getDWCGlobalUniqueIdentifier();
    boolean byGuid = false;
    boolean byIndividualName = false;
    MarkedIndividual ind = myShepherd.getMarkedIndividual(enc);
    if(guid != null){
      if(guid.indexOf("NCAquariums")>-1) byGuid=true;
    }
    if(ind != null && ind.getNamesList() != null && ind.getNamesList().size()>0){
      List<String> indNames = ind.getNamesList();
      for(String currentName: indNames){
        if(currentName.indexOf("USA-")>-1) byIndividualName = true;
        if(currentName.indexOf("UNID-")>-1) byIndividualName = true;
      }
    }
    boolean byCountry = (Util.stringExists(country) && country.toLowerCase().contains("united states"));
    List<String> usaNames = new ArrayList<String>();
    usaNames.add("ara.mcclanahan");
    usaNames.add("carol.price");
    usaNames.add("webusa");
    boolean bySubmitter = (Util.stringExists(submitter) && (usaNames.contains(submitter.toLowerCase())));
    String locationId = enc.getLocationID();
    List<String> usaLocations = new ArrayList<String>();
    usaLocations.add("Frying Pan Tower");
    usaLocations.add("USS Schurz");
    usaLocations.add("Hyde (AR-386)");
    usaLocations.add("Aeolus (AR-305)");
    usaLocations.add("Dixie Arrow");
    usaLocations.add("Caribsea");
    boolean byLocationId = (Util.stringExists(locationId) && (usaLocations.contains(locationId)));
    boolean needsToBeFixed = byIndividualName || byCountry || bySubmitter || byLocationId || byGuid;
    if(needsToBeFixed) {
      System.out.println("byIndividualName is: " + byIndividualName);
      System.out.println("submitter is: " + submitter);
      System.out.println("country is: " + country);
      System.out.println("locationId is: " + locationId);
      System.out.println("guid is: " + guid);
      System.out.println("byLocationId is: " + byLocationId + ". byIndividualName is: " + byIndividualName + ". byCountry is: " + byCountry + ". bySubmitter is: " + bySubmitter + ". byIndividualName is: " + byIndividualName +". byGuid is: " + byGuid);
    }
    return (needsToBeFixed);
  }
%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
int numEncs=0;
int numIndividuals=0;
int numOccurrences=0;
int nullOccs=0;
int numAnnotations=0;
int numMediaAssets=0;

int numAccess=0;

int errors=0;
int total=0;



try {

	String rootDir = getServletContext().getRealPath("/");
  String baseDir = ServletUtilities.dataDir(context, rootDir);
	// String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");

  boolean committing=true;
  boolean committingEncs=true; // so we can double-check we've deleted the other objects without deleting the link to all of them, ie the encounter
  %>
  <h1>If you aren't seeing this, something is really wrong.</h1>
<p>Starting! committing = <%=committing%> and committingEncs = <%=committingEncs%> </p>
<hr></hr>
<ul>
<%

  int printPeriod = 5;
  int count=0;


  // todo: figure out correct start and end
  // long start = 1447545600000l;
  // long end   = 1447977600000l;
  //List<Encounter> allEncs=myShepherd.getEncountersSubmittedDuring(start, end);


	String filter="SELECT FROM org.ecocean.Encounter";



	Query q=myShepherd.getPM().newQuery(filter);
	Collection c = (Collection) (q.execute());
	ArrayList<Encounter> allEncs=new ArrayList<Encounter>(c);
	q.closeAll();
	total=allEncs.size();


  while(count < total){

    Encounter enc= allEncs.get(count);
    count++;

    boolean needsRemoval=needsToBeFixed(enc, myShepherd);
    if (!needsRemoval) continue;

  	numFixes++;
    boolean verbose = (count%printPeriod == 0);

    Occurrence occ = myShepherd.getOccurrence(enc);
    MarkedIndividual mark = myShepherd.getMarkedIndividualQuiet(enc.getIndividualID());



    if (verbose) {
      %><li>Row <%=count %><ul>
        <li>Encounter <%=enc %></li>
        <li>Occurrence <%=occ %></li>
        <li>Individual <%=mark %></li>
      </ul></li><%
    }


    if (enc.getAnnotations()!=null) {
          for (Annotation ann : enc.getAnnotations()) {
            //myShepherd.beginDBTransaction();
            enc.removeAnnotation(ann);
            myShepherd.updateDBTransaction();
            List<Task> iaTasks = Task.getTasksFor(ann, myShepherd);
            if (iaTasks!=null&&!iaTasks.isEmpty()) {
              for (Task iaTask : iaTasks) {
                iaTask.removeObject(ann);
                myShepherd.updateDBTransaction();
              }
            }
            myShepherd.throwAwayAnnotation(ann);
            myShepherd.updateDBTransaction();
          }
    }

    // get weird foreign key errors related to ENCOUNTER_ANNOTATIONS without this
    if (committing) enc.setAnnotations(new ArrayList<Annotation>());


    if (occ!=null) {
      numOccurrences++;
      if (committing) {
        // do we want to throw away occurrences
        //occ.setTaxonomies(new ArrayList<Taxonomy>());
        myShepherd.updateDBTransaction();
        myShepherd.throwAwayOccurrence(occ);
      }
    } else {
      nullOccs++;


    }

	    ImportTask task=myShepherd.getImportTaskForEncounter(enc.getCatalogNumber());
          if(task!=null) {
            task.removeEncounter(enc);
            task.addLog("Servlet EncounterDelete removed Encounter: "+enc.getCatalogNumber());
            myShepherd.updateDBTransaction();
          }

		  List<Project> projs=myShepherd.getProjectsForEncounter(enc);
          if(projs!=null&&projs.size()>0) {
			for(Project proj:projs){
				proj.removeEncounter(enc);

				myShepherd.updateDBTransaction();
			}
		  }


     if (mark!=null) {
       numIndividuals++;
	   mark.removeEncounter(enc);
	   myShepherd.updateDBTransaction();
      if (committing && mark.getNumEncounters()==0) myShepherd.throwAwayMarkedIndividual(mark);
     }

	//remove from marked individual

    if (committingEncs) {
      try {
        myShepherd.throwAwayEncounter(enc);
        numEncs++;
      } catch (Exception e) {
        System.out.println("Exception on throwAwayEncounter!!");
        e.printStackTrace();
        errors++;
      }
    }



    if (committing) {
  		myShepherd.commitDBTransaction();
  		myShepherd.beginDBTransaction();
    }
  }



}
catch(Exception e){
	//	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>

</ul>
<p>Done successfully: <%=numFixes %> touched encounters of <%=total %></p>
<p>Done successfully: <%=numEncs %> deleted encounters</p>
<p><strong>Errors: <%=errors %></strong></p>

<hr></hr>
<p>Done successfully: <%=numIndividuals %> individuals</p>
<p>Done successfully: <%=numOccurrences %> occurrences</p>
<p>Done successfully: <%=nullOccs %> <em>null</em>occurrences</p>
<p>Done successfully: <%=numAnnotations %> annotations</p>

</body>
</html>
