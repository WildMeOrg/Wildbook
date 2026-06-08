# Runbook: viewUsers corrective reindex (one-time, post-deploy)

After deploying the viewUsers ACL hardening, existing indexed encounters may
hold stale `viewUsers` arrays written by the old logic. The background
permissions pass now always rewrites `viewUsers` (including `[]`), so a single
forced run repairs every non-public encounter.

Steps:
1. Confirm the deploy is live (new `opensearchIndexPermissions()` in the WAR).
2. Force the permissions pass on the next background tick:
   - Preferred: trigger `OpenSearch.setPermissionsNeeded(true)` (any
     collaboration/org/role edit now does this), then wait for the background
     tick, OR
   - Force immediately by restarting the app (a forced run occurs when the
     last run is older than BACKGROUND_PERMISSIONS_MAX_FORCE_MINUTES).
3. Verify revocation propagated: pick an encounter whose last collaborator was
   removed and confirm its OpenSearch `viewUsers` is now empty:
   `GET encounter/_doc/<id>` -> `viewUsers` absent or `[]`.
4. Verify an invalid-owner encounter became admin-only: its doc should have no
   `submitterUserId` and empty/absent `viewUsers` after the full reindex.
5. Verify a known orgAdmin still sees member encounters (spot-check a search as
   that admin).
