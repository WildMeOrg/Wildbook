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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.CommonConfiguration;

/**
 * Servlet base class which dispatches calls to delegate methods based on the
 * value of the extra path info in the servlet URL.
 * For example, if the servlet is called with:
 *     {@code &lt;servletPath&gt;/foo}
 * then the method
 *     {@code foo(HttpServletRequest, HttpServletResponse)}
 * will be called within the servlet. Within the context of this class,
 * the method {@code foo} is considered the delegate method.
 * <p>
 * Sub-classes should register which methods they use with the various
 * {@code registerXXX()} methods, otherwise they will not be accessible.
 * This is best done in the {@code init()} method.
 *
 * @author Giles Winstanley
 */
abstract class DispatchServlet extends HttpServlet
{
  /** Class references for reflection. */
  protected static final Class[] SERVLET_ARGS = {HttpServletRequest.class, HttpServletResponse.class};
  /** List of method names supporting GET. */
  private final Set<String> methodsGET = new HashSet<String>();
  /** List of method names supporting POST. */
  private final Set<String> methodsPOST = new HashSet<String>();

  /**
   * @return The data directory used for web application storage.
   */
  protected File getDataDir(String context) throws FileNotFoundException {
    return CommonConfiguration.getDataDirectory(getServletContext(), context);
  }

  /**
   * @return The data directory used for web application storage.
   */
  protected File getUsersDataDir(String context) throws FileNotFoundException {
    return CommonConfiguration.getUsersDataDirectory(getServletContext(), context);
  }

  /**
   * Registers the named methods as valid for GET requests.
   * @param methodName name of method to register
   * @throws DelegateNotFoundException if the named method is not found
   */
  protected final void registerMethodGET(String... methodName) throws DelegateNotFoundException {
    for (String s : methodName) {
      if (getDelegateMethod(s) != null)
        methodsGET.add(s);
    }
  }

  /**
   * Unregisters the named methods as valid for GET requests.
   * @param methodName name of method to register
   * @throws DelegateNotFoundException if the named method is not found
   */
  protected final void unregisterMethodGET(String... methodName) throws DelegateNotFoundException {
    for (String s : methodName)
        methodsGET.remove(s);
  }

  /**
   * Registers the named methods as valid for POST requests.
   * @param methodName name of method to register
   * @throws DelegateNotFoundException if the named method is not found
   */
  protected final void registerMethodPOST(String... methodName) throws DelegateNotFoundException {
    for (String s : methodName) {
      if (getDelegateMethod(s) != null)
        methodsPOST.add(s);
    }
  }

  /**
   * Unregisters the named methods as valid for POST requests.
   * @param methodName name of method to register
   * @throws DelegateNotFoundException if the named method is not found
   */
  protected final void unregisterMethodPOST(String... methodName) throws DelegateNotFoundException {
    for (String s : methodName)
        methodsPOST.remove(s);
  }

  /**
   * Processes GET request, which is delegated to another method based on path.
   */
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    try {
      String mn = parseDelegateMethodName(req);
      if (mn == null || !methodsGET.contains(mn)) {
        handleDelegateNotFound(req, res);
        return;
      }
      dispatchToDelegate(this, getDelegateMethod(mn), req, res);
    } catch (DelegateNotFoundException cnfx) {
      handleDelegateNotFound(req, res);
    }
  }

  /**
   * Processes POST request, which is delegated to another method based on path.
   */
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    try {
      String mn = parseDelegateMethodName(req);
      if (mn == null || !methodsPOST.contains(mn)) {
        handleDelegateNotFound(req, res);
        return;
      }
      dispatchToDelegate(this, getDelegateMethod(parseDelegateMethodName(req)), req, res);
    } catch (DelegateNotFoundException cnfx) {
      handleDelegateNotFound(req, res);
    }
  }

  /**
   * Dispatches control to the supplied method.
   *
   * @param target servlet in which to look for target method
   * @param method delegate method to which to dispatch control
   * @param req servlet request
   * @param res servlet response
   */
  protected void dispatchToDelegate(HttpServlet target, Method method, HttpServletRequest req, HttpServletResponse res) throws DelegateNotFoundException {
    try {
      method.invoke(target, new Object[]{req, res});
    } catch (IllegalAccessException iax) {
      throw new DelegateNotFoundException("Couldn't access method", iax);
    } catch (InvocationTargetException itx) {
      itx.getTargetException().printStackTrace();
      try {
        PrintWriter pw = res.getWriter();
        itx.getTargetException().printStackTrace(pw);
        pw.flush();
      } catch (IOException iox) {
      }
    }
  }

  /**
   * Returns the delegate {@code Method} of the class instance with the
   * specified method name. The delegate method must have the standard
   * request/response parameter arguments.
   *
   * @param methodName name of delegate method
   */
  protected Method getDelegateMethod(String methodName) throws DelegateNotFoundException {
    try {
      return getClass().getMethod(methodName, SERVLET_ARGS);
    } catch (NullPointerException ex) {
      throw new DelegateNotFoundException("Couldn't locate method: " + methodName, ex);
    } catch (NoSuchMethodException ex) {
      throw new DelegateNotFoundException("Couldn't locate method: " + methodName, ex);
    }
  }

  /**
   * Method called when a delegate method cannot be found.
   * @param req servlet request
   * @param res servlet response
   * @throws ServletException
   * @throws IOException
   */
  abstract protected void handleDelegateNotFound(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException;

  /**
   * Gets the name of the delegate dispatch method from the servlet request, or null.
   */
  protected final String parseDelegateMethodName(HttpServletRequest req) {
    if (req.getPathInfo() == null)
      return null;
    Matcher m = Pattern.compile("^[^/]*/([^/]+)").matcher(req.getPathInfo());
    return m.matches() ? m.group(1) : null;
  }

}
/**
 * Exception class thrown when the the servlet cannot find a delegate method.
 */
class DelegateNotFoundException extends ServletException {
  DelegateNotFoundException(String s, Throwable t) {
    super(s, t);
  }

  DelegateNotFoundException(String s) {
    super(s);
  }
}
