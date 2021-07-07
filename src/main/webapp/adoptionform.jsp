<%@ page contentType="text/html; charset=utf-8" language="java"
    import="org.ecocean.servlet.*, org.ecocean.*, java.util.Properties, java.util.Date, java.util.Enumeration, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException"
    %>
<jsp:include page="header.jsp" flush="true" />
    <%
    //handle some cache-related security
    response.setHeader("Cache-Control", "no-cache" );
    //Forces caches to obtain a new copy of the page from the origin server
    response.setHeader("Cache-Control", "no-store" );
    //Directs caches not to store the page under any circumstance
    response.setDateHeader("Expires", 0);
    //Causes the proxy cache to see the page as "stale"
    response.setHeader("Pragma", "no-cache" );
    //HTTP 1.0 backward compatibility
    String context="context0" ;
    context=ServletUtilities.getContext(request);
    //language setup
    String langCode="en" ;
    if(session.getAttribute("langCode")!=null){
        langCode=(String)session.getAttribute("langCode");
    }
    Properties props=new Properties();
    props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));
    Properties stripeProps=ShepherdProperties.getProperties("stripeKeys.properties", "" , context);
    if(stripeProps==null) {
        System.out.println("There are no available API keys for Stripe!");
    }
    String stripePublicKey=stripeProps.getProperty("publicKey");
    Shepherd myShepherd=new Shepherd(context);
    myShepherd.setAction ("adoptionform.jsp");
    myShepherd.beginDBTransaction();
    try{
        String shark="" ;
    if(request.getParameter("number") !=null) {
        shark=request.getParameter("number");
    } // Necessary to persist your selected shark across multiple form submissions. // Payment status is also stored in session.
    if(request.getParameter("number") !=null) {
        session.setAttribute( "queryShark" , request.getParameter("number") );
    }
    String sessionShark=null;
    if (session.getAttribute( "queryShark" ) !=null) {
        sessionShark=((String)session.getAttribute( "queryShark" )).trim();
    }

    //params from donorbox
    String donorboxId = "";
    if(Util.stringExists(request.getParameter("id"))){
        donorboxId=request.getParameter("id");
    }
    String donorboxFirstName = "";
    if(Util.stringExists(request.getParameter("first_name"))){
        donorboxFirstName=request.getParameter("first_name");
    }
    String donorboxLastName = "";
    if(Util.stringExists(request.getParameter("last_name"))){
        donorboxLastName=request.getParameter("last_name");
    }
    String donorboxAmnt = "";
    if(Util.stringExists(request.getParameter("amount"))){
        donorboxAmnt=request.getParameter("amount");
    }
    String donorboxCurrency = "";
    if(Util.stringExists(request.getParameter("currency"))){
        donorboxCurrency=request.getParameter("currency");
    }
    String donorboxDuration = "";
    if(Util.stringExists(request.getParameter("duration"))){
        donorboxDuration=request.getParameter("duration");
    }

        session.setAttribute( "emailEdit" , false );
        boolean hasNickName=true;
        String nick="" ;
        try {
            if ((sessionShark!=null)&&(myShepherd.getMarkedIndividual(sessionShark)!=null)) {
                MarkedIndividual mi=myShepherd.getMarkedIndividual(sessionShark);
                nick=mi.getNickName();
                if (((nick==null) || nick.equals("Unassigned"))||(nick.equals(""))) {
                    hasNickName=false;
                }
            }
        } catch (Exception e) {
        System.out.println("Error looking up nickname: "+sessionShark);
		e.printStackTrace();
	}


	int count = myShepherd.getNumAdoptions();
	int allSharks = myShepherd.getNumMarkedIndividuals();
	int countAdoptable = allSharks - count;

	boolean isOwner = true;
	boolean acceptedPayment = true;

  String id = "";
  String adopterName = "";
  String adopterAddress = "";
  String adopterEmail = "";
  String adopterImage="";
  String adoptionStartDate = "";
  String adoptionEndDate = "";
  String adopterQuote = "";
  String adoptionManager = "";
  String sharkForm = "";
  String encounterForm = "";
  String notes = "";
  String adoptionType = "";

	String servletURL = " ../AdoptionAction"; %>

        <link rel="stylesheet" href="css/createadoption.css">
        <%
            if(Util.stringExists(donorboxId) && Util.stringExists(shark)){ //TODO I could cross check this against the donorbox api (for an additional $17/month), but I don't think warranted. As is, an evil user could just but an id in the url and pass this check. But, let's not over-engineer. Who is trying to hoodwink a non-profit just to be able to nickname an animal?
        %>
        <div class="container maincontent">
            <section class="centered">
                <h2>Thank you for your support!</h2>
                <div class="row">
                    <div class="form-header">
                        <h2>Adoption Profile</h2>
                        <img src="cust/mantamatcher/img/circle-divider.png" style="margin-bottom: 1.2em;" />
                    </div>
                </div>
            </section>
            <section class="centered">

            <form id="adoption-form" action="AdoptionAction" method="post"
                enctype="multipart/form-data" name="adoption_submission" target="_self" dir="ltr" lang="en">
                    <div class="input-col-1">
                        <div class="input-group">
                            <span class="input-group-addon"><%=CommonConfiguration.getAnimalSingular(context)%> ID</span>
                            <input id="sharkId" class=" input-m-width" name="shark" type="text" value="<%=sessionShark%>"
                                placeholder="Browse the gallery and find the shark that suits you">
                            <%if (!shark.equals("")) { %>
                                <% } %>
                        </div>
                        <% if ((hasNickName==false )||(nick.equals(""))) { %>
                            <div class="input-group">
                                <span class="input-group-addon"><%=CommonConfiguration.getAnimalSingular(context)%> Nickname</span>
                                <input class="input-l-width" type="text" name="newNickName" id="newNickName"></input>
                            </div>
                            <% } %>
                                <input class="input-m-width adoptionStartDate" name="adoptionStartDate" type="hidden"
                                    value="<%=adoptionStartDate%>">

                                <div class="input-group">
                                    <span class="input-group-addon">Adopter Name (optional)</span>
                                    <input class=" input-l-width" name="adopterName" type="text" value="<%=adopterName%>">
                                </div>
                                <div class="input-group">
                                    <span class="input-group-addon">Adopter Email (optional)</span>
                                    <input class=" input-l-width" name="adopterEmail" type="text"
                                        value="<%=adopterEmail%>"><br />
                                </div>
                                <div class="input-group">
                                    <span class="input-group-addon">Address (optional)</span>
                                    <input class=" input-l-width" name="adopterAddress" type="text"
                                        value="<%=adopterAddress%>">
                                </div>
                                <div class="input-group">
                                    <span class="input-group-addon">Profile Photo (optional)</span>
                                    <% String adopterImageString="" ; if(adopterImage!=null){
                                        adopterImageString=adopterImage; } %>
                                        <input class="input-l-width" name="theFile1" type="file" size="30"
                                            value="<%=adopterImageString%>">&nbsp;&nbsp;
                                        <% if ((adopterImage !=null) && (!adopterImageString.equals(""))) { %>
                                            <img src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=id%>/thumb.jpg"
                                                align="absmiddle" />&nbsp;
                                            <% } %>
                                </div>
                    </div>
                <!-- </div> -->
                <!-- <div class="row"> -->
                    <div class="input-col-2">
                        <div class="input-group">
                            <span class="input-group-addon">Message (optional)</span>
                            <textarea name="adopterQuote" id="adopterQuote"
                                placeholder="Enter a personal or gift message here. (e.g. Why is research and conservation of this species important?) here."><%=adopterQuote%>
                  </textarea>
                        </div>

                        <%-- Recaptcha widget --%>
                            <%= ServletUtilities.captchaWidget(request) %>

                                <!-- No submit button unless payment is accepted. May switch to totally non visible form prior to payment. -->
                                <% if (acceptedPayment) { %>
                                    <button class="large" type="submit" name="Submit" value="Submit">Finish Adoption<span
                                            class="button-icon" aria-hidden="true"></span></button>
                                    <% } %>
                                        <% if (acceptedPayment) { %>
                    </div>
                <!-- </div> -->
            </form>
            <% } %>

            </section>
        </div>
        <%
            }
            else{
                %>
                <script type="text/javascript">
                    //redirect to payment page
                    window.location.href = "/createadoption.jsp";
                </script>
                <%
            }
        %>

        <!-- Auto populate start date with current date. -->
        <script>
            var myDate, day, month, year, date;
            myDate = new Date();
            day = myDate.getDate();
            if (day < 10)
                day = "0" + day;
            month = myDate.getMonth() + 1;
            if (month < 10)
                month = "0" + month;
            year = myDate.getFullYear();
            date = year + "-" + month + "-" + day;
            $(".adoptionStartDate").val(date);
            $(".adoptionStartHeader").text(date);
        </script>
    <%
    } catch(Exception e){
        e.printStackTrace();
    } finally{
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    }
    %>
    <jsp:include page="footer.jsp" flush="true" />
