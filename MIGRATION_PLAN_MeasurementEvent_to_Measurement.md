# Migration Plan: MeasurementEvent → Measurement

## Overview

The `iot` branch uses `MeasurementEvent` while the `main` branch uses `Measurement`. Both classes are nearly identical - they extend `DataCollectionEvent` and have the same fields (`value`, `units`). This migration will convert all `MeasurementEvent` data to `Measurement` objects.

## Current State Analysis

### Class Comparison

| Aspect | MeasurementEvent (iot) | Measurement (main) |
|--------|------------------------|-------------------|
| Package | `org.ecocean.datacollection` | `org.ecocean` |
| Parent | `DataCollectionEvent` | `DataCollectionEvent` |
| Fields | `value` (Double), `units` (String) | `value` (Double), `units` (String) |
| DB Table | `MEASUREMENTEVENT` | `MEASUREMENT` |

### Inherited Fields (from DataCollectionEvent)
- `DataCollectionEventID` (primary key)
- `correspondingEncounterNumber`
- `type`
- `datetime`
- `samplingProtocol`
- `samplingEffort`
- `eventStartDate`, `eventEndDate`
- `fieldNumber`, `fieldNotes`, `eventRemarks`
- `institutionID`, `collectionID`, `datasetID`
- `institutionCode`, `collectionCode`, `datasetName`

### Current JDO Mapping (iot branch)
```xml
<!-- Encounter.measurements currently maps to: -->
<field name="measurements" persistence-modifier="persistent" default-fetch-group="true">
  <collection element-type="org.ecocean.datacollection.MeasurementEvent" dependent-element="true"/>
  <join/>
</field>
```

## Migration Strategy

### Phase 1: Pre-Migration (Analysis & Backup)

1. **Backup the database** before any changes
2. **Run analysis script** to count MeasurementEvent records:
   ```sql
   SELECT COUNT(*) FROM MEASUREMENTEVENT;
   SELECT COUNT(*) FROM ENCOUNTER_MEASUREMENTS_MEASUREMENTEVENT; -- join table
   ```
3. **Document current data** for verification after migration

### Phase 2: Database Migration Script

Create a JSP migration script (`/appadmin/migrateMeasurementEventToMeasurement.jsp`):

```java
<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.datacollection.MeasurementEvent" %>
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>
<%@ page import="javax.jdo.Query" %>
<%@ page import="java.util.*" %>

<%
String context = "context0";
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("MeasurementEvent_to_Measurement_Migration");
myShepherd.beginDBTransaction();

int migrated = 0;
int errors = 0;
List<String> errorMessages = new ArrayList<>();

try {
    // Get all Encounters with MeasurementEvents
    String filter = "SELECT FROM org.ecocean.Encounter WHERE measurements != null && measurements.size() > 0";
    Query query = myShepherd.getPM().newQuery(filter);

    try {
        Collection results = (Collection) query.execute();
        List<Encounter> encounters = new ArrayList<>(results);

        out.println("<h2>Migration Progress</h2>");
        out.println("<p>Found " + encounters.size() + " encounters with measurements</p>");
        out.flush();

        for (Encounter enc : encounters) {
            try {
                List<MeasurementEvent> oldMeasurements = enc.getMeasurements();
                if (oldMeasurements == null || oldMeasurements.isEmpty()) continue;

                List<Measurement> newMeasurements = new ArrayList<>();

                for (MeasurementEvent me : oldMeasurements) {
                    // Create new Measurement with same data
                    Measurement m = new Measurement(
                        me.getCorrespondingEncounterNumber(),
                        me.getType(),
                        me.getValue(),
                        me.getUnits(),
                        me.getSamplingProtocol()
                    );

                    // Copy all inherited fields
                    m.setDateTime(me.getDateTime());
                    m.setSamplingEffort(me.getSamplingEffort());
                    m.setEventStartDate(me.getEventStartDate());
                    m.setEventEndDate(me.getEventEndDate());
                    m.setFieldNumber(me.getFieldNumber());
                    m.setFieldNotes(me.getFieldNotes());
                    m.setEventRemarks(me.getEventRemarks());
                    m.setInstitutionID(me.getInstitutionID());
                    m.setCollectionID(me.getCollectionID());
                    m.setDatasetID(me.getDatasetID());
                    m.setInstitutionCode(me.getInstitutionCode());
                    m.setCollectionCode(me.getCollectionCode());
                    m.setDatasetName(me.getDatasetName());

                    myShepherd.getPM().makePersistent(m);
                    newMeasurements.add(m);
                }

                // NOTE: After code migration, update encounter to use new measurements
                // enc.setMeasurementsNew(newMeasurements);

                migrated++;

                if (migrated % 100 == 0) {
                    out.println("<p>Migrated " + migrated + " encounters...</p>");
                    out.flush();
                    myShepherd.commitDBTransaction();
                    myShepherd.beginDBTransaction();
                }

            } catch (Exception e) {
                errors++;
                errorMessages.add("Encounter " + enc.getCatalogNumber() + ": " + e.getMessage());
            }
        }

        myShepherd.commitDBTransaction();

    } finally {
        query.closeAll();
    }

    out.println("<h2>Migration Complete</h2>");
    out.println("<p>Successfully migrated: " + migrated + " encounters</p>");
    out.println("<p>Errors: " + errors + "</p>");

    if (!errorMessages.isEmpty()) {
        out.println("<h3>Error Details:</h3><ul>");
        for (String err : errorMessages) {
            out.println("<li>" + err + "</li>");
        }
        out.println("</ul>");
    }

} catch (Exception e) {
    myShepherd.rollbackDBTransaction();
    out.println("<p style='color:red'>Migration failed: " + e.getMessage() + "</p>");
    e.printStackTrace(new java.io.PrintWriter(out));
} finally {
    myShepherd.closeDBTransaction();
}
%>
```

### Phase 3: Code Migration

After data migration, update the codebase:

1. **Encounter.java** - Change field type:
   ```java
   // FROM:
   private List<MeasurementEvent> measurements;

   // TO:
   private List<Measurement> measurements;
   ```

2. **Update all method signatures** in Encounter.java:
   - `getMeasurements()` → returns `List<Measurement>`
   - `setMeasurements(List<Measurement>)`
   - `setMeasurement(Measurement, Shepherd)`
   - `addMeasurement(Measurement)`
   - etc.

3. **Update JDO mapping** (`package.jdo`):
   ```xml
   <field name="measurements" persistence-modifier="persistent" default-fetch-group="true">
     <collection element-type="org.ecocean.Measurement" dependent-element="true"/>
     <join/>
   </field>
   ```

4. **Update all files referencing MeasurementEvent**:
   - Search for `import org.ecocean.datacollection.MeasurementEvent`
   - Replace with `import org.ecocean.Measurement`

### Phase 4: Post-Migration Verification

1. **Verify data counts match**:
   ```sql
   SELECT COUNT(*) FROM MEASUREMENT;  -- Should match old MEASUREMENTEVENT count
   ```

2. **Spot-check random encounters** to verify measurements migrated correctly

3. **Run application tests** to ensure functionality works

### Phase 5: Cleanup (After Verification)

1. **Remove MeasurementEvent class** (`src/main/java/org/ecocean/datacollection/MeasurementEvent.java`)
2. **Remove MeasurementEvent JDO mapping** from `datacollection/package.jdo`
3. **Drop old tables** (after full verification):
   ```sql
   DROP TABLE MEASUREMENTEVENT;
   DROP TABLE ENCOUNTER_MEASUREMENTS_MEASUREMENTEVENT;
   ```

## Execution Order

```
1. BACKUP DATABASE
2. Run Phase 1 analysis
3. Run Phase 2 migration script (creates new Measurement objects)
4. Merge main branch code (replaces MeasurementEvent with Measurement in code)
5. Update JDO mapping to point to new Measurement objects
6. Deploy and test
7. Run Phase 4 verification
8. Run Phase 5 cleanup (only after full verification)
```

## Rollback Plan

If migration fails:
1. Restore database from backup
2. Revert code changes
3. Investigate and fix issues before re-attempting

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Data loss | Low | High | Full backup before migration |
| Missed field mappings | Low | Medium | Both classes have identical fields |
| Foreign key issues | Medium | Medium | Careful handling of join tables |
| Application downtime | Medium | Medium | Run during low-traffic period |

## Alternative: SQL-Based Migration (Recommended)

This is simpler and faster than the JSP approach for 40,871 records.

### Step 1: Backup
```sql
-- Create backup tables
CREATE TABLE MEASUREMENTEVENT_BACKUP AS SELECT * FROM MEASUREMENTEVENT;
CREATE TABLE ENCOUNTER_MEASUREMENTS_MEASUREMENTEVENT_BACKUP AS
  SELECT * FROM ENCOUNTER_MEASUREMENTS_MEASUREMENTEVENT;
```

### Step 2: Copy Data to Measurement Table
```sql
-- Insert MeasurementEvent data into MEASUREMENT table
-- Both tables have identical structure (inherit from DATACOLLECTIONEVENT)
INSERT INTO MEASUREMENT (DATACOLLECTIONEVENTID_OID, VALUE, UNITS)
SELECT DATACOLLECTIONEVENTID_OID, VALUE, UNITS
FROM MEASUREMENTEVENT;
```

### Step 3: Update Join Table (After Code Merge)
```sql
-- After merging main branch and updating JDO mapping,
-- copy the join table relationships
INSERT INTO ENCOUNTER_MEASUREMENTS_MEASUREMENT (CATALOGNUMBER_OID, DATACOLLECTIONEVENTID_EID)
SELECT CATALOGNUMBER_OID, DATACOLLECTIONEVENTID_EID
FROM ENCOUNTER_MEASUREMENTS_MEASUREMENTEVENT;
```

### Step 4: Verify
```sql
-- Counts should match
SELECT COUNT(*) AS old_count FROM MEASUREMENTEVENT;
SELECT COUNT(*) AS new_count FROM MEASUREMENT;
SELECT COUNT(*) AS old_join FROM ENCOUNTER_MEASUREMENTS_MEASUREMENTEVENT;
SELECT COUNT(*) AS new_join FROM ENCOUNTER_MEASUREMENTS_MEASUREMENT;
```

### Step 5: Cleanup (After Verification)
```sql
-- Only run after full verification!
DROP TABLE ENCOUNTER_MEASUREMENTS_MEASUREMENTEVENT;
DROP TABLE MEASUREMENTEVENT;
DROP TABLE MEASUREMENTEVENT_BACKUP;
DROP TABLE ENCOUNTER_MEASUREMENTS_MEASUREMENTEVENT_BACKUP;
```

## Migration Scripts Created

Two JSP scripts have been created in `/appadmin/`:

1. **migrateMeasurementEventToMeasurement.jsp** - Phase 1: Creates Measurement objects
   - Dry run mode (preview)
   - Execute mode (creates objects)
   - Verify mode (check counts)

2. **migrateMeasurements_Phase2_LinkToEncounters.jsp** - Phase 2: Links to Encounters
   - Run AFTER code merge from main branch
   - Updates Encounter.measurements lists

## Recommended Execution Order

### Option A: SQL Migration (Faster, Recommended)
```
1. BACKUP DATABASE
2. Merge main branch code
3. Update JDO mapping (Encounter.measurements → org.ecocean.Measurement)
4. Run SQL Step 2 (copy MEASUREMENTEVENT → MEASUREMENT)
5. Run SQL Step 3 (copy join table)
6. Deploy application
7. Verify
8. Cleanup old tables
```

### Option B: JSP Migration (More Controlled)
```
1. BACKUP DATABASE
2. Run Phase 1 JSP (dry run first, then execute)
3. Verify counts
4. Merge main branch code
5. Update JDO mapping
6. Run Phase 2 JSP (link Measurements to Encounters)
7. Deploy and test
8. Cleanup
```

## Estimated Effort

- Phase 1: 30 minutes
- Phase 2: 1-2 hours (depending on data volume)
- Phase 3: Already done by merging main branch
- Phase 4: 1 hour
- Phase 5: 30 minutes

**Total: ~4 hours** (excluding main branch merge)

## SQL Migration Time Estimate

For 40,871 records:
- SQL copy: ~1-2 minutes
- Join table copy: ~1 minute
- Total SQL execution: ~5 minutes

The SQL approach is significantly faster.
