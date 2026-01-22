-- ============================================================================
-- Migration: MeasurementEvent to Measurement
-- Database: PostgreSQL
-- Records: ~40,871 MeasurementEvent records
--
-- Purpose: Migrate iot branch from MeasurementEvent to Measurement class
--          to align with main branch codebase
--
-- IMPORTANT: Run steps in order. Step 2 requires code merge first!
-- ============================================================================

-- ============================================
-- STEP 0: BACKUP (Run first!)
-- ============================================
-- Always backup before migration

BEGIN;

CREATE TABLE "MEASUREMENTEVENT_BACKUP" AS SELECT * FROM "MEASUREMENTEVENT";
CREATE TABLE "ENCOUNTER_MEASUREMENTS_BACKUP" AS SELECT * FROM "ENCOUNTER_MEASUREMENTS";
CREATE TABLE "DATACOLLECTIONEVENT_BACKUP" AS SELECT * FROM "DATACOLLECTIONEVENT";

-- Verify backups created with correct counts
SELECT 'MEASUREMENTEVENT_BACKUP' as table_name, COUNT(*) as row_count FROM "MEASUREMENTEVENT_BACKUP"
UNION ALL
SELECT 'ENCOUNTER_MEASUREMENTS_BACKUP', COUNT(*) FROM "ENCOUNTER_MEASUREMENTS_BACKUP"
UNION ALL
SELECT 'DATACOLLECTIONEVENT_BACKUP', COUNT(*) FROM "DATACOLLECTIONEVENT_BACKUP";

-- Expected output:
-- MEASUREMENTEVENT_BACKUP        | 40871
-- ENCOUNTER_MEASUREMENTS_BACKUP  | 40871 (approximately)
-- DATACOLLECTIONEVENT_BACKUP     | (larger number, includes other types)


-- ============================================
-- STEP 1: Copy MeasurementEvent data to Measurement
--
-- Can run BEFORE or AFTER code merge
-- MEASUREMENT table should be empty (0 records)
-- Same DATACOLLECTIONEVENTID values work because they
-- reference existing DATACOLLECTIONEVENT parent records
-- ============================================

-- First verify MEASUREMENT is empty
SELECT COUNT(*) as current_measurement_count FROM "MEASUREMENT";
-- Expected: 0

-- Copy all MeasurementEvent records to Measurement
INSERT INTO "MEASUREMENT" ("DATACOLLECTIONEVENTID", "UNITS", "VALUE")
SELECT "DATACOLLECTIONEVENTID", "UNITS", "VALUE"
FROM "MEASUREMENTEVENT";

-- Verify copy succeeded
SELECT 'MEASUREMENTEVENT (source)' as table_name, COUNT(*) as row_count FROM "MEASUREMENTEVENT"
UNION ALL
SELECT 'MEASUREMENT (target)', COUNT(*) FROM "MEASUREMENT";

-- Both counts should match (40871)


-- ============================================
-- STEP 2: Update join table FK to point to MEASUREMENT
--
-- *** RUN ONLY AFTER MERGING MAIN BRANCH CODE ***
-- *** AND UPDATING JDO MAPPING ***
--
-- The code must expect List<Measurement> before this step
-- ============================================

-- Drop the old FK constraint pointing to MEASUREMENTEVENT
ALTER TABLE "ENCOUNTER_MEASUREMENTS"
DROP CONSTRAINT "ENCOUNTER_MEASUREMENTS_FK2";

-- Add new FK constraint pointing to MEASUREMENT
ALTER TABLE "ENCOUNTER_MEASUREMENTS"
ADD CONSTRAINT "ENCOUNTER_MEASUREMENTS_FK2"
FOREIGN KEY ("DATACOLLECTIONEVENTID_EID")
REFERENCES "MEASUREMENT"("DATACOLLECTIONEVENTID")
DEFERRABLE INITIALLY DEFERRED;

-- Verify FK was updated
SELECT
    tc.constraint_name,
    tc.table_name,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.table_name = 'ENCOUNTER_MEASUREMENTS'
    AND tc.constraint_type = 'FOREIGN KEY';

-- Should show FK2 now references MEASUREMENT, not MEASUREMENTEVENT


-- ============================================
-- STEP 3: Verify migration
-- ============================================

-- Check all counts match
SELECT 'MEASUREMENTEVENT' as table_name, COUNT(*) as row_count FROM "MEASUREMENTEVENT"
UNION ALL
SELECT 'MEASUREMENT', COUNT(*) FROM "MEASUREMENT"
UNION ALL
SELECT 'ENCOUNTER_MEASUREMENTS', COUNT(*) FROM "ENCOUNTER_MEASUREMENTS";

-- Test join works: Sample encounters with their measurements
SELECT
    e."CATALOGNUMBER",
    m."DATACOLLECTIONEVENTID",
    d."TYPE",
    m."VALUE",
    m."UNITS"
FROM "ENCOUNTER" e
JOIN "ENCOUNTER_MEASUREMENTS" em ON e."CATALOGNUMBER" = em."CATALOGNUMBER_OID"
JOIN "MEASUREMENT" m ON em."DATACOLLECTIONEVENTID_EID" = m."DATACOLLECTIONEVENTID"
JOIN "DATACOLLECTIONEVENT" d ON m."DATACOLLECTIONEVENTID" = d."DATACOLLECTIONEVENTID"
LIMIT 20;

-- Count measurements per encounter (sanity check)
SELECT
    em."CATALOGNUMBER_OID" as encounter_id,
    COUNT(*) as measurement_count
FROM "ENCOUNTER_MEASUREMENTS" em
JOIN "MEASUREMENT" m ON em."DATACOLLECTIONEVENTID_EID" = m."DATACOLLECTIONEVENTID"
GROUP BY em."CATALOGNUMBER_OID"
ORDER BY measurement_count DESC
LIMIT 10;

END;

-- ============================================
-- STEP 4: Cleanup (ONLY after full verification!)
--
-- Wait at least 1 week after successful deployment
-- Ensure application is working correctly
-- Keep backups until confident
-- ============================================

-- Uncomment these lines ONLY when ready to permanently remove old data:

-- DROP TABLE "MEASUREMENTEVENT";
-- DROP TABLE "MEASUREMENTEVENT_BACKUP";
-- DROP TABLE "ENCOUNTER_MEASUREMENTS_BACKUP";
-- DROP TABLE "DATACOLLECTIONEVENT_BACKUP";

-- Note: DATACOLLECTIONEVENT parent records should NOT be deleted
-- They are still referenced by the new MEASUREMENT records


-- ============================================
-- ROLLBACK (If something goes wrong)
-- ============================================

-- If you need to rollback BEFORE Step 2 (FK change):
-- DELETE FROM "MEASUREMENT";
-- DROP TABLE "MEASUREMENTEVENT_BACKUP";
-- DROP TABLE "ENCOUNTER_MEASUREMENTS_BACKUP";
-- DROP TABLE "DATACOLLECTIONEVENT_BACKUP";

-- If you need to rollback AFTER Step 2 (FK change):
-- ALTER TABLE "ENCOUNTER_MEASUREMENTS" DROP CONSTRAINT "ENCOUNTER_MEASUREMENTS_FK2";
-- ALTER TABLE "ENCOUNTER_MEASUREMENTS"
-- ADD CONSTRAINT "ENCOUNTER_MEASUREMENTS_FK2"
-- FOREIGN KEY ("DATACOLLECTIONEVENTID_EID")
-- REFERENCES "MEASUREMENTEVENT"("DATACOLLECTIONEVENTID")
-- DEFERRABLE INITIALLY DEFERRED;
-- DELETE FROM "MEASUREMENT";
