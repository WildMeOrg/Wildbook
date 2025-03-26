package org.ecocean;

import java.lang.StringBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;

import org.ecocean.servlet.ServletUtilities;
import org.ecocean.social.SocialUnit;
import org.ecocean.Util.MeasurementDesc;

import java.util.Iterator;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class IndividualQueryProcessor extends QueryProcessor {
    private static final String SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE =
        "SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && ";
    private static final String VARIABLES_STATEMENT = " VARIABLES org.ecocean.Encounter enc";

    public static String queryStringBuilder(HttpServletRequest request, StringBuffer prettyPrint,
        Map<String, Object> paramMap) {
        String parameterDeclaration = "";
        String context = "context0";

        context = ServletUtilities.getContext(request);

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IndividualQueryProcessor.class");

        int DOBday1 = 1, DOBday2 = 31, DOBmonth1 = 1, DOBmonth2 = 12, DOByear1 = 0, DOByear2 = 3000;
        try {
            DOBmonth1 = (new Integer(request.getParameter("DOBmonth1"))).intValue();
        } catch (Exception nfe) {}
        try {
            DOBmonth2 = (new Integer(request.getParameter("DOBmonth2"))).intValue();
        } catch (Exception nfe) {}
        try {
            DOByear1 = (new Integer(request.getParameter("DOByear1"))).intValue();
        } catch (Exception nfe) {}
        try {
            DOByear2 = (new Integer(request.getParameter("DOByear2"))).intValue();
        } catch (Exception nfe) {}
        try {
            DOBday1 = (new Integer(request.getParameter("DOBday1"))).intValue();
        } catch (Exception nfe) {}
        try {
            DOBday2 = (new Integer(request.getParameter("DOBday2"))).intValue();
        } catch (Exception nfe) {}
        int DODday1 = 1, DODday2 = 31, DODmonth1 = 1, DODmonth2 = 12, DODyear1 = 0, DODyear2 = 3000;
        try {
            DODmonth1 = (new Integer(request.getParameter("DODmonth1"))).intValue();
        } catch (Exception nfe) {}
        try {
            DODmonth2 = (new Integer(request.getParameter("DODmonth2"))).intValue();
        } catch (Exception nfe) {}
        try {
            DODyear1 = (new Integer(request.getParameter("DODyear1"))).intValue();
        } catch (Exception nfe) {}
        try {
            DODyear2 = (new Integer(request.getParameter("DODyear2"))).intValue();
        } catch (Exception nfe) {}
        try {
            DODday1 = (new Integer(request.getParameter("DODday1"))).intValue();
        } catch (Exception nfe) {}
        try {
            DODday2 = (new Integer(request.getParameter("DODday2"))).intValue();
        } catch (Exception nfe) {}
        String filter = SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE;
        String jdoqlVariableDeclaration = VARIABLES_STATEMENT;
        // filter for location------------------------------------------
        if ((request.getParameter("locationField") != null) &&
            (!request.getParameter("locationField").equals(""))) {
            String locString = request.getParameter("locationField").toLowerCase().replaceAll("%20",
                " ").trim();

            filter = filterWithCondition(filter,
                "(enc.verbatimLocality.toLowerCase().indexOf('" + locString + "') != -1)");

            prettyPrint.append("Location contains \"" + locString + "\".<br />");
        }
        // end location filter--------------------------------------------------------------------------------------
        // filter for submitter organization ids------------------------------------------
        if ((request.getParameter("organizationId") != null) &&
            (!request.getParameter("organizationId").equals("")) &&
            Util.isUUID(request.getParameter("organizationId"))) {
            String orgId = request.getParameter("organizationId");
            filter =
                "SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && user.username == enc.submitterID && org.members.contains(user) && org.id == '"
                + orgId + "'";
            String variables_statement =
                " VARIABLES org.ecocean.Encounter enc; org.ecocean.User user; org.ecocean.Organization org";
            jdoqlVariableDeclaration = addVars(variables_statement, filter);
            prettyPrint.append("Submitter organization is \"" + orgId + "\".<br />");
        }
        // end submitter organization ids filter--------------------------------------------------------------------------------------
        // filter for projectName-------------------
        if (Util.isUUID(request.getParameter("projectId"))) {
            System.out.println("dammit you better get here");
            filter =
                "SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && proj.encounters.contains(enc) && ";
            String[] projectIds = request.getParameterValues("projectId");
            if ((projectIds != null) && (!projectIds[0].equals("None"))) {
                prettyPrint.append("Assigned to one of the following projects: ");
                int numProjIds = projectIds.length;
                String projIdFilter = "(";
                for (int i = 0; i < numProjIds; i++) {
                    String currentProjId = projectIds[i].toLowerCase().replaceAll("%20",
                        " ").trim();
                    if (!currentProjId.equals("")) {
                        if (projIdFilter.equals("(")) {
                            projIdFilter += " proj.id == \"" + currentProjId + "\"";
                        } else {
                            projIdFilter += " || proj.id == \"" + currentProjId + "\"";
                        }
                        // prettyPrint.append(filter + " " + projIdFilter);
                    }
                }
                projIdFilter += " )";
                if (filter.equals(
                    "SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && proj.encounters.contains(enc) && "))
                        {
                    filter += projIdFilter;
                } else {
                    filter += (" && " + projIdFilter);
                }
                prettyPrint.append(filter);
                prettyPrint.append("<br/>");
            }
            String variables_statement =
                " VARIABLES org.ecocean.Encounter enc; org.ecocean.Project proj";
            jdoqlVariableDeclaration = addOrgVars(variables_statement, filter);
        } else {
        }
        // end filter for projectName------------------
        // locationID filters-------------------------------------------------
        String[] locCodes = request.getParameterValues("locationCodeField");
        if (locCodes == null && ServletUtilities.getParameterOrAttribute("locationCodeField",
            request) != null) {
            // allow for singleton passing from other servlets
            locCodes = new String[] {
                ServletUtilities.getParameterOrAttribute("locationCodeField", request)
            };
        }
        if ((locCodes != null) && (!locCodes[0].equals(""))) {
            prettyPrint.append("Sighted in at least one of the following locationsIDs: ");
            int kwLength = locCodes.length;
            String locIDFilter = "(";
            for (int kwIter = 0; kwIter < kwLength; kwIter++) {
                String kwParam = locCodes[kwIter].replaceAll("%20", " ").trim();
                if (!kwParam.equals("")) {
                    if (locIDFilter.equals("(")) {
                        locIDFilter += " enc.locationID == \"" + kwParam + "\"";
                    } else {
                        locIDFilter += " || enc.locationID == \"" + kwParam + "\"";
                    }
                    prettyPrint.append(kwParam + " ");
                }
            }
            locIDFilter += " )";
            filter = filterWithCondition(filter, locIDFilter);
            prettyPrint.append("<br />");
        }
        // end locationID filters-----------------------------------------------
        // individualID filters-------------------------------------------------
        // supports multiple individualID parameters as well as comma-separated lists of individualIDs within them
        String individualID = request.getParameter("individualID");
        if ((individualID != null) && (!individualID.equals("None")) &&
            (!individualID.trim().equals(""))) {
            prettyPrint.append("Individual ID contains the following: ");
            individualID = individualID.toLowerCase();

            String locIDFilter = " (individualID == \"" + individualID +
                "\" || names.valuesAsString.toLowerCase().indexOf(\"" + individualID + "\") != -1)";
            if (filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                filter += locIDFilter;
            } else { filter += (" && " + locIDFilter); }
            prettyPrint.append("<br />");
        }
        // end individualID filters-----------------------------------------------
        // patterningCode filters-------------------------------------------------
        String[] patterningCodes = request.getParameterValues("patterningCodeField");
        if ((patterningCodes != null) && (!patterningCodes[0].equals("None"))) {
            prettyPrint.append("Patterning code is one of the following: ");
            int kwLength = patterningCodes.length;
            String patterningCodeFilter = "(";
            for (int kwIter = 0; kwIter < kwLength; kwIter++) {
                String kwParam = patterningCodes[kwIter].replaceAll("%20", " ").trim();
                if (!kwParam.equals("")) {
                    if (patterningCodeFilter.equals("(")) {
                        patterningCodeFilter += " enc97.patterningCode == \"" + kwParam + "\"";
                    } else {
                        patterningCodeFilter += " || enc97.patterningCode == \"" + kwParam + "\"";
                    }
                    prettyPrint.append(kwParam + " ");
                }
            }
            patterningCodeFilter += " )";

            filter = filterWithCondition(filter,
                ("encounters.contains(enc97) &&" + patterningCodeFilter));
            if (!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc97")) {
                jdoqlVariableDeclaration += ";org.ecocean.Encounter enc97";
            }
            prettyPrint.append("<br />");
        }
        // end patterningCode filters-----------------------------------------------
        // haplotype filters-------------------------------------------------
        String[] haplos = request.getParameterValues("haplotypeField");
        if ((haplos != null) && (!haplos[0].equals("None"))) {
            prettyPrint.append("Haplotype is one of the following: ");
            int kwLength = haplos.length;
            String locIDFilter = "(";
            for (int kwIter = 0; kwIter < kwLength; kwIter++) {
                String kwParam = haplos[kwIter].replaceAll("%20", " ").trim();
                if (!kwParam.equals("")) {
                    if (locIDFilter.equals("(")) {
                        locIDFilter += " localHaplotypeReflection == \"" + kwParam + "\"";
                    } else {
                        locIDFilter += " || localHaplotypeReflection == \"" + kwParam + "\"";
                    }
                    prettyPrint.append(kwParam + " ");
                }
            }
            locIDFilter += " )";
            filter = filterWithCondition(filter, locIDFilter);
            prettyPrint.append("<br />");
        }
        // end haplotype filters-----------------------------------------------
        // annotation viewpoint and class filters-------------------------------------------------
        String[] hasViewpoint = request.getParameterValues("hasViewpoint");
        String[] hasIAClass = request.getParameterValues("hasIAClass");
        if (((hasViewpoint != null) && (!hasViewpoint[0].equals(""))) ||
            ((hasIAClass != null) && (!hasIAClass[0].equals("")))) {
            if ((hasViewpoint != null) && (!hasViewpoint[0].equals(""))) {
                prettyPrint.append("Viewpoint is one of the following: ");
                int kwLength = hasViewpoint.length;
                String locIDFilter = "(";
                for (int kwIter = 0; kwIter < kwLength; kwIter++) {
                    String kwParam = hasViewpoint[kwIter].trim();
                    if (!kwParam.equals("")) {
                        if (locIDFilter.equals("(")) {
                            locIDFilter += " annot46.viewpoint == \"" + kwParam + "\"";
                        } else {
                            locIDFilter += " || annot46.viewpoint == \"" + kwParam + "\"";
                        }
                        prettyPrint.append(kwParam + " ");
                    }
                }
                locIDFilter += " )";
                if (filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                    filter += locIDFilter;
                } else { filter += (" && " + locIDFilter); }
                prettyPrint.append("<br />");
            }
            if ((hasIAClass != null) && (!hasIAClass[0].equals(""))) {
                prettyPrint.append("IA class is one of the following: ");
                int kwLength = hasIAClass.length;
                String locIDFilter = "(";
                for (int kwIter = 0; kwIter < kwLength; kwIter++) {
                    String kwParam = hasIAClass[kwIter].trim();
                    if (!kwParam.equals("")) {
                        if (locIDFilter.equals("(")) {
                            locIDFilter += " annot46.iaClass == \"" + kwParam + "\"";
                        } else {
                            locIDFilter += " || annot46.iaClass == \"" + kwParam + "\"";
                        }
                        prettyPrint.append(kwParam + " ");
                    }
                }
                locIDFilter += " )";
                if (filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                    filter += locIDFilter;
                } else { filter += (" && " + locIDFilter); }
                prettyPrint.append("<br />");
            }
            // set the jdoql variable used by both
            filter += (" && encounters.contains(enc452) && enc452.annotations.contains(annot46)");
            if (jdoqlVariableDeclaration.length() > 0) {
                jdoqlVariableDeclaration +=
                    ";org.ecocean.Annotation annot46;org.ecocean.Encounter enc452;";
            } else {
                jdoqlVariableDeclaration =
                    " VARIABLES org.ecocean.Annotation annot46;org.ecocean.Encounter enc452;";
            }
        }
        // end viewpoint and class filters-----------------------------------------------
        // username filters-------------------------------------------------
        String[] usernames = request.getParameterValues("username");
        if ((usernames != null) && (!usernames[0].equals("None"))) {
            prettyPrint.append("Username is one of the following: ");
            int kwLength = usernames.length;
            String patterningCodeFilter = "(";
            for (int kwIter = 0; kwIter < kwLength; kwIter++) {
                String kwParam = usernames[kwIter].replaceAll("%20", " ").trim();
                if (!kwParam.equals("")) {
                    if (patterningCodeFilter.equals("(")) {
                        patterningCodeFilter += " enc1515.submitterID == \"" + kwParam + "\"";
                    } else {
                        patterningCodeFilter += " || enc1515.submitterID == \"" + kwParam + "\"";
                    }
                    prettyPrint.append(kwParam + " ");
                }
            }
            patterningCodeFilter += " )";

            filter = filterWithCondition(filter,
                ("encounters.contains(enc1515) &&" + patterningCodeFilter));
            if (!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc1515")) {
                jdoqlVariableDeclaration += ";org.ecocean.Encounter enc1515";
            }
            prettyPrint.append("<br />");
        }
        // end username filters-----------------------------------------------
        // behavior filters-------------------------------------------------
        String[] behaviors = request.getParameterValues("behaviorField");
        if ((behaviors != null) && (!behaviors[0].equals("None"))) {
            prettyPrint.append("behaviorField is one of the following: ");
            int kwLength = behaviors.length;
            String locIDFilter = "(";
            for (int kwIter = 0; kwIter < kwLength; kwIter++) {
                String kwParam = behaviors[kwIter].replaceAll("%20", " ").trim();
                if (!kwParam.equals("")) {
                    if (locIDFilter.equals("(")) {
                        locIDFilter += " enc.behavior == \"" + kwParam + "\"";
                    } else {
                        locIDFilter += " || enc.behavior == \"" + kwParam + "\"";
                    }
                    prettyPrint.append(kwParam + " ");
                }
            }
            locIDFilter += " )";
            filter = filterWithCondition(filter, locIDFilter);
            prettyPrint.append("<br />");
        }
        // end behavior filters-----------------------------------------------
        // lifeStage filters-------------------------------------------------
        String[] stages = request.getParameterValues("lifeStageField");
        if ((stages != null) && (!stages[0].equals("None")) && (!stages[0].equals(""))) {
            prettyPrint.append("Life stage is one of the following: ");
            int kwLength = stages.length;
            String stageFilter = "(";
            for (int kwIter = 0; kwIter < kwLength; kwIter++) {
                String kwParam = stages[kwIter].replaceAll("%20", " ").trim();
                if (!kwParam.equals("")) {
                    if (stageFilter.equals("(")) {
                        stageFilter += " enc.lifeStage == \"" + kwParam + "\"";
                    } else {
                        stageFilter += " || enc.lifeStage == \"" + kwParam + "\"";
                    }
                    prettyPrint.append(kwParam + " ");
                }
            }
            stageFilter += " ) ";
            filter = filterWithCondition(filter, stageFilter);
            prettyPrint.append("<br />");
        }
        // Measurement filters-----------------------------------------------
        List<MeasurementDesc> measurementDescs = Util.findMeasurementDescs("en", context);
        String measurementPrefix = "measurement";
        StringBuilder measurementFilter = new StringBuilder(); // "( collectedData.contains(measurement) && (");
        boolean atLeastOneMeasurement = false;
        int measurementsInQuery = 0;
        for (MeasurementDesc measurementDesc : measurementDescs) {
            String valueParamName = measurementPrefix + measurementDesc.getType() + "(value)";
            String value = request.getParameter(valueParamName);
            if (value != null) {
                value = value.trim();
                if (value.length() > 0) {
                    String operatorParamName = measurementPrefix + measurementDesc.getType() +
                        "(operator)";
                    String operatorParamValue = request.getParameter(operatorParamName);
                    if (operatorParamValue == null) {
                        operatorParamValue = "";
                    }
                    String operator = null;
                    if ("gt".equals(operatorParamValue)) {
                        operator = ">";
                    } else if ("lt".equals(operatorParamValue)) {
                        operator = "<";
                    } else if ("eq".equals(operatorParamValue)) {
                        operator = "==";
                    } else if ("gteq".equals(operatorParamValue)) {
                        operator = ">=";
                    } else if ("lteq".equals(operatorParamValue)) {
                        operator = "<=";
                    }
                    if (operator != null) {
                        prettyPrint.append(measurementDesc.getUnitsLabel());
                        prettyPrint.append(" is ");
                        prettyPrint.append(operator);
                        prettyPrint.append(value);
                        prettyPrint.append("<br/>");
                        if (atLeastOneMeasurement) {
                            measurementFilter.append("&&");
                        }
                        String measurementVar = "measurement" + measurementsInQuery++;
                        if (filter.indexOf("encounters.contains(enc)") == -1) {
                            measurementFilter.append("(encounters.contains(enc)) && ");
                        }
                        measurementFilter.append("(enc.measurements.contains(" + measurementVar +
                            ") && ");
                        measurementFilter.append(measurementVar + ".value " + operator + " " +
                            value);
                        measurementFilter.append(" && " + measurementVar + ".type == ");
                        measurementFilter.append("\"" + measurementDesc.getType() + "\")");
                        atLeastOneMeasurement = true;
                    }
                }
            }
        }
        if (atLeastOneMeasurement) {
            if (jdoqlVariableDeclaration.length() > 0) {
                jdoqlVariableDeclaration += ";";
            }
            for (int i = 0; i < measurementsInQuery; i++) {
                if (i > 0) {
                    jdoqlVariableDeclaration += "; ";
                }
                jdoqlVariableDeclaration += " org.ecocean.Measurement measurement" + i;
            }
            filter = filterWithCondition(filter, measurementFilter.toString());
        }
        // end measurement filters
        // BiologicalMeasurement filters-----------------------------------------------
        List<MeasurementDesc> bioMeasurementDescs = Util.findBiologicalMeasurementDescs("en",
            context);
        String bioMeasurementPrefix = "biomeasurement";
        StringBuilder bioMeasurementFilter = new StringBuilder();
        bioMeasurementFilter.append(
            "encounters.contains(enc) && enc.tissueSamples.contains(dce322) ");
        boolean bioAtLeastOneMeasurement = false;
        int bioMeasurementsInQuery = 0;
        for (MeasurementDesc measurementDesc : bioMeasurementDescs) {
            String valueParamName = bioMeasurementPrefix + measurementDesc.getType() + "(value)";
            String value = request.getParameter(valueParamName);
            if (value != null) {
                value = value.trim();
                if (value.length() > 0) {
                    String operatorParamName = bioMeasurementPrefix + measurementDesc.getType() +
                        "(operator)";
                    String operatorParamValue = request.getParameter(operatorParamName);
                    if (operatorParamValue == null) {
                        operatorParamValue = "";
                    }
                    String operator = null;
                    if ("gt".equals(operatorParamValue)) {
                        operator = ">";
                    } else if ("lt".equals(operatorParamValue)) {
                        operator = "<";
                    } else if ("eq".equals(operatorParamValue)) {
                        operator = "==";
                    } else if ("gteq".equals(operatorParamValue)) {
                        operator = ">=";
                    } else if ("lteq".equals(operatorParamValue)) {
                        operator = "<=";
                    }
                    if (operator != null) {
                        prettyPrint.append("Biological/chemical measurement " +
                            measurementDesc.getType());
                        prettyPrint.append(" is ");
                        prettyPrint.append(operator);
                        prettyPrint.append(value);
                        prettyPrint.append("<br/>");
                        if (bioAtLeastOneMeasurement) {
                        }
                        String measurementVar = "biomeasurement" + bioMeasurementsInQuery++;
                        bioMeasurementFilter.append(" && dce322.analyses.contains(" +
                            measurementVar + ")");
                        bioMeasurementFilter.append(" && ( " + measurementVar + ".value " +
                            operator + " " + value + " )");
                        bioMeasurementFilter.append(" && ( " + measurementVar +
                            ".measurementType == ");
                        bioMeasurementFilter.append("\"" + measurementDesc.getType() + "\" )");
                        bioAtLeastOneMeasurement = true;
                    }
                }
            }
        }
        if (bioAtLeastOneMeasurement) {
            if (jdoqlVariableDeclaration.length() > 0) {
                jdoqlVariableDeclaration += ";org.ecocean.genetics.TissueSample dce322;";
            } else {
                jdoqlVariableDeclaration = " VARIABLES org.ecocean.genetics.TissueSample dce322;";
            }
            for (int i = 0; i < bioMeasurementsInQuery; i++) {
                if (i > 0) {
                    jdoqlVariableDeclaration += "; ";
                }
                jdoqlVariableDeclaration +=
                    "org.ecocean.genetics.BiologicalMeasurement biomeasurement" + i;
            }
            filter = filterWithCondition(filter, bioMeasurementFilter.toString());
        }
        // end BiologicalMeasurement filters
        // verbatimEventDate filters-------------------------------------------------
        String[] verbatimEventDates = request.getParameterValues("verbatimEventDateField");
        if ((verbatimEventDates != null) && (!verbatimEventDates[0].equals("None"))) {
            prettyPrint.append("verbatimEventDateField is one of the following: ");
            int kwLength = verbatimEventDates.length;
            String locIDFilter = "(";
            for (int kwIter = 0; kwIter < kwLength; kwIter++) {
                String kwParam = verbatimEventDates[kwIter].replaceAll("%20", " ").trim();
                if (!kwParam.equals("")) {
                    if (locIDFilter.equals("(")) {
                        locIDFilter += " enc.verbatimEventDate == \"" + kwParam + "\"";
                    } else {
                        locIDFilter += " || enc.verbatimEventDate == \"" + kwParam + "\"";
                    }
                    prettyPrint.append(kwParam + " ");
                }
            }
            locIDFilter += " )";
            filter = filterWithCondition(filter, locIDFilter);
            prettyPrint.append("<br />");
        }
        // end verbatimEventDate filters-----------------------------------------------
        // start date added filter----------------------------
        if ((request.getParameter("dateaddedpicker1") != null) &&
            (!request.getParameter("dateaddedpicker1").trim().equals("")) &&
            (request.getParameter("dateaddedpicker2") != null) &&
            (!request.getParameter("dateaddedpicker2").trim().equals(""))) {
            try {
                DateTimeFormatter parser = ISODateTimeFormat.dateTimeParser();
                DateTime date1 = parser.parseDateTime(request.getParameter("dateaddedpicker1"));
                DateTime date2 = parser.parseDateTime(request.getParameter("dateaddedpicker2"));

                prettyPrint.append("Encounter creation dates between: " +
                    date1.toString(ISODateTimeFormat.date()) + " and " +
                    date2.toString(ISODateTimeFormat.date()) + "<br />");

                filter = filterWithCondition(filter,
                    "((enc.dwcDateAddedLong >= " + date1.getMillis() +
                    ") && (enc.dwcDateAddedLong <= " + date2.getMillis() + "))");
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
        }
        // end date added filter------------------------------------------

        String releaseDateFromStr = request.getParameter("releaseDateFrom");
        String releaseDateToStr = request.getParameter("releaseDateTo");
        String pattern = CommonConfiguration.getProperty("releaseDateFormat", context);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        if (releaseDateFromStr != null && releaseDateFromStr.trim().length() > 0) {
            try {
                Date releaseDateFrom = simpleDateFormat.parse(releaseDateFromStr);
                if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                    filter += " && ";
                }
                filter += "(enc13.releaseDate >= releaseDateFrom)";
                filter += " && encounters.contains(enc13) ";
                parameterDeclaration = updateParametersDeclaration(parameterDeclaration,
                    "java.util.Date releaseDateFrom");
                jdoqlVariableDeclaration = QueryProcessor.updateJdoqlVariableDeclaration(
                    jdoqlVariableDeclaration, "org.ecocean.Encounter enc13");
                paramMap.put("releaseDateFrom", releaseDateFrom);
                prettyPrint.append("release date >= " + simpleDateFormat.format(releaseDateFrom));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (releaseDateToStr != null && releaseDateToStr.trim().length() > 0) {
            try {
                Date releaseDateTo = simpleDateFormat.parse(releaseDateToStr);
                if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                    filter += " && ";
                }
                filter += "(enc13.releaseDate <= releaseDateTo)";
                if (!filter.contains("enc13")) {
                    filter += " && encounters.contains(enc13) ";
                }
                parameterDeclaration = updateParametersDeclaration(parameterDeclaration,
                    "java.util.Date releaseDateTo");
                jdoqlVariableDeclaration = QueryProcessor.updateJdoqlVariableDeclaration(
                    jdoqlVariableDeclaration, "org.ecocean.Encounter enc13");
                paramMap.put("releaseDateTo", releaseDateTo);
                prettyPrint.append("releaseDate <= " + simpleDateFormat.format(releaseDateTo));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Tag filters------------------------------------------------------
        StringBuilder metalTagFilter = new StringBuilder();
        Enumeration<String> parameterNames = request.getParameterNames();
        int metalTagsInQuery = 0;
        while (parameterNames.hasMoreElements()) {
            String parameterName = parameterNames.nextElement();
            final String metalTagPrefix = "metalTag(";
            if (parameterName.startsWith(metalTagPrefix)) {
                String metalTagLocation = parameterName.substring(metalTagPrefix.length(),
                    parameterName.lastIndexOf(')'));
                String value = request.getParameter(parameterName);
                if (value != null && value.trim().length() > 0) {
                    prettyPrint.append("metal tag ");
                    prettyPrint.append(metalTagLocation);
                    prettyPrint.append(" is ");
                    prettyPrint.append(value);
                    prettyPrint.append("<br/>");
                    String metalTagVar = "metalTag" + metalTagsInQuery++;
                    metalTagFilter.append("(enc12.metalTags.contains(" + metalTagVar + ") && ");
                    metalTagFilter.append(metalTagVar + ".location == " +
                        Util.quote(metalTagLocation));
                    String jdoParam = "tagNumber" + metalTagsInQuery;
                    metalTagFilter.append(" && " + metalTagVar + ".tagNumber == " +
                        Util.quote(value) + ")");
                }
            }
        }
        if (metalTagFilter.length() > 0) {
            if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                filter += " && ";
            }
            filter += metalTagFilter.toString();
            for (int i = 0; i < metalTagsInQuery; i++) {
                jdoqlVariableDeclaration = QueryProcessor.updateJdoqlVariableDeclaration(
                    jdoqlVariableDeclaration, "org.ecocean.tag.MetalTag metalTag" + i);
            }
            jdoqlVariableDeclaration = QueryProcessor.updateJdoqlVariableDeclaration(
                jdoqlVariableDeclaration, "org.ecocean.Encounter enc12");
        }
        String satelliteTagFilter = processSatelliteTagFilter(request, prettyPrint);
        if (satelliteTagFilter.length() > 0) {
            if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                filter += " && ";
            }
            filter += " (encounters.contains(enc10)) && ";
            filter += satelliteTagFilter;
            jdoqlVariableDeclaration = QueryProcessor.updateJdoqlVariableDeclaration(
                jdoqlVariableDeclaration, "org.ecocean.Encounter enc10");
        }
        String acousticTagFilter = processAcousticTagFilter(request, prettyPrint);
        if (acousticTagFilter.length() > 0) {
            if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                filter += " && ";
            }
            filter += acousticTagFilter;
            filter += " && (encounters.contains(enc11)) ";
            jdoqlVariableDeclaration = QueryProcessor.updateJdoqlVariableDeclaration(
                jdoqlVariableDeclaration, "org.ecocean.Encounter enc11");
        }
        // end Tag Filters -------------------------------------------------
        // hasTissueSample filters-------------------------------------------------
        if (request.getParameter("hasTissueSample") != null) {
            prettyPrint.append("Has tissue sample.");
            if (filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                filter += "encounters.contains(enc4) && enc4.tissueSamples.contains(dce3)";
            } else {
                if (filter.indexOf("enc4.tissueSamples.contains(dce3)") == -1) {
                    filter += (" && enc4.tissueSamples.contains(dce3) ");
                }
                if (filter.indexOf("encounters.contains(enc4)") == -1) {
                    filter += " && encounters.contains(enc4)";
                }
            }
            prettyPrint.append("<br />");
            if (!jdoqlVariableDeclaration.contains("org.ecocean.genetics.TissueSample dce3")) {
                jdoqlVariableDeclaration += ";org.ecocean.genetics.TissueSample dce3";
            }
            if (!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc4")) {
                jdoqlVariableDeclaration += ";org.ecocean.Encounter enc4";
            }
        }
        // end hasTissueSample filters-----------------------------------------------
        // hasPhoto filters-------------------------------------------------
        if (request.getParameter("hasPhoto") != null) {
            prettyPrint.append("Has at least one photo.");
            if (filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                filter +=
                    "encounters.contains(enc464) && enc464.annotations.contains(annot2) && annot2.features.contains(feat2) && feat2.asset != null ";
            } else if (filter.indexOf("enc464.annotations.contains(photo2)") == -1) {
                filter +=
                    (
                    " && encounters.contains(enc464) && enc464.annotations.contains(annot2) && annot2.features.contains(feat2) && feat2.asset != null ");
            }
            prettyPrint.append("<br />");
            if (!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc464")) {
                jdoqlVariableDeclaration += ";org.ecocean.Encounter enc464";
            }
            if (!jdoqlVariableDeclaration.contains("org.ecocean.Annotation annot2")) {
                jdoqlVariableDeclaration += ";org.ecocean.Annotation annot2";
            }
            if (!jdoqlVariableDeclaration.contains("org.ecocean.media.Feature feat2")) {
                jdoqlVariableDeclaration += ";org.ecocean.media.Feature feat2";
            }
        }
        // end hasPhoto filters-----------------------------------------------
        // keyword filters-------------------------------------------------
        myShepherd.beginDBTransaction();
        String[] keywords = request.getParameterValues("keyword");
        String photoKeywordOperator = "&&";
        if ((request.getParameter("photoKeywordOperator") != null) &&
            (request.getParameter("photoKeywordOperator").equals("_OR_"))) {
            photoKeywordOperator = "||";
        }
        if ((keywords != null) && (!keywords[0].equals("None"))) {
            if (filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) { filter += "("; } else {
                filter += " && (";
            }
            if ((request.getParameter("photoKeywordOperator") != null) &&
                (request.getParameter("photoKeywordOperator").equals("_OR_"))) {
                prettyPrint.append("Photo/video keyword is any one of the following: ");
            } else {
                prettyPrint.append("All of these photo/video keywords are applied: ");
            }
            int kwLength = keywords.length;
            for (int kwIter = 0; kwIter < kwLength; kwIter++) {
                String locIDFilter = "(";
                String kwParam = keywords[kwIter].replaceAll("%20", " ").trim();
                if (!kwParam.equals("")) {
                    if (locIDFilter.equals("(")) {
                        locIDFilter += " word" + kwIter + ".indexname == \"" + kwParam + "\" ";
                    } else {
                        locIDFilter += " " + photoKeywordOperator + " word" + kwIter +
                            ".indexname == \"" + kwParam + "\" ";
                    }
                    Keyword kw = myShepherd.getKeyword(kwParam.trim());
                    prettyPrint.append("\"" + kw.getReadableName() + "\" ");
                }
                locIDFilter += " )";
                if (filter.indexOf("encounters.contains(enc3_" + kwIter + ")") == -1) {
                    if (kwIter > 0) { filter += " " + photoKeywordOperator + " "; }
                    filter += "(  encounters.contains(enc3_" + kwIter + ")";
                }
                if (filter.indexOf("enc3_" + kwIter + ".annotations.contains(photo" + kwIter +
                    ")") == -1) {
                    filter += " && enc3_" + kwIter + ".annotations.contains(photo" + kwIter + ")";
                }
                if (filter.indexOf("photo" + kwIter + ".features.contains(feat" + kwIter + ")") ==
                    -1) {
                    filter += " && photo" + kwIter + ".features.contains(feat" + kwIter + ")";
                }
                if (filter.indexOf("feat" + kwIter + ".asset.keywords.contains(word" + kwIter +
                    ")") == -1) {
                    filter += " && feat" + kwIter + ".asset.keywords.contains(word" + kwIter + ")";
                }
                filter += (" && " + locIDFilter + ")");
                if (!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc3_" + kwIter)) {
                    jdoqlVariableDeclaration += ";org.ecocean.Encounter enc3_" + kwIter;
                }
                if (!jdoqlVariableDeclaration.contains("org.ecocean.Annotation photo" + kwIter)) {
                    jdoqlVariableDeclaration += ";org.ecocean.Annotation photo" + kwIter;
                }
                if (!jdoqlVariableDeclaration.contains("org.ecocean.Keyword word" + kwIter)) {
                    jdoqlVariableDeclaration += ";org.ecocean.Keyword word" + kwIter;
                }
                if (!jdoqlVariableDeclaration.contains("org.ecocean.media.Feature feat" + kwIter)) {
                    jdoqlVariableDeclaration += ";org.ecocean.media.Feature feat" + kwIter;
                }
            }
            filter += " ) ";

            prettyPrint.append("<br />");
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();

        // end photo keyword filters-----------------------------------------------
        // ms markers filters-------------------------------------------------

        myShepherd.beginDBTransaction();
        List<String> markers = myShepherd.getAllLoci();
        int numMarkers = markers.size();
        String theseMarkers = "";
        boolean hasMarkers = false;
        for (int h = 0; h < numMarkers; h++) {
            String marker = markers.get(h);
            if (request.getParameter(marker) != null) {
                hasMarkers = true;
                String locIDFilter = "(";
                locIDFilter += " " + marker.replaceAll("-", "") + ".name == \"" + marker + "\" ";
                locIDFilter += " )";

                int alleleNum = 0;
                boolean hasMoreAlleles = true;
                while (hasMoreAlleles) {
                    if (request.getParameter((marker + "_alleleValue" + alleleNum)) != null) {
                        try {
                            Integer thisInt = new Integer(request.getParameter((marker +
                                "_alleleValue" + alleleNum)));
                            Integer relaxValue = new Integer(request.getParameter(
                                "alleleRelaxValue"));
                            Integer upperValue = thisInt + relaxValue;
                            Integer lowerValue = thisInt - relaxValue;
                            locIDFilter += (" && (" + marker.replaceAll("-",
                                "") + ".allele" + alleleNum + " >= " + lowerValue + ")" + " && (" +
                                marker.replaceAll("-",
                                "") + ".allele" + alleleNum + " <= " + upperValue + ")");
                        } catch (Exception e) {
                            hasMoreAlleles = false;
                        }
                    } else {
                        hasMoreAlleles = false;
                    }
                    alleleNum++;
                }
                theseMarkers += (marker + " ");
                if (filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                    filter +=
                        "encounters.contains(enc2) && enc2.tissueSamples.contains(dce2) && dce2.analyses.contains(msanalysis) && msanalysis.loci.contains("
                        + marker.replaceAll("-", "") + ") && " + locIDFilter;
                } else {
                    if (filter.indexOf("encounters.contains(enc2)") == -1) {
                        filter += " && encounters.contains(enc2)";
                    }
                    if (filter.indexOf("enc2.tissueSamples.contains(dce2)") == -1) {
                        filter += " && enc2.tissueSamples.contains(dce2)";
                    }
                    if (filter.indexOf("dce2.analyses.contains(analysis)") == -1) {
                        filter += " && dce2.analyses.contains(msanalysis)";
                    }
                    if (filter.indexOf("msanalysis.loci.contains(" + marker.replaceAll("-",
                        "") + ")") == -1) {
                        filter += " && msanalysis.loci.contains(" + marker.replaceAll("-",
                            "") + ")";
                    }
                    filter += (" && " + locIDFilter);
                }
                if (!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc2")) {
                    jdoqlVariableDeclaration += ";org.ecocean.Encounter enc2";
                }
                if (!jdoqlVariableDeclaration.contains("org.ecocean.genetics.TissueSample dce2")) {
                    jdoqlVariableDeclaration += ";org.ecocean.genetics.TissueSample dce2";
                }
                if (!jdoqlVariableDeclaration.contains(
                    "org.ecocean.genetics.MicrosatelliteMarkersAnalysis msanalysis")) {
                    jdoqlVariableDeclaration +=
                        ";org.ecocean.genetics.MicrosatelliteMarkersAnalysis msanalysis";
                }
                if (!jdoqlVariableDeclaration.contains("org.ecocean.genetics.Locus " +
                    marker.replaceAll("-", ""))) {
                    jdoqlVariableDeclaration += ";org.ecocean.genetics.Locus " +
                        marker.replaceAll("-", "");
                }
            }
        }
        if (hasMarkers) {
            prettyPrint.append("Microsatellite marker is one of the following: ");
            theseMarkers += "<br />";
            prettyPrint.append(theseMarkers);
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        // end ms markers filters-----------------------------------------------
        // has msmarkers filter-------------------------------------------------
        if (request.getParameter("hasMSMarkers") != null) {
            if (filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                filter +=
                    "encounters.contains(enc2) && enc2.tissueSamples.contains(dce2) && dce2.analyses.contains(msanalysis)";
            } else {
                if (filter.indexOf("encounters.contains(enc2)") == -1) {
                    filter += " && encounters.contains(enc2)";
                }
                if (filter.indexOf("enc2.tissueSamples.contains(dce2)") == -1) {
                    filter += " && enc2.tissueSamples.contains(dce2)";
                }
                if (filter.indexOf("dce2.analyses.contains(analysis)") == -1) {
                    filter += " && dce2.analyses.contains(msanalysis)";
                }
            }
            if (!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc2")) {
                jdoqlVariableDeclaration += ";org.ecocean.Encounter enc2";
            }
            if (!jdoqlVariableDeclaration.contains("org.ecocean.genetics.TissueSample dce2")) {
                jdoqlVariableDeclaration += ";org.ecocean.genetics.TissueSample dce2";
            }
            if (!jdoqlVariableDeclaration.contains(
                "org.ecocean.genetics.MicrosatelliteMarkersAnalysis msanalysis")) {
                jdoqlVariableDeclaration +=
                    ";org.ecocean.genetics.MicrosatelliteMarkersAnalysis msanalysis";
            }
            prettyPrint.append("Has microsatellite markers.");
            theseMarkers += "<br />";
            prettyPrint.append(theseMarkers);
        }
        // end ms markers filters-----------------------------------------------
        // hasHaplotype filters-------------------------------------------------
        if (request.getParameter("hasHaplotype") != null) {
            prettyPrint.append("Has a haplotype assigned. ");
            if (filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                filter +=
                    "encounters.contains(enc7) && enc7.tissueSamples.contains(dce1) && dce1.analyses.contains(analysis)";
            } else {
                if (filter.indexOf("enc7.tissueSamples.contains(dce1)") == -1) {
                    filter += " && enc7.tissueSamples.contains(dce1)";
                }
                if (filter.indexOf("encounters.contains(enc7)") == -1) {
                    filter += " && encounters.contains(enc7)";
                }
                if (filter.indexOf("dce1.analyses.contains(analysis)") == -1) {
                    filter += " && dce1.analyses.contains(analysis)";
                }
            }
            prettyPrint.append("<br />");
            if (!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc7")) {
                jdoqlVariableDeclaration += ";org.ecocean.Encounter enc7";
            }
            if (!jdoqlVariableDeclaration.contains("org.ecocean.genetics.TissueSample dce1")) {
                jdoqlVariableDeclaration += ";org.ecocean.genetics.TissueSample dce1";
            }
            if (!jdoqlVariableDeclaration.contains(
                "org.ecocean.genetics.MitochondrialDNAAnalysis analysis")) {
                jdoqlVariableDeclaration +=
                    ";org.ecocean.genetics.MitochondrialDNAAnalysis analysis";
            }
        }
        // end hasHaplotype filters-----------------------------------------------
        // genetic sex filters-------------------------------------------------
        String[] genSexes = request.getParameterValues("geneticSexField");
        if ((genSexes != null) && (!genSexes[0].equals("None"))) {
            prettyPrint.append("Genetic sex determination is one of the following: ");
            int kwLength = genSexes.length;
            String locIDFilter = "(";
            for (int kwIter = 0; kwIter < kwLength; kwIter++) {
                String kwParam = genSexes[kwIter].replaceAll("%20", " ").trim();
                if (!kwParam.equals("")) {
                    if (locIDFilter.equals("(")) {
                        locIDFilter += " sexanalysis.sex == \"" + kwParam + "\" ";
                    } else {
                        locIDFilter += " || sexanalysis.sex == \"" + kwParam + "\" ";
                    }
                    prettyPrint.append(kwParam + " ");
                }
            }
            locIDFilter += " )";
            if (filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
                filter +=
                    "encounters.contains(enc9) && enc9.tissueSamples.contains(dce9) && dce9.analyses.contains(sexanalysis) && "
                    + locIDFilter;
            } else {
                if (filter.indexOf("encounters.contains(enc9)") == -1) {
                    filter += " && encounters.contains(enc9)";
                }
                if (filter.indexOf("enc9.tissueSamples.contains(dce9)") == -1) {
                    filter += " && enc9.tissueSamples.contains(dce9)";
                }
                if (filter.indexOf("dce9.analyses.contains(sexanalysis)") == -1) {
                    filter += " && dce9.analyses.contains(sexanalysis)";
                }
                filter += (" && " + locIDFilter);
            }
            prettyPrint.append("<br />");
            if (!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc9")) {
                jdoqlVariableDeclaration += ";org.ecocean.Encounter enc9";
            }
            if (!jdoqlVariableDeclaration.contains("org.ecocean.genetics.TissueSample dce9")) {
                jdoqlVariableDeclaration += ";org.ecocean.genetics.TissueSample dce9";
            }
            if (!jdoqlVariableDeclaration.contains(
                "org.ecocean.genetics.SexAnalysis sexanalysis")) {
                jdoqlVariableDeclaration += ";org.ecocean.genetics.SexAnalysis sexanalysis";
            }
        }
        // end genetic sex filters-----------------------------------------------
        // filter for genus------------------------------------------
        if ((request.getParameter("genusField") != null) &&
            (!request.getParameter("genusField").equals(""))) {
            String genusSpecies = request.getParameter("genusField").replaceAll("%20", " ").trim();
            String genus = "";
            String specificEpithet = "";

            // now we have to break apart genus species
            StringTokenizer tokenizer = new StringTokenizer(genusSpecies, " ");
            if (tokenizer.countTokens() == 2) {
                genus = tokenizer.nextToken();
                specificEpithet = tokenizer.nextToken();

                filter = filterWithCondition(filter, " enc.genus == '" + genus + "' ");
                filter = filterWithCondition(filter,
                    " enc.specificEpithet == '" + specificEpithet + "' ");

                prettyPrint.append("genus and species are \"" + genusSpecies + "\".<br />");
            }
        }
        // filter for identificationRemarks------------------------------------------
        if ((request.getParameter("identificationRemarksField") != null) &&
            (!request.getParameter("identificationRemarksField").equals(""))) {
            String idRemarks = request.getParameter("identificationRemarksField").trim();
            filter = filterWithCondition(filter,
                "encounters.contains(enc98) && enc98.identificationRemarks.startsWith('" +
                idRemarks + "')");
            if (!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc98")) {
                jdoqlVariableDeclaration += ";org.ecocean.Encounter enc98";
            }
            prettyPrint.append("identificationRemarks starts with \"" + idRemarks + "\".<br />");
        }
        // filter by alive/dead status------------------------------------------
        if ((request.getParameter("alive") != null) || (request.getParameter("dead") != null)) {
            if (request.getParameter("alive") == null) {
                filter = filterWithCondition(filter, "!enc.livingStatus.startsWith('alive')");

                prettyPrint.append("Alive.<br />");
            }
            if (request.getParameter("dead") == null) {
                filter = filterWithCondition(filter, "!enc.livingStatus.startsWith('dead')");

                prettyPrint.append("Dead.<br />");
            }
        }
        // start DOB filter----------------------------
        if ((request.getParameter("DOBstart") != null) &&
            (request.getParameter("DOBend") != null) &&
            (!request.getParameter("DOBstart").equals("")) &&
            (!request.getParameter("DOBend").equals(""))) {
            try {
                DateTime gcMin = new DateTime(request.getParameter("DOBstart"));
                DateTime gcMax = new DateTime(request.getParameter("DOBend"));

                filter = filterWithCondition(filter,
                    "((timeOfBirth >= " + gcMin.getMillis() + ") && (timeOfBirth <= " +
                    gcMax.getMillis() + "))");

                prettyPrint.append("Date of birth between: " + request.getParameter("DOBstart") +
                    " and " + request.getParameter("DOBend") + "<br />");
            } catch (Exception nfe) {
                nfe.printStackTrace();
            }
        }
        // end DOB filter ----------------------------------------
        // start DOD filter----------------------------
        if ((request.getParameter("DODstart") != null) &&
            (request.getParameter("DODend") != null) &&
            (!request.getParameter("DODstart").equals("")) &&
            (!request.getParameter("DODend").equals(""))) {
            try {
                DateTime gcMin = new DateTime(request.getParameter("DODstart"));
                DateTime gcMax = new DateTime(request.getParameter("DODend"));

                filter = filterWithCondition(filter,
                    "((timeOfDeath >= " + gcMin.getMillis() + ") && (timeOfDeath <= " +
                    gcMax.getMillis() + "))");

                prettyPrint.append("Date of death between: " + request.getParameter("DODstart") +
                    " and " + request.getParameter("DODend") + "<br />");
            } catch (Exception nfe) {
                nfe.printStackTrace();
            }
        }
        // end DOD filter ----------------------------------------
        // start date filter----------------------------
        if ((request.getParameter("datepicker1") != null) &&
            (!request.getParameter("datepicker1").trim().equals("")) &&
            (request.getParameter("datepicker2") != null) &&
            (!request.getParameter("datepicker2").trim().equals(""))) {
            try {
                DateTimeFormatter parser = ISODateTimeFormat.dateTimeParser();
                DateTime date1 = parser.parseDateTime(request.getParameter("datepicker1"));
                DateTime date2 = parser.parseDateTime(request.getParameter("datepicker2"));
                long date1Millis = date1.getMillis();
                long date2Millis = date2.getMillis();
                // if same dateTime is set by both pickers, then add a full day of milliseconds to picker2 to cover the entire day
                date2Millis += (24 * 60 * 60 * 1000 - 1);

                prettyPrint.append("Dates between: " + date1.toString(ISODateTimeFormat.date()) +
                    " and " + date2.toString(ISODateTimeFormat.date()) + "<br />");

                filter = filterWithCondition(filter,
                    "((enc.dateInMilliseconds >= " + date1Millis +
                    ") && (enc.dateInMilliseconds <= " + date2Millis + "))");
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
        }
        // end date filter ----------------------------------------
        // GPS filters-------------------------------------------------
        if ((request.getParameter("ne_lat") != null) &&
            (!request.getParameter("ne_lat").equals(""))) {
            if ((request.getParameter("ne_long") != null) &&
                (!request.getParameter("ne_long").equals(""))) {
                if ((request.getParameter("sw_lat") != null) &&
                    (!request.getParameter("sw_lat").equals(""))) {
                    if ((request.getParameter("sw_long") != null) &&
                        (!request.getParameter("sw_long").equals(""))) {
                        try {
                            String thisLocalFilter = "(";
                            double ne_lat = (new Double(request.getParameter(
                                "ne_lat"))).doubleValue();
                            double ne_long = (new Double(request.getParameter(
                                "ne_long"))).doubleValue();
                            double sw_lat = (new Double(request.getParameter(
                                "sw_lat"))).doubleValue();
                            double sw_long = (new Double(request.getParameter(
                                "sw_long"))).doubleValue();
                            if ((sw_long > 0) && (ne_long < 0)) {
                                // process lats
                                thisLocalFilter += "(enc.decimalLatitude <= " +
                                    request.getParameter("ne_lat") +
                                    ") && (enc.decimalLatitude >= " +
                                    request.getParameter("sw_lat") + ")";

                                // process longs
                                thisLocalFilter += " && ((enc.decimalLongitude <= " +
                                    request.getParameter("ne_long") +
                                    ") || (enc.decimalLongitude >= " +
                                    request.getParameter("sw_long") + "))";

                                // }
                            } else {
                                // process lats
                                thisLocalFilter += "(enc.decimalLatitude <= " +
                                    request.getParameter("ne_lat") +
                                    ") && (enc.decimalLatitude >= " +
                                    request.getParameter("sw_lat") + ")";

                                // process longs
                                thisLocalFilter += " && (enc.decimalLongitude <= " +
                                    request.getParameter("ne_long") +
                                    ") && (enc.decimalLongitude >= " +
                                    request.getParameter("sw_long") + ")";
                            }
                            thisLocalFilter += " )";
                            if (filter.equals("")) { filter = thisLocalFilter; } else {
                                filter = filterWithCondition(filter, thisLocalFilter);
                            }
                            prettyPrint.append("GPS Boundary NE: \"" +
                                request.getParameter("ne_lat") + ", " +
                                request.getParameter("ne_long") + "\".<br />");
                            prettyPrint.append("GPS Boundary SW: \"" +
                                request.getParameter("sw_lat") + ", " +
                                request.getParameter("sw_long") + "\".<br />");
                        } catch (Exception ee) {
                            System.out.println(
                                "Exception when trying to process lat and long data in EncounterQueryProcessor!");
                            ee.printStackTrace();
                        }
                    }
                }
            }
        }
        // end GPS filters-----------------------------------------------
        if (request.getParameter("noQuery") == null) {
            // build the rest of the MarkedIndividual query filter string
            // --filter by years between resights---------------------------
            if ((request.getParameter("resightGap") != null) &&
                (!request.getParameter("resightGap").equals("")) &&
                (request.getParameter("resightGapOperator") != null)) {
                int numResights = 0;
                String operator = "greater";
                try {
                    numResights = (new Integer(request.getParameter("resightGap"))).intValue();
                    operator = request.getParameter("resightGapOperator");
                } catch (NumberFormatException nfe) {}
                if (operator.equals("greater")) {
                    operator = ">=";
                    prettyPrint.append("Number of years between resights is >= " +
                        request.getParameter("resightGap") + "<br />");
                } else if (operator.equals("less")) {
                    operator = "<=";
                    prettyPrint.append("Number of years between resights is <= " +
                        request.getParameter("resightGap") + "<br />");
                } else if (operator.equals("equals")) {
                    operator = "==";
                    prettyPrint.append("Number of years between resights is = " +
                        request.getParameter("resightGap") + "<br />");
                }
                filter = filterWithCondition(filter,
                    "( maxYearsBetweenResightings " + operator + " " + numResights + " )");
            }
            // ---end if resightOnly---------------------------------------
            // filter for sex------------------------------------------
            if ((request.getParameter("male") != null) ||
                (request.getParameter("female") != null) ||
                (request.getParameter("unknown") != null)) {
                if (request.getParameter("male") == null) {
                    filter = filterWithCondition(filter, "!sex.startsWith('male')");
                    prettyPrint.append("Sex is not male.<br />");
                }
                if (request.getParameter("female") == null) {
                    filter = filterWithCondition(filter, "!sex.startsWith('female')");
                    prettyPrint.append("Sex is not female.<br />");
                }
                if (request.getParameter("unknown") == null) {
                    filter = filterWithCondition(filter,
                        "!sex.startsWith('unknown') && sex != null");
                    prettyPrint.append("Sex is not unknown.<br />");
                }
            }
            // filter by sex--------------------------------------------------------------------------------------
        } // end if not noQuery
          // in the case where no parameters were specified, we need to replace the final "&&"
        // System.out.println("filter is--"+filter+"--");
        if (filter.equals(SELECT_FROM_ORG_ECOCEAN_INDIVIDUAL_WHERE)) {
            filter = "SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc)";
        }
        filter += jdoqlVariableDeclaration;
        filter += parameterDeclaration;
        myShepherd = null;
        System.out.println("IndividualQueryProcessor filter: " + filter);

        // quick fix in case we have any duplicated '&& &&' due to bad query filter construction above
        filter = filter.replaceAll("&&\\s&&", " && ");

        return filter;
    }

    public static MarkedIndividualQueryResult processQuery(Shepherd myShepherd,
        HttpServletRequest request, String order) {
        Iterator<MarkedIndividual> allSharks;
        Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
        StringBuffer prettyPrint = new StringBuffer();
        Map<String, Object> paramMap = new HashMap<String, Object>();
        String filter = queryStringBuilder(request, prettyPrint, paramMap);

        Query query = myShepherd.getPM().newQuery(filter);

        if ((order != null) && (!order.trim().equals(""))) {
            query.setOrdering(order);
        }
        try {
            // range: can be passed as parameter (from user) or attribute (from other servlet)
            int rangeStart = -1;
            int rangeEnd = -1;
            try {
                if (request.getParameter("rangeStart") != null) {
                    rangeStart = Integer.parseInt(request.getParameter("rangeStart"));
                } else if (request.getAttribute("rangeStart") != null) {
                    rangeStart = (Integer)request.getAttribute("rangeStart");
                }
                if (request.getParameter("rangeEnd") != null) {
                    rangeEnd = Integer.parseInt(request.getParameter("rangeEnd"));
                } else if (request.getAttribute("rangeEnd") != null) {
                    rangeEnd = (Integer)request.getAttribute("rangeEnd");
                }
            } catch (NumberFormatException nfe) {}
            if (rangeStart != -1 && rangeEnd != -1) {
                query.setRange(rangeStart, rangeEnd);
            }
            if (request.getParameter("sort") != null) {
                if (request.getParameter("sort").equals("sex")) {
                    allSharks = myShepherd.getAllMarkedIndividuals(query, "sex ascending",
                        paramMap);
                } else if (request.getParameter("sort").equals("name")) {
                    allSharks = myShepherd.getAllMarkedIndividuals(query, "individualID ascending",
                        paramMap);
                } else if (request.getParameter("sort").equals("numberEncounters")) {
                    allSharks = myShepherd.getAllMarkedIndividuals(query,
                        "numberEncounters descending", paramMap);
                } else if (request.getParameter("sort").equals("numberLocations")) {
                    allSharks = myShepherd.getAllMarkedIndividuals(query,
                        "numberLocations descending", paramMap);
                } else if (request.getParameter("sort").equals("dateTimeLatestSighting")) {
                    allSharks = myShepherd.getAllMarkedIndividuals(query,
                        "dateTimeLatestSighting descending", paramMap);
                }
                else if (request.getParameter("sort").equals("dateTimeLatestSighting")) {
                    allSharks = myShepherd.getAllMarkedIndividuals(query,
                        "dateTimeLatestSighting descending", paramMap);
                } else {
                    allSharks = myShepherd.getAllMarkedIndividuals(query, "individualID ascending",
                        paramMap);
                }
            } else {
                allSharks = myShepherd.getAllMarkedIndividuals(query, "individualID ascending",
                    paramMap);
                // keyword and then individualID ascending
            }
            // process over to Vector
            if (allSharks != null) {
                while (allSharks.hasNext()) {
                    MarkedIndividual temp_shark = allSharks.next();
                    rIndividuals.add(temp_shark);
                }
            }
        } catch (NullPointerException npe) {}
        // community search
        if (request.getParameterValues("community") != null) {
            String[] communities = request.getParameterValues("community");
            int numCommunities = communities.length;
            prettyPrint.append("Social unit is one of the following: ");
            for (int i = 0; i < numCommunities; i++) {
                prettyPrint.append(communities[i] + " ");
                SocialUnit su = myShepherd.getSocialUnit(communities[i]);
                if (su != null) {
                    for (int q = 0; q < rIndividuals.size(); q++) {
                        MarkedIndividual tShark = (MarkedIndividual)rIndividuals.get(q);
                        if (!su.hasMarkedIndividualAsMember(tShark)) {
                            rIndividuals.remove(q);
                            q--;
                        }
                    }
                }
            }
            prettyPrint.append("<br />");
        }
        // role search
        if (request.getParameterValues("role") != null) {
            boolean orRoles = true;
            if (request.getParameter("andRoles") != null) { orRoles = false; }
            String[] roles = request.getParameterValues("role");
            int numRoles = roles.length;
            if (!orRoles) {
                prettyPrint.append("Social roles include all of the following: ");
            } else {
                prettyPrint.append("Social roles is one of the following: ");
            }
            for (int h = 0; h < numRoles; h++) {
                prettyPrint.append(roles[h] + "&nbsp;");
            }
            // logical OR the roles
            for (int q = 0; q < rIndividuals.size(); q++) {
                MarkedIndividual tShark = (MarkedIndividual)rIndividuals.get(q);
                List<String> myRoles = myShepherd.getAllRoleNamesForMarkedIndividual(
                    tShark.getIndividualID());
                if (orRoles) {
                    // logical OR the role
                    boolean hasRole = false;
                    int f = 0;
                    while (!hasRole && (f < numRoles)) {
                        if (myRoles.contains(roles[f])) { hasRole = true; }
                        f++;
                    }
                    if (!hasRole) {
                        rIndividuals.remove(q);
                        q--;
                    }
                } else {
                    // logical AND the roles
                    boolean hasRole = true;
                    int f = 0;
                    while (hasRole && (f < numRoles)) {
                        if (!myRoles.contains(roles[f])) { hasRole = false; }
                        f++;
                    }
                    if (!hasRole) {
                        rIndividuals.remove(q);
                        q--;
                    }
                }
            }
            prettyPrint.append("<br />");
        }
        // min number of resights
        if ((request.getParameter("numResights") != null) &&
            (!request.getParameter("numResights").equals("")) &&
            (request.getParameter("numResightsOperator") != null)) {
            prettyPrint.append("Number of resights is " +
                request.getParameter("numResightsOperator") + " than " +
                request.getParameter("numResights") + "<br />");

            int numResights = 1;
            String operator = "greater";
            try {
                numResights = (new Integer(request.getParameter("numResights"))).intValue();
                operator = request.getParameter("numResightsOperator");
            } catch (NumberFormatException nfe) {}
            for (int q = 0; q < rIndividuals.size(); q++) {
                MarkedIndividual tShark = (MarkedIndividual)rIndividuals.get(q);
                if (operator.equals("greater")) {
                    if (tShark.getMaxNumYearsBetweenSightings() < numResights) {
                        rIndividuals.remove(q);
                        q--;
                    }
                } else if (operator.equals("less")) {
                    if (tShark.getMaxNumYearsBetweenSightings() > numResights) {
                        rIndividuals.remove(q);
                        q--;
                    }
                } else if (operator.equals("equals")) {
                    if (tShark.getMaxNumYearsBetweenSightings() != numResights) {
                        rIndividuals.remove(q);
                        q--;
                    }
                }
            } // end for
        } // end if resightOnly

        String[] locCodes = request.getParameterValues("locationCodeField");
        // check whether locationIDs are AND'd rather than OR'd
        if (request.getParameter("andLocationIDs") != null) {
            prettyPrint = new StringBuffer(prettyPrint.toString().replaceAll(
                "Sighted in at least one of the following locationsIDs",
                "Sighted at least once in each of the following location IDs"));
            if ((locCodes != null) && (!locCodes[0].equals("None"))) {
                for (int q = 0; q < rIndividuals.size(); q++) {
                    MarkedIndividual tShark = (MarkedIndividual)rIndividuals.get(q);
                    int kwLength = locCodes.length;
                    int kwIter = 0;
                    boolean matchesSelectedLocationIDs = true;
                    while ((kwIter < kwLength) && matchesSelectedLocationIDs) {
                        String kwParam = locCodes[kwIter].replaceAll("%20", " ").trim();
                        if (!kwParam.equals("")) {
                            if (!tShark.wasSightedInLocationCode(kwParam)) {
                                rIndividuals.remove(q);
                                q--;
                                matchesSelectedLocationIDs = false;
                            }
                        }
                        kwIter++;
                    }
                }
            }
        }
        // filter based on first sighting year
        if ((request.getParameter("firstYearField") != null) &&
            (!request.getParameter("firstYearField").trim().equals(""))) {
            prettyPrint.append("First sighted in year: " + request.getParameter("firstYearField") +
                "<br />");
            int firstSightedInYear = new Integer(request.getParameter("firstYearField")).intValue();
            for (int q = 0; q < rIndividuals.size(); q++) {
                MarkedIndividual tShark = (MarkedIndividual)rIndividuals.get(q);
                if (tShark.getEarliestSightingYear() != firstSightedInYear) {
                    rIndividuals.remove(q);
                    q--;
                }
            } // end for
        }
        query.closeAll();
        return (new MarkedIndividualQueryResult(rIndividuals, filter, prettyPrint.toString()));
    }

    private static String processSatelliteTagFilter(HttpServletRequest request,
        StringBuffer prettyPrint) {
        StringBuilder sb = new StringBuilder();
        String name = request.getParameter("satelliteTagName");

        if (name != null && name.length() > 0 && !"None".equals(name)) {
            prettyPrint.append("satellite tag name is: ");
            prettyPrint.append(name);
            prettyPrint.append("<br/>");
            sb.append('(');
            sb.append("enc10.satelliteTag.name == ");
            sb.append(Util.quote(name));
            sb.append(')');
        }
        String serialNumber = request.getParameter("satelliteTagSerial");
        if (serialNumber != null && serialNumber.length() > 0) {
            prettyPrint.append("satellite tag serial is: ");
            prettyPrint.append(serialNumber);
            prettyPrint.append("<br/>");
            if (sb.length() > 0) {
                sb.append(" && ");
            }
            sb.append('(');
            sb.append("enc10.satelliteTag.serialNumber == ");
            sb.append(Util.quote(serialNumber));
            sb.append(')');
        }
        String argosPttNumber = request.getParameter("satelliteTagArgosPttNumber");
        if (argosPttNumber != null && argosPttNumber.length() > 0) {
            prettyPrint.append("satellite tag Argos PTT Number is: ");
            prettyPrint.append(argosPttNumber);
            prettyPrint.append("<br/>");
            if (sb.length() > 0) {
                sb.append(" && ");
            }
            sb.append('(');
            sb.append("enc10.satelliteTag.argosPttNumber == ");
            sb.append(Util.quote(argosPttNumber));
            sb.append(')');
        }
        return sb.toString();
    }

    private static String processAcousticTagFilter(HttpServletRequest request,
        StringBuffer prettyPrint) {
        StringBuilder tagFilter = new StringBuilder();
        String acousticTagSerial = request.getParameter("acousticTagSerial");

        if (acousticTagSerial != null && acousticTagSerial.length() > 0) {
            prettyPrint.append("acoustic tag serial number is: ");
            prettyPrint.append(acousticTagSerial);
            prettyPrint.append("<br/>");
            tagFilter.append('(');
            tagFilter.append("enc11.acousticTag.serialNumber == ");
            tagFilter.append(Util.quote(acousticTagSerial));
            tagFilter.append(')');
        }
        String acousticTagId = request.getParameter("acousticTagId");
        if (acousticTagId != null && acousticTagId.length() > 0) {
            prettyPrint.append("acoustic tag id is: ");
            prettyPrint.append(acousticTagId);
            prettyPrint.append("<br/>");
            if (tagFilter.length() > 0) {
                tagFilter.append(" && ");
            }
            tagFilter.append('(');
            tagFilter.append("enc11.acousticTag.idNumber == ");
            tagFilter.append(Util.quote(acousticTagId));
            tagFilter.append(')');
        }
        return tagFilter.toString();
    }

    public static String addVars(String jdoqlVariableDeclaration, String vars) {
        QueryProcessor.updateJdoqlVariableDeclaration(jdoqlVariableDeclaration, vars);
        return jdoqlVariableDeclaration;
    }

    public static String updateParametersDeclaration(String parameterDeclaration,
        String typeAndVariable) {
        StringBuilder sb = new StringBuilder(parameterDeclaration);

        if (parameterDeclaration.length() == 0) {
            sb.append(" PARAMETERS ");
        } else {
            sb.append(", ");
        }
        sb.append(typeAndVariable);
        return sb.toString();
    }

    private static String addOrgVars(String jdoqlVariableDeclaration, String orgs) {
        QueryProcessor.updateJdoqlVariableDeclaration(jdoqlVariableDeclaration, orgs);
        return jdoqlVariableDeclaration;
    }
}
