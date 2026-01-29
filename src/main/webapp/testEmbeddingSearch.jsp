<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="

com.pgvector.PGvector,
org.ecocean.shepherd.core.Shepherd,
java.util.*, org.ecocean.*,org.ecocean.servlet.*
"%>

<%!
private String annInfo(Annotation ann, Shepherd myShepherd) {
	String out = "<ul>";
	out += "<li>Annotation <a target=\"_blank\" href=\"/obrowse.jsp?type=Annotation&id=" + ann.getId() + "\">" + ann.getId() + "</a> viewpoint=" + ann.getViewpoint() + " " + ann + "</li>";
        Encounter enc = ann.findEncounter(myShepherd);
        if (enc == null) {
            out += "<li>(unknown encounter)</li><li>(unknown individual)</li>";
        } else {
            out += "<li>Encounter <a target=\"_blank\" href=\"/encounters/encounter.jsp?number=" + enc.getId() + "\">" + enc.getId() + "</a> " + enc + "</li>";
            MarkedIndividual indiv = enc.getIndividual();
            if (indiv == null) {
                out += "<li>(unknown individual)</li>";
            } else {
                out += "<li><b>Individual <a target=\"_blank\" href=\"/individuals.jsp?number=" + indiv.getId() + "\">" + indiv.getId() + "</a></b> " + indiv + "</li>";
            }
        }
	out += "</ul>";
	return out;
}
%>

<%

Shepherd myShepherd=new Shepherd(request);

Annotation ann = myShepherd.getAnnotation(request.getParameter("id"));
if (ann == null) {
    out.println("<h2>unknown id <br /> pass ?id=ANNOTATION_ID on url</h2>");
    return;
}

out.println("<p><h2>query annot:</h2> " + annInfo(ann, myShepherd) + "</p>");

String[] methodVersions = new String[]{"v2", "v3"};
for (String methodVersion : methodVersions) {
    out.println("<hr /><h2>results (miewID " + methodVersion + "):</h2>");

    //List<Annotation> matches = ann.getMatches(myShepherd, null, false, null, null); 
    List<Annotation> matches = ann.getMatches(myShepherd, null, false, "miewID", methodVersion);
    for (Annotation mann : matches) {
            out.println("<p>" + annInfo(mann, myShepherd) + "</p>");
    }
}

myShepherd.rollbackAndClose();

%>

