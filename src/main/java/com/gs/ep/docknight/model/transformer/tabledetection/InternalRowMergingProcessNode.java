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
import com.gs.ep.docknight.model.PositionalContext;
import com.gs.ep.docknight.model.TabularCellElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup.GridType;
import com.gs.ep.docknight.model.TabularElementGroup.TableType;
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;

/**
 * Process node in table detection workflow to merge rows
 */
public class InternalRowMergingProcessNode extends AbstractProcessNode {

  /* Matches strings ending with a COLON, or Strings with only four characters in brackets or strings with only a HYPHEN. */
  private static final Pattern TABLE_UNIQUE_CELL_PATTERN = Pattern.compile(":$|^\\(.{1,4}\\)$|^-$");
  /* Matches strings ending in dash, with atleast one non dash character */
  private static final Pattern DASH_WRAPPED_TEXT_PATTERN = Pattern
      .compile(".*[^\\p{Pd}].*\\p{Pd}$");

  private static final int MAX_MERGED_COLS = 2; // Maximum number of columns in table for its row to be merged if its tanle type is normal
  private static final double NON_GRID_COL_BOUNDARY_CLOSENESS_FACTOR = 0.15;
  private static final double GRID_COL_BOUNDARY_CLOSENESS_FACTOR = 0.1;
  private static final double NEXT_COL_BOUNDARY_CLOSENESS_FACTOR = 0.52;
  private static final double MIN_ROW_LEVEL_SYMMETRY_FOR_WRAPPING = 0.5;

  /**
   * @return True if row at {@code rowIndex1} of table {@code newTable} can be merged with row at
   * {@code rowIndex2} of table {@code oldTable}
   */
  private static boolean areRowsMergeable(TabularElementGroup<Element> oldTable,
      TabularElementGroup<Element> newTable, Pair<double[], double[]> columnBoundaries,
      int rowIndex1, int rowIndex2, double rowLevelSymmetry, TableType tableType) {
    int colCount = oldTable.numberOfColumns();
    if (Objects.equals(tableType, TableType.KEY_VALUE)) {
      MutableList<Element> belowElements = oldTable.getMergedCell(rowIndex2, 1).getElements();
      Element belowElement = belowElements.notEmpty() ? belowElements.get(0) : null;
      MutableList<Element> aboveElements = newTable.getMergedCell(rowIndex1, 1).getElements();
      Element aboveElement = aboveElements.notEmpty() ? aboveElements.get(0) : null;

      // Condition to merge rows with key value pairs if following conditions are satisfied-
      // Below row key element should be empty and below row value elment should not be number
      // Below row and above row belongs to same vertical group and does not have different visual style
      return oldTable.getCell(rowIndex2, 0).getElements()
          .allSatisfy(element -> element.getTextStr().isEmpty())
          && !oldTable.getCell(rowIndex2, 1).getElements().allSatisfy(
          element -> SemanticsChecker.isSemanticallyIncomplete(element.getTextStr())
              || SemanticsChecker.isAmountOrPercentage(element.getTextStr()))
          && (belowElement == null || aboveElement == null || (
          aboveElement.getPositionalContext().getVerticalGroup() == belowElement
              .getPositionalContext().getVerticalGroup()
              && !aboveElement.hasDifferentVisualStylesFromElement(belowElement)));
    } else if (Objects.equals(tableType, TableType.GRID_BASED)) {
      if (rowIndex2 <= oldTable.getColumnHeaderCount()) {
        return false;
      }
      double borderBelowAboveCellElements = -1;
      double borderAboveBelowCellElements = -1;
      for (int c = 0; c < colCount; c++) {
        MutableList<Element> belowCellElements = oldTable.getMergedCell(rowIndex2, c).getElements();
        MutableList<Element> aboveCellElements = newTable.getMergedCell(rowIndex1, c).getElements();
        if (aboveCellElements.notEmpty() && aboveCellElements.getLast().getPositionalContext()
            .isVisualBottomBorder()) {
          double border = aboveCellElements.getLast().getPositionalContext().getVisualBottom();
          if (borderBelowAboveCellElements == -1 || borderBelowAboveCellElements > border) {
            borderBelowAboveCellElements = border;
          }
        }
        if (belowCellElements.notEmpty() && belowCellElements.getFirst().getPositionalContext()
            .isVisualTopBorder()) {
          double border = belowCellElements.getFirst().getPositionalContext().getVisualTop();
          if (borderAboveBelowCellElements == -1 || borderAboveBelowCellElements < border) {
            borderAboveBelowCellElements = border;
          }
        }
      }
      // Rows are mergeable if there are no borders or if the border surrounds both the rows
      return borderBelowAboveCellElements == -1 || borderAboveBelowCellElements == -1
          || borderBelowAboveCellElements > borderAboveBelowCellElements;
    } else if (Objects.equals(tableType, TableType.FULLY_POPULATED)) {
      MutableList<TabularCellElementGroup<Element>> aboveRowCells = newTable.getCells()
          .get(rowIndex1);
      MutableList<TabularCellElementGroup<Element>> belowRowCells = oldTable.getCells()
          .get(rowIndex2);
      boolean onlyFirstAboveCellNonEmpty =
          !aboveRowCells.getFirst().getTextStr().isEmpty() && areAllCellsEmpty(
              aboveRowCells.subList(1, aboveRowCells.size()));
      boolean onlyFirstBelowCellNonEmpty =
          !belowRowCells.getFirst().getTextStr().isEmpty() && areAllCellsEmpty(
              belowRowCells.subList(1, belowRowCells.size()));
      Element belowElement = oldTable.getMergedCell(rowIndex2, 0).getElements().getFirst();
      Element aboveElement = newTable.getMergedCell(rowIndex1, 0).getElements().getFirst();

      // Rows are mergeable if following conditions are satisfied-
      // 1. Only first element in above cell is not empty and reset all are empty
      // 2. Below element should have non empty cell other than first position
      // 3. Both elements belongs to same vertical group and should havve same visual style
      // 4. Above element should not end with color or full stop
      // 5. Different in left coordinates of above and below element should be less than or equal to character's width
      return onlyFirstAboveCellNonEmpty && !onlyFirstBelowCellNonEmpty
          && aboveElement != null && belowElement != null
          && Double.compare(Math.abs(
          aboveElement.getAttribute(Left.class).getValue().getMagnitude() - belowElement
              .getAttribute(Left.class).getValue().getMagnitude()),
          aboveElement.getAttribute(Width.class).getMagnitude() / aboveElement.getTextStr()
              .length()) <= 0
          && Objects.equals(belowElement.getPositionalContext().getVerticalGroup(),
          aboveElement.getPositionalContext().getVerticalGroup())
          && !aboveElement.hasDifferentVisualStylesFromElement(belowElement)
          && !aboveElement.getTextStr().endsWith(":") && !aboveElement.getTextStr().endsWith(".");
    } else {
      int mergedCellCount = 0;
      for (int c = 0; c < colCount; c++) {
        MutableList<Element> belowCellElements = oldTable.getMergedCell(rowIndex2, c).getElements();
        MutableList<Element> aboveCellElements = newTable.getMergedCell(rowIndex1, c).getElements();
        if (belowCellElements.notEmpty() && aboveCellElements.notEmpty()) {
          if (!areCellsMergeable(aboveCellElements, belowCellElements, c, columnBoundaries,
              rowLevelSymmetry)) {
            return false;
          }
          mergedCellCount++;
        }
      }

      return mergedCellCount > 0 && mergedCellCount <= MAX_MERGED_COLS;
    }
  }

  /**
   * @return True if {@code aboveCellElements} and {@code belowCellElements} can be merged, else
   * return False
   */
  private static boolean areCellsMergeable(MutableList<Element> aboveCellElements,
      MutableList<Element> belowCellElements, int columnNum,
      Pair<double[], double[]> columnBoundaries, double rowLevelSymmetry) {
    Element firstAboveElement = aboveCellElements.get(0);
    Element firstBelowElement = belowCellElements.get(0);
    String firstAboveElementTextStr = firstAboveElement.getTextStr();
    String firstBelowElementTextStr = firstBelowElement.getTextStr();
    SemanticsChecker.RegexType aboveElementsRegexType = SemanticsChecker.RegexType
        .getFor(firstAboveElementTextStr);
    SemanticsChecker.RegexType belowElementsRegexType = SemanticsChecker.RegexType
        .getFor(firstBelowElementTextStr);

    return (DASH_WRAPPED_TEXT_PATTERN.matcher(firstAboveElementTextStr).matches()
        || aboveElementsRegexType != SemanticsChecker.RegexType.NUMERIC
        && belowElementsRegexType != SemanticsChecker.RegexType.NUMERIC
        && aboveElementsRegexType != SemanticsChecker.RegexType.DATE
        && belowElementsRegexType != SemanticsChecker.RegexType.DATE)
        && !firstAboveElementTextStr.endsWith(":") && !firstAboveElementTextStr.endsWith(".")
        && !(SemanticsChecker.containsOnlyIndex(firstAboveElementTextStr) && SemanticsChecker
        .containsOnlyIndex(firstBelowElementTextStr))
        && firstAboveElement.equalsAttributeValue(firstBelowElement, TextStyles.class, List::equals)
        && firstAboveElement
        .equalsAttributeValue(firstBelowElement, Color.class, java.awt.Color::equals)
        && !TABLE_UNIQUE_CELL_PATTERN.matcher(firstAboveElementTextStr).find()
        && firstAboveElement.getPositionalContext().getVerticalGroup() == firstBelowElement
        .getPositionalContext().getVerticalGroup()
        && (rowLevelSymmetry < MIN_ROW_LEVEL_SYMMETRY_FOR_WRAPPING || isTextWrappableAcrossRows(
        aboveCellElements, belowCellElements, columnNum, columnBoundaries));
  }

  /**
   * @return True if text from {@code aboveCellElements} can be wrapped across rows
   */
  private static boolean isTextWrappableAcrossRows(MutableList<Element> aboveCellElements,
      MutableList<Element> belowCellElements, int columnNum,
      Pair<double[], double[]> columnBoundaries) {
    double aboveCellRightBoundary = aboveCellElements.collect(
        e -> e.getAttribute(Left.class).getMagnitude() + e.getAttribute(Width.class).getMagnitude())
        .max();

    if (isGridBasedElement(aboveCellElements.getFirst())) {
      double aboveCellRightBoundaryIfNotWrapped =
          aboveCellRightBoundary + belowCellElements.getFirst().getAttribute(Width.class)
              .getMagnitude();
      double aboveCellGridRight = aboveCellElements.getFirst().getPositionalContext()
          .getVisualRight();
      double aboveCellGridWidth =
          aboveCellGridRight - aboveCellElements.getFirst().getPositionalContext().getVisualLeft();
      return aboveCellRightBoundaryIfNotWrapped
          > aboveCellGridRight - GRID_COL_BOUNDARY_CLOSENESS_FACTOR * aboveCellGridWidth;
    } else {
      double[] columnLeftBoundaries = columnBoundaries.getOne();
      double[] columnRightBoundaries = columnBoundaries.getTwo();

      double currColWidth = columnRightBoundaries[columnNum] - columnLeftBoundaries[columnNum];
      boolean isAboveCellTextCloseToColBoundary = aboveCellRightBoundary
          > columnRightBoundaries[columnNum]
          - NON_GRID_COL_BOUNDARY_CLOSENESS_FACTOR * currColWidth;

      if (columnNum < columnLeftBoundaries.length - 1) {
        double meanColWidth =
            (columnRightBoundaries[columnNum + 1] - columnLeftBoundaries[columnNum]) / 2;
        boolean isAboveCellTextCloseToNextColBoundary = aboveCellRightBoundary
            > columnLeftBoundaries[columnNum + 1]
            - NEXT_COL_BOUNDARY_CLOSENESS_FACTOR * meanColWidth;
        return isAboveCellTextCloseToColBoundary && isAboveCellTextCloseToNextColBoundary;
      }
      return isAboveCellTextCloseToColBoundary;
    }
  }

  /**
   * @return True if all the cells in {@code rowCells} are empty, else return False
   */
  private static boolean areAllCellsEmpty(MutableList<TabularCellElementGroup<Element>> rowCells) {
    return rowCells.allSatisfy(cell -> cell.getTextStr().isEmpty());
  }

  /**
   * @return True if {@code element} is surrounded by borders
   */
  public static boolean isGridBasedElement(Element element) {
    PositionalContext<Element> context = element.getPositionalContext();
    return context.isVisualTopBorder() && context.isVisualLeftBorder() && context
        .isVisualRightBorder();
  }

  @Override
  public List getRequiredKeys() {
    return Lists.mutable.of(
        CustomScratchpadKeys.TABULAR_GROUP,
        CustomScratchpadKeys.DOCUMENT_SOURCE,
        CustomScratchpadKeys.PAGE_NUMBER,
        CustomScratchpadKeys.TABLE_INDEX,
        CustomScratchpadKeys.SPLIT_ROW_INDEX,
        CustomScratchpadKeys.IS_GRID_BASED_TABLE_DETECTION_ENABLED
    );
  }

  @Override
  public List getStoredKeys() {
    return Lists.mutable.of(CustomScratchpadKeys.TABULAR_GROUP);
  }

  @Override
  public void execute(Scratchpad scratchpad) {
    this.scratchpad = scratchpad;
    TabularElementGroup<Element> tabularGroup = CustomScratchpadKeys.TABULAR_GROUP
        .retrieveFrom(scratchpad);
    GridType gridType = scratchpad
        .retrieve(CustomScratchpadKeys.IS_GRID_BASED_TABLE_DETECTION_ENABLED);
    TableType tableType =
        tabularGroup.isGridTypeSatisfied(gridType) ? TableType.GRID_BASED : TableType.NORMAL;
    TabularElementGroup<Element> processedTabularGroup = this
        .mergeInternalRows(tabularGroup, tableType);
    if (this.isTableRowsFormKeyValuePairs(processedTabularGroup)) {
      processedTabularGroup = this.mergeInternalRows(processedTabularGroup, TableType.KEY_VALUE);
    } else if (this.isTableRowsFullyPopulated(processedTabularGroup)) {
      processedTabularGroup = this
          .mergeInternalRows(processedTabularGroup, TableType.FULLY_POPULATED);
    }
    processedTabularGroup.setBackReferences();
    scratchpad.store(CustomScratchpadKeys.TABULAR_GROUP, processedTabularGroup);
  }

  /**
   * @return updated table after merging internal rows in {@code tabularGroup} based on condition
   * corresponding to table type {@code tableType}
   */
  private TabularElementGroup<Element> mergeInternalRows(TabularElementGroup<Element> tabularGroup,
      TableType tableType) {
    Pair<double[], double[]> columnBoundaries = tabularGroup.getColumnBoundaries();
    TabularElementGroup<Element> newTabularGroup = new TabularElementGroup<>(
        tabularGroup.numberOfRows(), tabularGroup.numberOfColumns(),
        tabularGroup.getColumnHeaderCount());
    double rowLevelSymmetry = tabularGroup.getRowLevelSymmetry();
    int rowIndex1 = 0; //index in new tabular group
    int rowIndex2 = 0; //index in tabular group
    this.addOrMergeRow(tabularGroup, newTabularGroup, rowIndex1, rowIndex2);
    for (rowIndex2 = 1; rowIndex2 < tabularGroup.numberOfRows(); rowIndex2++) {
      /* check if row r and r+1 can be merged */
      if (!areRowsMergeable(tabularGroup, newTabularGroup, columnBoundaries, rowIndex1, rowIndex2,
          rowLevelSymmetry, tableType)) {
        rowIndex1++;
      }
      this.addOrMergeRow(tabularGroup, newTabularGroup, rowIndex1, rowIndex2);
    }
    newTabularGroup.setCaption(tabularGroup.getCaption());
    newTabularGroup.curtail(rowIndex1 + 1);
    this.logEntry(newTabularGroup.numberOfRows() == tabularGroup.numberOfRows() ? "No "
        : "" + "Internal rows merged.");
    return newTabularGroup;
  }

  /**
   * Merge the row {@code rowIndex2} of table {@oldTable} to the the row {@code rowIndex1} of table
   * {@newTable}
   */
  private void addOrMergeRow(TabularElementGroup<Element> tabularGroup,
      TabularElementGroup<Element> newTabularGroup, int rowIndex1, int rowIndex2) {
    for (int columnIndex = 0; columnIndex < tabularGroup.numberOfColumns(); columnIndex++) {
      this.mergeCells(tabularGroup, newTabularGroup, rowIndex1, rowIndex2, columnIndex);
    }
    if (rowIndex2 < tabularGroup.getColumnHeaderCount()) {
      newTabularGroup.setColumnHeaderCount(rowIndex1 + 1);
    }
  }

  /**
   * Merge all the elements present in cell ({@code rowIndex2}, {@code columnIndex}) of table
   * {@oldTable} to the elements in cell ({@code rowIndex1}, {@code columnIndex}) of table
   * {@newTable}
   */
  private void mergeCells(TabularElementGroup<Element> oldTable,
      TabularElementGroup<Element> newTable, int rowIndex1, int rowIndex2, int columnIndex) {
    TabularCellElementGroup<Element> oldCell = oldTable.getCells().get(rowIndex2).get(columnIndex);
    TabularCellElementGroup<Element> newCell = newTable.getCells().get(rowIndex1).get(columnIndex);
    oldCell.getElements().each(element ->
    {
      if (!newCell.getElements().contains(element)) {
        newCell.add(element);
      }
    });
    newCell.setHorizontallyMerged(oldCell.isHorizontallyMerged());
    newCell.setVerticallyMerged(oldCell.isVerticallyMerged());
  }

  /**
   * If table forms key value pairs in row, following conditions has to be satisfied: 1. Number of
   * columns = 2 2. Elements in first column can either be empty or text in the elements can be
   * empty or ends with colon.
   *
   * @return True if {@code tabularGroup} contains rows with key value pairs.
   */
  private boolean isTableRowsFormKeyValuePairs(TabularElementGroup<Element> tabularGroup) {
    return tabularGroup.numberOfColumns() == 2 && tabularGroup.getCells()
        .collect(rowCell -> rowCell.get(0))
        .allSatisfy(tabularCellElementGroup -> tabularCellElementGroup.getElements().isEmpty()
            || tabularCellElementGroup.getElements()
            .anySatisfy(
                cellElement -> cellElement.getTextStr().isEmpty() || cellElement.getTextStr()
                    .endsWith(":")));
  }

  /**
   * A table is fully populated table if all rows in it satisfies any of the below conditions- 1.
   * Either the all cells in row are empty or all cells in row are not empty 2. Only first cell is
   * non empty and rest of the cell in the row is empty
   *
   * @return True if {@code tabularGroup} is fully populated
   */
  private boolean isTableRowsFullyPopulated(TabularElementGroup<Element> tabularGroup) {
    return tabularGroup.getCells()
        .subList(tabularGroup.getColumnHeaderCount(), tabularGroup.numberOfRows()).allSatisfy(row ->
        {
          boolean allCellsEmpty = areAllCellsEmpty(row);
          boolean allCellsNonEmpty = row.allSatisfy(cell -> !cell.getTextStr().isEmpty());
          boolean onlyFirstCellNonEmpty =
              row.notEmpty() && !row.getFirst().getTextStr().isEmpty() && areAllCellsEmpty(
                  row.subList(1, row.size()));
          return allCellsEmpty || allCellsNonEmpty || onlyFirstCellNonEmpty;
        });
  }
}
