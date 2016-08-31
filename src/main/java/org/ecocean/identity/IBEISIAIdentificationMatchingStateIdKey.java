///NOTE: this is not really working now, so FIXME someday
package org.ecocean.identity;

import java.util.StringTokenizer;

public class IBEISIAIdentificationMatchingStateIdKey implements java.io.Serializable {
    public String annId1;
    public String annId2;

    public IBEISIAIdentificationMatchingStateIdKey() {}

    public IBEISIAIdentificationMatchingStateIdKey(String value) {
        StringTokenizer token = new StringTokenizer(value, "::");
        this.annId1 = token.nextToken();
        this.annId2 = token.nextToken();
    }

    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof IBEISIAIdentificationMatchingStateIdKey)) return false;
        IBEISIAIdentificationMatchingStateIdKey k = (IBEISIAIdentificationMatchingStateIdKey)obj;
        return annId1.equals(k.annId1) && annId2.equals(k.annId2);
    }

    public int hashCode() {
        return this.annId1.hashCode() ^ this.annId2.hashCode();
    }

    public String toString() {
        return this.annId1 + "::" + this.annId2;
    }

}



