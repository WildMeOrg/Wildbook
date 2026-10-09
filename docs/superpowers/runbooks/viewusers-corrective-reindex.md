# Runbook: index ACL drift and the permissions audit

The OpenSearch encounter, annotation and individual documents each carry an ACL
(`publiclyReadable`, owner id(s), `viewUsers`) written by their serializers. The
permissions audit (`org.ecocean.security.PermissionsAudit`) verifies every document
against the database on a fixed schedule and repairs the ones that differ. Nothing
signals it and nothing is acknowledged: whatever one audit misses, the next one
re-reads from a fresh snapshot.

## What to expect after a deploy

- The first audits repair whatever drifted before, within the per-audit caps
  (`backgroundPermissionsMaxRepairsPerPass`, `backgroundPermissionsMaxRebuildsPerPass`).
  A catalog with a lot of drift takes a few audits at the retry delay.
- Each audit logs one line per index (`PermissionsAudit: <index>: scanned=... repaired=...
  conflicts=... structural=... deferred=... failed=...`) and a summary with the
  elapsed time and the sampled peak heap (heap use at phase boundaries). On a converged
  system every counter except `scanned`
  is 0. A steady non-zero `repaired` on a quiet system means a serializer and the
  audit disagree about a document: treat it as a bug, not as drift.

## Consistency contract

An existing eligible document is repaired by a subsequent audit whose snapshot
includes the change and whose repair succeeds. Under healthy operation without
repair backlog or concurrent stale writes, latency includes the remainder of an
in-flight audit, the configured delay, and the next audit's duration. Failures,
caps, deferred rebuilds, and concurrent writes can extend it; there is no
unconditional deadline. The only readers of the child copies are the
token-authenticated annotation and individual search paths and `MediaResolveApi`.

## Configuration (`OpenSearch.properties`)

| property | default | meaning |
|---|---|---|
| `backgroundPermissionsMinutes` | 10 | delay after a completed audit |
| `backgroundPermissionsRetryMinutes` | 2 | delay after an incomplete audit (at most 5 in a row) |
| `backgroundPermissionsRunner` | true | whether this JVM runs the audit |
| `backgroundPermissionsPageSize` | 1000 | scroll page size |
| `backgroundPermissionsMaxRepairsPerPass` | 20000 | conditional ACL writes per audit |
| `backgroundPermissionsMaxRebuildsPerPass` | 500 | child rebuilds per audit |
| `backgroundPermissionsScrollKeepAlive` | 5m | scroll context keep-alive |

Exactly one JVM per database may run the audit. With several Tomcats sharing a
database, set `backgroundPermissionsRunner=false` on all but one. A restart is a
stop-then-start of one process; two live JVMs auditing the same database is not
supported.

## Checks

1. Pick an encounter whose last collaborator was removed: within a couple of audits
   `GET encounter/_doc/<id>` shows an empty `viewUsers`, and its annotations'
   documents show the same.
2. Pick an encounter whose owner no longer has a user record: its document has
   `publiclyReadable=false`, no `submitterUserId`, and only location-role viewers.
3. An index whose deployed mapping lacks doc values on an ACL field, or carries a
   `null_value`, is skipped with a reason in the log; fix the mapping and reindex.
