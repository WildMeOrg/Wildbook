package org.ecocean;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.json.JSONObject;

import org.joda.time.DateTime;

/**
 * Each pointLocation is a specific spot on Earth defined by latitude, longitude elevation above (or below) sea level and a time.
 *
 * The Path object is made up of an array of these, and a group over time create a useful way of tracking a survey and survey track.
 *
 * @author Colin Kingen
 *
 */

public class PointLocation implements java.io.Serializable {
    /**
     *
     */
    private static final long serialVersionUID = -3758129925666366058L;

    public String pointLocationID = null;

    private Double latitude = null;
    private Double longitude = null;
    private Double bearing = null;

    private Measurement elevation;

    private Long dateTime = null;

    private String correspondingPathID = null;
    private String correspondingEncounterID = null;
    private String correspondingOccurrenceID = null;

    public PointLocation() {};

    public PointLocation(Double lat, Double lon) {
        if (latLonCheck(lat, lon)) {
            this.longitude = lon;
            this.latitude = lat;
        }
        generateUUID();
    }

    public PointLocation(Double lat, Double lon, Long date) {
        System.out.println("Lat: " + lat + " Lon: " + lon + " Date: " + date);
        if (latLonCheck(lat, lon) && date != null) {
            this.longitude = lon;
            this.latitude = lat;
            this.dateTime = date;
        }
        generateUUID();
    }

    public PointLocation(Double lat, Double lon, Long date, Measurement el) {
        // if (latLonCheck(lat, lon)&&date!=null&& elevation!= null) {
        this.longitude = lon;
        this.latitude = lat;
        this.dateTime = date;
        this.elevation = el;
        // }
        generateUUID();
    }

    public String getID() {
        return pointLocationID;
    }

    public Long getDateTimeInMilli() {
        if (dateTime != null) {
            return dateTime.longValue();
        }
        return null;
    }

    public String getDateTimeAsString() {
        if (dateTime != null) {
            DateTime dt = new DateTime(dateTime);
            return dt.toString();
        }
        return null;
    }

    public String getTimeAsString() {
        if (dateTime != null) {
            DateTime dt = new DateTime(dateTime);
            String time = String.valueOf(dt.getHourOfDay()) + ":" +
                String.valueOf(dt.getMinuteOfHour());
            return time;
        }
        return null;
    }

    public void setDateTimeInMilli(Long dt) {
        if (dt > 9132014) {
            this.dateTime = dt;
        }
    }

    public Double getLatitude() {
        if (latitude != -1) {
            return latitude;
        }
        return null;
    }

    public void setLatitude(Double lat) {
        if (lat >= -90 && lat <= 90) {
            latitude = lat;
        }
    }

    public Double getLongitude() {
        if (longitude != -1) {
            return longitude;
        }
        return null;
    }

    public void setBearing(Double bear) {
        if (bear >= -180 && bear <= 180) {
            bearing = bear;
        }
    }

    public Double getBearing() {
        if (bearing != -1) {
            return bearing;
        }
        return null;
    }

    public void setLongitude(Double lon) {
        if (lon >= -180 && lon <= 180) {
            longitude = lon;
        }
    }

    public String getEncounterID() {
        if (correspondingEncounterID != null) {
            return correspondingEncounterID;
        }
        return null;
    }

    public void setEncounterID(String id) {
        if (id != null && !id.equals("")) {
            correspondingEncounterID = id;
        }
    }

    public String getOccurrenceID() {
        if (correspondingOccurrenceID != null) {
            return correspondingOccurrenceID;
        }
        return null;
    }

    public void setOccurenceID(String id) {
        if (id != null && !id.equals("")) {
            correspondingOccurrenceID = id;
        }
    }

    public String getPathID() {
        if (correspondingPathID != null) {
            return correspondingPathID;
        }
        return null;
    }

    public void setPathID(String id) {
        if (id != null && !id.equals("")) {
            correspondingPathID = id;
        }
    }

    private void generateUUID() {
        this.pointLocationID = Util.generateUUID().toString();
    }

    private boolean latLonCheck(Double lat, Double lon) {
        return Util.isValidDecimalLatitude(lat) && Util.isValidDecimalLongitude(lon);
    }

    // distance squared
    public Double diff2(PointLocation p2) {
        if (p2 == null) return null;
        if ((this.longitude == null) || (this.latitude == null) || (p2.longitude == null) ||
            (p2.latitude == null)) return null;
        return (this.longitude - p2.longitude) * (this.longitude - p2.longitude) +
                   (this.latitude - p2.latitude) * (this.latitude - p2.latitude);
    }

    public JSONObject toJSONObject() {
        JSONObject rtn = new JSONObject();

        rtn.put("id", getID());
        rtn.put("latitude", latitude);
        rtn.put("longitude", longitude);
        rtn.put("bearing", bearing);
        if (dateTime != null) rtn.put("dateTime", new DateTime(dateTime));
        if (elevation != null) rtn.put("elevation", elevation.toJSONObject());
        return rtn;
    }

    public String toString() {
        return new ToStringBuilder(this)
                   .append("id", getID())
                   .append("bearing", bearing)
                   .append("(" + latitude + "," + longitude + ")")
                   .append("elev", elevation)
                   .append("dateTime", new DateTime(dateTime))
                   .toString();
    }
}
