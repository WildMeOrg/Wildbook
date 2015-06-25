<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2008-2015 Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.Shepherd,
             org.ecocean.User,
             org.ecocean.Role,
             org.ecocean.servlet.ServletUtilities,
             org.ecocean.grid.GridManager,
             org.ecocean.grid.GridManagerFactory,
             org.ecocean.CommonConfiguration,
             java.util.ArrayList"
%>

<%
String context="context0";
context=ServletUtilities.getContext(request);

//grab a gridManager
GridManager gm = GridManagerFactory.getGridManager();
int numProcessors = gm.getNumProcessors();
int numWorkItems = gm.getIncompleteWork().size();

Shepherd myShepherd = new Shepherd(context);
  
//check usernames and passwords
myShepherd.beginDBTransaction();
ArrayList<User> users=myShepherd.getAllUsers();
if (users.size() == 0) {
    String salt=ServletUtilities.getSalt().toHex();
    String hashedPassword=ServletUtilities.hashAndSaltPassword("tomcat123", salt);
    //System.out.println("Creating default hashed password: "+hashedPassword+" with salt "+salt);
    
    User newUser = new User("tomcat",hashedPassword,salt);
    myShepherd.getPM().makePersistent(newUser);
    System.out.println("Creating tomcat user account...");
    
    ArrayList<Role> roles=myShepherd.getAllRoles();
    if (roles.size()==0) {
        System.out.println("Creating tomcat roles...");
        
        Role newRole1=new Role("tomcat","admin");
        newRole1.setContext("context0");
        myShepherd.getPM().makePersistent(newRole1);
        Role newRole4=new Role("tomcat","destroyer");
        newRole4.setContext("context0");
        myShepherd.getPM().makePersistent(newRole4);
        
        Role newRole5=new Role("tomcat","manager");
        newRole5.setContext("context0");
        myShepherd.getPM().makePersistent(newRole5);
        
        Role newRole6=new Role("tomcat","adoption");
        newRole6.setContext("context0");
        myShepherd.getPM().makePersistent(newRole6);
        
        Role newRole7=new Role("tomcat","imageProcessor");
        newRole7.setContext("context0");
        myShepherd.getPM().makePersistent(newRole7);
        
        Role newRole8=new Role("tomcat","approve");
        newRole8.setContext("context0");
        myShepherd.getPM().makePersistent(newRole8);
        Role newRole9=new Role("tomcat","identifier");
        newRole9.setContext("context0");
        myShepherd.getPM().makePersistent(newRole9);
        Role newRole2=new Role("tomcat","researcher");
        newRole2.setContext("context0");
        myShepherd.getPM().makePersistent(newRole2);
        System.out.println("Creating tomcat user account...");
    }
    
    myShepherd.commitDBTransaction();
}
%>

<html xmlns="http://www.w3.org/1999/xhtml">
    <head>
      <title><%=CommonConfiguration.getHTMLTitle(context)%>
      </title>
      <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
      <meta name="Description"
            content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
      <meta name="Keywords"
            content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
      <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
      <link href="<%=CommonConfiguration.getCSSURLLocation(request, context) %>"
            rel="stylesheet" type="text/css"/>
      <link rel="shortcut icon"
            href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>
      <link href='http://fonts.googleapis.com/css?family=Oswald:400,300,700' rel='stylesheet' type='text/css'>
      <link rel="stylesheet" href="cust/mantamatcher/css/manta.css" />

      <script src="tools/jquery/js/jquery.min.js"></script>
      <script src="tools/bootstrap/js/bootstrap.min.js"></script>

    </head>
    <body role="document">
        <!-- ****header**** -->
        <header class="page-header clearfix">
            <nav class="navbar navbar-default navbar-fixed-top">
              <div class="header-top-wrapper">
                <div class="container">
                  <div class="search-and-secondary-wrapper">
                    <ul class="secondary-nav hor-ul no-bullets">
                      <li><a href="#" title="">English</a></li><li><a href="#" title="">Login</a></li><li><a href="#" title="">User wiki</a></li>
                    </ul>
                    <div class="search-wrapper">
                      <label class="search-field-header">
                        <input placeholder="record nr., encounter nr., nickname or id" />
                        <input type="submit" value="search" />
                      </label>
                    </div>
                  </div>
                  <a class="navbar-brand" href="/">MantaMatcher the wildbook for manta rays</a>
                </div>
              </div>
              <div class="nav-bar-wrapper">
                <div class="container">
                  <div class="navbar-header clearfix">
                    <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#navbar" aria-expanded="false" aria-controls="navbar">
                      <span class="sr-only">Toggle navigation</span>
                      <span class="icon-bar"></span>
                      <span class="icon-bar"></span>
                      <span class="icon-bar"></span>
                    </button>
                  </div>
                  <div id="navbar" class="navbar-collapse collapse">
                    <ul class="nav navbar-nav">
                                  <!--                -->
                      <li class="active home text-hide"><a href="/">Home</a></li>
                      <li><a href="#">Report Encounter</a></li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">Learn <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                          <li><a href="#">Action</a></li>
                          <li><a href="#">Another action</a></li>
                        </ul>
                      </li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">Participate <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                          <li><a href="#">Action</a></li>
                          <li><a href="#">Another action</a></li>
                          <li><a href="#">Something else here</a></li>
                          <li class="divider"></li>
                          <li class="dropdown-header">Nav header</li>
                          <li><a href="#">Separated link</a></li>
                          <li><a href="#">One more separated link</a></li>
                        </ul>
                      </li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">Individuals <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                          <li><a href="#">Action</a></li>
                        </ul>
                      </li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">Encounters <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                          <li><a href="#">Action</a></li>
                        </ul>
                      </li>
                      <li class="dropdown">
                        <a href="#">About</a>
                      </li>
                      <li>
                        <a href="#">Contact </a>
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
            </nav>
        </header>
        <!-- ****/header**** -->

        <div class="wbcontent">
