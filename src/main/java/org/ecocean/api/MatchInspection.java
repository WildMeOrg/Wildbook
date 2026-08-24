package org.ecocean.api;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.ia.MatchInspectionPairxEnricher;
import org.ecocean.ia.MatchInspectionPairxEnricher.ProspectEnrichmentOutcome;
import org.ecocean.ia.MatchResult;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * On-demand PairX inspection-image endpoint.
 *
 * <p>POST {@code /api/v3/match-inspection/{mrId}/{prospectAnnId}}</p>
 *
 * <p>Triggers single-prospect PairX enrichment on the user's first
 * inspector-open. Idempotent: subsequent calls for an already-enriched
 * prospect return the existing asset without re-calling PairX.</p>
 *
 * <p>Replaces the eager bulk PairX enrichment that previously ran
 * inline inside {@code MlServiceProcessor.runMatchProspects} — see
 * {@code docs/plans/2026-05-23-pairx-enricher-on-demand.md}.</p>
 */
public class MatchInspection extends ApiBase {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.MatchInspection.doPost");
        myShepherd.beginDBTransaction();
        try {
            User currentUser = myShepherd.getUser(request);
            if (currentUser == null) {
                writeError(response, 401, "unauthenticated");
                return;
            }
            // URI shape: /api/v3/match-inspection/{mrId}/{prospectAnnId}
            // split index:    [0] "" [1] "api" [2] "v3" [3] "match-inspection" [4] mrId [5] prospectAnnId
            // Reject extra segments to keep the endpoint contract tight
            // (Codex C7a round-1 Minor #1: >=6 silently accepted longer paths).
            String uri = request.getRequestURI();
            String[] args = uri.split("/");
            if (args.length != 6) {
                writeError(response, 400,
                    "invalid path; expected /api/v3/match-inspection/{mrId}/{prospectAnnId}");
                return;
            }
            String mrId = args[4];
            String prospectAnnId = args[5];
            if (!Util.stringExists(mrId) || !Util.isUUID(mrId)) {
                writeError(response, 400, "invalid matchResultId");
                return;
            }
            if (!Util.stringExists(prospectAnnId) || !Util.isUUID(prospectAnnId)) {
                writeError(response, 400, "invalid prospectAnnotationId");
                return;
            }
            MatchResult mr = myShepherd.getMatchResult(mrId);
            if (mr == null) {
                writeError(response, 404, "match result not found");
                return;
            }
            // Authz: the user must be authorized for the query annotation's
            // encounter — same scope used for the rest of MatchResults UI.
            Annotation queryAnn = mr.getQueryAnnotation();
            Encounter queryEnc = (queryAnn == null) ? null : queryAnn.findEncounter(myShepherd);
            boolean isAdmin = currentUser.isAdmin(myShepherd);
            if (!isAdmin) {
                if ((queryEnc == null) ||
                    !ServletUtilities.isUserAuthorizedForEncounter(
                        queryEnc, request, myShepherd)) {
                    writeError(response, 403, "no access to match result");
                    return;
                }
            }
            // Release the Shepherd before the enricher runs (Phase B is HTTP
            // and Phase C opens its own short Shepherds).
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
            myShepherd = null;

            MatchInspectionPairxEnricher enricher = new MatchInspectionPairxEnricher(context);
            ProspectEnrichmentOutcome outcome =
                enricher.enrichOneProspect(mrId, prospectAnnId);

            switch (outcome.result) {
              case EXISTS:
                  writeAssetResponse(response, outcome.assetJson, 200);
                  return;
              case CREATED:
                  writeAssetResponse(response, outcome.assetJson, 201);
                  return;
              case NOT_ASSOCIATED:
                  writeError(response, 404, "prospect not associated with match result");
                  return;
              case UNSUPPORTED:
                  writeError(response, 422, outcome.message == null
                      ? "prospect cannot be enriched" : outcome.message);
                  return;
              case PAIRX_FAILED:
              default:
                  writeError(response, 502, "pairx failed: " +
                      (outcome.message == null ? "(no detail)" : outcome.message));
                  return;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            writeError(response, 500, "internal error: " + ex.getMessage());
        } finally {
            // Guarded close so a Shepherd cleanup throw doesn't shadow
            // the upstream outcome (same pattern as Codex C1 round-2).
            // myShepherd is set to null on the success path after the
            // commit + close above; on the exception path it's still
            // the original handle and may have an active transaction.
            if (myShepherd != null) {
                try {
                    myShepherd.rollbackAndClose();
                } catch (Exception ex) {
                    System.out.println(
                        "[WARN] MatchInspection.doPost rollbackAndClose failed: " + ex);
                }
            }
        }
    }

    private void writeAssetResponse(HttpServletResponse response, JSONObject assetJson,
        int statusCode) throws IOException {
        JSONObject body = new JSONObject();
        body.put("success", true);
        if (assetJson != null) body.put("asset", assetJson);
        response.setStatus(statusCode);
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(body.toString());
    }

    private void writeError(HttpServletResponse response, int statusCode, String message)
        throws IOException {
        JSONObject body = new JSONObject();
        body.put("success", false);
        if (message != null) body.put("message", message);
        response.setStatus(statusCode);
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(body.toString());
    }
}
