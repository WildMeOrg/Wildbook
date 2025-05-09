// basic bulk import
package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import org.ecocean.api.bulk.*;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

public class BulkImporter {
    public static JSONObject createImport(List<Map<String, Object> > rows,
        Map<String, MediaAsset> maMap, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();

        for (int rowNum = 0; rowNum < rows.size(); rowNum++) {
            List<BulkValidator> fields = new ArrayList<BulkValidator>();
            Map<String, Object> rowResult = rows.get(rowNum);
            for (String rowFieldName : rowResult.keySet()) {
                Object fieldObj = rowResult.get(rowFieldName);
                if (fieldObj instanceof BulkValidator) {
                    fields.add((BulkValidator)fieldObj);
                }
                // } else if (fieldObj instanceof BulkValidatorException) {
            }
            System.out.println(">>>>>> " + rowNum);
            processRow(fields, maMap, myShepherd);
        }
        return rtn;
    }

    // this assumes all values have been validated, so just go for it! set data with values. good luck!
    private static void processRow(List<BulkValidator> fields, Map<String, MediaAsset> maMap,
        Shepherd myShepherd) {
        Encounter enc = new Encounter();

        if (enc != null) return; // FIXME temp disable
        enc.setId(Util.generateUUID());
        enc.setDWCDateAdded();
        enc.setDWCDateLastModified();

        // some fields we do on a subsequent pass, as they require special care
        // handy for these subsequent passes
        Map<String, BulkValidator> fmap = new HashMap<String, BulkValidator>();
        for (BulkValidator field : fields) {
            System.out.println("   >> " + field);
            fmap.put(field.getFieldName(), field);
        }
        Set<String> allFieldNames = fmap.keySet();
        List<String> maFields = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.mediaAsset");
        List<String> kwFields = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.keyword");
        List<String> multiKwFields = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.mediaAsset.keywords");
        System.out.println(">>>>>>>>>>>> maFields: " + maFields);
        System.out.println(">>>>>>>>>>>> kwFields: " + kwFields);
        System.out.println(">>>>>>>>>>>> multiKwFields: " + multiKwFields);

        // Keyword kw = myShepherd.getOrCreateKeyword(kwString);
    }
}
