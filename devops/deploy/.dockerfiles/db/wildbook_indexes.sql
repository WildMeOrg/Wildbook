-- =============================================================================
-- PostgreSQL Index Optimization for Wildbook
-- Run this script AFTER initial data load for best performance
-- Optimized for Azure Standard D8s v5 (8 vCPUs, 32 GB RAM)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ENCOUNTER TABLE INDEXES
-- The most frequently queried table in Wildbook
-- -----------------------------------------------------------------------------

-- Primary lookup by catalogNumber (already primary key, but ensure it's indexed)
-- CREATE INDEX IF NOT EXISTS idx_encounter_catalognumber ON encounter (catalognumber);

-- Date-based queries (very common for search results)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_dateinmilliseconds
    ON encounter (dateinmilliseconds DESC NULLS LAST);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_year_month_day
    ON encounter (year DESC NULLS LAST, month, day);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_dwcdateaddedlong
    ON encounter (dwcdateaddedlong DESC NULLS LAST);

-- Location-based queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_locationid
    ON encounter (locationid);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_location_coords
    ON encounter (decimallatitude, decimallongitude)
    WHERE decimallatitude IS NOT NULL AND decimallongitude IS NOT NULL;

-- Species/taxonomy queries (very common filters)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_genus_species
    ON encounter (genus, specificepithet);

-- State/workflow queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_state
    ON encounter (state);

-- Individual assignment (critical for photo-ID workflows)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_individual
    ON encounter (individual_individualid);

-- Occurrence grouping
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_occurrenceid
    ON encounter (occurrenceid);

-- Submitter queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_submitterid
    ON encounter (submitterid);

-- Composite index for common search patterns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_search_composite
    ON encounter (state, locationid, dateinmilliseconds DESC NULLS LAST);

-- Full-text search on verbatimLocality (if frequently searched)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_verbatimlocality_gin
    ON encounter USING gin (to_tsvector('english', verbatimlocality))
    WHERE verbatimlocality IS NOT NULL;

-- -----------------------------------------------------------------------------
-- MARKEDINDIVIDUAL TABLE INDEXES
-- Second most important table for photo-ID
-- -----------------------------------------------------------------------------

-- Primary lookup (already primary key)
-- CREATE INDEX IF NOT EXISTS idx_markedindividual_individualid ON markedindividual (individualid);

-- Version-based sync (already defined in JDO but verify)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_markedindividual_version
    ON markedindividual (version);

-- Sex filter (common in searches)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_markedindividual_sex
    ON markedindividual (sex);

-- Time of birth/death for demographic queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_markedindividual_timeofbirth
    ON markedindividual (timeofbirth) WHERE timeofbirth IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_markedindividual_timeofdeath
    ON markedindividual (timeofdeath) WHERE timeofdeath IS NOT NULL;

-- Encounter count for filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_markedindividual_numberencounters
    ON markedindividual (numberencounters DESC NULLS LAST);

-- -----------------------------------------------------------------------------
-- ANNOTATION TABLE INDEXES
-- Critical for machine learning and image matching
-- -----------------------------------------------------------------------------

-- Primary lookup (already primary key)
-- CREATE INDEX IF NOT EXISTS idx_annotation_id ON annotation (id);

-- Version-based sync (already defined in JDO)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_annotation_version
    ON annotation (version);

-- Match-against flag (critical for ID matching queries)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_annotation_matchagainst
    ON annotation (matchagainst) WHERE matchagainst = true;

-- Viewpoint (common filter in matching)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_annotation_viewpoint
    ON annotation (viewpoint);

-- Species classification
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_annotation_iaclass
    ON annotation (iaclass);

-- Quality and distinctiveness (for matching priority)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_annotation_quality
    ON annotation (quality DESC NULLS LAST) WHERE quality IS NOT NULL;

-- MediaAsset foreign key
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_annotation_mediaasset
    ON annotation (mediaasset_id);

-- Composite for common matching queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_annotation_match_composite
    ON annotation (matchagainst, viewpoint, iaclass)
    WHERE matchagainst = true;

-- ACM ID lookup (for WBIA/Wildbook Image Analysis)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_annotation_acmid
    ON annotation (acmid) WHERE acmid IS NOT NULL;

-- -----------------------------------------------------------------------------
-- MEDIAASSET TABLE INDEXES
-- Large table with binary/media references
-- -----------------------------------------------------------------------------

-- UUID lookup (unique, verify index exists)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mediaasset_uuid
    ON mediaasset (uuid);

-- Content hash for deduplication
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mediaasset_contenthash
    ON mediaasset (contenthash) WHERE contenthash IS NOT NULL;

-- ACM ID lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mediaasset_acmid
    ON mediaasset (acmid) WHERE acmid IS NOT NULL;

-- Occurrence relationship
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mediaasset_occurrence
    ON mediaasset (occurrence_occurrenceid);

-- Detection/identification status (for ML pipeline tracking)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mediaasset_detectionstatus
    ON mediaasset (detectionstatus);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mediaasset_identificationstatus
    ON mediaasset (identificationstatus);

-- Valid image flag (for IA processing)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mediaasset_validimageforia
    ON mediaasset (validimageforia) WHERE validimageforia = true;

-- Parent ID (for derived media assets)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mediaasset_parentid
    ON mediaasset (parentid) WHERE parentid IS NOT NULL;

-- -----------------------------------------------------------------------------
-- OCCURRENCE TABLE INDEXES
-- Groups encounters together
-- -----------------------------------------------------------------------------

-- Primary lookup (already primary key)
-- CREATE INDEX IF NOT EXISTS idx_occurrence_occurrenceid ON occurrence (occurrenceid);

-- Location-based queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_occurrence_location
    ON occurrence (decimallatitude, decimallongitude)
    WHERE decimallatitude IS NOT NULL;

-- Survey relationship
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_occurrence_survey
    ON occurrence (correspondingsurveyid) WHERE correspondingsurveyid IS NOT NULL;

-- Timestamp for ordering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_occurrence_millis
    ON occurrence (millis DESC NULLS LAST);

-- -----------------------------------------------------------------------------
-- USER TABLE INDEXES
-- Frequently joined for access control
-- -----------------------------------------------------------------------------

-- Username lookup (already unique but verify)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_username
    ON users (username);

-- Hashed email for privacy-preserving lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_hashedemailaddress
    ON users (hashedemailaddress) WHERE hashedemailaddress IS NOT NULL;

-- Last login for activity tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_lastlogin
    ON users (lastlogin DESC NULLS LAST);

-- -----------------------------------------------------------------------------
-- TAXONOMY TABLE INDEXES
-- Lookup table, heavily cached but indexed for initial loads
-- -----------------------------------------------------------------------------

-- Scientific name (already unique in JDO)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_taxonomy_scientificname
    ON taxonomy (scientificname);

-- ITIS TSN (already unique in JDO)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_taxonomy_itistsn
    ON taxonomy (itistsn) WHERE itistsn IS NOT NULL;

-- -----------------------------------------------------------------------------
-- FEATURE TABLE INDEXES
-- Links annotations to specific features
-- -----------------------------------------------------------------------------

-- Annotation relationship
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_feature_annotation
    ON feature (annotation_id);

-- Asset relationship
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_feature_asset
    ON feature (asset_id);

-- Feature type
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_feature_type
    ON feature (type_id);

-- -----------------------------------------------------------------------------
-- PROJECT TABLE INDEXES
-- Multi-project support
-- -----------------------------------------------------------------------------

-- Owner lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_project_ownerid
    ON project (ownerid);

-- Project ID prefix (for custom ID generation)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_project_idprefix
    ON project (projectidprefix) WHERE projectidprefix IS NOT NULL;

-- -----------------------------------------------------------------------------
-- SURVEY TABLE INDEXES
-- Field effort tracking
-- -----------------------------------------------------------------------------

-- Time-based queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_survey_starttime
    ON survey (starttime DESC NULLS LAST);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_survey_endtime
    ON survey (endtime DESC NULLS LAST);

-- Organization filter
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_survey_organization
    ON survey (organization);

-- -----------------------------------------------------------------------------
-- JOIN/ASSOCIATION TABLE INDEXES
-- These tables link entities and are frequently scanned
-- -----------------------------------------------------------------------------

-- Encounter-Annotation join table
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_annotations_enc
    ON encounter_annotations (encounter_catalognumber);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_encounter_annotations_ann
    ON encounter_annotations (annotations_id);

-- Occurrence-Encounter join table
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_occurrence_encounters_occ
    ON occurrence_encounters (occurrence_occurrenceid);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_occurrence_encounters_enc
    ON occurrence_encounters (encounters_catalognumber);

-- Project-Encounter join table
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_project_encounters_proj
    ON project_encounters (project_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_project_encounters_enc
    ON project_encounters (encounters_catalognumber);

-- User-Organization join table
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_organizations_user
    ON users_organization (user_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_organizations_org
    ON users_organization (organization_id);

-- -----------------------------------------------------------------------------
-- COLLABORATION TABLE INDEXES
-- Access control relationships
-- -----------------------------------------------------------------------------

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collaborations_username1
    ON collaborations (username1);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collaborations_username2
    ON collaborations (username2);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collaborations_state
    ON collaborations (state);

-- Composite for common lookup pattern
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collaborations_users_state
    ON collaborations (username1, username2, state);

-- -----------------------------------------------------------------------------
-- ROLE TABLE INDEXES
-- Authorization lookups
-- -----------------------------------------------------------------------------

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_roles_username
    ON user_roles (username);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_roles_context
    ON user_roles (context);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_roles_composite
    ON user_roles (username, context, role_name);

-- -----------------------------------------------------------------------------
-- IDENTITY/MATCHING TABLE INDEXES
-- Machine learning and matching state
-- -----------------------------------------------------------------------------

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ibeisia_state_annid1
    ON ibeisiaidentificationmatchingstate (annid1);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ibeisia_state_annid2
    ON ibeisiaidentificationmatchingstate (annid2);

-- -----------------------------------------------------------------------------
-- STATISTICS REFRESH
-- Update statistics after creating indexes for optimal query planning
-- -----------------------------------------------------------------------------

-- Run ANALYZE on all major tables
ANALYZE encounter;
ANALYZE markedindividual;
ANALYZE annotation;
ANALYZE mediaasset;
ANALYZE occurrence;
ANALYZE users;
ANALYZE taxonomy;
ANALYZE feature;
ANALYZE project;
ANALYZE survey;
ANALYZE collaborations;
ANALYZE user_roles;

-- -----------------------------------------------------------------------------
-- INDEX MAINTENANCE NOTES
-- -----------------------------------------------------------------------------
--
-- 1. Run this script during low-traffic periods (indexes are built CONCURRENTLY)
--
-- 2. Monitor index usage with:
--    SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
--    FROM pg_stat_user_indexes
--    ORDER BY idx_scan DESC;
--
-- 3. Find unused indexes:
--    SELECT schemaname, tablename, indexname, idx_scan
--    FROM pg_stat_user_indexes
--    WHERE idx_scan = 0 AND indexrelname NOT LIKE '%_pkey'
--    ORDER BY pg_relation_size(indexrelid) DESC;
--
-- 4. Check index bloat:
--    SELECT tablename, indexname,
--           pg_size_pretty(pg_relation_size(indexrelid)) as index_size
--    FROM pg_stat_user_indexes
--    ORDER BY pg_relation_size(indexrelid) DESC;
--
-- 5. Rebuild bloated indexes periodically:
--    REINDEX INDEX CONCURRENTLY index_name;
--
-- =============================================================================
