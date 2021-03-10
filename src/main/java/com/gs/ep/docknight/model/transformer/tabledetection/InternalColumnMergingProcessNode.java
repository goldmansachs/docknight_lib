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
import com.gs.ep.docknight.model.TabularCellElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.util.List;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;

/**
 * Process node in table detection workflow to merge columns
 */
public class InternalColumnMergingProcessNode extends AbstractProcessNode {

  /**
   * Max width of right column to be merged.
   */
  private static final double MAX_MERGEABLE_COLUMN_WIDTH = 10.8;

  /**
   * Checks whether column at index {@code currColIndex} in table {@code tabularGroup} can be merged
   * with column present immediately right to it.
   *
   * @param tabularGroup table whose columns are being checked for merging criteria
   * @param currColIndex column index of first column which is being checked for merging
   * @return boolean flag indicating whether column can be merged to next column or not
   */
  private static boolean isColMergeableWithNextAdjacent(TabularElementGroup<Element> tabularGroup,
      int currColIndex) {
    int nextColIndex = currColIndex + 1;
    if (nextColIndex >= tabularGroup.numberOfColumns()) {
      return false;
    }
    int currRowIndex = 0;
    for (MutableList<TabularCellElementGroup<Element>> currRow : tabularGroup.getCells()) {
      TabularCellElementGroup<Element> currCell = currRow.get(currColIndex);
      TabularCellElementGroup<Element> nextCell = currRow.get(nextColIndex);
      if (!hasNarrowContent(nextCell)) {
        return false;
      }
      if (currRowIndex == tabularGroup.getColumnHeaderCount() - 1) {
        if (!(currCell.getElements().notEmpty() && nextCell.getElements().isEmpty())) {
          return false;
        }
      } else if (!areTableCellsMergeable(currCell, nextCell)) {
        return false;
      }
      currRowIndex++;
    }
    return true;
  }

  /**
   * Merge column at index {@code currColIndex} of table {@ocde tabularGroup} with the column
   * immediately right to it.
   *
   * @param tabularGroup table whose column has to be merged.
   * @param currColIndex index of first column which has to be merged.
   */
  private static void mergeColWithNextAdjacent(TabularElementGroup<Element> tabularGroup,
      int currColIndex) {
    int nextColIndex = currColIndex + 1;
    if (nextColIndex < tabularGroup.numberOfColumns()) {
      tabularGroup.getCells().each(row ->
      {
        row.get(currColIndex).getElements().addAll(row.get(nextColIndex).getElements());
        row.remove(nextColIndex);
      });
    }
  }

  /**
   * Narrow content means the cell content occupies very less width (less than {@see
   * com.gs.ep.docknight.model.transformer.tabledetection.InternalColumnMergingProcessNode#MAX_MERGEABLE_COLUMN_WIDTH})
   *
   * @param cell cell whose content is being checked.
   * @return boolean flag indicating whether cell content is narrow or not.
   */
  private static boolean hasNarrowContent(TabularCellElementGroup<Element> cell) {
    return cell.getElements().allSatisfy(
        element -> element.getAttribute(Width.class).getValue().getMagnitude()
            <= MAX_MERGEABLE_COLUMN_WIDTH);
  }

  /**
   * Checks whether cell1 and cell2 can be merged or not. They are mergeable if either of them
   * contains empty text or if cell1 text is semantically incomplete
   *
   * @param cell1 cell present in left column
   * @param cell2 cell present in right column (next column to cell1)
   * @return boolean flag indicating whether cell1 and cell2 are mergeable or not.
   */
  public static boolean areTableCellsMergeable(TabularCellElementGroup<Element> cell1,
      TabularCellElementGroup<Element> cell2) {
    return cell1.getTextStr().isEmpty()
        || cell2.getTextStr().isEmpty()
        || (cell1.getElements().size() == 1 && SemanticsChecker
        .isSemanticallyIncomplete(cell1.getFirst().getTextStr()));
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
        CustomScratchpadKeys.SPLIT_TABULAR_GROUPS
    );
  }

  @Override
  public void execute(Scratchpad scratchpad) {
    this.scratchpad = scratchpad;
    List<TabularElementGroup<Element>> splitTabularGroups = CustomScratchpadKeys.SPLIT_TABULAR_GROUPS
        .retrieveFrom(scratchpad);
    if (scratchpad.retrieveBoolean(CustomScratchpadKeys.IS_PARENT_TABLE)) {
      splitTabularGroups.forEach(this::mergeInternalColumns);
    }
    scratchpad.store(CustomScratchpadKeys.SPLIT_TABULAR_GROUPS, splitTabularGroups);
  }

  private void mergeInternalColumns(TabularElementGroup<Element> tabularGroup) {
    int currColIndex = 0;
    boolean isModified = false;
    while (currColIndex < tabularGroup.numberOfColumns() - 1) {
      if (isColMergeableWithNextAdjacent(tabularGroup, currColIndex)) {
        this.logEntry(String.format("Merging column '%d' with next column.", currColIndex + 1));
        mergeColWithNextAdjacent(tabularGroup, currColIndex);
        isModified = true;
      } else {
        currColIndex++;
      }
    }
    if (isModified) {
      tabularGroup.setBackReferences();
    }
  }
}
