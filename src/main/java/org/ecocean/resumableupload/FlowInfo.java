package ec.com.mapache.ngflow.upload;

import java.io.File;
import java.util.HashSet;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class FlowInfo {

    public int      flowChunkSize;
    public long     flowTotalSize;
    public String   flowIdentifier;
    public String   flowFilename;
    public String   flowRelativePath;

    public static class flowChunkNumber {
        public flowChunkNumber(int number) {
            this.number = number;
        }

        public int number;

        @Override
        public boolean equals(Object obj) {
            return obj instanceof flowChunkNumber
                    ? ((flowChunkNumber)obj).number == this.number : false;
        }

        @Override
        public int hashCode() {
            return number;
        }
    }

    //Chunks uploaded
    public HashSet<flowChunkNumber> uploadedChunks = new HashSet<flowChunkNumber>();

    public String flowFilePath;

    public boolean valid(){
        if (flowChunkSize < 0 || flowTotalSize < 0
                || HttpUtils.isEmpty(flowIdentifier)
                || HttpUtils.isEmpty(flowFilename)
                || HttpUtils.isEmpty(flowRelativePath)) {
            return false;
        } else {
            return true;
        }
    }
    public String checkIfUploadFinished() {
        //check if upload finished
/*
System.out.println("checkIfUploadFinished()");
System.out.println(flowChunkSize + " / " + flowTotalSize);
System.out.println("uploadedChunks: " + uploadedChunks);
*/
        int count = (int) Math.ceil(((double) flowTotalSize) / ((double) flowChunkSize));
        for(int i = 1; i < count + 1; i ++) {
//System.out.println(i + "?");
            if (!uploadedChunks.contains(new flowChunkNumber(i))) {
//System.out.println("failed on i=" + i);
                return null;
            }
        }

        //Upload finished, change filename.
        File file = new File(flowFilePath);
        String new_path = file.getAbsolutePath().substring(0, file.getAbsolutePath().length() - ".temp".length());
        file.renameTo(new File(new_path));
        return new_path;
    }
}
