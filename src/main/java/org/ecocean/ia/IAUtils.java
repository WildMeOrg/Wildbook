package org.ecocean.ia;

import java.util.ArrayList;
import java.util.List;

import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.importer.ImportTask;
import org.json.JSONObject;

public class IAUtils {
  
  
  public static List<String> intakeMediaAssets(ImportTask it, Shepherd myShepherd) {
    
    List<Encounter> encs=it.getEncounters();
    ArrayList<String> taskIDs=new ArrayList<String>();
    
    for (Encounter enc: encs) {
      for(MediaAsset ma: enc.getMedia()) {
        ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
      }
      Task parentTask = null;  //this is *not* persisted, but only used so intakeMediaAssets will inherit its params
      if (enc.getLocationID() != null) {
        parentTask = new Task();
        JSONObject tp = new JSONObject();
        JSONObject mf = new JSONObject();
        mf.put("locationId", enc.getLocationID());
        tp.put("matchingSetFilter", mf);
        parentTask.setParameters(tp);
      }
      Task task = org.ecocean.ia.IA.intakeMediaAssets(myShepherd, enc.getMedia(), parentTask);  //TODO are they *really* persisted for another thread (queue)
      myShepherd.storeNewTask(task);
      taskIDs.add(task.getId());
    }
    return taskIDs;

    
  }
  
  

}
