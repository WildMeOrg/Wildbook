/*
    RateLimitation is meant to track a resource which can only be used a certain number of iterations per unit time.
    Usage would be like: (will not do more than 10 actions in a 2 hour period)

        private static RateLimitation rl = new RateLimitation();  //best to be static on a class for example
        while (rl.numSinceHoursAgo(2) > 10) {
            //wait or do something else or give up etc
        }
        //code HERE that does limited action
        rl.addEvent();  //if you want to be paranoid/conservative, can move *before* event action

    Events are synchronized, so should work across threads.
*/
  
package org.ecocean;

import java.util.List;
import java.util.ArrayList;
import org.joda.time.DateTime;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class RateLimitation {
    private static long DEFAULT_MAX_EVENTS = 10000000l;  //this is meant as memory/sanity check; but is it too low??
    private static long DEFAULT_MAX_TIMESPAN = 30 * 24 * 60 * 60 * 1000;  //30 days

    private List<Long> events;
    private long maxEvents = DEFAULT_MAX_EVENTS;  //-1 means no limit; no way to set differently for now... can change if we ever care
    private long maxTimespan = DEFAULT_MAX_TIMESPAN;
    private long started = 0;

    public RateLimitation() {
        this(DEFAULT_MAX_TIMESPAN);
    }
    public RateLimitation(long maxTimespan) {  //-1 means no limit
        this.events = new ArrayList<Long>();
        this.started = System.currentTimeMillis();
        this.maxTimespan = maxTimespan;
    }

    public synchronized int addEvent(long ms) {
        events.add(ms);  //note this will get removed if outside maxTimespan!
        if (maxEvents > 1) {  //hey we should have at least one!
            while (events.size() > maxEvents) {
                events.remove(0);  //prune off oldest
            }
        }
        if (maxTimespan < 1) return numEvents();  //dont bother trimming older stuff (below)
        long now = System.currentTimeMillis();
        while ((events.size() > 0) && ((now - events.get(0)) > maxTimespan)) {
            events.remove(0);
        }
        return numEvents();
    }

    public synchronized int addEvent() {
        return addEvent(System.currentTimeMillis());
    }

    public synchronized int numEvents() {
        return events.size();
    }

    //note is *inclusive* of since value
    public synchronized int numSince(long since) {
        for (int i = 0 ; i < events.size() ; i++) {
            if (events.get(i) >= since) return events.size() - i;
        }
        return 0;
    }

    public synchronized int numSinceMillisAgo(long ms) {
        return numSince(System.currentTimeMillis() - ms);
    }
    public synchronized int numSinceMinutesAgo(long min) {
        return numSinceMillisAgo(min * 60 * 1000);
    }
    public synchronized int numSinceHoursAgo(long hr) {
        return numSinceMinutesAgo(hr * 60);
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("now", System.currentTimeMillis())
                .append("started", started)
                .append("earliest", (events.size() == 0) ? "-" : events.get(0))
                .append("latest", (events.size() == 0) ? "-" : events.get(events.size() - 1))
                .append("maxEvents", maxEvents)
                .append("maxTimespan", maxTimespan)
                .append("numEvents", numEvents())
                .append("lastMin", numSinceMinutesAgo(1))
                .append("lastHour", numSinceHoursAgo(1))
                .append("lastDay", numSinceHoursAgo(24))
                .toString();
    }
    
}
