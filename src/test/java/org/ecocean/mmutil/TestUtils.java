package org.ecocean.mmutil;

import java.util.Locale;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Miscellaneous testing utility methods.
 *
 * @author Giles Winstanley
 */
public final class TestUtils {
  /** Filename extensions used for generating random filenames. */
  private static final String[] EXT = { "jpg", "jpeg", "png", "gif" };
  /** Valid characters used for generating random filenames. */
  private static final char[] CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789^&'@{}[],$=!-#()%.+~_".toCharArray();

  public TestUtils() {
  }

  public static String createRandomFilename(int min, int max) {
    if (min < 1)
      throw new IllegalArgumentException("min must be > 0");
    if (min > max)
      throw new IllegalArgumentException("min > max");
    Random rnd = new Random();
    int len = (min == max) ? min : min + rnd.nextInt(max - min);
    StringBuilder sb = new StringBuilder(len + 4);
    sb.append(CHARS[rnd.nextInt(62)]);
    for (int i = 0; i < len - 1; i++)
      sb.append(CHARS[rnd.nextInt(CHARS.length)]);
    sb.append('.');
    int ext = rnd.nextInt(EXT.length);
    sb.append(rnd.nextDouble() < 0.6d ? EXT[ext] : EXT[ext].toUpperCase(Locale.US));
    return sb.toString();
  }

  @Test
  public void testCreateRandomFilename() {
    int COUNT = 500;
    Random rnd = new Random(System.currentTimeMillis());
    for (int i = 0; i < COUNT; i++) {
      int min = 1 + rnd.nextInt(5);
      int max = min + rnd.nextInt(5);
      String fn = createRandomFilename(min, max);
      int len = fn.lastIndexOf(".");
      assertTrue(String.format("%s, min=%d, max=%d, len=%d", fn, min, max, len), len >= min);
      assertTrue(String.format("%s, min=%d, max=%d, len=%d", fn, min, max, len), len <= max);
    }
  }
}
