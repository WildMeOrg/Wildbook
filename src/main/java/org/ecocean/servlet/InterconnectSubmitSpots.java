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

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SuperSpot;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

//import java.util.Vector;


//adds spots to a new encounter
public class InterconnectSubmitSpots extends HttpServlet {


  private void deleteOldScans(String side, String num) {
    try {
      //File file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFull"+side+"Scan.xml");
      File file = new File(getServletContext().getRealPath(("/encounters/" + num + "/lastFull" + side + "Scan.xml")));

      if (file.exists()) {
        file.delete();
      }
      //file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFull"+side+"I3SScan.xml");
      file = new File(getServletContext().getRealPath(("/encounters/" + num + "/lastFull" + side + "I3SScan.xml")));
      if (file.exists()) {
        file.delete();
      }
      file = new File(getServletContext().getRealPath(("/encounters/" + num + "/lastBoost" + side + "Scan.xml")));
      if (file.exists()) {
        file.delete();
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doGet(request, response);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Shepherd myShepherd = new Shepherd();
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    String num = " ";
    ArrayList<SuperSpot> spots = new ArrayList<SuperSpot>();
    boolean locked = false;
    String side = "left";

    if (request.getParameter("number") != null)
      num = request.getParameter("number").replaceAll("%20", "").trim();
    myShepherd.beginDBTransaction();

    if (myShepherd.isEncounter(num)) {

      boolean haveData = false;
      boolean ok2add = false;

      Encounter enc = myShepherd.getEncounter(num);
      try {

        if (enc.isAssignedToMarkedIndividual().equals("Unassigned")) {
          //System.out.println("Yes, shark is unassigned!");
          ok2add = true;
          for (int i = 0; i < 200; i++) {
            if ((request.getParameter("spotx" + (new Integer(i)).toString()) != null) && (request.getParameter("spoty" + (new Integer(i)).toString()) != null)) {
              //System.out.println("Got here iterating!");
              String centroidx = request.getParameter("spotx" + i).replaceAll(",", ".");
              //System.out.println(centroidx);
              String centroidy = request.getParameter("spoty" + i).replaceAll(",", ".");
              //System.out.println(centroidy);
              if ((!centroidx.equals("")) && (!centroidy.equals(""))) {
                double d_centroidx = (new Double(centroidx)).doubleValue();
                double d_centroidy = (new Double(centroidy)).doubleValue();
                SuperSpot newSpot = new SuperSpot(d_centroidx, d_centroidy);
                spots.add(newSpot);
                haveData = true;
              }
              ;
            }
          }
          if ((spots.size()) > 0) {
            ArrayList<SuperSpot> superSpots = spots;


            //this is the master object that will eventually be passed into an encounter object along with other data
            //ArrayList superSpotArray=new superSpot[superSpots.size()];
            ArrayList<SuperSpot> superSpotArray2 = superSpots;

            //let's add the three reference points
            double ref1x = (new Double(request.getParameter("ref1x"))).doubleValue();
            double ref1y = (new Double(request.getParameter("ref1y"))).doubleValue();
            double ref2x = (new Double(request.getParameter("ref2x"))).doubleValue();
            double ref2y = (new Double(request.getParameter("ref2y"))).doubleValue();
            double ref3x = (new Double(request.getParameter("ref3x"))).doubleValue();
            double ref3y = (new Double(request.getParameter("ref3y"))).doubleValue();

            ArrayList<SuperSpot> refs = new ArrayList<SuperSpot>();
            refs.add(new SuperSpot(ref1x, ref1y));
            refs.add(new SuperSpot(ref2x, ref2y));
            refs.add(new SuperSpot(ref3x, ref3y));


            if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
              side = "right";
              enc.setRightSpots(superSpotArray2);
              enc.setRightReferenceSpots(refs);
              enc.setNumRightSpots(superSpotArray2.size());
            } else {

              enc.setSpots(superSpotArray2);
              enc.setLeftReferenceSpots(refs);
              enc.setNumLeftSpots(superSpotArray2.size());
            }
            String user = "Unknown User";
            if (request.getRemoteUser() != null) {
              user = request.getRemoteUser();
            }
            enc.addComments("<p><em>" + user + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added " + side + "-side spot data." + "</p>");
          }
          ;


          String s_ref1x = request.getParameter("ref1x");
          String s_ref1y = request.getParameter("ref1y");
          String s_ref2x = request.getParameter("ref2x");
          String s_ref2y = request.getParameter("ref2y");
          String s_ref3x = request.getParameter("ref3x");
          String s_ref3y = request.getParameter("ref3y");
          //System.out.println("Ref points:"+s_ref1x+","+s_ref1y+","+s_ref2x+","+s_ref2y+","+s_ref3x+","+s_ref3y);


          //let's add the three reference points
          double ref1x = Double.parseDouble(s_ref1x);
          double ref1y = Double.parseDouble(s_ref1y);
          double ref2x = Double.parseDouble(s_ref2x);
          double ref2y = Double.parseDouble(s_ref2y);
          double ref3x = Double.parseDouble(s_ref3x);
          double ref3y = Double.parseDouble(s_ref3y);


        } else {
          out.println(ServletUtilities.getHeader(request));
          out.println("<p>You are not allowed to modify spot data for an encounter that belongs to a shark. Please remove the encounter from the shark before attempting to modify its spot data.</p>");
          out.println(ServletUtilities.getFooter());
          ok2add = false;
          myShepherd.rollbackDBTransaction();

        }

      } catch (NullPointerException npe) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<p>The spot pattern was only partially transmitted, resulting in a NullPointerException.</p>");
        out.println(ServletUtilities.getFooter());
        npe.printStackTrace();
        ok2add = false;
      } catch (Exception lock) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<p>This encounter object is in a locked state and may be in use by another user or may be locked in error.</p>");
        out.println(ServletUtilities.getFooter());
        lock.printStackTrace();
        ok2add = false;
      }


      if (!locked && ok2add) {
        myShepherd.commitDBTransaction();
        if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
          deleteOldScans("Right", num);
        } else {
          deleteOldScans("", num);
        }


        myShepherd.beginDBTransaction();
        //let's try to nicely format the ouput. otherwise, just output the confirmation text
        try {

          out.println(ServletUtilities.getHeader(request));
          if (locked) {
            out.println("<p>This object is currently in use by another user. Please wait a few seconds and then attempt to add spot data again.</p>");
          } else if ((enc.getSpots() != null) && haveData && (side.equals("left"))) {
            out.print("<strong>Step 1 Confirmed:</strong> Thank you for submitting " + side + "-side spot data for encounter number " + num + "!</p>\n");
            if (side.equals("right")) {
              out.println("<p>" + enc.getNumRightSpots() + " right-side spots were successfully added.</p>");
            } else {
              out.println("<p>" + enc.getNumSpots() + " left-side spots were successfully added.</p>");
            }

            out.println("<p><strong>Step 2: Upload the spot " + side + "-side image that you extracted the spot data from.</strong>");
            out.println("<form action=\"EncounterAddSpotFile\" method=\"post\" enctype=\"multipart/form-data\" name=\"addSpotsFile\">");
            out.println("<input name=\"action\" type=\"hidden\" value=\"fileadder\" id=\"action\">");
            out.println("<input name=\"number\" type=\"hidden\" value=\"" + num + "\" id=\"number\">");
            if (side.equals("right")) {
              out.println("<input name=\"rightSide\" type=\"hidden\" value=\"true\" id=\"rightSide\">");
            }
            out.println("<p>Add spot data image file:</p>");
            out.println("<p><input name=\"file2add\" type=\"file\" size=\"20\"></p>");
            out.println("<p><input name=\"addtlFile\" type=\"submit\" id=\"addtlFile\" value=\"Upload spot image file\"></p>");
            out.println("</form><p><i>Other options: </i></p>");
            String message = "Spot-matching data was uploaded for encounter#" + num + ".";
            ServletUtilities.informInterestedParties(request, num, message);

          }
          //check for right-side spot submissions
          else if ((enc.getRightSpots() != null) && haveData && (side.equals("right"))) {
            out.print("<strong>Step 1 Confirmed:</strong> Thank you for submitting " + side + "-side spot data for encounter number " + num + "!</p>\n");
            if (side.equals("right")) {
              out.println("<p>" + enc.getNumRightSpots() + " right-side spots were successfully added.</p>");
            } else {
              out.println("<p>" + enc.getNumSpots() + " left-side spots were successfully added.</p>");
            }

            out.println("<p><strong>Step 2: Upload the spot " + side + "-side image that you extracted the spot data from.</strong>");
            out.println("<form action=\"EncounterAddSpotFile\" method=\"post\" enctype=\"multipart/form-data\" name=\"addSpotsFile\">");
            out.println("<input name=\"action\" type=\"hidden\" value=\"fileadder\" id=\"action\">");
            out.println("<input name=\"number\" type=\"hidden\" value=\"" + num + "\" id=\"number\">");
            if (side.equals("right")) {
              out.println("<input name=\"rightSide\" type=\"hidden\" value=\"true\" id=\"rightSide\">");
            }
            out.println("<p>Add spot data image file:</p>");
            out.println("<p><input name=\"file2add\" type=\"file\" size=\"20\"></p>");
            out.println("<p><input name=\"addtlFile\" type=\"submit\" id=\"addtlFile\" value=\"Upload spot image file\"></p>");
            out.println("</form><p><i>Other options: </i></p>");
            String message = "Spot-matching data was uploaded for encounter#" + num + ".";
            ServletUtilities.informInterestedParties(request, num, message);

          } else if ((enc.getSpots() != null) && (!haveData) && (side.equals("left"))) {
            out.println("<p>You didn't submit any data!</p>");
          } else {
            out.println("<p>Unfortunately, no spots were added to this shark. Check to see if you correctly added any data in the previous form.</p>");
          }
          ;
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + num + "\">Return to encounter #" + num + "</a></p>\n");
          out.println(ServletUtilities.getFooter());
        } catch (Exception genericE) {
          locked = true;
          genericE.printStackTrace();
          myShepherd.rollbackDBTransaction();

          ok2add = false;
        }

        if (ok2add) {

          myShepherd.commitDBTransaction();

        }
      }

    } else {
      myShepherd.rollbackDBTransaction();
      try {
        out.println(ServletUtilities.getHeader(request));
        out.println("<p>You did not specify a valid number for this encounter: " + num + "</p>");
        out.println(ServletUtilities.getFooter());
      } catch (Exception e) {
        out.println("I couldn't find the template file to write to, but the spots were added successfully.");
        e.printStackTrace();
      }
    }
    out.close();
    myShepherd.closeDBTransaction();
  }

}