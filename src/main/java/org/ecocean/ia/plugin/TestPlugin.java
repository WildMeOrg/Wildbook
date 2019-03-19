package org.ecocean.ia.plugin;

import javax.servlet.ServletContextEvent;
import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.media.*;
import org.ecocean.queue.*;
import org.ecocean.Annotation;
import org.json.JSONObject;
import java.util.List;
import java.io.IOException;
import org.apache.commons.lang3.builder.ToStringBuilder;

/*

    TestPlugin is meant to a be "template" plugin to serve as a guide to creating other IA plugins.
    (As well as test out new ideas/features, perhaps.)

    Note: this is using a Queue as a more complicated example, thus:
        -- startup() creates a Queue, including a handler (consumer), which:
        -- will use handleQueueMessage() as the consumer
        -- intakeMediaAssets() and intakeAnnotations() both use addToQueue() to publish a (meaningless) job to the Queue

    A Queue is not strictly necessary.  The intake methods could have simply done whatever processing necessary (e.g. send
    the objects to a blackbox IA service) in real-time.  The Queue just demostrates how to allow intake methods
    to asynchronously stack up jobs in the background.

    TODO -- mockup a TestPlugin callback, once we figure out how that will get routed thru IAGateway!!

*/
public class TestPlugin extends IAPlugin {
    private String context = null;
    private Queue queue = null;

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

        //now we do stuff for initiating our Queue + consumer

        final TestPlugin me = this;
        class TestPluginMessageHandler extends QueueMessageHandler {
            public boolean handler(String msg) {
                me.handleQueueMessage(msg);  //does the real handling of incoming messages
                return true;
            }
        }

        //start a sort of fakey queue to listen on
        try {
            queue = QueueUtil.getBest(this.context, "TestPlugin");
        } catch (java.io.IOException ex) {
            log("ERROR: queue startup exception: " + ex.toString());
        }
        if (queue == null) {
            log("WARNING: queue service NOT started");
            return;
        }

        //handles incoming messages
        TestPluginMessageHandler qh = new TestPluginMessageHandler();
        try {
            queue.consume(qh);
            log("INFO: queue.consume() started on " + queue.toString());
        } catch (java.io.IOException iox) {
            log("WARNING: queue.consume() FAILED on " + queue.toString() + ": " + iox.toString());
        }
    }

    @Override
    public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas, final Task parentTask) {
        Task t = new Task();
        t.setObjectMediaAssets(mas);
        JSONObject p = new JSONObject();
        p.put("testPlugin", true);
        t.setParameters(p);
        try {
            addToQueue("TASK:" + t.getId());
        } catch (IOException iox) {
            log("WARNING: addToQueue() threw " + iox.toString());
        }
        return t;
    }
    @Override
    public Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
        Task t = new Task();
        t.setObjectAnnotations(anns);
        JSONObject p = new JSONObject();
        p.put("testPlugin", true);
        t.setParameters(p);
        try {
            addToQueue("TASK:" + t.getId());
        } catch (IOException iox) {
            log("WARNING: addToQueue() threw " + iox.toString());
        }
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



    /*
           Queue-related stuff here.... see notes at top about Queue usage
    */

    private void handleQueueMessage(String msg) {
        log(">>>>> CONSUMING '" + msg + "'");
        //this could be some expensive, time-consuming process here.....
    }

    public void addToQueue(String content) throws IOException {
        log("<<<<< PUBLISHING '" + content + "'");
        getQueue().publish(content);
    }

    public Queue getQueue() throws IOException {
        if (queue != null) return queue;
        queue = QueueUtil.getBest(this.context, "TestPlugin");
        return queue;
    }


}
