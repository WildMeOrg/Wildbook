package org.ecocean.mmutil;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.*;
import java.net.URLEncoder;
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
  public static final String MMA_VER = "1.24a";

  /**
   * Parses the MantaMatcher results text file for the specified SPV,
   * then converts it to a formatted HTML results page (via FreeMarker).
   * @param conf FreeMarker configuration
   * @param spv {@code SinglePhotoVideo} instance denoting base reference image
   * @param dataDir folder containing all webapp data (for deriving reference folders)
   * @param refUrlPrefix URL prefix to use for reference links
   * @param pageUrlFormat Format string for encounter page URL (with <em>%s</em> placeholder)
   * @return A map containing parsed results ready for use with a FreeMarker template
   * @throws IOException
   * @throws ParseException
   * @throws TemplateException
   */
  static String convertResultsToHtml(Shepherd shepherd, Configuration conf, String text, SinglePhotoVideo spv, File dataDir, String refUrlPrefix, String pageUrlFormat) throws IOException, TemplateException, ParseException {
    // Create null data model for template engine.
    Map model = null;
    try {
      // Parse results from text file.
      MMAResult matchResult = parseMatchResults(text, spv, dataDir);
      // Convert results to data model for template engine.
      model = convertResultsToTemplateModel(shepherd, matchResult, spv, refUrlPrefix, pageUrlFormat);
    } catch (Exception ex) {
      // Handled by null model recognized by template.
    }
    // Fill template and write to output file.
    Template temp = conf.getTemplate("MantaMatcher-results.ftl");
    StringWriter sw = new StringWriter();
    temp.process(model, sw);
    sw.flush();
    return sw.getBuffer().toString();
  }

  /**
   * Converts a parsed MantaMatcher results object into a FreeMarker template
   * model for the specified SPV.
   * The resulting FreeMarker model map can be used directly in a FreeMarker
   * template, with the following model structure (showing map key names):
   * <ul>
   * <li><strong>version</strong> - MantaMatcher algorithm version string
   * <li><strong>datetime</strong> - {@code Date} instance parsed from version string
   * <li><strong>matchMethod</strong> - string describing algorithm match method
   * <li><strong>matchDirection</strong> - string describing algorithm match direction
   * <li><strong>scoreCombination</strong> - string describing algorithm score combination method
   * <li><strong>results</strong> - list of test results parsed from results text file
   *   <ul>
   *   <li><strong>link</strong> - link to test encounter page
   *   <li><strong>name</strong> - name of test image
   *   <li><strong>nameCR</strong> - name of test CR image
   *   <li><strong>linkCR</strong> - link to test CR image
   *   <li><strong>nameEH</strong> - name of test EH image
   *   <li><strong>linkEH</strong> - link to test EH image
   *   <li><strong>nameFT</strong> - name of test FT image
   *   <li><strong>linkFT</strong> - link to test FT image
   *   <li><strong>featureCount</strong> - feature count for this result
   *   <li><strong>timeTaken</strong> - time taken to process this result
   *   <li><strong>confidence</strong> - confidence in score of this result
   *   <li><strong>matches</strong> - list of individual matches (in order)
   *     <ul>
   *     <li><strong>rank</strong> - rank of match
   *     <li><strong>ref</strong> - reference folder name
   *     <li><strong>imgbase</strong> - reference file name
   *     <li><strong>link</strong> - link to matched encounter page
   *     <li><strong>score</strong> - algorithm score of match
   *     <li><strong>nameCR</strong> - name of match CR image
   *     <li><strong>linkCR</strong> - link to match CR image
   *     <li><strong>nameEH</strong> - name of match EH image
   *     <li><strong>linkEH</strong> - link to match EH image
   *     <li><strong>nameFT</strong> - name of match FT image
   *     <li><strong>linkFT</strong> - link to match FT image
   *     <li><strong>individualID</strong> - individualID of match
   *     <li><strong>encounterDate</strong> - encounter date of match
   *     <li><strong>pigmentation</strong> - pigmentation of match
   *     </ul>
   *   </ul>
   * </ul>
   * @param matchResult MMAResult instance in which to place parsed data
   * @param spv {@code SinglePhotoVideo} instance denoting base reference image
   * @param dataDirUrlPrefix URL prefix to use for reference links
   * @param pageUrlFormat Format string for page URLs (with <em>%s</em> placeholder)
   * @return A map containing parsed results ready for use with a FreeMarker template
   * @throws FileNotFoundException if unable to find CR image files
   * @throws UnsupportedEncodingException if unable to encode a URL (unlikely)
   */
  @SuppressWarnings("unchecked")
  private static Map convertResultsToTemplateModel(Shepherd shepherd, MMAResult matchResult, SinglePhotoVideo spv, String dataDirUrlPrefix, String pageUrlFormat) throws FileNotFoundException, UnsupportedEncodingException {

    // Repackage data model into template model.
    Map model = new HashMap();
    List modelResults = new ArrayList();
    model.put("results", modelResults);
    model.put("version", matchResult.version);
    model.put("datetime", matchResult.date);
//    model.put("refCounts", matchResult.mapRefCounts);
//    model.put("testCounts", matchResult.mapTestCounts);
    model.put("matchMethod", matchResult.matchingMethod);
    model.put("matchDirection", matchResult.matchingDirection);
    model.put("scoreCombination", matchResult.scoreCombination);

    for (Map.Entry<String, List<MMAMatch>> test : matchResult.mapTests.entrySet()) {
      Map modelResult = new HashMap();
      modelResults.add(modelResult);

      String pathCR = test.getKey();
      File fCR = new File(pathCR);
      if (!fCR.isFile())
        throw new FileNotFoundException("File not found: " + fCR.getAbsolutePath());
      String encId = fCR.getParentFile().getName();

      String nameCR = fCR.getName();
      String nameEH = nameCR.replace("_CR", "_EH");
      String nameFT = nameCR.replace("_CR", "_FT");
      String linkCR = convertFileToURL(dataDirUrlPrefix, fCR);
      String linkEH = linkCR.replace("_CR", "_EH");
      String linkFT = linkCR.replace("_CR", "_FT");
      String encUrl = String.format(pageUrlFormat, encId);
      modelResult.put("link", encUrl);
      modelResult.put("name", nameCR.substring(0, nameCR.indexOf("_CR")));
      modelResult.put("nameCR", nameCR);
      modelResult.put("linkCR", linkCR);
      modelResult.put("nameEH", nameEH);
      modelResult.put("linkEH", linkEH);
      modelResult.put("nameFT", nameFT);
      modelResult.put("linkFT", linkFT);
//      log.trace("Processing: {}", modelResult.get("nameCR"));
      modelResult.put("featureCount", matchResult.featureCount);
      modelResult.put("timeTaken", matchResult.timeTaken);
      modelResult.put("confidence", matchResult.confidence);

      List modelMatches = new ArrayList();
      modelResult.put("matches", modelMatches);
      for (MMAMatch match : test.getValue()) {
//        log.trace(String.format("Match: %s", match));
        // Null-check needed in case referent wasn't found.
        if (match.fileRef == null)
          continue;
        File dir = match.fileRef.getParentFile();
        String matchEncId = dir.getName();
        String encUrlMatch = String.format(pageUrlFormat, matchEncId);
        Map modelMatch = new HashMap();
        modelMatch.put("rank", match.rank);
        modelMatch.put("ref", dir.getName());
        modelMatch.put("link", encUrlMatch);
        modelMatch.put("score", match.score);
        modelMatch.put("imgbase", match.bestMatch);
//        log.trace(String.format("encUrlMatch: %s", modelMatch.get("encUrlMatch")));
//        log.trace(String.format("rank       : %s", modelMatch.get("rank")));
//        log.trace(String.format("ref        : %s", modelMatch.get("ref")));
//        log.trace(String.format("link       : %s", modelMatch.get("link")));
//        log.trace(String.format("score      : %s", modelMatch.get("score")));
//        log.trace(String.format("imgbase    : %s", modelMatch.get("imgbase")));

        // Fill details from match.
        Encounter enc = shepherd.getEncounter(matchEncId);
        modelMatch.put("individualID", enc.getIndividualID());
        modelMatch.put("encounterDate", enc.getVerbatimEventDate());
        modelMatch.put("pigmentation", enc.getPatterningCode());

        if (match.bestMatch != null) {
          modelMatch.put("nameCR", String.format("%s_CR", match.bestMatch));
          modelMatch.put("nameEH", String.format("%s_EH", match.bestMatch));
          modelMatch.put("nameFT", String.format("%s_FT", match.bestMatch));
          // Derive links to image files for this match.
          Map<String, File> mmMap = MantaMatcherUtilities.getMatcherFilesMap(match.fileRef);
          String fnCR = mmMap.get("CR").getName();
          String fnEH = mmMap.get("EH").getName();
          String fnFT = mmMap.get("FT").getName();
          String mlinkCR = convertFileToURL(dataDirUrlPrefix, mmMap.get("CR"));
          String mlinkEH = convertFileToURL(dataDirUrlPrefix, mmMap.get("EH"));
          String mlinkFT = convertFileToURL(dataDirUrlPrefix, mmMap.get("FT"));
          modelMatch.put("linkCR", mlinkCR);
          modelMatch.put("linkEH", mlinkEH);
          modelMatch.put("linkFT", mlinkFT);
          modelMatches.add(modelMatch);
//          log.trace(String.format("nameCR: %s", modelMatch.get("nameCR")));
//          log.trace(String.format("nameEH: %s", modelMatch.get("nameEH")));
//          log.trace(String.format("nameFT: %s", modelMatch.get("nameFT")));
//          log.trace(String.format("linkCR: %s", modelMatch.get("linkCR")));
//          log.trace(String.format("linkEH: %s", modelMatch.get("linkEH")));
//          log.trace(String.format("linkFT: %s", modelMatch.get("linkFT")));
        }
      }
    }

    return model;
  }

  private static final String convertFileToURL(String dataDirUrlPrefix, File file) throws UnsupportedEncodingException {
    File dir = file.getParentFile();
    String dirStr = dir.getAbsolutePath().replace(File.separatorChar, '/');
    dirStr = dirStr.replaceFirst("^.*/encounters/", "");
    String url = String.format("%s/%s/%s", dataDirUrlPrefix, dirStr, URLEncoder.encode(file.getName(), "UTF-8"));
    log.trace(String.format("file            : %s", file.getAbsolutePath()));
    log.trace(String.format("dataDirUrlPrefix: %s", dataDirUrlPrefix));
    log.trace(String.format("dirStr          : %s", dirStr));
    log.trace(String.format("url             : %s", url));
    return url;
  }

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
   * @param text text of results file
   * @param spv {@code SinglePhotoVideo} instance denoting base reference image
   * @param dataDir folder containing all webapp data (for deriving reference folders)
   * @return MMAResult instance containing parsed data
   */
  private static MMAResult parseMatchResults(String text, SinglePhotoVideo spv, File dataDir) throws IOException, ParseException {
    if (!checkResultsFormat(text))
      throw new IOException("Invalid results file format");
    // Split results into sections.
    // Section 0: header, reference set, test set, match specification
    // Section n: result n
    // Section N: match summary
    String[] sections = text.split("={10,}");

    MMAResult result = new MMAResult();
    result.dirTest = spv.getFile().getParentFile();
    for (int i = 0; i < sections.length; i++) {
      if (i == 0)
        parseMatchResultsHeader(result, sections[i].trim());
      else if (i == sections.length - 1)
        parseMatchResultsSummary(result, sections[i].trim());
      else
        parseMatchResultsCore(result, sections[i].trim(), dataDir, result.dirTest);
    }

    return result;
  }

  /**
   * Parses the core section of MMA results text.
   * @param result MMAResult instance in which to place parsed data
   * @param text text of core section of the results file
   * @param dataDir folder containing all webapp data (for deriving reference folders)
   * @param dirTest folder containing test images (for deriving reference folder)
   * @throws ParseException if there is a problem during parsing
   * @throws IOException if there is a problem locating {@code dirTest}
   */
  private static void parseMatchResultsCore(MMAResult result, String text, File dataDir, File dirTest) throws ParseException, IOException {
    Scanner sc = null;
    try {
      sc = new Scanner(text).useDelimiter("[\\r\\n]+");
      // Parse header info.
      String s = sc.findInLine("Processing : (.+)");
      MatchResult mr = sc.match();
      String pathCR = mr.group(1);
//      log.trace("Processing: {}", pathCR);
      sc.nextLine();

      s = sc.findInLine("No. of features : (\\d+)");
      mr = sc.match();
      result.featureCount = Integer.parseInt(mr.group(1));
//      log.trace("Feature Count: {}", result.featureCount);
      sc.nextLine();

      s = sc.findInLine("Matching [^:]+ : ([\\d.]+)");
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

        Pattern p = Pattern.compile("^(\\d+)\\) \\{([^{}]+)\\} \\[([\\d.]+)\\]\\s*(?:\\(best match: '([^']+)'\\))?$", Pattern.MULTILINE);
        List<MMAMatch> matches = new ArrayList<MMAMatch>();
        while ((s = sc.findInLine(p)) != null) {
          mr = sc.match();
          MMAMatch res = new MMAMatch();
          res.rank = Integer.parseInt(mr.group(1));
          res.score = Float.parseFloat(mr.group(3));
          if (mr.group(4) != null) {
            res.bestMatch = mr.group(4);
            // Obtain encounter dir for matched image.
            File dir = new File(Encounter.dir(dataDir, mr.group(2)));
            // Find matched image file (base image, not CR).
            res.fileRef = findReferenceFile(dir, res.bestMatch);
            // Only add items with a non-zero score (only lines with 'best match' anyway).
            matches.add(res);
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
    finally {
      if (sc != null)
        sc.close();
    }
  }

  /**
   * Parses the header section of MMA results text.
   * @param result MMAResult instance in which to place parsed data
   * @param text text of header section of the results file
   */
  private static void parseMatchResultsHeader(MMAResult result, String text) throws ParseException {
    Scanner sc = null;
    try {
      sc = new Scanner(text).useDelimiter("[\\r\\n]+");
      // Parse header info.
      String s = sc.findInLine("(Manta Matcher version [^,]+),\\s+(.+)");
      MatchResult mr = sc.match();
      DateFormat df = new SimpleDateFormat("dd MMM yyyy, hh:mm:ss");
      result.version = mr.group(1);
      result.date = df.parse(mr.group(2));
      sc.nextLine();
      sc.nextLine();

      // Create holders for reference/test sets.
      List<String> refs = new ArrayList<String>();
      List<Integer> refCounts = new ArrayList<Integer>();
      List<String> tests = new ArrayList<String>();

      // Parse reference set.
      Pattern p = Pattern.compile("^(?:Reference Set: )?(.+)(?<!SIFT files found)$", Pattern.MULTILINE);
      while ((s = sc.findInLine(p)) != null) {
        refs.add(sc.match().group(1));
        sc.nextLine();
      }
      p = Pattern.compile("([\\d]+)\\s+SIFT files found", Pattern.MULTILINE);
      int sum = 0;
      while ((s = sc.findInLine(p)) != null) {
        int n = Integer.parseInt(sc.match().group(1));
        // SIFT count in text file is cumulative, so calculate difference.
        int dif = n - sum;
        refCounts.add(dif);
        sc.nextLine();
        sum += dif;
      }
      sc.nextLine();

      // Parse test set.
      p = Pattern.compile("^(?:Test Image: )?(.+)(?<!SIFT files found)$", Pattern.MULTILINE);
      while ((s = sc.findInLine(p)) != null) {
        tests.add(sc.match().group(1));
//        log.trace("Test: {}", tests.get(tests.size() - 1));
        sc.nextLine();
      }
      sc.nextLine();
      Map<String, Integer> mapRefCounts = new LinkedHashMap<String, Integer>();
      Map<String, Integer> mapTestCounts = new LinkedHashMap<String, Integer>();
      for (int i = 0; i < refs.size(); i++)
        mapRefCounts.put(refs.get(i), refCounts.get(i));
      for (int i = 0; i < tests.size(); i++)
        mapTestCounts.put(tests.get(i), 1);
      result.mapRefCounts = mapRefCounts;
      result.mapTestCounts = mapTestCounts;

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
    finally {
      if (sc != null)
        sc.close();
    }
  }

  /**
   * Parses the summary section of MMA results text.
   * @param result MMAResult instance in which to place parsed data
   * @param text text of summary section of the results file
   */
  private static void parseMatchResultsSummary(MMAResult result, String text) throws ParseException {
    // TODO: implement
  }

  /**
   * Utility method to locate an image file using its parent folder and root
   * filename. Unfortunately the MMA currently doesn't reference the full
   * filename, so this needs to be derived by examining the file-system for
   * possible files matching the root name.
   * @param dir folder in which to search
   * @param root root filename
   * @return file reference
   * @throws IOException if there is a problem locating {@code dir}
   */
  private static File findReferenceFile(File dir, String root) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("^").append(root).append("\\.").append(MediaUtilities.REGEX_SUFFIX_FOR_WEB_IMAGES);
    FilenameFilter ff = new RegexFilenameFilter(sb.toString());
    File[] files = dir.listFiles(ff);
    if (files == null)
      throw new FileNotFoundException("Folder not found: " + dir.getAbsolutePath());
    if (files.length == 0)
      return null;
    if (files.length > 1)
      log.warn(String.format("Found %d matching image files in folder: %s", files.length, dir.getAbsolutePath()));
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
  private static final class MMAResult {
    /** MantaMatcher Algorithm version string. */
    private String version;
    /** Date/time parsed from version string. */
    private Date date;
    /** Map of reference-folder to SIFT files found. */
    private Map<String, Integer> mapRefCounts;
    /** Map of test-folder to SIFT files found. */
    private Map<String, Integer> mapTestCounts;
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
    private Map<String, List<MMAMatch>> mapTests = new LinkedHashMap<String, List<MMAMatch>>();
    /** Test folder in which source comparison image files are found. */
    private File dirTest;

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("MMAMatchResult{");
      sb.append("version=").append(version);
      sb.append(", date=").append(date);
      sb.append(", refs=").append(mapRefCounts == null ? "null" : Integer.toString(mapRefCounts.size()));
      sb.append(", tests=").append(mapTestCounts == null ? "null" : Integer.toString(mapTestCounts.size()));
      sb.append(", featureCount=").append(featureCount);
      sb.append(", timeTaken=").append(timeTaken);
      sb.append(", confidence=").append(confidence);
      sb.append("}");
      return sb.toString();
    }
  }

  /**
   * Simple data holder for each match within a single MMA match result.
   */
  private static final class MMAMatch {
    /** Rank of result within Ranking List. */
    private int rank;
    /** Score, as assigned by MantaMatcher algorithm. */
    private float score;
    /** Filename root of match. */
    private String bestMatch;
    /** Reference file of matched image (base image, not CR, etc.). */
    private File fileRef;

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("MMAResult{");
      sb.append("rank=").append(rank);
      sb.append(", score=").append(score);
      sb.append(", bestMatch=").append(bestMatch);
      sb.append(", fileRef=").append(fileRef == null ? "" : fileRef.getAbsolutePath());
      sb.append("}");
      return sb.toString();
    }
  }
}
