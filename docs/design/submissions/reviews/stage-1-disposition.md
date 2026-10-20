# Stage 1 review disposition

Claude round 1: six Major findings, no Critical findings. The user authorized
read-only external Claude review of relevant project files.

- M1: validation never bumps input revision; empty body plus If-Match; returns
  ETag; repeat validation safely recovers a lost response.
- M2: owner GET exposes terminal tombstones; repeated cancellation returns 204;
  all post-acceptance/failed/uncertain states are explicitly non-cancellable.
- M3: published stable error codes and optional issues; invalid versus stale
  validation has distinct status/code behavior.
- M4: added GET rows plus rowCount/rowsDigest for recovery after lost PUT response.
- M5: occurrence/individual result IDs are nonempty strings, not forced UUIDs.
- M6: servlet-level legacy unknown-field-default test, importer-level repeated-ID
  grouping/year precision test, exact day diagnostic, short-array null padding.
  Existing BulkApiPostTest duplicate/synonym tests, BulkImagesTest missing/corrupt
  image tests and BulkApiOtherTest status/authorization fixtures cover those
  legacy cases. Media-per-encounter enforcement is new behavior to test in stage 3;
  no claim that these unit tests establish durable background/IA correctness.

Minor fixes: clarify omission of whole processing object; remove irrelevant
precondition responses; define exact upload filename/retry behavior; require GET
after create replay; advertise Bearer only; permit additive response fields;
remove YAML aliases; strengthen format/negative/example/HTTP checks; add response
examples and result links.

Round 2 and updated test results pending. Earlier baseline: 106 existing tests
passed. Earlier new characterization run: four tests passed. These do not replace
running the strengthened suites.

Round 2: **No Major findings remain; nothing blocking.** All six Majors resolved.
Corrected the accidental shared filename description, explicitly registered format
checks with negative UUID/date examples, clarified {} validation input, retained
rows during tombstone retention, marked rowsDigest informational, documented error
mappings, and made warning/grouping assertions more precise. The required mode's
default is documentation of whole-processing omission, not schema default filling.
Strengthened suites passed 21 tests; final small assertion changes are rerun with
the next test gate. Stage 1 is cleared for runtime implementation.
