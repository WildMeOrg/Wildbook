package org.ecocean.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

/**
 * Pure parsing contract for Shepherd.parseRelationshipKey -- the piece that turns the
 * persistenceID the social UI submits ("1011[OID]org.ecocean.social.Relationship", or a bare
 * numeric key) into a datastore key. Null means "not a well-formed Relationship id", which the
 * servlets answer with 400, as opposed to a well-formed id whose row is gone (404).
 */
class ShepherdParseRelationshipKeyTest {
    @Test void parsesTheFullOidStringTheUiSubmits() {
        assertEquals(1011L, Shepherd.parseRelationshipKey(
            "1011[OID]org.ecocean.social.Relationship"));
    }

    @Test void parsesABareNumericKey() {
        assertEquals(7L, Shepherd.parseRelationshipKey("7"));
    }

    @Test void toleratesSurroundingWhitespace() {
        assertEquals(1011L, Shepherd.parseRelationshipKey(
            "  1011[OID]org.ecocean.social.Relationship \t"));
    }

    @Test void rejectsMalformedInput() {
        assertNull(Shepherd.parseRelationshipKey(null));
        assertNull(Shepherd.parseRelationshipKey(""));
        assertNull(Shepherd.parseRelationshipKey("   "));
        assertNull(Shepherd.parseRelationshipKey("abc"));
        assertNull(Shepherd.parseRelationshipKey("12abc"));
        assertNull(Shepherd.parseRelationshipKey("-5"));
    }

    @Test void rejectsAnOidStringForAnotherClass() {
        assertNull(Shepherd.parseRelationshipKey("1011[OID]org.ecocean.Encounter"));
        assertNull(Shepherd.parseRelationshipKey("1011[OID]anything"));
    }

    @Test void rejectsDigitsBeyondLongRange() {
        assertNull(Shepherd.parseRelationshipKey("99999999999999999999999999"));
        assertNull(Shepherd.parseRelationshipKey(
            "99999999999999999999999999[OID]org.ecocean.social.Relationship"));
    }
}
