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

import org.eclipse.collections.impl.factory.Maps;
import java.io.Serializable;
import java.util.Map;

/**
 * Concrete class to represent tag information for DTD
 */
public class AnnotableBase implements Annotable, Serializable {

  private static final long serialVersionUID = -7743097183624382384L;
  private Map<String, String> annotations;

  @Override
  public void addAnnotation(String type, String value) {
    if (this.annotations == null) {
      this.annotations = Maps.mutable.empty();
    }
    this.annotations.put(type, value);
  }

  @Override
  public boolean hasAnnotation(String type, String value) {
    return this.hasAnnotation(type) ? this.annotations.get(type).equals(value) : false;
  }

  @Override
  public boolean hasAnnotation(String type) {
    return this.annotations != null ? this.annotations.containsKey(type) : false;
  }

  @Override
  public boolean hasAnnotations() {
    return this.annotations != null && !this.annotations.isEmpty();
  }

  @Override
  public String getAnnotationValue(String type) {
    return this.annotations.get(type);
  }
}
