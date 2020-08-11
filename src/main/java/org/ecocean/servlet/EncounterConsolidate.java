/**
 * Manually consolidates user encounter information on the basis of a provided username (the one that will have ownship transferred to it`)
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

public class EncounterConsolidate{
  public static void makeEncountersMissingSubmittersPublic(Shepherd myShepherd){
    System.out.println("makeEncountersMissingSubmitterIdsPublic entered");
  	String filter="SELECT FROM org.ecocean.Encounter where this.submitterID==\"N/A\" || this.submitterID==null && submitters.isEmpty()"; //.contains(null)"
  	ArrayList<Encounter> encs=new ArrayList<Encounter>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    System.out.println(c.size() + " encounters found with missing submitterIds");
    if(c!=null){
      encs=new ArrayList<Encounter>(c);
      for(int i=0; i<encs.size(); i++){
        Encounter currentEncounter = encs.get(i);
        List<User> submitters = currentEncounter.getSubmitters();
        // if(i==0 || i==encs.size()-1){ //TODO temporary just to streamline printing
        //   System.out.println("adding public as submitter id to encounter: " + currentEncounter.getCatalogNumber() + " with submitterId " + currentEncounter.getSubmitterID());
        //   System.out.println("submitters in encounter include the likes of:");
        //   for(int j=0; j<submitters.size(); j++){
        //     if(j==0 || j==submitters.size()-1){
        //       System.out.println(submitters.get(j).getUsername());
        //     }
        //   }
        // }
        renameEncounterSubmitterID(myShepherd, currentEncounter, "Public");
      }
    }
    query.closeAll();
  }

  public static void renameEncounterSubmitterID(Shepherd myShepherd, Encounter enc, String newSubmitterId){
    // System.out.println("renameEncounterSubmitterID entered");
    enc.setSubmitterID(newSubmitterId.toLowerCase().trim()); //TODO comment me in when you want this live
    myShepherd.commitDBTransaction(); //TODO comment me in when you want this live
    myShepherd.beginDBTransaction(); //TODO comment me in when you want this live
  }
}
