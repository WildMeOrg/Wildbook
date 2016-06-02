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
				.container-fluid.main-section .row.donate-row {
					width: 100%;
					min-height: 240px;
					background: url('<%=urlLoc%>/cust/mantamatcher/img/wwf-join-banner.jpg');
					background-repeat: no-repeat;
					background-position: center;
					margin-left: 0;
					margin-right: 0;
					margin-top: 10px;
				}

				@media (min-width: 1100px) {
					.container-fluid.main-section .row.donate-row {
						background: url('<%=urlLoc%>/cust/mantamatcher/img/wwf-join-banner-large.jpg');
						background-repeat: no-repeat;
						background-position: center;
						background-size: cover;
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
				}
				.row.collaborators-row a img.second {
					bottom: -1px;
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
								<div class="row donate-row">
									<div class="col-xs-6 bc4 donate-zone">
											<!--<h1 class="hidden">Wildbook</h1>-->
											<h2 class="jumboesque">Miten voit auttaa?</h2>
											<!--
											<button id="watch-movie" class="large light">
									Watch the movie
									<span class="button-icon" aria-hidden="true">
								</button>
							-->   <h2>
											<a href="adoptanimal.jsp">
													<!--<button class="large">Report encounter<span class="button-icon" aria-hidden="true"></button>-->
													<button class="btn btn-primary">Liity Norppa-Kummiksi</button>
											</a>
										</h2>
									</div>

								</div>

								<div class="row collaborators-row grey-background">
									<p>Yhteisty&ouml;ss&auml;:</p>
									</br>

									<a href="">
										<img border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/collab1.png">
									</a>
									&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
									<a href="">
										<img class="second" border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/collab2.png">
									</a>
								</div>


								<div class="row wwf-row">
									<div class="col-xs-2">
										<a href="">
											<img border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/wwf_panda_logo.svg">
										</a>
									</div>

									<div class="col-xs-10">
										<h2 style="color:#000;"> Rakennamme tulevaisuuden, jossa ihmiset ja luonto el&auml;v&auml;t tasapainossa</h2>

											<ul class="nav">
												<li><a href="#">Yksityisyyden suoja ja tekijänoikeudet</a></li>
												&nbsp;&nbsp;&nbsp;&nbsp;
												<li><a href="#">Keräyslupa ja rekisteriseloste</a></li>
												&nbsp;&nbsp;&nbsp;&nbsp;
												<li><a href="#">Työpaikat</a></li>
												&nbsp;&nbsp;&nbsp;&nbsp;
												<li><a href="#">Yhteystiedot</a></li>
												&nbsp;&nbsp;&nbsp;&nbsp;
												<li><a href="#">Palaute</a></li>
											</ul>
										</br>
										<small class="copyright source-org vcard" itemprop="copyrightHolder">Copyright © <span itemprop="copyrightYear">2014</span> <span class="org fn" itemprop="creator">WWF Suomi</span>, valokuvien tekijänoikeudet kuvien yhteydessä</small>
									</div>

								</div>

                <div class="row grey-background very-footer">
                  <p class="col-sm-8 col-md-8 col-lg-8">
                    <small>This software is distributed under the GPL v3 license and is intended to support mark-recapture field studies.
                  <br> <a href="http://www.wildme.org/wildbook" target="_blank">Wildbook v.<%=ContextConfiguration.getVersion() %></a> </small>
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

				  ga('create', 'UA-30944767-5', 'auto');
				  ga('send', 'pageview');

			</script>

        </footer>
        <!-- /footer -->
    </body>
</html>
