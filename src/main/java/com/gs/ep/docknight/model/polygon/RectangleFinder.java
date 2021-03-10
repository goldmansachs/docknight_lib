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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.sorted.MutableSortedMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.SortedMaps;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.VerticalLine;
import com.gs.ep.docknight.model.polygon.LineAbscissa.AbscissaType;
import java.awt.geom.Rectangle2D;

/**
 * Class which is used to find found rectangles and horizontally open rectangles. It also contains
 * rectangle builders that are used to construct those rectangles.
 */
public class RectangleFinder {

  private MutableList<Rectangle2D> foundRectangles;
  private MutableList<OpenRectangle> horizontallyOpenRectangles;
  private MutableList<RectangleBuilder> createdRectangleBuilders;

  public RectangleFinder(MutableSet<Element> horizontalLines, MutableSet<Element> verticalLines) {
    this.foundRectangles = Lists.mutable.empty();
    this.createdRectangleBuilders = Lists.mutable.empty();
    this.horizontallyOpenRectangles = Lists.mutable.empty();
    MutableSortedMap<Double, HorizontalLine> currentHorizontalLines = SortedMaps.mutable.of();
    MutableList<LineAbscissa> lineAbscissas = Lists.mutable.empty();
    for (Element horizontalLine : horizontalLines) {
      lineAbscissas.add(new LineAbscissa(AbscissaType.HORIZONTAL_LINE_LEFT, horizontalLine));
      lineAbscissas.add(new LineAbscissa(AbscissaType.HORIZONTAL_LINE_RIGHT, horizontalLine));
    }
    for (Element verticalLine : verticalLines) {
      lineAbscissas.add(new LineAbscissa(AbscissaType.VERTICAL_LINE, verticalLine));
    }
    lineAbscissas.sort(LineAbscissa.LINE_ABSCISSA_COMPARATOR);

    for (LineAbscissa lineAbscissa : lineAbscissas) {
      if (AbscissaType.HORIZONTAL_LINE_LEFT.equals(lineAbscissa.getAbscissaType())) {
        currentHorizontalLines.put(lineAbscissa.getElement().getAttribute(Top.class).getMagnitude(),
            (HorizontalLine) lineAbscissa.getElement());
      } else if (AbscissaType.HORIZONTAL_LINE_RIGHT.equals(lineAbscissa.getAbscissaType())) {
        currentHorizontalLines
            .remove(lineAbscissa.getElement().getAttribute(Top.class).getMagnitude());
      } else {
        VerticalLine verticalLine = (VerticalLine) lineAbscissa.getElement();
        MutableList<HorizontalLine> horizontalLinesClosedOnLeftSide = Lists.mutable.empty();
        for (RectangleBuilder rectangleBuilder : this.createdRectangleBuilders) {
          // Consider vertical line as right border
          MutableSortedSet<HorizontalLine> intersectingBorders = rectangleBuilder
              .findIntersectingBorders(verticalLine);
          this.horizontallyOpenRectangles.addAll(
              rectangleBuilder.getHorizontallyOpenRectangles(verticalLine, intersectingBorders));
          this.foundRectangles.addAll(rectangleBuilder
              .createRectanglesWithRightBorder(verticalLine, intersectingBorders.toList()));
          rectangleBuilder.removeRightSideClosedLines(intersectingBorders);
          horizontalLinesClosedOnLeftSide.addAll(intersectingBorders);
        }

        // Considering vertical line as left border
        RectangleBuilder leftBorderedRectangleBuilder = new RectangleBuilder(verticalLine,
            currentHorizontalLines);
        leftBorderedRectangleBuilder.removeLeftSideClosedLines(horizontalLinesClosedOnLeftSide);
        this.createdRectangleBuilders.add(leftBorderedRectangleBuilder);
      }
    }
  }

  public MutableList<Rectangle2D> getFoundRectangles() {
    return this.foundRectangles;
  }

  public MutableList<OpenRectangle> getHorizontallyOpenRectangles() {
    return this.horizontallyOpenRectangles;
  }

  public MutableList<RectangleBuilder> getCreatedRectangleBuilders() {
    return this.createdRectangleBuilders;
  }
}
