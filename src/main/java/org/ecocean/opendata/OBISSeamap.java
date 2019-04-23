package org.ecocean.opendata;

import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.Taxonomy;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.security.Collaboration;
import javax.jdo.Query;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class OBISSeamap extends Share {

    public OBISSeamap(final String context) {
        super(context);
        this.init();
    }

    public void init() {
        if (!isEnabled()) {
            log("not enabled; exiting init()");
            return;
        }
        //do these once to get into cache
        getCollaborationUser();
        getShareAll();
        //TODO support organizationId (when Organization makes it to master!)
    }


    public void generate() throws IOException {
        String outPath = getProperty("outputFile", null);
        if (outPath == null) throw new IllegalArgumentException("must have 'outputFile' set in properties file");
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction(this.typeCode() + ".generate");
        myShepherd.beginDBTransaction();
        // here we want to export all Occurrences (and their Encounters), and then Occurrence-less Encounters as well
        String jdoql = "SELECT FROM org.ecocean.Occurrence";
        Query query = myShepherd.getPM().newQuery(jdoql);
        Collection c = (Collection) (query.execute());
        List<Occurrence> occs = new ArrayList<Occurrence>(c);
        query.closeAll();
        myShepherd.rollbackDBTransaction();
        BufferedWriter writer = new BufferedWriter(new FileWriter(outPath));
        for (Occurrence occ : occs) {
            if (!isShareable(occ)) continue;
            String row = tabRow(occ);
            if (row != null) writer.write(row);
        }

//select count(*) from "ENCOUNTER" left join "OCCURRENCE_ENCOUNTERS" on ("ENCOUNTER"."CATALOGNUMBER" = "OCCURRENCE_ENCOUNTERS"."CATALOGNUMBER_EID") where "OCCURRENCE_ENCOUNTERS"."OCCURRENCEID_OID" is null;
        
        writer.close();
        log(outPath + " written by generate()");
    }

    public boolean isShareable(Object obj) {
System.out.println("isShareable-OBJ: " + obj);
        if (obj == null) return false;
        if (obj instanceof Encounter) return isShareable((Encounter)obj);
        if (obj instanceof Occurrence) return isShareable((Occurrence)obj);
        return false;
    }


    public boolean isShareable(Encounter enc) {
        if (enc == null) return false;
        if (getShareAll()) return true;
System.out.println("isShareable-ENC: " + enc);
        User cu = getCollaborationUser();
        if ((cu != null) && Util.stringExists(cu.getUsername()))
            return Collaboration.canUserAccessEncounter(enc, context, cu.getUsername()); 
        return false;
    }

    public boolean isShareable(Occurrence occ) {
        if (occ == null) return false;
        if (getShareAll()) return true;
System.out.println("isShareable-OCC: " + occ);
        if ((occ.getEncounters() == null) || (occ.getEncounters().size() < 1)) return false;
        for (Encounter enc : occ.getEncounters()) {
            if (!isShareable(enc)) return false;
        }
        return true;
    }


    //these are the row (record) for tab-delim output; assuming OBISSeamap flat-file Darwin Core
    //  NOTE: these do not include trailing newline
    public String tabRow(Occurrence occ) {
        if (occ == null) return null;
        List<String> fields = new ArrayList<String>();
        fields.add(getGUID(occ.getOccurrenceID()));
        Taxonomy tx = occ.getTaxonomy();
        if ((tx == null) || (tx.getScientificName() == null)) {
            log("cannot share " + occ + " due to invalid Taxonomy!");
            return null;
        }
        fields.add(tx.getScientificName());
        return String.join("\t", fields);
    }

    public String tabRow(Encounter enc) {
        if (enc == null) return null;
        List<String> fields = new ArrayList<String>();
        fields.add(getGUID(enc.getCatalogNumber()));
        if (!Util.stringExists(enc.getTaxonomyString())) {
            log("cannot share " + enc + " due to invalid Taxonomy!");
            return null;
        }
        fields.add(enc.getTaxonomyString());
        return String.join("\t", fields);
    }

}
