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

import org.eclipse.collections.impl.factory.Lists;
import java.io.Serializable;
import java.util.List;

/**
 * Class containing list of elements along with an enclosingAttribute. This class is used to contain
 * children of the element
 */
public class ElementList<E extends Element> implements Cloneable, DerivedCastable<ElementList<E>>,
    Serializable {

  private static final long serialVersionUID = -4376546053735132035L;
  private List<E> elements;
  private ElementAttribute<E, ? extends ElementList<E>> enclosingAttribute;

  public ElementList(List<E> elements) {
    this.elements = elements;
    assignElementListReference(this, this.elements);
  }

  public ElementList(E[] elements) {
    this.elements = Lists.mutable.empty();
    for (E element : elements) {
      this.elements.add(element);
    }
    assignElementListReference(this, this.elements);
  }

  public ElementList() {
    this.elements = Lists.mutable.empty();
  }

  private static <E extends Element> void assignElementListReference(ElementList<E> elementList,
      List<E> elements) {
    int i = 0;
    for (E element : elements) {
      element.setElementListContext(elementList, i);
      i++;
    }
  }

  public List<E> getElements() {
    return this.elements;
  }

  public void setElements(List<E> elements) {
    this.elements = elements;
    assignElementListReference(this, this.elements);
  }

  public boolean isPositionBased() {
    return false;
  }

  public E getFirst() {
    return this.elements.get(0);
  }

  public E getLast() {
    return this.elements.get(this.elements.size() - 1);
  }

  public boolean isEmpty() {
    return this.elements.isEmpty();
  }

  public void appendElement(E element) {
    this.elements.add(element);
    element.setElementListContext(this, this.elements.size() - 1);
  }

  public ElementAttribute<E, ? extends ElementList<E>> getEnclosingAttribute() {
    return this.enclosingAttribute;
  }

  public void setEnclosingAttribute(
      ElementAttribute<E, ? extends ElementList<E>> enclosingAttribute) {
    this.enclosingAttribute = enclosingAttribute;
  }

  public ElementList<E> clone() {
    try {
      ElementList<E> elementList = (ElementList<E>) super.clone();
      elementList.elements = Lists.mutable.empty();
      for (E element : this.elements) {
        elementList.elements.add((E) element.clone());
      }
      assignElementListReference(elementList, elementList.elements);
      return elementList;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ElementList)) {
      return false;
    }

    ElementList<?> that = (ElementList<?>) o;

    if (!this.elements.equals(that.elements)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return this.elements.hashCode();
  }
}
