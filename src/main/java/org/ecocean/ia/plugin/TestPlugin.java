package org.ecocean.ia.plugin;

import javax.servlet.ServletContextEvent;
import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.media.*;
import org.ecocean.Annotation;
import org.json.JSONObject;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;

/*
    TestPlugin is meant to a be "template" plugin to serve as a guide to creating other IA plugins.
    (As well as test out new ideas/features, perhaps.)
*/
public class TestPlugin extends IAPlugin {
    private String context = null;

    public TestPlugin() {
        super();
    }
    public TestPlugin(String context) {
        super(context);
    }

    @Override
    public boolean isEnabled() {
        return Util.booleanNotFalse(IA.getProperty(context, "enableTestPlugin"));
    }

    @Override
    public boolean init(String context) {
        this.context = context;
        log(this.toString() + " init() called on context " + context);
        return true;
    }

    @Override
    public void startup(ServletContextEvent sce) {
        log(this.toString() + " startup() called on context " + context);
    }

    @Override
    public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
        Task t = new Task();
        t.setObjectMediaAssets(mas);
        JSONObject p = new JSONObject();
        p.put("testPlugin", true);
        t.setParameters(p);
        return t;
    }
    @Override
    public Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
        Task t = new Task();
        t.setObjectAnnotations(anns);
        JSONObject p = new JSONObject();
        p.put("testPlugin", true);
        t.setParameters(p);
        return t;
    }

    //this spices up the messages a little, since this is a TestPlugin after all
    private static void log(String message) {
        IA.log("####### TestPlugin ####### " + message);
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("TestPlugin")
                .toString();
    }
}
