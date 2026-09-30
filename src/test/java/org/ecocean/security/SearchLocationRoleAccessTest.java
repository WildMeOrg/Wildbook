package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.ecocean.Encounter;
import org.ecocean.OpenSearch;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchLocationRoleAccessTest {
    private JSONObject previousTree;
    private Shepherd shepherd;
    private User user;
    private Encounter encounter;
    private Set<String> roles;

    @BeforeEach void setUp() {
        previousTree = LocationRoleTestTree.inject();
        shepherd = mock(Shepherd.class);
        user = mock(User.class);
        encounter = mock(Encounter.class);
        roles = new HashSet<String>();
        when(user.getId()).thenReturn("researcher-id");
        when(user.getUsername()).thenReturn("researcher-name");
        when(shepherd.getContext()).thenReturn("context0");
        when(shepherd.getEncounter("enc-1")).thenReturn(encounter);
        when(encounter.getLocationID()).thenReturn("Komodo");
        when(shepherd.doesUserHaveAnyRole(eq("researcher-name"), anyCollection(), eq("context0")))
            .thenAnswer(invocation -> !Collections.disjoint(roles,
                (Collection<?>)invocation.getArgument(1)));
    }

    @AfterEach void tearDown() {
        LocationRoleTestTree.restore(previousTree);
    }

    private JSONObject document() {
        return new JSONObject().put("id", "enc-1").put("locationId", "Komodo")
            .put("submitterUserId", "another-user")
            .put("mediaAssets", new JSONArray().put(new JSONObject().put("uuid", "image-1")));
    }

    private void assertVisible(JSONObject doc) throws Exception {
        JSONObject result = OpenSearch.sanitizeDoc(doc, "encounter", shepherd, user);
        assertEquals("full", result.getString("access"));
        assertEquals("image-1", result.getJSONArray("mediaAssets").getJSONObject(0).getString("uuid"));
        assertFalse(result.has("viewUsers"));
        assertFalse(result.has("submitterUserId"));
        assertFalse(doc.has("access"), "sanitization must not modify the indexed document");
    }

    private void assertHidden(JSONObject doc) throws Exception {
        JSONObject result = OpenSearch.sanitizeDoc(doc, "encounter", shepherd, user);
        assertEquals("none", result.getString("access"));
        assertFalse(result.has("mediaAssets"));
    }

    @Test void exactRoleShowsImagesBeforePermissionsAreIndexed() throws Exception {
        roles.add("Komodo");
        assertVisible(document());
    }

    @Test void ancestorRoleShowsImagesAcrossRepeatedRequestsWithStalePermissions() throws Exception {
        roles.add("Indonesia");
        JSONObject doc = document().put("viewUsers", new JSONArray().put("unrelated-user"));
        for (int i = 0; i < 3; i++) assertVisible(doc);
        assertEquals("unrelated-user", doc.getJSONArray("viewUsers").getString(0));
    }

    @Test void emptyIndexedPermissionsDoNotBlockLocationRole() throws Exception {
        roles.add("Flores Sea");
        assertVisible(document().put("viewUsers", new JSONArray()));
    }

    @Test void unrelatedAndSystemRolesDoNotShowImages() throws Exception {
        roles.add("Pakistan");
        roles.add("researcher");
        assertHidden(document());
    }

    @Test void childRoleDoesNotGrantParentLocation() throws Exception {
        roles.add("Komodo");
        when(encounter.getLocationID()).thenReturn("Flores Sea");
        assertHidden(document());
    }

    @Test void roleCheckUsesCurrentEncounterLocation() throws Exception {
        roles.add("Indonesia");
        when(encounter.getLocationID()).thenReturn("Pakistan");
        assertHidden(document());
        roles.clear();
        roles.add("Pakistan");
        assertVisible(document());
    }

    @Test void missingEncounterOrIdDoesNotGrantAccess() throws Exception {
        roles.add("Indonesia");
        when(shepherd.getEncounter("enc-1")).thenReturn(null);
        assertHidden(document());
        JSONObject doc = document();
        doc.remove("id");
        assertHidden(doc);
        verify(shepherd, never()).getEncounter(isNull(String.class));
        verify(shepherd, never()).getEncounter("");
    }

    @Test void missingLocationDoesNotGrantAccess() throws Exception {
        roles.add("Indonesia");
        when(encounter.getLocationID()).thenReturn(null);
        assertHidden(document());
    }

    @Test void revokedRoleDoesNotKeepFallbackAccess() throws Exception {
        roles.add("Indonesia");
        JSONObject doc = document();
        assertVisible(doc);
        roles.clear();
        assertHidden(doc);
    }

    @Test void tokenSearchKeepsIndexedPermissions() throws Exception {
        roles.add("Indonesia");
        JSONObject result = OpenSearch.sanitizeDoc(document(), "encounter", shepherd, user, true);
        assertEquals("none", result.getString("access"));
        assertFalse(result.has("mediaAssets"));
        verify(shepherd, never()).getEncounter(anyString());
    }

    @Test void existingIndexedGrantsNeedNoEncounterLookup() throws Exception {
        assertVisible(document().put("publiclyReadable", true));
        assertVisible(document().put("submitterUserId", user.getId()));
        assertVisible(document().put("viewUsers", new JSONArray().put(user.getId())));
        when(user.isAdmin(shepherd)).thenReturn(true);
        assertVisible(document());
        verify(shepherd, never()).getEncounter(anyString());
    }
}
