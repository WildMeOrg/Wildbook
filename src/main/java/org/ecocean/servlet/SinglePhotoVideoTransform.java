package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import com.google.gson.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

public class SinglePhotoVideoTransform extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        StringBuffer jb = new StringBuffer();
        String line = null;
        try {
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                jb.append(line);
            }
        } catch (Exception e) {}
        System.out.println("GOT " + jb.toString());

        JsonElement jel = new JsonParser().parse(jb.toString());
        JsonObject jobj = jel.getAsJsonObject();
        String id = jobj.get("id").getAsString();
        SinglePhotoVideo spv = myShepherd.getSinglePhotoVideo(id);
        if (spv == null) {
            out.print("{\"error\": \"invalid id " + id + "\"}");
            return;
        }
        String name = jobj.get("name").getAsString();
        float clientWidth = jobj.get("clientWidth").getAsFloat();
        JsonArray t = jobj.getAsJsonArray("transform");
        float[] transform = new float[t.size()];
        for (int i = 0; i < t.size(); i++) {
            transform[i] = t.get(i).getAsFloat();
        }
        File f = new File(spv.getFullFileSystemPath());
        String targetPath = f.getParent() + "/" + name;
        boolean trying = spv.transformTo(context, transform, clientWidth, targetPath);
        HashMap m = new HashMap();
        m.put("success", trying);
        m.put("name", name);
        Gson gson = new Gson();
        out.println(gson.toJson(m));
        // myShepherd.closeDBTransaction();
        // myShepherd = null;
        out.flush();
        out.close();
    }
}
