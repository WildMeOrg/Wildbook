package org.ecocean;

import java.util.Comparator;
import java.util.GregorianCalendar;

public class EncounterDateComparator implements Comparator {
    /**
     * If reverse is true, encounters will be sorted with earliest to most recent. If reverse is true, encounters will be sorted most recent first and
     * then back through time.
     */
    private boolean reverse = false;

    EncounterDateComparator() {}

    EncounterDateComparator(boolean reverse) {
        this.reverse = reverse;
    }

    public int compare(Object a, Object b) {
        Encounter a_enc = (Encounter)a;
        Encounter b_enc = (Encounter)b;
        GregorianCalendar a1 = new GregorianCalendar(a_enc.getYear(), a_enc.getMonth(),
            a_enc.getDay());
        GregorianCalendar b1 = new GregorianCalendar(b_enc.getYear(), b_enc.getMonth(),
            b_enc.getDay());

        if (!reverse) {
            if (a1.getTimeInMillis() > b1.getTimeInMillis()) {
                return -1;
            } else if (a1.getTimeInMillis() < b1.getTimeInMillis()) {
                return 1;
            }
            return 0;
        } else {
            if (a1.getTimeInMillis() < b1.getTimeInMillis()) {
                return -1;
            } else if (a1.getTimeInMillis() > b1.getTimeInMillis()) {
                return 1;
            }
            return 0;
        }
    }
}
