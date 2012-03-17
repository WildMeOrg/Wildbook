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
 * tests for /submit.jsp
 */
public class SubmitIT extends WebTestCase {
  public void setUp() throws Exception {
    super.setUp();
    setBaseUrl("http://localhost:9090/shepherd");
  }
  public void testSubmit() {
    beginAt("/index.jsp");
    clickLinkWithExactText("Participate");
    // necessary fields
    // encounter date (day, month, year, hour, minutes)
    // sighting location
    // submitterName
    // submitterEmail
    // theFile1 - theFile4 (at least 1)
    selectOption("day", "1");
    selectOption("month", "1");
    selectOption("year", "2011");
    selectOption("hour", "12 am");
    selectOption("minutes", ":00");
    setTextField("location", "the world");
    setTextField("submitterName", "mark");
    setTextField("submitterEmail", "mark.mcbride@gmail.com");
    setTextField("theFile1", "src/main/webapp/images/logbook.gif");
    submit("Submit");
    assertResponseCode(200);
  }
}
