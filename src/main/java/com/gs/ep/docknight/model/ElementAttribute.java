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

import java.util.List;

/**
 * Attribute defined to establish hierarchical structure or child elements of the element.
 */
public abstract class ElementAttribute<E extends Element, L extends ElementList<E>> extends
    Attribute<L> {

  private static final long serialVersionUID = -3641816188223463838L;

  @Override
  public L getElementList() {
    return this.getValue();
  }

  public List<E> getElements() {
    return this.getValue().getElements();
  }

  @Override
  public Attribute<L> clone(Element parentElement) {
    Attribute<L> attribute = super.clone(parentElement);
    attribute.setValue((L) attribute.getValue().clone());
    return attribute;
  }

  @Override
  public void setValue(L value) {
    super.setValue(value);
    value.setEnclosingAttribute(this);
  }
}
