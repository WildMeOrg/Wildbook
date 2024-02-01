package org.ecocean.mmutil;

import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to convert text file results from the MantaMatcher algorithm plain
 * text output format to HTML format for display.
 * <p>Compared to the algorithm functionality, it is restricted in only
 * allowing a single <em>test</em> and single <em>reference</em> folder each
 * to be specified.
 * <p>For a specified {@code SinglePhotoVideo} instance, its folder it scanned
 * for an associated MMA output file (with <em>.txt</em> extension).
 * This is parsed for results, and a FreeMarker template is used to generate
 * and XHTML output file for display.
 *
 * @author Giles Winstanley
 *
 * @see <a href="http://freemarker.org/">FreeMarker template engine</a>
 */
public final class MMAResultsProcessor {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(MMAResultsProcessor.class);
  /** Version of MMA for which this code works. */
  public static final String MMA_VER = "1.26a";

  /**
   * Checks the format of the results file text for specification conformance.
   * @param text text of results file
   * @return true if text conforms, false otherwise
   */
  private static boolean checkResultsFormat(String text) {
    // Perform quick checks for valid results format.
    final String[] CHECKS = {
      "Manta Matcher version " + MMA_VER,
      "Reference Set: ",
      "SIFT files found",
      "Test Image: ",
      "### Ranking List ###",
      "Confidence: "
    };
    for (String check : CHECKS) {
      if (!text.contains(check))
        return false;
    }
    return true;
  }

  /**
   * Parses the MMA results from the results text.
   * @param shepherd {@code Shepherd} instance for object reference
   * @param text text of results file
   * @param spv {@code SinglePhotoVideo} instance denoting base reference image
   * @param dataDir folder containing all webapp data (for deriving reference folders)
   * @return MMAResult instance containing parsed data
   * @throws IOException if thrown during parsing
   * @throws ParseException if thrown during parsing
   */
  public static MMAResult parseMatchResults(Shepherd shepherd, String text, SinglePhotoVideo spv, File dataDir) throws IOException, ParseException {
    if (!checkResultsFormat(text))
      throw new IOException("Invalid results file format");
    // Split results into sections.
    // Section 0: header, reference set, test set, match specification
    // Section n: result n
    // Section N: match summary
    String[] sections = text.split("={10,}");

    MMAResult result = new MMAResult();
    result.dirTest = spv.getFile().getParentFile();
    result.testEncounterNumber = spv.getCorrespondingEncounterNumber();
    for (int i = 0; i < sections.length; i++) {
      if (i == 0)
        parseMatchResultsHeader(shepherd, result, sections[i].trim());
      else if (i == sections.length - 1)
        parseMatchResultsSummary(shepherd, result, sections[i].trim());
      else
        parseMatchResultsCore(shepherd, result, sections[i].trim(), dataDir, result.dirTest);
    }

    return result;
  }

  /**
   * Parses the core section of MMA results text.
   * @param shepherd {@code Shepherd} instance for object reference
   * @param result MMAResult instance in which to place parsed data
   * @param text text of core section of the results file
   * @param dataDir folder containing all webapp data (for deriving reference folders)
   * @param dirTest folder containing test images (for deriving reference folder)
   * @throws ParseException if there is a problem during parsing
   * @throws IOException if there is a problem locating {@code dirTest}
   */
  private static void parseMatchResultsCore(Shepherd shepherd, MMAResult result, String text, File dataDir, File dirTest) throws ParseException, IOException {
    try (Scanner sc = new Scanner(text).useDelimiter("[\\r\\n]+")) {
      // Parse header info.
      String s = sc.findInLine("Processing : (.+) \\(original image: (.+)\\)");
      MatchResult mr = sc.match();
      String pathCR = mr.group(1);
      String pathOriginal = mr.group(2);
//      log.trace("Processing: {}", pathCR);
      sc.nextLine();

      s = sc.findInLine("No. of features : (\\d+)");
      mr = sc.match();
      result.featureCount = Integer.parseInt(mr.group(1));
//      log.trace("Feature Count: {}", result.featureCount);
      sc.nextLine();
      sc.nextLine();

      s = sc.findInLine("time taken : ([\\d.]+)");
      mr = sc.match();
      result.timeTaken = Float.parseFloat(mr.group(1));
//      log.trace("Time Taken: {}", result.timeTaken);
      sc.nextLine();
      sc.nextLine();
      sc.nextLine();

      s = sc.findInLine("Confidence: ([\\d.]+)");
      mr = sc.match();
      result.confidence = Float.parseFloat(mr.group(1));
//      log.trace("Confidence: {}", result.confidence);
      if (sc.hasNext())
      {
        sc.nextLine();

        Pattern p = Pattern.compile("^(\\d+)\\) \\{([^{}]+)\\} \\[([\\d.]+)\\]\\s*(?:\\(best match: '([^']+)', path='([^']+)'\\))?$", Pattern.MULTILINE);
        List<MMAMatch> matches = new ArrayList<>();
        while ((s = sc.findInLine(p)) != null) {
          mr = sc.match();
          MMAMatch res = new MMAMatch();
          res.rank = Integer.parseInt(mr.group(1));
          res.score = Float.parseFloat(mr.group(3));
          if (mr.group(4) != null && mr.group(5) != null) {
            res.bestMatch = mr.group(4);
            res.bestMatchPath = mr.group(5);
            // Parse/assign encounter number.
            res.matchEncounterNumber = mr.group(2);
            try {
              // Obtain encounter dir for matched image.
              File dir = new File(Encounter.dir(dataDir, res.matchEncounterNumber));
              // Find matched image file (base image, not CR).
              res.fileRef = findReferenceFile(dir, res.bestMatch, res.bestMatchPath);
              // Fill details from encounter object.
              Encounter enc = shepherd.getEncounter(res.matchEncounterNumber);
              if (enc != null) {
                res.individualId = enc.getIndividualID();
                res.encounterDate = enc.getDate();
                res.pigmentation = enc.getPatterningCode();
                // Only add items with a non-zero score (only lines with 'best match' anyway).
                matches.add(res);
              }
            } catch (Exception ex) {
              // Ignore; just means previously matched encounter can't now be found.
              log.trace("Failed to find encounter for match: " + res.matchEncounterNumber);
            }
          }
          try
          {
            sc.nextLine();
          }
          catch (NoSuchElementException ex)
          {
            // Ignore.
          }
        }
        result.mapTests.put(pathCR, matches);
      }
    }
  }

  /**
   * Parses the header section of MMA results text.
   * @param shepherd {@code Shepherd} instance for object reference
   * @param result MMAResult instance in which to place parsed data
   * @param text text of header section of the results file
   */
  private static void parseMatchResultsHeader(Shepherd shepherd, MMAResult result, String text) throws ParseException {
    try (Scanner sc = new Scanner(text).useDelimiter("[\\r\\n]+")) {
      // Parse header info.
      String s = sc.findInLine("(Manta Matcher version ([^,]+)),\\s+(.+)");
      MatchResult mr = sc.match();
      DateFormat df = new SimpleDateFormat("dd MMM yyyy, hh:mm:ss");
      result.version = mr.group(2);
      result.date = df.parse(mr.group(3));
      sc.nextLine();
      sc.nextLine();

      // Create holders for reference/test sets.
      List<String> refs = new ArrayList<>();
      List<String> tests = new ArrayList<>();

      // Parse reference set.
      Pattern p = Pattern.compile("^(?:Reference Set: )?(.+)(?<!SIFT files found)$", Pattern.MULTILINE);
      while ((s = sc.findInLine(p)) != null) {
        refs.add(sc.match().group(1));
//        log.trace(String.format("Found reference item: %s", refs.get(refs.size() - 1)));
        sc.nextLine();
      }
      s = sc.findInLine("([\\d]+)\\s+SIFT files found");
      mr = sc.match();
      result.siftCountRef = Integer.parseInt(mr.group(1));
      sc.nextLine();
      sc.nextLine();

      // Parse test set.
      p = Pattern.compile("^(?:Test Image: )?(.+)(?<!SIFT files found)$", Pattern.MULTILINE);
      while ((s = sc.findInLine(p)) != null) {
        tests.add(sc.match().group(1));
//        log.trace("Found test item: {}", tests.get(tests.size() - 1));
        sc.nextLine();
      }
      sc.nextLine();

      // Parse match/score choices.
      s = sc.findInLine("Matching method: (.+)");
      result.matchingMethod = sc.match().group(1);
//      log.trace("Matching method: {}", result.matchingMethod);
      sc.nextLine();
      s = sc.findInLine("Matching direction: (.+)");
      result.matchingDirection = sc.match().group(1);
//      log.trace("Matching direction: {}", result.matchingDirection);
      sc.nextLine();
      s = sc.findInLine("Score combination: (.+)");
      result.scoreCombination = sc.match().group(1);
//      log.trace("Score combination: {}", result.scoreCombination);
    }
  }

  /**
   * Parses the summary section of MMA results text.
   * @param shepherd {@code Shepherd} instance for object reference
   * @param result MMAResult instance in which to place parsed data
   * @param text text of summary section of the results file
   */
  private static void parseMatchResultsSummary(Shepherd shepherd, MMAResult result, String text) throws ParseException {
    // TODO: implement
  }

  /**
   * Utility method to locate an original reference image file using a path
   * reference to a related CR image file.
   * This needs to be derived by examining the file-system.
   * @param dir folder in which to search
   * @param root root filename
   * @param crPath path to CR image file
   * @return file reference
   * @throws IOException if there is a problem locating {@code dir}
   */
  private static File findReferenceFile(File dir, String root, String crPath) throws IOException {
    if (dir == null || !dir.isDirectory())
      throw new FileNotFoundException("Folder not found: " + dir);
    List<File> files = MediaUtilities.listWebImageFiles(dir, root);
    if (files.isEmpty())
      return null;
    if (files.size() > 1)
      log.warn(String.format("Found %d matching image files in folder: %s", files.size(), dir.getAbsolutePath()));
    // Double-check for existence of file.
    for (File f : files) {
      if (f.exists()) {
        return f;
      }
    }
    return null;
  }

  /**
   * Simple data holder for MMA match results.
   */
  public static final class MMAResult {
    /** MantaMatcher Algorithm version string. */
    private String version;
    /** Date/time parsed from version string. */
    private Date date;
    /** Count of reference SIFT files found. */
    private Integer siftCountRef;
    /** Encounter number of item being tested for matches. */
    private String testEncounterNumber;
    /** Matching method used by algorithm. */
    private String matchingMethod;
    /** Matching direction used by algorithm. */
    private String matchingDirection;
    /** Score combination method used by algorithm. */
    private String scoreCombination;
    /** Feature count found by algorithm. */
    private int featureCount;
    /** Time taken for algorithm processing. */
    private float timeTaken;
    /** Score confidence. */
    private float confidence;
    /** Map of test-image-path to match-results. */
    private Map<String, List<MMAMatch>> mapTests = new LinkedHashMap<>();
    /** Test folder in which source comparison image files are found. */
    private File dirTest;

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("MMAMatchResult{");
      sb.append("version=").append(version);
      sb.append(", date=").append(date);
      sb.append(", siftCountRef=").append(siftCountRef == null ? "null" : siftCountRef);
      sb.append(", testEncounterNumber=").append(testEncounterNumber);
      sb.append(", featureCount=").append(featureCount);
      sb.append(", timeTaken=").append(timeTaken);
      sb.append(", confidence=").append(confidence);
      sb.append("}");
      return sb.toString();
    }

    public String getVersion() { return version; }
    public Date getDate() { return date; }
    public Integer getSiftCountRef() { return siftCountRef; }
    public String getTestEncounterNumber() { return testEncounterNumber; }
    public String getMatchingMethod() { return matchingMethod; }
    public String getMatchingDirection() { return matchingDirection; }
    public String getScoreCombination() { return scoreCombination; }
    public int getFeatureCount() { return featureCount; }
    public float getTimeTaken() { return timeTaken; }
    public float getConfidence() { return confidence; }
    public Map<String, List<MMAMatch>> getMapTests() { return mapTests; }
    public File getDirTest() { return dirTest; }
  }

  /**
   * Simple data holder for each match within a single MMA match result.
   */
  public static final class MMAMatch {
    /** Rank of result within Ranking List. */
    private int rank;
    /** Score, as assigned by MantaMatcher algorithm. */
    private float score;
    /** Encounter number of reference item for this match. */
    private String matchEncounterNumber;
    /** Filename root of match. */
    private String bestMatch;
    /** Filename path of match. */
    private String bestMatchPath;
    /** Reference file of matched image (base image, not CR, etc.). */
    private File fileRef;
    /** MarkedIndividual ID relating to this encounter. */
    private String individualId;
    /** Date of encounter. */
    private String encounterDate;
    /** Pigmentation of manta relating to this encounter. */
    private String pigmentation;

    private MMAMatch() {}

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("MMAResult{");
      sb.append("rank=").append(rank);
      sb.append(", score=").append(score);
      sb.append(", matchEncounterNumber=").append(matchEncounterNumber);
      sb.append(", bestMatch=").append(bestMatch);
      sb.append(", bestMatchPath=").append(bestMatchPath);
      sb.append(", fileRef=").append(fileRef == null ? "" : fileRef.getAbsolutePath());
      sb.append("}");
      return sb.toString();
    }

    public int getRank() { return rank; }
    public float getScore() { return score; }
    public String getMatchEncounterNumber() { return matchEncounterNumber; }
    public String getBestMatch() { return bestMatch; }
    public String getBestMatchPath() { return bestMatchPath; }
    public File getFileRef() { return fileRef; }
    public String getIndividualId() { return individualId; }
    public String getEncounterDate() { return encounterDate; }
    public String getPigmentation() { return pigmentation; }
  }
}
