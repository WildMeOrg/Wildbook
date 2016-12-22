package org.ecocean;

import java.util.Date;
import java.io.Serializable;

/**
 * <code>User</code> stores information about a contact/user.
 * Examples: photographer, submitter
 * @author Ed Stastny
 */
public class Role implements java.io.Serializable {
  
  
  private static final long serialVersionUID = -7034712240056255450L;
 

  
    private String username;
    private String rolename ;
    private String context;
  
    
    //JDOQL required empty instantiator
    public Role(){}
    
    public Role(String username,String rolename) {
      this.username=username;
      this.rolename=rolename;
    }

 
    public String getUsername() {
      return username;
    }
    public void setUsername(String username) {
      this.username = username;
    }
  
    public String getRolename() {
      return rolename;
    }
    public void setRolename(String rolename) {
      this.rolename = rolename;
    }
    
    public String getContext() {
      if(context==null)return "context0";
      return context;
    }
    
    public void setContext(String newContext) {
      this.context = newContext;
    }
  


}
