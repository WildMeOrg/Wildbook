<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,java.util.Properties, org.ecocean.servlet.ServletUtilities, org.ecocean.NoteField, org.ecocean.Shepherd " %>
<%

  //setup our Properties object to hold all properties
  
  String langCode = ServletUtilities.getLanguageCode(request);

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);
  
  

  
  String context=ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
        request.setAttribute("pageTitle", "Kitizen Science &gt; Spay/Neuter Evidence");

%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">

<h1>Spay/Neuter Evidence</h1>
<img src="images/evidence_catpapers.jpg" width="260" height="400" hspace="10" vspace="10" align="right" />

<%= NoteField.buildHtmlDiv("0bf6f76b-0ef2-4358-8ecc-c19f13843de0", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-0bf6f76b-0ef2-4358-8ecc-c19f13843de0">
<p><em>
First, a short definition of terms used on this page and elsewhere on the website.  There are a lot of names for programs that sterilize unowned outdoor cats (trap-neuter-return, shelter-neuter-return, return to field, community cat programs) and return them to where they were trapped.  Here, we use &quot;spay/neuter program&quot; as our most all-inclusive and neutral term.  There are also many names for unowned outdoor cats (feral cat, community cat, stray cat, working cat, barn cat).  We have chosen to use &quot;free-roaming cat&quot; as our most all-inclusive and neutral term. </em></p>
</div>

<h2><a name="terminology" id="terminology"></a>Isn't there already proof that spay/neuter works? </h2>

<%= NoteField.buildHtmlDiv("0bf6f76b-0ef2-4358-8ecc-c19f13843de1", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-0bf6f76b-0ef2-4358-8ecc-c19f13843de1">
<p>In debates about whether or not spay/neuter programs &quot;work,&quot; people with different values and goals can disagree simply because they are looking at the question differently.  There are many ways to define <em>success</em>: cat population decreases, saving cats' lives in shelters, reducing cat complaints to animal control.  There are many <em>metrics</em> to measure: shelter statistics, free-roaming cat numbers, mathematical modeling outputs.  There are many ways to <em>design</em> research: controlled experiments, observational and case studies, and statistical modeling.  And finally, <em>time frames</em> can last from a short period to decades. </p>
<p>This page contains is a list of all known original, peer-reviewed research articles about the impacts of spay/neuter programs in North America, currently totaling 36.  Our research list does not include public opinion surveys, review articles, letters to the editor, veterinary best practice guidelines, unpublished data and student theses, studies from other parts of the world, dog spay/neuter, or general research about free-roaming cat behavior and health.  The list also does not include studies solely about feline contraceptives and other means of cat reproductive control that are currently in development but not yet in widespread use.  (To read more about those exciting new areas of research, visit <a href="https://www.acc-d.org" target="_blank">the Alliance for Contraception in Cats &amp; Dogs</a>.) </p>
</div>

<h2><a name="whatcitsci" id="whatcitsci2"></a>The terminology of science </h2>

<%= NoteField.buildHtmlDiv("0bf6f76b-0ef2-4358-8ecc-c19f13843de2", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-0bf6f76b-0ef2-4358-8ecc-c19f13843de2">
<p>There can be a language barrier and learning curve when you first start to read scientific papers, because many of the words used in science also have popular meanings. Here are some definitions of key scientific terms we use on this website and in the articles in our collection. </p>
<p><strong>Research</strong>: In casual usage, “research” is used to mean any act of gathering information to aid in decision-making, such as “researching vacation destinations.”  In a scientific context, research is a more formal and structured process.  The scientific method consists of identifying an issue to investigate, designing a study to collect data, analyzing the data, and drawing a conclusion based on the evidence.  This process can be decision-oriented, as in “actionable science” that applies scientific findings to decision-making in a management or policy realm.  On this web page, we group research into three basic designs: experimental, observational, and modeling.  All study designs are strengthened with the use of larger sample sizes and more replications to reduce variability.
</p>
</div>

<img src="images/evidence_controlledexperiment.jpg" width="300" height="300" hspace="10" vspace="10" align="left" />

<%= NoteField.buildHtmlDiv("0bf6f76b-0ef2-4358-8ecc-c19f13843de3", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-0bf6f76b-0ef2-4358-8ecc-c19f13843de3">
<p><strong>Experimental study</strong>: Experiments test whether an intentional intervention by the researcher affects subjects or areas compared to subjects or areas that do not receive the intervention.  These are known as “controls.”  For example, after spraying a fertilizer on your lawn for a month, it appears greener.  Does this prove that the fertilizer worked?  No, because you don't know what would have happened without the fertilizer.  Your lawn may be greener because it was a good month for rain.  A better plan would be a controlled experiment of spraying half of your lawn with fertilizer and leaving the other half alone.  Only by comparing treated animals, humans, or areas against a similar but untreated control group can you become more confident that it was your treatment that caused the difference.  The strongest experimental designs use randomization to determine which subjects get an intervention and which don't, ensuring that bias was not accidentally introduced in selection.  Controlled experiments can be difficult and expensive to conduct, but they allow us to draw the strongest conclusions. </p>
<p><strong>Observational study</strong>: Observational studies are easier and less expensive to conduct than experiments.  (Think of the grass example above: an observational case study is like noting that your lawn became greener after applying fertilizer.)  In these studies, information about groups or areas is examined in detail.  These studies are often <em>retrospective</em>, meaning they analyze data about something that already happened.  Observational studies sometimes compare their group/area to another group/area, but this comparison is less rigorous than a controlled experiment.  (A friend who does not use fertilizer says she has not seen her lawn become greener this month.  But, the fertilizer is not the only difference between your two lawns.  Her soil type or shade coverage may be different.)  Observational studies can't draw as strong a conclusion as an experimental study, but they can be a valuable first step to provide background for designing an experiment, and their conclusions become stronger when repeated, especially in various contexts. </p>
</div>

<img src="images/evidence_catintotrap.jpg" width="492" height="250" hspace="10" vspace="10" align="right" />

<%= NoteField.buildHtmlDiv("0bf6f76b-0ef2-4358-8ecc-c19f13843de4", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-0bf6f76b-0ef2-4358-8ecc-c19f13843de4">
<p>
<strong>Modeling study</strong>: Mathematical modeling is used to make predictions about an outcome that is unknown by creating simulations based on information that is known.  You probably look at one form of modeling every day: the weather forecast.  Like weather forecasts, sometimes research models are excellent at making correct predictions, and sometimes they are not.  Models get better with the addition of more and context-specific field data.  Models give us an opportunity to simulate a wide range of events and make informed estimates about what could happen, allowing researchers to test far more ideas than would be practical or affordable to do in the real world.</p>
<p><strong>Significant</strong>: In everyday usage, “significant” is used to suggest “important.”  In research, significance is a quantifiable measure telling the researcher that they can be fairly certain that the results are real and not the product of random chance.  It is often defined as when a statistical test has a p-value under 0.05.  The smaller a p-value is, the more certain we are.  This same approach can be used to distinguish whether two populations differ from each other, such as in testing whether your experimental and control groups' results are significantly different. </p>
<p><strong>Peer review</strong>: A quality control step where other researchers in the same field (“peers”) read and critique your research article before it can be published.  This process is meant to find errors in your methods or thinking, ask for more details, suggest alternative explanations for your findings, and even sometimes decide that a study is not good enough to be formally published in an academic research journal. </p>
</div>

<h2><a name="infographics" id="infographics"></a>Summary and infographics</h2>

<%= NoteField.buildHtmlDiv("0bf6f76b-0ef2-4358-8ecc-c19f13843de5", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-0bf6f76b-0ef2-4358-8ecc-c19f13843de5">
<p>If you search online for &quot;spay neuter evidence&quot; or &quot;TNR evidence,&quot; depending on the values  of the website you happen to click, you will be presented with only half of the picture.  By contrast, our comprehensive reading list contains research with a variety of conclusions about the use of spay/neuter programs for cat management.  We want you to make your own critiques and conclusions to better understand the complexity of research on this important topic. </p>
<p>We also want to highlight that there is not yet published research doing what Kitizen Science was founded to do: conduct <em>controlled field experiments</em> over the <em>long term</em> about the impact of spay/neuter programs on free-roaming cat <em>population sizes</em>.  Some studies have some of these pieces, but no one has yet put them all together yet. </p>
<p>Here is a quick graphical summary of evidence about the impact of spay/neuter programs in North America, with American studies mapped:</p>
</div>

<p align="center"><img src="images/evidence_map.gif" width="600" height="490" hspace="10" vspace="10" align="middle" /></p>
<p align="center"><img src="images/evidence_piecharts.gif" width="800" height="470" hspace="10" vspace="10" align="bottom" /></p>
<p align="center">&nbsp;</p>
<h2><a name="library" id="library2"></a>Research library</h2>
<%= NoteField.buildHtmlDiv("142a335d-addc-4b04-9d0b-b60a30011a89", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-142a335d-addc-4b04-9d0b-b60a30011a89">
<p>All article summaries below list the title, year published, author(s), location, time span, study design, metric(s) of interest, sample size, and their DOI (document identification number) or another identifier so you can find these articles online. These are listed starting with the most recent publications.</p>
<p><strong>﻿Back to School: An Updated Evaluation of the Effectiveness of a Long-Term Trap-Neuter-Return Program on a University’s Free-Roaming Cat Population (2019)</strong></p>
<p><em>Authors: Daniel D. Spehar, Peter J. Wolf <br />
Location: Orlando, FL<br />
Time span: 28 years<br />
Study design:  Observational<br />
Metrics of interest: Population size<br />
Sample size: 204 cats<br />
DOI: </em><em>10.3390/ani9100768</em></p>
<p>This article follows up on a 2003 study (Evaluation of the effect of a long-term trap-neuter-return and adoption program on a free-roaming cat population) by Levy et al. which examined a long-term TNR program on a large university campus.  This new article extends the timeline to 28 years, the longest period of any study of a trap-neuter-return program.  As with the Levy et al. (2003) study, the data for this article is drawn from colony caretakers' records and interviews from those managing the cats.  In 2019, only 10 cats remained on the campus, down from 23 cats at the end of the previous case study, despite the campus's human population more than doubling in size.</p>
<p>* </p>
<p><strong>﻿﻿Implementing Nonlethal Solutions for Free-Roaming Cat Management in a County in the Southeastern United States (2019)</strong></p>
<p><em>Author: Francis Hamilton<br />
Location: ﻿Hillsborough County, FL<br />
Time span: 15 years<br />
Study design: Observational<br />
Metrics of interest: Shelter intake, shelter euthanasia, shelter live release/save rate<br />
Sample size: 128,000 cats<br />
DOI: </em><em>﻿10.3389/fvets.2019.00259</em></p>
<p>This study follows how multiple entities offered three different nonlethal cat control options in a county that started with a shelter cat euthanasia rate of over 90%.  These programs included spay/neuter vouchers for cats in low-income households, a trap-neuter-vaccinate-return program for free-roaming cats, and a return-to-field sterilization program for free-roaming cats.  From 2002-2017, as the number of cats sterilized through these programs increased, shelter intake decreased and the live release rate increased, even though the local human population increased.  The article also records the histories of organizations involved in this process, conflicts, and hurdles faced by those working to implement changes.</p>
<p>* </p>
<p><strong>A Long-Term Lens: Cumulative Impacts of Free-Roaming Cat Management Strategy and Intensity on Preventable Cat Mortalities (2019)  </strong></p>
<p><em>Authors: John D. Boone, Philip S. Miller, Joyce R. Briggs, Valerie A. W. Benka, Dennis F. Lawler, Margaret Slater, Julie K. Levy, Stephen Zawistowski <br />
  Study design: Modeling <br />
  Metrics of interest: Population size, cat welfare <br />
  DOI: 10.3389/fvets.2019.00238 </em></p>
<p>Here, researchers updated their previous 2014 model comparing cat management options for reducing both cat population sizes and the preventable deaths of cats.  The management scenarios compared were doing nothing, low-intensity removal, high-intensity removal, low-intensity culling, high-intensity culling, low-intensity sterilization, and high-intensity sterilization.  Model parameters included cat abandonment and age-specific fecundity, survival, and dispersal, and models were repeated with initial population sizes of 50-5000 to test scalability.  All options performed better than doing nothing, with high-intensity sterilization reducing cat deaths the most, and high intensity cat removal reducing cat populations the most.</p>
<p>* </p>
<p><strong>Decrease in Population and Increase in Welfare of Community Cats in a Twenty-Three Year Trap-Neuter-Return Program in Key Largo, FL: The ORCAT Program (2019)  </strong></p>
<p><em>Authors: Rachael E. Kreisler, Heather N. Cornell, Julie K. Levy <br />
  Location: Key Largo, FL <br />
  Time span: 14 years <br />
  Study design: Observational <br />
  Metrics of interest: Population size, cat welfare <br />
  Sample size: 455 cats <br />
  DOI: 10.3389/fvets.2019.00007 </em></p>
<p>This study examined a TNR program on an isolated peninsula that operated over the course of 23 years.  A key purpose of the study was to provide a long-term look at TNR.  Most of the data reported in the study pertains to metrics of cat health and welfare, and from 1999-2013, cat census data were also collected, with the census being performed by a single caretaker during 10 different counts.  The cat population of the area went from 455 to 206, showing a 55% decrease.  Authors conclude that cat welfare increased over time, as the average age of cats increased and prevalence of viral diseases decreased. </p>
<p>*</p>
<p><strong>Integrated Return-To-Field and Targeted Trap-Neuter-Vaccinate-Return Programs Result in Reductions of Feline Intake and Euthanasia at Six Municipal Animal Shelters (2019)  </strong></p>
<p><em>Authors: Daniel D. Spehar, Peter J. Wolf <br />
  Locations: Albuquerque, NM; San Antonio, TX; Baltimore, MD; Philadelphia, PA; Tucson, AZ; Columbus, GA <br />
  Time span: 3 years <br />
  Study design: Observational <br />
  Metrics of interest: Shelter intake, shelter euthanasia, shelter live release/save rate, cat welfare <br />
  Sample size: 72,970 cats <br />
  DOI: 10.3389/fvets.2019.00077 </em></p>
<p>Here, authors studied cat data from six locations with community cat programs which combined targeted TNVR with return-to-field approaches.  They compared shelter metrics from before programs launched to the same metrics after 3 years.  Cat euthanasia declined by 59-91% (median 83%), intake by 1-45% (median 32%), and live release rates by 17-168% (median 53%).  Some shelters experienced an increase in adoptions during the study period, some saw a decline.  Data for cats dead on arrival, sometimes used as a proxy for cat numbers in an area, varied by location and were noted to be difficult to compare based on different areas' methods of tracking this information. </p>
<p>*</p>
<p><strong>A case study in citizen science: The effectiveness of a trap-neuter-return program in a Chicago neighborhood (2018)  </strong></p>
<p><em>Authors: Daniel D. Spehar, Peter J. Wolf <br />
  Location: Chicago, IL <br />
  Time span: 10 years <br />
  Study design: Observational <br />
  Metric of interest: Population size <br />
  Sample size: 195 cats <br />
  DOI: 10.3390/ani8010014 </em></p>
<p>This publication presents a neighborhood-scale TNR program and how it impacted the size of the cat population.  The project covered an initial 20 cat colonies over 9.3 square kilometers in an area of single and double family homes with small apartment buildings in a major city.  The project leader kept records of each cat, conducted a census each year, and interviewed neighbors and colony caretakers.  Analyzing this data showed that a population of 195 cats decreased to 44 cats remaining on the landscape, with a mean size reduction of 54% across colonies, and eight colonies eliminated entirely.  Shelter data covering other local TNR efforts demonstrate that reductions in cat intake were seen city-wide. </p>
<p>*</p>
<p><strong>Impact of a trap-neuter-return event on the size of free-roaming cat colonies around barns and stables in Quebec: A randomized controlled trial (2018) </strong></p>
<p><em>Authors: Valérie Bissonnette, Bertrand Lussier, Béatrice Doizé, Julie Arsenault <br />
  Location: Quebec, Canada <br />
  Time span: 1 year <br />
  Study design: Experimental <br />
  Metric of interest: Population size <br />
  Sample size: Population size <br />
  PMID: 30026643 </em></p>
<p>Here, Canadian researchers sought to test the impacts of a one-time TNR intervention on cat colonies in barns and stables near Saint-Hyacinthe, Quebec.  The authors note that no field studies of TNR had been conducted in a cold climate and sought to compare their results to studies from warm and temperate climates.  Cat numbers in the treatment and control colonies were first assessed using camera traps, then the TNR colonies were subjected to trappings and sterilization, with an average of 92% of cats per treated colony being sterilized.  Cat population sizes were assessed again at 7 and 12 months, and no statistically significant differences between sterilized versus control colonies were found. </p>
<p>*</p>
<p><strong>The Impact of an Integrated Program of Return-to-Field and Targeted Trap-Neuter-Return on Feline Intake and Euthanasia at a Municipal Animal Shelter (2018)  </strong></p>
<p><em>Authors: Daniel D. Spehar, Peter J. Wolf <br />
  Location: Albuquerque, NM <br />
  Time span: 3 years <br />
  Study design: Observational <br />
  Metrics of interest: Shelter intake, shelter euthanasia, shelter live release/save rate <br />
  Sample size: 11,746 cats <br />
  DOI: 10.3390/ani8040055 </em></p>
<p>This study investigated a three-year time period during a TNR and return-to-field program operated out of two open admission animal shelters and in cooperation with other cat rescues and nonprofits.  During the study period, 11,746 cats were enrolled, 91% of which were returned to colonies.  After the three-year program, the shelters decreased their cat euthanasia rate by 74.4% and kitten euthanasia by 81.8% and increased their live release rate by 47.7%.  Authors note that a local animal welfare organization reported a 62% decline in cat intake in targeted TNR areas compared to an 8% decrease in intake in non-target areas. </p>
<p>*</p>
<p><strong>An Examination of an Iconic Trap-Neuter-Return Program: The Newburyport, Massachusetts Case Study (2017)  </strong></p>
<p><em>Authors: Daniel D. Spehar, Peter J. Wolf <br />
  Location: Newburyport, MA <br />
  Time span: 17 years <br />
  Study design: Observational <br />
  Metric of interest: Population size <br />
  Sample size: 300 cats <br />
  DOI: 10.3390/ani7110081 </em></p>
<p>This qualitative article documents the history of an early TNR program based on interviews and remaining records from 1992 until the final cat passed away in 2009.  This historical case study begins with the inception of the program in a mixed-use waterfront neighborhood (about 1.9 by 0.4 kilometers in size) which was reported to have an overabundance of cats.  One recollection estimated 300-400 cats in the area, but another pair of trappers conducted a census and put the number at 300.  Upon capture, about a third of the cats were sociable enough to be adopted, and the remaining cats were sterilized and returned, with a small number euthanized or relocated.  The final cat died at an estimated 16 years of age. </p>
<p>*</p>
<p><strong>Estimating free-roaming cat populations and the effects of one-year Trap-Neuter-Return management effort in a highly urban area (2016)  </strong></p>
<p><em>Authors: R. J. Kilgour, S. B. Magle, M. Slater, A. Christian, E. Weiss, M. DiTullio <br />
  Location: New York, NY <br />
  Time span: 1 year <br />
  Study design: Experimental <br />
  Metric of interest: Population size<br /> 
  Sample size: 185 cats <br />
  DOI: 10.1007/s11252-016-0583-8 </em></p>
<p>This article details a controlled experiment to test the efficacy of TNR in a dense urban area.  Photographic mark-recapture transect routes were planned based on similarity of urban features (commercial and residential areas).  The TNR effort involved sterilizing (or releasing as already sterilized) 125 cats in Bedford-Stuyvesant and 60 in Harlem, and population estimates were made before the program and one year later.  Cat population estimates were between 30.3 and 52.5 per area, and researchers did not find a significant difference between years, except for the Bedford-Stuyvesant TNR area increasing.  Authors propose that they may have been underestimating cat populations, noting they did not conduct their population surveys during times when cats are known to be active. </p>
<p>*</p>
<p><strong>A spatial agent-based model of feral cats and analysis of population and nuisance controls (2016)  </strong></p>
<p><em>Authors: Timothy Ireland &amp; Rachael Miller Neiland <br />
  Study design: Modeling <br />
  Metrics of interest: Population size, cat behavior <br />
  DOI: 10.1016/j.ecolmodel.2016.06.014 </em></p>
<p>This paper's purpose was to expand a model by McCarthy, Levine, and Reed (2013) comparing TNR and trap-vasectomy-hysterectomy-return (TVHR).  The authors also sought to measure the nuisance behaviors that remain when a cat has a vasectomy or hysterectomy instead of a standard neuter or spay.  Model parameters included age classes, carrying capacities, survival, female hormonal cycles, litter sizes, connectivity, weaning, seasonality and more.  This model confirmed that TVHR is more effective than TNR at reducing cat populations.  However, TNR is more effective at reducing nuisance behaviors.  Fertility control measures were most effective when applied in March and April. </p>
<p>*</p>
<p><strong>Association between a shelter-neuter-return program and cat health at a large municipal animal shelter (2016)  </strong></p>
<p><em>Authors:  Charlotte H. Edinboro, Heather N. Watson, Anne Fairbrother <br />
  Location: San Jose, CA <br />
  Time span: 8 years <br />
  Study design: Observational <br />
  Metrics of interest: Shelter intake, cat welfare <br />
  Sample size: 75,535 cats <br />
  DOI: 10.2460/javma.248.3.298  </em></p>
<p>Here, authors analyzed data from a large animal shelter to examine the effects of a shelter-neuter-return program on cat admissions and euthanasia, as well as tracking URI cases and changes in treatment over the same period.  After removing records for cat cases that didn't involve admission (outpatient clinic use and cat complaint calls), researchers included records from 75,535 live cats in analysis.  Comparing data from before and after instituting the shelter-neuter-return program, cats received by the shelter as dead decreased, live admissions decreased, and euthanasia decreased.  Authors suggest that decreased admissions made available resources that allowed them to treat more URI cases in the animal shelter. </p>
<p>*</p>
<p><strong>Simulating free-roaming cat population management options in open demographic environments (2014)  </strong></p>
<p><em>Authors: Philip S. Miller, John D. Boone,  Joyce R. Briggs, Dennis F. Lawler, Julie K. Levy, Felicia B. Nutter, Margaret Slater, Stephen Zawistowski <br />
  Study design: Modeling <br />
  Metric of interest: Population size <br />
  DOI: 10.1371/journal.pone.0113553  </em></p>
<p>In this article, authors built models of trap-neuter-return, trap-remove (euthanize), and contraception for reducing free-roaming cat populations.  In a rebuttal to similar previous studies, this model included density-dependent kitten mortality and looked at open and closed populations of cats in an attempt to be more biologically accurate.  Model parameters included cat abandonment and age-specific fecundity, survival, and dispersal, and comparisons between large urban, small urban, and rural environments.  Authors estimated that every 6 months, one would need to TNR 40% of intact cats to have the same population decline as if you removed 30% of cats.  Both contraception options were less effective in the long term. </p>
<p>*</p>
<p><strong>Effect of high-impact targeted trap-neuter-return and adoption of community cats on cat intake to a shelter (2014)  </strong></p>
<p><em>Authors: Julie K. Levy, N. M. Isaza, Karen C. Scott <br />
  Location: Alachua County, FL <br />
  Time span: 2 years <br />
  Study design: Observational <br />
  Metrics of interest: Shelter intake, shelter euthanasia <br />
  Sample size: 2,366 cats <br />
  DOI: 10.1016/j.tvjl.2014.05.001 </em></p>
<p>This publication tracked shelter cat euthanasia and intake before and after the implementation of a program to sterilize 50% of the estimated community cat population in a target zip code.  The target area was described as lower middle class and had historically high impoundments of cats compared to the rest of the county.  The study estimated the zip code's population of community cats by conducting a survey asking residents about cat feeding and used these numbers to roughly estimate about 4383 cats in the area.  After the two-year study period, shelter intake decreased by 66% in the target area (compared to 12% in non-target areas), and euthanasia was 17.5-fold higher in surrounding areas than the target area. </p>
<p>*</p>
<p><strong>Study of the effect on shelter cat intakes and euthanasia from a shelter neuter return project of 10,080 cats from March 2010 to June 2014 (2014)  </strong></p>
<p><em>Authors: Karen L. Johnson &amp; Jon Cicirelli <br />
  Location: San Jose, CA <br />
  Time span: 4 years <br />
  Study design: Observational <br />
  Metrics of interest: Shelter intake, shelter euthanasia, shelter live release/save rate <br />
  Sample size: 10,080 cats <br />
  DOI: 10.7717/peerj.646 </em></p>
<p>This study, covering 2010 to 2014, was a follow-up to a 2013 article by Kass, Johnson, and Wang that used similar data from earlier years.  This new article sought to continue following trends in cat impoundment and euthanasia in the region after the institution of a shelter-neuter-return program to sterilize free-roaming cats.  Cat intakes from five shelters continued an overall downward trend apart from an increase in 2009, which authors suspect was due to the economic recession.  Dead cats picked up declined by 20%, impoundment decreased by 29.1%, euthanasia decreased from 70% to 23% of intakes, and the save rate increased from 29.1% to 76.7%.  Authors conclude that this produced an overall cost savings to the shelters. </p>
<p>*</p>
<p><strong>Estimation of effectiveness of three methods of feral cat population control by use of a simulation model (2013)  </strong></p>
<p><em>Authors: Robert J. McCarthy, Stephen H. Levine, J. Michael Reed <br />
  Study design: Modeling <br />
  Metric of interest: Population size <br />
  DOI: 10.2460/javma.243.4.502  </em></p>
<p>Here, researchers built a model of three methods for reducing feral cat populations: lethal control, traditional TNR, and trap-vasectomy-hysterectomy-release (TVHR).  Model parameters included reproduction, mating systems, pregnancy duration, weaning, hormonal cycling, seasonality, pseudopregnancy, litter sizes, and more.  At low treatment rates, all methods were ineffective, and at very high treatment rates, all methods could extinguish the cat population.  TVHR, which leaves cats hormonally/behaviorally intact, was more effective than traditional TNR at all treatment levels.  This is attributed in part to neutering and spaying increasing cat lifespans.  Authors note the downside of vasectomy and hysterectomy: fighting, vocalizing, and mating nuisance behaviors would persist. </p>
<p>*</p>
<p><strong>Evaluation of animal control measures on pet demographics in Santa Clara County, California, 1993–2006 (2013)  </strong></p>
<p><em>Authors: Philip H. Kass, Karen L. Johnson, Hsin-Yi Wang <br />
  Location: Santa Clara, CA <br />
  Time span: 13 years <br />
  Study design: Observational <br />
  Metrics of interest: Shelter intake, costs, pet cats <br />
  Sample size: 26,650 cats, survey of 1,000 households <br />
  DOI: 10.7717/peerj.18  </em></p>
<p>Combining surveys in 1993 and 2005 with shelter data, authors explored trends in pets and shelters.  Over 12 years, more pet cats were being kept indoors, more pet cats were sterilized, fewer people reported feeding a smaller number of stray cats, and shelter cat intake decreased.  They estimated that the county had approximately 169,000 unowned fed cats in 1993 and 135,000 in 2005 based on extrapolations from survey data asking people whether they fed stray cats and how many.  Authors used shelter data prior to the low-cost spay/neuter program to compare projected shelter intake with actual intake, concluding that cat intake would have been higher without the program, which saved the county approximately $1.5 million. </p>
<p>*</p>
<p><strong>Costs and Benefits of Trap-Neuter-Release and Euthanasia for Removal of Urban Cats in Oahu, Hawaii (2012)  </strong></p>
<p><em>Authors:  Cheryl A. Lohr, Linda J. Cox,  Christopher A. Lepczyk <br />
Location: Model / Hawaii <br />
Study design: Modeling <br />
Metrics of interest: Population size, costs, impacts on birds/wildlife <br />
DOI: 10.1111/j.1523-1739.2012.01935.x </em></p>
<p>This article details simulations of trap-neuter-release and trap-euthanize programs to test how long it would take for a free-roaming cat population to become extinct under each, and which is least expensive.  Included model parameters were cat abandonment, reproduction, survival, labor, equipment, food, veterinary care, euthanasia costs, and the dollar value of reducing cat predation upon birds (with birds' lives valued between $1-15,000).  Authors conclude that trap-euthanize programs are more cost effective and more likely to result in extirpation of cat populations, and that trap-neuter-release programs are only more cost effective with a population size under 1670 cats. </p>
<p>*</p>
<p><strong>Impact of a Subsidized Spay Neuter Clinic on Impoundments and Euthanasia in a Community Shelter and on Service and Complaint Calls to Animal Control (2012)  </strong></p>
<p><em>Authors: Janet Scarlett &amp; Naomi Johnston <br />
Location: Transylvania County, North Carolina <br />
Time span: 4 years <br />
Study design: Observational <br />
Metrics of interest: Shelter intake, shelter euthanasia, nuisance complaints <br />
Sample size: 2,259 cats <br />
DOI: 10.1080/10888705.2012.624902 </em></p>
<p>This study monitored the 4 years after the start of a pay-what-you-can spay/neuter program for dogs and cats to learn whether this program changed rates of impoundment, euthanasia, and nuisance complaints.  (This study focused on pet cats, so what if any proportion of cats presented to the spay/neuter clinic were free-roaming or feral is unknown.)  Compared to data from the 4 years before the program, cat impoundment and euthanasia at the shelter decreased after the program was implemented.  Authors emphasize the challenges of using shelter data to draw conclusions due to issues with both types and quality of data, noting a large error they discovered in the shelter's statistics. </p>
<p>*</p>
<p><strong>Impact of Publicly Sponsored Neutering Programs on Animal Population Dynamics at Animal Shelters: The New Hampshire and Austin Experiences (2010)  </strong></p>
<p><em>Authors: Sara C. White, Ellen Jefferson, Julie K. Levy <br />
Locations: New Hampshire; Austin, TX <br />
Time span: 11 years and 7 years <br />
Study design: Observational <br />
Metrics of interest: Shelter intake, shelter euthanasia <br />
Sample size: 50,462 and 16,097 surgeries <br />
DOI: 10.1080/10888700903579903 </em></p>
<p>This article examines two models for delivering low-cost spay/neuter services for cats and dogs.  The New Hampshire program covered sterilizing animals in shelters before adoption and low-cost surgeries to low-income pet owners, and the Texas program offered affordable surgeries in zip codes targeted for having higher shelter intake, lower household incomes, and fewer veterinary clinics than elsewhere in the city.  In New Hampshire, cat intake and euthanasia declined after the start of their program.  In Texas, cat intake and euthanasia increased in both target and non-target zip codes, but the rate of increase was lower in the program's target areas. </p>
<p>*</p>
<p><strong>Effects of sterilization on movements of feral cats at a wildland-urban interface (2010)  </strong></p>
<p><em>Authors: Darcee A. Guittilla &amp; Paul Stapp <br />
Location: Catalina Island, CA <br />
Time span: 1 year, 8 months <br />
Study design: Experimental <br />
Metric of interest: Cat behavior <br />
Sample size: 27 cats <br />
DOI: 10.1644/09-MAMM-A-111.1 </em></p>
<p>This study tested whether sterilization has an impact on the activity and behaviors of free-roaming cats on a small island with a TNR program.  27 cats were captured and fitted with tracking collars to test whether sterilized cats wander less than reproductively intact cats, and home ranges and movement patterns were analyzed.  Authors found no difference between the home range size of intact versus sterilized cats, which contradicts the belief that spaying and neutering cats reduces wandering.  Efforts were also made to estimate the number of free-roaming cats on the island, and the authors placed that number at 600-750 animals. </p>
<p>*</p>
<p><strong>An Evaluation of Feral Cat Management Options Using a Decision Analysis Network (2010)  </strong></p>
<p><em>Authors: Kerrie Anne T. Loyd &amp; Jayna L. DeVore <br />
Study design: Modeling <br />
Metrics of interest: Population size, costs <br />
DOI: 10.5751/ES-03558-150410 </em></p>
<p>Here, researchers used a Bayesian Belief Network model to compare cat control methods: doing nothing, euthanizing cats, TNR, TNR plus removing kittens, or TNR with disease testing, vaccination, and post-release monitoring.  The model parameters sought to account for stakeholder preferences, initial and future cat population sizes, costs, cat predation upon wildlife, and lethality to cats.  The authors found that both euthanizing cats and doing nothing had low costs, followed by TNR with kitten removal at moderate costs, then other TNR variants at moderate/high costs.  They suggest that TNR could be used for managing small populations of 50 or fewer cats, but that euthanasia is best for larger cat populations. </p>
<p>*</p>
<p><strong>Utilization of Matrix Population Models to Assess a 3-Year Single Treatment Nonsurgical Contraception Program Versus Surgical Sterilization in Feral Cat Populations (2009)  </strong></p>
<p><em>Authors: Christine M. Budke &amp; Margaret R. Slater <br />
Study design: Modeling <br />
Metric of interest: Population size <br />
DOI: 10.1080/10888700903163419 </em></p>
<p>This simulation compared cat population growth under the conditions of doing nothing, using TNR, or treating cats with a three-year hypothetical contraceptive.  Simulating a closed population of 100 female cats with no carrying capacity, model parameters included a range of juvenile and adult fecundity and survival values.  Authors found that ceasing cat population growth would require 51% of adult and juvenile female cats to be sterilized annually, or for 60% of casts to be treated with a three-year contraceptive.  They note that although there would need to be a high sterilization rate in early years, as the population stabilizes, fewer need sterilization each year. </p>
<p>*</p>
<p><strong>Evaluation of euthanasia and trap–neuter–return (TNR) programs in managing free-roaming cat populations (2009)  </strong></p>
<p><em>Authors: Paige M. Schmidt, Todd M. Swannack, Roel R. Lopez, Margaret R. Slater <br />
Location: Model / Texas <br />
Study design: Modeling<br />
Metric of interest: Population size <br />
DOI: 10.1071/WR08018 </em></p>
<p>This paper examines TNR, euthanasia, and combinations of TNR and euthanasia for reducing free-roaming cat populations in Texas.  Rather than aggregating general cat vital statistics, these researchers used data specific to their region from a study of 43 real free-roaming cats of both sexes.  The model parameters included cat mortality and natality, density-dependence, and immigration.  Population decreases were comparable among methods assuming no immigration, but at 25% and 50% levels of immigration, euthanasia caused greater decreases in cat populations.  Authors emphasize the importance of reducing abandonment of unwanted pets and low-cost spay/neuter programs for pet cats. </p>
<p>*</p>
<p><strong>Analysis of programs to reduce overpopulation of companion animals: Do adoption and low-cost spay/neuter programs merely cause substitution of sources? (2007)  </strong></p>
<p><em>Authors: Joshua M. Frank &amp; Pamela L. Carlisle-Frank <br />
Locations: Utah; Alabama; Maricopa County, AZ; Lodi, CA; Alachua County, FL <br />
Time span: 3-5 years <br />
Study design: Observational <br />
Metrics of interest: Shelter intake, shelter adoptions, impacts on veterinarians <br />
Sample size: Five programs <br />
DOI: 10.1016/j.ecolecon.2006.09.011</em></p>
<p>Here, authors tested three beliefs about animal adoption and discount spay/neuter: whether they substitute surgery sources by reducing spay/neuter by private veterinarians, whether discount spay/neuter reduces shelter intake, and whether adoptions from small/private &quot;no kill&quot; organizations reduce adoptions from animal control.  Authors found that discount and regular spay/neuter have a positive relationship (they both increased over time), adoptions from small/private animal rescues and animal control were not significantly related, and discount spay/neuter programs and shelter intake were also not significantly related. </p>
<p>*</p>
<p><strong>Analysis of the impact of trap-neuter-return programs on populations of feral cats (2005)  </strong></p>
<p><em>Authors: Patrick Foley, Janet E. Foley, Julie K. Levy, Terry Paik <br />
Study design: Modeling <br />
Metric of interest: Population size <br />
DOI: 10.2460/javma.2005.227.1775 </em></p>
<p>This publication investigated data from TNR programs in Florida and California, along with surveys of people who feed free-roaming cats, to create a model of the &quot;critical neutering rate&quot; of cats that would need to occur in order to see a population decrease.  Model parameters included were cat fecundity, initial population sizes, and carrying capacity.  Using an estimated cat lifespan of 5 years, the model estimated that 71% of cats in California would need to be sterilized, and 94% in Florida, or an annual neutering fraction of 15% for California and 19% for Florida.  The authors conclude that such proportions given by the model are &quot;unrealistic&quot; to achieve, but also cite studies where a population reduction was achieved with less intensive sterilization. </p>
<p>*</p>
<p><strong>Use of matrix population models to estimate the efficacy of euthanasia versus trap-neuter-return for management of free-roaming cats (2004)  </strong></p>
<p><em>Authors: Mark C. Andersen, Brent J. Martin, Gary W. Roemer <br />
Study design: Modeling <br />
Metric of interest: Population size <br />
DOI: 10.2460/javma.2004.225.1871 </em></p>
<p>In this study, authors present models comparing trap-neuter-return and trap-euthanize for free-roaming cat management.  Using published values for model parameters of cat fecundity and survival for juveniles and adults, researchers found that a 75% sterilization rate in female cats would result in a lambda value of 1.08 (population still slightly increasing), but a 75% reduction in survival via euthanasia would result in a lambda of 0.47 (cat population decreasing by half annually).  The study emphasizes that controlling cats via targeting their survival is more effective than methods targeting their fecundity.  Authors note that adoptions of stray cats from the free-roaming cat pool function similarly to euthanasia for reducing the population. </p>
<p>*</p>
<p><strong>Trap/Neuter/Release Methods Ineffective in Controlling Domestic Cat &quot;Colonies&quot; on Public Lands (2003) </strong></p>
<p><em>Authors: Daniel Castillo &amp; Alice L. Clarke <br />
  Location: Miami-Dade County, FL <br />
  Time span: 1 year <br />
  Study design: Observational <br />
  Metric of interest: Population size <br />
  Sample size: 128 cats <br />
DOI: https://www.jstor.org/stable/43912243 </em></p>
<p>This paper examines cat advocates' claim that managed colonies of free-roaming cats would decrease over time.  Researchers monitored parks with cat colonies for approximately one year and used capture-recapture methods to monitor the populations.  Authors conclude that managed cat colonies don't decrease in size over time, with one park's cat population increasing slightly and the other not significantly changing.  Authors note that while some original members of the cat colonies decreased over time, dumping of new cats at the sites kept the cat numbers the same or increasing.  Authors do not define what a &quot;managed&quot; colony means for the purpose of this study or what proportion of cats in each place were sterilized. </p>
<p>*</p>
<p><strong>Evaluation of the effect of a long-term trap-neuter-return and adoption program on a free-roaming cat population (2003)  </strong></p>
<p><em>Authors: Julie K. Levy, David W. Gale, Leslie A. Gale <br />
Location: Orlando, FL <br />
Time span: 11 years <br />
Study design: Observational <br />
Metric of interest: Population size <br />
Sample size: 155 cats <br />
DOI: 10.2460/javma.2003.222.42 </em></p>
<p>In this article, researchers studied the population dynamics of a TNR and adoption program based in a large university campus.  Launched in 1991, the program became more structured by 1996 when all campus cats had been identified, photographed, and tracked according to their socialization level, colony, and other information.  Overall, 155 cats were part of this program, with a peak campus cat population size of 68 in 1996.  Prior to detailed records, observers are reported to have estimated the cat population at 120.  Over 11 years, colony sizes decreased from 3-25 cats to 1-5 cats.  The cat population was 23 at the end of the reporting period, a 66% reduction from the peak known population. </p>
<p>*</p>
<p><strong>Characteristics of free-roaming cats and their caretakers (2002)  </strong></p>
<p><em>Authors: Lisa A. Centonze &amp; Julie K. Levy <br />
Location: Florida <br />
Time span: 15 years <br />
Study design: Observational <br />
Metrics of interest: Population size, cat welfare, cat caretakers <br />
Sample size: 920 cats <br />
DOI: 10.2460/javma.2002.220.1627 </em></p>
<p>This publication surveyed clients of a TNR clinic to gather information about the size of cat colonies being cared for, characteristics of the cats, and demographics of the caretakers themselves.  Researchers received 101 responses which together covered 132 colonies of cats.  Caretakers reported caring for a total of 920 cats before they became involved in the TNR program, and 678 cats after starting the TNR program, with a 2 week to 15-year time span covered between.  Authors note a 27% decrease in mean colony size during this time, but also state that the numbers in the paper don't add up and can't be considered accurate, highlighting the problem of relying on the memories of respondents. </p>
<p>*</p>
<p><strong>Body Condition of Feral Cats and the Effect of Neutering (2002)  </strong></p>
<p><em>Authors: Karen C. Scott, Julie K. Levy,  Shawn P. Gorman, Susan M. Newell <br />
Location: Florida <br />
Time span: 1 year <br />
Study design: Experimental <br />
Metric of interest: Cat welfare <br />
Sample size: 14 cats <br />
DOI: 10.1207/S15327604JAWS0503_04  </em></p>
<p>Here, authors tracked whether spaying and neutering feral cats resulted in an increased weight, body condition score, and fat pad amount.  Of 105 feral cats brought to a sterilization clinic, 14 were able to be recaptured a year later and have body measurements repeated.  Of these cats, there was a mean weight increase of 40% and a one-point higher body condition score compared to at the time of surgery.  The cats subjected to follow-up measurements were sampled based on caretakers that could be contacted and were willing to re-trap cats and take a questionnaire.  Caretakers reported that these cats were friendlier, roamed less, and healthier after surgery. </p>
<p>*</p>
<p><strong>The Effects of Implementing a Feral Cat Spay/Neuter Program in a Florida County Animal Control Service (2002)  </strong></p>
<p><em>Authors: Kathy L. Hughes, Margaret R. Slater, Linda Haller <br />
Location: Orange County, FL <br />
Time span: 6 years <br />
Study design: Observational <br />
Metrics of interest: Shelter intake, shelter euthanasia, nuisance complaints, costs <br />
Sample size: 7,903 cats <br />
DOI: 10.1207/S15327604JAWS0504 </em></p>
<p>This article examined changes in shelter statistics for cat impoundment, euthanasia, complaints, and the costs of sterilization versus euthanasia after 6 years of a feral cat sterilization program.  Compared to prior to the program, cat impoundment numbers stayed the same (although the country's human population grew), euthanasia decreased by 18%, complaints decreased by 25%, and the county was estimated to have saved $655,949 because sterilizing a cat was cheaper than holding it prior to euthanasia.  Decreases in dog impoundment, euthanasia, and complaints were also seen during this time, and authors note that it's not possible to know how much of the cat decreases were attributable to the feral cat program. </p>
<p>*</p>
<p><strong>Implementation of a Feral Cat Management Program on a University Campus (2002)  </strong></p>
<p><em>Authors: Kathy L. Hughes, Margaret R. Slater <br />
  Location: College Station, TX <br />
Time span: 2 years <br />
Study design: Observational <br />
Metrics of interest: Nuisance complaints, cat welfare <br />
Sample size: 158 cats <br />
DOI: 10.1207/S15327604JAWS0501 </em></p>
<p>This study is one of a number of published accounts of descriptive statistics about a single spay/neuter program without having a research question or an explicit aim to document an impact.  However, this article covers a two-year period, and the decrease in the number of kitten and juvenile cats captured during year two could be seen as a proxy for success in reducing cat reproduction.  Cat recaptured for re-vaccination in year two showed a mean weight gain of 0.3kg from year one, suggesting that sterilization may have led to a healthier body condition.  Authors also note that cat complaints on campus decreased after the program was implemented.</p>
<p>*</p>
<p><strong>Domestic Cat &quot;Colonies&quot; in Natural Areas: A Growing Exotic Species Threat (2002) </strong></p>
<p><em>Authors: Alice L. Clarke &amp; Teresa Pacin <br />
Location: Florida <br />
Time span: 10 months and 4 years <br />
Study design: Observational <br />
Metric of interest: Population size <br />
Sample size: 2009 cats, &quot;more than 1000&quot; cats <br />
DOI: https://www.jstor.org/stable/43912043 </em></p>
<p>Here, researchers reviewed the topic of free-roaming cats for land managers and briefly discuss two South Florida feline spay/neuter groups.  The Cat Network provided records for a total of 2009 cat surgeries occurring in a 10-month period.  The article notes that the group did not collect data on cat population sizes over time and could not conclude if the organization was reducing cat populations.  ORCAT, a group reported to have better funding and record-keeping, reported stated that when they began, their area had over 1000 free-roaming cats, and now had about 500 after 4 years of spay/neuter work. </p>
<p>*</p>
<p><strong>Neutering of feral cats as an alternative to eradication programs (1993)  </strong></p>
<p><em>Authors: Karl I. Zaunbrecher &amp; Richard E. Smith <br />
Location: Carville, LA <br />
Time span: 3 years <br />
Study design: Observational <br />
Metrics of interest: Population size, nuisance complaints <br />
Sample size: 44 cats <br />
PMID: 8226225 </em></p>
<p>These authors explored whether a spay/neuter program could stabilize the cat population at a medical facility in rural Louisiana and whether the program would reduce nuisance behaviors and colony turnover.  They conducted a cat census before starting the sterilization program, a year and a half later, and three years later.  The cat population went from 44 to 36 cats, with a low population turnover despite some new cats suspected to have been dumped at the site.  Researchers also state that the health of the cats appeared to have improved based on their body condition, and cat nuisance noises from mating disappeared. </p>
<p>*</p>
<p><strong>The Potential for the Control of Feral Cat Populations by Neutering (1986)  </strong></p>
<p><em>Authors: Richard E. Smith &amp; Simon M. Shane <br />
Location: Baton Rouge, LA <br />
Time span: Unclear, possibly 4 months <br />
Study design: Observational <br />
Metric of interest: Cat behavior <br />
Sample size: 8 cats </em></p>
<p>This article sought to replicate a 1984 study from the United Kingdom conducted by Neville and Remfry.  These researchers could not locate naturally-occurring feral cat colonies nearby and attempted to create colonies for the purpose of the study.  They obtained kittens from an animal control center, relocated them to rural areas, and fed them for a period of at least 6 weeks.  Some cats were then spayed and neutered and released again.  Many study cats were reported to have wandered away or gone missing, two unsterilized females gave birth to litters, and researchers conclude that their attempt to create artificial feral colonies for the study failed. </p>
</div>

</div>


<jsp:include page="footer.jsp" flush="true" />
