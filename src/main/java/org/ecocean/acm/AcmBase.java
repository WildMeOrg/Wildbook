/*
    note: this was an attempt to add a base class under Annotation and MediaAsset...
    but ultimately failed due to not being able to configure DataNucleus to do the right thing in the db. :(
*/

package org.ecocean.acm;

public class AcmBase implements java.io.Serializable {

    private String acmId;

    public void setAcmId(String id) {
        this.acmId = id;
    }
    public String getAcmId() {
        return this.acmId;
    }
}
