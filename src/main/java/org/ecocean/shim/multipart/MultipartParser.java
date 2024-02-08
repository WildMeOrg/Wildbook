package org.ecocean.shim.multipart;

import jakarta.servlet.http.HttpServletRequest;

import org.ecocean.shim.multipart.Part;

/*
import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;
*/


public final class MultipartParser {

    //MultipartParser mp = new MultipartParser(request, (CommonConfiguration.getMaxMediaSizeInMegabytes(context) * 1048576));
    public MultipartParser(HttpServletRequest request, int size) {
    }

    public Part readNextPart() {
        return null;
    }

}
