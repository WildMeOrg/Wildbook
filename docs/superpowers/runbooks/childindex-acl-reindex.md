# Child-Index ACL Migration + Corrective Reindex (Spec A)

This runbook covers deploying the token-scoped individual + annotation reads (Spec A). It adds
denormalized ACL fields (`publiclyReadable`, `submitterUserIds`, `viewUsers`) to the **annotation**
and **individual** OpenSearch indices so the token pre-filter can gate them by encounter access.
Existing installs need a one-time mapping addition + corrective reindex; fresh installs index the
fields automatically.

---

## What changed (code)

- The annotation serializer now writes its single parent encounter's ACL (`publiclyReadable`/
  `submitterUserIds`/`viewUsers`); 0 or >1 parents → fail-closed (admin-only).
- The individual serializer now writes the **union** over member encounters' ACL; an encounterless
  individual → `publiclyReadable=true` (world-readable, matching `canUserAccessMarkedIndividual`).
- All three fields are computed from one source (`Encounter.opensearchAclFields`), so encounter,
  annotation, and individual ACLs agree.
- Reindex triggers refresh children on ACL changes (the permissions pass enqueues a changed
  encounter's deep reindex) and on encounter↔individual membership changes (reassign/merge/split).
- `viewUsers` is already in `Base.opensearchMapping()`; only `publiclyReadable` (boolean) and
  `submitterUserIds` (keyword) are new in the annotation/individual mappings.

---

## 1. Add the new fields to the live mappings (additive)

OpenSearch allows adding fields to an existing mapping without recreating the index. For both the
`annotation` and `individual` indices:

```bash
# annotation
curl -s -X PUT "$OS_URL/annotation/_mapping" -H 'Content-Type: application/json' -d '{
  "properties": {
    "publiclyReadable": { "type": "boolean" },
    "submitterUserIds": { "type": "keyword" }
  }
}'
# individual
curl -s -X PUT "$OS_URL/individual/_mapping" -H 'Content-Type: application/json' -d '{
  "properties": {
    "publiclyReadable": { "type": "boolean" },
    "submitterUserIds": { "type": "keyword" }
  }
}'
```

(`viewUsers` is already mapped as `keyword` on both via the `Base` mapping — do not re-add it.)
No drop/recreate is required. If you prefer a clean index version, create new indices from the
updated mappings and reindex into them instead.

---

## 2. One-time corrective reindex (ordered)

Backfill the new fields onto existing docs. Run **in this order** (analogue of Artifact A's
`viewUsers` corrective pass), using the install's existing full-reindex tooling:

1. **Encounter pass** — ensures encounter `viewUsers`/`publiclyReadable`/`submitterUserId` are current
   (the source the children derive from). Run the existing Artifact A corrective `viewUsers` reindex /
   permissions pass first. Anonymous-owned encounters must be `publiclyReadable=true`; invalid/deleted
   non-anonymous owners stay admin-only (fail-closed) — this is handled by `Encounter.opensearchAclFields`.
2. **Annotation reindex (THE LONG POLE)** — there are typically several annotations per encounter, so
   on large installs this is the biggest job (can be 100K+ docs). Full reindex of the `annotation` index.
3. **Individual reindex** — full reindex of the `individual` index (computes the union per individual).

After this one-time pass, the reindex triggers keep the fields current incrementally; there is no
ongoing bulk cost.

---

## 3. Fail-closed during reindex (safe ordering)

Until step 2 completes for an index, the new ACL fields are simply **absent** on those child docs →
the token pre-filter's `should` clauses match nothing → a non-admin token sees **nothing** on that
index (admins are unaffected; they bypass the filter). A partial or in-progress reindex therefore
degrades to "too restrictive," **never** "leaky." Order does not create an exposure window.

---

## 4. Engineering note — reindex cost

The child serializers compute `Encounter.computeViewUsers` per related encounter at serialize time
(the per-doc computation Artifact A's bulk pass was designed to avoid for scale). This makes the
one-time annotation/individual reindex (step 2/3) CPU- and DB-heavy on large installs. It is a
one-time cost; incremental updates afterward are cheap (single-object deep reindex on change).

If the one-time reindex proves prohibitively slow on a very large install, a deferred optimization is
to extend Artifact A's bulk `opensearchIndexPermissions` map-building pass (which already builds the
collaboration/org maps once) to also write child `viewUsers` via `indexUpdate`, rather than
recomputing per child doc. This is an optimization only — not required for correctness — and is
intentionally out of scope for the initial Spec A implementation.

---

## 5. Verify (smoke test)

After the reindex, with a non-admin researcher's token (see `jwt-keypair-setup.md` + `POST
/api/v3/auth/token`):

```bash
# individual: only individuals with >=1 visible encounter; response carries identity fields only
curl -s -X POST "$HOST/api/v3/search/individual" -H "Authorization: Bearer $TOK" \
  -H 'Content-Type: application/json' -d '{"query":{"match_all":{}}}'
# -> hits contain id/displayName/names/sex/taxonomy only; NO numberEncounters/users/encounterIds;
#    NO publiclyReadable/submitterUserIds/viewUsers; X-Wildbook-Total-Hits is the scoped count.

# annotation: only annotations whose parent encounter is visible; embeddings present; no ACL fields
curl -s -X POST "$HOST/api/v3/search/annotation" -H "Authorization: Bearer $TOK" \
  -H 'Content-Type: application/json' -d '{"query":{"match_all":{}}}'

# occurrence/media_asset via token -> 403 (still deferred)
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$HOST/api/v3/search/occurrence" \
  -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"query":{"match_all":{}}}'
# -> 403
```

Compare an **admin** token: it is unscoped (sees all individuals/annotations), with the internal ACL
fields still scrubbed from responses.

---

## 6. Media-resolve smoke test (Spec: token-scoped media resolve)

No reindex is needed for `POST /api/v3/media/resolve` (no new index fields). After deploying the
endpoint, verify with tokens (see `jwt-keypair-setup.md` + `POST /api/v3/auth/token`):

```bash
# admin token: resolve the two salamander missed-match candidate annotations -> image url + scaled bbox
curl -s -X POST "$HOST/api/v3/media/resolve" -H "Authorization: Bearer $ADMIN_TOK" \
  -H 'Content-Type: application/json' \
  -d '{"annotationIds":["<ann-BGBI_22-168>","<ann-BGBI_23-2716>"]}'
# -> 200, JSON array with 2 entries: imageUrl (…-master.jpg or …-mid.jpg), imageWidth/Height,
#    bbox in that image's pixel space, theta, viewpoint, encounterId, individualId, methodVersion.
# Fetch each imageUrl and confirm it returns image bytes (HTTP 200).

# non-admin token: an annotation whose parent encounter the user cannot see resolves to empty
curl -s -X POST "$HOST/api/v3/media/resolve" -H "Authorization: Bearer $RESEARCHER_TOK" \
  -H 'Content-Type: application/json' -d '{"annotationIds":["<private-annotation-id>"]}'
# -> 200 with [] (silently absent; indistinguishable from a nonexistent id — no existence oracle)

# no token -> 401
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$HOST/api/v3/media/resolve" \
  -H 'Content-Type: application/json' -d '{"annotationIds":["x"]}'
# -> 401
```

Sanity-check the bbox: crop the returned `imageUrl` to `bbox` — it should frame the animal region the
embedding was computed from. The bbox is already in the returned image's pixel space (no client
scaling needed); `imageWidth`/`imageHeight` are provided for verification.
