package org.ecocean;

import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Util;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.Properties;
import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;

public class JsonProperties {

	private static final String propertiesOverrideDir = "/data/wildbook_data_dir/WEB-INF/classes/bundles";
	private static final String propertiesDir = "WEB-INF/classes/bundles";
	private static final String jsonLinkPrefix = "@";

	private String fname;
	private String fullPath;
	private JSONObject json;

	public JsonProperties(String fname) throws FileNotFoundException {

		try {
			this.setFname(fname);
			this.loadFullPath();
			this.setJson(fromFile(this.getFullPath()));

		}
		catch (Exception e) {
			System.out.println("Hit an exception on new JsonProperties:");
			e.printStackTrace();
		}

	}


	public JSONObject getJson() {
	    return json;
	}

	public void setJson(JSONObject json) {
	    this.json = json;
	}

	public String getFname() {
	    return fname;
	}

	public void setFname(String fname) {
	    this.fname = fname;
	}

	public String getFullPath() {
	    return fullPath;
	}

	public void setFullPath(String fullPath) {
	    this.fullPath = fullPath;
	}

	public Object get(String periodSeparatedKeys) {
		System.out.println("get called on "+periodSeparatedKeys);
		String[] keys = periodSeparatedKeys.split("\\.");
		String keyPrintable = String.join("->", keys);
		System.out.println("get parsed keys "+keyPrintable);
		return getRecursive(keys, this.getJson());
	}

	public Object getRecursive(String[] keys, JSONObject currentLevel) {
		System.out.println("getRecursive called on "+String.join(".", keys));

		String key = keys[0];
		String linkDestination = getLinkDestination(key, currentLevel);
		boolean followLink = (linkDestination!=null);
		if (followLink) System.out.println("getRecursive following a link to "+linkDestination);

		// base case
		if (keys.length == 1) {
			return (followLink ? get(linkDestination) : currentLevel.get(key));
		}

		// multiline ternary : nextLevel depends on whether we are following a link at this level
		JSONObject nextLevel = followLink ?
				(JSONObject) this.get(linkDestination) :
				(JSONObject) currentLevel.get(key);

		String[] nextKeys = Arrays.copyOfRange(keys, 1, keys.length);
		return getRecursive(nextKeys, nextLevel);
	}

	// if jobj.key = <a link to elsewhere in this file>, this returns the link destination. else null
	public String getLinkDestination(String key, JSONObject jobj) {
		try {
			String val = jobj.getString(key);
			if (val.startsWith(jsonLinkPrefix)) return val.substring(1);
		}
		catch (Exception e) {}
		return null;
	}

	// sets full path to the file defined by this.fname, looking first in overrideDir then the default propertiesDir
	public void loadFullPath() throws FileNotFoundException {
		if (Util.fileExists(overrideFilepath())) {
			this.setFullPath(overrideFilepath());
		} else if (Util.fileExists(defaultFilepath())) {
			this.setFullPath(defaultFilepath());
		} else {
			throw new FileNotFoundException("Could not find file "+fname+" in default ("+defaultFilepath()+") or override ("+overrideFilepath()+") directories");
		}
	}

	public static JSONObject fromFile(String fullPath) {
    File f = new File(fullPath);
    JSONObject json = null;
    try {
      InputStream is = new FileInputStream(fullPath);
      String jsonTxt = IOUtils.toString(is, "UTF-8");
      System.out.println("JSON parsed: "+jsonTxt);
      json = new JSONObject(jsonTxt);
    } catch (Exception e) {
    	System.out.println("Hit an exception on JsonProperties.fromFile("+fullPath+")");
    	e.printStackTrace();
    }
    return json;
	}

	private String overrideFilepath() {
		return propertiesOverrideDir + File.separator + getFname();
	}
	private String defaultFilepath() {
		return propertiesDir + File.separator + getFname();
	}



}

