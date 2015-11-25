package org.ecocean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.MessageFormat;
import java.util.Properties;

/**
 * Utility class for displaying results of a webapp action.
 * To use, create an {@code ActionResult} instance and assign it as an attribute of the {@code HttpServletRequest}
 * before forwarding to the JSP page for display (see {@link #JSP_PAGE}).
 * <p>
 * To customize results messages, place resource definitions in the {@code actionResult.properties} files.
 * Link/comment/detail texts are processed via a {@code MessageFormat} instance, and client code may assign
 * object arrays for each to be formatted appropriately.
 *
 * @author Giles Winstanley
 */
public class ActionResult {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(ActionResult.class);
  /** Session key for referencing batch upload data. */
  public static final String SESSION_KEY = "ActionResult";
  /** Path for referencing JSP page. */
  public static final String JSP_PAGE = "/actionResult.jsp";
  /** Whether action succeeded. */
  protected boolean succeeded;
  /** String representing resource key for action. */
  protected String actionKey;
  /** String representing onward navigation link. */
  protected String link;
  /** Object parameters for filling link text (if needed). */
  protected Object[] linkParams;
  /** Object parameters for filling comments (if needed). */
  protected Object[] commentParams;
  /** Object parameters for filling detail text (if needed). */
  protected Object[] detailParams;
  /** Whether detail text is preformatted (as for HTML pre tags). */
  protected boolean pre;

  /**
   * Creates a new instance.
   * @param actionKey resource key for action
   * @param succeeded whether action succeeded
   * @param link URL link for onward navigation
   */
  public ActionResult(String actionKey, boolean succeeded, String link) {
    this.actionKey = actionKey;
    this.succeeded = succeeded;
    this.link = link;
  }

  /**
   * Creates a new instance.
   * @param actionKey resource key for action
   * @param succeeded whether action succeeded
   */
  public ActionResult(String actionKey, boolean succeeded) {
    this(actionKey, succeeded, null);
  }

  public ActionResult setLink(String link) {
    this.link = link;
    return this;
  }

  /**
   * Sets the parameters for displayed link text.
   * @param o object array for use in formatted message
   * @return this ActionResult instance (for method chaining support)
   */
  public ActionResult setLinkParams(Object... o) {
    this.linkParams = o;
    return this;
  }

  /**
   * Sets the parameters for displayed comment text.
   * @param o object array for use in formatted message
   * @return this ActionResult instance (for method chaining support)
   */
  public ActionResult setCommentParams(Object... o) {
    this.commentParams = o;
    return this;
  }

  /**
   * Sets the parameters for displayed detail text.
   * @param o object array for use in formatted message
   * @return this ActionResult instance (for method chaining support)
   */
  public ActionResult setDetailParams(Object... o) {
    this.detailParams = o;
    return this;
  }

  /**
   * Sets whether the detail text should be displayed as preformatted (like HTML &lt;pre&gt; tag).
   * @param pre whether text is preformatted
   * @return this ActionResult instance (for method chaining support)
   */
  public ActionResult setDetailTextPreformatted(boolean pre) {
    this.pre = pre;
    return this;
  }

  /**
   * @return true if detail text is set to be preformatted, false otherwise
   */
  public boolean isDetailTextPreformatted() {
    return pre;
  }

  /**
   * @return String key for resource lookup
   */
  public String getActionKey() {
    return actionKey;
  }

  /**
   * @return true if ActionResult is marked as succeeded, false otherwise
   */
  public boolean isSucceeded() {
    return succeeded;
  }

  /**
   * Returns the message text for this ActionResult.
   * @param bundle resources for string lookup
   * @return String representing message text
   */
  public String getMessage(Properties bundle) {
    return bundle.getProperty(String.format("%s.message.%s", actionKey, succeeded ? "success" : "failure"));
  }

  /**
   * Returns the link (navigable URL) for this ActionResult.
   * @return String representing link
   */
  public String getLink() {
    return link;
  }

  /**
   * Utility method to find first non-null matching key from the specified resource.
   * @param bundle resources to search
   * @param keys keys to use for finding a non-null match
   * @return matching resource, or null if none found
   */
  protected static String findFirstMatchingNonNull(Properties bundle, String[] keys) {
    for (String s : keys) {
      String x = bundle.getProperty(s);
      if (x != null)
        return x;
    }
    return null;
  }

  /**
   * Returns the link text for this ActionResult.
   * @param bundle resources for string lookup
   * @return String representing link text
   */
  public String getLinkText(Properties bundle) {
    String[] keys = {
            String.format("%s.link.%s", actionKey, succeeded ? "success" : "failure"),
            String.format("%s.link.common", actionKey),
            "action.generic.link.common"
    };
    String text = findFirstMatchingNonNull(bundle, keys);
    return linkParams == null ? text : MessageFormat.format(text, linkParams);
  }

  /**
   * Returns the comment text for this ActionResult.
   * @param bundle resources for string lookup
   * @return String representing comment text
   */
  public String getComment(Properties bundle) {
    String[] keys = {
            String.format("%s.comment.%s", actionKey, succeeded ? "success" : "failure"),
            String.format("%s.comment.common", actionKey),
            String.format("action.generic.comment.%s", succeeded ? "success" : "failure"),
            "action.generic.comment.common"
    };
    String text = findFirstMatchingNonNull(bundle, keys);
    // If resource text is just a placeholder for parameter, return null if param doesn't exist,
    // otherwise return resource text.
    return commentParams == null ? ("{0}".equals(text) ? null : text) : MessageFormat.format(text, commentParams);
  }

  /**
   * Returns the detail text for this ActionResult.
   * @param bundle resources for string lookup
   * @return String representing detail text
   */
  public String getDetailText(Properties bundle) {
    String[] keys = {
            String.format("%s.detail.%s", actionKey, succeeded ? "success" : "failure"),
            String.format("%s.detail.common", actionKey),
            String.format("action.generic.detail.%s", succeeded ? "success" : "failure"),
            "action.generic.detail.common"
    };
    String text = findFirstMatchingNonNull(bundle, keys);
    return detailParams == null ? ("{0}".equals(text) ? null : text) : MessageFormat.format(text, detailParams);
  }
}
