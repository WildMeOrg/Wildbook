package org.ecocean.security;

import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.MarkedIndividual;

import static org.ecocean.Util.addToMultimap;

// java sucks for making us add three import lines just to use a multimap (without calling a single method on it!). INELEGANT. NEXT!
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 * a HiddenDataReporter is a simple, non-persistent class.
 * Its intended use is mainly in exporters or search results.
 * It keeps track of data that was hidden from a particular user
 * so that that user can request collaborations with the data owners
 */
public class HiddenDataReporter implements java.io.Serializable {

	private Map<String,Set<String>> hiddenEncountersByOwner;
	private Map<String,Set<String>> hiddenOccurrencesByOwner;
	private Map<String,Set<String>> hiddenIndividualsByOwner;

	public HiddenDataReporter() {
		this.hiddenEncountersByOwner  = new HashMap<String, Set<String>>();
		this.hiddenOccurrencesByOwner = new HashMap<String, Set<String>>();
		this.hiddenIndividualsByOwner = new HashMap<String, Set<String>>();
	}

	public void addEncounter(Encounter enc) {
		addToMultimap(hiddenEncountersByOwner, enc.getAssignedUsername(), enc.getCatalogNumber());
	}

	public void addOccurrence(Occurrence occ) {
		addToMultimap(hiddenOccurrencesByOwner, occ.getSubmitterID(), occ.getOccurrenceID());
	}

	public void addIndividual(MarkedIndividual mark) {
		for (Object obj: mark.getEncounters()) {
			Encounter enc = (Encounter) obj; // weird...
			if (enc.getAssignedUsername()!=null) {
				addToMultimap(hiddenIndividualsByOwner, enc.getAssignedUsername(), mark.getIndividualID());
			}
		}
	}

	public int numEncounters (){ return getEncountersByOwner ().size(); }
	public int numOccurrences(){ return getOccurrencesByOwner().size(); }
	public int numIndividuals(){ return getIndividualsByOwner().size(); }

	public Map<String,Set<String>> getEncountersByOwner () {return hiddenEncountersByOwner ;}
	public Map<String,Set<String>> getOccurrencesByOwner() {return hiddenOccurrencesByOwner;}
	public Map<String,Set<String>> getIndividualsByOwner() {return hiddenIndividualsByOwner;}
}