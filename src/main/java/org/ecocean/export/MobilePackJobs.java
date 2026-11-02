package org.ecocean.export;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.commons.codec.digest.DigestUtils;
import org.ecocean.*;
import org.ecocean.media.MediaAsset;
import org.ecocean.api.EncounterExport;
import org.ecocean.queue.Queue;
import org.ecocean.queue.QueueMessageHandler;
import org.ecocean.queue.QueueUtil;
import org.ecocean.shepherd.core.Shepherd;
import org.json.*;

/** Serial background exports reuse Wildbook's supervised file queue. */
public final class MobilePackJobs {
    private static final Map<String, Queue> QUEUES = new HashMap<>();
    private static final int MAX_ENCOUNTERS = 1000;
    private static final int MAX_EMBEDDINGS = 5000;

    public static boolean enabled(String context) {
        try {
            MobilePackConfiguration.https(CommonConfiguration.getProperty("mobilePackInstanceUrl", context));
            new MobilePackPublisher(context);
            String path = CommonConfiguration.getProperty("mobilePackWorkDir", context);
            return path != null && Path.of(path).isAbsolute();
        } catch (Exception ex) { return false; }
    }

    private static final class PhotoTask {
        final Path source, output;
        final int[] box;
        final String rotationInfo;
        final double theta;
        PhotoTask(Annotation annotation, Path output) throws Exception {
            MediaAsset asset = annotation.getMediaAsset();
            if (asset != null && asset.localPath() == null) asset.cacheLocal(); // remote asset stores
            if (asset == null || asset.localPath() == null)
                throw new IllegalArgumentException("Reference photos must be available locally for export");
            this.source = asset.localPath();
            this.output = output;
            int[] bbox = annotation.getBbox();
            this.box = bbox == null ? null : bbox.clone();
            // Stored boxes are upright-frame pixels on EXIF-rotated assets; theta describes a rotated box.
            this.rotationInfo = annotation.getMediaAsset().getRotationInfo();
            this.theta = annotation.getTheta();
        }
    }

    public static Path root(String context) throws IOException {
        String configured = CommonConfiguration.getProperty("mobilePackWorkDir", context);
        if (configured == null || !Path.of(configured).isAbsolute())
            throw new IOException("Configure an absolute private mobilePackWorkDir outside the web root");
        Path root = Path.of(configured);
        Files.createDirectories(root);
        return root;
    }

    public static synchronized void start(String context) throws IOException {
        if (QUEUES.containsKey(context)) return;
        if (!enabled(context)) return;
        root(context);
        Queue queue = QueueUtil.getBest(context, "mobile-packs-" + context);
        if (queue == null) throw new IOException("Pack export queue unavailable");
        // FileQueue is at-most-once. Surface interrupted work instead of leaving a permanent spinner.
        try (java.util.stream.Stream<Path> jobs = Files.list(root(context))) {
            for (Path path : (Iterable<Path>)jobs.filter(file -> file.getFileName().toString().matches("[a-f0-9-]{36}\\.json"))::iterator) {
                try {
                    JSONObject pending = new JSONObject(Files.readString(path));
                    String id = pending.getString("jobId");
                    if ("queued".equals(pending.optString("status"))) {
                        queue.publish(id);
                    } else if (java.util.Arrays.asList("building", "publishing").contains(pending.optString("status"))) {
                        pending.put("status", "failed").put("message", "Server restarted during export; start a new export to retry");
                        save(context, id, pending);
                    }
                } catch (Exception ex) {
                    // One unreadable job file must not keep every other export from starting.
                    System.err.println("Mobile pack job file skipped during startup: " + path.getFileName() + " " + ex);
                }
            }
        }
        queue.consume(new QueueMessageHandler() {
            @Override public boolean handler(String id) throws IOException {
                run(context, id);
                return true;
            }
        });
        QUEUES.put(context, queue);
    }

    public static synchronized String submit(String context, JSONObject request) throws IOException {
        start(context);
        Queue queue = QUEUES.get(context);
        if (queue == null) throw new IOException("Mobile pack publishing is not configured");
        String id = UUID.randomUUID().toString();
        request.put("jobId", id).put("status", "queued").put("createdAt", java.time.Instant.now().toString());
        save(context, id, request);
        queue.publish(id);
        return id;
    }

    public static JSONObject read(String context, String id) throws IOException {
        if (!id.matches("[a-f0-9-]{36}")) throw new IOException("Invalid job identity");
        return new JSONObject(Files.readString(root(context).resolve(id + ".json")));
    }

    private static void save(String context, String id, JSONObject job) throws IOException {
        Path temporary = Files.createTempFile(root(context), "job-", ".tmp");
        try {
            Files.writeString(temporary, job.toString());
            Files.move(temporary, root(context).resolve(id + ".json"), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void run(String context, String id) throws IOException {
        JSONObject job = read(context, id);
        if (java.util.Arrays.asList("ready", "failed").contains(job.optString("status"))) return;
        Path work = null;
        try {
            work = Files.createTempDirectory(root(context), "build-");
            job.put("status", "building"); save(context, id, job);
            build(context, job, work);
            job.put("status", "ready");
        } catch (Exception ex) {
            // No artifact URLs, tokens, or sensitive search filters in public status/errors.
            job.put("status", "failed").put("message", ex instanceof IllegalArgumentException ?
                ex.getMessage() : "Pack export failed; check server configuration and retry the export");
            System.err.println("Mobile pack job " + id + " failed: " + ex);
        } finally {
            try {
                save(context, id, job);
            } finally {
                if (work != null) removeTree(work);
            }
        }
    }

    private static void removeTree(Path work) {
        try (java.util.stream.Stream<Path> files = Files.walk(work)) {
            for (Path file : (Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)
                Files.deleteIfExists(file);
        } catch (IOException ex) {
            System.err.println("Mobile pack build directory could not be removed: " + work + " " + ex);
        }
    }

    private static void build(String context, JSONObject job, Path work) throws Exception {
        Shepherd shepherd = new Shepherd(context);
        List<MobilePackArchive.Individual> individuals;
        MobilePackConfiguration profile;
        List<String> encounterIds = new ArrayList<>();
        List<PhotoTask> photos = new ArrayList<>();
        shepherd.beginDBTransaction();
        try {
            User owner = shepherd.getUser(job.getString("owner"));
            if (owner == null || !owner.isAdmin(shepherd)) throw new IllegalArgumentException("Publication requires an administrator");
            JSONObject stored = OpenSearch.queryLoad(job.getString("searchQueryId"), shepherd);
            if (stored == null || stored.optJSONObject("query") == null || !"encounter".equals(stored.optString("indexName")))
                throw new IllegalArgumentException("An Encounter Search is required");
            JSONObject sanitized = OpenSearch.querySanitize(OpenSearch.queryScrubStored(stored), owner, shepherd);
            List<Encounter> encounters = new ArrayList<>();
            int[] stale = {0};
            OpenSearch search = new OpenSearch();
            try (OpenSearch.DocValuesSnapshot snapshot = search.openDocValuesSnapshot(List.of("encounter"), "5m")) {
                snapshot.scanQuery("encounter", sanitized, Collections.emptyList(), 500, true, hits -> {
                    for (int n = 0; n < hits.length(); n++) {
                        JSONObject hit = hits.getJSONObject(n);
                        if (!Encounter.opensearchAccess(hit.getJSONObject("_source"), owner, shepherd)) continue;
                        Encounter encounter = shepherd.getEncounter(hit.getString("_id"));
                        // The index can lag deletions; a vanished record is counted, not fatal.
                        if (encounter == null) { stale[0]++; continue; }
                        if (!owner.isAdmin(shepherd) && !encounter.canUserAccess(owner, shepherd)) continue;
                        encounters.add(encounter); encounterIds.add(encounter.getCatalogNumber());
                        if (encounters.size() > MAX_ENCOUNTERS)
                            throw new IllegalArgumentException("Narrow the search to at most " + MAX_ENCOUNTERS + " encounters");
                    }
                });
            }
            if (encounters.isEmpty()) throw new IllegalArgumentException("No accessible encounters match this search");
            Encounter first = encounters.get(0);
            String taxonomy = first.getTaxonomyString();
            if (taxonomy == null || first.getTaxonomy(shepherd) == null)
                throw new IllegalArgumentException("All search records must have a configured species");
            for (Encounter encounter : encounters) {
                if (!taxonomy.equals(encounter.getTaxonomyString()))
                    throw new IllegalArgumentException("Choose a search containing one species");
            }
            // The dialog shows "taxonomy — feature class"; the worker must not silently use another species profile.
            String selected = job.optString("taxonomy", null);
            if (selected == null || !selected.equals(taxonomy))
                throw new IllegalArgumentException("The search species does not match the selected mobile pipeline");
            profile = IAJsonProperties.iaConfig().getMobilePackConfiguration(first.getTaxonomy(shepherd), job.getString("featureClass"));
            Map<String, MarkedIndividual> identities = EncounterExport.buildEncounterIndividualMap(shepherd, encounters);
            Map<String, MobilePackArchive.Individual> groups = new LinkedHashMap<>();
            Set<String> annotationIds = new HashSet<>();
            int skipped = 0;
            int vectors = 0;
            JSONObject model = profile.model();
            for (Encounter encounter : encounters) {
                MarkedIndividual identity = identities.get(encounter.getCatalogNumber());
                if (identity == null) { skipped++; continue; }
                for (Annotation annotation : encounter.getAnnotations()) {
                    if (!profile.featureClass().equals(annotation.getIAClass()) || !annotation.getMatchAgainst() ||
                        !annotationIds.add(annotation.getId())) continue;
                    Embedding embedding = annotation.getEmbeddingByMethod(model.getString("method"), model.getString("methodVersion"));
                    if (embedding == null) { skipped++; continue; }
                    float[] vector = embedding.vectorToFloatArray();
                    if (vector == null || vector.length != model.getInt("embeddingDim"))
                        throw new IllegalArgumentException("An embedding does not match the configured mobile dimensions");
                    if (++vectors > MAX_EMBEDDINGS) throw new IllegalArgumentException("Narrow the search to fewer embeddings");
                    // Stable across releases of this source; prevents cross-instance bare-ID collisions.
                    String sourceId = identity.getId();
                    String individualId = scopedIdentity(job.getString("instanceUrl"), sourceId);
                    MobilePackArchive.Individual individual = groups.computeIfAbsent(individualId,
                        key -> new MobilePackArchive.Individual(key, identity.getDisplayName()));
                    individual.embeddings.add(vector);
                    if (individual.photos.size() < 2) {
                        Path photo = work.resolve(individualId + "-" + individual.photos.size() + ".jpg");
                        photos.add(new PhotoTask(annotation, photo));
                        individual.photos.add(photo);
                    }
                }
            }
            individuals = new ArrayList<>(groups.values());
            job.put("skippedCount", skipped + stale[0]);
        } finally { shepherd.rollbackAndClose(); }
        // Fail before decoding or uploading anything if the released model is not where the profile says.
        JSONObject released = profile.model();
        MobilePackArtifacts.preflight(released.getString("downloadUrl"), released.getLong("sizeBytes"));
        // Image decoding never holds a database connection or persistent objects.
        for (PhotoTask photo : photos) referencePhoto(photo);
        JSONObject detector = profile.detector();
        Path cache = root(context).resolve("artifact-cache");
        Path onnx = MobilePackArtifacts.fetch(cache, detector.getString("downloadUrl"), detector.getString("sha256"), detector.getLong("sizeBytes"));
        if (Files.size(onnx) != detector.getLong("sizeBytes")) throw new IOException("Detector size differs");
        Path config = MobilePackArtifacts.fetch(cache, detector.getString("configUrl"), detector.getString("configSha256"), 65536);
        Path archive = work.resolve("pack.zip");
        JSONObject manifest = MobilePackArchive.write(archive, profile, job.getString("displayName"), job.getString("instanceUrl"), individuals, onnx, config);
        JSONObject descriptor = new JSONObject().put("schemaVersion", 1).put("packId", job.getString("packId"))
            .put("version", job.getString("jobId")).put("displayName", job.getString("displayName"))
            .put("species", profile.species()).put("featureClass", profile.featureClass())
            .put("wildbookInstanceUrl", job.getString("instanceUrl"))
            .put("individualCount", manifest.getInt("individualCount")).put("embeddingCount", manifest.getInt("embeddingCount"))
            .put("archive", new JSONObject().put("sha256", MobilePackArtifacts.sha256(archive)).put("sizeBytes", Files.size(archive)))
            .put("embeddingModel", profile.model());
        // Reload the current user before publication; loss of the publishing role aborts the job.
        Shepherd recheck = new Shepherd(context);
        recheck.beginDBTransaction();
        try {
            User owner = recheck.getUser(job.getString("owner"));
            if (owner == null || !owner.isAdmin(recheck)) throw new IllegalArgumentException("Publication permission changed");
            for (String encounterId : encounterIds)
                if (recheck.getEncounter(encounterId) == null) throw new IllegalArgumentException("Search records changed; export again");
        } finally { recheck.rollbackAndClose(); }
        job.put("status", "publishing"); save(context, job.getString("jobId"), job);
        job.put("publication", new MobilePackPublisher(context).publish(descriptor, archive));
    }

    public static String scopedIdentity(String instance, String source) throws Exception {
        return "wb-" + DigestUtils.sha256Hex(instance + "\n" + source);
    }

    private static void referencePhoto(PhotoTask photo) throws Exception {
        BufferedImage image;
        try (javax.imageio.stream.ImageInputStream input = ImageIO.createImageInputStream(photo.source.toFile())) {
            if (input == null) throw new IllegalArgumentException("Reference photo is unavailable");
            Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported reference image");
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if ((long)reader.getWidth(0) * reader.getHeight(0) > 40000000L)
                    throw new IllegalArgumentException("Reference image exceeds 40 megapixels");
                image = reader.read(0);
            } finally { reader.dispose(); }
        }
        if (image == null) throw new IllegalArgumentException("Reference photo is not a supported image");
        BufferedImage thumbnail = ReferenceCrop.fit(
            ReferenceCrop.crop(image, photo.rotationInfo, photo.box, photo.theta), 640);
        if (!ImageIO.write(thumbnail, "jpg", photo.output.toFile())) throw new IOException("Could not encode reference photo");
    }
}
