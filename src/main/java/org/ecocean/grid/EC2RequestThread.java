package org.ecocean.grid;

import java.util.*;
import java.io.*;
import org.ecocean.ShepherdProperties;


//Amazon EC2 imports
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;


/**
 * The Amazon EC2 request thread contacts AWS and requests VMs based on an image.
 * @author jholmber
 *
 */
public class EC2RequestThread implements Runnable, ISharkGridThread {

	public Thread threadObject;
	public boolean finished=false;
	String context="context0";
	
	//Properties props=ShepherdProperties.getProperties("ec2.properties");

	/**Constructor to create a new thread object*/
	public EC2RequestThread() {
		threadObject=new Thread(this, ("EC2RequestThread"));
	}

	/**main method of the thread*/
	public void run() {
			getEC2Instances();
	}

	public boolean isFinished(){return finished;}


	private void getEC2Instances() {


		try {
			/* Create a connection instance */
		  
		  //Step 1. create an EC2 client instance
		  AWSCredentials credentials = new PropertiesCredentials(new File("/opt/tomcat6/webapps/shepherd_data_dir/WEB-INF/classes/bundles/awsec2.properties"));
		  AmazonEC2Client amazonEC2Client = new AmazonEC2Client(credentials);
		  amazonEC2Client.setEndpoint("ec2.us-west-2.amazonaws.com");
		  /*
		  //Step 2: create a security group
		  CreateSecurityGroupRequest csgr = new CreateSecurityGroupRequest();
       csgr.withGroupName("WhaleSharkJavaSecurityGroup").withDescription("whaleshark.org security group");
		  CreateSecurityGroupResult createSecurityGroupResult =  amazonEC2Client.createSecurityGroup(csgr);
			
		  //Step 3. Create a key pair
		  CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest();
		  createKeyPairRequest.withKeyName(keyName);
		  */
			

		  
			System.out.println("Connected to AWS.");
			
			ArrayList<String> allowedInstanceValues=new ArrayList<String>();
			allowedInstanceValues.add("0");
			allowedInstanceValues.add("16");
			
			com.amazonaws.services.ec2.model.Filter runningInstancesFilter = new com.amazonaws.services.ec2.model.Filter("instance-state-code",allowedInstanceValues);
			
			int maxInstances=4;
			
	     List reservList = amazonEC2Client.describeInstances((new DescribeInstancesRequest()).withFilters(runningInstancesFilter)).getReservations();
	     int numInstancesToLaunch=maxInstances-reservList.size();
	     System.out.println(".....There are already "+reservList.size()+" instances in the pending or running states. So I will launch: "+numInstancesToLaunch);
	     if(numInstancesToLaunch>0){
			
	       RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
			          
	       runInstancesRequest.withImageId("ami-35124f05")
			                     .withInstanceType("c3.2xlarge")
			                     .withMinCount(numInstancesToLaunch)
			                     .withMaxCount(numInstancesToLaunch)
			                     .setInstanceInitiatedShutdownBehavior("terminate");
			
			
	       //Connection conn = new Connection(hostname);

	       /* Now connect */
	       //AdvancedVerifier adv=new AdvancedVerifier();
			
	       /* Authenticate.
	        * If you get an IOException saying something like
	        * "Authentication method password not supported by the server at this stage."
	        * then please check the FAQ.
	        */

	       //boolean isAuthenticated = conn.authenticateWithPassword(username, password);

		
	       /* Create a session */

		

	       System.out.println("Executed my command...");
			
	       RunInstancesResult runInstancesResult = amazonEC2Client.runInstances(runInstancesRequest);
			
			
			  System.out.println(runInstancesResult.toString());
			
	    }
			
			/*
			 * This basic example does not handle stderr, which is sometimes dangerous
			 * (please read the FAQ).
			 */
		
			
			System.out.println("");


			//let's count the number of request jobs in the queue


			//int numUnfullfilledRequests=0;

		
		
		
		
		}
		catch(Exception excep){

			excep.printStackTrace();
		}


			finished=true;
	}




}