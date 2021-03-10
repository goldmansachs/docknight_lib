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
import com.gs.ep.docknight.model.ComparableBufferedImage;
import com.gs.ep.docknight.model.Element;

/**
 * Attribute defined for image data of the element
 */
public class ImageData extends Attribute<ComparableBufferedImage> {

  private static final long serialVersionUID = 4438148314739979633L;

  public ImageData(ComparableBufferedImage value) {
    this.setValue(value);
  }

  @Override
  public Class getHolderInterface() {
    return Holder.class;
  }

  public interface Holder<E extends Element> {

    default E add(ImageData attribute) {
      E element = (E) this;
      element.addAttribute(attribute);
      return element;
    }

    default ImageData getImageData() {
      E element = (E) this;
      return element.getAttribute(ImageData.class);
    }
  }
}
