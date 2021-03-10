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

package com.gs.ep.docknight.model.element;

import org.eclipse.collections.impl.factory.Lists;
import com.gs.ep.docknight.model.Attribute;
import com.gs.ep.docknight.model.ElementAttribute;
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Top;
import java.util.List;

/**
 * Class representing page breaks. These occurred at the end of page and are used to break a single
 * page into multiple pages.
 */
public class PageBreak extends GraphicalElement
    implements Top.Holder<PageBreak> {

  private static final long serialVersionUID = 3579758621811011539L;

  @Override
  public List<Class<? extends ElementAttribute>> getDefaultLayout() {
    return Lists.mutable.of();
  }

  @Override
  public <A extends Attribute> A getAttribute(Class<A> attributeClass) {
    return attributeClass.equals(Left.class) ? (A) new Left(Length.ZERO)
        : super.getAttribute(attributeClass);
  }
}
