
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

import org.eclipse.collections.api.list.MutableList;

/**
 * Represents the cell element of the {@see TabularElementGroup}
 */
public class TabularCellElementGroup<E extends Element> extends ElementGroup<E> {

  private static final long serialVersionUID = -472243496625385026L;
  private boolean verticallyMerged;               // If this cell is merged with above cell in the table
  private boolean horizontallyMerged;          // If this cell is merged with left cell in the table

  public TabularCellElementGroup() {
    this.verticallyMerged = false;
    this.horizontallyMerged = false;
  }

  public TabularCellElementGroup(boolean verticallyMerged, boolean horizontallyMerged) {
    this.verticallyMerged = verticallyMerged;
    this.horizontallyMerged = horizontallyMerged;
  }

  /**
   * Check if cell element group has borders or not in all four directions
   */
  public RectangleProperties<Boolean> getBorderExistence() {
    boolean hasTopBorder = false;
    boolean hasRightBorder = false;
    boolean hasBottomBorder = false;
    boolean hasLeftBorder = false;
    for (E element : this.getElements()) {
      PositionalContext<E> positionalContext = element.getPositionalContext();
      if (positionalContext != null) {
        hasTopBorder = hasTopBorder || positionalContext.isVisualTopBorder();
        hasRightBorder = hasRightBorder || positionalContext.isVisualRightBorder();
        hasBottomBorder = hasBottomBorder || positionalContext.isVisualBottomBorder();
        hasLeftBorder = hasLeftBorder || positionalContext.isVisualLeftBorder();
      }
    }
    return new RectangleProperties<>(hasTopBorder, hasRightBorder, hasBottomBorder, hasLeftBorder);
  }

  public boolean isVerticallyMerged() {
    return this.verticallyMerged;
  }

  public void setVerticallyMerged(boolean verticallyMerged) {
    this.verticallyMerged = verticallyMerged;
  }

  public boolean isHorizontallyMerged() {
    return this.horizontallyMerged;
  }

  public void setHorizontallyMerged(boolean horizontallyMerged) {
    this.horizontallyMerged = horizontallyMerged;
  }

  @Override
  public TabularCellElementGroup<E> add(E element) {
    MutableList<E> elements = this.getElements();
    if (elements.isEmpty()) {
      elements.add(element);
    } else {
      int i;
      for (i = elements.size() - 1; i >= 0; i--) {
        E elem = elements.get(i);
        if (PositionalElementList.compareByHorizontalAndThenVerticalAlignment(element, elem) >= 0) {
          elements.add(i + 1, element);
          break;
        }
      }
      if (i == -1) {
        elements.add(0, element);
      }
    }
    return this;
  }
}
