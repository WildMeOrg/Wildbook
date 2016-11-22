package org.ecocean.batch;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser of CSV data fields for batch upload to Wildbook.
 * <p>The parser requires at least two CSV files to be created in RFC4180
 * format, one to represent individuals, and one to represent encounters.
 * Each CSV file should be in the UTF-8 character-encoding, and must contain
 * a header row denoting the data field for each column. Further CSV files
 * may also be specified for parsing (media/samples).</p>
 * <p>For each input CSV file there exists a corresponding method to retrieve
 * the parsed data:</p>
 * <ul>
 * <li>{@link #getIndividualData()}</li>
 * <li>{@link #getEncounterData()}</li>
 * <li>{@link #getMediaData()}</li>
 * <li>{@link #getSampleData()}</li>
 * </ul>
 *
 * @see <a href="http://www.wildme.org/wildbook/">Wildbook</a>
 * @see <a href="https://github.com/holmbergius/Wildbook">Wildbook on Github</a>
 * @see <a href="http://www.ietf.org/rfc/rfc4180.txt">RFC4180</a>
 *
 * @author Giles Winstanley
 */
public class BatchParser {
  /** Name of resources file. */
  private static final String RESOURCES = "BatchParser";
  /** SLF4J logger instance for writing log entries. */
  private static Logger log = LoggerFactory.getLogger(BatchParser.class);
  /** Enumeration of template types. */
  public static enum Type { INDIVIDUAL, ENCOUNTER, MEASUREMENT, MEDIA, SAMPLE }
  /** Locale for internationalization. */
  private Locale locale;
  /** Resources for internationalization. */
  private ResourceBundle res;
  /** List of DateFormat instances for parsing dates. */
  private final List<DateFormat> dateFormat = new ArrayList<DateFormat>();
  /** List of DateFormat instances for parsing times. */
  private final List<DateFormat> timeFormat = new ArrayList<DateFormat>();
  /** List of DateFormat instances for parsing date/times. */
  private final List<DateFormat> dateTimeFormat = new ArrayList<DateFormat>();
  /** CSV file containing individuals to be parsed. */
  private File csvInd;
  /** CSV file containing encounters to be parsed. */
  private File csvEnc;
  /** CSV file containing measurements to be parsed. */
  private File csvMea;
  /** CSV file containing media to be parsed. */
  private File csvMed;
  /** CSV file containing samples to be parsed. */
  private File csvSam;
  /** CSV file to be parsed. */
  private final List<String> errors = new ArrayList<String>();
  /** Ordered list of field key (for encounters). */
  private final List<String> encPosList = new ArrayList<String>();
  /** Ordered list of field key (for individuals). */
  private final List<String> indPosList = new ArrayList<String>();
  /** Ordered list of field key (for measurements). */
  private final List<String> meaPosList = new ArrayList<String>();
  /** Ordered list of field key (for media). */
  private final List<String> medPosList = new ArrayList<String>();
  /** Ordered list of field key (for samples). */
  private final List<String> samPosList = new ArrayList<String>();
  /** Map of field key to CSV column header/title. */
  private final Map<String, String> headerMap = new HashMap<String, String>();
  /** Map of CSV column header/title to field key (for individuals). */
  private final Map<String, String> headerPamInd = new HashMap<String, String>();
  /** Map of CSV column header/title to field key (for encounters). */
  private final Map<String, String> headerPamEnc = new HashMap<String, String>();
  /** Map of CSV column header/title to field key (for measurements). */
  private final Map<String, String> headerPamMea = new HashMap<String, String>();
  /** Map of CSV column header/title to field key (for media). */
  private final Map<String, String> headerPamMed = new HashMap<String, String>();
  /** Map of CSV column header/title to field key (for samples). */
  private final Map<String, String> headerPamSam = new HashMap<String, String>();
  /** Map of field key to required flag. */
  private final Map<String, Boolean> requiredMap = new HashMap<String, Boolean>();
  /** Map of field key to field format string. */
  private final Map<String, String> formatMap = new HashMap<String, String>();
  /** Map of field key to format type. */
  private final Map<String, FormatType> typeMap = new HashMap<String, FormatType>();
  /** Map of field values for individuals. */
  private List<Map<String, Object>> indValueMap = new ArrayList<Map<String, Object>>();
  /** Map of field values for encounters. */
  private List<Map<String, Object>> encValueMap = new ArrayList<Map<String, Object>>();
  /** Map of field values for measurements. */
  private List<Map<String, Object>> meaValueMap = new ArrayList<Map<String, Object>>();
  /** Map of field values for media. */
  private List<Map<String, Object>> medValueMap = new ArrayList<Map<String, Object>>();
  /** Map of field values for samples. */
  private List<Map<String, Object>> samValueMap = new ArrayList<Map<String, Object>>();

  public BatchParser(Locale locale, File csvInd, File csvEnc) throws IOException {
    this.csvInd = csvInd;
    this.csvEnc = csvEnc;
    this.locale = (locale == null) ? Locale.getDefault() : locale;
    String pfx = getClass().getPackage().getName() + ".";
    this.res = ResourceBundle.getBundle(pfx + RESOURCES, this.locale);
    // Setup DateFormat instances for parsing dates.
    dateFormat.add(new SimpleDateFormat("yyyy-MM-dd"));
//    dateFormat.add(new SimpleDateFormat("dd/MM/yyyy"));
    // Setup DateFormat instances for parsing times.
    timeFormat.add(new SimpleDateFormat("HH:mm"));
//    timeFormat.add(new SimpleDateFormat("hh:mma"));
//    timeFormat.add(new SimpleDateFormat("hh:mm a"));
    // Setup DateFormat instances for parsing date/times.
    dateTimeFormat.add(new SimpleDateFormat("yyyy-MM-dd HH:mm"));

    for (DateFormat df : dateFormat)
      df.setLenient(false);
    for (DateFormat df : timeFormat)
      df.setLenient(false);
    for (DateFormat df : dateTimeFormat)
      df.setLenient(false);

    readFieldOrder();
    readFieldResources();
    crossCheckFieldResources();
    // Check for successful parsing of resources.
    if (!errors.isEmpty()) {
      for (String s : errors)
        log.trace(s);
      throw new IOException("Error parsing resources");
    }
  }

  public BatchParser(File csvInd, File csvEnc) throws IOException {
    this(null, csvInd, csvEnc);
  }

  public BatchParser(Locale locale) throws IOException {
    this(locale, null, null);
  }

  /**
   * Sets the CSV file containing definitions of measurements.
   * @param csvMea File instance containing data to be parsed
   */
  public void setFileMeasurements(File csvMea) {
    this.csvMea = csvMea;
  }

  /**
   * Sets the CSV file containing definitions of media.
   * @param csvMed File instance containing data to be parsed
   */
  public void setFileMedia(File csvMed) {
    this.csvMed = csvMed;
  }

  /**
   * Sets the CSV file containing definitions of samples.
   * @param csvSam File instance containing data to be parsed
   */
  public void setFileSamples(File csvSam) {
    this.csvSam = csvSam;
  }

  /**
   * @return The list of error messages that occurred during parsing
   */
  public List<String> getErrors() {
    return errors;
  }

  /**
   * @return The list of maps of field keys to field values for individuals
   */
  public List<Map<String, Object>> getIndividualData() {
    return indValueMap;
  }

  /**
   * @return The list of maps of field keys to field values for encounters
   */
  public List<Map<String, Object>> getEncounterData() {
    return encValueMap;
  }

  /**
   * @return the list of maps of field keys to field values for measurements
   */
  public List<Map<String, Object>> getMeasurementData() {
    return meaValueMap;
  }

  /**
   * @return the list of maps of field keys to field values for media
   */
  public List<Map<String, Object>> getMediaData() {
    return medValueMap;
  }

  /**
   * @return the list of maps of field keys to field values for samples
   */
  public List<Map<String, Object>> getSampleData() {
    return samValueMap;
  }

  /**
   * Reads resources describing order of CSV fields.
   * Due to an inherent Java restriction on handling ResourceBundles,
   * this file has to be read again to determine field order.
   */
  private void readFieldOrder() throws IOException {
    String pfx = "/" + getClass().getPackage().getName().replace(".", "/") + "/";
    InputStream is = getClass().getResourceAsStream(pfx + RESOURCES + ".properties");
    if (is == null)
      throw new IOException("Unable to find resource for field order");
    BufferedReader in = null;
    try {
      Pattern p = Pattern.compile("^column\\.((individual|encounter|measurement|media|sample)\\.([A-Za-z_]+))\\s*=.*$", Pattern.CASE_INSENSITIVE);
      in = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      for (String line = in.readLine(); line != null; line = in.readLine()) {
        Matcher m = p.matcher(line);
        if (!m.matches())
          continue;
        if ("individual".equals(m.group(2)))
          indPosList.add(m.group(1));
        else if ("encounter".equals(m.group(2)))
          encPosList.add(m.group(1));
        else if ("measurement".equals(m.group(2)))
          meaPosList.add(m.group(1));
        else if ("media".equals(m.group(2)))
          medPosList.add(m.group(1));
        else if ("sample".equals(m.group(2)))
          samPosList.add(m.group(1));
      }
    } finally {
      if (in != null)
        in.close();
    }
  }

  /**
   * Reads resources describing relationships between CSV columns and class fields.
   * Populates internal maps:
   * <ul>
   * <li>Map of field key to header title (e.g. individual.sex =&gt; Sex</li>
   * <li>Map of field key to required flag (e.g. individual.sex =&gt; true</li>
   * <li>Map of field key to format type (e.g. individual.sex =&gt; &lt;FormatType instance&gt;</li>
   * <li>Map of field key to format string (e.g. individual.sex =&gt; {"male","female","unknown"}</li>
   * </ul>
   */
  private void readFieldResources() {
    // Read extra info about CSV columns.
    Pattern p = Pattern.compile("^(column|format)\\.(([a-z]+)\\.([A-Za-z_]+))$");
    for (String key : res.keySet()) {
      Matcher m = p.matcher(key);
      if (!m.matches())
        continue;

      if ("column".equals(m.group(1))) {
        headerMap.put(m.group(2), res.getString(key));
        Map<String, String> headerPam = null;
        if (m.group(3).equals("individual"))
          headerPam = headerPamInd;
        else if (m.group(3).equals("encounter"))
          headerPam = headerPamEnc;
        else if (m.group(3).equals("measurement"))
          headerPam = headerPamMea;
        else if (m.group(3).equals("media"))
          headerPam = headerPamMed;
        else if (m.group(3).equals("sample"))
          headerPam = headerPamSam;
        else
          continue;
        headerPam.put(res.getString(key), m.group(2));
        // Check if column is required.
        try {
          String mf = res.getString(key + ".required");
          if (mf != null && Boolean.parseBoolean(mf))
            requiredMap.put(m.group(2), Boolean.TRUE);
        } catch (MissingResourceException mrx) {
          // Ignore, as not required.
        }
      } else if ("format".equals(m.group(1))) {
        String fs = res.getString(key);
        // Check/assign default format (string).
        if (fs == null || "".equals(fs))
          fs = "string";
        // Parse format string via regex. --- FIXME:START HERE
        formatMap.put(m.group(2), fs);
        FormatType ft = FormatType.findType(fs);
//        log.trace(String.format("Mapped '%s' (%s / %s)", m.group(2), fs, ft));
        if (ft != null) {
          typeMap.put(m.group(2), ft);
        } else {
          errors.add(MessageFormat.format(res.getString("err.InvalidFormat"), m.group(1), m.group(2)));
//          log.trace(String.format("fs=%s, ft=%s, m(0)=%s, m(1)=%s, m(2)=%s", fs, ft, m.group(0), m.group(1), m.group(2)));
        }
      }
    }
  }

  /**
   * Cross-checks the resources describing relationships between CSV columns
   * and class fields.
   */
  private void crossCheckFieldResources() throws IOException {
    List<String> list = new ArrayList<String>();
    for (Map.Entry<String, String> me : headerMap.entrySet()) {
      if (!formatMap.containsKey(me.getKey())) {
        list.add(me.getKey());
        errors.add(MessageFormat.format(res.getString("err.MissingFormat"), me.getValue()));
      }
    }
    for (String key : formatMap.keySet()) {
      if (!headerMap.containsKey(key)) {
        log.info(String.format("Unused format (i.e. no column definition) for key: %s", key));
      }
    }
    if (!list.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append(" (");
      for (int i = 0; i < list.size(); i++) {
        if (i > 0)
          sb.append(", ");
        sb.append(list.get(i));
      }
      sb.append(")");
      String pfx = getClass().getPackage().getName() + ".";
      throw new IOException("Error in resource integrity; check contents of ResourceBundle: " + pfx + RESOURCES + sb.toString());
    }
  }

  /**
   * Performs CSV file parsing.
   * @return true if parsing succeeded, false otherwise.
   * @throws FileNotFoundException if the specified individuals/encounter files can't be found
   */
  public boolean parseBatchData() throws FileNotFoundException {
    if (!csvInd.exists())
      throw new FileNotFoundException("Individuals CSV file not found");
    if (!csvEnc.exists())
      throw new FileNotFoundException("Encounters CSV file not found");

    indValueMap = parseBatchData(csvInd, Type.INDIVIDUAL);
    encValueMap = parseBatchData(csvEnc, Type.ENCOUNTER);
    if (csvMea != null && csvMea.exists())
      meaValueMap = parseBatchData(csvMea, Type.MEASUREMENT);
    if (csvMed != null && csvMed.exists())
      medValueMap = parseBatchData(csvMed, Type.MEDIA);
    if (csvSam != null && csvSam.exists())
      samValueMap = parseBatchData(csvSam, Type.SAMPLE);
    if (encValueMap == null || encValueMap.isEmpty())
      errors.add(MessageFormat.format(res.getString("err.NoData"), toTitleCase(Type.ENCOUNTER.toString())));

    return errors.isEmpty();
  }

  /**
   * Performs CSV file parsing.
   * Parsing is somewhat tolerant, as column order is not enforced,
   * and data types are coerced to some degree.
   * (e.g. boolean =&gt; true/false/yes/no)
   * @param csvFile CSV file to parse
   * @param type type of data item to parsed
   * @return map of field keys to values
   */
  private List<Map<String, Object>> parseBatchData(File csvFile, Type type) {
    // Select which type of data to parse.
    Map<String, String> headerPam = null;
    String prefix = null;
    switch (type) {
      case INDIVIDUAL:
        prefix = "individual";
        headerPam = headerPamInd;
        break;
      case ENCOUNTER:
        prefix = "encounter";
        headerPam = headerPamEnc;
        break;
      case MEASUREMENT:
        prefix = "measurement";
        headerPam = headerPamMea;
        break;
      case MEDIA:
        prefix = "media";
        headerPam = headerPamMed;
        break;
      case SAMPLE:
        prefix = "sample";
        headerPam = headerPamSam;
        break;
      default:
        throw new IllegalArgumentException("Invalid type specified: " + type);
    }

    // Find count of field keys for specified prefix.
    int colCount = 0;
    for (String key : headerMap.keySet()) {
      if (key.startsWith(prefix)) {
        colCount++;
      }
    }

    List<Map<String, Object>> valueData = new ArrayList<Map<String, Object>>();
    CSVReader csvr = null;
    try {
      csvr = new CSVReader(new InputStreamReader(new FileInputStream(csvFile), Charset.forName("UTF-8")));
      String[] line = null;

      // Parse column headers.
      // Headers are mapped to field keys to add tolerance for misordered columns.
      List<String> cols = new ArrayList<String>();
      line = csvr.readNext();
      for (String s : line)
      {
        // Check if column header is a known field.
        if (!headerMap.containsValue(s))
          errors.add(MessageFormat.format(res.getString("err.InvalidTitle"), type.toString(), s));
        else
          cols.add(headerPam.get(s));
      }

      // If error already, bail out.
      if (!errors.isEmpty())
      {
        for (String err : errors)
          log.warn(err);
        return null;
      }

      // Parse values from each line of CSV file according to field identifiers.
      int lineNum = 1;
      while ((line = csvr.readNext()) != null) {
        lineNum++;
        // NOTE: This if-block checks for use of all fields;
        // change to '=' to insist all data columns exist in CSV files.
        if (line.length > colCount) {
          errors.add(MessageFormat.format(res.getString("err.InvalidFieldCount"), type.toString(), lineNum, line.length, colCount));
          continue;
        }
        Map<String, Object> valueMap = new HashMap<String, Object>();
        for (int i = 0; i < line.length; i++) {
          // Read column value, and find field key for this column.
          String s = line[i];
          String fieldKey = cols.get(i);
          String fieldHeader = headerMap.get(fieldKey);
          Boolean req = requiredMap.get(fieldKey);
          if (req == null)
            req = Boolean.FALSE;

          // Check for value length too long for database field.
          if (s != null && s.length() > 254) {
            errors.add(MessageFormat.format(res.getString("err.ValueTooLong"), toTitleCase(type.toString()), fieldHeader, lineNum));
            continue;
          }

          // Check for unspecified required field.
          if (req && (s == null || "".equals(s))) {
            errors.add(MessageFormat.format(res.getString("err.RequiredValue"), toTitleCase(type.toString()), fieldHeader, lineNum));
            continue;
          }

          // Check field for parsability to specified type.
          FormatType formatType = typeMap.get(fieldKey);
          Object val = parseValue(s, formatType, formatMap.get(fieldKey));
//          log.trace(String.format("fieldKey=%s, formatType=%s, s=%s, val=%s", fieldKey, formatType, s, val));
          if (val == null || (val instanceof List && ((List)val).isEmpty())) {
            if (req)
              errors.add(MessageFormat.format(res.getString("err.InvalidValue"), toTitleCase(type.toString()), fieldHeader, lineNum, s));
          } else
            valueMap.put(fieldKey, val);
        }
        valueData.add(valueMap);
      }
      return valueData;
    } catch (IOException ex) {
      log.error(ex.getMessage(), ex);
      return null;
    } finally {
      if (csvr != null) {
        try {
          csvr.close();
        } catch (IOException ex) {
          log.error(ex.getMessage(), ex);
        }
      }
    }
  }

  /**
   * Parses the specified string according to the specified {@code FormatType}
   * instance and format string, returning an object instance if parsed
   * successfully, or {@code null} otherwise.
   * @param value value string to be checked
   * @param formatType {@code FormatType} against which to check the string
   * @param formatString {@code FormatType} against which to check the string
   */
  @SuppressWarnings("unchecked")
  private Object parseValue(String value, FormatType formatType, String formatString) {
    assert formatType != null;
    assert formatString != null;
    // Unnecessary to check, but helps populate usable data.
    Matcher fm = formatType.getFormatMatcher(formatString);
    if (!fm.matches()) {
      log.warn("ERROR: format string should have already been checked");
      return null;
    }
    // Match value string against format type.
    Matcher vm = formatType.getValueMatcher(value, formatString);
//    log.trace(String.format("Value:%s, ValueMatcher:%s", value, vm));
    if (vm == null || !vm.matches())
      return null;
    if (value == null || "".equals(value))
      return null;

    String group1 = fm.group(1);
    List<String> list = new ArrayList<String>();
    switch (formatType) {
      case SIMPLE:  // Returns an object (Integer/Long/Float/Double/Boolean/Date).
        // Parse simple type value from value string.
        return parseSimple(group1, vm.group(0));
      case SIMPLE_MULTI:  // Returns a list of SIMPLE-type objects.
        // Determine simple type to use.
        List x = new ArrayList();
        String delim = Character.toString(value.charAt(0));
        for (StringTokenizer st = new StringTokenizer(value, delim); st.hasMoreTokens();)
          x.add(parseSimple(group1, st.nextToken()));
        return x;
      case REGEX:  // Returns a Matcher instance.
        return value.matches(group1) ? Pattern.compile(group1).matcher(value) : null;
//        return value.matches(group1) ? value : null;
      case ENUM:  // Returns a String.
        return value;
//        for (StringTokenizer st = new StringTokenizer(value, ","); st.hasMoreTokens();)
//          list.add(st.nextToken());
//        return list;
      case ENUM_MULTI:  // Returns a list of String objects.
        Pattern emp = Pattern.compile("\\s*\"([^,\"]*)\"\\s*");
        for (StringTokenizer st = new StringTokenizer(value, ","); st.hasMoreTokens();) {
          String s = st.nextToken();
          Matcher emm = emp.matcher(s);
          if (emm.matches())
            list.add(emm.group(1));
        }
        return list;
      default:
    }
    return null;
  }

  /**
   * Parses a &quot;simple&quot; value (i.e. not multi/regex/etc.)
   * @param type simple type string
   * @param value
   * @return parsed value as an object instance
   */
  private Object parseSimple(String type, String value) {
    String trim = value != null ? value.trim() : value;
    if ("string".equals(type))
      return trim;
    else if ("integer".equals(type))
      return Integer.parseInt(trim);
    else if ("long".equals(type))
      return Long.parseLong(trim);
    else if ("float".equals(type))
      return Float.parseFloat(trim);
    else if ("double".equals(type))
      return Double.parseDouble(trim);
    else if ("boolean".equals(type))
      return Boolean.valueOf(trim);
    else if ("date".equals(type)) {
      // Returns a Date instance with time fields set to default,
      // and date fields set to parsed date.
      for (DateFormat df : dateFormat) {
        try {
          Date d = df.parse(trim);
          if (d != null)
            return d;
        } catch (ParseException ex) {
          // Ignore; gets flagged by null value.
        }
      }
    } else if ("time".equals(type)) {
      // Returns a Date instance with date fields set to default,
      // and time fields set to parsed time.
      for (DateFormat df : timeFormat) {
        try {
          Date d = df.parse(trim);
          if (d != null)
            return d;
        } catch (ParseException ex) {
          // Ignore; gets flagged by null value.
        }
      }
    } else if ("datetime".equals(type)) {
      for (DateFormat df : dateTimeFormat) {
        try {
          Date d = df.parse(trim);
          if (d != null)
            return d;
        } catch (ParseException ex) {
          // Ignore; gets flagged by null value.
        }
      }
    } else
      throw new IllegalArgumentException("Invalid type specified: " + type);
    return null;
  }

  private String toTitleCase(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    sb.append(s.substring(0, 1).toUpperCase(locale));
    sb.append(s.substring(1).toLowerCase(locale));
    return sb.toString();
  }

  /**
   * @return a four-element string array of CSV templates.
   */
  public String[] generateTemplates() {
    String tInd = generateTemplate(Type.INDIVIDUAL);
    String tEnc = generateTemplate(Type.ENCOUNTER);
    String tMea = generateTemplate(Type.MEASUREMENT);
    String tMed = generateTemplate(Type.MEDIA);
    String tSam = generateTemplate(Type.SAMPLE);
    return new String[]{tInd, tEnc, tMea, tMed, tSam};
  }

  /**
   *
   * @param type template type to generate
   * @return CSV template as a string
   */
  private String generateTemplate(Type type) {
    List<String> srcList = null;
    switch (type) {
      case INDIVIDUAL:
        srcList = indPosList;
        break;
      case ENCOUNTER:
        srcList = encPosList;
        break;
      case MEASUREMENT:
        srcList = meaPosList;
        break;
      case MEDIA:
        srcList = medPosList;
        break;
      case SAMPLE:
        srcList = samPosList;
        break;
      default:
        throw new IllegalArgumentException("Invalid type specified: " + type);
    }
    List<String> dstList = new ArrayList<String>();

    for (String s : srcList) {
      String key = "column." + s;
      dstList.add(res.getString(key));
    }
    StringWriter sw = new StringWriter();
    try {
      CSVWriter csvw = new CSVWriter(sw);
      csvw.writeNext(dstList.toArray(new String[dstList.size()]));
      csvw.flush();
      csvw.close();
    } catch (IOException iox) {
      log.warn(iox.getMessage(), iox);
    }
    return sw.toString();
  }

  /**
   * Enumerated type to represent field type format.
   */
  private static enum FormatType {
    SIMPLE("\\s*(string|integer|long|float|double|boolean|date|time|datetime)\\s*"),
    SIMPLE_MULTI("\\s*(string|integer|long|float|double|boolean|date|time|datetime)\\s*\\+\\s*$"),
    REGEX("^\\s*(?i:regex):(.+)$"),
    ENUM("^\\s*\\{\\s*((?:[^,]+\\s*,)+\\s*(?:[^,]+))\\s*\\}\\s*"),
    ENUM2("^\\s*\\{\\s*((?:\"[^\"]+\"\\s*,)+\\s*(?:\"[^\"]+\"))\\s*\\}\\s*"),
    ENUM_MULTI("^\\s*\\{\\s*((?:[^\"]+\\s*,)+\\s*(?:[^\"]+))\\s*\\}\\s*\\+\\s*$"),
    OTHER_MULTI("^\\s*(.+)\\s*\\+\\s*$");

    private static final Pattern P_STRING = Pattern.compile(".*");
    private static final Pattern P_INTEGER = Pattern.compile("[+-]?\\d+");
    private static final Pattern P_LONG = Pattern.compile("[+-]?\\d+");
    private static final Pattern P_FLOAT = Pattern.compile("[+-]?\\d+(?:\\.\\d+)?");
    private static final Pattern P_DOUBLE = Pattern.compile("[+-]?\\d+(?:\\.\\d+)?");
    private static final Pattern P_BOOLEAN = Pattern.compile("(?i:true|false|yes|no)");
    private static final Pattern P_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern P_TIME = Pattern.compile("\\d{2}:\\d{2}");
    private static final Pattern P_DATETIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");

    private final String regex;

    FormatType(String regex) {
      this.regex = regex;
    }

    /**
     * Returns a {@code Matcher} for the specified value string, based on the
     * format string specified.
     */
    private Matcher getValueMatcher(CharSequence value, CharSequence format) {
      Matcher fm = getFormatMatcher(format);
      if (!fm.matches())
        throw new RuntimeException("Invalid format string specified for this FormatType");
      Matcher m = null;
      if (SIMPLE.equals(this) || SIMPLE_MULTI.equals(this)) {
        String s = fm.group(1);
        if ("string".equals(s))
          m = P_STRING.matcher(value);
        else if ("integer".equals(s))
          m = P_INTEGER.matcher(value);
        else if ("long".equals(s))
          m = P_LONG.matcher(value);
        else if ("float".equals(s))
          m = P_FLOAT.matcher(value);
        else if ("double".equals(s))
          m = P_DOUBLE.matcher(value);
        else if ("boolean".equals(s))
          m = P_BOOLEAN.matcher(value);
        else if ("date".equals(s))
          m = P_DATE.matcher(value);
        else if ("time".equals(s))
          m = P_TIME.matcher(value);
        else if ("datetime".equals(s))
          m = P_DATETIME.matcher(value);
      } else if (REGEX.equals(this)) {
//        log.trace(String.format("Matching '%s' against pattern '%s'", value, fm.group(1)));
        m = Pattern.compile(fm.group(1)).matcher(value);
      } else if (ENUM.equals(this)) {
        // Build value matcher from enumerated types.
        String[] opts = fm.group(1).split(",");
        StringBuilder sb = new StringBuilder();
        sb.append("^\\s*(?:");
        for (int i = 0; i < opts.length; i++) {
          if (i > 0)
            sb.append("|");
          sb.append(opts[i].replace("|", "\\|"));
        }
        sb.append(")\\s*$");
//				log.trace("ENUM type pattern: " + sb.toString());
        Pattern p = Pattern.compile(sb.toString());
        return p.matcher(value);
      } else if (ENUM2.equals(this)) {
        // TODO: implement
      } else if (ENUM_MULTI.equals(this)) {
        // Build value matcher from enumerated types.
        String[] opts = fm.group(1).split(",");
        StringBuilder sb = new StringBuilder();
        sb.append("(?:");
        for (int i = 0; i < opts.length; i++) {
          if (i > 0)
            sb.append("|");
          sb.append(opts[i].replace("|", "\\|"));
        }
        sb.append(")");
        String optStr = sb.toString();

        sb.setLength(0);
        sb.append("^\\s*((");
        sb.append(optStr);
        sb.append("\\s*,)+\\s*");
        sb.append(optStr);
        sb.append(")\\s*$");
//        log.trace("ENUM_MULTI type pattern: " + sb.toString());
        Pattern p = Pattern.compile(sb.toString());
        return p.matcher(value);
      } else { //if (OTHER_MULTI.equals(this)) {
        // TODO: implement
      }
      return m;
    }

    /**
     * Returns a {@code Matcher} for the specified format type.
     */
    private Matcher getFormatMatcher(CharSequence format) {
      return Pattern.compile(regex).matcher(format);
    }

    private static FormatType findType(CharSequence format) {
      if (format == null || "".equals(format))
        return null;
      for (FormatType type : FormatType.values()) {
        Matcher m = type.getFormatMatcher(format);
        if (m.matches())
          return type;
      }
      return null;
    }
  }
}
