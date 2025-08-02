package org.ecocean;

import java.util.Vector;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Keyword implements Comparable<Keyword> {
    // the primary key of the keyword
    protected String indexname;

    // the visible descriptor of the keyword
    protected String readableName;

    // a Vector of String relative paths to the photo file that the keyword applies to
    public Vector photos;

    // hackey! used to remove zombie Keywords. Probs not worth merging to master but we will need to do this cleanup
    // on Flukebook.
    private boolean isUsed = false;

    /**
     * empty constructor required by JDO Enhancer
     */
    public Keyword() {}

    // use this constructor for new keywords
    public Keyword(String readableName) {
        this.readableName = readableName;
        // photos = new Vector();
    }

    /*
       public void removeImageName(String imageFile) {
       for (int i = 0; i < photos.size(); i++) {
        String thisName = (String) photos.get(i);
        if (thisName.equals(imageFile)) {
          photos.remove(i);
          i--;
        }
       }
       }
     */
    public boolean getIsUsed() { return isUsed; }
    public void setIsUsed(boolean isUsed) { this.isUsed = isUsed; }

    public String getReadableName() {
        return readableName;
    }

    // This method is overwritten by inheriting classes like LabeledKeyword
    public String getDisplayName() {
        return getReadableName();
    }

    public void setReadableName(String name) {
        this.readableName = name;
    }

    public String getIndexname() {
        return indexname;
    }

    /*
       public void addImageName(String photoName) {
       if (!isMemberOf(photoName)) {
        photos.add(photoName);
       }
       }
     */
    public boolean isMemberOf(String photoName) {
        // boolean truth=false;
        for (int i = 0; i < photos.size(); i++) {
            String thisName = (String)photos.get(i);
            if (thisName.equals(photoName)) {
                return true;
            }
        }
        return false;
    }

    // convenience method for removing duplicate keywords
    public boolean isDuplicateOf(Keyword kw) {
        return (this.readableName.equals(kw.getReadableName()) &&
                   !this.indexname.equals(kw.getIndexname()));
    }

    /*
       public boolean isMemberOf(Encounter enc) {
       //boolean truth=false;
       Vector photos = enc.getAdditionalImageNames();
       int photoSize = photos.size();
       for (int i = 0; i < photoSize; i++) {
        String thisName = enc.getEncounterNumber() + "/" + (String) photos.get(i);
        if (isMemberOf(thisName)) {
          return true;
        }
       }
       return false;
       }
     */

    /*
       public Vector getMembers() {
       return photos;
       }
     */
    public String toString() {
        return new ToStringBuilder(this)
                   .append(indexname)
                   .append(readableName)
                   .toString();
    }

    public boolean isLabeledKeyword() {
        return false;
    }

    @Override public int compareTo(final Keyword keyword) {
        if (Util.stringExists(this.getReadableName()) &&
            Util.stringExists(keyword.getReadableName())) {
            return this.getReadableName().compareToIgnoreCase(keyword.getReadableName());
        }
        return 0;
    }
}
