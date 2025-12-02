package org.ecocean.export;

import org.apache.commons.lang3.StringUtils;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.MarkedIndividual;
import org.ecocean.MultiValue;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

public class EncounterImageExportFile {
    public static final String UNIDENTIFIED_INDIVIDUAL = "unidentified_individual";

    public enum ExportOptions {
        IncludeUnidentifiedEncounters
    } private final List<Encounter> encounters;
    private final Map<String, MarkedIndividual> encounterToIndividual;
    private final int numAnnotationsPerId;
    private final EnumSet<ExportOptions> exportFlags;

    public EncounterImageExportFile(List<Encounter> encounters,
        Map<String, MarkedIndividual> encounterIndividualMap, int numAnnotationsPerId,
        EnumSet<ExportOptions> exportFlags) {
        this.encounters = encounters;
        this.encounterToIndividual = encounterIndividualMap;
        this.numAnnotationsPerId = numAnnotationsPerId;
        this.exportFlags = exportFlags;
    }

    public void writeTo(ZipOutputStream outputStream) {
        for (Encounter e : encounters) {
            int annotatationIdx = 0;
            for (Annotation a : e.getAnnotations()) {
                MediaAsset ma = a.getMediaAsset();
                if (!a.getMatchAgainst() || StringUtils.isBlank(a.getViewpoint()) ||
                    ma.webURL() == null) {
                    System.out.printf("Skipping annotation %s%n", a.getId());
                    continue;
                }
                // if numAnnotationsPerId is 1, then the annotationIdx will be 1 as we write the second image
                if (numAnnotationsPerId > 0 && annotatationIdx >= numAnnotationsPerId) {
                    continue;
                }
                try {
                    String displayName = getDisplayName(e, encounterToIndividual);
                    if (Objects.equals(displayName,
                        UNIDENTIFIED_INDIVIDUAL) &&
                        !exportFlags.contains(ExportOptions.IncludeUnidentifiedEncounters)) {
                        continue;
                    }
                    URI imageUrl = ma.webURL().toURI();
                    System.out.printf("Writing image [%s] %s%n", displayName, imageUrl);

                    ZipEntry croppedImageEntry = new ZipEntry(String.format("images/%s/%s_%s.jpg",
                        displayName, e.getID(), annotatationIdx++));
                    outputStream.putNextEntry(croppedImageEntry);
                    BufferedImage originalImage = ImageIO.read(imageUrl.toURL());
                    BufferedImage croppedImage;
                    int[] bbox = a.getBbox();
                    if (bbox == null || bbox.length < 4 || bbox[2] == 0 || bbox[3] == 0) {
                        croppedImage = originalImage;
                    } else {
                        croppedImage = originalImage.getSubimage(bbox[0], bbox[1], bbox[2],
                            bbox[3]);
                        System.out.printf("  Cropped to [%d,%d,%d,%d] %n", bbox[0], bbox[1],
                            bbox[2], bbox[3]);
                    }
                    ImageIO.write(croppedImage, "jpg", outputStream);
                    outputStream.closeEntry();
                } catch (Exception ex) {
                    throw new RuntimeException("Unable to process annotation " + a.getId() +
                            " for encounter " + e.getId(), ex);
                }
            }
        }
    }

    private String getDisplayName(Encounter e,
        Map<String, MarkedIndividual> encounterToIndividual) {
        String displayName = UNIDENTIFIED_INDIVIDUAL;

        // Try the map first (handles both old and new relationships)
        MarkedIndividual individual = encounterToIndividual.get(e.getCatalogNumber());

        if (individual != null) {
            MultiValue names = individual.getNames();
            List<String> nameLabels = names.getSortedKeys();
            if (!nameLabels.isEmpty()) {
                displayName = names.getValue(nameLabels.get(0));
            }
        }
        return displayName;
    }
}
