package org.ecocean.mmutil;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Giles Winstanley
 */
public class DataUtilitiesTest {

  public DataUtilitiesTest() {
  }

  @Test
  public void testCreateUniqueEncounterId() {
    // Test that all new IDs are unique when created in a tight loop.
    // Non-unique IDs should be collapsed in set, reducing its size.
    int COUNT = 500;
    Random rnd = new Random();
    Set<String> set = new HashSet<String>();
    for (int i = 0; i < COUNT; i++) {
      set.add(DataUtilities.createUniqueEncounterId());
      // Sleep a short time to simulate a little data processing.
      try {
        Thread.sleep(1L + rnd.nextInt(10));
      } catch (InterruptedException ex) {
        ex.printStackTrace();
      }
    }
    assertEquals(COUNT, set.size());
  }

}
