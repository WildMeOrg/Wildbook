package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.genetics.TissueSample;
import org.ecocean.media.Feature;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Status-code tests for GET /api/v3/individuals/info/social-data.
 *
 * Uses Testcontainers for real Postgres + real ACL evaluation; Shepherd is
 * intercepted via MockedConstruction so that direct doGet() works without
 * a live Wildbook deployment, while all ACL-path methods delegate to a real
 * Shepherd backed by the test container.
 */
@Testcontainers
class MarkedIndividualInfoTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static Properties props;
    static String seededIndivId;
    static User seededOwner;
    static String seededOwnerUsername;
    static User stranger;
    static String strangerUsername;
    static String indivWithTissueAndAnnotId;
    // individual with mixed encounters: one private (owner-only) + one public
    static String mixedIndivId;

    // occurringWith security test fields
    // focalOwner is reused as the focal individual's owner (seededOwner)
    // focalIndivIdInSharedOcc: focal owned by focalOwner; companion encounter owned by stranger
    static String focalIndivIdInSharedOcc;
    static String hiddenCompanionDisplayName;
    // focalIndivIdWithViewableCompanion: focal + companion both viewable by focalOwner
    static String focalIndivIdWithViewableCompanion;
    static String viewableCompanionDisplayName;
    // alias so test methods read naturally (same object as seededOwner)
    static User focalOwner;

    // relationship test fields
    static String focalIndivWithViewableRel;
    static String focalIndivWithHiddenPartnerRel;
    static String hiddenPartnerId;

    @BeforeAll
    static void setUp() throws Exception {
        Properties cc = new Properties();
        cc.setProperty("collaborationSecurityEnabled", "true");
        CommonConfiguration.initialize("context0", cc);

        props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        TestPMFUtil.closePMF("context0");

        // Seed data: owner user, an encounter they own, and an individual.
        // Also seed a stranger user with no access.
        Shepherd sh = new Shepherd("context0", props);
        try {
            sh.beginDBTransaction();

            // Create owner user
            seededOwnerUsername = "owner_user";
            String salt = ServletUtilities.getSalt().toHex();
            String hashed = ServletUtilities.hashAndSaltPassword("password", salt);
            seededOwner = new User(seededOwnerUsername, hashed, salt);
            sh.getPM().makePersistent(seededOwner);
            // Assign researcher role
            Role ownerRole = new Role(seededOwnerUsername, "researcher");
            sh.getPM().makePersistent(ownerRole);

            // Create stranger user (no collaboration with owner)
            strangerUsername = "stranger_user";
            String salt2 = ServletUtilities.getSalt().toHex();
            String hashed2 = ServletUtilities.hashAndSaltPassword("password", salt2);
            stranger = new User(strangerUsername, hashed2, salt2);
            sh.getPM().makePersistent(stranger);
            Role strangerRole = new Role(strangerUsername, "researcher");
            sh.getPM().makePersistent(strangerRole);

            // Create encounter owned by owner (private to owner)
            Encounter enc = new Encounter();
            enc.setSubmitterID(seededOwnerUsername);
            sh.storeNewEncounter(enc);

            // seededIndivId: private individual (only owner can view; stranger sees 403)
            MarkedIndividual indiv = new MarkedIndividual("TestIndividual", enc);
            sh.storeNewMarkedIndividual(indiv);
            seededIndivId = indiv.getId();

            // mixedIndivId: one private encounter (owner) + one public encounter (null submitter).
            // Owner sees 2 encounters; stranger can view the individual via the public encounter
            // but still sees only 1 encounter (the public one).
            Encounter encPrivate2 = new Encounter();
            encPrivate2.setSubmitterID(seededOwnerUsername);
            sh.storeNewEncounter(encPrivate2);
            Encounter encPublic = new Encounter();
            // submitterID intentionally left null so isUsernameAnonymous(null)==true
            sh.storeNewEncounter(encPublic);
            MarkedIndividual mixedIndiv = new MarkedIndividual("MixedIndividual", encPublic);
            mixedIndiv.addEncounter(encPrivate2);
            sh.storeNewMarkedIndividual(mixedIndiv);
            mixedIndivId = mixedIndiv.getId();

            // Create an individual whose single encounter has both a TissueSample and an Annotation.
            Encounter encTA = new Encounter();
            encTA.setSubmitterID(seededOwnerUsername);
            TissueSample ts = new TissueSample(null, "sample-001");
            encTA.addTissueSample(ts);
            sh.getPM().makePersistent(ts);
            // Use empty feature list to avoid FeatureType.initAll() requirement
            Annotation ann = new Annotation("test-species", new ArrayList<Feature>());
            sh.storeNewAnnotation(ann);
            encTA.addAnnotation(ann);
            sh.storeNewEncounter(encTA);

            MarkedIndividual indivTA = new MarkedIndividual("TissueAnnotIndividual", encTA);
            sh.storeNewMarkedIndividual(indivTA);
            indivWithTissueAndAnnotId = indivTA.getId();

            // focalOwner alias
            focalOwner = seededOwner;

            // --- occurringWith security test: hidden companion ---
            // Focal encounter owned by seededOwner; companion encounter owned by stranger.
            // seededOwner cannot view stranger's encounter (no collaboration).
            Encounter focalEncHidden = new Encounter();
            focalEncHidden.setSubmitterID(seededOwnerUsername);
            sh.storeNewEncounter(focalEncHidden);

            Encounter hiddenCompanionEnc = new Encounter();
            hiddenCompanionEnc.setSubmitterID(strangerUsername);
            sh.storeNewEncounter(hiddenCompanionEnc);

            hiddenCompanionDisplayName = "HiddenCompanion";
            MarkedIndividual hiddenCompanionIndiv = new MarkedIndividual(hiddenCompanionDisplayName,
                hiddenCompanionEnc);
            // set back-reference so coEnc.getIndividual() works when loaded from occurrence
            hiddenCompanionEnc.setIndividual(hiddenCompanionIndiv);
            sh.storeNewMarkedIndividual(hiddenCompanionIndiv);

            MarkedIndividual focalIndivHidden = new MarkedIndividual("FocalForHiddenOcc",
                focalEncHidden);
            focalEncHidden.setIndividual(focalIndivHidden);
            sh.storeNewMarkedIndividual(focalIndivHidden);
            focalIndivIdInSharedOcc = focalIndivHidden.getId();

            Occurrence occHidden = new Occurrence("occ-hidden-companion-001");
            occHidden.addEncounterAndUpdateIt(focalEncHidden);
            occHidden.addEncounterAndUpdateIt(hiddenCompanionEnc);
            sh.storeNewOccurrence(occHidden);

            // --- occurringWith viewable companion test ---
            // Both focal and companion encounters owned by seededOwner (fully viewable).
            Encounter focalEncViewable = new Encounter();
            focalEncViewable.setSubmitterID(seededOwnerUsername);
            sh.storeNewEncounter(focalEncViewable);

            Encounter viewableCompanionEnc = new Encounter();
            viewableCompanionEnc.setSubmitterID(seededOwnerUsername);
            sh.storeNewEncounter(viewableCompanionEnc);

            viewableCompanionDisplayName = "ViewableCompanion";
            MarkedIndividual viewableCompanionIndiv = new MarkedIndividual(viewableCompanionDisplayName,
                viewableCompanionEnc);
            // set back-reference so coEnc.getIndividual() works when loaded from occurrence
            viewableCompanionEnc.setIndividual(viewableCompanionIndiv);
            sh.storeNewMarkedIndividual(viewableCompanionIndiv);

            MarkedIndividual focalIndivViewable = new MarkedIndividual("FocalForViewableOcc",
                focalEncViewable);
            focalEncViewable.setIndividual(focalIndivViewable);
            sh.storeNewMarkedIndividual(focalIndivViewable);
            focalIndivIdWithViewableCompanion = focalIndivViewable.getId();

            Occurrence occViewable = new Occurrence("occ-viewable-companion-001");
            occViewable.addEncounterAndUpdateIt(focalEncViewable);
            occViewable.addEncounterAndUpdateIt(viewableCompanionEnc);
            sh.storeNewOccurrence(occViewable);

            // --- relationship test: viewable partner ---
            // focal individual with encounter owned by seededOwner; partner also owned by seededOwner
            Encounter relFocalEnc = new Encounter();
            relFocalEnc.setSubmitterID(seededOwnerUsername);
            sh.storeNewEncounter(relFocalEnc);
            MarkedIndividual relFocal = new MarkedIndividual("RelFocal", relFocalEnc);
            sh.storeNewMarkedIndividual(relFocal);
            focalIndivWithViewableRel = relFocal.getId();

            Encounter viewablePartnerEnc = new Encounter();
            viewablePartnerEnc.setSubmitterID(seededOwnerUsername);
            sh.storeNewEncounter(viewablePartnerEnc);
            MarkedIndividual viewablePartner = new MarkedIndividual("ViewablePartner", viewablePartnerEnc);
            sh.storeNewMarkedIndividual(viewablePartner);

            org.ecocean.social.Relationship viewableRel = new org.ecocean.social.Relationship(
                "mother-calf", relFocal, viewablePartner, "mother", "calf");
            sh.getPM().makePersistent(viewableRel);

            // --- relationship test: hidden partner (owned by stranger, not viewable by focalOwner) ---
            Encounter hiddenRelFocalEnc = new Encounter();
            hiddenRelFocalEnc.setSubmitterID(seededOwnerUsername);
            sh.storeNewEncounter(hiddenRelFocalEnc);
            MarkedIndividual hiddenRelFocal = new MarkedIndividual("HiddenRelFocal", hiddenRelFocalEnc);
            sh.storeNewMarkedIndividual(hiddenRelFocal);
            focalIndivWithHiddenPartnerRel = hiddenRelFocal.getId();

            Encounter hiddenPartnerEnc = new Encounter();
            hiddenPartnerEnc.setSubmitterID(strangerUsername);
            sh.storeNewEncounter(hiddenPartnerEnc);
            MarkedIndividual hiddenPartner = new MarkedIndividual("HiddenPartner", hiddenPartnerEnc);
            sh.storeNewMarkedIndividual(hiddenPartner);
            hiddenPartnerId = hiddenPartner.getId();

            org.ecocean.social.Relationship hiddenRel = new org.ecocean.social.Relationship(
                "associate", hiddenRelFocal, hiddenPartner);
            sh.getPM().makePersistent(hiddenRel);

            sh.commitDBTransaction();
        } catch (Exception e) {
            sh.rollbackDBTransaction();
            throw e;
        } finally {
            sh.closeDBTransaction();
        }
    }

    @AfterAll
    static void tearDown() {
        TestPMFUtil.closePMF("context0");
    }

    /**
     * Invokes doGet with a mocked request/response pair. The Shepherd
     * constructed inside doGet is intercepted via MockedConstruction so that
     * direct doGet() works without a live Wildbook deployment. ACL-relevant
     * methods are pre-fetched from a real Shepherd backed by the test container
     * BEFORE entering MockedConstruction (to avoid recursive interception of
     * inner {@code new Shepherd(...)} calls), then returned as pre-loaded values.
     * This drives real ACL evaluation on real domain objects (canUserView runs
     * against the real MarkedIndividual + Encounter data).
     *
     * @param authUser the User that should appear authenticated (null = anonymous)
     * @param idParam  the ?id= query parameter value
     */
    static JSONObject invoke(User authUser, String idParam) throws Exception {
        // Determine the username from the authUser object. We use seededOwnerUsername /
        // strangerUsername (stored before JDO PM close) when possible to avoid JDO
        // detached-field access issues on the closed-PM User objects.
        final String lookupUsername;
        if (authUser == null) {
            lookupUsername = null;
        } else if (authUser == seededOwner) {
            lookupUsername = seededOwnerUsername;
        } else if (authUser == stranger) {
            lookupUsername = strangerUsername;
        } else {
            lookupUsername = authUser.getUsername();
        }

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        when(req.getRequestURI()).thenReturn("/api/v3/individuals/info/social-data");
        when(req.getParameter("id")).thenReturn(idParam);
        // getContext() falls back to "context0" when servletContext is null
        when(req.getServletContext()).thenReturn(null);
        when(req.getContextPath()).thenReturn("");
        when(req.getCookies()).thenReturn(null);
        when(req.getServerName()).thenReturn("localhost");
        when(req.getParameter("context")).thenReturn(null);

        // Wire getUserPrincipal so that Shepherd.getUsername(request) resolves correctly.
        // We use the pre-resolved lookupUsername to avoid JDO detachment issues.
        if (authUser != null && lookupUsername != null) {
            Principal principal = mock(Principal.class);
            when(principal.toString()).thenReturn(lookupUsername);
            when(req.getUserPrincipal()).thenReturn(principal);
        } else {
            when(req.getUserPrincipal()).thenReturn(null);
        }

        // Open a real Shepherd BEFORE MockedConstruction and keep it open for the duration
        // of doGet() so that JDO-managed objects (MarkedIndividual.encounters, Encounter fields)
        // are still accessible when canUserView() traverses them.
        // We must NOT call new Shepherd() inside the MockedConstruction configurator lambda
        // because MockedConstruction intercepts ALL new Shepherd() calls in the JVM.
        final Shepherd backingShepherd = new Shepherd("context0", props);
        backingShepherd.beginDBTransaction();
        try {
            final User resolvedUser = (lookupUsername == null) ? null
                : backingShepherd.getUser(lookupUsername);
            final java.util.List<Role> resolvedRoles =
                (resolvedUser == null) ? java.util.Collections.emptyList()
                : backingShepherd.getAllRolesForUserInContext(lookupUsername, "context0");

            // Intercept new Shepherd("context0") so doGet can run without the real app DB.
            // All ACL-relevant methods delegate to the open backingShepherd.
            try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                    (mock, ctx) -> {
                        // no-op lifecycle (backingShepherd manages the real transaction)
                        doNothing().when(mock).setAction(anyString());
                        doNothing().when(mock).beginDBTransaction();
                        doNothing().when(mock).rollbackDBTransaction();
                        doNothing().when(mock).closeDBTransaction();
                        when(mock.getContext()).thenReturn("context0");

                        // Return user from the open backing shepherd
                        when(mock.getUser(any(HttpServletRequest.class))).thenReturn(resolvedUser);

                        // Delegate individual lookup to the open backing shepherd
                        // (JDO objects are live because backingShepherd's PM is still open)
                        when(mock.getMarkedIndividual(anyString())).thenAnswer(inv -> {
                            String id = (String) inv.getArgument(0);
                            if (id == null) return null;
                            return backingShepherd.getMarkedIndividual(id.trim());
                        });

                        // Return pre-fetched roles (drives user.isAdmin(shepherd))
                        when(mock.getAllRolesForUserInContext(anyString(), anyString()))
                            .thenReturn(resolvedRoles);

                        // Expose the backing PM so Collaboration.collaborationBetweenUsers()
                        // (called via Encounter.canUserAccess -> canCollaborate) can run
                        // its JDO queries without a null PM.
                        when(mock.getPM()).thenReturn(backingShepherd.getPM());

                        // Delegate doesUserHaveRole (used inside collaborationBetweenUsers)
                        when(mock.doesUserHaveRole(anyString(), anyString(), anyString()))
                            .thenAnswer(inv -> backingShepherd.doesUserHaveRole(
                                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

                        // Delegate getOccurrence so occurringWith can load co-occurrences
                        when(mock.getOccurrence(anyString()))
                            .thenAnswer(inv -> backingShepherd.getOccurrence(
                                (String) inv.getArgument(0)));
                        when(mock.getOccurrence(any(Encounter.class)))
                            .thenAnswer(inv -> backingShepherd.getOccurrence(
                                (Encounter) inv.getArgument(0)));
                        // Delegate getAllRelationshipsForMarkedIndividual
                        when(mock.getAllRelationshipsForMarkedIndividual(anyString()))
                            .thenAnswer(inv -> backingShepherd.getAllRelationshipsForMarkedIndividual(
                                (String) inv.getArgument(0)));
                    })) {

                new MarkedIndividualInfo().doGet(req, resp);
            }
        } finally {
            backingShepherd.rollbackDBTransaction();
            backingShepherd.closeDBTransaction();
        }

        String body = out.toString();
        assertFalse(body.isEmpty(), "Response body must not be empty");
        return new JSONObject(body);
    }

    @Test
    void anonymousIs401() throws Exception {
        JSONObject j = invoke(null, "any-id");
        assertEquals(401, j.optInt("statusCode", -1),
            "Anonymous request must return 401; got: " + j);
    }

    @Test
    void blankIdIs400() throws Exception {
        JSONObject j = invoke(seededOwner, "");
        assertEquals(400, j.optInt("statusCode", -1),
            "Blank id must return 400; got: " + j);
    }

    @Test
    void unknownIdIs404() throws Exception {
        JSONObject j = invoke(seededOwner, "00000000-0000-0000-0000-000000000000");
        assertEquals(404, j.optInt("statusCode", -1),
            "Unknown id must return 404; got: " + j);
    }

    @Test
    void notViewableIs403() throws Exception {
        // stranger has no collaboration with owner, so cannot view the seeded individual
        JSONObject j = invoke(stranger, seededIndivId);
        assertEquals(403, j.optInt("statusCode", -1),
            "Non-viewable individual must return 403; got: " + j);
    }

    @Test
    void viewableReturns200WithArrays() throws Exception {
        JSONObject j = invoke(seededOwner, seededIndivId);
        assertEquals(200, j.optInt("statusCode", -1),
            "Owner must get 200; got: " + j);
        assertTrue(j.has("encounters"), "Response must have 'encounters' array");
        assertTrue(j.has("relationships"), "Response must have 'relationships' array");
    }

    @Test
    void onlyViewableEncountersReturned() throws Exception {
        // mixedIndiv has one private (owner-only) + one public encounter.
        // Owner sees both; stranger (no collab) sees only the public one.
        JSONObject asOwner = invoke(seededOwner, mixedIndivId);
        JSONObject asStranger = invoke(stranger, mixedIndivId);
        assertTrue(asOwner.getJSONArray("encounters").length()
                   > asStranger.getJSONArray("encounters").length());
    }

    @Test
    void dataTypesBothWhenTissueAndAnnotation() throws Exception {
        JSONObject j = invoke(seededOwner, indivWithTissueAndAnnotId);
        String dt = j.getJSONArray("encounters").getJSONObject(0).getString("dataTypes");
        assertEquals("both", dt);
    }

    @Test
    void occurringWithExcludesNonViewableCompanion() throws Exception {
        JSONObject j = invoke(focalOwner, focalIndivIdInSharedOcc);
        org.json.JSONArray encs = j.getJSONArray("encounters");
        for (int i = 0; i < encs.length(); i++) {
            String ow = encs.getJSONObject(i).optString("occurringWith", "");
            assertFalse(ow.contains(hiddenCompanionDisplayName),
                "non-viewable companion must not leak into occurringWith");
        }
    }

    @Test
    void occurringWithIncludesViewableCompanion() throws Exception {
        JSONObject j = invoke(focalOwner, focalIndivIdWithViewableCompanion);
        String ow = j.getJSONArray("encounters").getJSONObject(0).optString("occurringWith", "");
        assertTrue(ow.contains(viewableCompanionDisplayName));
    }

    @Test void relationshipIdRoundTripsThroughGetObjectById() throws Exception {
        JSONObject j = invoke(focalOwner, focalIndivWithViewableRel);
        // _id is the numeric datastore key (stripped of the "[OID]..." suffix).
        // DataNucleus getObjectById(Class, Object) accepts the numeric string for increment-strategy tables.
        // Note: getObjectById(Class, fullOIDString) would fail because it tries to parse the string
        // as a Long — the "[OID]..." suffix is only valid for the no-class 1-arg getObjectById overload.
        String id = j.getJSONArray("relationships").getJSONObject(0).getString("_id");
        Shepherd s = new Shepherd("context0", props);
        s.beginDBTransaction();
        try {
            org.ecocean.social.Relationship rel =
                (org.ecocean.social.Relationship) s.getPM()
                    .getObjectById(org.ecocean.social.Relationship.class, id);
            assertNotNull(rel, "emitted _id must resolve the same Relationship the legacy "
                + "edit/remove path resolves");
        } finally { s.rollbackAndClose(); }
    }

    @Test void relationshipToNonViewablePartnerOmitted() throws Exception {
        JSONObject j = invoke(focalOwner, focalIndivWithHiddenPartnerRel);
        // only the viewable-partner relationship is present
        org.json.JSONArray rels = j.getJSONArray("relationships");
        for (int i = 0; i < rels.length(); i++) {
            String pid = rels.getJSONObject(i).getJSONObject("partner").getString("individualID");
            assertNotEquals(hiddenPartnerId, pid);
        }
    }
}
