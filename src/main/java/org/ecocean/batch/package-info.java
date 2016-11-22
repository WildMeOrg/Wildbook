/**
 * Package containing code relating to administrator-level batch upload feature
 * for importing data.
 *
 * <p>These classes are generally not used alone, and are referenced by the
 * {@link org.ecocean.servlet.BatchUpload BatchUpload} servlet,
 * which mediates its use along with associated JSP pages.</p>
 *
 * <p>The parser requires three CSV files to be created in standard RFC4180
 * format, one to represent individuals, one to represent encounters,
 * and one to represent photos relating to the encounters.
 * Each CSV file should be in the UTF-8 character-encoding, and must contain
 * a header row denoting the data field for each column.
 * The BatchParser class also has the ability to generate skeleton CSV
 * files, which can be used as templates for end users to complete either
 * by hand, or as a guide for auto-generating the CSV files from code.</p>
 *
 * <p>The batch processor will parse the CSV files to check for errors,
 * of which the user will be notified if any are found. If no errors are found,
 * the data is collated into new Java objects ready for persistence to the
 * database. A summary/confirmation step is included to allow the user to
 * cancel the operation before the data is committed to the database.
 * This allows users to check whether their CSV data will be interpreted
 * correctly by the batch processor, or needs additional manipulation.
 *
 * @see org.ecocean.servlet.BatchUpload
 * @see <a href="http://www.ietf.org/rfc/rfc4180.txt">RFC4180</a>
 *
 * @author Giles Winstanley
 */
package org.ecocean.batch;
