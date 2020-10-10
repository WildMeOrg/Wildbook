package org.ecocean;

import java.time.ZonedDateTime;
import java.time.ZoneId;

/*
    this class is to allow functional equivalence of java.time.ZonedDateTime, but allow us to persist the value in a db,
    while maintaining the original source time zone value

    -- useful notes:   https://www.baeldung.com/migrating-to-java-8-date-time-api
*/
public class ComplexDateTime implements java.io.Serializable {
    private String timeZone;
    private ZonedDateTime dateTime;
    private ZonedDateTime adjustedCache = null;

    public ComplexDateTime() {
    }
    public ComplexDateTime(boolean now) {
        if (now) {
            dateTime = ZonedDateTime.now();
            updateTimeZone();
        }
    }
    public ComplexDateTime(String iso8601) {  //expects ISO8601 (might work for others?)
        dateTime = ZonedDateTime.parse(iso8601);
        updateTimeZone();
    }
    public ComplexDateTime(ZonedDateTime zdt) {  //expects ISO8601 (might work for others?)
        dateTime = zdt;
        updateTimeZone();
    }

/*
    public String getTimeZone() {
        return timeZone;
    }
    public void setTimeZone(String tz) {
        timeZone = tz;
    }
*/

/*   lets not get the "raw" value here, cuz we need to rely on getZonedDateTime() below
    public ZonedDateTime getZonedDateTime() {
        return dateTime;
    }
    public void setZonedDateTime(ZonedDateTime zdt) {
        dateTime = zdt;
        updateTimeZone();
    }
*/
    public void updateTimeZone() {  //set timeZone based on dateTime
        SystemLog.debug("updateTimeZone called with dateTime={}", dateTime);
        if ((dateTime != null) && (dateTime.getZone() != null)) {
            timeZone = dateTime.getZone().toString();
            adjustedCache = null;
        }
    }

/*
    note due to the way this is persisted (and read back in from db) we need to apply this adjustment for the timezone!
*/
    public ZonedDateTime getZonedDateTime() {
        SystemLog.debug("[1] getZonedDateTime() called with dateTime={} and timeZone={}", dateTime, timeZone);
        if (adjustedCache != null) return adjustedCache;
        if ((dateTime == null) || (timeZone == null)) return null;
        adjustedCache = dateTime.withZoneSameInstant(ZoneId.of(timeZone));
        SystemLog.debug("[2] getZonedDateTime() now set adjustedCache={}", adjustedCache);
        return adjustedCache;
    }

    public String toIso8601() {
        ZonedDateTime dt = getZonedDateTime();
        if (dt == null) return null;
        return dt.toOffsetDateTime().toString();
    }
    public String toString() { return toIso8601(); }

    // h/t  https://stackoverflow.com/a/37335420
    public org.joda.time.DateTime toDateTime() {
        ZonedDateTime dt = getZonedDateTime();
        if (dt == null) return null;
        return new org.joda.time.DateTime(
            dt.toInstant().toEpochMilli(),
            org.joda.time.DateTimeZone.forTimeZone(java.util.TimeZone.getTimeZone(dt.getZone()))
        );
    }

    //this is a little helper to the iso8601 constructor that gently gives us null if we cant make it
    public static ComplexDateTime gentlyFromIso8601(String iso8601) {
        if (iso8601 == null) return null;
        try {
            return new ComplexDateTime(iso8601);
        } catch (Exception ex) {
            SystemLog.error("ComplexDateTime.gentlyFromIso8601() could not parse iso8601={}, error={}", iso8601, ex.toString());
            return null;
        }
    }

/*
    //still unsure if we should pass in DateTime instead of LocalDateTime... hmph
    public static ZonedDateTime asZonedDateTime(DateTime dt, String tzString) {
        // see also?  https://stackoverflow.com/questions/28877981/how-to-convert-from-org-joda-time-datetime-to-java-time-zoneddatetime
        Instant instant = Instant.ofEpochMilli(dt.getMillis());
System.out.println("dt=" + dt + " | tzString=" + tzString + " => " + ZonedDateTime.ofInstant(instant, ZoneId.of(tzString)));
        return ZonedDateTime.ofInstant(instant, ZoneId.of(tzString));
    }
    //meant to be iso8601 going in, but it might (!) work on other things?
    public static ZonedDateTime asZonedDateTime(String iso8601) {
        return ZonedDateTime.parse(iso8601);
    }
*/


}
