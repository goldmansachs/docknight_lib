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
import org.eclipse.collections.impl.tuple.Tuples;
import com.gs.ep.docknight.model.Attribute;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.Length;

/**
 * Attribute defined for page size of the element
 */
public class PageSize extends Attribute<Pair<Length, Length>> {

  public static final Pair<Length, Length> A4 = Tuples
      .pair(new Length(595, Length.Unit.pt), new Length(842, Length.Unit.pt));
  private static final long serialVersionUID = 475625608913624357L;

  public PageSize(Pair<Length, Length> value) {
    this.setValue(value);
  }

  public PageSize(Length width, Length height) {
    this.setValue(Tuples.pair(width, height));
  }

  public static PageSize createA4() {
    return new PageSize(PageSize.A4);
  }

  @Override
  public Class getHolderInterface() {
    return Holder.class;
  }

  public interface Holder<E extends Element> {

    default E add(PageSize attribute) {
      E element = (E) this;
      element.addAttribute(attribute);
      return element;
    }

    default PageSize getPageSize() {
      E element = (E) this;
      return element.getAttribute(PageSize.class);
    }
  }
}
