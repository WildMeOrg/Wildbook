package org.ecocean;

import java.text.MessageFormat;
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

  public ActionResult_Individual(String actionKey, boolean succeeded, String link) {
    super(actionKey, succeeded, link);
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
    String[] keys = {
            String.format("%s.link.%s", actionKey, succeeded ? "success" : "failure"),
            String.format("%s.link.common", actionKey),
            String.format("individual.link.common", actionKey)
    };
    String text = findFirstMatchingNonNull(bundle, keys);
    return linkParams == null ? text : MessageFormat.format(text, linkParams);
  }
}
