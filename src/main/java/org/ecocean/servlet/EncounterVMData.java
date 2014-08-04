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
import org.ecocean.SinglePhotoVideo;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;

import com.google.gson.Gson;


public class EncounterVMData extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  private void setDateLastModified(Encounter enc) {

    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Shepherd myShepherd = new Shepherd();
    //set up for response
    response.setContentType("text/json");
    PrintWriter out = response.getWriter();
    boolean locked = false, isOwner = true;
    //boolean isAssigned = false;
		HashMap rtn = new HashMap();


		if (request.getParameter("number") != null) {
			myShepherd.beginDBTransaction();
			Encounter enc = myShepherd.getEncounter(request.getParameter("number"));

			if (enc == null) {
				rtn.put("error", "invalid Encounter number");

			} else if (request.getParameter("candidates") != null) {
				ArrayList candidates = new ArrayList();
				String filter = "this.catalogNumber != \"" + enc.getCatalogNumber() + "\"";
				String[] fields = {"locationID", "sex", "patterningCode"};
				for (String f : fields) {
					String val = request.getParameter(f);
					if (val != null) filter += " && this." + f + " == \"" + val + "\"";  //TODO safely quote!  sql injection etc
				}
				String mma = request.getParameter("mmaCompatible");
				if ((mma != null) && !mma.equals("")) {
					if (mma.equals("true")) {
						filter += " && this.mmaCompatible == true";
					} else {
						filter += " && (this.mmaCompatible == false || this.mmaCompatible == null)";
					}
				}
System.out.println("candidate filter => " + filter);

				Iterator all = myShepherd.getAllEncounters("catalogNumber", filter);
				while (all.hasNext()) {
					Encounter cand = (Encounter)all.next();
					HashMap e = new HashMap();
					e.put("id", cand.getCatalogNumber());
					e.put("dateInMilliseconds", cand.getDateInMilliseconds());
					e.put("locationID", cand.getLocationID());
					e.put("individualID", cand.getIndividualID());
					ArrayList<SinglePhotoVideo> spvs = myShepherd.getAllSinglePhotoVideosForEncounter(cand.getCatalogNumber());
					ArrayList images = new ArrayList();
					String dataDir = CommonConfiguration.getDataDirectoryName() + "/encounters/";
					for (SinglePhotoVideo s : spvs) {
						if (myShepherd.isAcceptableImageFile(s.getFilename())) {
							HashMap i = new HashMap();
							i.put("url", "/" + dataDir + "/" + cand.getCatalogNumber() + "/" + s.getFilename());
							List k = s.getKeywords();
							i.put("keywords", k);
							images.add(i);
						}
					}
					e.put("images", images);
					candidates.add(e);
				}
				rtn.put("candidates", candidates);

			} else {
				ArrayList<SinglePhotoVideo> spvs = myShepherd.getAllSinglePhotoVideosForEncounter(enc.getCatalogNumber());
				String dataDir = CommonConfiguration.getDataDirectoryName() + "/encounters/" + enc.getCatalogNumber();

				ArrayList images = new ArrayList();
				for (SinglePhotoVideo s : spvs) {
					if (myShepherd.isAcceptableImageFile(s.getFilename())) {
						HashMap i = new HashMap();
						i.put("url", "/" + dataDir + "/" + s.getFilename());
						List k = s.getKeywords();
						i.put("keywords", k);
						images.add(i);
					}
				}
		
				rtn.put("id", enc.getCatalogNumber());
				rtn.put("patterningCode", enc.getPatterningCode());
				rtn.put("sex", enc.getSex());
				rtn.put("locationID", enc.getLocationID());
				rtn.put("individualID", enc.getIndividualID());
				rtn.put("dateInMilliseconds", enc.getDateInMilliseconds());
				rtn.put("mmaCompatible", enc.getMmaCompatible());
				rtn.put("images", images);
			}


		} else {
			rtn.put("error", "invalid Encounter number");
		}


    out.println(new Gson().toJson(rtn));
    out.close();
		myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();
  }

}
