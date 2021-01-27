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
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.SortedMaps;
import org.eclipse.collections.impl.factory.SortedSets;
import org.eclipse.collections.impl.utility.Iterate;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.VerticalLine;
import com.gs.ep.docknight.model.polygon.OpenRectangle.OpenSide;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.Comparator;

/**
 * Class representing builder to build rectangles
 */
public class RectangleBuilder {

  private static final Comparator<HorizontalLine> HORIZONTAL_LINE_VERTICAL_COMPARATOR = Comparator
      .comparingDouble(e -> e.getTop().getMagnitude());
  private final VerticalLine leftBorder;
  private final MutableSortedMap<Double, HorizontalLine> intersectingLines;
  private final MutableSortedSet<HorizontalLine> leftSideOpenCandidates;
  private final MutableSortedSet<HorizontalLine> rightSideOpenCandidates;

  public RectangleBuilder(VerticalLine leftBorder,
      MutableSortedMap<Double, HorizontalLine> horizontalLines) {
    this.leftBorder = leftBorder;
    this.leftSideOpenCandidates = SortedSets.mutable.of(HORIZONTAL_LINE_VERTICAL_COMPARATOR);
    this.rightSideOpenCandidates = SortedSets.mutable.of(HORIZONTAL_LINE_VERTICAL_COMPARATOR);
    this.intersectingLines = SortedMaps.mutable.of();
    if (Iterate.notEmpty(horizontalLines)) {
      double verticalLowerBound =
          leftBorder.getTop().getMagnitude() - RectilinearPolygon.SEPARATION_EPSILON;
      double verticalUpperBound = verticalLowerBound + leftBorder.getStretch().getMagnitude()
          + 2 * RectilinearPolygon.SEPARATION_EPSILON;

      double topHorizontalLineY = horizontalLines.getFirst().getTop().getMagnitude();
      double bottomHorizontalLineY = horizontalLines.getLast().getTop().getMagnitude();

      if (topHorizontalLineY < verticalUpperBound && bottomHorizontalLineY > verticalLowerBound) {
        MutableSortedMap<Double, HorizontalLine> validLines = horizontalLines
            .subMap(verticalLowerBound, verticalUpperBound);
        this.intersectingLines.putAll(validLines);
        this.rightSideOpenCandidates.addAll(
            validLines.select(h -> h.getLeft().getMagnitude() + h.getStretch().getMagnitude() >
                this.leftBorder.getLeft().getMagnitude() + RectilinearPolygon.SEPARATION_EPSILON));
        this.leftSideOpenCandidates.addAll(validLines.select(h -> h.getLeft().getMagnitude() <
            this.leftBorder.getLeft().getMagnitude() - RectilinearPolygon.SEPARATION_EPSILON));
      }
    }
  }

  /**
   * Find all horizontal lines such that they are intersecting with vertical line {@code
   * rightBorder}
   *
   * @param rightBorder vertical line representing right side of rectangle
   * @return horizontal lines intersecting the line {@code rightBorder}
   */
  public MutableSortedSet<HorizontalLine> findIntersectingBorders(VerticalLine rightBorder) {
    MutableSortedSet<HorizontalLine> intersectingHorizontalBorders = SortedSets.mutable
        .of(HORIZONTAL_LINE_VERTICAL_COMPARATOR);
    if (Math.abs(rightBorder.getLeft().getMagnitude() - this.leftBorder.getLeft().getMagnitude())
        < RectilinearPolygon.SEPARATION_EPSILON) {
      return intersectingHorizontalBorders;
    }
    double verticalLowerBound =
        rightBorder.getTop().getMagnitude() - RectilinearPolygon.SEPARATION_EPSILON;
    double verticalUpperBound = verticalLowerBound + rightBorder.getStretch().getMagnitude()
        + 2 * RectilinearPolygon.SEPARATION_EPSILON;

    if (Iterate.notEmpty(this.intersectingLines)) {
      double topHorizontalLineY = this.intersectingLines.getFirst().getTop().getMagnitude();
      double bottomHorizontalLineY = this.intersectingLines.getLast().getTop().getMagnitude();

      if (topHorizontalLineY < verticalUpperBound && bottomHorizontalLineY > verticalLowerBound) {
        MutableList<HorizontalLine> horizontalLinesWithinHeight = this.intersectingLines
            .subMap(verticalLowerBound, verticalUpperBound).toList();
        double rightBorderX = rightBorder.getLeft().getMagnitude();
        for (HorizontalLine horizontalLine : horizontalLinesWithinHeight) {
          double horizontalLineLeftX = horizontalLine.getLeft().getMagnitude();
          double horizontalLineRightX =
              horizontalLineLeftX + horizontalLine.getStretch().getMagnitude();
          if (rightBorderX >= (horizontalLineLeftX - RectilinearPolygon.SEPARATION_EPSILON)
              && rightBorderX <= (horizontalLineRightX + RectilinearPolygon.SEPARATION_EPSILON)) {
            intersectingHorizontalBorders.add(horizontalLine);
          }
        }
      }
    }
    return intersectingHorizontalBorders;
  }

  /**
   * Create all valid rectangles using {@code rightBorder} and {@code
   * intersectingHorizontalBorders}
   *
   * @param rightBorder vertical line representing right border
   * @param intersectingHorizontalBorders all horizontal lines intersecting with {@code
   * rightBorder}
   * @return all valid rectangles
   */
  public MutableList<Rectangle2D> createRectanglesWithRightBorder(VerticalLine rightBorder,
      MutableList<HorizontalLine> intersectingHorizontalBorders) {
    MutableList<Rectangle2D> constructedRectangles = Lists.mutable.empty();
    double rightBorderX = rightBorder.getLeft().getMagnitude();
    double leftBorderX = this.leftBorder.getLeft().getMagnitude();
    if (Math.abs(rightBorderX - leftBorderX) < RectilinearPolygon.SEPARATION_EPSILON) {
      return constructedRectangles;
    }
    for (int i = 1; i < intersectingHorizontalBorders.size(); i++) {
      HorizontalLine topBorder = intersectingHorizontalBorders.get(i - 1);
      HorizontalLine bottomBorder = intersectingHorizontalBorders.get(i);
      double topBorderY = topBorder.getTop().getMagnitude();
      double bottomBorderY = bottomBorder.getTop().getMagnitude();

      double width = rightBorderX - leftBorderX;
      double height = bottomBorderY - topBorderY;
      Rectangle2D rectangle = new Rectangle2D.Double(leftBorderX, topBorderY, width, height);
      if (RectilinearPolygon.isValidRectangle(rectangle)) {
        constructedRectangles.add(rectangle);
      }
    }
    return constructedRectangles;
  }

  /**
   * Remove {@code lines} from horizontal lines list which are supposed to form right side open
   * rectangles
   *
   * @param lines horizontal lines
   */
  public void removeRightSideClosedLines(Collection<HorizontalLine> lines) {
    this.rightSideOpenCandidates.removeAll(lines);
  }

  /**
   * Remove {@code lines} from horizontal lines list which are supposed to form left side open
   * rectangles
   *
   * @param lines horizontal lines
   */
  public void removeLeftSideClosedLines(Collection<HorizontalLine> lines) {
    this.leftSideOpenCandidates.removeAll(lines);
  }

  /**
   * Construct the largest right side open rectangle list
   *
   * @return the list containing single largest right side open rectangle.
   */
  public MutableList<OpenRectangle> getRightSideOpenRectangles() {
    MutableList<OpenRectangle> rightSideOpenRectangles = Lists.mutable.empty();
    if (this.rightSideOpenCandidates.notEmpty()) {
      HorizontalLine topBorder = this.rightSideOpenCandidates.getFirst();
      HorizontalLine bottomBorder = this.rightSideOpenCandidates.getLast();
      if (Math.abs(topBorder.getTop().getMagnitude() - bottomBorder.getTop().getMagnitude())
          > RectilinearPolygon.SEPARATION_EPSILON) {
        rightSideOpenRectangles.add(new OpenRectangle(OpenSide.RIGHT)
            .fixLeftBorder(this.leftBorder)
            .fixTopBorder(topBorder)
            .fixBottomBorder(bottomBorder));
      }
    }
    return rightSideOpenRectangles;
  }

  /**
   * Get horizontally open rectangles (open rectangles with top/bottom missing)
   *
   * @param rightBorder vertical line representing right border
   * @param intersectingBorders horizontal lines intersection with {@code rightBorder}
   * @return horizontally open rectangles
   */
  public MutableList<OpenRectangle> getHorizontallyOpenRectangles(VerticalLine rightBorder,
      MutableSortedSet<HorizontalLine> intersectingBorders) {
    MutableList<OpenRectangle> horizontallyOpenRectangles = Lists.mutable.of();
    if (Math.abs(rightBorder.getLeft().getMagnitude() - this.leftBorder.getLeft().getMagnitude())
        < RectilinearPolygon.SEPARATION_EPSILON) {
      return horizontallyOpenRectangles;
    }
    if (intersectingBorders.notEmpty()) {
      double leftBorderTopY = this.leftBorder.getTop().getMagnitude();
      double leftBorderBottomY = leftBorderTopY + this.leftBorder.getStretch().getMagnitude();
      double rightBorderTopY = rightBorder.getTop().getMagnitude();
      double rightBorderBottomY = rightBorderTopY + rightBorder.getStretch().getMagnitude();

      HorizontalLine topMostBorder = intersectingBorders.getFirst();
      double topMostBorderY = topMostBorder.getTop().getMagnitude();
      if (topMostBorderY > leftBorderTopY + RectilinearPolygon.SEPARATION_EPSILON
          && topMostBorderY > rightBorderTopY + RectilinearPolygon.SEPARATION_EPSILON) {
        horizontallyOpenRectangles.add(new OpenRectangle(OpenSide.TOP)
            .fixLeftBorder(this.leftBorder)
            .fixRightBorder(rightBorder)
            .fixBottomBorder(topMostBorder));
      }
      HorizontalLine bottomMostBorder = intersectingBorders.getLast();
      double bottomMostBorderY = bottomMostBorder.getTop().getMagnitude();
      if (bottomMostBorderY < leftBorderBottomY - RectilinearPolygon.SEPARATION_EPSILON
          && bottomMostBorderY < rightBorderBottomY - RectilinearPolygon.SEPARATION_EPSILON) {
        horizontallyOpenRectangles.add(new OpenRectangle(OpenSide.BOTTOM)
            .fixLeftBorder(this.leftBorder)
            .fixRightBorder(rightBorder)
            .fixTopBorder(bottomMostBorder));
      }
    }
    return horizontallyOpenRectangles;
  }

  public VerticalLine getLeftBorder() {
    return this.leftBorder;
  }

  public MutableSortedMap<Double, HorizontalLine> getIntersectingLines() {
    return this.intersectingLines;
  }
}
