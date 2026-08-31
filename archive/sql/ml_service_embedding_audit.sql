-- ml-service migration v2 (commit #4): pre-UNIQUE-promotion audit on EMBEDDING.
--
-- The non-unique composite index EMBEDDING_ANN_METHOD_VER_IDX on
-- (ANNOTATION_ID, METHOD, METHODVERSION) is created by JDO in
-- src/main/resources/org/ecocean/package.jdo. The v2 plan defers promoting
-- it to UNIQUE until a per-deployment audit confirms no existing duplicates.
--
-- Run this query before promoting. Must return zero rows. If it returns
-- rows, dedupe those first.

SELECT "ANNOTATION_ID", "METHOD", "METHODVERSION", COUNT(*) AS dup_count
FROM "EMBEDDING"
GROUP BY "ANNOTATION_ID", "METHOD", "METHODVERSION"
HAVING COUNT(*) > 1
ORDER BY dup_count DESC, "ANNOTATION_ID";
