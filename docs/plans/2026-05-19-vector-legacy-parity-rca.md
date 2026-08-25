Reading prompt from stdin...
OpenAI Codex v0.130.0
--------
workdir: /mnt/c/claude-skills/kb/ml-service
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: high
reasoning summaries: none
session id: 019e4ba5-f66b-7f81-aec7-0313f388fed5
--------
user
# v2 ml-service vector vs legacy WBIA MiewID — parity RCA

You are collaborating with another agent to figure out how to make
the Wildbook v2 vector matching pipeline and the legacy WBIA-MiewID
pipeline return **identical match results** (same prospects, same
order, same scores) given the same query and matchingSetFilter.
Both use the MiewID v4.1 (`miewid-msv4.1`) checkpoint at the model
level; differences are above that layer.

Repos: `/mnt/c/Wildbook-clean2` (Wildbook, branch
`migrate-ml-service-v2`, PR #1580), `/mnt/c/wbia-plugin-miew-id`
(WBIA MiewID plugin), `/mnt/c/ml-service` (ml-service FastAPI).

## Observed deployment behavior (flakebook.wildme.org)

User ran the same query annotation through both pipelines with
identical `matchingSetFilter = {locationIds: ["Botanischer Garten Bielefeld"]}`.

| | Vector task | Legacy MiewID task |
|---|---|---|
| Candidates (matching set size) | 15329 | 15290 |
| Prospects returned | 48 | 1000 |
| Top score | 1.000 (perfect-image duplicate) | 0.7365 |
| Score distribution | smooth (1.0, 0.45, 0.39, 0.35, …) | three at ~0.736 then hard cliff to 0.000 |
| Annotation-ID overlap (vector∩legacy) | 0 | 0 |

The score=1.0 in vector is correct (a different encounter has an
identical-image duplicate). Both tasks queried the same annotation;
both reported essentially the same candidate pool size (off by ~39
annotations, probably indexing drift between runs).

**Yet zero ID overlap** between vector's top 48 and legacy's top
1000. That's structural, not coincidence.

## What I've already verified

### 1. Embedding-level parity is proven
A local test (`/tmp/miewid_score_parity_test.py`) loaded the
`miewid-msv4.1` checkpoint, extracted embeddings for 3 sample
images, computed pairwise cosines, ran them through Wildbook's
`Annotation.openSearchScoreToCosine` transform (`2x - 1`, clamped).
Round-trip error was 0.0 for all real-cosine values; the only
non-zero "error" was 1.19e-07 at the +1.0 boundary where the
clamp correctly snaps float32 dot-product overshoot back. So the
**math is correct end to end on the vector side**.

### 2. Both pipelines use the same score formula
- **OpenSearch Lucene knn** with `space_type=cosinesimil` returns
  `score = (1 + cos_sim) / 2`. Vector path then transforms back to
  raw cosine via C17's `2x - 1` in
  `Annotation.openSearchScoreToCosine`. Stored in MR as raw cosine,
  range [-1, 1].
- **WBIA-MiewID** at
  `/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:806-809`:
  ```python
  def distance_to_score(distance):
      score = (2 - distance) / 2
      return score
  ```
  With `distance = 1 - cos_sim` from `cosine_distance` in
  `/mnt/c/wbia-plugin-miew-id/wbia_miew_id/metrics/distance.py:34-47`
  (L2-normalize both, dot product, subtract from 1).
  Simplifies to `score = (1 + cos_sim) / 2`. Stored in MR via
  legacy `populateProspects(type, annotIds, scores, …)` as that
  value, range [0, 1].

So both pipelines compute the same underlying cosine. Score-scale
mismatch (raw cos vs `(1+cos)/2`) is a Wildbook-side serialization
difference, NOT an algorithmic disagreement. Easily resolved either
by post-transforming legacy in the comparison script, OR by aligning
Wildbook's storage so both paths write the same scale to
`MatchResultProspect.score`.

### 3. Score = 1.0 self-duplicate is legitimate
The user confirmed `142e9ca2-…` (vector's top, cos=1.0) is a
different annotation that happens to be an identical-image
duplicate. Vector's `must_not encounterId=queryEncId` exclusion
fired correctly.

### 4. WBIA emits per-annotation scores
At `_plugin.py:535-541` (the `yield` loop in
`miew_id_predict_request`), per-(qaid, daid) scores are yielded
individually. They're per-annotation cosine-derived, not
name-aggregated. Aggregation happens later in `get_match_results`
at `_plugin.py:389-391` (`name_scores = max(annot_scores per name)`)
and is stored separately in the match result for the "indiv"
score_type, while `annot_score_list` keeps the per-annotation
values. Wildbook reads both:
- `annot_score_list` → MR prospects of type "annot"
- `score_list` (name scores) → MR prospects of type "indiv"

### 5. Score=0.000 entries in legacy are real, not "tail" filtering
`score = (1 + cos) / 2` at score=0 means cos = -1 (opposite
embeddings — vanishingly rare for real images), OR WBIA returned 0
as a sentinel. The aid_scores_from_score_dict at `_plugin.py:818-820`
returns `name_score_dict.get(daid, 0)` — so if WBIA's per-aid score
dict doesn't have an entry for that daid, it gets 0. That suggests
WBIA may emit scores only for SOME of the daids it was asked about.

## Open questions for you

1. **Why zero ID overlap?** Both pipelines ostensibly score the same
   ~15K candidate pool. Vector returns top 48 by cosine descending.
   Legacy returns 1000. Even if vector's top-48 are all of one
   individual, those 48 specific UUIDs should appear *somewhere* in
   legacy's 1000. They don't. Hypotheses:
   - **Candidate-set divergence**: vector's
     `Annotation.getMatchingSetQuery` and legacy's
     `Annotation.getMatchingSet(myShepherd)` build candidate pools
     differently. The off-by-39 in counts (15329 vs 15290) hints at
     this. Are they applying different filters?
   - **WBIA scores only a subset**: WBIA's `daid_list` may not equal
     the full Wildbook-side matching set. WBIA may have its own
     internal filtering (e.g., excluding unregistered annotations,
     un-named, etc.) and only return scores for what it considered.
     The `aid_scores_from_score_dict` fallback to 0 suggests the
     daid set passed in is larger than what WBIA actually scored.
   - **knn k=50 cap on vector** trims out all the candidates that
     would have appeared in legacy's top-1000. If vector's top-48
     are all from one (well-matched) individual, and legacy's
     top-1000 is dominated by other individuals' lower-cosine
     candidates that WBIA happens to surface, the sets could
     legitimately be disjoint.

2. **What does it take to achieve full prospect-level parity?**
   - Lift vector's k=50 to match legacy's result size?
   - Verify both `getMatchingSet` and `getMatchingSetQuery` apply
     identical filters?
   - Align score storage scale (raw cos vs (1+cos)/2)?
   - Sort prospects the same way (currently both
     `MatchResultProspect.compareTo` desc by score)?
   - Handle the "WBIA's daid_list ≠ Wildbook's matching set" gap?

3. **Read `Annotation.getMatchingSet(Shepherd)` (the legacy
   variant) vs `Annotation.getMatchingSetQuery(Shepherd, taskParams,
   useClauses)` (the vector variant)** in
   `/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java`.
   Where do they diverge? Is the legacy variant applying the
   `taskParams.matchingSetFilter` predicates (location, owner,
   project) the same way?

4. **Trace the legacy daid_list pipeline.** `Task.java` →
   `IBEISIA.beginIdentifyAnnotations` → `sendIdentify` →
   `tlist.add(toFancyUUID(ann.getAcmId()))` (at IBEISIA.java:319).
   What `tanns` does `sendIdentify` get, and does it match
   `Annotation.getMatchingSetQuery`'s OS-side count of 15329?

5. **Is "identical match results" actually achievable** as the
   product goal, or are there architectural differences between
   the pipelines (e.g., WBIA's name aggregation, dedup by acmId,
   pairx_rerank) that make true parity impossible without changing
   one side's behavior?

## What I want from you

Read the two pipelines side by side and produce a parity plan with:

- **Root-cause analysis**: where exactly the candidate sets and
  scores diverge architecturally.
- **A concrete checklist of changes** to either pipeline (or both)
  needed to achieve identical match results. Be specific: file:line
  citations, what to change, what NOT to change.
- **A staged validation plan**: how to verify each gap is closed,
  given that the user can run SQL queries and arbitrary Python
  scripts on the deployment.
- **A "minimum bar" recommendation**: if FULL parity is too costly,
  what's the meaningful subset (e.g., "same scores for the
  intersection of prospects" or "same prospects above some cosine
  threshold") that's worth aiming for.

Be skeptical of my analysis; verify in the source. Use absolute
paths with `:line` citations. **Read-only — do not modify any
files.**

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll keep this read-only and inspect the Wildbook and WBIA paths directly. I’ll first map the matching-set construction and the legacy daid handoff, then compare score/result shaping around prospect population.
exec
/bin/bash -lc "rg -n \"getMatchingSet\\(|getMatchingSetQuery\\(|beginIdentifyAnnotations|sendIdentify|populateProspects|openSearchScoreToCosine|MatchResultProspect|matchingSetFilter\" /mnt/c/Wildbook-clean2/src/main/java/org/ecocean /mnt/c/wbia-plugin-miew-id/wbia_miew_id /mnt/c/ml-service" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc 'git -C /mnt/c/Wildbook-clean2 status --short && git -C /mnt/c/Wildbook-clean2 branch --show-current && git -C /mnt/c/wbia-plugin-miew-id status --short && git -C /mnt/c/ml-service status --short' in /mnt/c/claude-skills/kb/ml-service
 succeeded in 1191ms:
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2526:        // NOTE: opposite logic in MatchResultProspect.isInProject()
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:5501:                    tp.put("matchingSetFilter", mf);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:5547:                    tp.put("matchingSetFilter", mf);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:320:            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:143:        JSONObject matchingSetFilter = new JSONObject();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:227:            // we might grow matchingSetFilter stuff later, but for now we only have locationIds from ui
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:239:                matchingSetFilter.put("locationIds", locArr);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:523:                                        matchingSetFilter);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:628:                initiateIA(dupId, skipIdentification, encAssets, matchingSetFilter);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:797:        JSONObject matchingSetFilter) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:814:            if (!skipIdent) taskParams.put("matchingSetFilter", matchingSetFilter);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:17:import org.ecocean.ia.MatchResultProspect;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:931:    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:936:            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:958:                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1039:            JSONObject filt = taskParams.optJSONObject("matchingSetFilter");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1133:        System.out.println("getMatchingSetQuery() returning query=" + query.toString(4));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1137:    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1138:        return getMatchingSet(myShepherd, null, true);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1141:    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject taskParams) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1142:        return getMatchingSet(myShepherd, taskParams, true);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1145:    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject taskParams,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1148:        JSONObject query = getMatchingSetQuery(myShepherd, taskParams, useClauses);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1164:            System.out.println("getMatchingSet() exception: " + ex);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1177:        System.out.println("getMatchingSet() results: hitSize=" + hitSize + "; hits length=" +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194:                getMatchingSetQuery(myShepherd, taskParams, useClauses));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1258:    public static double openSearchScoreToCosine(double osScore) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1304:                ann.setOpensearchScore(openSearchScoreToCosine(osScore));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1321:        JSONObject j = taskParams.optJSONObject("matchingSetFilter");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1868:    // similar as above for MatchResultProspects
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1869:    public int deleteMatchResultProspects(Shepherd myShepherd) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1870:        List<MatchResultProspect> mrps = myShepherd.getMatchResultProspects(this);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1873:        for (MatchResultProspect mrp : mrps) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1875:            System.out.println("[DEBUG] (" + ct + ") ann.deleteMatchResultProspects() on id=" +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1904:        int np = this.deleteMatchResultProspects(myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1907:            " MatchResults, " + np + " MatchResultProspects, " + ne + " Embeddings on " + this);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:648:            // if(islObj.optString("queueStatus").equals("queued")){sendIdentify=false;}
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:741:        return getParameters().optJSONObject("matchingSetFilter");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:839:        // matchingSetFilter, legacy WBIA log-based MR generation). The v2
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:848:            rtn.put("matchingSetFilter", getMatchingSetFilter());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:227:    public static JSONObject sendIdentify(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:232:        if (!isIAPrimed()) System.out.println("WARNING: sendIdentify() called without IA primed");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:239:        Util.mark("sendIdentify-0  tanns.size()=" + ((tanns == null) ? "null" : tanns.size()),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:243:        myShepherd.setAction("IBEISIA.sendIdentify");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:265:        Util.mark("sendIdentify-1", startTime);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:268:                System.out.println("WARNING: IBEISIA.sendIdentify() [qanns] skipping invalid " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:289:        Util.mark("sendIdentify-2", startTime);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:299:        Util.mark("sendIdentify-A", startTime);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:302:            System.out.println("--- sendIdentify() passed null tanns..... why???");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:304:            tanns = qanns.get(0).getMatchingSet(myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:306:        Util.mark("sendIdentify-B  tanns.size()=" + ((tanns == null) ? "null" : tanns.size()),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:313:                    System.out.println("WARNING: IBEISIA.sendIdentify() [tanns] skipping invalid " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:330:        Util.mark("sendIdentify-C", startTime);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:362:        Util.mark("sendIdentify-D", startTime);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:850:    public static JSONObject beginIdentifyAnnotations(ArrayList<Annotation> qanns,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:854:            "INFO: You no longer need to send species with call to beginIdentifyAnnotations. It is derived from the Annotation's Encounters.");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:855:        return beginIdentifyAnnotations(qanns, tanns, queryConfigDict, userConfidence, myShepherd,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:859:    // trying to optimize the original beginIdentifyAnnotations()  [above]
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:860:    public static JSONObject beginIdentifyAnnotations(ArrayList<Annotation> qanns,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:866:            System.out.println("WARNING: beginIdentifyAnnotations() called without IA primed");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:885:            // this voodoo via JH will insure that .acmId is on the MediaAssets which are loaded via getMatchingSet() below (for speed)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:899:                    "beginIdentifyAnnotations(): have to set tanns. Matching set being built from the first ann in the list.");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:900:                tanns = qanns.get(0).getMatchingSet(myShepherd,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:939:                identRtn = sendIdentify(qanns, tanns, queryConfigDict, userConfidence, baseUrl,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:955:            results.put("sendIdentify", identRtn);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:957:            System.out.println("sendIdentify ---> " + identRtn);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:963:                System.out.println("beginIdentifyAnnotations() unsuccessful on sendIdentify(): " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:976:        jlog.put("_action", "sendIdentify");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:984:    // NOTE!!!  this might be redundant with beginIdentifyAnnotations above. (this came from crc)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:1513:                    taskParameters.put("matchingSetFilter", mf);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IAQueryCache.java:28:        ArrayList<Annotation> anns = qanns.get(0).getMatchingSet(myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IAUtils.java:28:                tp.put("matchingSetFilter", mf);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:38:    private Set<MatchResultProspect> prospects;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:50:    // number of MatchResultProspects [per type] to actually store (hotspotter
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:85:        this.populateProspects(annots, false, myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:86:        this.populateProspects(annots, true, myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:149:        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:152:        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:158:    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:166:            this.prospects = new HashSet<MatchResultProspect>();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:182:            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:203:    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:208:            this.prospects = new HashSet<MatchResultProspect>();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:213:            _populateProspectsByIndividual(annots, myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:217:                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:243:    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:294:                this.prospects.add(new MatchResultProspect(best, bestScore, "indiv", null));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:300:            this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:311:    public Set<MatchResultProspect> getProspects() {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:567:        return params.optJSONObject("matchingSetFilter");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:584:        for (MatchResultProspect mrp : prospects) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:591:    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:593:        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:596:        for (MatchResultProspect mrp : prospects) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:609:            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:15:public class MatchResultProspect implements java.io.Serializable, Comparable<MatchResultProspect> {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:22:    public MatchResultProspect() {}
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:24:    public MatchResultProspect(Annotation ann) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:29:    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:97:    @Override public int compareTo(MatchResultProspect other) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:125:                for (MatchResultProspect prospect : mr.getProspects()) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:228:            MatchResultProspect target = findProspect(mr, dto.prospectAnnotationId,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:264:    static MatchResultProspect findProspect(MatchResult mr, String annotationId,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:267:        for (MatchResultProspect p : mr.getProspects()) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:108:                        ann.deleteMatchResultProspects(myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:128:                        ann.deleteMatchResultProspects(myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:899:                            tp.put("matchingSetFilter", mf);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:420:                matchingSet = ann.getMatchingSet(myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:443:            JSONObject sent = IBEISIA.beginIdentifyAnnotations(qanns, matchingSet, queryConfigDict,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:448:                System.out.println("beginIdentifyAnnotations() was unsuccessful due to " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:456:                throw new IOException("beginIdentifyAnnotations() failed due to " + errorMsg);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/MediaAssetCreate.java:122:                        tp.put("matchingSetFilter", mf);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/AnnotationEdit.java:166:                        annot.deleteMatchResultProspects(myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/shepherd/core/Shepherd.java:20:import org.ecocean.ia.MatchResultProspect;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/shepherd/core/Shepherd.java:2861:    public List<MatchResultProspect> getMatchResultProspects(Annotation ann) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/shepherd/core/Shepherd.java:2862:        List<MatchResultProspect> all = new ArrayList<MatchResultProspect>();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/shepherd/core/Shepherd.java:2865:        String filter = "SELECT FROM org.ecocean.ia.MatchResultProspect WHERE annotation.id == '" +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/shepherd/core/Shepherd.java:2869:        if (c != null) all = new ArrayList<MatchResultProspect>(c);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/ProjectIA.java:86:                            tp.put("matchingSetFilter", mf);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/ProjectIA.java:102:                    // JSONObject rtnIA = IBEISIA.sendIdentify(qanns, tanns, queryConfigDict, userConfidence, baseUrl, context);

codex
The initial search confirms the legacy Java `getMatchingSet(...)` overload now delegates through `getMatchingSetQuery(...)`, but there are still call sites that pass `null` task params. I’m reading the exact branches next, especially `beginIdentifyAnnotations` and `sendIdentify`, because that is where the filter can silently fall out.
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java | sed -n '1225,1345p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java | sed -n '900,1225p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java | sed -n '210,375p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java | sed -n '830,980p'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 291ms:
   900	                new org.datanucleus.api.rest.orgjson.JSONObject(), fullAccess);
   901	        } else {
   902	            jobj = new org.datanucleus.api.rest.orgjson.JSONObject();
   903	        }
   904	        return jobj;
   905	    }
   906	
   907	    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeMedia(HttpServletRequest request)
   908	    throws org.datanucleus.api.rest.orgjson.JSONException {
   909	        return this.sanitizeMedia(request, false);
   910	    }
   911	
   912	    public boolean isPart() {
   913	        return ((this.iaClass != null) && this.iaClass.contains("+"));
   914	    }
   915	
   916	    public String getPartIfPresent() {
   917	        String thisPart = "";
   918	
   919	        if (this.iaClass != null && this.iaClass.contains("+")) {
   920	            String[] arr = this.iaClass.split("\\+");
   921	            thisPart = arr[arr.length - 1];
   922	        }
   923	        return thisPart;
   924	    }
   925	
   926	/*
   927	   both must be arrays which contain objects.
   928	   these will be "mixed into" the built default query. TODO this might cause some conflict or
   929	   overwriting that needs to be addressed in the future
   930	 */
   931	    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
   932	        boolean useClauses) {
   933	        Encounter enc = this.findEncounter(myShepherd);
   934	
   935	        if (enc == null) {
   936	            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
   937	                this);
   938	            return null;
   939	        }
   940	        JSONObject query = new JSONObject(
   941	            "{\"query\": {\"bool\": {\"filter\": [], \"must_not\": []} } }");
   942	        JSONObject wrapper = new JSONObject();
   943	        JSONObject arg = new JSONObject();
   944	        String txStr = enc.getTaxonomyString();
   945	        if (txStr != null) {
   946	            useClauses = true;
   947	            if (txStr.endsWith(" sp")) {
   948	                arg.put("encounterTaxonomy", txStr.substring(0, txStr.length() - 2) + "*");
   949	                wrapper.put("wildcard", arg);
   950	            } else {
   951	                arg.put("encounterTaxonomy", txStr);
   952	                wrapper.put("match", arg);
   953	            }
   954	            query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
   955	        } else if (!Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
   956	            "allowIdentificationWithoutTaxonomy"))) {
   957	            System.out.println(
   958	                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
   959	            return null;
   960	        }
   961	        // it seems like useClauses=false only ever was used when no taxonomy was present and basically
   962	        // returned every annotation with matchAgainst=T and an acmId
   963	        if (useClauses) {
   964	            if (!Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
   965	                "ignoreViewpointMatching", this.getTaxonomy(myShepherd)))) {
   966	                String[] viewpoints = this.getViewpointAndNeighbors();
   967	                if (viewpoints != null) {
   968	                    arg = new JSONObject();
   969	                    arg.put("viewpoint", new JSONArray(viewpoints));
   970	                    wrapper = new JSONObject();
   971	                    wrapper.put("terms", arg);
   972	                    // query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
   973	                    // to handle allowing null viewpoint, opensearch query gets messy!
   974	                    JSONArray should = new JSONArray(
   975	                        "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"viewpoint\"}}}}]");
   976	                    should.put(wrapper);
   977	                    JSONObject bool = new JSONObject("{\"bool\": {}}");
   978	                    bool.getJSONObject("bool").put("should", should);
   979	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
   980	                        bool);
   981	                }
   982	            }
   983	            // this does either/or part/iaClass - unsure if this is correct
   984	            boolean usedPart = false;
   985	            if (Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
   986	                "usePartsForIdentification"))) {
   987	                String part = this.getPartIfPresent();
   988	                if (!Util.stringIsEmptyOrNull(part)) {
   989	                    arg = new JSONObject();
   990	                    arg.put("iaClass", "*" + part);
   991	                    wrapper = new JSONObject();
   992	                    wrapper.put("wildcard", arg);
   993	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
   994	                        wrapper);
   995	                    usedPart = true;
   996	                }
   997	            }
   998	            if (!usedPart && (this.getIAClass() != null)) {
   999	                arg = new JSONObject();
  1000	                arg.put("iaClass", this.getIAClass());
  1001	                wrapper = new JSONObject();
  1002	                wrapper.put("match", arg);
  1003	                query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
  1004	                    wrapper);
  1005	            }
  1006	        }
  1007	        // matchAgainst true
  1008	        arg = new JSONObject();
  1009	        arg.put("matchAgainst", true);
  1010	        wrapper = new JSONObject();
  1011	        wrapper.put("term", arg);
  1012	        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
  1013	
  1014	        // must have acmId
  1015	        arg = new JSONObject();
  1016	        arg.put("field", "acmId");
  1017	        wrapper = new JSONObject();
  1018	        wrapper.put("exists", arg);
  1019	        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
  1020	
  1021	        // exclude our encounter
  1022	        arg = new JSONObject();
  1023	        arg.put("encounterId", enc.getId());
  1024	        wrapper = new JSONObject();
  1025	        wrapper.put("match", arg);
  1026	        query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(wrapper);
  1027	
  1028	        // skip dead animals
  1029	        Long dateMS = enc.getDateInMillisecondsFallback();
  1030	        if (dateMS != null) {
  1031	            wrapper = new JSONObject(
  1032	                "{\"range\": {\"encounterIndividualTimeOfDeath\": { \"lte\": " + dateMS + " } } }");
  1033	            query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(
  1034	                wrapper);
  1035	        }
  1036	        // now process taskParams
  1037	        if (taskParams != null) {
  1038	            String userId = taskParams.optString("userId", null);
  1039	            JSONObject filt = taskParams.optJSONObject("matchingSetFilter");
  1040	            if (filt != null) {
  1041	                // locationId=FOO and locationIds=[FOO,BAR]
  1042	                boolean useNullLocation = false;
  1043	                List<String> rawLocationIds = new ArrayList<String>();
  1044	                String tmp = Util.basicSanitize(filt.optString("locationId", null));
  1045	                if (Util.stringExists(tmp)) rawLocationIds.add(tmp);
  1046	                JSONArray larr = filt.optJSONArray("locationIds");
  1047	                if (larr != null) {
  1048	                    for (int i = 0; i < larr.length(); i++) {
  1049	                        tmp = Util.basicSanitize(larr.optString(i));
  1050	                        if ("__NULL__".equals(tmp)) {
  1051	                            useNullLocation = true;
  1052	                        } else if (Util.stringExists(tmp) && !rawLocationIds.contains(tmp)) {
  1053	                            rawLocationIds.add(tmp);
  1054	                        }
  1055	                    }
  1056	                }
  1057	                List<String> expandedLocationIds = LocationID.expandIDs(rawLocationIds);
  1058	                if (expandedLocationIds.size() > 0) {
  1059	                    arg = new JSONObject();
  1060	                    arg.put("encounterLocationId", new JSONArray(expandedLocationIds));
  1061	                    wrapper = new JSONObject();
  1062	                    wrapper.put("terms", arg);
  1063	                    if (useNullLocation) {
  1064	                        JSONArray should = new JSONArray(
  1065	                            "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"encounterLocationId\"}}}}]");
  1066	                        should.put(wrapper);
  1067	                        JSONObject bool = new JSONObject("{\"bool\": {}}");
  1068	                        bool.getJSONObject("bool").put("should", should);
  1069	                        query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1070	                            "filter").put(bool);
  1071	                    } else {
  1072	                        query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1073	                            "filter").put(wrapper);
  1074	                    }
  1075	                }
  1076	                // owner ... which requires we have userId in the taskParams
  1077	                JSONArray owner = filt.optJSONArray("owner");
  1078	                JSONArray uids = new JSONArray();
  1079	                if ((owner != null) && (userId != null)) {
  1080	                    for (int i = 0; i < owner.length(); i++) {
  1081	                        String opt = owner.optString(i, null);
  1082	                        if (!Util.stringExists(opt)) continue;
  1083	                        if (opt.equals("me")) {
  1084	                            uids.put(userId);
  1085	                        } else {
  1086	                            uids.put(opt);
  1087	                        }
  1088	                    }
  1089	                }
  1090	                if (uids.length() > 0) {
  1091	                    arg = new JSONObject();
  1092	                    arg.put("encounterUserUuid", uids);
  1093	                    wrapper = new JSONObject();
  1094	                    wrapper.put("terms", arg);
  1095	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
  1096	                        wrapper);
  1097	                }
  1098	                // projectId
  1099	                String projectId = filt.optString("projectId", null);
  1100	                if (Util.stringExists(projectId)) {
  1101	                    arg = new JSONObject();
  1102	                    arg.put("encounterProjectIds", projectId);
  1103	                    wrapper = new JSONObject();
  1104	                    wrapper.put("match", arg);
  1105	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
  1106	                        wrapper);
  1107	                }
  1108	            }
  1109	        }
  1110	        /* saving this for possible future passing raw queries
  1111	           JSONArray arr = additionalQuery.optJSONArray("filter");
  1112	           if (arr != null) {
  1113	            for (int i = 0; i < arr.length(); i++) {
  1114	                JSONObject clause = arr.optJSONObject(i);
  1115	                if (clause != null)
  1116	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1117	                        "filter").put(clause);
  1118	            }
  1119	           }
  1120	           arr = additionalQuery.optJSONArray("must_not");
  1121	           if (arr != null) {
  1122	            for (int i = 0; i < arr.length(); i++) {
  1123	                JSONObject clause = arr.optJSONObject(i);
  1124	                if (clause != null)
  1125	                    query.getJSONObject("query").getJSONObject("bool").getJSONArray(
  1126	                        "must_not").put(clause);
  1127	            }
  1128	           }
  1129	         */
  1130	
  1131	        // this exludes the very noisy embeddings from opensearch results since we dont need it
  1132	        query.put("_source", new JSONObject("{ \"excludes\": [\"embeddings\"] }"));
  1133	        System.out.println("getMatchingSetQuery() returning query=" + query.toString(4));
  1134	        return query;
  1135	    }
  1136	
  1137	    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd) {
  1138	        return getMatchingSet(myShepherd, null, true);
  1139	    }
  1140	
  1141	    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject taskParams) {
  1142	        return getMatchingSet(myShepherd, taskParams, true);
  1143	    }
  1144	
  1145	    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject taskParams,
  1146	        boolean useClauses) {
  1147	        ArrayList<Annotation> anns = new ArrayList<Annotation>();
  1148	        JSONObject query = getMatchingSetQuery(myShepherd, taskParams, useClauses);
  1149	        OpenSearch os = new OpenSearch();
  1150	        long startTime = System.currentTimeMillis();
  1151	
  1152	        if (query == null) return anns;
  1153	        JSONObject queryRes = null;
  1154	        int hitSize = -1;
  1155	        try {
  1156	            int pageSize = 10000;
  1157	            try {
  1158	                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
  1159	            } catch (Exception ex) {}
  1160	            os.deletePit("annotation");
  1161	            queryRes = os.queryPit("annotation", query, 0, pageSize, null, null);
  1162	            hitSize = queryRes.optJSONObject("hits").optJSONObject("total").optInt("value");
  1163	        } catch (Exception ex) {
  1164	            System.out.println("getMatchingSet() exception: " + ex);
  1165	            ex.printStackTrace();
  1166	        }
  1167	        JSONArray hits = OpenSearch.getHits(queryRes);
  1168	        for (int i = 0; i < hits.length(); i++) {
  1169	            JSONObject hit = hits.optJSONObject(i);
  1170	            if (hit == null) continue;
  1171	            Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
  1172	            if (ann != null) {
  1173	                ann.setOpensearchScore(hit.optDouble("_score", 0.0d));
  1174	                anns.add(ann);
  1175	            }
  1176	        }
  1177	        System.out.println("getMatchingSet() results: hitSize=" + hitSize + "; hits length=" +
  1178	            hits.length() + "; anns size=" + anns.size() + "; " +
  1179	            (System.currentTimeMillis() - startTime) + "ms");
  1180	        return anns;
  1181	    }
  1182	
  1183	    // a variation of matchingSet query, but includes the vector stuff - thus returns actual matches(!)
  1184	    // method and methodVersion are used to determine *which* embedding to use; if null it will use 1st embedding
  1185	    // return null when this annot has no embeddings to match, sorry!
  1186	
  1187	    // this version will construct matchingSetQuery
  1188	    public JSONObject getMatchQuery(Shepherd myShepherd, JSONObject taskParams, boolean useClauses,
  1189	        String method, String methodVersion) {
  1190	        Embedding emb = getEmbeddingByMethod(method, methodVersion);
  1191	
  1192	        if (emb == null) return null;
  1193	        return getMatchQuery(method, methodVersion,
  1194	                getMatchingSetQuery(myShepherd, taskParams, useClauses));
  1195	    }
  1196	
  1197	    // this version if you already have matchingSetQuery
  1198	    public JSONObject getMatchQuery(String method, String methodVersion,
  1199	        JSONObject matchingSetQuery) {
  1200	        Embedding emb = getEmbeddingByMethod(method, methodVersion);
  1201	
  1202	        if (emb == null) return null;
  1203	        JSONObject nested = new JSONObject(
  1204	            "{\"nested\": {\"path\": \"embeddings\", \"query\": {\"bool\": {}}}}");
  1205	        // Inside the nested bool, keep ONLY the knn clause in `must` so the
  1206	        // per-hit score is exactly the OS knn similarity (no spurious
  1207	        // +1.0-per-term-clause offset). method/methodVersion become
  1208	        // `filter` clauses — they still constrain results but contribute
  1209	        // 0 to score. (Empty-match-prospects C17: MiewID score parity.)
  1210	        JSONArray must = new JSONArray();
  1211	        JSONObject knn = new JSONObject("{\"knn\": {\"embeddings.vector\": {}}}");
  1212	        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("vector",
  1213	            new JSONArray(emb.vectorToFloatArray()));
  1214	        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("k", KNN_K_DISTANCE_VALUE);
  1215	        must.put(knn);
  1216	        JSONArray filter = new JSONArray();
  1217	        if (method != null)
  1218	            filter.put(new JSONObject("{\"term\": {\"embeddings.method\":\"" + method + "\"}}"));
  1219	        if (methodVersion != null)
  1220	            filter.put(new JSONObject("{\"term\": {\"embeddings.methodVersion\":\"" + methodVersion +
  1221	                "\"}}"));
  1222	        nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put("must",
  1223	            must);
  1224	        if (filter.length() > 0) {
  1225	            nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put(

 succeeded in 296ms:
  1225	            nested.getJSONObject("nested").getJSONObject("query").getJSONObject("bool").put(
  1226	                "filter", filter);
  1227	        }
  1228	
  1229	        // we put nested under its own top-level must, that way its score counts (whereas filter does not)
  1230	        JSONArray nestedMust = new JSONArray();
  1231	        nestedMust.put(nested);
  1232	        matchingSetQuery.getJSONObject("query").getJSONObject("bool").put("must", nestedMust);
  1233	        return matchingSetQuery;
  1234	    }
  1235	
  1236	    /**
  1237	     * Transform an OpenSearch knn score to raw cosine similarity in
  1238	     * [-1, 1] — the value the MiewID pipeline itself reports. Public
  1239	     * so tests and any future scoring consumers use the same
  1240	     * conversion. (Empty-match-prospects C17.)
  1241	     *
  1242	     * <p><b>OS engine assumption:</b> this formula assumes the
  1243	     * annotation index uses the <b>Lucene</b> {@code knn_vector}
  1244	     * engine, which scores {@code cosinesimil} as
  1245	     * {@code score = (1 + cos) / 2} (equivalently {@code (2 - d) / 2}
  1246	     * where {@code d = 1 - cos}). This holds for OpenSearch 3.1's
  1247	     * default engine and any 2.x deployment that pins
  1248	     * {@code method.engine = "lucene"} in the index mapping.</p>
  1249	     *
  1250	     * <p>The OpenSearch 2.15 NMSLIB and Faiss engines score
  1251	     * {@code cosinesimil} differently ({@code 1 / (1 + d)} —
  1252	     * {@link <a href="https://opensearch.org/docs/2.15/search-plugins/knn/knn-score-script/#spaces">OS 2.15 knn spaces</a>}).
  1253	     * If a deployment opts into a non-Lucene engine, this conversion
  1254	     * will produce wrong cosine values. To use a non-Lucene engine
  1255	     * safely, either pin the engine globally (recommended) or add an
  1256	     * engine-aware conversion variant here.</p>
  1257	     */
  1258	    public static double openSearchScoreToCosine(double osScore) {
  1259	        double cos = 2.0 * osScore - 1.0;
  1260	        if (cos > 1.0) return 1.0;
  1261	        if (cos < -1.0) return -1.0;
  1262	        return cos;
  1263	    }
  1264	
  1265	    // finds annotations based on embedding vector matches
  1266	    // null means we didnt have an embedding to query with
  1267	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject taskParams,
  1268	        boolean useClauses, String method, String methodVersion) {
  1269	        return getMatches(myShepherd,
  1270	                getMatchQuery(myShepherd, taskParams, useClauses, method, methodVersion));
  1271	    }
  1272	
  1273	    // where we already have the query
  1274	    public List<Annotation> getMatches(Shepherd myShepherd, JSONObject matchQuery) {
  1275	        if (matchQuery == null) return null;
  1276	        List<Annotation> anns = new ArrayList<Annotation>();
  1277	        OpenSearch os = new OpenSearch();
  1278	        long startTime = System.currentTimeMillis();
  1279	        JSONObject queryRes = null;
  1280	        int hitSize = -1;
  1281	        try {
  1282	            int pageSize = 10000;
  1283	            try {
  1284	                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
  1285	            } catch (Exception ex) {}
  1286	            os.deletePit("annotation");
  1287	            queryRes = os.queryPit("annotation", matchQuery, 0, pageSize, null, null);
  1288	            hitSize = queryRes.optJSONObject("hits").optJSONObject("total").optInt("value");
  1289	        } catch (Exception ex) {
  1290	            System.out.println("getMatches() exception: " + ex);
  1291	            ex.printStackTrace();
  1292	        }
  1293	        JSONArray hits = OpenSearch.getHits(queryRes);
  1294	        for (int i = 0; i < hits.length(); i++) {
  1295	            JSONObject hit = hits.optJSONObject(i);
  1296	            if (hit == null) continue;
  1297	            Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
  1298	            if (ann != null) {
  1299	                // OS Lucene knn with cosinesimil returns (1 + cos) / 2 in
  1300	                // [0, 1]. Convert to raw cosine in [-1, 1] so the score
  1301	                // matches the MiewID pipeline's native output.
  1302	                // (Empty-match-prospects C17.)
  1303	                double osScore = hit.optDouble("_score", 0.0d);
  1304	                ann.setOpensearchScore(openSearchScoreToCosine(osScore));
  1305	                anns.add(ann);
  1306	            }
  1307	        }
  1308	        System.out.println("getMatches() results: hitSize=" + hitSize + "; hits length=" +
  1309	            hits.length() + "; anns size=" + anns.size() + "; " +
  1310	            (System.currentTimeMillis() - startTime) + "ms");
  1311	        return anns;
  1312	    }
  1313	
  1314	    /*
  1315	        sorta weird to have this in here, but it is inherently linked with getMatchingSetXXX() above ...
  1316	        this is a string that uniquely identifies the matchingSet, dependent of content (e.g. cant be based on content uuids)
  1317	     */
  1318	    public String getCurvrankDailyTag(JSONObject taskParams) {
  1319	        if (taskParams == null) return null;
  1320	        String userId = taskParams.optString("userId", null);
  1321	        JSONObject j = taskParams.optJSONObject("matchingSetFilter");
  1322	        if (j == null) return null;
  1323	        String tag = "";
  1324	
  1325	        // currently we have only owner=me, which requires a userId
  1326	        JSONArray owner = j.optJSONArray("owner");
  1327	        boolean mineOnly = false;
  1328	        if ((owner != null) && (userId != null)) {
  1329	            for (int i = 0; i < owner.length(); i++) {
  1330	                if ("me".equals(owner.optString(i, null))) mineOnly = true;
  1331	            }
  1332	        }
  1333	        if (mineOnly) {
  1334	            tag += "user:" + userId;
  1335	        } else {
  1336	            tag += "user:ANY";
  1337	        }
  1338	        // now locations, which we want sorted and lowercase, to ensure consistency in multi-value names
  1339	        List<String> locs = new ArrayList<String>();
  1340	        if (j.optString("locationId", null) != null) locs.add(j.getString("locationId"));
  1341	        JSONArray larr = j.optJSONArray("locationIds");
  1342	        if (larr != null) {
  1343	            for (int i = 0; i < larr.length(); i++) {
  1344	                String val = larr.optString(i, null);
  1345	                if ((val != null) && !locs.contains(val)) locs.add(val);

 succeeded in 284ms:
   830	        System.out.println("iaCheckMissing -> " + tryAgain);
   831	        return tryAgain;
   832	    }
   833	
   834	    private static Object mediaAssetToUri(MediaAsset ma) {
   835	        // System.out.println("=================== mediaAssetToUri " + ma + "\n" + ma.getParameters() + ")\n");
   836	        URL curl = ma.containerURLIfPresent();
   837	
   838	        if (curl == null) curl = ma.webURL();
   839	        if (ma.getStore() instanceof LocalAssetStore) {
   840	            // return ma.localPath().toString(); //nah, lets skip local and go for "url" flavor?
   841	            if (curl == null) return null;
   842	            return curl.toString();
   843	        } else {
   844	            if (curl == null) return null;
   845	            return curl.toString();
   846	        }
   847	    }
   848	
   849	    // If you realllllly want to send species I'll just swallow it.
   850	    public static JSONObject beginIdentifyAnnotations(ArrayList<Annotation> qanns,
   851	        ArrayList<Annotation> tanns, JSONObject queryConfigDict, JSONObject userConfidence,
   852	        Shepherd myShepherd, String species, Task task, String baseUrl, boolean fastlane) {
   853	        System.out.println(
   854	            "INFO: You no longer need to send species with call to beginIdentifyAnnotations. It is derived from the Annotation's Encounters.");
   855	        return beginIdentifyAnnotations(qanns, tanns, queryConfigDict, userConfidence, myShepherd,
   856	                task, baseUrl, fastlane);
   857	    }
   858	
   859	    // trying to optimize the original beginIdentifyAnnotations()  [above]
   860	    public static JSONObject beginIdentifyAnnotations(ArrayList<Annotation> qanns,
   861	        ArrayList<Annotation> tanns, JSONObject queryConfigDict, JSONObject userConfidence,
   862	        Shepherd myShepherd, Task task, String baseUrl, boolean fastlane) {
   863	        long tt = System.currentTimeMillis();
   864	
   865	        if (!isIAPrimed())
   866	            System.out.println("WARNING: beginIdentifyAnnotations() called without IA primed");
   867	        // possibly could exclude qencs from tencs?
   868	        String jobID = "-1";
   869	        JSONObject results = new JSONObject();
   870	        results.put("success", false); // pessimism!
   871	        ArrayList<Annotation> allAnns = new ArrayList<Annotation>();
   872	        String taskID = "_UNKNOWN_";
   873	        if (task != null) taskID = task.getId(); // "should never happen"
   874	        log(taskID, jobID, new JSONObject("{\"_action\": \"initIdentify\"}"),
   875	            myShepherd.getContext());
   876	        String curvrankDailyTag = null;
   877	
   878	        Util.mark("OPTIMIZED identify process start", tt);
   879	        try {
   880	            for (Annotation ann : qanns) {
   881	                if (validForIdentification(ann, myShepherd.getContext())) {
   882	                    allAnns.add(ann);
   883	                }
   884	            }
   885	            // this voodoo via JH will insure that .acmId is on the MediaAssets which are loaded via getMatchingSet() below (for speed)
   886	            javax.jdo.FetchGroup grp =
   887	                myShepherd.getPM().getPersistenceManagerFactory().getFetchGroup(MediaAsset.class,
   888	                "BIA");
   889	            grp.addMember("acmId").addMember("store").addMember("id").addMember(
   890	                "parametersAsString").addMember("parameters").addMember("metadata").addMember(
   891	                "labels").addMember("userLatitude").addMember("userLongitude").addMember(
   892	                "userDateTime").addMember("features");
   893	            myShepherd.getPM().getFetchPlan().addGroup("BIA");
   894	
   895	            Util.mark("OPT bia 2", tt);
   896	            if (tanns == null || tanns.isEmpty()) {
   897	                String iaClass = qanns.get(0).getIAClass();
   898	                System.out.println(
   899	                    "beginIdentifyAnnotations(): have to set tanns. Matching set being built from the first ann in the list.");
   900	                tanns = qanns.get(0).getMatchingSet(myShepherd,
   901	                    (task == null) ? null : task.getParameters());
   902	                curvrankDailyTag = qanns.get(0).getCurvrankDailyTag((task ==
   903	                    null) ? null : task.getParameters());
   904	            }
   905	            Util.mark("OPT bia 3", tt);
   906	
   907	            System.out.println("- mark 2");
   908	            if (tanns != null && !tanns.isEmpty()) {
   909	                System.out.println("INFO: tanns, (matchingSet) is not null. Contains " +
   910	                    tanns.size() + " annotations.");
   911	                for (Annotation ann : tanns) {
   912	                    allAnns.add(ann);
   913	                }
   914	            }
   915	            Util.mark("OPT bia 4", tt);
   916	
   917	            results.put("sendAnnotationsAsNeeded", sendAnnotationsAsNeeded(allAnns, myShepherd));
   918	            Util.mark("OPT bia 4X", tt);
   919	            if (tanns != null) {
   920	                System.out.println("                               ... qanns has: " + qanns.size() +
   921	                    " ... taans has: " + tanns.size());
   922	            } else {
   923	                System.out.println("                               ... qanns has: " + qanns.size() +
   924	                    " ... taans is null! Target is all annotations.");
   925	            }
   926	            if (curvrankDailyTag != null) {
   927	                if (queryConfigDict == null) queryConfigDict = new JSONObject();
   928	                // from JP on 12/27/2019 - if we want to specify an unfiltered list, just omit the tag
   929	                if (!curvrankDailyTag.toLowerCase().equals("user:any") &&
   930	                    !curvrankDailyTag.toLowerCase().equals("user:any;locs:")) {
   931	                    queryConfigDict.put("curvrank_daily_tag", curvrankDailyTag);
   932	                }
   933	            }
   934	            Util.mark("bia 4C", tt);
   935	            // this should attempt to repair missing Annotations
   936	            boolean tryAgain = true;
   937	            JSONObject identRtn = null;
   938	            while (tryAgain) {
   939	                identRtn = sendIdentify(qanns, tanns, queryConfigDict, userConfidence, baseUrl,
   940	                    myShepherd.getContext(), taskID, fastlane);
   941	                System.out.println("identRtn contains ========> " + identRtn);
   942	                if (identRtn == null) {
   943	                    results.put("error", "identRtn == NULL");
   944	                    results.put("success", false);
   945	                    return results;
   946	                } else if (identRtn != null && identRtn.getJSONObject("status") != null &&
   947	                    !identRtn.getJSONObject("status").getString("message").equals("rejected")) {
   948	                    tryAgain = iaCheckMissing(identRtn, myShepherd.getContext(), myShepherd);
   949	                } else {
   950	                    results.put("error", identRtn.get("status"));
   951	                    results.put("success", false);
   952	                    return results;
   953	                }
   954	            }
   955	            results.put("sendIdentify", identRtn);
   956	
   957	            System.out.println("sendIdentify ---> " + identRtn);
   958	            if ((identRtn != null) && identRtn.has("status") &&
   959	                identRtn.getJSONObject("status").getBoolean("success")) {
   960	                jobID = identRtn.get("response").toString();
   961	                results.put("success", true);
   962	            } else {
   963	                System.out.println("beginIdentifyAnnotations() unsuccessful on sendIdentify(): " +
   964	                    identRtn);
   965	                results.put("error", identRtn.get("status"));
   966	                results.put("success", false);
   967	            }
   968	        } catch (Exception ex) { // most likely from sendFoo()
   969	            System.out.println("WARN: IBEISIA.beginIdentity() failed due to an exception: " +
   970	                ex.toString());
   971	            ex.printStackTrace();
   972	            results.put("success", false);
   973	            results.put("error", ex.toString());
   974	        }
   975	        JSONObject jlog = new JSONObject();
   976	        jlog.put("_action", "sendIdentify");
   977	        jlog.put("_response", results);
   978	        log(taskID, jobID, jlog, myShepherd.getContext());
   979	
   980	        return results;

 succeeded in 291ms:
   210	            map.get("annot_name_list").add((name == null) ? "____" : name);
   211	            markSent(ann);
   212	            ct++;
   213	        }
   214	        System.out.println("sendAnnotations(): sending " + ct);
   215	        if (ct < 1) return null;
   216	        // this should only be checking for missing images, i guess?
   217	        boolean tryAgain = true;
   218	        JSONObject res = null;
   219	        while (tryAgain) {
   220	            res = RestClient.post(url, hashMapToJSONObject(map));
   221	            tryAgain = iaCheckMissing(res, context, myShepherd);
   222	        }
   223	        return res;
   224	    }
   225	
   226	    // note: if tanns here is null, then it is exemplar for this species
   227	    public static JSONObject sendIdentify(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns,
   228	        JSONObject queryConfigDict, JSONObject userConfidence, String baseUrl, String context,
   229	        String taskId, boolean fastlane)
   230	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   231	        InvalidKeyException {
   232	        if (!isIAPrimed()) System.out.println("WARNING: sendIdentify() called without IA primed");
   233	        String u = IA.getProperty(context, "IBEISIARestUrlStartIdentifyAnnotations");
   234	        if (u == null)
   235	            throw new MalformedURLException(
   236	                      "configuration value IBEISIARestUrlStartIdentifyAnnotations is not set");
   237	        URL url = new URL(u);
   238	        long startTime = System.currentTimeMillis();
   239	        Util.mark("sendIdentify-0  tanns.size()=" + ((tanns == null) ? "null" : tanns.size()),
   240	            startTime);
   241	
   242	        Shepherd myShepherd = new Shepherd(context);
   243	        myShepherd.setAction("IBEISIA.sendIdentify");
   244	        myShepherd.beginDBTransaction();
   245	
   246	        HashMap<String, Object> map = new HashMap<String, Object>();
   247	        map.put("callback_url", callbackUrl(baseUrl));
   248	        map.put("jobid", taskId);
   249	        if (queryConfigDict != null) map.put("query_config_dict", queryConfigDict);
   250	        // OK, check here and dont let HotSpotter in
   251	        boolean isHotspotter = false;
   252	        if (queryConfigDict != null && queryConfigDict.toString().indexOf("sv_on") > -1)
   253	            isHotspotter = true;
   254	        if (fastlane && !isHotspotter) map.put("lane", "fast");
   255	        map.put("matching_state_list",
   256	            IBEISIAIdentificationMatchingState.allAsJSONArray(myShepherd));                             // this is "universal"
   257	        if (userConfidence != null) map.put("user_confidence", userConfidence);
   258	        ArrayList<JSONObject> qlist = new ArrayList<JSONObject>();
   259	        ArrayList<JSONObject> tlist = new ArrayList<JSONObject>();
   260	        ArrayList<String> qnlist = new ArrayList<String>();
   261	        ArrayList<String> tnlist = new ArrayList<String>();
   262	
   263	        ///note: for names here, we make the gigantic assumption that they individualID has been migrated to uuid already!
   264	        String iaClass = null;
   265	        Util.mark("sendIdentify-1", startTime);
   266	        for (Annotation ann : qanns) {
   267	            if (!validForIdentification(ann, context)) {
   268	                System.out.println("WARNING: IBEISIA.sendIdentify() [qanns] skipping invalid " +
   269	                    ann);
   270	                continue;
   271	            }
   272	            // Should we fall back on gleaning species from the Enc? We do it to find the iaClass initially.. Redundant? Squishy? Discuss.
   273	            if (iaClass == null) {
   274	                if (ann.getIAClass() != null) {
   275	                    iaClass = ann.getIAClass();
   276	                } else {
   277	                    iaClass = org.ecocean.ia.plugin.WildbookIAM.getIASpecies(ann, myShepherd);
   278	                }
   279	            }
   280	            qlist.add(toFancyUUID(ann.getAcmId()));
   281	            /* jonc now fixed it so we can have null/unknown ids... but apparently this needs to be "____" (4 underscores) ; also names are now just
   282	               strings (not uuids)
   283	               // i guess (???) we need some kinda ID for query annotations (even tho we dont know who they are); so wing it?
   284	               qnlist.add(toFancyUUID(Util.generateUUID()));
   285	             */
   286	
   287	            qnlist.add(IA_UNKNOWN_NAME);
   288	        }
   289	        Util.mark("sendIdentify-2", startTime);
   290	        // Do we have a qaan? We need one, or load a failure response.
   291	        if (qlist.isEmpty()) {
   292	            myShepherd.rollbackDBTransaction();
   293	            myShepherd.closeDBTransaction();
   294	            JSONObject noQueryAnn = new JSONObject();
   295	            noQueryAnn.put("status", new JSONObject().put("message", "rejected"));
   296	            noQueryAnn.put("error", "No query annotation was valid for identification. ");
   297	            return noQueryAnn;
   298	        }
   299	        Util.mark("sendIdentify-A", startTime);
   300	        boolean setExemplarCaches = false;
   301	        if (tanns == null) {
   302	            System.out.println("--- sendIdentify() passed null tanns..... why???");
   303	            System.out.println("     gotta compute :(");
   304	            tanns = qanns.get(0).getMatchingSet(myShepherd);
   305	        }
   306	        Util.mark("sendIdentify-B  tanns.size()=" + ((tanns == null) ? "null" : tanns.size()),
   307	            startTime);
   308	        // int ct = 0;
   309	        if (tanns != null)
   310	            for (Annotation ann : tanns) {
   311	                // Util.mark(ct + "]  sib-1 ann=" + ann.getId() + "/" + ann.getAcmId(), startTime);
   312	                if (!validForIdentification(ann, context)) {
   313	                    System.out.println("WARNING: IBEISIA.sendIdentify() [tanns] skipping invalid " +
   314	                        ann);
   315	                    continue;
   316	                }
   317	                // Util.mark("      sib-2 ann=" + ann.getId() + "/" + ann.getAcmId(), startTime);
   318	                // ct++;
   319	                tlist.add(toFancyUUID(ann.getAcmId()));
   320	                String indivId = annotGetIndiv(ann, myShepherd);
   321	                // argh we need to standardize this and/or have a method. :/
   322	                if ((indivId == null) || (indivId.toLowerCase().equals("unassigned"))) {
   323	                    tnlist.add(IA_UNKNOWN_NAME);
   324	                } else {
   325	                    tnlist.add(indivId);
   326	                }
   327	            }
   328	        // query_config_dict={'pipeline_root' : 'BC_DTW'}
   329	
   330	        Util.mark("sendIdentify-C", startTime);
   331	        // bail on empty target annots:
   332	        if (Util.collectionIsEmptyOrNull(tlist)) {
   333	            System.out.println("WARNING: bailing on empty target list");
   334	            JSONObject emptyRtn = new JSONObject();
   335	            JSONObject status = new JSONObject();
   336	            status.put("message", "rejected");
   337	            status.put("error", "Empty target annotation list");
   338	            status.put("emptyTargetAnnotations", true);
   339	            emptyRtn.put("status", status);
   340	
   341	            myShepherd.rollbackDBTransaction();
   342	            myShepherd.closeDBTransaction();
   343	
   344	            return emptyRtn;
   345	        }
   346	        map.put("query_annot_uuid_list", qlist);
   347	        map.put("database_annot_uuid_list", tlist);
   348	        // We need to send IA null in this case. If you send it an empty list of annotation names or uuids it will check against nothing..
   349	        // If the list is null it will check against everything.
   350	        map.put("query_annot_name_list", qnlist);
   351	        // if we have no target lists, pass null for "all"
   352	        if (Util.collectionIsEmptyOrNull(tlist)) {
   353	            map.put("database_annot_uuid_list", null);
   354	        } else {
   355	            map.put("database_annot_uuid_list", tlist);
   356	        }
   357	        if (Util.collectionIsEmptyOrNull(tnlist)) {
   358	            map.put("database_annot_name_list", null);
   359	        } else {
   360	            map.put("database_annot_name_list", tnlist);
   361	        }
   362	        Util.mark("sendIdentify-D", startTime);
   363	
   364	        System.out.println(
   365	            "===================================== qlist & tlist ========================= [taskId="
   366	            + taskId + "]");
   367	        System.out.println(qlist + " callback=" + callbackUrl(baseUrl));
   368	        if (Util.collectionIsEmptyOrNull(tlist) || Util.collectionIsEmptyOrNull(tnlist)) {
   369	            System.out.println("tlist/tnlist == null! Checking against all.");
   370	        } else {
   371	            System.out.println("tlist.size()=" + tlist.size() + " annnnd tnlist.size()=" +
   372	                tnlist.size());
   373	        }
   374	        System.out.println("qlist.size()=" + qlist.size() + " annnnd qnlist.size()=" +
   375	            qnlist.size() + ". not printing the map about to be POSTed because it's a big'un.");

exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java | sed -n '1,130p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java | sed -n '560,625p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java | sed -n '1,330p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java | sed -n '280,360p'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 252ms:
   560	        return task.getParameters();
   561	    }
   562	
   563	    public JSONObject getTaskMatchingSetFilter() {
   564	        if (task == null) return null;
   565	        JSONObject params = task.getParameters();
   566	        if (params == null) return null;
   567	        return params.optJSONObject("matchingSetFilter");
   568	    }
   569	
   570	/*
   571	    see note at top about candidates vs numberCandidates
   572	    public int numberCandidates() {
   573	        return Util.collectionSize(candidates);
   574	    }
   575	 */
   576	    public int numberProspects() {
   577	        return this.numberProspects;
   578	    }
   579	
   580	    public Set<String> prospectScoreTypes() {
   581	        Set<String> types = new HashSet<String>();
   582	
   583	        if (numberProspects() == 0) return types;
   584	        for (MatchResultProspect mrp : prospects) {
   585	            types.add(mrp.getType());
   586	        }
   587	        return types;
   588	    }
   589	
   590	    // if cutoff < 0 then it will not be truncated at all
   591	    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
   592	        Set<String> projectIds, Shepherd myShepherd) {
   593	        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();
   594	
   595	        if (numberProspects() == 0) return pros;
   596	        for (MatchResultProspect mrp : prospects) {
   597	            if (mrp.isType(type) && mrp.isInProjects(projectIds, myShepherd)) pros.add(mrp);
   598	        }
   599	        Collections.sort(pros);
   600	        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
   601	        return pros;
   602	    }
   603	
   604	    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
   605	        JSONObject sj = new JSONObject();
   606	
   607	        for (String type : prospectScoreTypes()) {
   608	            JSONArray jarr = new JSONArray();
   609	            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
   610	                jarr.put(mrp.jsonForApiGet(myShepherd));
   611	            }
   612	            sj.put(type, jarr);
   613	        }
   614	        return sj;
   615	    }
   616	
   617	    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
   618	        JSONObject rtn = new JSONObject();
   619	
   620	        rtn.put("id", id);
   621	        rtn.put("queryAnnotation", annotationDetails(queryAnnotation, myShepherd));
   622	        rtn.put("numberTotalProspects", numberProspects());
   623	        rtn.put("numberCandidates", getNumberCandidates());
   624	        rtn.put("created", Util.millisToISO8601String(created));
   625	        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));

 succeeded in 249ms:
     1	package org.ecocean.ia;
     2	
     3	import java.io.File;
     4	import java.io.IOException;
     5	import java.net.URL;
     6	import java.util.ArrayList;
     7	import java.util.Collections;
     8	import java.util.Comparator;
     9	import java.util.HashMap;
    10	import java.util.HashSet;
    11	import java.util.List;
    12	import java.util.Map;
    13	import java.util.Set;
    14	
    15	import org.json.JSONArray;
    16	import org.json.JSONObject;
    17	
    18	import org.ecocean.api.UploadedFiles;
    19	import org.ecocean.Annotation;
    20	import org.ecocean.Encounter;
    21	import org.ecocean.ia.MLService;
    22	import org.ecocean.ia.Task;
    23	import org.ecocean.identity.IBEISIA;
    24	import org.ecocean.identity.IdentityServiceLog;
    25	import org.ecocean.media.AssetStore;
    26	import org.ecocean.media.Feature;
    27	import org.ecocean.media.MediaAsset;
    28	import org.ecocean.media.URLAssetStore;
    29	import org.ecocean.MarkedIndividual;
    30	import org.ecocean.RestClient;
    31	import org.ecocean.shepherd.core.Shepherd;
    32	import org.ecocean.Util;
    33	
    34	public class MatchResult implements java.io.Serializable {
    35	    private String id;
    36	    private long created;
    37	    private Task task;
    38	    private Set<MatchResultProspect> prospects;
    39	    private Annotation queryAnnotation;
    40	    private int numberCandidates = 0;
    41	    // we store *actual* count here, but they may not all exist
    42	    // via .prospects due to MAXIMUM_PROSPECTS_STORED (see below)
    43	    private int numberProspects = 0;
    44	    // not sure we really *need* true fk link to these annots
    45	    // they might be gone now and will we ever use this?
    46	    // so for now we just populate numberCandidates
    47	    private Set<Annotation> candidates;
    48	    // fallback number to cutoff number of prospects to return
    49	    public static final int DEFAULT_PROSPECTS_CUTOFF = 100;
    50	    // number of MatchResultProspects [per type] to actually store (hotspotter
    51	    // results can produce thousands, but storing them all is excessive)
    52	    public static final int MAXIMUM_PROSPECTS_STORED = 500;
    53	
    54	    public MatchResult() {
    55	        id = Util.generateUUID();
    56	        created = System.currentTimeMillis();
    57	    }
    58	
    59	    public MatchResult(Task task) {
    60	        this();
    61	        this.task = task;
    62	    }
    63	
    64	    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
    65	    throws IOException {
    66	        this();
    67	        this.createFromIdentityServiceLog(isLog, myShepherd);
    68	    }
    69	
    70	    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
    71	    throws IOException {
    72	        this();
    73	        this.task = task;
    74	        this.createFromJsonResult(jsonResult, myShepherd);
    75	    }
    76	
    77	    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
    78	        Shepherd myShepherd)
    79	    throws IOException {
    80	        this();
    81	        this.task = task;
    82	        this.numberCandidates = numberCandidates;
    83	        this.setQueryAnnotationFromTask();
    84	        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
    85	        this.populateProspects(annots, false, myShepherd);
    86	        this.populateProspects(annots, true, myShepherd);
    87	    }
    88	
    89	    public int getNumberCandidates() {
    90	        return numberCandidates;
    91	    }
    92	
    93	    public void createFromIdentityServiceLog(IdentityServiceLog isLog, Shepherd myShepherd)
    94	    throws IOException {
    95	        if (isLog == null) throw new IOException("log passed is null");
    96	        String taskId = isLog.getTaskID();
    97	        this.task = myShepherd.getTask(taskId);
    98	        if (this.task == null) throw new IOException("task is null for taskId=" + taskId);
    99	        JSONObject res = isLog.getJsonResult();
   100	        if (res == null) {
   101	            System.out.println("ERROR: getJsonResult() failed on " + isLog + " with status=" +
   102	                isLog.getStatusJson());
   103	            throw new IOException("could not get json result");
   104	        }
   105	        createFromJsonResult(res, myShepherd);
   106	    }
   107	
   108	    public Annotation setQueryAnnotationFromTask()
   109	    throws IOException {
   110	        if (this.task == null)
   111	            throw new IOException("setQueryAnnotationFromTask() failed as task is null");
   112	        int numAnns = this.task.countObjectAnnotations();
   113	        if (numAnns < 1)
   114	            throw new IOException("setQueryAnnotationFromTask() failed as task has no annotations");
   115	        if (numAnns > 1)
   116	            System.out.println("WARNING: setQueryAnnotationFromTask() has " + numAnns +
   117	                " annotations; using first");
   118	        this.queryAnnotation = this.task.getObjectAnnotations().get(0);
   119	        return this.queryAnnotation;
   120	    }
   121	
   122	    // json_result section should be passed here
   123	    public void createFromJsonResult(JSONObject res, Shepherd myShepherd)
   124	    throws IOException {
   125	        if (res == null) throw new IOException("null json_result passed");
   126	        if (res.optJSONArray("query_annot_uuid_list") == null)
   127	            throw new IOException("no query annot list");
   128	        if (res.getJSONArray("query_annot_uuid_list").length() < 1)
   129	            throw new IOException("empty query annot list");
   130	        // for now we are assuming a single query annot. sorrynotsorry.
   131	        String queryAnnotId = IBEISIA.fromFancyUUID(res.getJSONArray(
   132	            "query_annot_uuid_list").optJSONObject(0));
   133	        this.queryAnnotation = getAnnotationFromAcmId(queryAnnotId, myShepherd);
   134	        if (this.queryAnnotation == null)
   135	            throw new IOException("failed to load query annot from id=" + queryAnnotId);
   136	        if (res.optJSONObject("cm_dict") == null)
   137	            throw new IOException("no cm_dict found in " + res);
   138	        // results is the real scores (etc) we are looking for.... finally!
   139	        JSONObject results = res.getJSONObject("cm_dict").optJSONObject(queryAnnotId);
   140	        if (results == null) throw new IOException("no actual results found");
   141	        // see note at top about true annot list of candidates vs number
   142	        if (res.optJSONArray("database_annot_uuid_list") != null)
   143	            this.numberCandidates = res.getJSONArray("database_annot_uuid_list").length();
   144	/*
   145	        annot_score_list <=> dannot_uuid_list
   146	        score_list is for indiv scores but on dannot_uuid_list (same length)
   147	        name_score_list <=> unique_name_uuid_list ???
   148	 */
   149	        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
   150	            results.optJSONArray("annot_score_list"), results.optJSONArray("dannot_extern_list"),
   151	            results.optString("dannot_extern_reference", null), myShepherd);
   152	        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
   153	            results.optJSONArray("score_list"), results.optJSONArray("dannot_extern_list"),
   154	            results.optString("dannot_extern_reference", null), myShepherd);
   155	        System.out.println("[DEBUG] createFromJsonResult() created " + this);
   156	    }
   157	
   158	    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
   159	        JSONArray externs, String externRef, Shepherd myShepherd)
   160	    throws IOException {
   161	        if ((annotIds == null) || (scores == null))
   162	            throw new IOException("null annotIds or scores");
   163	        if (annotIds.length() != scores.length())
   164	            throw new IOException("mismatch in size of annotIds/scores");
   165	        if (this.prospects == null)
   166	            this.prospects = new HashSet<MatchResultProspect>();
   167	        int num = 0;
   168	        this.numberProspects += annotIds.length(); // true number of prospects
   169	        for (int i = 0; i < annotIds.length(); i++) {
   170	            double score = scores.optDouble(i, -Double.MAX_VALUE);
   171	            String id = IBEISIA.fromFancyUUID(annotIds.optJSONObject(i));
   172	            Annotation ann = getAnnotationFromAcmId(id, myShepherd);
   173	            if (ann == null) {
   174	                System.out.println("WARNING: populateProspect failed to load annotId=" + id +
   175	                    "; skipping; score=" + score);
   176	                continue;
   177	            }
   178	            MediaAsset ma = null;
   179	            // we only try if we have a true value in externs[i]
   180	            if ((externs != null) && (externs.length() > i) && externs.optBoolean(i, false))
   181	                ma = createInspectionHeatmapAsset(externRef, id, myShepherd);
   182	            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
   183	            num++;
   184	            if (num >= MAXIMUM_PROSPECTS_STORED) {
   185	                System.out.println("[DEBUG] hit max (" + MAXIMUM_PROSPECTS_STORED +
   186	                    ") number storable prospects on " + this);
   187	                break;
   188	            }
   189	        }
   190	        return num;
   191	    }
   192	
   193	    // we just have a list of annots which matched (e.g. via vectors in opensearch)
   194	    // NOTE: currently does not check MAXIMUM_PROSPECTS_STORED because vector search
   195	    // tends to return relatively few prospects. TODO adjust later if this proves untrue.
   196	    //
   197	    // Empty-match-prospects design Track 2 C13: prospects are created with
   198	    // {@code asset=null}. The PairX inspection image is populated later by
   199	    // {@link MatchInspectionPairxEnricher} in a Phase A/B/C flow so the
   200	    // outer Shepherd is never held across the PairX HTTP call. Holding a
   201	    // Shepherd across that ~10-30s POST would risk connection-pool
   202	    // exhaustion under load (Codex C12 review High).
   203	    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
   204	        Shepherd myShepherd)
   205	    throws IOException {
   206	        if (Util.collectionIsEmptyOrNull(annots)) return 0;
   207	        if (this.prospects == null)
   208	            this.prospects = new HashSet<MatchResultProspect>();
   209	        if (scoreByIndividual) {
   210	            // C19: per-individual scores are now the best per-annotation
   211	            // cosine within the group (same scale as the annot tab), and
   212	            // un-ID'd candidates are emitted as singletons.
   213	            _populateProspectsByIndividual(annots, myShepherd);
   214	        } else {
   215	            // these scores are direct from opensearch
   216	            for (Annotation ann : annots) {
   217	                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
   218	                    null));
   219	            }
   220	        }
   221	        this.numberProspects = this.prospects.size();
   222	        return this.numberProspects;
   223	    }
   224	
   225	    /**
   226	     * Build indiv-tab prospects (scoreType "indiv") from the knn
   227	     * candidate annotations. C19 changes from the prior count-based
   228	     * scoring of identified-only individuals to a uniform best-cosine
   229	     * scoring that also surfaces un-ID'd candidates as singleton
   230	     * "individuals" — matching the legacy WBIA HotSpotter behavior of
   231	     * assigning placeholder name {@code "____"} to un-ID'd
   232	     * annotations.
   233	     *
   234	     * <p>For each MarkedIndividual that owns one or more candidate
   235	     * annotations, the prospect carries the best-cosine annotation
   236	     * within that group and score = its cosine similarity (raw OS
   237	     * knn score, post-C17 transform). For each candidate whose
   238	     * encounter has no MarkedIndividual, a singleton prospect carries
   239	     * that annotation and its own cosine. All entries sort by score
   240	     * descending — the indiv tab and the image tab now use the same
   241	     * scoring scale.</p>
   242	     */
   243	    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
   244	        // Key by individual ID (String), NOT by MarkedIndividual object.
   245	        // Base.equals() compares by id but Base does not override
   246	        // hashCode(), so two distinct MarkedIndividual instances with
   247	        // the same id would hash to different buckets and emit
   248	        // duplicate indiv prospects. Keying by id avoids that
   249	        // (Codex C19 review Medium).
   250	        Map<String, List<Annotation> > tally =
   251	            new HashMap<String, List<Annotation> >();
   252	        List<Annotation> singletons = new ArrayList<Annotation>();
   253	
   254	        for (Annotation ann : annots) {
   255	            Encounter enc = ann.findEncounter(myShepherd);
   256	            // No encounter at all: skip (no individual axis possible).
   257	            if (enc == null) continue;
   258	            MarkedIndividual indiv = enc.getIndividual();
   259	            if (indiv == null || indiv.getId() == null) {
   260	                // Un-ID'd (no MarkedIndividual or its id is null):
   261	                // treat as a singleton "individual" so the indiv tab
   262	                // still shows it, matching legacy WBIA behavior (C19).
   263	                // The annotation is the singleton's own representative;
   264	                // the frontend renders these as "potential new
   265	                // individual" rows since the annotation.encounter.
   266	                // individual link is null.
   267	                singletons.add(ann);
   268	            } else {
   269	                String key = indiv.getId();
   270	                if (!tally.containsKey(key))
   271	                    tally.put(key, new ArrayList<Annotation>());
   272	                tally.get(key).add(ann);
   273	            }
   274	        }
   275	        if (tally.isEmpty() && singletons.isEmpty()) return;
   276	
   277	        // For each ID'd individual: pick the highest-cosine annotation
   278	        // within its candidate group. That becomes the rep prospect.
   279	        // Multi-annotation individuals no longer get a count-based
   280	        // boost — score is per-annotation cosine, same scale as the
   281	        // image tab. prospectsSorted(...) handles final ordering, so
   282	        // we don't pre-sort here.
   283	        for (Map.Entry<String, List<Annotation> > ent : tally.entrySet()) {
   284	            Annotation best = null;
   285	            double bestScore = -Double.MAX_VALUE;
   286	            for (Annotation cand : ent.getValue()) {
   287	                double s = cand.getOpensearchScore();
   288	                if (best == null || s > bestScore) {
   289	                    best = cand;
   290	                    bestScore = s;
   291	                }
   292	            }
   293	            if (best != null) {
   294	                this.prospects.add(new MatchResultProspect(best, bestScore, "indiv", null));
   295	            }
   296	        }
   297	        // Singletons: one prospect per un-ID'd annotation, scored by
   298	        // its own cosine.
   299	        for (Annotation ann : singletons) {
   300	            this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(),
   301	                "indiv", null));
   302	        }
   303	    }
   304	
   305	    /**
   306	     * Public read-only view of the prospects collection so the
   307	     * {@link MatchInspectionPairxEnricher} can iterate them in Phase A
   308	     * and Phase C without reaching into private state. Returns the
   309	     * underlying Set; callers must not mutate.
   310	     */
   311	    public Set<MatchResultProspect> getProspects() {
   312	        return this.prospects;
   313	    }
   314	
   315	    /**
   316	     * Public accessor for the queryAnnotation field. Returns whatever
   317	     * value was set by {@link #setQueryAnnotationFromTask()} or
   318	     * {@link #createFromJsonResult(JSONObject, Shepherd)} — may be null
   319	     * if neither has run.
   320	     */
   321	    public Annotation getQueryAnnotation() {
   322	        return this.queryAnnotation;
   323	    }
   324	
   325	    /** Public accessor for the JDO primary key. */
   326	    public String getId() {
   327	        return this.id;
   328	    }
   329	
   330	    private Annotation getAnnotationFromAcmId(String acmId, Shepherd myShepherd) {

 succeeded in 271ms:
     1	package org.ecocean.ia;
     2	
     3	import java.util.HashSet;
     4	import java.util.Set;
     5	
     6	import org.json.JSONArray;
     7	import org.json.JSONObject;
     8	
     9	import org.ecocean.Annotation;
    10	import org.ecocean.Encounter;
    11	import org.ecocean.media.MediaAsset;
    12	import org.ecocean.shepherd.core.Shepherd;
    13	import org.ecocean.Util;
    14	
    15	public class MatchResultProspect implements java.io.Serializable, Comparable<MatchResultProspect> {
    16	    private Annotation annotation;
    17	    private double score = 0.0d;
    18	    private String scoreType;
    19	    private MediaAsset asset;
    20	    private MatchResult matchResult;
    21	
    22	    public MatchResultProspect() {}
    23	
    24	    public MatchResultProspect(Annotation ann) {
    25	        this();
    26	        this.annotation = ann;
    27	    }
    28	
    29	    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
    30	        this();
    31	        this.annotation = ann;
    32	        this.score = score;
    33	        this.scoreType = type;
    34	        this.asset = asset;
    35	    }
    36	
    37	    /**
    38	     * Attach a PairX inspection MediaAsset to this prospect. Used by
    39	     * {@link MatchInspectionPairxEnricher} in Phase C to enrich
    40	     * prospects after the MatchResult has been persisted (empty-match-
    41	     * prospects design Track 2 C13: PairX is now non-blocking and
    42	     * runs without holding the outer Shepherd across HTTP).
    43	     */
    44	    public void setAsset(MediaAsset asset) {
    45	        this.asset = asset;
    46	    }
    47	
    48	    public MediaAsset getAsset() {
    49	        return asset;
    50	    }
    51	
    52	    public Annotation getAnnotation() {
    53	        return annotation;
    54	    }
    55	
    56	    public double getScore() {
    57	        return score;
    58	    }
    59	
    60	    public String getType() {
    61	        return scoreType;
    62	    }
    63	
    64	    public boolean isType(String type) {
    65	        if (type == null) return (this.scoreType == null);
    66	        return type.equals(this.scoreType);
    67	    }
    68	
    69	    public boolean isInProjects(Set<String> projectIds, Shepherd myShepherd) {
    70	        // if we have no projects to filter on, we consider this to be in it
    71	        if (Util.collectionIsEmptyOrNull(projectIds)) return true;
    72	        if (annotation == null) return false;
    73	        Encounter enc = annotation.findEncounter(myShepherd);
    74	        if (enc == null) return false;
    75	        return enc.isInProjects(projectIds, myShepherd);
    76	    }
    77	
    78	    public String toString() {
    79	        return scoreType + "=" + score + " on " + annotation + " for " + matchResult;
    80	    }
    81	
    82	    public JSONObject jsonForApiGet(Shepherd myShepherd) {
    83	        JSONObject rtn = new JSONObject();
    84	
    85	        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd));
    86	        rtn.put("score", score);
    87	        // skipping scoreType since this is currently only used filtered by scoreType already
    88	        if (asset != null) {
    89	            JSONObject aj = asset.toSimpleJSONObject();
    90	            aj.put("url", asset.webURL()); // we have no "safe" url
    91	            rtn.put("asset", aj);
    92	        }
    93	        return rtn;
    94	    }
    95	
    96	    // used in sorting
    97	    @Override public int compareTo(MatchResultProspect other) {
    98	        // we invert this so higher score is first
    99	        int comp = Double.compare(other.score, this.score);
   100	        // if the scores are the same (comp == 0), we want to ensure consistent/deterministic
   101	        // ordering (otherwise tied scores come back random order), so we use annot id
   102	        if ((comp == 0) && (this.annotation != null) && (this.annotation.getId() != null) && (other.annotation != null))
   103	            return this.annotation.getId().compareTo(other.annotation.getId());
   104	        // scores are *not* equal, so we just let comparison stand as-is
   105	        return comp;
   106	    }
   107	}

 succeeded in 287ms:
   280	        }
   281	        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
   282	            task.numberAnnotations() + " annots) has embedding match: " + iaConfig);
   283	        // Track per-subtask outcomes so the parent task's terminal state
   284	        // reflects reality (plan v2 §commit #3: previous code unconditionally
   285	        // marked the parent "completed" even if every subtask failed).
   286	        int subtasksOk = 0;
   287	        int subtasksFailed = 0;
   288	        for (Annotation ann : task.getObjectAnnotations()) {
   289	            // every ann gets a subTask
   290	            Task subTask = new Task(task);
   291	            subTask.addObject(ann);
   292	            // we need embedding(s) on this annot to find prospects, so lets try to make some on the fly if we dont have one
   293	            // TODO not sure if this is wise, or it would be better to just fail outright and let some background process do this
   294	            if (ann.numberEmbeddings() < 1) {
   295	                System.out.println(
   296	                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
   297	                    ann);
   298	                try {
   299	                    ann.extractEmbeddings(myShepherd);
   300	                } catch (IAException ex) {
   301	                    System.out.println(
   302	                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
   303	                        " due to: " + ex);
   304	                }
   305	                // if none now, we just fail and continue onto next annot
   306	                if (ann.numberEmbeddings() < 1) {
   307	                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
   308	                        " due to no suitable embeddings for " + iaConfig);
   309	                    subTask.setStatus("error");
   310	                    subTask.setStatusDetailsAddError("REQUIRED",
   311	                        "no suitable embeddings for getMatches()");
   312	                    subTask.setCompletionDateInMilliseconds();
   313	                    myShepherd.getPM().makePersistent(subTask);
   314	                    subtasksFailed++;
   315	                    continue;
   316	                }
   317	            }
   318	            // Build matchingSetQuery for the candidate count.
   319	            boolean useClauses = false; // TODO how??
   320	            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
   321	                useClauses);
   322	            // getMatchingSetQuery can return null (e.g. encounter missing,
   323	            // taxonomy filtered out). Skip this subtask cleanly rather than
   324	            // NPE'ing on the queryCount/getMatchQuery below.
   325	            if (matchingSetQuery == null) {
   326	                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
   327	                subTask.setStatus("error");
   328	                subTask.setStatusDetailsAddError("REQUIRED", "null matchingSetQuery");
   329	                subTask.setCompletionDateInMilliseconds();
   330	                myShepherd.getPM().makePersistent(subTask);
   331	                subtasksFailed++;
   332	                continue;
   333	            }
   334	            // Count eligible candidates BEFORE getMatchQuery, which mutates
   335	            // matchingSetQuery in place (Annotation.java:1203). The previous
   336	            // order returned the knn result count instead of the candidate
   337	            // count. Defense in depth: also pass a deep clone of
   338	            // matchingSetQuery to getMatchQuery so the eligible-set object
   339	            // never silently changes shape if a future caller relies on it.
   340	            OpenSearch os = new OpenSearch();
   341	            int numberCandidates = -2;
   342	            try {
   343	                numberCandidates = os.queryCount("annotation", matchingSetQuery);
   344	            } catch (IOException ex) {
   345	                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
   346	            }
   347	            // Read method/version directly from the new _id_conf contract;
   348	            // fall back to splitting model_id for legacy configs.
   349	            String method = iaConfig.optString("method", null);
   350	            String version = iaConfig.optString("version", null);
   351	            if (!Util.stringExists(method)) {
   352	                String[] mv = MLService.getMethodValues(iaConfig);
   353	                method = mv[0];
   354	                version = mv[1];
   355	            }
   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
   357	                new JSONObject(matchingSetQuery.toString()));
   358	            // i think this will never happen now, due to on-the-fly fix above; but leaving to be safe
   359	            if (matchQuery == null) {
   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +

exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java | sed -n '360,455p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java | sed -n '390,465p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java | sed -n '600,880p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py | sed -n '360,430p;515,550p;780,830p'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 294ms:
   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
   361	                    " due to no suitable embeddings for " + iaConfig);
   362	                subTask.setStatus("error");
   363	                subTask.setStatusDetailsAddError("REQUIRED",
   364	                    "no suitable embeddings for getMatches()");
   365	                subTask.setCompletionDateInMilliseconds();
   366	                myShepherd.getPM().makePersistent(subTask);
   367	                subtasksFailed++;
   368	                continue; // on to next ann
   369	            }
   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
   371	            System.out.println("findMatchProspects() on " + ann + " found " +
   372	                Util.collectionSize(prospects) + " prospects (in " + numberCandidates +
   373	                " candidates) for subTask " + subTask.getId());
   374	            boolean mrOk = true;
   375	            try {
   376	                // we build this even if empty, cuz that means we got results; just not nice ones
   377	                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
   378	                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
   379	                myShepherd.getPM().makePersistent(mr);
   380	            } catch (IOException ex) {
   381	                System.out.println("findMatchProspects() MatchResult creation failed on " +
   382	                    subTask + ": " + ex);
   383	                ex.printStackTrace();
   384	                mrOk = false;
   385	            }
   386	            if (mrOk) {
   387	                subTask.setStatus("completed");
   388	                subtasksOk++;
   389	            } else {
   390	                subTask.setStatus("error");
   391	                subTask.setStatusDetailsAddError("UNKNOWN",
   392	                    "MatchResult persistence failed");
   393	                subtasksFailed++;
   394	            }
   395	            subTask.setCompletionDateInMilliseconds();
   396	            myShepherd.getPM().makePersistent(subTask);
   397	        }
   398	        // Reflect subtask outcomes on the parent. Any failure marks the
   399	        // parent "error" with a PARTIAL detail; otherwise "completed".
   400	        // No "partial" terminal status is introduced — Task.statusInEndState
   401	        // only recognizes completed/error, so adding one would ripple into
   402	        // polling/UI semantics.
   403	        if (subtasksFailed == 0) {
   404	            task.setStatus("completed");
   405	        } else {
   406	            task.setStatus("error");
   407	            task.setStatusDetailsAddError("PARTIAL",
   408	                subtasksFailed + " of " + (subtasksOk + subtasksFailed) + " subtasks failed");
   409	        }
   410	        task.setCompletionDateInMilliseconds();
   411	        return true;
   412	    }
   413	
   414	    public String toString() {
   415	        String st = "Embedding " + id;
   416	
   417	        st += " (vec len " + this.vectorLength() + ")";
   418	        if (annotation != null) st += " [Annotation " + annotation.getId() + "]";
   419	        st += " " + this.getMethodDescription();
   420	        st += " " + Util.prettyPrintDateTime(this.created);
   421	        return st;
   422	    }
   423	
   424	    public boolean equals(final Object o2) {
   425	        if (o2 == null) return false;
   426	        if (!(o2 instanceof Embedding)) return false;
   427	        Embedding two = (Embedding)o2;
   428	        if ((this.id == null) || (two == null) || (two.getId() == null)) return false;
   429	        return this.id.equals(two.getId());
   430	    }
   431	
   432	    public int hashCode() {
   433	        if (id == null) return Util.generateUUID().hashCode();
   434	        return id.hashCode();
   435	    }
   436	}

 succeeded in 296ms:
   390	            System.out.println("subTask[" + i + "] => " + taskRes.toString());
   391	        }
   392	        if (limitTargetSize > -1) res.put("_limitTargetSize", limitTargetSize);
   393	        res.put("tasks", taskList);
   394	        res.put("success", true);
   395	        return res;
   396	    }
   397	
   398	    private static JSONObject _sendIdentificationTask(Annotation ann, String context,
   399	        String baseUrl, JSONObject queryConfigDict, JSONObject userConfidence, int limitTargetSize,
   400	        Task task, Shepherd myShepherd, boolean fastlane)
   401	    throws IOException {
   402	        boolean success = true;
   403	        String annTaskId = "UNKNOWN_" + Util.generateUUID();
   404	
   405	        if (task != null) annTaskId = task.getId();
   406	        JSONObject taskRes = new JSONObject();
   407	        taskRes.put("taskId", annTaskId);
   408	        JSONArray jids = new JSONArray();
   409	        jids.put(ann.getId()); // for now there is only one
   410	        taskRes.put("annotationIds", jids);
   411	        System.out.println("+ starting ident task " + annTaskId);
   412	        JSONObject shortCut = IAQueryCache.tryTargetAnnotationsCache(context, ann, taskRes,
   413	            myShepherd);
   414	        if (shortCut != null) return shortCut;
   415	        try {
   416	            // TODO: cache this examplars list (per species)
   417	            ///note: this can all go away if/when we decide not to need limitTargetSize
   418	            ArrayList<Annotation> matchingSet = null;
   419	            if (limitTargetSize > -1) {
   420	                matchingSet = ann.getMatchingSet(myShepherd);
   421	                if ((matchingSet == null) || (matchingSet.size() < 5)) {
   422	                    System.out.println("=======> Small matching set for this Annotation id= " +
   423	                        ann.getId());
   424	                    System.out.println("=======> Set size is: " + matchingSet.size());
   425	                    System.out.println("=======> Specific Epithet is: " +
   426	                        ann.findEncounter(myShepherd).getSpecificEpithet() + "    Genus is: " +
   427	                        ann.findEncounter(myShepherd).getGenus());
   428	                }
   429	                if (matchingSet.size() > limitTargetSize) {
   430	                    System.out.println(
   431	                        "WARNING: limited identification matchingSet list size from " +
   432	                        matchingSet.size() + " to " + limitTargetSize);
   433	                    matchingSet = new ArrayList<Annotation>(matchingSet.subList(0,
   434	                        limitTargetSize));
   435	                }
   436	                taskRes.put("matchingSetSize", matchingSet.size());
   437	            }
   438	            /// end can-go-away
   439	
   440	            ArrayList<Annotation> qanns = new ArrayList<Annotation>();
   441	            qanns.add(ann);
   442	            IBEISIA.waitForIAPriming();
   443	            JSONObject sent = IBEISIA.beginIdentifyAnnotations(qanns, matchingSet, queryConfigDict,
   444	                userConfidence, myShepherd, task, baseUrl, fastlane);
   445	            if (!sent.optBoolean("success",
   446	                false) && sent.toString().indexOf("emptyTargetAnnotations") == -1) {
   447	                String errorMsg = sent.optString("error", "(unknown error)");
   448	                System.out.println("beginIdentifyAnnotations() was unsuccessful due to " +
   449	                    errorMsg + "; hopefully we requeue");
   450	                // set the status as complete as we faithfully completed the query but nothing to match against
   451	                if (task != null) {
   452	                    task.setStatus("completed");
   453	                    task.setCompletionDateInMilliseconds(Long.valueOf(System.currentTimeMillis()));
   454	                    myShepherd.updateDBTransaction();
   455	                }
   456	                throw new IOException("beginIdentifyAnnotations() failed due to " + errorMsg);
   457	            }
   458	            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING);
   459	            taskRes.put("beginIdentify", sent);
   460	            String jobId = null;
   461	            if ((sent.optJSONObject("status") != null) &&
   462	                sent.getJSONObject("status").optBoolean("success", false))
   463	                jobId = sent.optString("response", null);
   464	            taskRes.put("jobId", jobId);
   465	            // validIds.toArray(new String[validIds.size()])

 succeeded in 341ms:
   600	     * Callers gating read-only decisions on status (e.g., the
   601	     * empty-match-prospects Track 2 batch gate) need this so a read of
   602	     * a sibling task's status doesn't mutate that task as a side
   603	     * effect.
   604	     *
   605	     * <p>(Empty-match-prospects design Track 2 C7.)</p>
   606	     */
   607	    public String getStoredStatus() {
   608	        return this.status;
   609	    }
   610	
   611	    public String getStatus(Shepherd myShepherd) {
   612	        // see if we might be dead in the water
   613	        // TODO skipping status==null cuz i cant figure out what this means and there are so many of them
   614	        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {
   615	            this.status = "error";
   616	            long ti = timeInactive();
   617	            setStatusDetailsAddError("TIMEOUT",
   618	                "this task is likely timed out; no activity for " + Util.millisToHumanApprox(ti));
   619	            return this.status;
   620	        }
   621	        // if status is not null, just send it
   622	        if (status != null) return status;
   623	        // otherwise
   624	        // note: this is LOCAL status :(  so it is not changing this.status, only returning the value
   625	        String status = "waiting to queue";
   626	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
   627	            myShepherd);
   628	        if (logs != null && logs.size() > 0) {
   629	            Collections.reverse(logs); // so it has newest first like mostRecent above
   630	            IdentityServiceLog l = logs.get(0);
   631	            JSONObject islObj = l.toJSONObject();
   632	            if (islObj.optString("status") != null &&
   633	                islObj.optString("status").equals("completed")) {
   634	                status = islObj.optString("status");
   635	            } else if (islObj.optJSONObject("status") != null &&
   636	                (islObj.optJSONObject("status").optJSONObject("needReview") != null)) {
   637	                status = "completed";
   638	            } else if (logs.toString().indexOf("score") > -1) {
   639	                status = "completed";
   640	            } else if (islObj.toString().indexOf("HTTP error code") > -1) {
   641	                status = "error";
   642	            } else if (!islObj.optString("queueStatus").equals("")) {
   643	                status = islObj.optString("queueStatus");
   644	            } else if (islObj.opt("status") != null &&
   645	                islObj.opt("status").toString().indexOf("initIdentify") > -1) {
   646	                status = "queuing";
   647	            }
   648	            // if(islObj.optString("queueStatus").equals("queued")){sendIdentify=false;}
   649	            // if(status.equals("waiting to queue"))System.out.println("islObj: "+islObj.toString());
   650	        }
   651	        System.out.println("[DEBUG] getStatus() fell through to status='" + status + "' on Task " +
   652	            this.getId());
   653	        return status;
   654	    }
   655	
   656	    // this is stitched together from import.jsp. godspeed.
   657	    // "resumeStalledTasks" functionality was stripped from this. if needed, revisit original method in import.jsp
   658	    // also the original building/modification of (passed-in) idStatusMap is dropped
   659	    public String getOverallStatus(Shepherd myShepherd) {
   660	        String status = "unknown";
   661	
   662	        if (this.hasChildren()) {
   663	            // accumulate status across children
   664	            HashMap<String, String> map = new HashMap<String, String>();
   665	            // this should only ever be two layers deep
   666	            for (Task childTask : this.getChildren()) {
   667	                if (childTask.hasChildren()) {
   668	                    for (Task childTask2 : childTask.getChildren()) {
   669	                        if ((childTask2.getObjectAnnotations() != null) &&
   670	                            (childTask2.getObjectAnnotations().size() > 0) &&
   671	                            childTask2.getObjectAnnotations().get(0).getMatchAgainst() &&
   672	                            (childTask2.getObjectAnnotations().get(0).getIAClass() != null)) {
   673	                            map.put(childTask2.getId(), childTask2.getStatus(myShepherd));
   674	                        }
   675	                    }
   676	                } else {
   677	                    if ((childTask.getObjectAnnotations() != null) &&
   678	                        (childTask.getObjectAnnotations().size() > 0) &&
   679	                        childTask.getObjectAnnotations().get(0).getMatchAgainst() &&
   680	                        (childTask.getObjectAnnotations().get(0).getIAClass() != null)) {
   681	                        map.put(childTask.getId(), childTask.getStatus(myShepherd));
   682	                    }
   683	                }
   684	            }
   685	            // now, how do we report these?
   686	            HashMap<String, Integer> resultsMap = new HashMap<String, Integer>();
   687	            for (String key : map.values()) {
   688	                // task results
   689	                if (!resultsMap.containsKey(key)) {
   690	                    resultsMap.put(key, new Integer(1));
   691	                } else {
   692	                    resultsMap.put(key, new Integer(resultsMap.get(key) + 1));
   693	                }
   694	            }
   695	            status = resultsMap.toString();
   696	        } else { // childless
   697	            status = this.getStatus(myShepherd);
   698	        }
   699	        return status;
   700	    }
   701	
   702	    public boolean isFastlane(Shepherd myShepherd) {
   703	        String status = "waiting to queue";
   704	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
   705	            myShepherd);
   706	
   707	        if (getParameters() != null && getParameters().optBoolean("fastlane", false)) {
   708	            return true;
   709	        }
   710	        return false;
   711	    }
   712	
   713	    public void setStatus(String newStatus) {
   714	        setModified();
   715	        if (newStatus == null) status = null;
   716	        else { status = newStatus; }
   717	    }
   718	
   719	    public Long getCompletionDateInMilliseconds() { return completionDateInMilliseconds; }
   720	
   721	    // this will set all date stuff based on ms since epoch
   722	    public void setCompletionDateInMilliseconds(Long ms) {
   723	        this.completionDateInMilliseconds = ms;
   724	    }
   725	
   726	    // no arg = set to now
   727	    public void setCompletionDateInMilliseconds() {
   728	        this.completionDateInMilliseconds = Long.valueOf(System.currentTimeMillis());
   729	    }
   730	
   731	    // capture original queue message to make this Task more easily resumeable
   732	    public String getQueueResumeMessage() { return queueResumeMessage; }
   733	    public void setQueueResumeMessage(String message) {
   734	        if (message == null) { queueResumeMessage = null; } else {
   735	            queueResumeMessage = message;
   736	        }
   737	    }
   738	
   739	    public JSONObject getMatchingSetFilter() {
   740	        if (getParameters() == null) return null;
   741	        return getParameters().optJSONObject("matchingSetFilter");
   742	    }
   743	
   744	    public JSONObject getIdentificationMethodInfo() {
   745	        if (getParameters() == null) return null;
   746	        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
   747	        JSONObject rtn = new JSONObject();
   748	        // vector/embed flavor
   749	        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
   750	            null) != null) {
   751	            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
   752	                "model_id", null);
   753	            if (modelId == null) {
   754	                rtn.put("description", "Vector embedding match");
   755	            } else {
   756	                rtn.put("description", "Vector embedding match (model: " + modelId + ")");
   757	                rtn.put("modelId", modelId);
   758	            }
   759	            return rtn;
   760	        }
   761	        // it seems both of these are in most logs (and are identical), but being safe in case there are
   762	        // examples in the wild with only one
   763	        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
   764	            "query_config_dict");
   765	        if (conf == null)
   766	            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
   767	                "queryConfigDict");
   768	        // we set HotSpotter if pipeline_root is not set here
   769	        if (conf != null) rtn.put("name", conf.optString("pipeline_root", "HotSpotter"));
   770	        rtn.put("description",
   771	            getParameters().getJSONObject("ibeis.identification").optString("description",
   772	            "unknown algorithm/method"));
   773	        return rtn;
   774	    }
   775	
   776	    // convenience
   777	    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
   778	        return myShepherd.getMatchResults(this);
   779	    }
   780	
   781	    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
   782	        List<MatchResult> all = myShepherd.getMatchResults(this);
   783	
   784	        if (Util.collectionIsEmptyOrNull(all)) return null;
   785	        return all.get(0);
   786	    }
   787	
   788	    // logs are returned in chronological order here, so if the latest is desired, take the LAST one
   789	    public List<MatchResult> generateMatchResults(Shepherd myShepherd) {
   790	        List<MatchResult> mrs = new ArrayList<MatchResult>();
   791	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(this.id, "IBEISIA",
   792	            myShepherd);
   793	
   794	        if (logs == null) return mrs;
   795	        for (IdentityServiceLog log : logs) {
   796	            JSONObject res = log.getJsonResult();
   797	            // in theory this is how we can tell if it is an ident result log versus detection
   798	            if ((res != null) && (res.optJSONObject("cm_dict") != null)) {
   799	                try {
   800	                    MatchResult mr = new MatchResult(log, myShepherd);
   801	                    System.out.println("[INFO] generateMatchResults() [log t=" +
   802	                        log.getTimestamp() + "] on Task " + this.getId() + " generated: " + mr);
   803	                    myShepherd.getPM().makePersistent(mr);
   804	                    mrs.add(mr);
   805	                    setStatusDetailsAddLog("Created " + mr + " from IdentityServiceLog " +
   806	                        log.getTimestamp());
   807	                } catch (java.io.IOException ex) {
   808	                    System.out.println("[ERROR] generateMatchResults() [log t=" +
   809	                        log.getTimestamp() + "] on Task " + this.getId() + " failed: " + ex);
   810	                    ex.printStackTrace();
   811	                    setStatusDetailsAddError("UNKNOWN",
   812	                        "Creation of MatchResult from IdentityServiceLog " + log.getTimestamp() +
   813	                        " failed due to: " + ex);
   814	                }
   815	            }
   816	        }
   817	        return mrs;
   818	    }
   819	
   820	    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
   821	        JSONObject rtn = new JSONObject();
   822	
   823	        rtn.put("id", getId());
   824	        rtn.put("parentTaskId", getParentId());
   825	        rtn.put("dateCreated", Util.millisToISO8601String(getCreatedLong()));
   826	        rtn.put("dateCompleted", Util.millisToISO8601String(getCompletionDateInMilliseconds()));
   827	        rtn.put("timeInactiveMillis", timeInactive());
   828	        // TODO theory is that we might not need to use/store queryAnnotation on MatchResult as
   829	        // we should have it here, hence this debugging value ... possible optimization for later
   830	        if (hasObjectAnnotations()) {
   831	            JSONArray annotArr = new JSONArray();
   832	            for (Annotation ann : getObjectAnnotations()) {
   833	                if (ann != null) annotArr.put(ann.getId());
   834	            }
   835	            rtn.put("__taskAnnotations", annotArr);
   836	        }
   837	        JSONObject methodInfo = getIdentificationMethodInfo();
   838	        // methodInfo gates the "identification-like" extras (method label,
   839	        // matchingSetFilter, legacy WBIA log-based MR generation). The v2
   840	        // ml-service path doesn't persist `ibeis.identification` on its
   841	        // match tasks, so methodInfo is null even though the MatchResult
   842	        // is correctly persisted in the DB. Without the decoupling below,
   843	        // the API silently drops the matchResults field from the JSON
   844	        // tree and the React match-results page renders empty
   845	        // (empty-match-prospects design Track 2 C15).
   846	        if (methodInfo != null) {
   847	            rtn.put("method", methodInfo);
   848	            rtn.put("matchingSetFilter", getMatchingSetFilter());
   849	        }
   850	        // Always serialize an existing MatchResult regardless of methodInfo.
   851	        // Vector (v2) results generate their MatchResult eagerly during
   852	        // matching, so getLatestMatchResult will find one whenever the
   853	        // pipeline ran successfully. Legacy WBIA results still rely on
   854	        // generateMatchResults (log-based) to construct the MR on demand,
   855	        // and that path stays gated by methodInfo since it interprets
   856	        // identification-method-specific log JSON.
   857	        MatchResult mr = getLatestMatchResult(myShepherd);
   858	        if ((mr == null) && (methodInfo != null) && !hasChildren()) {
   859	            System.out.println(
   860	                "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
   861	                this.getId());
   862	            List<MatchResult> mrs = generateMatchResults(myShepherd);
   863	            rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
   864	            if (mrs.size() > 0) {
   865	                mr = mrs.get(mrs.size() - 1);
   866	                // this hack is important cuz it forces a db commit even though we are a GET api call sorrynotsorry
   867	                rtn.put("_commitShepherd", true);
   868	            }
   869	        }
   870	        if (mr != null)
   871	            rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
   872	        // now we recurse thru children if applicable
   873	        if (hasChildren()) {
   874	            JSONArray charr = new JSONArray();
   875	            for (Task child : children) {
   876	                // TODO decide if we need to process child????
   877	                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
   878	                // we have to bubble this up all the way to the toplevel  :/
   879	                if (childJson.optBoolean("_commitShepherd", false))
   880	                    rtn.put("_commitShepherd", true);

 succeeded in 325ms:
   360	    grouped_scores = ut.apply_grouping(score_list, groupxs)
   361	
   362	    ibs = depc.controller
   363	    unique_qnids = ibs.get_annot_nids(unique_qaids)
   364	
   365	    # scores
   366	    _iter = zip(unique_qaids, unique_qnids, grouped_daids, grouped_scores)
   367	    for qaid, qnid, daids, scores in _iter:
   368	        dnids = ibs.get_annot_nids(daids)
   369	
   370	        # Remove distance to self
   371	        annot_scores = np.array(scores)
   372	        daid_list_ = np.array(daids)
   373	        dnid_list_ = np.array(dnids)
   374	
   375	        is_valid = daid_list_ != qaid
   376	        daid_list_ = daid_list_.compress(is_valid)
   377	        dnid_list_ = dnid_list_.compress(is_valid)
   378	        annot_scores = annot_scores.compress(is_valid)
   379	
   380	        # Hacked in version of creating an annot match object
   381	        match_result = wbia.AnnotMatch()
   382	        match_result.qaid = qaid
   383	        match_result.qnid = qnid
   384	        match_result.daid_list = daid_list_
   385	        match_result.dnid_list = dnid_list_
   386	        match_result._update_daid_index()
   387	        match_result._update_unique_nid_index()
   388	
   389	        grouped_annot_scores = vt.apply_grouping(annot_scores, match_result.name_groupxs)
   390	        name_scores = np.array([np.max(dists) for dists in grouped_annot_scores])
   391	        match_result.set_cannonical_name_score(annot_scores, name_scores)
   392	        yield match_result
   393	
   394	
   395	class MiewIdRequest(dt.base.VsOneSimilarityRequest):
   396	    _symmetric = False
   397	    _tablename = 'MiewId'
   398	
   399	    @ut.accepts_scalar_input
   400	    def get_fmatch_overlayed_chip(request, aid_list, overlay=True, config=None):
   401	        depc = request.depc
   402	        ibs = depc.controller
   403	        chips = ibs.get_annot_chips(aid_list)
   404	        return chips
   405	
   406	    def render_with_visualization(request, cm, aid, **kwargs):
   407	        depc = request.depc
   408	        ibs = depc.controller
   409	
   410	        species = ibs.get_annot_species_texts(aid)
   411	        model, config, (model_url, config_url) = read_config_and_load_model(species)
   412	
   413	        aid_list = [cm.qaid, aid]
   414	        test_loader, test_dataset = _load_data(ibs, aid_list, config, batch_size=1)
   415	
   416	        out_image = draw_one_pairx(
   417	            config.engine.device,
   418	            test_loader,
   419	            model,
   420	            config.data.crop_bbox,
   421	            visualization_type="lines_and_colors",
   422	            layer_key="backbone.blocks.3",
   423	            k_lines=20,
   424	            k_colors=10,
   425	        )
   426	        return out_image
   427	
   428	    def render_without_visualization(request, cm, aid, **kwargs):
   429	        overlay = kwargs.get('draw_fmatches')
   430	        chips = request.get_fmatch_overlayed_chip(
   515	                aid_score_list = aid_scores_from_score_dict(miew_id_scores, daids)
   516	                aid_score_dict = dict(zip(daids, aid_score_list))
   517	
   518	                qaid_score_dict[qaid] = aid_score_dict
   519	        else:
   520	            miew_id_annot_distances = ibs.miew_id_predict_light_distance(
   521	                qaid,
   522	                daids,
   523	                config['config_path'],
   524	            )
   525	            qaid_score_dict[qaid] = {}
   526	            for daid, miew_id_annot_distance in zip(daids, miew_id_annot_distances):
   527	                qaid_score_dict[qaid][daid] = distance_to_score(miew_id_annot_distance)
   528	
   529	        # Optional PairX re-ranking (engaged via config flag from Wildbook)
   530	        if config.get('pairx_rerank', False):
   531	            qaid_score_dict[qaid] = _pairx_rerank(
   532	                ibs, qaid, daids, qaid_score_dict[qaid], config
   533	            )
   534	
   535	    for qaid, daid in zip(qaid_list, daid_list):
   536	        if qaid == daid:
   537	            daid_score = 0.0
   538	        else:
   539	            aid_score_dict = qaid_score_dict.get(qaid, {})
   540	            daid_score = aid_score_dict.get(daid)
   541	        yield (daid_score,)
   542	
   543	
   544	@register_ibs_method
   545	def evaluate_distmat(ibs, aid_list, config, use_depc, ranks=[1, 5, 10, 20]):
   546	    """Evaluate 1vsall accuracy of matching on annotations by
   547	    computing distance matrix.
   548	    """
   549	    embs = np.array(miew_id_embedding(ibs, aid_list, config, use_depc))
   550	    print('Computing distance matrix ...')
   780	    return final_aids
   781	
   782	
   783	@register_ibs_method
   784	def miew_id_new_accuracy(ibs, aid_list, min_sights=3, max_sights=10):
   785	    aids = subset_with_resights_range(ibs, aid_list, min_sights, max_sights)
   786	    ranks = miew_id_mass_accuracy(ibs, aids)
   787	    accuracy = accuracy_at_k(ibs, ranks)
   788	    print(
   789	        'Accuracy at k for annotations with %s-%s sightings:' % (min_sights, max_sights)
   790	    )
   791	    print(accuracy)
   792	    return accuracy
   793	
   794	
   795	def _db_labels_for_miew_id(ibs, daid_list):
   796	    db_labels = ibs.get_annot_name_texts(daid_list, distinguish_unknowns=True)
   797	    # db_auuids = ibs.get_annot_name_rowids(daid_list)
   798	    # # later we must know which db_labels are for single auuids, hence prefix
   799	    # db_auuids = [UNKNOWN + '-' + str(auuid) for auuid in db_auuids]
   800	    # db_labels = [
   801	    #     lab if lab is not UNKNOWN else auuid for lab, auuid in zip(db_labels, db_auuids)
   802	    # ]
   803	    db_labels = np.array(db_labels)
   804	    return db_labels
   805	
   806	def distance_to_score(distance):
   807	    score = (2 - distance) / 2
   808	    score = np.float64(score)
   809	    return score
   810	
   811	def distance_dicts_to_score_dicts(distance_dicts, conversion_func=distance_to_score):
   812	    score_dicts = distance_dicts.copy()
   813	    name_score_dicts = {}
   814	    for entry in score_dicts:
   815	        name_score_dicts[entry['label']] = conversion_func(entry['distance'])
   816	    return name_score_dicts
   817	
   818	def aid_scores_from_score_dict(name_score_dict, daid_list):
   819	    daid_scores = [name_score_dict.get(daid, 0) for daid in daid_list]
   820	    return daid_scores
   821	
   822	def aid_scores_from_name_scores(ibs, name_score_dict, daid_list):
   823	    daid_name_list = list(_db_labels_for_miew_id(ibs, daid_list))
   824	
   825	    name_count_dict = {
   826	        name: daid_name_list.count(name) for name in name_score_dict.keys()
   827	    }
   828	
   829	    name_annotwise_score_dict = {
   830	        name: name_score_dict[name] / name_count_dict[name]

 succeeded in 37529ms:
 M .github/workflows/claude-code-review.yml
 M .github/workflows/claude.yml
 M .gitignore
 M archive/copyBuild.sh
 M archive/imageTransformWrapper.sh
 M archive/sql/ml_service_embedding_audit.sql
 M archive/sql/ml_service_idempotency.sql
 M config/git-json-info.ps1
 M config/git-json-info.sh
 M devops/deploy/.dockerfiles/alloy/config.alloy
 M devops/deploy/.dockerfiles/alloy/config.tempo.alloy
 M devops/deploy/.dockerfiles/db/postgresql.conf
 M devops/deploy/.dockerfiles/nginx/nginx-https.conf
 M devops/deploy/.dockerfiles/nginx/nginx.conf
 M devops/deploy/.dockerfiles/opensearch/log4j2.properties
 M devops/deploy/.dockerfiles/tomcat/server.xml
 M devops/deploy/_env.template
 M devops/deploy/docker-compose.yml
 M devops/development/.dockerfiles/tomcat/server.xml
 M devops/development/docker-compose.yml
 M docs/plans/2026-05-09-ml-service-migration-v2.md
 M docs/plans/2026-05-18-empty-match-prospects-design.md
 M docs/plans/2026-05-18-wbia-image-registration-design.md
A  docs/plans/2026-05-19-mls-bbox-clamp-design-review.md
 M frontend/maven-build.sh
 M frontend/package-lock.json
 M frontend/package.json
 M frontend/src/App.jsx
 M frontend/src/AuthenticatedSwitch.jsx
 M frontend/src/FrontDesk.jsx
 M frontend/src/SiteSettingsContext.jsx
 M frontend/src/UnAuthenticatedSwitch.jsx
 M frontend/src/__tests__/FrontDesk.test.js
 M frontend/src/__tests__/components/AddAdditionalModal.test.js
 M frontend/src/__tests__/components/AuthenticatedSwitch.test.js
 M frontend/src/__tests__/components/Map.test.js
 M frontend/src/__tests__/components/SearchAndSelectInput.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportEditableDataTable.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportImageUpload.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportInstuctionsModal.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportStore.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportTask.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoCard.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoModal.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/EditAnnotation.test.js
 M frontend/src/__tests__/pages/Encounter/Encounter.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterPageViewOnly.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterStore.test.js
 M frontend/src/__tests__/pages/Encounter/HelperFunctions.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/ImageCard.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModal.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModalStore.test.js
 M frontend/src/__tests__/pages/Encounter/LocationSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MapDisplay.test.js
 M frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsReview.test.js
 M frontend/src/__tests__/pages/Encounter/MoreDetails.test.js
 M frontend/src/__tests__/pages/Encounter/NewMatchStore.test.js
 M frontend/src/__tests__/pages/Encounter/ProjectsCard.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/BiologicalSamplesAndAnalysesFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/CalenderView.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/DateFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterFormStore.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterSearch.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/IndividualDateFilter.test.js
 M frontend/src/__tests__/pages/LandingPage/LandingPage.test.js
 M frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js
 M frontend/src/__tests__/pages/ManualAnnotationPage/ManualAnnotation.test.js
 M frontend/src/__tests__/pages/MatchResults/CreateNewIndividualModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/InstructionsModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchConfirmedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchCriteriaDrawer.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx
 M frontend/src/__tests__/pages/MatchResults/NewIndividualCreatedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js
 M frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js
 M frontend/src/__tests__/pages/PoliciesAndData.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ImageSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/PlaceSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportAnEncounter.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportEncounterStore.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/SpeciesSection.test.js
 M frontend/src/__tests__/pages/login/LoginPageAuthenticate.test.js
 M frontend/src/__tests__/pages/login/LoginPageButtonState.test.js
 M frontend/src/__tests__/pages/login/LoginPageError.test.js
 M frontend/src/__tests__/pages/login/LoginPageInput.test.js
 M frontend/src/__tests__/pages/login/LoginPageLinks.test.js
 M frontend/src/__tests__/pages/login/LoginPagePasswordToggle.test.js
 M frontend/src/__tests__/pages/login/LoginPageRender.test.js
 M frontend/src/__tests__/pages/login/LoginPageSubmit.test.js
 M frontend/src/components/AnnotationOverlay.jsx
 M frontend/src/components/AuthenticatedAppHeader.jsx
 M frontend/src/components/Chip.jsx
 M frontend/src/components/ContainerWithSpinner.jsx
 M frontend/src/components/DataTable.jsx
 M frontend/src/components/FilterPanel.jsx
 M frontend/src/components/Footer.jsx
 M frontend/src/components/Form/FormGroupMultiSelect.jsx
 M frontend/src/components/ImageModal.jsx
 M frontend/src/components/LoadingScreen.jsx
 M frontend/src/components/Map.jsx
 M frontend/src/components/MultiSelectWithCheckbox.jsx
 M frontend/src/components/SimpleDataTable.jsx
 M frontend/src/components/SmallSpinner.jsx
 M frontend/src/components/UnAuthenticatedAppHeader.jsx
 M frontend/src/components/filterFields/BiologicalSamplesAndAnalysesFilter.jsx
 M frontend/src/components/filterFields/DateFilter.jsx
 M frontend/src/components/filterFields/IdentityFilter.jsx
 M frontend/src/components/filterFields/ImageLabelFilter.jsx
 M frontend/src/components/filterFields/IndividualsObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/LocationFilterMap.jsx
 M frontend/src/components/filterFields/LocationFilterText.jsx
 M frontend/src/components/filterFields/MetadataFilter.jsx
 M frontend/src/components/filterFields/ObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/SocialFilter.jsx
 M frontend/src/components/generalInputs/CoordinatesInput.jsx
 M frontend/src/components/header/HeaderDropdownItems.jsx
 M frontend/src/components/header/Menu.jsx
 M frontend/src/components/home/PickUpWhereYouLeft.jsx
 M frontend/src/components/icons/EditIcon.jsx
 M frontend/src/components/icons/EncounterIcon.jsx
 M frontend/src/components/icons/ExitIcon.jsx
 M frontend/src/components/icons/FullscreenIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon2.jsx
 M frontend/src/constants/navMenu.js
 M frontend/src/hooks/useDocumentTitle.js
 M frontend/src/locale/de.json
 M frontend/src/locale/en.json
 M frontend/src/locale/es.json
 M frontend/src/locale/fr.json
 M frontend/src/locale/it.json
 M frontend/src/models/encounters/useFilterEncountersWithMediaAssets.js
 M frontend/src/pages/AboutUs.jsx
 M frontend/src/pages/BulkImport/BulkImportErrorSummaryBar.jsx
 M frontend/src/pages/BulkImport/BulkImportImageUpload.jsx
 M frontend/src/pages/BulkImport/BulkImportInstructionsModal.jsx
 M frontend/src/pages/BulkImport/BulkImportStore.js
 M frontend/src/pages/BulkImport/BulkImportTask.jsx
 M frontend/src/pages/BulkImport/EditableDataTable.jsx
 M frontend/src/pages/Citation.jsx
 M frontend/src/pages/EditAnnotation.jsx
 M frontend/src/pages/Encounter/AddPeople.jsx
 M frontend/src/pages/Encounter/AttributesSectionEdit.jsx
 M frontend/src/pages/Encounter/ContactInfoCard.jsx
 M frontend/src/pages/Encounter/ContactInfoModal.jsx
 M frontend/src/pages/Encounter/Encounter.jsx
 M frontend/src/pages/Encounter/IdentifySectionEdit.jsx
 M frontend/src/pages/Encounter/IdentifySectionReview.jsx
 M frontend/src/pages/Encounter/ImageCard.jsx
 M frontend/src/pages/Encounter/LocationSectionEdit.jsx
 M frontend/src/pages/Encounter/MapDisplay.jsx
 M frontend/src/pages/Encounter/MatchCriteria.jsx
 M frontend/src/pages/Encounter/MetadataSectionEdit.jsx
 M frontend/src/pages/Encounter/MoreDetails.jsx
 M frontend/src/pages/Encounter/ProjectsCard.jsx
 M frontend/src/pages/Encounter/SpotMappingCard.jsx
 M frontend/src/pages/Encounter/constants.js
 M frontend/src/pages/Encounter/pollingHelpers.js
 M frontend/src/pages/Encounter/stores/EncounterStore.js
 M frontend/src/pages/Encounter/stores/NewMatchStore.js
 M frontend/src/pages/Encounter/stores/helperFunctions.js
 M frontend/src/pages/HowToPhotograph.jsx
 M frontend/src/pages/Login.jsx
 M frontend/src/pages/ManualAnnotation.jsx
 M frontend/src/pages/MatchResultsPage/MatchResults.jsx
 M frontend/src/pages/MatchResultsPage/components/CreateNewIndividualModal.jsx
 M frontend/src/pages/MatchResultsPage/components/EmptyMatchPlaceholder.jsx
 M frontend/src/pages/MatchResultsPage/components/InspectorModal.jsx
 M frontend/src/pages/MatchResultsPage/components/InstructionsModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchConfirmedModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchCriteriaDrawer.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx
 M frontend/src/pages/MatchResultsPage/components/NewIndividualCreatedModal.jsx
 M frontend/src/pages/MatchResultsPage/constants.js
 M frontend/src/pages/MatchResultsPage/helperFunctions.js
 M frontend/src/pages/MatchResultsPage/icons/ExitFullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FilterIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/HatchMarkIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/InfoIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ToggleAnnotationIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomInIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomOutIcon.jsx
 M frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
 M frontend/src/pages/PoliciesAndData/PoliciesAndData.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ImageSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/PlaceSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounterStore.js
 M frontend/src/pages/ReportsAndManagamentPages/SpeciesSection.jsx
 M frontend/src/pages/SearchPages/EncounterSearch.jsx
 M frontend/src/pages/SearchPages/components/ExportModal.jsx
 M frontend/src/pages/SearchPages/searchResultTabs/GalleryView.jsx
 M frontend/src/pages/SearchPages/stores/EncounterFormStore.js
 M frontend/src/pages/SearchPages/stores/ImageModalStore.js
 M frontend/src/service-worker.js
 M pom.xml
 M src/main/java/org/ecocean/Annotation.java
 M src/main/java/org/ecocean/AnnotationLite.java
 M src/main/java/org/ecocean/Base.java
 M src/main/java/org/ecocean/CommonConfiguration.java
 M src/main/java/org/ecocean/EmailTemplate.java
 M src/main/java/org/ecocean/Embedding.java
 M src/main/java/org/ecocean/Encounter.java
 M src/main/java/org/ecocean/IAJsonProperties.java
 M src/main/java/org/ecocean/ImageProcessor.java
 M src/main/java/org/ecocean/LocationID.java
 M src/main/java/org/ecocean/MarkedIndividual.java
 M src/main/java/org/ecocean/NotificationMailer.java
 M src/main/java/org/ecocean/Occurrence.java
 M src/main/java/org/ecocean/OpenSearch.java
 M src/main/java/org/ecocean/RestClient.java
 M src/main/java/org/ecocean/SpotterConserveIO.java
 M src/main/java/org/ecocean/StartupWildbook.java
 M src/main/java/org/ecocean/Survey.java
 M src/main/java/org/ecocean/User.java
 M src/main/java/org/ecocean/Util.java
 M src/main/java/org/ecocean/acm/AcmUtil.java
 M src/main/java/org/ecocean/api/GenericObject.java
 M src/main/java/org/ecocean/api/Login.java
 M src/main/java/org/ecocean/api/Logout.java
 M src/main/java/org/ecocean/api/MarkedIndividualInfo.java
 M src/main/java/org/ecocean/api/SiteSettings.java
 M src/main/java/org/ecocean/api/bulk/BulkImportUtil.java
 M src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java
 M src/main/java/org/ecocean/cache/QueryCacheFactory.java
 M src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
 M src/main/java/org/ecocean/grid/AppletHeartbeatThread.java
 M src/main/java/org/ecocean/grid/EncounterLite.java
 M src/main/java/org/ecocean/grid/GridManager.java
 M src/main/java/org/ecocean/grid/MatchGraphCreationThread.java
 M src/main/java/org/ecocean/grid/MatchedPoints.java
 M src/main/java/org/ecocean/grid/SpotTriangle.java
 M src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java
 M src/main/java/org/ecocean/ia/DeferredMatchPublisher.java
 M src/main/java/org/ecocean/ia/IA.java
 M src/main/java/org/ecocean/ia/IAException.java
 M src/main/java/org/ecocean/ia/IAGatewayDeferredMatchPublisher.java
 M src/main/java/org/ecocean/ia/MLService.java
 M src/main/java/org/ecocean/ia/MatchEligibilityQuery.java
 M src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java
 M src/main/java/org/ecocean/ia/MatchResult.java
 M src/main/java/org/ecocean/ia/MatchResultProspect.java
 M src/main/java/org/ecocean/ia/MatchVisibilityGate.java
 M src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java
 M src/main/java/org/ecocean/ia/MlServiceClient.java
 M src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
 M src/main/java/org/ecocean/ia/MlServiceProcessor.java
 M src/main/java/org/ecocean/ia/Task.java
 M src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
 M src/main/java/org/ecocean/identity/IBEISIA.java
 M src/main/java/org/ecocean/identity/IdentityServiceLog.java
 M src/main/java/org/ecocean/media/AssetStore.java
 M src/main/java/org/ecocean/media/AssetStoreConfig.java
 M src/main/java/org/ecocean/media/AssetStoreFactory.java
 M src/main/java/org/ecocean/media/Feature.java
 M src/main/java/org/ecocean/media/LocalAssetStore.java
 M src/main/java/org/ecocean/media/MediaAsset.java
 M src/main/java/org/ecocean/mmutil/FileUtilities.java
 M src/main/java/org/ecocean/mmutil/MediaUtilities.java
 M src/main/java/org/ecocean/movement/Path.java
 M src/main/java/org/ecocean/opendata/OBISSeamap.java
 M src/main/java/org/ecocean/opendata/Share.java
 M src/main/java/org/ecocean/resumableupload/UploadServlet.java
 M src/main/java/org/ecocean/security/ShepherdRealm.java
 M src/main/java/org/ecocean/servlet/AnnotationEdit.java
 M src/main/java/org/ecocean/servlet/EncounterDelete.java
 M src/main/java/org/ecocean/servlet/EncounterForm.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveSpots.java
 M src/main/java/org/ecocean/servlet/GrothMatchServlet.java
 M src/main/java/org/ecocean/servlet/IAGateway.java
 M src/main/java/org/ecocean/servlet/IAGlobals.java
 M src/main/java/org/ecocean/servlet/MediaAssetModify.java
 M src/main/java/org/ecocean/servlet/ReactAppServlet.java
 M src/main/java/org/ecocean/servlet/SiteSearch.java
 M src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java
 M src/main/java/org/ecocean/servlet/UserCreate.java
 M src/main/java/org/ecocean/servlet/UserResetPassword.java
 M src/main/java/org/ecocean/servlet/WorkspaceDelete.java
 M src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java
 M src/main/java/org/ecocean/servlet/export/IndividualSearchExportCapture.java
 M src/main/java/org/ecocean/servlet/export/MarkRecaptureEncounters.java
 M src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java
 M src/main/java/org/ecocean/servlet/importer/DeleteImportTask.java
 M src/main/java/org/ecocean/servlet/importer/ImportTask.java
 M src/main/java/org/ecocean/servlet/importer/StandardImport.java
 M src/main/java/org/ecocean/shepherd/core/Shepherd.java
 M src/main/java/org/ecocean/shepherd/core/ShepherdProperties.java
 M src/main/resources/bundles/IA.json
 M src/main/resources/bundles/IA.properties
 M src/main/resources/bundles/commonConfiguration.properties
 M src/main/resources/bundles/de/commonConfigurationLabels.properties
 M src/main/resources/bundles/de/header.properties
 M src/main/resources/bundles/en/commonConfigurationLabels.properties
 M src/main/resources/bundles/en/header.properties
 M src/main/resources/bundles/en/index.properties
 M src/main/resources/bundles/en/occurrence.properties
 M src/main/resources/bundles/en/submit.properties
 M src/main/resources/bundles/en/survey.properties
 M src/main/resources/bundles/es/commonConfigurationLabels.properties
 M src/main/resources/bundles/es/header.properties
 M src/main/resources/bundles/es/index.properties
 M src/main/resources/bundles/fr/commonConfigurationLabels.properties
 M src/main/resources/bundles/fr/encounter.properties
 M src/main/resources/bundles/fr/encounterSearch.properties
 M src/main/resources/bundles/fr/header.properties
 M src/main/resources/bundles/fr/index.properties
 M src/main/resources/bundles/fr/submit.properties
 M src/main/resources/bundles/haplotypeColorCodes.properties
 M src/main/resources/bundles/it/commonConfigurationLabels.properties
 M src/main/resources/bundles/it/header.properties
 M src/main/resources/bundles/it/index.properties
 M src/main/resources/bundles/locationID.json
 M src/main/resources/bundles/locationIDGPS.properties
 M src/main/resources/bundles/locationID_indocet.json
 M src/main/resources/bundles/newIndividualNumbers.properties
 M src/main/resources/bundles/opendata.properties
 M src/main/resources/log4j2.xml
 M src/main/resources/org/ecocean/ia/package.jdo
 M src/main/resources/org/ecocean/media/package.jdo
 M src/main/resources/org/ecocean/package.jdo
 M src/main/resources/servletResponseTemplate.htm
 M src/main/webapp/WEB-INF/web.xml
 M src/main/webapp/appadmin/catchUpEmbeddings.jsp
 M src/main/webapp/appadmin/importEmbeddings.jsp
 M src/main/webapp/appadmin/testEmbeddingSearch.jsp
 M src/main/webapp/appadmin/users.jsp
 M src/main/webapp/contactus.jsp
 M src/main/webapp/css/imageEnhancer.css
 M src/main/webapp/cust/mantamatcher/img/wildbook_logo_white.svg
 M src/main/webapp/cust/mantamatcher/styles/_bootstrap-overrides.less
 M src/main/webapp/cust/mantamatcher/styles/_custom.less
 M src/main/webapp/cust/mantamatcher/styles/_wildbook.less
 M src/main/webapp/datacleaning/individuals.jsp
 M src/main/webapp/encounters/biologicalSamples.jsp
 M src/main/webapp/encounters/encounterCropTool.jsp
 M src/main/webapp/encounters/encounterSpotTool.jsp
 M src/main/webapp/encounters/manualAnnotation.jsp
 M src/main/webapp/encounters/scanEndApplet.jsp
 M src/main/webapp/encounters/spotMatchingAlgorithm.jsp
 M src/main/webapp/header.jsp
 M src/main/webapp/iaResults.jsp
 M src/main/webapp/import.jsp
 M src/main/webapp/import/photos.jsp
 M src/main/webapp/import/reviewDirectory.jsp
 M src/main/webapp/import/spreadsheet.jsp
 M src/main/webapp/index.jsp
 M src/main/webapp/javascript/ia.IBEIS.js
 M src/main/webapp/javascript/relationshipDiagrams/graphAbstract.js
 M src/main/webapp/listImages.jsp
 M src/main/webapp/logout.jsp
 M src/main/webapp/obrowse.jsp
 M src/main/webapp/occurrence.jsp
 M src/main/webapp/pictureBook.jsp
 M src/main/webapp/privacyPolicy.jsp
 M src/main/webapp/projects/project.jsp
 M src/main/webapp/submit.jsp
 M src/main/webapp/surveys/survey.jsp
 M src/main/webapp/surveys/surveyMapEmbed.jsp
 M src/main/webapp/termsOfUse.jsp
 M src/main/webapp/welcome.jsp
 M src/test/java/org/ecocean/AnnotationMiewIDScoreTest.java
 M src/test/java/org/ecocean/AnnotationTest.java
 M src/test/java/org/ecocean/EmbeddingTest.java
 M src/test/java/org/ecocean/IAJsonPropertiesTest.java
 M src/test/java/org/ecocean/MatchResultTest.java
 M src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java
 M src/test/java/org/ecocean/OpenSearchVisibilityTest.java
 M src/test/java/org/ecocean/UtilTest.java
 M src/test/java/org/ecocean/api/EncounterApiTest.java
 M src/test/java/org/ecocean/api/EncounterExportImagesTest.java
 M src/test/java/org/ecocean/api/SettingApiTest.java
 M src/test/java/org/ecocean/api/TaskMatchResults.java
 M src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java
 M src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java
 M src/test/java/org/ecocean/grid/GrothOptimizationTest.java
 M src/test/java/org/ecocean/grid/GrothParameterSweepTest.java
 M src/test/java/org/ecocean/grid/MatchGraphCacheTest.java
 M src/test/java/org/ecocean/ia/IdentificationTest.java
 M src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java
 M src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java
 M src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java
 M src/test/java/org/ecocean/ia/MatchResultIndivProspectsTest.java
 M src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java
 M src/test/java/org/ecocean/ia/MlServiceClientTest.java
 M src/test/java/org/ecocean/ia/MlServiceJobOutcomeTest.java
 M src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java
 M src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java
 M src/test/java/org/ecocean/ia/MlServiceProcessorTest.java
 M src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java
 M src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java
 M src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java
 M src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java
 M src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java
 M src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java
 M src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java
 M src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java
 M src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java
 M src/test/java/org/ecocean/servlet/ReactAppServletTest.java
 M src/test/java/org/ecocean/servlet/importer/ImportTaskCountsTest.java
 M src/test/resources/log4j2-test.xml
?? IA.json
?? docs/plans/2026-05-19-vector-legacy-parity-rca.md
?? docs/plans/score_parity_deployment.py
migrate-ml-service-v2
 M wbia_miew_id/datasets/default_dataset.py
 M wbia_miew_id/engine/train_fn.py
 M wbia_miew_id/evaluate.py
 M wbia_miew_id/helpers/config.py
 M wbia_miew_id/losses/__init__.py
 M wbia_miew_id/models/heads.py
 M wbia_miew_id/models/model_helpers.py
 M wbia_miew_id/train.py
?? .claude/
?? docs/plans/2026-05-19-bbox-resilience-design.md

exec
/bin/bash -lc "nl -ba /mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py | sed -n '450,535p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/wbia-plugin-miew-id/wbia_miew_id/metrics/distance.py | sed -n '1,80p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "rg -n \"miew_id_predict|miew_id_predict_light|pairx_rerank|dannot_uuid_list|annot_score_list|score_list|get_match_results|cm_dict\" /mnt/c/wbia-plugin-miew-id /mnt/c/Wildbook-clean2/src/main/java/org/ecocean -g '"'!*.class'"'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 214ms:
     1	import numpy as np
     2	from scipy.spatial.distance import cdist
     3	
     4	import torch
     5	import torch.nn.functional as F
     6	
     7	def remove_diagonal(A):
     8	    print("A.shape", A.shape)
     9	    print(A.size(0), A.size(1))
    10	    if A.size(0) != A.size(1):
    11	        raise ValueError("Input must be a square matrix")
    12	    
    13	    mask = ~torch.eye(A.size(0), dtype=torch.bool)
    14	    return A[mask].reshape(A.size(0), -1)
    15	
    16	def euclidean_squared_distance(input1, input2):
    17	    """Computes euclidean squared distance./
    18	
    19	    Args:
    20	        input1 (torch.Tensor): 2-D feature matrix.
    21	        input2 (torch.Tensor): 2-D feature matrix.
    22	
    23	    Returns:
    24	        torch.Tensor: distance matrix.
    25	    """
    26	    m, n = input1.size(0), input2.size(0)
    27	    mat1 = torch.pow(input1, 2).sum(dim=1, keepdim=True).expand(m, n)
    28	    mat2 = torch.pow(input2, 2).sum(dim=1, keepdim=True).expand(n, m).t()
    29	    distmat = mat1 + mat2
    30	    distmat.addmm_(input1, input2.t(), beta=1, alpha=-2)
    31	    return distmat
    32	
    33	
    34	def cosine_distance(input1, input2):
    35	    """Computes cosine distance.
    36	
    37	    Args:
    38	        input1 (torch.Tensor): 2-D feature matrix.
    39	        input2 (torch.Tensor): 2-D feature matrix.
    40	
    41	    Returns:
    42	        torch.Tensor: distance matrix.
    43	    """
    44	    input1_normed = F.normalize(input1, p=2, dim=1)
    45	    input2_normed = F.normalize(input2, p=2, dim=1)
    46	    distmat = 1 - torch.mm(input1_normed, input2_normed.t())
    47	    return distmat
    48	
    49	def compute_distance_matrix(input1, input2, metric='euclidean'):
    50	    """A wrapper function for computing distance matrix.
    51	
    52	    Args:
    53	        input1 (torch.Tensor): 2-D feature matrix.
    54	        input2 (torch.Tensor): 2-D feature matrix.
    55	        metric (str, optional): "euclidean" or "cosine".
    56	            Default is "euclidean".
    57	
    58	    Returns:
    59	        torch.Tensor: distance matrix.
    60	
    61	    """
    62	    if not isinstance(input1, torch.Tensor):
    63	        input1 = torch.from_numpy(input1)
    64	    if not isinstance(input2, torch.Tensor):
    65	        input2 = torch.from_numpy(input2)
    66	    # check input
    67	    assert input1.dim() == 2, 'Expected 2-D tensor, but got {}-D'.format(input1.dim())
    68	    assert input2.dim() == 2, 'Expected 2-D tensor, but got {}-D'.format(input2.dim())
    69	    assert input1.size(1) == input2.size(1)
    70	
    71	    if metric == 'euclidean':
    72	        distmat = euclidean_squared_distance(input1, input2)
    73	    elif metric == 'cosine':
    74	        distmat = cosine_distance(input1, input2)
    75	    else:
    76	        raise ValueError(
    77	            'Unknown distance metric: {}. '
    78	            'Please choose either "euclidean" or "cosine"'.format(metric)
    79	        )
    80	

 succeeded in 222ms:
   450	    #     # This list has to be in the format of [query_aid, db_aid]
   451	    #     aid_list = np.concatenate(([cm.qaid],  aids))
   452	    #     test_loader, test_dataset = _load_data(ibs, aid_list, config)
   453	
   454	    #     batch_images = draw_batch(config, test_loader,  model, images_dir = '', method='gradcam_plus_plus', eigen_smooth=False, show=False)
   455	
   456	    #     return batch_images
   457	    
   458	    def postprocess_execute(request, table, parent_rowids, rowids, result_list):
   459	        qaid_list, daid_list = list(zip(*parent_rowids))
   460	        score_list = ut.take_column(result_list, 0)
   461	        depc = request.depc
   462	        config = request.config
   463	        cm_list = list(get_match_results(depc, qaid_list, daid_list, score_list, config))
   464	
   465	        depc = request.depc
   466	        ibs = depc.controller
   467	        for cm in cm_list:
   468	            species = ibs.get_annot_species_texts(cm.qaid)
   469	            _, _, (model_url, config_url) = read_config_and_load_model(species)
   470	            cm.model_url = model_url
   471	            cm.config_url = config_url
   472	
   473	        table.delete_rows(rowids)
   474	        return cm_list
   475	
   476	    def execute(request, *args, **kwargs):
   477	        # kwargs['use_cache'] = False
   478	        result_list = super(MiewIdRequest, request).execute(*args, **kwargs)
   479	        qaids = kwargs.pop('qaids', None)
   480	        if qaids is not None:
   481	            result_list = [result for result in result_list if result.qaid in qaids]
   482	        return result_list
   483	
   484	
   485	@register_preproc_annot(
   486	    tablename='MiewId',
   487	    parents=[ANNOTATION_TABLE, ANNOTATION_TABLE],
   488	    colnames=['score'],
   489	    coltypes=[float],
   490	    configclass=MiewIdConfig,
   491	    requestclass=MiewIdRequest,
   492	    fname='miew_id',
   493	    rm_extern_on_delete=True,
   494	    chunksize=None,
   495	)
   496	def wbia_plugin_miew_id(depc, qaid_list, daid_list, config):
   497	    ibs = depc.controller
   498	
   499	    qaids = list(set(qaid_list))
   500	    daids = list(set(daid_list))
   501	
   502	    use_knn = config.get('use_knn', True)
   503	
   504	    qaid_score_dict = {}
   505	    for qaid in tqdm.tqdm(qaids):
   506	        if use_knn:
   507	                miew_id_dists = ibs.miew_id_predict_light(
   508	                    qaid,
   509	                    daids,
   510	                    config['config_path'],
   511	                )
   512	                miew_id_scores = distance_dicts_to_score_dicts(miew_id_dists)
   513	
   514	                # aid_score_list = aid_scores_from_name_scores(ibs, miew_id_name_scores, daids)
   515	                aid_score_list = aid_scores_from_score_dict(miew_id_scores, daids)
   516	                aid_score_dict = dict(zip(daids, aid_score_list))
   517	
   518	                qaid_score_dict[qaid] = aid_score_dict
   519	        else:
   520	            miew_id_annot_distances = ibs.miew_id_predict_light_distance(
   521	                qaid,
   522	                daids,
   523	                config['config_path'],
   524	            )
   525	            qaid_score_dict[qaid] = {}
   526	            for daid, miew_id_annot_distance in zip(daids, miew_id_annot_distances):
   527	                qaid_score_dict[qaid][daid] = distance_to_score(miew_id_annot_distance)
   528	
   529	        # Optional PairX re-ranking (engaged via config flag from Wildbook)
   530	        if config.get('pairx_rerank', False):
   531	            qaid_score_dict[qaid] = _pairx_rerank(
   532	                ibs, qaid, daids, qaid_score_dict[qaid], config
   533	            )
   534	
   535	    for qaid, daid in zip(qaid_list, daid_list):

 succeeded in 888ms:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:103:_pairx_logger = logging.getLogger('wbia_miew_id.pairx_rerank')
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:133:def _pairx_rerank(ibs, qaid, daids, score_dict, config):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:141:        pairx_rerank_score, normalize_pairx_score,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:192:            raw_pairx = pairx_rerank_score(
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:342:            ut.ParamInfo('pairx_rerank', False),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:351:def get_match_results(depc, qaid_list, daid_list, score_list, config):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:354:    # score_list = request.score_list
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:360:    grouped_scores = ut.apply_grouping(score_list, groupxs)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:460:        score_list = ut.take_column(result_list, 0)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:463:        cm_list = list(get_match_results(depc, qaid_list, daid_list, score_list, config))
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:507:                miew_id_dists = ibs.miew_id_predict_light(
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:514:                # aid_score_list = aid_scores_from_name_scores(ibs, miew_id_name_scores, daids)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:515:                aid_score_list = aid_scores_from_score_dict(miew_id_scores, daids)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:516:                aid_score_dict = dict(zip(daids, aid_score_list))
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:520:            miew_id_annot_distances = ibs.miew_id_predict_light_distance(
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:530:        if config.get('pairx_rerank', False):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:531:            qaid_score_dict[qaid] = _pairx_rerank(
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:698:def miew_id_predict_light(ibs, qaid, daid_list, config=None):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:710:def miew_id_predict_light_distance(ibs, qaid, daid_list, config=None):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:725:    ans = ibs.miew_id_predict_light(qaid, daids)
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:11:when `pairx_rerank: true` is sent in the request config.
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:92:1. **`pairx_rerank_score(device, img_0, img_1, model, layer_key)`**
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:176:            ut.ParamInfo('pairx_rerank', False),        # master switch
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:189:    "pairx_rerank": true,
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:206:if config.get('pairx_rerank', False):
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:207:    qaid_score_dict[qaid] = _pairx_rerank(
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:212:**New function: `_pairx_rerank(ibs, qaid, daids, score_dict, config)`**
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:215:def _pairx_rerank(ibs, qaid, daids, score_dict, config):
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:231:        pairx_rerank_score, normalize_pairx_score,
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:248:            raw_pairx = pairx_rerank_score(
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:303:Add a guard in `_pairx_rerank` before entering the scoring loop:
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:324:**New file: `tests/test_pairx_rerank.py`**
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:326:1. Unit test `pairx_rerank_score()` with synthetic feature maps
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:330:5. Unit test `pairx_rerank_score` returns `None` on exception (no crash)
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:331:6. Integration test: run MiewID with `pairx_rerank=False` vs `True`, verify
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:341:| `wbia_miew_id/_plugin.py` | Add config params, model cache, `_pairx_rerank`, `_prepare_image` |
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:343:| `tests/test_pairx_rerank.py` | New: unit + integration tests |
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/scoring.py:36:def pairx_rerank_score(device, img_0, img_1, model, layer_key,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:760:                    JSONArray s = r.getJSONObject(i).getJSONArray("score_list");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:1875:        resolveNames(needNameResolution, j.optJSONObject("cm_dict"), myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:3666:// cmDict has a structure like:  { acmId1: { dname_list: [], dannot_uuid_list: [] } } .... um, i think?
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:3667:        // resolveNames(anns, j.optJSONObject("cm_dict"), myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:798:            if ((res != null) && (res.optJSONObject("cm_dict") != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:136:        if (res.optJSONObject("cm_dict") == null)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:137:            throw new IOException("no cm_dict found in " + res);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:139:        JSONObject results = res.getJSONObject("cm_dict").optJSONObject(queryAnnotId);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:145:        annot_score_list <=> dannot_uuid_list
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:146:        score_list is for indiv scores but on dannot_uuid_list (same length)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:147:        name_score_list <=> unique_name_uuid_list ???
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:149:        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:150:            results.optJSONArray("annot_score_list"), results.optJSONArray("dannot_extern_list"),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:152:        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:153:            results.optJSONArray("score_list"), results.optJSONArray("dannot_extern_list"),

codex
The main candidate-set code is shared, but only when the task parameters reach it. One concrete divergence already shows up: `IAGateway` only precomputes a target set when `limitTargetSize > -1`, and that precompute uses `ann.getMatchingSet(myShepherd)` with no task params. The normal legacy path relies on `beginIdentifyAnnotations` to rebuild with task params.
exec
/bin/bash -lc "nl -ba /mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py | sed -n '90,215p;330,360p;690,755p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "rg -n \"api_endpoint|findMatchProspects|extractEmbeddings|miewid|miewid-msv4.1|ibeis.identification|query_config_dict|pairx_rerank|use_knn\" /mnt/c/Wildbook-clean2/src/main/java/org/ecocean /mnt/c/ml-service /mnt/c/wbia-plugin-miew-id -g '"'!*.class'"'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "rg -n \"KNN_K_DISTANCE_VALUE|knn.algo_param.ef_search|max_result_window|queryPit\\(|search_after|pit\" /mnt/c/Wildbook-clean2/src/main/java/org/ecocean /mnt/c/Wildbook-clean2/src/main/java -g '*.java'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 292ms:
    90	GLOBAL_EMBEDDING_CACHE = {}
    91	
    92	# ---------------------------------------------------------------------------
    93	# PairX re-ranking: model cache + thread safety
    94	# ---------------------------------------------------------------------------
    95	# PairX modifies model state (hooks, zennit canonization, backward passes).
    96	# A per-species lock serializes PairX scoring while allowing concurrent
    97	# MiewID embedding computation (which uses torch.no_grad and is read-only).
    98	
    99	_PAIRX_MODEL_CACHE = {}     # species -> (model, config, device)
   100	_PAIRX_MODEL_LOCKS = {}     # species -> threading.Lock
   101	_PAIRX_CACHE_LOCK = threading.Lock()
   102	
   103	_pairx_logger = logging.getLogger('wbia_miew_id.pairx_rerank')
   104	
   105	
   106	def _get_pairx_model(species):
   107	    """Get or load a model for PairX re-ranking.  Cached per species."""
   108	    with _PAIRX_CACHE_LOCK:
   109	        if species not in _PAIRX_MODEL_CACHE:
   110	            model, mconfig, _ = read_config_and_load_model(species)
   111	            device = next(model.parameters()).device
   112	            model.eval()
   113	            # MiewIdNet has no .device property; set it for PairX compat
   114	            model.device = device
   115	            _PAIRX_MODEL_CACHE[species] = (model, mconfig, device)
   116	            _PAIRX_MODEL_LOCKS[species] = threading.Lock()
   117	    return _PAIRX_MODEL_CACHE[species], _PAIRX_MODEL_LOCKS[species]
   118	
   119	
   120	def _prepare_pairx_image(ibs, aid, config, device):
   121	    """Load and transform one annotation into a tensor for PairX."""
   122	    test_loader, _ = _load_data(ibs, [aid], config, multithread=False,
   123	                                batch_size=1)
   124	    batch = next(iter(test_loader))
   125	    img_tensor = batch[0]  # (1, C, H, W)
   126	    if len(img_tensor.shape) == 3:
   127	        img_tensor = img_tensor.unsqueeze(0)
   128	    img_tensor = img_tensor.to(device).float()
   129	    img_tensor.requires_grad_(True)
   130	    return img_tensor
   131	
   132	
   133	def _pairx_rerank(ibs, qaid, daids, score_dict, config):
   134	    """Re-rank top-k candidates using PairX spatial correspondence metrics.
   135	
   136	    Thread-safe: acquires a per-species lock around all PairX scoring so
   137	    that hook registration, zennit canonization, and backward passes on
   138	    the shared model don't collide across Gunicorn threads.
   139	    """
   140	    from wbia_miew_id.visualization.pairx.scoring import (
   141	        pairx_rerank_score, normalize_pairx_score,
   142	    )
   143	    from wbia_miew_id.visualization.pairx.core import choose_canonizer
   144	
   145	    shortlist_k = config.get('pairx_shortlist_k', 20)
   146	    alpha = config.get('pairx_alpha', 0.8)
   147	    layer_key = config.get('pairx_layer_key', 'backbone.blocks.5')
   148	    sigmoid_k = config.get('pairx_sigmoid_k', 10.0)
   149	    sigmoid_x0 = config.get('pairx_sigmoid_x0', 0.5)
   150	
   151	    _pairx_logger.info(
   152	        'PairX re-ranking qaid=%s: shortlist_k=%d, alpha=%.2f, layer=%s',
   153	        qaid, shortlist_k, alpha, layer_key,
   154	    )
   155	
   156	    # Sort by MiewID score descending, take top-k
   157	    sorted_daids = sorted(
   158	        daids, key=lambda d: score_dict.get(d, 0), reverse=True
   159	    )
   160	    shortlist = sorted_daids[:shortlist_k]
   161	
   162	    species = ibs.get_annot_species_texts(qaid)
   163	    (model, mconfig, device), model_lock = _get_pairx_model(species)
   164	
   165	    # Fail fast if backbone isn't supported by PairX's canonizer
   166	    try:
   167	        choose_canonizer(model)
   168	    except Exception:
   169	        _pairx_logger.warning(
   170	            'PairX re-ranking skipped: unsupported backbone %s',
   171	            type(model).__name__,
   172	        )
   173	        return score_dict
   174	
   175	    # Prepare image tensors outside the lock (I/O + DB reads)
   176	    query_img = _prepare_pairx_image(ibs, qaid, mconfig, device)
   177	    db_imgs = {daid: _prepare_pairx_image(ibs, daid, mconfig, device)
   178	               for daid in shortlist}
   179	
   180	    # Hold the lock only for model forward/backward passes
   181	    with model_lock:
   182	        for daid in shortlist:
   183	            db_img = db_imgs[daid]
   184	
   185	            # Zero gradients to prevent accumulation across pairs
   186	            model.zero_grad()
   187	            if query_img.grad is not None:
   188	                query_img.grad = None
   189	            if db_img.grad is not None:
   190	                db_img.grad = None
   191	
   192	            raw_pairx = pairx_rerank_score(
   193	                device, query_img, db_img, model, layer_key
   194	            )
   195	
   196	            if raw_pairx is None:
   197	                # PairX failed for this pair; keep MiewID score (scaled)
   198	                score_dict[daid] = alpha * score_dict[daid]
   199	                continue
   200	
   201	            pairx_norm = normalize_pairx_score(raw_pairx, sigmoid_k, sigmoid_x0)
   202	            miewid_score = score_dict[daid]
   203	            score_dict[daid] = alpha * miewid_score + (1 - alpha) * pairx_norm
   204	
   205	    _pairx_logger.info('PairX re-ranking complete for qaid=%s', qaid)
   206	
   207	    # Scale non-shortlisted scores (same pattern as Hybrid plugin)
   208	    for daid in sorted_daids[shortlist_k:]:
   209	        score_dict[daid] = alpha * score_dict.get(daid, 0)
   210	
   211	    return score_dict
   212	
   213	
   214	@register_ibs_method
   215	def miew_id_embedding(ibs, aid_list, config=None, use_depc=True):
   330	
   331	            embeddings.append(output.detach().cpu().numpy())
   332	
   333	    embeddings = np.concatenate(embeddings)
   334	    return embeddings
   335	
   336	
   337	class MiewIdConfig(dt.Config):  # NOQA
   338	    def get_param_info_list(self):
   339	        return [
   340	            ut.ParamInfo('config_path', None),
   341	            ut.ParamInfo('use_knn', True, hideif=True),
   342	            ut.ParamInfo('pairx_rerank', False),
   343	            ut.ParamInfo('pairx_shortlist_k', 20),
   344	            ut.ParamInfo('pairx_alpha', 0.8),
   345	            ut.ParamInfo('pairx_layer_key', 'backbone.blocks.5'),
   346	            ut.ParamInfo('pairx_sigmoid_k', 10.0),
   347	            ut.ParamInfo('pairx_sigmoid_x0', 0.5),
   348	        ]
   349	
   350	
   351	def get_match_results(depc, qaid_list, daid_list, score_list, config):
   352	    """ converts table results into format for ipython notebook """
   353	    # qaid_list, daid_list = request.get_parent_rowids()
   354	    # score_list = request.score_list
   355	    # config = request.config
   356	
   357	    unique_qaids, groupxs = ut.group_indices(qaid_list)
   358	    # grouped_qaids_list = ut.apply_grouping(qaid_list, groupxs)
   359	    grouped_daids = ut.apply_grouping(daid_list, groupxs)
   360	    grouped_scores = ut.apply_grouping(score_list, groupxs)
   690	            nid_list=nid_list,
   691	            viewpoint_list=viewpoint_list,
   692	        )
   693	
   694	        return test_ibs
   695	
   696	
   697	@register_ibs_method
   698	def miew_id_predict_light(ibs, qaid, daid_list, config=None):
   699	    db_embs = np.array(ibs.miew_id_embedding(daid_list, config))
   700	    query_emb = np.array(ibs.miew_id_embedding([qaid], config))
   701	
   702	    # db_labels = np.array(ibs.get_annot_name_texts(daid_list, distinguish_unknowns=True))
   703	    db_labels = np.array(daid_list)
   704	
   705	    ans = pred_light(query_emb, db_embs, db_labels)
   706	    return ans
   707	
   708	
   709	@register_ibs_method
   710	def miew_id_predict_light_distance(ibs, qaid, daid_list, config=None):
   711	    assert len(daid_list) == len(set(daid_list))
   712	    db_embs = np.array(ibs.miew_id_embedding(daid_list, config))
   713	    query_emb = np.array(ibs.miew_id_embedding([qaid], config))
   714	
   715	    input1 = torch.Tensor(query_emb)
   716	    input2 = torch.Tensor(db_embs)
   717	    distmat = compute_distance_matrix(input1, input2, metric='cosine')
   718	    distances = np.array(distmat[0])
   719	    return distances
   720	
   721	
   722	def _miew_id_accuracy(ibs, qaid, daid_list):
   723	    daids = daid_list.copy()
   724	    daids.remove(qaid)
   725	    ans = ibs.miew_id_predict_light(qaid, daids)
   726	    ans_names = [row['label'] for row in ans]
   727	    ground_truth = ibs.get_annot_name_texts(qaid)
   728	    try:
   729	        rank = ans_names.index(ground_truth) + 1
   730	    except ValueError:
   731	        rank = -1
   732	    print('rank %s' % rank)
   733	    return rank
   734	
   735	
   736	def miew_id_mass_accuracy(ibs, aid_list, daid_list=None):
   737	    if daid_list is None:
   738	        daid_list = aid_list
   739	    ranks = [_miew_id_accuracy(ibs, aid, daid_list) for aid in aid_list]
   740	    return ranks
   741	
   742	
   743	def accuracy_at_k(ibs, ranks, max_rank=10):
   744	    counts = [ranks.count(i) for i in range(1, max_rank + 1)]
   745	    percent_counts = [count / len(ranks) for count in counts]
   746	    cumulative_percent = [
   747	        sum(percent_counts[:i]) for i in range(1, len(percent_counts) + 1)
   748	    ]
   749	    return cumulative_percent
   750	
   751	
   752	def subset_with_resights(ibs, aid_list, n=3):
   753	    names = ibs.get_annot_name_rowids(aid_list)
   754	    name_counts = _count_dict(names)
   755	    good_annots = [aid for aid, name in zip(aid_list, names) if name_counts[name] >= n]

 succeeded in 1563ms:
/mnt/c/wbia-plugin-miew-id/README.md:23:Model specs and dataset overview can be found at the [model card page for the Multispecies-v2 model](https://huggingface.co/conservationxlabs/miewid-msv2) and the [Multispecies-v3 model](https://huggingface.co/conservationxlabs/miewid-msv3)
/mnt/c/wbia-plugin-miew-id/README.md:34:model_tag = f"conservationxlabs/miewid-msv2"
/mnt/c/wbia-plugin-miew-id/README.md:69:    anno_path='beluga_example_miewid/benchmark_splits/test.csv',
/mnt/c/wbia-plugin-miew-id/README.md:103:python train.py --config=examples/beluga_example_miewid/benchmark_model/miew_id.msv2_all.yaml
/mnt/c/wbia-plugin-miew-id/README.md:109:python evaluate.py --config=examples/beluga_example_miewid/benchmark_model/miew_id.msv2_all.yaml
/mnt/c/wbia-plugin-miew-id/README.md:124:[Recommended] The CSV beluga data can be downlaoded from [here](https://cthulhu.dyn.wildme.io/public/datasets/beluga_example_miewid.tar.gz).
/mnt/c/ml-service/test_stress.py:104:        "model_id": "miewid-msv4.1",
/mnt/c/ml-service/test_stress.py:145:        "model_id": "miewid-msv4.1",
/mnt/c/ml-service/test_stress.py:184:            "model_id": "miewid-msv4.1",
/mnt/c/ml-service/test_stress.py:218:        "model_id": "miewid-msv4.1",
/mnt/c/ml-service/test_extract_endpoint.py:20:        "model_id": "miewid-msv4.1",  # Adjust this based on your model configuration
/mnt/c/ml-service/README.md:14:| `miewid` | MiewID transformer | Embedding extraction for re-identification |
/mnt/c/ml-service/README.md:121:    "model_id": "miewid-msv4.1",
/mnt/c/ml-service/README.md:131:    "model_id": "miewid-msv4.1",
/mnt/c/ml-service/README.md:156:    "model_id": "miewid-msv3",
/mnt/c/ml-service/README.md:184:    "extract_model_id": "miewid-msv4.1",
/mnt/c/ml-service/README.md:227:        "extract_model_id": "miewid-msv4.1"
/mnt/c/ml-service/README.md:494:            "model_id": "miewid-msv4.1",
/mnt/c/ml-service/README.md:495:            "model_type": "miewid",
/mnt/c/ml-service/README.md:539:**MiewID** (`miewid`):
/mnt/c/ml-service/test_pipeline_endpoint.py:22:        "extract_model_id": "miewid-msv4.1",  # Adjust based on your extract model
/mnt/c/ml-service/test_checkpoint_loading.py:71:        "model_id": "miewid-msv3_checkpoint",  # Use checkpoint model if available
/mnt/c/ml-service/test_optional_bbox.py:19:        "model_id": "miewid-msv4.1",
/mnt/c/ml-service/test_optional_bbox.py:49:        "model_id": "miewid-msv4.1",
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:11:when `pairx_rerank: true` is sent in the request config.
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:43:         - fused = alpha * miewid_score + (1 - alpha) * pairx_normalized
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:44:         - Non-shortlisted candidates keep miewid_score * alpha
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:92:1. **`pairx_rerank_score(device, img_0, img_1, model, layer_key)`**
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:174:            ut.ParamInfo('use_knn', True, hideif=True),
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:176:            ut.ParamInfo('pairx_rerank', False),        # master switch
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:185:These can be passed from Wildbook in the `query_config_dict`:
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:189:    "pairx_rerank": true,
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:206:if config.get('pairx_rerank', False):
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:207:    qaid_score_dict[qaid] = _pairx_rerank(
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:212:**New function: `_pairx_rerank(ibs, qaid, daids, score_dict, config)`**
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:215:def _pairx_rerank(ibs, qaid, daids, score_dict, config):
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:231:        pairx_rerank_score, normalize_pairx_score,
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:248:            raw_pairx = pairx_rerank_score(
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:258:            miewid_score = score_dict[daid]
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:259:            score_dict[daid] = alpha * miewid_score + (1 - alpha) * pairx_norm
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:303:Add a guard in `_pairx_rerank` before entering the scoring loop:
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:324:**New file: `tests/test_pairx_rerank.py`**
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:326:1. Unit test `pairx_rerank_score()` with synthetic feature maps
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:330:5. Unit test `pairx_rerank_score` returns `None` on exception (no crash)
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:331:6. Integration test: run MiewID with `pairx_rerank=False` vs `True`, verify
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:341:| `wbia_miew_id/_plugin.py` | Add config params, model cache, `_pairx_rerank`, `_prepare_image` |
/mnt/c/wbia-plugin-miew-id/docs/plans/pairx-reranking.md:343:| `tests/test_pairx_rerank.py` | New: unit + integration tests |
/mnt/c/ml-service/docs/plans/2026-05-19-bbox-resilience-design.md:168:3. **Model preprocessing** (`app/models/efficientnet.py`, `densenet_orientation.py`, `miewid.py`): defensive — calls `clamp_bbox` again before slicing, in case a non-router caller (e.g., `ModelHandler.predict` at `model_handler.py:108` or `/pipeline/`'s direct model calls at `pipeline_router.py:227,236`) bypasses the router clamp. When the model layer detects degenerate, it raises a `ValueError("bbox does not intersect image")`; routers translate that to 422.
/mnt/c/ml-service/docs/plans/2026-05-19-bbox-resilience-design.md:249:| `app/models/miewid.py` | Replace `crop_and_rotate_image` body (`:146-...`) with `safe_crop` so MiewID inherits the policy. |
/mnt/c/ml-service/docs/plans/2026-05-19-bbox-resilience-design.md:321:3. **PR 3 — model preprocessing.** Plumb `safe_crop` through `efficientnet.py`, `densenet_orientation.py`, `miewid.py`. Remove the two inconsistent full-image fallbacks. Update echoed bbox to effective bbox.
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/evaluate.py:33:                 model_params=None, checkpoint_path=None, model=None, visualize=False, visualization_output_dir='miewid_visualizations'):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/evaluate.py:141:    def visualize_results(test_outputs, df_test, test_dataset, model, device, k=5, valid_batch_size=2, output_dir='miewid_visualizations'):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:53:      with open('/v_config/miewid/model_config.json', 'r') as config_file:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:56:      print("Error: File not found. Please check the file path /v_config/miewid/model_config.json ")
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:58:      print("Error: Invalid JSON format. Please check the file content /v_config/miewid/model_config.json.")
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:67:      with open('/v_config/miewid/model_bin_config.json', 'r') as config_file:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:70:      print("Error: File not found. Please check the file path /v_config/miewid/model_bin_config.json ")
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:72:      print("Error: Invalid JSON format. Please check the file content /v_config/miewid/model_bin_config.json.")
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:103:_pairx_logger = logging.getLogger('wbia_miew_id.pairx_rerank')
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:133:def _pairx_rerank(ibs, qaid, daids, score_dict, config):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:141:        pairx_rerank_score, normalize_pairx_score,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:192:            raw_pairx = pairx_rerank_score(
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:202:            miewid_score = score_dict[daid]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:203:            score_dict[daid] = alpha * miewid_score + (1 - alpha) * pairx_norm
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:341:            ut.ParamInfo('use_knn', True, hideif=True),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:342:            ut.ParamInfo('pairx_rerank', False),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:502:    use_knn = config.get('use_knn', True)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:506:        if use_knn:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:530:        if config.get('pairx_rerank', False):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:531:            qaid_score_dict[qaid] = _pairx_rerank(
/mnt/c/ml-service/tests/test_pipeline_router_classifier.py:29:    from app.models.miewid import MiewidModel
/mnt/c/ml-service/tests/test_pipeline_router_classifier.py:81:    from app.models.miewid import MiewidModel
/mnt/c/ml-service/tests/test_pipeline_router_classifier.py:122:    from app.models.miewid import MiewidModel
/mnt/c/ml-service/tests/test_pipeline_router_classifier.py:166:    from app.models.miewid import MiewidModel
/mnt/c/ml-service/docs/superpowers/plans/2026-05-16-densenet-classifier.md:974:    from app.models.miewid import MiewidModel
/mnt/c/ml-service/docs/superpowers/plans/2026-05-16-densenet-classifier.md:1025:    from app.models.miewid import MiewidModel
/mnt/c/ml-service/docs/superpowers/plans/2026-05-16-densenet-classifier.md:1058:    from app.models.miewid import MiewidModel
/mnt/c/ml-service/app/test_main.py:19:                "model_id": "miewid-msv4.1",
/mnt/c/ml-service/app/test_main.py:42:                "model_id": "miewid-msv4.1",
/mnt/c/ml-service/app/test_main.py:59:               "theta2": [0.0], "model_id": "miewid-msv4.1", "crop_bbox": False, 
/mnt/c/ml-service/app/test_main.py:73:               "model_id": "miewid-msv4.1", "crop_bbox": False,
/mnt/c/ml-service/app/test_main.py:87:               "model_id": "miewid-msv4.1", "crop_bbox": False,
/mnt/c/ml-service/app/test_main.py:101:               "model_id": "miewid-msv4.1", "crop_bbox": False,
/mnt/c/ml-service/app/test_main.py:115:               "theta2": [-10.0], "model_id": "miewid-msv4.1", "crop_bbox": False, 
/mnt/c/ml-service/app/test_main.py:157:               "model_id": "miewid-msv4.1", "crop_bbox": False,
/mnt/c/ml-service/app/test_main.py:171:               "model_id": "miewid-msv4.1", "crop_bbox": False,
/mnt/c/ml-service/app/test_main.py:183:               "model_id": "miewid-msv4.1", "crop_bbox": False,
/mnt/c/ml-service/app/test_main.py:197:               "model_id": "miewid-msv4.1", "crop_bbox": False,
/mnt/c/ml-service/app/test_main.py:209:               "model_id": "miewid-msv4.1", "crop_bbox": False,
/mnt/c/ml-service/app/routers/pipeline_router.py:10:from app.models.miewid import MiewidModel
/mnt/c/ml-service/app/routers/extract_router.py:8:from app.models.miewid import MiewidModel
/mnt/c/ml-service/app/routers/explain_router.py:38:    if model.lower().startswith("miewid"):
/mnt/c/ml-service/app/routers/explain_router.py:83:        possible_models = ["miewid-msv3", "miewid-msv4.1"]
/mnt/c/ml-service/app/routers/explain_router.py:215:    model_id: str = "miewid-msv4.1"
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:265:    public static boolean findMatchProspects(JSONObject iaConfig, Task task, Shepherd myShepherd) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:268:        // api_endpoint) as well as legacy entries (with `api_endpoint`).
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:271:            || Util.stringExists(iaConfig.optString("api_endpoint", null));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:281:        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:296:                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:299:                    ann.extractEmbeddings(myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:302:                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:307:                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:326:                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:345:                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:360:                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:371:            System.out.println("findMatchProspects() on " + ann + " found " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:378:                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:381:                System.out.println("findMatchProspects() MatchResult creation failed on " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/SiteSettings.java:137:                            // idOptCopy.remove("api_endpoint");
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/split_dataset.ipynb:23:    "df_annot = pd.read_csv('beluga_example_miewid/annotations.csv')"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/split_dataset.ipynb:230:    "os.makedirs('beluga_example_miewid/splits', exist_ok=True)\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/split_dataset.ipynb:232:    "df_tr.to_csv('beluga_example_miewid/splits/train.csv')\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/split_dataset.ipynb:233:    "df_val.to_csv('beluga_example_miewid/splits/val.csv')\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/split_dataset.ipynb:234:    "df_te.to_csv('beluga_example_miewid/splits/test.csv')"
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:249:        if (queryConfigDict != null) map.put("query_config_dict", queryConfigDict);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:328:        // query_config_dict={'pipeline_root' : 'BC_DTW'}
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:3258:    // right now this just uses opt.queryConfigDict as query_config_dict so it passes thru as-is
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/match_vis.py:77:                        valid_batch_size=2, output_dir='miewid_visualizations'):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:57:    "    anno_path: beluga_example_miewid/benchmark_splits/test.csv\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:58:    "    checkpoint_path: beluga_example_miewid/benchmark_model/miew_id.msv2_all.bin\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:65:    "    anno_path: beluga_example_miewid/benchmark_splits/train.csv\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:70:    "    anno_path: beluga_example_miewid/benchmark_splits/test.csv\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:97:    "project_name: miewid-training\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:124:       "'YAML successfully written to beluga_example_miewid/model/config.yaml'"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:133:    "config_path = 'beluga_example_miewid/model/config.yaml'\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:148:      "Loading config from path: beluga_example_miewid/model/config.yaml\n"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:166:      "Checkpoints will be saved at:  ./runs/miewid-training/beluga-example-exp-1\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:195:      "project_name: miewid-training\n"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:245:       "Syncing run <strong><a href='https://wandb.ai/lashao/miewid-training/runs/psep5t4t/workspace' target=\"_blank\">beluga-example-exp-1</a></strong> to <a href='https://wandb.ai/lashao/miewid-training' target=\"_blank\">Weights & Biases</a> (<a href='https://wandb.me/run' target=\"_blank\">docs</a>)<br/>"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:257:       " View project at <a href='https://wandb.ai/lashao/miewid-training' target=\"_blank\">https://wandb.ai/lashao/miewid-training</a>"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:269:       " View run at <a href='https://wandb.ai/lashao/miewid-training/runs/psep5t4t/workspace' target=\"_blank\">https://wandb.ai/lashao/miewid-training/runs/psep5t4t/workspace</a>"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:689:       " View run <strong style=\"color:#cdcd00\">beluga-example-exp-1</strong> at: <a href='https://wandb.ai/lashao/miewid-training/runs/psep5t4t/workspace' target=\"_blank\">https://wandb.ai/lashao/miewid-training/runs/psep5t4t/workspace</a><br/>Synced 6 W&B file(s), 0 media file(s), 2 artifact file(s) and 0 other file(s)"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/scoring.py:36:def pairx_rerank_score(device, img_0, img_1, model, layer_key,
/mnt/c/ml-service/app/model_config.json:18:	    "model_id": "miewid-msv4.1",
/mnt/c/ml-service/app/model_config.json:19:	    "model_type": "miewid",
/mnt/c/ml-service/app/model_config.json:25:	    "model_id": "miewid-trout",
/mnt/c/ml-service/app/model_config.json:26:	    "model_type": "miewid",
/mnt/c/ml-service/app/model_config.json:28:	    "checkpoint_path": "/datasets/miewid_trout.bin"
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/IAJsonProperties.java:380:    // note the key discrepancy between queryConfigDict and query_config_dict in old world vs new
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/IAJsonProperties.java:396:            thisIdentOpt.put("queryConfigDict", thisIdentOpt.opt("query_config_dict"));
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:58:    "    anno_path: beluga_example_miewid/benchmark_splits/test.csv\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:59:    "    checkpoint_path: beluga_example_miewid/benchmark_model/miew_id.msv2_all.bin\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:66:    "    anno_path: beluga_example_miewid/benchmark_splits/train.csv\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:71:    "    anno_path: beluga_example_miewid/benchmark_splits/test.csv\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:98:    "project_name: miewid-training\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:125:       "'YAML successfully written to beluga_example_miewid/model/config.yaml'"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:134:    "config_path = 'beluga_example_miewid/model/config.yaml'\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:149:      "Loading config from path: beluga_example_miewid/model/config.yaml\n"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:175:    "model_tag = f\"conservationxlabs/miewid-msv3\"\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:198:      "Checkpoints will be saved at:  ./runs/miewid-training/beluga-example-exp-1\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:228:      "project_name: miewid-training\n"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:278:       "Syncing run <strong><a href='https://wandb.ai/lashao/miewid-training/runs/to3kw81u' target=\"_blank\">beluga-example-exp-1</a></strong> to <a href='https://wandb.ai/lashao/miewid-training' target=\"_blank\">Weights & Biases</a> (<a href='https://wandb.me/run' target=\"_blank\">docs</a>)<br/>"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:290:       " View project at <a href='https://wandb.ai/lashao/miewid-training' target=\"_blank\">https://wandb.ai/lashao/miewid-training</a>"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:302:       " View run at <a href='https://wandb.ai/lashao/miewid-training/runs/to3kw81u' target=\"_blank\">https://wandb.ai/lashao/miewid-training/runs/to3kw81u</a>"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:929:       " View run <strong style=\"color:#cdcd00\">beluga-example-exp-1</strong> at: <a href='https://wandb.ai/lashao/miewid-training/runs/to3kw81u' target=\"_blank\">https://wandb.ai/lashao/miewid-training/runs/to3kw81u</a><br/> View project at: <a href='https://wandb.ai/lashao/miewid-training' target=\"_blank\">https://wandb.ai/lashao/miewid-training</a><br/>Synced 6 W&B file(s), 0 media file(s), 2 artifact file(s) and 0 other file(s)"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:57:      "loaded checkpoint from beluga_example_miewid/benchmark_model/miew_id.msv2_all.bin\n"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:65:    "    anno_path='beluga_example_miewid/benchmark_splits/test.csv',\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:80:    "    checkpoint_path='beluga_example_miewid/benchmark_model/miew_id.msv2_all.bin',\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:202:    "model_tag = f\"conservationxlabs/miewid-msv2\""
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:233:       "configuration_miewid.py:   0%|          | 0.00/777 [00:00<?, ?B/s]"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:243:      "A new version of the following files was downloaded from https://huggingface.co/conservationxlabs/miewid-msv2:\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:244:      "- configuration_miewid.py\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:256:       "modeling_miewid.py:   0%|          | 0.00/5.33k [00:00<?, ?B/s]"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:280:      "A new version of the following files was downloaded from https://huggingface.co/conservationxlabs/miewid-msv2:\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:283:      "A new version of the following files was downloaded from https://huggingface.co/conservationxlabs/miewid-msv2:\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:284:      "- modeling_miewid.py\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:328:    "    anno_path='beluga_example_miewid/benchmark_splits/test.csv',\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:30:       from example_loaders import toy_df, wildme_multispecies_miewid
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:37:To use [WildMe multispecies MiewID](https://huggingface.co/conservationxlabs/miewid-msv2), you can use:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:39:       model, img_size, img_transforms = wildme_multispecies_miewid(device)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/download_example.py:29:    url = "https://cthulhu.dyn.wildme.io/public/datasets/beluga_example_miewid.tar.gz"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/download_example.py:32:    tar_path = os.path.join(script_dir, "beluga_example_miewid.tar.gz")
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/download_example.py:33:    extract_to = os.path.join(script_dir, "beluga_example_miewid")
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:132:        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:139:        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:746:        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:749:        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:751:            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:763:        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:764:            "query_config_dict");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:766:            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:771:            getParameters().getJSONObject("ibeis.identification").optString("description",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:840:        // ml-service path doesn't persist `ibeis.identification` on its
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/example_loaders.py:5:def wildme_multispecies_miewid(device):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/example_loaders.py:6:    ckpt_path = f"conservationxlabs/miewid-msv2"
/mnt/c/ml-service/app/models/model_handler.py:18:    'miewid': {
/mnt/c/ml-service/app/models/model_handler.py:19:        'module': 'app.models.miewid',
/mnt/c/ml-service/app/models/miewid.py:85:            'model_type': 'miewid',
/mnt/c/ml-service/app/models/miewid.py:96:            model_tag = f"conservationxlabs/miewid-msv3"
/mnt/c/ml-service/app/models/miewid.py:98:            model_tag = f"conservationxlabs/miewid-msv2"
/mnt/c/ml-service/app/models/miewid.py:128:            model_tag = "conservationxlabs/miewid-msv3"
/mnt/c/ml-service/app/models/miewid.py:130:            model_tag = "conservationxlabs/miewid-msv2"
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:214:                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:275:                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:523:            // findMatchProspects returns false when the match config is not
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:526:            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:530:                    "findMatchProspects rejected match config: " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:557:            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:694:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:696:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:739:        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:740:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:741:            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:403:        payload.put("model_id", "miewid-msv4.1");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:550:            urlStr = confs.get(0).optString("api_endpoint", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:247:            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1739:    public void extractEmbeddings(String txStr, Shepherd myShepherd)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1744:        System.out.println("[INFO] extractEmbeddings(): embedding processed for " + this);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1748:    public void extractEmbeddings(Shepherd myShepherd)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1753:        extractEmbeddings(enc.getTaxonomyString(), myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:200:        payload.put("model_id", "miewid-msv4.1");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:441:                newTaskParams.put("ibeis.identification",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:449:                    newTaskParams.put("ibeis.identification",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:470:                if (Embedding.findMatchProspects(opts.get(i), tasks.get(i), myShepherd)) continue;
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/demo.py:6:from example_loaders import toy_df, wildme_multispecies_miewid
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/demo.py:19:    model, img_size, img_transforms = wildme_multispecies_miewid(device)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:227:            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:305:            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:364:        if (endpoint == null) throw new IAException("null api_endpoint");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:369:            throw new IAException("api_endpoint url error: " + urlEx);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:402:        payload.remove("api_endpoint");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:418:        payload.remove("api_endpoint");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:34: * entirely (legacy api_endpoint-only configs that can't derive a
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:57:     * @param method         embedding method (e.g. "miewid-msv4.1");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:103:        // fallback chain as Embedding.findMatchProspects
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:105:        // then MLService.getMethodValues for legacy api_endpoint
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:498:        // csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 ||
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:507:            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && created > "
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:519:            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && parameters.indexOf('fastlane') > -1 && created > "
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:525:            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && parameters.indexOf('fastlane') > -1 && completionDateInMilliseconds > "
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:603:                "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) ";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:478:    // (commit #9) post-persist to avoid running findMatchProspects against an
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:647:            // embedding entry. (Legacy api_endpoint-only configs that
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/GetCurrentIAInfo.java:129:                        task.getParameters().has("ibeis.identification")) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportTask.java:432:                        atask.getParameters().has("ibeis.identification")) {

 succeeded in 1821ms:
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/FormUtilities.java:201:            ClassEditTemplate.capitalizedPrettyFieldName(fieldName));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/IndividualQueryProcessor.java:971:            String specificEpithet = "";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/IndividualQueryProcessor.java:978:                	specificEpithet+=tokenizer.nextToken()+" ";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/IndividualQueryProcessor.java:980:                specificEpithet=specificEpithet.trim();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/IndividualQueryProcessor.java:984:                    " enc.specificEpithet == '" + specificEpithet + "' ");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/SpotterConserveIO.java:248:            enc.setSpecificEpithet(tax[1]);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/SpotterConserveIO.java:581:        enc.setSpecificEpithet("test");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/SpotterConserveIO.java:584:            enc.setSpecificEpithet(waSpecies);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Util.java:410:    public static String[] stringToGenusSpecificEpithet(String s) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Util.java:793:    public static String capitolizeFirstLetter(String str) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Util.java:799:    public static String capitolizeFirstLetterOnly(String str) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Util.java:802:        return capitolizeFirstLetter(lower);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Util.java:410:    public static String[] stringToGenusSpecificEpithet(String s) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Util.java:793:    public static String capitolizeFirstLetter(String str) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Util.java:799:    public static String capitolizeFirstLetterOnly(String str) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Util.java:802:        return capitolizeFirstLetter(lower);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/export/EncounterAnnotationExportFile.java:135:        ExportColumn.newEasyColumn("Encounter.specificEpithet", columns);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:1620:                            if (enc.getGenus() != null && enc.getSpecificEpithet() != null &&
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:3744:            "SELECT \"ANNOTATION\".\"ACMID\" as acmId, \"ENCOUNTER\".\"GENUS\" as genus, \"ENCOUNTER\".\"SPECIFICEPITHET\" as specificEpithet FROM \"ANNOTATION\" JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ENCOUNTER_ANNOTATIONS\".\"ID_EID\" = \"ANNOTATION\".\"ID\") JOIN \"ENCOUNTER\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") WHERE \"ANNOTATION\".\"ACMID\" IS NOT NULL;";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:3754:            String specificEpithet = (String)row[2];
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:3755:            rtn.put(acmId, Util.taxonomyString(genus, specificEpithet));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:84:                    pageSize = os.getSettings(indexName).optInt("max_result_window", 10000);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:88:                    JSONObject queryRes = os.queryPit(indexName, sanitized, numFrom, pageSize, sort,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1339:                String specificEpithet = "";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1346:                    	specificEpithet+=tokenizer.nextToken()+" ";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1348:                    specificEpithet=specificEpithet.trim();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1352:                    filter += " && specificEpithet == '" + specificEpithet + "' ";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1605:                pageSize = os.getSettings(indexName).optInt("max_result_window", 10000);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1609:                JSONObject queryRes = os.queryPit(indexName, sanitized, numFrom, pageSize, sort,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Taxonomy.java:105:    public String[] getGenusSpecificEpithet() {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Taxonomy.java:106:        return Util.stringToGenusSpecificEpithet(this.scientificName);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Taxonomy.java:105:    public String[] getGenusSpecificEpithet() {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Taxonomy.java:106:        return Util.stringToGenusSpecificEpithet(this.scientificName);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:38:    public static final int KNN_K_DISTANCE_VALUE = 50;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1158:                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1161:            queryRes = os.queryPit("annotation", query, 0, pageSize, null, null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1214:        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("k", KNN_K_DISTANCE_VALUE);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1284:                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1287:            queryRes = os.queryPit("annotation", matchQuery, 0, pageSize, null, null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1450:                newEnc.setSpecificEpithet(someEnc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Base.java:251:        JSONObject res = opensearch.queryPit(indexname, query, numFrom, pageSize, sort, sortOrder);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:98:    public String specificEpithet;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:420:        if (shouldReplace(enc2.getSpecificEpithet(), getSpecificEpithet()))
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:421:            setSpecificEpithet(enc2.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:1702:    // lmao, we have this capitalization of the getter for reflexivity purposes
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:1723:    // lmao, we have this capitalization of the getter for reflexivity purposes
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2200:        setSpecificEpithet(species);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2204:    public String getSpecificEpithet() {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2205:        return specificEpithet;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2208:    public void setSpecificEpithet(String newEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2209:        if (newEpithet != null) { specificEpithet = newEpithet.replaceAll("_", " "); } else {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2210:            specificEpithet = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2216:        return Util.taxonomyString(getGenus(), getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2228:    // right now this updates .genus and .specificEpithet ... but in some glorious future we will just store Taxonomy!
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2232:        String[] gs = tax.getGenusSpecificEpithet();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2236:            this.specificEpithet = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2239:            this.specificEpithet = gs[1].replaceAll("_", " ");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2245:        String[] gs = Util.stringToGenusSpecificEpithet(s);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2250:            this.specificEpithet = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2253:            this.specificEpithet = gs[1].replaceAll("_", " ");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2290:        this.setSpecificEpithet(sp[1]);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:3396:        jobj.remove("specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:3714:        enc.setSpecificEpithet(this.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:5149:        case "specificEpithet":
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:5150:            setSpecificEpithet((String)value);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:66:    private String specificEpithet;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1047:    public String getSpecificEpithet() {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1048:        return specificEpithet;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1051:    public void setSpecificEpithet(String newEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1052:        specificEpithet = newEpithet;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1060:            setSpecificEpithet(tax);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1067:            setSpecificEpithet(ep);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1072:        return Util.taxonomyString(getGenus(), getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1079:        if (!force && ((genus != null) || (specificEpithet != null))) return getTaxonomyString();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1082:            if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1084:                specificEpithet = enc.getSpecificEpithet();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1767:            String s = Util.taxonomyString(enc.getGenus(), enc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2433:        String genus, String specificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2449:        // System.out.println("findByNames: "+genus+" "+specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2451:        if ((genus != null) && (specificEpithet != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2453:            specificEpithet = specificEpithet.trim();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2454:            taxonomyStringFilter = " && enc.genus == '" + genus + "' && enc.specificEpithet == '" +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2455:                specificEpithet + "' VARIABLES org.ecocean.Encounter enc";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2482:        String specificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2485:        System.out.println("withName: " + genus + " " + specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2486:        List<MarkedIndividual> inds = findByNames(myShepherd, regex, genus, specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:77:    private int pitRetry = 0;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:217:            // also? "knn.algo_param.ef_search": 100
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:348:        String id = jrtn.optString("pit_id", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:365:        String pitId = PIT_CACHE.get(indexName);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:367:        if (pitId == null) return;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:370:        body.put("pit_id", pitId);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:374:        System.out.println("OpenSearch.deletePit(" + indexName + ") [" + pitId + "] completed");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:377:    public JSONObject queryPit(String indexName, final JSONObject query, int numFrom, int pageSize,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:381:        String pitId = createPit(indexName);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:392:        JSONObject jpit = new JSONObject();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:393:        jpit.put("id", pitId);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:394:        jpit.put("keep_alive", SEARCH_PIT_TIME);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:395:        query.put("pit", jpit);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:400:            pitRetry = 0;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:402:            System.out.println("queryPit() using pitId=" + pitId + " failed[" + pitRetry +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:404:            pitRetry++;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:405:            if (pitRetry > 5) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:407:                throw new IOException("queryPit() failed to POST query");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:411:            return queryPit(indexName, query, numFrom, pageSize, sort, sortOrder);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:714:                new JSONObject("{\"index.max_result_window\": " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/SearchApi.java:68:                    // for now, we delete pit by default. TODO: let frontend decide when to keep it
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/SearchApi.java:69:                    // by passing in the previous pit (e.g. for pagination)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/SearchApi.java:91:                        JSONObject queryRes = os.queryPit(indexName, query, numFrom, pageSize, sort,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OccurrenceQueryProcessor.java:119:            String specificEpithet = "";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OccurrenceQueryProcessor.java:123:                specificEpithet = tokenizer.nextToken();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OccurrenceQueryProcessor.java:127:                    " enc.specificEpithet == '" + specificEpithet + "' ");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java:37:        "specificEpithet", "year", "submitterID"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java:319:        // make sure we didnt set one of genus/specificEpithet and create invalid taxonomy
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java:322:            throw new ApiException("genus and specificEpithet are an invalid taxonomy: " + sciName,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:200:                        String specificEpithet = str.nextToken();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:201:                        if (str.hasMoreTokens()) specificEpithet += " " + str.nextToken();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:203:                            "SELECT count(this) FROM org.ecocean.MarkedIndividual where encounters.contains(enc) && enc.specificEpithet == '"
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:204:                            + specificEpithet.replaceAll("_", " ") + "'",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:205:                            (genus + "_" + specificEpithet.replaceAll(" ", "_")),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:206:                            "Number of marked individuals (" + genus + " " + specificEpithet + ")",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:212:                            "SELECT count(this) FROM org.ecocean.Encounter where specificEpithet == '" +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:213:                            specificEpithet.replaceAll("_", " ") + "'",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:214:                            (genus + "_" + specificEpithet.replaceAll(" ", "_")),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:215:                            "Number of encounters (" + genus + " " + specificEpithet + ")", context);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:441:            JSONObject result = os.queryPit(indexName, query, 0, 0, null, null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/SpotterConserveIO.java:248:            enc.setSpecificEpithet(tax[1]);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/SpotterConserveIO.java:581:        enc.setSpecificEpithet("test");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/SpotterConserveIO.java:584:            enc.setSpecificEpithet(waSpecies);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkValidator.java:33:        "Encounter.researcherComments", "Encounter.sex", "Encounter.specificEpithet",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkValidator.java:67:        "Encounter.genus", "Encounter.specificEpithet"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImportUtil.java:130:        Object taxS = getValue(rtn, "Encounter.specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImportUtil.java:137:                rtn.put("Encounter.specificEpithet",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:327:            // despite the fact, the lookup of existing projects uses prefix only. so replicating questionable behavior here.
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:432:            case "Encounter.specificEpithet":
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:433:                enc.setSpecificEpithet(bv.getValueString());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:879:            fmap.containsKey("Encounter.specificEpithet"))) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:881:                ") is missing genus and/or specificEpithet values");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:885:        String specificEpithet = fmap.get("Encounter.specificEpithet").getValueString();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:887:            indiv = MarkedIndividual.withName(myShepherd, id, genus, specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:896:            indiv.setSpecificEpithet(specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/CommonConfiguration.java:347:    public static String getAnimalSingularCapitalized(String context) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/CommonConfiguration.java:349:            return getProperty("animalSingularCapitalized", context).trim();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/CommonConfiguration.java:352:                "Error in getAnimalSingularCapitalized. Returning nothing as a default.");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/CommonConfiguration.java:366:    public static String animalPluralCapitalized(String context) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/CommonConfiguration.java:371:                "Error in getAnimalPluralCapitalized. Returning nothing as a default.");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ClassEditTemplate.java:164:        return (nameLookup.getProperty(fieldName, capitalizedPrettyFieldName(fieldName)));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ClassEditTemplate.java:177:    public static String capitalizedPrettyFieldName(String fieldName) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:50:            // specificEpithet = request.getParameter("specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:53:                String specificEpithet = "";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:60:                    specificEpithet = tokenizer.nextToken();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:64:                        specificEpithet += " " + tokenizer.nextToken();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:67:                    myShark.setSpecificEpithet(specificEpithet.replaceAll(",", "").replaceAll("_",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:71:                        "Set genus and species to: " + genus + " " + specificEpithet.replaceAll(",",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:75:                    myShark.setSpecificEpithet(null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:375:        String specificEpithet = getString(row, "Encounter.specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:376:        if (specificEpithet != null) enc.setSpecificEpithet(specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:575:    // capitolizes the final directory in path
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:577:        String fixed = capitolizeLastFilepart(path);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:588:    private String capitolizeLastFilepart(String path) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:707:        String capitolizedPart = filename.substring(indexOfLastSlash + 1,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:711:        return (beforePart + capitolizedPart + afterPart);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:781:        String specificEpithet = getString(row, "Encounter.specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:782:        if (specificEpithet != null) mark.setSpecificEpithet(specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetField.java:43:            fieldSetterName = "set" + Util.capitolizeFirstLetterOnly(fieldName);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:468:        String specificEpithet = getString(row, "Encounter.specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:469:        if (specificEpithet != null) enc.setSpecificEpithet(specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:740:    // capitolizes the final directory in path
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:742:        String fixed = capitolizeLastFilepart(path);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:753:    private String capitolizeLastFilepart(String path) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:913:        String capitolizedPart = filename.substring(indexOfLastSlash + 1,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:917:        return (beforePart + capitolizedPart + afterPart);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:895:        String specificEpithet = getString(row, "Encounter.specificEpithet", colIndexMap, verbose,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:897:        boolean hasSpecificEpithet = false;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:898:        if (specificEpithet != null && !specificEpithet.trim().equals("")) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:899:            hasSpecificEpithet = true;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:900:            enc.setSpecificEpithet(specificEpithet.trim());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:908:        if (!hasSpecificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:909:            // mark specific epithet
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:910:            feedback.logParseError(getColIndexFromColName("Encounter.specificEpithet", colIndexMap),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:914:        if (hasGenus && hasSpecificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:923:                feedback.logParseError(getColIndexFromColName("Encounter.specificEpithet",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:924:                    colIndexMap), specificEpithet, row, "UNSUPPORTED VALUE: " + specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:1431:        String species = getString(row, "Encounter.specificEpithet", colIndexMap, verbose,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:1725:        String capitolizedPart = filename.substring(indexOfLastSlash + 1,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:1729:        return (beforePart + capitolizedPart + afterPart);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:1825:                enc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:1830:                individualID, enc.getGenus(), enc.getSpecificEpithet(), userIndividualCache);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2348:        String genus, String specificEpithet,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2353:                if (mi.getGenus() == null || mi.getSpecificEpithet() == null ||
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2354:                    "".equals(mi.getSpecificEpithet()) || "".equals(mi.getGenus())) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2358:                    if (genus != null && specificEpithet != null && !"".equals(genus) &&
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2359:                        !"".equals(specificEpithet)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2361:                            specificEpithet.equals(mi.getSpecificEpithet())) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/SearchApi.java:68:                    // for now, we delete pit by default. TODO: let frontend decide when to keep it
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/SearchApi.java:69:                    // by passing in the previous pit (e.g. for pagination)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/SearchApi.java:91:                        JSONObject queryRes = os.queryPit(indexName, query, numFrom, pageSize, sort,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:1620:                            if (enc.getGenus() != null && enc.getSpecificEpithet() != null &&
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:3744:            "SELECT \"ANNOTATION\".\"ACMID\" as acmId, \"ENCOUNTER\".\"GENUS\" as genus, \"ENCOUNTER\".\"SPECIFICEPITHET\" as specificEpithet FROM \"ANNOTATION\" JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ENCOUNTER_ANNOTATIONS\".\"ID_EID\" = \"ANNOTATION\".\"ID\") JOIN \"ENCOUNTER\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") WHERE \"ANNOTATION\".\"ACMID\" IS NOT NULL;";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:3754:            String specificEpithet = (String)row[2];
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:3755:            rtn.put(acmId, Util.taxonomyString(genus, specificEpithet));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/media/MediaAsset.java:910:                        if (enc.getGenus() != null && enc.getSpecificEpithet() != null) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/media/MediaAsset.java:912:                            jf.put("specificEpithet", enc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/media/MediaAsset.java:1644:                        newEnc.setSpecificEpithet(whichever.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java:37:        "specificEpithet", "year", "submitterID"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java:319:        // make sure we didnt set one of genus/specificEpithet and create invalid taxonomy
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java:322:            throw new ApiException("genus and specificEpithet are an invalid taxonomy: " + sciName,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportExcelMetadata.java:726:        enc.setSpecificEpithet("geometricus");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportAccess.java:539:        enc.setSpecificEpithet("novaeangliae");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportAccess.java:922:        // note non-capitalization of fin as opposed to Right Dors. Fin
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:307:    // capitolizes the final directory in path
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:309:        String fixed = capitolizeLastFilepart(path);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:320:    private String capitolizeLastFilepart(String path) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:440:        String species = getString(row, "Encounter.specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:441:        if (Util.shouldReplace(species, mark.getSpecificEpithet()))
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:442:            mark.setSpecificEpithet(species);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:395:            String specificEpithet = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:404:                        specificEpithet = tokenizer.nextToken().replaceAll(",", "").replaceAll("_",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:459:                    genus, specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:470:                        genus, specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:482:                            specificEpithet), ma));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:491:            enc.setSpecificEpithet(specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:1046:        String specificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:1069:                newAnnotations.add(new Annotation(Util.taxonomyString(genus, specificEpithet), ma));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:1095:        Encounter enc, ArrayList<Annotation> newAnnotations, String genus, String specificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:1118:                newAnnotations.add(new Annotation(Util.taxonomyString(genus, specificEpithet), ma));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:200:                        String specificEpithet = str.nextToken();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:201:                        if (str.hasMoreTokens()) specificEpithet += " " + str.nextToken();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:203:                            "SELECT count(this) FROM org.ecocean.MarkedIndividual where encounters.contains(enc) && enc.specificEpithet == '"
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:204:                            + specificEpithet.replaceAll("_", " ") + "'",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:205:                            (genus + "_" + specificEpithet.replaceAll(" ", "_")),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:206:                            "Number of marked individuals (" + genus + " " + specificEpithet + ")",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:212:                            "SELECT count(this) FROM org.ecocean.Encounter where specificEpithet == '" +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:213:                            specificEpithet.replaceAll("_", " ") + "'",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:214:                            (genus + "_" + specificEpithet.replaceAll(" ", "_")),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:215:                            "Number of encounters (" + genus + " " + specificEpithet + ")", context);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MetricsBot.java:441:            JSONObject result = os.queryPit(indexName, query, 0, 0, null, null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:425:                    System.out.println("=======> Specific Epithet is: " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:426:                        ann.findEncounter(myShepherd).getSpecificEpithet() + "    Genus is: " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/CommonConfiguration.java:347:    public static String getAnimalSingularCapitalized(String context) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/CommonConfiguration.java:349:            return getProperty("animalSingularCapitalized", context).trim();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/CommonConfiguration.java:352:                "Error in getAnimalSingularCapitalized. Returning nothing as a default.");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/CommonConfiguration.java:366:    public static String animalPluralCapitalized(String context) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/CommonConfiguration.java:371:                "Error in getAnimalPluralCapitalized. Returning nothing as a default.");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ClassEditTemplate.java:164:        return (nameLookup.getProperty(fieldName, capitalizedPrettyFieldName(fieldName)));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ClassEditTemplate.java:177:    public static String capitalizedPrettyFieldName(String fieldName) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkValidator.java:33:        "Encounter.researcherComments", "Encounter.sex", "Encounter.specificEpithet",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkValidator.java:67:        "Encounter.genus", "Encounter.specificEpithet"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImportUtil.java:130:        Object taxS = getValue(rtn, "Encounter.specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImportUtil.java:137:                rtn.put("Encounter.specificEpithet",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:327:            // despite the fact, the lookup of existing projects uses prefix only. so replicating questionable behavior here.
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:432:            case "Encounter.specificEpithet":
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:433:                enc.setSpecificEpithet(bv.getValueString());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:879:            fmap.containsKey("Encounter.specificEpithet"))) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:881:                ") is missing genus and/or specificEpithet values");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:885:        String specificEpithet = fmap.get("Encounter.specificEpithet").getValueString();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:887:            indiv = MarkedIndividual.withName(myShepherd, id, genus, specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/bulk/BulkImporter.java:896:            indiv.setSpecificEpithet(specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportExcelFile.java:173:                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportExcelFile.java:175:                            (enc.getGenus() + " " + enc.getSpecificEpithet()));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportExcelFile.java:204:                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportExcelFile.java:207:                        Label lNumberx15 = new Label(14, count, enc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Base.java:251:        JSONObject res = opensearch.queryPit(indexname, query, numFrom, pageSize, sort, sortOrder);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:38:    public static final int KNN_K_DISTANCE_VALUE = 50;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1158:                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1161:            queryRes = os.queryPit("annotation", query, 0, pageSize, null, null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1214:        knn.getJSONObject("knn").getJSONObject("embeddings.vector").put("k", KNN_K_DISTANCE_VALUE);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1284:                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1287:            queryRes = os.queryPit("annotation", matchQuery, 0, pageSize, null, null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1450:                newEnc.setSpecificEpithet(someEnc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/GridManager.java:640:            if (el.getGenus() != null && el.getSpecificEpithet() != null)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/GridManager.java:641:                species = (el.getGenus() + " " + el.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:98:    public String specificEpithet;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:420:        if (shouldReplace(enc2.getSpecificEpithet(), getSpecificEpithet()))
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:421:            setSpecificEpithet(enc2.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:1702:    // lmao, we have this capitalization of the getter for reflexivity purposes
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:1723:    // lmao, we have this capitalization of the getter for reflexivity purposes
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2200:        setSpecificEpithet(species);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2204:    public String getSpecificEpithet() {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2205:        return specificEpithet;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2208:    public void setSpecificEpithet(String newEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2209:        if (newEpithet != null) { specificEpithet = newEpithet.replaceAll("_", " "); } else {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2210:            specificEpithet = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2216:        return Util.taxonomyString(getGenus(), getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2228:    // right now this updates .genus and .specificEpithet ... but in some glorious future we will just store Taxonomy!
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2232:        String[] gs = tax.getGenusSpecificEpithet();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2236:            this.specificEpithet = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2239:            this.specificEpithet = gs[1].replaceAll("_", " ");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2245:        String[] gs = Util.stringToGenusSpecificEpithet(s);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2250:            this.specificEpithet = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2253:            this.specificEpithet = gs[1].replaceAll("_", " ");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:2290:        this.setSpecificEpithet(sp[1]);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:3396:        jobj.remove("specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:3714:        enc.setSpecificEpithet(this.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:5149:        case "specificEpithet":
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Encounter.java:5150:            setSpecificEpithet((String)value);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterVMData.java:105:                    filter += " && specificEpithet == '" + enc.getSpecificEpithet() + "'";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/GetIndividualSearchGoogleMapsPoints.java:90:        grp2.addMember("sex").addMember("genus").addMember("specificEpithet").addMember(
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportShapefile.java:179:                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportShapefile.java:180:                        genusSpeciesString = enc.getGenus() + " " + enc.getSpecificEpithet();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:47:    String specificEpithet = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:110:            if (enc.getSpecificEpithet() != null) this.specificEpithet = enc.getSpecificEpithet();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1587:    public String getSpecificEpithet() { return specificEpithet; }
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1589:        if (this.getGenus() == null || this.getSpecificEpithet() == null) { return false; }
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1590:        if (el.getGenus() == null || el.getSpecificEpithet() == null) { return false; }
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1592:            this.getSpecificEpithet().equals(el.getSpecificEpithet())) return true;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1607:        if (specificEpithet != null) j.put("specificEpithet", specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1640:        el.specificEpithet = j.optString("specificEpithet", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/ExportColumn.java:118:        className = Util.capitolizeFirstLetterOnly(className);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/ExportColumn.java:129:        String getterName = "get" + Util.capitolizeFirstLetter(fieldName);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportMetadataExcel.java:183:                ExportColumn.newEasyColumn("Encounter.specificEpithet", columns);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/GetEncounterSearchGoogleMapsPoints.java:188:                            enc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportSocialNetwork.java:122:                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportSocialNetwork.java:124:                            (enc.getGenus() + " " + enc.getSpecificEpithet()));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportSocialNetwork.java:153:                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportSocialNetwork.java:156:                        Label lNumberx15 = new Label(14, count, enc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/export/EncounterAnnotationExportFile.java:135:        ExportColumn.newEasyColumn("Encounter.specificEpithet", columns);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:84:                    pageSize = os.getSettings(indexName).optInt("max_result_window", 10000);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:88:                    JSONObject queryRes = os.queryPit(indexName, sanitized, numFrom, pageSize, sort,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1339:                String specificEpithet = "";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1346:                    	specificEpithet+=tokenizer.nextToken()+" ";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1348:                    specificEpithet=specificEpithet.trim();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1352:                    filter += " && specificEpithet == '" + specificEpithet + "' ";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1605:                pageSize = os.getSettings(indexName).optInt("max_result_window", 10000);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/EncounterQueryProcessor.java:1609:                JSONObject queryRes = os.queryPit(indexName, sanitized, numFrom, pageSize, sort,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/media/MediaAsset.java:910:                        if (enc.getGenus() != null && enc.getSpecificEpithet() != null) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/media/MediaAsset.java:912:                            jf.put("specificEpithet", enc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/media/MediaAsset.java:1644:                        newEnc.setSpecificEpithet(whichever.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:47:    String specificEpithet = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:110:            if (enc.getSpecificEpithet() != null) this.specificEpithet = enc.getSpecificEpithet();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1587:    public String getSpecificEpithet() { return specificEpithet; }
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1589:        if (this.getGenus() == null || this.getSpecificEpithet() == null) { return false; }
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1590:        if (el.getGenus() == null || el.getSpecificEpithet() == null) { return false; }
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1592:            this.getSpecificEpithet().equals(el.getSpecificEpithet())) return true;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1607:        if (specificEpithet != null) j.put("specificEpithet", specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/EncounterLite.java:1640:        el.specificEpithet = j.optString("specificEpithet", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/GridManager.java:640:            if (el.getGenus() != null && el.getSpecificEpithet() != null)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/grid/GridManager.java:641:                species = (el.getGenus() + " " + el.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/LightRestServlet.java:75:            "submitterOrganization", "genus", "specificEpithet", "dwcDateAdded", "modified"
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/FormUtilities.java:201:            ClassEditTemplate.capitalizedPrettyFieldName(fieldName));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/IndividualQueryProcessor.java:971:            String specificEpithet = "";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/IndividualQueryProcessor.java:978:                	specificEpithet+=tokenizer.nextToken()+" ";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/IndividualQueryProcessor.java:980:                specificEpithet=specificEpithet.trim();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/IndividualQueryProcessor.java:984:                    " enc.specificEpithet == '" + specificEpithet + "' ");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:66:    private String specificEpithet;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1047:    public String getSpecificEpithet() {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1048:        return specificEpithet;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1051:    public void setSpecificEpithet(String newEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1052:        specificEpithet = newEpithet;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1060:            setSpecificEpithet(tax);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1067:            setSpecificEpithet(ep);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1072:        return Util.taxonomyString(getGenus(), getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1079:        if (!force && ((genus != null) || (specificEpithet != null))) return getTaxonomyString();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1082:            if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1084:                specificEpithet = enc.getSpecificEpithet();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:1767:            String s = Util.taxonomyString(enc.getGenus(), enc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2433:        String genus, String specificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2449:        // System.out.println("findByNames: "+genus+" "+specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2451:        if ((genus != null) && (specificEpithet != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2453:            specificEpithet = specificEpithet.trim();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2454:            taxonomyStringFilter = " && enc.genus == '" + genus + "' && enc.specificEpithet == '" +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2455:                specificEpithet + "' VARIABLES org.ecocean.Encounter enc";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2482:        String specificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2485:        System.out.println("withName: " + genus + " " + specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/MarkedIndividual.java:2486:        List<MarkedIndividual> inds = findByNames(myShepherd, regex, genus, specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:395:            String specificEpithet = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:404:                        specificEpithet = tokenizer.nextToken().replaceAll(",", "").replaceAll("_",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:459:                    genus, specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:470:                        genus, specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:482:                            specificEpithet), ma));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:491:            enc.setSpecificEpithet(specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:1046:        String specificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:1069:                newAnnotations.add(new Annotation(Util.taxonomyString(genus, specificEpithet), ma));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:1095:        Encounter enc, ArrayList<Annotation> newAnnotations, String genus, String specificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:1118:                newAnnotations.add(new Annotation(Util.taxonomyString(genus, specificEpithet), ma));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:50:            // specificEpithet = request.getParameter("specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:53:                String specificEpithet = "";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:60:                    specificEpithet = tokenizer.nextToken();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:64:                        specificEpithet += " " + tokenizer.nextToken();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:67:                    myShark.setSpecificEpithet(specificEpithet.replaceAll(",", "").replaceAll("_",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:71:                        "Set genus and species to: " + genus + " " + specificEpithet.replaceAll(",",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:75:                    myShark.setSpecificEpithet(null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetField.java:43:            fieldSetterName = "set" + Util.capitolizeFirstLetterOnly(fieldName);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:77:    private int pitRetry = 0;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:217:            // also? "knn.algo_param.ef_search": 100
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:348:        String id = jrtn.optString("pit_id", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:365:        String pitId = PIT_CACHE.get(indexName);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:367:        if (pitId == null) return;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:370:        body.put("pit_id", pitId);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:374:        System.out.println("OpenSearch.deletePit(" + indexName + ") [" + pitId + "] completed");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:377:    public JSONObject queryPit(String indexName, final JSONObject query, int numFrom, int pageSize,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:381:        String pitId = createPit(indexName);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:392:        JSONObject jpit = new JSONObject();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:393:        jpit.put("id", pitId);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:394:        jpit.put("keep_alive", SEARCH_PIT_TIME);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:395:        query.put("pit", jpit);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:400:            pitRetry = 0;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:402:            System.out.println("queryPit() using pitId=" + pitId + " failed[" + pitRetry +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:404:            pitRetry++;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:405:            if (pitRetry > 5) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:407:                throw new IOException("queryPit() failed to POST query");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:411:            return queryPit(indexName, query, numFrom, pageSize, sort, sortOrder);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:714:                new JSONObject("{\"index.max_result_window\": " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OccurrenceQueryProcessor.java:119:            String specificEpithet = "";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OccurrenceQueryProcessor.java:123:                specificEpithet = tokenizer.nextToken();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OccurrenceQueryProcessor.java:127:                    " enc.specificEpithet == '" + specificEpithet + "' ");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterVMData.java:105:                    filter += " && specificEpithet == '" + enc.getSpecificEpithet() + "'";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:375:        String specificEpithet = getString(row, "Encounter.specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:376:        if (specificEpithet != null) enc.setSpecificEpithet(specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:575:    // capitolizes the final directory in path
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:577:        String fixed = capitolizeLastFilepart(path);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:588:    private String capitolizeLastFilepart(String path) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:707:        String capitolizedPart = filename.substring(indexOfLastSlash + 1,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:711:        return (beforePart + capitolizedPart + afterPart);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:781:        String specificEpithet = getString(row, "Encounter.specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/WebImport.java:782:        if (specificEpithet != null) mark.setSpecificEpithet(specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:468:        String specificEpithet = getString(row, "Encounter.specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:469:        if (specificEpithet != null) enc.setSpecificEpithet(specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:740:    // capitolizes the final directory in path
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:742:        String fixed = capitolizeLastFilepart(path);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:753:    private String capitolizeLastFilepart(String path) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:913:        String capitolizedPart = filename.substring(indexOfLastSlash + 1,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:917:        return (beforePart + capitolizedPart + afterPart);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:895:        String specificEpithet = getString(row, "Encounter.specificEpithet", colIndexMap, verbose,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:897:        boolean hasSpecificEpithet = false;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:898:        if (specificEpithet != null && !specificEpithet.trim().equals("")) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:899:            hasSpecificEpithet = true;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:900:            enc.setSpecificEpithet(specificEpithet.trim());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:908:        if (!hasSpecificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:909:            // mark specific epithet
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:910:            feedback.logParseError(getColIndexFromColName("Encounter.specificEpithet", colIndexMap),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:914:        if (hasGenus && hasSpecificEpithet) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:923:                feedback.logParseError(getColIndexFromColName("Encounter.specificEpithet",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:924:                    colIndexMap), specificEpithet, row, "UNSUPPORTED VALUE: " + specificEpithet);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:1431:        String species = getString(row, "Encounter.specificEpithet", colIndexMap, verbose,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:1725:        String capitolizedPart = filename.substring(indexOfLastSlash + 1,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:1729:        return (beforePart + capitolizedPart + afterPart);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:1825:                enc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:1830:                individualID, enc.getGenus(), enc.getSpecificEpithet(), userIndividualCache);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2348:        String genus, String specificEpithet,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2353:                if (mi.getGenus() == null || mi.getSpecificEpithet() == null ||
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2354:                    "".equals(mi.getSpecificEpithet()) || "".equals(mi.getGenus())) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2358:                    if (genus != null && specificEpithet != null && !"".equals(genus) &&
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2359:                        !"".equals(specificEpithet)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2361:                            specificEpithet.equals(mi.getSpecificEpithet())) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportAccess.java:539:        enc.setSpecificEpithet("novaeangliae");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportAccess.java:922:        // note non-capitalization of fin as opposed to Right Dors. Fin
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportExcelMetadata.java:726:        enc.setSpecificEpithet("geometricus");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/LightRestServlet.java:75:            "submitterOrganization", "genus", "specificEpithet", "dwcDateAdded", "modified"
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:425:                    System.out.println("=======> Specific Epithet is: " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:426:                        ann.findEncounter(myShepherd).getSpecificEpithet() + "    Genus is: " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:307:    // capitolizes the final directory in path
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:309:        String fixed = capitolizeLastFilepart(path);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:320:    private String capitolizeLastFilepart(String path) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:440:        String species = getString(row, "Encounter.specificEpithet");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:441:        if (Util.shouldReplace(species, mark.getSpecificEpithet()))
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:442:            mark.setSpecificEpithet(species);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/GetIndividualSearchGoogleMapsPoints.java:90:        grp2.addMember("sex").addMember("genus").addMember("specificEpithet").addMember(
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportMetadataExcel.java:183:                ExportColumn.newEasyColumn("Encounter.specificEpithet", columns);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportSocialNetwork.java:122:                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportSocialNetwork.java:124:                            (enc.getGenus() + " " + enc.getSpecificEpithet()));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportSocialNetwork.java:153:                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportSocialNetwork.java:156:                        Label lNumberx15 = new Label(14, count, enc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/GetEncounterSearchGoogleMapsPoints.java:188:                            enc.getSpecificEpithet());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/ExportColumn.java:118:        className = Util.capitolizeFirstLetterOnly(className);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/ExportColumn.java:129:        String getterName = "get" + Util.capitolizeFirstLetter(fieldName);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportShapefile.java:179:                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportShapefile.java:180:                        genusSpeciesString = enc.getGenus() + " " + enc.getSpecificEpithet();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportExcelFile.java:173:                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportExcelFile.java:175:                            (enc.getGenus() + " " + enc.getSpecificEpithet()));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportExcelFile.java:204:                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/export/EncounterSearchExportExcelFile.java:207:                        Label lNumberx15 = new Label(14, count, enc.getSpecificEpithet());

exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java | sed -n '360,430p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java | sed -n '200,225p;700,720p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '500,545p;680,745p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java | sed -n '430,480p'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 306ms:
   360	        System.out.println("OpenSearch.deleteAllPits() completed");
   361	    }
   362	
   363	    public void deletePit(String indexName)
   364	    throws IOException {
   365	        String pitId = PIT_CACHE.get(indexName);
   366	
   367	        if (pitId == null) return;
   368	        Request req = new Request("DELETE", "/_search/point_in_time");
   369	        JSONObject body = new JSONObject();
   370	        body.put("pit_id", pitId);
   371	        req.setJsonEntity(body.toString());
   372	        getRestResponse(req);
   373	        PIT_CACHE.remove(indexName);
   374	        System.out.println("OpenSearch.deletePit(" + indexName + ") [" + pitId + "] completed");
   375	    }
   376	
   377	    public JSONObject queryPit(String indexName, final JSONObject query, int numFrom, int pageSize,
   378	        String sort, String sortOrder)
   379	    throws IOException {
   380	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   381	        String pitId = createPit(indexName);
   382	        Request searchRequest = new Request("POST", "/_search?track_total_hits=true");
   383	        query.put("from", numFrom);
   384	        query.put("size", pageSize);
   385	        // "sort": [ {"@timestamp": {"order": "asc"}} ]
   386	        if (sort != null) {
   387	            JSONArray sortArr = new JSONArray();
   388	            if ((sortOrder == null) || !"desc".equals(sortOrder)) sortOrder = "asc";
   389	            sortArr.put(new JSONObject("{\"" + sort + "\":{\"order\":\"" + sortOrder + "\"}}"));
   390	            query.put("sort", sortArr);
   391	        }
   392	        JSONObject jpit = new JSONObject();
   393	        jpit.put("id", pitId);
   394	        jpit.put("keep_alive", SEARCH_PIT_TIME);
   395	        query.put("pit", jpit);
   396	        searchRequest.setJsonEntity(query.toString());
   397	        String rtn = null;
   398	        try {
   399	            rtn = getRestResponse(searchRequest);
   400	            pitRetry = 0;
   401	        } catch (ResponseException ex) {
   402	            System.out.println("queryPit() using pitId=" + pitId + " failed[" + pitRetry +
   403	                "] with: " + ex);
   404	            pitRetry++;
   405	            if (pitRetry > 5) {
   406	                ex.printStackTrace();
   407	                throw new IOException("queryPit() failed to POST query");
   408	            }
   409	            // we try again, but attempt to get new PIT
   410	            PIT_CACHE.remove(indexName);
   411	            return queryPit(indexName, query, numFrom, pageSize, sort, sortOrder);
   412	        }
   413	        return new JSONObject(rtn);
   414	    }
   415	
   416	    // just return the actual hit results
   417	    // note: each object in the array has _id but actual doc is in _source!!
   418	    public static JSONArray getHits(JSONObject queryResults) {
   419	        JSONArray failed = new JSONArray();
   420	
   421	        if (queryResults == null) return failed;
   422	        JSONObject outerHits = queryResults.optJSONObject("hits");
   423	        if (outerHits == null) {
   424	            System.out.println("could not find (outer) hits");
   425	            return failed;
   426	        }
   427	        JSONArray hits = outerHits.optJSONArray("hits");
   428	        if (hits == null) {
   429	            System.out.println("could not find hits");
   430	            return failed;

 succeeded in 238ms:
   500	        }
   501	
   502	        Shepherd shep = new Shepherd(context);
   503	        shep.setAction(ACTION_PREFIX + "runMatchProspects");
   504	        try {
   505	            shep.beginDBTransaction();
   506	            List<Annotation> anns = new ArrayList<Annotation>();
   507	            for (String annId : annotationIds) {
   508	                Annotation ann = shep.getAnnotation(annId);
   509	                if (ann != null) anns.add(ann);
   510	            }
   511	            if (anns.isEmpty()) {
   512	                Task task = Task.load(taskId, shep);
   513	                markTaskDroppedStale(shep, task, "annotations missing");
   514	                shep.commitDBTransaction();
   515	                return MlServiceJobOutcome.stale("annotations missing");
   516	            }
   517	
   518	            Task parent = Task.load(taskId, shep);
   519	            Task matchTask = (parent == null) ? new Task() : new Task(parent);
   520	            matchTask.setObjectAnnotations(anns);
   521	            matchTask.addParameter("mlServiceV2Match", true);
   522	            shep.getPM().makePersistent(matchTask);
   523	            // findMatchProspects returns false when the match config is not
   524	            // a vector config or matchConfig is null. Don't leave the match
   525	            // task without a terminal status — mark the parent task error.
   526	            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
   527	            if (!ran) {
   528	                matchTask.setStatus("error");
   529	                matchTask.setStatusDetailsAddError("INVALID_MATCH_CONFIG",
   530	                    "findMatchProspects rejected match config: " +
   531	                    (matchConfig == null ? "null" : matchConfig.toString()));
   532	                matchTask.setCompletionDateInMilliseconds();
   533	                // Update the parent task in this same transaction (parent is
   534	                // already loaded above) so the two updates commit atomically.
   535	                // Splitting across transactions risks leaving the parent
   536	                // "completed" if the second commit fails or the JVM dies.
   537	                if (parent != null) {
   538	                    markTaskError(parent, "INVALID_MATCH_CONFIG",
   539	                        "no usable vector match config");
   540	                }
   541	                shep.commitDBTransaction();
   542	                return MlServiceJobOutcome.validationError("INVALID_MATCH_CONFIG",
   543	                    "no usable vector match config");
   544	            }
   545	            String matchTaskId = matchTask.getId();
   680	        JSONObject matchConfig = null;
   681	        JSONArray identConfigs = iac.getIdentConfig(taxy);
   682	        if (identConfigs != null) {
   683	            for (int i = 0; i < identConfigs.length(); i++) {
   684	                JSONObject entry = identConfigs.optJSONObject(i);
   685	                if (entry == null) continue;
   686	                if (entry.optBoolean("default", false)
   687	                    && "vector".equals(entry.optString("pipeline_root", null))) {
   688	                    matchConfig = new JSONObject(entry.toString());
   689	                    break;
   690	                }
   691	            }
   692	        }
   693	        if (matchConfig == null) matchConfig = new JSONObject();
   694	        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
   695	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
   696	            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
   697	        }
   698	        return matchConfig;
   699	    }
   700	
   701	    private JSONObject inferMatchConfig(List<String> annotationIds) {
   702	        if (annotationIds == null || annotationIds.isEmpty()) return null;
   703	        Shepherd shep = new Shepherd(context);
   704	        shep.setAction(ACTION_PREFIX + "inferMatchConfig");
   705	        try {
   706	            shep.beginDBTransaction();
   707	            for (String annId : annotationIds) {
   708	                Annotation ann = shep.getAnnotation(annId);
   709	                if (ann == null) continue;
   710	                Embedding emb = ann.getAnEmbedding();
   711	                if (emb != null) {
   712	                    JSONObject config = new JSONObject();
   713	                    config.put("method", emb.getMethod());
   714	                    config.put("version", emb.getMethodVersion());
   715	                    config.put("pipeline_root", "vector");
   716	                    return config;
   717	                }
   718	            }
   719	            return null;
   720	        } finally {
   721	            shep.rollbackAndClose();
   722	        }
   723	    }
   724	
   725	    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
   726	        JSONObject mlConfig) {
   727	        JSONObject config = (matchConfig == null) ? new JSONObject()
   728	            : new JSONObject(matchConfig.toString());
   729	        if (embeddingSource != null) {
   730	            if (!Util.stringExists(config.optString("method", null))
   731	                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
   732	                config.put("method", embeddingSource.optString("embedding_model_id"));
   733	            }
   734	            if (!Util.stringExists(config.optString("version", null))
   735	                && Util.stringExists(embeddingSource.optString("embedding_model_version", null))) {
   736	                config.put("version", embeddingSource.optString("embedding_model_version"));
   737	            }
   738	        }
   739	        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
   740	            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
   741	            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
   742	        }
   743	        return config;
   744	    }
   745	

 succeeded in 295ms:
   200	            Base.opensearchSyncIndex(myShepherd, Occurrence.class, BACKGROUND_SLICE_SIZE);
   201	            Base.opensearchSyncIndex(myShepherd, MediaAsset.class, BACKGROUND_SLICE_SIZE);
   202	            System.out.println("OpenSearch background indexing finished.");
   203	        } catch (Exception ex) {
   204	            ex.printStackTrace();
   205	        } finally {
   206	            myShepherd.rollbackAndClose();
   207	            unsetActiveIndexingBackground();
   208	        }
   209	    }
   210	
   211	    public void createIndex(String indexName, JSONObject mapping)
   212	    throws IOException {
   213	        if (!isValidIndexName(indexName)) throw new IOException("invalid index name: " + indexName);
   214	        IndexSettings indexSettings = null;
   215	        // a little hacky but meh
   216	        if (indexName.equals("annotation")) {
   217	            // also? "knn.algo_param.ef_search": 100
   218	            indexSettings = IndexSettings.of(is -> is.knn(true));
   219	        }
   220	        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder().index(
   221	            indexName).settings(indexSettings).build();
   222	
   223	        client.indices().create(createIndexRequest);
   224	        // TODO fold in this settings-change into indexSettings above
   225	        indexClose(indexName);
   700	                    String id = hits.optJSONObject(i).optString("_id", "__FAIL__");
   701	                    Long version = hits.optJSONObject(i).optJSONObject("_source").optLong("version",
   702	                        -999L);
   703	                    versions.put(id, version);
   704	                }
   705	                // continue with next scroll...
   706	                query = new JSONObject();
   707	                query.put("_scroll_id", res.optString("_scroll_id", "__FAIL__"));
   708	                res = queryRawScroll(query);
   709	            }
   710	        }
   711	        // this is a little hacky, but allows us to page thru results enough to cover what we have
   712	        if (versions.size() > 10000) {
   713	            putSettings(indexName,
   714	                new JSONObject("{\"index.max_result_window\": " +
   715	                Math.round(1.2 * versions.size()) + "}"));
   716	        }
   717	        return versions;
   718	    }
   719	
   720	    public JSONObject getSettings(final String indexName)

 succeeded in 245ms:
   430	                    }
   431	                    System.out.println("matchingAlgorithms2: " + newOpts.toString());
   432	                    if (newOpts.size() > 0) {
   433	                        opts = newOpts;
   434	                        System.out.println("Swapping opts for newOpts!!");
   435	                    }
   436	                }
   437	            }
   438	            if ((opts == null) || (opts.size() < 1)) continue; // no ID for this iaClass.
   439	            // just one IA class, one algorithm case
   440	            if (opts.size() == 1 && annotsByIaClass.size() == 1) {
   441	                newTaskParams.put("ibeis.identification",
   442	                    ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
   443	                topTask.setParameters(newTaskParams);
   444	                tasks.add(topTask); // topTask will be used as *the*(only) task -- no children
   445	            } else {
   446	                for (int i = 0; i < opts.size(); i++) {
   447	                    Task t = new Task();
   448	                    t.setObjectAnnotations(annsOneIAClass);
   449	                    newTaskParams.put("ibeis.identification",
   450	                        ((opts.get(i) == null) ? "DEFAULT" : opts.get(i)));                                        // overwrites each time
   451	                    t.setParameters(newTaskParams);
   452	                    topTask.addChild(t);
   453	                    tasks.add(t);
   454	                }
   455	            }
   456	            newTaskParams.put("fastlane", fastlane);
   457	            if (fastlane) newTaskParams.put("lane", "fast");
   458	            myShepherd.storeNewTask(topTask);
   459	
   460	            // these are re-used in every task
   461	            JSONArray annArr = new JSONArray();
   462	            for (Annotation ann : annsOneIAClass) {
   463	                annArr.put(ann.getId());
   464	            }
   465	            JSONObject aj = new JSONObject();
   466	            aj.put("annotationIds", annArr);
   467	            String baseUrl = getBaseURL(context);
   468	            for (int i = 0; i < opts.size(); i++) {
   469	                // if this is a vector-based matching option, this will just do the job and be done
   470	                if (Embedding.findMatchProspects(opts.get(i), tasks.get(i), myShepherd)) continue;
   471	                JSONObject qjob = new JSONObject();
   472	                qjob.put("identify", aj);
   473	                qjob.put("taskId", tasks.get(i).getId());
   474	                qjob.put("__context", context);
   475	                qjob.put("__baseUrl", baseUrl);
   476	                if (opts.get(i) != null) qjob.put("opt", opts.get(i));
   477	                boolean sent = false;
   478	                try {
   479	                    if (fastlane) {
   480	                        // if fastlane and a smaller, bespoke request, get this into the faster queue

exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '160,225p;250,285p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '430,505p;545,570p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc 'rg -n "runMatchProspects|matchingSetFilter|mlServiceV2Match|matchConfig|extract|pipeline" /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet' in /mnt/c/claude-skills/kb/ml-service
 succeeded in 231ms:
   160	        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
   161	        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
   162	            matchConfig);
   163	        if (matchOutcome != null) return matchOutcome;
   164	        return MlServiceJobOutcome.ok(persisted.annotationIds);
   165	    }
   166	
   167	    private DetectionContext loadDetectionContext(String taxonomyString, String taskId,
   168	        String encounterId, String maId) {
   169	        Shepherd shep = new Shepherd(context);
   170	        shep.setAction(ACTION_PREFIX + "loadDetectionContext");
   171	        try {
   172	            FeatureType.initAll(shep);
   173	            shep.beginDBTransaction();
   174	            MediaAsset ma = shep.getMediaAsset(maId);
   175	            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
   176	            Task task = Task.load(taskId, shep);
   177	
   178	            String staleReason = detectionStaleReason(ma, enc, encounterId);
   179	            if (staleReason != null) {
   180	                markTaskDroppedStale(shep, task, staleReason);
   181	                shep.commitDBTransaction();
   182	                return DetectionContext.done(MlServiceJobOutcome.stale(staleReason));
   183	            }
   184	
   185	            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString, enc);
   186	            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
   187	            if (configs == null) {
   188	                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
   189	                markTaskCompleted(task);
   190	                shep.commitDBTransaction();
   191	                return DetectionContext.done(MlServiceJobOutcome.stale("pending-species"));
   192	            }
   193	
   194	            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
   195	                markTaskError(task, "INVALID",
   196	                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
   197	                shep.commitDBTransaction();
   198	                return DetectionContext.done(MlServiceJobOutcome.validationError("INVALID",
   199	                    "_mlservice_conf missing predict_model_id"));
   200	            }
   201	
   202	            URL webUrl = ma.webURL();
   203	            if (webUrl == null) {
   204	                markTaskError(task, "INVALID_IMAGE_URI",
   205	                    "MediaAsset " + maId + " has no webURL");
   206	                shep.commitDBTransaction();
   207	                return DetectionContext.done(MlServiceJobOutcome.validationError(
   208	                    "INVALID_IMAGE_URI", "MediaAsset " + maId + " has no webURL"));
   209	            }
   210	
   211	            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
   212	            shep.commitDBTransaction();
   213	            return new DetectionContext(webUrl.toString(),
   214	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
   215	                configs.matchConfig);
   216	        } finally {
   217	            shep.rollbackAndClose();
   218	        }
   219	    }
   220	
   221	    private ExtractionContext loadExtractionContext(String taxonomyString, String taskId,
   222	        String annId) {
   223	        Shepherd shep = new Shepherd(context);
   224	        shep.setAction(ACTION_PREFIX + "loadExtractionContext");
   225	        try {
   250	            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
   251	                markTaskCompleted(task);
   252	                shep.commitDBTransaction();
   253	                return ExtractionContext.done(MlServiceJobOutcome.stale("embedding-exists"));
   254	            }
   255	
   256	            URL webUrl = ma.webURL();
   257	            if (webUrl == null) {
   258	                markTaskError(task, "INVALID_IMAGE_URI",
   259	                    "Annotation " + annId + " media asset has no webURL");
   260	                shep.commitDBTransaction();
   261	                return ExtractionContext.done(MlServiceJobOutcome.validationError(
   262	                    "INVALID_IMAGE_URI", "Annotation " + annId + " media asset has no webURL"));
   263	            }
   264	            int[] bbox = ann.getBbox();
   265	            if (bbox == null || bbox.length != 4) {
   266	                markTaskError(task, "INVALID_BBOX", "Annotation " + annId + " has no bbox");
   267	                shep.commitDBTransaction();
   268	                return ExtractionContext.done(MlServiceJobOutcome.validationError("INVALID_BBOX",
   269	                    "Annotation " + annId + " has no bbox"));
   270	            }
   271	
   272	            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
   273	            shep.commitDBTransaction();
   274	            return new ExtractionContext(webUrl.toString(),
   275	                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
   276	                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
   277	        } finally {
   278	            shep.rollbackAndClose();
   279	        }
   280	    }
   281	
   282	    private MlServiceJobOutcome finalizeZeroDetections(String maId, String taskId) {
   283	        Shepherd shep = new Shepherd(context);
   284	        shep.setAction(ACTION_PREFIX + "finalizeZeroDetections");
   285	        try {

 succeeded in 231ms:
   430	        } finally {
   431	            shep.rollbackAndClose();
   432	        }
   433	    }
   434	
   435	    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
   436	        JSONObject matchConfig) {
   437	        // Initial invocation: attempt=1, firstDeferredAt=null (the
   438	        // gate stamps `now` so age-out is measured from this first
   439	        // call, not from later re-fires).
   440	        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, 1, null);
   441	    }
   442	
   443	    /**
   444	     * Shared body for the initial {@link #waitAndRunMatch} call and
   445	     * the re-gated {@link #runDeferredMatch} path. Drives the
   446	     * {@link MatchVisibilityGate}: READY → run match; DEFER → publish
   447	     * a deferred-match job through the publisher; GIVE_UP → log WARN
   448	     * and run match against whatever is visible (partial results are
   449	     * better than silently no match task; Codex round-2 #2).
   450	     *
   451	     * <p>(Empty-match-prospects design Track 2 C11.)</p>
   452	     */
   453	    private MlServiceJobOutcome waitAndRunMatchInternal(List<String> annotationIds,
   454	        String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {
   455	        MatchVisibilityGate.GateOutcome gate = visibilityGate.gateForBatch(
   456	            annotationIds, taskId, matchConfig, attempt, firstDeferredAt);
   457	        switch (gate.kind) {
   458	          case READY:
   459	            return runMatchProspects(annotationIds, taskId, matchConfig);
   460	          case DEFER:
   461	            enqueueDeferredMatch(annotationIds, taskId, matchConfig, gate);
   462	            return MlServiceJobOutcome.ok(annotationIds);
   463	          case GIVE_UP:
   464	          default:
   465	            System.out.println(
   466	                "WARN: MatchVisibilityGate aged out for task " + taskId +
   467	                " after attempt=" + gate.attempt + " elapsed=" +
   468	                gate.elapsedMillis + "ms reason=" + gate.reason +
   469	                "; running match against current visible corpus");
   470	            return runMatchProspects(annotationIds, taskId, matchConfig);
   471	        }
   472	    }
   473	
   474	    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
   475	        if (jobData == null) {
   476	            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
   477	        }
   478	        List<String> annotationIds = jsonArrayToStringList(jobData.optJSONArray("annotationIds"));
   479	        String taskId = jobData.optString("taskId", null);
   480	        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
   481	        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
   482	        // Carry forward attempt + firstDeferredAt so age-out is
   483	        // measured by elapsed wall-clock from the original DEFER, not
   484	        // by attempt count (Codex round-4 OQ #1).
   485	        int attempt = jobData.optInt("attempt", 2);
   486	        Long firstDeferredAt = jobData.has("firstDeferredAt")
   487	            ? Long.valueOf(jobData.optLong("firstDeferredAt")) : null;
   488	        // Re-gate; deferred match earns the same protection as the
   489	        // initial call (Codex round-2 Major: don't degrade back to
   490	        // today's bug on the first deferral).
   491	        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
   492	            attempt, firstDeferredAt);
   493	    }
   494	
   495	    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
   496	        JSONObject matchConfig) {
   497	        if (annotationIds == null || annotationIds.isEmpty()) {
   498	            markTaskCompleted(taskId);
   499	            return MlServiceJobOutcome.ok(new ArrayList<String>());
   500	        }
   501	
   502	        Shepherd shep = new Shepherd(context);
   503	        shep.setAction(ACTION_PREFIX + "runMatchProspects");
   504	        try {
   505	            shep.beginDBTransaction();
   545	            String matchTaskId = matchTask.getId();
   546	            shep.commitDBTransaction();
   547	            shep.rollbackAndClose();  // close BEFORE PairX enrichment (Track 2 C13)
   548	            // Phase 4 (C13): PairX inspection-image enrichment. The
   549	            // MatchResult + prospects are already persisted with
   550	            // null inspection MediaAssets; the enricher fills them in
   551	            // out-of-transaction via a Phase A/B/C flow per prospect.
   552	            // Per-prospect failure is non-blocking — UI render works
   553	            // either way, just without the inspection image.
   554	            enrichPairxAssetsForMatchTask(matchTaskId);
   555	            return MlServiceJobOutcome.ok(annotationIds);
   556	        } catch (Exception ex) {
   557	            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
   558	            return MlServiceJobOutcome.persistError("MATCH", ex.getMessage());
   559	        } finally {
   560	            shep.rollbackAndClose();
   561	        }
   562	    }
   563	
   564	    /**
   565	     * Phase 4: drive {@link MatchInspectionPairxEnricher} for every
   566	     * MatchResult attached to a child of {@code matchTaskId}. Runs
   567	     * after the main runMatchProspects transaction has closed, so the
   568	     * PairX HTTP work doesn't hold a Shepherd. (Empty-match-prospects
   569	     * design Track 2 C13.)
   570	     */

 succeeded in 597ms:
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:106:            response = client.pipeline(det.apiEndpoint, det.imageUri, det.mlConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:124:        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:127:            matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:146:            response = client.extract(ext.apiEndpoint, ext.imageUri, ext.bbox, ext.theta,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:160:        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:162:            matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:215:                configs.matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:250:            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:276:                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:410:            JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:411:            if (hasEmbeddingForMatchConfig(ann, matchConfig)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            markTaskError(taskId, "PERSIST", "extraction persist failed: " + ex.getMessage());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:436:        JSONObject matchConfig) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:440:        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, 1, null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:454:        String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:456:            annotationIds, taskId, matchConfig, attempt, firstDeferredAt);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:459:            return runMatchProspects(annotationIds, taskId, matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:461:            enqueueDeferredMatch(annotationIds, taskId, matchConfig, gate);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:470:            return runMatchProspects(annotationIds, taskId, matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:480:        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:481:        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:491:        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:495:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:496:        JSONObject matchConfig) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:503:        shep.setAction(ACTION_PREFIX + "runMatchProspects");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:521:            matchTask.addParameter("mlServiceV2Match", true);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:524:            // a vector config or matchConfig is null. Don't leave the match
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:526:            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:531:                    (matchConfig == null ? "null" : matchConfig.toString()));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:567:     * after the main runMatchProspects transaction has closed, so the
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:674:        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:675:        return new ConfigPair(mlConfig, matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:680:        JSONObject matchConfig = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:687:                    && "vector".equals(entry.optString("pipeline_root", null))) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:688:                    matchConfig = new JSONObject(entry.toString());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:693:        if (matchConfig == null) matchConfig = new JSONObject();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:694:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:696:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:698:        return matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:715:                    config.put("pipeline_root", "vector");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:725:    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:727:        JSONObject config = (matchConfig == null) ? new JSONObject()
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:728:            : new JSONObject(matchConfig.toString());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:824:        JSONObject matchConfig, MatchVisibilityGate.GateOutcome gate) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:835:        if (matchConfig != null) payload.put("matchConfig", matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:895:    private static boolean hasEmbeddingForMatchConfig(Annotation ann, JSONObject matchConfig) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:897:        if (matchConfig == null) return ann.numberEmbeddings() > 0;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:898:        String method = matchConfig.optString("method", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:899:        String version = matchConfig.optString("version", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:911:        final JSONObject matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:913:        ConfigPair(JSONObject mlConfig, JSONObject matchConfig) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:915:            this.matchConfig = matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:923:        final JSONObject matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:927:            JSONObject matchConfig) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:931:            this.matchConfig = matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:939:            this.matchConfig = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:952:        final JSONObject matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:958:            JSONObject matchConfig, double[] bbox, double theta) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:962:            this.matchConfig = matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:972:            this.matchConfig = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterForm.java:899:                            tp.put("matchingSetFilter", mf);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:143:        JSONObject matchingSetFilter = new JSONObject();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:227:            // we might grow matchingSetFilter stuff later, but for now we only have locationIds from ui
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:239:                matchingSetFilter.put("locationIds", locArr);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:523:                                        matchingSetFilter);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:628:                initiateIA(dupId, skipIdentification, encAssets, matchingSetFilter);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:797:        JSONObject matchingSetFilter) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:814:            if (!skipIdent) taskParams.put("matchingSetFilter", matchingSetFilter);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:878:        // set it properly when IA pipeline is finished
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:887:            if (iaSummary.optBoolean("pipelineStarted", false)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:888:                if (iaSummary.optBoolean("pipelineComplete", false)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BulkImport.java:892:                    jt.put("status", "processing-pipeline");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/BaseObject.java:337:                // stuff can be done (e.g. IA pipeline)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:741:        return getParameters().optJSONObject("matchingSetFilter");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:768:        // we set HotSpotter if pipeline_root is not set here
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:769:        if (conf != null) rtn.put("name", conf.optString("pipeline_root", "HotSpotter"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:839:        // matchingSetFilter, legacy WBIA log-based MR generation). The v2
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:848:            rtn.put("matchingSetFilter", getMatchingSetFilter());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:853:        // pipeline ran successfully. Legacy WBIA results still rely on
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterAddSpotFile.java:27: * This servlet allows the user to upload an extracted, processed patterning file that corresponds to a previously uploaded set of spots. This file is
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterAddSpotFile.java:28: * then used for visualization of the extracted pattern and visualizations of potentially matched spots.
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterAddSpotFile.java:78:                    fileName = "extractRight" + encounterNumber + ".jpg";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterAddSpotFile.java:80:                    fileName = "extract" + encounterNumber + ".jpg";
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterAddSpotFile.java:158:                                    ("extract" + sideAddition + encounterNumber + ".jpg"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:648:     * <p>(Empty-match-prospects design Track 1 C4: extracted from
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/EncounterSetPatterningPassport.java:77:        // extract the uploaded files as an Enumeration
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2065:            System.out.println("extracted int for line " + i);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2096:            System.out.println("extracted long for line " + i);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/StandardImport.java:2132:            System.out.println("extracted double for line " + i);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportTask.java:642:        boolean pipelineStarted = false;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportTask.java:681:            pipelineStarted = true;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportTask.java:718:            pipelineStarted = true;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportTask.java:734:        pj.put("pipelineStarted", pipelineStarted);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportTask.java:735:        boolean pipelineComplete = ((ds.equals("complete") || ds.equals("skipped")) &&
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportTask.java:737:        pj.put("pipelineComplete", pipelineComplete);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/importer/ImportExcelMetadata.java:536:            out.println("!!! Sheet was not successfully extracted !!!");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:106:            response = client.pipeline(det.apiEndpoint, det.imageUri, det.mlConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:124:        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:127:            matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:146:            response = client.extract(ext.apiEndpoint, ext.imageUri, ext.bbox, ext.theta,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:160:        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:162:            matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:215:                configs.matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:250:            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:276:                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:410:            JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:411:            if (hasEmbeddingForMatchConfig(ann, matchConfig)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:427:            markTaskError(taskId, "PERSIST", "extraction persist failed: " + ex.getMessage());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:436:        JSONObject matchConfig) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:440:        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, 1, null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:454:        String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:456:            annotationIds, taskId, matchConfig, attempt, firstDeferredAt);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:459:            return runMatchProspects(annotationIds, taskId, matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:461:            enqueueDeferredMatch(annotationIds, taskId, matchConfig, gate);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:470:            return runMatchProspects(annotationIds, taskId, matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:480:        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:481:        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:491:        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:495:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:496:        JSONObject matchConfig) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:503:        shep.setAction(ACTION_PREFIX + "runMatchProspects");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:521:            matchTask.addParameter("mlServiceV2Match", true);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:524:            // a vector config or matchConfig is null. Don't leave the match
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:526:            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:531:                    (matchConfig == null ? "null" : matchConfig.toString()));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:567:     * after the main runMatchProspects transaction has closed, so the
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:674:        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:675:        return new ConfigPair(mlConfig, matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:680:        JSONObject matchConfig = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:687:                    && "vector".equals(entry.optString("pipeline_root", null))) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:688:                    matchConfig = new JSONObject(entry.toString());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:693:        if (matchConfig == null) matchConfig = new JSONObject();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:694:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:696:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:698:        return matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:715:                    config.put("pipeline_root", "vector");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:725:    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:727:        JSONObject config = (matchConfig == null) ? new JSONObject()
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:728:            : new JSONObject(matchConfig.toString());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:824:        JSONObject matchConfig, MatchVisibilityGate.GateOutcome gate) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:835:        if (matchConfig != null) payload.put("matchConfig", matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:895:    private static boolean hasEmbeddingForMatchConfig(Annotation ann, JSONObject matchConfig) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:897:        if (matchConfig == null) return ann.numberEmbeddings() > 0;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:898:        String method = matchConfig.optString("method", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:899:        String version = matchConfig.optString("version", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:911:        final JSONObject matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:913:        ConfigPair(JSONObject mlConfig, JSONObject matchConfig) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:915:            this.matchConfig = matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:923:        final JSONObject matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:927:            JSONObject matchConfig) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:931:            this.matchConfig = matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:939:            this.matchConfig = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:952:        final JSONObject matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:958:            JSONObject matchConfig, double[] bbox, double theta) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:962:            this.matchConfig = matchConfig;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:972:            this.matchConfig = null;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:634:        // extraction lifecycle (Phases 1-5) and the deferred-match path
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/MediaAssetCreate.java:122:                        tp.put("matchingSetFilter", mf);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/ProjectIA.java:86:                            tp.put("matchingSetFilter", mf);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:17: * HTTP-only wrapper around ml-service ({@code /pipeline/} and {@code /extract/}
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:69:     * POSTs to {@code apiEndpoint/pipeline/} with the predict/classify/extract/
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:82:    public JSONObject pipeline(String apiEndpoint, String imageUri, JSONObject config)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:85:        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/pipeline/"),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:92:     * POSTs to {@code apiEndpoint/extract/}. Used for manual annotations
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:95:     * @throws IAException same contract as {@link #pipeline}.
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:97:    public JSONObject extract(String apiEndpoint, String imageUri, double[] bbox,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:101:        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/extract/"),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:125:            if (config.has("extract_model_id"))
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:126:                p.put("extract_model_id", config.opt("extract_model_id"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:137:        if (config != null && config.has("extract_model_id")) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:138:            p.put("extract_model_id", config.opt("extract_model_id"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:152:            throw new IAException("INVALID", "/pipeline/ returned null", false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:155:                "/pipeline/ returned success=false: " + response, false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:159:                "/pipeline/ response missing 'results' array: " + response, false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:167:                    "/pipeline/ results[" + i + "] is not an object", false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:174:                    "/pipeline/ results[" + i + "] missing theta", false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:178:                    "/pipeline/ results[" + i + "] theta non-finite", false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:186:            throw new IAException("INVALID", "/extract/ returned null", false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:189:                "/extract/ returned success=false: " + response, false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:197:                "/pipeline/ results[" + idx + "] bbox must be a 4-element array", false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:202:                    "/pipeline/ results[" + idx + "] bbox[" + j + "] non-finite", false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceClient.java:206:                "/pipeline/ results[" + idx + "] bbox width/height must be >= 1", false, false);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:19: * the matchConfig provides them) have an EMBEDDING row for the
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:197:    // true if all annotations "are done" from (trying to) extract embeddings
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MLService.java:305:            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/InterconnectSubmitSpots.java:192:                            "-side image that you extracted the spot data from.</strong>");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/InterconnectSubmitSpots.java:225:                            "-side image that you extracted the spot data from.</strong>");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IAUtils.java:28:                tp.put("matchingSetFilter", mf);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:82:        JSONObject matchConfig,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:102:        // Derive method/methodVersion from matchConfig using the same
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:117:        String method = (matchConfig == null) ? null
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:118:            : matchConfig.optString("method", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:119:        String methodVersion = (matchConfig == null) ? null
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:120:            : matchConfig.optString("version", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:121:        if (!Util.stringExists(method) && matchConfig != null) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:122:            String[] mv = MLService.getMethodValues(matchConfig);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGate.java:105:     * @param matchConfig          the ml-service _id_conf entry,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGate.java:119:        JSONObject matchConfig,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:567:        return params.optJSONObject("matchingSetFilter");
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:160:     * Phase B: POST to {@code /explain/} and extract the base64 image.
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:160:        // If the species' _id_conf.default.pipeline_root is "vector" AND
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java:227:     * vector pipeline. Each MediaAsset gets its own child Task under
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:29: * This servlet allows the user to upload an extracted, processed patterning file that corresponds to a previously uploaded set of spots. This file is
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:30: * then used for visualization of the extracted pattern and visualizations of potentially matched spots.
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:96:	            Encounter.subdir(encId) + "/extract" + (rightSide ? "Right" : "") + encId + ".jpg"));

exec
/bin/bash -lc "rg -n \"Task\\(|public Task|Task\\(Task\" /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java | sed -n '1,170p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java | sed -n '130,235p'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 249ms:
45:    public Task() {
48:    public Task(String id) {
54:    public Task(Task p) {
275:    public Task getParent() {
306:    public Task getRootTask() {
308:        return parent.getRootTask();
311:    public Task deepContains(Annotation ann) {
321:    public Task deepContains(MediaAsset ma) {
576:            Task r = t.getRootTask();

 succeeded in 249ms:
     1	/*
     2	    an instance of an ia.Task can be persisted and represents the state of that task
     3	    ... replacement (and improvement upon, hopefully) messy identity/IdentityServiceLog.java
     4	 */
     5	package org.ecocean.ia;
     6	
     7	import java.util.ArrayList;
     8	import java.util.Collection;
     9	import java.util.Collections;
    10	import java.util.HashMap;
    11	import java.util.List;
    12	import java.util.Map;
    13	import java.util.Set;
    14	import javax.jdo.Query;
    15	import org.apache.commons.lang3.builder.ToStringBuilder;
    16	import org.ecocean.Annotation;
    17	import org.ecocean.Encounter;
    18	import org.ecocean.media.MediaAsset;
    19	import org.ecocean.shepherd.core.Shepherd;
    20	import org.ecocean.User;
    21	import org.ecocean.Util;
    22	import org.joda.time.DateTime;
    23	import org.json.JSONArray;
    24	import org.json.JSONObject;
    25	
    26	import org.ecocean.identity.IdentityServiceLog;
    27	
    28	public class Task implements java.io.Serializable {
    29	    public static long TIMEOUT_INACTIVE_MILLIS = 7l * 24l * 60l * 60l * 1000l;
    30	    private String id = null;
    31	    private long created = -1;
    32	    private long modified = -1;
    33	    // private List<Object> objects = null;  //in some perfect world i could figure out how to persist this.  :/  oh, for a wb base class.
    34	    private List<MediaAsset> objectMediaAssets = null;
    35	    private List<Annotation> objectAnnotations = null;
    36	    private Task parent = null;
    37	    private List<Task> children = null;
    38	    private String parameters = null;
    39	    private String status;
    40	    // general use, but notably will contain error details when status=error
    41	    private String statusDetails = null;
    42	    private Long completionDateInMilliseconds;
    43	    private String queueResumeMessage;
    44	
    45	    public Task() {
    46	        this(Util.generateUUID());
    47	    }
    48	    public Task(String id) {
    49	        this.id = id;
    50	        created = System.currentTimeMillis();
    51	        modified = System.currentTimeMillis();
    52	    }
    53	    // makes a child of the passed Task (and inherits the parameters!!)
    54	    public Task(Task p) {
    55	        this();
    56	        this.setParameters(p.getParameters());
    57	        this.setParent(p);
    58	    }
    59	
    60	    public String getId() {
    61	        return id;
    62	    }
    63	
    64	    public long getCreatedLong() {
    65	        return created;
    66	    }
    67	
    68	    public long getModifiedLong() {
    69	        return modified;
    70	    }
    71	
    72	    public long timeInactive() {
    73	        long now = System.currentTimeMillis();
    74	
    75	        if (modified > 0) return (now - modified);
    76	        if (created > 0) return (now - created);
    77	        // weird or inconclusive:
    78	        return -1l;
    79	    }
    80	
    81	    public boolean timedOutDueToInactivity() {
    82	        return (timeInactive() > TIMEOUT_INACTIVE_MILLIS);
    83	    }
    84	
    85	    public boolean statusInEndState() {
    86	        if ("completed".equals(status)) return true;
    87	        if ("error".equals(status)) return true;
    88	        // ml-service migration v2: "dropped-stale" is terminal — the task's
    89	        // target was deleted before the queued job ran. Neither success nor
    90	        // error; the inactivity-timeout watchdog must not flip it to error.
    91	        if ("dropped-stale".equals(status)) return true;
    92	        return false;
    93	    }
    94	
    95	    public void setModified() {
    96	        modified = System.currentTimeMillis();
    97	    }
    98	
    99	    public boolean canUserAccess(User user, Shepherd myShepherd) {
   100	        if (user == null) return false;
   101	        if (user.isAdmin(myShepherd)) return true;
   102	        Encounter enc = null;
   103	        // if we have annotations, use first to determine encounter
   104	        if (this.countObjectAnnotations() > 0) {
   105	            enc = this.getObjectAnnotations().get(0).findEncounter(myShepherd);
   106	        } else if (this.countObjectMediaAssets() > 0) { // no annots, use asset instead
   107	            MediaAsset ma = this.getObjectMediaAssets().get(0);
   108	            // we iterate over all annots on this asset til we find an encounter.
   109	            // it might be better to find *all* encounters and return access based on each;
   110	            // however the main use for userHasAccess() revolves around *annotation-based* tasks (matching)
   111	            // so i think this means asset-based access of tasks will be rare or unused anyway
   112	            for (Annotation ann : ma.getAnnotations()) {
   113	                if (ann != null) enc = ann.findEncounter(myShepherd);
   114	                if (enc != null) break;
   115	            }
   116	        }
   117	        if (enc == null) return false;
   118	        if (enc.isPubliclyReadable()) return true;
   119	        // note: we also have enc.canUserView() and enc.canUserEdit() !!! :(
   120	        return enc.canUserAccess(user, myShepherd.getContext());
   121	    }
   122	
   123	/*
   124	    // not really convinced these are accurate enough to use
   125	    //   actual computation of these things is complicated
   126	    //   leaving these for future potential exploration, if needed.
   127	
   128	    public boolean isTypeDetection() {
   129	        if (this.hasObjectMediaAssets()) return true;
   130	        if (this.hasObjectAnnotations()) return false;
   131	        if (this.parameters == null) return false;
   132	        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
   133	        if (this.getParameters().optBoolean("ibeis.detection", false)) return true;
   134	        return false;
   135	    }
   136	    public boolean isTypeIdentification() {
   137	        if (this.isTypeDetection()) return false;  // we trust this a little more if (this.hasObjectAnnotations()) return true;
   138	        if (this.parameters == null) return false;
   139	        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
   140	        return false;
   141	    }
   142	
   143	    public boolean initiatedWithDetection() {
   144	        if (this.parameters == null) return false;
   145	        return this.getParameters().optBoolean("ibeis.detection", false);
   146	    }
   147	    public boolean initiatedWithIdentification() {
   148	        if (this.parameters == null) return false;  // not sure how i feel about this return !this.getParameters().optBoolean("skipIdent", false);
   149	    }
   150	 */
   151	    public int countObjectMediaAssets() {
   152	        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
   153	    }
   154	
   155	    public int countObjectAnnotations() {
   156	        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
   157	    }
   158	
   159	    public int countObjects() {
   160	        return countObjectMediaAssets() + countObjectAnnotations();
   161	    }
   162	
   163	    // not sure if these two are mutually exclusive by definition, but lets assume not (wtf would that even mean? i dunno)
   164	    public boolean hasObjectMediaAssets() {
   165	        return (countObjectMediaAssets() > 0);
   166	    }
   167	
   168	    public boolean hasObjectAnnotations() {
   169	        return (countObjectAnnotations() > 0);
   170	    }

 succeeded in 282ms:
   130	        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask, -1);
   131	    }
   132	
   133	    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
   134	        Taxonomy taxy, final Task parentTask, int tweetAssetId) {
   135	        System.out.println("intakeMediaAssetsOneSpecies called for " + mas.size() +
   136	            " media assets:");
   137	        handleMissingAcmids(mas, myShepherd);
   138	        for (MediaAsset ma : mas) {
   139	            System.out.println("intakeMediaAssetsOneSpecies incl. ma " + ma.getId());
   140	            System.out.println("acmid is: " + ma.getAcmId());
   141	        }
   142	        JSONArray maArr = new JSONArray();
   143	        for (MediaAsset ma : mas) {
   144	            maArr.put(ma.getId());
   145	        }
   146	        System.out.println("intakeMediaAssetsOneSpecies constructed maArr " + maArr.toString());
   147	
   148	        Task topTask = new Task();
   149	        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
   150	        topTask.setObjectMediaAssets(mas);
   151	        myShepherd.storeNewTask(topTask);
   152	
   153	        String context = myShepherd.getContext();
   154	        String baseUrl = getBaseURL(context);
   155	
   156	        // Ia configs are keyed off taxonomies
   157	        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
   158	
   159	        // Migration plan v2 §commit #10b: routing reroute.
   160	        // If the species' _id_conf.default.pipeline_root is "vector" AND
   161	        // _mlservice_conf is configured, route per-asset through the
   162	        // MlServiceProcessor lifecycle. Otherwise fall through to the legacy
   163	        // WBIA path below — production deployments without _mlservice_conf
   164	        // see no behavior change at all.
   165	        //
   166	        // Per-asset CHILD tasks under topTask (vs v1's shared topTask) so
   167	        // child finalization is local; no first-finisher-wins. The topTask
   168	        // remains as the aggregator for the caller contract (and so legacy
   169	        // summary code that reads topTask.objectMediaAssets keeps working).
   170	        if (iaConfig != null && taxy != null &&
   171	            iaConfig.getActiveMlServiceConfigs(taxy) != null) {
   172	            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
   173	                context, baseUrl);
   174	        }
   175	
   176	        // what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
   177	        JSONObject dj = new JSONObject();
   178	        dj.put("mediaAssetIds", maArr);
   179	        // mimicking intakeAnnotations, we assume the first mediaAsset is representative of all of them wrt Taxonomies, configs etc.
   180	        int numDetectAlgos = iaConfig.numDetectionAlgos(taxy);
   181	        Boolean[] sent = new Boolean[numDetectAlgos];
   182	        for (int i = 0; i < numDetectAlgos; i++) {
   183	            // task for this job (only create new (child) tasks if multiple detect algos)
   184	            Task task = (numDetectAlgos == 1) ? topTask : new Task();
   185	            task.setObjectMediaAssets(mas);
   186	            task.setParameters(topTask.getParameters());
   187	
   188	            JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl, i);
   189	            task.addParameter("detectArgs", detectArgs);
   190	
   191	            String detectionUrl = iaConfig.getDetectionUrl(taxy, i);
   192	            task.addParameter("__detect_url", detectionUrl);
   193	
   194	            JSONObject qjob = new JSONObject();
   195	            qjob.put("detect", dj);
   196	            qjob.put("__detect_args", detectArgs);
   197	            qjob.put("__detect_url", detectionUrl);
   198	            // task is queued here
   199	            qjob.put("taskId", topTask.getId());
   200	            qjob.put("__context", context);
   201	            qjob.put("__baseUrl", baseUrl);
   202	            System.out.println("intakeMediaAssetsOneSpecies about to add additionalArgs to query");
   203	            if (tweetAssetId != -1) {
   204	                qjob.put("tweetAssetId", tweetAssetId);
   205	            }
   206	            System.out.println(
   207	                "intakeMediaAssetsOneSpecies successfully added additionalArgs to query");
   208	            sent[i] = false;
   209	            try {
   210	                // job is queued here
   211	                sent[i] = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
   212	                    qjob.toString());
   213	            } catch (java.io.IOException iox) {
   214	                System.out.println("ERROR: IA.intakeMediaAssets() hit exception on taxonomy " +
   215	                    taxy.toString() + ", detectArgs = " + detectArgs.toString());
   216	                System.out.println("ERROR: IA.intakeMediaAssets() addToQueue() threw " +
   217	                    iox.toString());
   218	            }
   219	        }
   220	        System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() +
   221	            " assets; queued? = " + sent + "; " + topTask);
   222	        return topTask;
   223	    }
   224	
   225	    /**
   226	     * ml-service migration v2 §commit #10b: per-asset job enqueue for the
   227	     * vector pipeline. Each MediaAsset gets its own child Task under
   228	     * topTask; each emits a {@code mlServiceV2:true} payload to the
   229	     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
   230	     * picks them up via the IAGateway dispatcher (commit #10a).
   231	     *
   232	     * <p>Per-asset child Tasks avoid v1's first-finisher-wins on the shared
   233	     * topTask. The topTask itself remains as the aggregator that holds the
   234	     * full MediaAsset list for caller-side summary code.</p>
   235	     *

exec
/bin/bash -lc "rg -n \"def pred_light|pred_light\\(\" /mnt/c/wbia-plugin-miew-id/wbia_miew_id" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java | sed -n '235,330p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IA.java | sed -n '330,430p'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 269ms:
   235	     *
   236	     * <p>encounterId is derived best-effort from the MediaAsset's existing
   237	     * trivial annotation (every Encounter.addMediaAsset call creates one).
   238	     * If null, MlServiceProcessor persists annotations without explicit
   239	     * Encounter linkage and downstream MediaAsset.assignEncounters handles
   240	     * the assignment per the legacy IBEISIA detect-callback pattern.</p>
   241	     */
   242	    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
   243	        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
   244	        int queued = 0;
   245	        for (MediaAsset ma : mas) {
   246	            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
   247	                queued++;
   248	            }
   249	        }
   250	        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
   251	            mas.size() + " assets; queued=" + queued + "; topTask=" + topTask);
   252	        return topTask;
   253	    }
   254	
   255	    /**
   256	     * Build and enqueue one v2 ml-service job for a single MediaAsset.
   257	     * Returns {@code true} iff the FileQueue write succeeded.
   258	     *
   259	     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
   260	     * normal intake path) and the startup stale-mlservice reconciler in
   261	     * {@code StartupWildbook}. The reconciler relies on the boolean
   262	     * return to decide whether to commit accompanying state changes; the
   263	     * normal intake path tolerates the swallowed-failure behavior.</p>
   264	     *
   265	     * <p><b>Task persistence note:</b> {@link Shepherd#storeNewTask}
   266	     * internally commits/reopens the transaction, so the child Task row
   267	     * is persisted before this method enqueues. On enqueue failure the
   268	     * child Task remains in the DB as an orphan — there is no queued
   269	     * job that will ever drive it. The orphan IS still discoverable
   270	     * via {@link org.ecocean.media.MediaAsset#getRootIATasks} (since
   271	     * the task references the MediaAsset through objectMediaAssets),
   272	     * so it may surface in operator-facing task listings until cleaned
   273	     * up by an out-of-band path. Callers that need cleanup should
   274	     * delete the orphan explicitly; the default posture here is to
   275	     * accept it since FileQueue write failures are rare.</p>
   276	     *
   277	     * <p>If {@code topTask} is null a fresh root task is created inside
   278	     * this method. This matches the reconciler's use case where there is
   279	     * no caller-side aggregator umbrella.</p>
   280	     */
   281	    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
   282	        Taxonomy taxy, Task topTask, String context, String baseUrl) {
   283	        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
   284	        ArrayList<MediaAsset> singleton = new ArrayList<MediaAsset>();
   285	        singleton.add(ma);
   286	        childTask.setObjectMediaAssets(singleton);
   287	        myShepherd.storeNewTask(childTask);
   288	
   289	        // Best-effort encounterId via existing annotations on the MA.
   290	        String encounterId = null;
   291	        ArrayList<Annotation> existing = ma.getAnnotations();
   292	        if (existing != null) {
   293	            for (Annotation a : existing) {
   294	                Encounter enc = a.findEncounter(myShepherd);
   295	                if (enc != null) {
   296	                    encounterId = enc.getId();
   297	                    break;
   298	                }
   299	            }
   300	        }
   301	
   302	        JSONObject qjob = new JSONObject();
   303	        qjob.put("mlServiceV2", true);
   304	        qjob.put("mediaAssetId", ma.getId());
   305	        qjob.put("taxonomyString", taxy.getScientificName());
   306	        qjob.put("taskId", childTask.getId());
   307	        qjob.put("__context", context);
   308	        qjob.put("__baseUrl", baseUrl);
   309	        if (Util.stringExists(encounterId)) {
   310	            qjob.put("encounterId", encounterId);
   311	        }
   312	
   313	        try {
   314	            return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
   315	        } catch (java.io.IOException iox) {
   316	            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
   317	                "addToDetectionQueue threw on ma " + ma.getId() + ": " + iox);
   318	            return false;
   319	        }
   320	    }
   321	
   322	    public static void handleMissingAcmids(List<MediaAsset> mediaAssets, Shepherd myShepherd) {
   323	        int count = 0;
   324	        int stopAfter = 200000;
   325	        int batchThreshold = 50;
   326	        int batchesSoFar = 0;
   327	        ArrayList<MediaAsset> assetsWithMissingAcmids = new ArrayList<MediaAsset>();
   328	
   329	        try {
   330	            for (MediaAsset ma : mediaAssets) {

 succeeded in 272ms:
   330	            for (MediaAsset ma : mediaAssets) {
   331	                count++;
   332	                if (count > stopAfter) {
   333	                    break;
   334	                }
   335	                if (ma != null && !ma.hasAcmId()) {
   336	                    assetsWithMissingAcmids.add(ma);
   337	                }
   338	                if ((assetsWithMissingAcmids.size() >= batchThreshold) ||
   339	                    count == mediaAssets.size()) {
   340	                    if (assetsWithMissingAcmids.size() > 0) { // if count gets to the end and assetsWithMissingAcmids is still empty, no need to do any of this
   341	                        try {
   342	                            IBEISIA.sendMediaAssetsNew(assetsWithMissingAcmids,
   343	                                myShepherd.getContext());
   344	                        } catch (Exception e) {
   345	                            System.out.println(
   346	                                "Error sending media asset to IA in handleMissingAcmids method in IA.java");
   347	                            e.printStackTrace();
   348	                        }
   349	                    }
   350	                    batchesSoFar++;
   351	                    assetsWithMissingAcmids = new ArrayList<MediaAsset>();
   352	                    myShepherd.updateDBTransaction();
   353	                }
   354	            }
   355	        } catch (Exception e) {
   356	            System.out.println("Error in handleMissingAcmids in IA.java");
   357	            e.printStackTrace();
   358	            myShepherd.rollbackDBTransaction();
   359	        }
   360	    }
   361	
   362	    // similar behavior to above: basically fake /ia api call, but via queue
   363	    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
   364	    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
   365	        return intakeAnnotations(myShepherd, anns, null, false);
   366	    }
   367	
   368	    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
   369	        final Task parentTask, boolean fastlane) {
   370	        // List<List<Annotation>> annses = binAnnotsByIaClass(anns);
   371	        //// slightly complicated bc we need to create child tasks only if there are multiple iaClasses
   372	        // if (annses.size() == 1) return intakeAnnotationsOneIAClass(myShepherd, annses.get(0), parentTask);
   373	        //// here we make child tasks
   374	        // Task topTask = (parentTask==null) ? new Task() : parentTask;
   375	        // for (List<Annotation> annsOneIaClass: annses) {
   376	        // topTask.addChild(intakeAnnotationsOneIAClass(myShepherd, anns, parentTask));
   377	        // }
   378	        // return topTask;
   379	        // }
   380	        // public static Task intakeAnnotationsOneIAClass(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
   381	        // System.out.println("Starting intakeAnnotations");
   382	        if ((anns == null) || (anns.size() < 1)) return null;
   383	        Task topTask = new Task();
   384	        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
   385	        topTask.setObjectAnnotations(anns);
   386	        String context = myShepherd.getContext();
   387	
   388	        /*
   389	            what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed for this we use
   390	               IBEISIA.identOpts to decide how many flavors of identification we need to do!   if have more than one we need to make a set of subtasks
   391	         */
   392	
   393	/*
   394	        String iaClass = anns.get(0).getIAClass(); //IAClass is a standard with image analysis that identifies the featuretype used for identification
   395	           List<JSONObject> opts = null;
   396	        // below gets it working for dolphins but can be generalized easily from IA.properties String inferredIaClass =
   397	           IBEISIA.inferIaClass(anns.get(0), myShepherd);
   398	        String bottlenose = "dolphin_bottlenose_fin";
   399	        if (bottlenose.equals(iaClass) || bottlenose.equals(inferredIaClass)) {
   400	            System.out.println("IA.java is sending a Tursiops truncatus job");
   401	            opts = IBEISIA.identOpts(context, bottlenose);
   402	        } else { // defaults to the default ia.properties IBEISIdentOpt, in our case humpback flukes opts = IBEISIA.identOpts(context);
   403	        }
   404	 */
   405	        // List<JSONObject> opts = IBEISIA.identOpts(myShepherd, anns.get(0));
   406	        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
   407	        List<List<Annotation> > annotsByIaClass = binAnnotsByIaClass(anns);
   408	        for (List<Annotation> annsOneIAClass : annotsByIaClass) {
   409	            List<JSONObject> opts = iaConfig.identOpts(myShepherd, annsOneIAClass.get(0));
   410	            // now we remove ones with default=false (they may get added in below via matchingAlgorithms param (via newOpts)
   411	            if (opts != null) {
   412	                Iterator<JSONObject> itr = opts.iterator();
   413	                while (itr.hasNext()) {
   414	                    if (!itr.next().optBoolean("default", true)) itr.remove();
   415	                }
   416	            }
   417	            System.out.println("identOpts: " + opts);
   418	            List<Task> tasks = new ArrayList<Task>();
   419	            JSONObject newTaskParams = new JSONObject(); // we merge parentTask.parameters in with opts from above
   420	            if (parentTask != null && parentTask.getParameters() != null) {
   421	                newTaskParams = parentTask.getParameters();
   422	                System.out.println("newTaskParams: " + newTaskParams.toString());
   423	                if (newTaskParams.optJSONArray("matchingAlgorithms") != null) {
   424	                    JSONArray matchingAlgorithms = newTaskParams.optJSONArray("matchingAlgorithms");
   425	                    System.out.println("matchingAlgorithms1: " + matchingAlgorithms.toString());
   426	                    ArrayList<JSONObject> newOpts = new ArrayList<JSONObject>();
   427	                    int maLength = matchingAlgorithms.length();
   428	                    for (int y = 0; y < maLength; y++) {
   429	                        newOpts.add(matchingAlgorithms.getJSONObject(y));
   430	                    }

 succeeded in 467ms:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:705:    ans = pred_light(query_emb, db_embs, db_labels)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/metrics/knn.py:50:def pred_light(query_embedding, db_embeddings, db_labels, n_results=50):

exec
/bin/bash -lc "rg -n \"numberTotalProspects|prospects|cutoff|matchResultsJson\\(\" /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet /mnt/c/Wildbook-clean2/src/main/java/org/ecocean -g '*.java'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/wbia-plugin-miew-id/wbia_miew_id/metrics/knn.py | sed -n '1,95p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java | sed -n '330,430p;520,555p'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 231ms:
     1	# -*- coding: utf-8 -*-
     2	import numpy as np
     3	from sklearn.neighbors import NearestNeighbors
     4	
     5	
     6	def predict_k_neigh(db_emb, db_lbls, test_emb, k=5, remove_duplicates=False):
     7	    """Get k nearest solutions from the database for test embeddings (query)
     8	    using k-NearestNeighbors algorithm.
     9	    Input:
    10	        db_emb (float array): database embeddings of size (num_emb, emb_size)
    11	        db_lbls (str or int array): database labels of size (num_emb,)
    12	        test_emb (float array): test embeddings of size (num_emb_t, emb_size)
    13	        k (int): number of predictions to return
    14	    Returns:
    15	        neigh_lbl_un (str or int array): labels of predictions of shape (num_emb_t, k)
    16	        neigh_ind_un (int array): labels of indices of nearest points of shape (num_emb_t, k)
    17	        neigh_dist_un (float array): distances of predictions of shape (num_emb_t, k)
    18	    """
    19	    # Set number of nearest points (with duplicated labels)
    20	    k_w_dupl = min(50, len(db_emb))
    21	    nn_classifier = NearestNeighbors(n_neighbors=k_w_dupl, metric='cosine')
    22	    nn_classifier.fit(db_emb, db_lbls)
    23	
    24	    # Predict nearest neighbors and distances for test embeddings
    25	    neigh_dist, neigh_ind = nn_classifier.kneighbors(test_emb)
    26	
    27	    # Get labels of nearest neighbors
    28	    neigh_lbl = np.zeros(shape=neigh_ind.shape, dtype=db_lbls.dtype)
    29	    for i, preds in enumerate(neigh_ind):
    30	        for j, pred in enumerate(preds):
    31	            neigh_lbl[i, j] = db_lbls[pred]
    32	
    33	    # Remove duplicates
    34	    neigh_lbl_un = []
    35	    neigh_ind_un = []
    36	    neigh_dist_un = []
    37	
    38	    for j in range(neigh_lbl.shape[0]):
    39	        indices = np.arange(0, len(neigh_lbl[j]))
    40	        a, b = neigh_lbl[j], indices
    41	        if remove_duplicates:
    42	            a, b = rem_dupl(a, b)
    43	        neigh_lbl_un.append(a[:k])
    44	        neigh_ind_un.append(neigh_ind[j][b][:k].tolist())
    45	        neigh_dist_un.append(neigh_dist[j][b][:k].tolist())
    46	
    47	    return neigh_lbl_un, neigh_ind_un, neigh_dist_un
    48	
    49	
    50	def pred_light(query_embedding, db_embeddings, db_labels, n_results=50):
    51	    """Get k nearest solutions from the database for one query embedding
    52	    using k-NearestNeighbors algorithm.
    53	    """
    54	    neigh_lbl_un, neigh_ind_un, neigh_dist_un = predict_k_neigh(
    55	        db_embeddings, db_labels, query_embedding, k=n_results, remove_duplicates=False
    56	    )
    57	
    58	    neigh_lbl_un = neigh_lbl_un[0]
    59	    neigh_dist_un = neigh_dist_un[0]
    60	
    61	    ans_dict = [
    62	        {'label': lbl, 'distance': dist} for lbl, dist in zip(neigh_lbl_un, neigh_dist_un)
    63	    ]
    64	    return ans_dict
    65	
    66	
    67	def rem_dupl(seq, seq2=None):
    68	    """Remove duplicates from a sequence and keep the order of elements.
    69	    Do it in unison with a sequence 2."""
    70	    seen = set()
    71	    seen_add = seen.add
    72	    if seq2 is None:
    73	        return [x for x in seq if not (x in seen or seen_add(x))]
    74	    else:
    75	        a = [x for x in seq if not (x in seen or seen_add(x))]
    76	        seen = set()
    77	        seen_add = seen.add
    78	        b = [seq2[i] for i, x in enumerate(seq) if not (x in seen or seen_add(x))]
    79	        return a, b

 succeeded in 249ms:
   330	    private Annotation getAnnotationFromAcmId(String acmId, Shepherd myShepherd) {
   331	        if (acmId == null) return null;
   332	        Annotation found = findAcmIdInTaskAnnotations(acmId);
   333	        if (found != null) return found;
   334	        List<Annotation> anns = myShepherd.getAnnotationsWithACMId(acmId, true);
   335	        System.out.println("[WARNING] getAnnotationFromAcmId() failed to find " + acmId +
   336	            " in task annots; loaded by acmId " + Util.collectionSize(anns) + " annot(s)");
   337	        if ((anns == null) || (anns.size() < 1)) return null;
   338	        return anns.get(0);
   339	    }
   340	
   341	    private Annotation findAcmIdInTaskAnnotations(String acmId) {
   342	        if ((this.task == null) || (acmId == null)) return null;
   343	        if (!this.task.hasObjectAnnotations()) return null;
   344	        for (Annotation ann : this.task.getObjectAnnotations()) {
   345	            if (acmId.equals(ann.getAcmId())) return ann;
   346	        }
   347	        return null;
   348	    }
   349	
   350	    // if it exists, we just return the thing, other wise we attempt to create it
   351	    public MediaAsset createInspectionHeatmapAsset(String externRef, String annotId,
   352	        Shepherd myShepherd) {
   353	        if (externRef == null) return null;
   354	        String url = "/api/query/graph/match/thumb/?extern_reference=" + externRef;
   355	        url += "&query_annot_uuid=" + this.queryAnnotation.getAcmId();
   356	        url += "&database_annot_uuid=" + annotId;
   357	        url += "&version=heatmask";
   358	        URL fullUrl = IBEISIA.iaURL(myShepherd.getContext(), url);
   359	        File tmpFile = new File("/tmp/extern-" + this.id + "-" + externRef + "-" +
   360	            this.queryAnnotation.getId() + "-" + annotId + ".jpg");
   361	        System.out.println("[DEBUG] trying extern fetch url=" + fullUrl + " => " + tmpFile);
   362	        MediaAsset ma = null;
   363	        try {
   364	            URLAssetStore.fetchFileFromURL(fullUrl, tmpFile);
   365	            ma = UploadedFiles.makeMediaAsset(this.id, tmpFile, myShepherd);
   366	            ma.addLabel("matchInspectionHeatmap");
   367	            System.out.println("[INFO] createInspectionHeatmapAsset() fetched " + fullUrl +
   368	                " and created " + ma);
   369	            tmpFile.delete();
   370	        } catch (Exception ex) {
   371	            System.out.println(
   372	                "[ERROR] createInspectionHeatmapAsset() asset creation failed using " + fullUrl +
   373	                " => " + tmpFile + ": " + ex);
   374	            ex.printStackTrace();
   375	        }
   376	        return ma;
   377	    }
   378	
   379	/*
   380	   notes on pairx payload:
   381	   - image1_uris / image2_uris accept URLs or local file paths (as seen by the server)
   382	   - If you provide 1 image1 and N image2s, it compares that single image1 against each image2 (1-to-many)
   383	   - If you provide N of each, they're compared pairwise (N-to-N, max 16 pairs)
   384	   - bb1/bb2 are bounding boxes as [x, y, width, height]
   385	   - visualization_type options: "lines_and_colors", "only_lines", "only_colors"
   386	   - layer_key controls feature depth — earlier layers (e.g. backbone.blocks.1) give point-specific matches, later layers
   387	    (e.g. backbone.blocks.5) give broader region matches
   388	 */
   389	    public MediaAsset createInspectionPairxAsset(Annotation ann1, Annotation ann2,
   390	        Shepherd myShepherd) {
   391	        if ((ann1 == null) || (ann2 == null)) return null;
   392	        MediaAsset ma1 = ann1.getMediaAsset();
   393	        MediaAsset ma2 = ann2.getMediaAsset();
   394	        if ((ma1 == null) || (ma2 == null)) return null;
   395	        // we need this to find MLService endpoint
   396	        Encounter enc = ann1.findEncounter(myShepherd);
   397	        if (enc == null) return null;
   398	        JSONObject payload = new JSONObject();
   399	        payload.put("algorithm", "pairx");
   400	        payload.put("visualization_type", "only_colors");
   401	        payload.put("k_colors", 5);
   402	        // payload.put("k_lines", 20);
   403	        payload.put("model_id", "miewid-msv4.1");
   404	        payload.put("crop_bbox", false);
   405	        payload.put("layer_key", "backbone.blocks.3");
   406	        payload.put("image1_uris", new JSONArray(new String[] { ma1.webURL().toString() }));
   407	        payload.put("image2_uris", new JSONArray(new String[] { ma2.webURL().toString() }));
   408	        payload.put("theta1", new JSONArray(new Double[] { ann1.getTheta() }));
   409	        payload.put("theta2", new JSONArray(new Double[] { ann2.getTheta() }));
   410	        // bb1 / bb2 payload construction. See addBboxPayload Javadoc for
   411	        // the two bugs this fixes (shared-array + negative-bbox-rejection,
   412	        // empty-match-prospects design Track 2 C12). If either clamped
   413	        // bbox has zero width or height, skip the POST entirely — PairX
   414	        // also rejects degenerate boxes.
   415	        int[] clamped1 = clampBbox(ann1.getBbox());
   416	        int[] clamped2 = clampBbox(ann2.getBbox());
   417	        if (isDegenerateBbox(clamped1) || isDegenerateBbox(clamped2)) {
   418	            System.out.println(
   419	                "[INFO] createInspectionPairxAsset() skipping PairX for ann1=" +
   420	                ann1.getId() + " ann2=" + ann2.getId() +
   421	                ": degenerate clamped bbox " +
   422	                java.util.Arrays.toString(clamped1) + " / " +
   423	                java.util.Arrays.toString(clamped2));
   424	            return null;
   425	        }
   426	        addBboxPayload(payload, clamped1, clamped2);
   427	
   428	        // get the image data from pairx endpoint
   429	        JSONObject res = null;
   430	        URL pairxUrl = null;
   520	     * <p>Two bugs in the previous implementation are addressed
   521	     * together (empty-match-prospects design Track 2 C12):</p>
   522	     * <ol>
   523	     *   <li>The previous code reused one tmpArr for both keys, so
   524	     *       {@code tmpArr.put(0, ann2)} after {@code payload.put("bb1", tmpArr)}
   525	     *       mutated the shared array and made {@code bb2 == bb1}.
   526	     *       Building two outer arrays here keeps the references
   527	     *       distinct.</li>
   528	     *   <li>{@link #clampBbox} (called by the production entry point
   529	     *       before this method) prevents negative x/y from being
   530	     *       sent to PairX, which would return HTTP 400
   531	     *       "Bounding box values should be positive".</li>
   532	     * </ol>
   533	     *
   534	     * <p>Package-visible so {@code MatchResultPairxPayloadTest} can
   535	     * assert the JSON shape without spinning up a real Annotation.</p>
   536	     */
   537	    static void addBboxPayload(JSONObject payload, int[] bbox1, int[] bbox2) {
   538	        payload.put("bb1", new JSONArray().put(bboxToJsonArray(bbox1)));
   539	        payload.put("bb2", new JSONArray().put(bboxToJsonArray(bbox2)));
   540	    }
   541	
   542	    public static URL _getPairxUrl(String txStr)
   543	    throws IOException {
   544	        if (txStr == null) throw new IOException("passed null taxonomy");
   545	        String urlStr = null;
   546	        try {
   547	            MLService mls = new MLService();
   548	            List<JSONObject> confs = mls.getConfigs(txStr);
   549	            if (confs.size() < 1) throw new IOException("empty MLService configs for tx=" + txStr);
   550	            urlStr = confs.get(0).optString("api_endpoint", null);
   551	        } catch (IAException ex) {
   552	            throw new IOException(ex);
   553	        }
   554	        if (urlStr == null) return null;
   555	        return new URL(urlStr + "/explain/");

 succeeded in 995ms:
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:51:        double cutoff = 2;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:90:                cutoff, myShepherd, context, "", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:125:                        righty, cutoff, myShepherd, context, "LocationID", locs);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:132:            // successfulWrite=writeBoostedResult(encNumber, res, encNumber, newEncDate, newEncShark, newEncSize, righty, cutoff, myShepherd, props);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:167:        String newEncSize, boolean rightSide, double cutoff, Shepherd myShepherd, String context,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:323:        String newEncDate, String newEncShark, String newEncSize, boolean rightSide, double cutoff,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:601:     * empty-match-prospects Track 2 batch gate) need this so a read of
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:605:     * <p>(Empty-match-prospects design Track 2 C7.)</p>
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:820:    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:845:        // (empty-match-prospects design Track 2 C15).
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:860:                "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:871:            rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:877:                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:458:        // no JDO touchpoints. (Empty-match-prospects design Track 1 C5.)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:582:     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:648:     * <p>(Empty-match-prospects design Track 1 C4: extracted from
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:733:     * (Empty-match-prospects design Track 1 C6.)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1145:     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:1573:    1. how do we determine where the cutoff is for auto-creating the annotation?-- made some methods for this
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:292:            // we need embedding(s) on this annot to find prospects, so lets try to make some on the fly if we dont have one
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:370:            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:372:                Util.collectionSize(prospects) + " prospects (in " + numberCandidates +
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:377:                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:55:     * above. (Empty-match-prospects design Track 2 C11 testability
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:451:     * <p>(Empty-match-prospects design Track 2 C11.)</p>
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:549:            // MatchResult + prospects are already persisted with
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:568:     * PairX HTTP work doesn't hold a Shepherd. (Empty-match-prospects
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:551:    // ml-service migration v2 / empty-match-prospects design Track 2 C8.
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:112:                            int prospectsSize = org.ecocean.ia.MatchResult.DEFAULT_PROSPECTS_CUTOFF;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:119:                                // note: negative size means all of them (no cutoff)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:120:                                prospectsSize = Integer.parseInt(request.getParameter(
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:121:                                    "prospectsSize"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:123:                            rtn.put("prospectsSize", prospectsSize);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:124:                            JSONObject mrJson = task.matchResultsJson(prospectsSize, projectIds,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:51:        double cutoff = 2;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:90:                cutoff, myShepherd, context, "", null);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:125:                        righty, cutoff, myShepherd, context, "LocationID", locs);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:132:            // successfulWrite=writeBoostedResult(encNumber, res, encNumber, newEncDate, newEncShark, newEncSize, righty, cutoff, myShepherd, props);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:167:        String newEncSize, boolean rightSide, double cutoff, Shepherd myShepherd, String context,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/WriteOutScanTask.java:323:        String newEncDate, String newEncShark, String newEncSize, boolean rightSide, double cutoff,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:24: * <p>(Empty-match-prospects design Track 2 C10.)</p>
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGate.java:10: * empty-match-prospects bug where per-image ml-service jobs fired
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchVisibilityGate.java:43: * <p>(Empty-match-prospects design Track 2 C10.)</p>
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:40:     * prospects after the MatchResult has been persisted (empty-match-
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:41:     * prospects design Track 2 C13: PairX is now non-blocking and
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:38:    private Set<MatchResultProspect> prospects;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:42:    // via .prospects due to MAXIMUM_PROSPECTS_STORED (see below)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:48:    // fallback number to cutoff number of prospects to return
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:84:        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:165:        if (this.prospects == null)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:166:            this.prospects = new HashSet<MatchResultProspect>();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:168:        this.numberProspects += annotIds.length(); // true number of prospects
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:182:            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:186:                    ") number storable prospects on " + this);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:195:    // tends to return relatively few prospects. TODO adjust later if this proves untrue.
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:197:    // Empty-match-prospects design Track 2 C13: prospects are created with
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:207:        if (this.prospects == null)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:208:            this.prospects = new HashSet<MatchResultProspect>();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:217:                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:221:        this.numberProspects = this.prospects.size();
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:226:     * Build indiv-tab prospects (scoreType "indiv") from the knn
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:248:        // duplicate indiv prospects. Keying by id avoids that
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:281:        // image tab. prospectsSorted(...) handles final ordering, so
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:294:                this.prospects.add(new MatchResultProspect(best, bestScore, "indiv", null));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:300:            this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(),
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:306:     * Public read-only view of the prospects collection so the
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:312:        return this.prospects;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:412:        // empty-match-prospects design Track 2 C12). If either clamped
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:476:     * <p>Package-visible for unit testing. (Empty-match-prospects
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:521:     * together (empty-match-prospects design Track 2 C12):</p>
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:584:        for (MatchResultProspect mrp : prospects) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:590:    // if cutoff < 0 then it will not be truncated at all
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:591:    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:596:        for (MatchResultProspect mrp : prospects) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:600:        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:604:    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:609:            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:617:    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:622:        rtn.put("numberTotalProspects", numberProspects());
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:625:        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:21: * {@link MatchResult}'s prospects with PairX inspection MediaAssets.
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:29: *       prospects, and builds a list of {@link PairxDto} carrying every
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:41: * error on one prospect logs and continues; other prospects in the
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:44: * <p>(Empty-match-prospects design Track 2 C13.)</p>
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:59:     * <p>Returns the number of prospects that received a new inspection
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:94:            "/" + dtos.size() + " prospects on mr=" + matchResultId);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:126:                    // Skip prospects that already have an inspection image
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:15: * Direct-SQL utility for the empty-match-prospects batch gate.
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:37: * <p>(Empty-match-prospects design Track 2 C9.)</p>
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/StartupWildbook.java:439:     * {@code wbiaRegisterAttempts}; the next cycle retries until cutoff.</p>
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/StartupWildbook.java:781:            // closes. (Empty-match-prospects design Track 1 C5: WBIA
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:112:                            int prospectsSize = org.ecocean.ia.MatchResult.DEFAULT_PROSPECTS_CUTOFF;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:119:                                // note: negative size means all of them (no cutoff)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:120:                                prospectsSize = Integer.parseInt(request.getParameter(
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:121:                                    "prospectsSize"));
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:123:                            rtn.put("prospectsSize", prospectsSize);
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:124:                            JSONObject mrJson = task.matchResultsJson(prospectsSize, projectIds,
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/IAGatewayDeferredMatchPublisher.java:14: * <p>(Empty-match-prospects design Track 2 C11 — Codex round-4
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/DeferredMatchPublisher.java:15: * <p>(Empty-match-prospects design Track 2 C10: testability seam
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:34:    // produced only ~3 prospects regardless of how large the matching
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1209:        // 0 to score. (Empty-match-prospects C17: MiewID score parity.)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1240:     * conversion. (Empty-match-prospects C17.)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1302:                // (Empty-match-prospects C17.)
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/ScanWorkItemResultsHandler.java:78:        // double cutoff=2;
/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/ScanWorkItemResultsHandler.java:78:        // double cutoff=2;

codex
The WBIA plugin has a stronger structural issue than the Java filter path: the “knn” mode is not scoring the full candidate list. It computes nearest neighbors with `k_w_dupl = min(50, len(db_emb))`, then fills every non-neighbor daid with `0`, which explains the legacy hard cliff.
exec
/bin/bash -lc "nl -ba /mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py | sed -n '214,335p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/ml-service/app/models/miewid.py | sed -n '1,220p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc 'rg -n "def _load_data|crop_bbox|theta|bbox|rotate|transform|img_size|MiewidModel" /mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py /mnt/c/wbia-plugin-miew-id/wbia_miew_id /mnt/c/ml-service/app/models/miewid.py' in /mnt/c/claude-skills/kb/ml-service
 succeeded in 212ms:
   214	@register_ibs_method
   215	def miew_id_embedding(ibs, aid_list, config=None, use_depc=True):
   216	    r"""
   217	    Generate embeddings for MiewID
   218	    Args:
   219	        ibs (IBEISController): IBEIS / WBIA controller object
   220	        aid_list  (int): annot ids specifying the input
   221	        use_depc (bool): use dependency cache
   222	    CommandLine:
   223	        python -m wbia_miew_id._plugin miew_id_embedding
   224	    Example:
   225	        >>> # ENABLE_DOCTEST
   226	        >>> import wbia_miew_id
   227	        >>> from wbia_miew_id._plugin import DEMOS, CONFIGS, MODELS
   228	        >>> species = 'rhincodon_typus'
   229	        >>> test_ibs = wbia_miew_id._plugin.wbia_miew_id_test_ibs(DEMOS[species], species, 'test2021')
   230	        >>> aid_list = test_ibs.get_valid_aids(species=species)
   231	        >>> rank1 = test_ibs.evaluate_distmat(aid_list, CONFIGS[species], use_depc=False)
   232	        >>> expected_rank1 = 0.81366
   233	        >>> assert abs(rank1 - expected_rank1) < 1e-2
   234	
   235	    Example:
   236	        >>> # ENABLE_DOCTEST
   237	        >>> import wbia_miew_id
   238	        >>> from wbia_miew_id._plugin import DEMOS, CONFIGS, MODELS
   239	        >>> species = 'whale_grey'
   240	        >>> test_ibs = wbia_miew_id._plugin.wbia_miew_id_test_ibs(DEMOS[species], species, 'test2021')
   241	        >>> aid_list = test_ibs.get_valid_aids(species=species)
   242	        >>> rank1 = test_ibs.evaluate_distmat(aid_list, CONFIGS[species], use_depc=False)
   243	        >>> expected_rank1 = 0.69505
   244	        >>> assert abs(rank1 - expected_rank1) < 1e-2
   245	
   246	    Example:
   247	        >>> # ENABLE_DOCTEST
   248	        >>> import wbia_miew_id
   249	        >>> from wbia_miew_id._plugin import DEMOS, CONFIGS, MODELS
   250	        >>> species = 'horse_wild'
   251	        >>> test_ibs = wbia_miew_id._plugin.wbia_miew_id_test_ibs(DEMOS[species], species, 'test2021')
   252	        >>> aid_list = test_ibs.get_valid_aids(species=species)
   253	        >>> rank1 = test_ibs.evaluate_distmat(aid_list, CONFIGS[species], use_depc=False)
   254	        >>> expected_rank1 = 0.32773
   255	        >>> assert abs(rank1 - expected_rank1) < 1e-2
   256	
   257	    """
   258	    global GLOBAL_EMBEDDING_CACHE
   259	
   260	    dirty_aids = []
   261	    for aid in aid_list:
   262	        if aid not in GLOBAL_EMBEDDING_CACHE:
   263	            dirty_aids.append(aid)
   264	
   265	    if len(dirty_aids) > 0:
   266	        print('Computing %d non-cached embeddings' % (len(dirty_aids), ))
   267	        if use_depc:
   268	            config_map = {'config_path': config}
   269	            dirty_embeddings = ibs.depc_annot.get(
   270	                'MiewIdEmbedding', dirty_aids, 'embedding', config_map
   271	            )
   272	        else:
   273	            dirty_embeddings = miew_id_compute_embedding(ibs, dirty_aids, config)
   274	
   275	        for dirty_aid, dirty_embedding in zip(dirty_aids, dirty_embeddings):
   276	            GLOBAL_EMBEDDING_CACHE[dirty_aid] = dirty_embedding
   277	
   278	    embeddings = ut.take(GLOBAL_EMBEDDING_CACHE, aid_list)
   279	
   280	    return embeddings
   281	
   282	
   283	class MiewIdEmbeddingConfig(dt.Config):  # NOQA
   284	    _param_info_list = [
   285	        ut.ParamInfo('config_path', default=None),
   286	    ]
   287	
   288	
   289	@register_preproc_annot(
   290	    tablename='MiewIdEmbedding',
   291	    parents=[ANNOTATION_TABLE],
   292	    colnames=['embedding'],
   293	    coltypes=[np.ndarray],
   294	    configclass=MiewIdEmbeddingConfig,
   295	    fname='miew_id',
   296	    chunksize=128,
   297	)
   298	@register_ibs_method
   299	def miew_id_embedding_depc(depc, aid_list, config=None):
   300	    ibs = depc.controller
   301	    embs = miew_id_compute_embedding(ibs, aid_list, config=config['config_path'])
   302	    for aid, emb in zip(aid_list, embs):
   303	        yield (np.array(emb),)
   304	
   305	
   306	@register_ibs_method
   307	def miew_id_compute_embedding(ibs, aid_list, config=None, multithread=False):
   308	    # Get species from the first annotation
   309	    species = ibs.get_annot_species_texts(aid_list[0])
   310	
   311	    # Load model
   312	    model, config, (model_url, config_url) = read_config_and_load_model(species)
   313	
   314	    # Initialize the gradient scaler
   315	    scaler = GradScaler()
   316	
   317	    # Preprocess images to model input
   318	    test_loader, test_dataset = _load_data(ibs, aid_list, config, multithread)
   319	
   320	    # Compute embeddings
   321	    embeddings = []
   322	    model.eval()
   323	    with torch.no_grad():
   324	        for images, names, image_paths, image_bboxes, image_thetas in test_loader:
   325	            if config.use_gpu:
   326	                images = images.cuda(non_blocking=True)
   327	
   328	            with autocast():
   329	                output = model(images.float())
   330	
   331	            embeddings.append(output.detach().cpu().numpy())
   332	
   333	    embeddings = np.concatenate(embeddings)
   334	    return embeddings
   335	

 succeeded in 209ms:
     1	from .base_model import BaseModel
     2	import logging
     3	from transformers import AutoModel
     4	from fastapi import HTTPException
     5	from typing import Dict, Any, List, Optional, Tuple
     6	import timm
     7	import torch
     8	import torch.nn as nn
     9	import torchvision.transforms as transforms
    10	from PIL import Image
    11	import numpy as np
    12	import io
    13	import math
    14	from app.utils.checkpoint_utils import get_checkpoint_path
    15	
    16	logger = logging.getLogger(__name__)
    17	
    18	
    19	class GeM(nn.Module):
    20	    """Generalized Mean Pooling."""
    21	    def __init__(self, p=3, eps=1e-6):
    22	        super().__init__()
    23	        self.p = nn.Parameter(torch.ones(1) * p)
    24	        self.eps = eps
    25	
    26	    def forward(self, x):
    27	        return nn.functional.avg_pool2d(
    28	            x.clamp(min=self.eps).pow(self.p),
    29	            (x.size(-2), x.size(-1))
    30	        ).pow(1.0 / self.p)
    31	
    32	
    33	class MiewIdNet(nn.Module):
    34	    """Standalone MiewID model architecture matching wbia-plugin-miew-id training code."""
    35	    def __init__(self, model_name='efficientnetv2_rw_m'):
    36	        super().__init__()
    37	        self.backbone = timm.create_model(model_name, pretrained=False)
    38	        final_in_features = self.backbone.classifier.in_features
    39	        self.backbone.classifier = nn.Identity()
    40	        self.backbone.global_pool = nn.Identity()
    41	        self.pooling = GeM()
    42	        self.bn = nn.BatchNorm1d(final_in_features)
    43	
    44	    def forward(self, x):
    45	        x = self.backbone.forward_features(x)
    46	        x = self.pooling(x).view(x.size(0), -1)
    47	        return self.bn(x)
    48	
    49	
    50	class MiewidModel(BaseModel):
    51	    def __init__(self):
    52	        self.model = None
    53	        self.model_info = {}
    54	        self.device = "cuda"
    55	        self.preprocess = None
    56	        self.use_checkpoint = False
    57	
    58	    def load(self, model_path: str = "", device: str = "cuda", **kwargs) -> None:
    59	        self.device = device
    60	        checkpoint_path = kwargs.get('checkpoint_path')
    61	        
    62	        # Check if we should use checkpoint loading
    63	        if checkpoint_path:
    64	            self.use_checkpoint = True
    65	            local_checkpoint_path = get_checkpoint_path(checkpoint_path)
    66	            # Remove checkpoint_path from kwargs to avoid duplicate parameter error
    67	            filtered_kwargs = {k: v for k, v in kwargs.items() if k != 'checkpoint_path'}
    68	            version = kwargs.get('version', 3)
    69	            if isinstance(version, (int, float)) and version >= 4:
    70	                self._load_standalone(local_checkpoint_path, device)
    71	            else:
    72	                self._load_from_checkpoint(local_checkpoint_path, device, **filtered_kwargs)
    73	        else:
    74	            self.use_checkpoint = False
    75	            self._load_from_huggingface(device, **kwargs)
    76	
    77	        # Initialize preprocessing transforms
    78	        self.preprocess = transforms.Compose([
    79	            transforms.Resize((440, 440)),
    80	            transforms.ToTensor(),
    81	            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
    82	        ])
    83	
    84	        self.model_info = {
    85	            'model_type': 'miewid',
    86	            'device': device,
    87	            'imgsz': kwargs.get('imgsz', 440),
    88	            'version': kwargs.get('version', 3),
    89	            'checkpoint_path': checkpoint_path,
    90	            'use_checkpoint': self.use_checkpoint
    91	        }
    92	
    93	    def _load_from_huggingface(self, device: str, **kwargs) -> None:
    94	        """Load model from HuggingFace Hub."""
    95	        if kwargs.get('version', 3) == 3:
    96	            model_tag = f"conservationxlabs/miewid-msv3"
    97	        else:
    98	            model_tag = f"conservationxlabs/miewid-msv2"
    99	            
   100	        self.model = AutoModel.from_pretrained(model_tag, trust_remote_code=True)
   101	        self.model.eval()
   102	        self.model.to(device)
   103	        logger.info(f"Loaded MiewID model from HuggingFace: {model_tag}")
   104	
   105	    def _apply_checkpoint(self, model: nn.Module, checkpoint_path: str, device: str, strict: bool = True) -> None:
   106	        """Load checkpoint weights into a model and move to device."""
   107	        try:
   108	            checkpoint = torch.load(checkpoint_path, map_location='cpu', weights_only=True)
   109	            model.load_state_dict(checkpoint, strict=strict)
   110	            model.eval()
   111	            model.to(device)
   112	            try:
   113	                model.device = torch.device(device)
   114	            except AttributeError:
   115	                pass  # HuggingFace models have read-only .device property
   116	            self.model = model
   117	            logger.info(f"Loaded MiewID model from checkpoint: {checkpoint_path}")
   118	        except Exception as e:
   119	            logger.error(f"Failed to load checkpoint from {checkpoint_path}: {str(e)}")
   120	            raise HTTPException(
   121	                status_code=500,
   122	                detail=f"Failed to load model checkpoint: {str(e)}"
   123	            )
   124	
   125	    def _load_from_checkpoint(self, checkpoint_path: str, device: str, **kwargs) -> None:
   126	        """Load HuggingFace base model and overlay checkpoint weights."""
   127	        if kwargs.get('version', 3) == 3:
   128	            model_tag = "conservationxlabs/miewid-msv3"
   129	        else:
   130	            model_tag = "conservationxlabs/miewid-msv2"
   131	        model = AutoModel.from_pretrained(model_tag, trust_remote_code=True)
   132	        self._apply_checkpoint(model, checkpoint_path, device, strict=False)
   133	
   134	    def _load_standalone(self, checkpoint_path: str, device: str) -> None:
   135	        """Load model from checkpoint using standalone timm-based architecture."""
   136	        model = MiewIdNet()
   137	        self._apply_checkpoint(model, checkpoint_path, device, strict=True)
   138	
   139	    def get_model_info(self) -> Dict[str, Any]:
   140	        """Get information about the loaded model."""
   141	        return self.model_info
   142	    
   143	    def predict(self, **kwargs):
   144	        raise HTTPException(status_code=400, detail=f"MiewID should not be used for prediction")
   145	
   146	    def crop_and_rotate_image(self, image: Image.Image, bbox: Tuple[int, int, int, int], theta: float = 0.0) -> Image.Image:
   147	        """
   148	        Crop and rotate an image based on bounding box and rotation angle.
   149	        
   150	        Args:
   151	            image: PIL Image
   152	            bbox: Tuple of (x, y, width, height)
   153	            theta: Rotation angle in radians
   154	            
   155	        Returns:
   156	            Cropped and rotated PIL Image
   157	        """
   158	        x, y, w, h = bbox
   159	        
   160	        # Crop the image
   161	        cropped = image.crop((x, y, x + w, y + h))
   162	        
   163	        # Rotate if theta is provided
   164	        if theta != 0.0:
   165	            # Convert radians to degrees
   166	            angle_degrees = math.degrees(theta)
   167	            cropped = cropped.rotate(-angle_degrees, expand=True)
   168	        
   169	        return cropped
   170	
   171	    def extract_embeddings(self, image_bytes: bytes, bbox: Optional[Tuple[int, int, int, int]] = None, theta: float = 0.0) -> np.ndarray:
   172	        """
   173	        Extract embeddings from an image using optional bounding box and rotation.
   174	        
   175	        Args:
   176	            image_bytes: Image data as bytes
   177	            bbox: Optional tuple of (x, y, width, height). If None, uses full image
   178	            theta: Rotation angle in radians
   179	            
   180	        Returns:
   181	            Numpy array containing the embeddings
   182	        """
   183	        try:
   184	            # Load image from bytes
   185	            image = Image.open(io.BytesIO(image_bytes)).convert('RGB')
   186	            
   187	            # Process the image (crop and rotate if bbox provided, otherwise just rotate)
   188	            if bbox is not None:
   189	                processed_image = self.crop_and_rotate_image(image, bbox, theta)
   190	            else:
   191	                # Use full image, only apply rotation if needed
   192	                if theta != 0.0:
   193	                    angle_degrees = math.degrees(theta)
   194	                    processed_image = image.rotate(-angle_degrees, expand=True)
   195	                else:
   196	                    processed_image = image
   197	            
   198	            # Apply preprocessing transforms
   199	            input_tensor = self.preprocess(processed_image)
   200	            input_batch = input_tensor.unsqueeze(0).to(self.device)
   201	            
   202	            # Extract embeddings
   203	            with torch.no_grad():
   204	                embeddings = self.model(input_batch)
   205	            
   206	            # Convert to numpy and return
   207	            return embeddings.cpu().numpy()
   208	            
   209	        except Exception as e:
   210	            logger.error(f"Error extracting embeddings: {str(e)}")
   211	            raise HTTPException(status_code=500, detail=f"Error extracting embeddings: {str(e)}")

 succeeded in 439ms:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:14:import torchvision.transforms as transforms  # noqa: E402
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:26:from wbia_miew_id.datasets import PluginDataset, get_test_transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:121:    """Load and transform one annotation into a tensor for PairX."""
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:324:        for images, names, image_paths, image_bboxes, image_thetas in test_loader:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:420:            config.data.crop_bbox,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:600:def _load_data(ibs, aid_list, config, multithread=False, batch_size=None):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:605:    test_transform = get_test_transforms((config.data.image_size[0], config.data.image_size[1]))
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:607:    bboxes = ibs.get_annot_bboxes(aid_list)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:610:    thetas = ibs.get_annot_thetas(aid_list)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:617:        bboxes,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:619:        thetas,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:620:        test_transform,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:685:        bbox_list = [a['bbox'] for a in coco_annots]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:688:            bbox_list=bbox_list,
/mnt/c/ml-service/app/models/miewid.py:3:from transformers import AutoModel
/mnt/c/ml-service/app/models/miewid.py:9:import torchvision.transforms as transforms
/mnt/c/ml-service/app/models/miewid.py:50:class MiewidModel(BaseModel):
/mnt/c/ml-service/app/models/miewid.py:77:        # Initialize preprocessing transforms
/mnt/c/ml-service/app/models/miewid.py:78:        self.preprocess = transforms.Compose([
/mnt/c/ml-service/app/models/miewid.py:79:            transforms.Resize((440, 440)),
/mnt/c/ml-service/app/models/miewid.py:80:            transforms.ToTensor(),
/mnt/c/ml-service/app/models/miewid.py:81:            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
/mnt/c/ml-service/app/models/miewid.py:146:    def crop_and_rotate_image(self, image: Image.Image, bbox: Tuple[int, int, int, int], theta: float = 0.0) -> Image.Image:
/mnt/c/ml-service/app/models/miewid.py:148:        Crop and rotate an image based on bounding box and rotation angle.
/mnt/c/ml-service/app/models/miewid.py:152:            bbox: Tuple of (x, y, width, height)
/mnt/c/ml-service/app/models/miewid.py:153:            theta: Rotation angle in radians
/mnt/c/ml-service/app/models/miewid.py:156:            Cropped and rotated PIL Image
/mnt/c/ml-service/app/models/miewid.py:158:        x, y, w, h = bbox
/mnt/c/ml-service/app/models/miewid.py:163:        # Rotate if theta is provided
/mnt/c/ml-service/app/models/miewid.py:164:        if theta != 0.0:
/mnt/c/ml-service/app/models/miewid.py:166:            angle_degrees = math.degrees(theta)
/mnt/c/ml-service/app/models/miewid.py:167:            cropped = cropped.rotate(-angle_degrees, expand=True)
/mnt/c/ml-service/app/models/miewid.py:171:    def extract_embeddings(self, image_bytes: bytes, bbox: Optional[Tuple[int, int, int, int]] = None, theta: float = 0.0) -> np.ndarray:
/mnt/c/ml-service/app/models/miewid.py:177:            bbox: Optional tuple of (x, y, width, height). If None, uses full image
/mnt/c/ml-service/app/models/miewid.py:178:            theta: Rotation angle in radians
/mnt/c/ml-service/app/models/miewid.py:187:            # Process the image (crop and rotate if bbox provided, otherwise just rotate)
/mnt/c/ml-service/app/models/miewid.py:188:            if bbox is not None:
/mnt/c/ml-service/app/models/miewid.py:189:                processed_image = self.crop_and_rotate_image(image, bbox, theta)
/mnt/c/ml-service/app/models/miewid.py:192:                if theta != 0.0:
/mnt/c/ml-service/app/models/miewid.py:193:                    angle_degrees = math.degrees(theta)
/mnt/c/ml-service/app/models/miewid.py:194:                    processed_image = image.rotate(-angle_degrees, expand=True)
/mnt/c/ml-service/app/models/miewid.py:198:            # Apply preprocessing transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/evaluate.py:1:from wbia_miew_id.datasets import MiewIdDataset, get_train_transforms, get_valid_transforms, get_test_transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/evaluate.py:32:                 crop_bbox, valid_batch_size, num_workers, eval_groups, fliplr, fliplr_view, n_filter_min, n_subsample_max,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/evaluate.py:45:        self.crop_bbox = crop_bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/evaluate.py:85:                             images_dir, image_size, crop_bbox, valid_batch_size, num_workers, 
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/evaluate.py:100:            transforms=get_test_transforms((image_size[0], image_size[1])),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/evaluate.py:103:            crop_bbox=crop_bbox,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/evaluate.py:136:        crop_bbox = self.crop_bbox, 
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/evaluate.py:152:            self.use_full_image_path, self.images_dir, self.image_size, self.crop_bbox, 
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/evaluate.py:189:    crop_bbox=config.data.crop_bbox,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:14:import torchvision.transforms as transforms  # noqa: E402
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:26:from wbia_miew_id.datasets import PluginDataset, get_test_transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:121:    """Load and transform one annotation into a tensor for PairX."""
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:324:        for images, names, image_paths, image_bboxes, image_thetas in test_loader:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:420:            config.data.crop_bbox,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:600:def _load_data(ibs, aid_list, config, multithread=False, batch_size=None):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:605:    test_transform = get_test_transforms((config.data.image_size[0], config.data.image_size[1]))
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:607:    bboxes = ibs.get_annot_bboxes(aid_list)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:610:    thetas = ibs.get_annot_thetas(aid_list)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:617:        bboxes,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:619:        thetas,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:620:        test_transform,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:685:        bbox_list = [a['bbox'] for a in coco_annots]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:688:            bbox_list=bbox_list,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/split_dataset.ipynb:55:       "      <th>theta</th>\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/split_dataset.ipynb:138:       "   theta viewpoint  name         file_name       species  \\\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/__init__.py:2:from .transforms import *
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/train.py:7:from wbia_miew_id.datasets import MiewIdDataset, get_train_transforms, get_valid_transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/train.py:123:        crop_bbox = config.data.crop_bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/train.py:138:            crop_bbox = False
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/train.py:142:            transforms=get_train_transforms((config.data.image_size[0], config.data.image_size[1])),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/train.py:145:            crop_bbox=crop_bbox)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/train.py:149:            transforms=get_valid_transforms((config.data.image_size[0], config.data.image_size[1])),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/train.py:152:            crop_bbox=crop_bbox)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:45:    "  crop_bbox: true\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/run_training.ipynb:95:    "  theta_zero: 0.785\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/preprocess.py:38:    df['bbox'] = df[['x', 'y', 'w', 'h']].values.tolist()
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/preprocess.py:68:    names_id = le.fit_transform(names)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/transforms.py:5:from albumentations.core.transforms_interface import ImageOnlyTransform
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/transforms.py:6:from albumentations.pytorch.transforms import ToTensorV2
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/transforms.py:8:import torchvision.transforms as T
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/transforms.py:9:from albumentations.core.transforms_interface import ImageOnlyTransform
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/transforms.py:14:    """Wrap PyTorch Resize transform for Albumentations compatibility."""
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/transforms.py:23:        # Apply the resize transform
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/transforms.py:49:def get_train_transforms(image_size):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/transforms.py:60:                shift_limit=0.25, scale_limit=0.2, rotate_limit=15, p=0.5
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/transforms.py:68:def get_valid_transforms(image_size):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/transforms.py:77:def get_test_transforms(image_size):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/match_vis.py:53:        render_transformed=True, show=False, use_cuda=use_cuda)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:16:    "from transformers import AutoModel"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:46:    "  crop_bbox: true\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/finetune.ipynb:96:    "  theta_zero: 0.785\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/engine/group_eval.py:3:from wbia_miew_id.datasets import MiewIdDataset, get_test_transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/engine/group_eval.py:8:def group_eval_run(df_test, eval_groups, model, n_filter_min, n_subsample_max, image_size, fliplr, fliplr_view, crop_bbox, valid_batch_size, device):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/engine/group_eval.py:24:                    transforms=get_test_transforms((image_size[0], image_size[1])),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/engine/group_eval.py:27:                    crop_bbox=crop_bbox,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/engine/group_eval.py:68:        crop_bbox = config.data.crop_bbox, 
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/model.py:67:        # Pass img_size to timm for ViTs that need position embedding interpolation
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/model.py:69:        if kwargs.get('img_size'):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/model.py:70:            timm_kwargs['img_size'] = kwargs['img_size']
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:9:from torchvision import transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:11:def process_image(row, crop_bbox, preprocess_dir, chip_idx, target_size):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:13:    bbox = row['bbox']
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:14:    theta = row['theta'] if row['theta'] is not None else 0
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:20:    if crop_bbox:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:21:        image = get_chip_from_img(image, bbox, theta)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:27:    tensor_interpolate = transforms.Compose([transforms.ToTensor(), transforms.Resize((target_w, target_h), antialias=True, interpolation=transforms.InterpolationMode.BILINEAR), transforms.ToPILImage()])
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:38:def preprocess_images(df, crop_bbox, preprocess_dir, target_size, num_workers=None):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:42:        futures = [executor.submit(process_image, row, crop_bbox, preprocess_dir, chip_idx, target_size) for chip_idx, row in tqdm(df.iterrows(), total=len(df))]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:85:    crop_bbox = config.data.crop_bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:87:    df_train_full = preprocess_images(df_train_full, crop_bbox, preprocess_dir_train, target_size)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/images.py:88:    df_val_full = preprocess_images(df_val_full, crop_bbox, preprocess_dir_val, target_size)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:19:        bboxes,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:21:        thetas,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:22:        transform,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:25:        crop_bbox=True,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:30:        self.bboxes = bboxes
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:32:        self.transform = transform
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:34:        self.crop_bbox = crop_bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:37:        thetas = [t if t is not None else 0 for t in thetas]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:38:        self.thetas = thetas
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:65:            bbox = self.bboxes[idx]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:66:            theta = self.thetas[idx]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:73:            # if  self.crop_bbox:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:74:            #     x1, y1, w, h = bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:79:            #         self.bboxes[idx] = [0, 0, image.shape[1], image.shape[0]]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:81:            # Crop a rotated bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:82:            if self.crop_bbox:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:83:                image = get_chip_from_img(image, bbox, theta)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:89:        if self.transform is not None:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:90:            augmented = self.transform(image=image.copy())
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:92:            # image = self.transform(image.copy())
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:94:        return image, self.names[idx], self.image_paths[idx], torch.Tensor(self.bboxes[idx]), self.thetas[idx]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:3:from torchvision import transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:8:    unnormalize = transforms.Normalize((-aug_mean / aug_std).tolist(), (1.0 / aug_std).tolist())
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:19:## Functions for handling rotated bounding boxes
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:36:def rotate_box(x1,y1,x2,y2,theta):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:44:        [np.cos(theta), -np.sin(theta)],
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:45:        [np.sin(theta), np.cos(theta)]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:86:def get_chip_from_img(img, bbox, theta):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:87:    x1,y1,w,h = bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:93:    # Do a faster, regular crop if theta is negligible
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:94:    if abs(theta) < 0.1:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:95:        x1, y1, w, h = [max(0, int(x)) for x in bbox]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:98:        cropped_image = crop_rect(img, ((xm, ym), (x2-x1, y2-y1), theta))[0]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/helpers.py:102:        print(f'Using original image. Invalid parameters - theta: {theta}, bbox: {bbox}')
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:14:    "from transformers import AutoModel"
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:71:    "    crop_bbox=True,\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/examples/extract_and_evaluate.ipynb:334:    "    crop_bbox=True,\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:14:            cos(theta + m)wandb: ERROR Abnormal program exit
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:34:        # --------------------------- cos(theta) & phi(theta) ---------------------------
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:78:        cos_theta = torch.mm(embbedings, kernel_norm)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:79:        cos_theta = cos_theta.clamp(-1, 1)  # for numerical stability
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:81:        m_hot = torch.zeros(index.size()[0], cos_theta.size()[1], device=cos_theta.device)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:82:        margin = torch.normal(mean=self.m, std=self.std, size=label[index, None].size(), device=cos_theta.device) # Fast converge .clamp(self.m-self.std, self.m+self.std)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:85:                distmat = cos_theta[index, label.view(-1)].detach().clone()
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:91:        cos_theta.acos_()
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:92:        cos_theta[index] += m_hot
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:93:        cos_theta.cos_().mul_(self.s)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:95:        loss = self.loss_fn(cos_theta, label)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:176:        cos_theta = F.linear(embbedings, weight_norm)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:177:        cos_theta = cos_theta.clamp(-1, 1)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:179:        # Target logit: cos(theta_yi + m)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:180:        target_logit = cos_theta[torch.arange(cos_theta.size(0)), label]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:181:        sin_theta = torch.sqrt(1.0 - target_logit.pow(2)).clamp(0, 1)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:182:        cos_theta_m = target_logit * self.cos_m - sin_theta * self.sin_m  # cos(theta + m)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:186:            target_logit > self.threshold, cos_theta_m, target_logit - self.mm
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:190:        # For hard negatives (cos_theta_j > cos(theta_yi + m)): add penalty
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:195:        mask = cos_theta > cos_theta_m.unsqueeze(1)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:196:        hard_example = cos_theta[mask]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:197:        # Modulate: cos(theta_j + m) + cos(theta_j) * t for hard negatives
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:198:        cos_theta[mask] = hard_example * (self.t + hard_example)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:201:        cos_theta.scatter_(1, label.view(-1, 1), final_target.unsqueeze(1))
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:203:        output = cos_theta * self.s
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:236:        cos_theta = F.linear(embbedings, weight_norm)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:237:        cos_theta = cos_theta.clamp(-1, 1)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:240:        target_logit = cos_theta[torch.arange(cos_theta.size(0)), label]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:242:                              size=target_logit.size(), device=cos_theta.device)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:248:        sin_theta = torch.sqrt(1.0 - target_logit.pow(2)).clamp(0, 1)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:249:        cos_theta_m = target_logit * cos_m - sin_theta * sin_m  # cos(theta + m_elastic)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:252:            target_logit > threshold, cos_theta_m, target_logit - mm
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:259:        mask = cos_theta > cos_theta_m.unsqueeze(1)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:260:        hard_example = cos_theta[mask]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:261:        cos_theta[mask] = hard_example * (self.t + hard_example)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:264:        cos_theta.scatter_(1, label.view(-1, 1), final_target.unsqueeze(1))
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/models/heads.py:266:        output = cos_theta * self.s
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:1:## Functions for handling rotated bounding boxes
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:22:def rotate_box(x1,y1,x2,y2,theta):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:30:        [np.cos(theta), -np.sin(theta)],
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:31:        [np.sin(theta), np.cos(theta)]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:42:    """Crop a rotated rectangle from an image using PIL (no OpenCV)."""
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:63:    # PIL rotate uses degrees, counter-clockwise positive (same as cv2.getRotationMatrix2D)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:69:    pil_rotated = pil_canvas.rotate(angle_deg, resample=PILImage.BILINEAR, center=(new_center_x, new_center_y), fillcolor=(255, 255, 255))
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:71:    img_rot = np.array(pil_rotated)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:109:def get_chip_from_img(img, bbox, theta):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:110:    x1,y1,w,h = bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:116:    # Treat NaN/None/invalid theta as zero rotation
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:118:        theta_valid = float(theta)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:119:        if theta_valid != theta_valid:  # NaN check
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:120:            theta_valid = 0
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:122:        theta_valid = 0
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:123:    theta = theta_valid
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:125:    # Do a faster, regular crop if theta is negligible
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:126:    if abs(theta) < 0.1:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:127:        x1, y1, w, h = [max(0, int(x)) for x in bbox]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:130:        cropped_image = crop_rect(img, ((xm, ym), (x2-x1, y2-y1), theta))[0]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py:134:        print(f'Using original image. Invalid parameters - theta: {theta}, bbox: {bbox}')
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/default_dataset.py:11:    def __init__(self, csv, transforms=None, fliplr=False, fliplr_view=[], crop_bbox=False, n_train_classes=None):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/default_dataset.py:14:        self.augmentations = transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/default_dataset.py:17:        self.crop_bbox = crop_bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/default_dataset.py:29:        bbox = row['bbox']
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/default_dataset.py:30:        theta = row['theta'] if row['theta'] is not None else 0
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/default_dataset.py:33:        if self.crop_bbox:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/default_dataset.py:34:            image = get_chip_from_img(image, bbox, theta)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/default_dataset.py:46:                "image_idx": self.csv.index[index], "file_path": image_path, "bbox": torch.Tensor(bbox),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/default_dataset.py:47:                'theta': theta, 'species': species}
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:66:    bboxes = []
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:72:            batch_bbox = batch[3]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:88:            bboxes.extend(batch_bbox)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:90:    bboxes = [t.int().tolist() for t in bboxes]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:131:    qry_bbox = bboxes[qry_idx]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:133:    x1, y1, w, h = qry_bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:151:    db_bbox = bboxes[db_idx]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:152:    x1, y1, w, h = db_bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:182:    bboxes = []
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:183:    thetas = []
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:191:                batch_bbox = batch[3]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:192:                batch_theta = batch[4]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:197:                batch_bbox = batch["bbox"]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:198:                batch_theta = batch["theta"]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:213:            bboxes.extend(batch_bbox)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:215:            batch_theta = batch_theta.tolist()
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:216:            thetas.extend(batch_theta)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:219:    bboxes = [t.int().tolist() for t in bboxes]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:222:    return embeddings, labels, images, paths, bboxes, thetas
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:224:def draw_batch(device, test_loader, model, images_dir = '', method='hires_cam', eigen_smooth=False, render_transformed=False, show=False, use_cuda=True):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:231:    embeddings, labels, images, paths, bboxes, thetas = generate_embeddings(device, model, test_loader)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:295:        if render_transformed:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:301:            qry_bbox = bboxes[qry_idx]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:302:            # x1, y1, w, h = qry_bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:307:            qry_theta = thetas[qry_idx]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:308:            qry_float = get_chip_from_img(qry_float, qry_bbox, qry_theta)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:319:        if render_transformed:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:325:            db_bbox = bboxes[db_idx + i//2]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:326:            # x1, y1, w, h = db_bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:331:            db_theta = thetas[db_idx + i//2]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:332:            db_float = get_chip_from_img(db_float, db_bbox, db_theta)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/gradcam.py:346:        if render_transformed:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/tools.py:75:    merge_cols = ['uuid_x', 'bbox']
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:4:import torchvision.transforms as transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:9:from .pairx.xai_dataset import get_pretransform_img
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:17:    crop_bbox,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:28:        test_loader (DataLoader): Should contain two images, with 4 items for each (image, name, path, bbox as xywh).
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:46:    transformed_images = []
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:47:    pretransform_images = []
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:49:    # get transformed and untransformed images out of test_loader
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:51:        (transformed_image,), _, (path,), (bbox,), (theta,) = batch[:5]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:53:        if len(transformed_image.shape) == 3:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:54:            transformed_image = transformed_image.unsqueeze(0)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:56:        transformed_images.append(transformed_image.to(device))
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:58:        img_size = tuple(transformed_image.shape[-2:])
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:59:        pretransform_image = load_image(path)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:61:        if crop_bbox:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:62:            pretransform_image = get_chip_from_img(pretransform_image, bbox, theta)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:64:        pretransform_image = np.array(transforms.Resize(img_size)(Image.fromarray(pretransform_image)))
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:65:        pretransform_images.append(pretransform_image)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:67:    img_0, img_1 = transformed_images
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx_draw.py:68:    img_np_0, img_np_1 = pretransform_images
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/coco_helpers.py:71:        ## same as picture - no bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/coco_helpers.py:77:        annot = {'bbox': [x, y, w, h],
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/coco_helpers.py:78:                  'theta': 'none',
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/etl/coco_helpers.py:81:                  'segmentation_bbox': [x, y, w, h],
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/getters.py:20:# def get_dataloader(df_data, images_dir, cfg, transforms, shuffle=True):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/getters.py:24:#         transforms=transforms,
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/config.py:51:    crop_bbox: bool = False
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/config.py:90:    theta_zero: float
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/config.py:94:    img_size: int = None
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:14:def rotate_box(x1,y1,x2,y2,theta):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:22:        [np.cos(theta), -np.sin(theta)],
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:23:        [np.sin(theta), np.cos(theta)]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:64:def get_chip_from_img(img, bbox, theta):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:65:    x1,y1,w,h = bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:71:    # Do a faster, regular crop if theta is negligible
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:72:    if abs(theta) < 0.1:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:73:        x1, y1, w, h = [int(x) for x in bbox]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:76:        cropped_image = crop_rect(img, ((xm, ym), (x2-x1, y2-y1), theta))[0]
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:80:        print(f'Using original image. Invalid parameters - theta: {theta}, bbox: {bbox}')
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:85:def plot_images(df, species=None, filter_key="name", filter_value=None, large_grid=False, crop_bbox=False):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:95:    - crop_bbox: Boolean to control whether to crop images based on bounding boxes (default: False).
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:128:        theta = row['theta']
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:129:        bbox = row['bbox']
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:134:        if crop_bbox:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/plot_images.py:135:            img = get_chip_from_img(img, bbox, theta)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/requirements.txt:124:transformers==4.49.0
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:39:       model, img_size, img_transforms = wildme_multispecies_miewid(device)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:43:- `img_size`: A `(w, h)` tuple with the input image size of the model, e.g., `(440, 440)`.
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:44:- `img_transforms`: Any transforms that should be applied to the images before the model is called, as a function.
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:52:       img_0, img_1, img_np_0, img_np_1 = get_img_pair_from_paths(device, img_path_0, img_path_1, img_size, img_transforms)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:54:This loads the images with and without transforms applied, so that the untransformed images can be used for visualizations.
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:61:                      img_0,                     # First image, transformed
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:62:                      img_1,                     # Second image, transformed      
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:63:                      img_np_0,                  # First image, pretransform
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/README.md:64:                      img_np_1,                  # Second image, pretransform
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/example_loaders.py:1:from transformers import AutoModel
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/example_loaders.py:2:import torchvision.transforms as transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/example_loaders.py:11:    multispecies_img_transforms = transforms.Compose([transforms.Resize((440, 440)),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/example_loaders.py:12:                                                      transforms.ToTensor(),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/example_loaders.py:13:                                                      transforms.Normalize(mean=[0.485, 0.456, 0.406],
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/example_loaders.py:16:    return multispecies_model, (440,440), multispecies_img_transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:5:import torchvision.transforms as transforms
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:9:    def __init__(self, df, img_size=(440,440), img_transform=None, img_path=""):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:14:            self.bbox = True
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:18:            self.bbox = False
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:20:        self.theta = "theta" in df.columns
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:23:        self.img_size = img_size
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:24:        self.img_transform = img_transform
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:27:    def _load_image(self, row, transform=True):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:36:        if self.bbox:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:45:        if self.theta:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:46:            img = img.rotate(math.degrees(row["theta"]))
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:48:        img = transforms.Resize(self.img_size)(img)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:50:        if transform and self.img_transform:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:51:            return self.img_transform(img), name, annot
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:62:    def get_image_pretransform(self, annot_id):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:65:        return self._load_image(selected_row.iloc[0], transform=False)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:67:    def get_image_transformed(self, annot_id):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:76:        img_0, _, _ = self.get_image_transformed(annot_0)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:77:        img_1, _, _ = self.get_image_transformed(annot_1)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:82:        img_np_0, _, _ = self.get_image_pretransform(annot_0)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:83:        img_np_1, _, _ = self.get_image_pretransform(annot_1)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:87:def get_img_pair_from_paths(device, img_path_0, img_path_1, img_size, img_transform):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:88:    def get_img_pair_from_path(img_path, transform):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:93:            img = transforms.Resize(img_size)(img)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:95:            if transform:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:96:                return img_transform(img).unsqueeze(0).to(device)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:100:    return (get_img_pair_from_path(img_path_0, transform=True),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:101:            get_img_pair_from_path(img_path_1, transform=True),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:102:            get_img_pair_from_path(img_path_0, transform=False),
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:103:            get_img_pair_from_path(img_path_1, transform=False),)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:105:def get_pretransform_img(img_path, img_size, bbox=None):
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:110:    if bbox is not None:
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:111:        x, y, w, h = bbox
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/xai_dataset.py:122:    img = transforms.Resize(img_size)(img)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/demo.py:19:    model, img_size, img_transforms = wildme_multispecies_miewid(device)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/demo.py:22:    dataset = XAIDataset(df, img_size, img_transforms)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/demo.py:31:        img_0, img_1, img_np_0, img_np_1 = get_img_pair_from_paths(device, f"data/{data_0}", f"data/{data_1}", img_size, img_transforms)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/demo.py:39:    #img_0_1, img_1_1, img_np_0_1, img_np_1_1 = get_img_pair_from_paths(device, "data/smores_radiator.jpg", "data/smores_sink.jpg", img_size, img_transforms)
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/demo.py:41:    pairx_imgs = explain(torch.cat(imgs_0),                  # transformed image 0
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/demo.py:42:                        torch.cat(imgs_1),                  # transformed image 1
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/demo.py:43:                        imgs_np_0,               # untransformed image 0
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/demo.py:44:                        imgs_np_1,               # untransformed image 1
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/environment.yml:20:    - transformers==4.49.0
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/core.py:274:        img_0 (torch.Tensor): The first input image, with any transforms applied.
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/core.py:275:        img_1 (torch.Tensor): The second input image, with any transforms applied.
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/core.py:276:        img_np_0 (numpy.ndarray): The first image, resized but not otherwise transformed, as a NumPy array.
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/visualization/pairx/core.py:277:        img_np_1 (numpy.ndarray): The second image, resized but not otherwise transformed, as a NumPy array.
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/EDA_split_data.ipynb:368:    "    plot_images(df, species=species, crop_bbox=True)\n",
/mnt/c/wbia-plugin-miew-id/wbia_miew_id/helpers/split/EDA_split_data.ipynb:374:    "    plot_images(df, species=species, filter_key=\"name_viewpoint\", filter_value=random_class, crop_bbox=True)"

exec
/bin/bash -lc "nl -ba /mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py | sed -n '1,105p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py | sed -n '600,625p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/transforms.py | sed -n '68,88p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/helpers.py | sed -n '90,140p'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 265ms:
   600	def _load_data(ibs, aid_list, config, multithread=False, batch_size=None):
   601	    r"""
   602	    Load data, preprocess and create data loaders
   603	    """
   604	
   605	    test_transform = get_test_transforms((config.data.image_size[0], config.data.image_size[1]))
   606	    image_paths = ibs.get_annot_image_paths(aid_list)
   607	    bboxes = ibs.get_annot_bboxes(aid_list)
   608	    names = ibs.get_annot_name_rowids(aid_list)
   609	    viewpoints = ibs.get_annot_viewpoints(aid_list)
   610	    thetas = ibs.get_annot_thetas(aid_list)
   611	    chips = ibs.get_annot_chips(aid_list)
   612	
   613	    dataset = PluginDataset(
   614	        chips,
   615	        image_paths,
   616	        names,
   617	        bboxes,
   618	        viewpoints,
   619	        thetas,
   620	        test_transform,
   621	        fliplr=config.test.fliplr,
   622	        fliplr_view=config.test.fliplr_view,
   623	        use_chips=True
   624	    )
   625	

 succeeded in 265ms:
     1	# -*- coding: utf-8 -*-
     2	from torch.utils.data import Dataset
     3	import cv2
     4	import numpy as np
     5	import torch
     6	from .helpers import get_chip_from_img
     7	
     8	
     9	class PluginDataset(Dataset):
    10	    """Dataset to load animal data for inference.
    11	    Used in plugin.
    12	    """
    13	
    14	    def __init__(
    15	        self,
    16	        chips,
    17	        image_paths,
    18	        names,
    19	        bboxes,
    20	        viewpoints,
    21	        thetas,
    22	        transform,
    23	        fliplr=False,
    24	        fliplr_view=None,
    25	        crop_bbox=True,
    26	        use_chips=True
    27	    ):
    28	        self.chips = chips
    29	        self.image_paths = image_paths
    30	        self.bboxes = bboxes
    31	        self.names = names
    32	        self.transform = transform
    33	        self.viewpoints = viewpoints
    34	        self.crop_bbox = crop_bbox
    35	        self.use_chips = use_chips
    36	
    37	        thetas = [t if t is not None else 0 for t in thetas]
    38	        self.thetas = thetas
    39	
    40	        if fliplr:
    41	            assert isinstance(fliplr_view, list) and all(
    42	                isinstance(item, str) for item in fliplr_view
    43	            )
    44	
    45	        self.fliplr = fliplr
    46	        self.fliplr_view = fliplr_view
    47	
    48	    def load_image(self, image_path):
    49	        image = cv2.imread(image_path)
    50	        if len(image.shape) == 2: 
    51	            image = cv2.cvtColor(image, cv2.COLOR_GRAY2RGB)
    52	        else:
    53	            image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    54	        return image
    55	
    56	    def __len__(self):
    57	        return len(self.image_paths)
    58	
    59	    def __getitem__(self, idx):
    60	
    61	        if self.use_chips:
    62	            image = self.chips[idx]
    63	        else:
    64	            image_path = self.image_paths[idx]
    65	            bbox = self.bboxes[idx]
    66	            theta = self.thetas[idx]
    67	
    68	            image = self.load_image(image_path)
    69	            if image is None:
    70	                raise ValueError('Fail to read {}'.format(self.image_paths[id]))
    71	
    72	            # # Crop bounding box area
    73	            # if  self.crop_bbox:
    74	            #     x1, y1, w, h = bbox
    75	            #     image = image[y1 : y1 + h, x1 : x1 + w]
    76	            #     if min(image.shape) < 1:
    77	            #         # Use original image
    78	            #         image = self.load_image(image_path)
    79	            #         self.bboxes[idx] = [0, 0, image.shape[1], image.shape[0]]
    80	            
    81	            # Crop a rotated bbox
    82	            if self.crop_bbox:
    83	                image = get_chip_from_img(image, bbox, theta)
    84	
    85	        if self.fliplr:
    86	            if self.viewpoints[idx] in self.fliplr_view:
    87	                image = np.fliplr(image)
    88	
    89	        if self.transform is not None:
    90	            augmented = self.transform(image=image.copy())
    91	            image = augmented['image']
    92	            # image = self.transform(image.copy())
    93	            
    94	        return image, self.names[idx], self.image_paths[idx], torch.Tensor(self.bboxes[idx]), self.thetas[idx]

 succeeded in 225ms:
    68	def get_valid_transforms(image_size):
    69	    return albumentations.Compose(
    70	        [
    71	            albumentations.Resize(image_size[0], image_size[1], always_apply=True),
    72	            albumentations.Normalize(),
    73	            ToTensorV2(p=1.0)
    74	        ]
    75	    )
    76	
    77	def get_test_transforms(image_size):
    78	    return albumentations.Compose(
    79	        [
    80	            albumentations.Resize(image_size[0], image_size[1], always_apply=True),
    81	            albumentations.Normalize(),
    82	            ToTensorV2(p=1.0)
    83	        ]
    84	    )

 succeeded in 240ms:
    90	    # Clamp to image bounds
    91	    x1c = max(0, x1)
    92	    y1c = max(0, y1)
    93	    x2c = min(new_width, x2)
    94	    y2c = min(new_height, y2)
    95	
    96	    img_crop = img_rot[y1c:y2c, x1c:x2c]
    97	
    98	    # Pad if crop extends beyond canvas
    99	    if img_crop.shape[0] != crop_h or img_crop.shape[1] != crop_w:
   100	        padded = np.ones((crop_h, crop_w, 3), dtype=img.dtype) * 255
   101	        paste_x = x1c - x1
   102	        paste_y = y1c - y1
   103	        padded[paste_y:paste_y+img_crop.shape[0], paste_x:paste_x+img_crop.shape[1]] = img_crop
   104	        img_crop = padded
   105	
   106	    return img_crop, img_rot
   107	
   108	
   109	def get_chip_from_img(img, bbox, theta):
   110	    x1,y1,w,h = bbox
   111	    x2 = x1 + w
   112	    y2 = y1 + h
   113	    xm = (x1 + x2) // 2
   114	    ym = (y1 + y2) // 2
   115	
   116	    # Treat NaN/None/invalid theta as zero rotation
   117	    try:
   118	        theta_valid = float(theta)
   119	        if theta_valid != theta_valid:  # NaN check
   120	            theta_valid = 0
   121	    except (TypeError, ValueError):
   122	        theta_valid = 0
   123	    theta = theta_valid
   124	
   125	    # Do a faster, regular crop if theta is negligible
   126	    if abs(theta) < 0.1:
   127	        x1, y1, w, h = [max(0, int(x)) for x in bbox]
   128	        cropped_image = img[y1 : y1 + h, x1 : x1 + w]
   129	    else:
   130	        cropped_image = crop_rect(img, ((xm, ym), (x2-x1, y2-y1), theta))[0]
   131	
   132	    if min(cropped_image.shape) < 1:
   133	        # Use original image
   134	        print(f'Using original image. Invalid parameters - theta: {theta}, bbox: {bbox}')
   135	        cropped_image = img
   136	
   137	    return cropped_image

exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java | sed -n '28,42p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java | sed -n '100,130p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java | sed -n '740,770p;1848,1882p'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 220ms:
   100	                if (currentUser == null) {
   101	                    rtn.put("statusCode", 401);
   102	                    rtn.put("error", "access denied");
   103	                } else {
   104	                    if ((args.length > 2) && ("match-results".equals(args[2]))) {
   105	                        Task task = myShepherd.getTask(args[1]);
   106	                        if (task == null) {
   107	                            rtn.put("statusCode", 404);
   108	                            rtn.put("error", "not found");
   109	                        } else {
   110	                            // right now we replicate legacy functionality and allow access to anyone
   111	                            // based on task id only, rather than blocking based on task.canUserAccess()
   112	                            int prospectsSize = org.ecocean.ia.MatchResult.DEFAULT_PROSPECTS_CUTOFF;
   113	                            Set<String> projectIds = null;
   114	                            String[] pvals = request.getParameterValues("projectId");
   115	                            if ((pvals != null) && (pvals.length > 0))
   116	                                projectIds = new HashSet<String>(Arrays.asList(
   117	                                    request.getParameterValues("projectId")));
   118	                            try {
   119	                                // note: negative size means all of them (no cutoff)
   120	                                prospectsSize = Integer.parseInt(request.getParameter(
   121	                                    "prospectsSize"));
   122	                            } catch (NumberFormatException ex) {}
   123	                            rtn.put("prospectsSize", prospectsSize);
   124	                            JSONObject mrJson = task.matchResultsJson(prospectsSize, projectIds,
   125	                                myShepherd);
   126	                            rtn.put("projectIds", projectIds);
   127	                            rtn.put("matchResultsRoot", mrJson);
   128	                            rtn.put("success", true);
   129	                            rtn.put("statusCode", 200);
   130	                            // this means we created on-the-fly some MatchResult(s) that need persisting

 succeeded in 230ms:
    28	
    29	public class Annotation extends Base implements java.io.Serializable {
    30	    public Annotation() {}
    31	    private String id;
    32	    // Number of nearest neighbors the OpenSearch knn query returns
    33	    // for vector matching. Was 4, which after self-encounter exclusion
    34	    // produced only ~3 prospects regardless of how large the matching
    35	    // set was — far fewer than the legacy WBIA paths. Bumped to 50 so
    36	    // vector matching can populate the match-results page comparably
    37	    // to MiewID's 12-default and HotSpotter's larger result sets.
    38	    public static final int KNN_K_DISTANCE_VALUE = 50;
    39	    private static final String[][] VALID_VIEWPOINTS = new String[][] {
    40	        { "up", "up", "up", "up", "up", "up", "up", "up", }, {
    41	            "upfront", "upfrontright", "upright", "upbackright", "upback", "upbackleft", "upleft",
    42	                "upfrontleft"

 succeeded in 281ms:
   740	
   741	        if (jres == null) {
   742	            System.out.println(
   743	                "WARNING: getTaskResultsAsHashMap() had null results from getTaskResults(" +
   744	                taskID + "); return empty HashMap");
   745	            return res;
   746	        }
   747	        res.put("taskID", taskID);
   748	        if (jres.has("success")) res.put("success", jres.get("success"));
   749	        if (jres.has("error")) res.put("error", jres.get("error"));
   750	        if (jres.has("results")) {
   751	            HashMap<String, Object> rout = new HashMap<String, Object>();
   752	            JSONArray r = jres.getJSONArray("results");
   753	            Shepherd myShepherd = new Shepherd(context);
   754	            myShepherd.setAction("IBEISIA.getTaskResultsAsHashMap");
   755	            myShepherd.beginDBTransaction();
   756	            for (int i = 0; i < r.length(); i++) {
   757	                if (r.getJSONObject(i).has("query_annot_uuid")) {
   758	                    HashMap<String, Double> scores = new HashMap<String, Double>();
   759	                    JSONArray m = r.getJSONObject(i).getJSONArray("match_annot_list");
   760	                    JSONArray s = r.getJSONObject(i).getJSONArray("score_list");
   761	                    for (int j = 0; j < m.length(); j++) {
   762	                        Encounter menc = Encounter.findByAnnotationId(m.getString(j), myShepherd);
   763	                        scores.put(menc.getCatalogNumber(), s.getDouble(j));
   764	                    }
   765	                    Encounter enc = Encounter.findByAnnotationId(r.getJSONObject(i).getString(
   766	                        "query_annot_uuid"), myShepherd);
   767	                    rout.put(enc.getCatalogNumber(), scores);
   768	                }
   769	            }
   770	            myShepherd.rollbackDBTransaction();
  1848	        if (needReview) {
  1849	            jlog.put("needReviewMap", needReviewMap);
  1850	            for (String id : needReviewMap.keySet()) {
  1851	                if (!anns.containsKey(id)) {
  1852	                    System.out.println(
  1853	                        "WARNING: processCallbackIdentify() unable to load Annotation " + id +
  1854	                        " to set identificationStatus");
  1855	                } else {
  1856	                    anns.get(id).setIdentificationStatus(STATUS_PENDING);
  1857	                }
  1858	            }
  1859	            for (String aid : anns.keySet()) { // set annots *not* in needReviewMap complete.  (will there even be any?)
  1860	                if (!needReviewMap.keySet().contains(aid)) {
  1861	                    anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
  1862	                    needNameResolution.add(anns.get(aid));
  1863	                }
  1864	            }
  1865	        } else {
  1866	            for (String aid : anns.keySet()) {
  1867	                anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
  1868	                needNameResolution.add(anns.get(aid));
  1869	            }
  1870	            jlog.put("loopComplete", true);
  1871	            rtn.put("loopComplete", true);
  1872	            jlog.put("_infDict", infDict);
  1873	            exitIdentificationLoop(infDict, myShepherd);
  1874	        }
  1875	        resolveNames(needNameResolution, j.optJSONObject("cm_dict"), myShepherd);
  1876	        log(taskID, null, jlog, myShepherd.getContext());
  1877	
  1878	        // set "completed" on Task
  1879	        Task task = myShepherd.getTask(taskID);
  1880	        if (task != null) {
  1881	            task.setStatus("completed");
  1882	            task.setCompletionDateInMilliseconds();

exec
/bin/bash -lc "rg -n \"validForIdentification|markSent|sendAnnotationsAsNeeded|sendAnnotations\\(\" /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java | sed -n '120,215p;800,830p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java | sed -n '1030,1085p'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 239ms:
159:            markSent(ma);
167:    public static JSONObject __sendAnnotations(ArrayList<Annotation> anns, String context,
172:            System.out.println("WARNING: sendAnnotations() called without IA primed");
187:            if (!validForIdentification(ann, context)) {
188:                System.out.println("WARNING: IBEISIA.sendAnnotations() skipping invalid " + ann);
211:            markSent(ann);
214:        System.out.println("sendAnnotations(): sending " + ct);
267:            if (!validForIdentification(ann, context)) {
312:                if (!validForIdentification(ann, context)) {
823:                    __sendAnnotations(anns, context, myShepherd);
881:                if (validForIdentification(ann, myShepherd.getContext())) {
917:            results.put("sendAnnotationsAsNeeded", sendAnnotationsAsNeeded(allAnns, myShepherd));
1103:    private static void markSent(MediaAsset ma) {
1112:    private static void markSent(Annotation ann) {
1275:        if (validForIdentification(ann, context) && iaConf.isValidIAClass(taxonomyBeforeDetection,
1475:                        if (ann != null && IBEISIA.validForIdentification(ann,
3581:    public static boolean validForIdentification(Annotation ann) {
3582:        return validForIdentification(ann, null);
3585:    public static boolean validForIdentification(Annotation ann, String context) {
3618:            System.out.println("NOTE: IBEISIA.validForIdentification() failing " + ann.toString() +
3691:        return plugin.sendAnnotations(anns, true, myShepherd);
3694:    public static JSONObject sendAnnotationsAsNeeded(ArrayList<Annotation> anns,
3698:        Util.mark("sendAnnotationsAsNeeded -in- ", tt);
3708:        Util.mark("sendAnnotationsAsNeeded 1 ", tt);
3712:        Util.mark("sendAnnotationsAsNeeded 2-hs ", tt);
3725:        Util.mark("sendAnnotationsAsNeeded 3-hs ", tt);
3731:            Util.mark("sendAnnotationsAsNeeded 4 ", tt);
3733:                rtn.put("sendAnnotations", plugin.sendAnnotations(annsToSend, false, myShepherd));
3737:        Util.mark("sendAnnotationsAsNeeded -out- ", tt);

 succeeded in 245ms:
   120	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   121	        map.put("image_uri_list", new ArrayList<JSONObject>());
   122	        map.put("image_uuid_list", new ArrayList<JSONObject>());
   123	        map.put("image_width_list", new ArrayList<Integer>());
   124	        map.put("image_height_list", new ArrayList<Integer>());
   125	        map.put("image_time_posix_list", new ArrayList<Integer>());
   126	        map.put("image_gps_lat_list", new ArrayList<Double>());
   127	        map.put("image_gps_lon_list", new ArrayList<Double>());
   128	        for (MediaAsset ma : mas) {
   129	            if (!needToSend(ma)) continue;
   130	            ImageAttributes iatt = ma.getImageAttributes();
   131	            int w = 0;
   132	            int h = 0;
   133	            if (iatt != null) {
   134	                w = (int)iatt.getWidth();
   135	                h = (int)iatt.getHeight();
   136	            }
   137	            // we are *required* to have a width/height to pass to IA, so lets skip...
   138	            if ((w < 1) || (h < 1)) {
   139	                System.out.println("WARNING: IBEISIA.sendMediaAssets() skipping " + ma.toString() +
   140	                    " - unable to find width/height");
   141	                continue;
   142	            }
   143	            map.get("image_width_list").add(w);
   144	            map.get("image_height_list").add(h);
   145	
   146	            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
   147	            map.get("image_uuid_list").add(toFancyUUID(uuidToSend));
   148	            map.get("image_uri_list").add(mediaAssetToUri(ma));
   149	
   150	            map.get("image_gps_lat_list").add(ma.getLatitude());
   151	            map.get("image_gps_lon_list").add(ma.getLongitude());
   152	
   153	            DateTime t = ma.getDateTime();
   154	            if (t == null) {
   155	                map.get("image_time_posix_list").add(0);
   156	            } else {
   157	                map.get("image_time_posix_list").add((int)Math.floor(t.getMillis() / 1000)); // IBIES-IA wants seconds since epoch
   158	            }
   159	            markSent(ma);
   160	            ct++;
   161	        }
   162	        System.out.println("sendMediaAssets(): sending " + ct);
   163	        if (ct < 1) return null; // null for "none to send" ?  is this cool?
   164	        return RestClient.post(url, hashMapToJSONObject(map));
   165	    }
   166	
   167	    public static JSONObject __sendAnnotations(ArrayList<Annotation> anns, String context,
   168	        Shepherd myShepherd)
   169	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   170	        InvalidKeyException {
   171	        if (!isIAPrimed())
   172	            System.out.println("WARNING: sendAnnotations() called without IA primed");
   173	        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
   174	        if (u == null)
   175	            throw new MalformedURLException(
   176	                      "configuration value IBEISIARestUrlAddAnnotations is not set");
   177	        URL url = new URL(u);
   178	        int ct = 0;
   179	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   180	        map.put("image_uuid_list", new ArrayList<String>());
   181	        map.put("annot_uuid_list", new ArrayList<String>());
   182	        map.put("annot_species_list", new ArrayList<String>());
   183	        map.put("annot_bbox_list", new ArrayList<int[]>());
   184	        map.put("annot_name_list", new ArrayList<String>());
   185	        for (Annotation ann : anns) {
   186	            if (!needToSend(ann)) continue;
   187	            if (!validForIdentification(ann, context)) {
   188	                System.out.println("WARNING: IBEISIA.sendAnnotations() skipping invalid " + ann);
   189	                continue;
   190	            }
   191	            // Try and get an iaClass from the  annotation. If detection ran correctly.. it should be there.
   192	            // I guess fall back on the species from ann if you don't find anything? Maybe you shouldn't... because detect shouldn't have anything to
   193	            // do
   194	            // with the human friendly "species", just ia class. Oh well, doing it anyway for now.. FIGHT ME ABOUT IT
   195	            String iaClass = null;
   196	            if (Util.stringExists(ann.getIAClass())) {
   197	                iaClass = ann.getIAClass();
   198	                System.out.println("iaClass set from Annotation.");
   199	            } else {
   200	                System.out.println(
   201	                    "===> CRITICAL ERROR: Annotation did not have a useable class candidate to send to identification for iaClass. ");
   202	                continue;
   203	            }
   204	            int[] bbox = ann.getBbox();
   205	            map.get("annot_bbox_list").add(bbox);
   206	            map.get("image_uuid_list").add(toFancyUUID(ann.getMediaAsset().getUUID()));
   207	            map.get("annot_uuid_list").add(toFancyUUID(ann.getUUID()));
   208	            map.get("annot_species_list").add(iaClass);
   209	            String name = ann.findIndividualId(myShepherd);
   210	            map.get("annot_name_list").add((name == null) ? "____" : name);
   211	            markSent(ann);
   212	            ct++;
   213	        }
   214	        System.out.println("sendAnnotations(): sending " + ct);
   215	        if (ct < 1) return null;
   800	                    String uuid = fromFancyUUID(list.getJSONObject(i));
   801	                    System.out.println("**** FAKE ATTEMPT to sendMediaAssets: uuid=" + uuid);
   802	                    // TODO: actually send the mediaasset duh
   803	                }
   804	            }
   805	        }
   806	        if ((res.getJSONObject("response") != null) &&
   807	            res.getJSONObject("response").has("missing_annot_uuid_list")) {
   808	            JSONArray list = res.getJSONObject("response").getJSONArray("missing_annot_uuid_list");
   809	            if (list.length() > 0) {
   810	                ArrayList<Annotation> anns = new ArrayList<Annotation>();
   811	                try {
   812	                    for (int i = 0; i < list.length(); i++) {
   813	                        String acmId = fromFancyUUID(list.getJSONObject(i));
   814	                        ArrayList<Annotation> annsTemp = myShepherd.getAnnotationsWithACMId(acmId);
   815	                        anns.add(annsTemp.get(0));
   816	                    }
   817	                } catch (Exception e) { e.printStackTrace(); }
   818	                // would this ever recurse? seems like a 600 would only happen inside sendAnnotations for missing MediaAssets.
   819	                System.out.println("**** attempting to make up for missing Annotation(s): " +
   820	                    anns.toString());
   821	                JSONObject srtn = null;
   822	                try {
   823	                    __sendAnnotations(anns, context, myShepherd);
   824	                } catch (Exception ex) {}
   825	                System.out.println(" returned --> " + srtn);
   826	                if ((srtn != null) && (srtn.getJSONObject("status") != null) &&
   827	                    srtn.getJSONObject("status").getBoolean("success")) tryAgain = true;    // it "worked"?
   828	            }
   829	        }
   830	        System.out.println("iaCheckMissing -> " + tryAgain);

 succeeded in 284ms:
  1030	            return null;
  1031	        }
  1032	        for (int i = logs.size() - 1; i >= 0; i--) {
  1033	            if (logs.get(i).getTaskID() != null) {
  1034	                String id = logs.get(i).getTaskID();
  1035	                myShepherd.rollbackDBTransaction();
  1036	                myShepherd.closeDBTransaction();
  1037	                return id;
  1038	            } // get first one we find. too bad!
  1039	        }
  1040	        myShepherd.rollbackDBTransaction();
  1041	        myShepherd.closeDBTransaction();
  1042	        return null;
  1043	    }
  1044	
  1045	    public static String[] findTaskIDsFromObjectID(String objectID, Shepherd myShepherd) {
  1046	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByObjectID(SERVICE_NAME,
  1047	            objectID, myShepherd);
  1048	
  1049	        if ((logs == null) || (logs.size() < 1)) return null;
  1050	        String[] ids = new String[logs.size()];
  1051	        int ct = 0;
  1052	        for (IdentityServiceLog l : logs) {
  1053	            if (l.getTaskID() == null) continue;
  1054	            if (Arrays.asList(ids).contains(l.getTaskID())) continue;
  1055	            ids[ct] = l.getTaskID();
  1056	            ct++;
  1057	        }
  1058	        return ids;
  1059	    }
  1060	
  1061	    public static String findJobIDFromTaskID(String taskID, String context) {
  1062	        Shepherd myShepherd = new Shepherd(context);
  1063	
  1064	        myShepherd.setAction("IBEISIA.findJobIDFromTaskID");
  1065	        myShepherd.beginDBTransaction();
  1066	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, SERVICE_NAME,
  1067	            myShepherd);
  1068	        if ((logs == null) || (logs.size() < 1)) {
  1069	            myShepherd.rollbackDBTransaction();
  1070	            myShepherd.closeDBTransaction();
  1071	            return null;
  1072	        }
  1073	        String jobID = logs.get(logs.size() - 1).getServiceJobID();
  1074	        if ("-1".equals(jobID)) {
  1075	            myShepherd.rollbackDBTransaction();
  1076	            myShepherd.closeDBTransaction();
  1077	            return null;
  1078	        }
  1079	        myShepherd.rollbackDBTransaction();
  1080	        myShepherd.closeDBTransaction();
  1081	        return jobID;
  1082	    }
  1083	
  1084	    // IBEIS-IA wants a uuid as a single-key json object like: {"__UUID__": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxx"} so we use these to go back and
  1085	    // forth

exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java | sed -n '3578,3625p;3690,3738p'" in /mnt/c/claude-skills/kb/ml-service
exec
/bin/bash -lc "nl -ba /mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java | sed -n '1084,1120p'" in /mnt/c/claude-skills/kb/ml-service
 succeeded in 242ms:
  3578	        return false;
  3579	    }
  3580	
  3581	    public static boolean validForIdentification(Annotation ann) {
  3582	        return validForIdentification(ann, null);
  3583	    }
  3584	
  3585	    public static boolean validForIdentification(Annotation ann, String context) {
  3586	        if (ann == null) return false;
  3587	        String acmId = ann.getAcmId();
  3588	        /*
  3589	            NOTE: we need to allow for case where ann.acmId is null; namely, this is an annot IA knows nothing about.
  3590	            in this case, we will be keeping this out of AnnotationLite.cache, as it should get added later
  3591	         */
  3592	        AnnotationLite annl = null;
  3593	        if (acmId != null) {
  3594	            annl = AnnotationLite.getCache(acmId);
  3595	            if ((annl != null) && (annl.getValidForIdentification() != null))
  3596	                return annl.getValidForIdentification();
  3597	        }
  3598	        // System.out.println("BBOX features -> " + ann.getFeatures()); //please leave this line in (ask jon... sigh)
  3599	        List<Feature> forceJdoToUnpackTheseFeatures = ann.getFeatures();
  3600	        String ungodlyHackString = "";
  3601	        if (forceJdoToUnpackTheseFeatures != null)
  3602	            ungodlyHackString = forceJdoToUnpackTheseFeatures.toString();
  3603	        int[] bbox = ann.getBbox();
  3604	        if (bbox == null) {
  3605	            System.out.println("NOTE: IBEISIA.validToSendToIA() failing " + ann.toString() +
  3606	                " - invalid bbox");
  3607	            if (acmId != null) {
  3608	                if (annl == null) {
  3609	                    annl = new AnnotationLite(false);
  3610	                } else {
  3611	                    annl.setValidForIdentification(false);
  3612	                }
  3613	                AnnotationLite.setCache(acmId, annl);
  3614	            }
  3615	            return false;
  3616	        }
  3617	        if (context != null && !validIAClassForIdentification(ann, context) && !ann.isTrivial()) {
  3618	            System.out.println("NOTE: IBEISIA.validForIdentification() failing " + ann.toString() +
  3619	                " - annotation does not have valid Identification class.");
  3620	            if (acmId != null) {
  3621	                if (annl == null) {
  3622	                    annl = new AnnotationLite(false);
  3623	                } else {
  3624	                    annl.setValidForIdentification(false);
  3625	                }
  3690	
  3691	        return plugin.sendAnnotations(anns, true, myShepherd);
  3692	    }
  3693	
  3694	    public static JSONObject sendAnnotationsAsNeeded(ArrayList<Annotation> anns,
  3695	        Shepherd myShepherd) {
  3696	        long tt = System.currentTimeMillis();
  3697	
  3698	        Util.mark("sendAnnotationsAsNeeded -in- ", tt);
  3699	        JSONObject rtn = new JSONObject();
  3700	        rtn.put("numAnnotsTotal", Util.collectionSize(anns));
  3701	        if (Util.collectionIsEmptyOrNull(anns)) return rtn;
  3702	        WildbookIAM plugin = getPluginInstance(myShepherd.getContext());
  3703	        ArrayList<Annotation> annsToSend = new ArrayList<Annotation>();
  3704	        // List<String> iaAnnotIds = plugin.iaAnnotationIds();
  3705	        HashSet<String> iaAnnotIds = new HashSet(plugin.iaAnnotationIds());
  3706	        if (iaAnnotIds.isEmpty())
  3707	            throw new RuntimeException("iaAnnotIds is empty; possible IA problems");
  3708	        Util.mark("sendAnnotationsAsNeeded 1 ", tt);
  3709	        ArrayList<MediaAsset> masToSend = new ArrayList<MediaAsset>();
  3710	        // List<String> iaImageIds = plugin.iaImageIds();  //in a better world we would do this *after* we have built up masToSend
  3711	        HashSet<String> iaImageIds = null;
  3712	        Util.mark("sendAnnotationsAsNeeded 2-hs ", tt);
  3713	        for (Annotation ann : anns) {
  3714	            if (iaAnnotIds.contains(ann.getAcmId())) continue;
  3715	            MediaAsset ma = ann.getMediaAsset();
  3716	            if (ma == null) continue; // snh #bad
  3717	            annsToSend.add(ann);
  3718	            // get iaImageIds only if we need it
  3719	            if (iaImageIds == null) iaImageIds = new HashSet(plugin.iaImageIds());
  3720	            if (iaImageIds.isEmpty())
  3721	                throw new RuntimeException("iaImageIds is empty; possible IA problems");
  3722	            if (iaImageIds.contains(ma.getAcmId())) continue;
  3723	            masToSend.add(ma);
  3724	        }
  3725	        Util.mark("sendAnnotationsAsNeeded 3-hs ", tt);
  3726	        rtn.put("numAnnotsToSend", Util.collectionSize(annsToSend));
  3727	        rtn.put("numAssetsToSend", Util.collectionSize(masToSend));
  3728	        try {
  3729	            if (!Util.collectionIsEmptyOrNull(masToSend))
  3730	                rtn.put("sendMediaAssets", plugin.sendMediaAssets(masToSend, false));
  3731	            Util.mark("sendAnnotationsAsNeeded 4 ", tt);
  3732	            if (!Util.collectionIsEmptyOrNull(annsToSend))
  3733	                rtn.put("sendAnnotations", plugin.sendAnnotations(annsToSend, false, myShepherd));
  3734	        } catch (Exception ex) {
  3735	            rtn.put("sendAnnotMAException", ex.toString());
  3736	        }
  3737	        Util.mark("sendAnnotationsAsNeeded -out- ", tt);
  3738	        return rtn;

 succeeded in 252ms:
  1084	    // IBEIS-IA wants a uuid as a single-key json object like: {"__UUID__": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxx"} so we use these to go back and
  1085	    // forth
  1086	    public static String fromFancyUUID(JSONObject u) {
  1087	        if (u == null) return null;
  1088	        return u.optString("__UUID__", null);
  1089	    }
  1090	
  1091	    public static JSONObject toFancyUUID(String u) {
  1092	        JSONObject j = new JSONObject();
  1093	
  1094	        j.put("__UUID__", u);
  1095	        return j;
  1096	    }
  1097	
  1098	    private static boolean needToSend(MediaAsset ma) {
  1099	        // return true;
  1100	        return ((alreadySentMA.get(ma.getId()) == null) || !alreadySentMA.get(ma.getId()));
  1101	    }
  1102	
  1103	    private static void markSent(MediaAsset ma) {
  1104	        alreadySentMA.put(ma.getIdInt(), true);
  1105	    }
  1106	
  1107	    private static boolean needToSend(Annotation ann) {
  1108	        // return true;
  1109	        return ((alreadySentAnn.get(ann.getId()) == null) || !alreadySentAnn.get(ann.getId()));
  1110	    }
  1111	
  1112	    private static void markSent(Annotation ann) {
  1113	        alreadySentAnn.put(ann.getId(), true);
  1114	    }
  1115	
  1116	/*
  1117	    this is the re-tooling of this method which does nothing with encounter(s)
  1118	    REMINDER: shouldUpdateSpeciesFromIa() no longer gets called here and thus should be called once all annots are made
  1119	 */
  1120	    public static Annotation createAnnotationFromIAResult(JSONObject jann, MediaAsset asset,

codex
**RCA**

The largest verified divergence is WBIA’s MiewID “knn” path. It does **not** score the full 15K target set. In [knn.py](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/metrics/knn.py:20), it hard-caps nearest-neighbor search at `min(50, len(db_emb))`, and [pred_light](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/metrics/knn.py:50) returns only those neighbors. Then [aid_scores_from_score_dict](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:818) assigns `0` to every requested `daid` not present in that top-50 dict. That explains the legacy cliff to `0.000`; most legacy “prospects” are sentinel zeros, not scored MiewID comparisons.

Candidate-set construction is mostly shared now. Legacy `getMatchingSet(...)` delegates to `getMatchingSetQuery(...)` at [Annotation.java:1145](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1145). The vector path also builds its count/query through [Embedding.java:318](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:318). The filter logic for `matchingSetFilter.locationIds`, owner, and project lives in [Annotation.java:1036](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1036). So `getMatchingSet` vs `getMatchingSetQuery` is not the likely primary cause, assuming the task params are present.

There are still call-site hazards. `IAGateway` precomputes `matchingSet = ann.getMatchingSet(myShepherd)` without task params when `limitTargetSize > -1` at [IAGateway.java:418](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:418). The normal legacy path passes `task.getParameters()` into `getMatchingSet` at [IBEISIA.java:896](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:896). If `limitTargetSize` is active, legacy can silently ignore `matchingSetFilter`.

The zero overlap is not explained by the 50 cap alone. If WBIA and OpenSearch held the same embeddings for the same 15K candidates, their top-50 should substantially overlap. Zero overlap points to at least one deeper mismatch: WBIA’s registered annotation corpus is not the OpenSearch embedding corpus, preprocessing differs, or the IDs being compared are from different identity namespaces. WBIA sends/queries `ann.getAcmId()` as fancy UUIDs at [IBEISIA.java:319](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:319), while MatchResult maps those back via `getAnnotationsWithACMId` at [MatchResult.java:330](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:330).

A likely embedding-corpus/preprocessing mismatch: WBIA computes embeddings from `ibs.get_annot_chips(...)` with `use_chips=True` at [_plugin.py:600](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:600), [plugin_dataset.py:61](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:61). ml-service computes from the original image bytes plus bbox/theta crop/rotate at [miewid.py:171](/mnt/c/ml-service/app/models/miewid.py:171). Those may not be pixel-identical chips even with the same checkpoint.

**Parity Checklist**

1. Decide the canonical score scale. Do not change the math. Either store both as `(1 + cos) / 2` or both as raw cosine. Vector currently converts OS Lucene scores with `2x - 1` at [Annotation.java:1258](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1258); legacy stores `(1 + cos) / 2` from [_plugin.py:806](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:806).

2. Remove the WBIA sentinel-zero path for parity tests. Set legacy `use_knn=false` so [_plugin.py:520](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:520) uses `miew_id_predict_light_distance` and scores every daid, or change `pred_light(..., n_results)` and `predict_k_neigh` to actually use the requested result size instead of hard `50`.

3. Make result limits equal. Vector is hard-coded to `KNN_K_DISTANCE_VALUE = 50` at [Annotation.java:38](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:38). Legacy stores up to 500 per score type at [MatchResult.java:52](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:52), while API cutoff defaults to 100 at [GenericObject.java:112](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:112). For parity, make `k`, stored cap, and API `prospectsSize` the same.

4. Ensure both paths use the same candidate UUID list before scoring. Verify `database_annot_uuid_list` from [IBEISIA.java:346](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:346) equals the OpenSearch `getMatchingSetQuery` IDs for the same task params.

5. Disable or normalize architectural extras. PairX reranking mutates scores when enabled at [_plugin.py:529](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:529). Legacy name aggregation creates separate name scores at [_plugin.py:389](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:389); compare only `"annot"` prospects first.

6. Align preprocessing, not just checkpoint. Either have ml-service use the exact WBIA chip image path/generation, or have WBIA score embeddings produced by the same ml-service extraction pipeline.

**Validation Plan**

1. For one task, dump three sets: OpenSearch candidate annotation IDs, legacy `database_annot_uuid_list`, and WBIA registered annot IDs. Compare by Wildbook annotation `id` and `acmId`.

2. Re-run legacy with `use_knn=false`. If vector top hits appear, the hard cap/sentinel-zero path was the visible legacy failure. If not, inspect embedding/preprocessing or WBIA corpus drift.

3. For 20 shared candidate `acmId`s, extract WBIA embeddings and OpenSearch stored embeddings, L2-normalize, and compare cosine for query-vs-candidate. This directly separates “ranking engine” issues from “different vectors” issues.

4. Run a pure offline scorer: same query vector, same ordered candidate vector matrix, exact cosine sort. Compare offline top-N to vector and legacy. That becomes the truth set.

5. Only after candidate IDs and vectors match, validate storage/API: same score scale, same `scoreType`, same cutoff, same tie-break by annotation id from [MatchResultProspect.java:97](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:97).

**Minimum Bar**

Full identical parity is achievable only if one canonical candidate list, one canonical embedding/preprocessing source, one scoring scale, and one exact top-N algorithm are enforced. Otherwise, the meaningful minimum bar is: same candidate-set intersection, same cosine for shared annotation pairs within float tolerance, and same top-N above a chosen cosine threshold after converting both pipelines to the same score scale.
tokens used
151,601
**RCA**

The largest verified divergence is WBIA’s MiewID “knn” path. It does **not** score the full 15K target set. In [knn.py](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/metrics/knn.py:20), it hard-caps nearest-neighbor search at `min(50, len(db_emb))`, and [pred_light](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/metrics/knn.py:50) returns only those neighbors. Then [aid_scores_from_score_dict](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:818) assigns `0` to every requested `daid` not present in that top-50 dict. That explains the legacy cliff to `0.000`; most legacy “prospects” are sentinel zeros, not scored MiewID comparisons.

Candidate-set construction is mostly shared now. Legacy `getMatchingSet(...)` delegates to `getMatchingSetQuery(...)` at [Annotation.java:1145](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1145). The vector path also builds its count/query through [Embedding.java:318](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:318). The filter logic for `matchingSetFilter.locationIds`, owner, and project lives in [Annotation.java:1036](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1036). So `getMatchingSet` vs `getMatchingSetQuery` is not the likely primary cause, assuming the task params are present.

There are still call-site hazards. `IAGateway` precomputes `matchingSet = ann.getMatchingSet(myShepherd)` without task params when `limitTargetSize > -1` at [IAGateway.java:418](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/IAGateway.java:418). The normal legacy path passes `task.getParameters()` into `getMatchingSet` at [IBEISIA.java:896](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:896). If `limitTargetSize` is active, legacy can silently ignore `matchingSetFilter`.

The zero overlap is not explained by the 50 cap alone. If WBIA and OpenSearch held the same embeddings for the same 15K candidates, their top-50 should substantially overlap. Zero overlap points to at least one deeper mismatch: WBIA’s registered annotation corpus is not the OpenSearch embedding corpus, preprocessing differs, or the IDs being compared are from different identity namespaces. WBIA sends/queries `ann.getAcmId()` as fancy UUIDs at [IBEISIA.java:319](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:319), while MatchResult maps those back via `getAnnotationsWithACMId` at [MatchResult.java:330](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:330).

A likely embedding-corpus/preprocessing mismatch: WBIA computes embeddings from `ibs.get_annot_chips(...)` with `use_chips=True` at [_plugin.py:600](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:600), [plugin_dataset.py:61](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/datasets/plugin_dataset.py:61). ml-service computes from the original image bytes plus bbox/theta crop/rotate at [miewid.py:171](/mnt/c/ml-service/app/models/miewid.py:171). Those may not be pixel-identical chips even with the same checkpoint.

**Parity Checklist**

1. Decide the canonical score scale. Do not change the math. Either store both as `(1 + cos) / 2` or both as raw cosine. Vector currently converts OS Lucene scores with `2x - 1` at [Annotation.java:1258](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1258); legacy stores `(1 + cos) / 2` from [_plugin.py:806](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:806).

2. Remove the WBIA sentinel-zero path for parity tests. Set legacy `use_knn=false` so [_plugin.py:520](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:520) uses `miew_id_predict_light_distance` and scores every daid, or change `pred_light(..., n_results)` and `predict_k_neigh` to actually use the requested result size instead of hard `50`.

3. Make result limits equal. Vector is hard-coded to `KNN_K_DISTANCE_VALUE = 50` at [Annotation.java:38](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:38). Legacy stores up to 500 per score type at [MatchResult.java:52](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:52), while API cutoff defaults to 100 at [GenericObject.java:112](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/api/GenericObject.java:112). For parity, make `k`, stored cap, and API `prospectsSize` the same.

4. Ensure both paths use the same candidate UUID list before scoring. Verify `database_annot_uuid_list` from [IBEISIA.java:346](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/identity/IBEISIA.java:346) equals the OpenSearch `getMatchingSetQuery` IDs for the same task params.

5. Disable or normalize architectural extras. PairX reranking mutates scores when enabled at [_plugin.py:529](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:529). Legacy name aggregation creates separate name scores at [_plugin.py:389](/mnt/c/wbia-plugin-miew-id/wbia_miew_id/_plugin.py:389); compare only `"annot"` prospects first.

6. Align preprocessing, not just checkpoint. Either have ml-service use the exact WBIA chip image path/generation, or have WBIA score embeddings produced by the same ml-service extraction pipeline.

**Validation Plan**

1. For one task, dump three sets: OpenSearch candidate annotation IDs, legacy `database_annot_uuid_list`, and WBIA registered annot IDs. Compare by Wildbook annotation `id` and `acmId`.

2. Re-run legacy with `use_knn=false`. If vector top hits appear, the hard cap/sentinel-zero path was the visible legacy failure. If not, inspect embedding/preprocessing or WBIA corpus drift.

3. For 20 shared candidate `acmId`s, extract WBIA embeddings and OpenSearch stored embeddings, L2-normalize, and compare cosine for query-vs-candidate. This directly separates “ranking engine” issues from “different vectors” issues.

4. Run a pure offline scorer: same query vector, same ordered candidate vector matrix, exact cosine sort. Compare offline top-N to vector and legacy. That becomes the truth set.

5. Only after candidate IDs and vectors match, validate storage/API: same score scale, same `scoreType`, same cutoff, same tie-break by annotation id from [MatchResultProspect.java:97](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:97).

**Minimum Bar**

Full identical parity is achievable only if one canonical candidate list, one canonical embedding/preprocessing source, one scoring scale, and one exact top-N algorithm are enforced. Otherwise, the meaningful minimum bar is: same candidate-set intersection, same cosine for shared annotation pairs within float tolerance, and same top-N above a chosen cosine threshold after converting both pipelines to the same score scale.
