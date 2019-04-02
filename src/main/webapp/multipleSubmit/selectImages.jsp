<%@ page contentType="text/html; charset=utf-8" language="java" 
        import="org.ecocean.CommonConfiguration,
        java.util.Properties, 
        org.ecocean.servlet.ServletUtilities,
        org.ecocean.*,
        java.util.Properties,
        java.util.List,
        java.util.ArrayList"        
%>

<%
    String langCode = ServletUtilities.getLanguageCode(request);
    String context=ServletUtilities.getContext(request);
    Properties props = new Properties();
    props = ShepherdProperties.getProperties("multipleSubmit.properties", langCode,context);
    //Properties recaptchaProps = new Properties();
    //recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", langCode,context);
%>


<jsp:include page="../header.jsp" flush="true"/>
<div class="container maincontent">

    <div class="row">
        <div class="col-xs-12 col-lg-12">
            <h2><%= props.getProperty("pageHeader")%></h2>
            <p><%= props.getProperty("headerDesc")%></p>
            <hr>
            <br>

            <form id="multipleSubmission"
                method="post"
                enctype="multipart/form-data"
                name="multipleSubmission"
                target="_self" dir="ltr"
                lang="en"
                accept-charset="UTF-8">

                <!-- specify number of individuals and number of encounters in two input items -->

                <div class="row"> 
                    <div class="col-sm-4">
                        <label>Specify number of individuals:</label>
                    </div>
                    <div class="col-sm-4">
                        <input type="number" name="numberIndividuals" min="1" max="50">
                    </div>
                </div>

                <br>

                <div class="row">
                    <div class="col-sm-4">
                        <label>Specify number of encounters:</label>
                    </div>
                    <div class="col-sm-4"> 
                        <input type="number" name="numberEncounters" min="1" max="50">
                    </div>
                </div>
                
                <!-- uses a button to click file input so we can style easier (file inputs suck to style) -->

                <div>    
                    <input class="btn btn-large btn-file-selector" type="button" onclick="document.getElementById('file-selector-input').click()" value="Select Files" />
                </div>
                <input id="file-selector-input" name="allFiles" class="hidden-input" type="file" accept=".jpg, .jpeg, .png, .bmp, .gif, .mov, .wmv, .avi, .mp4, .mpg" style="display:none;" multiple size="50" onChange="updateList(this);" />

                <br>

                <!-- Recaptcha widget -->
                <%= ServletUtilities.captchaWidget(request) %>

                <br>

                <button class="btn btn-large" id="sendButton" type="submit" onclick="return sendButtonClicked();" disabled>Continue</button>

            </form>
        </div> 
        <hr>
    </div>
</div>

<jsp:include page="../footer.jsp" flush="true"/>
