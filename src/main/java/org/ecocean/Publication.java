package org.ecocean;
/**
 * created for the store publication notes
 *
 * @author Jaydeep Chauhan
 *
 */
public class Publication implements java.io.Serializable {
    public Publication() {}  //empty for jdo
    private String PUBLICATION_ID; 

    private String PUBLICATION_REMARK;

    public String getPUBLICATION_ID() {
      return PUBLICATION_ID;
    }

    public void setPUBLICATION_ID(String pUBLICATION_ID) {
      PUBLICATION_ID = pUBLICATION_ID;
    }

    public String getPUBLICATION_REMARK() {
      return PUBLICATION_REMARK;
    }

    public void setPUBLICATION_REMARK(String pUBLICATION_REMARK) {
      PUBLICATION_REMARK = pUBLICATION_REMARK;
    }

    public Publication(String pUBLICATION_ID, String pUBLICATION_REMARK) {
      super();
      PUBLICATION_ID = pUBLICATION_ID;
      PUBLICATION_REMARK = pUBLICATION_REMARK;
    }
    
}
