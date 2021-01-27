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

import org.eclipse.collections.api.tuple.Pair;
import com.gs.ep.docknight.model.Attribute;
import com.gs.ep.docknight.model.Element;
import java.awt.Rectangle;
import java.util.List;

/**
 * Attribute defined for page color of the element
 */
public class PageColor extends Attribute<List<Pair<Rectangle, Integer>>> {

  private static final long serialVersionUID = -8074166023533631331L;

  public PageColor(List<Pair<Rectangle, Integer>> value) {
    this.setValue(value);
  }

  @Override
  public Class getHolderInterface() {
    return Holder.class;
  }

  public interface Holder<E extends Element> {

    default E add(PageColor attribute) {
      E element = (E) this;
      element.addAttribute(attribute);
      return element;
    }

    default PageColor getPageColor() {
      E element = (E) this;
      return element.getAttribute(PageColor.class);
    }
  }
}
