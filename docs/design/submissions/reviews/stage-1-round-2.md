**No Major findings remain.** All six round-1 Majors are fixed in the contract and tests. There are no Critical findings and nothing blocking. I found one new wording defect worth fixing before the contract is published (item 1). Everything else is Minor. I didn't run anything, as you asked. The test findings below come from reading the code, so the strengthened test run you have in progress is still the real check.

## Round-1 Majors

| # | Status | Evidence |
|---|---|---|
| M1 | Resolved | `openapi.yaml:692-711`: validate never bumps the revision, returns an `ETag`, is safe to repeat, and has no body `revision`. The example revisions agree: manifest 1 → validation 2 → `acceptedRevision` 2. |
| M2 | Resolved | `:165-167`: GET returns 200 with a tombstone. `:232-235`: a repeat DELETE returns 204 whatever `If-Match` says, and the other states return 409. 410 is no longer listed on DELETE. |
| M3 | Resolved | `:1033-1073`: stable `code` enum plus optional `issues`. `:807-809`: invalid, stale and already-committed validations now have distinct status/code pairs. |
| M4 | Resolved | `GET /rows` added, plus `rowCount` (required) and `rowsDigest`. |
| M5 | Resolved | `:1418-1427`: `occurrenceIds` and `individualIds` are now non-empty strings. |
| M6 | Resolved | Unknown-field default is pinned at servlet level: `BulkApiPostTest.java:464-488` uses no `tolerance` override and checks warnings=1, errors=0. Grouping and year-only precision are tested at importer level (`:490-524`). The Feb 30 check is exact (`Compat:64-66`). The short-array null padding case is added (`:69-79`). The disposition says which existing suites cover the remaining cases. |

## Findings

1. **Medium (fix before publishing), `openapi.yaml`: one description was pasted onto unrelated fields.** The text "Exact accepted multipart filename; also the row reference." now appears on the results `cursor` (`:929`), `Error.message`/`requestId` (`:1065,1069`), all four `Issue` string fields (`:1270-1285`), `configDigest`/`manifestDigest` (`:1323,1327`), `File.mediaType` (`:1241`), `statusUrl` (`:1379`), `Phase.message` (`:1398`), `ResultRow.clientRowId` (`:1412`) and `nextCursor` (`:1473`). Generated clients and docs would describe these fields wrongly. It looks like the YAML-alias removal went wrong. **Fix:** keep that description only on `File.name` (`:1227`). Remove it everywhere else, or replace it with a correct one-line description.

2. **Minor, `check_contract.py:55-57`: the format check probably isn't doing anything.** From memory of jsonschema's `_format.py` (please confirm against the installed version), `uuid` is only registered for Draft 2019-09 and 2020-12, not Draft 4. `date-time` is only checked if `rfc3339-validator` is installed; otherwise it's skipped without warning. So the Minor 8 fix may have no effect. **Fix:** add a negative example with `"id": "not-a-uuid"` (and a bad `date-time`) marked `valid: false`, so the checker fails if formats aren't enforced. Or register those checkers on the `FormatChecker` explicitly.

3. **Minor, `openapi.yaml:796-801` vs `stage-1-disposition.md:6`: validate body.** The disposition says "empty body", but `requestBody.required: true` with the `Validate` schema means clients must send `{}`. **Fix:** either say "send `{}`" in the description, or set `required: false`.

4. **Minor, `openapi.yaml:430-496`: `GET /rows` has no 410.** GET `/files` (`:556`) returns 410 for tombstones, but GET `/rows` doesn't list it. **Fix:** add 410 to `GET /rows`, or state in the description that rows stay readable during tombstone retention.

5. **Minor, `openapi.yaml:432-435` vs `:1208-1210`: row comparison rules don't match.** GET `/rows` promises the "exact stored rows" but tells clients to compare "normalized JSON values". Meanwhile `rowsDigest` canonicalization doesn't say how numbers are written (`2026` vs `2026.0`). **Fix:** say that GET returns the rows as accepted and that clients compare by JSON value equality. Either state that `rowsDigest` uses RFC 8785 (JCS) canonical JSON, or mark it informational only.

6. **Minor, `openapi.yaml`: some error mappings are still missing or irrelevant.**
   - DELETE still lists 413 and 422 (`:295-306`), which can't happen. Remove them.
   - Validate lists 422, but its description says errors return 200 with `valid=false`. Say when 422 applies (e.g. no rows yet), or remove it.
   - Two status mappings are unstated. Rows PUT with a duplicate `clientRowId` (409 or 422?) and DELETE in a non-cancellable state (409 `INVALID_STATE`?). Add a one-line code→status note or table under `Error`.

7. **Minor, `openapi.yaml:1100` and `check_contract.py:61-62`: leftover processing default.** `default: import-only` on the required `mode` field can never take effect. The prose now explains the rule correctly, but the checker still asserts the default. **Fix:** remove the `default` and the assertion, or leave both as documentation only. This is cosmetic.

8. **Minor, `BulkApiPostTest.java:485-486`: the warning test doesn't check where the warning came from.** Any single warning makes it pass. **Fix:** assert that the one warning has `fieldName == "Unknown.field"` and the unknown-fieldname type. With `verbose` set, the response may include it; if not, capture it from `dataWarnings` via the verbose output.

9. **Minor, `BulkApiPostTest.java:519`: the grouping test depends on `.get(0)`.** It assumes the Shepherd built first is the one that stores encounters. **Fix:** loop over `sh.constructed()` and assert `storeNewEncounter` was called exactly once in total, so the test doesn't depend on construction order.
