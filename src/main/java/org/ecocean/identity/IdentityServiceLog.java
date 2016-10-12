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

package org.ecocean.identity;

import org.ecocean.Shepherd;
import javax.jdo.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Calendar;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.apache.commons.lang3.builder.ToStringBuilder;


public class IdentityServiceLog implements java.io.Serializable {
    static final long serialVersionUID = -1020952058531486782L;

    private String taskID;
    private long timestamp;
    private String serviceName;
    private String serviceJobID;
    private String[] objectIDs;
    private String status;


    //probably (?) we will standardize on  "status" actually being a json object, so this will likely be the main constructor
    public IdentityServiceLog(String taskID, String[] objectIDs, String serviceName, String serviceJobID, JSONObject jstatus) {
        this(taskID, objectIDs, serviceName, serviceJobID, jstatus.toString());
    }

    public IdentityServiceLog(String taskID, String serviceName, String serviceJobID, JSONObject jstatus) {
        this(taskID, null, serviceName, serviceJobID, jstatus.toString());
    }

    public IdentityServiceLog(String taskID, String[] objectIDs, String serviceName, String serviceJobID, String status) {
        this.taskID = taskID;
        this.objectIDs = objectIDs;
        this.serviceName = serviceName;
        this.serviceJobID = serviceJobID;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }
    public IdentityServiceLog(String taskID, String objectID, String serviceName, String serviceJobID, JSONObject jstatus) {
        String[] sa = new String[1];
        sa[0] = objectID;
        this.taskID = taskID;
        this.objectIDs = sa;
        this.serviceName = serviceName;
        this.serviceJobID = serviceJobID;
        this.status = jstatus.toString();
        this.timestamp = System.currentTimeMillis();
    }

    public void setTaskID(String t) {
        taskID = t;
    }
    public String getTaskID() {
        return taskID;
    }
    public void setServiceName(String n) {
        serviceName = n;
    }
    public String getServiceName() {
        return serviceName;
    }
    public void setServiceJobID(String j) {
        serviceJobID = j;
    }
    public String getServiceJobID() {
        return serviceJobID;
    }
    public void setTimestamp(long t) {
        timestamp = t;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public void setStatus(String s) {
        status = s;
    }
    public String getStatus() {
        return status;
    }
    public String[] getObjectIDs() {
        return objectIDs;
    }

    public JSONObject getStatusJson() {
        try {
            return new JSONObject(status);
        } catch (JSONException je) {
            System.out.println(this + " -- error parsing status json string (" + status + "): " + je.toString());
            return null;
        }
    }

    public static ArrayList<IdentityServiceLog> load(String taskID, String serviceName, String serviceJobID, Shepherd myShepherd) {
        Extent cls = myShepherd.getPM().getExtent(IdentityServiceLog.class, true);
        Query qry = myShepherd.getPM().newQuery(cls, "this.taskID == \"" + taskID + "\" && this.serviceName == \"" + serviceName + "\" && this.serviceJobID == \"" + serviceJobID + "\"");
        qry.setOrdering("timestamp");
        ArrayList<IdentityServiceLog> log=new ArrayList<IdentityServiceLog>();
        try {
            Collection coll = (Collection) (qry.execute());
            log= new ArrayList<IdentityServiceLog>(coll);
        } 
        catch (Exception ex) {
            //return new ArrayList<IdentityServiceLog>();
          ex.printStackTrace();
        }
        qry.closeAll();
        return log;
    }

    public static ArrayList<IdentityServiceLog> loadByTaskID(String taskID, String serviceName, Shepherd myShepherd) {
      //Shepherd myShepherd=new Shepherd(context); 
      //myShepherd.setAction("IdentityServiceLog.loadByTaskID");
      //myShepherd.beginDBTransaction();
      Extent cls = myShepherd.getPM().getExtent(IdentityServiceLog.class, true);
        Query qry = myShepherd.getPM().newQuery(cls, "this.taskID == \"" + taskID + "\" && this.serviceName == \"" + serviceName + "\"");
        qry.setOrdering("timestamp");
        ArrayList<IdentityServiceLog> log=new ArrayList<IdentityServiceLog>();
        try {
            Collection coll = (Collection) (qry.execute());
            log=new ArrayList<IdentityServiceLog>(coll);
        } 
        catch (Exception ex) {
            //return new ArrayList<IdentityServiceLog>();
          ex.printStackTrace();
        }
        qry.closeAll();
        //myShepherd.rollbackDBTransaction();
        //myShepherd.closeDBTransaction();
        return log;
    }

    public static ArrayList<IdentityServiceLog> loadByServiceJobID(String serviceName, String serviceJobID, Shepherd myShepherd) {
//System.out.println("serviceName=(" + serviceName + ") serviceJobID=(" + serviceJobID + ")");
        Extent cls = myShepherd.getPM().getExtent(IdentityServiceLog.class, true);
        Query qry = myShepherd.getPM().newQuery(cls, "this.serviceName == \"" + serviceName + "\" && this.serviceJobID == \"" + serviceJobID + "\"");
        qry.setOrdering("timestamp");
        ArrayList<IdentityServiceLog> log=new ArrayList<IdentityServiceLog>();
        try {
            Collection coll = (Collection) (qry.execute());
            log=new ArrayList<IdentityServiceLog>(coll);
        } 
        catch (Exception ex) {
            //return new ArrayList<IdentityServiceLog>();
          ex.printStackTrace();
        }
        qry.closeAll();
        return log;
    }


    public static ArrayList<IdentityServiceLog> loadByObjectID(String serviceName, String objectID, Shepherd myShepherd) {
//System.out.println("serviceName=(" + serviceName + ") serviceJobID=(" + serviceJobID + ")");
        Extent cls = myShepherd.getPM().getExtent(IdentityServiceLog.class, true);
        //Query qry = myShepherd.getPM().newQuery(cls, "this.serviceName == \"" + serviceName + "\" && this.objectIDs.contains(\"" + objectID + "\")");
        Query qry = myShepherd.getPM().newQuery(cls, "this.serviceName == \"" + serviceName + "\"");
        qry.setOrdering("timestamp");
        ArrayList<IdentityServiceLog> log=new ArrayList<IdentityServiceLog>();
        try {
            Collection coll = (Collection) (qry.execute());
            for (Object c : coll) {
                IdentityServiceLog l = (IdentityServiceLog)c;
                if (l.hasObjectID(objectID)) log.add(l);
            }
            //log=new ArrayList<IdentityServiceLog>(coll);
        } 
        catch (Exception ex) {
            //return new ArrayList<IdentityServiceLog>();
          ex.printStackTrace();
        }
        qry.closeAll();
        return log;
    }


    //loads only most recent task's worth of log items; note: it is in newest-first order (unlike most log returns)
    public static ArrayList<IdentityServiceLog> loadMostRecentByObjectID(String serviceName, String objectID, Shepherd myShepherd) {
//System.out.println("serviceName=(" + serviceName + ") serviceJobID=(" + serviceJobID + ")");
        Extent cls = myShepherd.getPM().getExtent(IdentityServiceLog.class, true);
        Query qry = myShepherd.getPM().newQuery(cls, "this.serviceName == \"" + serviceName + "\"");
        qry.setOrdering("timestamp DESC");
        String recentTaskId = null;
        ArrayList<IdentityServiceLog> log=new ArrayList<IdentityServiceLog>();
        try {
            Collection coll = (Collection) (qry.execute());
            for (Object c : coll) {
                IdentityServiceLog l = (IdentityServiceLog)c;
                if (l.hasObjectID(objectID)) {
                    recentTaskId = l.getTaskID();
                    break;
                }
            }
            if (recentTaskId == null) return null;
            for (Object c : coll) {
                IdentityServiceLog l = (IdentityServiceLog)c;
                if (recentTaskId.equals(l.getTaskID())) log.add(l);
            }
        } 
        catch (Exception ex) {
            //return new ArrayList<IdentityServiceLog>();
          ex.printStackTrace();
        }
        qry.closeAll();
        return log;
    }


/*
 IDENTITYSERVICELOG_ID | SERVICEJOBID | SERVICENAME |       STATUS       |                TASKID                |   TIMESTAMP   |               OBJECTID               
-----------------------+--------------+-------------+--------------------+--------------------------------------+---------------+--------------------------------------
                   737 | -1           | IBEISIA     | {"_action":"init"} | 6bc34656-0847-4a80-b093-2a52481d8b22 | 1459307111383 | fd5b557e-337c-4239-9625-dfacc8822550
                   739 | -1           | IBEISIA     | {"_action":"init"} | 8fce30ef-a8e2-4a4f-8830-5a3273df9baf | 1459307241643 | fd5b557e-337c-4239-9625-dfacc8822550
                   745 | -1           | IBEISIA     | {"_action":"init"} | 32bb426a-a03d-4386-bbbf-15d6259ef732 | 1459308840326 | fd5b557e-337c-4239-9625-dfacc8822550
(3 rows)

(END)*/

    public static ArrayList<IdentityServiceLog> summaryForAnnotationId(String annId, Shepherd myShepherd) {
        Extent cls = myShepherd.getPM().getExtent(IdentityServiceLog.class, true);
        Query qry = myShepherd.getPM().newQuery(cls, "this.serviceName == \"IBEISIA\"");
        qry.setOrdering("timestamp");
        HashMap<String,IdentityServiceLog> lmap = new HashMap<String,IdentityServiceLog>();
        try {
            Collection coll = (Collection) (qry.execute());
            //log=new ArrayList<IdentityServiceLog>(coll);
            for (Object c : coll) {
                IdentityServiceLog log = (IdentityServiceLog)c;
                if (log.getTaskID() == null) continue;
                if (log.hasObjectID(annId) || (lmap.get(log.getTaskID()) != null)) {
                    lmap.put(log.getTaskID(), log);
                }
            }
        } 
        catch (Exception ex) {
            //return new ArrayList<IdentityServiceLog>();
          ex.printStackTrace();
        }
        qry.closeAll();
        return new ArrayList<IdentityServiceLog>(lmap.values());
    }


    public boolean hasObjectID(String oid) {
        String[] sa = this.getObjectIDs();
        if (sa == null) return false;
        return Arrays.asList(sa).contains(oid);
    }

    //this is the pass-in-log-lines version ... could make similar for from-db version
    // thus, this assumes these are already only for a given taskId
    public static String[] findObjectIDs(ArrayList<IdentityServiceLog> logs) {
        for (IdentityServiceLog l : logs) {
            if ((l.getObjectIDs() != null) && (l.getObjectIDs().length > 0)) return l.getObjectIDs();
        }
        return null;
    }


//S3AssetStore s3as = ((S3AssetStore) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(S3AssetStore.class, 3), true)));

    public void save(Shepherd myShepherd) {
        myShepherd.getPM().makePersistent(this);
    }

/*
    public static void save(IdentityServiceLog l, Shepherd myShepherd) {
        myShepherd.getPM().makePersistent(l);
    }
*/

    public JSONObject toJSONObject() {
        JSONObject j = new JSONObject();
        j.put("serviceName", this.getServiceName());
        j.put("serviceJobId", this.getServiceJobID());
        j.put("taskId", this.getTaskID());
        j.put("timestamp", this.getTimestamp());
        if (this.getObjectIDs() != null) j.put("objectIds", new JSONArray(this.getObjectIDs()));
        j.put("status", this.getStatusJson());
        return j;
    }

    //probably (?) we will standardize on  "status" actually being a json object, so this will likely be the main constructor
    public String toString() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        return new ToStringBuilder(this)
                .append("timestamp", timestamp)
                .append("date/time", c.getTime().toString())
                .append("taskID", taskID)
                .append("objectIDs", Arrays.toString(objectIDs))
                .append("serviceName", serviceName)
                .append("serviceJobID", serviceJobID)
                .append("status", status)
                .toString();
    }


}
