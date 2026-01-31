package org.ecocean.export;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EncounterCOCOExportFile {
    private final List<Encounter> encounters;
    private final Shepherd shepherd;

    public EncounterCOCOExportFile(List<Encounter> encounters, Shepherd shepherd) {
        this.encounters = encounters;
        this.shepherd = shepherd;
    }

    public void writeTo(OutputStream outputStream) throws IOException {
        // TODO: implement
    }
}
