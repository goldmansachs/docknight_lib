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

package com.gs.ep.docknight.model.polygon;

import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Stretch;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.VerticalLine;
import java.util.Comparator;

/**
 * Class representing abscissa of line
 */
public class LineAbscissa {

  public static final Comparator<LineAbscissa> LINE_ABSCISSA_COMPARATOR = (o1, o2) ->
  {
    double o1Abscissa = o1.getAbscissaType().getAbscissaValue(o1.getElement());
    double o2Abscissa = o2.getAbscissaType().getAbscissaValue(o2.getElement());
    if (Math.abs(o1Abscissa - o2Abscissa) > RectilinearPolygon.SEPARATION_EPSILON || o1
        .getAbscissaType().equals(o2.getAbscissaType())) {
      return Double.compare(o1Abscissa, o2Abscissa);
    }
    return Integer.compare(o1.getAbscissaType().getPriority(), o2.getAbscissaType().getPriority());
  };
  private AbscissaType abscissaType;
  private Element element;

  public LineAbscissa(AbscissaType abscissaType, Element element) {
    this.abscissaType = abscissaType;
    this.element = element;
  }

  public AbscissaType getAbscissaType() {
    return abscissaType;
  }

  public Element getElement() {
    return element;
  }

  /**
   * Class representing type of abscissa
   */
  public enum AbscissaType {
    HORIZONTAL_LINE_LEFT(0)   // left x coordinate of horizontal line
        {
          @Override
          public double getAbscissaValue(Element e) throws IllegalArgumentException {
            if (e instanceof HorizontalLine) {
              return ((HorizontalLine) e).getAttribute(Left.class).getMagnitude();
            }
            throw new IllegalArgumentException("Element must be an instance of HorizontalLine");
          }
        },
    VERTICAL_LINE(1)          // x coordinate of vertical line
        {
          @Override
          public double getAbscissaValue(Element e) throws IllegalArgumentException {
            if (e instanceof VerticalLine) {
              return ((VerticalLine) e).getAttribute(Left.class).getMagnitude();
            }
            throw new IllegalArgumentException("Element must be an instance of VerticalLine");
          }
        },
    HORIZONTAL_LINE_RIGHT(2)   // right x coordinate of horizontal line
        {
          @Override
          public double getAbscissaValue(Element e) throws IllegalArgumentException {
            if (e instanceof HorizontalLine) {
              HorizontalLine horizontalLine = (HorizontalLine) e;
              double leftAbscissa = horizontalLine.getAttribute(Left.class).getMagnitude();
              double horizontalStretch = horizontalLine.getAttribute(Stretch.class).getMagnitude();
              return leftAbscissa + horizontalStretch;
            }
            throw new IllegalArgumentException("Element must be an instance of HorizontalLine");
          }
        };

    private final int priority;

    AbscissaType(int priority) {
      this.priority = priority;
    }

    public abstract double getAbscissaValue(Element e) throws IllegalArgumentException;

    public int getPriority() {
      return this.priority;
    }
  }
}
