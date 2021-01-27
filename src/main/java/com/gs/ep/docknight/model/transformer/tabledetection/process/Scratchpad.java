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

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.multimap.Multimap;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Scratchpad {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(Scratchpad.class);

  private Map pad;

  private Set retrievedKeys;
  private Set storedKeys;

  /**
   * Creates an empty scratchpad
   */
  public Scratchpad() {
    this.initialize();
  }

  /**
   * Clears and re-initializes the scratchpad.
   */
  public void initialize() {
    this.pad = UnifiedMap.newMap();
    this.retrievedKeys = UnifiedSet.newSet();
    this.storedKeys = UnifiedSet.newSet();
  }

  /**
   * Adds a data item to the scratchpad using a {@link ScratchpadKey}. This is the preferred
   * method.
   *
   * @param key The key used to identify a data item on the scratchpad.
   * @param value The data item to be added to the scratchpad.
   */
  public synchronized <V> void store(ScratchpadKey<V> key, V value) {
    LOGGER.debug("Adding the following key to the scratchpad: {}", key);
    // If a string-based entry exists with the name of this key, then remove it so that we are effectively
    // overwriting the value.
    String name = this.findStringByKey(key);
    if (name != null) {
      this.remove(key.getName());
    }
    this.addStoredKey(key);
    this.pad.put(key, value);
  }

  /**
   * Retrieves a data item from the scratchpad using a {@link ScratchpadKey}. This is the preferred
   * method. If the data item can't be located, this method will fall-back to a String based lookup
   * using the name of the key.
   *
   * @param key The key used to look up a data item in the scratchpad.
   * @return The data item retrieved or null. The data item's type will have been determined by the
   * key used to retrieve it.
   */
  public synchronized <V> V retrieve(ScratchpadKey<V> key) {
    LOGGER.debug("Retrieving the following key from the scratchpad: {}", key);
    this.addRetrievedKey(key);
    V result = (V) this.pad.get(key);
    if (result == null) {
      // Fall-back to a String search
      LOGGER.debug("Falling back to String key: {}", key.getName());
      result = (V) this.pad.get(key.getName());
    }
    return result;
  }

  /**
   * Retrieves a data item from the scratchpad using a {@link ScratchpadKey}. This is the preferred
   * method. If the data item can't be located, this method will fall-back to a String based lookup
   * using the name of the key. If that too fails, then the supplied default value will be
   * returned.
   *
   * @param key The key used to look up a data item in the scratchpad.
   * @return The data item retrieved or the default value supplied. The data item's type will have
   * been determined by the key used to retrieve it.
   */
  public synchronized <V> V retrieve(ScratchpadKey<V> key, V defaultValue) {
    V result = this.retrieve(key);
    return result == null ? defaultValue : result;
  }

  /**
   * Convenience method to retrieve a primitive boolean value from a Boolean data item. For general
   * information on retrieval, see {@link #retrieve}
   * <p/>
   * Note this method duplicates the special behaviour of the legacy retrieveBoolean class in order
   * to maintain backward compatibility.
   *
   * @param key The key to retrieve data for
   * @return The boolean value of the retrieved Boolean data item
   */
  public boolean retrieveBoolean(ScratchpadKey<Boolean> key) {
    return this.retrieve(key, Boolean.FALSE).booleanValue();
  }

  /**
   * Convenience method to retrieve a primitive int value from an Integer data item. For general
   * information on retrieval, see {@link #retrieve}
   *
   * @param key The key to retrieve data for
   * @return The int value of the retrieved Integer data item
   */
  public int retrieveInt(ScratchpadKey<Integer> key) {
    return this.retrieve(key).intValue();
  }

  /**
   * Convenience method to retrieve a primitive double value from a Double data item. For general
   * information on retrieval, see {@link #retrieve}
   *
   * @param key The key to retrieve data for
   * @return The double value of the retrieved Double data item
   */
  public double retrieveDouble(ScratchpadKey<Double> key) {
    return this.retrieve(key).doubleValue();
  }

  /**
   * Returns the list of keys that have been used to retrieve data. NOTE - This list can contain a
   * mixed list of Strings and ScratchpadKeys. If a value was retrieved using a ScratchpadKey, the
   * string version WILL NOT appear in this list, and vice versa.
   */
  public synchronized Set getRetrievedKeys() {
    return Collections.unmodifiableSet(this.retrievedKeys);
  }

  /**
   * Returns the list of keys that have been stored on the Scratchpad. This is an audit log, so will
   * contain all keys that were ever stored, even if they have subsequently been removed. NOTE -
   * This list can contain a mixed list of Strings and ScratchpadKeys. If a value was stored using a
   * ScratchpadKey, the string version WILL NOT appearing this list, and vice versa.
   */
  public synchronized Set getStoredKeys() {
    return Collections.unmodifiableSet(this.storedKeys);
  }

  /**
   * Removes an object from the scratchpad
   *
   * @deprecated Please use an equivalent ScratchpadKey based method instead
   */
  @Deprecated
  public synchronized Object remove(String name) {
    LOGGER.debug("Removing the following key from the scratchpad: {}", name);
    Object result = this.pad.remove(name);
    if (result == null) {
      LOGGER.debug("Unable to find String, searching for corresponding key");
      ScratchpadKey key = this.findKeyByString(name);
      result = this.pad.remove(key);
    }
    return result;
  }

  private void addRetrievedKey(Object key) {
    this.retrievedKeys.add(key);
  }

  private void addStoredKey(Object key) {
    this.storedKeys.add(key);
  }

  // Finds a {@link ScratchpadKey} entry on the map given it's name.
  private ScratchpadKey findKeyByString(final String name) {
    return (ScratchpadKey) Iterate.detect(this.pad.keySet(), new Predicate() {
      public boolean accept(Object anObject) {
        return anObject instanceof ScratchpadKey
            && ((ScratchpadKey) anObject).getName().equals(name);
      }
    });
  }

  private <V> String findStringByKey(final ScratchpadKey<V> key) {
    return (String) Iterate.detect(this.pad.keySet(), new Predicate() {
      public boolean accept(Object anObject) {
        return key.getName().equals(anObject);
      }
    });
  }
}
