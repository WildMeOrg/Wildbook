# Stage 3: private uploads and strict validation

Historical increment notes. For the complete implementation and current private
configuration/storage requirements, use [pilot-runbook.md](pilot-runbook.md).

This increment adds files GET/POST and validate POST. Commit remains disabled.
Configure `submissions.stagingDirectory` to an existing private absolute directory,
owned by the service account, outside the legacy upload tree and web document root.
The service rejects overlap with legacy uploads. All application instances must see
the same storage. Restrict directory access to the service account.

One multipart `file` per request, JPEG/PNG only, up to configured media bytes
(capped at 200 MiB), 24 million decoded pixels, and 16,000 pixels per dimension.
Multipart overhead is limited to 64 KiB. Draft limits: 200 files, 200 MiB completed
bytes. A per-draft PostgreSQL transaction lock reserves the write slot before
streaming. One bounded temporary file can exist in addition to completed capacity
while retry content is compared; failures before commit remove it. A failed commit
acknowledgment retains the file because the transaction may actually have committed.
Crash orphans await the conservative maintenance workflow in the worker stage.

Filenames must be unchanged by Wildbook's filename cleaner, at most 128 characters,
ASCII letters/digits/dots/underscores/hyphens and start with a letter or digit.
Case-only conflicts are rejected on every filesystem. Manifest responses never
expose private blob paths. Lost-response retries use GET files and the current ETag.

Validation copies the payload and reuses BulkImportUtil/BulkValidator. Configured
location membership is an additional boundary. Actual staged image bytes and
hashes are checked without creating media, encounters or IA jobs. Each accepted
row creates a separate new encounter; this pilot does not accept explicit encounter,
individual, occurrence, project or owner IDs. Per-row media counts therefore match
the importer's grouping semantics for this supported subset. Partial dates retain
their precision. Legacy required fields and defaults are unchanged.

Validation saves a report against the same revision, configuration digest and
manifest digest. Edits clear the report. The draft owner is the effective owner;
client fields cannot override it. Execution must revalidate before creating records.
