/*
 *   Copyright 2021 Goldman Sachs.
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 */

package com.gs.ep.docknight.model.transformer.tabledetection.process;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;

/**
 * Typesafe enumeration implementation that allows for subclassing to provide trees of
 * enumerations.
 * <p/>
 * V represents the type of value that will be stored against this key.
 */
public abstract class ScratchpadKey<V> implements Serializable {

  private static final ConcurrentMap<ScratchpadKey.ScratchpadKeyIdentifier, ScratchpadKey<?>> ELEMENTS_BY_IDENTIFIER =
      new ConcurrentHashMap<ScratchpadKey.ScratchpadKeyIdentifier, ScratchpadKey<?>>();

  private final ScratchpadKey.ScratchpadKeyIdentifier identifier;
  private final String name;

  /**
   * Access modifier should be {@code private} in all derived classes
   *
   * @param name In order to co-exist with legacy code using the old String-based mechanism, a name
   * may be supplied that maps to the old style {@code String} key. This will be used to identify
   * data on the Scratchpad should the {@code ScratchpadKey} itself fail (e.g. because the required
   * data was stored on the scratchpad using a {@code String}). <p/><p/> This name is also used by
   * the {@code toString()} method so it's worth providing one even if interoperability with legacy
   * code is not a requirement.
   */
  protected ScratchpadKey(String name) {
    this.name = name == null ? "" : name;
    this.identifier = ScratchpadKey.IdentifierGenerator
        .getNextIdentifier(this.getClass());
    ELEMENTS_BY_IDENTIFIER.put(this.identifier, this);
  }

  /**
   * @return The name of this {@code ScratchpadKey}.
   */
  public String getName() {
    return this.name;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + " [" + this.getName() + ']';
  }

  /**
   * Don't allow any custom behaviour here
   *
   * @param other The object to be compared with
   * @return {@code true} if this object is the same as the object passed; {@code false} otherwise.
   */
  @Override
  public final boolean equals(Object other) {
    return super.equals(other);
  }

  /**
   * Don't allow any custom behaviour here
   *
   * @return a hash code
   */
  @Override
  public final int hashCode() {
    return super.hashCode();
  }

  /**
   * Throws CloneNotSupportedException.  This guarantees that enums are never cloned
   *
   * @return (never returns)
   * @throws CloneNotSupportedException always
   */
  @Override
  protected final Object clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();
  }

  // Generates unique identifiers for ScratchpadKeys.
  private static final class IdentifierGenerator {

    private static final MutableMap<Class, Integer> ORDINALS_BY_CLASS = UnifiedMap.newMap();

    private IdentifierGenerator() {
      throw new AssertionError("Suppress default constructor for noninstantiability");
    }

    // Gets the next identifier for the supplied class.
    static synchronized ScratchpadKey.ScratchpadKeyIdentifier getNextIdentifier(
        Class aClass) {
      Integer nextOrdinal = ORDINALS_BY_CLASS.get(aClass);
      nextOrdinal =
          nextOrdinal == null ? Integer.valueOf(0) : Integer.valueOf(nextOrdinal.intValue() + 1);
      ORDINALS_BY_CLASS.put(aClass, nextOrdinal);
      return new ScratchpadKey.ScratchpadKeyIdentifier(
          aClass, nextOrdinal);
    }
  }

  // Uniquely identifies a Scratchpad key. A unique identifier is required in order for us to be able read-resolve
  // reliably.
  private static final class ScratchpadKeyIdentifier implements Serializable {

    private final Class aClass;
    private final Integer ordinal;

    private ScratchpadKeyIdentifier(Class aClass, Integer ordinal) {
      this.aClass = aClass;
      this.ordinal = ordinal;
    }

    @Override
    public int hashCode() {
      return 199 * this.aClass.hashCode() + this.ordinal.intValue();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ScratchpadKey.ScratchpadKeyIdentifier)) {
        return false;
      }
      ScratchpadKey.ScratchpadKeyIdentifier other = (ScratchpadKey.ScratchpadKeyIdentifier) obj;
      return this.aClass.equals(other.aClass)
          && this.ordinal.equals(other.ordinal);
    }

    @Override
    public String toString() {
      return this.aClass.getName() + " - " + this.ordinal;
    }
  }
}


