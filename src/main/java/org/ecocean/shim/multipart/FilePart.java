package org.ecocean.shim.multipart;

import java.io.File;

public class FilePart extends Part {

    public FilePart() {
    }

    public String getFileName() {
        return "";
    }

    public long writeTo(File file) {
        return 0L;
    }

}
