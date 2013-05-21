/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean;

import net.sourceforge.jwebunit.junit.WebTestCase;

/**
 * Created by IntelliJ IDEA.
 * User: mmcbride
 * Date: 2/24/11
 * Time: 2:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class AdminIT extends WebTestCase {
  public void setUp() throws Exception {
    super.setUp();
    setBaseUrl("http://localhost:9090/shepherd");
  }

  public void testResourcesAreProtected() {
    beginAt("/index.jsp");
    gotoPage("/appadmin/admin.jsp");
    assertResponseCode(200);
    assertTextPresent("Username");
  }

  /*
  public void testGeneralAdmin() {
    login();
    gotoPage("/appadmin/admin.jsp");
    assertTextPresent("Username");
  }

  public void testKeywordAdmin() {
    login();
    gotoPage("/appadmin/kwAdmin.jsp");
    assertTextPresent("Username");
  }
*/
  
  protected void login() {
    beginAt("/index.jsp");
    clickLinkWithExactText("Log in");
    setTextField("username", "tomcat");
    setTextField("password", "tomcat123");
    submit();
    assertTextPresent("Login success!");
  }
}
