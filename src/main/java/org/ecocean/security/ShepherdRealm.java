package org.ecocean.security;



import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.*;
import org.apache.shiro.subject.*;


import org.ecocean.*;

import java.util.TreeSet;
import java.util.Set;
import java.util.ArrayList;

import org.ecocean.servlet.ServletUtilities;

public class ShepherdRealm extends AuthorizingRealm {

 
 
  public ShepherdRealm() {
    super();
  }


    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {

        UsernamePasswordToken upToken = (UsernamePasswordToken) token;
        String username = upToken.getUsername();

        // Null username is invalid
        if (username == null) {
            throw new AccountException("Null usernames are not allowed by this realm.");
        }

        AuthenticationInfo info = null;
       
            Shepherd myShepherd=new Shepherd();
            myShepherd.beginDBTransaction();
        
            String password = "";
            //getPasswordForUser(conn, username);

            if (myShepherd.getUser(username)==null) {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                myShepherd=null;
                throw new UnknownAccountException("No account found for user [" + username + "]");
            }
            else{
              
              User user=myShepherd.getUser(username);
              String fullName="";
              if(user.getFullName()!=null){fullName=user.getFullName();}
              info = new SimpleAuthenticationInfo(username, user.getPassword().toCharArray(), fullName);

              myShepherd.rollbackDBTransaction();
              myShepherd.closeDBTransaction();
              myShepherd=null;

            return info;
            }
    } 

  private String getPasswordForUser(String username) {

    String password = null;
    Shepherd myShepherd=new Shepherd();
    myShepherd.beginDBTransaction();
    if(myShepherd.getUser(username)!=null){
      User user=myShepherd.getUser(username);
      password=user.getPassword();
    }
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    myShepherd=null;
    return password;
  }

    protected Set getRoleNamesForUser(String username){
        
        Set roleNames = new TreeSet();
        Shepherd myShepherd=new Shepherd();
        myShepherd.beginDBTransaction();
        if(myShepherd.getUser(username)!=null){
          
            User user=myShepherd.getUser(username);
            ArrayList<Role> roles=myShepherd.getAllRolesForUser(username);
            int numRoles=roles.size();
            for(int i=0;i<numRoles;i++){
              roleNames.add(roles.get(i).getRolename());
            }
          
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        myShepherd=null;
       
        return roleNames;
    } 
    
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
     String username = (String) principals.getPrimaryPrincipal();
     return new SimpleAuthorizationInfo(getRoleNamesForUser(username));
}

}