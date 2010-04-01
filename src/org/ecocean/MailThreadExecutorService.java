package org.ecocean;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;

public class MailThreadExecutorService {
	
private static ThreadPoolExecutor threadPool;
	
	public synchronized static ThreadPoolExecutor getExecutorService() {

		try{
			if (threadPool==null) {
				
				threadPool=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS, (new ArrayBlockingQueue(100)));

			}
			return threadPool;
			}
		catch (Exception jdo){
			jdo.printStackTrace();
			System.out.println("I couldn't instantiate the mailThreadExecutorService.");
			return null;
			}
		}
	
}