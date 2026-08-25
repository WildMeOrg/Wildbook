-- ml-service migration v2: WBIA-registration columns + supporting indexes
--
-- This script complements the JDO mapping in src/main/resources/org/ecocean/package.jdo.
-- DataNucleus auto-creates the new ANNOTATION columns (WBIAREGISTERED,
-- WBIAREGISTERATTEMPTS) on next startup. This file adds the parts DataNucleus
-- cannot auto-create: a one-time backfill marking legacy annotations as
-- already-registered with WBIA, and partial indexes for the registration poller
-- and the stale-mlservice reconciler.
--
-- Duplicate-detection idempotency does NOT need any DB support: it is handled
-- in-memory via Feature geometry (MlServiceProcessor.findExistingAnnotation),
-- the same way the legacy WBIA detection path does it. There is therefore no
-- idempotency unique index or composite CHECK constraint.
--
-- Safe to re-run. Each statement is either idempotent (CREATE INDEX IF NOT
-- EXISTS, ALTER COLUMN) or filters on the pre-backfill state (UPDATEs touching
-- only NULL rows).

BEGIN;

-- (0) Clean up superseded idempotency objects from the earlier version of
--     this script / JDO mapping, if a deployment already applied them.
--     Duplicate detection is now in-memory (Feature geometry), so the
--     composite unique index, its CHECK constraint, the predictModelId
--     index, and the three idempotency columns are no longer used.
--     All guarded with IF EXISTS, so this is a no-op on environments that
--     never created them. Indexes on a column are dropped automatically
--     when the column is dropped, but we drop them explicitly first for
--     clarity. The columns are safe to drop because DataNucleus no longer
--     maps them (fields removed from package.jdo).
ALTER TABLE "ANNOTATION" DROP CONSTRAINT IF EXISTS annotation_mlservice_composite_check;
DROP INDEX IF EXISTS "ANNOTATION_MLSERVICE_IDEM_idx";
DROP INDEX IF EXISTS "ANNOTATION_PREDICTMODELID_IDX";
ALTER TABLE "ANNOTATION" DROP COLUMN IF EXISTS "PREDICTMODELID";
ALTER TABLE "ANNOTATION" DROP COLUMN IF EXISTS "BBOXKEY";
ALTER TABLE "ANNOTATION" DROP COLUMN IF EXISTS "THETAKEY";

-- (1) Harden WBIAREGISTERATTEMPTS at the SQL level. DataNucleus creates the
--     column from package.jdo with allows-null=false + default 0, but if an
--     older DataNucleus run on this deployment already created it without
--     those properties (rare but possible), this block repairs it idempotently.
--     SET DEFAULT first so new rows get 0; backfill NULLs to 0 (with VERSION
--     bump for OpenSearch reindex); then SET NOT NULL.
ALTER TABLE "ANNOTATION"
    ALTER COLUMN "WBIAREGISTERATTEMPTS" SET DEFAULT 0;

UPDATE "ANNOTATION"
SET "WBIAREGISTERATTEMPTS" = 0,
    "VERSION" = (EXTRACT(EPOCH FROM now()) * 1000)::bigint
WHERE "WBIAREGISTERATTEMPTS" IS NULL;

ALTER TABLE "ANNOTATION"
    ALTER COLUMN "WBIAREGISTERATTEMPTS" SET NOT NULL;

-- (2) One-time WBIA-registration backfill: legacy annotations that already
--     have an acmId were registered with WBIA via the historical IBEISIA
--     flow. Mark them as registered so the new background-polling thread
--     does NOT re-register them.
--
--     Bumps VERSION in the same statement per the repo rule for direct SQL
--     writes to indexed ANNOTATION rows (OpenSearch reindexer reads VERSION).
--     Filters on WBIAREGISTERED IS NULL so re-running this script only
--     touches rows the previous run missed.
UPDATE "ANNOTATION"
SET "WBIAREGISTERED" = TRUE,
    "VERSION" = (EXTRACT(EPOCH FROM now()) * 1000)::bigint
WHERE "ACMID" IS NOT NULL AND "WBIAREGISTERED" IS NULL;

-- (3) Partial index for the WBIA-registration polling thread (commit #11
--     fix-pass). The poller's JDOQL filter is
--         wbiaRegistered == false AND wbiaRegisterAttempts < 10
--     ordered by wbiaRegisterAttempts ASC. Partial-on-FALSE keeps the
--     index tiny: legacy rows are TRUE post-backfill, registered rows are
--     TRUE, and only the small still-pending set lives in the index.
--     The predicate matches the poller's filter exactly (also excluding
--     parked rows at attempts == MAX_ATTEMPTS, so abandoned rows never
--     hit the index).
CREATE INDEX IF NOT EXISTS "ANNOTATION_WBIAREGISTER_PENDING_IDX"
ON "ANNOTATION" ("WBIAREGISTERATTEMPTS")
WHERE "WBIAREGISTERED" = FALSE AND "WBIAREGISTERATTEMPTS" < 10;

-- (4) Partial index for the startup stale-mlservice reconciler (commit
--     #12 fix-pass). The reconciler's JDOQL filter is
--         detectionStatus == 'processing-mlservice' AND revision < <cutoff>
--     run once at startup. Partial-on-detectionStatus keeps the index
--     tiny: assets in any other state never enter the index, and a
--     healthy deployment has at most a handful in-flight at any moment.
CREATE INDEX IF NOT EXISTS "MEDIAASSET_STALE_MLSERVICE_IDX"
ON "MEDIAASSET" ("REVISION")
WHERE "DETECTIONSTATUS" = 'processing-mlservice';

COMMIT;
