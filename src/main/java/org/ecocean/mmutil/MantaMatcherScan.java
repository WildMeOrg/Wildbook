package org.ecocean.mmutil;

import org.ecocean.Encounter;
import org.ecocean.media.*;
import org.ecocean.StringUtils;
import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * Represents a MantaMatcher algorithm scan.
 *
 * @author Giles Winstanley
 */
public final class MantaMatcherScan implements Serializable, Comparable<MantaMatcherScan> {
  private static final long serialVersionUID = 4738545960875882715L;
  /** Data collection event ID of SinglePhotoVideo scanned. */
  private String dataCollectionEventId;
  /** Set of LocationIDs used to perform the scan (to support retro scans: null = GLOBAL, empty = REGIONAL). */
  private Set<String> locationIds;
  /** Unique ID for this scan. */
  private String id;
  /** File containing scan input (TXT file). */
  private File scanInput;
  /** File containing scan results (TXT file). */
  private File scanOutputTXT;
  /** File containing scan results (CSV file). */
  private File scanOutputCSV;
  /** Date/time of scan. */
  private Date dateTime;
  /** Flag indicating whether either of the files is non-standard. */
  private boolean nonStandardFiles = false;

  public MantaMatcherScan(MediaAsset ma, Encounter enc,Collection<String> locationIds, Date dateTime) {
    Objects.requireNonNull(ma);
    Objects.requireNonNull(locationIds);
    Objects.requireNonNull(dateTime);
    File file=ma.localPath().toFile();
    this.dataCollectionEventId = (new Integer(ma.getId())).toString();
    this.locationIds = new TreeSet<>(locationIds);
    this.id = UUID.randomUUID().toString();
    this.scanInput = new File(file.getParentFile(), String.format("%s_mmaScan_%s_input.txt", (new Integer(ma.getId())).toString(), id));
    this.scanOutputTXT = new File(file.getParentFile(), String.format("%s_mmaScan_%s_output.txt", (new Integer(ma.getId())).toString(), id));
    this.scanOutputCSV = new File(file.getParentFile(), String.format("%s_mmaScan_%s_output.csv", (new Integer(ma.getId())).toString(), id));
    this.dateTime = dateTime;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    MantaMatcherScan that = (MantaMatcherScan)o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public String getDataCollectionEventId() {
    return dataCollectionEventId;
  }

  public Set<String> getLocationIds() {
    return locationIds;
  }

  /**
   * Collates the LocationIDs into a single string using the specified extra string data.
   * @param res ResourceBundle to use for LocationID lookups
   * @param resKey format string to use as resource key for lookups (e.g. &quot;locationName.%s&quot;)
   * @param prefix prefix for each LocationID item
   * @param suffix suffix for each LocationID item
   * @param delimiter delimiter between items
   * @return string representing all LocationIDs
   */
  public String getLocationIdString(ResourceBundle res, String resKey, String prefix, String suffix, String delimiter) {
    return StringUtils.collateStrings(locationIds, res, resKey, prefix, suffix, delimiter);
  }

  /**
   * Collates the LocationIDs into a single string using the specified extra string data.
   * @param prefix prefix for each LocationID item
   * @param suffix suffix for each LocationID item
   * @param delimiter delimiter between items
   * @return string representing all LocationIDs
   */
  public String getLocationIdString(String prefix, String suffix, String delimiter) {
    return StringUtils.collateStrings(locationIds, prefix, suffix, delimiter);
  }

  public File getScanInput() {
    return scanInput;
  }

  public File getScanOutputTXT() {
    return scanOutputTXT;
  }

  public File getScanOutputCSV() {
    return scanOutputCSV;
  }

  public Date getDateTime() {
    return dateTime;
  }

  public void setDateTime(Date dateTime) {
    this.dateTime = dateTime;
  }

  public void deleteScanFiles() {
    scanInput.delete();
    scanOutputTXT.delete();
    scanOutputCSV.delete();
  }

  /**
   * Returns the ID of this scan instance.
   * @return ID of scan (currently based on millisecond date/time), or null if date/time not set
   */
  public String getId() {
    return id;
  }

  @Override
  public int compareTo(MantaMatcherScan o) {
    return (o == null) ? -1 : dateTime.compareTo(o.getDateTime());
  }
}
