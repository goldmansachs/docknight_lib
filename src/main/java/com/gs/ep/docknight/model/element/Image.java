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
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementAttribute;
import com.gs.ep.docknight.model.attribute.Caption;
import com.gs.ep.docknight.model.attribute.ImageData;
import com.gs.ep.docknight.model.attribute.Url;
import java.util.List;

/**
 * Class representation for image within document
 */
public class Image extends Rectangle<Image> implements
    Caption.Holder<Image>,
    Url.Holder<Image>,
    ImageData.Holder<Image> {

  private static final long serialVersionUID = -8859773509857984399L;

  @Override
  public List<Class<? extends ElementAttribute>> getDefaultLayout() {
    return Lists.mutable.of(Caption.class);
  }

  public boolean isImageRepresentationForForm() {
    return this.getAlternateRepresentations() != null && ListAdapter
        .adapt(this.getAlternateRepresentations().getValue())
        .anySatisfy(e -> e instanceof FormElement);
  }

  @Override
  public String getTextStr() {
    return this.isImageRepresentationForForm() ? ListAdapter
        .adapt(this.getAlternateRepresentations().getValue()).collect(Element::getTextStr)
        .makeString(Element.INTER_LINE_SEP) : super.getTextStr();
  }
}
