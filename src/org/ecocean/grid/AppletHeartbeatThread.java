package org.ecocean.grid;


import java.net.URL;
import java.net.URLConnection;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.io.IOException;

/**
 * COmment
 * @author jholmber
 * more
 *
 */
public class AppletHeartbeatThread implements Runnable, ISharkGridThread {

	public Thread heartbeatThread;
	public boolean finished=false;
	private int numProcessors=1;
	private String appletID="";
	private String rootURL="";
	private String version="";

	/**Constructor to create a new thread object*/
	public AppletHeartbeatThread(String appletID, int numProcessors, String thisURLRoot, String version) {
		this.numProcessors=numProcessors;
		this.appletID=appletID;
		heartbeatThread=new Thread(this, ("sharkGridNodeHeartbeat_"+appletID));
		this.rootURL=thisURLRoot;
		heartbeatThread.start();
		this.version=version;
	}
		

		
	/**main method of the heartbeat thread*/
	public void run() {
		//boolean ok2run=true;
		while(!finished){
			try{
				sendHeartbeat(appletID);
				Thread.sleep(90000);
			}
			catch(Exception e){
				System.out.println("Heartbeat thread registering an exception while trying to sleep!");
			}
		}
	}
	
	public boolean isFinished(){return finished;}
	
	public void setFinished(boolean finish){this.finished=finish;}
	
	
	private void sendHeartbeat(String appletID){
		try{
			System.out.println("...sending heartbeat...thump...thump...");
			URL u = new URL( rootURL+"/GridHeartbeatReceiver?nodeIdentifier="+appletID+"&numProcessors="+numProcessors+"&version="+version);
			URLConnection finishConnection = u.openConnection();  

			InputStream inputStreamFromServlet = finishConnection.getInputStream();
			BufferedReader in = new BufferedReader(new InputStreamReader(inputStreamFromServlet)); 
			String line = in.readLine(); 
			in.close();
			
			//process the returned line however needed
			
			
		}
		catch(MalformedURLException mue){
			System.out.println("!!!!!I hit a MalformedURLException in the heartbeat thread!!!!!");
			mue.printStackTrace();
			
		}
		catch(IOException ioe){
			System.out.println("!!!!!I hit a MalformedURLException in the heartbeat thread!!!!!");
			ioe.printStackTrace();
		}
		catch(Exception e){
			System.out.println("!!!!!I hit a MalformedURLException in the heartbeat thread!!!!!");
			e.printStackTrace();
		}
	}
	
	
		
}