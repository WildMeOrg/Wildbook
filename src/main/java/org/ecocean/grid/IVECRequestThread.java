package org.ecocean.grid;

import java.util.*;
import java.io.*;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;



/**
 * The IVEC request thread contacts IVEC and requests nodes.
 * @author jholmber
 *
 */
public class IVECRequestThread implements Runnable, ISharkGridThread {

	public Thread threadObject;
	public boolean finished=false;
	String hostname = "";
	String username = "jholmberg";
	String password = "";

	/**Constructor to create a new thread object*/
	public IVECRequestThread(String hostname, String username, String password) {
		threadObject=new Thread(this, ("IVECRequestThread"));
		this.hostname=hostname;
		this.username=username;
		this.password=password;
	}

	/**main method of the thread*/
	public void run() {
			getIVECNodes();
	}

	public boolean isFinished(){return finished;}


	private void getIVECNodes() {

		GridManager gm=GridManagerFactory.getGridManager();
		int maxIVECNodes=25;
		int currentIVECNodeRequests=0;






		try {
			/* Create a connection instance */

			System.out.println("Trying to contact IVEC: "+hostname);
			Connection conn = new Connection(hostname);

			/* Now connect */
			//AdvancedVerifier adv=new AdvancedVerifier();
			conn.connect();
			System.out.println("Connected to IVEC.");
			/* Authenticate.
			 * If you get an IOException saying something like
			 * "Authentication method password not supported by the server at this stage."
			 * then please check the FAQ.
			 */

			boolean isAuthenticated = conn.authenticateWithPassword(username, password);

			if (isAuthenticated == false)
				throw new IOException("Authentication failed.");
			System.out.println("Successfully authenticated to IVEC...");
			/* Create a session */

			Session sess = conn.openSession();

			sess.requestDumbPTY();
			sess.startShell();

			InputStream stdout = new StreamGobbler(sess.getStdout());

			BufferedReader br = new BufferedReader(new InputStreamReader(stdout));

			StringBuffer sb=new StringBuffer();
			while (true)
			{
				int myChar=br.read();
				System.out.print((char)myChar);
				sb.append((char)myChar);
				if ((hostname.equals("epic.ivec.org"))&&(sb.toString().indexOf("jholmberg@epicuser")!=-1)) {break;}
				//else if ((hostname.equals("xe.ivec.org"))&&(sb.toString().indexOf("jholmberg@xeuser")!=-1)) {break;}
				//else if ((hostname.equals("cognac.ivec.org"))&&(sb.toString().indexOf("-bash-")!=-1)) {break;}

			}
			System.out.println("");

			OutputStream outStreamWriter=sess.getStdin();
			String qstat="qstat -u jholmberg\r";
			outStreamWriter.write(qstat.getBytes());


			System.out.println("Executed my command...");
			/*
			 * This basic example does not handle stderr, which is sometimes dangerous
			 * (please read the FAQ).
			 */
			System.out.println("Reading in IVEC qstat response...");
			sb=new StringBuffer();
			while (true)
			{
				int myChar=br.read();
				System.out.print((char)myChar);
				sb.append((char)myChar);
				if ((hostname.equals("epic.ivec.org"))&&(sb.toString().indexOf("jholmberg@epicuser")!=-1)) {break;}
				//else if ((hostname.equals("xe.ivec.org"))&&(sb.toString().indexOf("jholmberg@xeuser")!=-1)) {break;}
				//else if ((hostname.equals("cognac.ivec.org"))&&(sb.toString().indexOf("-bash-")!=-1)) {break;}


			}
			System.out.println("");


			//let's count the number of request jobs in the queue


			int numUnfullfilledRequests=0;

			//if (hostname.equals("xe.ivec.org")) {numUnfullfilledRequests=countOccurrences(sb.toString(),".xe");}
			if (hostname.equals("epic.ivec.org")) {numUnfullfilledRequests=countOccurrences(sb.toString(),".epic");}
			//else if (hostname.equals("cognac.ivec.org")) {numUnfullfilledRequests=countOccurrences(sb.toString(),".beer");}




			int numNodesToRequest=maxIVECNodes-numUnfullfilledRequests;
			System.out.println("Number IVEC requests: "+numUnfullfilledRequests);


			//System.out.println("If I were requesting nodes, I would request:"+numNodesToRequest );



			for(int q=0;q<numNodesToRequest;q++){

				System.out.println("Executing goGrid: "+q);

				String qsub="qsub goGrid\r";
				outStreamWriter.write(qsub.getBytes());

				sb=new StringBuffer();
				while (true)
				{
					int myChar=br.read();
					//System.out.print((char)myChar);
					sb.append((char)myChar);
					//if ((hostname.equals("xe.ivec.org"))&&(sb.toString().indexOf("jholmberg@xeuser")!=-1)) {break;}
					if ((hostname.equals("epic.ivec.org"))&&(sb.toString().indexOf("jholmberg@epicuser")!=-1)) {break;}
					//else if ((hostname.equals("cognac.ivec.org"))&&(sb.toString().indexOf("-bash-")!=-1)) {break;}


				}
				//System.out.println("");
			}



			/* Show exit status, if available (otherwise "null") */

			System.out.println("SSH ExitCode: " + sess.getExitStatus());

			/* Close this session */

			sess.close();

			/* Close the connection */

			conn.close();

		}
		catch (IOException e)
		{
			e.printStackTrace();

		}
		catch(Exception excep){

			excep.printStackTrace();
		}


			finished=true;
	}


	   public static int countOccurrences(String arg1, String arg2) {
	          int count = 0;
	          int index = 0;
	          while ((index = arg1.indexOf(arg2, index)) != -1) {
	               ++index;
	               ++count;
	          }
	          return count;
	     }

}