package org.ecocean.security;



import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.*;
import org.apache.shiro.subject.*;
import org.ecocean.*;
import org.apache.shiro.SecurityUtils;

import java.util.TreeSet;
import java.util.Set;
import java.util.List;

import org.apache.shiro.web.servlet.ShiroHttpServletRequest;
import org.ecocean.servlet.ServletUtilities;
import org.apache.shiro.mgt.RealmSecurityManager;

import org.apache.shiro.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;

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
        String context="context0";
       
            Shepherd myShepherd=new Shepherd(context);
            myShepherd.setAction("ShepherdRealm.class.doGetAuthenticationInfo");
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
    Shepherd myShepherd=new Shepherd("context0");
    myShepherd.setAction("ShepherdRealm.class.getPasswordForUser");
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

    protected Set getRoleNamesForUserInContext(String username,String context){
        
        Set roleNames = new TreeSet();
        //always use context0 below as all users are stored there
        String actualContext="context0";
        if(context!=null){actualContext=context;}
        
        Shepherd myShepherd=new Shepherd("context0");
        myShepherd.setAction("ShepherdRealm.class.getRolenamesForUsersInContext");
        myShepherd.beginDBTransaction();
        if(myShepherd.getUser(username)!=null){
          
            User user=myShepherd.getUser(username);
            List<Role> roles=myShepherd.getAllRolesForUserInContext(username,actualContext);
            int numRoles=roles.size();
            for(int i=0;i<numRoles;i++){
              roleNames.add(roles.get(i).getRolename());
              //System.out.println("ShepherdRealm:Adding role: "+roles.get(i).getRolename());
            }
          
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        myShepherd=null;
       
        return roleNames;
    } 
    
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
     String username = (String) principals.getPrimaryPrincipal();
     Subject subject = SecurityUtils.getSubject();
     HttpServletRequest request = WebUtils.getHttpRequest(subject); 
     String context=ServletUtilities.getContext(request);
     //System.out.println("Context in ShepherdReal is: "+context);
     //ServletContainerSessionManager.

     return new SimpleAuthorizationInfo(getRoleNamesForUserInContext(username,context));
}

}