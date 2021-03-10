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

import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import com.gs.ep.docknight.model.Element;

/**
 * Abstract class to represent inline elements
 *
 * @param <E> concrete class representing inline element concept
 */
public abstract class InlineElement<E extends Element> extends TextRectangle<E> {

  private static final long serialVersionUID = 4271375132072223821L;

  @Override
  public Pair<String, String> getTextStrDelim() {
    return Tuples.pair("", "");
  }

  @Override
  public boolean isInline() {
    return true;
  }
}


