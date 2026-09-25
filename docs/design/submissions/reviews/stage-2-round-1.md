No Critical findings. I have one Major finding, and it's about scope containment, not privilege escalation. Everything else is Minor. I only read the code; I didn't run any tests.

## Major

**1. Submission-scoped tokens also work anywhere identity tokens work.**
`JwtService.signSubmission` (`api/auth/JwtService.java:104`) uses the same issuer and audience as identity-only tokens. It only adds a `submissionScope` claim.
- `WildbookTokenAuthenticationFilter` (`security/WildbookTokenAuthenticationFilter.java:73-76`) never looks at that claim. So a `submissions:read` or `submissions:write` token is accepted on `/api/v3/search/**` and `/api/v3/media/resolve`.
- The external scoped-access service described in the JwtService Javadoc would also accept it as a full identity token.
- Blocking unscoped tokens from submissions was done. Keeping scoped tokens out of everything else was not.
- The practical risk is limited: minting needs the same password as an identity token, so no one gains privileges. But a leaked pilot client token gives search access for the token's lifetime (up to 24h), not just access to drafts. Since scoped credentials are the point of this stage, I'd fix it now.
- **Fix:** mint submission tokens with a separate audience, e.g. a new `jwtSubmissionAudience` setting defaulting to `wildbook-submissions`. Add a `verify(token, audience)` overload and use it in `SubmissionAuthenticationFilter`. That keeps both the search filter and the external service rejecting these tokens with no change on their side. As an extra safeguard, have `WildbookTokenAuthenticationFilter` reject any token that has a `submissionScope` claim. Add a test showing a scoped token gets 401 on search.

## Minor

2. **The published spec and the capabilities response disagree.** `SubmissionApiCapabilities.limits` has `additionalProperties: false` (`openapi.yaml:396`), but `Submissions.java:76` returns `maxDraftsPerUser`. Strict generated clients will reject the response. Also, `stage-2-operations.md:32-33` says the 256-fields-per-row limit is published in capabilities, but it isn't. Fix: add `maxDraftsPerUser` and `maxFieldsPerRow` to the schema, and return `maxFieldsPerRow`.

3. **PUT rows documents 410 for expired or cancelled drafts, but the code returns 409.** `openapi.yaml:2084` lists 410 for "Expired or cancelled draft". `SubmissionStore.editable` always returns 409 `INVALID_STATE`, which matches what DELETE documents. Fix: remove the 410 from PUT, or return 410 `GONE` there.

4. **The 20-draft quota says to retry in 5 seconds.** `SubmissionAuthenticationFilter.error` sends `Retry-After: 5` on every 429. The draft cap won't clear in 5 seconds; it clears when a draft is cancelled or expires. Clients that honour the header will keep retrying pointlessly. Fix: leave out `Retry-After` for this quota (or report when the oldest draft expires), and keep it for real rate limits.

5. **The filter's `try` block also wraps `chain.doFilter`** (`SubmissionAuthenticationFilter.java:71-76`). Any `IOException`, `ServletException` or runtime exception from later in the chain is logged as a generic 503 "authentication unavailable". It may also be written onto a response that has already been committed. Fix: finish authentication inside the `try`, then call `chain.doFilter` outside it.

6. **Request bodies are parsed leniently.** `new JSONObject(String)` in org.json 20240303 accepts unquoted keys and values, single quotes, and (I believe) extra text after the closing `}`. `Submissions.body` also silently replaces invalid UTF-8 bytes, which changes stored field values. The strict key and type checks still bound the envelope, so the risk is low. Fix: decode with a `CharsetDecoder` set to `REPORT`, and reject trailing content after parsing (or parse strictly with Jackson). Only enrolled writers can reach the parser, so the unbounded nesting depth risk is small, but a depth-capped parser would also cover it.

7. **Error order: admission is checked before routing** (`Submissions.java:23`). A PUT or POST to a route that doesn't exist returns 503 or 403 instead of 404. That's cosmetic, but it misleads clients probing capabilities.

8. **The If-Match pattern differs slightly.** The spec's `^"[0-9]+"$` accepts values that the code rejects with 400 (`{1,18}` digits). Add `maxLength: 20` or a bounded pattern to the spec.

9. **Some behaviours this stage relies on aren't tested:**
   - Concurrent creates with *distinct* keys near the 20-draft cap. That case is the reason for the per-owner lock.
   - Logical expiry: an expired draft stays readable, rejects edits with 409, and doesn't count toward the quota.
   - An admin accessing another owner's draft.
   - The negative test for finding 1.

   The existing competing-edit test is good. Getting 412 rather than 503 shows the advisory lock is serializing, not just the JDO version check.

## Checked and correct
- Transaction handling:
  - No optimistic-transaction setting in `jdoconfig`, so JDO runs datastore transactions and `pg_advisory_xact_lock` is held on the transaction's own connection until commit or rollback.
  - The `JDOConnection` is closed before further persistence-manager calls.
  - Lookups use `setIgnoreCache` plus `refresh`.
  - Responses are built before commit.
  - `commitDBTransactionWithStatus` maps a failed commit to 503.
- Create idempotency:
  - The key hash is scoped to context, owner and operation.
  - Canonical hashing makes an omitted `processing` equal to an explicit import-only one.
  - The replay check runs before the quota check.
  - A replay returns exactly the original body, as the test confirms.
- Ownership: non-owners get 404, the context is checked, and admin status comes from the token's user.
- Session handling: the filter never calls `login` or `getSession`, and `isUserInRole` ignores roles from the cookie session.
- Gates:
  - GET works with either scope and has no admission check.
  - Writes need write scope and re-check enrollment in both the filter and the servlet.
  - AuthToken only grants write scope to enrolled users while admission is enabled.
- Existing tokens: `sign()` without a scope produces the same claims as before, and the Shiro `[urls]` order puts `submissionAuth` ahead of the bulk rules. Bulk and browser routes are unchanged.
- Cancellation: a repeat cancel returns 204 before the revision comparison, as documented, and editing an expired draft returns 409.
- The JDO mapping (UUID primary key, unique key hash, `LOCK_VERSION`, `LONGVARCHAR`) matches the design. The enhancer's default includes pick up `package.jdo`.

Once finding 1 is fixed, I'd consider stage 2 converged with no Major findings. The Minor items can go into this change or be tracked separately.
