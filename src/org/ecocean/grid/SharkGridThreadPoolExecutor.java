package org.ecocean.grid;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;

public class SharkGridThreadPoolExecutor extends ThreadPoolExecutor{
	
	public SharkGridThreadPoolExecutor(int corePoolSize, int maximumPoolSize){
		super(corePoolSize,maximumPoolSize,0,TimeUnit.SECONDS, (new ArrayBlockingQueue(100)));
	} 
	
	protected void afterExecute(AppletWorkItemThread r, Throwable t){
		super.afterExecute(r, t);
		r.nullThread();
		r=null;
		
	} 

}
