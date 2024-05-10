package org.ecocean;

import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import org.datanucleus.api.rest.orgjson.JSONException;
import org.datanucleus.api.rest.orgjson.JSONObject;

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

    public Adoption(String id, String adopterName, String adopterEmail, String adoptionStartDate,
        String adoptionEndDate) {
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
        if (img == null) { img = null; } else {
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

    // Returns a somewhat rest-like JSON object containing the metadata
    public JSONObject uiJson(HttpServletRequest request, boolean includeOrganizations)
    throws JSONException {
        JSONObject jobj = new JSONObject();

        jobj.put("id", this.getID());
        jobj.put("stripeCustomerId", this.getStripeCustomerId());
        jobj.put("adopterEmail", this.getAdopterEmail());
        jobj.put("adopterName", this.getAdopterName());
        jobj.put("adopterImage", this.getAdopterImage());
        jobj.put("adoptionType", this.getAdoptionType());
        jobj.put("encounter", this.getEncounter());
        jobj.put("individual", this.getMarkedIndividual());
        jobj.put("adoptionStartDate", this.getAdoptionStartDate());
        jobj.put("adoptionEndDate", this.getAdoptionEndDate());
        return jobj;
    }
}
