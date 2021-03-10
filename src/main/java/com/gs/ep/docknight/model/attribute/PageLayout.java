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
 * Attribute defined for page layout of the element. It contains rectangular regions for scanned and
 * hand written areas within the page.
 */
public class PageLayout extends Attribute<List<Pair<Rectangle, String>>> {

  public static final String HAND_WRITTEN = "hand_written";
  public static final String SCANNED = "scanned";
  private static final long serialVersionUID = 6059559013353245627L;

  public PageLayout(List<Pair<Rectangle, String>> value) {
    this.setValue(value);
  }

  @Override
  public Class getHolderInterface() {
    return Holder.class;
  }

  public interface Holder<E extends Element> {

    default E add(PageLayout attribute) {
      E element = (E) this;
      element.addAttribute(attribute);
      return element;
    }

    default PageLayout getPageLayout() {
      E element = (E) this;
      return element.getAttribute(PageLayout.class);
    }
  }
}
