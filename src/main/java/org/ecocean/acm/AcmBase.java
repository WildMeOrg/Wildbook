package org.ecocean.acm;

import java.io.Serializable;

public class AcmBase implements java.io.Serializable {

    private String acmId;

    public void setAcmId(String id) {
        this.acmId = id;
    }
    public String getAcmId() {
        return this.acmId;
    }
}
