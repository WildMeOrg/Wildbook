-- Organization membership: one row per (organization, user), with no position column.
--
-- ORGANIZATION_MEMBERS used to back two indexed lists (User.organizations and
-- Organization.members) sharing one IDX column. DataNucleus wrote IDX as the
-- organization's position in the *user's* list, but the table's primary key was
-- (ORGANIZATION_ID, IDX), so a user joining an organization collided with whichever
-- member already held that position. Adds failed whenever that position was taken,
-- which in a populated organization is nearly always: /UserCreate silently,
-- /OrganizationEdit and service-key creation with a 500.
--
-- User.organizations and Organization.members are now Sets, which DataNucleus keys on
-- (ORGANIZATION_ID, USER_ID) when it creates the table. It does not alter an
-- existing table, so run this once on every existing database:
--
--   1. stop Wildbook (the old build still writes IDX; the new one cannot insert
--      while IDX is NOT NULL)
--   2. psql -v ON_ERROR_STOP=1 -f archive/sql/organization_members_pk.sql
--   3. start the new build (it logs an ERROR at startup if this script was skipped)
--
-- Safe to re-run: on an already-migrated table it rebuilds the same key.
--
-- Rollback: the old build cannot write to the migrated table (no IDX column). To go back,
-- re-add IDX numbered per user (the old build writes it as the position in the user's list,
-- so key it on (USER_ID, IDX), which the old build can live with, rather than the old key):
--   ALTER TABLE "ORGANIZATION_MEMBERS" DROP CONSTRAINT "ORGANIZATION_MEMBERS_PK";
--   ALTER TABLE "ORGANIZATION_MEMBERS" ADD COLUMN "IDX" INTEGER;
--   UPDATE "ORGANIZATION_MEMBERS" m SET "IDX" = n.i - 1 FROM (SELECT ctid, row_number() OVER
--     (PARTITION BY "USER_ID" ORDER BY "ORGANIZATION_ID") AS i FROM "ORGANIZATION_MEMBERS") n
--     WHERE m.ctid = n.ctid;
--   ALTER TABLE "ORGANIZATION_MEMBERS" ALTER COLUMN "IDX" SET NOT NULL;
--   ALTER TABLE "ORGANIZATION_MEMBERS" ADD CONSTRAINT "ORGANIZATION_MEMBERS_PK" PRIMARY KEY ("USER_ID", "IDX");

BEGIN;

LOCK TABLE "ORGANIZATION_MEMBERS" IN ACCESS EXCLUSIVE MODE;

-- rows that cannot be a membership
DELETE FROM "ORGANIZATION_MEMBERS" WHERE "ORGANIZATION_ID" IS NULL OR "USER_ID" IS NULL;

-- the same user listed twice in one organization: keep one row
DELETE FROM "ORGANIZATION_MEMBERS" a
 USING "ORGANIZATION_MEMBERS" b
 WHERE a."ORGANIZATION_ID" = b."ORGANIZATION_ID"
   AND a."USER_ID" = b."USER_ID"
   AND a.ctid > b.ctid;

-- the old key is named ORGANIZATION_MEMBERS_PK or ORGANIZATION_MEMBERS_pkey depending on
-- how the table was created; drop whichever exists
DO $$
DECLARE pk text;
BEGIN
    SELECT conname INTO pk FROM pg_constraint
     WHERE conrelid = '"ORGANIZATION_MEMBERS"'::regclass AND contype = 'p';
    IF pk IS NOT NULL THEN
        EXECUTE format('ALTER TABLE "ORGANIZATION_MEMBERS" DROP CONSTRAINT %I', pk);
    END IF;
END $$;

ALTER TABLE "ORGANIZATION_MEMBERS" DROP COLUMN IF EXISTS "IDX";

ALTER TABLE "ORGANIZATION_MEMBERS"
    ADD CONSTRAINT "ORGANIZATION_MEMBERS_PK" PRIMARY KEY ("ORGANIZATION_ID", "USER_ID");

COMMIT;
