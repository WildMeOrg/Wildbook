package org.ecocean;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MailThreadExecutorService {
    // private static ThreadPoolExecutor threadPool;

    public synchronized static ThreadPoolExecutor getExecutorService() {
        try {
            // if ((threadPool == null)||(threadPool.isTerminated())) {

            // threadPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, (new ArrayBlockingQueue(100)));

            return (new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                    (new ArrayBlockingQueue(100))));

            // }
            // return threadPool;
        } catch (Exception jdo) {
            jdo.printStackTrace();
            System.out.println("I couldn't deliver a requested ThreadPoolExecutor.");
            return null;
        }
    }
}
