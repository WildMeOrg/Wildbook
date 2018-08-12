package org.ecocean.security;

import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter;
import org.apache.shiro.web.util.WebUtils;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.servlet.ServletUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;

public class WildbookBasicHttpAuthenticationFilter extends
    BasicHttpAuthenticationFilter {
  
  
  protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        String authorizationHeader = getAuthzHeader(request);
        if (authorizationHeader == null || authorizationHeader.length() == 0) {
            // Create an empty authentication token since there is no
           // Authorization header.
            return createToken("", "", request, response);
       }

        //if (log.isDebugEnabled()) {
        //    log.debug("Attempting to execute login with headers [" + authorizationHeader + "]");
        //}

        String[] prinCred = getPrincipalsAndCredentials(authorizationHeader, request);
        if (prinCred == null || prinCred.length < 2) {
           // Create an authentication token with an empty password,
            // since one hasn't been provided in the request.
            String username = prinCred == null || prinCred.length == 0 ? "" : prinCred[0];
           return createToken(username, "", request, response);
        }

       String username = prinCred[0];
        String password = prinCred[1];
        
        String salt="";
        String context="context0";
        //context=ServletUtilities.getContext(request);
        Shepherd myShepherd=new Shepherd(context);
        myShepherd.setAction("WildbookBasicHttpAuthenticationFilter.class");
        myShepherd.beginDBTransaction();
        
        try{
          if(myShepherd.getUser(username)!=null){
            User user=myShepherd.getUser(username);
            salt=user.getSalt();  
            if(request.getParameter("acceptUserAgreement")!=null){
              System.out.println("Trying to set acceptance for UserAgreement!");
              user.setAcceptedUserAgreement(true);
              myShepherd.commitDBTransaction();
            }
            else{
              myShepherd.rollbackDBTransaction();
            }
          
          }
          else{
            myShepherd.rollbackDBTransaction();
          }
        }
        catch(Exception e){
          myShepherd.rollbackDBTransaction();
        }
        
        myShepherd.closeDBTransaction();
        String hashedPassword=ServletUtilities.hashAndSaltPassword(password, salt);
        
        

        return createToken(username, hashedPassword, request, response);
  }
  
  

}
