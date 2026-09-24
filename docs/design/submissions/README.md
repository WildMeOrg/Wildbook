# Submissions contract workbench

This folder contains the local implementation contract, review evidence and pilot
handoff. The API has not been deployed. The accepted direction and
implementation sequence are in [the implementation plan](../../plans/2026-09-23-submissions-api-implementation.md).

- `openapi.yaml`: version-one contract; published OpenAPI reflects the implemented pilot subset.
- `examples.json`: example envelopes and structured validation issues.
- `scripts/submissions/check_contract.py` (repository root): checks local references,
  example schema validity and key routing/precondition invariants. Requires PyYAML
  and jsonschema; it is not a complete OpenAPI conformance validator.
- `BulkSubmissionCompatibilityTest`: new characterization of legacy optional
  location, year-only dates, equivalent row encodings and unknown-field policy.

Run the local contract checks from the repository root:

```bash
python3 scripts/submissions/check_contract.py
```

Stage-one completion requires the targeted legacy baseline, new characterization
tests and Claude review. Each later implementation stage also requires Claude
review before proceeding. Review findings and dispositions are recorded under reviews/.

## Review status

The user explicitly approved sending relevant project files to Claude for
read-only reviews at every stage, excluding credentials and unrelated material.
Stages 1–6 have converged with no Critical or Major findings after corrections.
Claude reviewed each stage read-only; execution evidence below comes from local checks.
Full review transcripts and dispositions are under reviews/.

## Local verification

Baseline on implementation checkout `24cc99aede`, before runtime changes
(PR base is `dcf6f460de`; see the provenance note below):

```bash
mvn -o test -Dtest=BulkApiPostTest,BulkApiOtherTest,BulkGeneralTest,BulkImagesTest,BulkImporterMissingAssetTest,AuthTokenTest,AuthTokenStepUpTest,WildbookTokenAuthenticationFilterTest,'UploadPaths*Test'
```

Result: **106 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.
This is the selected unit-test baseline, not a full build or database recovery test.
The run emitted background datastore diagnostics from existing mocked importer
fixtures but completed successfully. Maven needed execution outside the sandbox
because the canonical capitalized checkout path was treated as read-only.

Initial stage-one contract check (historical): **10 operations/seven examples passed**.
The current contract check covers 11 operations and 18 examples.

New characterization suite:

```bash
mvn -o test -Dtest=BulkSubmissionCompatibilityTest
```

Result: **4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.
The new suite ran separately after the baseline, before runtime implementation.

After Claude round-one fixes, the contract check passes 11 operations and 16
positive/negative examples. The strengthened tests pass:

```bash
mvn -o test -Dtest=BulkSubmissionCompatibilityTest,BulkApiPostTest
```

**21 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.**


## Implementation verification

Stage 2 authentication/draft/persistence tests: **38 passed**, including PostgreSQL
restart, concurrent create/edit, quota race and rollback checks.

Latest combined upload/validation/importer/queue run: **37 passed**, zero failures,
errors or skips. Command:

```bash
mvn -o test -Dtest=SubmissionFilesTest,SubmissionValidatorTest,SubmissionStoreDbTest,BulkImporterSubmissionBoundaryTest,BulkSubmissionCompatibilityTest,BulkApiPostTest,BulkImporterMissingAssetTest
```

Client: `python3 -m unittest discover -s scripts/submissions -p test_client.py`:
**7 passed**, including command-flow create/commit recovery, row correction,
pagination and state-lock tests. Contract checker: **11 operations and 18 examples passed**.

The full clean build ran the frontend: **21 bulk-import suites passed**; the entire
frontend had **130 suites passed, 16 failed; 1,362 tests passed, 40 failed**.
Failures are in unchanged frontend sources (including a missing Citation module
and existing component test expectations); no base-commit frontend comparison was
run, so these are not asserted to be proven pre-existing failures. Production
frontend compilation completed. The clean build's first Java run had **1,108
tests, one failure, one error, seven skipped**. Both failures were new test
fixtures missing usernames. Those fixtures are corrected; the final full Java
rerun passed as recorded below.

Corrected PostgreSQL rerun: `mvn -o test -Dtest=SubmissionStoreDbTest`:
**15 passed, zero failures/errors/skips; BUILD SUCCESS**. This includes the actual
two-image adapter import, caller rollback, cleanup retention/locking, daily quota,
invalid-validation rejection and replay pagination.

`check_contract.py --runtime` passes both captured server responses (capabilities
and commit acceptance) against both the draft and published OpenAPI schemas.
These local tests do not replace the QA/browser release gate in
[pilot-runbook.md](pilot-runbook.md).
No installation has been deployed or enrolled.

Final full Java regression: `mvn -o install`: **1,109 tests, zero failures, zero
errors, seven skipped**. This includes all submissions/authentication tests and
the existing bulk-import, upload, permissions and database suites. **BUILD SUCCESS**,
including WAR packaging and local Maven installation. Frontend tests were run by the preceding clean invocation and
retain the separate failure limitation above.

Existing published OpenAPI paths and schemas were compared with the original
checkout and remain unchanged; new route/auth documentation is additive.

## Final addition: published agent skill

At the user's request, added after the full implementation/build: the public
`/api/v3/agent-skill/submit-sightings` resource, registered in AgentSkill and linked
from the base toolbox and read-only API reference. It includes all supported
fields, installation settings discovery, wire formats, field-specific validation
failures, authorization boundaries, retry reconciliation and result mapping.
`mvn -o test -Dtest=AgentSkillTest,AgentSkillContentTest`: **15 passed, zero
failures/errors/skips; BUILD SUCCESS**. Existing skill routing/content checks and
new runtime-parsed request examples passed. Two Claude review rounds converged
with no Critical or Major issues; transcripts and disposition are under reviews/.
This addition does not change submissions runtime behavior.

Final artifact after the agent-skill addition: `mvn -o -DskipTests package`: **BUILD
SUCCESS**. Tests were deliberately not rerun during packaging; the full API and
subsequent skill test runs are recorded above. Verified the WAR contains byte-for-byte
current submit-sightings, toolbox and API-reference resources, the new catalog
registration, submissions servlet and JDO metadata. Artifact:
`target/wildbook-10.14.war`. The deployment descriptor is handled separately as
explained in the pilot runbook. No deployment or account enrollment was performed.

## PR base and verification provenance

The PR branch is based on `main` at `dcf6f460de`, excluding the separate mobile-layout
commit `24cc99aede` present during implementation/testing. The difference between
those bases contains only frontend files; Java sources and dependencies are identical.
The frontend results and built WAR above therefore describe the earlier checkout,
not a fresh frontend build of the PR base. No frontend changes are part of this PR.
QA/browser verification on the final branch remains a release gate.

Claude also approved the final PR handoff with no blockers; see
[PR handoff review](reviews/pr-handoff-review.md). The suggested link and
verification-provenance wording clarifications were incorporated.
