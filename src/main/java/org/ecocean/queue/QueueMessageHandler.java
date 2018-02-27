package org.ecocean.queue;

//public class QueueMessageHandler implements Runnable {
public class QueueMessageHandler {
/*
    private String message;
    public void setMessage(String msg) {
        this.message = msg;
    }
    public void run() {
        System.out.println("WARNING: default QueueMessageHandler.run() should be overridden!  [message=" + this.message + "]");
    }
*/
    public boolean handler(String msg) throws java.io.IOException {
        System.out.println("WARNING: default QueueMessageHandler.handler() should be overridden!  [message=" + msg + "]");
        return true;
    }
}

