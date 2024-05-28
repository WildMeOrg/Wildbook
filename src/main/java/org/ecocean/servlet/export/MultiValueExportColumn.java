package org.ecocean.servlet.export;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import jxl.write.*;
import org.ecocean.*;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

public class MultiValueExportColumn extends ExportColumn {
    // since we have multiple names per row, we need to know this
    private int nameNum;
    private boolean isLabel; // if isLabel is true, it's a name label. Else it's a name value.

    public MultiValueExportColumn(Class declaringClass, String header, Method getter, int colNum) {
        super(declaringClass, header, getter, colNum);
    }

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

    public void writeLabel(MultiValue names, int rowNum, WritableSheet sheet)
    throws jxl.write.WriteException, InvocationTargetException, IllegalAccessException {
        List<String> sortedKeys = names.getSortedKeys();

        writeLabel(sortedKeys, names, rowNum, sheet);
    }

    // it saves time to only sort the keys once per MultiValue by shortcutting into this method
    public void writeLabel(List<String> sortedKeys, MultiValue names, int rowNum,
        WritableSheet sheet)
    throws jxl.write.WriteException, InvocationTargetException, IllegalAccessException {
        if (names == null || sortedKeys.size() < (nameNum + 1)) {
            return; // out of bounds for this names list
        }
        String key = sortedKeys.get(nameNum);
        String value = names.getValue(key);
        if (!Util.stringExists(value)) return; // don't print out names without values

        String writeValue = (isLabel) ? key : value; // are we writing the key or value
        writeValue = cleanWriteValue(writeValue);
        sheet.addCell(new Label(colNum, rowNum, writeValue));
    }

    public void writeLabel(List<String> sortedKeys, MultiValue names, int rowNum, Sheet sheet)
    throws InvocationTargetException, IllegalAccessException {
        if (names == null || sortedKeys.size() < (nameNum + 1)) {
            return; // out of bounds for this names list
        }
        String key = sortedKeys.get(nameNum);
        String value = names.getValue(key);
        if (!Util.stringExists(value)) return; // don't print out names without values

        String writeValue = (isLabel) ? key : value; // are we writing the key or value
        writeValue = cleanWriteValue(writeValue);

        // Ensure the row exists in the sheet
        Row row = sheet.getRow(rowNum);
        if (row == null) {
            row = sheet.createRow(rowNum);
        }
        Cell cell = row.createCell(colNum, Cell.CELL_TYPE_STRING);
        cell.setCellValue(writeValue);
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

        // that was easy!
    }
}
