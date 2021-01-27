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
import com.gs.ep.docknight.model.attribute.LetterSpacing;
import com.gs.ep.docknight.model.attribute.Text;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Url;
import java.util.List;

/**
 * Class representation for inline element with text
 */
public class TextElement extends InlineElement<TextElement> implements
    Text.Holder<TextElement>,
    Url.Holder<TextElement>,
    TextStyles.Holder<TextElement>,
    LetterSpacing.Holder<TextElement> {

  private static final long serialVersionUID = 8620645260323059529L;

  @Override
  public List<Class<? extends ElementAttribute>> getDefaultLayout() {
    return Lists.mutable.of();
  }

  @Override
  public String getTextStr() {
    Text text = this.getText();
    return text == null ? "" : text.getValue();
  }
}
