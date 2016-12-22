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
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import java.util.concurrent.ThreadPoolExecutor;

public class StripePayment extends HttpServlet {

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
    String amount = request.getParameter("amount");
    String name = request.getParameter("nameOnCard");
    String email = request.getParameter("email");
    String planName = request.getParameter("planName");

    String pennyAmount = "";

    if ((amount != null) && (amount != "")) {
      pennyAmount = "" + ( Integer.parseInt(amount) * 100);
    }

    HttpSession session = request.getSession();
    String queryShark = (String)session.getAttribute("queryShark");

    Boolean paidStatus = false;

    String chargeId = "";
    String customerId = "";

    String context = "context0";
    context = ServletUtilities.getContext(request);
    Properties stripeProps = ShepherdProperties.getProperties("stripeKeys.properties", "", context);
    if (stripeProps == null) {
         System.out.println("There are no available API keys for Stripe!");
    }
    String secretKey = stripeProps.getProperty("secretKey");
    Stripe.apiKey = secretKey;

    if (planName.equals("none")) {
      try {
        Map<String, Object> cardMap = new HashMap<String, Object>();
        cardMap.put("source", token);
        cardMap.put("amount", pennyAmount);
        cardMap.put("currency", "usd");
        cardMap.put("description", "Whaleshark.org one time donation.");

        Map<String, String> initialMetadata = new HashMap<String, String>();
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
        // Throws if user does not select a plan.
        out.println("No plan was selected, or form was missing other data. Card not charged, please try again.");
        System.out.println("Generic error from stripe on donation. ");
        System.out.println("Token: " + token );
      } catch (Exception e) {
        System.out.println("Something went wrong outside of stripe.");
        System.out.println("Token: " + request.getParameter("stripeToken"));
      }
    } else {
      try {
        Map<String, Object> subscriberParams = new HashMap<>();
        subscriberParams.put("source", token);
        subscriberParams.put("plan", planName);
        subscriberParams.put("email", email);

        Customer customer = Customer.create(subscriberParams);
        if ( customer.getSubscriptions().getTotalCount() > 0 ) {
          request.setAttribute("paidStatus", true);
          session.setAttribute("paid", true);
          session.setAttribute("stripeID", customer.getId() );
          System.out.println("Stripe ID: " + (String)session.getAttribute("StripeID") );

        }
        request.setAttribute("customerId", customer.getId());
      } catch (StripeException e) {
        System.out.println("Generic error from stripe on subscribe. ");
        System.out.println("Token: " + token );
      }
    }

    String newQuery = "";
    try {
      if ((!queryShark.equals(null))&&(!queryShark.equals(""))) {
        newQuery = "?number=" + queryShark;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (planName.equals("none")) {
      try {
        String emailContext = "context0";
        String langCode = "en";
        String to = email;
        String type = "oneTimeDonation";
        String message = "Thank you for you donation to Wild Me of $" + amount + " dollars. Wild Me is a 5013c nonprifit , and donations are tax deductable in the United States.";
        System.out.println("About to email one time donor...");
        // Retrieve background service for processing emails
        ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
        NotificationMailer mailer = new NotificationMailer(emailContext, langCode, to, type, message);
        es.execute(mailer);
      }
      catch (Exception e) {
        System.out.println("Error in sending email confirmation of adoption.");
        e.printStackTrace();
      }
      try {
        System.out.println("ONE TIME DONATION redirect success!");
        response.sendRedirect(request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/donationThanks.jsp");
      } catch (IOException ie) {
        System.out.println("Donation failed on redirect... IO exception.");
      } catch (Exception e) {
        System.out.println("General Exeption... No redirect.");
      }
    } else {
      try {
        System.out.println("SUBSCRIPTION redirect success!");
        getServletContext().getRequestDispatcher("/createadoption.jsp" + newQuery).forward(request, response);
      } catch (IOException ie) {
        System.out.println("Donation failed on redirect... IO exception.");
      } catch (Exception e) {
        System.out.println("Servlet Exeption... No redirect.");
      }
    }
  }
}
