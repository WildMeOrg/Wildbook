<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.MediaAsset,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%!

  boolean relevantAnnot(Annotation ann) {
    MediaAsset ma = ann.getMediaAsset();
    return (ma!=null && ma.getDetectionStatus()!=null);
  }


%>


<%

Shepherd myShepherd=new Shepherd(request);

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
int numNonTrivial=0;
int numSibs=0;
int numMediaAssets=0;

int numAccess=0;

int errors=0;

boolean committing=false;

List<String> acmIds = new ArrayList<String>();
List<Integer> maIds = new ArrayList<Integer>();
int noAcmId =0;


int limit=1000000;

try {

  List<Encounter> allEncs=myShepherd.getEncountersByField("submitterID","SDRP");

  %>
  <h1>If you aren't seeing this, something is really wrong.</h1>
  <p>Starting! committing = <%=committing%> and limit = <%=limit%> </p>
  </hr>
  <ul>
<%

  int printPeriod = 50;
  int count=0;
  int total=allEncs.size();

  while(count < total && count < limit){

    Encounter enc= allEncs.get(count);
    count++;

  	numFixes++;
    boolean verbose = (count%printPeriod == 0);


    if (verbose) {
      System.out.println("deleteNeaqAnnots on row "+count+"/"+total);
      %><li>Row <%=count %><ul>
        <li>Encounter <%=enc %></li>
      </ul></li><%
    }


    if (enc.getAnnotations()!=null) {
      for (Annotation ann: enc.getAnnotations()) {
        numAnnotations++;
        if (relevantAnnot(ann)) {
          maIds.add(ann.getMediaAsset().getId());

          if (!ann.isTrivial()) {
            if (ann.hasAcmId()) acmIds.add(ann.getAcmId());
            numNonTrivial++;

            // This for loop deletes sibling annotations in the multi-detect scenario
            for (Annotation sibling: ann.getSiblings()) {
              numSibs++;
              if (committing) {
                Encounter sibEnc = sibling.findEncounter(myShepherd);
                if (sibEnc!=null) sibEnc.removeAnnotation(sibling);
                sibling.detachFromMediaAsset();
                myShepherd.throwAwayAnnotation(sibling);
              }
            }

            // This block deletes the annotation and replaces it with a trivial one
            if (committing) {
              Annotation newAnn = ann.revertToTrivial(myShepherd);
              myShepherd.storeNewAnnotation(newAnn);
              myShepherd.throwAwayAnnotation(ann);
            }
          }

        }
        else {
          noAcmId++;
          continue;
        }
      }
    }

    if (committing) {
  		myShepherd.commitDBTransaction();
  		myShepherd.beginDBTransaction();
    }
  }
}
catch(Exception e){
  System.out.println("Exception on deleteNeaqAnnots!!");
  e.printStackTrace();
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>

</ul>
<p>Done successfully: <%=numFixes %> touched encounters</p>
<p>Done successfully: <%=numAnnotations %> annotations, of which <%=numNonTrivial%> are not trivial.</p>
<p>Done successfully: <%=numSibs %> deleted sibling annotations</p>
<p>Done successfully: <%=noAcmId %> annots without an acmIds</p>
<p>Here are all (<%=acmIds.size()%>) of the deleted *annotation* acmIds:<ul  style="font-family: monospace;">
  <%
  for (String acmId: acmIds) {
    %><li><%=acmId%></li><%
  }
  %>
</ul></p>

<p>And here are all (<%=maIds.size()%> non-unique) of the saved maIds:<ul  style="font-family: monospace;">
  <%
  for (Integer maId: new LinkedHashSet<Integer>(Util.asSortedList(maIds))) {
    %><li><%=maId%></li><%
  }
  %>
</ul></p>


</body>
</html>
