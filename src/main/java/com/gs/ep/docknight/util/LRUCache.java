/*
 *   Copyright 2020 Goldman Sachs.
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

package com.gs.ep.docknight.util;

import org.eclipse.collections.api.block.predicate.Predicate;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Class representing LRU Cache
 */
public class LRUCache<X> {

  private int maxSize;
  private Deque<X> store;

  public LRUCache(int maxSize) {
    this.maxSize = maxSize;
    this.store = new LinkedList<>();
  }

  /**
   * Get the first element from LRU Cache, satisfying the input {@code predicate} Also, set the
   * element returned to recently used position in cache
   */
  public synchronized X get(Predicate<X> predicate) {
    Iterator<X> storeIterator = this.store.iterator();
    boolean isFirst = true;
    while (storeIterator.hasNext()) {
      X object = storeIterator.next();
      if (predicate.accept(object)) {
        if (!isFirst) {
          storeIterator.remove();
          this.store.addFirst(object);
        }
        return object;
      }
      isFirst = false;
    }
    return null;
  }

  /**
   * Add the {@code object} to LRU cache
   */
  public synchronized void add(X object) {
    if (this.store.size() == this.maxSize) {
      this.store.removeLast();
    }
    this.store.addFirst(object);
  }
}
