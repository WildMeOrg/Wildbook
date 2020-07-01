<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.joda.time.*,java.text.DateFormat,java.text.*,
org.ecocean.grid.*, org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

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
<ul>
<%
boolean commit = false;
if (commit) {
    try {

        String[] names = {"Tom","Karen","Bud","Stephanie","Archibald","Penny","Steven","Erin","Trevor","Carlos","Belinda","Wanda","Murray","Patrick","Gertrude","Pam","Tim","John"};
        // rootDir = request.getSession().getServlet().getServletContext().getRealPath("/");
        // String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "wildbook_data_dir");
    
        ArrayList<Encounter> encs = new ArrayList<>();
        ArrayList<MarkedIndividual> indys = new ArrayList<MarkedIndividual>();

        int count = 0;

        while (count<18) {
            myShepherd.beginDBTransaction();
            Encounter enc = new Encounter();
            enc.setState("approved");
            myShepherd.storeNewEncounter(enc, Util.generateUUID());
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
            MarkedIndividual indy = new MarkedIndividual();
            indy.setIndividualID(names[count]);
            myShepherd.storeNewMarkedIndividual(indy);
            myShepherd.commitDBTransaction();
            enc.setIndividualID(indy.getIndividualID());

            count++;
            System.out.println("====== Created this Indy/Enc for testing! Number: "+count+" Name: "+names[count]);
        }
        System.out.println("====== Done! Created testing individuals from list of 18.");

    } catch (Exception e) {
        myShepherd.rollbackDBTransaction();
    } finally {
        myShepherd.closeDBTransaction();
    }
  }
}

%>

</ul>
</body>
</html>
