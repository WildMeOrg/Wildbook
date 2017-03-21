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

package org.ecocean.servlet;

import org.ecocean.Adoption;
import org.ecocean.CommonConfiguration;
import org.ecocean.Shepherd;
import org.ecocean.ShepherdProperties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Properties;

import java.io.*;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;

public class DeleteAdoption extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("DeleteAdoption.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    
    Properties props=ShepherdProperties.getProperties("stripeKeys.properties","",context);

    Stripe.apiKey = props.getProperty("publicKey");

    String sharkID = request.getParameter("sharkID");
    String customerID = request.getParameter("customerID");
    String adoptionID = request.getParameter("adoptionID");
    System.out.println("Shark : " + sharkID );
    System.out.println("Adoption : " + adoptionID );
    System.out.println("Customer : " + customerID );
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File adoptionsDir=new File(shepherdDataDir.getAbsolutePath()+"/adoptions");


    myShepherd.beginDBTransaction();
    if ((myShepherd.isAdoption(adoptionID))) {

      Adoption ad = myShepherd.getAdoptionDeepCopy(adoptionID);
      if((request.getParameter("customerID")==null)&&(ad.getStripeCustomerId()!=null)){
        customerID=ad.getStripeCustomerId();
      }

      
      // This section attempts to delete stripe subscription.
      if ((customerID != null)&&(customerID != "")) {
        try {
          Customer customer = Customer.retrieve(customerID);
          customer.delete();
        } catch (StripeException se) {
          System.out.println("External Stripe exception deleting customer.");
        } catch (Exception e) {
          System.out.println("Internal exception deleting customer.");
        }
      }

      // This section deletes adoption from database.
      try {
        //Adoption ad = myShepherd.getAdoptionDeepCopy(adoptionID);

        String savedFilename = request.getParameter("adoptionID") + ".dat";
        //File thisEncounterDir=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getAdoptionDirectory()+File.separator+request.getParameter("number"));
        File thisAdoptionDir = new File(adoptionsDir.getAbsolutePath()+"/" + request.getParameter("adoptionID"));
        if(!thisAdoptionDir.exists()){thisAdoptionDir.mkdirs();}


        File serializedBackup = new File(thisAdoptionDir, savedFilename);
        FileOutputStream fout = new FileOutputStream(serializedBackup);
        ObjectOutputStream oos = new ObjectOutputStream(fout);
        oos.writeObject(ad);
        oos.close();

        Adoption ad2 = myShepherd.getAdoption(adoptionID);

        myShepherd.throwAwayAdoption(ad2);


      } catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully removed adoption " + adoptionID + ". However, a saved copy an still be retrieved by the webmaster.");

        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "Return to Wildbook</a></p>\n");
        out.println("To restore, cont ");
        out.println(ServletUtilities.getFooter(context));
      }
      else {

        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> I failed to delete this adoption. Try again, and if this error continues to occur contact the webmaster.");
        out.println("The webmaster can be reached at <strong>adoptions at whaleshark dot org</strong>.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "Return to Wildbook</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      }

    } else {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to remove your adoption file. I cannot find the encounter that you intended it for in the database.");
      out.println("<strong>Error:</strong> I was unable to remove your adoption file. I cannot find the adoption in the database. If you feel you have reached this in error, contact the webmaster.");
      out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "Return to Wildbook</a></p>\n");	
      out.println(ServletUtilities.getFooter(context));

    }
    out.close();
  }


}
