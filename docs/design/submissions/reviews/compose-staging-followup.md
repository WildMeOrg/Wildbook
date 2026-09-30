# Deployment staging mount follow-up

Actual Claude CLI reviewed the Compose mount and runbook, then reviewed the final diff including all five commented properties. Final verdict: approve, no blockers. Reviews were limited to the supplied diff; backend key names and existing retention behavior were checked separately.

Validation: Docker Compose config --quiet with placeholder environment values passed; YAML parsing and mount-path assertion passed; properties remain entirely commented; LF and git diff --check passed. No application behavior changed.

## Final Claude review

**Approve. I found no blockers.** I reviewed only the diff you pasted. This session had no file-reading tools, so I couldn't check the lines around it or the backend code.

**Safety: defaults are still off.**
- All five `submissions.*` keys in `apiAccessKeys.properties` are commented out. The shipped bundle therefore sets nothing, and each gate stays at its code default of false.
- A missing or empty `allowedUserIds` enrolls nobody, so it fails closed.
- `stagingDirectory` is only an example in a comment. It is never actually set.
- The runbook says clearly that the mount alone doesn't enable anything.
- The new mount is at `/srv/wildbook-private/submissions`. That's outside `/usr/local/tomcat/webapps/` and the data-dir mount, so nothing new becomes web-served. The host path is a sibling of `wildbook_data_dir`, not inside it.

**Consistency: the three files agree.**
- The host path, container path and property name match across the Compose file, the runbook and the properties file.
- The no-overlap rule (webapps, uploads, imports, asset-store roots) reads the same in the properties comment and the runbook.
- The runbook explains Compose's auto-create behaviour (a missing path gets default ownership, not 0700) and that you must recreate the container, not just restart it, to pick up the mount. Both are correct.

**Non-blocking nits (fix or ignore):**
1. **Getting the UID on a first deploy is circular.** `docker compose exec wildbook id` needs a running container. On a fresh host, the first `up` would create the staging directory with default ownership before the operator has the UID. It works for the upgrade path the runbook describes. Two ways to handle the fresh-host case:
   - Suggest `docker run --rm --entrypoint id <wildbook image>` instead.
   - Or add a line saying to fix ownership and permissions (chown/chmod) before enabling submissions if Compose already created the directory.
2. **The mount applies to every deployment that picks up this Compose file.** Operators who never enable submissions will still get an empty directory with default ownership on the host. It's harmless, but a line in the release notes would avoid surprise.
3. **"Described above" depends on text I couldn't see.** The properties comment says "the private data-dir override described above". Please confirm the file header (before line 29) actually describes that override.
4. **Durations not checked.** You verified the key names. I couldn't check the "seven days" retention or the "hourly" sweeper against the code, so confirm those match the constants if you haven't already.
