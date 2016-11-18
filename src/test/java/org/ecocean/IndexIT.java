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

//import net.sourceforge.jwebunit.junit.WebTestCase;
import static net.sourceforge.jwebunit.junit.JWebUnit.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class IndexIT {

  @Before
  public void prepare() throws Exception {
    //super.setUp();
    setBaseUrl("http://localhost:9090/wildbook");
  }
  
  @Test
  public void testHome() throws Exception {
    setScriptingEnabled(false);
    beginAt("/");
    assertResponseCode(200);
    assertTextPresent("Encounters");
  }

  @Test
  public void testIndex() throws Exception {
    setScriptingEnabled(false);
    beginAt("/index.jsp");
    assertResponseCode(200);
    assertTextPresent("Encounters");
    
    
  }
  
  @After
  public void close() {
    closeBrowser();
  }
}
