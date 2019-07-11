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
//import java.util.Vector;
//import org.apache.commons.lang3.builder.ToStringBuilder;

public class Config {
    private String name;
    private String value;
    //private long timestamp;

    public Config() {
    }

    public Config(String name, JSONObject value) {
        this.name = name;
        if (value == null) {
            this.value = null;
        } else {
            this.value = value.toString();
        }
    }

    public JSONObject getValue() {
        if (value == null) return new JSONObject();
        return new JSONObject(value);
    }
    public void setValue(JSONObject v) {
        if (v == null) {
            value = null;
        } else {
            value = v.toString();
        }
    }

    public static Config load(String name, Shepherd myShepherd) {
        try {
            return (Config)(myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Config.class, name), true));
        } catch (org.datanucleus.exceptions.NucleusObjectNotFoundException ex) {
            return null;
        } catch (javax.jdo.JDOObjectNotFoundException ex) {
            return null;
        }
    }

    public static void save(Config conf, Shepherd myShepherd) {
        myShepherd.getPM().makePersistent(conf);
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
