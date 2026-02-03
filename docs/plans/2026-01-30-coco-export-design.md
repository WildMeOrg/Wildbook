# COCO Export Feature Design

**Date:** 2026-01-30
**Status:** Approved
**Purpose:** Export Wildbook encounter search results to COCO format for MIEW-ID training

## Overview

Create a COCO exporter that outputs annotations, images, and individual animal IDs directly from Wildbook, eliminating the need to synchronize with WBIA before exporting.

## Output Structure

```
wildbook-coco-export.tar.gz
├── annotations.json
└── images/
    ├── {mediaAsset-uuid-1}.jpg
    ├── {mediaAsset-uuid-2}.jpg
    └── ...
```

## COCO JSON Schema

### Top-level Structure

```json
{
  "info": {
    "description": "Wildbook COCO Export",
    "version": "1.0",
    "date_created": "2026-01-30T12:00:00Z",
    "contributor": "Wildbook",
    "individual_id_mapping": {
      "uuid-1": 0,
      "uuid-2": 1
    }
  },
  "licenses": [{ "id": 1, "name": "See Wildbook Terms", "url": "" }],
  "categories": [...],
  "images": [...],
  "annotations": [...]
}
```

### Category Object

Derived from distinct `Annotation.getIAClass()` values.

```json
{
  "id": 1,
  "name": "whale_shark",
  "supercategory": "animal"
}
```

### Image Object

One per unique MediaAsset.

```json
{
  "id": 12345,
  "file_name": "a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg",
  "width": 1920,
  "height": 1080,
  "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "date_captured": "2024-06-15T14:30:00Z",
  "gps_lat_captured": -27.4698,
  "gps_lon_captured": 153.0251
}
```

### Annotation Object

One per Annotation.

```json
{
  "id": 1,
  "image_id": 12345,
  "category_id": 1,
  "bbox": [100, 200, 300, 400],
  "area": 120000,
  "segmentation": [[100, 200, 400, 200, 400, 600, 100, 600]],
  "iscrowd": 0,
  "uuid": "annotation-uuid",
  "viewpoint": "left",
  "theta": 0.0,
  "individual_ids": [42],
  "individual_uuid": "b7e4c2a1-...-9f3d",
  "name": "Stumpy"
}
```

**Field notes:**
- `bbox`: [x, y, width, height] format
- `area`: width × height
- `segmentation`: Rectangle polygon derived from bbox
- `theta`: Rotation from transformMatrix or Feature parameters (0.0 if none)
- `individual_ids`: Single-element array with mapped integer, or empty array if unidentified
- `individual_uuid`: MarkedIndividual UUID for traceability, null if unidentified
- `name`: Display name for human reference, null if unidentified

## Individual ID Mapping

MarkedIndividual UUIDs are mapped to sequential integers for MIEW-ID compatibility:

1. Collect all unique `MarkedIndividual.getId()` UUIDs from export
2. Sort alphabetically for deterministic ordering
3. Assign sequential integers starting from 0
4. Store mapping in `info.individual_id_mapping`

## Filtering Rules

**Include:**
- All annotations from matching encounters with valid bbox and iaClass

**Skip (with warning log):**
- Annotations where `getBbox()` returns null or invalid
- Annotations where `getIAClass()` returns null or empty
- Annotations where `getMediaAsset()` returns null
- MediaAssets where dimensions cannot be determined

**Security:**
- Apply `HiddenEncReporter` filtering
- Respect user permissions on encounters

## Deduplication

- Multiple annotations can reference the same MediaAsset
- Export each unique MediaAsset image only once
- Track by MediaAsset UUID

## Code Architecture

### Files to Create

1. **`org.ecocean.servlet.export.EncounterSearchExportCOCO`**
   - HttpServlet handling POST requests
   - Receives search query from encounter search results
   - Uses `EncounterQueryProcessor.processQuery()`
   - Delegates to export file class
   - Streams tar.gz response

2. **`org.ecocean.export.EncounterCOCOExportFile`**
   - Core export logic (reusable)
   - Takes List<Encounter> and Shepherd
   - Builds COCO data structures
   - Writes tar.gz with images + JSON

### Data Flow

```
Search Results Page → EncounterSearchExportCOCO servlet
                              ↓
                      EncounterQueryProcessor.processQuery()
                              ↓
                      List<Encounter>
                              ↓
                      EncounterCOCOExportFile
                              ↓
                      (iterate encounters → annotations → MediaAssets)
                              ↓
                      tar.gz stream to client
```

## Integration

### Servlet Mapping (web.xml)

```xml
<servlet>
  <servlet-name>EncounterSearchExportCOCO</servlet-name>
  <servlet-class>org.ecocean.servlet.export.EncounterSearchExportCOCO</servlet-class>
</servlet>
<servlet-mapping>
  <servlet-name>EncounterSearchExportCOCO</servlet-name>
  <url-pattern>/encounters/exportCOCO</url-pattern>
</servlet-mapping>
```

### Response Headers

```
Content-Type: application/gzip
Content-Disposition: attachment; filename="wildbook-coco-export.tar.gz"
```

### UI

Add "Export COCO" option to encounter search results page alongside existing export options (Excel, KML, Shapefile).

## Data Sources

| COCO Field | Wildbook Source |
|------------|-----------------|
| bbox | `Annotation.getBbox()` |
| viewpoint | `Annotation.getViewpoint()` |
| category | `Annotation.getIAClass()` |
| theta | `Annotation.transformMatrix` / Feature parameters |
| individual_ids | Mapped from `MarkedIndividual.getId()` |
| individual_uuid | `MarkedIndividual.getId()` |
| name | `MarkedIndividual.getDisplayName()` |
| image width/height | `MediaAsset.getWidth()`, `getHeight()` |
| gps_lat/lon | `MediaAsset.getLatitude()`, `getLongitude()` |
| date_captured | `MediaAsset.getDateTime()` |
| image file | `MediaAsset.localPath()` or store access |
