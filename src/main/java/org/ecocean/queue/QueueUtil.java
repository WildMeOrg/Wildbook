package org.ecocean.queue;

import java.io.IOException;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.lang.Runnable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.List;
import java.util.ArrayList;


public class QueueUtil {
    private static List<ScheduledExecutorService> runningSES = new ArrayList<ScheduledExecutorService>();
    private static List<ScheduledFuture> runningSF = new ArrayList<ScheduledFuture>();


    public static Queue getBest(String context, String name) throws IOException {
        if (RabbitMQQueue.isAvailable(context)) {
            RabbitMQQueue.init(context);
            return new RabbitMQQueue(name);
        }
        //fallback to FileQueue
        if (!FileQueue.isAvailable(context)) return null;
        FileQueue.init(context);
        return new FileQueue(name);
    }



    //helper method for backgrounding queue consumers who dont background themselves
    //unnecessary for RabbitMQQueue (consumer goes into background automatically)
    public static void background(final Queue queue) throws IOException {
        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(1);
        final ScheduledFuture schedFuture = schedExec.scheduleWithFixedDelay(new Runnable() {
            int count = 0;
            public void run() {
                ++count;
                String message = null;
                boolean cont = true;
                try {
                    message = queue.getNext();
                } catch (Exception ex) {
                    System.out.println(queue.toString() + " getNext() got an exception; halting: " + ex.toString());
                    cont = false;
                }
                if (count % 100 == 1) System.out.println("==== " + queue.toString() + " run [count " + count + "]; queue=" + queue.toString() + "; continue = " + cont + " ====");
                boolean ok = true;
                //note message == null means it was read, but there is nothign to handle
                if (cont && (message != null)) {
                    try {
                        ok = queue.messageHandler.handler(message);
                    } catch (IOException ioex) {
                        System.out.println("WARNING: swallowed IOException from message handler: " + ioex.toString());
                    }
                }
//////System.out.println("count=" + count + "; handled-ok=" + ok + "; cont=" + cont + "; msg=" + message);
                ////TODO what does !ok mean for us here????  we dont really have ACK like rabbitmq.... so... ????
                if (!cont) {
                    System.out.println(":::: " + queue.toString() + " shutdown via discontinue signal ::::");
                    schedExec.shutdown();
                }
            }
        },
        10,  //initial delay  ... TODO these could be configurable, obvs
        10,  //period delay *after* execution finishes
        TimeUnit.SECONDS);
        runningSES.add(schedExec);
        runningSF.add(schedFuture);

        System.out.println("---- about to awaitTermination() ----");
        try {
            schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: queue interrupted! " + ex.toString());
        }
        System.out.println("==== schedExec.shutdown() called, apparently");
    }

    // mostly for ContextDestroyed in StartupWildbook..... i think?
    public static void cleanup() {
        for (ScheduledExecutorService ses : runningSES) {
            ses.shutdown();
            try {
                if (ses.awaitTermination(20, TimeUnit.SECONDS)) {
                    ses.shutdownNow();
                    if (ses.awaitTermination(20, TimeUnit.SECONDS)) {
                        System.out.println("!!! QueueUtil.cleanup() -- ExecutorService did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                ses.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        for (ScheduledFuture sf : runningSF) {
            sf.cancel(true);
        }
        System.out.println("QueueUtil.cleanup() finished.");
    }


}
