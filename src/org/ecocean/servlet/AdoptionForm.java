/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.servlet;

import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;
import org.apache.struts.upload.MultipartRequestHandler;

import javax.servlet.http.HttpServletRequest;
import java.util.Calendar;


public class AdoptionForm extends ActionForm {

  public AdoptionForm() {

    System.out.println("I have created an adoptionForm.");
    System.out.println();
  }

  public static final String ERROR_PROPERTY_MAX_LENGTH_EXCEEDED = "The maximum upload length has been exceeded by the client.";

  //define the variables for encounter submission

  private Calendar date = Calendar.getInstance();


  private String adopterName = "";
  private String adopterAddress = "";
  private String adopterEmail = "";
  private String adopterImage = "";
  private String adoptionStartDate = "";
  private String adoptionEndDate = "";
  private String adopterQuote = "";
  private String adoptionManager = "";
  private String shark = "";
  private String encounter = "";
  private String notes = "";
  private String adoptionType = "";
  private String number = "";

  public String getNumber() {
    return number;
  }

  public void setNumber(String num) {
    this.number = num;
  }

  public String getAdopterName() {
    return adopterName;
  }

  public void setAdopterName(String an) {
    this.adopterName = an;
  }

  public String getAdopterAddress() {
    return adopterAddress;
  }

  public void setAdopterAddress(String an) {
    this.adopterAddress = an;
  }

  public String getAdopterEmail() {
    return adopterEmail;
  }

  public void setAdopterEmail(String an) {
    this.adopterEmail = an;
  }

  public String getAdopterImage() {
    return adopterImage;
  }

  public void setAdopterImage(String an) {
    this.adopterImage = an;
  }

  public String getAdoptionStartDate() {
    return adoptionStartDate;
  }

  public void setAdoptionStartDate(String an) {
    this.adoptionStartDate = an;
  }

  public String getAdoptionEndDate() {
    return adoptionEndDate;
  }

  public void setAdoptionEndDate(String an) {
    this.adoptionEndDate = an;
  }

  public String getAdopterQuote() {
    return adopterQuote;
  }

  public void setAdopterQuote(String an) {
    this.adopterQuote = an;
  }

  public String getAdoptionManager() {
    return adoptionManager;
  }

  public void setAdoptionManager(String an) {
    this.adoptionManager = an;
  }

  public String getShark() {
    return shark;
  }

  public void setShark(String an) {
    this.shark = an;
  }

  public String getEncounter() {
    return encounter;
  }

  public void setEncounter(String an) {
    this.encounter = an;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String an) {
    this.notes = an;
  }

  public String getAdoptionType() {
    return adoptionType;
  }

  public void setAdoptionType(String at) {
    this.adoptionType = at;
  }

  /**
   * The value of the text the user has sent as form data
   */
  protected String theText;

  /**
   * The value of the embedded query string parameter
   */
  protected String queryParam;

  /**
   * Whether or not to write to a file
   */
  protected boolean writeFile = true;

  /**
   * The files that the user has uploaded
   */
  protected FormFile theFile1;


  /**
   * The file path to write to
   */
  protected String filePath1;


  //reset all variables
  public void reset() {
    writeFile = false;
    adopterName = "";
    adopterAddress = "";
    adopterEmail = "";
    adopterImage = "";
    adoptionStartDate = "";
    adoptionEndDate = "";
    adopterQuote = "";
    adoptionManager = "";
    shark = "";
    notes = "";
    adoptionType = "";
    number = "";
  }


  /**
   * Retrieve the value of the text the user has sent as form data
   */
  public String getTheText() {
    return theText;
  }

  /**
   * Set the value of the form data text
   */
  public void setTheText(String theText) {
    this.theText = theText;
  }

  /**
   * Retrieve the value of the query string parameter
   */
  public String getQueryParam() {
    return queryParam;
  }

  /**
   * Set the value of the query string parameter
   */
  public void setQueryParam(String queryParam) {
    this.queryParam = queryParam;
  }

  /**
   * Retrieve a representation of the file the user has uploaded
   */
  public FormFile getTheFile1() {
    if (theFile1 != null) {
      System.out.println("File one is good.");
    }
    return theFile1;
  }


  /**
   * Set a representation of the file the user has uploaded
   */
  public void setTheFile1(FormFile theFile1) {
    this.theFile1 = theFile1;
  }

  /**
   * Set whether or not to write to a file
   */
  public void setWriteFile(boolean writeFile) {
    this.writeFile = writeFile;
  }

  /**
   * Get whether or not to write to a file
   */
  public boolean getWriteFile() {
    return writeFile;
  }

  /**
   * Set the path to write a file to
   */
  public void setFilePath1(String filePath1) {
    this.filePath1 = filePath1;
  }


  /**
   * Check to make sure the client hasn't exceeded the maximum allowed upload size inside of this
   * validate method.
   */
  public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
    ActionErrors errors = new ActionErrors();
    //has the maximum length been exceeded?
    Boolean maxLengthExceeded = (Boolean)
      request.getAttribute(MultipartRequestHandler.ATTRIBUTE_MAX_LENGTH_EXCEEDED);
    if ((maxLengthExceeded != null) && (maxLengthExceeded.booleanValue())) {
      writeFile = false;
      errors.add(ERROR_PROPERTY_MAX_LENGTH_EXCEEDED, new ActionError("maxLengthExceeded"));
    }


    return errors;

  }
}
