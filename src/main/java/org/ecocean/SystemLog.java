package org.ecocean;

// just a little wrapper to be our main place to funnel logs in the nextgen world

public class SystemLog {
    public static final String INFO = "INFO";
    public static final String DEBUG = "DEBUG";
    public static final String ERROR = "ERROR";

    public SystemLog() {}

    public static void log(String msg) {
        log(INFO, msg);
    }
    public static void log(String level, String msg) {
        if (level == null) level = DEBUG;
        if (msg == null) msg = "<NULL LOG MESSAGE>";
        //meh, quick-n-easy temporary!  but soon: https://mkyong.com/logging/log4j-hello-world-example/
        System.out.println("SystemLog [" + new org.joda.time.DateTime() + "|" + level +"] " + msg);
    }

}
