----
----  this file contains some generally useful scripts for migration, but which are laregely optional or for debugging purposes
----



-- if encounter.submitterID only contains legit user.username values, this should work
-- if not, you can find the offenders with the next statemen
ALTER TABLE "ENCOUNTER" ADD CONSTRAINT "ENCOUNTER_SUBMITTERID_FK1" FOREIGN KEY ("SUBMITTERID") REFERENCES "USERS"("USERNAME") ON UPDATE CASCADE DEFERRABLE INITIALLY DEFERRED;

-- finds bunk encounter.submitterID values (and how many encounters they are on)
SELECT "SUBMITTERID" AS bad, count(*) FROM "ENCOUNTER" WHERE "SUBMITTERID" NOT IN (SELECT "USERNAME" FROM "USERS" WHERE "USERNAME" IS NOT NULL) GROUP BY bad;


-- tally locationID values and how often they are used (helpful to decide which are "important" e.g. for timeZone)
SELECT "LOCATIONID" AS loc, COUNT(*) AS ct FROM "ENCOUNTER" GROUP BY loc ORDER BY ct DESC;


-- look through taxonomies for near-duplicates, typos, and nonsense
SELECT CONCAT("GENUS", ' ', "SPECIFICEPITHET") AS tx, COUNT(*) AS ct FROM "ENCOUNTER" GROUP BY tx ORDER BY tx;


