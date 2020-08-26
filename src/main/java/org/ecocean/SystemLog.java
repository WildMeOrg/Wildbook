package org.ecocean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*  based on SLF4J:  http://www.slf4j.org/manual.html

    SystemLog.info(), SystemLog.debug(), SystemLog.error()...

*/

public class SystemLog {
    /////private static final Logger logger = LoggerFactory.getLogger(MediaAssetFactory.class);
    public static Logger getLogger(Class cls) {
        return LoggerFactory.getLogger(cls);
    }

/*
    TODO

    we could use _another object class_ based on args passed in?  like any org.ecocean... ones?

    we could also printStackTrace on any args that are Exceptions?

*/

    public static void info(String msg) {
        getLogger(SystemLog.class).info(msg);
    }
    public static void info(String format, Object... args) {
        getLogger(SystemLog.class).info(format, args);
    }

    public static void error(String msg) {
        getLogger(SystemLog.class).error(msg);
    }
    public static void error(String format, Object... args) {
        getLogger(SystemLog.class).error(format, args);
    }

/*
    public static void log(String msg) {
        log("INFO", msg);
    }
    public static void log(String level, String msg) {
        if (level == null) level = "DEBUG";
        if (msg == null) msg = "<NULL LOG MESSAGE>";
        //meh, quick-n-easy temporary!  but soon: https://mkyong.com/logging/log4j-hello-world-example/
        System.out.println("SystemLog [" + new org.joda.time.DateTime() + "|" + level +"] " + msg);
    }
*/

    /////private static final Logger logger = LoggerFactory.getLogger(MediaAssetFactory.class);
}
