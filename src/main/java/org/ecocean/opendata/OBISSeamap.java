package org.ecocean.opendata;

import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.Taxonomy;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.media.MediaAsset;
import org.ecocean.security.Collaboration;
import javax.jdo.Query;
import java.util.Collection;
import java.util.List;
import java.net.URL;
import java.util.ArrayList;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import org.joda.time.DateTime;

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
        generate(null, null);
    }
    //yeah, one is jdoql, one is sql.  sigh.
    public void generate(String occurrence_jdoql, String encounter_sql) throws IOException {
        String outPath = getProperty("outputFile", null);
        if (outPath == null) throw new IllegalArgumentException("must have 'outputFile' set in properties file");
        BufferedWriter writer = new BufferedWriter(new FileWriter(outPath));

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction(this.typeCode() + ".generate");
        myShepherd.beginDBTransaction();

        // here we want to export all Occurrences (and their Encounters), and then Occurrence-less Encounters as well
        if (occurrence_jdoql == null) occurrence_jdoql = "SELECT FROM org.ecocean.Occurrence";
        Query query = myShepherd.getPM().newQuery(occurrence_jdoql);
        Collection c = (Collection) (query.execute());
        List<Occurrence> occs = new ArrayList<Occurrence>(c);
        query.closeAll();
        for (Occurrence occ : occs) {
            if (!isShareable(occ)) continue;
            String row = tabRow(occ, myShepherd);
            if (row != null) writer.write(row);
        }

        // cant figure out how to do this via jdoql.  :/
        if (encounter_sql == null) encounter_sql = "SELECT * FROM \"ENCOUNTER\" LEFT JOIN \"OCCURRENCE_ENCOUNTERS\" ON (\"ENCOUNTER\".\"CATALOGNUMBER\" = \"OCCURRENCE_ENCOUNTERS\".\"CATALOGNUMBER_EID\") WHERE \"OCCURRENCE_ENCOUNTERS\".\"OCCURRENCEID_OID\" IS NULL";
        query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", encounter_sql);
        query.setClass(Encounter.class);
        c = (Collection) (query.execute());
        List<Encounter> encs = new ArrayList<Encounter>(c);
        query.closeAll();
        for (Encounter enc : encs) {
            if (!isShareable(enc)) continue;
            String row = tabRow(enc, myShepherd);
            if (row != null) writer.write(row);
        }

        writer.close();
        myShepherd.rollbackDBTransaction();
        log(outPath + " written by generate()");
    }

    public boolean isShareable(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Encounter) return isShareable((Encounter)obj);
        if (obj instanceof Occurrence) return isShareable((Occurrence)obj);
        return false;
    }


    public boolean isShareable(Encounter enc) {
        if (enc == null) return false;
        if (getShareAll()) return true;
        User cu = getCollaborationUser();
        if ((cu != null) && Util.stringExists(cu.getUsername()) && Collaboration.canUserAccessEncounter(enc, context, cu.getUsername()))
            return true;
        if (isShareOrganizationUser(enc.getSubmitters())) return true;
        return false;
    }

    public boolean isShareable(Occurrence occ) {
        if (occ == null) return false;
        if (getShareAll()) return true;
        if ((occ.getEncounters() == null) || (occ.getEncounters().size() < 1)) return false;
        for (Encounter enc : occ.getEncounters()) {
            if (!isShareable(enc)) return false;
        }
        return true;
    }


    //these are the row (record) for tab-delim output; assuming OBISSeamap flat-file Darwin Core
    //  NOTE: these do not include trailing newline
    public String tabRowOLD(Occurrence occ, Shepherd myShepherd) {
        if (occ == null) return null;
        List<String> fields = new ArrayList<String>();
        fields.add(getGUID("O-" + occ.getOccurrenceID()));
        Long d = occ.getDateTimeLong();
        if (d == null) d = occ.getMillisFromEncounters();
        if (d == null) {
            log("cannot share " + occ + " due to invalid date!");
            return null;
        }
        fields.add((new DateTime(d)).toString().substring(0,16).replace("T", " "));
        occ.setLatLonFromEncs(false);
        Double dlat = occ.getDecimalLatitude();
        Double dlon = occ.getDecimalLongitude();
        if ((dlat == null) || (dlon == null)) {
            log("cannot share " + occ + " due to invalid lat/lon!");
            return null;
        }
        fields.add(Double.toString(dlat));
        fields.add(Double.toString(dlon));
        Taxonomy tx = occ.getTaxonomy();  //this often fails.  :(
        String txString = null;
        if (tx != null) txString = tx.getScientificName();
        if ((txString == null) && (occ.getEncounters() != null)) {
            for (Encounter enc : occ.getEncounters()) {
                txString = enc.getTaxonomyString();
            }
        }
        if (txString == null) {
            log("cannot share " + occ + " due to invalid Taxonomy!");
            return null;
        }
        fields.add(txString);
        fields.add(Integer.toString(occ.getGroupSizeCalculated()));
        MediaAsset ma = occ.getRepresentativeMediaAsset();
        if (ma == null) {
            fields.add("");
        } else {
            ArrayList<MediaAsset> kids = ma.findChildrenByLabel(myShepherd, "_watermark");
            if ((kids == null) || (kids.size() < 1)) {
                fields.add("");
            } else {
                URL u = kids.get(0).webURL();
                if (u == null) {
                    fields.add("");
                } else {
                    fields.add(u.toString());
                }
            }
        }
        return String.join("\t", fields);
    }

    public String tabRow(Occurrence occ, Shepherd myShepherd) {
        if ((occ == null) || Util.collectionIsEmptyOrNull(occ.getEncounters())) return null;
        String rtn = "";
        for (Encounter enc : occ.getEncounters()) {
            String e = tabRow(enc, myShepherd);
            if (e != null) rtn += e;
        }
        return rtn;
    }

//header of field contents
//GUID	DATE	OCCURRENCE_ID	DEC_LAT	DEC_LON	TAXONOMY	INDIV_ID	SEX	LIFE_STAGE	IMAGE_URL	CONTRIBUTERS	COPYRIGHT_INFO
    public String tabRow(Encounter enc, Shepherd myShepherd) {
        if (enc == null) return null;
        List<String> fields = new ArrayList<String>();
        fields.add(getGUID("E-" + enc.getCatalogNumber()));
        String d = enc.getDate();
        if (!Util.stringExists(d)) {
            log("cannot share " + enc + " due to invalid date!");
            return null;
        }
        fields.add(d);
        fields.add(forceString(enc.getOccurrenceID()));
        Double dlat = enc.getLatitudeAsDouble();
        Double dlon = enc.getLongitudeAsDouble();
        if ((dlat == null) || (dlon == null)) {
            log("cannot share " + enc + " due to invalid lat/lon!");
            return null;
        }
        fields.add(Double.toString(dlat));
        fields.add(Double.toString(dlon));
        if (!Util.stringExists(enc.getTaxonomyString())) {
            log("cannot share " + enc + " due to invalid Taxonomy!");
            return null;
        }
        fields.add(enc.getTaxonomyString());
        fields.add(forceString(enc.getIndividualID()));
        fields.add(forceString(enc.getSex()));
        fields.add(forceString(enc.getLifeStage()));
        //////fields.add("1");  //for encounter, always just one individual
        ArrayList<MediaAsset> mas = enc.getMedia();
        if ((mas == null) || (mas.size() < 1)) {
            fields.add("");
        } else {
            ArrayList<MediaAsset> kids = mas.get(0).findChildrenByLabel(myShepherd, "_thumb");
            if ((kids == null) || (kids.size() < 1)) {
                fields.add("");
            } else {
                URL u = kids.get(0).webURL();
                if (u == null) {
                    fields.add("");
                } else {
                    fields.add(u.toString());
                }
            }
        }
        if (Util.collectionIsEmptyOrNull(enc.getSubmitters())) {
            fields.add("");
        } else {
            List<String> names = new ArrayList<String>();
            for (User u : enc.getSubmitters()) {
                if (u.getFullName() != null) names.add(u.getFullName());
            }
            fields.add(String.join(", ", names));
        }
        fields.add("[flukebook copyright?]");
        return String.join("\t", fields) + "\n";
    }


    private static String forceString(String txt) {
        if (!Util.stringExists(txt)) return "";  //this checks for "unknown", "none", etc...
        return txt;
    }
}
