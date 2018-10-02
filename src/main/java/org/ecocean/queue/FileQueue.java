package org.ecocean.queue;

import org.ecocean.Util;
import org.ecocean.CommonConfiguration;
import java.io.File;
import org.ecocean.servlet.ServletUtilities;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.Files;
import org.apache.commons.lang3.StringUtils;
import java.io.PrintWriter;

/*

File-based queue... not as nice as RabbitMQ but should(!?) work on "most" systems?

*/

public class FileQueue extends Queue {
    private static String TYPE_NAME = "File";
    private static File queueBaseDir = null;
    private File queueDir = null;

    public static boolean isAvailable(String context) {
        return true;  //TODO until we come up with a scenario where it wont work?
    }

    public FileQueue(final String name) throws IOException {
        super(name);
        if (queueBaseDir == null) throw new IOException("FileQueue.init() has not yet been called!");
        this.type = TYPE_NAME;
        queueDir = new File(queueBaseDir, name);  //TODO scrub name of invalid chars
        if (!queueDir.exists() || !queueDir.isDirectory()) {
            boolean ok = queueDir.mkdirs();
            if (!ok) throw new IOException("FileQueue failed to create " + queueDir.toString());
        }
    }

    public static synchronized void init(String context) throws IOException {
        File qd = setQueueDir(context);
        if (qd == null) throw new IOException("ERROR: unable to FileQueue.setQueueDir()");
        System.out.println("[INFO] FileQueue.init(" + context + ") complete");
    }

    public static synchronized File setQueueDir(String context) throws IOException {
        if (queueBaseDir != null) return queueBaseDir;  //hey we have one already!
        String qd = Queue.getProperty(context, "filequeue_basedir");
        if (qd == null) qd = CommonConfiguration.getProperty("ScheduledQueueDir", "context0");  //legacy

        if (qd == null) {  //lets try to make one *somewhere*
            //queueBaseDir = Files.createTempDirectory("WildbookFileQueue").toFile();
            queueBaseDir = new File("/tmp/WildbookFileQueue");
            System.out.println("INFO: default (temporary) queueBaseDir being used: " + queueBaseDir);
        } else {
            queueBaseDir = new File(qd);
        }
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

    public void consume(final QueueMessageHandler msgHandler) throws IOException {
        this.messageHandler = msgHandler;
        QueueUtil.background(this);
    }

    public String getNext() throws IOException {
        if (queueDir == null) throw new IOException(this.toString() + " FileQueue.getNext() queueDir is null");
        File nextFile = getNextFile();
        if (nextFile == null) return null;  //wait and try again...

        File activeFile = new File(nextFile.toString() + ".active");
        if (activeFile.exists()) {
            System.out.println("WARNING: " + this.toString() + " wanted to create " + activeFile.toString() + " but it exists; skipping");
            //TODO keep a count maybe then skip over for real after N tries?  something like that....
            return null;
        }
        if (!nextFile.renameTo(activeFile)) {
            System.out.println("WARNING: " + this.toString() + " wanted to create " + activeFile.toString() + " but rename failed; skipping");
            //TODO ditto above comment
            return null;
        }

        System.out.println("INFO: " + this.toString() + " successfully engaged file " + nextFile.toString() + "; made .active");

        // for now we assume we *only* support json content... fix if you need to, future
        String fcontents = null;
        try {
            fcontents = StringUtils.join(Files.readAllLines(activeFile.toPath(), java.nio.charset.Charset.defaultCharset()), "");
        } catch (Exception ex) {
            throw new IOException("ERROR: " + this.toString() + " could not read " + nextFile + ": " + ex.toString());
        }

        File completedFile = new File(nextFile.toString() + ".complete");
        if (completedFile.exists()) {
            System.out.println("WARNING: " + this.toString() + " wanted to create " + completedFile.toString() + " but it exists; skipping");
            //TODO ditto
            return null;
        }
        if (!activeFile.renameTo(completedFile)) {
            System.out.println("WARNING: " + this.toString() + " wanted to create " + completedFile.toString() + " but rename failed; skipping");
            //TODO ditto
            return null;
        }

        if (this.isConsumerShutdownMessage(fcontents)) throw new IOException("SHUTDOWN message received");
        return fcontents;
    }


    //current algorithm is "oldest" (FIFO); filename simply must be a valid uuid (with no extension)
    public File getNextFile() throws IOException {
        if (queueDir == null) return null;
        if (!queueDir.exists() || !queueDir.isDirectory()) {
            System.out.println("WARNING: " + queueDir + " does not exist or is not a directory; skipping");
            return null;
        }
        FileTime oldestTime = null;
        File oldestFile = null;
        File[] dfiles = null;
        try {
            dfiles = queueDir.listFiles();
        } catch (Exception ex) {
            System.out.println("WARNING: could not read directory " + queueDir + ": " + ex.toString());
            return null;
        }
        if (dfiles == null) {
            System.out.println("WARNING: " + queueDir + " had trouble reading directory contents; skipping");
            return null;
        }

        for (File f : dfiles) {
//System.out.println("queueDir file=" + f);
            //this always wins... so we can just grind everything to a halt
            if (isStopFile(f)) throw new IOException(this.toString() + " FileQueue STOP FILE found");
            if (f.isDirectory() || !Util.isUUID(f.getName())) continue;  //ignore!
            BasicFileAttributes attr = null;
            try {
                attr = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
            } catch (java.io.IOException ioe) {
                System.out.println("WARNING: could not read file attributes for " + f + ": " + ioe.toString());
            }
            if (attr == null) continue;
//System.out.println(f.toString() + " time = " + attr.creationTime());
            if ((oldestTime == null) || (oldestTime.compareTo(attr.creationTime()) > 0)) {
                oldestTime = attr.creationTime();
                oldestFile = f;
            }
        }
        return oldestFile;
    }

    private static boolean isStopFile(File file) {
        if (file == null) return false;
        return file.getName().equals("STOP");
    }

    public void shutdown() {
    }

    @Override
    public String toString() {
        return super.toString() + " -> " + queueDir;
    }

}
