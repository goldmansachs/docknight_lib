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

package com.gs.ep.docknight.model;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Visitor pattern interface defined for {@see com.gs.ep.docknight.model.Element}. This pattern is
 * used in order to add method handleAttribute in {@see com.gs.ep.docknight.model.Element} class
 * without modifying it.
 */
public interface ElementVisitor<D> {

  /**
   * Default method to handle the {@code element} with {@code data}
   */
  default void handleElement(Element element, D data) {
    try {
      Method method = this.getClass()
          .getMethod("handleElement", element.getClass(), this.getElementVisitorDataClass());
      method.invoke(this, element, data);
    } catch (NoSuchMethodException e) {
      throw new UnsupportedOperationException("Rendering element " + element.getName(), e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e.getTargetException());
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Get the class for data object which is used in handling the element.
   *
   * @return data class
   */
  Class<? extends D> getElementVisitorDataClass();
}
