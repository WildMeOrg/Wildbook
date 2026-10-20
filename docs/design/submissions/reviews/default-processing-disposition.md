# Default detection and identification follow-up

The operator changed the new submissions API default to detection followed by individual matching. Explicit import-only and saved submissions retain their previous behavior. Legacy bulk import and its unchecked publisher remain unchanged; the submissions adapter uses a separate checked queue publication method.

## Claude reviews

Actual Claude CLI reviewed the architecture, implementation, corrective rounds and final agent skill using supplied code/text. Review sessions did not access the live installation or credentials. The agent skill was updated after implementation and backend verification.

- Architecture: task/message persistence before publishing, existing-root guard, old-mode retention, callback configuration, blocked derivative outcomes and operator recovery addressed.
- Implementation: verified real legacy producer and importer result shape; a golden test compares messages, including string media IDs. Queue temp names are ignored by consumers; checked publication uses atomic rename and file/directory synchronization. No automatic reader of queue resume messages was found.
- Recovery: two workers cannot publish the same handoff, a failed preparation commit cannot publish, ambiguous sends are held, and stale claims are fenced under the submission lock. Imported-record failures and AI-handoff failures have distinct phase codes and not-started semantics.
- Client: original creation body/mode are persisted, including import-only interpretation of older client state; direct unittest execution includes the mode tests.
- Operator documentation: nullable AI_STATE/AI_STARTED_AT rollout, queue permissions, callback configuration, and manual reconciliation are documented. Existing admin resend paths are manual and must not compete with a worker-owned handoff.

Backend final verdict: approved, no concrete blockers. See the final review transcripts. No deployment of this follow-up or live detection/identification test has been performed yet.

Final agent-skill verdict: approved, no blocking inaccuracies. The two nonblocking wording suggestions were applied (unknown AI outcome versus uncertain record import; missing capabilities field).

Verification: final full Java/WAR build passed 1,123 tests, zero failures/errors, seven skipped. Focused compatibility suite passed 58 tests; final review corrections passed focused processing/database tests. Python client: eight tests passed. OpenAPI: 11 operations, 21 examples and runtime responses passed against both specs.

Final resource/phase verification: 20 focused tests passed and WAR packaging succeeded. Packaged agent skills, OpenAPI and submission JDO metadata match the source files byte-for-byte, and the processing adapter class is included.
