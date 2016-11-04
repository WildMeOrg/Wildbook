package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.lang.*;

import java.util.HashMap;
import java.util.Map;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;

public class StripePayment extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

    Map<String, Object> chargeParams = new HashMap<String, Object>();

    Stripe.apiKey = "sk_test_sHm3KrvEv0dERpO0Qgg5lkDE";
    String token = request.getParameter("stripeToken");
    String amount = request.getParameter("amount");
    String name = request.getParameter("nameOnCard");
    String email = request.getParameter("email");
    String planName = request.getParameter("planName");

    HttpSession session = request.getSession();
    String queryShark = session.getAttribute("queryShark");

    HttpSession session = request.getSession();                              
    Boolean paidStatus = false;
    Boolean hasNickname = false;

    String chargeId = "";
    String customerId = "";

    // int amount = Integer.valueOf(request.getParameter("amount"));
    if (planName.equals("none")) {
      try {
        Map<String, Object> cardMap = new HashMap<String, Object>();
        cardMap.put("source", token);
        cardMap.put("amount", amount);
        cardMap.put("currency", "usd");
        cardMap.put("description", "Whaleshark.org one time donation.");

        Map<String, String> initialMetadata = new HashMap<String, String>();
        // initialMetadata.put("order_id", "6735");
        initialMetadata.put("name", name);
        initialMetadata.put("email", email);

        cardMap.put("metadata", initialMetadata);

        Charge charge = Charge.create(cardMap);

        request.setAttribute("chargeId", charge.getId());

        if (charge.getPaid().equals(true)) {
          request.setAttribute("paidStatus", true);
          session.setAttribute("paid", true);
        }

        System.out.println(charge);

      } catch (StripeException e) {
        System.out.println("Generic error from stripe on donation. ");
        System.out.println("Token: " + token );
      } catch (Exception e) {
        System.out.println("Something went wrong outside of stripe.");
        System.out.println("Token: " + request.getParameter("stripeToken"));
      }
    } else {
      try {
        Map<String, Object> subscriberParams = new HashMap<String, Object>();
        subscriberParams.put("source", token);
        subscriberParams.put("plan", planName);
        subscriberParams.put("email", email);

        Customer customer = Customer.create(subscriberParams);
        if ( customer.getSubscriptions().getTotalCount() > 0 ) {
          request.setAttribute("paidStatus", true);
          session.setAttribute("paid", true);
        }
        request.setAttribute("customerId", customer.getId());
      } catch (StripeException e) {
        System.out.println("Generic error from stripe on subscribe. ");
        System.out.println("Token: " + token );
      }
    }

    try {
      System.out.println("Redirect success!");
      System.out.println("Show me da sharky:" + queryShark);
      getServletContext().getRequestDispatcher("/createadoption.jsp" + queryShark).forward(request, response);
    } catch (IOException ie) {
      System.out.println("Donation failed on redirect... IO exception.");
    } catch (ServletException e) {
      System.out.println("Servlet Exeption... No redirect.");
    }
  }
}
