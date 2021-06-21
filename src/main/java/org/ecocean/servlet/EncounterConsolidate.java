/**
 * Methods associated with consolidating encounters with wonky metadata of some sort or another
 *
 * @author mfisher
 */

 package org.ecocean.servlet;
 import org.ecocean.servlet.ServletUtilities;

 import org.ecocean.*;

 import com.oreilly.servlet.multipart.FilePart;
 import com.oreilly.servlet.multipart.MultipartParser;
 import com.oreilly.servlet.multipart.ParamPart;
 import com.oreilly.servlet.multipart.Part;
 import javax.servlet.ServletConfig;
 import javax.servlet.ServletException;
 import javax.servlet.http.HttpServlet;
 import javax.servlet.http.HttpServletRequest;
 import javax.servlet.http.HttpServletResponse;
 import java.util.*;
 import java.io.File;
 import java.io.IOException;
 import java.io.PrintWriter;
 import java.util.Properties;
 import org.ecocean.*;
 import org.ecocean.servlet.*;
 // import javax.jdo.Query;
 import javax.jdo.*;
 import org.json.JSONObject;

public class EncounterConsolidate{
  public static JSONObject makeEncountersMissingSubmittersPublic(Shepherd myShepherd){
    JSONObject returnJson = new JSONObject();
    returnJson.put("success", false);
    int encountersMadePublicCount = 0;
  	String filter="SELECT FROM org.ecocean.Encounter where this.submitterID==\"N/A\" || this.submitterID==null"; //.contains(null)" //&& submitters.isEmpty()
  	List<Encounter> encs=new ArrayList<Encounter>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    System.out.println("dedupe: " + c.size() + " encounters found with missing submitterIds");
    if(c!=null){
      encs=new ArrayList<Encounter>(c);
      for(int i=0; i<encs.size(); i++){
        Encounter currentEncounter = encs.get(i);
        renameEncounterSubmitterID(myShepherd, currentEncounter, "Public");
        encountersMadePublicCount ++;
      }
    }
    query.closeAll();
    returnJson.put("encountersMadePublicCount", encountersMadePublicCount);
    returnJson.put("success", true);
    return returnJson;
  }

  public static void renameEncounterSubmitterID(Shepherd myShepherd, Encounter enc, String newSubmitterId){
    if(newSubmitterId == null || newSubmitterId.equals("") || enc == null){
      return;
    }
    System.out.println("dedupe a reassigning encounter " + enc.getCatalogNumber() + " to submitter " + newSubmitterId);
    enc.setSubmitterID(newSubmitterId.toLowerCase().trim());
    myShepherd.getPM().makePersistent(enc);
    myShepherd.updateDBTransaction();
  }
}
