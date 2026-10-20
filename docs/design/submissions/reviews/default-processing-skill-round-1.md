# Review: submit-sightings skill update (detect-and-identify default)

I had no file or shell tools in this session, so this review covers only the diff and full skill text you pasted. I couldn't check any claim against `SubmissionProcessing.java`, `openapi.yaml` or the capabilities response. Where a point depends on backend behaviour, I say so.

**Overall:** the update is careful. It keeps records separate from AI handoff, forbids resending, and forbids reporting `dispatched` as completed identification. The main gaps for an agent are:
- **Access:** a step it has no access to (the import task page).
- **Polling:** no rule for when to stop polling.
- **Missing details:** unnamed fields and unclear status semantics.
- **Mode choice:** no clear rule for which mode to send.

## High priority

1. **"Inspect the linked import task for progress and match candidates" is probably impossible for the agent.** `links.importTask` is a web page. The skill also says "cookies do not substitute for scoped tokens", so a `submissions:*` bearer token almost certainly can't read it. As written, the `dispatched` and `failed` rows point the agent at something it can't do. Reword so the agent gives the task link to the person: "match candidates are reviewed by a person in Wildbook."

2. **There's no rule for when to stop polling after `imported`.**
   - `pending` waits on derivatives. If derivatives are `unknown`, does AI stay `pending` forever or move to `unknown`? This needs stating.
   - `dispatching` can last up to one hour before it's held as `unknown`. An agent following "wait and poll" could sit there for an hour.
   - Suggested text: "After `imported`, fetch results and report. You may keep polling within your budget until the AI phases leave `pending`/`dispatching`. Otherwise report them as not yet handed off. `dispatched`, `failed`, `unknown` and `skipped` are final as far as this API is concerned."

3. **A failed AI handoff doesn't say whether the submission state changes.** The states table has no row for "state `imported`, but detection `failed`/`unknown`". Say explicitly that `AI_HANDOFF_*` leaves the submission `imported` (if that's true). Also say whether an uncertain AI handoff counts toward the "one queued/importing/uncertain job per owner" quota. If it does, the next batch gets a 429 and the agent needs to know why.

4. **Phase code fields aren't named.** "Check its phase code/message" and "reports `IMPORT_FAILED`…" don't give field names (e.g. `detection.code`) or say which resource carries them. The section sits under results, but a failed import may have no results page. Name the fields and the resource, e.g. `GET /submissions/{id}`.

5. **The agent has no clear rule for choosing a mode.** "Clients should send their intended mode explicitly" is right, but give the actual rule:
   - Send `detect-and-identify` unless the person asked for import-only, or `processingModes` lacks it.
   - In that second case, stop and ask. Don't fall back to import-only.

   Also give the shape of `processingModes` (strings or objects? is there a default marker?) and the error an older server returns for an unsupported mode.

   This matters more because `contractVersion` is still `"1"` while the meaning of an omitted mode changed. An agent can't tell from the version which default applies.

## Medium priority

6. **Validation report check.** Add "confirm `processing.mode` matches the intended mode", alongside the existing `effectiveOwnerId` check before commit.

7. **Previews should state the mode.** The person's import approval then clearly covers detection and matching. They create annotations, may create more encounters, and can't be undone through this API.

8. **Encounter count conflict.** "One per row" in *What it does* clashes with "Detection may add … additional encounters". Qualify it as "Import creates one encounter per row; detection may later add more."

9. **What import-only gives up isn't stated.** No API call starts AI later for an import-only submission, and there's no retry for a failed or unknown handoff. Say so where the mode is chosen, not only in the "do not rerun" line. That line should also list detection.

10. **The create-retry paragraph is hard to follow.** It mixes migration history ("older omitted-mode submissions normalized…") with instructions. Split it into:
    - **Rule:** always send the mode, and retry with the byte-identical saved body.
    - **Note:** a draft saved before this version keeps import-only; don't recreate it to get matching, tell the person.

    Also check that the backend's retry comparison can't turn an omitted-mode replay into `IDEMPOTENCY_KEY_REUSED` after the upgrade.

11. **Whether capabilities reflects IA readiness.** If `detect-and-identify` is advertised even when IA isn't configured, the agent learns about it only after records are imported (`AI_HANDOFF_FAILED`). Say this, so the agent can warn the person that the records will exist without matching.

## Reporting guidance

- Also forbid reporting `dispatched` as completed **detection**, not just identification.
- Report the mode used. For explicit import-only, say detection and matching weren't requested.
- For `IMPORT_*` codes: say AI didn't start. For `AI_HANDOFF_*`: say the records exist but the AI handoff failed or is uncertain.

## Minor

- "IA" is never expanded; write "image analysis (IA)".
- "Use the import task for downstream processing" is vague. Say what the person does there.
- The frontmatter `description` doesn't mention detection or matching.
- `index.md` has one very long line, where the rest of the file wraps.
- The index says "individual matching"; the skill says "identification matching". Pick one term.

## Test coverage

`AgentSkillContentTest` checks only the example's mode. Worth adding:
- The AI state table lists exactly the states defined in the processing code (`pending`/`dispatching`/`dispatched`/`failed`/`unknown`), so they can't drift apart.
- Both mode names and the four `IMPORT_*`/`AI_HANDOFF_*` codes appear in the skill.
- `index.md` mentions `import-only`.
