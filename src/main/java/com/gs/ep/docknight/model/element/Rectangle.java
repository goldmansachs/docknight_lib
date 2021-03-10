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

import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.attribute.Align;
import com.gs.ep.docknight.model.attribute.AlternateRepresentations;
import com.gs.ep.docknight.model.attribute.BackGroundColor;
import com.gs.ep.docknight.model.attribute.BorderColor;
import com.gs.ep.docknight.model.attribute.BorderStyle;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Layout;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;

/**
 * Abstract class to represent rectangle shape objects within document
 *
 * @param <E> concrete class representing rectangle concept
 */
public abstract class Rectangle<E extends Element> extends Element implements
    Align.Holder<E>,
    BackGroundColor.Holder<E>,
    BorderColor.Holder<E>,
    BorderStyle.Holder<E>,
    Layout.Holder<E>,
    Top.Holder<E>,
    Left.Holder<E>,
    Height.Holder<E>,
    Width.Holder<E>,
    AlternateRepresentations.Holder<E> {

  private static final long serialVersionUID = 8364035241304502533L;
}
