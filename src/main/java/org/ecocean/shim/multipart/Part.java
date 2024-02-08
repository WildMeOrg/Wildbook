package org.ecocean.shim.multipart;

//import jakarta.servlet.http.HttpServletRequest;

/*
import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;
*/


public class Part {

    public Part() {
    }

    public boolean isParam() {
        return false;
    }

    public boolean isFile() {
        return false;
    }

    public String getName() {
        return "";
    }
}
