package org.ecocean.queue;
import java.util.Properties;
import org.ecocean.ShepherdProperties;


public abstract class Queue {
    protected String type = null;
    protected String queueName = null;
    protected QueueMessageHandler messageHandler = null;

    protected Queue(final String name) {
        queueName = name;
    }

    public static synchronized void init(String context) throws java.io.IOException {} //override in base class

    public abstract void publish(String msg) throws java.io.IOException;

    //assumed to detach into background
    public abstract void consume(QueueMessageHandler msgHandler) throws java.io.IOException;

    //this is "internal" and is mostly used for manually backgrounded needs (like FileQueue)
    //  NOTE: when used, return of null means messageHandler will NOT be called!
    public abstract String getNext() throws java.io.IOException;

    //this is static and should be overridden
    public static boolean isAvailable(String context) { return false; }

    public abstract void shutdown();

    public boolean isConsumerShutdownMessage(String msg) {
        return "SHUTDOWN".equals(msg);
    }

    public static String getProperty(String context, String label) {  //no-default flavor
        return getProperty(context, label, null);
    }

    public static String getProperty(String context, String label, String def) {
        Properties qp = getProperties(context);
        if (qp == null) {
            System.out.println("Queue.getProperty(" + label + ") has no properties; queue.properties unavailable?");
            return null;
        }
        return qp.getProperty(label, def);
    }
    //note, we could cache this in static hashmap (based on context) but that would lose ability to alter without reloading tomcat
    //  tho changing *without restarting tomcat* is probably bad anyway!  since the listeners would already be running etc... :/
    private static Properties getProperties(String context) {
        try {
            return ShepherdProperties.getProperties("queue.properties", "", context);
        } catch (Exception ex) {
            //System.out.println("Queue.getProperties() failed: " + ex.toString());  //NPE seems to mean no queue.properties file exists
            return null;
        }
    }

    public String getType() {
        return type;
    }

    public String toString() {
        return "[" + ((type == null) ? "unknown type" : type) + "] " + ((queueName == null) ? "UNNAMED_QUEUE" : queueName);
    }
}


