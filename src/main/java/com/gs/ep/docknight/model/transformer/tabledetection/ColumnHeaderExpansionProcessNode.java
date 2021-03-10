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
import com.gs.ep.docknight.model.ElementCollection;
import com.gs.ep.docknight.model.ElementGroup;
import com.gs.ep.docknight.model.PositionalContext;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.TabularCellElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.context.PagePartitionType;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Set;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.partition.list.PartitionMutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;

/**
 * Process node in table detection workflow to expand column headers and also to detect table
 * caption
 */
public class ColumnHeaderExpansionProcessNode extends AbstractProcessNode {

  public static final double COLUMN_HEADER_COVERAGE_FACTOR = 0.5;
  private static final int LEFT_TABLE_BOUNDARY_ALLOWANCE = 10;

  /**
   * Filter elements in {@code aboveLine} that does not lie too beyond the bounding box {@code
   * tableBBox}
   */
  private static MutableList<Element> filterElementsOnVisualBoundary(Element[] aboveLine,
      Rectangle2D tableBBox) {
    MutableList<Element> aboveLineElements = ArrayIterate
        .select(aboveLine, a -> a != null && !isElementBeyondBoundingBox(a, tableBBox));

    if (aboveLineElements.size() == 1) {
      Element elementInAboveLine = aboveLineElements.get(0);
      TabularElementGroup<Element> aboveLineTabularGroup = elementInAboveLine.getPositionalContext()
          .getTabularGroup();
      if (aboveLineTabularGroup == null) {
        Element previousElementInVerticalGroup = getPreviousElementInVerticalGroup(
            elementInAboveLine);
        if (previousElementInVerticalGroup != null && isElementBeyondBoundingBox(
            previousElementInVerticalGroup, tableBBox)) {
          return Lists.mutable.empty();
        }
      }
    }
    return aboveLineElements;
  }

  /**
   * @return previous element which is present in same vertical group as {@code element}
   */
  private static Element getPreviousElementInVerticalGroup(Element element) {
    MutableList<Element> elements = element.getPositionalContext().getVerticalGroup().getElements();
    int vgIndex = elements.detectIndex(e -> e == element);
    if (vgIndex != 0) {
      return elements.get(vgIndex - 1);
    }
    return null;
  }

  /**
   * @return first non empty cell from the {@code tabularGroup} in the column at index {@code
   * colNum} while traversing rows from {@code startingRowNum} in reverse order
   */
  private static Element detectFirstElementInColumnInReverse(
      TabularElementGroup<Element> tabularGroup, int colNum, int startingRowNum) {
    TabularCellElementGroup<Element> elementGroup = tabularGroup.getCell(startingRowNum, colNum);
    for (int i = startingRowNum; i >= 0 && Iterate.isEmpty(elementGroup.getElements()); i--) {
      elementGroup = tabularGroup.getCell(i, colNum);
    }
    return elementGroup.getFirst();
  }

  /**
   * Add the elements of vertical group corresponding to element {@code elementInAboveLine} to
   * {@code caption}
   *
   * @param elementInAboveLine elements which are present in above line
   * @param caption caption cell which will be modified
   * @param aboveLineCount If it is equal to 1, then there is no need to add elements to caption
   * which are part of last line of paragraph
   */
  private static void addVerticalGroupElementsToCaption(Element elementInAboveLine,
      TabularCellElementGroup<Element> caption, int aboveLineCount) {
    MutableList<Element> elements = elementInAboveLine.getPositionalContext().getVerticalGroup()
        .getElements();
    int vgIndex = elements.detectIndex(e -> e == elementInAboveLine);
    while (vgIndex >= 0) {
      Element aboveElement = elements.get(vgIndex);
      if (aboveLineCount == 1 && SemanticsChecker
          .isLastLineOfParagraph(aboveElement.getTextStr())) {
        break;
      }
      caption.add(aboveElement);
      vgIndex--;
    }
  }

  /**
   * @return True if {@code element} lies out the table bounding box{@code tableBBox}
   */
  private static boolean isElementBeyondBoundingBox(Element element, Rectangle2D tableBBox) {
    double bBoxLeft = tableBBox.getMinX();
    double left = element.getAttribute(Left.class).getValue().getMagnitude();
    double width = element.getAttribute(Width.class).getValue().getMagnitude();
    double right = left + width;
    return left < bBoxLeft - LEFT_TABLE_BOUNDARY_ALLOWANCE || right > tableBBox.getMaxX();
  }

  /**
   * @return boundingRectangle (top, left, width, height) of the table {@code tabularGroup}
   */
  private static Rectangle2D getBorderBoxForTable(TabularElementGroup<Element> tabularGroup) {
    Element firstElement = tabularGroup.getFirst();
    return firstElement.getPositionalContext().getBoundingRectangle();
  }

  /**
   * Populate elements from a table other than {@code tabularGroup}  in {@code aboveLine} such that
   * elements are at above position than the first row elements of {@code tabularGroup}
   */
  protected static void populateAboveLine(Element[] aboveLine,
      TabularElementGroup<Element> tabularGroup) {
    int colCount = tabularGroup.numberOfColumns();
    Element firstElement = null;
    double top = Double.MAX_VALUE;

    //Find the element in first row which has lowest top attribute (coming at the top position in the page)
    for (int j = 0; j < colCount; j++) {
      if (tabularGroup.getMergedCell(0, j).getElements().notEmpty()) {
        Element currentElement = tabularGroup.getMergedCell(0, j).getFirst();
        if (currentElement.getAttribute(Top.class).getValue().getMagnitude() < top) {
          top = currentElement.getAttribute(Top.class).getValue().getMagnitude();
          firstElement = currentElement;
        }
      }
    }

    // Populate elements from different table and which are coming above the firstElement in variable aboveLine
    if (firstElement != null) {
      int pageBreakNumber = firstElement.getPositionalContext().getPageBreakNumber();
      PagePartitionType partitionType = firstElement.getPositionalContext().getPagePartitionType();
      MutableList<TextElement> previousElements = firstElement.getPositionalContext()
          .getPreviousElements().selectInstancesOf(TextElement.class)
          .select(e -> e.getPositionalContext().getTabularGroup() != tabularGroup).take(colCount)
          .select(e ->
          {
            PositionalContext<Element> context = e.getPositionalContext();
            return context.getPageBreakNumber() == pageBreakNumber
                && context.getPagePartitionType() == partitionType;
          }).toList();
      for (int i = 0; i < previousElements.size(); i++) {
        aboveLine[colCount - i - 1] = previousElements.get(i);
      }
    }
  }

  /**
   * Set the aboveLine elements to null which are not on the same line as element {@code
   * aboveLine}[{@code nearestIndex}]
   */
  private static void filterAboveLineBasedOnHorAlignment(Element[] aboveLine, int nearestIndex) {
    for (int i = 0; i < aboveLine.length; i++) {
      if (i != nearestIndex && aboveLine[i] != null &&
          PositionalElementList.compareByHorizontalAlignment(aboveLine[i], aboveLine[nearestIndex])
              != 0) {
        aboveLine[i] = null;
      }
    }
  }

  /**
   * Update aboveLine element to null if there is no overlap of the element with any of the table
   * column's otherwise update aboveLine element such that there is maximum overlap with the table
   * column
   *
   * @param aboveLine TextElements in the line above the table.
   * @param tabularGroup table of which the columns are used for calculating vertical alignment
   * @return the average overlap between aboveLine and table columns
   */
  private static double filterAboveLineBasedOnVerAlignment(Element[] aboveLine,
      TabularElementGroup<Element> tabularGroup) {
    Pair<double[], double[]> columnBoundaries = tabularGroup.getColumnBoundaries();
    double[] columnLeftBoundaries = columnBoundaries.getOne();
    double[] columnRightBoundaries = columnBoundaries.getTwo();
    double expansionConfidence = 0.0;

    for (int j = 0; j < tabularGroup.numberOfColumns(); j++) {
      boolean found = false;
      double overlap = 0;
      /* for each column choose the closest above element considering alignment */
      for (int i = 0; i < aboveLine.length; i++) {
        if (aboveLine[i] != null) {
          PositionalContext<Element> positionalContext = aboveLine[i].getPositionalContext();
          double width = columnRightBoundaries[j] - columnLeftBoundaries[j];
          double errorAllowed = j == 0 ? 0.1 : 0.5;
          if (positionalContext.getVisualLeft() - columnLeftBoundaries[j] < errorAllowed * width
              && columnRightBoundaries[j] - positionalContext.getVisualRight()
              < errorAllowed * width) {
            double currOverlap = Math.max(0,
                Math.min(columnRightBoundaries[j], positionalContext.getVisualRight()) - Math
                    .max(positionalContext.getVisualLeft(), columnLeftBoundaries[j])) / width;
            if (overlap < currOverlap) {
              aboveLine[j] = aboveLine[i];
              overlap = currOverlap;
              found = true;
            }
          }
        }
      }
      if (found) {
        expansionConfidence += overlap;
      } else {
        aboveLine[j] = null;
      }
    }
    return expansionConfidence / tabularGroup.numberOfColumns();
  }

  /**
   * @return index of {@code tableToDelete} on the page where {@code element} is present
   */
  private static int getTableIndexInPage(Element element,
      TabularElementGroup<Element> tableToDelete) {
    return ((PositionalElementList<Element>) element.getElementListContext().getOne())
        .getTabularGroups().indexOf(tableToDelete);
  }

  /**
   * @return True if all values in {@code aboveLine} is null, else return False
   */
  private static boolean isAboveLineEmpty(Element[] aboveLine) {
    int currIndex = 0;
    while (currIndex < aboveLine.length && aboveLine[currIndex] == null) {
      currIndex++;
    }
    return currIndex == aboveLine.length;
  }

  /**
   * Form the extended cell content by including the aboveLine[i] element and elements from its
   * vertical group based on text properties of cell in {@code tabularGroup}
   *
   * @return the created cell corresponding to aboveLine[i] element
   */
  private static TabularCellElementGroup<Element> getExtendedCellContent(Element[] aboveLine,
      TabularElementGroup<Element> tabularGroup, int i) {
    TabularCellElementGroup<Element> cell = new TabularCellElementGroup<>();
    if (aboveLine[i] != null) {
      MutableList<Element> elements = aboveLine[i].getPositionalContext().getVerticalGroup()
          .getElements();

      int row = 0;
      while (tabularGroup.getMergedCell(row, i).getElements().isEmpty()) {
        row++;
      }
      Element lastElement = aboveLine[i];
      Element firstElement = tabularGroup.getMergedCell(row, i).getFirst();
      for (int vgIndex = elements.detectIndex(e -> e == lastElement); vgIndex >= 0; vgIndex--) {
        Element nextElement = filterAboveElementBasedOnTextAttr(firstElement,
            elements.get(vgIndex));
        if (nextElement == null) {
          break;
        }
        cell.add(nextElement);
      }
    }
    return cell;
  }

  /**
   * If {@code elements} are present in last two rows of the table, then they can be deleted from
   * that table, otherwise they can not be deleted
   *
   * @return flag representing whether deletion of the {@code elements} from table is possible or
   * not
   */
  private static boolean isDeletionFromPreviousTablesPossible(MutableList<Element> elements) {
    for (Element element : elements) {
      PositionalContext<Element> elementListContext = element.getPositionalContext();
      TabularElementGroup<Element> previousTable = elementListContext.getTabularGroup();
      if (previousTable != null) {
        int rowNum = elementListContext.getTabularRow();
        if (rowNum < previousTable.numberOfRows() - 2) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Method to check whether {@code aboveElement} and {@code firstElement} can come in two
   * consecutive rows based on text attributes
   *
   * @return {@code aboveElement} if they can come in consecutive rows else return False
   */
  private static Element filterAboveElementBasedOnTextAttr(Element firstElement,
      Element aboveElement) {
    Element filteredElement = null;
    if (aboveElement != null
        && aboveElement.equalsAttributeValue(firstElement, FontSize.class,
        (l1, l2) -> l1.getMagnitude() >= l2.getMagnitude())
        && (firstElement.getAttribute(TextStyles.class) == null || firstElement
        .equalsAttributeValue(aboveElement, TextStyles.class, (l1, l2) -> l2.containsAll(l1)))
        && SemanticsChecker.RegexType.getFor(aboveElement.getTextStr().trim()).getPriority()
        >= SemanticsChecker.RegexType.ALPHA.getPriority()) {
      filteredElement = aboveElement;
    }
    return filteredElement;
  }

  @Override
  public List getRequiredKeys() {
    return Lists.mutable.of(
        CustomScratchpadKeys.PROCESSED_TABULAR_GROUP,
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
        CustomScratchpadKeys.PROCESSED_TABULAR_GROUP,
        CustomScratchpadKeys.PREV_TABLES_TO_DELETE
    );
  }

  @Override
  public void execute(Scratchpad scratchpad) {
    this.scratchpad = scratchpad;
    TabularElementGroup<Element> tabularGroup = CustomScratchpadKeys.PROCESSED_TABULAR_GROUP
        .retrieveFrom(scratchpad);
    this.expandColumnHeaderDown(tabularGroup);
    TabularElementGroup<Element> processedTabularGroup = this.expandUp(tabularGroup);
    scratchpad.store(CustomScratchpadKeys.PROCESSED_TABULAR_GROUP, processedTabularGroup);
  }

  /**
   * Populate column header count of {@code tabularGroup}. (Expanding column header in download
   * direction from first row of table {@code tabularGroup})
   */
  private void expandColumnHeaderDown(TabularElementGroup<Element> tabularGroup) {
    int rowCount = tabularGroup.numberOfRows();
    int colCount = tabularGroup.numberOfColumns();
    boolean[] headerFound = new boolean[colCount];
    double minHeaderCoverageRequired = COLUMN_HEADER_COVERAGE_FACTOR * colCount;
    int coverage = 0;
    //Find non empty cell in each column. Each non empty cell contributes to the coverage.
    // If coverage is greater than minimum required header coverage, then column header count = max(index of row of each non empty cell found earlier)
    for (int r = 0; r < rowCount; r++) {
      for (int c = 0; c < colCount; c++) {
        if (!headerFound[c] && tabularGroup.getCell(r, c).isNotEmpty()) {
          headerFound[c] = true;
          coverage++;
          if (coverage >= minHeaderCoverageRequired) {
            tabularGroup.setColumnHeaderCount(Math.max(r + 1, tabularGroup.getColumnHeaderCount()));
            return;
          }
        }
      }
    }
  }

  /**
   * Expanding column headers in upward direction for first row of table {@code tabularGroup}
   */
  private TabularElementGroup<Element> expandUp(TabularElementGroup<Element> tabularGroup) {
    int colCount = tabularGroup.numberOfColumns();

    Element[] aboveLine = new Element[colCount];

    /*1. Prepare a candidate above row*/
    populateAboveLine(aboveLine, tabularGroup);

    /*2. Choose the nearest one in the candidate row*/
    int nearestIndex = ArrayIterate.collect(aboveLine, e -> e == null ? 0
        : (e.getAttribute(Top.class).getValue().getMagnitude() + e.getAttribute(Height.class)
            .getValue().getMagnitude())).zipWithIndex().maxBy(Pair::getOne).getTwo();

    /*3. Retain all those are horizontally aligned with the nearest one and eliminate others*/
    filterAboveLineBasedOnHorAlignment(aboveLine, nearestIndex);

    if (isAboveLineEmpty(aboveLine)) {
      return tabularGroup;
    }

    Rectangle2D tableBBox = getBorderBoxForTable(tabularGroup);
    boolean aboveLineAndTableHaveDifferentBBox = ArrayIterate.allSatisfy(aboveLine, r -> r == null
        || r.getPositionalContext().getBoundingRectangle() != tableBBox);
    if (tableBBox != null && aboveLineAndTableHaveDifferentBBox) {
      return this
          .getCaptionForTableWithBBoxDifferentFromAboveLineBBox(tabularGroup, aboveLine, tableBBox);
    }
    /*4. If table does not have a bounding box, find the left and right boundaries of each column and go to next step*/
    double expansionConfidence = filterAboveLineBasedOnVerAlignment(aboveLine, tabularGroup);

    /*5. Validate and Create new Row*/
    return this.getTabularGroupWithExpandedHeaders(aboveLine, tabularGroup, expansionConfidence);
  }

  /**
   * This method will be called only when table has well defined bounding box and caption is outside
   * of the bounding box.
   *
   * Caption will be found only if all the following scenarios are satisfied. 1. Above Line does not
   * have a numeric text element except for the first element. 2. Above Line has atleast one text
   * element that is within the table boundary. 3. Above Line should not be the last line of a
   * paragraph. 4. If above line is part of table, then it should either be part of one single
   * column or it should satisfy the oneElementHeader condition with its previous row element in the
   * same column.
   *
   * If the above conditions are satisfied, following will be considered as caption. 1. If the above
   * line is part of table and if it is one column, then the rows with just that column filled from
   * the last row along with elements that is outside the above line table which are within boundary
   * of the table will be considered as caption. 2. If the above line is part of table but not all
   * lie in one column, then the last row along with elements that is outside the above line table
   * which are within boundary of the table are considered as caption. 3. Else the vertical group
   * containing the above line will be considered as caption. If the above line has one text
   * element, then while traversing the vertical group from bottom, we stop if any of the text
   * elements ends with a period. and the traversed portion will be considered as caption.
   *
   * @param tabularGroup Table for which caption has to be found.
   * @param aboveLine TextElements in the line above the table.
   * @param tableBBox Table's Bounding box.
   * @return table with caption if caption is found.
   */
  private TabularElementGroup<Element> getCaptionForTableWithBBoxDifferentFromAboveLineBBox(
      TabularElementGroup<Element> tabularGroup, Element[] aboveLine, Rectangle2D tableBBox) {
    MutableList<Element> aboveLineElements = filterElementsOnVisualBoundary(aboveLine, tableBBox);
    MutableList<String> strings = aboveLineElements.collect(Element::getTextStr);
    if (aboveLineElements.isEmpty() || !SemanticsChecker.canBeHeader(strings)) {
      return tabularGroup;
    }
    boolean isAboveLinePartOfTable = aboveLineElements
        .anySatisfy(a -> a.getPositionalContext().getTabularColumn() != null);
    if (isAboveLinePartOfTable && this.areElemsPartOfTableRowSpanningMultipleCols(aboveLineElements)
        && !this.isVisualStyleDifferentFromPrevTableRow(aboveLineElements)) {
      return tabularGroup;
    }
    return this.getTableWithCaption(tabularGroup, aboveLineElements, isAboveLinePartOfTable);
  }

  /**
   * Populate caption in the table
   *
   * @param tabularGroup table for which caption has to be updated
   * @param aboveLineElements elements which are present above the table
   * @param isAboveLinePartOfTable flag to check if above lines elements are part of table
   * @return updated table
   */
  private TabularElementGroup<Element> getTableWithCaption(
      TabularElementGroup<Element> tabularGroup,
      MutableList<Element> aboveLineElements, boolean isAboveLinePartOfTable) {
    this.logEntry("Caption found.");
    if (isAboveLinePartOfTable) {
      if (aboveLineElements.size() == 1 && !this
          .areElemsPartOfTableRowSpanningMultipleCols(aboveLineElements)) {
        return this.getTableCaptionGivenAboveLineElementsArePartOfRowOfSizeOne(tabularGroup,
            aboveLineElements);
      }
      return this
          .getTableCaptionGivenAboveLineElementsArePartOfRowSpanningMultipleCols(tabularGroup,
              aboveLineElements);
    }
    return this.getTableCaptionTraversingVerticalGroup(tabularGroup, aboveLineElements);
  }

  /**
   * @return True if {@code aboveLineElements} are part of table and is spanning multiple columns.
   * (returns True if number of non empty cell in a table row is greater than one)
   */
  private boolean areElemsPartOfTableRowSpanningMultipleCols(
      MutableList<Element> aboveLineElements) {
    Element element = aboveLineElements
        .detect(a -> a.getPositionalContext().getTabularGroup() != null);
    if (element == null) {
      return false;
    }
    PositionalContext<Element> elementListData = element.getPositionalContext();
    TabularElementGroup<Element> aboveLineTabularGroup = elementListData.getTabularGroup();
    Integer tabularRow = elementListData.getTabularRow();
    MutableList<? extends ElementGroup<Element>> elementGroups = aboveLineTabularGroup
        .getMergedRows().get(tabularRow);
    return elementGroups.count(cell -> Iterate.notEmpty(cell.getElements())) > 1;
  }

  /**
   * Add the {@code aboveLineElements} to the table caption of {@code tabularGroup}. Also, remove
   * the {@code aboveLineElements} from the existing table if it exists
   *
   * @param tabularGroup table for which the caption has to be updated
   * @param aboveLineElements elements which are present above the table
   * @return updated table
   */
  private TabularElementGroup<Element> getTableCaptionGivenAboveLineElementsArePartOfRowSpanningMultipleCols(
      TabularElementGroup<Element> tabularGroup, MutableList<Element> aboveLineElements) {
    TabularCellElementGroup<Element> caption = new TabularCellElementGroup<>();
    aboveLineElements.each(aboveLineElement ->
    {
      PositionalContext<Element> elementListData = aboveLineElement.getPositionalContext();
      TabularElementGroup<Element> aboveLineTabularGroup = elementListData.getTabularGroup();
      caption.add(aboveLineElement);
      if (aboveLineTabularGroup != null) {
        int row = elementListData.getTabularRow();
        aboveLineTabularGroup.deleteRow(row);
      }
    });
    tabularGroup.setCaption(caption);
    return tabularGroup;
  }

  /**
   * @return True if any element in {@code aboveLineElements} has different style from the immediate
   * above element (which is present in previous table row)
   */
  private boolean isVisualStyleDifferentFromPrevTableRow(MutableList<Element> aboveLineElements) {
    return aboveLineElements.anySatisfy(aboveLineElement ->
    {
      PositionalContext<Element> elementListData = aboveLineElement.getPositionalContext();
      TabularElementGroup<Element> aboveLineTabularGroup = elementListData.getTabularGroup();
      if (aboveLineTabularGroup != null) {
        int row = elementListData.getTabularRow();
        int col = elementListData.getTabularColumn();
        if (row == 0) {
          return false;
        }
        Element firstElement = detectFirstElementInColumnInReverse(aboveLineTabularGroup, col,
            row - 1);
        if (firstElement == null || aboveLineElement
            .hasDifferentVisualStylesFromElement(firstElement)) {
          return true;
        }
      }
      return false;
    });
  }

  /**
   * Populate caption in {@code tabularGroup} if the following conditions are satisfied 1. there
   * exists a table of which there is a element in list {@code aboveLineElements} 2.
   * aboveLineElements should be present in a table as its last row
   *
   * @param tabularGroup table of which caption has to be populated
   * @param aboveLineElements These are the elements present at the top of {@code tabularGroup} and
   * will contribute in forming of caption
   */
  private TabularElementGroup<Element> getTableCaptionGivenAboveLineElementsArePartOfRowOfSizeOne(
      TabularElementGroup<Element> tabularGroup, MutableList<Element> aboveLineElements) {
    PartitionMutableList<Element> elementsOutsideAndPartOfTable = aboveLineElements
        .partition(element ->
            element.getPositionalContext().getTabularGroup() == null);
    if (elementsOutsideAndPartOfTable.getRejected().isEmpty()) {
      return tabularGroup;
    }
    Element elementInAboveLine = elementsOutsideAndPartOfTable.getRejected().get(0);
    PositionalContext<Element> elementListData = elementInAboveLine.getPositionalContext();
    TabularElementGroup<Element> aboveLineTabularGroup = elementListData.getTabularGroup();
    int rowNum = elementListData.getTabularRow();
    int colNum = elementListData.getTabularColumn();
    if (rowNum != aboveLineTabularGroup.numberOfRows()
        - 1)// aboveLine[i] is not last row of the table which is hypothetical
    {
      return tabularGroup;
    }
    MutableList<MutableList<TabularCellElementGroup<Element>>> cells = aboveLineTabularGroup
        .getCells();
    TabularCellElementGroup<Element> caption = new TabularCellElementGroup<>();
    MutableList<TabularCellElementGroup<Element>> cellsOfRow = cells.get(rowNum);
    MutableList<Element> cell = cellsOfRow.get(colNum).getElements();

    // Populate cell content from column colNum to caption cell if the number of non empty cells in the row is 1
    for (int filledColumnCount = 1; rowNum > 0 && filledColumnCount == 1 && cell.notEmpty();
        rowNum--) {
      cell.each(caption::add);
      aboveLineTabularGroup.deleteRow(rowNum);
      cellsOfRow = cells.get(rowNum - 1);
      filledColumnCount = cellsOfRow.count(c -> c.getElements().notEmpty());
      cell = cellsOfRow.get(colNum).getElements();
    }
    elementsOutsideAndPartOfTable.getSelected().each(element -> ColumnHeaderExpansionProcessNode
        .addVerticalGroupElementsToCaption(element, caption, 1));
    tabularGroup.setCaption(caption);
    return tabularGroup;
  }

  /**
   * Add the elements of vertical group corresponding to elements {@code aboveLineElements} to the
   * caption of table {@code tabularGroup}
   *
   * @param tabularGroup table for which caption has to be populated
   * @param aboveLineElements elmenets which are present in above the table {@code tabularGroup}
   * @return updated table
   */
  private TabularElementGroup<Element> getTableCaptionTraversingVerticalGroup(
      TabularElementGroup<Element> tabularGroup, MutableList<Element> aboveLineElements) {
    TabularCellElementGroup<Element> caption = new TabularCellElementGroup<>();
    aboveLineElements.each(
        elementInAboveLine -> addVerticalGroupElementsToCaption(elementInAboveLine, caption,
            aboveLineElements.size()));
    tabularGroup.setCaption(caption);
    return tabularGroup;
  }

  /**
   * Add the {@code aboveLine} elements either to table caption or add them as new row to the table
   * {@code tabularGroup}. Also maintain consistency for back references for any modification
   */
  private TabularElementGroup<Element> getTabularGroupWithExpandedHeaders(Element[] aboveLine,
      TabularElementGroup<Element> tabularGroup, double confidence) {
    if (isAboveLineEmpty(aboveLine)) {
      return tabularGroup;
    }

    // Construct the newRow using aboveLine elmeents
    MutableList<TabularCellElementGroup<Element>> newRow = Lists.mutable.empty();

    TabularCellElementGroup<Element> cell = getExtendedCellContent(aboveLine, tabularGroup, 0);
    newRow.add(cell);

    int mergers = 0;
    int expand = 0;
    int colCount = tabularGroup.numberOfColumns();
    for (int i = 1; i < colCount; i++) {
      if (aboveLine[i] == null) {
        expand = 0;
        break;
      }
      if (aboveLine[i] == aboveLine[i - 1]) {
        newRow.add(new TabularCellElementGroup<>(false, true));
        mergers++;
      } else {
        cell = getExtendedCellContent(aboveLine, tabularGroup, i);
        if (cell.getElements().notEmpty()) {
          newRow.add(cell);
          expand += i > 1 ? 1 : 0;
        } else {
          expand = 0;
          break;
        }
      }
    }

    // Add the new Row elements as table caption
    TabularElementGroup<Element> finalTable = tabularGroup;
    if (mergers >= colCount - 2 && expand == 0 && confidence == 1
        && newRow.size() == aboveLine.length && newRow.size() > 1 &&
        ((newRow.get(0).getElements().notEmpty() &&
            SemanticsChecker.RegexType.getFor(newRow.get(0).getFirst().getTextStr()).getPriority()
                > 1) || (newRow.get(1).getElements().notEmpty() &&
            SemanticsChecker.RegexType.getFor(newRow.get(1).getFirst().getTextStr()).getPriority()
                > 1))
        && isDeletionFromPreviousTablesPossible(newRow.get(1).getElements())
        && isDeletionFromPreviousTablesPossible(newRow.get(0).getElements())) {
      this.deleteEntriesFromPreviousTables(newRow.get(0).getElements());
      this.deleteEntriesFromPreviousTables(newRow.get(1).getElements());
      ElementGroup<Element> caption = new ElementGroup<>();
      if (newRow.get(0).getElements().notEmpty()) {
        for (Element ele : newRow.get(0).getElements()) {
          caption.add(ele);
        }
      }
      for (Element ele : newRow.get(1).getElements()) {
        caption.add(ele);
      }
      this.logEntry("Caption found.");
      tabularGroup.setCaption(caption);
    }

    // expand for headers and parentHeaders
    MutableList<Element> newRowElements = ListIterate
        .flatCollect(newRow, ElementGroup::getElements);
    if (((expand >= colCount - 2 && mergers == 0 && colCount >= 2 && expand > 0) || (expand > 0
        && mergers > 0)) && isDeletionFromPreviousTablesPossible(newRowElements)) {
      this.deleteEntriesFromPreviousTables(newRowElements);
      TabularElementGroup<Element> newTabularGroup = new TabularElementGroup<>(0,
          tabularGroup.numberOfColumns(),
          Math.max(tabularGroup.getColumnHeaderCount(), DEFAULT_COLUMN_HEADER_COUNT));
      newTabularGroup.addRow(newRow, 0);
      for (int i = 0; i < tabularGroup.numberOfRows(); i++) {
        newTabularGroup.addRow(tabularGroup.getCells().get(i), i + 1);
      }
      this.logEntry("Parent header found after expansion.");
      finalTable = newTabularGroup;
      finalTable.setConfidence(TableDetectionConfidenceFeatures.EXPAND_UP_CONFIDENCE, confidence);
      if (finalTable.getCells().get(0).select(ElementCollection::isNotEmpty).size() != finalTable
          .getCells().get(1).select(ElementCollection::isNotEmpty).size()) {
        finalTable.setColumnHeaderCount(finalTable.getColumnHeaderCount() + 1);
      }
      finalTable.setCaption(tabularGroup.getCaption());
    }
    finalTable.setBackReferences();
    return finalTable;
  }

  /**
   * Delete the row from the table where the {@code elements} are present
   */
  private void deleteEntriesFromPreviousTables(MutableList<Element> elements) {
    MutableMap<TabularElementGroup<Element>, Boolean> modifiedPreviousTables = Maps.mutable.empty();
    for (int currElementIndex = elements.size() - 1; currElementIndex >= 0; currElementIndex--) {
      PositionalContext<Element> elementListContext = elements.get(currElementIndex)
          .getPositionalContext();
      TabularElementGroup<Element> prevTable = elementListContext.getTabularGroup();
      if (prevTable != null) {
        int rowNumber = elementListContext.getTabularRow();
        prevTable.deleteRow(rowNumber);
        modifiedPreviousTables.put(prevTable, false);
        if (prevTable.getCells().isEmpty()) {
          Set<Integer> tablesToDelete = CustomScratchpadKeys.PREV_TABLES_TO_DELETE
              .retrieveFrom(this.scratchpad);
          tablesToDelete.add(getTableIndexInPage(elements.get(currElementIndex), prevTable));
          this.scratchpad.store(CustomScratchpadKeys.PREV_TABLES_TO_DELETE, tablesToDelete);
          modifiedPreviousTables.put(prevTable, true);
        }
      }
    }
    modifiedPreviousTables.select((prevTable, isEmpty) -> isEmpty)
        .forEach((prevTable, isEmpty) -> prevTable.setBackReferences());
  }
}
