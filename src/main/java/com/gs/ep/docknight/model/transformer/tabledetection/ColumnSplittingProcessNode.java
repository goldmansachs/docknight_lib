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

package com.gs.ep.docknight.model.transformer.tabledetection;

import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.RectangleProperties;
import com.gs.ep.docknight.model.TabularCellElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.util.List;
import java.util.ListIterator;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.partition.list.PartitionList;
import org.eclipse.collections.impl.factory.Lists;

/**
 * Process node in table detection workflow to split columns
 */
public class ColumnSplittingProcessNode extends AbstractProcessNode {

  /**
   * Populate {@code nextColHeaderCell} and {@code currColHeaderCell} using parition list
   * {@splitColumnHeaders}
   *
   * @param splitColumnHeaders list where selected elements represent elements which can not be
   * connected to its immediate left element
   * @param currColHeaderCell table cell representing header in current column
   * @param nextColHeaderCell table cell representing header in next column
   */
  private static void populateHeaderCells(PartitionList<Element> splitColumnHeaders,
      TabularCellElementGroup<Element> currColHeaderCell,
      TabularCellElementGroup<Element> nextColHeaderCell) {
    nextColHeaderCell.getElements().addAll(splitColumnHeaders.getSelected().toList());
    currColHeaderCell.getElements().clear();
    currColHeaderCell.getElements().addAll(splitColumnHeaders.getRejected().toList());
  }

  /**
   * Reassign the elements from {@code currCell} to {@code newCell} if the element's abscissa range
   * overlaps more with new column width.
   *
   * @param currColHeaderBoundingBox - bounding box of current column header cell
   * @param nextColHeaderBoundingBox - bounding box of new column header cell
   * @param currCell - cell whose elements are being reassigned
   * @param newCell - cell where elements are being reassigned to.
   */
  private static void reassignElementToBestOverlappingCol(
      RectangleProperties<Double> currColHeaderBoundingBox,
      RectangleProperties<Double> nextColHeaderBoundingBox,
      TabularCellElementGroup<Element> currCell, TabularCellElementGroup<Element> newCell) {
    if (currCell.getElements().notEmpty()) {
      ListIterator<Element> currCellElementsIterator = currCell.getElements().listIterator();
      while (currCellElementsIterator.hasNext()) {
        Element element = currCellElementsIterator.next();
        RectangleProperties<Double> elementBoundingBox = getElementBoundaries(element);
        double currColOverlapWidth = getHorizontalOverlapWidth(elementBoundingBox,
            currColHeaderBoundingBox);
        double nextColOverlapWidth = getHorizontalOverlapWidth(elementBoundingBox,
            nextColHeaderBoundingBox);
        if (currColOverlapWidth < nextColOverlapWidth) {
          newCell.add(element);
          currCellElementsIterator.remove();
        }
      }
    }
  }

  /**
   * Calculate bounding box coordinate of {@code element}
   *
   * @param element Element whose bounding box coordinates are being calculated
   * @return bounding box coordinates of {@code element}
   */
  private static RectangleProperties<Double> getElementBoundaries(Element element) {
    double top = element.getAttribute(Top.class).getValue().getMagnitude();
    double bottom = top + element.getAttribute(Height.class).getValue().getMagnitude();
    double left = element.getAttribute(Left.class).getValue().getMagnitude();
    double right = left + element.getAttribute(Width.class).getValue().getMagnitude();
    return new RectangleProperties<>(top, right, bottom, left);
  }

  /**
   * Calculate width of overlapping portion between {@code box1} abscissa coordinates and {@code
   * box2} abscissa coordinates
   *
   * @param box1 - first bounding box
   * @param box2 - second bounding box
   * @return overlapping width
   */
  private static double getHorizontalOverlapWidth(RectangleProperties<Double> box1,
      RectangleProperties<Double> box2) {
    return Math.max(0.0,
        Math.min(box1.getRight(), box2.getRight()) - Math.max(box1.getLeft(), box2.getLeft()));
  }

  /**
   * Partition the {@ocde currCell} elements into two lists where selected elements represent
   * elements which can not be connected to its left elements
   *
   * @param currCell cell whose elements are being partitioned
   * @return partitioned list
   */
  private static PartitionList<Element> makeColumnPartition(
      TabularCellElementGroup<Element> currCell) {
    MutableList<Element> elementList = currCell.getElements();
    return elementList.partition(element ->
    {
      Element adjacentLeftElement = element.getPositionalContext().getShadowedLeftElement();
      return adjacentLeftElement != null && elementList.contains(adjacentLeftElement)
          && !SemanticsChecker
          .canBeConnected(adjacentLeftElement.getTextStr(), element.getTextStr());
    });
  }

  @Override
  public List getRequiredKeys() {
    return Lists.mutable.of(
        CustomScratchpadKeys.SPLIT_TABULAR_GROUPS,
        CustomScratchpadKeys.HEADER_CONFIDENCE,
        CustomScratchpadKeys.IS_SPLIT_PERMISSIBLE,
        CustomScratchpadKeys.DOCUMENT_SOURCE,
        CustomScratchpadKeys.PAGE_NUMBER,
        CustomScratchpadKeys.TABLE_INDEX,
        CustomScratchpadKeys.SPLIT_ROW_INDEX,
        CustomScratchpadKeys.IS_PARENT_TABLE
    );
  }

  @Override
  public List getStoredKeys() {
    return Lists.mutable.of(
        CustomScratchpadKeys.END_RESULT
    );
  }

  @Override
  public void execute(Scratchpad scratchpad) {
    this.scratchpad = scratchpad;
    List<TabularElementGroup<Element>> splitTabularGroups = CustomScratchpadKeys.SPLIT_TABULAR_GROUPS
        .retrieveFrom(scratchpad);
    if (scratchpad.retrieveBoolean(CustomScratchpadKeys.IS_PARENT_TABLE)) {
      splitTabularGroups.forEach(this::splitMergedColumns);
    }
    scratchpad.store(CustomScratchpadKeys.END_RESULT, splitTabularGroups);
  }

  /**
   * Split the columns in table {@code tabularGroup} based on column headers. Example: Nested column
   * headers
   *
   * @param tabularGroup - table whose columns have to be split.
   */
  private void splitMergedColumns(TabularElementGroup<Element> tabularGroup) {
    if (tabularGroup.getColumnHeaderCount() == 0) {
      return;
    }
    for (int currColIndex = 0; currColIndex < tabularGroup.numberOfColumns(); currColIndex++) {
      int lastHeaderRowIndex = tabularGroup.getColumnHeaderCount() - 1;
      TabularCellElementGroup<Element> headerCell = tabularGroup.getCells().get(lastHeaderRowIndex)
          .get(currColIndex);
      RectangleProperties<Boolean> headerCellBorder = headerCell.getBorderExistence();
      if (!(headerCellBorder.getLeft() || headerCellBorder.getRight())) {
        PartitionList<Element> splitColumnHeaders = makeColumnPartition(headerCell);
        if (splitColumnHeaders.getRejected().notEmpty() && splitColumnHeaders.getSelected()
            .notEmpty()) {
          int nextColIndex = currColIndex + 1;
          if (nextColIndex < tabularGroup.numberOfColumns() && tabularGroup.getCells()
              .get(lastHeaderRowIndex).get(nextColIndex).getElements().isEmpty()) {
            // Splitting only the header
            this.logEntry(String.format("Doing only column header splitting at column No. '%d'.",
                currColIndex + 1));
            TabularCellElementGroup<Element> currCell = tabularGroup.getCells()
                .get(lastHeaderRowIndex).get(currColIndex);
            TabularCellElementGroup<Element> nextCell = tabularGroup.getCells()
                .get(lastHeaderRowIndex).get(nextColIndex);
            populateHeaderCells(splitColumnHeaders, currCell, nextCell);
          } else {
            // Splitting the last column header and all the rows below it.
            this.logEntry(String.format(
                "Doing column header splitting and content reorganization at Column No. '%d'.",
                currColIndex + 1));
            TabularCellElementGroup<Element> currCell = null;
            TabularCellElementGroup<Element> newCell = null;
            for (int currRowIndex = 0; currRowIndex <= lastHeaderRowIndex; currRowIndex++) {
              currCell = tabularGroup.getCells().get(currRowIndex).get(currColIndex);
              newCell = new TabularCellElementGroup<>(currCell.isHorizontallyMerged(),
                  currCell.isVerticallyMerged());
              tabularGroup.getCells().get(currRowIndex).add(nextColIndex, newCell);
            }

            //currCell and newCell can never be null as lastHeaderRowIndex >= 0
            populateHeaderCells(splitColumnHeaders, currCell, newCell);
            RectangleProperties<Double> currColHeaderBoundingBox = currCell.getTextBoundingBox();
            RectangleProperties<Double> nextColHeaderBoundingBox = newCell.getTextBoundingBox();
            for (int currRowIndex = lastHeaderRowIndex + 1;
                currRowIndex < tabularGroup.numberOfRows(); currRowIndex++) {
              currCell = tabularGroup.getCells().get(currRowIndex).get(currColIndex);
              newCell = new TabularCellElementGroup<>(currCell.isHorizontallyMerged(),
                  currCell.isVerticallyMerged());
              tabularGroup.getCells().get(currRowIndex).add(nextColIndex, newCell);
              reassignElementToBestOverlappingCol(currColHeaderBoundingBox,
                  nextColHeaderBoundingBox, currCell, newCell);
            }
          }
        }
      }
    }
    tabularGroup.setBackReferences();
  }
}
