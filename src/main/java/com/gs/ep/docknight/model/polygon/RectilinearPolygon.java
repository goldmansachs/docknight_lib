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
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.SortedSets;
import org.eclipse.collections.impl.utility.Iterate;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.VerticalLine;
import java.awt.geom.Rectangle2D;
import java.util.Comparator;

/**
 * Class to represent rectilinear polygon. A rectilinear polygon is a polygon all of whose edge
 * intersections are at right angles. For our use case, we only consider edges parallel to the
 * coordinate axes. Such polygons are also called axis-aligned rectilinear polygons.
 */
public class RectilinearPolygon {

  public static final double SEPARATION_EPSILON = 1.0;
  public static final double CONTEXT_LIMIT = 1000;
  /**
   * Comparator to compare abscissa of vertical lines. If they are equal, then compare the top
   * ordinates of vertical lines.
   */
  public static final Comparator<VerticalLine> VERTICAL_LINE_HORIZONTAL_COMPARATOR = new Comparator<VerticalLine>() {
    @Override
    public int compare(VerticalLine o1, VerticalLine o2) {
      double o1X = o1.getLeft().getMagnitude();
      double o2X = o2.getLeft().getMagnitude();
      if (o1X != o2X) {
        return Double.compare(o1X, o2X);
      }

      double o1TopY = o1.getTop().getMagnitude();
      double o2TopY = o2.getTop().getMagnitude();
      return Double.compare(o1TopY, o2TopY);
    }
  };
  /**
   * Comparator to compare ordinate of horizontal lines. If they are equal, then compare the left
   * abscissa of horizontal lines.
   */
  public static final Comparator<HorizontalLine> HORIZONTAL_LINE_VERTICAL_COMPARATOR = new Comparator<HorizontalLine>() {
    @Override
    public int compare(HorizontalLine o1, HorizontalLine o2) {
      double o1Y = o1.getTop().getMagnitude();
      double o2Y = o2.getTop().getMagnitude();
      if (o1Y != o2Y) {
        return Double.compare(o1Y, o2Y);
      }
      double o1LeftX = o1.getLeft().getMagnitude();
      double o2LeftX = o2.getLeft().getMagnitude();
      return Double.compare(o1LeftX, o2LeftX);
    }
  };
  private MutableSortedSet<HorizontalLine> horizontalBorders;
  private MutableSortedSet<VerticalLine> verticalBorders;
  private MutableList<Rectangle2D> enclosedRectangles;

  public RectilinearPolygon(Rectangle2D candidateRectangle) {
    this.horizontalBorders = SortedSets.mutable.of(HORIZONTAL_LINE_VERTICAL_COMPARATOR);
    this.verticalBorders = SortedSets.mutable.of(VERTICAL_LINE_HORIZONTAL_COMPARATOR);
    this.enclosedRectangles = Lists.mutable.of(candidateRectangle);

    this.verticalBorders.add(VerticalLine
        .from(candidateRectangle.getMinY(), candidateRectangle.getMinX(),
            candidateRectangle.getHeight()));
    this.verticalBorders.add(VerticalLine
        .from(candidateRectangle.getMinY(), candidateRectangle.getMaxX(),
            candidateRectangle.getHeight()));
    this.horizontalBorders.add(HorizontalLine
        .from(candidateRectangle.getMinY(), candidateRectangle.getMinX(),
            candidateRectangle.getWidth()));
    this.horizontalBorders.add(HorizontalLine
        .from(candidateRectangle.getMaxY(), candidateRectangle.getMinX(),
            candidateRectangle.getWidth()));
  }

  /**
   * Construct the rectilinear polygons using all the input rectangles {@code boundingBoxes}.
   *
   * @param boundingBoxes input rectangles.
   * @return constructed rectilinear polygons.
   */
  public static MutableList<RectilinearPolygon> buildRectilinearPolygons(
      MutableList<Rectangle2D> boundingBoxes) {
    MutableList<RectilinearPolygon> rectilinearPolygons = Lists.mutable.empty();
    MutableList<Rectangle2D> boundingBoxesSorted = boundingBoxes.toSortedList(Comparator
        .comparingDouble(Rectangle2D::getMinY).thenComparingDouble(Rectangle2D::getMinX));
    for (Rectangle2D boundingBox : boundingBoxesSorted) {
      boolean isIncluded = false;
      for (RectilinearPolygon rectilinearPolygon : rectilinearPolygons) {
        isIncluded = rectilinearPolygon.includeRectangleIfPossible(boundingBox) || isIncluded;
      }
      if (!isIncluded) {
        RectilinearPolygon rectilinearPolygon = new RectilinearPolygon(boundingBox);
        rectilinearPolygons.add(rectilinearPolygon);
      }
    }
    return rectilinearPolygons;
  }

  /**
   * Check if the rectangle is valid or not. The rectangle is valid if contains width and length and
   * length of both of them is greater than 1 unit.
   *
   * @param rectangle rectangle which will be checked.
   * @return boolean flag indicating whether rectangle is valid or not.
   */
  public static boolean isValidRectangle(Rectangle2D rectangle) {
    return rectangle.getWidth() > RectilinearPolygon.SEPARATION_EPSILON
        && rectangle.getHeight() > RectilinearPolygon.SEPARATION_EPSILON;
  }

  /**
   * Include the {@code candidateRectangle} in current polygon if it's any border is adjacent to
   * already contained rectangles inside this polygon.
   *
   * @param candidateRectangle candidate rectangle which is being checked for inclusion .
   * @return boolean flag whether candidate rectangle is included or not.
   */
  public boolean includeRectangleIfPossible(Rectangle2D candidateRectangle) {
    if (!this.isRectangleVerticallyOverlapping(candidateRectangle)) {
      return false;
    }
    boolean isTopBorderIncluded = this
        .includeHorizontalBorderIfRectangleAdjacent(candidateRectangle, HorizontalBorderType.TOP);
    boolean isBottomBorderIncluded = this
        .includeHorizontalBorderIfRectangleAdjacent(candidateRectangle,
            HorizontalBorderType.BOTTOM);
    boolean isLeftBorderIncluded = this
        .includeVerticalBorderIfRectangleAdjacent(candidateRectangle, VerticalBorderType.LEFT);
    boolean isRightBorderIncluded = this
        .includeVerticalBorderIfRectangleAdjacent(candidateRectangle, VerticalBorderType.RIGHT);
    if (isLeftBorderIncluded || isRightBorderIncluded || isTopBorderIncluded
        || isBottomBorderIncluded) {
      this.enclosedRectangles.add(candidateRectangle);
      return true;
    }
    return false;
  }

  /**
   * Check if {@code candidateRectangle} overlaps with the current polygon vertically.
   *
   * @param candidateRectangle rectangle to check.
   * @return boolean flag indicating whether {@code candidateRectangle} overlaps with the current
   * polygon vertically.
   */
  public boolean isRectangleVerticallyOverlapping(Rectangle2D candidateRectangle) {
    double topMostY = this.horizontalBorders.getFirst().getTop().getMagnitude();
    double bottomMostY = this.horizontalBorders.getLast().getTop().getMagnitude();
    return (candidateRectangle.getMinY() < bottomMostY + SEPARATION_EPSILON
        && candidateRectangle.getMaxY() > topMostY - SEPARATION_EPSILON);
  }

  /**
   * Check whether {@code candidateAdjacentRectangle}'s {@code candidateBorderType} is adjacent to
   * existing horizontal borders of this polygon
   *
   * @param candidateAdjacentRectangle rectangle whose border is to check
   * @param candidateBorderType border type (top/bottom) of the candidate rectangle
   * @return boolean flag if candidate rectangle's horizontal border type is adjacent to existing
   * borders of this polygon
   */
  private boolean includeHorizontalBorderIfRectangleAdjacent(Rectangle2D candidateAdjacentRectangle,
      HorizontalBorderType candidateBorderType) {
    boolean isAdjacent = false;
    double rectangleCandidateBorderY = candidateBorderType.getBorder(candidateAdjacentRectangle)
        .getTop().getMagnitude();
    double rectangleOppositeBorderY = candidateBorderType
        .getOppositeBorder(candidateAdjacentRectangle).getTop().getMagnitude();
    double rectangleBorderLeftX = candidateAdjacentRectangle.getMinX();
    double rectangleBorderRightX = candidateAdjacentRectangle.getMaxX();
    double rectangleWidth = candidateAdjacentRectangle.getWidth();

    double verticalLowerBound = rectangleOppositeBorderY - SEPARATION_EPSILON;
    double verticalUpperBound = rectangleOppositeBorderY + SEPARATION_EPSILON;

    if (Iterate.notEmpty(this.horizontalBorders)) {
      double topBorderY = this.horizontalBorders.getFirst().getTop().getMagnitude();
      double bottomBorderY = this.horizontalBorders.getLast().getTop().getMagnitude();

      if (topBorderY < verticalUpperBound && bottomBorderY > verticalLowerBound) {
        HorizontalLine lowerBound = HorizontalLine
            .from(verticalLowerBound, rectangleBorderLeftX, rectangleWidth);
        HorizontalLine upperBound = HorizontalLine
            .from(verticalUpperBound, rectangleBorderLeftX, rectangleWidth);
        MutableList<HorizontalLine> horizontallyCollinearBorders = this.horizontalBorders
            .subSet(lowerBound, upperBound).toList();
        for (HorizontalLine collinearBorder : horizontallyCollinearBorders) {
          double collinearBorderLeftX = collinearBorder.getLeft().getMagnitude();
          double collinearBorderRightX =
              collinearBorderLeftX + collinearBorder.getStretch().getMagnitude();

          if (collinearBorderLeftX <= rectangleBorderRightX
              && collinearBorderRightX >= rectangleBorderLeftX) {
            isAdjacent = true;
            break;
          }
        }
        if (isAdjacent) {
          this.horizontalBorders.add(
              HorizontalLine.from(rectangleCandidateBorderY, rectangleBorderLeftX, rectangleWidth));
        }
      }
    }
    return isAdjacent;
  }

  /**
   * Check whether {@code candidateAdjacentRectangle}'s {@code candidateBorderType} is adjacent to
   * existing vertical borders of this polygon
   *
   * @param candidateAdjacentRectangle rectangle whose border is to check
   * @param candidateBorderType border type (left/right) of the candidate rectangle
   * @return boolean flag if candidate rectangle's vertical border type is adjacent to existing
   * borders of this polygon
   */
  private boolean includeVerticalBorderIfRectangleAdjacent(Rectangle2D candidateAdjacentRectangle,
      VerticalBorderType candidateBorderType) {
    boolean isAdjacent = false;
    double rectangleCandidateBorderX = candidateBorderType.getBorder(candidateAdjacentRectangle)
        .getLeft().getMagnitude();
    double rectangleOppositeBorderX = candidateBorderType
        .getOppositeBorder(candidateAdjacentRectangle).getLeft().getMagnitude();
    double rectangleBorderTopY = candidateAdjacentRectangle.getMinY();
    double rectangleBorderBottomY = candidateAdjacentRectangle.getMaxY();
    double rectangleHeight = candidateAdjacentRectangle.getHeight();

    double horizontalLowerBound = rectangleOppositeBorderX - SEPARATION_EPSILON;
    double horizontalUpperBound = rectangleOppositeBorderX + SEPARATION_EPSILON;

    if (Iterate.notEmpty(this.verticalBorders)) {
      double leftBorderX = this.verticalBorders.getFirst().getLeft().getMagnitude();
      double rightBorderX = this.verticalBorders.getLast().getLeft().getMagnitude();

      if (leftBorderX < horizontalUpperBound && rightBorderX > horizontalLowerBound) {
        VerticalLine dummyLowerBound = VerticalLine
            .from(rectangleBorderTopY, horizontalLowerBound, rectangleHeight);
        VerticalLine upperBound = VerticalLine
            .from(rectangleBorderTopY, horizontalUpperBound, rectangleHeight);
        MutableList<VerticalLine> verticallyCollinearBorders = this.verticalBorders
            .subSet(dummyLowerBound, upperBound).toList();
        for (VerticalLine collinearBorder : verticallyCollinearBorders) {
          double collinearBorderTopY = collinearBorder.getTop().getMagnitude();
          double collinearBorderBottomY =
              collinearBorderTopY + collinearBorder.getStretch().getMagnitude();

          if (collinearBorderTopY <= rectangleBorderBottomY
              && collinearBorderBottomY >= rectangleBorderTopY) {
            isAdjacent = true;
            break;
          }
        }
        if (isAdjacent) {
          this.verticalBorders.add(
              VerticalLine.from(rectangleBorderTopY, rectangleCandidateBorderX, rectangleHeight));
        }
      }
    }
    return isAdjacent;
  }

  /**
   * Get the bounding rectangle for the current polygon
   *
   * @return bounding rectangle for the current polygon
   */
  public Rectangle2D getBoundingRectangle() {
    if (this.enclosedRectangles.isEmpty()) {
      return null;
    }
    double boundingBoxLeftX = this.verticalBorders.getFirst().getLeft().getMagnitude();
    double boundingBoxRightX = this.verticalBorders.getLast().getLeft().getMagnitude();
    double boundingBoxTopY = this.horizontalBorders.getFirst().getTop().getMagnitude();
    double boundingBoxBottomY = this.horizontalBorders.getLast().getTop().getMagnitude();
    return new Rectangle2D.Double(boundingBoxLeftX, boundingBoxTopY,
        boundingBoxRightX - boundingBoxLeftX, boundingBoxBottomY - boundingBoxTopY);
  }

  public MutableList<Rectangle2D> getEnclosedRectangles() {
    return this.enclosedRectangles;
  }

  /**
   * Calculate number of rows. Number of rows is defined as number of distinct horizontal lines-1
   *
   * @return number of rows
   */
  public int getNumberOfRows() {
    return this.horizontalBorders.collect(horizontalLine -> horizontalLine.getTop().getMagnitude())
        .distinct().size() - 1;
  }

  /**
   * Calculate number of columns. Number of columns is defined as number of distinct vertical
   * lines-1
   *
   * @return number of columns
   */
  public int getNumberOfColumns() {
    return this.verticalBorders.collect(verticalLine -> verticalLine.getLeft().getMagnitude())
        .distinct().size() - 1;
  }

  /**
   * Class to represent horizontal borders
   */
  private enum HorizontalBorderType {
    TOP {
      @Override
      public HorizontalLine getBorder(Rectangle2D rectangle) {
        return HorizontalLine.from(rectangle.getMinY(), rectangle.getMinX(), rectangle.getWidth());
      }

      @Override
      public HorizontalLine getOppositeBorder(Rectangle2D rectangle) {
        return HorizontalLine.from(rectangle.getMaxY(), rectangle.getMinX(), rectangle.getWidth());
      }
    },
    BOTTOM {
      @Override
      public HorizontalLine getBorder(Rectangle2D rectangle) {
        return HorizontalLine.from(rectangle.getMaxY(), rectangle.getMinX(), rectangle.getWidth());
      }

      @Override
      public HorizontalLine getOppositeBorder(Rectangle2D rectangle) {
        return HorizontalLine.from(rectangle.getMinY(), rectangle.getMinX(), rectangle.getWidth());
      }
    };

    public abstract HorizontalLine getBorder(Rectangle2D rectangle);

    public abstract HorizontalLine getOppositeBorder(Rectangle2D rectangle);
  }

  /**
   * Class to represent vertical borders
   */
  private enum VerticalBorderType {
    LEFT {
      @Override
      public VerticalLine getBorder(Rectangle2D rectangle) {
        return VerticalLine.from(rectangle.getMinY(), rectangle.getMinX(), rectangle.getHeight());
      }

      @Override
      public VerticalLine getOppositeBorder(Rectangle2D rectangle) {
        return VerticalLine.from(rectangle.getMinY(), rectangle.getMaxX(), rectangle.getHeight());
      }
    },
    RIGHT {
      @Override
      public VerticalLine getBorder(Rectangle2D rectangle) {
        return VerticalLine.from(rectangle.getMinY(), rectangle.getMaxX(), rectangle.getHeight());
      }

      @Override
      public VerticalLine getOppositeBorder(Rectangle2D rectangle) {
        return VerticalLine.from(rectangle.getMinY(), rectangle.getMinX(), rectangle.getHeight());
      }
    };

    public abstract VerticalLine getBorder(Rectangle2D rectangle);

    public abstract VerticalLine getOppositeBorder(Rectangle2D rectangle);
  }
}
