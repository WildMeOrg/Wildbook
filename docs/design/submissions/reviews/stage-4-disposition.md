# Stage 4 disposition

Claude round 1 found a Critical implicit-transaction-commit problem in the legacy
Shepherd save helpers. Deferred mode now persists encounters, occurrences,
individuals and projects directly through the caller's PersistenceManager. Legacy
mode retains its helpers. ImportTask saves also use direct persistence so errors
propagate. Deferred mode skips independent progress transactions, cache eviction
and derivative/index dispatch; raw row diagnostics are suppressed on this path.

Round 2 found no Critical or Major issues and traced all reachable calls for the
strict pilot field subset. Added explicit helper/dispatch boundary assertions and
a real PostgreSQL rollback test for the deferred importer. The adapter revalidates
approved revision/digests, resolves owner, rejects missing usernames, reuses media
creation and records source-row mappings from actual row resolution.

Legacy grouped-row/year-precision/missing-media fixtures remain in the targeted
regression run. The combined run passed 37 tests; the later real importer rollback
check and full integration build are recorded in README.
