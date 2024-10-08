package org.ecocean;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.Calendar;
import org.ecocean.media.MediaAsset;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

// Does actual comparison processing of batch-uploaded images.
public final class ImageProcessor implements Runnable {
    private static Logger log = LoggerFactory.getLogger(ImageProcessor.class);


    private String context = "context0";
    private String command = null;
    private String imageSourcePath = null;
    private String imageTargetPath = null;
    private String arg = null;
    private int width = 0;
    private int height = 0;
    private float[] transform = new float[0];
    private MediaAsset parentMA = null;

    public ImageProcessor(String context, String action, int width, int height,
        String imageSourcePath, String imageTargetPath, String arg, MediaAsset pma) {
        this.context = context;
        this.width = width;
        this.height = height;
        this.imageSourcePath = imageSourcePath;
        this.imageTargetPath = imageTargetPath;
        this.arg = arg;
        if (this.arg == null) this.arg = "";
        this.parentMA = pma;
        if ((action != null) && action.equals("watermark")) {
            this.command = CommonConfiguration.getProperty("imageWatermarkCommand", this.context);
        } else if ((action != null) && action.equals("maintainAspectRatio")) {
            this.command = CommonConfiguration.getProperty("imageResizeMaintainAspectCommand",
                this.context);
        } else {
            this.command = CommonConfiguration.getProperty("imageResizeCommand", this.context);
        }
    }

    // no need for action when passing a transform, as it can only be one
    public ImageProcessor(String context, String imageSourcePath, String imageTargetPath, float w,
        float h, float[] transform, MediaAsset pma) {
        this.context = context;
        this.imageSourcePath = imageSourcePath;
        this.imageTargetPath = imageTargetPath;
        this.width = Math.round(w);
        this.height = Math.round(h);
        this.transform = transform;
        this.parentMA = pma;
        this.command = CommonConfiguration.getProperty("imageTransformCommand", this.context);
    }

    // the crop-only version of transforming; only takes x,y,w,h
    public ImageProcessor(String context, String imageSourcePath, String imageTargetPath, float x,
        float y, float w, float h, MediaAsset pma) {
        this.context = context;
        this.imageSourcePath = imageSourcePath;
        this.imageTargetPath = imageTargetPath;
        this.width = Math.round(w);
        this.height = Math.round(h);
        this.transform = new float[6];
        this.transform[0] = 1;
        this.transform[1] = 0;
        this.transform[2] = 0;
        this.transform[3] = 1;
        this.transform[4] = x;
        this.transform[5] = y;
        this.parentMA = pma;
        this.command = CommonConfiguration.getProperty("imageTransformCommand", this.context);
    }

    public void run() {
// status = Status.INIT;
        if (StringUtils.isBlank(this.command)) {
            log.warn("Can't run processor due to empty command");
            return;
        }
        if (StringUtils.isBlank(this.imageSourcePath)) {
            log.warn("Can't run processor due to empty source path");
            return;
        }
        if (StringUtils.isBlank(this.imageTargetPath)) {
            log.warn("Can't run processor due to empty target path");
            return;
        }
        String comment = CommonConfiguration.getProperty("imageComment", this.context);
        if (comment == null) comment = "%year All rights reserved. | wildbook.org";
        String cname = ContextConfiguration.getNameForContext(this.context);
        if (cname != null) comment += " | " + cname;
        String maId = "unknown";
        String rotation = "";
        if (this.parentMA != null) {
            if (this.parentMA.getUUID() != null) {
                maId = this.parentMA.getUUID();
                comment += " | parent " + maId;
            } else {
                maId = this.parentMA.setHashCode();
                comment += " | parent hash " + maId; // a stretch, but maybe should never happen?
            }
            if (this.parentMA.hasLabel("rotate90")) {
                rotation = "-flip -transpose";
            } else if (this.parentMA.hasLabel("rotate180")) {
                rotation = "-flip -flop";
            } else if (this.parentMA.hasLabel("rotate270")) {
                rotation = "-flip -transverse";
            }
        }
        comment += " | v" + Long.toString(System.currentTimeMillis());
        try {
            InetAddress ip = InetAddress.getLocalHost();
            comment += ":" + ip.toString() + ":" + ip.getHostName();
        } catch (UnknownHostException e) {}
        int year = Calendar.getInstance().get(Calendar.YEAR);
        comment = comment.replaceAll("%year", Integer.toString(year));
        comment = comment.replaceAll("'", "");

        String fullCommand;
        fullCommand = this.command.replaceAll("%width", Integer.toString(this.width))
                .replaceAll("%height", Integer.toString(this.height))
                .replaceAll("%maId", maId)
                .replaceAll("%additional", rotation);
        // walk thru transform array and replace "tN" with transform[N]
        if (this.transform.length > 0) {
            for (int i = 0; i < this.transform.length; i++) {
                fullCommand = fullCommand.replaceAll("%t" + Integer.toString(i),
                    Float.toString(this.transform[i]));
            }
        }
        String[] command = fullCommand.split("\\s+");
        // we have to do this *after* the split-on-space cuz files may have spaces!
        for (int i = 0; i < command.length; i++) {
            if (command[i].equals("%imagesource")) command[i] = this.imageSourcePath;
            if (command[i].equals("%imagetarget")) command[i] = this.imageTargetPath;
            // note this assumes comment stands alone. :/
            if (command[i].equals("%comment")) command[i] = comment;
            if (command[i].equals("%arg")) command[i] = this.arg;
            System.out.println("COMMAND[" + i + "] = (" + command[i] + ")");
        }
// System.out.println("done run()");
// System.out.println("command = " + Arrays.asList(command).toString());

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(command);
// System.out.println("before!");

        try {
            Process proc = pb.start();
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(
                proc.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(
                proc.getErrorStream()));
            String line;
            while ((line = stdInput.readLine()) != null) {
                System.out.println(">>>> " + line);
            }
            while ((line = stdError.readLine()) != null) {
                System.out.println("!!!! " + line);
            }
            proc.waitFor();
            System.out.println("DONE?????");
        } catch (Exception ioe) {
            log.error("Trouble running processor [" + command + "]", ioe);
        }
        System.out.println("RETURN");
    }
}
