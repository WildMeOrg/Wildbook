package org.ecocean.export;

import org.apache.commons.lang3.StringUtils;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.MarkedIndividual;
import org.ecocean.MultiValue;

import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

public class EncounterImageExportFile {
    private final List<Encounter> encounters;
    private final Map<String, MarkedIndividual> encounterToIndividual;

    public EncounterImageExportFile(List<Encounter> encounters,
        Map<String, MarkedIndividual> encounterIndividualMap) {
        this.encounters = encounters;
        this.encounterToIndividual = encounterIndividualMap;
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
                try {
                    String displayName = getDisplayName(e, encounterToIndividual);
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

    private static String getDisplayName(Encounter e,
        Map<String, MarkedIndividual> encounterToIndividual) {
        String displayName = "unidentified_individual";

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
