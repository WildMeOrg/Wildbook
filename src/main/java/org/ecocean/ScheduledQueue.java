/*
    note: see StartupWildbook.java for ScheduledExecutorService process which calls this, if you are curious about such things.
*/
package org.ecocean;

import java.io.File;
import javax.servlet.ServletContext;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.Files;
import org.ecocean.servlet.IAGateway;
import org.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import java.io.PrintWriter;

public class ScheduledQueue {

    private static File queueDir = null;
    private static int rapidIterationDepth = 0;

    public static File getQueueDir() {
        return queueDir;
    }

    public static File setQueueDir(ServletContext context) {
        //String root = context.getRealPath("/");
        String qd = CommonConfiguration.getProperty("ScheduledQueueDir", "context0");
        if (qd == null) qd = "/tmp/ScheduledQueue";
        queueDir = new File(qd);
        return queueDir;
    }

    public static boolean checkQueue() {
        if (queueDir == null) {
            System.out.println("ERROR: ScheduledQueue.checkQueue queueDir is null; failing");
            return false;
        }
        File nextFile = getNextFile();
        if (nextFile == null) return true;  //wait and try again...

        if (isStopFile(nextFile)) {
            System.out.println("INFO: ScheduledQueue found stop file; shutting down.");
            return false;
        }

        File activeFile = new File(nextFile.toString() + ".active");
        if (activeFile.exists()) {
            System.out.println("WARNING: ScheduledQueue wanted to create " + activeFile.toString() + " but it exists; skipping");
            //TODO keep a count maybe then skip over for real after N tries?  something like that....
            return true;
        }
        if (!nextFile.renameTo(activeFile)) {
            System.out.println("WARNING: ScheduledQueue wanted to create " + activeFile.toString() + " but rename failed; skipping");
            //TODO ditto above comment
            return true;
        }

        System.out.println("INFO: ScheduledQueue successfully engaged file " + nextFile.toString() + "; made .active");

        // for now we assume we *only* support json content... fix if you need to, future
        String fcontents = null;
        JSONObject jobj = null;
        try {
            fcontents = StringUtils.join(Files.readAllLines(activeFile.toPath(), java.nio.charset.Charset.defaultCharset()), "");
            jobj = new JSONObject(fcontents);
        } catch (Exception ex) {
            System.out.println("ERROR: ScheduledQueue could not read or parse json for " + nextFile + ": " + ex.toString());
            jobj = new JSONObject();  //this allows passthru for completion
        }

        //this must have a taskId coming in, cuz otherwise how would (detached, async) caller know what it is!
        // __context and __baseUrl should be set -- this is done automatically in IAGateway, but if getting here by some other method, do the work!
        if ((jobj.optJSONObject("detect") != null) && (jobj.optString("taskId", null) != null)) {
            JSONObject res = new JSONObject("{\"success\": false}");
            res.put("taskId", jobj.getString("taskId"));
            String context = jobj.optString("__context", "context0");
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("ScheduledQueue.detect");
            myShepherd.beginDBTransaction();
            String baseUrl = jobj.optString("__baseUrl", null);
            try {
                JSONObject rtn = IAGateway._doDetect(jobj, res, myShepherd, context, baseUrl);
                System.out.println("INFO: ScheduledQueue 'detect' from " + nextFile + " successful --> " + rtn.toString());
                myShepherd.commitDBTransaction();
            } catch (Exception ex) {
                System.out.println("ERROR: ScheduledQueue 'detect' from " + nextFile + " threw exception: " + ex.toString());
                myShepherd.rollbackDBTransaction();
            }
            myShepherd.closeDBTransaction();

        } else if ((jobj.optJSONObject("identify") != null) && (jobj.optString("taskId", null) != null)) {  //ditto about taskId
System.out.println("identify TOP!");
            JSONObject res = new JSONObject("{\"success\": false}");
            res.put("taskId", jobj.getString("taskId"));
            String context = jobj.optString("__context", "context0");
System.out.println(" > context = " + context);
System.out.println(" > taskId = " + jobj.getString("taskId"));
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("ScheduledQueue.identify");
            myShepherd.beginDBTransaction();
            String baseUrl = jobj.optString("__baseUrl", null);
System.out.println("--- BEFORE _doIdentify() ---");
            try {
                JSONObject rtn = IAGateway._doIdentify(jobj, res, myShepherd, context, baseUrl);
                System.out.println("INFO: ScheduledQueue 'identify' from " + nextFile + " successful --> " + rtn.toString());
                myShepherd.commitDBTransaction();
            } catch (Exception ex) {
                System.out.println("ERROR: ScheduledQueue 'identify' from " + nextFile + " threw exception: " + ex.toString());
                myShepherd.rollbackDBTransaction();
            }
            myShepherd.closeDBTransaction();

        } else {
            System.out.println("WARNING: ScheduledQueue unable to use json data in " + nextFile + "; ignoring");
        }

        File completedFile = new File(nextFile.toString() + ".complete");
        if (completedFile.exists()) {
            System.out.println("WARNING: ScheduledQueue wanted to create " + completedFile.toString() + " but it exists; skipping");
            //TODO ditto
            return true;
        }
        if (!activeFile.renameTo(completedFile)) {
            System.out.println("WARNING: ScheduledQueue wanted to create " + completedFile.toString() + " but rename failed; skipping");
            //TODO ditto
            return true;
        }

        boolean cont = true;
        //this chunk cycles rapidly thru all of queue without waiting between jobs (by not returning to ScheduledExecutorService)...
        //  this could be "bad" as it will not let that code do anything else (e.g. break on stop file).
        //  not sure if this is insanity, but can be commented out to prevent
        nextFile = getNextFile();
        if ((nextFile != null) && (rapidIterationDepth < 10)) {   //depth is a sanity valve... only recurse so far rapidly, then bail (for a breather)
            System.out.println("INFO: ScheduledQueue.checkQueue() rapidly iterating [depth=" + rapidIterationDepth + "] due to existence of file " + nextFile);
            rapidIterationDepth++;
            cont = checkQueue();  //will keep recursing and will fall thru when no more files || reached max depth
        }

        rapidIterationDepth = 0;
        return cont;
    }

    public static String addToQueue(String contents) throws java.io.FileNotFoundException, java.io.IOException {
        if (queueDir == null) throw new RuntimeException("ScheduledQueue.addToQueue() failed, queueDir is not set");
        String qid = Util.generateUUID();
        File tmpFile = new File(queueDir, "addToQueue-" + qid + ".tmp");  //write to tmp file first so it doesnt (yet) get picked up by queue til done
        PrintWriter qout = new PrintWriter(tmpFile);
        qout.print(contents);
        qout.close();
        tmpFile.renameTo(new File(queueDir, qid));
System.out.println("INFO: addToQueue added " + qid);
        return qid;
    }

    //current algorithm is "oldest" (FIFO); filename simply must be a valid uuid (with no extension)
    public static File getNextFile() {
        if (queueDir == null) return null;
        if (!queueDir.exists() || !queueDir.isDirectory()) {
            System.out.println("WARNING: " + queueDir + " does not exist or is not a directory; skipping");
            return null;
        }
        FileTime oldestTime = null;
        File oldestFile = null;
//System.out.println(">>>>>>>>>>>>>>>>>>>>>>> attempting to read from " + queueDir);
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
            if (isStopFile(f)) return f; //this always wins... so we can just grind everything to a halt
            if (f.isDirectory() || !Util.isUUID(f.getName())) continue;
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

}
