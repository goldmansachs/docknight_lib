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
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.eclipse.collections.impl.tuple.Tuples;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementCollection;
import com.gs.ep.docknight.model.ElementGroup;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.TabularCellElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup.VectorTag;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;

/**
 * Class to expand the table by include above elements either as table caption or table headers
 */
public final class TableExpander {

  public static final double ALIGNMENT_DELTA = 3.0;
  private static final int MAX_INTER_ROW_DISTANCE = 50;
  private static final String AMOUNTS_REGEX = "[(][a-z]*\\p{Space}?in\\p{Space}(?<Factor>[a-z]+)[)]";
  private static final Pattern MULTIPLICATIVE_FACTOR_PATTERN = Pattern
      .compile(AMOUNTS_REGEX, Pattern.CASE_INSENSITIVE);
  private static final MutableMap<String, Integer> MULTIPLICATIVE_FACTORS_MAP = Maps.mutable
      .of("hundreds", 100, "thousands", 1000, "millions", 100000);
  private static final MutableMap<Character, String> CURRENCY_MAP = Maps.mutable.empty();
  private static Pattern currencyPattern;

  private TableExpander() {
  }

  /**
   * Perform column expansion on {@code tabularGroup}. Expansion is done by checking whether row
   * elements above top row of table can become headers or not. Row elements are header if it
   * satisfies the properties like (bold, aligning with column or visual bottom border, etc). If
   * above row elements are header, add that row to existing table.
   *
   * @param tabularGroup table to be processed
   * @return processed table
   */
  public static TabularElementGroup<Element> performColumnExpansion(
      TabularElementGroup<Element> tabularGroup) {
    int headerCount = tabularGroup.getColumnHeaderCount();
    if (headerCount > 1) {
      return tabularGroup;
    }
    TabularElementGroup<Element> processedTabularGroup = tabularGroup;
    int targetRowIndex = tabularGroup.getColumn(tabularGroup.numberOfColumns() - 1)
        .detectIndex(tceg -> !tceg.getTextStr().isEmpty());
    if (targetRowIndex < 0) {
      return tabularGroup;
    }
    MutableList<Element> targetRow = tabularGroup.getMergedRows().get(targetRowIndex)
        .collect(ElementGroup::getFirst);
    MutableList<Element> expansionRow = getAboveExpansionRow(targetRow);
    MutableList<Element> expansionRowTrimmed = expansionRow.select(Objects::nonNull);
    if (expansionRowTrimmed.isEmpty()) {
      return processedTabularGroup;
    }
    Pair<double[], double[]> columnBoundaryArrays = tabularGroup.getColumnBoundaries();
    double[] columnBoundaryLefts = columnBoundaryArrays.getOne();
    double[] columnBoundaryRights = columnBoundaryArrays.getTwo();
    MutableList<Pair<Double, Double>> columnBoundaries = Lists.mutable.empty();
    for (int boundaryIndex = 0; boundaryIndex < tabularGroup.numberOfColumns(); boundaryIndex++) {
      columnBoundaries.add(
          Tuples.pair(columnBoundaryLefts[boundaryIndex], columnBoundaryRights[boundaryIndex]));
    }
    if (expansionRow.anySatisfy(e -> e != null && checkIfNoiseElement(e, columnBoundaries))) {
      expansionRow = getAboveExpansionRow(expansionRow);
    }
    expansionRowTrimmed = expansionRow.select(Objects::nonNull);
    if (expansionRowTrimmed.isEmpty() || expansionRow
        .anySatisfy(e -> tabularGroup.getElements().contains(e))) {
      return processedTabularGroup;
    }
    if (expansionRow.allSatisfy(e -> e == null || e.getAttribute(TextStyles.class) != null && e
        .getAttribute(TextStyles.class).getValue().contains(TextStyles.BOLD)
        && doesAlignWithAnyColumn(e, columnBoundaries))) {
      return addExpansionRowToTable(processedTabularGroup, expansionRowTrimmed,
          getRowSpanIndices(columnBoundaries, expansionRowTrimmed).collect(Pair::getOne));
    }
    // Explore possibility of multi-column headers
    MutableList<Pair<Integer, Integer>> expansionRowSpanIndices = getRowSpanIndices(
        columnBoundaries, expansionRowTrimmed);
    int elementIndex = 0;
    while (elementIndex < expansionRowTrimmed.size()) {
      ElementGroup<Element> expansionVG = expansionRowTrimmed.get(elementIndex)
          .getEnclosingVerticalGroups().getFirst();
      if (expansionVG == null) {
        break;
      }
      Element closestExpansionElement = expansionVG.getLast();
      double tableTop = tabularGroup.getCells().getFirst().collect(
          cell -> cell.getElements().isEmpty() ? Double.MAX_VALUE
              : cell.getFirst().getAttribute(Top.class).getMagnitude()).min();
      double distanceFromTable =
          tableTop - closestExpansionElement.getAttribute(Top.class).getMagnitude()
              - closestExpansionElement.getAttribute(Height.class).getMagnitude();
      if (distanceFromTable < 20.0 &&
          expansionVG.getTextStr().split("\\p{Space}").length <= 10 &&
          closestExpansionElement.getPositionalContext().isVisualBottomBorder() &&
          closestExpansionElement.getAttribute(TextStyles.class) != null &&
          closestExpansionElement.getAttribute(TextStyles.class).getValue()
              .contains(TextStyles.BOLD) &&
          expansionRowSpanIndices.get(elementIndex).getOne() < expansionRowSpanIndices
              .get(elementIndex).getTwo()) {
        elementIndex++;
      } else {
        break;
      }
    }
    // If all the expansion row elements are valid
    if (elementIndex == expansionRowTrimmed.size()) {
      processedTabularGroup = addExpansionRowToTable(processedTabularGroup, expansionRowTrimmed,
          expansionRowSpanIndices.collect(Pair::getOne));
      for (int spanIndex = 0; spanIndex < expansionRowSpanIndices.size(); spanIndex++) {
        for (int colIndex = expansionRowSpanIndices.get(spanIndex).getOne() + 1;
            colIndex <= expansionRowSpanIndices.get(spanIndex).getTwo(); colIndex++) {
          processedTabularGroup.getCell(0, colIndex).setHorizontallyMerged(true);
        }
      }
    }
    return processedTabularGroup;
  }

  /**
   * Get the row span indices. Row span indices tells column indices range which are nested elements
   * under the header expansionRow element. Example: <p>Header</p> <p>a | b | c</p> <p>Return
   * [Pair(0,2)]
   *
   * @param columnBoundaries column boundaries (left, right) for each column
   * @param expansionRow row elements for which span indices has to be found
   * @return row span indices
   */
  private static MutableList<Pair<Integer, Integer>> getRowSpanIndices(
      MutableList<Pair<Double, Double>> columnBoundaries, MutableList<Element> expansionRow) {
    return expansionRow.collect(expansionRowElement ->
    {
      int leftSpanIndex = columnBoundaries.detectIndex(
          boundaryPair -> boundaryPair.getTwo() >= expansionRowElement.getPositionalContext()
              .getAlignmentLeft());
      if (leftSpanIndex == -1) {
        leftSpanIndex = columnBoundaries.detectIndex(
            boundaryPair -> boundaryPair.getTwo() >= expansionRowElement.getPositionalContext()
                .getVisualLeft());
      }
      int rightSpanIndex = columnBoundaries.toReversed().detectIndex(
          boundaryPair -> boundaryPair.getOne() <= expansionRowElement.getPositionalContext()
              .getAlignmentRight());
      if (rightSpanIndex == -1) {
        rightSpanIndex = columnBoundaries.toReversed().detectIndex(
            boundaryPair -> boundaryPair.getOne() <= expansionRowElement.getPositionalContext()
                .getVisualRight());
      } else {
        rightSpanIndex = columnBoundaries.size() - 1
            - rightSpanIndex;  // TODO: This condition should be outside the else part
      }
      return Tuples.pair(leftSpanIndex, rightSpanIndex);
    });
  }

  /**
   * Update the caption of {@code tabularGroup}. Caption is selected such that either it is a header
   * element or it matches caption pattern of any table type
   *
   * @param tabularGroup table
   */
  public static void performCaptionExpansion(TabularElementGroup<Element> tabularGroup) {
    Element currentExpansionElement = tabularGroup.getElements().getFirst();
    double tableTop = currentExpansionElement.getAttribute(Top.class).getMagnitude();
    int expansionCount = 0;
    while (expansionCount < 10
        && currentExpansionElement.getAttribute(Top.class).getMagnitude() - tableTop <= 50) {
      expansionCount++;
      currentExpansionElement = currentExpansionElement.getPreviousSibling();
      if (currentExpansionElement == null) {
        break;
      }
      if (currentExpansionElement instanceof TextElement) {
        String expansionText = currentExpansionElement.getTextStr();
        if (ArrayAdapter.adapt(TableType.values())
            .anySatisfy(type -> type.getCaptionPatternToMatch().matcher(expansionText).find())
            || isValidHeaderElement(currentExpansionElement, tabularGroup)) {
          MutableList<Element> captionElements = currentExpansionElement
              .getEnclosingVerticalGroups().getFirst().getElements()
              .select(element -> isValidHeaderElement(element, tabularGroup));
          // If there is only a regex match then only include that element and not any other element in the vertical group
          if (captionElements.isEmpty()) {
            captionElements.add(currentExpansionElement);
          }
          TabularCellElementGroup<Element> caption = new TabularCellElementGroup<>();
          captionElements.each(caption::add);
          tabularGroup.setCaption(caption);
          break;
        }
      }
    }
    if (tabularGroup.getCaption() != null) {
      performTableCategorization(tabularGroup);
    }
  }

  /**
   * Update table caption with whole vertical group of that element if it satisfies caption pattern
   * of any table type
   */
  private static void performTableCategorization(TabularElementGroup<Element> table) {
    int startSpanIndex = table.getCaption().getFirst().getElementListIndex();
    int endSpanIndex = table.getFirst().getElementListIndex() + 1;
    if (startSpanIndex > endSpanIndex) {
      return;
    }
    MutableList<ElementGroup<Element>> groupsInSearchSpan = ListAdapter
        .adapt(table.getCaption().getFirst().getElementList().getElements())
        .subList(startSpanIndex, endSpanIndex).selectInstancesOf(TextElement.class)
        .flatCollect(ElementCollection::getEnclosingVerticalGroups).distinct();
    for (ElementGroup<Element> vg : groupsInSearchSpan) {
      String textToSearch = vg.getTextStr();
      if (textToSearch.split(SemanticsChecker.SINGLE_SPACE).length > 15) {
        continue;
      }
      for (TableType tableType : TableType.values()) {
        if (tableType.getCaptionPatternToMatch().matcher(textToSearch).find()) {
          table.setCaption(vg);
          return;
        }
      }
    }
  }

  /**
   * Check if {@code element} can be a header element for {@code tabularGroup}. Header element
   * satisfies the following properties: <ol> <li>It lies outside the table</li> <li>It has no
   * elements to its left/right direction</li> <li>It is written in bold text style</li> <li>It is a
   * complete text</li> <li>It's text length is sufficient (not too small or too large)</li> <li>It
   * should not be closed with round brackets</li> </ol>
   *
   * @param element element
   * @param tabularGroup table
   * @return boolean flag indicating whether {@code element} can be a header element for {@code
   * tabularGroup}
   */
  private static boolean isValidHeaderElement(Element element,
      TabularElementGroup<Element> tabularGroup) {
    MutableList<Element> leftElements = element.getPositionalContext().getLeftElements()
        .getElements();
    MutableList<Element> rightElements = element.getPositionalContext().getRightElements()
        .getElements();
    String elementText = element.getTextStr();

    boolean isExternalElement = !tabularGroup.getElements().contains(element);
    boolean isHorizontallySingularElement =
        leftElements.isEmpty() || leftElements.allSatisfy(Objects::isNull) || rightElements
            .isEmpty() || rightElements
            .allSatisfy(Objects::isNull); // TODO - check this condition. There should be "and" condition for left and right elements
    boolean isBoldText =
        element.getAttribute(TextStyles.class) != null && element.getAttribute(TextStyles.class)
            .getValue().contains(TextStyles.BOLD);
    boolean isCompleteText = !elementText.endsWith(",");
    boolean isImpliedText = elementText.startsWith("(") && elementText.endsWith(")");
    boolean isAcceptableTextLength =
        elementText.length() >= 10 && elementText.split("\\p{Space}").length <= 15;

    return isExternalElement && isBoldText && isHorizontallySingularElement && isCompleteText
        && !isImpliedText && isAcceptableTextLength;
  }

  /**
   * Check if {@code element} lies within any column and also is aligned with that column
   *
   * @param element element to be checked
   * @param columnBoundaries column boundaries (left, right) for each column
   * @return boolean indicating whether {@code element} lies within any column and also is aligned
   * with that column
   */
  private static boolean doesAlignWithAnyColumn(Element element,
      MutableList<Pair<Double, Double>> columnBoundaries) {
    return columnBoundaries.anySatisfy(boundary ->
        element.getAttribute(Left.class).getValue().getMagnitude() >= boundary.getOne() &&
            element.getAttribute(Left.class).getValue().getMagnitude() + element
                .getAttribute(Width.class).getValue().getMagnitude() <= boundary.getTwo() &&
            (element.getAttribute(Left.class).getValue().getMagnitude() - boundary.getOne()
                <= ALIGNMENT_DELTA ||
                boundary.getTwo() - element.getAttribute(Left.class).getValue().getMagnitude()
                    + element.getAttribute(Width.class).getValue().getMagnitude()
                    <= ALIGNMENT_DELTA));
  }

  /**
   * Create new table from {@code tabularGroup} by adding header row {@code expansionRowTrimmed}
   *
   * @param tabularGroup original table which will be processed
   * @param expansionRowTrimmed expansion row elements (first level header elements)
   * @param spanStartIndices column indices corresponding to expansion row elements
   * @return processed table
   */
  private static TabularElementGroup<Element> addExpansionRowToTable(
      TabularElementGroup<Element> tabularGroup, MutableList<Element> expansionRowTrimmed,
      MutableList<Integer> spanStartIndices) {
    TabularElementGroup<Element> processedTabularGroup = new TabularElementGroup<>(0,
        tabularGroup.numberOfColumns(), 1 + tabularGroup.getColumnHeaderCount());
    MutableList<Element> rearrangedExpansionRow = Lists.mutable.empty();
    int elementIndex = 0;
    for (int columnIndex = 0; columnIndex < tabularGroup.numberOfColumns(); columnIndex++) {
      if (spanStartIndices.contains(columnIndex)) {
        rearrangedExpansionRow.add(expansionRowTrimmed.get(elementIndex++));
      } else {
        rearrangedExpansionRow.add(null);
      }
    }

    // Add the header row in processed table
    processedTabularGroup.addRow(rearrangedExpansionRow.collect(e ->
    {
      TabularCellElementGroup<Element> cell = new TabularCellElementGroup<>();
      if (e != null) {
        for (Element vgElement : e.getEnclosingVerticalGroups().getFirst().getElements()) {
          cell.add(vgElement);
        }
      }
      return cell;
    }), 0);

    // Add the remaining rows and their properties to processed table
    for (int i = 0; i < tabularGroup.numberOfRows(); i++) {
      processedTabularGroup.addRow(tabularGroup.getCells().get(i), i + 1);
    }
    for (VectorTag tag : VectorTag.values()) {
      MutableSet<Integer> rowsForTagInSecondTable = tabularGroup.getVectorIndicesForTag(tag);
      rowsForTagInSecondTable.each(index -> processedTabularGroup.addVectorTag(tag, index + 1));
    }
    processedTabularGroup.setCaption(tabularGroup.getCaption());
    return processedTabularGroup;
  }

  /**
   * Find the above row elements that are above to {@code elementsToExpand}
   *
   * @param elementsToExpand elements of current row
   * @return above row elements
   */
  private static MutableList<Element> getAboveExpansionRow(MutableList<Element> elementsToExpand) {
    Element expansionRowElement = null;
    int expansionRowElementIndex = -1;
    double top = Double.MAX_VALUE;
    for (int elementIndex = 0; elementIndex < elementsToExpand.size(); elementIndex++) {
      Element elementToExpand = elementsToExpand.get(elementIndex);
      Element aboveElement =
          elementToExpand != null && elementToExpand.hasPositionalContext() ? elementToExpand
              .getPositionalContext().getShadowedAboveElement() : null;
      if (aboveElement != null && aboveElement.getAttribute(Top.class) != null
          && PositionalElementList.compareByHorizontalAlignment(elementToExpand, aboveElement)
          < MAX_INTER_ROW_DISTANCE
          && (aboveElement.getAttribute(Top.class).getValue().getMagnitude() < top
          || aboveElement.getPositionalContext().isVisualBottomBorder()
          && expansionRowElement != null && !expansionRowElement.getPositionalContext()
          .isVisualBottomBorder())) {
        top = aboveElement.getAttribute(Top.class).getValue().getMagnitude();
        expansionRowElement = aboveElement;
        expansionRowElementIndex = elementIndex;
      }
    }
    if (expansionRowElement == null) {
      return Lists.mutable.empty();
    }
    MutableList<Element> expansionRow = Lists.mutable.empty();

    // Find left elements left to expandionRowElement
    if (expansionRowElement.getPositionalContext().getLeftElements() != null) {
      MutableList<Element> leftElements = expansionRowElement.getPositionalContext()
          .getLeftElements().getElements().select(Objects::nonNull);
      // To handle cases where elements may not be perfectly aligned across the row
      while (leftElements.notEmpty()
          && leftElements.getFirst().getPositionalContext().getLeftElements() != null) {
        MutableList<Element> elementsToAdd = leftElements.getFirst().getPositionalContext()
            .getLeftElements().getElements().select(Objects::nonNull);
        if (elementsToAdd.isEmpty()) {
          break;
        }
        leftElements.addAll(0, elementsToAdd);
      }
      int leftElementsDelta = leftElements.size() - expansionRowElementIndex;
      if (leftElementsDelta > 0) {
        for (int deletedElementsCount = 0; deletedElementsCount < leftElementsDelta;
            deletedElementsCount++) {
          leftElements.remove(0);
        }
      } else if (leftElementsDelta < 0) {
        for (int insertedElementsCount = 0; insertedElementsCount > leftElementsDelta;
            insertedElementsCount--) {
          leftElements.add(0, null);
        }
      }
      expansionRow.addAll(leftElements);
    }
    expansionRow.add(expansionRowElement);

    // Find right elements to expandsionRowElement
    if (expansionRowElement.getPositionalContext().getRightElements() != null) {
      MutableList<Element> rightElements = expansionRowElement.getPositionalContext()
          .getRightElements().getElements().select(Objects::nonNull);
      // To handle cases where elements may not be perfectly aligned across the row
      while (rightElements.notEmpty()
          && rightElements.getLast().getPositionalContext().getRightElements() != null) {
        MutableList<Element> elementsToAdd = rightElements.getLast().getPositionalContext()
            .getRightElements().getElements().select(Objects::nonNull);
        if (elementsToAdd.isEmpty()) {
          break;
        }
        rightElements.addAll(elementsToAdd);
      }
      int rightElementsDelta =
          rightElements.size() + 1 + expansionRowElementIndex - elementsToExpand.size();
      if (rightElementsDelta > 0) {
        for (int deletedElementsCount = 0; deletedElementsCount < rightElementsDelta;
            deletedElementsCount++) {
          rightElements.remove(rightElements.size() - 1);
        }
      } else if (rightElementsDelta < 0) {
        for (int insertedElementsCount = 0; insertedElementsCount > rightElementsDelta;
            insertedElementsCount--) {
          rightElements.add(rightElements.size(), null);
        }
      }
      expansionRow.addAll(rightElements);
    }
    return expansionRow;
  }

  /**
   * Check if the {@code element} is noise element. Element is noise element if it satisfies all the
   * conditions: <ol> <li>There should not be any visual bottom border of this element</li>
   * <li>There should not be any elements to its left/right</li> <li>Element should not be aligned
   * to any of the columns</li> </ol>
   *
   * @param element element to be checked
   * @param columnBoundaries column boundaries (left, right) for each column
   * @return boolean flag indicating whether {@code element} is noise element
   */
  private static boolean checkIfNoiseElement(Element element,
      MutableList<Pair<Double, Double>> columnBoundaries) {
    MutableList<Element> leftElements = element.getPositionalContext().getLeftElements()
        .getElements();
    MutableList<Element> rightElements = element.getPositionalContext().getRightElements()
        .getElements();
    boolean isHorizontallySingularElement =
        (leftElements.isEmpty() || leftElements.allSatisfy(Objects::isNull)) && (
            rightElements.isEmpty() || rightElements.allSatisfy(Objects::isNull));

    return !element.getPositionalContext().isVisualBottomBorder() && isHorizontallySingularElement
        && !doesAlignWithAnyColumn(element, columnBoundaries);
  }

  /**
   * Get the currency associated with {@tabularGroup} if it exists
   *
   * @param tabularGroup table
   * @return pair of (currency symbol, currency name)
   */
  public static Pair<String, String> getAssociatedCurrency(
      TabularElementGroup<Element> tabularGroup) {
    String tabularString = tabularGroup.toString();
    Matcher currencyMatcher = getCurrencyPattern().matcher(tabularString);
    if (currencyMatcher.find()) {
      String currencySymbol = currencyMatcher.group();
      String currencyName = getCurrencyMap().get(currencySymbol.charAt(0));
      return Tuples.pair(currencySymbol, currencyName);
    }
    return null;
  }

  /**
   * Get the multiplicative factor for {@code tabularGroup}. Multiplicative factor is value by which
   * each amount in table should be multiplied in order to get its applicable amount. Example:
   * Return 100 if "value in hundreds" is listed inside the table
   *
   * @param tabularGroup table
   * @return multiplicative factor
   */
  public static Integer getMultiplicativeFactor(TabularElementGroup<Element> tabularGroup) {
    int firstElementIndex =
        tabularGroup.getCaption() == null ? tabularGroup.getElements().getFirst()
            .getElementListIndex() : tabularGroup.getCaption().getFirst().getElementListIndex();
    if (firstElementIndex > tabularGroup.getLast().getElementListIndex()) {
      return 1;
    }
    String tabularString = new ElementGroup<>(ListAdapter.adapt(
        tabularGroup.getElements().getFirst().getElementList().getElements()
            .subList(firstElementIndex, tabularGroup.getLast().getElementListIndex())))
        .getTextStr();
    Matcher factorMatcher = MULTIPLICATIVE_FACTOR_PATTERN.matcher(tabularString);
    if (factorMatcher.find()) {
      String factor = factorMatcher.group("Factor");
      if (MULTIPLICATIVE_FACTORS_MAP.containsKey(factor)) {
        return MULTIPLICATIVE_FACTORS_MAP.get(factor);
      }
    }
    return 1;
  }

  /**
   * Initialize currency pattern and currency map
   */
  private static void initializeCurrencyPattern() {
    try {
      MutableList<String> currencySymbolsLines = ListAdapter.adapt(IOUtils.readLines(
          TableExpander.class.getClassLoader().getResourceAsStream("currency_symbols.txt")));
      MutableList<String> currencySymbols = Lists.mutable.empty();
      for (int currencySymbolLineIndex = 1; currencySymbolLineIndex < currencySymbolsLines.size();
          currencySymbolLineIndex += 2) {
        Character currencySymbol = Character
            .toChars(Integer.decode(currencySymbolsLines.get(currencySymbolLineIndex - 1)))[0];
        CURRENCY_MAP.put(currencySymbol, currencySymbolsLines.get(currencySymbolLineIndex));
        currencySymbols
            .add(currencySymbolsLines.get(currencySymbolLineIndex - 1).replaceFirst("0x", "\\\\u"));
      }
      currencyPattern = Pattern.compile(currencySymbols.makeString("|"));
    } catch (IOException e) {
      throw new RuntimeException("Unable to load Currency Symbols file", e);
    }
  }

  /**
   * Getter for currency pattern. The pattern contains all the currency symbols
   *
   * @return currency pattern
   */
  public static Pattern getCurrencyPattern() {
    if (currencyPattern == null) {
      initializeCurrencyPattern();
    }
    return currencyPattern;
  }

  /**
   * Getter for currency map. Currency map is a map with key as currency symbol and value as
   * currency in words.
   *
   * @return currency map
   */
  public static MutableMap<Character, String> getCurrencyMap() {
    if (CURRENCY_MAP.isEmpty()) {
      initializeCurrencyPattern();
    }
    return CURRENCY_MAP;
  }
}
