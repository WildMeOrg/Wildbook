package org.ecocean.importutils;

import org.ecocean.*;

public class CellFeedback {

    // These two booleans cover the 3 possible states of a cell:
    // 1: successful parse (T,F), 2:no value provided (T,T), 3: unsuccessful parse with a value provided (F,F).
    private boolean success;
    private boolean isBlank;
    String valueStr;

    public CellFeedback(Object value, boolean success, boolean isBlank) {
        System.out.println("about to create cellFeedback for value "+value);
        if (value == null) valueStr = null;
        else valueStr = value.toString();
        this.success = success;
        this.isBlank = isBlank;
        System.out.println("new cellFeedback: got valueStr "+valueStr+" success: "+success+" and isBlank: "+isBlank);
    }
    
    public String html() { // here's where we add the excel value string on errors
        StringBuffer str = new StringBuffer();
        str.append("<td class=\"cellFeedback "+classStr()+"\" title=\""+titleStr()+"\"><span>");
        if (Util.stringExists(valueStr)) {
            str.append(valueStr);
        }
        str.append("</span></td>");
        return str.toString();
    }
    
    public String classStr() {
        if (isBlank) return "blank";
        if (!success) return "error";
        return "success";
    }
    
    public String titleStr() {
        if (isBlank) return "Cell was blank in excel file.";
        if (!success) return "ERROR: The import was unable to parse this cell. Please ensure that there are not letters or special characters in number fields (ex. lat/lon text or degree mark), formulas where there should be values or other data inconsistencies.";
        return "Successfully parsed value from excel file.";    
    }
    
    public String valueString() {
        if (valueStr!=null) return valueStr;
        return null;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setIsBlank(boolean isBlank) {
        this.isBlank = isBlank;
    }

    public void setValueString(String val) {
        this.valueStr = val;
    }

    public boolean getSuccess() {
        return this.success;
    }

    public boolean isBlank() {
        return isBlank;
    }
    
}
