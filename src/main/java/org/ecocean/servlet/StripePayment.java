package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.*;

import java.util.HashMap;
import java.util.Map;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.net.RequestOptions;
import com.stripe.net.RequestOptions.RequestOptionsBuilder;

public class StripePayment extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

    String token = request.getParameter("stripeToken");
    String amount = request.getParameter("amount");
    String number = request.getParameter("number");
    String cvc = request.getParameter("cvc");
    String exp_month = request.getParameter("exp_month");
    String exp_year = request.getParameter("exp_year");

    RequestOptions requestOptions = (new RequestOptionsBuilder()).setApiKey("sk_test_sHm3KrvEv0dERpO0Qgg5lkDE").build(); // eventually hide
    Map<String, Object> chargeMap = new HashMap<String, Object>();
    chargeMap.put("amount", amount);
    chargeMap.put("currency", "usd");
    Map<String, Object> cardMap = new HashMap<String, Object>();
    cardMap.put("number", number);
    cardMap.put("exp_month", exp_month);
    cardMap.put("exp_year", exp_year);
    chargeMap.put("card", cardMap);
    try {
        Charge charge = Charge.create(chargeMap, requestOptions);
        System.out.println(charge);
    } catch (StripeException e) {
        e.printStackTrace();
    }
    try {
      response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/createadoption.jsp");
    } catch (IOException ie) {
        ie.printStackTrace();
    }
  }
}
