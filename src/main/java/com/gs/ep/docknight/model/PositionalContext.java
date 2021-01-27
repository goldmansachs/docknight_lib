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

package com.gs.ep.docknight.model;

import org.eclipse.collections.api.LazyIterable;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.context.PagePartitionType;
import com.gs.ep.docknight.model.element.GraphicalElement;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Class which captures positional context (like neighbouring element information) for the element.
 */
public class PositionalContext<E extends Element> extends ElementListData implements Cloneable {

  public static final int SIGNIFICANT_ELEMENT_MIN_CHARS = 3;
  private static final long serialVersionUID = -3538842840234180456L;
  private static final double UNDER_AND_OVER_LINE_DISTANCE_FACTOR = 1;
  private final E self;
  private PagePartitionType pagePartitionType = PagePartitionType.CONTENT;
  private double visualTop;
  private double visualBottom;
  private double visualLeft;
  private double visualRight;
  private boolean isVisualTopBorder;
  private boolean isVisualBottomBorder;
  private boolean isVisualLeftBorder;
  private boolean isVisualRightBorder;
  private double alignmentLeft;
  private double alignmentRight;
  @SuppressWarnings("NonSerializableFieldInSerializableClass")
  private Rectangle2D boundingRectangle;
  private ElementGroup<E> belowElements;
  private ElementGroup<E> aboveElements;
  private E shadowedBelowElement;
  private E shadowedAboveElement;
  private ElementGroup<E> leftElements;
  private ElementGroup<E> rightElements;
  private E shadowedLeftElement;
  private E shadowedRightElement;
  private ElementGroup<E> verticalGroup;
  private TabularElementGroup<E> tabularGroup;
  private Integer tabularRow;
  private Integer tabularColumn;

  public PositionalContext(E self) {
    this.self = self;
  }

  public int getPageBreakNumber() {
    PositionalElementList<Element> elementList = this.self.getElementList();
    return elementList.getPageBreakNumber(this.self);
  }


  @Override
  protected PositionalContext<E> clone() {
    PositionalContext<E> clonedPositionalContext = new PositionalContext<>(
        (E) this.self.getCurrentClone());
    clonedPositionalContext.pagePartitionType = this.pagePartitionType;
    clonedPositionalContext.visualTop = this.visualTop;
    clonedPositionalContext.visualBottom = this.visualBottom;
    clonedPositionalContext.visualLeft = this.visualLeft;
    clonedPositionalContext.visualRight = this.visualRight;
    clonedPositionalContext.alignmentLeft = this.alignmentLeft;
    clonedPositionalContext.alignmentRight = this.alignmentRight;
    clonedPositionalContext.isVisualTopBorder = this.isVisualTopBorder;
    clonedPositionalContext.isVisualBottomBorder = this.isVisualBottomBorder;
    clonedPositionalContext.isVisualLeftBorder = this.isVisualLeftBorder;
    clonedPositionalContext.isVisualRightBorder = this.isVisualRightBorder;

    if (this.belowElements != null) {
      clonedPositionalContext.belowElements = this.belowElements.getCurrentClone();
    }
    if (this.aboveElements != null) {
      clonedPositionalContext.aboveElements = this.aboveElements.getCurrentClone();
    }
    if (this.leftElements != null) {
      clonedPositionalContext.leftElements = this.leftElements.getCurrentClone();
    }
    if (this.rightElements != null) {
      clonedPositionalContext.rightElements = this.rightElements.getCurrentClone();
    }
    if (this.boundingRectangle != null) {
      clonedPositionalContext.boundingRectangle = (Rectangle2D) this.boundingRectangle.clone();
    }
    if (this.shadowedBelowElement != null) {
      clonedPositionalContext.shadowedBelowElement = (E) this.shadowedBelowElement
          .getCurrentClone();
    }
    if (this.shadowedAboveElement != null) {
      clonedPositionalContext.shadowedAboveElement = (E) this.shadowedAboveElement
          .getCurrentClone();
    }
    if (this.shadowedLeftElement != null) {
      clonedPositionalContext.shadowedLeftElement = (E) this.shadowedLeftElement.getCurrentClone();
    }
    if (this.shadowedRightElement != null) {
      clonedPositionalContext.shadowedRightElement = (E) this.shadowedRightElement
          .getCurrentClone();
    }
    if (this.verticalGroup != null) {
      clonedPositionalContext.verticalGroup = this.verticalGroup.getCurrentClone();
    }
    if (this.tabularGroup != null) {
      clonedPositionalContext.tabularGroup = this.tabularGroup.getCurrentClone();
    }
    if (this.tabularRow != null) {
      clonedPositionalContext.tabularRow = this.tabularRow;
    }
    if (this.tabularColumn != null) {
      clonedPositionalContext.tabularColumn = this.tabularColumn;
    }
    return clonedPositionalContext;
  }

  @Override
  public E getSelf() {
    return this.self;
  }

  public PagePartitionType getPagePartitionType() {
    return this.pagePartitionType;
  }

  public void setPagePartitionType(PagePartitionType pagePartitionType) {
    this.pagePartitionType = pagePartitionType;
  }

  public double getVisualTop() {
    return this.visualTop;
  }

  public void setVisualTop(double visualTop) {
    this.visualTop = visualTop;
  }

  public double getVisualBottom() {
    return this.visualBottom;
  }

  public void setVisualBottom(double visualBottom) {
    this.visualBottom = visualBottom;
  }

  public double getVisualLeft() {
    return this.visualLeft;
  }

  public void setVisualLeft(double visualLeft) {
    this.visualLeft = visualLeft;
  }

  public double getVisualRight() {
    return this.visualRight;
  }

  public void setVisualRight(double visualRight) {
    this.visualRight = visualRight;
  }

  public boolean isVisualTopBorder() {
    return this.isVisualTopBorder;
  }

  public void setVisualTopBorder(boolean visualTopBorder) {
    this.isVisualTopBorder = visualTopBorder;
  }

  public boolean isVisualBottomBorder() {
    return this.isVisualBottomBorder;
  }

  public void setVisualBottomBorder(boolean visualBottomBorder) {
    this.isVisualBottomBorder = visualBottomBorder;
  }

  public boolean isVisualLeftBorder() {
    return this.isVisualLeftBorder;
  }

  public void setVisualLeftBorder(boolean visualLeftBorder) {
    this.isVisualLeftBorder = visualLeftBorder;
  }

  public boolean isVisualRightBorder() {
    return this.isVisualRightBorder;
  }

  public void setVisualRightBorder(boolean visualRightBorder) {
    this.isVisualRightBorder = visualRightBorder;
  }


  public double getAlignmentRight() {
    return this.alignmentRight;
  }

  public void setAlignmentRight(double alignmentRight) {
    this.alignmentRight = alignmentRight;
  }

  public double getAlignmentLeft() {
    return this.alignmentLeft;
  }

  public void setAlignmentLeft(double alignmentLeft) {
    this.alignmentLeft = alignmentLeft;
  }

  public Rectangle2D getBoundingRectangle() {
    return this.boundingRectangle;
  }

  public void setBoundingRectangle(Rectangle2D boundingRectangle) {
    this.boundingRectangle = boundingRectangle;
  }

  public ElementGroup<E> getBelowElements() {
    return this.belowElements;
  }

  public void setBelowElements(ElementGroup<E> belowElements) {
    this.belowElements = belowElements;
  }

  public ElementGroup<E> getAboveElements() {
    return this.aboveElements;
  }

  public void setAboveElements(ElementGroup<E> aboveElements) {
    this.aboveElements = aboveElements;
  }

  public E getShadowedBelowElement() {
    return this.shadowedBelowElement;
  }

  public void setShadowedBelowElement(E shadowedBelowElement) {
    this.shadowedBelowElement = shadowedBelowElement;
  }

  public E getShadowedAboveElement() {
    return this.shadowedAboveElement;
  }

  public void setShadowedAboveElement(E shadowedAboveElement) {
    this.shadowedAboveElement = shadowedAboveElement;
  }

  public ElementGroup<E> getLeftElements() {
    return this.leftElements;
  }

  public void setLeftElements(ElementGroup<E> leftElements) {
    this.leftElements = leftElements;
  }

  public ElementGroup<E> getRightElements() {
    return this.rightElements;
  }

  public void setRightElements(ElementGroup<E> rightElements) {
    this.rightElements = rightElements;
  }

  public E getShadowedLeftElement() {
    return this.shadowedLeftElement;
  }

  public void setShadowedLeftElement(E shadowedLeftElement) {
    this.shadowedLeftElement = shadowedLeftElement;
  }

  public E getShadowedRightElement() {
    return this.shadowedRightElement;
  }

  public void setShadowedRightElement(E shadowedRightElement) {
    this.shadowedRightElement = shadowedRightElement;
  }

  public ElementGroup<E> getVerticalGroup() {
    return this.verticalGroup;
  }

  public void setVerticalGroup(ElementGroup<E> verticalGroup) {
    this.verticalGroup = verticalGroup;
  }


  public TabularElementGroup<E> getTabularGroup() {
    return this.tabularGroup;
  }

  public void setTabularGroup(TabularElementGroup<E> tabularGroup) {
    this.tabularGroup = tabularGroup;
  }

  public Integer getTabularRow() {
    return this.tabularRow;
  }

  public void setTabularRow(Integer tabularRow) {
    this.tabularRow = tabularRow;
  }

  public Integer getTabularColumn() {
    return this.tabularColumn;
  }

  public void setTabularColumn(Integer tabularColumn) {
    this.tabularColumn = tabularColumn;
  }


  @Override
  public String toString() {
    return this.self.getTextStr();
  }

  /**
   * @return True if the current element is underlined else return False
   */
  public boolean hasUnderlinedBorder() {
    double acceptableUnderlinedBorderThreshold =
        UNDER_AND_OVER_LINE_DISTANCE_FACTOR * this.self.getAttribute(FontSize.class).getMagnitude();
    double bottomBorderDistanceFromElement =
        this.visualBottom - this.self.getAttribute(Top.class).getMagnitude() - this.self
            .getAttribute(Height.class).getMagnitude();
    if (this.isVisualBottomBorder
        && Double.compare(bottomBorderDistanceFromElement, acceptableUnderlinedBorderThreshold)
        < 0) {
      return this.shadowedBelowElement == null || Double.compare(bottomBorderDistanceFromElement,
          this.shadowedBelowElement.getAttribute(Top.class).getMagnitude() - this.visualBottom) < 0;
    }
    return false;
  }

  /**
   * @return True if the current element is underlined else return False
   */
  public boolean hasOverlinedBorder() {
    double acceptableOverlinedBorderThreshold =
        UNDER_AND_OVER_LINE_DISTANCE_FACTOR * this.self.getAttribute(FontSize.class).getMagnitude();
    double topBorderDistanceFromElement =
        this.self.getAttribute(Top.class).getMagnitude() - this.visualTop;
    if (this.isVisualTopBorder
        && Double.compare(topBorderDistanceFromElement, acceptableOverlinedBorderThreshold) < 0) {
      return this.shadowedAboveElement == null ||
          Double.compare(topBorderDistanceFromElement,
              this.visualTop - this.shadowedAboveElement.getAttribute(Top.class).getMagnitude()
                  - this.shadowedAboveElement.getAttribute(Height.class).getMagnitude()) < 0;
    }
    return false;
  }

  /**
   * Returns all the previous siblings of current element
   */
  public LazyIterable<E> getPreviousElements() {
    Pair<ElementList<Element>, Integer> elementListContext = this.getSelf().getElementListContext();
    return (LazyIterable<E>) ListAdapter
        .adapt(elementListContext.getOne().getElements().subList(0, elementListContext.getTwo()))
        .asReversed()
        .select(e -> !(e instanceof GraphicalElement));
  }

  /**
   * Set the table properties in this context to null
   */
  public void deleteTableReference() {
    this.setTabularGroup(null);
    this.setTabularRow(null);
    this.setTabularColumn(null);
  }

  /**
   * Checks if current element is a plural header. Plural header is a header which has nested
   * headers below it. Algo: If count of below elements which are horizontally aligned is greater
   * than 1, then current element is plural header
   *
   * @return True if below elements forms nested header else return False
   */
  public boolean isPluralHeader() {
    if (this.belowElements.size() > 1) {
      E firstElement = this.belowElements.getFirst();
      return this.belowElements.getElements()
          .count(e -> e.getTextStr().length() >= SIGNIFICANT_ELEMENT_MIN_CHARS &&
              PositionalElementList.compareByHorizontalAlignment(e, firstElement) == 0) > 1;
    }
    return false;
  }
}
