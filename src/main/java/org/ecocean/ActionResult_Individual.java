package org.ecocean;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Extension to simple ActionResult which provides additional functionality:
 * <ul>
 *   <li>Customized &quot;return to individual&quot; link.</li>
 * </ul>
 *
 * @author Giles Winstanley
 */
public class ActionResult_Individual extends ActionResult {

  public ActionResult_Individual(Locale locale, String actionKey, boolean succeeded, String link) {
    super(locale, actionKey, succeeded, link);
  }

  public ActionResult_Individual(String actionKey, boolean succeeded, String link) {
    super(actionKey, succeeded, link);
  }

  public ActionResult_Individual(Locale locale, String actionKey, boolean succeeded) {
    super(locale, actionKey, succeeded);
  }

  public ActionResult_Individual(String actionKey, boolean succeeded) {
    super(actionKey, succeeded);
  }

  /**
   * Returns the link text for this ActionResult.
   * @param bundle resources for string lookup
   * @return String representing link text
   */
  public String getLinkText(Properties bundle) {
    List<String> keys = createKeys("link", actionKey, succeeded);
    keys.add(2, "individual.link.common");
    if (linkTextOverrideKey != null) {
      keys.add(keys.get(0) + "." + linkTextOverrideKey);
    }
    String text = findFirstMatchingNonNull(bundle, keys.toArray(new String[0]));
    return linkParams == null ? text : StringUtils.format(locale, text, linkParams);
  }
}
