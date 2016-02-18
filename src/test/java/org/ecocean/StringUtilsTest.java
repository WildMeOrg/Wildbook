package org.ecocean;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class StringUtilsTest {

  public StringUtilsTest() {
  }

  @Test
  public void testCollateStrings() {
    Collection<String> c = new ArrayList<String>(){{
      add("foo");
      add("bar");
      add("baz");
    }};
    try {
      StringUtils.collateStrings((Collection<String>)null, (ResourceBundle)null, null, null, null, null);
      fail();
    }
    catch (NullPointerException ex) { /* expected */ }
    assertEquals("", StringUtils.collateStrings(Collections.EMPTY_LIST, (ResourceBundle)null, null, null, null, null));
    assertEquals("foobarbaz", StringUtils.collateStrings(c, (ResourceBundle)null, null, null, null, null));
    assertEquals("foo,bar,baz", StringUtils.collateStrings(c, (ResourceBundle)null, null, null, null, ","));
    assertEquals("foo, bar, baz", StringUtils.collateStrings(c, (ResourceBundle)null, null, null, null, ", "));
    assertEquals("<b>foo</b>, <b>bar</b>, <b>baz</b>", StringUtils.collateStrings(c, (ResourceBundle)null, null, "<b>", "</b>", ", "));
    TestBundle res = new TestBundle();
    assertEquals("<b>foo</b>, <b>bar</b>, <b>baz</b>", StringUtils.collateStrings(c, res, "loc", "<b>", "</b>", ", "));
    try {
      StringUtils.collateStrings(c, res, "location.%s", "<b>", "</b>", ", ");
      fail();
    }
    catch (MissingResourceException ex) { /* expected */ }
    assertEquals("<b>Foo</b>, <b>Bar</b>, <b>Baz</b>", StringUtils.collateStrings(c, res, "loc.%s", "<b>", "</b>", ", "));
  }

  @Test
  public void testFormat() {
    String s = "{0}";
    assertEquals("1,234", StringUtils.format(Locale.US, s, 1234));
    assertEquals("1.234", StringUtils.format(new Locale("es"), s, 1234));
    assertEquals("1.234", StringUtils.format(Locale.GERMAN, s, 1234));
  }

  private final class TestBundle extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {
              {"loc.foo", "Foo"},
              {"loc.bar", "Bar"},
              {"loc.baz", "Baz"}
      };
    }
  }
}