package org.ecocean.auth;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** An organization-scoped service credential. Only the SHA-256 of the secret is stored. */
public class ServiceKey {
    private String id;
    private String organizationId;
    private String serviceUserId;
    private String label;
    private String secretHash;
    private String scopes;
    private long createdAt;
    private String createdBy;
    private long expiresAt;
    private Long revokedAt;
    private String revokedBy;
    private Long lastUsedAt;

    public ServiceKey() {}

    public ServiceKey(String id, String organizationId, String serviceUserId, String label,
        String secretHash, String scopes, long createdAt, String createdBy, long expiresAt) {
        this.id = id; this.organizationId = organizationId; this.serviceUserId = serviceUserId;
        this.label = label; this.secretHash = secretHash; this.scopes = scopes;
        this.createdAt = createdAt; this.createdBy = createdBy; this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public String getServiceUserId() { return serviceUserId; }
    public String getLabel() { return label; }
    public String getSecretHash() { return secretHash; }
    public String getScopes() { return scopes; }
    public long getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public long getExpiresAt() { return expiresAt; }
    public Long getRevokedAt() { return revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public Long getLastUsedAt() { return lastUsedAt; }

    public void setLabel(String label) { this.label = label; }
    public void setRevokedAt(Long revokedAt) { this.revokedAt = revokedAt; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public void setLastUsedAt(Long lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public boolean isRevoked() { return revokedAt != null; }

    /** Not revoked and not yet expired. */
    public boolean isLive(long now) { return revokedAt == null && expiresAt > now; }

    public Set<String> getScopeSet() {
        if (scopes == null || scopes.trim().isEmpty()) return Collections.emptySet();
        Set<String> out = new LinkedHashSet<>();
        for (String s : scopes.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
