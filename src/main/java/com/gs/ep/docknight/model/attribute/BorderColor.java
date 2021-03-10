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

package com.gs.ep.docknight.model.attribute;

import com.gs.ep.docknight.model.Attribute;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.RectangleProperties;

/**
 * Attribute defined for border color of the element
 */
public class BorderColor extends Attribute<RectangleProperties<java.awt.Color>> {

  private static final long serialVersionUID = -4970027392367361162L;

  public BorderColor(RectangleProperties<java.awt.Color> value) {
    this.setValue(value);
  }

  public BorderColor(java.awt.Color top, java.awt.Color right, java.awt.Color bottom,
      java.awt.Color left) {
    this.setValue(new RectangleProperties(top, right, bottom, left));
  }

  public BorderColor(java.awt.Color common) {
    this.setValue(new RectangleProperties(common));
  }

  @Override
  public Class getHolderInterface() {
    return Holder.class;
  }

  public interface Holder<E extends Element> {

    default E add(BorderColor attribute) {
      E element = (E) this;
      element.addAttribute(attribute);
      return element;
    }

    default BorderColor getBorderColor() {
      E element = (E) this;
      return element.getAttribute(BorderColor.class);
    }
  }
}
