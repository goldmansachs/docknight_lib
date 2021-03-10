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
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.RectangleProperties;

/**
 * Attribute defined for page margin of the element
 */
public class PageMargin extends Attribute<RectangleProperties<Length>> {

  private static final long serialVersionUID = 6170170893866504907L;

  public PageMargin(RectangleProperties<Length> value) {
    this.setValue(value);
  }

  public PageMargin(Length top, Length right, Length bottom, Length left) {
    this.setValue(new RectangleProperties(top, right, bottom, left));
  }

  public PageMargin(Length common) {
    this.setValue(new RectangleProperties(common));
  }

  @Override
  public Class getHolderInterface() {
    return Holder.class;
  }

  public interface Holder<E extends Element> {

    default E add(PageMargin attribute) {
      E element = (E) this;
      element.addAttribute(attribute);
      return element;
    }

    default PageMargin getPageMargin() {
      E element = (E) this;
      return element.getAttribute(PageMargin.class);
    }
  }
}
