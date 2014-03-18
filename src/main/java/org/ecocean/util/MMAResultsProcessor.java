package org.ecocean.util;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.ecocean.SinglePhotoVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to convert text file results from the MantaMatcher algorithm plain
 * text output format to HTML format for display.
 * <p>Compared to the algorithm functionality, it is restricted in only
 * allowing a single <em>test</em> and single <em>reference</em> folder each
 * to be specified.
 * <p>The specified folder is scanned for files with <em>.txt</em> extension,
 * which are considered MMA output files. These are parsed for results, and
 * associated XHTML files (extension <em>.xhtml</em>) are generated for display.
 * Any existing XHMTL files with relevant filename will be overwritten.
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
   * @param refUrlPrefix URL prefix to use for reference links
   * @param pageUrlFormat Format string for encounter page URL (with <em>%s</em> placeholder)
   * @return A map containing parsed results ready for use with a FreeMarker template
   * @throws IOException
   * @throws ParseException
   * @throws TemplateException
   */
  static String convertResultsToHtml(Configuration conf, String text, SinglePhotoVideo spv, String refUrlPrefix, String pageUrlFormat) throws IOException, TemplateException, ParseException {
    // Parse results from text file.
    MMAResult matchResult = parseMatchResults(text);
    // Convert results to data model for template engine.
    Map model = convertResultsToTemplateModel(matchResult, spv, refUrlPrefix, pageUrlFormat);

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
   * @param matchResult MMAResult instance in which to place parsed data
   * @param spv {@code SinglePhotoVideo} instance denoting base reference image
   * @param refUrlPrefix URL prefix to use for reference links
   * @param pageUrlFormat Format string for page URLs (with <em>%s</em> placeholder)
   * @return A map containing parsed results ready for use with a FreeMarker template
   * @throws IOException
   * @throws ParseException
   * @throws TemplateException
   */
  @SuppressWarnings("unchecked")
  private static Map convertResultsToTemplateModel(MMAResult matchResult, SinglePhotoVideo spv, String refUrlPrefix, String pageUrlFormat) throws FileNotFoundException, UnsupportedEncodingException {

    // Construct test dir-path & URL-prefix from ref versions.
    File testDir = spv.getFile().getParentFile();
    String testUrlPrefix = String.format("%s/%s", refUrlPrefix, testDir.getName());
//    log.trace(String.format("testDir      : %s", testDir));
//    log.trace(String.format("testUrlPrefix: %s", testUrlPrefix));
//    log.trace(String.format("refUrlPrefix : %s", refUrlPrefix));

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
      String nameCR = fCR.getName();
      String nameCR_url = URLEncoder.encode(nameCR, "US-ASCII");
      String encUrl = String.format(pageUrlFormat, fCR.getParentFile().getName());
      modelResult.put("link", encUrl);
      modelResult.put("name", nameCR.substring(0, nameCR.indexOf("_CR")));
      modelResult.put("nameCR", nameCR);
      modelResult.put("linkCR", String.format("%s/%s", testUrlPrefix, nameCR));
      modelResult.put("nameEH", nameCR.replace("_CR", "_EH"));
      modelResult.put("linkEH", String.format("%s/%s", testUrlPrefix, nameCR_url.replace("_CR", "_EH")));
      modelResult.put("nameFT", nameCR.replace("_CR", "_FT"));
      modelResult.put("linkFT", String.format("%s/%s", testUrlPrefix, nameCR_url.replace("_CR", "_FT")));
//      log.trace("Processing: {}", modelResult.get("nameCR"));
      modelResult.put("featureCount", matchResult.featureCount);
      modelResult.put("timeTaken", matchResult.timeTaken);
      modelResult.put("confidence", matchResult.confidence);

      List modelMatches = new ArrayList();
      modelResult.put("matches", modelMatches);
      for (MMAMatch match : test.getValue()) {
        String encUrlMatch = String.format(pageUrlFormat, match.ref);
        Map modelMatch = new HashMap();
        modelMatch.put("rank", match.rank);
        modelMatch.put("ref", match.ref);
        modelMatch.put("link", encUrlMatch);
        modelMatch.put("score", match.score);
        modelMatch.put("imgbase", match.bestMatch);
//        log.trace(String.format("encUrlMatch: %s", modelMatch.get("encUrlMatch")));
//        log.trace(String.format("rank       : %s", modelMatch.get("rank")));
//        log.trace(String.format("ref        : %s", modelMatch.get("ref")));
//        log.trace(String.format("link       : %s", modelMatch.get("link")));
//        log.trace(String.format("score      : %s", modelMatch.get("score")));
//        log.trace(String.format("imgbase    : %s", modelMatch.get("imgbase")));

        if (match.bestMatch != null) {
          modelMatch.put("nameCR", String.format("%s_CR", match.bestMatch));
          modelMatch.put("nameEH", String.format("%s_EH", match.bestMatch));
          modelMatch.put("nameFT", String.format("%s_FT", match.bestMatch));
          modelMatch.put("linkCR", String.format("%s/%s/%s_CR.jpg", refUrlPrefix, match.ref, match.bestMatch));
          modelMatch.put("linkEH", String.format("%s/%s/%s_EH.jpg", refUrlPrefix, match.ref, match.bestMatch));
          modelMatch.put("linkFT", String.format("%s/%s/%s_FT.jpg", refUrlPrefix, match.ref, match.bestMatch));
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
      "Confidence: ",
      "best match"
    };
    for (String check : CHECKS) {
      if (text.indexOf(check) < 0)
        return false;
    }
    return true;
  }

  /**
   * Parses the MMA results from the results text.
   * @param text text of results file
   * @return MMAResult instance containing parsed data
   */
  private static MMAResult parseMatchResults(String text) throws IOException, ParseException {
    if (!checkResultsFormat(text))
      throw new IOException("Invalid results file format");
    // Split results into sections.
    // Section 0: header, reference set, test set, match specification
    // Section n: result n
    // Section N: match summary
    String[] sections = text.split("={10,}");

    MMAResult result = new MMAResult();
    for (int i = 0; i < sections.length; i++) {
      if (i == 0)
        parseMatchResultsHeader(result, sections[i].trim());
      else if (i == sections.length - 1)
        parseMatchResultsSummary(result, sections[i].trim());
      else
        parseMatchResultsCore(result, sections[i].trim());
    }

    return result;
  }

  /**
   * Parses the core section of MMA results text.
   * @param result MMAResult instance in which to place parsed data
   * @param text text of core section of the results file
   */
  private static void parseMatchResultsCore(MMAResult result, String text) throws ParseException {
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
      sc.nextLine();

      Pattern p = Pattern.compile("^(\\d+)\\) \\{([^{}]+)\\} \\[([\\d.]+)\\]\\s*(?:\\(best match: '([^']+)'\\))?$", Pattern.MULTILINE);
      List<MMAMatch> matches = new ArrayList<MMAMatch>();
      while ((s = sc.findInLine(p)) != null) {
        mr = sc.match();
        MMAMatch res = new MMAMatch();
        res.rank = Integer.parseInt(mr.group(1));
        res.ref = mr.group(2);
        res.score = Float.parseFloat(mr.group(3));
        if (mr.group(4) != null) {
          res.bestMatch = mr.group(4);
          // Only add items with a non-zero score.
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
   * Simple data holder for MMA match results.
   */
  private static final class MMAResult {
    private String version;
    private Date date;
    private Map<String, Integer> mapRefCounts;
    private Map<String, Integer> mapTestCounts;
    private String matchingMethod;
    private String matchingDirection;
    private String scoreCombination;
    private int featureCount;
    private float timeTaken;
    private float confidence;
    private Map<String, List<MMAMatch>> mapTests = new LinkedHashMap<String, List<MMAMatch>>();

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
    private int rank;
    private String ref;
    private float score;
    private String bestMatch;

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("MMAResult{");
      sb.append("rank=").append(rank);
      sb.append(", ref=").append(ref);
      sb.append(", score=").append(score);
      sb.append(", bestMatch=").append(bestMatch);
      sb.append("}");
      return sb.toString();
    }
  }
}
