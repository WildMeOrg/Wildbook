# Submitting sightings on behalf of an organization member

Date: 2026-11-09
Status: design, revision 8 (import task owned by the named member)
Area: submissions API (`/api/v3/submissions`) with organization service keys

## Problem

An agent that submits sightings with an organization's service key creates encounters owned by
the organization's service account (`svc-<orgId>-<8 hex>`). `SubmissionImporter.execute` forces
`Encounter.submitterID` to the draft owner's username (`SubmissionImporter.java:35`), and
`SubmissionValidator.FIELDS` rejects `Encounter.submitterID` (`UNSUPPORTED_FIELD`).

Wildbook's access model is per user: an encounter is visible to its owner, to users with an
approved collaboration with the owner, to site admins and to location-role holders
(`Collaboration.canUserAccessEncounter`, `Encounter.canUserEdit`). Organization membership alone
grants nothing. orgAdmins get an assumed edit collaboration with every member of their
organization (`Collaboration.collaborationBetweenUsers`). Nobody can collaborate with the service
account, because it cannot log in to accept a request.

So the researcher who collected the data cannot see or edit it unless they are an orgAdmin.

## Goal

Let an agent using a service key name the organization member who collected a submission's
sightings. The server checks the name before anything is imported, and the encounters are then
owned by that member.

## Non-goals

- Sharing service-account-owned encounters with the whole organization (a separate idea).
- Attribution on the page bulk import, which already accepts `Encounter.submitterID` under a
  browser login.
- Attribution with ordinary user tokens.
- Re-owning encounters already imported under the service account.
- Per-row attribution. One submission names one collector (see "Why per submission").

## Design

### 1. Who may attribute: any service-account submission

There is no new scope. Ownership by the person who collected the data is central to how Wildbook
works, so every service key with `submissions:write` may name a collector. Few keys exist, and a
key can only name an existing member of its own organization or `public` (sections 3 and 3a). The
worst cases are mis-attribution between real members and deliberately publishing data as public.

Whether a draft may use `onBehalfOf` is decided from the datastore, not the token: the draft owner
must be a service account (`User.getServiceOrganizationId() != null`, read fresh). An ordinary
user's draft, including an admin's, gets 403 `ATTRIBUTION_NOT_ALLOWED`. No JWT, `Actor` or key
change is needed.

### 2. The request field

Draft creation (`POST /submissions`) accepts an optional top-level `onBehalfOf: "<username>"`.
It applies to every row. Rows still may not contain `Encounter.submitterID` (`UNSUPPORTED_FIELD`,
unchanged).

If `onBehalfOf` is present and the draft owner is not a service account, creation is refused with
403 `ATTRIBUTION_NOT_ALLOWED`.

`SubmissionJson`'s create parser rejects unknown properties and rebuilds the stored object
(`SubmissionJson.java:86`, `:98`). Both its allowlist and its normalized output gain `onBehalfOf`:
a trimmed, non-empty string of at most 100 characters. Because it is part of the stored create JSON,
`SubmissionStore`'s existing create hash and idempotency cover it.

`onBehalfOf` is **immutable**. `PUT` of rows accepts only `rows`, as today; a new revision
invalidates validation but never changes the create JSON. Naming a different collector needs a new
draft.

### 3. Which names are accepted

`onBehalfOf` accepts exactly two kinds of value (user decision):

**A. The literal `public`** (exact, lower case; no other anonymous spelling such as `N/A` or an empty
string). The encounters are then owned by `public`, the same value the page bulk import accepts for
`Encounter.submitterID` (`BulkValidator.java:427`). See section 6 for what public ownership means.

**B. An organization member.** The organization is the draft owner's
`User.getServiceOrganizationId()`. The name is accepted only if all of these hold, read fresh:

1. a `User` with exactly that username exists, loaded with an uncached query;
2. the user is a **direct** member of that organization, checked with the same uncached membership
   SQL `ServiceKeyAuth` uses (`ServiceKeyAuth.java:137`), never recursive membership and never
   `Organization.hasMember`'s in-memory list. That SQL and `ServiceKeyAuth`'s fresh-object loader
   (`ServiceKeyAuth.java:170`) are extracted to a shared helper and reused for step 1 as well;
3. the user is not a service account (`getServiceOrganizationId() == null`).

Privileged members (admin, orgAdmin, orgSuper) are eligible. Owning an encounter does not pass their
roles to the service account; it only means their existing collaborators and orgAdmins, possibly in
other organizations, gain the usual access to it. This is documented for key managers.

Anything else gives one validation error, `SUBMITTER_NOT_ALLOWED`, with one message ("must be
public or a member of this organization"). The legacy `BulkValidator` username messages, which tell
a missing user from an existing one, are not passed through.

### 3a. Invariant: a key never creates a User

Attribution only ever resolves to an account that already exists; nothing on this path creates one.
This also holds for the rest of the submissions API today, and must keep holding:

- The shared `BulkImporter` can create users: from submitter, photographer and inform-other email
  columns (`getOrCreateUser`, `BulkImporter.java:1137`) and from a project owner username
  (`BulkImporter.java:1182`). None of those columns is in `SubmissionValidator.FIELDS`, so a
  submission carrying them is invalid (`UNSUPPORTED_FIELD`) and cannot be committed.
- `onBehalfOf` is never handed to anything that creates users; an unknown name is
  `SUBMITTER_NOT_ALLOWED`.
- Tests pin both: an unknown `onBehalfOf` creates no `User`, and each user-creating bulk column is
  rejected by the submissions validator.

### 4. Where it is checked

One shared resolver does every check. It freshly loads the user at `draft.ownerId`; if that user is
missing or no longer a service account, it fails with `ATTRIBUTION_NOT_ALLOWED`. Otherwise it
resolves `onBehalfOf` as in section 3. Creating the draft alone cannot guarantee the owner is still a
service account at import, so the resolver runs at every step:

- **Create**: refuses `onBehalfOf` on a draft whose owner is not a service account (403).
- **Validate**: runs the resolver and reports ownership in the validation result:
  `effectiveOwnerId` (today always `draft.ownerId`, `SubmissionValidator.java:166`) becomes the
  collector's user ID, or `public` for public; a new `draftOwnerId` always names the submitting
  account; a new `effectiveOwner: {username, userId, organizationId}` is present only for attributed
  drafts (`userId` and `organizationId` are JSON nulls, `JSONObject.NULL`, for `public`). The client sees it in the stored report
  that commit approves by `validationId`.
- **First commit**: after the existing accepted-commit replay branch (`SubmissionJobs.java:27`),
  which still returns a stored result without re-checking, runs the resolver again before accepting.
- **Import**: `SubmissionImporter.execute` starts with an owner guard that answers `ACCESS_DENIED`
  for a missing or ineligible owner (`SubmissionImporter.java:20`). For an attributed draft the
  resolver runs before that guard, so an owner who is no longer a service account gives
  `ATTRIBUTION_NOT_ALLOWED`; unattributed drafts keep today's guard. `execute` then reruns
  `validate`, and with it the resolver, before
  copying any media, and compares `effectiveOwner` (including `userId`) with the approved report
  along with `normalizedRows`. A member who left the organization, a username now held by another
  user, or an owner no longer a service account fails here. These failures are raised as
  `PreImportRejection` (`VALIDATION_STALE` for a changed collector, `ATTRIBUTION_NOT_ALLOWED` for a
  changed owner); `SubmissionJobs` (`SubmissionJobs.java:115`) turns that into a definite `failed`
  submission and ImportTask, never `needs_reconciliation`, and nothing is imported. Membership changes are not locked against; the rule is "valid at the final
  pre-import check".
- The importer replaces only its unconditional override: `Encounter.submitterID` becomes the
  effective owner's username (or `public`), then `BulkImportUtil.validateRow` and `BulkImporter`
  assign ownership exactly as now (`BulkValidator.java:426` still runs after the stricter check).

`configDigest` and `manifestDigest` are not involved: they cover configuration and files. The
owner is protected by the explicit `effectiveOwner` comparison.

### 5. Results and provenance

- **Creation receipt.** `SubmissionJobs.results` (`SubmissionJobs.java:130`) filters each result row
  by the actor's current access to its encounters, so the service account would lose every row of
  an attributed submission. The filter is skipped only when the draft has `onBehalfOf` **and** the
  actor is the draft owner (`actorId.equals(draft.ownerId)`). Admins already bypass the filter,
  and anyone else already fails draft access. The rows contain only IDs, which the owner itself created; live record
  access stays governed by normal permissions.
- **Import task ownership.** The ImportTask is created at first commit (`SubmissionJobs.java:61`,
  today `new ImportTask(owner, job)`). For a member collector its creator is the resolved member, so
  the import appears in their own lists (`Shepherd.getImportTasksForUser` filters on `creator`; used
  by `/api/v3/bulk-import`, `imports.jsp` and `UserHome`) and they get the creator's access to it
  (`Collaboration.java:560`, `:576`; `ServletUtilities.java:632`-`654`). For `public` there is no
  user to own it, so the creator stays the service account. The import-time resolver already
  refuses a changed collector, so the creator set at commit cannot disagree with the encounters'
  owner of an imported submission. A submission refused at import leaves a failed, empty task owned
  by the member.
- The reserved ImportTask is created with an empty (not null) encounter list. A member can now see
  a queued or failed task with no encounters, and today task detail (`ImportTask.java:840`) and both
  delete paths (`ImportTask.java:738`, `DeleteImportTask.java:62`) fail on a null list.
- As creator the member gets the usual task-owner browser controls (detection, identification,
  deletion), subject to existing route roles. They get no access to the submission draft.
- The submission draft and its job stay owned by the service account: the record of which service
  account submitted the data (the draft stores `ownerId`, not which key). The draft links to the
  ImportTask through `jobId`, and the task already stores the `submissionId`.
- The attributed member does not get access to the draft; they reach their encounters normally.
- Provenance: the encounter index already has `importTaskId` and `importTaskCreatorId`
  (`Encounter.java:4420`); for a member collector the creator is now the member, and the sending
  service account is found through the submission draft that references the ImportTask. No new
  stored note.
- For a member collector, the service account loses `data:read` search visibility of the encounters
  unless some other grant applies. Intended, and documented in the skill. Public encounters stay
  searchable by everyone, including the service account and other organizations' `data:read`
  tokens (`OpenSearch.java:1652`).

### 6. What "owned by the member" does and does not mean

- `Encounter.submitterID` (the same field as `assignedUsername`) is the member. `setSubmitterID`
  has no side effects.
- `Encounter.submitters` is not populated, and the service account is never added to it (that would
  grant it access). No creation email is sent, as today on this path.
- **Public ownership.** For `onBehalfOf: "public"` the data behaves like any other public-owned
  data in Wildbook, as with the page bulk import today:
  - Encounters: anyone, including visitors who are not logged in, can view them
    (`Collaboration.canUserAccessEncounter`); any logged-in user can edit them
    (`Encounter.canUserEdit`).
  - Import task: any logged-in user can reach it through one of its encounters and delete it with
    all its encounters through the bulk-import API (`Collaboration.java:558`,
    `ImportTask.java:733`). The legacy delete servlet additionally requires the researcher role.
  - Sightings: anonymously viewable, and logged-in users pass the sighting edit check through a
    public encounter, subject to each route's roles (`ServletUtilities.java:662`).
  - Individuals: none is created here, but if a public encounter is later linked to an individual,
    that individual becomes publicly viewable and searchable and passes the legacy individual edit
    check (`MarkedIndividual.java:3011`, `ServletUtilities.java:588`). Its other encounters keep
    their own access rules.
  - Images: other organizations' `data:read` tokens can resolve eligible public annotations to image
    URLs, which can expose the full-frame image, not only the boxed region
    (`MediaResolveApi.java:164`).
  - The skill tells agents to use `public` only for data meant to be open to everyone.
- Occurrences: with one owner per submission, every sighting the submission creates is owned by
  the member. Existing sightings cannot be joined: validation rejects an existing
  `Encounter.sightingID` with `SIGHTING_EXISTS` (`SubmissionValidator.java:86`) and the importer
  uses `newOccurrencesOnly()` (`SubmissionImporter.java:51`), as today.
- ImportTask deletion: an attributed member can see the import task through their encounters and
  can delete it with all its encounters (`Collaboration.java:558`, `DeleteImportTask.java:57`).
  With one owner per submission those are all theirs. Service tokens cannot reach these browser
  routes.
- Detection may create additional encounters; they copy owner and submitters
  (`Encounter.java:3894`), so they belong to the member too. Tested.

### 7. Agent skill and docs

- `submit-sightings.md`: document `onBehalfOf` (members and `public`, with what public means),
  `SUBMITTER_NOT_ALLOWED`,
  `ATTRIBUTION_NOT_ALLOWED`, one collector per submission, and that results remain available.
  When a member is named, search will not show the records to the service account unless another
  access grant applies; public records stay searchable by everyone.
- API reference: `onBehalfOf` and the two error codes. Key-management docs: a service key can
  credit data to members of its organization or publish it as `public`.
- `inat-to-wildbook-import.md`: align its `submitterID` advice.

## Why per submission

Per-row attribution would put several owners in one ImportTask. Any of them could then see the task
and delete every encounter in it, including other members' rows, and a shared sighting would belong
to whichever row came first. Fixing that means changing ImportTask deletion and detail access for
all imports. One owner per submission avoids both; an agent splits a batch by collector, which is
cheap.

## Tests

- Permission: a service-account draft may use `onBehalfOf`; an ordinary user's and an admin's
  draft get 403 `ATTRIBUTION_NOT_ALLOWED`.
- Names: direct member accepted (including an orgAdmin); `public` accepted and imported as
  public-owned; `N/A`, `Public`, empty, unknown user, non-member, member of a parent or child
  organization only, and another service account all give `SUBMITTER_NOT_ALLOWED` with the same
  message.
- Import task: for a member collector its creator is the member and it appears in the member's import
  list; for `public` the creator is the service account. A queued task's detail opens; a task
  refused at import (member removed after commit) opens and can be deleted by the member.
- Import guard: an attributed draft whose owner is no longer a service account fails with
  `ATTRIBUTION_NOT_ALLOWED`, not `ACCESS_DENIED`.
- Public: encounters and the new sighting are public-owned; the service account still finds them in
  search; the validation report has null `userId` and `organizationId`.
- Resolver at each step: owner no longer a service account at validate, first commit and import
  gives `ATTRIBUTION_NOT_ALLOWED`; at import it is a definite failure, not `needs_reconciliation`;
  accepted-commit replay still returns the stored result.
- Import: member removed after commit gives `VALIDATION_STALE` and nothing imported; username
  re-used by a different user gives `VALIDATION_STALE`.
- Ownership: member can view and edit; an unrelated org member cannot; detection-created encounters
  are the member's.
- Results: the service account gets all rows of an attributed submission; another actor and
  unattributed submissions keep today's behaviour.
- Create: unknown or malformed `onBehalfOf` rejected by the parser; same idempotency key with a
  different `onBehalfOf` is a conflict; `PUT` rows cannot change it.
- Validation report: `effectiveOwnerId`, `draftOwnerId` and `effectiveOwner` for attributed and
  unattributed drafts.
- `Encounter.submitterID` in a row is still `UNSUPPORTED_FIELD`.

## Decisions taken

1. No new key scope: any service-account submission may name a collector (user decision,
   revision 4). Permission comes from the draft owner being a service account, read from the
   datastore, so there is no token or commit-time snapshot.
2. Provenance from the existing `importTaskId` / `importTaskCreatorId`; no new note.
3. Any direct human member is eligible, privileged ones included; no opt-in.
4. One collector per submission instead of per row.
5. A key never creates a User (section 3a). `public` is not an account.
6. `public` is the only accepted value besides organization members (user decision, revision 5).
7. One shared resolver at validate, first commit and import (Codex round 4).
8. The ImportTask's creator is the named member (user decision, revision 7); the service account
   stays the owner of the submission draft.

## Related work

A separate change is being analysed for reading: today an organization's service account sees only
public encounters and its own, so a `data:read` key cannot see private encounters owned by the
organization's members (reported by a GiraffeSpotter user). If service accounts gain read access to
their organization members' encounters, the search-loss consequence in section 5 goes away for
member collectors. This design does not depend on that change; the results exception stays either
way, because a member can later leave the organization.

