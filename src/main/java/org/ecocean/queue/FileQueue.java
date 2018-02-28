package org.ecocean.queue;

import java.util.Properties;
import org.ecocean.ShepherdProperties;
import org.ecocean.Util;
import org.ecocean.CommonConfiguration;
import java.io.File;
import org.ecocean.servlet.ServletUtilities;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;

/*

File-based queue... not as nice as RabbitMQ but should(!?) work on "most" systems?

*/

public class FileQueue extends Queue {
    private static String TYPE_NAME = "File";
    private static File queueBaseDir = null;
    private File queueDir = null;

    public static boolean isAvailable(HttpServletRequest request) {
        return true;  //TODO until we come up with a scenario where it wont work?
    }

    public FileQueue(final String name) throws IOException {
        super(name);
        if (queueBaseDir == null) throw new IOException("FileQueue.init() has not yet been called!");
        this.type = TYPE_NAME;
        queueDir = new File(queueBaseDir, name);  //TODO scrub name of invalid chars
        if (!queueDir.isDirectory()) System.out.println("WARNING: FileQueue needs accessible dir " + queueDir);
    }

    public static synchronized void init(HttpServletRequest request) throws IOException {
        String context = ServletUtilities.getContext(request);
/*  these are actually optional for FileQueue
        Properties props = ShepherdProperties.getProperties("queue.properties", "", context);
        if (props == null) throw new IOException("no queue.properties");
*/
        File qd = setQueueDir(context);
        if (qd == null) throw new IOException("ERROR: unable to FileQueue.setQueueDir()");
        System.out.println("[INFO] FileQueue.init() complete");
    }

    public static File setQueueDir(String context) {
        String qd = null;
        Properties props = ShepherdProperties.getProperties("queue.properties", "", context);
        if (props != null) qd = props.getProperty("filequeue_basedir");
        if (qd == null) qd = CommonConfiguration.getProperty("ScheduledQueueDir", "context0");  //legacy
        if (qd == null) qd = "/tmp/WildbookFileQueue";
            ///Files.createTempDirectory(.......).toFile();  maybe use this instead as fallback???
        if (qd == null) return null;
        queueBaseDir = new File(qd);
        return queueBaseDir;
    }

    public void publish(String msg) throws IOException {
        if (queueDir == null) throw new IOException("FileQueue.publish() failed, queueDir is not set");
        String qid = Util.generateUUID();
        File tmpFile = new File(queueDir, "addToQueue-" + qid + ".tmp");  //write to tmp file first so it doesnt (yet) get picked up by queue til done
        PrintWriter qout = new PrintWriter(tmpFile);
        qout.print(msg);
        qout.close();
        tmpFile.renameTo(new File(queueDir, qid));
System.out.println("INFO: FileQueue.publish() added " + queueDir + " -> " + qid);
    }

    //i think this never returns?
    public void consume(final QueueMessageHandler msgHandler) throws IOException {
    }


    @Override
    public String toString() {
        return super.toString() + " -> " + queueDir;
    }

}
