package org.ecocean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class SpamCheckerTest {

  private SpamChecker sc;

  @Before
  public void setUp() throws IOException {
    sc = new SpamChecker();
  }

  @After
  public void tearDown() {
  }

//  @Test
  public void testIsSpam() {
  }

  @Test
  public void testContainsDefiniteSpam() {
    assertFalse(sc.containsDefiniteSpam(null));
    assertTrue(sc.containsDefiniteSpam("porn"));
    assertTrue(sc.containsDefiniteSpam("foo porn bar"));
    assertTrue(sc.containsDefiniteSpam("foo#pornographybar"));
    assertTrue(sc.containsDefiniteSpam("href"));
    assertTrue(sc.containsDefiniteSpam("href="));
    assertTrue(sc.containsDefiniteSpam("<a href"));
    assertTrue(sc.containsDefiniteSpam("&lt;a href"));
  }

  @Test
  public void testContainsPossibleSpam() {
    assertFalse(sc.containsPossibleSpam(null));
    assertTrue(sc.containsPossibleSpam("buy NEW CLOTHEShttp://www.ertyuio.com/gucci fooBar"));
    assertTrue(sc.containsPossibleSpam("buy NEW CLOTHEShttps://www.ertyuio.com/gucci fooBar"));
    assertFalse(sc.containsPossibleSpam("when including a website, don't add http at the start!"));
  }
}