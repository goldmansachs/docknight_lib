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
import com.gs.ep.docknight.model.ElementGroup;
import com.gs.ep.docknight.model.PositionalContext;
import com.gs.ep.docknight.model.TabularCellElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup.VectorTag;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.util.List;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.list.Interval;

/**
 * Process node in table detection workflow to detect total rows. Total row is a row in which total
 * amount is present (which might be the sum of amount from the previous rows in the table)
 */
public class TotalRowDetectionProcessNode extends AbstractProcessNode {

  private static final int MAX_CONTINUOUS_TOTAL_ROWS_ALLOWED = 3;

  /**
   * Remove lines that form a group of three or more total consecutive rows
   *
   * @param totalRows indices of rows which form total row
   * @return indices of total rows after removing consecutive total rows
   */
  public static MutableList<Integer> removeLargeContinuousRowGroups(
      MutableList<Integer> totalRows) {
    MutableList<Integer> refinedTotalRows = Lists.mutable.empty();
    int continuousTotalRowCount;
    for (int i = 0; i < totalRows.size(); i += continuousTotalRowCount) {
      continuousTotalRowCount = 1;
      while (i + continuousTotalRowCount < totalRows.size()
          && totalRows.get(i + continuousTotalRowCount) - totalRows.get(i)
          == continuousTotalRowCount) {
        ++continuousTotalRowCount;
      }

      if (continuousTotalRowCount <= MAX_CONTINUOUS_TOTAL_ROWS_ALLOWED) {
        refinedTotalRows.addAllIterable(totalRows.subList(i, i + continuousTotalRowCount));
      }
    }
    return refinedTotalRows;
  }

  /**
   * Get indices of numerical columns from the {@code tabularGroup}
   *
   * @param tabularGroup table
   * @param rowsContainingNumber indices of rows which contain numbers
   * @return indices of numerical columns
   */
  private static MutableList<Integer> getNumericalColumns(TabularElementGroup<Element> tabularGroup,
      MutableSet<Integer> rowsContainingNumber) {
    return tabularGroup.numberOfColumns() == 1 ? Lists.mutable.empty()
        : Interval.oneTo(tabularGroup.numberOfColumns() - 1)
            .select(colIndex -> rowsContainingNumber.allSatisfy(rowIndex ->
                colIndex >= tabularGroup.getCells().get(rowIndex).size()
                    || TotalRowSelectionCriterion
                    .isAmountRepresentation(tabularGroup.getCell(rowIndex, colIndex).getTextStr())
                    || tabularGroup.getCell(rowIndex, colIndex).getTextStr().isEmpty())).toList();
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
        CustomScratchpadKeys.IS_PARENT_TABLE,
        CustomScratchpadKeys.PREV_TABLES_TO_DELETE,
        CustomScratchpadKeys.END_RESULT
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
    List<TabularElementGroup<Element>> splitTabularGroups = (List<TabularElementGroup<Element>>) scratchpad
        .retrieve(CustomScratchpadKeys.END_RESULT);
    for (TabularElementGroup<Element> tabularGroup : splitTabularGroups) {
      this.detectTotalRows(tabularGroup);
    }
    scratchpad.store(CustomScratchpadKeys.END_RESULT, splitTabularGroups);
  }

  /**
   * Populate total row tag in {@code tabularGroup} with detected total rows. ALgo: <ol>
   * <li>numericRows <- Find all the rows inside a table that contain numbers</li>
   * <li>numericColumns <- Find all the columns such that every cell at the intersection of
   * numericRow and tableCell will either be empty or contains numerical data. We will call these
   * cells numericalCells.</li> <li>referenceRow <- Find the row that has no textStyles out of
   * numericRows. If none found, return first row of numericRows.</li> <li>specialRows <- Select all
   * rows from numericRows that have visual style different from the referenceRow. Different visual
   * style computation will happen only on numericalColumns.</li> <li>Add rows to specialRows that
   * satisfy all the following conditions, for a rowElement (line in a numericalCell): <ul>
   * <li>rowElement should have amount representation.</li> <li>It must have a top border.</li>
   * <li>It should have an element directly above it (i.e.it's shadowed above element (SAE) should
   * not be null)</li> <li>Element above it (SAE) should also have amount representation.</li>
   * <li>It should not be a grid element (because we do not want to apply border property if it is
   * already a grid element).</li> <li>Either it has underline or overline border or it does not
   * have any element directly below it (i.e. its shadowed below element SBE is null) or its SBE
   * exists and contains alphabets</li> </ul></li> <li>If specialRows set is empty then assign
   * specialRows <- numericRows</li> <li>filteredRows <- Apply the regex on these specialRows to
   * filter the rows containing total related keywords like "net", "sub", "total" etc with an amount
   * directly to their right in the row.</li> <li>totalRows <- Select all rows from the filteredRows
   * that do not form a group of more than 3 consecutive rows.</li> </ol>
   *
   * @param tabularGroup table in which total rows need to be detected
   */
  private void detectTotalRows(TabularElementGroup<Element> tabularGroup) {
    if (tabularGroup.getColumnHeaderCount() < tabularGroup.numberOfRows()) {
      MutableSet<Integer> rowsContainingNumber = TotalRowSelectionCriterion.NUMBERS_PRESENT
          .filterTableRows(tabularGroup,
              Interval.fromTo(tabularGroup.getColumnHeaderCount(), tabularGroup.numberOfRows() - 1)
                  .toSet());

      MutableSet<Integer> possibleTotalRowCandidates = this
          .getPossibleTotalRowCandidates(tabularGroup, rowsContainingNumber);
      if (possibleTotalRowCandidates.isEmpty()) {
        possibleTotalRowCandidates = rowsContainingNumber;
      }

      MutableSet<Integer> rowsContainingTotalRegex = TotalRowSelectionCriterion.REGEX_SEARCH
          .filterTableRows(tabularGroup, possibleTotalRowCandidates);
      if (rowsContainingTotalRegex.notEmpty()) {
        tabularGroup.addVectorTags(VectorTag.TOTAL_ROW,
            removeLargeContinuousRowGroups(rowsContainingTotalRegex.toSortedList()));
      } else if (possibleTotalRowCandidates.size() != rowsContainingNumber.size()) {
        tabularGroup.addVectorTags(VectorTag.TOTAL_ROW,
            removeLargeContinuousRowGroups(possibleTotalRowCandidates.toSortedList()));
      }
    }
  }

  /**
   * Get total row candidates that satisfy the table detection criteria
   *
   * @param tabularGroup table
   * @param rowsContainingNumber indices of rows which contain numbers
   * @return indices of total row candidates
   */
  private MutableSet<Integer> getPossibleTotalRowCandidates(
      TabularElementGroup<Element> tabularGroup, MutableSet<Integer> rowsContainingNumber) {
    MutableSet<Integer> possibleTotalRowCandidates = Sets.mutable.empty();
    MutableList<TotalRowSelectionCriterion> selectionCriteria = Lists.mutable
        .of(TotalRowSelectionCriterion.DIFFERENT_STYLE_FROM_COMMON_ROWS);
    if (!tabularGroup.areHorizontalLinesSignificant()) {
      selectionCriteria.add(TotalRowSelectionCriterion.BORDERS_PRESENT);
    }

    MutableList<Integer> numericalColumns = getNumericalColumns(tabularGroup, rowsContainingNumber);
    if (rowsContainingNumber.size() >= 2) {
      for (TotalRowSelectionCriterion criterion : selectionCriteria) {
        criterion.setNumericalColumns(numericalColumns);
        MutableSet<Integer> filteredRows = criterion
            .filterTableRows(tabularGroup, rowsContainingNumber);
        if (filteredRows.size() != rowsContainingNumber.size()) {
          possibleTotalRowCandidates.addAll(filteredRows);
        }
      }
    }
    return possibleTotalRowCandidates;
  }

  /**
   * Class representing different logic/criterion for the total row selection
   */
  private enum TotalRowSelectionCriterion {
    REGEX_SEARCH {
      @Override
      public boolean isApplicable(MutableList<TabularCellElementGroup<Element>> rowCells) {
        MutableList<Element> rowElements = rowCells.flatCollect(ElementGroup::getElements);
        return rowElements.anySatisfy(rowElement ->
        {
          Element rightElement = rowElement.getPositionalContext().getShadowedRightElement();
          return SemanticsChecker.meansTotal(rowElement.getTextStr()) && rightElement != null
              && isAmountRepresentation(rightElement.getTextStr());
        });
      }
    },
    DIFFERENT_STYLE_FROM_COMMON_ROWS {
      private Integer referenceRowForComparison;

      @Override
      protected MutableSet<Integer> initialSetup(TabularElementGroup<Element> tabularGroup,
          MutableSet<Integer> rowIndices) {
        this.referenceRowForComparison = this
            .getReferenceRowForComparison(tabularGroup, rowIndices);
        return rowIndices;
      }

      @Override
      public boolean isApplicable(MutableList<TabularCellElementGroup<Element>> rowCells) {
        MutableList<Element> rowElements = this.getElementsFromNumericalColumnsInRow(rowCells);
        return rowElements.anySatisfy(rowElementToBeCompared ->
        {
          PositionalContext<Element> rowElementPositionalContext = rowElementToBeCompared
              .getPositionalContext();
          TabularElementGroup<Element> tabularGroup = rowElementPositionalContext.getTabularGroup();
          if (tabularGroup != null) {
            int col = rowElementPositionalContext.getTabularColumn();
            Element referenceRowElement = tabularGroup.getCell(this.referenceRowForComparison, col)
                .getLast();
            return referenceRowElement != null && rowElementToBeCompared
                .hasDifferentVisualStylesFromElement(referenceRowElement);
          }
          return false;
        });
      }

      private Integer getReferenceRowForComparison(TabularElementGroup<Element> tabularGroup,
          MutableSet<Integer> rowIndices) {
        int referenceRowSize = 0;
        Integer referenceRow = null;
        for (Integer rowIndex : rowIndices) {
          MutableList<TabularCellElementGroup<Element>> rowCells = tabularGroup.getCells()
              .get(rowIndex);
          int rowSize = rowCells.size();
          if (rowSize > referenceRowSize && rowCells
              .flatCollect(TabularCellElementGroup::getElements)
              .noneSatisfy(rowElement -> rowElement.hasAttribute(TextStyles.class))) {
            referenceRowSize = rowSize;
            referenceRow = rowIndex;
          }
        }
        return referenceRow == null ? rowIndices.getFirst() : referenceRow;
      }
    },
    BORDERS_PRESENT {
      @Override
      public boolean isApplicable(MutableList<TabularCellElementGroup<Element>> rowCells) {
        return this.getElementsFromNumericalColumnsInRow(rowCells).anySatisfy(rowElement ->
        {
          PositionalContext<Element> positionalContext = rowElement.getPositionalContext();
          return positionalContext.isVisualTopBorder()
              && positionalContext.getShadowedAboveElement() != null &&
              isAmountRepresentation(positionalContext.getShadowedAboveElement().getTextStr())
              && !InternalRowMergingProcessNode.isGridBasedElement(rowElement) &&
              (positionalContext.hasUnderlinedBorder()
                  || positionalContext.getShadowedBelowElement() == null || positionalContext
                  .hasOverlinedBorder() ||
                  SemanticsChecker
                      .hasAlphabets(positionalContext.getShadowedBelowElement().getTextStr()));
        });
      }
    },
    NUMBERS_PRESENT {
      @Override
      public boolean isApplicable(MutableList<TabularCellElementGroup<Element>> rowCells) {
        // Excluding first column as numbers present in it can not represent total value. It also removes false positive case where number in first column may represent row index.
        return ColumnHeaderMergingProcessNode
            .containsNumericCell(rowCells.subList(1, rowCells.size()));
      }
    };

    private MutableList<Integer> numericalColumns = Lists.mutable.empty();

    /**
     * Check if {@code text} is amount representation
     *
     * @param text text to be checked
     * @return boolean flag indicating whether {@code text} is amount representation
     */
    private static boolean isAmountRepresentation(String text) {
      SemanticsChecker.RegexType regexType = SemanticsChecker.RegexType.getFor(text);
      if (regexType != SemanticsChecker.RegexType.NUMERIC) {
        text = text.replaceFirst(SemanticsChecker.NUMERIC_QUALIFIERS, "0");
        regexType = SemanticsChecker.RegexType.getFor(text);
      }
      return regexType == SemanticsChecker.RegexType.NUMERIC;
    }

    public void setNumericalColumns(MutableList<Integer> numericalColumns) {
      this.numericalColumns = numericalColumns;
    }

    /**
     * Filter row indices from {@code rowIndices} that satisfy the total row criteria
     *
     * @param tabularGroup table
     * @param rowIndices indices of input rows
     * @return indices of filtered table rows
     */
    public MutableSet<Integer> filterTableRows(TabularElementGroup<Element> tabularGroup,
        MutableSet<Integer> rowIndices) {
      if (rowIndices.isEmpty()) {
        return rowIndices;
      }
      rowIndices = this.initialSetup(tabularGroup, rowIndices);
      return rowIndices
          .select(rowIndex -> this.isApplicable(tabularGroup.getCells().get(rowIndex)));
    }

    /**
     * Checks whether the {@code rowCells} satisfy the current total row selection criteria.
     *
     * @param rowCells cell elements in the row.
     * @return boolean flag indicating whether cell elements in row satisfy current criteria.
     */
    public abstract boolean isApplicable(MutableList<TabularCellElementGroup<Element>> rowCells);

    protected MutableSet<Integer> initialSetup(TabularElementGroup<Element> tabularGroup,
        MutableSet<Integer> rowIndices) {
      return rowIndices;
    }

    /**
     * Get all elements from numerical column from the particular row
     *
     * @param rowCells cells of particular row
     * @return elements present in numerical column
     */
    protected MutableList<Element> getElementsFromNumericalColumnsInRow(
        MutableList<TabularCellElementGroup<Element>> rowCells) {
      MutableList<Element> rowElements = Lists.mutable.empty();
      for (Integer numericalColumn : this.numericalColumns) {
        if (rowCells.size() > numericalColumn) {
          rowElements.addAll(rowCells.get(numericalColumn).getElements());
        }
      }
      return rowElements;
    }
  }
}
