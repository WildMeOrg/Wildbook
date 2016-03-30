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
import java.util.Collection;
import java.util.Calendar;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.commons.lang3.builder.ToStringBuilder;


public class IdentityServiceLog implements java.io.Serializable {
    static final long serialVersionUID = -1020952058531486782L;

    private String taskID;
    private long timestamp;
    private String serviceName;
    private String serviceJobID;
    private String objectID;
    private String status;


    //probably (?) we will standardize on  "status" actually being a json object, so this will likely be the main constructor
    public IdentityServiceLog(String taskID, String objectID, String serviceName, String serviceJobID, JSONObject jstatus) {
        this(taskID, objectID, serviceName, serviceJobID, jstatus.toString());
    }

    public IdentityServiceLog(String taskID, String serviceName, String serviceJobID, JSONObject jstatus) {
        this(taskID, null, serviceName, serviceJobID, jstatus.toString());
    }

    public IdentityServiceLog(String taskID, String objectID, String serviceName, String serviceJobID, String status) {
        this.taskID = taskID;
        this.objectID = objectID;
        this.serviceName = serviceName;
        this.serviceJobID = serviceJobID;
        this.status = status;
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
    public String getObjectID() {
        return objectID;
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
        Query qry = myShepherd.getPM().newQuery(cls, "this.serviceName == \"" + serviceName + "\" && this.objectID == \"" + objectID + "\"");
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






//S3AssetStore s3as = ((S3AssetStore) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(S3AssetStore.class, 3), true)));

    public void save(Shepherd myShepherd) {
        myShepherd.getPM().makePersistent(this);
    }

/*
    public static void save(IdentityServiceLog l, Shepherd myShepherd) {
        myShepherd.getPM().makePersistent(l);
    }
*/


    //probably (?) we will standardize on  "status" actually being a json object, so this will likely be the main constructor
    public String toString() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        return new ToStringBuilder(this)
                .append("timestamp", timestamp)
                .append("date/time", c.getTime().toString())
                .append("taskID", taskID)
                .append("serviceName", serviceName)
                .append("serviceJobID", serviceJobID)
                .append("status", status)
                .toString();
    }


}
