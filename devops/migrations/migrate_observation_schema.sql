-- ============================================================================
-- Migration: Observation Schema (iot branch → main branch)
-- Database: PostgreSQL
--
-- Purpose: Migrate from DataPoint-based OBSERVATION to main branch OBSERVATION
--          - DPOBSERVATION (1 record, correct structure) → OBSERVATION
--          - Old OBSERVATION + DATAPOINT (form templates) → archived/dropped
--
-- Records to migrate: 1 (in DPOBSERVATION)
-- Records to archive: 2,762 (in OBSERVATION - form templates, mostly empty)
--
-- IMPORTANT: Run steps in order. Backup first!
-- ============================================================================

-- ============================================
-- STEP 0: PRE-MIGRATION ANALYSIS
-- ============================================
-- Run these queries to verify current state before migration

-- Check record counts
SELECT 'DPOBSERVATION' as table_name, COUNT(*) as records FROM "DPOBSERVATION"
UNION ALL
SELECT 'OBSERVATION', COUNT(*) FROM "OBSERVATION"
UNION ALL
SELECT 'DATAPOINT', COUNT(*) FROM "DATAPOINT"
UNION ALL
SELECT 'ENCOUNTER_OBSERVATIONS', COUNT(*) FROM "ENCOUNTER_OBSERVATIONS"
UNION ALL
SELECT 'OCCURRENCE_OBSERVATIONS', COUNT(*) FROM "OCCURRENCE_OBSERVATIONS"
UNION ALL
SELECT 'SURVEY_OBSERVATIONS', COUNT(*) FROM "SURVEY_OBSERVATIONS";

-- Expected:
-- DPOBSERVATION: 1
-- OBSERVATION: 2762
-- ENCOUNTER_OBSERVATIONS: 0
-- OCCURRENCE_OBSERVATIONS: 1
-- SURVEY_OBSERVATIONS: 0


-- ============================================
-- STEP 1: BACKUP ALL TABLES
-- ============================================

-- Backup the tables we'll modify
CREATE TABLE "DPOBSERVATION_BACKUP" AS SELECT * FROM "DPOBSERVATION";
CREATE TABLE "OBSERVATION_OLD_BACKUP" AS SELECT * FROM "OBSERVATION";
CREATE TABLE "DATAPOINT_BACKUP" AS SELECT * FROM "DATAPOINT";

-- Backup join tables (for safety)
CREATE TABLE "ENCOUNTER_OBSERVATIONS_BACKUP" AS SELECT * FROM "ENCOUNTER_OBSERVATIONS";
CREATE TABLE "OCCURRENCE_OBSERVATIONS_BACKUP" AS SELECT * FROM "OCCURRENCE_OBSERVATIONS";
CREATE TABLE "SURVEY_OBSERVATIONS_BACKUP" AS SELECT * FROM "SURVEY_OBSERVATIONS";
CREATE TABLE "DATACOLLECTIONEVENT_OBSERVATIONS_BACKUP" AS SELECT * FROM "DATACOLLECTIONEVENT_OBSERVATIONS";
CREATE TABLE "ABSTRACTTAG_OBSERVATIONS_BACKUP" AS SELECT * FROM "ABSTRACTTAG_OBSERVATIONS";

-- Verify backups
SELECT 'DPOBSERVATION_BACKUP' as backup_table, COUNT(*) as records FROM "DPOBSERVATION_BACKUP"
UNION ALL
SELECT 'OBSERVATION_OLD_BACKUP', COUNT(*) FROM "OBSERVATION_OLD_BACKUP";


-- ============================================
-- STEP 2: DROP OLD OBSERVATION TABLE
-- ============================================
-- The old OBSERVATION table contains DataSheet form field templates
-- (2,762 records, only 4 have values, none linked to entities)

-- First, drop the FK constraint from OBSERVATION to DATAPOINT
ALTER TABLE "OBSERVATION" DROP CONSTRAINT "OBSERVATION_FK1";

-- Drop the old OBSERVATION table
DROP TABLE "OBSERVATION";

-- Note: DATAPOINT table is kept for now as other tables may reference it
-- (AMOUNT, CHECK, COUNT, INSTANT, LONGCOUNT, DATASHEET_DATA)


-- ============================================
-- STEP 3: RENAME DPOBSERVATION TO OBSERVATION
-- ============================================

-- Rename the table
ALTER TABLE "DPOBSERVATION" RENAME TO "OBSERVATION";

-- Rename the primary key index
ALTER INDEX "DPOBSERVATION_pkey" RENAME TO "OBSERVATION_pkey";

-- Rename the FK constraint (DPOBSERVATION_FK1 → OBSERVATION_FK1)
-- This FK links to DATACOLLECTIONEVENT
ALTER TABLE "OBSERVATION"
RENAME CONSTRAINT "DPOBSERVATION_FK1" TO "OBSERVATION_FK1";


-- ============================================
-- STEP 4: UPDATE FOREIGN KEY REFERENCES
-- ============================================
-- The join tables have FKs that referenced DPOBSERVATION
-- PostgreSQL should handle the rename automatically, but let's verify
-- and recreate if needed for clarity

-- Check current FK constraints on join tables
SELECT
    tc.table_name,
    tc.constraint_name,
    ccu.table_name AS foreign_table_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND tc.table_name IN (
        'ENCOUNTER_OBSERVATIONS',
        'OCCURRENCE_OBSERVATIONS',
        'SURVEY_OBSERVATIONS',
        'DATACOLLECTIONEVENT_OBSERVATIONS',
        'ABSTRACTTAG_OBSERVATIONS'
    )
ORDER BY tc.table_name;

-- If any FK still shows DPOBSERVATION (shouldn't happen), recreate them:
-- (Uncomment and run only if needed)

/*
-- ENCOUNTER_OBSERVATIONS
ALTER TABLE "ENCOUNTER_OBSERVATIONS" DROP CONSTRAINT "ENCOUNTER_OBSERVATIONS_FK2";
ALTER TABLE "ENCOUNTER_OBSERVATIONS"
ADD CONSTRAINT "ENCOUNTER_OBSERVATIONS_FK2"
FOREIGN KEY ("OBSERVATIONID_EID")
REFERENCES "OBSERVATION"("OBSERVATIONID")
DEFERRABLE INITIALLY DEFERRED;

-- OCCURRENCE_OBSERVATIONS
ALTER TABLE "OCCURRENCE_OBSERVATIONS" DROP CONSTRAINT "OCCURRENCE_OBSERVATIONS_FK2";
ALTER TABLE "OCCURRENCE_OBSERVATIONS"
ADD CONSTRAINT "OCCURRENCE_OBSERVATIONS_FK2"
FOREIGN KEY ("OBSERVATIONID_EID")
REFERENCES "OBSERVATION"("OBSERVATIONID")
DEFERRABLE INITIALLY DEFERRED;

-- SURVEY_OBSERVATIONS
ALTER TABLE "SURVEY_OBSERVATIONS" DROP CONSTRAINT "SURVEY_OBSERVATIONS_FK2";
ALTER TABLE "SURVEY_OBSERVATIONS"
ADD CONSTRAINT "SURVEY_OBSERVATIONS_FK2"
FOREIGN KEY ("OBSERVATIONID_EID")
REFERENCES "OBSERVATION"("OBSERVATIONID")
DEFERRABLE INITIALLY DEFERRED;

-- DATACOLLECTIONEVENT_OBSERVATIONS
ALTER TABLE "DATACOLLECTIONEVENT_OBSERVATIONS" DROP CONSTRAINT "DATACOLLECTIONEVENT_OBSERVATIONS_FK2";
ALTER TABLE "DATACOLLECTIONEVENT_OBSERVATIONS"
ADD CONSTRAINT "DATACOLLECTIONEVENT_OBSERVATIONS_FK2"
FOREIGN KEY ("OBSERVATIONID_EID")
REFERENCES "OBSERVATION"("OBSERVATIONID")
DEFERRABLE INITIALLY DEFERRED;

-- ABSTRACTTAG_OBSERVATIONS
ALTER TABLE "ABSTRACTTAG_OBSERVATIONS" DROP CONSTRAINT "ABSTRACTTAG_OBSERVATIONS_FK1";
ALTER TABLE "ABSTRACTTAG_OBSERVATIONS"
ADD CONSTRAINT "ABSTRACTTAG_OBSERVATIONS_FK1"
FOREIGN KEY ("OBSERVATIONID_EID")
REFERENCES "OBSERVATION"("OBSERVATIONID")
DEFERRABLE INITIALLY DEFERRED;
*/


-- ============================================
-- STEP 5: VERIFY MIGRATION
-- ============================================

-- Check new OBSERVATION table structure
-- Should have: OBSERVATIONID, NAME, VALUE, PARENTOBJECTCLASS,
--              PARENTOBJECTID, DATEADDEDMILLI, DATEMODIFIEDMILLI
SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_name = 'OBSERVATION'
ORDER BY ordinal_position;

-- Check record count (should be 1)
SELECT COUNT(*) as observation_count FROM "OBSERVATION";

-- Verify the one observation record
SELECT * FROM "OBSERVATION";

-- Verify join table still works
SELECT
    o."OBSERVATIONID",
    o."NAME",
    o."VALUE",
    o."PARENTOBJECTCLASS",
    o."PARENTOBJECTID"
FROM "OCCURRENCE_OBSERVATIONS" oo
JOIN "OBSERVATION" o ON oo."OBSERVATIONID_EID" = o."OBSERVATIONID";

-- Check FK constraints are properly set
SELECT
    tc.table_name,
    tc.constraint_name,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND ccu.table_name = 'OBSERVATION';


-- ============================================
-- STEP 6: CLEANUP (After verification - wait 1 week)
-- ============================================
-- Only run after confirming application works correctly

-- Drop backup tables
-- DROP TABLE "DPOBSERVATION_BACKUP";
-- DROP TABLE "OBSERVATION_OLD_BACKUP";
-- DROP TABLE "DATAPOINT_BACKUP";
-- DROP TABLE "ENCOUNTER_OBSERVATIONS_BACKUP";
-- DROP TABLE "OCCURRENCE_OBSERVATIONS_BACKUP";
-- DROP TABLE "SURVEY_OBSERVATIONS_BACKUP";
-- DROP TABLE "DATACOLLECTIONEVENT_OBSERVATIONS_BACKUP";
-- DROP TABLE "ABSTRACTTAG_OBSERVATIONS_BACKUP";

-- Optional: Clean up DATAPOINT if no longer needed
-- Check what still references DATAPOINT first:
-- SELECT
--     tc.table_name,
--     tc.constraint_name
-- FROM information_schema.table_constraints AS tc
-- JOIN information_schema.constraint_column_usage AS ccu
--     ON ccu.constraint_name = tc.constraint_name
-- WHERE tc.constraint_type = 'FOREIGN KEY'
--     AND ccu.table_name = 'DATAPOINT';

-- If nothing references it (after dropping OBSERVATION FK):
-- DROP TABLE "DATAPOINT";


-- ============================================
-- ROLLBACK PROCEDURE (If something goes wrong)
-- ============================================

/*
-- ROLLBACK: Restore original state

-- Step R1: Rename OBSERVATION back to DPOBSERVATION
ALTER TABLE "OBSERVATION" RENAME TO "DPOBSERVATION";
ALTER INDEX "OBSERVATION_pkey" RENAME TO "DPOBSERVATION_pkey";
ALTER TABLE "DPOBSERVATION"
RENAME CONSTRAINT "OBSERVATION_FK1" TO "DPOBSERVATION_FK1";

-- Step R2: Recreate old OBSERVATION table from backup
CREATE TABLE "OBSERVATION" AS SELECT * FROM "OBSERVATION_OLD_BACKUP";
ALTER TABLE "OBSERVATION" ADD PRIMARY KEY ("ID");

-- Step R3: Recreate FK to DATAPOINT
ALTER TABLE "OBSERVATION"
ADD CONSTRAINT "OBSERVATION_FK1"
FOREIGN KEY ("ID")
REFERENCES "DATAPOINT"("ID");

-- Step R4: Drop backups after successful rollback
-- DROP TABLE "DPOBSERVATION_BACKUP";
-- DROP TABLE "OBSERVATION_OLD_BACKUP";
-- etc.
*/


-- ============================================
-- NOTES
-- ============================================
/*
1. The old OBSERVATION table (2,762 records) contained DataSheet form field
   templates, not actual observation data. Only 4 had values, none were
   linked to Encounters/Occurrences/Surveys.

2. DPOBSERVATION already had the correct main branch schema structure.
   This migration simply renames it to OBSERVATION.

3. The DATAPOINT table is retained because other DataSheet-related tables
   still reference it (AMOUNT, CHECK, COUNT, INSTANT, LONGCOUNT, DATASHEET_DATA).

4. After code merge from main branch, the JDO will expect:
   - Table: OBSERVATION
   - Columns: observationID, name, value, parentObjectClass,
              parentObjectID, dateAddedMilli, dateModifiedMilli

   The renamed table provides all these columns.

5. Extra columns in the renamed table (OBSERVATIONS_INTEGER_IDX,
   OBSERVATIONS_DATACOLLECTIONEVENTID_OWN) will be ignored by JDO
   if not mapped, or can be dropped if desired.
*/
