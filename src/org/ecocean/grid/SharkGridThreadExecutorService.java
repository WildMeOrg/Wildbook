package org.ecocean.grid;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;

public class SharkGridThreadExecutorService {
	
//private static ThreadPoolExecutor threadPool;
private static SharkGridThreadPoolExecutor threadPool;
	
	public synchronized static ThreadPoolExecutor getExecutorService() {

		try{
			if (threadPool==null) {
				GridManager gm=GridManagerFactory.getGridManager();
				//threadPool=new ThreadPoolExecutor(gm.getCreationDeletionThreadQueueSize(),gm.getCreationDeletionThreadQueueSize(),0,TimeUnit.SECONDS, (new ArrayBlockingQueue(100)));
				threadPool=new SharkGridThreadPoolExecutor(gm.getCreationDeletionThreadQueueSize(),gm.getCreationDeletionThreadQueueSize());
				
			}
			return threadPool;
			}
		catch (Exception jdo){
			jdo.printStackTrace();
			System.out.println("I couldn't instantiate the sharkGridThreadExecutorService.");
			return null;
			}
		}
	
}
