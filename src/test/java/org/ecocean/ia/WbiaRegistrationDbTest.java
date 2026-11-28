package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.identity.IBEISIA;
import org.ecocean.ia.plugin.WildbookIAM;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** Pending WBIA registrations and their flags survive real datastore commits/reloads. */
class WbiaRegistrationDbTest extends IdentificationDbTestSupport {
    @Test void fixtureRelationshipsSurviveReload() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY);
        String annId = annotationIds(e1).get(0);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        Shepherd check = open("check");
        try {
            Annotation ann = check.getAnnotation(annId);
            assertNotNull(ann.getMediaAsset(), "the bbox feature links the annotation to its asset");
            assertEquals(1, ann.getFeatures().size());
            assertTrue(Arrays.equals(new int[] { 1, 1, 10, 10 }, ann.getBbox()));
            assertFalse(ann.isTrivial());
            assertEquals(e1.getCatalogNumber(), ann.findEncounter(check).getCatalogNumber());
            assertNotNull(ann.getTaxonomy(check), "identOpts resolves the taxonomy through the encounter");
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void realIdentifyRegistersPendingAnnotationsAndPersistsTheFlags() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, BODY, BODY);
        List<String> ids = annotationIds(e1);
        Annotation q = sh.getAnnotation(ids.get(0));
        q.setWbiaRegistered(Boolean.FALSE);
        q.getMediaAsset().setAcmId(null);                    // minted at identify time
        String qAssetUuid = q.getMediaAsset().getUUID();
        String qAcm = q.getAcmId();
        Annotation t = sh.getAnnotation(ids.get(1));
        t.setWbiaRegistered(Boolean.FALSE);
        String tAcm = t.getAcmId();
        String tAssetAcm = t.getMediaAsset().getAcmId();
        Annotation k = sh.getAnnotation(ids.get(2));
        k.setWbiaRegistered(Boolean.TRUE);                    // already registered: legacy path, known to WBIA
        String taskId = identifyTaskFor(sh, q);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        WbiaFake wbia = new WbiaFake();
        wbia.knownAnnotIds.add(acmOf(e1, 2));          // snapshots: k's objects are detached by now
        wbia.knownImageIds.add(assetAcmOf(e1, 2));
        Shepherd consumer = open("consumer");
        try {
            withRealWbia(wbia, () -> {
                ArrayList<Annotation> qanns = new ArrayList<Annotation>(Arrays.asList(consumer.getAnnotation(ids.get(0))));
                ArrayList<Annotation> tanns = new ArrayList<Annotation>(Arrays.asList(
                    consumer.getAnnotation(ids.get(1)), consumer.getAnnotation(ids.get(2))));
                JSONObject results = IBEISIA.beginIdentifyAnnotations(qanns, tanns, new JSONObject(), null,
                    consumer, Task.load(taskId, consumer), "http://wb", false);
                assertTrue(results.getBoolean("success"), results.toString());
            });
            consumer.commitDBTransaction();
        } finally {
            consumer.closeDBTransaction();
        }
        List<JSONObject> images = wbia.imagePosts();
        List<JSONObject> annots = wbia.annotationPosts();
        assertEquals(2, images.size(), "one forced image POST per pending annotation");
        assertEquals(qAssetUuid, fancyAt(images.get(0), "image_uuid_list", 0), "q's image is forced under the minted acmId");
        assertEquals(tAssetAcm, fancyAt(images.get(1), "image_uuid_list", 0));
        assertEquals(2, annots.size(), "one forced annotation POST per pending annotation; none for the registered one");
        assertEquals(qAcm, fancyAt(annots.get(0), "annot_uuid_list", 0), "the query is forced under its acmId");
        assertEquals(qAssetUuid, fancyAt(annots.get(0), "image_uuid_list", 0));
        assertEquals(tAcm, fancyAt(annots.get(1), "annot_uuid_list", 0));
        assertEquals(1, wbia.sentTargets.size());
        assertEquals(Arrays.asList(ids.get(1), ids.get(2)), idsOf(wbia.sentTargets.get(0)), "the identify request lists the pruned targets");
        Shepherd check = open("check");
        try {
            assertEquals(Boolean.TRUE, check.getAnnotation(ids.get(0)).getWbiaRegistered(), "the query's flag is persisted");
            assertEquals(Boolean.TRUE, check.getAnnotation(ids.get(1)).getWbiaRegistered(), "the target's flag is persisted");
            assertEquals(qAcm, check.getAnnotation(ids.get(0)).getAcmId(), "the forced path never rewrites acmId");
            assertEquals(qAssetUuid, check.getAnnotation(ids.get(0)).getMediaAsset().getAcmId(), "the minted asset acmId is persisted");
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void realIdentifyFailsForAnUnregistrablePendingQueryAndDropsAnIneligibleTarget() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, BODY, BODY);
        List<String> ids = annotationIds(e1);
        for (String id : ids) sh.getAnnotation(id).setWbiaRegistered(Boolean.FALSE);
        String taskId = identifyTaskFor(sh, sh.getAnnotation(ids.get(0)));
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        // (a) the target ids.get(1) is ineligible -> dropped; the query and ids.get(2) register
        WbiaFake wbia = new WbiaFake();
        wbia.eligibilityOverride.put(ids.get(1), "missing media asset");
        Shepherd consumer = open("consumer");
        try {
            withRealWbia(wbia, () -> {
                JSONObject results = IBEISIA.beginIdentifyAnnotations(
                    new ArrayList<Annotation>(Arrays.asList(consumer.getAnnotation(ids.get(0)))),
                    new ArrayList<Annotation>(Arrays.asList(consumer.getAnnotation(ids.get(1)), consumer.getAnnotation(ids.get(2)))),
                    new JSONObject(), null, consumer, Task.load(taskId, consumer), "http://wb", false);
                assertTrue(results.getBoolean("success"), results.toString());
                assertEquals(ids.get(1), results.getJSONObject("sendAnnotationsAsNeeded").getJSONArray("droppedTargets").getString(0));
            });
            consumer.commitDBTransaction();
        } finally {
            consumer.closeDBTransaction();
        }
        assertEquals(2, wbia.annotationPosts().size(), "the query and the eligible target were forced; the dropped target was not");
        assertEquals(Arrays.asList(ids.get(2)), idsOf(wbia.sentTargets.get(0)), "the dropped target is absent from the request");

        // (b) WBIA echoes a different uuid for the query -> validation fails -> success=false, nothing sent, flag stays FALSE
        Shepherd reset = open("reset");
        try {
            reset.getAnnotation(ids.get(0)).setWbiaRegistered(Boolean.FALSE);   // as if the first job had never run
            reset.commitDBTransaction();
        } finally {
            reset.closeDBTransaction();
        }
        WbiaFake refusing = new WbiaFake();
        refusing.echoWrongAnnotationUuid = true;
        Shepherd consumer2 = open("consumer2");
        try {
            withRealWbia(refusing, () -> {
                JSONObject results = IBEISIA.beginIdentifyAnnotations(
                    new ArrayList<Annotation>(Arrays.asList(consumer2.getAnnotation(ids.get(0)))),
                    new ArrayList<Annotation>(Arrays.asList(consumer2.getAnnotation(ids.get(2)))),
                    new JSONObject(), null, consumer2, Task.load(taskId, consumer2), "http://wb", false);
                assertFalse(results.getBoolean("success"));
                assertTrue(results.getString("error").contains(ids.get(0)));
                assertEquals(Boolean.FALSE, consumer2.getAnnotation(ids.get(0)).getWbiaRegistered(),
                    "unconfirmed: the attached flag stays pending right after the failed identify");
            });
            consumer2.commitDBTransaction();     // commit, so nothing a failure path wrote can hide behind a rollback
        } finally {
            consumer2.closeDBTransaction();
        }
        assertTrue(refusing.sentTargets.isEmpty(), "no identify request was sent");
        assertEquals(1, refusing.annotationPosts().size(), "the refused POST was the only one");
        Shepherd check = open("check");
        try {
            assertEquals(Boolean.FALSE, check.getAnnotation(ids.get(0)).getWbiaRegistered(), "unconfirmed: the flag stays pending");
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void pendingAnnotationAlreadyPresentUnderItsAcmIdIsFlaggedTrueWithoutAPostAndPersisted() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, BODY);
        List<String> ids = annotationIds(e1);
        for (String id : ids) sh.getAnnotation(id).setWbiaRegistered(Boolean.FALSE);
        String qAcm = sh.getAnnotation(ids.get(0)).getAcmId();
        String tAcm = sh.getAnnotation(ids.get(1)).getAcmId();
        String taskId = identifyTaskFor(sh, sh.getAnnotation(ids.get(0)));
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        WbiaFake wbia = new WbiaFake();
        wbia.knownAnnotIds.addAll(Arrays.asList(qAcm, tAcm));
        Shepherd consumer = open("consumer");
        try {
            withRealWbia(wbia, () -> {
                JSONObject results = IBEISIA.beginIdentifyAnnotations(
                    new ArrayList<Annotation>(Arrays.asList(consumer.getAnnotation(ids.get(0)))),
                    new ArrayList<Annotation>(Arrays.asList(consumer.getAnnotation(ids.get(1)))),
                    new JSONObject(), null, consumer, Task.load(taskId, consumer), "http://wb", false);
                assertTrue(results.getBoolean("success"));
            });
            consumer.commitDBTransaction();
        } finally {
            consumer.closeDBTransaction();
        }
        assertTrue(wbia.posts.isEmpty(), "zero registration POSTs");
        assertEquals(Arrays.asList(ids.get(1)), idsOf(wbia.sentTargets.get(0)));
        Shepherd check = open("check");
        try {
            assertEquals(Boolean.TRUE, check.getAnnotation(ids.get(0)).getWbiaRegistered());
            assertEquals(Boolean.TRUE, check.getAnnotation(ids.get(1)).getWbiaRegistered());
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void legacyNullFlagAnnotationKeepsTodaysPathAndItsNullFlag() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, BODY);
        List<String> ids = annotationIds(e1);
        Annotation q = sh.getAnnotation(ids.get(0));
        q.setWbiaRegistered(null);                              // legacy row
        String qAcmBefore = q.getAcmId();
        String qAssetAcm = q.getMediaAsset().getAcmId();
        Annotation t = sh.getAnnotation(ids.get(1));
        t.setWbiaRegistered(Boolean.TRUE);
        String taskId = identifyTaskFor(sh, q);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        // WBIA knows the target and its image but not the legacy query nor its image
        WbiaFake wbia = new WbiaFake();
        wbia.knownAnnotIds.add(acmOf(e1, 1));          // snapshots: t's objects are detached by now
        wbia.knownImageIds.add(assetAcmOf(e1, 1));
        Shepherd consumer = open("consumer");
        try {
            withRealWbia(wbia, () -> {
                JSONObject results = IBEISIA.beginIdentifyAnnotations(
                    new ArrayList<Annotation>(Arrays.asList(consumer.getAnnotation(ids.get(0)))),
                    new ArrayList<Annotation>(Arrays.asList(consumer.getAnnotation(ids.get(1)))),
                    new JSONObject(), null, consumer, Task.load(taskId, consumer), "http://wb", false);
                assertTrue(results.getBoolean("success"), results.toString());
            });
            consumer.commitDBTransaction();
        } finally {
            consumer.closeDBTransaction();
        }
        assertEquals(1, wbia.imagePosts().size(), "today's non-forced image send");
        assertEquals(qAssetAcm, fancyAt(wbia.imagePosts().get(0), "image_uuid_list", 0));
        assertEquals(1, wbia.annotationPosts().size(), "today's non-forced annotation send");
        assertFalse(wbia.annotationPosts().get(0).has("annot_uuid_list"), "the legacy send lets WBIA mint the uuid");
        assertEquals(Arrays.asList(ids.get(1)), idsOf(wbia.sentTargets.get(0)));
        Shepherd check = open("check");
        try {
            Annotation qR = check.getAnnotation(ids.get(0));
            assertNull(qR.getWbiaRegistered(), "the legacy flag is untouched");
            assertFalse(qAcmBefore.equals(qR.getAcmId()), "today's path rewrote acmId to WBIA's minted uuid (the behaviour the forced path exists to avoid)");
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void iaCheckMissingForcedRepairPersistsTheFlagAndRetries() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY);
        String annId = annotationIds(e1).get(0);
        sh.getAnnotation(annId).setWbiaRegistered(Boolean.FALSE);
        String acm = sh.getAnnotation(annId).getAcmId();
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        WbiaFake wbia = new WbiaFake();
        Shepherd consumer = open("consumer");
        try {
            withRealWbia(wbia, () -> {
                assertTrue(IBEISIA.iaCheckMissing(missingAnnotationReply(acm), CTX, consumer), "repaired: try the identify again");
            });
            consumer.commitDBTransaction();
        } finally {
            consumer.closeDBTransaction();
        }
        assertEquals(1, wbia.annotationPosts().size());
        assertEquals(acm, fancyAt(wbia.annotationPosts().get(0), "annot_uuid_list", 0), "repaired under its acmId");
        Shepherd check = open("check");
        try {
            assertEquals(Boolean.TRUE, check.getAnnotation(annId).getWbiaRegistered(), "persisted by the consumer's commit");
        } finally {
            check.rollbackAndClose();
        }
        // a second report for the same (now TRUE) annotation takes the legacy branch: no forced POST, no retry
        WbiaFake again = new WbiaFake();
        again.knownAnnotIds.add(acm);
        Shepherd consumer2 = open("consumer2");
        try {
            withRealWbia(again, () -> {
                assertFalse(IBEISIA.iaCheckMissing(missingAnnotationReply(acm), CTX, consumer2));
            });
        } finally {
            consumer2.rollbackAndClose();
        }
        // the legacy branch re-sends the reported annotation through IBEISIA.__sendAnnotations
        // exactly as before (its POST carries no annot_theta_list); what must not happen is a
        // WildbookIAM (forced) registration POST — every WildbookIAM annotation POST carries
        // annot_theta_list — or a retry
        assertEquals(1, again.annotationPosts().size(), "the legacy resend happened exactly once");
        for (JSONObject post : again.annotationPosts()) {
            assertFalse(post.has("annot_theta_list"), "no forced annotation POST for a TRUE annotation");
        }
    }
}
