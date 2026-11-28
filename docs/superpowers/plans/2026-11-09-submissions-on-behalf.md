# Submit on behalf of an organization member: implementation plan

> For agentic workers: implement task by task; each task ends green before the next.

**Goal:** a service-key submission can name, once per submission, the organization member who
collected it (or `public`), and the imported encounters and ImportTask are owned by that member.

**Architecture:** one new class, `SubmissionAttribution`, holds the rule. The create parser stores
`onBehalfOf`; create, validate, first commit and import call the resolver; the importer writes the
resolved username as `Encounter.submitterID`; commit makes the member the ImportTask creator;
`results` returns rows to the submitting service account.

**Tech stack:** Java 17, DataNucleus JDO, JUnit 5, Testcontainers PostgreSQL, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-09-service-key-submit-on-behalf-design.md` (revision 8).

## Global constraints

- Branch `feat/submissions-on-behalf` off `origin/main`, worktree `/mnt/c/Wildbook-onbehalf`.
- No new key scope, JWT claim or `Actor` field.
- A key never creates a `User`.
- Accepted values: exactly `public`, or a direct member of the draft owner's service organization
  who is not a service account. One error code and message for every other value.
- Files LF only. One `mvn` run in the worktree at a time.
- Commit trailers: `Co-Authored-By: Codex <noreply@openai.com>` (when Codex shaped the change) and
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review focus

1. A draft owned by a non-service account (including an admin) sends `onBehalfOf`: 403
   `ATTRIBUTION_NOT_ALLOWED`, nothing stored.
2. A member removed between commit and import: definite `failed`, `VALIDATION_STALE`, nothing
   imported, the task readable and deletable (empty encounter list).
3. A username that differs only by case, or `Public` / `N/A` / empty: `SUBMITTER_NOT_ALLOWED`.
4. The service account loses access to a member's encounters but still gets the results rows.
5. Idempotent create replay with a different `onBehalfOf`: 409 `IDEMPOTENCY_KEY_REUSED`.

---

### Task 1: the resolver

**Files:**
- Modify: `src/main/java/org/ecocean/auth/ServiceKeyAuth.java` (make `directOrganizationIds` and
  `fresh` public; behaviour unchanged).
- Modify: `src/main/java/org/ecocean/submission/Submission.java` (add `getCreateJson()`; the field
  is private with no getter).
- Create: `src/main/java/org/ecocean/api/submission/SubmissionAttribution.java`.
- Test: `src/test/java/org/ecocean/api/submission/SubmissionStoreDbTest.java` (DB-backed; it already
  has users, drafts and the real importer).

**Interfaces (produced):**
- `static final String PUBLIC = "public"`, `static final String NOT_ALLOWED = "SUBMITTER_NOT_ALLOWED"`,
  `static final String OWNER_NOT_ALLOWED = "ATTRIBUTION_NOT_ALLOWED"`.
- `static String requested(Submission draft)`: `onBehalfOf` from the stored create JSON, or null.
- `static String serviceOrganization(Shepherd sh, String ownerId)`: freshly loads the owner
  (`ServiceKeyAuth.fresh(pm, User.class, "uuid == :id", ownerId)`) and returns its
  `getServiceOrganizationId()`; throws `SubmissionException(403, OWNER_NOT_ALLOWED, ...)` when the
  user is missing or not a service account.
- `static JSONObject collector(Shepherd sh, String organizationId, String name)`: returns
  `{username, userId, organizationId}` (both IDs `JSONObject.NULL` for `public`), or null when the
  name is not allowed. Member check: fresh `User` by exact `username`, `getServiceOrganizationId()
  == null`, and `organizationId` in `ServiceKeyAuth.directOrganizationIds(pm, user.getId())`.
  `ServiceKeyException` from those lookups becomes `SubmissionException(503,
  "CAPABILITY_UNAVAILABLE", ...)`.
- `static final String NOT_ALLOWED_MESSAGE = "onBehalfOf must be public or a member of this organization"`.

**Test fixture (shared by tasks 1-6):** in `SubmissionStoreDbTest`, a helper modelled on
`ServiceKeyAuthDbTest.fixture` (`ServiceKeyAuthDbTest.java:47`): persist an `Organization`, a
service `User` with `setServiceOrganizationId(org.getId())` and members (an ordinary member, one with
the `orgAdmin` role, a member of a child organization only, a member of an unrelated organization),
call `addMember`, and commit before any uncached read. `realImport` (`SubmissionStoreDbTest.java:442`)
gains an optional create body (to carry `onBehalfOf`) and a hook that runs after validation and
before `enqueue`, alongside its existing post-enqueue hook; its policy and location mocks stay
active around both.

- [ ] Write DB tests: `collector` accepts a direct member, a member holding `orgAdmin`, and `public`; returns null for an unknown
  name, a different-case username, `Public`, `N/A`, a member of another organization only, a
  member of a child organization only, a member of the parent organization only (hierarchy built
  with `addChild` and checked through `getChildren` after reload), and another service account; `serviceOrganization` throws 403 for an ordinary user and for a missing
  user; no `User` row is added by any call.
- [ ] Implement, reusing `SubmissionValidator.issue` for errors and `SubmissionJson.canonical` for comparisons; run `mvn -o test -Dtest=SubmissionStoreDbTest`; commit.

### Task 2: create

**Files:** `SubmissionJson.java` (create parser), `SubmissionStore.java` (create),
`Submission.java` (`json`), tests in `SubmissionJsonTest.java` and `SubmissionStoreDbTest.java`.

- `SubmissionJson.create` allows `onBehalfOf`: a string, trimmed, 1 to 100 characters after
  trimming, otherwise 400 `BAD_REQUEST` "Invalid onBehalfOf". Normalized output includes it only
  when present.
- `SubmissionStore.create`: after the existing replay branch and before the admission counts, when
  normalized has `onBehalfOf`, call `serviceOrganization(sh, ownerId)` (403 on failure; nothing
  persisted). Replay with the same key and a different `onBehalfOf` is already 409 through the
  create hash.
- `Submission.json` echoes `onBehalfOf` when present.

- [ ] Tests: parser accepts and trims, rejects number, empty, whitespace-only, 101 characters;
  output has no `onBehalfOf` when absent. DB: an ordinary owner and an owner holding the `admin`
  role both get 403 and no draft; service owner gets the draft with `onBehalfOf` echoed; same key
  with another value gets 409; `replaceRows` leaves `onBehalfOf` unchanged. `SubmissionsTest`:
  the HTTP create returns 201 with `onBehalfOf`.
- [ ] Implement; run tests; commit.

### Task 3: validate report

**Files:** `SubmissionValidator.java`, test `SubmissionStoreDbTest.java`.

- When `requested(draft)` is non-null: `serviceOrganization` (throws 403), then `collector`. A null
  collector adds one error `{code: SUBMITTER_NOT_ALLOWED, field: "onBehalfOf", message:
  NOT_ALLOWED_MESSAGE}` with no row index.
- Report: `draftOwnerId` always the draft owner. `effectiveOwnerId`: the collector's `userId`, or
  `"public"`, for an accepted collector; the draft owner otherwise. `effectiveOwner`: the collector
  object, only for an accepted collector.

- [ ] Tests: member, public, not allowed, and unattributed reports; owner no longer a service
  account (its `serviceOrganizationId` cleared after create) makes validate fail with 403.
- [ ] Implement; run tests; commit.

### Task 4: commit

**Files:** `SubmissionJobs.java` (`enqueue`), test `SubmissionStoreDbTest.java`.

- After the existing replay branch and approval checks, for an attributed draft:
  `serviceOrganization` (403), `collector` again; null, or a result whose canonical JSON differs from
  the approved report's `effectiveOwner`, gives 409 `VALIDATION_STALE`.
- The ImportTask creator is the member (`sh.getUserByUUID(collector.userId)`) for a member, the
  owner otherwise. The task is created with `setEncounters(new ArrayList<>())`.

- [ ] Tests: member creator; public creator is the service account; member removed between validate
  and commit (pre-enqueue hook) gives 409 and no task; the username deleted and re-created as a
  different user (new UUID) between validate and commit gives 409; owner no longer a service
  account before commit gives 403; replay of an accepted commit after the member was removed still
  returns the stored result. The queued task appears in `Shepherd.getImportTasksForUser(member)`,
  and `task.iaSummaryJson(sh)` and `ImportTask.deleteWithRelated(taskId, member, sh)` succeed on it
  (empty encounter list, not null).
- [ ] Implement; run tests; commit.

### Task 5: import

**Files:** `SubmissionImporter.java`, test `SubmissionStoreDbTest.java`.

- At the start, for an attributed draft, `serviceOrganization`; its 403 becomes
  `PreImportRejection(403, ATTRIBUTION_NOT_ALLOWED, ...)` before the existing owner guard.
- The rerun of `validate` resolves the owner again; a `SubmissionException` with code
  `ATTRIBUTION_NOT_ALLOWED` thrown from it is also converted to `PreImportRejection`, so an owner
  that changes between the two checks still ends `failed`, never `needs_reconciliation`
  (`SubmissionJobs.execute` only treats `PreImportRejection` as definite).
- After the rerun of `validate`, also compare canonical `effectiveOwner` of approved and checked
  reports; a difference is `PreImportRejection(409, VALIDATION_STALE, ...)`.
- `Encounter.submitterID` is the approved collector's username (or `public`) for an attributed
  draft, the owner's username otherwise.

- [ ] Tests (real importer): member-owned encounters and sighting; public-owned encounters and
  sighting; member removed after commit ends `failed` with `VALIDATION_STALE`, no media assets
  added, task `failed` with an empty encounter list that `iaSummaryJson` and `deleteWithRelated`
  handle; owner no longer a service account ends `failed` with `ATTRIBUTION_NOT_ALLOWED`, never
  `needs_reconciliation` (tested with the change made before execute and, through a Shepherd
  override, between the first check and the validator rerun). Permissions: the member and an
  orgAdmin of the organization can access the encounters (`Collaboration.canUserAccessEncounter`),
  an unrelated organization member and the service account cannot; a public encounter is
  accessible to an unrelated user. `cloneWithoutAnnotations` of a member encounter keeps the
  member as `submitterID` (detection clones).
- [ ] Implement; run tests; commit.

### Task 6: results

**Files:** `SubmissionJobs.java` (`results`), test `SubmissionStoreDbTest.java`.

- Skip the per-row access filter when `requested(draft) != null` and `actor.equals(draft.getOwnerId())`.

- [ ] Test: after a member import, `Collaboration.canUserAccessEncounter` is false for the service
  account, yet `results` returns every row to it; another actor gets 404; an unattributed draft
  keeps the filter. Public search visibility: with collaboration security enabled, the imported
  public encounter's `opensearchAclFields(sh).publiclyReadable` is true and the member encounter's is
  false (same contract `OpenSearchAclFilterTest` relies on); run `OpenSearchAclFilterTest` too.
- [ ] Implement; run tests; commit.

### Task 7: contract, docs, invariant test

**Files:** `Submissions.java` (capabilities), `openapi.yaml`, `agent-skills/submit-sightings.md`,
`agent-skills/api-reference.md`, `agent-skills/inat-to-wildbook-import.md`,
`SubmissionValidatorTest.java`, `AgentSkillContentTest` if its assertions cover changed text.

- Capabilities gain `"attribution": {"field": "onBehalfOf", "values": ["public", "member username"],
  "scope": "submission"}`.
- OpenAPI: `onBehalfOf` on the create body and echoed on the draft; the two codes; the validation
  report's `effectiveOwnerId` (`openapi.yaml:329`) allows `"public"` as well as a UUID, plus
  `draftOwnerId` and `effectiveOwner` (nullable `userId` / `organizationId`).
- Key-management text (in `api-reference.md`, and the service-key section of the docs): a key can
  credit data to any direct member of its organization, including admins and orgAdmins, whose
  existing collaborators and orgAdmins then get the usual access; or publish it as `public`.
- Skill: when and how to use `onBehalfOf`, one collector per submission, what `public` means, the
  two codes, results vs search visibility. Plain language.
- Invariant test: `SubmissionValidator.supported` is false for every user-creating bulk column
  (`Encounter.submitter0.emailAddress`, `.fullName`, `.affiliation`, the photographer and
  inform-other equivalents, `Encounter.project0.ownerUsername`), and for `Encounter.submitterID`.

- [ ] Write; run `SubmissionValidatorTest`, `SubmissionsTest`, `AgentSkillContentTest`; commit.

### Task 8: full verification and PR

- [ ] `mvn -o test -Dtest='Submission*Test,ServiceKey*Test,AuthToken*Test,AgentSkill*Test,ImportTaskTest'`.
- [ ] Codex branch review until converged.
- [ ] Push, open the PR against `main`, milestone 11.1 (number 29).
