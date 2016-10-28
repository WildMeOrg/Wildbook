package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.*;

import java.lang.*;

import java.util.HashMap;
import java.util.Map;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;

public class StripePayment extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

    Stripe.apiKey = "sk_test_sHm3KrvEv0dERpO0Qgg5lkDE";
    String token = request.getParameter("stripeToken");

    // int amount = Integer.valueOf(request.getParameter("amount"));

    Map<String, Object> cardMap = new HashMap<String, Object>();
    cardMap.put("source", token);
    cardMap.put("amount", 1000);
    cardMap.put("currency", "usd");
    cardMap.put("description", "Test Charge");
    try {
      Charge charge = Charge.create(cardMap);
      System.out.println(charge);
    } catch (StripeException e) {
      System.out.println("Generic error from stripe. ");
      System.out.println("Message is: " + e.getMessage());
    } catch (Exception e) {
      System.out.println("Something went wrong outside of stripe.");
      System.out.println("Message is: " + e.getMessage());
    }
    try {
      System.out.println("Success!");
      response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/createadoption.jsp");
    } catch (IOException ie) {
        System.out.println("Failed on redirect.");
    }
  }
}
