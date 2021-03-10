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

import org.eclipse.collections.api.block.predicate.Predicate2;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.VerticalLine;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

/**
 * Class to represent open rectangle. Open rectangle is a rectangle with one side missing.
 */
public class OpenRectangle {

  private VerticalLine leftBorder;
  private VerticalLine rightBorder;
  private HorizontalLine topBorder;
  private HorizontalLine bottomBorder;
  private OpenSide openSide;

  public OpenRectangle(OpenSide openSide) {
    this.openSide = openSide;
  }

  /**
   * Construct a rectangle by combining {@code openSideBottom} and {@code openSideTop}.
   *
   * @param openSideBottom open rectangle with bottom side missing.
   * @param openSideTop open rectangle with top side missing.
   * @return list of valid rectangles ( openSidebottom closed rectangle, combined rectangle,
   * openSideTop closed rectangle)
   */
  private static MutableList<Rectangle2D> combineHorizontallyOpenRectangles(
      OpenRectangle openSideBottom, OpenRectangle openSideTop) {
    if (Math.abs(
        openSideBottom.leftBorder.getLeft().getMagnitude() - openSideTop.leftBorder.getLeft()
            .getMagnitude()) < RectilinearPolygon.SEPARATION_EPSILON &&
        Math.abs(
            openSideBottom.rightBorder.getLeft().getMagnitude() - openSideTop.rightBorder.getLeft()
                .getMagnitude()) < RectilinearPolygon.SEPARATION_EPSILON) {
      Rectangle2D aboveRectangle = openSideBottom.createClosedRectangle();
      Rectangle2D belowRectangle = openSideTop.createClosedRectangle();
      double topY = aboveRectangle.getMaxY();
      double bottomY = belowRectangle.getMinY();
      double leftX = aboveRectangle.getMinX();
      double rightX = aboveRectangle.getMaxX();
      Rectangle2D middleRectangle = new Rectangle2D.Double(leftX, topY, rightX - leftX,
          bottomY - topY);
      return Lists.mutable.of(aboveRectangle, middleRectangle, belowRectangle)
          .select(RectilinearPolygon::isValidRectangle);
    }
    return Lists.mutable.empty();
  }

  /**
   * Combine horizontally open rectangles (bottom open rectangle, top open rectangle) and get their
   * rectangle information
   *
   * @param horizontallyOpenRectangles list containing horizontally open rectangles
   * @param combineCondition predicate which return true if (aboveLine, belowLines) can be
   * combined.
   * @return updated rectangle information
   */
  public static MutableList<Rectangle2D> combineHorizontallyOpenRectangles(
      MutableList<OpenRectangle> horizontallyOpenRectangles,
      Predicate2<VerticalLine, VerticalLine> combineCondition) {
    MutableList<OpenRectangle> bottomOpenRectangles = horizontallyOpenRectangles
        .select(openRectangle -> openRectangle.getOpenSide() == OpenSide.BOTTOM);
    MutableList<OpenRectangle> topOpenRectangles = horizontallyOpenRectangles
        .select(openRectangle -> openRectangle.getOpenSide() == OpenSide.TOP);
    MutableList<VerticalLine> verticalLinesFromBottomOpenRectangles = bottomOpenRectangles
        .flatCollect(OpenRectangle::getVerticalBorders);
    MutableSortedSet<VerticalLine> verticalLinesFromTopOpenRectangles = topOpenRectangles
        .flatCollect(OpenRectangle::getVerticalBorders)
        .toSortedSet(RectilinearPolygon.VERTICAL_LINE_HORIZONTAL_COMPARATOR);

    MutableSet<Element> combinedVerticalLines = Sets.mutable
        .ofAll(horizontallyOpenRectangles.flatCollect(OpenRectangle::getVerticalBorders));
    if (verticalLinesFromTopOpenRectangles.notEmpty()) {
      for (VerticalLine pivotLine : verticalLinesFromBottomOpenRectangles) {
        double pivotLineX = pivotLine.getLeft().getMagnitude();
        double pivotLineBeginY = pivotLine.getTop().getMagnitude();
        double pivotLineEndY = pivotLineBeginY + pivotLine.getStretch().getMagnitude();

        double leftMostLine = verticalLinesFromTopOpenRectangles.getFirst().getLeft()
            .getMagnitude();
        double rightMostLine = verticalLinesFromTopOpenRectangles.getLast().getLeft()
            .getMagnitude();
        if (pivotLineX + RectilinearPolygon.SEPARATION_EPSILON >= leftMostLine
            && pivotLineX - RectilinearPolygon.SEPARATION_EPSILON < rightMostLine) {
          VerticalLine dummyLowerBound = VerticalLine
              .from(pivotLineBeginY - RectilinearPolygon.CONTEXT_LIMIT,
                  pivotLineX - RectilinearPolygon.SEPARATION_EPSILON,
                  RectilinearPolygon.SEPARATION_EPSILON);
          VerticalLine dummyUpperBound = VerticalLine
              .from(pivotLineEndY, pivotLineX + RectilinearPolygon.SEPARATION_EPSILON,
                  RectilinearPolygon.SEPARATION_EPSILON);
          MutableSortedSet<VerticalLine> collinearLines = verticalLinesFromTopOpenRectangles
              .subSet(dummyLowerBound, dummyUpperBound);
          // collinearLines contains all the lines which are vertically aligned with pivot Line
          for (VerticalLine collinearLine : collinearLines) {
            double collinearLineBeginY = collinearLine.getTop().getMagnitude();
            double collinearLineEndY =
                collinearLineBeginY + collinearLine.getStretch().getMagnitude();

            // Combine the vertical lines if combine condition is satisfied
            if (collinearLineBeginY + RectilinearPolygon.SEPARATION_EPSILON > pivotLineEndY
                && combineCondition.accept(pivotLine, collinearLine)) {
              combinedVerticalLines.remove(pivotLine);
              combinedVerticalLines.remove(collinearLine);
              combinedVerticalLines.add((Element) VerticalLine
                  .from(pivotLineBeginY, pivotLineX, collinearLineEndY - pivotLineBeginY));
            }
          }
        }
      }
    }

    MutableSet<Element> horizontalLines = horizontallyOpenRectangles
        .flatCollect(OpenRectangle::getHorizontalBorders).collect(line -> (Element) line).toSet();
    RectangleFinder rectangleFinder = new RectangleFinder(horizontalLines, combinedVerticalLines);
    MutableList<Rectangle2D> combinedRectangles = rectangleFinder.getFoundRectangles();
    MutableList<Rectangle2D> internalRectangles = findInternalRectangles(combinedRectangles,
        horizontallyOpenRectangles.flatCollect(OpenRectangle::getVerticalBorders));
    return combinedRectangles.withAll(internalRectangles);
  }

  public static MutableList<Rectangle2D> findInternalRectangles(
      MutableList<Rectangle2D> combinedRectangles, MutableList<VerticalLine> brokenLines) {
    MutableList<Rectangle2D> internalRectangles = Lists.mutable.empty();
    MutableSortedSet<VerticalLine> sortedBrokenLines = brokenLines
        .toSortedSet(RectilinearPolygon.VERTICAL_LINE_HORIZONTAL_COMPARATOR);
    for (Rectangle2D rectangle : combinedRectangles) {
      VerticalLine dummyLowerBound = VerticalLine
          .from(rectangle.getMinY() - RectilinearPolygon.SEPARATION_EPSILON,
              rectangle.getMinX() - RectilinearPolygon.SEPARATION_EPSILON, 0.1);
      VerticalLine dummyUpperBound = VerticalLine
          .from(rectangle.getMaxY() + RectilinearPolygon.SEPARATION_EPSILON,
              rectangle.getMaxX() + RectilinearPolygon.SEPARATION_EPSILON, 0.1);
      MutableSortedSet<VerticalLine> matchingLines = sortedBrokenLines
          .subSet(dummyLowerBound, dummyUpperBound);
      for (VerticalLine matchingLine : matchingLines) {
        internalRectangles.add(
            new Rectangle2D.Double(rectangle.getMinX(), matchingLine.getTop().getMagnitude(),
                rectangle.getWidth(), matchingLine.getStretch().getMagnitude()));
      }
    }
    return internalRectangles;
  }

  /**
   * Setter for leftBorder vertical line
   *
   * @param leftBorder left border vertical line
   * @return this open rectangle
   */
  public OpenRectangle fixLeftBorder(VerticalLine leftBorder) {
    if (this.openSide.equals(OpenSide.LEFT)) {
      throw new IllegalArgumentException(
          "Left Border can not be set for OpenSide.LEFT OpenRectangle");
    }
    this.leftBorder = leftBorder;
    return this;
  }

  /**
   * Setter for rightBorder vertical line
   *
   * @param rightBorder right border vertical line
   * @return this open rectangle
   */
  public OpenRectangle fixRightBorder(VerticalLine rightBorder) {
    if (this.openSide.equals(OpenSide.RIGHT)) {
      throw new IllegalArgumentException(
          "Right Border can not be set for OpenSide.RIGHT OpenRectangle");
    }
    this.rightBorder = rightBorder;
    return this;
  }

  /**
   * Setter for topBorder horizontal line
   *
   * @param topBorder top border horizontal line
   * @return this open rectangle
   */
  public OpenRectangle fixTopBorder(HorizontalLine topBorder) {
    if (this.openSide.equals(OpenSide.TOP)) {
      throw new IllegalArgumentException(
          "Top Border can not be set for OpenSide.TOP OpenRectangle");
    }
    this.topBorder = topBorder;
    return this;
  }

  /**
   * Setter for bottomBorder horizontal line
   *
   * @param bottomBorder left border horizontal line
   * @return this open rectangle
   */
  public OpenRectangle fixBottomBorder(HorizontalLine bottomBorder) {
    if (this.openSide.equals(OpenSide.BOTTOM)) {
      throw new IllegalArgumentException(
          "Bottom Border can not be set for OpenSide.BOTTOM OpenRectangle");
    }
    this.bottomBorder = bottomBorder;
    return this;
  }

  /**
   * Get the width of this rectangle
   *
   * @return width of this rectangle
   */
  public double findWidth() {
    if (this.openSide.equals(OpenSide.LEFT) || this.openSide.equals(OpenSide.RIGHT)) {
      throw new IllegalArgumentException(
          "Width can not be calculated for OpenSide." + this.openSide.name() + " OpenRectangle");
    }
    return this.rightBorder.getLeft().getMagnitude() - this.leftBorder.getLeft().getMagnitude();
  }

  /**
   * Get the height of this rectangle
   *
   * @return height of this rectangle
   */
  public double findHeight() {
    if (this.openSide.equals(OpenSide.TOP) || this.openSide.equals(OpenSide.BOTTOM)) {
      throw new IllegalArgumentException(
          "Height can not be calculated for OpenSide." + this.openSide.name() + " OpenRectangle");
    }
    return this.bottomBorder.getTop().getMagnitude() - this.leftBorder.getTop().getMagnitude();
  }

  /**
   * Find the border element that can complete this rectangle
   *
   * @return missing border element
   */
  public Element findClosingBorder() {
    return this.openSide.findClosingBorder(this);
  }

  /**
   * Get the rectangle object corresponding to this object.
   *
   * @return rectangle object corresponding to this object.
   */
  public Rectangle2D createClosedRectangle() {
    VerticalLine leftBorder = this.leftBorder;
    VerticalLine rightBorder = this.rightBorder;
    HorizontalLine topBorder = this.topBorder;
    HorizontalLine bottomBorder = this.bottomBorder;
    switch (this.openSide) {
      case TOP:
        topBorder = (HorizontalLine) this.findClosingBorder();
        break;
      case BOTTOM:
        bottomBorder = (HorizontalLine) this.findClosingBorder();
        break;
      case LEFT:
        leftBorder = (VerticalLine) this.findClosingBorder();
        break;
      case RIGHT:
        rightBorder = (VerticalLine) this.findClosingBorder();
        break;
    }

    double leftX = leftBorder.getLeft().getMagnitude();
    double rightX = rightBorder.getLeft().getMagnitude();
    double topY = topBorder.getTop().getMagnitude();
    double bottomY = bottomBorder.getTop().getMagnitude();
    return new Rectangle2D.Double(leftX, topY, rightX - leftX, bottomY - topY);
  }

  /**
   * Return vertical borders of this rectangle
   *
   * @return vertical borders
   */
  public MutableList<VerticalLine> getVerticalBorders() {
    return Lists.mutable.of(this.leftBorder, this.rightBorder).select(Objects::nonNull);
  }

  /**
   * Return horizontal borders of this rectangle
   *
   * @return horizontal borders
   */
  public MutableList<HorizontalLine> getHorizontalBorders() {
    return Lists.mutable.of(this.topBorder, this.bottomBorder).select(Objects::nonNull);
  }

  public OpenSide getOpenSide() {
    return this.openSide;
  }

  /**
   * Class to represent open size
   */
  public enum OpenSide {
    BOTTOM {
      @Override
      OpenSide getComplementaryOpenSide() {
        return TOP;
      }

      @Override
      MutableList<Rectangle2D> combineRectangles(OpenRectangle openRectangle,
          OpenRectangle oppositeOpenRectangle) {
        return combineHorizontallyOpenRectangles(openRectangle, oppositeOpenRectangle);
      }

      @Override
      Element findClosingBorder(OpenRectangle openRectangle) {
        double bottomY = Math.min(
            openRectangle.leftBorder.getTop().getMagnitude() + openRectangle.leftBorder.getStretch()
                .getMagnitude(),
            openRectangle.rightBorder.getTop().getMagnitude() + openRectangle.rightBorder
                .getStretch().getMagnitude());
        return HorizontalLine.from(bottomY, openRectangle.leftBorder.getLeft().getMagnitude(),
            openRectangle.findWidth());
      }
    },
    TOP {
      @Override
      OpenSide getComplementaryOpenSide() {
        return BOTTOM;
      }

      @Override
      MutableList<Rectangle2D> combineRectangles(OpenRectangle openRectangle,
          OpenRectangle oppositeOpenRectangle) {
        return combineHorizontallyOpenRectangles(oppositeOpenRectangle, openRectangle);
      }

      @Override
      Element findClosingBorder(OpenRectangle openRectangle) {
        double topY = Math.max(openRectangle.leftBorder.getTop().getMagnitude(),
            openRectangle.rightBorder.getTop().getMagnitude());
        return HorizontalLine.from(topY, openRectangle.leftBorder.getLeft().getMagnitude(),
            openRectangle.findWidth());
      }
    },
    RIGHT {
      @Override
      OpenSide getComplementaryOpenSide() {
        return LEFT;
      }

      @Override
      MutableList<Rectangle2D> combineRectangles(OpenRectangle openRectangle,
          OpenRectangle oppositeOpenRectangle) {
        return Lists.mutable.empty();
      }

      @Override
      Element findClosingBorder(OpenRectangle openRectangle) {
        double rightX = Math.min(
            openRectangle.topBorder.getLeft().getMagnitude() + openRectangle.topBorder.getStretch()
                .getMagnitude(),
            openRectangle.bottomBorder.getLeft().getMagnitude() + openRectangle.bottomBorder
                .getStretch().getMagnitude());
        return VerticalLine.from(openRectangle.topBorder.getTop().getMagnitude(), rightX,
            openRectangle.findHeight());
      }
    },
    LEFT {
      @Override
      OpenSide getComplementaryOpenSide() {
        return RIGHT;
      }

      @Override
      MutableList<Rectangle2D> combineRectangles(OpenRectangle openRectangle,
          OpenRectangle oppositeOpenRectangle) {
        return Lists.mutable.empty();
      }

      @Override
      Element findClosingBorder(OpenRectangle openRectangle) {
        double leftX = Math.max(openRectangle.topBorder.getLeft().getMagnitude(),
            openRectangle.bottomBorder.getLeft().getMagnitude());
        return VerticalLine.from(openRectangle.topBorder.getTop().getMagnitude(), leftX,
            openRectangle.findHeight());
      }
    };

    /**
     * Find the opposite side of current open side
     *
     * @return opposite side of current open side
     */
    abstract OpenSide getComplementaryOpenSide();

    /**
     * Construct a rectangle by combining {@code openSideBottom} and {@code openSideTop}.
     *
     * @param openRectangle open rectangle with current side missing.
     * @param oppositeOpenRectangle open rectangle with opposite side missing.
     * @return list of valid rectangles ( openRectangle closed rectangle, combined rectangle,
     * oppositeOpenRectangle closed rectangle)
     */
    abstract MutableList<Rectangle2D> combineRectangles(OpenRectangle openRectangle,
        OpenRectangle oppositeOpenRectangle);

    /**
     * Find the border element that can complete the open rectangle
     *
     * @param openRectangle open rectangle
     * @return missing border element
     */
    abstract Element findClosingBorder(OpenRectangle openRectangle);
  }
}
