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
import org.w3c.dom.Node;

import java.io.File;
import java.io.StringReader;

import org.xml.sax.InputSource;


public class PatterningPassport implements java.io.Serializable {
  private static final long serialVersionUID = -5200145396524958700L;

  // Serialized variables
  private long timestampOfLastUpdate; // timestamp of the last update
  private String encounterUrl; // encounter URL
  private String mediaUrl; // media URL
  private String patterningPassportXmlUrl; // patterningPassport URL

  // Local vars
  private String encounterId; // encounter ID
  private String mediaId; // media ID
  private File webappsDir; // webapps directory
  private String passportDataXml; // the custom XML for the patterning passport

  static String NODE_NAME_ID = "idData";

  public PatterningPassport () {
  }

  /*
  public PatterningPassport (String passportDataXml, String encounterUrl, String mediaUrl, long timestampOfLastUpdate, String patterningPassportXmlUrl) {
    this.passportDataXml = passportDataXml;
    this.encounterUrl = encounterUrl;
    this.mediaUrl = mediaUrl;
    //this.timestampOfLastUpdate = timestampOfLastUpdate;
    updateTimeStamp();
    this.patterningPassportXmlUrl = patterningPassportXmlUrl;
  }
  */

  private void updateTimeStamp () {
    Date date= new java.util.Date();
    Timestamp ts = new Timestamp(date.getTime());
    this.timestampOfLastUpdate = ts.getTime();
  }

  private Boolean makeNewPassportXml (String context) {
    // -- FROM: http://www.mkyong.com/java/how-to-create-xml-file-in-java-dom/
    try {
      DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

      // root elements
      Document doc = docBuilder.newDocument();
      Element rootElement = doc.createElement("patterningPassport");
      doc.appendChild(rootElement);

      // File locations
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));

      // Universal passport fields for the XML
      String encounterUrlString = "foo/" + this.encounterId; // TODO -- generate URL
      String mediaSourceUrlString = "foo/" + this.mediaId; // TODO -- generate URL
      String passportXmlUrlString = "foo"; // TODO -- generate URL
      String encounterDir = this.webappsDir + "/shepherd_data_dir/encounters/" + this.encounterId;
      String xmlFilePathString = encounterDir + "/" + mediaId + "_pp.xml";

      Element metaElement = doc.createElement(NODE_NAME_ID);
      rootElement.appendChild(metaElement);

      // . encounter
      Element encounterId = doc.createElement("encounterId");
      encounterId.appendChild(doc.createTextNode(this.encounterId));
      metaElement.appendChild(encounterId);

      Element encounterUrl = doc.createElement("encounterUrl");
      encounterUrl.appendChild(doc.createTextNode(encounterUrlString));
      metaElement.appendChild(encounterUrl);

      // . media source (photo)
      Element mediaId = doc.createElement("mediaId");
      mediaId.appendChild(doc.createTextNode(this.mediaId));
      metaElement.appendChild(mediaId);

      Element mediaSourceUrl = doc.createElement("mediaSourceUrl");
      mediaSourceUrl.appendChild(doc.createTextNode(mediaSourceUrlString));
      metaElement.appendChild(mediaSourceUrl);

      // . track back to this XML data's file
      Element passportXmlUrl = doc.createElement("passportXmlUrl");
      passportXmlUrl.appendChild(doc.createTextNode(passportXmlUrlString));
      metaElement.appendChild(passportXmlUrl);

      // . passport data
      Element passportElement = doc.createElement("passportData");
      if (this.passportDataXml.isEmpty() == false) {
        try {
          Document passportDataDoc = docBuilder.parse( new InputSource(new StringReader(this.passportDataXml)));
          passportElement = passportDataDoc.getDocumentElement();

          // scrape out the "meta" section
          if (passportElement.hasChildNodes() == Boolean.TRUE) {
            for(Node childNode = passportElement.getFirstChild();
                childNode != null;){
              Node nextChild = childNode.getNextSibling();
              // do comparison/etc.
              if ((nextChild!=null)&&(nextChild.getNodeName()!=null)&&(nextChild.getNodeName() == NODE_NAME_ID)) {
                passportElement.removeChild(nextChild); // remove this node if it is ID data
              }

              childNode = nextChild;
            }
          }

          Element imported = (Element)doc.importNode(passportElement, Boolean.TRUE); // need to import to get node from different doc
          doc.getDocumentElement().appendChild(imported);
        } catch (Exception e) {
          e.printStackTrace();
          System.out.println("-----\nFAILURE! PatterningPassport data not saved.");
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
  public String getPassportDataXml(String context) {
    if (passportDataXml.equals(null)) {
      this.makeNewPassportXml(context); // make one if none is yet made
    }
    return passportDataXml;
  }
  /**
   * @param passportDataXml the passportDataXml to set
   */
  public Boolean setPassportDataXml(String passportDataXml,String context) {
    this.passportDataXml = passportDataXml;
    Boolean success = this.makeNewPassportXml(context);
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
