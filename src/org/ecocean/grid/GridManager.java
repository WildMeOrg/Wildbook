package org.ecocean.grid;

import java.util.ArrayList;
import javax.servlet.http.HttpServletRequest;

import org.ecocean.Shepherd;

public class GridManager {
	
	private ArrayList<GridNode> nodes=new ArrayList<GridNode>();
	
	//these are only generic nodes
	//targeted nodes are always allowed
	private int numAllowedNodes=25;
	private long nodeTimeout=180000;
	private String appletVersion="1.2";
	public long checkoutTimeout=120000;
	public int groupSize=20;
	public int creationDeletionThreadQueueSize=1;
	public int scanTaskLimit=50;
	private long lastGridStatsQuery=1;
	private long gridStatsRefreshPeriod=300000;
	private int numScanTasks=0;
	private int numScanWorkItems=0;
	private int numCollisions=0;
	public int maxGroupSize=100;
	public int numCompletedWorkItems=0;
	
	//Modified Groth algorithm parameters
	private String epsilon="0.01";
	private String R = "8";
	private String Sizelim="0.85";
	private String maxTriangleRotation="10";
	private String C = "0.99";
	private String secondRun="true";
	
	//hold uncompleted scanWorkItems
	private ArrayList<ScanWorkItem> toDo=new ArrayList<ScanWorkItem>();
	
	//hold incomplete scanWorkItems
	private ArrayList<ScanWorkItemResult> done=new ArrayList<ScanWorkItemResult>();
	
	public GridManager(){}

	public ArrayList<GridNode> getNodes() {
		return nodes;
	}
	
	public void setMaxGroupSize(int mgs){maxGroupSize=mgs;}
	
	public int getNumCollisions(){return numCollisions;}
	public synchronized void reportCollision(){numCollisions++;}
	
	public void setNodeTimeout(long timeout){nodeTimeout=timeout;}
	public long getNodeTimeout(){return nodeTimeout;}
	
	public long getCheckoutTimeout(){return checkoutTimeout;}
	public void setCheckoutTimeout(long timeout){this.checkoutTimeout=timeout;}
	
	public int getGroupSize(){return groupSize;}
	public void setGroupSize(int size){this.groupSize=size;}
	
	public int getCreationDeletionThreadQueueSize(){return creationDeletionThreadQueueSize;}
	
	public String getSupportedAppletVersion(){return appletVersion;}
	
	public int getNumNodes(){
		int numNodes=nodes.size();
		int returnValue=0;
		long currenTime=System.currentTimeMillis();
		for(int i=0;i<numNodes;i++){
			//System.out.println("gridManager: Time diff is: "+(currenTime-nodes.get(i).getLastCheckin()));
			if((currenTime-nodes.get(i).getLastHeartbeat())<nodeTimeout){
				returnValue++;
			}
			else {
				nodes.remove(i);
				i--;
				numNodes--;
			}
		}
		return returnValue;
	}
	
	public int getNumAllowedNodes(){return numAllowedNodes;}
	public void setNumAllowedNodes(int num){
		numAllowedNodes=num;
		
		//if the current number of nodes exceeds the new allowed number
		//then we have to remove them
		//for now, remove them from the bottom
		//int the future, we should consider removing them according
		//to an algorithm determining their potential
		//while(nodes.size()>numAllowedNodes){
		//	int size=nodes.size();
		//	nodes.remove(size-1);
		//}
	}
	
	public boolean containsNode(String nodeID){
		int numNodes=nodes.size();
		for(int i=0;i<numNodes;i++){
			if(nodes.get(i).getNodeIdentifier().equals(nodeID)){return true;}
		}
		return false;
	}
	
	/*public boolean canMakeSpace(HttpServletRequest request){
		String nodeID=request.getParameter("nodeIdentifier");
		int numNodes=nodes.size();
		long currenTime=System.currentTimeMillis();
		for(int i=0;i<numNodes;i++){
			System.out.println("gridManager: Time diff is: "+(currenTime-nodes.get(i).getLastCheckin()));
			if((currenTime-nodes.get(i).getLastHeartbeat())>nodeTimeout){
				nodes.remove(i);
				nodes.add(new gridNode(request));
				return true;
			}
		}
		return false;
	}*/

	public synchronized boolean isGridSpaceAvailable(HttpServletRequest request, boolean targeted){
		String nodeID=request.getParameter("nodeIdentifier");
		
		//clean out old nodes
		//int numNodes=nodes.size();
		//long currenTime=System.currentTimeMillis();
		cleanupOldNodes();
		
		
		//first, add the node to the queue
		if(!containsNode(nodeID)){
			GridNode node=new GridNode(request,groupSize);
			nodes.add(node);
		}
		
		//library users can always get permission to run targeted scans
		if (targeted){
			return true;
		}
		

		
		
		//beyond here we know it's a generic node, which means it may be denied access to the queue
		else if(isInAllowedPosition(nodeID)){return true;}
		//else if(canMakeSpace(request)){return true;}
		return false;
	}
	
	public synchronized boolean isInAllowedPosition(String nodeID){
		int numNodes=nodes.size();
		if(numNodes<numAllowedNodes){return true;}
		long currenTime=System.currentTimeMillis();
		for(int i=0;i<numNodes;i++){
			if(nodes.get(i).getNodeIdentifier().equals(nodeID)){
				if(i<=(numAllowedNodes-1)) return true;
			}
			//else if((currenTime-nodes.get(i).getLastHeartbeat())>nodeTimeout){
				//nodes.remove(i);
				//i--;
				//numNodes--;
			//}
		}
		return false;
	}
	
	public synchronized void processHeartbeat(HttpServletRequest request){
		String nodeID=request.getParameter("nodeIdentifier");
		if(containsNode(nodeID)){
			GridNode nd=getGridNode(nodeID);
			nd.registerHeartbeat();
		}
		else {
			//create a new node
			GridNode node=new GridNode(request,groupSize);
			nodes.add(node);
		}
	}
	
	public int getNextGroupSize(GridNode nd){
		return nd.getNextGroupSize(checkoutTimeout, maxGroupSize);
	}
	
	public GridNode getGridNode(String nodeID){
		int numNodes=nodes.size();
		for(int i=0;i<numNodes;i++){
			if(nodes.get(i).getNodeIdentifier().equals(nodeID)){return nodes.get(i);}
		}
		return null;
	}
	
	public int getScanTaskLimit(){
		return scanTaskLimit;
	}
	
	public void setScanTaskLimit(int limit){
		this.scanTaskLimit=limit;
	}
	
	private void cleanupOldNodes(){
		int numNodes=nodes.size();
		long currenTime=System.currentTimeMillis();
		for(int i=0;i<numNodes;i++){
			if((currenTime-nodes.get(i).getLastHeartbeat())>nodeTimeout){
				nodes.remove(i);
				i--;
				numNodes--;
			}

		}
	}
	
	public int getPerMinuteRate(){
		int rate=0;
		cleanupOldNodes();
		int numNodes=nodes.size();
		long totalComparisons=0;
		long totalTime=0;
		for(int i=0;i<numNodes;i++){
			GridNode nd=nodes.get(i);
			totalComparisons=totalComparisons+nd.numComparisons;
			totalTime=totalTime+nd.totalTimeSinceStart;
		}
		if(totalTime>0){
			rate=(int)(totalComparisons*60/(totalTime/1000));
		}
		return rate;
	}
	

	
	//call this from outside any other transaction
	private void updateGridStats(){
		long currenTime=System.currentTimeMillis();
			
		//refresh the grid stats if necessary	
		if((lastGridStatsQuery==1)||((currenTime-lastGridStatsQuery)>gridStatsRefreshPeriod)){
			Shepherd myShepherd=new Shepherd();
			myShepherd.beginDBTransaction();
			numScanTasks=myShepherd.getNumScanTasks();
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			myShepherd=null;
			lastGridStatsQuery=currenTime;
		}
	}
	
	public int getNumTasks(){
		updateGridStats();
		return numScanTasks;
	}
	
	public int getNumWorkItems(){
		updateGridStats();
		return numScanWorkItems;
	}
	
	public int getNumCompletedWorkItems(){
		return numCompletedWorkItems;
	}
	
	public synchronized void incrementCompletedWorkItems(int numCompleted){
		numCompletedWorkItems+=numCompleted;
	}
	
	public double getCollisionRatePercentage(){
		if(numCompletedWorkItems==0){return 0;}
		else{
			return (100*numCollisions/numCompletedWorkItems);
		}
	}
	
	public String getGrothEpsilon(){return epsilon;}
	public String getGrothR(){return R;}
	public String getGrothSizelim(){return Sizelim;}
	public String getGrothMaxTriangleRotation(){return maxTriangleRotation;}
	public String getGrothC(){return C;}
	public String getGrothSecondRun(){return secondRun;}
	
	
	public ArrayList<ScanWorkItem> getIncompleteWork(){
		return toDo;
	}
	
	public ArrayList<ScanWorkItemResult> getCompletedWork(){
		return done;
	}
	
	public void removeAllCompletedWorkItems(){
		done=new ArrayList<ScanWorkItemResult>();
	}
	
	public void removeAllWorkItems(){
		toDo=new ArrayList<ScanWorkItem>();
	}
	
	public synchronized void addWorkItem(ScanWorkItem swi){
		toDo.add(swi);
	}
	
	public synchronized ArrayList<ScanWorkItem> getWorkItems(int num){
		ArrayList<ScanWorkItem> returnItems=new ArrayList<ScanWorkItem>();
		int iterNum=toDo.size();
		boolean cont = true;
		for(int i=0;i<iterNum;i++){
			if(cont){
				ScanWorkItem item=toDo.get(i);
				if((!item.isCheckedOut(checkoutTimeout))&&(!item.isDone())){
					item.setStartTime(System.currentTimeMillis());
					returnItems.add(item);
					if(returnItems.size()>=num){cont = false;}
				}
			}
		}
		if(returnItems.size()>0){return returnItems;}
		else{
			for(int i=0;i<iterNum;i++){
				if(cont){
					ScanWorkItem item=toDo.get(i);
					if(!item.isDone()){
						//item.setStartTime(System.currentTimeMillis());
						returnItems.add(item);
						if(returnItems.size()>=num){cont = false;}
					}
				}
			}
		}
		return returnItems;
	}
	
	public void removeWorkItem(String uniqueNumberWorkItem){
		int iter=toDo.size();
		for(int i=0;i<iter;i++){
			if(toDo.get(i).getUniqueNumber().equals(uniqueNumberWorkItem)){
				toDo.remove(i);
				i--;
				iter--;
			}
		}
	}
	
	public synchronized void removeWorkItemsForTask(String taskID){
		for(int i=0;i<toDo.size();i++){
			if(toDo.get(i).getTaskIdentifier().equals(taskID)){
				toDo.remove(i);
				i--;
			}
		}
	}
	
	public void removeCompletedWorkItemsForTask(String taskID){
		//int iter=done.size();
		for(int i=0;i<done.size();i++){
			if(done.get(i).getUniqueNumberTask().equals(taskID)){
				done.remove(i);
				i--;
				//iter--;
			}
		}
	}
	
	public synchronized void checkinResult(ScanWorkItemResult swir){
		
		if(!doneContains(swir)){
			done.add(swir);
			numCompletedWorkItems++;
		} 
		else{numCollisions++;}
		//if(!done.contains(swir)){done.add(swir);}
		
		if((!swir.getUniqueNumberTask().equals("TuningTask"))&&(!swir.getUniqueNumberTask().equals("FalseMatchTask"))){
			removeWorkItem(swir.getUniqueNumberWorkItem());
		}
		else{
			ScanWorkItem swi = getWorkItem(swir.getUniqueNumberWorkItem());
			swi.setDone(true);
		}
	}
	
	public boolean doneContains(ScanWorkItemResult swir){
		boolean hasit=false;
		int iter=done.size();
		for(int i=0;i<iter;i++){
			if(done.get(i).getUniqueNumberWorkItem().equals(swir.getUniqueNumberWorkItem())){
				hasit=true;
			}
		}
		return hasit;
	}
	
	public boolean toDoContains(ScanWorkItem swi){
		boolean hasit=false;
		int iter=toDo.size();
		for(int i=0;i<iter;i++){
			if(toDo.get(i).getUniqueNumber().equals(swi.getUniqueNumber())){
				hasit=true;
			}
		}
		return hasit;
	}
	
	public int getNumWorkItemsCompleteForTask(String taskID){
		int num=0;
		int iter=done.size();
		for(int i=0;i<iter;i++){
			if(done.get(i).getUniqueNumberTask().equals(taskID)){
				num++;
			}
		}
		return num;
	}

	public int getNumWorkItemsIncompleteForTask(String taskID){
		int num=0;
		int iter=toDo.size();
		for(int i=0;i<iter;i++){
			if(toDo.get(i).getTaskIdentifier().equals(taskID)){
				num++;
			}
		}
		return num;
	}
	
	public ArrayList<ScanWorkItem> getRemainingWorkItemsForTask(String taskID){
		ArrayList<ScanWorkItem> list = new ArrayList<ScanWorkItem>();
		int iter=toDo.size();
		for(int i=0;i<iter;i++){
			if(toDo.get(i).getTaskIdentifier().equals(taskID)){
				list.add(toDo.get(i));
			}
		}
		return list;
	}
	
	
	public ArrayList<MatchObject> getMatchObjectsForTask(String taskID){
		ArrayList<MatchObject> list=new ArrayList<MatchObject>();
		int iter=done.size();
		for(int i=0;i<iter;i++){
			if(done.get(i).getUniqueNumberTask().equals(taskID)){
				list.add(done.get(i).getResult());
			}
		}
		return list;
	}
	
	public ArrayList<ScanWorkItemResult> getResultsForTask(String taskID){
		ArrayList<ScanWorkItemResult> list=new ArrayList<ScanWorkItemResult>();
		int iter=done.size();
		for(int i=0;i<iter;i++){
			if(done.get(i).getUniqueNumberTask().equals(taskID)){
				list.add(done.get(i));
			}
		}
		return list;
	}
	
	public int getNumWorkItemsAndResults(){return (done.size()+toDo.size());}
	
	public int getToDoSize(){return toDo.size();}
	public int getDoneSize(){return done.size();}
	
	public ScanWorkItem getWorkItem(String uniqueNum){
		int iter=toDo.size();
		ScanWorkItem swi=new ScanWorkItem();
		for(int i=0;i<iter;i++){
			if(toDo.get(i).getUniqueNumber().equals(uniqueNum)){
				return toDo.get(i);
			}
		}
		return swi;
	}
	
	public int getNumProcessors(){
		int numProcessors = 0;
		ArrayList<GridNode> nodes = getNodes();
		int numNodes = nodes.size();
		for(int i=0;i<numNodes;i++){
			GridNode node = nodes.get(i);
			numProcessors+=node.numProcessors;
		}
		return numProcessors;
		
	}
	
}	

