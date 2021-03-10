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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import java.io.Serializable;
import java.util.Objects;

/**
 * Concrete class to represent collection of elements
 */
public class ElementGroup<E extends Element> implements CloneableWithReference<ElementGroup<E>>,
    ElementCollection<E>, Serializable {

  private static final long serialVersionUID = -6342770045202114036L;
  private final MutableList<E> elements;
  private transient ElementGroup<E> clone;

  public ElementGroup(MutableList<E> elements) {
    this.elements = elements;
  }

  public ElementGroup() {
    this.elements = Lists.mutable.empty();
  }

  public ElementGroup<E> add(E element) {
    this.elements.add(element);
    return this;
  }

  @Override
  public MutableList<E> getElements() {
    return this.elements;
  }

  @Override
  public int size() {
    return this.elements.size();
  }

  @Override
  public E getFirst() {
    return this.elements.getFirst();
  }

  @Override
  public E getLast() {
    return this.elements.getLast();
  }

  /**
   * @return bounding box which covers all the elements in this collection.
   */
  public RectangleProperties<Double> getTextBoundingBox() {
    double left = this.elements
        .collectDouble(e -> e.getAttribute(Left.class).getValue().getMagnitude()).min();
    double right = this.elements
        .collectDouble(e -> e.getAttribute(Left.class).getValue().getMagnitude() +
            e.getAttribute(Width.class).getValue().getMagnitude()).max();
    double top = this.getFirst().getAttribute(Top.class).getValue().getMagnitude();
    double bottom = this.getLast().getAttribute(Top.class).getValue().getMagnitude() +
        this.getLast().getAttribute(Height.class).getValue().getMagnitude();
    return new RectangleProperties<>(top, right, bottom, left);
  }


  @Override
  protected ElementGroup<E> clone() {
    this.clone = new ElementGroup<>();
    for (E element : this.elements) {
      this.clone.add((E) element.getCurrentClone());
    }
    return this.clone;
  }

  @Override
  public ElementGroup<E> getCurrentClone() {
    if(this.clone == null){
      this.clone = this.clone();
    }
    return this.clone;
  }

  @Override
  public String toString() {
    return this.getTextStr();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    ElementGroup<?> that = (ElementGroup<?>) o;

    return Objects.equals(this.elements, that.elements);
  }

  @Override
  public int hashCode() {
    return this.elements == null ? 0 : this.elements.hashCode();
  }
}
