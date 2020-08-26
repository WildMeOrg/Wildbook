package org.ecocean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*  based on SLF4J:  http://www.slf4j.org/manual.html

    SystemLog.info(), SystemLog.debug(), SystemLog.error()...

    NOTE: when exception is passed as final arg, it will get stack trace in log
*/

public class SystemLog {
    /////private static final Logger logger = LoggerFactory.getLogger(MediaAssetFactory.class);
    public static Logger getLogger(Class cls) {
        return LoggerFactory.getLogger(cls);
    }

    public static Class guessClass(Object... args) {
        for (Object a : args) {
            if (a.getClass().getPackage().getName().startsWith("org.ecocean")) return a.getClass();
        }
        return SystemLog.class;
    }

    public static void info(String msg) {
        getLogger(SystemLog.class).info(msg);
    }
    public static void info(String format, Object... args) {
        getLogger(guessClass(args)).info(format, args);
    }

    public static void warn(String msg) {
        getLogger(SystemLog.class).warn(msg);
    }
    public static void warn(String format, Object... args) {
        getLogger(guessClass(args)).warn(format, args);
    }

    public static void debug(String msg) {
        getLogger(SystemLog.class).debug(msg);
    }
    public static void debug(String format, Object... args) {
        getLogger(guessClass(args)).debug(format, args);
    }

    public static void trace(String msg) {
        getLogger(SystemLog.class).trace(msg);
    }
    public static void trace(String format, Object... args) {
        getLogger(guessClass(args)).trace(format, args);
    }


    public static void error(String msg) {
        getLogger(SystemLog.class).error(msg);
    }
    public static void error(String format, Object... args) {
        Class cls = guessClass(args);
        getLogger(cls).error(format, args);
    }

}
