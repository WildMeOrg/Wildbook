package org.ecocean;

import java.sql.Timestamp;
import java.util.Date;


// for building xml
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
 
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.StringReader;

import org.xml.sax.InputSource;

public class PatterningPassport implements java.io.Serializable {
  
  private static final long serialVersionUID = 9099639217683181428L;
  
  // timestamp of the last update
  private long timestampOfLastUpdate;
  // the custom XML for the patterning passport
  private String passportDataXml;
  // encounter URL
  private String encounterUrl;
  // media URL
  private String mediaUrl;
  // patterningPassport URL
  private String patterningPassportXmlUrl;
  // webapps directory
  private File webappsDir;
  // encounter ID
  private String encounterId;
  // media ID
  private String mediaId;
  
  public PatterningPassport () {
  }
  
  public PatterningPassport (String passportDataXml, String encounterUrl, String mediaUrl, long timestampOfLastUpdate, String patterningPassportXmlUrl) {
    this.passportDataXml = passportDataXml;
    this.encounterUrl = encounterUrl;
    this.mediaUrl = mediaUrl;
    //this.timestampOfLastUpdate = timestampOfLastUpdate;
    updateTimeStamp();
    this.patterningPassportXmlUrl = patterningPassportXmlUrl;
  }
  
  private void updateTimeStamp () {
    Date date= new java.util.Date();
    Timestamp ts = new Timestamp(date.getTime());
    this.timestampOfLastUpdate = ts.getTime();
  }
  
  private Boolean makeNewPassportXml () {

    // -- FROM: http://www.mkyong.com/java/how-to-create-xml-file-in-java-dom/
    try {
      DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

      // root elements
      Document doc = docBuilder.newDocument();
      Element rootElement = doc.createElement("patterningPassport");
      doc.appendChild(rootElement);

      // File locations
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());

      // Universal passport fields for the XML 
      String encounterUrlString = "foo"; // TODO -- generate URL 
      String mediaSourceUrlString = "foo"; // TODO -- generate URL
      String passportXmlUrlString = "foo"; // TODO -- generate URL
      String encounterDir = this.webappsDir + "/shepherd_data_dir/encounters/" + this.encounterId;
      String xmlFilePathString = encounterDir + "/" + mediaId + "_pp.xml";

      // . encounter
      Element encounterUrl = doc.createElement("encounterUrl");
      encounterUrl.appendChild(doc.createTextNode(encounterUrlString)); 
      rootElement.appendChild(encounterUrl);

      // . media source (photo)
      Element mediaSourceUrl = doc.createElement("mediaSourceUrl");
      mediaSourceUrl.appendChild(doc.createTextNode(mediaSourceUrlString)); 
      rootElement.appendChild(mediaSourceUrl);

      // . track back to this XML data's file
      Element passportXmlUrl = doc.createElement("passportXmlUrl");
      passportXmlUrl.appendChild(doc.createTextNode(passportXmlUrlString)); 
      rootElement.appendChild(passportXmlUrl);

      // . passport data
      Element passportElement = doc.createElement("passportData");
      if (this.passportDataXml.isEmpty() == false) {
        try {
          Document passportDataDoc = docBuilder.parse( new InputSource(new StringReader(this.passportDataXml)));
          passportElement = passportDataDoc.getDocumentElement();
          Element imported = (Element)doc.importNode(passportElement, Boolean.TRUE); // need to import to get node from different doc
          doc.getDocumentElement().appendChild(imported);
        } catch (Exception e) {
          System.out.println("-----\nFAILURE! PatterningPassport data not saved. Exception: " + e.getLocalizedMessage());
          return Boolean.FALSE;
        }
      }
      
      // write the content into xml file
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      Transformer transformer = transformerFactory.newTransformer();
      DOMSource source = new DOMSource(doc);
      StreamResult result = new StreamResult(new File(xmlFilePathString));
      transformer.transform(source, result);
      
      System.out.println("-----\nSUCCESS! PatterningPassport saved at " + xmlFilePathString);
      return Boolean.TRUE;
    } catch (ParserConfigurationException pce) {
      pce.printStackTrace();
    } catch (TransformerException tfe) {
      tfe.printStackTrace();
    }
    return Boolean.FALSE;
  }
  
  /**
   * @return the timestampOfLastUpdate
   */
  public long getTimestampOfLastUpdate() {
    return timestampOfLastUpdate;
  }
 
  /**
   * @return the passportDataXml
   */
  public String getPassportDataXml() {
    if (passportDataXml.equals(null)) {
      this.makeNewPassportXml(); // make one if none is yet made
    }
    return passportDataXml;
  }
  /**
   * @param passportDataXml the passportDataXml to set
   */
  public Boolean setPassportDataXml(String passportDataXml) {
    this.passportDataXml = passportDataXml;
    Boolean success = this.makeNewPassportXml();
    this.updateTimeStamp();
    return success;
  }
  /**
   * @return the encounterUrl
   */
  public String getEncounterUrl() {
    // TODO read it from XML if needed
    return encounterUrl;
  }
  /**
   * @param encounterUrl the encounterUrl to set
   */
  public void setEncounterUrl(String encounterUrl) {
    this.encounterUrl = encounterUrl;
    this.updateTimeStamp();
  }
  /**
   * @return the mediaUrl
   */
  public String getMediaUrl() {
    // TODO read it from XML if needed
    return mediaUrl;
  }
  /**
   * @param mediaUrl the mediaUrl to set
   */
  public void setMediaUrl(String mediaUrl) {
    this.mediaUrl = mediaUrl;
    this.updateTimeStamp();
  }
  /**
   * @return the patterningPassportXmlUrl
   */
  public String getPatterningPassportXmlUrl() {
    // TODO read it from XML if needed
    return patterningPassportXmlUrl;
  }
  /**
   * @param patterningPassportXmlUrl the patterningPassportXmlUrl to set
   */
  public void setPatterningPassportXmlUrl(String patterningPassportXmlUrl) {
    this.patterningPassportXmlUrl = patterningPassportXmlUrl;
    this.updateTimeStamp();
  }

  public File getWebappsDir() {
    return webappsDir;
  }

  public void setWebappsDir(File webappsDir) {
    this.webappsDir = webappsDir;
  }

  /**
   * @return the encounterId
   */
  public String getEncounterId() {
    return encounterId;
  }

  /**
   * @param encounterId the encounterId to set
   */
  public void setEncounterId(String encounterId) {
    this.encounterId = encounterId;
  }

  /**
   * @return the mediaId
   */
  public String getMediaId() {
    return mediaId;
  }

  /**
   * @param mediaId the mediaId to set
   */
  public void setMediaId(String mediaId) {
    this.mediaId = mediaId;
  }
  
  
}
