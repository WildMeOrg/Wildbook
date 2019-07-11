/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean;

import org.json.JSONObject;
import org.json.JSONArray;
import javax.jdo.Query;
import java.util.Collection;
//import java.util.Vector;
//import org.apache.commons.lang3.builder.ToStringBuilder;

public class CatTest {
    private int id;
    private String username;
    private String trial;
    private long timestamp;
    private String results;

    public CatTest() {
    }

    public CatTest(String username, String trial, String results) {
        this.username = username;
        this.trial = trial;
        this.results = results;
        this.timestamp = System.currentTimeMillis();
    }


    public int getId() {
        return id;
    }
    public String getUsername() {
        return username;
    }
    public String getTrial() {
        return trial;
    }
    public long getTimestamp() {
        return timestamp;
    }

    public JSONArray getResultsAsJSONArray() {
        if (results == null) return null;
        return new JSONArray(results);
    }


    public static String getCurrentTrial(Shepherd myShepherd) {
        //NOTE: behavior change: "current trial" is now one randomly chose ref image *which the user has not yet done*
        /*
        Config conf = Config.load("currentTrial", myShepherd);
        if ((conf == null) || (conf.getValue() == null)) return null;
        return conf.getValue().optString("name", null);
        */
        return null;
    }
    public static void setCurrentTrial(String trial, Shepherd myShepherd) {
        Config conf = Config.load("currentTrial", myShepherd);
        if (conf == null) conf = new Config("currentTrial", null);
        JSONObject v = conf.getValue();
        v.put("name", trial);
        conf.setValue(v);
        Config.save(conf, myShepherd);
    }

    public static boolean isModeLive(Shepherd myShepherd) {
        Config conf = Config.load("modeLive", myShepherd);
        if ((conf == null) || (conf.getValue() == null)) return false;
        return conf.getValue().optBoolean("isLive", false);
    }
    public static void setModeLive(boolean isLive, Shepherd myShepherd) {
        Config conf = Config.load("modeLive", myShepherd);
        if (conf == null) conf = new Config("modeLive", null);
        JSONObject v = conf.getValue();
        v.put("isLive", isLive);
        conf.setValue(v);
        Config.save(conf, myShepherd);
    }

    public static CatTest save(Shepherd myShepherd, String username, String trial, String results) {
        CatTest c = new CatTest(username, trial, results);
        myShepherd.getPM().makePersistent(c);
        return c;
    }


    public static boolean trialAvailableToUser(Shepherd myShepherd, String username) {
        return trialAvailableToUser(myShepherd, username, getCurrentTrial(myShepherd));
    }
    public static boolean trialAvailableToUser(Shepherd myShepherd, String username, String trialName) {
        if (username == null) return false;
        Query q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.CatTest WHERE trial == '" + trialName + "' && username == '" + username + "'");
        Collection c = (Collection)q.execute();
//System.out.println(c.size());
        return (c.size() < 1);
    }

    public static JSONArray trialsTakenByUser(Shepherd myShepherd, String username) {
        JSONArray tr = new JSONArray();
        if (username == null) return tr;
        Query q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.CatTest WHERE username == '" + username + "'");
        Collection c = (Collection)q.execute();
        for (Object o : c) {
            CatTest t = (CatTest)o;
            tr.put(t.trial);
        }
        return tr;
    }
/*
    public String toString() {
        return new ToStringBuilder(this)
                .append(indexname)
                .append(readableName)
                .toString();
    }
*/

}
