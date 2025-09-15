package org.ecocean;

import java.util.Calendar;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

class BaseTest {
    // meant to test replacement of individual class .equals() with Base one
    @Test void equalsTest() {
        String uuid1 = "d316cd66-f0b1-49a4-859e-b1c4cec177ae";
        String uuid2 = "e316cd66-f0b1-49a4-859e-b1c4cec177ae";
        Integer other = 1;
        // Encounter
        Encounter enc1 = new Encounter();
        Encounter enc2 = new Encounter();

        enc1.setId(uuid1);
        enc2.setId(uuid2);
        assertFalse(enc1.equals(enc2));
        assertFalse(enc1.equals(other));
        enc2.setId(uuid1);
        assertTrue(enc1.equals(enc2));
        // Annotation
        Annotation ann1 = new Annotation();
        Annotation ann2 = new Annotation();
        ann1.setId(uuid1);
        ann2.setId(uuid2);
        assertFalse(ann1.equals(ann2));
        assertFalse(ann1.equals(other));
        ann2.setId(uuid1);
        assertTrue(ann1.equals(ann2));
    }
}
