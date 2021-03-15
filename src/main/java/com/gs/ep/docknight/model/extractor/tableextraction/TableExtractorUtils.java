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
import org.eclipse.collections.api.multimap.MutableMultimap;
import org.eclipse.collections.api.stack.MutableStack;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.factory.Stacks;
import org.eclipse.collections.impl.tuple.Tuples;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.extractor.TableExtractor;

/**
 * Utility class which contain helper methods for generating table map representation of {@see
 * com.gs.ep.docknight.model.element.Document document mode}.
 *
 * @see TableExtractor
 */
public final class TableExtractorUtils {

  private static final double THRESHOLD_INDENTATION = 0.5;

  private TableExtractorUtils() {
  }

  public static MutableList<TabularElementGroup<Element>> getEnrichedTables(
      MutableList<TabularElementGroup<Element>> tablesInDocument) {
    MutableList<TabularElementGroup<Element>> enrichedTables = tablesInDocument
        .collect(TableExpander::performColumnExpansion); // to get first level headers
    TableMerger.mergeTables(enrichedTables);
    enrichedTables = enrichedTables
        .collect(TableExpander::performColumnExpansion); // to get multi-column headers
    enrichedTables.each(TableExpander::performCaptionExpansion);
    return enrichedTables;
  }

  /**
   * Determine row heirarchy of the {@code tabularGroup}. RowHierarchy contains list of pairs where
   * first element is the hierarchy of row and second element is a map with two keys (parent and
   * child) and value of these keys contains parent and child row indexes respectively. RowHierarchy
   * list size is equal to number of rows in the table
   *
   * @param tabularGroup table
   * @return row hierarchy
   */
  public static MutableList<Pair<Integer, MutableMultimap<String, Integer>>> getRowHierarchy(
      TabularElementGroup<Element> tabularGroup) {
    int hierarchyLevel = 0;
    MutableList<Integer> indentations = Lists.mutable.empty();
    MutableList<Pair<Integer, MutableMultimap<String, Integer>>> rowHierarchy = Lists.mutable
        .empty();
    int rowIndex = 0;
    while (rowIndex <= tabularGroup
        .getColumnHeaderCount())   // TODO: Seems like a bug in this condition- equality condition should not be present
    {
      indentations.add(hierarchyLevel);
      rowHierarchy.add(Tuples.pair(hierarchyLevel, Multimaps.mutable.list.empty()));
      rowIndex++;
    }
    MutableStack<Double> indentationStack = Stacks.mutable.empty();
    while (rowIndex < tabularGroup.numberOfRows()) {
      if (tabularGroup.getMergedCell(rowIndex, 0).getElements().notEmpty()) {
        if (indentationStack.isEmpty()) {
          indentationStack.push(
              tabularGroup.getMergedCell(rowIndex, 0).getFirst().getAttribute(Left.class)
                  .getMagnitude());
        }
        double indentationShift =
            tabularGroup.getMergedCell(rowIndex, 0).getFirst().getAttribute(Left.class)
                .getMagnitude() - indentationStack.getFirst();
        if (indentationShift > THRESHOLD_INDENTATION) {
          hierarchyLevel += 1;
          indentationStack.push(
              tabularGroup.getMergedCell(rowIndex, 0).getFirst().getAttribute(Left.class)
                  .getMagnitude());
        } else if (indentationShift < -THRESHOLD_INDENTATION) {
          while (tabularGroup.getMergedCell(rowIndex, 0).getFirst().getAttribute(Left.class)
              .getMagnitude() - indentationStack.getFirst() < -THRESHOLD_INDENTATION) {
            hierarchyLevel -= 1;
            indentationStack.pop();
            if (indentationStack.isEmpty()) {
              indentationStack.push(
                  tabularGroup.getMergedCell(rowIndex, 0).getFirst().getAttribute(Left.class)
                      .getMagnitude());
            }
          }
        }
        if (hierarchyLevel < 0) {
          hierarchyLevel = 0;
        }
      }
      indentations.add(hierarchyLevel);
      MutableMultimap<String, Integer> rowMap = Multimaps.mutable.list.empty();
      MutableList<Integer> parentRows = getAllParentRows(indentations);
      rowMap.putAll(TableExtractor.MAP_KEY_PARENT, parentRows);
      rowHierarchy.add(Tuples.pair(hierarchyLevel, rowMap));
      for (Integer parentRow : parentRows) {
        rowHierarchy.get(parentRow).getTwo().put(TableExtractor.MAP_KEY_CHILD, rowIndex);
      }
      rowIndex++;
    }
    return rowHierarchy;
  }

  /**
   * Get all the parent rows. Parent rows are the rows which have lower left margin than last row.
   * Example: <ol start=0> <li>header1</li> <li>&nbsp;row1</li> <li>&nbsp;row2</li>
   * <li>&nbsp;&nbsp;row3</li> <li>&nbsp;&nbsp;row4</li> </ol> Return: [2,0]
   *
   * @param hierarchyLevels integer list representing heirarchy of all previous rows
   * @return parent rows
   */
  public static MutableList<Integer> getAllParentRows(MutableList<Integer> hierarchyLevels) {
    MutableList<Integer> parentRows = Lists.mutable.empty();
    for (int index = hierarchyLevels.size() - 2; index >= 0; index--) {
      if (hierarchyLevels.get(index) < hierarchyLevels.get(index + 1)) {
        parentRows.add(index);
      } else if (hierarchyLevels.get(index) > hierarchyLevels.get(index + 1)) {
        break;
      }
    }
    return parentRows;
  }

  /**
   * Find col span for cell at index ({@code rowIndex}, {@code colIndex}) in {@code
   * tabularElementGroup}. Col span represents number of columns right of current column (excluding
   * current column) that are merged with current column.
   *
   * @param tabularElementGroup table
   * @param rowIndex row index of table cell
   * @param colIndex column index of table cell
   * @return column span
   */
  public static int getColSpan(TabularElementGroup<Element> tabularElementGroup, int rowIndex,
      int colIndex) {
    int currIndex = colIndex + 1;
    while (currIndex < tabularElementGroup.numberOfColumns() && tabularElementGroup.getCells()
        .get(rowIndex).get(currIndex).isHorizontallyMerged()) {
      currIndex++;
    }
    return currIndex - colIndex;
  }

  /**
   * Find row span for cell at index ({@code rowIndex}, {@code colIndex}) in {@code
   * tabularElementGroup}. Row span represents number of rows below current row (excluding current
   * row) that are merged with current row.
   *
   * @param tabularElementGroup table
   * @param rowIndex row index of table cell
   * @param colIndex column index of table cell
   * @return row span
   */
  public static int getRowSpan(TabularElementGroup<Element> tabularElementGroup, int rowIndex,
      int colIndex) {
    int currIndex = rowIndex + 1;
    while (currIndex < tabularElementGroup.numberOfRows() && tabularElementGroup.getCells()
        .get(currIndex).get(colIndex).isVerticallyMerged()) {
      currIndex++;
    }
    return currIndex - rowIndex;
  }
}
