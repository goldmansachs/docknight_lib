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
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.util.List;
import java.util.Set;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.utility.ArrayIterate;

/**
 * Process node in table detection workflow in order to split the table
 */
public class TableSplittingProcessNode extends AbstractProcessNode {

  private static final double ROW_HEIGHT_VARIANCE_THRESHOLD = 0.15;
  private static final double SPLIT_HEADER_CONFIDENCE_THRESHOLD = 0.8;
  private static final int MINIMUM_TABLE_ROWS = 2;

  /**
   * @return map with key as table breaking criterion and value as row votes given according to that
   * criterion on the {@code table}
   */
  private static MutableMap<TableBreakingCriterion, double[]> getTableBreakingCriterionVotes(
      TabularElementGroup<Element> table) {
    int rowCount = table.numberOfRows();
    int colCount = table.numberOfColumns();

    TableBreakingCriterion[] criteria = TableBreakingCriterion.values();
    ArrayIterate.forEach(criteria, criterion -> criterion.initializeRowVotes(rowCount));

    for (int j = 0; j < colCount; j++) {
      ArrayIterate.forEach(criteria, TableBreakingCriterion::initializeStateForColumn);

      for (int r = 0; r < rowCount; r++) {
        Element currentElement = table.getCell(r, j).getFirst();
        int row = r;
        if (currentElement == null) {
          ArrayIterate.forEach(criteria, criterion -> criterion.updateStateForNullCell(row));
        } else {
          ArrayIterate
              .forEach(criteria, criterion -> criterion.updateStateForCell(currentElement, row));
        }
      }
    }
    return ArrayIterate
        .toMap(criteria, criterion -> criterion, TableBreakingCriterion::getRowVotes);
  }

  /**
   * Split the {@code table} into two portions. One table will contains the rows from 0 to r and
   * other table will contain the rows from r+1 to the end.
   */
  private static MutableList<TabularElementGroup<Element>> split(TabularElementGroup<Element> table,
      int r) {
    int rowCount = table.numberOfRows();
    int colCount = table.numberOfColumns();

    TabularElementGroup<Element> table1 = new TabularElementGroup<>(0, colCount,
        DEFAULT_COLUMN_HEADER_COUNT);
    TabularElementGroup<Element> table2 = new TabularElementGroup<>(0, colCount,
        DEFAULT_COLUMN_HEADER_COUNT);

    MutableList<MutableList<TabularCellElementGroup<Element>>> oldRows = table.getCells();
    for (int i = 0; i < rowCount; i++) {
      MutableList<TabularCellElementGroup<Element>> newRow = Lists.mutable.empty();
      for (int j = 0; j < colCount; j++) {
        TabularCellElementGroup<Element> prevCell = oldRows.get(i).get(j);
        TabularCellElementGroup<Element> cell = new TabularCellElementGroup<>(
            prevCell.isVerticallyMerged(), prevCell.isHorizontallyMerged());
        prevCell.getElements().each(cell::add);
        newRow.add(j, cell);
      }
      if (i <= r) {
        table1.addRow(newRow, i);
      } else {
        table2.addRow(newRow, i - r - 1);
      }
    }
    table1.setCaption(table.getCaption());
    table1.setColumnHeaderCount(Math.min(table.getColumnHeaderCount(), table1.numberOfRows()));
    table1.setBackReferences();
    table2.setBackReferences();
    return removeEmptyColumns(Lists.mutable.of(table1, table2));
  }

  /**
   * @return modified tables after removing columns which do not containing any non empty elements
   */
  private static MutableList<TabularElementGroup<Element>> removeEmptyColumns(
      MutableList<TabularElementGroup<Element>> tables) {
    MutableList<TabularElementGroup<Element>> newTabularGroups = Lists.mutable.empty();
    for (TabularElementGroup<Element> table : tables) {
      MutableList<MutableList<TabularCellElementGroup<Element>>> cells = table.getCells();
      int rowCount = table.numberOfRows();
      int colIndex = 0;
      while (colIndex < table.numberOfColumns()) {
        boolean delete = true;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
          if (table.getMergedCell(rowIndex, colIndex).getElements().notEmpty()) {
            delete = false;
            colIndex++;
            break;
          }
        }
        if (delete) {
          for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            cells.get(rowIndex).remove(colIndex);
          }
        }
      }
      newTabularGroups.add(table);
    }
    return newTabularGroups;
  }

  /**
   * @return confidence to split the table
   */
  private static double getSplitConfidence(double[] derivativeProducts,
      double[] derivativeProductsThresholds, double[] derivativeProductsStdDeviation,
      double nextHeaderConfidence) {
    int derivativesConsidered = 0;
    double splitConfidence = 0;
    for (int i = 0; i < derivativeProducts.length; i++) {
      if (derivativeProducts[i] >= derivativeProductsThresholds[i]) {
        splitConfidence += (derivativeProducts[0] - derivativeProductsThresholds[0])
            / derivativeProductsStdDeviation[i];
        derivativesConsidered++;
      }
    }
    double splitWeight = 0.5;
    double headerWeight = 0.5;
    return splitWeight * splitConfidence / derivativesConsidered
        + headerWeight * nextHeaderConfidence;
  }

  @Override
  public List getRequiredKeys() {
    return Lists.mutable.of(
        CustomScratchpadKeys.TABULAR_GROUP,
        CustomScratchpadKeys.HEADER_CONFIDENCE,
        CustomScratchpadKeys.IS_PARENT_TABLE,
        CustomScratchpadKeys.DOCUMENT_SOURCE,
        CustomScratchpadKeys.PAGE_NUMBER,
        CustomScratchpadKeys.TABLE_INDEX,
        CustomScratchpadKeys.SPLIT_ROW_INDEX,
        CustomScratchpadKeys.PREV_TABLES_TO_DELETE
    );
  }

  @Override
  public List getStoredKeys() {
    return Lists.mutable.of(
        CustomScratchpadKeys.IS_SPLIT_PERMISSIBLE,
        CustomScratchpadKeys.SPLIT_TABULAR_GROUPS
    );
  }

  @Override
  public void execute(Scratchpad scratchpad) {
    this.scratchpad = scratchpad;
    TabularElementGroup<Element> tabularGroup = CustomScratchpadKeys.TABULAR_GROUP
        .retrieveFrom(scratchpad);
    Set<Integer> indicesOfTablesToBeDeleted = CustomScratchpadKeys.PREV_TABLES_TO_DELETE
        .retrieveFrom(scratchpad);
    double headerConfidence = scratchpad.retrieveDouble(CustomScratchpadKeys.HEADER_CONFIDENCE);
    boolean isParentTable = scratchpad.retrieveBoolean(CustomScratchpadKeys.IS_PARENT_TABLE);
    boolean isSplitPermissible =
        isParentTable || (headerConfidence >= SPLIT_HEADER_CONFIDENCE_THRESHOLD);
    scratchpad.store(CustomScratchpadKeys.IS_SPLIT_PERMISSIBLE, isSplitPermissible);
    if (isSplitPermissible) {
      this.logEntry("Current table is permissible. Splitting it further recursively.");
      MutableList<TabularElementGroup<Element>> splitTabularGroups = this
          .splitTableRecursively(tabularGroup, indicesOfTablesToBeDeleted);
      splitTabularGroups.each(TabularElementGroup::setBackReferences);
      scratchpad.store(CustomScratchpadKeys.SPLIT_TABULAR_GROUPS, splitTabularGroups);
    } else {
      this.logEntry("Current split table is non-permissible.");
      scratchpad.store(CustomScratchpadKeys.SPLIT_TABULAR_GROUPS, Lists.mutable.of(tabularGroup));
    }
  }

  /**
   * For each row Compute the following: TextStyleBreaks, RegexBreaks, FontSizeBreaks, ColorBreaks,
   * etc For each row compute, check for particular products of these derivatives, if they goes
   * above thresholds, splits the table find second table headers and their confidence, if
   * confidence goes above threshold, keep the splits Call splitTablesRecursively recursively on new
   * tables
   **/

  private MutableList<TabularElementGroup<Element>> splitTableRecursively(
      TabularElementGroup<Element> tabularGroup, Set<Integer> indicesOfTablesToBeDeleted) {
    MutableList<TabularElementGroup<Element>> newTabularGroups = Lists.mutable.empty();
    MutableMap<TableBreakingCriterion, double[]> criterionToVotesMap = getTableBreakingCriterionVotes(
        tabularGroup);
    MutableList<TabularElementGroup<Element>> splitTablesFromVotes = this
        .getSplitTablesFromVotes(tabularGroup, criterionToVotesMap, indicesOfTablesToBeDeleted);

    if (splitTablesFromVotes.isEmpty()) {
      newTabularGroups.add(tabularGroup);
    } else {
      newTabularGroups.addAll(splitTablesFromVotes);
    }
    return newTabularGroups;
  }

  /**
   * Split the tables using {@code criterionToVotesMap}
   */
  private MutableList<TabularElementGroup<Element>> getSplitTablesFromVotes(
      TabularElementGroup<Element> table,
      MutableMap<TableBreakingCriterion, double[]> criterionToVotesMap,
      Set<Integer> overallIndicesOfTablesToDelete) {
    int rowCount = table.numberOfRows();
    int colCount = table.numberOfColumns();

    double[] rowTextBreaks = criterionToVotesMap.get(TableBreakingCriterion.ROW_TEXT_STYLE_BREAK);
    double[] rowNonNullCells = criterionToVotesMap
        .get(TableBreakingCriterion.ROW_NON_NULL_CELL_COUNT);
    double[] rowRegexBreaks = criterionToVotesMap.get(TableBreakingCriterion.ROW_REGEX_BREAK);
    double[] rowBoldCells = criterionToVotesMap.get(TableBreakingCriterion.ROW_BOLD_COUNT);
    double[] rowFontBreaks = criterionToVotesMap.get(TableBreakingCriterion.ROW_FONT_BREAK);
    double[] rowColorBreaks = criterionToVotesMap.get(TableBreakingCriterion.ROW_COLOR_BREAK);
    double[] rowFilledBreaks = criterionToVotesMap.get(TableBreakingCriterion.ROW_FILLED_BREAK);

    boolean isRowBasedTable = table.isRowBasedTable();

    double[] derivativeProducts = {0, 0, 0, 0};
    double[] derivativeProductsThresholds = {0.4, 0.8, 0.6, 0.6};
    double[] derivativeProductsStdDeviation = {0.2135, 0, 0.112, 0.10042};

    TableDetectionProcessModel processModel = new TableDetectionProcessModel();

    /* Calculate Header Score for each row */
    for (int r = 0; r < rowCount - 1; r++) {
      derivativeProducts[0] =
          (rowTextBreaks[r] / rowNonNullCells[r]) * (rowRegexBreaks[r] / rowNonNullCells[r]) * (
              rowBoldCells[r] / rowNonNullCells[r]);
      derivativeProducts[1] =
          (rowColorBreaks[r] / rowNonNullCells[r]) * (rowBoldCells[r] / rowNonNullCells[r]);
      derivativeProducts[2] =
          (rowFilledBreaks[r] / colCount) * (rowRegexBreaks[r] / rowNonNullCells[r]);
      derivativeProducts[3] =
          (rowFilledBreaks[r] / colCount) * (rowTextBreaks[r] / rowNonNullCells[r]);

      if ((isRowBasedTable && r >= MINIMUM_TABLE_ROWS && (rowFontBreaks[r] > 0.0
          || derivativeProducts[3] >= 0.5))
          || (r >= MINIMUM_TABLE_ROWS
          && (derivativeProducts[0] >= derivativeProductsThresholds[0]
          || derivativeProducts[1] >= derivativeProductsThresholds[1]
          || derivativeProducts[2] >= derivativeProductsThresholds[2]
          || derivativeProducts[3] >= derivativeProductsThresholds[3]
      ))) {
        this.logEntry(
            String.format("Checking permissibility for table split at row index: %d.", r));

        /* recursively apply the entire table enrichment procedure to the lower split table and return the final tables*/
        MutableList<TabularElementGroup<Element>> splitTables = split(table, r - 1);

        TabularElementGroup<Element> aboveTabularGroup = splitTables.get(0);
        TabularElementGroup<Element> belowTabularGroup = splitTables.get(1);

        Scratchpad childTableScratchpad = new Scratchpad();
        childTableScratchpad.store(CustomScratchpadKeys.TABULAR_GROUP, belowTabularGroup);
        childTableScratchpad.store(CustomScratchpadKeys.DOCUMENT_SOURCE,
            this.scratchpad.retrieve(CustomScratchpadKeys.DOCUMENT_SOURCE));
        childTableScratchpad.store(CustomScratchpadKeys.PAGE_NUMBER,
            this.scratchpad.retrieveInt(CustomScratchpadKeys.PAGE_NUMBER));
        childTableScratchpad.store(CustomScratchpadKeys.TABLE_INDEX,
            this.scratchpad.retrieveInt(CustomScratchpadKeys.TABLE_INDEX));
        childTableScratchpad.store(CustomScratchpadKeys.SPLIT_ROW_INDEX, r);
        childTableScratchpad.store(CustomScratchpadKeys.IS_PARENT_TABLE, false);
        childTableScratchpad
            .store(CustomScratchpadKeys.PREV_TABLES_TO_DELETE, Sets.mutable.empty());

        MutableList<TabularElementGroup<Element>> splitTabularGroups = (MutableList<TabularElementGroup<Element>>) processModel
            .execute(childTableScratchpad);
        if (childTableScratchpad.retrieve(CustomScratchpadKeys.IS_SPLIT_PERMISSIBLE)) {
          double splitConfidence = getSplitConfidence(derivativeProducts,
              derivativeProductsThresholds, derivativeProductsStdDeviation,
              childTableScratchpad.retrieveDouble(CustomScratchpadKeys.HEADER_CONFIDENCE));
          aboveTabularGroup.setConfidence(TableDetectionConfidenceFeatures.SPLIT_DOWN_CONFIDENCE,
              splitConfidence);
          if (splitTabularGroups.notEmpty()) {
            splitTabularGroups.get(0)
                .setConfidence(TableDetectionConfidenceFeatures.SPLIT_UP_CONFIDENCE,
                    splitConfidence);
          }
          Set<Integer> indicesOfTablesToDeleteFromSplitProcessing = CustomScratchpadKeys.PREV_TABLES_TO_DELETE
              .retrieveFrom(childTableScratchpad);
          if (!indicesOfTablesToDeleteFromSplitProcessing.contains(-1)) {
            splitTabularGroups.add(0, aboveTabularGroup);
          }
          indicesOfTablesToDeleteFromSplitProcessing.remove(-1);
          overallIndicesOfTablesToDelete.addAll(indicesOfTablesToDeleteFromSplitProcessing);
          this.scratchpad
              .store(CustomScratchpadKeys.PREV_TABLES_TO_DELETE, overallIndicesOfTablesToDelete);
          return splitTabularGroups;
        }
      }
    }
    return Lists.mutable.empty();
  }

  /**
   * Class representing different criterion for splitting the table
   */
  private enum TableBreakingCriterion {
    ROW_TEXT_STYLE_BREAK  // Increase the vote count for row which has different text style from its previous row
        {
          @Override
          public void initializeStateForColumn() {
            this.criterionState.put("TEXT_STYLE", Lists.mutable.empty());
          }

          @Override
          public void updateStateForCell(Element cellElement, int row) {
            List<String> currTextStyle = Lists.mutable.empty();
            if (cellElement.getAttribute(TextStyles.class) != null) {
              currTextStyle = cellElement.getAttribute(TextStyles.class).getValue();
              if (!currTextStyle.equals(this.criterionState.get("TEXT_STYLE"))) {
                this.rowVotes[row]++;
              }
            }
            this.criterionState.put("TEXT_STYLE", currTextStyle);
          }

          @Override
          public void updateStateForNullCell(int row) {
            this.criterionState.put("TEXT_STYLE", Lists.mutable.empty());
          }
        },
    ROW_REGEX_BREAK  // Increase the vote count for row which has different regex type from its previous row
        {
          @Override
          public void initializeStateForColumn() {
            this.criterionState.put("REGEX", SemanticsChecker.RegexType.NON_ALPHANUMERIC);
          }

          @Override
          public void updateStateForCell(Element cellElement, int row) {
            SemanticsChecker.RegexType currentRegexType = SemanticsChecker.RegexType
                .getFor(cellElement.getTextStr().trim());
            if (currentRegexType != this.criterionState.get("REGEX")) {
              this.rowVotes[row]++;
            }
            this.criterionState.put("REGEX", currentRegexType);
          }

          @Override
          public void updateStateForNullCell(int row) {
            this.criterionState.put("REGEX", SemanticsChecker.RegexType.NON_ALPHANUMERIC);
          }
        },
    ROW_COLOR_BREAK // Increase the vote count for row which has different color from its previous row
        {
          @Override
          public void updateStateForCell(Element cellElement, int row) {
            java.awt.Color currColor = null;
            if (cellElement.getAttribute(Color.class) != null) {
              currColor = cellElement.getAttribute(Color.class).getValue();
              this.rowVotes[row] += currColor.equals(this.criterionState.get("COLOR")) ? 0 : 1;
            }
            this.criterionState.put("COLOR", currColor);
          }

          @Override
          public void updateStateForNullCell(int row) {
            this.criterionState.put("COLOR", null);
          }
        },
    ROW_FONT_BREAK {
      @Override
      public void initializeStateForColumn() {
        this.criterionState.put("HEIGHT", 0.0);
        this.criterionState.put("VARIANCE", 0.0);
      }

      @Override
      public void updateStateForCell(Element cellElement, int row) {
        double currHeight = cellElement.getAttribute(Height.class).getValue().getMagnitude();
        double currVariance = (currHeight / (double) this.criterionState.get("HEIGHT")) - 1;
        if (currVariance < -ROW_HEIGHT_VARIANCE_THRESHOLD
            && (double) this.criterionState.get("VARIANCE") > ROW_HEIGHT_VARIANCE_THRESHOLD) {
          this.rowVotes[row - 1]++;
        }
        this.criterionState.put("HEIGHT", currHeight);
        this.criterionState.put("VARIANCE", currVariance);
      }

      public void updateStateForNullCell(int row) {
        double currHeight = 0.0;
        double currVariance = (currHeight / (double) this.criterionState.get("HEIGHT")) - 1;
        this.criterionState.put("HEIGHT", currHeight);
        this.criterionState.put("VARIANCE", currVariance);
      }
    },
    ROW_FILLED_BREAK // Increase the vote count for row where transition of (null -> non null or non null -> null) is made
        {
          @Override
          public void updateStateForCell(Element cellElement, int row) {
            if (this.criterionState.get("CELL_ELEMENT") == null) {
              this.rowVotes[row]++;
            }
            this.criterionState.put("CELL_ELEMENT", cellElement);
          }

          @Override
          public void updateStateForNullCell(int row) {
            if (this.criterionState.get("CELL_ELEMENT") != null) {
              this.rowVotes[row]++;
            }
            this.criterionState.put("CELL_ELEMENT", null);
          }
        },
    ROW_NON_NULL_CELL_COUNT // Increase the vote count for row when non empty cell emount is encountered
        {
          @Override
          public void updateStateForCell(Element cellElement, int row) {
            this.rowVotes[row]++;
          }
        },
    ROW_BOLD_COUNT // Increase the vote count for row where cell element with bold text style is encountered
        {
          @Override
          public void updateStateForCell(Element cellElement, int row) {
            if (cellElement.getAttribute(TextStyles.class) != null) {
              List<String> currTextStyle = cellElement.getAttribute(TextStyles.class).getValue();
              if (currTextStyle.contains(TextStyles.BOLD)) {
                this.rowVotes[row]++;
              }
            }
          }
        };

    final MutableMap<String, Object> criterionState;
    double[] rowVotes;

    TableBreakingCriterion() {
      this.criterionState = Maps.mutable.empty();
    }

    public double[] getRowVotes() {
      return this.rowVotes;
    }

    public void initializeRowVotes(int rowCount) {
      this.rowVotes = new double[rowCount];
    }

    /**
     * This method is used to initiate variables or state for the column
     */
    public void initializeStateForColumn() {
    }

    /**
     * This method is called with {@code row} and {@code cellElement} if we encountered a non null
     * cell element within that row
     */
    public abstract void updateStateForCell(Element cellElement, int row);

    /**
     * This method is called with {@code row} if we encountered a null cell within that row
     */
    public void updateStateForNullCell(int row) {
    }
  }
}
