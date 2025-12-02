package org.ecocean.ia;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/*
   import org.ecocean.Annotation;
   import org.ecocean.Base;
   import org.ecocean.Encounter;
   import org.ecocean.media.AssetStore;
   import org.ecocean.media.MediaAsset;
   import org.ecocean.media.MediaAssetFactory;
   import org.ecocean.MarkedIndividual;
   import org.ecocean.Occurrence;
   import org.ecocean.OpenSearch;
   import org.ecocean.resumableupload.UploadServlet;
   import org.ecocean.servlet.ReCAPTCHA;
   import org.ecocean.servlet.ServletUtilities;
   import org.ecocean.shepherd.core.Shepherd;
   import org.ecocean.shepherd.core.ShepherdPMF;
   import org.ecocean.User;
 */

import org.ecocean.ia.Task;
import org.ecocean.Util;

public class MatchResult implements java.io.Serializable {
    private String id;
    private long created;
    private Task task;

    public MatchResult() {
        id = Util.generateUUID();
        created = System.currentTimeMillis();
    }

    public MatchResult(Task task) {
        this();
        this.task = task;
    }
}
