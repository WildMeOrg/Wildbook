/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean;

import java.io.*;
import java.util.*;

import javax.servlet.ServletContext;

/**
 * Does actual comparison processing of batch-uploaded images.
 *
 * @author Jon Van Oast
 */
public final class ImageProcessor implements Runnable {
  //private static Logger log = LoggerFactory.getLogger(BatchProcessor.class);

  /** ServletContext for web application, to allow access to resources. */
  private ServletContext servletContext;


  /** Enumeration representing possible status values for the batch processor. */
  public enum Status { WAITING, INIT, RUNNING, FINISHED, ERROR };
  /** Enumeration representing possible processing phases. */
  public enum Phase { NONE, MEDIA_DOWNLOAD, PERSISTENCE, THUMBNAILS, PLUGIN, DONE };
  /** Current status of the batch processor. */
  private Status status = Status.WAITING;
  /** Current phase of the batch processor. */
  private Phase phase = Phase.NONE;
  /** Throwable instance produced by the batch processor (if any). */
  private Throwable thrown;
  
  private String context = "context0";
	private String command = null;
	private String imageSourcePath = null;
	private String imageTargetPath = null;
	private String action = null;
	private String arg = null;
	private int width = 0;
	private int height = 0;


  public ImageProcessor(String context, String action, int width, int height, String imageSourcePath, String imageTargetPath, String arg) {
		this.context = context;
		this.action = action;
		this.width = width;
		this.height = height;
		this.imageSourcePath = imageSourcePath;
		this.imageTargetPath = imageTargetPath;
		this.arg = arg;
		if ((action != null) && action.equals("watermark")) {
			this.command = CommonConfiguration.getProperty("imageWatermarkCommand", this.context);
		} else {
			this.command = CommonConfiguration.getProperty("imageResizeCommand", this.context);
		}
System.out.println("in ImageProcessor(), cmd =" + this.command);

	}


	public void run() {
    status = Status.INIT;

		if (isBlank(this.command) || isBlank(this.imageSourcePath) || isBlank(this.imageTargetPath)) return;

		String fullCommand = this.command.replaceAll("%width", Integer.toString(this.width)).replaceAll("%height", Integer.toString(this.height)).replaceAll("%imagesource", this.imageSourcePath).replaceAll("%imagetarget", this.imageTargetPath).replaceAll("%arg", this.arg);
System.out.println("start run(): " + fullCommand);
		String[] command = fullCommand.split("\\s+");

System.out.println("done run()");

		ProcessBuilder pb = new ProcessBuilder();
		pb.command(command);
/*
		Map<String, String> env = pb.environment();
		env.put("LD_LIBRARY_PATH", "/home/jon/opencv2.4.7");
*/
System.out.println("before!");

		try {
			Process proc = pb.start();
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
			String line;
			while ((line = stdInput.readLine()) != null) {
				System.out.println(">>>> " + line);
			}
			while ((line = stdError.readLine()) != null) {
				System.out.println("!!!! " + line);
			}
			proc.waitFor();
System.out.println("DONE?????");
			////int returnCode = p.exitValue();

		} catch (Exception ioe) {
			System.out.println("oops: " + ioe.toString());
		}

System.out.println("RETURN");
	}


	private boolean isBlank(String s) {
		return ((s == null) || s.equals(""));
	}

}
