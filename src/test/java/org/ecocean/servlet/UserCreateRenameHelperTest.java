package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserCreateRenameHelperTest {
    @Test void renameHelperPasswordIsRandom() {
        String a = UserCreate.unusablePassword();
        String b = UserCreate.unusablePassword();
        assertNotEquals(a, b, "two calls must differ");
        assertEquals(64, a.length(), "32 bytes as hex");
        assertEquals(64, b.length(), "32 bytes as hex");
        assertTrue(a.matches("[0-9a-f]{64}"), "lowercase hex only");
    }
}
