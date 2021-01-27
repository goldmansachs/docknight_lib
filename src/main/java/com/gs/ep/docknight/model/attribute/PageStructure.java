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

/**
 * Attribute defined for page structure of the element
 */
public class PageStructure extends Attribute<String> {

  public static final String NO_PAGE_BREAK = "no-page-break";
  public static final String FLOW_PAGE_BREAK = "flow-page-break";
  public static final String NO_FLOW_PAGE_BREAK = "no-flow-page-break";
  private static final long serialVersionUID = -3507677041162762534L;

  public PageStructure(String value) {
    this.setValue(value);
  }

  @Override
  public Class getHolderInterface() {
    return Holder.class;
  }

  public interface Holder<E extends Element> {

    default E add(PageStructure attribute) {
      E element = (E) this;
      element.addAttribute(attribute);
      return element;
    }

    default PageStructure getPageStructure() {
      E element = (E) this;
      return element.getAttribute(PageStructure.class);
    }
  }
}
