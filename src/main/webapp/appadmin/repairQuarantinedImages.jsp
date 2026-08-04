<%@ page contentType="text/plain; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,
org.ecocean.shepherd.core.Shepherd,
org.ecocean.media.MediaAsset,
org.ecocean.media.MediaAssetFactory,
org.ecocean.ia.plugin.WildbookIAM,
org.ecocean.identity.IBEISIA,
org.ecocean.AcmIdBot,
org.ecocean.Util,
javax.jdo.Query,
java.util.ArrayList,
java.util.Collection,
java.util.List,
org.json.JSONArray,
org.json.JSONObject" %>
<%
/*
 * Repair MediaAssets stuck at validImageForIA == false.
 *
 * WHY THIS EXISTS
 * ---------------
 * validImageForIA == false is a terminal state in practice. WildbookIAM.sendMediaAssets
 * refuses to register a flagged asset with WBIA, AcmIdBot's reconciliation sweep drops
 * flagged assets before they are even probed, and nothing re-validates them (
 * MediaAsset.validateSourceImage() is only consulted when the flag is null). Meanwhile
 * WildbookIAM.sendAnnotations does NOT check the flag, so annotations on such an asset are
 * still POSTed to WBIA, which rejects them with
 * "image_uuid_list has invalid values [(0, None)]". So a stale flag actively breaks
 * identification rather than merely hiding a bad image.
 *
 * Historically the flag could also be set purely because WBIA answered HTTP 500 during an
 * image send -- an HTTP status, not a verdict about the file. That logic has been removed,
 * but the rows it created remain.
 *
 * WHAT THIS DOES
 * --------------
 * Re-decodes each flagged asset's actual file via validateSourceImage() and lets the file
 * itself decide the flag. Genuinely corrupt images stay false on their own merits;
 * wrongly-flagged ones become true. Then, for whatever became valid, it asks WBIA which
 * acmIds it does not know and registers those.
 *
 * It never sets the flag true directly -- only validateSourceImage() decides.
 *
 * PARAMS
 *   max=<n>        assets to process this run (default 100)
 *   commit=true    actually persist and register; DEFAULT IS A DRY RUN
 *   register=false revalidate only; skip the WBIA probe/registration step
 *   assetId=<id>   operate on exactly one asset (ignores max; good for a first look)
 *   verbose=true   include a per-asset detail array in the output
 *
 * NOTE validateSourceImage() only inspects the file for LOCAL asset stores. On any other
 * store it cannot decide, and such assets are reported as "unrevalidatable" rather than
 * being silently treated as either valid or corrupt.
 */

String context = ServletUtilities.getContext(request);

int max = 100;
String maxParam = request.getParameter("max");
if (maxParam != null) {
    try {
        max = Integer.parseInt(maxParam.trim());
    } catch (NumberFormatException nfe) {
        response.setStatus(400);
        out.println(new JSONObject().put("error", "invalid max parameter: " + maxParam).toString(4));
        return;
    }
    if (max < 1) {
        response.setStatus(400);
        out.println(new JSONObject().put("error", "max must be a positive integer; got: " + maxParam).toString(4));
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
        out.println(new JSONObject().put("error", "invalid assetId parameter: " + assetIdParam).toString(4));
        return;
    }
}

boolean commit   = "true".equalsIgnoreCase(request.getParameter("commit"));
boolean register = !"false".equalsIgnoreCase(request.getParameter("register"));
boolean verbose  = "true".equalsIgnoreCase(request.getParameter("verbose"));

JSONObject result = new JSONObject();
result.put("dryRun", !commit);
result.put("registerWithWbia", register && commit);
JSONArray detail = new JSONArray();

int examined = 0, nowValid = 0, stillInvalid = 0, unrevalidatable = 0, errored = 0;
int probed = 0, missingAtWbia = 0, registered = 0, registerFailed = 0;

// ---- phase 1: collect the flagged asset ids (short transaction, primitives out) ----
List<Integer> ids = new ArrayList<Integer>();
Shepherd readShepherd = new Shepherd(context);
readShepherd.setAction("appadmin.repairQuarantinedImages.read");
readShepherd.beginDBTransaction();
Query q = null;
try {
    if (singleAssetId != null) {
        ids.add(singleAssetId);
    } else {
        q = readShepherd.getPM().newQuery(MediaAsset.class, "validImageForIA == false");
        q.setResult("id");            // project: no need to materialize the assets here
        q.setOrdering("id ascending");
        q.setRange(0, max);
        Collection c = (Collection)q.execute();
        for (Object o : c) {
            if (o != null) ids.add(Integer.valueOf(o.toString()));
        }
    }
} catch (Exception ex) {
    response.setStatus(500);
    out.println(new JSONObject().put("error", "read phase failed: " + ex.toString()).toString(4));
    return;
} finally {
    if (q != null) q.closeAll();
    readShepherd.rollbackAndClose();
}
result.put("flaggedAssetsSelected", ids.size());

// ---- phase 2: re-decode each file and let the file decide the flag ----
// Collected for phase 3. In a dry run nothing is committed: the enclosing
// rollbackAndClose() discards validateSourceImage()'s in-memory writes.
List<Integer> becameValidIds = new ArrayList<Integer>();
List<String> becameValidAcmIds = new ArrayList<String>();

Shepherd vShepherd = new Shepherd(context);
vShepherd.setAction("appadmin.repairQuarantinedImages.revalidate");
vShepherd.beginDBTransaction();
try {
    for (Integer id : ids) {
        examined++;
        JSONObject row = new JSONObject();
        row.put("assetId", id);
        try {
            MediaAsset ma = MediaAssetFactory.load(id.intValue(), vShepherd);
            if (ma == null) {
                row.put("outcome", "gone");
                errored++;
                if (verbose) detail.put(row);
                continue;
            }
            String storeType = null;
            try {
                if (ma.getStore() != null) storeType = ma.getStore().getType().toString();
            } catch (Exception ignore) { }
            row.put("store", (storeType == null) ? "unknown" : storeType);
            if (!"LOCAL".equals(storeType)) {
                // validateSourceImage() is a no-op off LOCAL; do not guess either way
                row.put("outcome", "unrevalidatable");
                unrevalidatable++;
                if (verbose) detail.put(row);
                continue;
            }
            boolean valid = ma.validateSourceImage();   // reads the actual file
            row.put("outcome", valid ? "nowValid" : "stillInvalid");
            if (valid) {
                nowValid++;
                becameValidIds.add(id);
                if (Util.stringExists(ma.getAcmId()) && Util.isUUID(ma.getAcmId()))
                    becameValidAcmIds.add(ma.getAcmId());
                row.put("acmId", ma.getAcmId());
                if (commit) vShepherd.updateDBTransaction();
            } else {
                stillInvalid++;
                // leave it flagged: the file genuinely does not decode
            }
        } catch (Exception ex) {
            errored++;
            row.put("outcome", "error");
            row.put("error", ex.toString());
            System.out.println("repairQuarantinedImages: asset " + id + " failed: " + ex);
        }
        if (verbose) detail.put(row);
    }
} finally {
    vShepherd.rollbackAndClose();   // dry run: discards; commit run: trailing empty tx only
}

result.put("examined", examined);
result.put("nowValid", nowValid);
result.put("stillInvalid", stillInvalid);
result.put("unrevalidatable", unrevalidatable);
result.put("errored", errored);

// ---- phase 3: ask WBIA which of the newly-valid acmIds it does not know (no tx open) ----
List<String> missing = new ArrayList<String>();
if (register && !becameValidAcmIds.isEmpty()) {
    try {
        probed = becameValidAcmIds.size();
        missing = WildbookIAM.iaMissingImageIds(becameValidAcmIds, context);
        missingAtWbia = missing.size();
    } catch (Exception ex) {
        result.put("probeError", ex.toString());
        missing = new ArrayList<String>();
    }
}
result.put("probedAtWbia", probed);
result.put("missingFromWbia", missingAtWbia);

// ---- phase 4: register the missing ones (own transaction, commit per confirmation) ----
if (commit && register && !missing.isEmpty()) {
    Shepherd sShepherd = new Shepherd(context);
    sShepherd.setAction("appadmin.repairQuarantinedImages.register");
    sShepherd.beginDBTransaction();
    try {
        for (String acmId : missing) {
            JSONObject row = new JSONObject();
            row.put("acmId", acmId);
            try {
                MediaAsset ma = MediaAssetFactory.loadByAcmId(acmId, sShepherd);
                if (ma == null) {
                    row.put("outcome", "assetGoneBeforeRegister");
                    registerFailed++;
                    if (verbose) detail.put(row);
                    continue;
                }
                String before = ma.getAcmId();
                if (!ma.hasFamily(sShepherd)) ma.updateStandardChildren();
                ArrayList<MediaAsset> one = new ArrayList<MediaAsset>();
                one.add(ma);
                // checkFirst=false: phase 3 already established absence
                JSONObject sendRtn = IBEISIA.sendMediaAssetsNew(one, context, false);
                if (AcmIdBot.sendConfirmedAcmId(sendRtn, ma.getAcmId())) {
                    // rectifyMediaAssetIds may have adopted WBIA's UUID. acmId IS part of
                    // the MediaAsset OpenSearch document but setAcmId does not bump
                    // revision, so bump it explicitly or the index keeps the old value.
                    if ((before == null) || !before.equals(ma.getAcmId())) {
                        row.put("acmIdChanged", before + " -> " + ma.getAcmId());
                        ma.setRevision();
                    }
                    sShepherd.updateDBTransaction();
                    registered++;
                    row.put("outcome", "registered");
                } else {
                    registerFailed++;
                    row.put("outcome", "unconfirmedByWbia");
                    System.out.println("repairQuarantinedImages: WBIA did not confirm " + acmId);
                }
            } catch (Exception ex) {
                registerFailed++;
                row.put("outcome", "registerError");
                row.put("error", ex.toString());
                System.out.println("repairQuarantinedImages: register failed for " + acmId +
                    ": " + ex);
            }
            if (verbose) detail.put(row);
        }
    } finally {
        sShepherd.rollbackAndClose();
    }
}
result.put("registeredWithWbia", registered);
result.put("registerFailed", registerFailed);

if (!commit) {
    result.put("note",
        "DRY RUN: nothing persisted and nothing sent to WBIA. Re-run with &commit=true to apply. "
        + "Flag changes shown here were computed by actually decoding each file.");
}
if (verbose) result.put("detail", detail);
result.put("remainingHint",
    "Re-run until flaggedAssetsSelected is 0. Assets reported stillInvalid keep the flag by "
    + "design -- their files do not decode -- and will be selected again on every run.");

out.println(result.toString(4));
%>
