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
import java.util.List;

/**
 * Attribute defined for text styles of the element
 */
public class TextStyles extends Attribute<List<String>> {

  public static final String ITALIC = "italic";
  public static final String VERBATIM = "vebatim";
  public static final String BOLD = "bold";
  public static final String LARGE = "large";
  public static final String LARGER = "large";
  public static final String VERY_LARGE = "very_large";
  public static final String HUGE = "huge";
  public static final String VERY_HUGE = "very_huge";
  public static final String SMALL = "small";
  public static final String SMALLER = "smaller";
  public static final String VERY_SMALL = "very_small";
  public static final String TINY = "tiny";
  public static final String UNDERLINE = "underline";
  public static final String URL = "url";
  private static final long serialVersionUID = 9076010971976452370L;

  public TextStyles(List<String> value) {
    this.setValue(value);
  }

  public TextStyles(String... value) {
    this.setValueFromArray(value);
  }

  @Override
  public Class getHolderInterface() {
    return Holder.class;
  }

  public interface Holder<E extends Element> {

    default E add(TextStyles attribute) {
      E element = (E) this;
      element.addAttribute(attribute);
      return element;
    }

    default TextStyles getTextStyles() {
      E element = (E) this;
      return element.getAttribute(TextStyles.class);
    }
  }
}
