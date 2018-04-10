/*
 * This file is a part of Wildbook.
 * Copyright (C) 2015 WildMe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Wildbook.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.ecocean.media;

import org.json.JSONObject;
import org.json.JSONException;
import org.ecocean.Shepherd;
import java.util.ArrayList;
import java.util.Collection;
import javax.jdo.Extent;
import javax.jdo.Query;


/**
 * A FeatureType (still under development) will be the unique identifier of the content type of a feature, such as "fluke trailing edge".
 * Likely it should also include (as part of a compound id) a version as well, so changes to meanings can be reflected here.
 *     etc.   TODO
 */
public class FeatureType implements java.io.Serializable {
    static final long serialVersionUID = 8844233450443974780L;

    private static ArrayList<FeatureType> allTypes;

    protected String id = null;  //TODO maybe should take on form of "org.ecocean.flukeTrailingEdge" or something?

    protected String description = null;

    public FeatureType(final String id) {
        this(id, null);
    }

    public FeatureType(final String id, final String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }
    public void setId(String i) {
        id = i;
    }   

    public String getDescription() {
        return description;
    }
    public void setDescription(String d) {
        description = d;
    }

    public static FeatureType load(final String id) {
        for (FeatureType ft : getAll()) {
            if (ft.getId().equals(id)) return ft;
        }
        return null;
    }
    //the myShepherd version is to call when initAll(myShepherd) has not been called to pre-populate allTypes (on same persistence manager
    public static FeatureType load(final String id, Shepherd myShepherd) {
        return ((FeatureType) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(FeatureType.class, id), true)));
    }


    public static ArrayList<FeatureType> getAll() {
        if (allTypes == null) throw new RuntimeException("FeatureType.initAll() has not been called");
        return allTypes;
    }

    public static ArrayList<FeatureType> initAll(Shepherd myShepherd) {
System.out.println("#### initAll()");
        myShepherd.beginDBTransaction();
        allTypes = new ArrayList<FeatureType>();
        Extent ext = myShepherd.getPM().getExtent(FeatureType.class, true);
        Query q = myShepherd.getPM().newQuery(ext);
        Collection c = (Collection) (q.execute());
        for (Object f : c) {
System.out.println("  - " + (FeatureType)f);
            allTypes.add((FeatureType)f);
        }
        myShepherd.rollbackDBTransaction();
        return allTypes;
    }

    public String toString() {
        return id;
/*
        return new ToStringBuilder(this)
                .append("id", id)
                .toString();
*/
    }

}
