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
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.util.List;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;

/**
 * Process node in table detection workflow to merge column headers
 */
public class ColumnHeaderMergingProcessNode extends AbstractProcessNode {

  private static final double MAX_ROWS_ALLOWED_IN_HEADER = 0.5;

  /**
   * @return the most voted row according to the first voting criteria which is applicable in the
   * {@code voitingCriteria}
   */
  private static int voteHeaderRows(TabularElementGroup<Element> tabularGroup,
      MutableList<HeaderRowsVotingCriterion> votingCriteria) {
    int rowCount = tabularGroup.numberOfRows();
    int colCount = tabularGroup.numberOfColumns();

    int i = 0;
    int[] headerLimitingVotes = new int[rowCount];
    for (HeaderRowsVotingCriterion votingCriterion : votingCriteria) {
      boolean isCriterionApplicable = false;
      for (int colIndex = 0; colIndex < colCount; colIndex++) {
        /* Get the first vertical group */
        int rowIndex = 0;
        while (rowIndex < rowCount && tabularGroup.getCell(rowIndex, colIndex).size() == 0) {
          rowIndex++;
        }
        /* Call compare to get the voted row */
        if (rowIndex < rowCount) {
          Element element = tabularGroup.getCell(rowIndex, colIndex).getFirst();
          ElementGroup<Element> verticalGroup = element.getPositionalContext().getVerticalGroup();
          isCriterionApplicable = isCriterionApplicable || votingCriterion.isApplicable(element);
          int votedRow =
              isCriterionApplicable ? votingCriterion.compare(verticalGroup, element) : 0;
          headerLimitingVotes[votedRow]++;
        }
      }
      if (isCriterionApplicable) {
        break;
      }
      i++;
      headerLimitingVotes = new int[rowCount];
    }

    /* Choose the max voted row */
    int lastHeaderRowIndex = 0;
    boolean isApplicableCriterionRegex =
        i < votingCriteria.size() && votingCriteria.get(i) == HeaderRowsVotingCriterion.REGEX;
    boolean hasNamedEntity = false;
    for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
      if (isApplicableCriterionRegex) {
        for (int colIndex = 0; colIndex < colCount; colIndex++) {
          if (SemanticsChecker
              .isNamedEntity(tabularGroup.getCell(rowIndex, colIndex).getTextStr())) {
            hasNamedEntity = true;
            break;
          }
        }
      }
      if (hasNamedEntity) {
        break;
      }
      if (headerLimitingVotes[rowIndex] > headerLimitingVotes[lastHeaderRowIndex]) {
        lastHeaderRowIndex = rowIndex;
      }
    }

    if ((lastHeaderRowIndex >= MAX_ROWS_ALLOWED_IN_HEADER * rowCount && isApplicableCriterionRegex)
        || lastHeaderRowIndex == 0) {
      int index = 0;
      while (index < rowCount && tabularGroup.getCell(index, 0).getFirst() == null) {
        index++;
      }
      lastHeaderRowIndex = index - 1;
    }

    return lastHeaderRowIndex;
  }

  /**
   * @return True if any two consecutive rows in {@code table} contains numeric cells such that
   * index of these rows will be less than equal to {@code headerRows}, else return False
   */
  private static boolean areNumbersBeingMerged(TabularElementGroup<Element> table, int headerRows) {
    boolean prevRowHasNumeralCell = false;
    for (int rowIndex = 0; rowIndex <= headerRows; rowIndex++) {
      boolean currRowHasNumeralCell = containsNumericCell(table.getCells().get(rowIndex));
      if (prevRowHasNumeralCell && currRowHasNumeralCell) {
        return true;
      }
      prevRowHasNumeralCell = currRowHasNumeralCell;
    }
    return false;
  }

  /**
   * @return True if any of the cell in the row {@code row} is purely numeric cell
   */
  public static boolean containsNumericCell(MutableList<TabularCellElementGroup<Element>> row) {
    int currColIndex = 0;
    boolean numberCellSeen = false;
    while (currColIndex < row.size() && !numberCellSeen) {
      numberCellSeen = SemanticsChecker.RegexType.getFor(row.get(currColIndex).getTextStr())
          == SemanticsChecker.RegexType.NUMERIC;
      currColIndex++;
    }
    return numberCellSeen;
  }

  /**
   * @return bottom border coordinate from the {@code positionalContext}
   */
  private static Double getBottomBorderPosition(PositionalContext<Element> positionalContext) {
    int currRowIndex = positionalContext.getTabularRow();
    int currColIndex = positionalContext.getTabularColumn();
    TabularElementGroup<Element> table = positionalContext.getTabularGroup();
    if (currColIndex >= table.getCells().get(currRowIndex).size()) {
      return null;
    }
    TabularCellElementGroup<Element> cell = positionalContext.getTabularGroup()
        .getCell(positionalContext.getTabularRow(), positionalContext.getTabularColumn());
    return cell.getBorderExistence().getBottom() ? positionalContext.getVisualBottom() : null;
  }

  /**
   * @return True if the row at index {@code currRowIndex} of {@code table} is occuring below border
   * position {@code bottomBorderPosition}
   */
  private static boolean isRowBelowBorder(TabularElementGroup<Element> table, int currRowIndex,
      Double bottomBorderPosition) {
    return bottomBorderPosition != null && bottomBorderPosition <= table.getCells()
        .get(currRowIndex)
        .detect(cell -> cell.getElements().notEmpty()).getFirst().getPositionalContext()
        .getVisualTop();
  }

  @Override
  public List getRequiredKeys() {
    // Keys received as input
    return Lists.mutable.of(
        CustomScratchpadKeys.TABULAR_GROUP,
        CustomScratchpadKeys.DOCUMENT_SOURCE,
        CustomScratchpadKeys.PAGE_NUMBER,
        CustomScratchpadKeys.TABLE_INDEX,
        CustomScratchpadKeys.SPLIT_ROW_INDEX
    );
  }

  @Override
  public List getStoredKeys() {
    // Keys returned as output
    return Lists.mutable.of(
        CustomScratchpadKeys.PROCESSED_TABULAR_GROUP
    );
  }

  @Override
  public void execute(Scratchpad scratchpad) {
    this.scratchpad = scratchpad;
    TabularElementGroup<Element> tabularGroup = CustomScratchpadKeys.TABULAR_GROUP
        .retrieveFrom(scratchpad);
    TabularElementGroup<Element> processedTabularGroup = this.mergeColumnHeaders(tabularGroup);
    processedTabularGroup.setBackReferences();
    scratchpad.store(CustomScratchpadKeys.PROCESSED_TABULAR_GROUP, processedTabularGroup);
  }

  /**
   * Merge column headers of table {@code tabularGroup}
   *
   * @return processed tabular group
   */
  private TabularElementGroup<Element> mergeColumnHeaders(
      TabularElementGroup<Element> tabularGroup) {
    MutableList<HeaderRowsVotingCriterion> votingCriteria = Lists.mutable
        .of(HeaderRowsVotingCriterion.TEXT_STYLES, HeaderRowsVotingCriterion.REGEX);
    int headerRows = voteHeaderRows(tabularGroup, votingCriteria);

    if (headerRows > 0 && !areNumbersBeingMerged(tabularGroup, headerRows)) {
      this.logEntry("Merging header rows.");
      return tabularGroup.getNewRowMergedTable(0, headerRows);
    }
    this.logEntry("No header row merging required.");
    return tabularGroup;
  }

  /**
   * Class to represent voting criteria to determine rows which can be considered as column headers
   */
  private enum HeaderRowsVotingCriterion {
    TEXT_STYLES {
      @Override
      public int compare(ElementGroup<Element> verticalGroup, Element startingElement) {
        // Find the row where elements have different text style in comparision to {@code startingElement}
        TabularElementGroup<Element> startingTabularGroup = startingElement.getPositionalContext()
            .getTabularGroup();
        MutableList<Element> elements = verticalGroup.getElements();
        int startingElementIndex = elements.detectIndex(e -> e == startingElement);
        int i;
        for (i = startingElementIndex; i < verticalGroup.size(); i++) {
          boolean textStyleFollowed = startingElement
              .equalsAttributeValue(elements.get(i), TextStyles.class, List::equals);
          boolean colorFollowed = startingElement
              .equalsAttributeValue(elements.get(i), Color.class, java.awt.Color::equals);
          boolean sameTable = startingTabularGroup
              .equals(elements.get(i).getPositionalContext().getTabularGroup());

          if (!(textStyleFollowed && colorFollowed && sameTable)) {
            break;
          }
        }

        PositionalContext<Element> positionalContext = elements
            .get(i == startingElementIndex ? startingElementIndex : i - 1).getPositionalContext();
        int lastHeaderRowWithContent = positionalContext.getTabularRow();
        int currRow = lastHeaderRowWithContent + 1;
        int col = positionalContext.getTabularColumn();
        TabularElementGroup<Element> tabularGroup = positionalContext.getTabularGroup();

        // Increment row pointer to include blank rows as well
        Double borderCoordinate = getBottomBorderPosition(positionalContext);
        while (currRow < tabularGroup.numberOfRows() && col < tabularGroup.getCells().get(currRow)
            .size() && tabularGroup.getCell(currRow, col).getElements().isEmpty()
            && !isRowBelowBorder(tabularGroup, currRow, borderCoordinate)) {
          currRow++;
        }
        return currRow - 1;
      }

      @Override
      public boolean isApplicable(Element element) {
        return element.getAttribute(TextStyles.class) != null && (
            element.getAttribute(TextStyles.class).getValue().contains(TextStyles.BOLD)
                || element.getAttribute(TextStyles.class).getValue().contains(TextStyles.ITALIC));
      }
    },
    REGEX {
      @Override
      public int compare(ElementGroup<Element> verticalGroup, Element startingElement) {
        // Find the row of the text element in {@code verticalGroup} such that table belonging to that element is different
        // from {@code startingElement}'s table if possible
        TabularElementGroup<Element> startingTabularGroup = startingElement.getPositionalContext()
            .getTabularGroup();
        MutableList<Element> elements = verticalGroup.getElements();
        int startingElementIndex = elements.detectIndex(e -> e == startingElement);
        int i = startingElementIndex;
        while (i < verticalGroup.size() && SemanticsChecker
            .hasAlphabets(elements.get(i).getTextStr())) {
          boolean sameTable = startingTabularGroup
              .equals(elements.get(i).getPositionalContext().getTabularGroup());
          if (!sameTable) {
            break;
          }
          i++;
        }
        PositionalContext<Element> positionalContext = elements
            .get(i == startingElementIndex ? startingElementIndex : i - 1).getPositionalContext();
        return positionalContext.getTabularRow();
      }

      @Override
      public boolean isApplicable(Element element) {
        return true;
      }
    };

    public abstract int compare(ElementGroup<Element> verticalGroup, Element startingElement);

    /**
     * @return True if {@code element} satisfies the voting criteria
     */
    public abstract boolean isApplicable(Element element);
  }
}
