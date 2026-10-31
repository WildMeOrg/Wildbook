# Mobile embedding pack publishing

The Encounter Search export dialog offers mobile publication only to administrators when all publisher settings and a mobile IA profile are configured. Publication is public and requires explicit acknowledgement in the form. It is separate from ordinary downloadable exports.

## Configure Wildbook

In the instance's commonConfiguration.properties override:

```properties
mobilePackServiceUrl=https://packs.example.org
mobilePackInstanceUrl=https://wildbook.example.org
mobilePackWorkDir=/data/private/mobile-pack-jobs
```

The work directory MUST be outside all served web roots and restricted to the Tomcat account. It contains owner-scoped job state, temporary photos and ZIPs, and the verified artifact cache. Provide adequate disk capacity. Normal builds remove temporary assets; inspect/remove abandoned `build-*` directories after process crashes, while no export is running. Job JSON records remain for audit/status; apply an operational retention policy.

In the private data-directory `apiAccessKeys.properties` override:

```properties
mobilePackPublisherToken=<instance-specific-secret>
```

The Azure registry must register the corresponding token hash and exactly matching `mobilePackInstanceUrl`. No Azure SDK or storage account keys are required in Wildbook.

## Configure IA.json

Merge `_mobile_packs` into the existing species object; do not replace its detection/identification configuration. Each key is the exact saved Annotation IA class, including body region. The examples below use placeholder hashes and byte sizes and MUST be replaced with actual release metadata.

```json
{
  "Equus": {
    "caballus": {
      "_mobile_packs": {
        "horse+face": {
          "species": "horse",
          "featureClass": "horse+face",
          "mobileDetector": {
            "id": "horse-face-yolo11n",
            "version": "1.0.0",
            "downloadUrl": "https://assets.example.org/models/horse-face/1.0.0/detector.onnx",
            "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "sizeBytes": 100,
            "configUrl": "https://assets.example.org/models/horse-face/1.0.0/detector.json",
            "configSha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
          },
          "embeddingModel": {
            "name": "miewid",
            "version": "4.1.0",
            "method": "REPLACE_WITH_STORED_METHOD",
            "methodVersion": "REPLACE_WITH_STORED_METHOD_VERSION",
            "downloadUrl": "https://assets.example.org/models/miewid/4.1.0/model.onnx",
            "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "sizeBytes": 100,
            "format": "onnx",
            "embeddingDim": 2152,
            "inputSize": [
              440,
              440
            ],
            "normalize": {
              "mean": [
                0.485,
                0.456,
                0.406
              ],
              "std": [
                0.229,
                0.224,
                0.225
              ]
            }
          }
        }
      }
    }
  }
}
```

`method` and `methodVersion` select existing stored Embedding records. They are not inferred from the mobile semantic version. Set them to the actual stored values for the scientifically validated model/preprocessing used to produce the gallery embeddings.

The detector configuration URL must contain the mobile detector JSON contract with `modelFile: "detector.onnx"`, supported YOLO layout, preprocessing, class labels, and postprocessing. Files are retrieved over permanent HTTPS URLs without redirects or query tokens, verified by SHA-256, cached by content, and bundled as `models/detector.onnx` and `config/detector.json`. This first implementation uses URLs; a server-local detector-file source is not implemented. Host the separately downloaded embedding model at the pack service's immutable `models/` asset origin.

The model release owner must validate ONNX runtime compatibility, stretch-resize preprocessing, crop conventions, detector class mapping and real identification quality. Packs do not add support for arbitrary detector architectures or embedding dimensions. The first supported embedding contract is ONNX, 2152 dimensions, 440×440 input; GPU/TFLite registry variants are not yet included.

## Workflow

1. Search for one species and open Export.
2. Choose a configured species/body region and a display name. Leave pack ID blank for a new code; retain the returned ID when publishing updates to the same site.
3. Confirm public release and publish. The API queues a supervised background export and the dialog polls owner-only status.
4. The worker reloads the user and sanitized saved search, scans an isolated OpenSearch snapshot, reuses existing join-table-aware individual mapping, and selects cached embeddings by explicit method/version.
5. Unidentified records or annotations without compatible embeddings are skipped and counted. Other feature classes are intentionally excluded. No ML inference is triggered. Maximum scope: 1,000 encounters and 5,000 embedding vectors; narrow larger searches.
6. The ZIP contains raw float32 little-endian embeddings, a contiguous individual index, up to two resized reference JPEGs per individual, the detector, and configuration. Reference assets must be locally available images no larger than 40 megapixels. Public individual IDs are stable SHA-256-derived source-scoped identifiers. Private query/user details and EXIF are not included.
7. The exporter rechecks publication privileges, uploads directly with a scoped SAS, and finalizes the release. The dialog shows the code, URL and QR.

Search membership is captured during the background run, not when the button was clicked. Mixed species fail. Existing missing-embedding workflows can be used before exporting again. The skipped count combines missing known identities and missing compatible annotation embeddings; it is not a count of all excluded search hits.

The file queue is at-most-once. Startup republishes queued jobs and marks previously building/publishing jobs failed with a retry instruction. A new export gets a new immutable version; a lost completion acknowledgement may already have published the previous release, so inspect the code before retrying. Competing exports can return a conflict instead of overwriting a newer release.

## API

- `GET /api/v3/mobile-packs`: administrator capability and configured feature-class choices.
- `POST /api/v3/mobile-packs`: `{searchQueryId, featureClass, displayName, confirmPublic:true, packId?}`; requires session auth, administrator role, and `X-Requested-With: XMLHttpRequest`. Returns 202 with job and pack IDs.
- `GET /api/v3/mobile-packs?jobId=...`: job-owner-only status; no ZIP or filesystem paths are returned.

Reuses `QueueUtil`, `OpenSearch.DocValuesSnapshot`, `EncounterExport.buildEncounterIndividualMap`, and `Annotation.getEmbeddingByMethod`. ml-service is unchanged.

## Validation

Run targeted Maven tests for MobilePackArchiveTest, IAJsonPropertiesTest, OpenSearchDocValuesSnapshotTest and OpenSearchPaginationTest, plus the frontend MobilePackExport tests. Database, live storage, deployed permissions, and physical-device inference need integration acceptance before enabling publication in production.

The administrator’s confirmation authorizes public publication of the selected data, including records that were private or unapproved in Wildbook. Review the search before publishing. V1 caps each export at 1,000 encounters and 5,000 embeddings, and decodes reference images after closing the database transaction. Missing reference media fails the export rather than silently changing the selected gallery.
