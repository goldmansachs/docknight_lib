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
 * Abstract class to represent attribute/properties for the element
 */
public abstract class Attribute<T> extends AnnotableBase implements Cloneable,
    DerivedCastable<Attribute<T>>, Serializable {

  public static final String ATTRIBUTES_PACKAGE = "com.gs.ep.docknight.model.attribute";
  private static final long serialVersionUID = -7885440617593307871L;

  @SuppressWarnings("NonSerializableFieldInSerializableClass")
  private T value;
  private Element parentElement;

  public String getName() {
    return this.getClass().getSimpleName();
  }

  public abstract Class getHolderInterface();

  public T getValue() {
    return this.value;
  }

  public void setValue(T value) {
    this.value = value;
    if (!this.isValid()) {
      throw new RuntimeException("Invalid " + this.getName() + " value (" + this.value + ")");
    }
  }

  public void setValueFromArray(Object[] value) {
    List<Object> data = Lists.mutable.empty();
    for (Object v : value) {
      data.add(v);
    }
    this.setValue((T) data);
  }

  /**
   * @return True if the attribute contains valid data. Example: font size attribute should have
   * magnitude > 0
   */
  public boolean isValid() {
    return true;
  }

  /**
   * Getter for parent element
   */
  public Element getParentElement() {
    return this.parentElement;
  }

  /**
   * Set the parent element for this attribute
   */
  public void setParentElement(Element parentElement) {
    this.parentElement = parentElement;
  }

  public <E extends Element, L extends ElementList<E>> L getElementList() {
    return null;
  }

  public Attribute<T> clone(Element parentElement) {
    try {
      Attribute<T> attribute = (Attribute<T>) super.clone();
      attribute.parentElement = parentElement;
      return attribute;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Attribute)) {
      return false;
    }

    Attribute<?> attribute = (Attribute<?>) o;

    if (!this.getClass().equals(attribute.getClass())) {
      return false;
    }
    if (this.value != null ? !this.value.equals(attribute.value) : attribute.value != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = this.value != null ? this.value.hashCode() : 0;
    result = 31 * result + this.getClass().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return this.value.toString();
  }
}
