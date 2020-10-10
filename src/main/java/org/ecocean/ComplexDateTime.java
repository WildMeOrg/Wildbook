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

    public String getTimeZone() {
        return timeZone;
    }
    public void setTimeZone(String tz) {
        timeZone = tz;
        updateFromTimeZone();  //when we adjust timezone, we must adjust dateTime to reflect this!
    }
    public ZonedDateTime getZonedDateTime() {
        return dateTime;
    }
    public void setZonedDateTime(ZonedDateTime zdt) {
        dateTime = zdt;
        updateTimeZone();
    }
    public void updateTimeZone() {  //set timeZone based on dateTime
        SystemLog.debug("updateTimeZone called with dateTime={}", dateTime);
        if ((dateTime != null) && (dateTime.getZone() != null)) timeZone = dateTime.getZone().toString();
    }

    /*
        this adjusts the dateTime to reflect a new timeZone value.  notably, it is called from setTimeZone(), which is also
        used when we are read from db
    */
    public void updateFromTimeZone() {
        SystemLog.debug("[1] updateFromTimeZone called with dateTime={} and timeZone={}", dateTime, timeZone);
        if ((dateTime != null) && (timeZone != null)) {
            dateTime = dateTime.withZoneSameInstant(ZoneId.of(timeZone));
            SystemLog.debug("[2] updateFromTimeZone now set dateTime={}", dateTime);
        }
    }

    public String toIso8601() {
        if (dateTime == null) return null;
        return dateTime.toOffsetDateTime().toString();
    }
    public String toString() { return toIso8601(); }

    // h/t  https://stackoverflow.com/a/37335420
    public org.joda.time.DateTime toDateTime() {
        if (dateTime == null) return null;
        return new org.joda.time.DateTime(
            dateTime.toInstant().toEpochMilli(),
            org.joda.time.DateTimeZone.forTimeZone(java.util.TimeZone.getTimeZone(dateTime.getZone()))
        );
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
