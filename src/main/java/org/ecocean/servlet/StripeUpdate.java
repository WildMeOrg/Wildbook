package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.lang.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import java.util.concurrent.ThreadPoolExecutor;

public class StripeUpdate extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

    Map<String, Object> chargeParams = new HashMap<String, Object>();

    PrintWriter out = response.getWriter();

    String token = request.getParameter("stripeToken");
    String name = request.getParameter("nameOnCard");

    HttpSession session = request.getSession();
    String stripeID = (String)session.getAttribute("stripeID");

    String context = "context0";
    context = ServletUtilities.getContext(request);
    Properties stripeProps = ShepherdProperties.getProperties("stripeKeys.properties", "", context);
    if (stripeProps == null) {
         System.out.println("There are no available API keys for Stripe!");
    }
    String secretKey = stripeProps.getProperty("secretKey");
    Stripe.apiKey = secretKey;

    try {
      Customer cu = Customer.retrieve(stripeID);
      Map<String, Object> updateParams = new HashMap<String, Object>();
      updateParams.put("source", token);
      cu.update(updateParams);
    } catch (Exception e){
      System.out.println("Exception retrieving customer " + name + " with stripe ID " + stripeID + " for payment update.");
      e.printStackTrace();
    }

    try {
      System.out.println("Adopter PAYMENT UPDATE redirect success!");
      response.sendRedirect(request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/adoptions/editSuccess.jsp");
    } catch (IOException ie) {
      System.out.println("Payment update failed on redirect... IO exception.");
    } catch (Exception e) {
      System.out.println("General Exeption in adopter payment update.");
    }
  }
}
