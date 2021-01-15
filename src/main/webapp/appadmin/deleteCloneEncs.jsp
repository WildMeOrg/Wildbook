<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.servlet.importer.ImportTask,org.ecocean.ia.Task,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%!
  // ideally modifying this servlet should reduce to modifying the below method
  // for ACW we are grabbing encounters with cloneWithoutAnnotations already, and now filtering those down to wild dogs submitted before a certain date
  boolean needsToBeFixed(Encounter enc) {
    String genus = enc.getGenus();
    if (!"Lycaon".equals(genus)) return false;
    long cutoff = 1610006400000l; // 1/8/2021, PST
    long submitted = enc.getDWCDateAddedLong();
    return (submitted < cutoff);
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
int totalEncs=0;
int badRows=0;

List<String> orphanAnnots = new ArrayList<String>();

try {

  boolean committing=true;
  boolean committingEncs=true; // so we can double-check we've deleted the other objects without deleting the link to all of them, ie the encounter
  %>
    <p>Starting! committing = <%=committing%> and committingEncs = <%=committingEncs%> </p>
    <hr></hr>
    <ul>
  <%

  List<Encounter> allEncs=myShepherd.getEncountersByFieldSubstring("researcherComments", "NOTE: cloneWithoutAnnotations");
  System.out.println("we have "+allEncs.size()+" encounters.");


  int printPeriod = 100;
  int count=0;
  totalEncs=allEncs.size();


  for (Encounter enc: allEncs) {

    if (!needsToBeFixed(enc)) continue;
    count++;

    numFixes++;
    boolean verbose = (count%printPeriod == 0);
    if (verbose) System.out.println("on row "+count);

    Occurrence occ = myShepherd.getOccurrence(enc);
    MarkedIndividual mark = myShepherd.getMarkedIndividualQuiet(enc.getIndividualID());
    ImportTask imp = myShepherd.getImportTaskForEncounter(enc);

//    if (mark==null && committing && !committingEncs) {
//      mark = myShepherd.getMarkedIndividualHard(enc);
//    }

    if (verbose) {
      %><li>Row <%=count %><ul>
        <li>Encounter <%=enc %></li>
        <li>Occurrence <%=occ %></li>
        <li>Individual <%=mark %></li>
      </ul></li><%
    }

    if (occ != null) {
      occ.removeEncounter(enc);
    }

    if (imp != null) {
      imp.removeEncounter(enc);
    }

    if (enc.getAnnotations()!=null) {
      for (Annotation ann: enc.getAnnotations()) {
        numAnnotations++;
        enc.removeAnnotation(ann);
        Task tasky = myShepherd.getTaskForAnnotation(ann);
        if (tasky!=null) {
          tasky.removeObject(ann);
        }
        if (committing) {
          try {
            myShepherd.throwAwayAnnotation(ann);
          } catch (Exception e) {
            // an IAtask exists with this annotation violating a foreign key contraint on delete
            orphanAnnots.add(ann.getId());
            System.out.println("Found orphan annot "+ann.toString());
          }
        }
      }
    }



    // get weird foreign key errors related to ENCOUNTER_ANNOTATIONS without this
    if (committing) enc.setAnnotations(new ArrayList<Annotation>());

    if (mark!=null) {
      numIndividuals++;
      if (committing) mark.removeEncounter(enc);
      if (committing && mark.numEncounters() == 0) myShepherd.throwAwayMarkedIndividual(mark);
    }

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
      try {
        myShepherd.commitDBTransaction();
      } catch (Exception e) {
        System.out.println("EXCEPTION on commit on row "+count+". rolling back!");
        e.printStackTrace();
        badRows++;
        myShepherd.rollbackDBTransaction();
      }
      myShepherd.beginDBTransaction();
    }
  }
}
catch(Exception e){
  myShepherd.rollbackDBTransaction();
}
finally{
  myShepherd.closeDBTransaction();

}

%>

</ul>
<p>Done successfully: <%=totalEncs %> encounters returned by myShepherd</p>
<p>Done successfully: <%=numFixes %> of which passed shouldBeFixed</p>
<p>Done successfully: <%=numEncs %> deleted encounters</p>
<p><strong>Errors: <%=errors %></strong></p>
<hr></hr>
<p>Done successfully: <%=numIndividuals %> individuals</p>
<p>Done successfully: <%=numAnnotations %> annotations deleted</p>
<p>Done successfully: <%=badRows %> orphaned annots: <ul>
  <%for (String annID: orphanAnnots) {
    %><li><%=annID%></li><%
  }
  %>
</ul></p>

</body>
</html>
