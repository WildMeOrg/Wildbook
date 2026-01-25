package org.ecocean.servlet.export;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import org.apache.commons.csv.CSVPrinter;
import org.ecocean.*;

public class MultiValueExportColumn extends ExportColumn {
    // since we have multiple names per row, we need to know this
    private int nameNum;
    private boolean isLabel; // if isLabel is true, it's a name label. Else it's a name value.

    // this is the initializer I'm gonna use
    public MultiValueExportColumn(int nameNum, int colNum, boolean isLabel) {
        super(MultiValue.class, nameNumString(nameNum, isLabel), null, colNum);
        this.nameNum = nameNum;
        this.isLabel = isLabel;
    }

    private static String nameNumString(int nameNum, boolean isLabel) {
        if (isLabel) return "Name" + nameNum + ".label";
        return "Name" + nameNum + ".value";
    }

    /**
     * Writes the label value to the row array at this column's position.
     * Uses colNum to ensure correct column alignment even when names is null.
     */
    public void writeLabel(List<String> sortedKeys, MultiValue names, int rowNum, String[] row) {
        String writeValue = "";

        if (names != null && sortedKeys != null && sortedKeys.size() > nameNum) {
            String key = sortedKeys.get(nameNum);
            String value = names.getValue(key);
            if (Util.stringExists(value)) {
                writeValue = (isLabel) ? key : value;
                writeValue = cleanWriteValue(writeValue);
            }
        }
        row[colNum] = writeValue;
    }

    private String cleanWriteValue(String writeValue) {
        if (MultiValue.DEFAULT_KEY_VALUE.equals(writeValue)) return "Default";
        return writeValue;
    }

    public static void addNameColumns(int numNames, List<ExportColumn> columns) {
        System.out.println("About to add " + numNames + " nameColumns. Columns.size()=" +
            columns.size());
        for (int i = 0; i < numNames; i++) {
            MultiValueExportColumn keyColumn = new MultiValueExportColumn(i, columns.size(), true);
            columns.add(keyColumn);
            MultiValueExportColumn valColumn = new MultiValueExportColumn(i, columns.size(), false);
            columns.add(valColumn);
        }
        System.out.println("Done adding " + numNames + " nameColumns. Columns.size()=" +
            columns.size());
    }
}
