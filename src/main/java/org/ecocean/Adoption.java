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

package org.ecocean;

/**
 * COmment
 *
 * @author jholmber
 */
public class Adoption implements java.io.Serializable {
  static final long serialVersionUID = -1020952058521486782L;

  private String id;
  private String stripeCustomerId;
  private String adopterName;
  private String adopterAddress;
  private String adopterEmail;
  private String adopterImage;
  private String adoptionStartDate;
  private String adoptionEndDate;
  private String adopterQuote;
  private String adoptionManager;
  private String individual;
  private String encounter;
  private String notes;
  private String adoptionType;

  public Adoption(String id, String adopterName, String adopterEmail, String adoptionStartDate, String adoptionEndDate) {
    this.adopterName = adopterName;
    this.adopterEmail = adopterEmail;
    this.adoptionStartDate = adoptionStartDate;
    this.adoptionEndDate = adoptionEndDate;
    this.id = id;
  }

  public String getAdopterName() {
    return adopterName;
  }

  public String getAdopterAddress() {
    return adopterAddress;
  }

  public String getAdopterEmail() {
    return adopterEmail;
  }

  public String getAdopterImage() {
    return adopterImage;
  }

  public String getAdoptionStartDate() {
    return adoptionStartDate;
  }

  public String getAdoptionEndDate() {
    return adoptionEndDate;
  }

  public String getAdopterQuote() {
    return adopterQuote;
  }

  public String getAdoptionManager() {
    return adoptionManager;
  }

  public String getNotes() {
    return notes;
  }

  public String getMarkedIndividual() {
    return individual;
  }

  public String getEncounter() {
    return encounter;
  }

  public String getID() {
    return id;
  }

  public String getStripeCustomerId() {
    return stripeCustomerId;
  }

  public String getAdoptionType() {
    return adoptionType;
  }

  public void setAdopterName(String name) {
    this.adopterName = name;
  }

  public void setAdopterAddress(String addr) {
    this.adopterAddress = addr;
  }

  public void setAdopterEmail(String em) {
    this.adopterEmail = em;
  }

  public void setAdopterImage(String img) {
    if(img==null){ img=null;}
    else{
    	this.adopterImage = img;
	  }
  }

  public void setAdoptionStartDate(String date) {
    this.adoptionStartDate = date;
  }

  public void setAdoptionEndDate(String date) {
    this.adoptionEndDate = date;
  }

  public void setAdopterQuote(String quote) {
    this.adopterQuote = quote;
  }

  public void setAdoptionManager(String man) {
    this.adoptionManager = man;
  }

  public void setNotes(String nt) {
    this.notes = nt;
  }

  public void setIndividual(String sh) {
    this.individual = sh;
  }

  public void setEncounter(String sh) {
    this.encounter = sh;
  }

  public void setID(String i) {
    this.id = i;
  }

  public void setStripeCustomerId(String sci) {
    this.stripeCustomerId = sci;
  }

  public void setAdoptionType(String at) {
    this.adoptionType = at;
  }

}
