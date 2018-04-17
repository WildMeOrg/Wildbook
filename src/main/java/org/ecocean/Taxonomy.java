package org.ecocean;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import org.ecocean.Util;

public class Taxonomy implements java.io.Serializable {

  private String id;

  // The scientific name is the authoritative name the scientific community uses to identify what is colloquially called "a species."
  // There should be only one record per scientificName value in the Taxonomy table, though they sometimes change e.g. when giraffes were reclassified.
  // usually "Genus species" or "Genus species subspecies"
  private String scientificName;
  private List<String> commonNames; 

  // A Convention: getters/setters for Taxonomy objects (in other Classes) will use noun "Taxonomy".
  // while convenience string-only methods will use noun "Species" (and might require Shepherds to see which Taxonomy objects exist in the DB, for e.g. putting a species string on an Encounter)

  public Taxonomy() {
  }

  public Taxonomy(String scientificName) {
    this.id = Util.generateUUID();
    this.setScientificName(scientificName);
    this.commonNames = new ArrayList<String>();
  }

  public Taxonomy(String scientificName, String commonName) {
    this(scientificName);
    this.addCommonName(commonName);
  }

  public String getId() {
    return id;
  }


  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }
  public String getScientificName() {
    return scientificName;
  }

  public void setCommonNames(List<String> commonNames) {
    this.commonNames = commonNames;
  }
  public List<String> getCommonNames() {
    return commonNames;
  }
  public void addCommonName(String commonName) {
    if (!this.commonNames.contains(commonName)) this.commonNames.add(commonName);
  }
  public String getCommonName() {
    return getCommonName(0);
  }
  public String getCommonName(int i) {
    if (commonNames==null || commonNames.size()<=i) return null;
    return commonNames.get(i);
  }



}