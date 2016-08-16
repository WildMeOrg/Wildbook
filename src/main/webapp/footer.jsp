<%@ page
		contentType="text/html; charset=utf-8"
		language="java"
     	import="org.ecocean.CommonConfiguration,org.ecocean.ContextConfiguration"
%>
        <%
        String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);
        %>

        <!-- footer -->

				<style type="text/css">

				.page-footer .container-fluid .main-secion .row.donate-row {
					margin-top: 10px;
				}

				.row.donate-row .col-xs-6 {
					padding-left: 0;
					padding-right: 0;
				}

				footer.page-footer .row.donate-row {
					margin-left: 0px;
					margin-right: 0px;
					margin-top: 10px;
					min-height: 240px;
				}

				.container-fluid.main-section .row.donate-row .seal-pic {
					min-height: 240px;
					background: url('<%=urlLoc%>/cust/mantamatcher/img/wwf-join-banner.jpg');
					background-repeat: no-repeat;
					background-position: center;
					margin-left: 0;
					margin-right: 0;
				}


				@media (min-width: 1100px) {
					.container-fluid.main-section .row.donate-row .seal-pic {
						background: url('<%=urlLoc%>/cust/mantamatcher/img/wwf-join-banner-large.jpg');
						background-repeat: no-repeat;
						background-position: center;
						background-size: cover;
					}
				}
				@media (max-width: 540px) {
					.row.donate-row h2.jumboesque {
						padding-top: 20px;
					}
				}

				@media (max-width:768px) {
					div.row.donate-row div.donate-zone {
						margin-bottom: -20px;
					}
					@media (min-width:520px) {
						.container-fluid.main-section .row.donate-row .seal-pic {
							background: url('<%=urlLoc%>/cust/mantamatcher/img/wwf-join-banner-medium.jpg');
							background-repeat: no-repeat;
							background-position: center;
							background-size: cover;
						}
					}
				}


				.row.donate-row h2.jumboesque {
					padding-top: 50px;
					font-size: 3.3em;
					color: #16696d;
					text-align: center;
				}
				.row.donate-row .donate-zone h2 {
					text-align: center;
				}

				.row.donate-row .donate-zone {
					background-color: #dff2f1;
					min-height: 240px;
					padding-left: 5px;
					padding-right:5px;
				}

				.row.donate-row .donate-zone button.btn {
					white-space: normal;
					max-width: 90%;
				}

				footer.page-footer div.row.collaborators-row {
					position: relative;
					text-align: center;
					padding-top: 25px;
					padding-bottom: 40px;
					margin-left: 0;
					margin-right: 0;
				}
				.row.collaborators-row p {
					text-align: center;
					font-weight: bold;
					margin-bottom: 0;
				}
				.row.collaborators-row a img {
					position: relative;
					padding: 10px;
					height: 180px;
				}
				.row.collaborators-row a img.second {
					bottom: 5px;
					padding: 38px;
				}

				footer .container-fluid.main-section {
					padding-top: 0;
					padding-bottom: 0;
				}

				footer.page-footer div.row.wwf-row {
					background: #fff;
			    padding: 25px 0px 25px 15px;
			    margin-left: 0;
			    margin-right: 0;
				}
				footer.page-footer div.row.wwf-row a img {
					height: 100px;
					margin-top: 15px;
					margin-bottom: 15px;
				}
				footer .nav>li>a {
					padding: 0 0;
				}
				footer .nav>li>a:hover, footer .nav>li>a:focus {
					background-color: #fff;
				}
				footer div.row.very-footer {
					margin: 0 0 0 0;
					padding-top: 15px;
				}

				</style>


        <footer class="page-footer">

            <div class="container-fluid">
              <div class="container-fluid main-section">
								<div class="row donate-row" style="position:relative">
									<div class="col-sm-6 bc4 donate-zone">
											<!--<h1 class="hidden">Wildbook</h1>-->
											<h2 class="jumboesque">Miten voit auttaa?</h2>
											<!--
											<button id="watch-movie" class="large light">
									Watch the movie
									<span class="button-icon" aria-hidden="true">
								</button>
							-->   <h2>
											<a href="http://www.wwf.fi/norppakummiksi">
													<!--<button class="large">Report encounter<span class="button-icon" aria-hidden="true"></button>-->
													<button class="btn btn-primary">Liity Norppa-Kummiksi</button>
											</a>
										</h2>
									</div>
									<div class="col-sm-6 bc4 seal-pic">
									</div>


								</div>

								<div class="row collaborators-row grey-background">
									<p>Yhteisty&ouml;ss&auml;:</p>
									</br>

									<a href="http://www.uef.fi/web/norppa"><img border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/collab1.png"></a>
									<a href="http://www.metsa.fi/saimaannorppa">
										<img class="second" border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/collab2.png">
									</a>
								</div>


								<div class="row wwf-row">


									<div class="col-xs-12" style="text-align: center;">
									<a style="
    									background-image: url('http://norppagalleria.wwf.fi/cust/mantamatcher/img/wwf_panda_logo.svg');
    									background-repeat: no-repeat;
    									background-size: contain;
    									width: 72px;
    									height: 108px;
    									display: inline-block;"

    									href="/" title="Etusivulle" rel="home" tabindex="1"><span class="screen-reader-text">WWF</span></a>
										<h2 style="color:#000;"> Rakennamme tulevaisuuden, jossa ihmiset ja luonto el&auml;v&auml;t tasapainossa</h2>

											<ul class="nav">
																			<li><a href="/yksityisyydensuoja_tekijanoikeudet/">Yksityisyyden suoja ja tekijänoikeudet</a></li>
							<li><a href="http://www.wwf.fi/kerayslupa/">Keräyslupa ja rekisteriseloste</a></li>
							&nbsp;&nbsp;&nbsp;&nbsp;<li><a href="http://www.wwf.fi/wwf-suomi/tyopaikat/">Työpaikat</a></li>
							&nbsp;&nbsp;&nbsp;&nbsp;<li><a href="http://www.wwf.fi/wwf-suomi/yhteystiedot/">Yhteystiedot</a></li>
							&nbsp;&nbsp;&nbsp;&nbsp;<li><a href="http://www.wwf.fi/wwf-suomi/yhteystiedot/palaute/" onClick="ga('send', 'event', {'eventCategory': 'feedback', 'eventAction': 'fee_step1_footer_', 'eventLabel': 'lnk'});" >Palaute</a></li>
											</ul>
										</br>
										<small class="copyright source-org vcard" itemprop="copyrightHolder">Copyright © <span itemprop="copyrightYear">2014</span> <span class="org fn" itemprop="creator">WWF Suomi</span>, valokuvien tekijänoikeudet kuvien yhteydessä</small>
									</div>

								</div>

                <div class="row grey-background very-footer">

                  <p class="col-sm-8 col-md-8 col-lg-8">
                     <a href="http://www.wildbook.org" target="_blank">Wildbook v.<%=ContextConfiguration.getVersion() %></a> </small>
                  </p>
                  <a href="http://www.wildme.org/wildbook" class="col-sm-4 col-md-4 col-lg-4" title="This site is Powered by Wildbook">
                     <img src="<%=urlLoc %>/images/logo_wildbook.png" alt=" logo" class="pull-right" />



                  </a>
                </div>
              </div>
            </div>

            <script>
				  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
				  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
				  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
				  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

				  ga('create', 'UA-189268-24', 'auto');
				  ga('send', 'pageview');

			</script>

        </footer>
        <!-- /footer -->
    </body>
</html>
