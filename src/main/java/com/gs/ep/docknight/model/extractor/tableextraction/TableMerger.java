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

package com.gs.ep.docknight.model.extractor.tableextraction;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup.VectorTag;
import com.gs.ep.docknight.model.context.PagePartitionType;
import com.gs.ep.docknight.model.element.TextElement;

/**
 * Class representing methods to merge tables within {@see com.gs.ep.docknight.model.element.Document
 * document}
 */
public final class TableMerger {

  private TableMerger() {
  }

  /**
   * Merge the tables in the document.
   *
   * @param tablesInDocument tables in the document
   */
  public static void mergeTables(MutableList<TabularElementGroup<Element>> tablesInDocument) {
    for (int tableIndex = 1; tableIndex < tablesInDocument.size(); tableIndex++) {
      TabularElementGroup<Element> secondTable = tablesInDocument.get(tableIndex);
      MutableList<MutableList<? extends ElementGroup<Element>>> secondTableRows = secondTable
          .getMergedRows();
      TabularElementGroup<Element> firstTable = tablesInDocument.get(tableIndex - 1);
      MutableList<MutableList<? extends ElementGroup<Element>>> firstTableRows = firstTable
          .getMergedRows();

      if (shouldMergeTables(firstTable, firstTableRows, secondTable, secondTableRows)) {
        tablesInDocument.remove(secondTable);
        for (int i = secondTable.getColumnHeaderCount(); i < secondTable.numberOfRows(); i++) {
          firstTable.addRow(secondTable.getCells().get(i), firstTable.numberOfRows());
        }
        for (VectorTag tag : VectorTag.values()) {
          MutableSet<Integer> rowsForTagInSecondTable = secondTable.getVectorIndicesForTag(tag);
          rowsForTagInSecondTable
              .each(index -> firstTable.addVectorTag(tag, index + firstTableRows.size()));
        }
      }
    }
  }

  /**
   * Checks if first table can be merged with second table. Tables can be merged if it satisfies
   * following conditions: <ol> <li>Number of columns is equal in both tables</li> <li>First table
   * should have headers</li> <li>There should not be any content present between two tables</li>
   * <li>If second table has headers, then either second table headers should be a part of first
   * table's header or (vice versa)</li> </ol>
   *
   * @param firstTable first table
   * @param firstTableRows merged rows of first table
   * @param secondTable second table
   * @param secondTableRows merged rows of second table
   * @return boolean flag indicating whether first table can be merged with second table
   */
  private static boolean shouldMergeTables(TabularElementGroup<Element> firstTable,
      MutableList<MutableList<? extends ElementGroup<Element>>> firstTableRows,
      TabularElementGroup<Element> secondTable,
      MutableList<MutableList<? extends ElementGroup<Element>>> secondTableRows) {
    // Match column count
    if (secondTable.numberOfColumns() != firstTable.numberOfColumns()) {
      return false;
    }
    // Merge only if the first table has headers
    if (firstTable.getColumnHeaderCount() == 0) {
      return false;
    }
    // If the second table doesn't have headers then skip this validation
    if (secondTable.getColumnHeaderCount() != 0) {
      int firstTableHeaderIndex = 0;
      int secondTableHeaderIndex = -1;
      while (firstTableHeaderIndex < firstTable.getColumnHeaderCount()) {
        for (int currentHeaderIndex = 0; currentHeaderIndex < secondTable.getColumnHeaderCount();
            currentHeaderIndex++) {
          if (firstTableRows.get(firstTableHeaderIndex).makeString()
              .equals(secondTableRows.get(currentHeaderIndex).makeString())) {
            secondTableHeaderIndex = currentHeaderIndex;
            break;
          }
        }
        if (secondTableHeaderIndex != -1) {
          break;
        }
        firstTableHeaderIndex++;
      }
      // Abandon table merging if no headers match from first table and second table
      if (secondTableHeaderIndex == -1) {
        return false;
      }
      int matchedHeaderCount = 1;
      while (firstTableHeaderIndex < firstTable.getColumnHeaderCount()
          && secondTableHeaderIndex < secondTable.getColumnHeaderCount()) {
        if (!firstTableRows.get(firstTableHeaderIndex).makeString()
            .equals(secondTableRows.get(secondTableHeaderIndex).makeString())) {
          break;
        }
        firstTableHeaderIndex++;
        secondTableHeaderIndex++;
        matchedHeaderCount++;
      }

      // If column headers from the table with minimum column header count do not match with other table's header, return false
      if (matchedHeaderCount < (
          Math.min(firstTable.getColumnHeaderCount(), secondTable.getColumnHeaderCount()) - 1)) {
        return false;
      }
    }
    // If there is any content between the two tables then don't merge
    return firstTable.getElements().getLast().getElementListIndex() < secondTable.getElements()
        .getFirst().getElementListIndex() && !ListAdapter.adapt(
        firstTable.getElements().getFirst().getElementList().getElements()
            .subList(firstTable.getElements().getLast().getElementListIndex() + 1,
                secondTable.getElements().getFirst().getElementListIndex()))
        .selectInstancesOf(TextElement.class).anySatisfy(
            e -> e.getPositionalContext().getPagePartitionType().equals(PagePartitionType.CONTENT));
  }
}
