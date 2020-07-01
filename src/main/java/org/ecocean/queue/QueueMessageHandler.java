package org.ecocean.queue;

public class QueueMessageHandler {
    public boolean handler(String msg) throws java.io.IOException {  //true will ACK message, false will not
        System.out.println("WARNING: default QueueMessageHandler.handler() should be overridden!  [message=" + msg + "]");
        return true;
    }
}

