package org.ecocean;

/**
 * @author Giles Winstanley
 */
public final class SpamChecker {
  /** Enumeration of possible spam detection outcomes. */
  public enum Result { SPAM, POSSIBLE_SPAM, NOT_SPAM };

  /**
   * Checks if the specified {@code Encounter} is a spam submission, based on the text content submitted.
   * Note: the specified encounter will generally not have any stored media files when passed to this method,
   * so spam determination should only be via text fields.
   * @param enc encounter to check
   * @return true if spam detected, false otherwise
   */
  public static Result isSpam(Encounter enc) {
    // Produces same spam-detection outcome as original embedded version.
    if (containsDefiniteSpam(enc.getSubmitterName()) || containsDefiniteSpam(enc.getSubmitterPhone()))
      return Result.SPAM;
    if (containsDefiniteSpam(enc.getPhotographerName()) || containsDefiniteSpam(enc.getPhotographerPhone()))
      return Result.SPAM;
    if (containsDefiniteSpam(enc.getLocation()) || containsDefiniteSpam(enc.getComments()) || containsDefiniteSpam(enc.getBehavior()))
      return Result.SPAM;

    return Result.NOT_SPAM;
  }

  /**
   * Checks if the specified text contains anything considered spam.
   * These detections are absolute, and should only be specified for definite spam text.
   * @param text text to check
   * @return true if spam words detected, false otherwise
   */
  public static boolean containsDefiniteSpam(String text) {
    if (text == null)
      return false;
    String s = text.toLowerCase();
    if (s.contains("porn") || s.contains("href"))
      return true;
//    if (s.contains("[url]") || s.contains("url="))
//      return true;
    return false;
  }
}
