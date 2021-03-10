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
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.FontFamily;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.TextAlign;

/**
 * Abstract class to represent rectangle shape objects (with text) within document
 *
 * @param <E> concrete class representing text rectangle concept
 */
public abstract class TextRectangle<E extends Element> extends Rectangle<E> implements
    Color.Holder<E>,
    FontFamily.Holder<E>,
    FontSize.Holder<E>,
    TextAlign.Holder<E> {

  private static final long serialVersionUID = -3440516323065151269L;
}
