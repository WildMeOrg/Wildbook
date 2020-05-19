package org.ecocean.api;

import org.ecocean.external.ExternalOrganization;

import java.util.Set;
import java.util.HashSet;

public class ExternalOrganizationSet implements java.io.Serializable {
    private Set<ExternalOrganization> set = null;

    public ExternalOrganizationSet() {}

    public ExternalOrganizationSet(Set<ExternalOrganization> set) {
        this();
        this.set = set;
    }

    public Set<ExternalOrganization> getSet() {
        return set;
    }
    public void setSet(Set<ExternalOrganization> s) {
        set = s;
    }
    public void addOrganization(ExternalOrganization org) {
        if (set == null) set = new HashSet<ExternalOrganization>();
        set.add(org);
    }
}

