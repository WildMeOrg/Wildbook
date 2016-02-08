package org.ecocean;

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
public class ActionResult_Encounter extends ActionResult {

  public ActionResult_Encounter(Locale locale, String actionKey, boolean succeeded, String link) {
    super(locale, actionKey, succeeded, link);
  }

  public ActionResult_Encounter(String actionKey, boolean succeeded, String link) {
    super(actionKey, succeeded, link);
  }

  public ActionResult_Encounter(Locale locale, String actionKey, boolean succeeded) {
    super(locale, actionKey, succeeded);
  }

  public ActionResult_Encounter(String actionKey, boolean succeeded) {
    super(actionKey, succeeded);
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
            String.format("encounter.link.common", actionKey)
    };
    String text = findFirstMatchingNonNull(bundle, keys);
    return linkParams == null ? text : StringUtils.format(locale, text, linkParams);
  }
}
