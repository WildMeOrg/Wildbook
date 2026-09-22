package org.ecocean.resumableupload;

import java.io.File;
import java.util.HashSet;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FlowInfo {
    public int flowChunkSize;
    public long flowTotalSize;
    public String flowIdentifier;
    public String flowFilename;
    public String flowRelativePath;

    public static class flowChunkNumber {
        public flowChunkNumber(int number) {
            this.number = number;
        }

        public int number;

        @Override public boolean equals(Object obj) {
            return obj instanceof flowChunkNumber ? ((flowChunkNumber)obj).number ==
                       this.number : false;
        }

        @Override public int hashCode() {
            return number;
        }
    }

    // Chunks uploaded
    public HashSet<flowChunkNumber> uploadedChunks = new HashSet<flowChunkNumber>();

    public String flowFilePath;
    /**
     * The validated final destination. Derived up front, NOT by trimming ".temp" off
     * flowFilePath: once that path is canonicalised it may resolve to a name with no ".temp"
     * suffix at all, and trimming five characters from it then lands outside the upload root.
     */
    public String finalFilePath;

    public boolean valid() {
        if (flowChunkSize <= 0 || flowTotalSize < 0 || HttpUtils.isEmpty(flowIdentifier) ||
            HttpUtils.isEmpty(flowFilename) || HttpUtils.isEmpty(flowRelativePath)) {
            return false;
        } else {
            return true;
        }
    }

    public String checkIfUploadFinished() {
        // check if upload finished
/*
   System.out.println("checkIfUploadFinished()");
   System.out.println(flowChunkSize + " / " + flowTotalSize);
   System.out.println("uploadedChunks: " + uploadedChunks);
 */
        // long, and via UploadPaths so the servlet's geometry check and this agree. `count + 1`
        // as an int overflowed to Integer.MIN_VALUE for a large count, skipping the loop entirely
        // and declaring a one-chunk upload complete.
        long count = UploadPaths.declaredChunkCount(flowChunkSize, flowTotalSize);

        for (long i = 1; i <= count; i++) {
// System.out.println(i + "?");
            if (!uploadedChunks.contains(new flowChunkNumber((int)i))) {
// System.out.println("failed on i=" + i);
                return null;
            }
        }
        // Upload finished, move the staging file to its validated destination.
        File file = new File(flowFilePath);
        if (finalFilePath == null) {
            System.out.println("WARNING: FlowInfo has no validated destination for " + flowFilePath);
            return null;
        }
        String new_path = finalFilePath;
        if (!file.renameTo(new File(new_path))) {
            // an ignored rename left a .temp file behind while still reporting completion
            System.out.println("WARNING: FlowInfo could not finalize " + flowFilePath);
            return null;
        }
        return new_path;
    }
}
