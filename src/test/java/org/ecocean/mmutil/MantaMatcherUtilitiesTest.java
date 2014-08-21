package org.ecocean.mmutil;

import java.io.File;
import java.util.Map;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MantaMatcherUtilitiesTest {
  public MantaMatcherUtilitiesTest() {
  }

  @Test @Ignore
  public void testCheckMatcherFilesExist() {
    // TODO: implement
  }

  @Test @Ignore
  public void testCheckMatcherResultsFilesExist() {
    // TODO: implement
  }

  @Test
  public void testGetMatcherFilesMap() {
    File dir = new File(System.getProperty("java.io.tmpdir"));

    File f = new File(dir, "TestFile.jpg");
    Map<String, File> map = MantaMatcherUtilities.getMatcherFilesMap(f);
    assertNotNull(map);
    assertEquals(5, map.size());
    assertEquals(f, map.get("O"));
    assertEquals(new File(dir, "TestFile_CR.jpg"), map.get("CR"));
    assertEquals(new File(dir, "TestFile_EH.jpg"), map.get("EH"));
    assertEquals(new File(dir, "TestFile_FT.jpg"), map.get("FT"));
    assertEquals(new File(dir, "TestFile.FEAT"), map.get("FEAT"));

    f = new File(dir, "Test2&(0)_.JPEG");
    map = MantaMatcherUtilities.getMatcherFilesMap(f);
    assertNotNull(map);
    assertEquals(5, map.size());
    assertEquals(f, map.get("O"));
    assertEquals(new File(dir, "Test2&(0)__CR.JPEG"), map.get("CR"));
    assertEquals(new File(dir, "Test2&(0)__EH.JPEG"), map.get("EH"));
    assertEquals(new File(dir, "Test2&(0)__FT.JPEG"), map.get("FT"));
    assertEquals(new File(dir, "Test2&(0)_.FEAT"), map.get("FEAT"));

    f = new File(dir, "Test3");
    try {
      map = MantaMatcherUtilities.getMatcherFilesMap(f);
      fail("Expected IllegalArgumentException");
    }
    catch (IllegalArgumentException ex) {
    }

    for (int i = 0; i < 30; i++) {
      String fn = TestUtils.createRandomFilename(1, 25);
//      System.out.println("Testing filename: " + fn);
      f = new File(dir, fn);
      map = MantaMatcherUtilities.getMatcherFilesMap(f);
      assertNotNull(map);
      assertEquals(5, map.size());
      assertEquals(f, map.get("O"));
      assertNotNull(map.get("CR"));
      assertNotNull(map.get("EH"));
      assertNotNull(map.get("FT"));
      assertNotNull(map.get("FEAT"));
      assertNotSame(f, map.get("CR"));
      assertNotSame(f, map.get("EH"));
      assertNotSame(f, map.get("FT"));
      assertNotSame(f, map.get("FEAT"));
      assertTrue(map.get("CR").getName().contains("_CR."));
      assertTrue(map.get("EH").getName().contains("_EH."));
      assertTrue(map.get("FT").getName().contains("_FT."));
      assertTrue(map.get("FEAT").getName().endsWith(".FEAT"));
    }
  }

  @Test @Ignore
  public void testGetResultsHtml() {
    // TODO: implement
  }
}