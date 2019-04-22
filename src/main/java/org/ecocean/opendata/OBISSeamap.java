package org.ecocean.opendata;


public class OBISSeamap extends Share {

    OBISSeamap(final String context) {
        super(context);
    }

    public void init() {
        if (!isEnabled()) {
            log("not enabled; exiting init()");
            return;
        }
    }


    public void generate() {
    }

    public boolean isShareable(Object obj) {
        return false;
    }

}
