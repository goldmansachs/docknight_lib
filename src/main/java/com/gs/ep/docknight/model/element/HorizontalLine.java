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
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.Length.Unit;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Stretch;
import com.gs.ep.docknight.model.attribute.Top;
import java.util.List;

/**
 * Class to represent horizontal line.
 */
public class HorizontalLine extends GraphicalElement implements
    Top.Holder<HorizontalLine>,
    Left.Holder<HorizontalLine>,
    Stretch.Holder<HorizontalLine> {

  private static final long serialVersionUID = 8919044188118221996L;

  public static HorizontalLine from(double top, double left, double stretch) {
    return new HorizontalLine()
        .add(new Top(new Length(top, Unit.pt)))
        .add(new Left(new Length(left, Unit.pt)))
        .add(new Stretch(new Length(stretch, Unit.pt)));
  }

  @Override
  public List<Class<? extends ElementAttribute>> getDefaultLayout() {
    return Lists.mutable.of();
  }
}
