<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,java.util.Properties, org.ecocean.servlet.ServletUtilities" %>
<%

  //setup our Properties object to hold all properties
  
  String langCode = ServletUtilities.getLanguageCode(request);

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);
  
  

  
  String context=ServletUtilities.getContext(request);

%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">
          <h1 class="intro">Rekisteriseloste</h1>
     

        <h2>1. Rekisterinpitäjä</h2>
<p>Maailman Luonnon Säätiö – World Wide Fund For Nature, Suomen rahasto sr (jäljempänä WWF), Y-tunnus 0215186-5, Lintulahdenkatu 10, 00500 HELSINKI, puh. (09) 774 0100 (vaihde)
</p>
<h2>2. Rekisteriasioiden yhteyshenkilö</h2>

<p>Justus Hyvärinen, Lintulahdenkatu 10, 00500 HELSINKI puh. (09) 774 0100 (vaihde) Sähköposti: etunimi.sukunimi@wwf.fi.
Rekisteriasioissa vastataan asiakasrekisterissä olevia henkilötietoja koskeviin kysymyksiin ja palautteisiin.
</p>
<h2>3. Rekisterin nimi</h2>
<p>WWF:n Norppagallerian kuvaajarekisteri
</p>
<h2>4. Rekisterin tekninen toteutus</h2>
<p>WWF:n Norppagallerian kuvaajarekisterin teknisestä toteutuksesta vastaa Wild Me.
</p>
<h2>5. Henkilötietojen käsittelyn tarkoitus (rekisterin käyttötarkoitus)</h2>
<p>Norppagalleria.wwf.fi –sivuilla kerätään norppakuvia ja -havaintoja tutkimuksen ja suojelun käyttöön. Yleisöhavainnot kirjataan rekisteriin, jonka avulla hoidetaan yhteydenpitoa havainnon tekijän ja palvelun ylläpitäjien välillä. Henkilötietoja ei käytetä markkinointitarkoituksiin. Havaintojen ilmoittajista tallennetaan seuraavat henkilötiedot: Nimi ja sähköpostiosoite.
</p>
<h2>6. Tietojen luovutus ja siirto</h2>
<p>Henkilötietoja luovutetaan säännöllisesti Itä-Suomen yliopiston (UEF) norppatutkimuksen käyttöön ja Metsähallituksen luontopalveluille saimaannorppakannan seuranta- ja suojelukäyttöön.
Lisäksi WWF voi luovuttaa tietoja toimivaltaisille viranomaisille esimerkiksi väärinkäytösten selvittämiseksi.
</p>
<h2>7. Rekisterin suojauksen periaatteet</h2>
<p>Henkilötietoja ovat oikeutettuja käyttämään vain ne henkilöt, joilla on työtehtäviensä puolesta siihen oikeus. Tiedot ovat suojattuja ohjelmistollisesti, palomuurein, salasanoin ja muilla teknisillä keinoilla.
</p>
<h2>8. Rekisteröidyn kielto-, tarkastus- ja korjausoikeus</h2>
<p>Rekisteröidyllä on WWF:n Norppagalleria -sivulla mahdollista kieltää havainnon esittäminen palvelun julkisilla sivuilla. Rekisteröity voi myös päättää havaintoa ilmoittaessaan, saako kuvaajan nimi näkyä julkisesti kuvan yhteydessä.
Rekisteröidyllä on henkilötietolain 26 §:n mukaisesti oikeus tarkastaa, mitä häntä koskevia tietoja henkilörekisteriin on talletettu. Rekisteröidyllä on oikeus vaatia virheellisen tiedon korjaamista ottamalla yhteys rekisterinpitäjään.
</p>


   
      <!-- end maintext -->
      </div>

    <jsp:include page="footer.jsp" flush="true"/>

