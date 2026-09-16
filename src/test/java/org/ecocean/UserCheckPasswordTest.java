package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UserCheckPasswordTest {

    private static final String SALT = "0123456789abcdef";

    private User userWith(String clear) {
        // IMPORTANT: User.setPassword(String) only ASSIGNS the field — it does NOT hash. Production
        // stores an already-hashed value alongside a matching salt, so the test must do the same.
        User u = new User();
        u.setUsername("alice");
        u.setSalt(SALT);
        u.setPassword(org.ecocean.servlet.ServletUtilities.hashAndSaltPassword(clear, SALT));
        return u;
    }

    @Test void checkPassword_trueForCorrect_falseForWrong() {
        User u = userWith("s3cr3t!");
        assertTrue(u.checkPassword("s3cr3t!"), "correct password verifies");
        assertFalse(u.checkPassword("nope"), "wrong password rejected");
    }

    @Test void checkPassword_falseOnNullOrNoStoredPassword() {
        User u = new User();
        u.setUsername("bob");              // no password/salt set
        assertFalse(u.checkPassword("anything"), "no stored password -> false");
        User u2 = userWith("pw");
        assertFalse(u2.checkPassword(null), "null candidate -> false");
        assertFalse(u2.checkPassword(""), "empty candidate -> false");
    }
}
