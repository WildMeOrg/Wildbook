package org.ecocean.grid;

//unenhanced comment

import org.ecocean.grid.ScanWorkItemResult;
//import java.io.Serializable;
//import java.util.Properties;

/**
 *An <code>encounter</code> object stores the complete data for a single sighting.
 *<code>Encounters</code> are added to MarkedIndividual objects as multiple encounters are associated with
 *known individuals. 
 *<p>
 *@author	Jason Holmberg
 *@version	1.2
*/
public class ScanWorkItemResult implements java.io.Serializable{
	static final long serialVersionUID = -146404246317385604L;
	private String uniqueNumWI;
	private String uniqueNumTask;
	private String workItemResultUniqueID;
	
	private MatchObject result;
	private I3SMatchObject i3sResult;
	
	/**
	 *empty constructor required by JDO Enhancer. DO NOT USE.
	 */
	public ScanWorkItemResult() {}
	
	public ScanWorkItemResult(String uniqueNumberOfTask, String uniqueNumberOfWorkItem, MatchObject result) {
		this.result=result;
		this.uniqueNumWI=uniqueNumberOfWorkItem;
		this.uniqueNumTask=uniqueNumberOfTask;
		this.workItemResultUniqueID=uniqueNumberOfWorkItem+"_result";
		this.i3sResult=i3sResult;
	}
	
	public MatchObject getResult(){
		return result;
	}
	
	public I3SMatchObject getI3SResult(){
		return i3sResult;
	}
	
		/**
	*Returns the unique number for this workItem.
	*/	
	public String getUniqueNumberWorkItem(){
		return uniqueNumWI;
	}
	
	public String getUniqueNumberWorkItemResult(){
		return workItemResultUniqueID;
	}
	
	public String getUniqueNumberTask(){
		return uniqueNumTask;
	}
	
	
}