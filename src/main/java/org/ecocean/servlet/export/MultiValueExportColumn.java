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

    // it saves time to only sort the keys once per MultiValue by shortcutting into this method
    public void writeLabel(List<String> sortedKeys, MultiValue names, int rowNum, CSVPrinter sheet)
    throws IOException {
        if (names == null || sortedKeys.size() < (nameNum + 1)) {
            return; // out of bounds for this names list
        }
        String key = sortedKeys.get(nameNum);
        String value = names.getValue(key);
        if (!Util.stringExists(value)) return; // don't print out names without values

        String writeValue = (isLabel) ? key : value; // are we writing the key or value
        writeValue = cleanWriteValue(writeValue);
        sheet.print(writeValue);
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
