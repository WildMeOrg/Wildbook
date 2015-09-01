<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
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
         import="org.ecocean.ShepherdProperties,org.ecocean.servlet.ServletUtilities,org.apache.shiro.crypto.*,org.apache.shiro.util.*,org.apache.shiro.crypto.hash.*,org.ecocean.*,
org.pac4j.core.client.*,
org.pac4j.core.context.*,
org.pac4j.oauth.core.*,
org.pac4j.oauth.client.*,
org.pac4j.oauth.credentials.*,
org.pac4j.oauth.profile.facebook.*,
org.ecocean.servlet.ServletUtilities,org.ecocean.grid.GridManager,org.ecocean.grid.GridManagerFactory, java.util.Properties,java.util.ArrayList" %>


<%

String langCode=ServletUtilities.getLanguageCode(request);

   String context=ServletUtilities.getContext(request);

  Shepherd myShepherd = new Shepherd(context);
		//User newUser=new User("tomcat",hashedPassword,salt);
		//myShepherd.getPM().makePersistent(newUser);

FacebookClient fbclient = new FacebookClient();
WebContext ctx = new J2EContext(request, response);
fbclient.setCallbackUrl("http://localhost.wildme.org/a/auth-test-return.jsp");
fbclient.redirect(ctx, false, false);


%>

end?
