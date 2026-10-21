package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

/**
 * Role.SYSTEM_ROLES is shared by StartupWildbook (seeding), UserConsolidate (ranking two users)
 * and LocationRoleAccess (names that must never be read as a locationID), so both its order and
 * its immutability are load-bearing.
 */
class RoleTest {
    @Test void systemRolesAreOrderedHighestPrivilegeFirst() {
        assertEquals(Arrays.asList("admin", "orgAdmin", "researcher", "rest", "machinelearning"),
            Role.SYSTEM_ROLES, "UserConsolidate walks this order to rank two users");
    }

    @Test void systemRoleNamesAlsoReserveExplicitCapabilities() {
        LinkedHashSet<String> expected = new LinkedHashSet<String>(Role.SYSTEM_ROLES);
        expected.add(Role.API_SUBMISSION);
        assertEquals(expected, Role.SYSTEM_ROLE_NAMES);
        org.junit.jupiter.api.Assertions.assertFalse(Role.SYSTEM_ROLES.contains(Role.API_SUBMISSION));
        assertTrue(Role.SYSTEM_ROLE_NAMES.contains("orgAdmin"));
        assertEquals(Role.SYSTEM_ROLES.size() + 1, Role.SYSTEM_ROLE_NAMES.size(), "no duplicates");
    }

    @Test void systemRolesAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> Role.SYSTEM_ROLES.add("intruder"));
        assertThrows(UnsupportedOperationException.class,
            () -> Role.SYSTEM_ROLE_NAMES.add("intruder"));
    }

    @Test void nullContextReadsAsContext0() {
        Role role = new Role("tomcat", "admin");

        assertEquals("context0", role.getContext(), "ShepherdRealm relies on this default");
    }
}
