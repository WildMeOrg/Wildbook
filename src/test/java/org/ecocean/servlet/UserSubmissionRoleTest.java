package org.ecocean.servlet;

import java.util.ArrayList;
import java.util.List;
import org.ecocean.Role;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserSubmissionRoleTest {
    @Test void onlySiteAdminCanChangeEnrollment() {
        assertFalse(Role.canEditRole(Role.API_SUBMISSION, false));
        assertTrue(Role.canEditRole(Role.API_SUBMISSION, true));
    }
    @Test void nonAdminEditPreservesEnrollmentAcrossRename() {
        Role capability = new Role("old-name", Role.API_SUBMISSION);
        List<Role> toReplace = new ArrayList<>(List.of(capability, new Role("old-name", "researcher")));
        UserCreate.preserveSubmissionRole(toReplace, "new-name", false);
        assertFalse(toReplace.contains(capability));
        assertEquals("new-name", capability.getUsername());
        assertEquals(1, toReplace.size());
    }
    @Test void adminCanRevokeEnrollmentByOmittingItFromReplacement() {
        Role capability = new Role("pilot", Role.API_SUBMISSION);
        List<Role> toReplace = new ArrayList<>(List.of(capability));
        UserCreate.preserveSubmissionRole(toReplace, "pilot", true);
        assertTrue(toReplace.contains(capability));
    }
    @Test void usernameCollisionIsRejectedAndQueryClosed() {
        org.ecocean.shepherd.core.Shepherd sh = mock(org.ecocean.shepherd.core.Shepherd.class);
        javax.jdo.PersistenceManager pm = mock(javax.jdo.PersistenceManager.class);
        javax.jdo.Query query = mock(javax.jdo.Query.class);
        when(sh.getPM()).thenReturn(pm);
        when(pm.newQuery(eq(org.ecocean.User.class), anyString())).thenReturn(query);
        when(query.execute("taken", "account-a")).thenReturn(1L);
        assertFalse(UserCreate.usernameAvailable(sh, "taken", "account-a"));
        verify(query).closeAll();
    }

    @Test void accountMergeDoesNotTransferSubmissionEnrollment() {
        org.ecocean.shepherd.core.Shepherd sh = mock(org.ecocean.shepherd.core.Shepherd.class);
        javax.jdo.PersistenceManager pm = mock(javax.jdo.PersistenceManager.class);
        when(sh.getPM()).thenReturn(pm);
        when(sh.getContext()).thenReturn("context0");
        org.ecocean.User retained = mock(org.ecocean.User.class), merged = mock(org.ecocean.User.class);
        when(retained.getUsername()).thenReturn("retained"); when(merged.getUsername()).thenReturn("merged");
        Role role = new Role("merged", Role.API_SUBMISSION);
        when(sh.getAllRolesForUserInContext("merged", "context0")).thenReturn(List.of(role));
        when(sh.getAllRolesForUserInContext("retained", "context0")).thenReturn(List.of());
        UserConsolidate.consolidateRoles(sh, retained, merged);
        verify(pm).deletePersistent(role);
        verify(pm, never()).makePersistent(role);
        assertEquals("merged", role.getUsername());
    }
    @Test void adoptingUnusedUsernameClearsAllPriorGrants() {
        org.ecocean.shepherd.core.Shepherd sh = mock(org.ecocean.shepherd.core.Shepherd.class);
        javax.jdo.PersistenceManager pm = mock(javax.jdo.PersistenceManager.class);
        javax.jdo.Query query = mock(javax.jdo.Query.class);
        when(sh.getPM()).thenReturn(pm);
        when(pm.newQuery(eq(Role.class), anyString())).thenReturn(query);
        List<Role> prior = List.of(new Role("reused", Role.API_SUBMISSION), new Role("reused", "admin"));
        when(query.execute("reused")).thenReturn(prior);
        UserCreate.clearUnownedRoles(sh, "reused");
        verify(pm).deletePersistentAll(prior);
        verify(query).closeAll();
    }
}
