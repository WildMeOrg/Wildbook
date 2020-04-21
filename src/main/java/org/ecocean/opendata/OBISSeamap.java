package org.ecocean.opendata;

import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.CommonConfiguration;
import org.ecocean.Taxonomy;
import org.ecocean.Survey;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.PointLocation;
import org.ecocean.media.MediaAsset;
import org.ecocean.security.Collaboration;
import org.ecocean.movement.SurveyTrack;
import org.ecocean.movement.Path;
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

    //this is the default attribution
    private static final String CONTRIBUTOR = "Flukebook.org";

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
        String effortOutPath = outPath + "_effort";  //fallback
        int dot = outPath.lastIndexOf(".");
        if (dot > 0) effortOutPath = outPath.substring(0,dot) + "_effort." + outPath.substring(dot+1);
        BufferedWriter writer = new BufferedWriter(new FileWriter(outPath));
        BufferedWriter effortWriter = new BufferedWriter(new FileWriter(effortOutPath));

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction(this.typeCode() + ".generate");
        myShepherd.beginDBTransaction();

        // here we want to export all Occurrences (and their Encounters), and then Occurrence-less Encounters as well
        if (occurrence_jdoql == null) occurrence_jdoql = "SELECT FROM org.ecocean.Occurrence";
        Query query = myShepherd.getPM().newQuery(occurrence_jdoql);
        Collection c = (Collection) (query.execute());
        List<Occurrence> occs = new ArrayList<Occurrence>(c);
        query.closeAll();
        List<String> surveyTrackIds = new ArrayList<String>();
        for (Occurrence occ : occs) {
            if (!isShareable(occ)) continue;
            SurveyTrack trk = occ.getSurveyTrack(myShepherd);
            String surveyTrackId = (trk == null) ? null : trk.getID();
            //TODO do we need to check isShareable(trk) at this point??? think not cuz it references back to occ
            if ((trk != null) && !surveyTrackIds.contains(surveyTrackId)) {
                String trow = tabRow(trk, myShepherd);
                if (trow != null) {
                    effortWriter.write(trow);
                } else {
                    surveyTrackId = null;  //dont reference via Occurrence data
                }
                surveyTrackIds.add(trk.getID());
            }
            String row = tabRow(occ, surveyTrackId, myShepherd);
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
        effortWriter.close();
        myShepherd.rollbackDBTransaction();
        log(outPath + " and " + effortOutPath + " written by generate()");
    }

    public boolean isShareable(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Encounter) return isShareable((Encounter)obj);
        if (obj instanceof Occurrence) return isShareable((Occurrence)obj);
        if (obj instanceof SurveyTrack) return isShareable((SurveyTrack)obj);
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

    public boolean isShareable(SurveyTrack trk) {
        if (trk == null) return false;
        if (getShareAll()) return true;
        if (Util.collectionIsEmptyOrNull(trk.getOccurrences())) return false;
        for (Occurrence occ : trk.getOccurrences()) {
            if (occ == null) continue;
            if (!isShareable(occ)) return false;
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
        fields.add(toISO8601(d));
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

    public String tabRow(Occurrence occ, String surveyTrackId, Shepherd myShepherd) {
        if ((occ == null) || Util.collectionIsEmptyOrNull(occ.getEncounters())) return null;
        String rtn = "";
        for (Encounter enc : occ.getEncounters()) {
            String e = tabRow(enc, surveyTrackId, myShepherd);
            if (e != null) rtn += e;
        }
        return rtn;
    }

//header of field contents
//GUID	DATE	OCCURRENCE_ID	SURVEY_ID   DEC_LAT	DEC_LON	TAXONOMY	INDIV_ID	SEX	LIFE_STAGE	IMAGE_URL	CONTRIBUTERS	COPYRIGHT_INFO
    public String tabRow(Encounter enc, Shepherd myShepherd) {
        return tabRow(enc, null, myShepherd);
    }
    public String tabRow(Encounter enc, String surveyTrackId, Shepherd myShepherd) {
        if (enc == null) return null;
        List<String> fields = new ArrayList<String>();
        //fields.add(getGUID("E-" + enc.getCatalogNumber()));  //decided now to have url/link be "guid" (via feedback from ei)
        fields.add(Encounter.getWebUrl(enc.getCatalogNumber(), CommonConfiguration.getServerURL(myShepherd)));
        String d = enc.getDate();
        if (!Util.stringExists(d)) {
            log("cannot share " + enc + " due to invalid date!");
            return null;
        }
        fields.add(d);
        fields.add(forceString(enc.getOccurrenceID()));
        fields.add(forceString(surveyTrackId));
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
            ArrayList<MediaAsset> kids = mas.get(0).findChildrenByLabel(myShepherd, "_mid");
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
        fields.add(CONTRIBUTOR);
        fields.add(getProperty("copyright", null));
        return String.join("\t", fields) + "\n";
    }


    public String tabRow(SurveyTrack trk, Shepherd myShepherd) {
        if (trk == null) return null;
        List<String> fields = new ArrayList<String>();
        fields.add(forceString(trk.getID()));
        fields.add(toISO8601(trk.getStartTime()));
        fields.add(toISO8601(trk.getEndTime()));

        List<String> points = new ArrayList<String>();
        Path path = trk.getPath();
        if (path != null) {
            //wanna keep 5 min apart, per obis request
            for (PointLocation pl : path.getPointLocationsSubsampledTimeGap(5L * 60000L)) {
                String dt = pl.getDateTimeAsString();
                Double lat = pl.getLatitude();
                Double lon = pl.getLongitude();
                if ((dt != null) && (lat != null) && (lon != null)) points.add("(" + dt + ";" + lat + ";" + lon + ")");
            }
        }
        if (points.size() < 1) return null;  //boring
        fields.add(String.join(", ", points));

        return String.join("\t", fields) + "\n";
    }


    private static String forceString(String txt) {
        if (!Util.stringExists(txt)) return "";  //this checks for "unknown", "none", etc...
        return txt;
    }

    private static String toISO8601(Long millis) {
        if (millis == null) return "";
        return (new DateTime(millis)).toString().substring(0,16).replace("T", " ");
    }
}
