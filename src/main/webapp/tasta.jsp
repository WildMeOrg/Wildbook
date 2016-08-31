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
          <h2 class="intro">Mist&auml; WWF:n Norppagalleriassa on kyse?</h2>
     



<p>Saimaannorpan turkin kuviot ovat yksil&ouml;llisi&auml; ja niit&auml; voidaan k&auml;ytt&auml;&auml; sormenj&auml;lkien tapaan yksil&ouml;iden tunnistamiseen. Tunnistetuista norppayksil&ouml;ist&auml; saatuja havaintoja voidaan menetelm&auml;n&auml; verrata esimerkiksi lintujen rengastukseen, kuitenkin sill&auml; erotuksella ett&auml; valokuvatunnistuksessa yksil&ouml;it&auml; ei tarvitse ottaa kiinni ja merkit&auml;. N&auml;in saatua tietoa voidaan hy&ouml;dynt&auml;&auml; muiden menetelmien t&auml;ydent&auml;j&auml;n&auml; monilla tavoin tutkimuksessa ja kannan seurannassa, esimerkiksi kannan koon arvioinnissa tai yksil&ouml;iden el&auml;m&auml;nkaaren selvitt&auml;misess&auml;.</p>

<p>Norppagalleriassa esittelemme tunnistettuja saimaannorppia ja ker&auml;&auml;mme lis&auml;&auml; kuvia ja havaintoja tutkimuksen ja suojelun k&auml;ytt&ouml;&ouml;n. Kaikkia tunnistettuja norppia ei esitell&auml; galleriassa. Esimerkiksi jotkut kuvaajat eiv&auml;t ole antaneet suostumustaan kuvien julkaisuun. Norppien yksil&ouml;llisest&auml; tunnistuksesta valokuvien perusteella vastaa It&auml;-Suomen yliopisto, ja kuvattomien havaintojen vastaanotosta vastaa Mets&auml;hallitus. Norppagalleriasivuston toteutuksesta ja yll&auml;pidosta vastaa WWF. Kuvien luvaton kopiointi Norppagalleriasta on kielletty.</p>

<h2 class="intro">Millainen kuva kelpaa yksil&ouml;tunnistukseen?</h2>

<p>Yksil&ouml;ntunnistukseen kelpaa kuva, josta erottaa norpan kyljen turkkikuvion. Usein kaukaakin otetut kuvat riitt&auml;v&auml;t tunnistukseen. Kuvista, joissa norppa on vedess&auml; eiv&auml;tk&auml; sen kylkikuviot n&auml;y, ei norppaa pystyt&auml; tunnistamaan. P&auml;&auml;osa Norppagallerian kuvista on saatu It&auml;-Suomen yliopiston tutkijoiden saimaannorpan lepokiville asentamista riistakameroista. Tutkimukselle on erityisluvat.</p>

<h2 class="intro">&Auml;l&auml; h&auml;iritse saimaannorppia!</h2>

<p>Vaikka kartutamme Norppagalleriaa tutkimuksen ja suojelun tarpeisiin, &auml;l&auml; koskaan h&auml;iritse norppia kuvien ja havaintojen saamiseksi! Saimaannorppa on luonnonsuojelulailla rauhoitettu ja luonnonsuojeluasetuksen mukaan erityisesti suojeltava laji, jonka tahallinen h&auml;iritseminen sen el&auml;m&auml;nkierron kannalta t&auml;rkeill&auml; paikoilla on lailla kielletty. Avovesiaikaisilla lev&auml;hdyspaikoilla h&auml;iri&ouml;n v&auml;ltt&auml;minen tarkoittaa k&auml;yt&auml;nn&ouml;ss&auml; sit&auml;, ett&auml; lepokivell&auml; olevaa norppaa ei tule tahallisesti, esimerkiksi valokuvaustarkoituksessa, l&auml;hesty&auml; niin ett&auml; se h&auml;iriintyy ja joutuu pulahtamaan veteen. Erityisen t&auml;rke&auml;&auml; h&auml;iri&ouml;n v&auml;ltt&auml;minen on karvanvaihtoaikaan huhtikuun puoliv&auml;list&auml; kes&auml;kuun puoliv&auml;liin, jolloin norpat viett&auml;v&auml;t paljon aikaa lepokivill&auml;. Pitk&auml;lt&auml;kin et&auml;isyydelt&auml; saa teleobjektiivilla hyviss&auml; olosuhteissa yksil&ouml;tunnistukseen riitt&auml;v&auml;n kuvan norppaa h&auml;iritsem&auml;tt&auml;.</p> 

      <!-- end maintext -->
      </div>

    <jsp:include page="footer.jsp" flush="true"/>

