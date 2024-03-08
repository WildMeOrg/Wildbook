package org.ecocean.api;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;



public class UserInfo extends ApiBase {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        JSONObject info = new JSONObject();

        //results = user.infoJSONObject(true);
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(info.toString());
    }  	

}
