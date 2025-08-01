package org.ecocean.servlet.export;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import jxl.write.*;
import org.apache.commons.text.StringEscapeUtils;
import org.ecocean.*;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

public class ExportColumn {
    public final String header;
    public final Method getter;
    public final int colNum;

    private final Class declaringClass;

    // since we have multiple MediaAssets and Keywords per row, these are used for those cols
    private int maNum = -1;
    private int kwNum = -1;
    private String labeledKwName = null;
    private int measureNum = -1;

    public ExportColumn(Class declaringClass, String header, Method getter, int colNum) {
        this.declaringClass = declaringClass;
        this.header = header;
        this.getter = getter;
        this.colNum = colNum;
    }

    // adds this to a running list of ExportColumns
    public ExportColumn(Class declaringClass, String header, Method getter,
        List<ExportColumn> columns) {
        this(declaringClass, header, getter, columns.size());
        columns.add(this);
    }

    // this is the initializer that uses an individuals' names list {}
    // public ExportColumn individualNameColumn(String nameKey)

    public String getStringValue(Object obj)
    throws InvocationTargetException, IllegalAccessException {
        if (obj == null) return null;
        Object value = null;
        try {
            value = getter.invoke(declaringClass.cast(obj)); // this is why we need declaringClass
        } catch (InvocationTargetException e) {
            System.out.println(
                "EncounterSearchExportMetadataExcel got an InvocationTargetException on column " +
                header + " and object " + obj);
            return null;
        } catch (Error e) {
            System.out.println(
                "EncounterSearchExportMetadataExcel got a more generic error on column " + header +
                " and object " + obj);
            e.printStackTrace();
            return null;
        }
        if (value == null) {
            return null;
        }
        return StringEscapeUtils.unescapeHtml4(value.toString());
    }

    public int getMeasurementNum() { return measureNum; }
    public void setMeasurementNum(int n) { this.measureNum = n; }

    public int getMaNum() { return maNum; }
    public void setMaNum(int n) { this.maNum = n; }

    public int getKwNum() { return kwNum; }
    public void setKwNum(int n) { this.kwNum = n; }

    public String getlabeledKwName() { return labeledKwName; }
    public void setLabeledKwName(String lkwn) { this.labeledKwName = lkwn; }

    public Class getDeclaringClass() {
        return declaringClass;
    }

    public boolean isFor(Class c) {
        return (declaringClass != null && declaringClass == c);
    }

    public Label getHeaderLabel() {
        return new Label(colNum, 0, header);
    }

    public void writeHeaderLabel(WritableSheet sheet)
    throws jxl.write.WriteException {
        sheet.addCell(getHeaderLabel());
    }

    public Label getLabel(Object obj, int rowNum)
    throws InvocationTargetException, IllegalAccessException {
        return new Label(colNum, rowNum, getStringValue(obj));
    }

    public void writeLabel(Object obj, int rowNum, WritableSheet sheet)
    throws jxl.write.WriteException, InvocationTargetException, IllegalAccessException {
        if (obj == null) return;
        sheet.addCell(getLabel(obj, rowNum));
    }

    public void writeHeaderLabel(Sheet sheet)
    throws InvocationTargetException, IllegalAccessException {
        Row row = sheet.getRow(0);

        if (row == null) {
            row = sheet.createRow(0);
        }
        Cell cell = row.createCell(colNum, Cell.CELL_TYPE_STRING);
        cell.setCellValue(header);
    }

    public void writeLabel(Object obj, int rowNum, Sheet sheet)
    throws InvocationTargetException, IllegalAccessException {
        if (obj == null) return;
        // Ensure the row exists in the sheet
        Row row = sheet.getRow(rowNum);
        if (row == null) {
            row = sheet.createRow(rowNum);
        }
        Cell cell = row.createCell(colNum, Cell.CELL_TYPE_STRING);
        if (obj instanceof String) {
            cell.setCellValue((String)obj);
        } else {
            cell.setCellValue(getStringValue(obj));
        }
    }

    // this would be a static method of above subclass if java allowed that
    public static ExportColumn newEasyColumn(String classDotFieldNameHeader,
        List<ExportColumn> columns)
    throws ClassNotFoundException, NoSuchMethodException {
        String[] parts = classDotFieldNameHeader.split("\\.");
        String className = parts[0];

        className = Util.capitolizeFirstLetterOnly(className);
        className = "org.ecocean." + className; // hmmm a little hacky no?
        Class declaringClass = null;
        try {
            declaringClass = Class.forName(className);
        } catch (ClassNotFoundException cnfe) {
            System.out.println("[ERROR]: newEasyColumn failed to find the class specified by " +
                classDotFieldNameHeader + ". Parsed className " + className);
            return null;
        }
        String fieldName = parts[1];
        String getterName = "get" + Util.capitolizeFirstLetter(fieldName);
        Method getter = null;

        try {
            getter = declaringClass.getMethod(getterName, null); // null means no arguments
        } catch (NoSuchMethodException nsme) {
            System.out.println("[ERROR]: newEasyColumn failed to find the method specified by " +
                classDotFieldNameHeader + ". Parsed getter name " + getterName +
                "; skipping this entire column!");
            return null;
        }
        System.out.println("newEasyColumn called with input " + classDotFieldNameHeader +
            ", getterName=" + getterName + ", fieldName=" + fieldName);

        return new ExportColumn(declaringClass, classDotFieldNameHeader, getter, columns);
    }
}
