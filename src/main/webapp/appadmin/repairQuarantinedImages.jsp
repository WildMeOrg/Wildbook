<%@ page contentType="text/plain; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,
org.ecocean.shepherd.core.Shepherd,
org.ecocean.media.MediaAsset,
org.ecocean.media.MediaAssetFactory,
org.ecocean.ia.plugin.WildbookIAM,
org.ecocean.identity.IBEISIA,
org.ecocean.Annotation,
org.ecocean.Util,
javax.jdo.Query,
java.util.ArrayList,
java.util.Collection,
java.util.HashMap,
java.util.HashSet,
java.util.List,
java.util.Map,
java.util.Set,
org.json.JSONArray,
org.json.JSONObject" %>
<%
/*
 * Repair MediaAssets stuck at validImageForIA == false.
 *
 * WHY THIS EXISTS
 * ---------------
 * validImageForIA == false is terminal in practice. WildbookIAM.sendMediaAssets refuses to
 * register a flagged asset with WBIA, AcmIdBot's reconciliation sweep drops flagged assets
 * before they are even probed, and nothing re-validates them (MediaAsset
 * .validateSourceImage() is only consulted when the flag is null). Meanwhile
 * WildbookIAM.sendAnnotations does NOT check the flag, so annotations on such an asset are
 * still POSTed and WBIA rejects them with "image_uuid_list has invalid values [(0, None)]".
 * A stale flag therefore actively breaks identification rather than merely hiding a bad
 * image. The HTTP-500 path that could create these flags has been removed, but the rows it
 * created remain.
 *
 * WHAT THIS DOES
 * --------------
 * Re-decodes each flagged asset's real file and lets the file decide the flag. Then, for
 * assets that now decode, asks WBIA which acmIds it does not know and registers those.
 *
 * THE CENTRAL SAFETY RULE: the flag is cleared ONLY together with a confirmed WBIA state --
 * either WBIA already has the image, or it confirms the registration we just sent. A local
 * decode proves ImageIO can read the file; it does NOT prove WBIA can fetch or accept it.
 * Clearing the flag on a local decode alone would make a transient WBIA failure permanent,
 * because the asset would no longer match this JSP's own selection filter and so could
 * never be retried.
 *
 * Each asset is processed in its OWN transaction, so one failure cannot roll back or flush
 * another, and commits are checked with commitDBTransactionWithStatus() because
 * commitDBTransaction() swallows JDO failures and would let us report a durable success
 * that never happened.
 *
 * PARAMS
 *   max=<n>         assets to consider this run (default 100)
 *   commit=true     persist and register; DEFAULT IS A DRY RUN
 *   probe=true      in a dry run, also ask WBIA which acmIds it lacks (remote GETs, read
 *                   only). Off by default so a dry run touches nothing remote.
 *   scope=matchable only assets backing a matchAgainst annotation (DEFAULT), since those
 *                   are the ones identification needs. scope=all is an explicit,
 *                   logged widening.
 *   assetId=<id>    operate on exactly one asset; it must still be flagged.
 *   verbose=true    include a per-asset detail array.
 *
 * validateSourceImage() only inspects the file for LOCAL stores; on any other store it
 * cannot decide, and such assets are reported "unrevalidatable" rather than guessed.
 */

String context = ServletUtilities.getContext(request);

int max = 100;
String maxParam = request.getParameter("max");
if (maxParam != null) {
    try {
        max = Integer.parseInt(maxParam.trim());
    } catch (NumberFormatException nfe) {
        response.setStatus(400);
        out.println(new JSONObject().put("error", "invalid max: " + maxParam).toString(4));
        return;
    }
    if (max < 1) {
        response.setStatus(400);
        out.println(new JSONObject().put("error", "max must be positive; got " + maxParam).toString(4));
        return;
    }
}
Integer singleAssetId = null;
String assetIdParam = request.getParameter("assetId");
if (assetIdParam != null) {
    try {
        singleAssetId = Integer.valueOf(assetIdParam.trim());
    } catch (NumberFormatException nfe) {
        response.setStatus(400);
        out.println(new JSONObject().put("error", "invalid assetId: " + assetIdParam).toString(4));
        return;
    }
}
String scope = request.getParameter("scope");
if (scope == null) scope = "matchable";
if (!"matchable".equals(scope) && !"all".equals(scope)) {
    response.setStatus(400);
    out.println(new JSONObject().put("error", "scope must be 'matchable' or 'all'").toString(4));
    return;
}
boolean commit     = "true".equalsIgnoreCase(request.getParameter("commit"));
boolean probeOnly  = "true".equalsIgnoreCase(request.getParameter("probe"));
boolean doProbe    = commit || probeOnly;
boolean verbose    = "true".equalsIgnoreCase(request.getParameter("verbose"));
boolean matchableOnly = "matchable".equals(scope);

JSONObject result = new JSONObject();
result.put("dryRun", !commit);
result.put("scope", scope);
result.put("willContactWbia", doProbe);
JSONArray detail = new JSONArray();

int considered = 0, outOfScope = 0, wouldClear = 0, stillInvalid = 0, unrevalidatable = 0;
int gone = 0, errored = 0, alreadyAtWbia = 0, registered = 0, notConfirmed = 0, commitFailed = 0;
int ambiguous = 0;

if ("all".equals(scope)) {
    System.out.println("WARNING: repairQuarantinedImages.jsp running with scope=all "
        + "(assets with no matchAgainst annotation included), commit=" + commit);
}

// ---- phase 1: select flagged asset ids (short tx, primitives out) ----
List<Integer> ids = new ArrayList<Integer>();
String phase1Error = null;
Shepherd readShepherd = null;
Query q = null;
try {
    readShepherd = new Shepherd(context);
    readShepherd.setAction("appadmin.repairQuarantinedImages.read");
    readShepherd.beginDBTransaction();
    // Always filter on the flag, even for a single assetId: this JSP's whole contract is
    // "repair quarantined assets", and it must not be usable to re-send an arbitrary asset.
    String filter = "validImageForIA == false";
    if (singleAssetId != null) filter += " && id == " + singleAssetId.intValue();
    q = readShepherd.getPM().newQuery(MediaAsset.class, filter);
    q.setResult("id");
    q.setOrdering("id ascending");
    if (singleAssetId == null) q.setRange(0, max);
    Collection c = (Collection)q.execute();
    for (Object o : c) {
        if (o != null) ids.add(Integer.valueOf(((Number)o).intValue()));
    }
} catch (Exception ex) {
    phase1Error = ex.toString();
} finally {
    try {
        if (q != null) q.closeAll();
    } finally {
        if (readShepherd != null) readShepherd.rollbackAndClose();
    }
}
if (phase1Error != null) {
    response.setStatus(500);
    out.println(new JSONObject().put("error", "read phase failed: " + phase1Error).toString(4));
    return;
}
result.put("flaggedAssetsSelected", ids.size());
if ((singleAssetId != null) && ids.isEmpty()) {
    result.put("note", "asset " + singleAssetId
        + " is not currently flagged validImageForIA=false; nothing to repair");
    out.println(result.toString(4));
    return;
}

// ---- phase 2: decide candidacy WITHOUT persisting anything ----
// Each asset gets its own transaction and is always rolled back here. validateSourceImage()
// writes the flag on the managed instance, so the prior value is restored immediately (no PM
// operation happens in between, so there is no flush point) and the rollback is a second
// line of defence.
List<Integer> candidateIds = new ArrayList<Integer>();
List<String> probeAcmIds = new ArrayList<String>();   // deduped, well-formed only
Set<String> probeSeen = new HashSet<String>();
// The exact acmId each asset carried when we probed for it. Phase 4 will only accept
// "WBIA already has this" if the asset STILL carries that same value -- probe evidence has
// to be bound to the state we later commit, or a value that changed underneath us (another
// thread, a sweep heal) would be treated as proven present though it was never asked about.
Map<Integer, String> probedAcmIdByAsset = new HashMap<Integer, String>();
// acmId is explicitly NOT unique (MediaAssetFactory.loadByAcmId returns the oldest of
// several). If two candidates share one, a single "present at WBIA" answer cannot tell us
// WHICH local asset is the registered image, so neither may be auto-cleared.
Map<String, Integer> candidatesPerAcmId = new HashMap<String, Integer>();

for (Integer id : ids) {
    considered++;
    JSONObject row = new JSONObject();
    row.put("assetId", id);
    Shepherd sh = null;
    try {
        sh = new Shepherd(context);
        sh.setAction("appadmin.repairQuarantinedImages.check." + id);
        sh.beginDBTransaction();
        MediaAsset ma = MediaAssetFactory.load(id.intValue(), sh);
        if (ma == null) {
            row.put("outcome", "gone");
            gone++;
        } else {
            String storeType = null;
            try {
                if (ma.getStore() != null) storeType = ma.getStore().getType().toString();
            } catch (Exception ignore) { }
            row.put("store", (storeType == null) ? "unknown" : storeType);
            boolean inScope = true;
            if (matchableOnly) {
                inScope = false;
                for (Annotation ann : ma.getAnnotations()) {
                    if ((ann != null) && ann.getMatchAgainst()) { inScope = true; break; }
                }
            }
            if (!inScope) {
                row.put("outcome", "outOfScope");
                outOfScope++;
            } else if (!"LOCAL".equals(storeType)) {
                row.put("outcome", "unrevalidatable");
                unrevalidatable++;
            } else {
                Boolean priorFlag = ma.isValidImageForIA();
                boolean decodes = ma.validateSourceImage();
                ma.setIsValidImageForIA(priorFlag);   // leave the instance clean
                if (decodes) {
                    wouldClear++;
                    candidateIds.add(id);
                    row.put("outcome", "decodesOk");
                    String acm = ma.getAcmId();
                    row.put("acmId", acm);
                    if (Util.stringExists(acm) && Util.isUUID(acm)) {
                        if (probeSeen.add(acm)) probeAcmIds.add(acm);
                        probedAcmIdByAsset.put(id, acm);
                        Integer seen = candidatesPerAcmId.get(acm);
                        candidatesPerAcmId.put(acm,
                            Integer.valueOf((seen == null) ? 1 : seen.intValue() + 1));
                    } else {
                        row.put("acmIdNote", "null/malformed; will be assigned on register");
                    }
                } else {
                    stillInvalid++;
                    row.put("outcome", "stillInvalid");
                }
            }
        }
    } catch (Exception ex) {
        errored++;
        row.put("outcome", "error");
        row.put("error", ex.toString());
        System.out.println("repairQuarantinedImages: check failed for asset " + id + ": " + ex);
    } finally {
        if (sh != null) sh.rollbackAndClose();   // phase 2 NEVER persists
    }
    if (verbose) detail.put(row);
}

result.put("considered", considered);
result.put("outOfScope", outOfScope);
result.put("decodesOk", wouldClear);
result.put("stillInvalid", stillInvalid);
result.put("unrevalidatable", unrevalidatable);
result.put("gone", gone);
result.put("errored", errored);

// ---- phase 3: ask WBIA which acmIds it lacks (no transaction open) ----
Set<String> missingAtWbia = new HashSet<String>();
boolean probeOk = false;
if (doProbe && !probeAcmIds.isEmpty()) {
    try {
        missingAtWbia.addAll(WildbookIAM.iaMissingImageIds(probeAcmIds, context));
        probeOk = true;
    } catch (Exception ex) {
        result.put("probeError", ex.toString());
    }
    result.put("probedAtWbia", probeAcmIds.size());
    result.put("missingFromWbia", probeOk ? missingAtWbia.size() : -1);
}

// ---- phase 4: per asset, register if needed and clear the flag only on confirmed state ----
if (commit) {
    if (!probeOk && !probeAcmIds.isEmpty()) {
        result.put("abort", "WBIA probe failed; refusing to clear any flag without knowing "
            + "WBIA's state. Nothing was changed. Retry when WBIA is reachable.");
    } else {
        for (Integer id : candidateIds) {
            JSONObject row = new JSONObject();
            row.put("assetId", id);
            Shepherd sh = null;
            boolean ok = false;
            try {
                sh = new Shepherd(context);
                sh.setAction("appadmin.repairQuarantinedImages.repair." + id);
                sh.beginDBTransaction();
                // reload by PRIMARY KEY: acmId is explicitly not unique
                // (MediaAssetFactory.loadByAcmId returns the oldest match), so keying phase
                // 4 off acmId could repair a different row than the one we examined.
                MediaAsset ma = MediaAssetFactory.load(id.intValue(), sh);
                if (ma == null) {
                    row.put("outcome", "goneBeforeRepair");
                    gone++;
                } else if (!ma.validateSourceImage()) {
                    // Re-decided under this transaction and it does not decode after all
                    // (it decoded in phase 2, so the file changed underneath us, or the
                    // read was flaky). The flag is already false and must stay false, so
                    // there is nothing to persist -- fall through to the finally's rollback
                    // rather than committing a no-op write.
                    stillInvalid++;
                    row.put("outcome", "stillInvalidOnRecheck");
                } else {
                    String priorAcmId = ma.getAcmId();
                    String probedAcmId = probedAcmIdByAsset.get(id);
                    Integer shared = (probedAcmId == null)
                        ? null : candidatesPerAcmId.get(probedAcmId);
                    // acmId is explicitly NOT unique. Counting duplicates only among this
                    // run's candidates is not enough: a sharing asset outside this page (or
                    // not flagged at all) would still make WBIA's "present" answer ambiguous,
                    // since it could belong to that other asset. So count ALL assets holding
                    // this acmId, inside this same transaction. A failed count is treated as
                    // ambiguous -- fail closed rather than clear a flag on an unverified
                    // uniqueness assumption.
                    int holdersOfAcmId = 1;
                    if (Util.stringExists(priorAcmId)) {
                        Query dupQ = null;
                        try {
                            dupQ = sh.getPM().newQuery(MediaAsset.class, "acmId == :a");
                            dupQ.setResult("count(id)");
                            Object cnt = dupQ.execute(priorAcmId);
                            holdersOfAcmId = (cnt instanceof Number)
                                ? ((Number)cnt).intValue() : -1;
                        } catch (Exception dex) {
                            holdersOfAcmId = -1;
                            System.out.println("repairQuarantinedImages: duplicate-acmId check"
                                + " failed for asset " + id + " (" + priorAcmId + "): " + dex);
                        } finally {
                            if (dupQ != null) dupQ.closeAll();
                        }
                    }
                    boolean acmIdAmbiguous = Util.stringExists(priorAcmId)
                        && (holdersOfAcmId != 1);
                    if (((shared != null) && (shared.intValue() > 1)) || acmIdAmbiguous) {
                        // Two or more assets carry this acmId (or the count could not be
                        // established). One "present at WBIA" answer cannot say which asset
                        // is the registered image, and re-POSTing would overwrite whichever
                        // one WBIA holds. Leave it flagged and surface it for a human.
                        ambiguous++;
                        row.put("outcome", "ambiguousDuplicateAcmId");
                        row.put("acmId", priorAcmId);
                        row.put("assetsHoldingThisAcmId", holdersOfAcmId);
                        if (shared != null) row.put("sharedByCandidatesThisRun", shared);
                        System.out.println("repairQuarantinedImages: asset " + id + " acmId "
                            + priorAcmId + " is held by " + holdersOfAcmId
                            + " asset(s) (-1 = count failed); leaving flagged for manual"
                            + " reconciliation");
                    } else {
                    // "WBIA already has it" may ONLY be concluded when the asset still
                    // carries the exact acmId we probed. Anything else -- null, malformed,
                    // never probed, or changed since phase 2 -- is unproven and falls through
                    // to the register branch, which POSTs and demands confirmation.
                    boolean provenPresent = Util.stringExists(priorAcmId)
                        && Util.isUUID(priorAcmId)
                        && priorAcmId.equals(probedAcmId)
                        && !missingAtWbia.contains(priorAcmId);
                    if (provenPresent) {
                        // clearing the flag here is backed by a probe of this exact value
                        row.put("outcome", "clearedFlagWbiaAlreadyHadImage");
                        ok = sh.commitDBTransactionWithStatus();
                        if (ok) {
                            alreadyAtWbia++;
                        } else {
                            commitFailed++;
                            row.put("outcome", "wbiaHadImageButCommitFailed");
                        }
                    } else {
                        if (!Util.stringExists(priorAcmId) || !Util.isUUID(priorAcmId))
                            ma.setAcmId(ma.getUUID());
                        ArrayList<MediaAsset> one = new ArrayList<MediaAsset>();
                        one.add(ma);
                        // NOTE: deliberately no updateStandardChildren() here. It writes
                        // files into the asset store and creates child MediaAssets as a
                        // side effect; those writes are not rollbackable and would happen
                        // even when the POST below fails. The URI WBIA receives is the
                        // parent's webURL, so children are not needed for registration.
                        JSONObject sendRtn = IBEISIA.sendMediaAssetsNew(one, context, false);
                        // Confirmation is inlined rather than calling
                        // AcmIdBot.sendConfirmedAcmId so this page is STANDALONE: it can be
                        // dropped into a running install that does not yet have this
                        // branch's Java deployed, which is the whole point of a repair tool.
                        // Mirrors that method exactly. sendMediaAssets returns
                        // {"batchResults":[{"response":[{"__UUID__":"..."}]}]} and a heal
                        // only counts when OUR uuid comes back in it -- never persist a
                        // registration WBIA did not acknowledge.
                        String expectAcm = ma.getAcmId();
                        JSONArray batches = (sendRtn == null)
                            ? null : sendRtn.optJSONArray("batchResults");
                        boolean confirmed = false;
                        if ((batches != null) && (expectAcm != null)) {
                            for (int b = 0; (b < batches.length()) && !confirmed; b++) {
                                // a skipped batch is the literal string "EMPTY BATCH", so
                                // optJSONObject returns null for it
                                JSONObject batch = batches.optJSONObject(b);
                                JSONArray resp = (batch == null)
                                    ? null : batch.optJSONArray("response");
                                if (resp == null) continue;
                                for (int i = 0; i < resp.length(); i++) {
                                    JSONObject fancy = resp.optJSONObject(i);
                                    if ((fancy != null) && expectAcm.equals(
                                            fancy.optString("__UUID__", null))) {
                                        confirmed = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (confirmed) {
                            // rectifyMediaAssetIds may have adopted WBIA's UUID. acmId IS in
                            // the MediaAsset OpenSearch document but setAcmId does not bump
                            // revision, so bump it or the index keeps the stale value.
                            if ((priorAcmId == null) || !priorAcmId.equals(ma.getAcmId())) {
                                row.put("acmIdChanged", priorAcmId + " -> " + ma.getAcmId());
                                ma.setRevision();
                            }
                            ok = sh.commitDBTransactionWithStatus();
                            if (ok) {
                                registered++;
                                row.put("outcome", "registeredAndFlagCleared");
                            } else {
                                commitFailed++;
                                row.put("outcome", "registeredButCommitFailed");
                            }
                        } else {
                            // WBIA did not acknowledge: roll back so the flag STAYS false
                            // and this asset is selected again on a later run
                            notConfirmed++;
                            row.put("outcome", "notConfirmedByWbiaLeftFlagged");
                            System.out.println("repairQuarantinedImages: WBIA did not confirm asset "
                                + id + "; leaving it flagged for retry");
                        }
                    }
                    }
                }
            } catch (Exception ex) {
                errored++;
                row.put("outcome", "error");
                row.put("error", ex.toString());
                System.out.println("repairQuarantinedImages: repair failed for asset " + id
                    + ": " + ex);
            } finally {
                // rollbackAndClose is a no-op rollback after a successful commit, and
                // discards everything on any unconfirmed or failed path
                if (sh != null) sh.rollbackAndClose();
            }
            if (verbose) detail.put(row);
        }
    }
}

result.put("clearedBecauseWbiaAlreadyHadImage", alreadyAtWbia);
result.put("registeredWithWbiaAndCleared", registered);
result.put("notConfirmedLeftFlagged", notConfirmed);
result.put("ambiguousDuplicateAcmIdLeftFlagged", ambiguous);
result.put("commitFailed", commitFailed);

if (!commit) {
    result.put("note", "DRY RUN: nothing persisted, nothing registered. 'decodesOk' counts "
        + "assets whose files really were decoded just now and which would be candidates. "
        + "Re-run with &commit=true to apply."
        + (doProbe ? "" : " Add &probe=true to also ask WBIA what it is missing."));
}
result.put("safetyNote", "A flag is only ever cleared together with confirmed WBIA state "
    + "(image already present, or registration acknowledged). Assets left flagged -- "
    + "stillInvalid or notConfirmed -- are selected again on the next run, by design.");
if (verbose) result.put("detail", detail);

out.println(result.toString(4));
%>
