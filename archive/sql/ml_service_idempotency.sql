-- ml-service migration v2 (commit #4): annotation idempotency + WBIA-registration backfill
--
-- This script complements the JDO mapping in src/main/resources/org/ecocean/package.jdo.
-- DataNucleus auto-creates the new ANNOTATION columns (PREDICTMODELID, BBOXKEY, THETAKEY,
-- WBIAREGISTERED, WBIAREGISTERATTEMPTS) on next startup. This file adds the parts
-- DataNucleus cannot auto-create: a Postgres partial unique index for ml-service
-- idempotency, a CHECK constraint that the composite columns are populated together,
-- and a one-time backfill marking legacy annotations as already-registered with WBIA.
--
-- Safe to re-run. Each statement is either idempotent (CREATE INDEX IF NOT
-- EXISTS, ALTER COLUMN, ADD CONSTRAINT guarded by pg_constraint lookup) or
-- filters on the pre-backfill state (UPDATEs touching only NULL rows).

BEGIN;

-- (1) Partial unique index: idempotency for ml-service-created annotations.
--     Filters on PREDICTMODELID IS NOT NULL so legacy WBIA-era rows are unaffected.
--     NOTE: Postgres unique indexes treat NULL as distinct, so any of the four
--     composite columns being NULL would defeat the constraint. The CHECK
--     constraint below guarantees the other three are also non-null when
--     PREDICTMODELID is non-null.
CREATE UNIQUE INDEX IF NOT EXISTS "ANNOTATION_MLSERVICE_IDEM_idx"
ON "ANNOTATION" ("MEDIAASSET_ID_OID", "PREDICTMODELID", "BBOXKEY", "THETAKEY")
WHERE "PREDICTMODELID" IS NOT NULL;

-- (2) CHECK constraint: when PREDICTMODELID is non-null, the other composite
--     columns must also be non-null. Defense in depth against partial fills
--     that would silently bypass the partial unique index.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'annotation_mlservice_composite_check'
          AND conrelid = '"ANNOTATION"'::regclass
    ) THEN
        ALTER TABLE "ANNOTATION" ADD CONSTRAINT annotation_mlservice_composite_check
            CHECK (
                "PREDICTMODELID" IS NULL OR (
                    "MEDIAASSET_ID_OID" IS NOT NULL AND
                    "BBOXKEY" IS NOT NULL AND
                    "THETAKEY" IS NOT NULL
                )
            );
    END IF;
END $$;

-- (3) Harden WBIAREGISTERATTEMPTS at the SQL level. DataNucleus creates the
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

-- (4) One-time WBIA-registration backfill: legacy annotations that already
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

-- (5) Partial index for the WBIA-registration polling thread (commit #11
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

-- (6) Partial index for the startup stale-mlservice reconciler (commit
--     #12 fix-pass). The reconciler's JDOQL filter is
--         detectionStatus == 'processing-mlservice' AND revision < <cutoff>
--     run once at startup. Partial-on-detectionStatus keeps the index
--     tiny: assets in any other state never enter the index, and a
--     healthy deployment has at most a handful in-flight at any moment.
CREATE INDEX IF NOT EXISTS "MEDIAASSET_STALE_MLSERVICE_IDX"
ON "MEDIAASSET" ("REVISION")
WHERE "DETECTIONSTATUS" = 'processing-mlservice';

COMMIT;
