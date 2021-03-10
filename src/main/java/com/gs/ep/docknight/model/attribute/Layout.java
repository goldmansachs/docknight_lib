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
import com.gs.ep.docknight.model.ElementAttribute;
import java.util.List;

/**
 * This attribute is used to define layout for element and if it is not present then default layout
 * is used.
 */
public class Layout extends Attribute<List<Class<? extends ElementAttribute>>> {

  private static final long serialVersionUID = 3762008499072542197L;

  public Layout(List<Class<? extends ElementAttribute>> value) {
    this.setValue(value);
  }

  public Layout(Class<? extends ElementAttribute>... value) {
    this.setValueFromArray(value);
  }

  @Override
  public Class getHolderInterface() {
    return Holder.class;
  }

  public interface Holder<E extends Element> {

    default E add(Layout attribute) {
      E element = (E) this;
      element.addAttribute(attribute);
      return element;
    }

    default Layout getLayout() {
      E element = (E) this;
      return element.getAttribute(Layout.class);
    }
  }
}
