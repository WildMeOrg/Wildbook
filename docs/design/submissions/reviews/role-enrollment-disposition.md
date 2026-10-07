# Role enrollment and API Access token UI review

Actual Claude CLI reviews were performed for architecture, implementation,
final code/documentation, and the final corrective change. The final corrective
review concluded: **“Verdict: Approved. My remaining blocker is fixed.”**
The architecture round inspected repository sources using read-only tools;
subsequent rounds reviewed supplied source/diffs without tools. These were code
reviews, not live browser or deployed authorization tests.

Resolved review topics:

- Explicit context0 enrollment managed by site administrators; no bootstrap or
  administrator implicit enrollment. Reserved role names remain excluded from
  location-based access while bootstrap grants remain unchanged.
- Fresh persisted enrollment checks, distinct token audiences, and monitoring
  access after write revocation. The legacy UUID setting is ignored.
- Account rename, username ownership, role replacement and merge behavior preserve
  deliberate account enrollment. Added PostgreSQL checks for the relevant queries.
- Read data remains the UI default; Data import explicitly requests write scope
  using fresh password confirmation. Switching purpose clears the displayed token.
- New UI strings cover all five languages. Operator instructions and public agent
  skills describe migration, token purposes and revocation limits; skills were
  updated after the implementation.

Verification:

- Full `mvn -o package`: 1,132 tests, zero failures/errors, seven skipped.
- After the final rename correction: `mvn -o package` with the affected user-role,
  PostgreSQL, policy, token-scope and agent-skill suites: 20 tests, all passing.
- Focused API Access and token helper Jest suites: 10 tests passed.
- React production build completed with warnings in existing dependencies/source.
- Contract checker: all 11 operations and 21 examples passed.

The role/UI follow-up has not been deployed. During rollout, a site administrator
must grant api-submission to existing pilot users; the old UUID list does not enroll
them. Verify token creation, import admission and revocation on the deployed site.
