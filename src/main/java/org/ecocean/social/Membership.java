package org.ecocean.social;

import org.ecocean.MarkedIndividual;
import org.ecocean.Util;

public class Membership implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String id = Util.generateUUID();
    private MarkedIndividual mi;
    private String role = null;
    private long startDate = 0L;
    private long endDate = 0L;

    public Membership() {}

    public Membership(MarkedIndividual mi, String role, long startDate, long endDate) {
        if (mi==null) throw new NullPointerException();
        this.mi = mi;
        if (role!=null&&!"".equals(role)) {
            this.role = role;
        }
        this.startDate = startDate;
        this.endDate = endDate; 
    }

    public Membership(MarkedIndividual mi, String role, long startDate) {
        this.mi = mi;
        if (role!=null&&!"".equals(role)) {
            this.role = role;
        }
        this.startDate = startDate;
    }

    public Membership(MarkedIndividual mi, String role) {
        this.mi = mi;
        if (role!=null&&!"".equals(role)) {
            this.role = role;
        }
    }

    public MarkedIndividual getMarkedIndividual() {
        return mi;
    }

    public String getMarkedIndividualDisplayName() {
        return mi.getDisplayName();
    }

    public String getRole() {
        return role;
    }    

    public long getStartDate() {
        return startDate;
    }

    public long getEndDate() {
        return endDate;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(long endDate) {
        this.endDate = endDate;
    }

    public String getId() {
        return id;
    }

}