package org.ecocean.acm;

import org.ecocean.Shepherd;
import org.ecocean.ia.IA;
import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import java.util.List;

public class AcmUtil {


    //these take a list of objects and a parallel list of acmIds to assign
    // returns number actually changed
    //  it should "handle weirdness" whatever that may mean?
    //  if we could get AcmBase to work (grrr) we could generalize this
    public static int rectifyMediaAssetIds(List<MediaAsset> mas, List<String> acmIds) {
        if ((mas == null) || (acmIds == null) || (mas.size() != acmIds.size())) {
            IA.log("ERROR: AcmUtil.rectifyMediaAssetIds() has invalid lists passed; failing");
            return -1;
        }
        int numChanged = 0;
        for (int i = 0 ; i < mas.size() ; i++) {
            if (mas.get(i) == null) {
                IA.log("WARNING: bizarre! AcmUtil.rectifyMediaAssetIds() has null MediaAsset at i=" + i + "; skipping");
            } else if (acmIds.get(i) == null) {
                IA.log("INFO: AcmUtil.rectifyMediaAssetIds() has null acmId response for " + mas.get(i) + "; skipping");
            } else if (mas.get(i).getAcmId() == null) {
                mas.get(i).setAcmId(acmIds.get(i));
                numChanged++;
            } else if (!mas.get(i).getAcmId().equals(acmIds.get(i))) {  //maybe we care a little more about changing the acmId ??
                IA.log("WARNING: AcmUtil.rectifyMediaAssetIds() changing acmId from " + mas.get(i).getAcmId() + " to " + acmIds.get(i) + " on " + mas.get(i));
                mas.get(i).setAcmId(acmIds.get(i));
                numChanged++;
            }
        }
        return numChanged;
    }

    public static int rectifyAnnotationIds(List<Annotation> anns, List<String> acmIds) {
        if ((anns == null) || (acmIds == null) || (anns.size() != acmIds.size())) {
            IA.log("ERROR: AcmUtil.rectifyAnnotationIds() has invalid lists passed; failing");
            return -1;
        }
        int numChanged = 0;
        for (int i = 0 ; i < anns.size() ; i++) {
            if (anns.get(i) == null) {
                IA.log("WARNING: bizarre! AcmUtil.rectifyAnnotationIds() has null Annotation at i=" + i + "; skipping");
            } else if (acmIds.get(i) == null) {
                IA.log("INFO: AcmUtil.rectifyAnnotationIds() has null acmId response for " + anns.get(i) + "; skipping");
            } else if (anns.get(i).getAcmId() == null) {
                anns.get(i).setAcmId(acmIds.get(i));
                numChanged++;
            } else if (!anns.get(i).getAcmId().equals(acmIds.get(i))) {
                IA.log("WARNING: AcmUtil.rectifyAnnotationIds() changing acmId from " + anns.get(i).getAcmId() + " to " + acmIds.get(i) + " on " + anns.get(i));
                anns.get(i).setAcmId(acmIds.get(i));
                numChanged++;
            }
        }
        return numChanged;
    }
}
