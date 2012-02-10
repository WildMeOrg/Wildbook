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

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.jdo.JDOException;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManagerFactory;


public class ShepherdPMF {

  private static PersistenceManagerFactory pmf;

  public synchronized static PersistenceManagerFactory getPMF() {
    //public static PersistenceManagerFactory getPMF(String dbLocation) {
    try {
      if (pmf == null) {

        Properties dnProperties = new Properties();


        dnProperties.setProperty("datanucleus.PersistenceManagerFactoryClass", "org.datanucleus.api.jdo.PersistenceManagerFactoryImpl");

        //class setup
        Enumeration<?> e = CommonConfiguration.getPropertyNames();
        while(e.hasMoreElements()) {
          String name = (String) e.nextElement();
          if (name.startsWith("datanucleus") || name.startsWith("javax.jdo")) {
            dnProperties.setProperty(name, CommonConfiguration.getProperty(name).trim());
          }
        }

        pmf = JDOHelper.getPersistenceManagerFactory(dnProperties);
      }
      return pmf;
    } catch (JDOException jdo) {
      jdo.printStackTrace();
      System.out.println("I couldn't instantiate a PMF.");
      return null;
    }
  }

}
