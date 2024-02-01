/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.mmutil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Utility class to allow convenient building of lists by method chaining.
 * This class is just a wrapper around a {@code List} implementation which
 * provides convenience methods to quickly create/edit lists.
 * Lists can also be easily created using the standard method
 * {@link java.util.Arrays#asList(java.lang.Object...)},
 * so this technique is just a added option.
 *
 * @author Giles Winstanley
 * @param <E> list element type
 */
public final class ListHelper<E> {
  /** List to be built. */
  private final List<E> list;

  /**
   * Creates an instance using the specified list implementation.
   * @param c {@code Class} type to use for list implementation
   */
  @SuppressWarnings("unchecked")
  private <T extends List> ListHelper(Class<T> c) {
    try {
      if (List.class.isAssignableFrom(c))
        this.list = (List<E>)c.newInstance();
      else
        throw new IllegalArgumentException("Invalid class type specified; not a List implementation");
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  /**
   * Creates an instance using {@code ArrayList} as the list implementation.
   */
  private ListHelper() {
    list = new ArrayList<E>();
  }

  /**
   * Creates an instance using the specified list.
   * @param list list to use
   */
  private ListHelper(List<E> list) {
    this.list = list;
  }

  /**
   * Creates a new instance using the specified list implementation,
   * and with the specified initial list item.
   * @param <T> {@code List} implementation to use
   * @param <E> list item type
   * @param c {@code Class} of {@code List} implementation to use
   * @param o initial item in list
   * @return {@code ListHelper} instance ready for further use.
   */
  public static <T extends List, E> ListHelper<E> create(Class<T> c, E o) {
    return new ListHelper<E>(c).add(o);
  }

  /**
   * Creates a new instance using the specified list implementation,
   * and with the specified initial list item.
   * @param <T> {@code List} implementation to use
   * @param <E> list item type
   * @param c {@code Class} of {@code List} implementation to use
   * @return {@code ListHelper} instance ready for further use.
   */
  public static <T extends List, E> ListHelper<E> create(Class<T> c) {
    return new ListHelper<E>(c);
  }

  /**
   * Creates a new instance (using an {@code ArrayList} implementation),
   * and with the specified initial list item.
   * @param <E> list item type
   * @param o initial item in list
   * @return {@code ListHelper} instance ready for further use.
   */
  public static <E> ListHelper<E> create(E o) {
    return new ListHelper<E>().add(o);
  }

  /**
   * Creates a new instance (using an {@code ArrayList} implementation).
   * @param <E> list item type
   * @return {@code ListHelper} instance ready for further use.
   */
  public static <E> ListHelper<E> create() {
    return new ListHelper<E>();
  }

  /**
   * Creates a new instance using the specified list.
   * @param <E> list item type
   * @param list list to use for
   * @return {@code ListHelper} instance ready for further use.
   */
  public static <E> ListHelper<E> use(List<E> list) {
    return new ListHelper<E>(list);
  }

  /**
   * Adds an item to the list.
   * @param o item to add to the list.
   * @return {@code ListHelper} instance ready for further use.
   */
  public ListHelper<E> add(E o) {
    list.add(o);
    return this;
  }

  /**
   * Adds an item to the list at the specified index.
   * @param i index of item to set
   * @param o item to add to the list.
   * @return {@code ListHelper} instance ready for further use.
   */
  public ListHelper<E> add(int i, E o) {
    list.add(i, o);
    return this;
  }

  /**
   * Sets the item at the specified index in the list.
   * @param i index of item to set
   * @param o item to add to the list.
   * @return {@code ListHelper} instance ready for further use.
   */
  public ListHelper<E> set(int i, E o) {
    list.set(i, o);
    return this;
  }

  /**
   * Adds the specified collection of items to the list.
   * @param c collection of items to add to the list.
   * @return {@code ListHelper} instance ready for further use.
   */
  public ListHelper<E> addAll(Collection<E> c) {
    list.addAll(c);
    return this;
  }

  /**
   * @return The underlying list instance.
   */
  public List<E> asList() {
    return list;
  }

  /**
   * Returns a single delimited string comprising the string
   * representations of the constituent items.
   * @param <E> list element type
   * @param delim string delimiter to use between list items
   * @return String comprising list contents sequentially concatenated with specified delimiter
   */
  public <E> String toDelimitedString(String delim) {
    return toDelimitedString(this.asList(), delim);
  }

  /**
   * Converts a list of items to a single delimited string comprising the string
   * representations of the constituent items.
   * @param <E> list element type
   * @param list list of objects to convert
   * @param delim string delimiter to use between list items
   * @return String comprising list contents sequentially concatenated with specified delimiter
   */
  public static <E> String toDelimitedString(List<E> list, String delim) {
    if (list == null)
      throw new NullPointerException();
    if (list.isEmpty())
      return null;
    StringBuilder sb = new StringBuilder();
    sb.append(list.get(0).toString());
    for (int i = 1; i < list.size(); i++) {
      sb.append(delim);
      sb.append(list.get(i).toString());
    }
    return sb.toString();
  }

  /**
   * Converts a list of items to a single delimited string comprising the string
   * representations of the constituent items, and also quotes items containing
   * whitespace or quotes.
   * @param <E> list element type
   * @param list list of objects to convert
   * @param delim string delimiter to use between list items
   * @return String comprising quoted list contents sequentially concatenated with specified delimiter
   */
  public static <E> String toDelimitedStringQuoted(List<E> list, String delim) {
    if (list == null)
      throw new NullPointerException();
    if (list.isEmpty())
      return null;
    StringBuilder sb = new StringBuilder();
    sb.append(quoteString(list.get(0).toString()));
    for (int i = 1; i < list.size(); i++) {
      sb.append(delim);
      sb.append(quoteString(list.get(i).toString()));
    }
    return sb.toString();
  }

  private static String quoteString(String s) {
    assert s != null;
    if (s.contains(" "))
      return new StringBuilder().append("\"").append(s.replace("\"", "\\\"")).append("\"").toString();
    return s.replace("\"", "\\\"");
  }
}
