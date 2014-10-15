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

import org.ecocean.*;

/*
import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;
*/

import org.datanucleus.api.rest.RestServlet;

//import javax.servlet.ServletConfig;
//import javax.servlet.http.HttpServlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

/*
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;

import java.util.Properties;
*/


public class Api extends RestServlet {
	private ApiAccess access = new ApiAccess();

/*
  public void init(ServletConfig config) throws ServletException {
	super.init(config);
  }
*/


	/*
     note: doPut just calls doPost in datanucleus.RestServlet
     this duplicates (rather than override) RestServlet.doPost(), as it needs certain parsed info (e.g. class, fields changed) to do its thing
	*/
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		access.initConfig(request);

		if (req.getContentLength() < 1)
		{
			resp.setContentLength(0);
			resp.setStatus(400);// bad request
			return;
		}

		char[] buffer = new char[req.getContentLength()];
		req.getReader().read(buffer);
		String str = new String(buffer);
		JSONObject jsonobj;
		PersistenceManager pm = pmf.getPersistenceManager();
		ExecutionContext ec = ((JDOPersistenceManager)pm).getExecutionContext();
		try
		{
			pm.currentTransaction().begin();
			jsonobj = new JSONObject(str);
			String className = getNextTokenAfterSlash(req);
			jsonobj.put("class", className);

			// Process any id info provided in the URL
			AbstractClassMetaData cmd = ec.getMetaDataManager().getMetaDataForClass(className, ec.getClassLoaderResolver());
			String path = req.getRequestURI().substring(req.getContextPath().length() + req.getServletPath().length());
			StringTokenizer tokenizer = new StringTokenizer(path, "/");
			tokenizer.nextToken(); // className
			if (tokenizer.hasMoreTokens())
			{
				String idToken = tokenizer.nextToken();
				Object id = RESTUtils.getIdentityForURLToken(cmd, idToken, nucCtx);
				if (id != null)
				{
					if (cmd.getIdentityType() == IdentityType.APPLICATION)
					{
						if (cmd.usesSingleFieldIdentityClass())
						{
							jsonobj.put(cmd.getPrimaryKeyMemberNames()[0], IdentityUtils.getTargetKeyForSingleFieldIdentity(id));
						}
					}
					else if (cmd.getIdentityType() == IdentityType.DATASTORE)
					{
						jsonobj.put("_id", IdentityUtils.getTargetKeyForDatastoreIdentity(id));
					}
				}
			}

			Object pc = RESTUtils.getObjectFromJSONObject(jsonobj, className, ec);
			Object obj = pm.makePersistent(pc);
			JSONObject jsonobj2 = RESTUtils.getJSONObjectFromPOJO(obj, ec);
			resp.getWriter().write(jsonobj2.toString());
			resp.setHeader("Content-Type", "application/json");
			pm.currentTransaction().commit();
		}
		catch (ClassNotResolvedException e)
		{
			try
			{
				JSONObject error = new JSONObject();
				error.put("exception", e.getMessage());
				resp.getWriter().write(error.toString());
				resp.setStatus(500);
				resp.setHeader("Content-Type", "application/json");
				LOGGER_REST.error(e.getMessage(), e);
			}
			catch (JSONException e1)
			{
				throw new RuntimeException(e1);
			}
		}
		catch (NucleusUserException e)
		{
			try
			{
				JSONObject error = new JSONObject();
				error.put("exception", e.getMessage());
				resp.getWriter().write(error.toString());
				resp.setStatus(400);
				resp.setHeader("Content-Type", "application/json");
				LOGGER_REST.error(e.getMessage(), e);
			}
			catch (JSONException e1)
			{
				throw new RuntimeException(e1);
			}
		}
		catch (NucleusException e)
		{
			try
			{
				JSONObject error = new JSONObject();
				error.put("exception", e.getMessage());
				resp.getWriter().write(error.toString());
				resp.setStatus(500);
				resp.setHeader("Content-Type", "application/json");
				LOGGER_REST.error(e.getMessage(), e);
			}
			catch (JSONException e1)
			{
				throw new RuntimeException(e1);
			}
		}
		catch (JSONException e)
		{
			try
			{
				JSONObject error = new JSONObject();
				error.put("exception", e.getMessage());
				resp.getWriter().write(error.toString());
				resp.setStatus(500);
				resp.setHeader("Content-Type", "application/json");
				LOGGER_REST.error(e.getMessage(), e);
			}
			catch (JSONException e1)
			{
				throw new RuntimeException(e1);
			}
		}
		finally
		{
			if (pm.currentTransaction().isActive())
			{
				pm.currentTransaction().rollback();
			}
			pm.close();
		}
		resp.setStatus(201);// created
	}




/*
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	doPost(request, response);
  }

  public void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//request.getMethod();
*/

}


