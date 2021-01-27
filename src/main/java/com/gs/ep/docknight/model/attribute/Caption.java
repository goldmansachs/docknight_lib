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

import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementAttribute;
import com.gs.ep.docknight.model.ElementList;
import com.gs.ep.docknight.model.element.InlineElement;

/**
 * Attribute defined for caption of the element
 */
public class Caption extends ElementAttribute<InlineElement, ElementList<InlineElement>> {

  private static final long serialVersionUID = 173115277925782625L;

  public Caption(ElementList<InlineElement> value) {
    this.setValue(value);
  }

  public Caption(InlineElement... value) {
    this(new ElementList<InlineElement>(value));
  }

  @Override
  public Class getHolderInterface() {
    return Holder.class;
  }

  public interface Holder<E extends Element> {

    default E add(Caption attribute) {
      E element = (E) this;
      element.addAttribute(attribute);
      return element;
    }

    default Caption getCaption() {
      E element = (E) this;
      return element.getAttribute(Caption.class);
    }
  }
}
