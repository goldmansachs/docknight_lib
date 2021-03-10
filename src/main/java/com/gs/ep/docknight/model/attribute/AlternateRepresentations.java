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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import com.gs.ep.docknight.model.Attribute;
import com.gs.ep.docknight.model.Element;
import java.util.List;

/**
 * Attribute defined for alternate representation of the element
 */
public class AlternateRepresentations extends Attribute<List<Element>> {

  private static final long serialVersionUID = 2757262908031925455L;

  public AlternateRepresentations(List<Element> alternateRepresentations) {
    this.setValue(alternateRepresentations);
  }

  @Override
  public Class getHolderInterface() {
    return Holder.class;
  }

  public String getTextStr() {
    MutableList<String> textStrList = Lists.mutable.empty();
    this.getValue().forEach(element -> textStrList.add(element.getTextStr()));
    return textStrList.makeString(" ");
  }

  public interface Holder<E extends Element> {

    default E add(AlternateRepresentations alternateRepresentations) {
      E element = (E) this;
      element.addAttribute(alternateRepresentations);
      return element;
    }

    default AlternateRepresentations getAlternateRepresentations() {
      E element = (E) this;
      return element.getAttribute(AlternateRepresentations.class);
    }
  }
}
