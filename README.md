
<h1><a href="http://www.ibeis.org"><img style="float: left;" align="middle" width="225px" height="225px" src="https://raw.githubusercontent.com/WildbookOrg/Wildbook/master/src/main/webapp/cust/mantamatcher/img/wildbook_logo.png"></a>Wildbook</h1>


 

Wildbook&reg; is an open source software framework to support mark-recapture, molecular ecology, and social ecology studies. The biological and statistical communities already support a number of excellent tools, such as Program MARK,GenAlEx, and SOCPROG for use in analyzing wildlife data. Wildbook is a complementary software application that:

-provides a scalable and collaborative platform for intelligent wildlife data storage and management, including advanced, consolidated searching

-provides an easy-to-use software suite of functionality that can be extended to meet the needs of wildlife projects, especially where individual identification is used

-provides an API to support the easy export of data to cross-disciplinary analysis applications (e.g., GenePop ) and other software (e.g., Google Earth)

-provides a platform that supports the exposure of data in biodiversity databases (e.g., GBIF and OBIS)

-provides a platform for animal biometrics that supports easy data access and facilitates matching application deployment for multiple species

<h2>Wildbook in IBEIS</h2>

<img width="125px" height="*" align="left" src="http://www.wildbook.org/lib/exe/fetch.php?w=200&tok=c557df&media=logo_400x4001.png" /> Wildbook is the data management layer for the <a href="http://www.ibeis.org">Image-Based Ecological Information System (IBEIS)</a>. IBEIS computer vision components pull data from Wildbook servers to detect features in images and identify individual animals. IBEIS brings massive-scale computer vision to wildlife research for the first time. 
<br />
<h2>Support</h2>

Please see <a href="https://www.wildbook.org">Wildbook.org</a> for documentation. 

Need direct help?

Wild Me (wildme.org) engineering staff provide support for Wildbook. You can contact us at: support@wildme.org

We provide support during regular office hours on Mondays and Tuesdays.

Support resources include:
<ul>
<li><a href="https://www.wildbook.org/doku.php?id=documentation">User Manual</a></li>
<li><a href="https://www.wildbook.org/doku.php?id=configuration">Configuration Guide</a></li>
<li><a href="https://www.wildbook.org/forum">Discussion Forum</a></li>
<li><a href="https://www.wildme.org/services">Wild Me Professional Services</a></li>
</ul>

<h2>History</h2>
Wildbook started as a collaborative software platform for globally-coordinated whale shark (Rhincodon typus ) research as deployed in the Wildbook for Whale Sharks (http://www.whaleshark.org). After many requests to use our software outside of whale shark research, it is now an open source, community-maintained standard for mark-recapture studies.


<p><img style="float: right;" align="middle" src="src/main/webapp/images/wild-me-logo-only-100-100.png"> Wildbook is a registered trademark of Wild Me, a 501(c)(3) non-profit organization.</p> https://www.wildme.org

<h1>Spotting Giant Sea Bass</h1>

<h2>Editing content</h2>

**Actual pages (jsp files)** are here: https://github.com/WildbookOrg/Wildbook/tree/bass/src/main/webapp

**Property files (most copy, and translations/localizations)** are here: https://github.com/WildbookOrg/Wildbook/tree/bass/src/main/resources/bundles

**Images** go here: https://github.com/WildbookOrg/Wildbook/tree/bass/src/main/webapp/cust/mantamatcher/img/bass

<ul>
 <li>Homepage background image ("hero image): https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/webapp/cust/mantamatcher/img/hero_manta.jpg </li>

<li>Specify header logos here: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/webapp/cust/mantamatcher/styles/_bootstrap-overrides.less</li>

 </ul>

<h2>Location IDs</h2>

Add new location ID names to **commonConfiguration.properties**, then request that changes be merged into the working version (not stored on GH):
https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/bundles/commonConfiguration.properties

Add **new location ID coordinates** here: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/bundles/locationIDGPS.properties


<h2>Emails</h2>

**Emails** are here: https://github.com/WildbookOrg/Wildbook/tree/bass/src/main/resources/emails/en

**Email template** (header and footer content used by other emails): 

* html: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/emails/en/email-template.html
* plaintext: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/emails/en/email-template.txt

Most important emails:

**New encounter submission (submission confirmation)**

* html: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/emails/en/newSubmission.html
* plaintext: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/emails/en/newSubmission.txt

**Encounter update (match notification)**

* html: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/emails/en/individualAddEncounter.html
* plaintext: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/emails/en/individualAddEncounter.txt 

**Encounter update (new individual)**

* html: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/emails/en/individualCreate.html
* plaintext: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/emails/en/individualCreate.txt

**Individual update (resighting notification)**

* html: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/emails/en/individualUpdate.html
* plaintext: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/resources/emails/en/individualUpdate.txt


<h2>Styling</h2>

**less files used to compile css)**: https://github.com/WildbookOrg/Wildbook/tree/bass/src/main/webapp/cust/mantamatcher/styles

Lots of settings here: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/webapp/cust/mantamatcher/styles/_bootstrap-vars.less

and here: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/webapp/cust/mantamatcher/styles/_bootstrap.less

<ul>
 <li>e.g. body text color (@gray-dark): <a href="https://www.colorhexa.com/333333">#333333</a>  </li>
 </ul>

**Some (mostly page-specific) styles are defined here**: 
https://github.com/WildbookOrg/Wildbook/tree/bass/src/main/webapp/css


<h2>Color palette notes</h2>

Colors get changed in this .less file: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/webapp/cust/mantamatcher/styles/_colours.less

<ul>
 <li>header background color and navbar text: <a href="https://www.colorhexa.com/0b718e">#0b718e</a>  </li>
 <li>footer background gray (aka @brand-light): <a href="https://www.colorhexa.com/dfdfdf">#dfdfdf</a>  </li>
 <li>latest encounter links on homepage (aka @brand-secondary): <a href="https://www.colorhexa.com/00c0f7">#00c0f7</a>  </li>
 <li>h1 and h2, Advanced Info button: <a href="https://www.colorhexa.com/005589">#005589</a>  </li>
 <li>Report Encounter button on homepage (aka whaleSharkblue): <a href="https://www.colorhexa.com/5d8cc1">#5d8cc1</a>  </li>
 </ul>

and here: https://github.com/WildbookOrg/Wildbook/blob/bass/src/main/webapp/cust/mantamatcher/styles/_bootstrap.less

<ul>
<li>red text for mandatory fields (.text-danger): <a href="https://www.colorhexa.com/a94442">#a94442</a>  </li>

<li>bright blue next arrows on gallery page: <a href="https://www.colorhexa.com/00AFCE">#00AFCE</a>  </li>
  
 </ul>

<h2> Quick reference </h2>

HTML entity encoder/decoder: https://mothereff.in/html-entities
