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
import org.eclipse.collections.impl.factory.Lists;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.element.FormElement;
import com.gs.ep.docknight.model.element.PageBreak;
import java.util.Collections;
import java.util.List;
import org.apache.pdfbox.util.QuickSort;

/**
 * Class containing list of elements and their positional properties like tables, paragraphs etx.
 */
public class PositionalElementList<E extends Element> extends ElementList<E> {

  private static final double ELEMENT_OVERLAP_EPSILON = 0.02;
  private static final long serialVersionUID = -6761197667480420193L;

  private List<ElementGroup<E>> verticalGroups;
  private MutableList<TabularElementGroup<E>> tabularGroups;
  private MutableList<Integer> pageBreakPositions;

  public PositionalElementList(List<E> elements, boolean sortByPosition) {
    if (sortByPosition) {
      List<E> inputElements = Lists.mutable.ofAll(elements);
      QuickSort
          .sort(inputElements, PositionalElementList::compareByHorizontalAndThenVerticalAlignment);
      this.setElements(inputElements);
    } else {
      this.setElements(elements);
    }

    this.verticalGroups = Lists.mutable.empty();
    this.tabularGroups = Lists.mutable.empty();
    this.pageBreakPositions = Lists.mutable.empty();

    int i = 0;
    for (E element : elements) {
      if (element instanceof PageBreak) {
        this.pageBreakPositions.add(i);
      }
      i++;
    }
  }

  public PositionalElementList(List<E> elements) {
    this(elements, true);
  }

  public PositionalElementList(E... elements) {
    this(Lists.mutable.of(elements));
  }

  /**
   * @return 0 If both elements are present on same horizontal line (horizontally aligned)
   * (determined using overlap threshold) otherwise if element1 is at higher position in document,
   * return negative else return positive
   */
  public static int compareByHorizontalAlignment(Element element1, Element element2) {
    if (!element1.hasAttribute(Top.class) || !element2.hasAttribute(Top.class)) {
      return 1;
    }

    double top1 = element1.getAttribute(Top.class).getValue().getMagnitude();
    Height height1 = element1.getAttribute(Height.class);
    double bottom1 = top1 + (height1 == null ? 1 : height1.getValue().getMagnitude());

    double top2 = element2.getAttribute(Top.class).getValue().getMagnitude();
    Height height2 = element2.getAttribute(Height.class);
    double bottom2 = top2 + (height2 == null ? 1 : height2.getValue().getMagnitude());

    double minHeight = Math.min(bottom1 - top1, bottom2 - top2);
    double overlapThreshold =
        element1 instanceof FormElement || element2 instanceof FormElement ? ELEMENT_OVERLAP_EPSILON
            : 0;
    double overlapValue = top1 >= top2 && top1 <= bottom2 ? getOverlap(bottom2, top1, minHeight) :
        top2 >= top1 && top2 <= bottom1 ? getOverlap(bottom1, top2, minHeight) : 0;

    return overlapValue > overlapThreshold ? 0
        : top1 < top2 ? Math.min((int) (top1 - top2), -1) : Math.max((int) (top1 - top2), 1);
  }

  /**
   * @return proportion of overlap between two elements using {@code aboveElementBottom} and {@code
   * belowElementTop}
   */
  private static double getOverlap(double aboveElementBottom, double belowElementTop,
      double height) {
    return (aboveElementBottom - belowElementTop) / height;
  }

  /**
   * @return 0 If both elements are present on same vertical line (vertically aligned) otherwise if
   * element1 is at left position than element2, return negative else return positive
   */
  public static int compareByVerticalAlignment(Element element1, Element element2) {
    double left1 = element1.getAttribute(Left.class).getValue().getMagnitude();
    Width width1 = element1.getAttribute(Width.class);
    double lineDelta1 = width1 == null ? 1 : width1.getValue().getMagnitude() / 2;

    double left2 = element2.getAttribute(Left.class).getValue().getMagnitude();
    Width width2 = element2.getAttribute(Width.class);
    double lineDelta2 = width2 == null ? 1 : width2.getValue().getMagnitude() / 2;

    boolean onSameLine = Math.abs(left1 - left2) <= Math.min(lineDelta1, lineDelta2);
    return onSameLine ? 0 : left1 < left2 ? -1 : 1;
  }

  /**
   * If elements {@code element1} and {@code element2} are horizontally aligned, use vertical
   * alignment for comparison else use horizontal alignment for comparison
   */
  public static int compareByHorizontalAndThenVerticalAlignment(Element element1,
      Element element2) {
    int lineSignum = compareByHorizontalAlignment(element1, element2);
    if (lineSignum == 0) {
      double left1 = element1.getAttribute(Left.class).getValue().getMagnitude();
      double left2 = element2.getAttribute(Left.class).getValue().getMagnitude();
      return Double.compare(left1, left2);
    } else {
      return lineSignum;
    }
  }

  @Override
  public void appendElement(E element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isPositionBased() {
    return true;
  }

  @Override
  public PositionalElementList<E> clone() {
    PositionalElementList<E> clonedElementList = (PositionalElementList<E>) super.clone();

    clonedElementList.verticalGroups = Lists.mutable.empty();
    for (ElementGroup<E> verticalGroup : this.verticalGroups) {
      clonedElementList.verticalGroups.add(verticalGroup.clone());
    }

    clonedElementList.tabularGroups = Lists.mutable.empty();
    for (TabularElementGroup<E> tabularGroup : this.tabularGroups) {
      clonedElementList.tabularGroups.add(tabularGroup.clone());
    }

    clonedElementList.pageBreakPositions = Lists.mutable.ofAll(this.pageBreakPositions);
    for (E clonedElement : clonedElementList.getElements()) {
      PositionalContext<E> positionalContext = clonedElement.getPositionalContext();
      if (positionalContext != null) {
        clonedElement.setPositionalContext(positionalContext.clone());
      }
    }

    return clonedElementList;
  }

  public void initializeContext(E element) {
    element.setPositionalContext(new PositionalContext<>(element));
  }

  public List<ElementGroup<E>> getVerticalGroups() {
    return this.verticalGroups;
  }

  public void addVerticalGroup(ElementGroup<E> verticalGroup) {
    this.verticalGroups.add(verticalGroup);
  }

  public MutableList<TabularElementGroup<E>> getTabularGroups() {
    return this.tabularGroups;
  }

  public void addTabularGroup(TabularElementGroup<E> tabularGroup) {
    this.tabularGroups.add(tabularGroup);
  }

  public void removeTabularGroup(TabularElementGroup<E> tabularGroup) {
    this.tabularGroups.remove(tabularGroup);
  }

  public int getNumberOfPageBreaks() {
    return this.pageBreakPositions.size();
  }

  /**
   * For a page with N page breaks: for elements before 1st page break -> return 0 for elements from
   * 1st page break till before 2nd page break -> 1 .......... for elements from Nth page break till
   * end -> N
   *
   * @return pageBreakNumber for element.
   */
  public int getPageBreakNumber(E element) {
    return Math
        .abs(Collections.binarySearch(this.pageBreakPositions, element.getElementListIndex()) + 1);
  }

  /**
   * Get the page break at position {@code pageBreakNumber}
   *
   * @param pageBreakNumber position of page break
   * @return page break at position {@code pageBreakNumber}
   */
  public PageBreak getPageBreak(int pageBreakNumber) {
    return this.getElements().get(this.getPageBreakPosition(pageBreakNumber)).as(PageBreak.class);
  }

  /**
   * Get the page break position of {@code pageBreakNumber} PageBreak. Page break position is the
   * index of page break element in element list
   *
   * @param pageBreakNumber - ith page break whose position has to be found.
   * @return index of {@code pageBreakNumber}th pageBreak in the element list
   */
  private int getPageBreakPosition(int pageBreakNumber) {
    return pageBreakNumber == 0 ? -1 :
        pageBreakNumber == this.getNumberOfPageBreaks() + 1 ? this.getElements().size()
            : this.pageBreakPositions.get(pageBreakNumber - 1);
  }

  /**
   * Get all the elements between two page breaks
   */
  public List<E> getElementsBetweenPageBreaks(int startPageBreakNumber, int endPageBreakNumber) {
    int startPageBreakPosition = this.getPageBreakPosition(startPageBreakNumber) + 1;
    int endPageBreakPosition = this.getPageBreakPosition(endPageBreakNumber);
    return this.getElements().subList(startPageBreakPosition, endPageBreakPosition);
  }

  /**
   * Find all elements from starting page till {@code endPageBreakNumber}
   *
   * @param endPageBreakNumber end page break number
   * @return all elements from starting page till {@code endPageBreakNumber}
   */
  public List<E> getElementsTillPageBreak(int endPageBreakNumber) {
    return this.getElementsBetweenPageBreaks(0, endPageBreakNumber);
  }

  /**
   * Find all elements from {@code startPageBreakNumber} till end
   *
   * @param startPageBreakNumber start page break number
   * @return all elements from {@code startPageBreakNumber} till end
   */
  public List<E> getElementsFromPageBreak(int startPageBreakNumber) {
    return this
        .getElementsBetweenPageBreaks(startPageBreakNumber, this.getNumberOfPageBreaks() + 1);
  }
}
