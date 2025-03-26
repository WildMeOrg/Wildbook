package org.ecocean.mmutil;

import java.util.Calendar;
import org.ecocean.Util;

/**
 * Class providing useful generic data-oriented utility methods.
 *
 * @author Giles Winstanley
 */
public class DataUtilities {
    private DataUtilities() {}

    /**
     * Creates a unique ID string for encounters. Unique to millisecond precision. 
     * @return unique ID string
     */
    public static String createUniqueId() {
        StringBuilder sb = new StringBuilder();
        Calendar cal = Calendar.getInstance();

        sb.append(String.format("%02d", cal.get(Calendar.YEAR) - 2000));
        sb.append(String.format("%02d", cal.get(Calendar.MONTH) + 1));
        sb.append(String.format("%02d", cal.get(Calendar.DAY_OF_MONTH)));
        sb.append(String.format("%02d", cal.get(Calendar.HOUR_OF_DAY)));
        sb.append(String.format("%02d", cal.get(Calendar.MINUTE)));
        sb.append(String.format("%02d", cal.get(Calendar.SECOND)));
        sb.append(String.format("%03d", cal.get(Calendar.MILLISECOND)));
        return sb.toString();
    }

    /**
     * Creates a unique ID string for encounters.
     * @return unique ID string
     */
    public static String createUniqueEncounterId() {
        return Util.generateUUID();
    }
}
