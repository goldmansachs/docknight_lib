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
import com.gs.ep.docknight.model.ElementAttribute;
import com.gs.ep.docknight.model.attribute.Content;
import com.gs.ep.docknight.model.attribute.PageColor;
import com.gs.ep.docknight.model.attribute.PageLayout;
import com.gs.ep.docknight.model.attribute.PositionalContent;
import java.util.List;

/**
 * Class representation for page within document
 */
public class Page extends TextRectangle<Page> implements
    Content.Holder<Page>,
    PositionalContent.Holder<Page>,
    PageColor.Holder<Page>,
    PageLayout.Holder<Page> {

  private static final long serialVersionUID = 7244915848277591913L;

  @Override
  public List<Class<? extends ElementAttribute>> getDefaultLayout() {
    return this.hasAttribute(Content.class) ? Lists.mutable.of(Content.class)
        : Lists.mutable.of(PositionalContent.class);
  }
}
