package org.ecocean.grid;

import java.util.*;
import java.io.*;
import org.ecocean.ShepherdProperties;


//Amazon EC2 imports
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Reservation;


/**
 * The Amazon EC2 request thread contacts AWS and requests VMs based on an image.
 * @author jholmber
 *
 */
public class EC2RequestThread implements Runnable, ISharkGridThread {

	public Thread threadObject;
	public boolean finished=false;
	String context="context0";
	
	Properties props=ShepherdProperties.getProperties("ec2.properties","");

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
		  AWSCredentials credentials = new PropertiesCredentials(new File(props.getProperty("credentialsFileFullPath")));
		  AmazonEC2Client amazonEC2Client = new AmazonEC2Client(credentials);
		  amazonEC2Client.setEndpoint(props.getProperty("endpoint"));
		  
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
			allowedInstanceValues.add("pending");
			allowedInstanceValues.add("running");
			
			com.amazonaws.services.ec2.model.Filter runningInstancesFilter = new com.amazonaws.services.ec2.model.Filter("instance-state-name",allowedInstanceValues);
			
			int maxInstances=(new Integer(props.getProperty("maxInstances"))).intValue();
			
	     List<Reservation> reservList = amazonEC2Client.describeInstances((new DescribeInstancesRequest()).withFilters(runningInstancesFilter)).getReservations();
	     int numReservations=reservList.size();
	     int numInstances=0;
	     for(int i=0;i<numReservations;i++){
	       if(reservList.get(i).getInstances().size()>0)numInstances+=reservList.get(i).getInstances().size();
	     }
	     
	     int numInstancesToLaunch=maxInstances-numInstances;
	     System.out.println(".....There are already "+reservList.size()+" instances in the pending or running states. So I will launch: "+numInstancesToLaunch);

	     /*
	     //first try the spot market
	     
	  
	     
	     // Initializes a Spot Instance Request
	     RequestSpotInstancesRequest requestRequest = new RequestSpotInstancesRequest();

	     requestRequest.setSpotPrice(props.getProperty("spotInstanceBidPrice"));
	     requestRequest.setInstanceCount(Integer.valueOf(numInstancesToLaunch));


	     // Setup the specifications of the launch. This includes the
	     // instance type (e.g. t1.micro) and the latest Amazon Linux
	     // AMI id available. Note, you should always use the latest
	     // Amazon Linux AMI id or another of your choosing.
	     LaunchSpecification launchSpecification = new LaunchSpecification();
	     launchSpecification.setImageId(props.getProperty("amiID"));
	     launchSpecification.setInstanceType(props.getProperty("instanceType"));
	     launchSpecification.setKeyName(props.getProperty("keyName"));
	     

	     // Add the security group to the request.
	     //ArrayList<String> securityGroups = new ArrayList<String>();
	     //securityGroups.add("GettingStartedGroup");
	     //launchSpecification.setSecurityGroups(securityGroups);

	     // Add the launch specifications to the request.
	     requestRequest.setLaunchSpecification(launchSpecification);

	     // Call the RequestSpotInstance API.
	     RequestSpotInstancesResult requestResult = amazonEC2Client.requestSpotInstances(requestRequest);
	     List<SpotInstanceRequest> requestResponses = requestResult.getSpotInstanceRequests();

	     ArrayList<String> spotInstanceRequestIds = new ArrayList<String>();
	     
	     numInstancesToLaunch=numInstancesToLaunch-spotInstanceRequestIds.size();
	     */
	     
	     if(numInstancesToLaunch>0){
	       
	       
	       

	       
			
	       RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
			          
	       runInstancesRequest.withImageId(props.getProperty("amiID"))
			                     .withInstanceType(props.getProperty("instanceType"))
			                     .withMinCount(numInstancesToLaunch)
			                     .withMaxCount(numInstancesToLaunch)
			                     .withKeyName(props.getProperty("keyName"))
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