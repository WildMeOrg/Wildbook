package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.ecocean.CommonConfiguration;
import org.ecocean.Occurrence;
import org.ecocean.Shepherd;

/**
 * Updates latitude, longitude for an occurrence.
 */
public class OccurrenceSetLatitudeLongitude extends OccurrenceSetGroupBehavior {
    private static final long serialVersionUID = 1L;

    @Override public void doPost(final HttpServletRequest request,
        final HttpServletResponse response)
    throws ServletException, IOException {
        final String context = ServletUtilities.getContext(request);
        final String latitude = request.getParameter("latitude");
        final String longitude = request.getParameter("longitude");

        if (StringUtils.isAnyBlank(latitude, longitude)) {
            final String message =
                "<strong>Error:</strong> I don't have enough information to complete your request.";
            printResponse(request, response, HttpServletResponse.SC_BAD_REQUEST, message, context);
            return;
        }
        final Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("OccurrenceSetLatitudeLongitude.class");
        myShepherd.beginDBTransaction();

        final Occurrence occ = myShepherd.getOccurrence(request.getParameter("number"));
        setDateLastModified(occ);

        final String latLongStringNew = latitude + ", " + longitude;
        String latLongString = StringUtils.EMPTY;

        try {
            latLongString = occ.getLatLonString();
            occ.setLatLongString(latitude, longitude, request.getParameter("bearing"),
                request.getParameter("distance"));
            occ.addComments("<p><em>" + request.getRemoteUser() + " on " +
                (new java.util.Date()).toString() +
                "</em><br>Changed latitude, longitude from:<br><i>" + latLongString +
                "</i><br>to:<br><i>" + latLongStringNew + "</i></p>");
        } catch (Exception e) {
            e.printStackTrace();
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            final String message =
                "<strong>Failure:</strong> Occurrence latitude, longitude was NOT updated because of an error: "
                + e.getLocalizedMessage();
            printResponse(request, response, HttpServletResponse.SC_BAD_REQUEST, message, context);
            return;
        }
        myShepherd.commitDBTransaction();
        final String message =
            "<strong>Success:</strong> Occurrence latitude, longitude was updated from:<br><i>" +
            latLongString + "</i><br>to:<br><i>" + latLongStringNew + "</i>";
        printResponse(request, response, HttpServletResponse.SC_OK, message, context);

        myShepherd.closeDBTransaction();
    }

    /**
     * Prints the servlet response using HttpServletResponse object.
     *
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @param status HttpStatus
     * @param message Print message
     * @param context Context
     * @throws IOException
     */
    protected void printResponse(final HttpServletRequest request,
        final HttpServletResponse response, final int status, final String message,
        final String context)
    throws IOException {
        response.setContentType("text/html");
        final PrintWriter out = response.getWriter();
        response.setStatus(status);
        out.println(ServletUtilities.getHeader(request));
        out.println(message);
        out.println("<p><a href=\"" + request.getScheme() + "://" +
            CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
            request.getParameter("number") + "\">Return to occurrence " +
            request.getParameter("number") + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
        out.close();
    }
}
