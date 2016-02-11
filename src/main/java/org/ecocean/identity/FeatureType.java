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

package org.ecocean.identity;

/*
import org.ecocean.CommonConfiguration;
import org.ecocean.ImageAttributes;
import org.ecocean.Keyword;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Files;
//import java.time.LocalDateTime;
import org.joda.time.DateTime;
import java.util.Date;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
//import java.io.FileInputStream;
import javax.jdo.Query;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
*/
import org.json.JSONObject;
import org.json.JSONException;
import org.ecocean.Shepherd;


/**
 * A FeatureType (still under development) will be the unique identifier of the content type of a feature, such as "fluke trailing edge".
 * Likely it should also include (as part of a compound id) a version as well, so changes to meanings can be reflected here.
 *     etc.   TODO
 */
public class FeatureType implements java.io.Serializable {
    static final long serialVersionUID = 8844233450443974780L;
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


    //TODO should probably have this loaded once like AssetStores
    public static FeatureType load(final String id, Shepherd myShepherd) {
        return ((FeatureType) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(FeatureType.class, id), true)));
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
