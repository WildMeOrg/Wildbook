package org.ecocean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Utility class for displaying results of a webapp action.
 * To use, create an {@code ActionResult} instance and assign it as an attribute of the {@code HttpServletRequest}
 * before forwarding to the JSP page for display (see {@link #JSP_PAGE}).
 * <p>
 * To customize results messages, place resource definitions in the <em>actionResults.properties</em> files.
 * Link/comment/detail texts are processed via a {@code MessageFormat} instance, and client code may assign
 * object arrays for each to be formatted appropriately.
 * <p>
 * Example Usage:
 * <pre>
 * Locale locale = Locale.getDefault();
 * // ...other code...
 * boolean actionSucceeded = foo.performAction();
 * String link = "http://...";
 * ActionResult actRes = new ActionResult(locale, "doFoo", actionSucceeded, link);
 * actRes.setMessageParams(foo.getId());
 * actRes.setLinkParams(foo.getId());
 * request.getSession().setAttribute(ActionResult.SESSION_KEY, actRes);
 * getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);
 * </pre>
 * For which in <em>actionResults.properties</em> might be the following properties defined:
 * <pre>
 * doFoo.message.success=Foo {0} successfully completed.
 * doFoo.message.failure=Foo {0} couldn't be completed correctly.
 * doFoo.link.common=Return to item {0}
 * </pre>
 * <p>
 * Additional details may be provided, which help explain the action to the user, using the following methods:
 * <ul>
 * <li><strong>Message</strong> is the main message, displayed under the title.</li>
 * <li><strong>Comment</strong> is an optional text panel displayed under the main message.</li>
 * <li><strong>DetailText</strong> is an optional, collapsable, pre-formatted text panel underneath Comment.</li>
 * <li><strong>Link</strong> is an optional link displayed underneath all text.</li>
 * </ul>
 * <p>
 * All sections can take optional formatting parameters, which are assigned with the various
 * {@code setXXXParams(...)} methods.
 * <p>
 * Each section also has a specified priority for resource key lookups, such that generic messages can be specified
 * for convenience. The various key lookups are performed as follows
 * (assuming for example: actionKey=foo, section=message, &amp; the action succeeded):
 * <ol>
 * <li>foo.message.success</li>
 * <li>foo.message.common.success</li>
 * <li>action.generic.message.success</li>
 * <li>action.generic.message.common.success</li>
 * <li>action.generic.message.common</li>
 * </ol>
 * <p>
 * For &quot;failed&quot; ActionResult instances, simply replace <em>success</em> with <em>failure</em>
 * in the above property lookup keys.
 * <p>
 * All sections can also optionally be overridden using any of the {@code setXXXOverrideKey(String)} methods.
 * For example, for the example above, if {@code actRes.setMessageOverrideKey("custom")} was called, it would
 * lookup the following key for a successful action result:
 * <pre>
 * doFoo.message.success.custom=Foo {0} successfully completed; great job!
 * </pre>
 * which allows ActionResult instances to be customized before finally being remitted.
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
  /** Locale for formatting messages. */
  protected Locale locale;
  /** Whether action succeeded. */
  protected boolean succeeded;
  /** String representing resource key for action. */
  protected String actionKey;
  /** String representing onward navigation link. */
  protected String link;
  /** Object parameters for filling message (if needed). */
  protected Object[] messageParams;
  /** Object parameters for filling link text (if needed). */
  protected Object[] linkParams;
  /** Object parameters for filling comments (if needed). */
  protected Object[] commentParams;
  /** Object parameters for filling detail text (if needed). */
  protected Object[] detailParams;
  /** Whether detail text is preformatted (as for HTML pre tags). */
  protected boolean pre;
  /** Key denoting override for choosing message. */
  protected String messageOverrideKey;
  /** Key denoting override for choosing comment. */
  protected String commentOverrideKey;
  /** Key denoting override for choosing detail text. */
  protected String detailTextOverrideKey;
  /** Key denoting override for choosing link. */
  protected String linkTextOverrideKey;

  /**
   * Creates a new instance.
   * @param locale locale for message formatting
   * @param actionKey resource key for action
   * @param succeeded whether action succeeded
   * @param link URL link for onward navigation
   */
  public ActionResult(Locale locale, String actionKey, boolean succeeded, String link) {
    this.locale = locale;
    this.actionKey = actionKey;
    this.succeeded = succeeded;
    this.link = link;
  }

  /**
   * Creates a new instance.
   * @param actionKey resource key for action
   * @param succeeded whether action succeeded
   * @param link URL link for onward navigation
   */
  public ActionResult(String actionKey, boolean succeeded, String link) {
    this(Locale.getDefault(), actionKey, succeeded, link);
  }

  /**
   * Creates a new instance.
   * @param locale locale for message formatting
   * @param actionKey resource key for action
   * @param succeeded whether action succeeded
   */
  public ActionResult(Locale locale, String actionKey, boolean succeeded) {
    this(locale, actionKey, succeeded, null);
  }

  /**
   * Creates a new instance.
   * @param actionKey resource key for action
   * @param succeeded whether action succeeded
   */
  public ActionResult(String actionKey, boolean succeeded) {
    this(actionKey, succeeded, null);
  }

  public ActionResult setSucceeded(boolean succeeded) {
    this.succeeded = succeeded;
    return this;
  }

  public ActionResult setActionKey(String actionKey) {
    this.actionKey = actionKey;
    return this;
  }

  public ActionResult setLink(String link) {
    this.link = link;
    return this;
  }

  /**
   * Sets the parameters for all displayed fields.
   * This is a convenience method to set parameters for all displayed fields to the same values.
   * @param o object array for use in formatted text fields
   * @return this ActionResult instance (for method chaining support)
   */
  public ActionResult setParams(Object... o) {
    setMessageParams(o);
    setCommentParams(o);
    setDetailParams(o);
    setLinkParams(o);
    return this;
  }

  public ActionResult addParams(Object... o) {
    if (o != null) {
      addMessageParams(o);
      addCommentParams(o);
      addDetailParams(o);
      addLinkParams(o);
    }
    return this;
  }

  /**
   * Sets the parameters for displayed message text.
   * @param o object array for use in formatted message
   * @return this ActionResult instance (for method chaining support)
   */
  public ActionResult setMessageParams(Object... o) {
    this.messageParams = o;
    return this;
  }

  /**
   * Adds additional parameters for displayed message text.
   * @param o object array for use in formatted message
   * @return this ActionResult instance (for method chaining support)
   */
  public ActionResult addMessageParams(Object... o) {
    if (o != null) {
      Object[] x = new Object[messageParams.length + o.length];
      System.arraycopy(messageParams, 0, x, 0, messageParams.length);
      System.arraycopy(o, 0, x, messageParams.length, o.length);
      this.messageParams = x;
    }
    return this;
  }

  /**
   * Sets the property key for overriding the message defaults.
   * @param key property key for lookup
   */
  public ActionResult setMessageOverrideKey(String key) {
    this.messageOverrideKey = key;
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
   * Adds additional parameters for displayed link text.
   * @param o object array for use in formatted message
   * @return this ActionResult instance (for method chaining support)
   */
  public ActionResult addLinkParams(Object... o) {
    if (o != null) {
      Object[] x = new Object[linkParams.length + o.length];
      System.arraycopy(linkParams, 0, x, 0, linkParams.length);
      System.arraycopy(o, 0, x, linkParams.length, o.length);
      this.linkParams = x;
    }
    return this;
  }

  /**
   * Sets the property key for overriding the link defaults.
   * @param key property key for lookup
   */
  public ActionResult setLinkOverrideKey(String key) {
    this.linkTextOverrideKey = key;
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
   * Adds additional parameters for displayed comment text.
   * @param o object array for use in formatted message
   * @return this ActionResult instance (for method chaining support)
   */
  public ActionResult addCommentParams(Object... o) {
    if (o != null) {
      Object[] x = new Object[commentParams.length + o.length];
      System.arraycopy(commentParams, 0, x, 0, commentParams.length);
      System.arraycopy(o, 0, x, commentParams.length, o.length);
      this.commentParams = x;
    }
    return this;
  }

  /**
   * Sets the property key for overriding the comment defaults.
   * @param key property key for lookup
   */
  public ActionResult setCommentOverrideKey(String key) {
    this.commentOverrideKey = key;
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
   * Adds additional parameters for displayed detail text.
   * @param o object array for use in formatted message
   * @return this ActionResult instance (for method chaining support)
   */
  public ActionResult addDetailParams(Object... o) {
    if (o != null) {
      Object[] x = new Object[detailParams.length + o.length];
      System.arraycopy(detailParams, 0, x, 0, detailParams.length);
      System.arraycopy(o, 0, x, detailParams.length, o.length);
      this.detailParams = x;
    }
    return this;
  }

  /**
   * Sets the property key for overriding the detail text defaults.
   * @param key property key for lookup
   */
  public ActionResult setDetailTextOverrideKey(String key) {
    this.detailTextOverrideKey = key;
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
   * Returns the link (navigable URL) for this ActionResult.
   * @return String representing link
   */
  public String getLink() {
    return link;
  }

  /**
   * Creates a set of keys for resource lookups based on the specified parameters.
   * @param type type of resource to lookup (message/comment/detail/link)
   * @param actionKey action-key to lookup
   * @param ok whether action succeeded or not
   * @return list of keys to attempt for resource lookup
   */
  protected static ArrayList<String> createKeys(String type, String actionKey, boolean ok) {
    ArrayList<String> keys = new ArrayList<>();
    keys.add(String.format("%s.%s.%s", actionKey, type, ok ? "success" : "failure"));
    keys.add(String.format("%s.%s.common", actionKey, type));
    keys.add(String.format("action.generic.%s.%s", type, ok ? "success" : "failure"));
    keys.add(String.format("action.generic.%s.common", type));
    return keys;
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
   * Returns the message text for this ActionResult.
   * @param bundle resources for string lookup
   * @return String representing message text
   */
  public String getMessage(Properties bundle) {
    ArrayList<String> keys = createKeys("message", actionKey, succeeded);
    if (messageOverrideKey != null) {
      keys.add(0, String.format("%s.message.%s.%s", actionKey, succeeded ? "success" : "failure", messageOverrideKey));
      keys.add(1, String.format("%s.message.common.%s", actionKey, messageOverrideKey));
    }
    String text = findFirstMatchingNonNull(bundle, keys.toArray(new String[0]));
    if (text == null) {
      log.debug(String.format("Failed to find valid key for ActionResult %s; check the following keys:\n%s", "message", StringUtils.collateStrings(keys, "\n")));
      return null;
    }
    return messageParams == null ? text : StringUtils.format(locale, text, messageParams);
  }

  /**
   * Returns the link text for this ActionResult.
   * @param bundle resources for string lookup
   * @return String representing link text
   */
  public String getLinkText(Properties bundle) {
    ArrayList<String> keys = createKeys("link", actionKey, succeeded);
    if (linkTextOverrideKey != null) {
      keys.add(0, String.format("%s.link.%s.%s", actionKey, succeeded ? "success" : "failure", linkTextOverrideKey));
      keys.add(1, String.format("%s.link.common.%s", actionKey, linkTextOverrideKey));
    }
    String text = findFirstMatchingNonNull(bundle, keys.toArray(new String[0]));
    if (text == null) {
      log.debug(String.format("Failed to find valid key for ActionResult %s; check the following keys:\n%s", "link", StringUtils.collateStrings(keys, "\n")));
      return null;
    }
    return linkParams == null ? text : StringUtils.format(locale, text, linkParams);
  }

  /**
   * Returns the comment text for this ActionResult.
   * @param bundle resources for string lookup
   * @return String representing comment text
   */
  public String getComment(Properties bundle) {
    ArrayList<String> keys = createKeys("comment", actionKey, succeeded);
    if (commentOverrideKey != null) {
      keys.add(0, String.format("%s.comment.%s.%s", actionKey, succeeded ? "success" : "failure", commentOverrideKey));
      keys.add(1, String.format("%s.comment.common.%s", actionKey, commentOverrideKey));
    }
    String text = findFirstMatchingNonNull(bundle, keys.toArray(new String[0]));
    if (text == null) {
      log.debug(String.format("Failed to find valid key for ActionResult %s; check the following keys:\n%s", "comment", StringUtils.collateStrings(keys, "\n")));
      return null;
    }
    // If resource text is just a placeholder for parameter, return null if param doesn't exist,
    // otherwise return resource text.
    return commentParams == null ? ("{0}".equals(text) ? null : text) : StringUtils.format(locale, text, commentParams);
  }

  /**
   * Returns the detail text for this ActionResult.
   * @param bundle resources for string lookup
   * @return String representing detail text
   */
  public String getDetailText(Properties bundle) {
    ArrayList<String> keys = createKeys("detail", actionKey, succeeded);
    if (detailTextOverrideKey != null) {
      keys.add(0, String.format("%s.detail.%s.%s", actionKey, succeeded ? "success" : "failure", detailTextOverrideKey));
      keys.add(1, String.format("%s.detail.common.%s", actionKey, detailTextOverrideKey));
    }
    String text = findFirstMatchingNonNull(bundle, keys.toArray(new String[0]));
    if (text == null) {
      log.debug(String.format("Failed to find valid key for ActionResult %s; check the following keys:\n%s", "detailText", StringUtils.collateStrings(keys, "\n")));
      return null;
    }
    return detailParams == null ? ("{0}".equals(text) ? null : text) : StringUtils.format(locale, text, detailParams);
  }
}
