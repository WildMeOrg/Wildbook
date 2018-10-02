package org.ecocean.ia.plugin;

import javax.servlet.ServletContextEvent;
import org.ecocean.Shepherd;
import org.ecocean.ia.IA;
import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import org.ecocean.ia.Task;
import java.util.List;


/*
    someday we might want to extend this with some more established/formal systems.  examples:
    + ServiceLoader https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html
    + https://pf4j.org/
    + http://jpf.sourceforge.net/
    + OSGi https://en.wikipedia.org/wiki/OSGi
    + https://stackoverflow.com/a/520344  (short-term?)
*/

public abstract class IAPlugin implements java.io.Serializable {
    protected IAPlugin() {
        init("context0");  // :( 
    }

    protected IAPlugin(String context) {
        init(context);
    }

    public abstract boolean init(String context);

    public abstract boolean isEnabled();

    //called by StatupWildbook (if plugin enabled) -- override if applicable
    public abstract void startup(ServletContextEvent sce);

    public abstract Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas);
    public abstract Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns);
}
