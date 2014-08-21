package org.ecocean.mmutil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Giles Winstanley
 */
public class ListHelperTest {
  public ListHelperTest() {
  }

  @Test
  public void testCreate_Class_GenericType() throws Exception {
    ListHelper<String> x = ListHelper.create(ArrayList.class, "test");
    Field list = ListHelper.class.getDeclaredField("list");
    list.setAccessible(true);
    assertTrue(list.get(x) != null);
    assertTrue(list.get(x).getClass().equals(ArrayList.class));
    assertTrue(((List)list.get(x)).size() == 1);
    assertTrue(((List)list.get(x)).get(0).getClass().equals(String.class));
    assertTrue(((List)list.get(x)).get(0).equals("test"));

    ListHelper<Integer> y = ListHelper.create(Vector.class, Integer.valueOf(3));
    list = ListHelper.class.getDeclaredField("list");
    list.setAccessible(true);
    assertTrue(list.get(y) != null);
    assertTrue(list.get(y).getClass().equals(Vector.class));
    assertTrue(((List)list.get(y)).size() == 1);
    assertTrue(((List)list.get(y)).get(0).getClass().equals(Integer.class));
    assertTrue(((List)list.get(y)).get(0).equals(Integer.valueOf(3)));

    ListHelper<String> z = ListHelper.create(LinkedList.class, "test");
    list = ListHelper.class.getDeclaredField("list");
    list.setAccessible(true);
    assertTrue(list.get(z) != null);
    assertTrue(list.get(z).getClass().equals(LinkedList.class));
    assertTrue(((List)list.get(z)).size() == 1);
    assertTrue(((List)list.get(z)).get(0).getClass().equals(String.class));
    assertTrue(((List)list.get(z)).get(0).equals("test"));
  }

  @Test
  public void testCreate_Class() throws Exception {
    ListHelper<String> x = ListHelper.create(ArrayList.class);
    Field list = ListHelper.class.getDeclaredField("list");
    list.setAccessible(true);
    assertTrue(list.get(x) != null);
    assertTrue(list.get(x).getClass().equals(ArrayList.class));
    assertTrue(((List)list.get(x)).isEmpty());

    ListHelper<Integer> y = ListHelper.create(Vector.class);
    list = ListHelper.class.getDeclaredField("list");
    list.setAccessible(true);
    assertTrue(list.get(y) != null);
    assertTrue(list.get(y).getClass().equals(Vector.class));
    assertTrue(((List)list.get(y)).isEmpty());

    ListHelper<String> z = ListHelper.create(LinkedList.class);
    list = ListHelper.class.getDeclaredField("list");
    list.setAccessible(true);
    assertTrue(list.get(z) != null);
    assertTrue(list.get(z).getClass().equals(LinkedList.class));
    assertTrue(((List)list.get(z)).isEmpty());
  }

  @Test
  public void testCreate_GenericType() throws Exception {
    ListHelper<String> x = ListHelper.create("test");
    List<String> list = x.asList();
    assertTrue(list.size() == 1);
    assertTrue(list.get(0).getClass().equals(String.class));
    assertTrue(list.get(0).equals("test"));
  }

  @Test
  public void testCreate_0args() {
    ListHelper<String> x = ListHelper.create();
    assertTrue(x.asList().isEmpty());
  }

  @Test
  public void testUse() {
    List<Integer> list = new LinkedList<Integer>();
    list.add(1);
    list.add(2);
    list.add(3);
    ListHelper<Integer> x = ListHelper.use(list);
    assertTrue(x.asList().size() == 3);
  }

  @Test
  public void testAdd_GenericType() {
    ListHelper<Integer> x = ListHelper.create();
    assertTrue(x.asList().isEmpty());
    x.add(Integer.valueOf(1));
    assertTrue(x.asList().size() == 1);
    x.add(2);
    assertTrue(x.asList().size() == 2);
    assertTrue(x.asList().get(0).equals(1));
    assertTrue(x.asList().get(1).equals(2));
  }

  @Test
  public void testAdd_int_GenericType() {
    ListHelper<Integer> x = ListHelper.create();
    x.add(1);
    x.add(2);
    assertTrue(x.asList().size() == 2);
    assertTrue(x.asList().get(0).equals(1));
    assertTrue(x.asList().get(1).equals(2));
    x.add(1, 3);
    assertTrue(x.asList().size() == 3);
    assertTrue(x.asList().get(0).equals(1));
    assertTrue(x.asList().get(1).equals(3));
    assertTrue(x.asList().get(2).equals(2));
  }

  @Test
  public void testSet() {
    ListHelper<Integer> x = ListHelper.create();
    x.add(1);
    x.add(2);
    assertTrue(x.asList().size() == 2);
    assertTrue(x.asList().get(0).equals(1));
    assertTrue(x.asList().get(1).equals(2));
    x.set(1, 3);
    assertTrue(x.asList().size() == 2);
    assertTrue(x.asList().get(0).equals(1));
    assertTrue(x.asList().get(1).equals(3));
  }

  @Test
  public void testAddAll() {
    List<Integer> list = new ArrayList<Integer>();
    list.add(1);
    list.add(2);
    list.add(3);

    ListHelper<Integer> x = ListHelper.create();
    x.addAll(list);
    assertTrue(x.asList().size() == 3);
    assertTrue(x.asList().get(0).equals(1));
    assertTrue(x.asList().get(1).equals(2));
    assertTrue(x.asList().get(2).equals(3));
    x.addAll(list);
    assertTrue(x.asList().size() == 6);
    assertTrue(x.asList().get(0).equals(1));
    assertTrue(x.asList().get(1).equals(2));
    assertTrue(x.asList().get(2).equals(3));
    assertTrue(x.asList().get(3).equals(1));
    assertTrue(x.asList().get(4).equals(2));
    assertTrue(x.asList().get(5).equals(3));
  }

  @Test
  public void testToDelimitedString_List_String() {
    List<Integer> x = new ArrayList<Integer>();
    x.add(1);
    x.add(2);
    x.add(3);
    assertEquals("1 2 3", ListHelper.toDelimitedString(x, " "));
    assertEquals("1,2,3", ListHelper.toDelimitedString(x, ","));
    assertEquals("1, 2, 3", ListHelper.toDelimitedString(x, ", "));

    List<String> y = new ArrayList<String>();
    y.add("Hello");
    y.add("World");
    assertEquals("Hello World", ListHelper.toDelimitedString(y, " "));
    assertEquals("Hello  World", ListHelper.toDelimitedString(y, "  "));
    assertEquals("Hello/World", ListHelper.toDelimitedString(y, "/"));
    assertEquals("Hello_World", ListHelper.toDelimitedString(y, "_"));

    y.clear();
    y.add("Hello there");
    y.add("\"World\"");
    assertEquals("Hello there \"World\"", ListHelper.toDelimitedString(y, " "));
    assertEquals("Hello there  \"World\"", ListHelper.toDelimitedString(y, "  "));
    assertEquals("Hello there/\"World\"", ListHelper.toDelimitedString(y, "/"));
    assertEquals("Hello there_\"World\"", ListHelper.toDelimitedString(y, "_"));
  }

  @Test
  public void testToDelimitedStringQuoted_List_String() {
    List<Integer> x = new ArrayList<Integer>();
    x.add(1);
    x.add(2);
    x.add(3);
    assertEquals("1 2 3", ListHelper.toDelimitedStringQuoted(x, " "));
    assertEquals("1,2,3", ListHelper.toDelimitedStringQuoted(x, ","));
    assertEquals("1, 2, 3", ListHelper.toDelimitedStringQuoted(x, ", "));

    List<String> y = new ArrayList<String>();
    y.add("Hello");
    y.add("World");
    assertEquals("Hello World", ListHelper.toDelimitedStringQuoted(y, " "));
    assertEquals("Hello  World", ListHelper.toDelimitedStringQuoted(y, "  "));
    assertEquals("Hello/World", ListHelper.toDelimitedStringQuoted(y, "/"));
    assertEquals("Hello_World", ListHelper.toDelimitedStringQuoted(y, "_"));

    y.clear();
    y.add("Hello there");
    y.add("\"World\"");
    assertEquals("\"Hello there\" \\\"World\\\"", ListHelper.toDelimitedStringQuoted(y, " "));
    assertEquals("\"Hello there\"  \\\"World\\\"", ListHelper.toDelimitedStringQuoted(y, "  "));
    assertEquals("\"Hello there\"/\\\"World\\\"", ListHelper.toDelimitedStringQuoted(y, "/"));
    assertEquals("\"Hello there\"_\\\"World\\\"", ListHelper.toDelimitedStringQuoted(y, "_"));
  }
}