// basic bulk import
package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import org.ecocean.api.bulk.*;
import org.ecocean.shepherd.core.Shepherd;

public class BulkImporter {

    public static JSONObject createImport(List<Map<String, Object>> rows, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();
        for (int rowNum = 0 ; rowNum < rows.size() ; rowNum++) {
            List<BulkValidator> fields = new ArrayList<BulkValidator>();
            Map<String, Object> rowResult = rows.get(rowNum);
            for (String rowFieldName : rowResult.keySet()) {
                Object fieldObj = rowResult.get(rowFieldName);
                if (fieldObj instanceof BulkValidator) {
                    fields.add((BulkValidator)fieldObj);
                }
                //} else if (fieldObj instanceof BulkValidatorException) {
            }
System.out.println(">>>>>> " + rowNum);
            processRow(fields, myShepherd);
        }
        return rtn;
    }

    private static void processRow(List<BulkValidator> fields, Shepherd myShepherd) {
        for (BulkValidator field : fields) {
            System.out.println("   >> " + field);
        }
    }
}

