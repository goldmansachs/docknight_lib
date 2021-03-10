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
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.util.List;
import java.util.Objects;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;

/**
 * Process node in table detection workflow to calculate the confidence of column headers
 */
public class HeaderConfidenceCalculationProcessNode extends AbstractProcessNode {

  public static final double NUMERIC_CELLS_THRESHOLD = 0.15;

  /**
   * @return the average confidence of column headers in the {@code table}
   */
  private static double computeHeaderConfidence(TabularElementGroup<Element> table) {
    double averageConfidence = 0;

    for (int i = 0; i < table.getColumnHeaderCount(); i++) {
      int numericCells = 0;
      int highlightedCells = 0;
      int coloredCells = 0;
      int nonNullCells = 0;
      // Determine numeric, highlighted, colored and non null cells in current row
      for (int c = 0; c < table.numberOfColumns(); c++) {
        MutableList<Element> cellElements = table.getMergedCell(i, c).getElements();
        String cellContent = cellElements.select(Objects::nonNull).collect(Element::getTextStr)
            .makeString(" ");

        if (!cellContent.isEmpty()) {
          nonNullCells++;
          SemanticsChecker.RegexType regexType = SemanticsChecker.RegexType.getFor(cellContent);
          if (regexType.getPriority() == SemanticsChecker.RegexType.NUMERIC.getPriority()) {
            numericCells++;
          }
          if (hasHighlightedElements(cellElements)) {
            highlightedCells++;
          }
          if (hasColoredElements(cellElements)) {
            coloredCells++;
          }
        }
      }
      double rowWiseConfidence;
      double numericCellsPercentage = (double) numericCells / nonNullCells;
      if (numericCellsPercentage > NUMERIC_CELLS_THRESHOLD) {
        rowWiseConfidence = 0;
      } else {
        double highlightedCellsPercentage = highlightedCells / (double) nonNullCells;
        double coloredCellsPercentage = coloredCells / (double) nonNullCells;
        rowWiseConfidence =
            4 * Math.abs(highlightedCellsPercentage - 0.5) * Math.abs(coloredCellsPercentage - 0.5)
                * (highlightedCellsPercentage + 0.1) * ((double) nonNullCells / table
                .numberOfColumns()) * (1 + Math.log(nonNullCells)) * (1.0 - numericCellsPercentage);
      }

      // If confidence of current row is too low for it to be considered a header, break out of the loop
      // and rows from 0 to the previous row (i-1) will be column headers
      if (rowWiseConfidence <= TableDetectionConfidenceFeatures.HEADER_CONFIDENCE
          .getThreshold(table)) {
        table.setColumnHeaderCount(i);
      } else {
        averageConfidence += rowWiseConfidence;
      }
    }
    return (table.getColumnHeaderCount() > 0) ? (averageConfidence / table.getColumnHeaderCount())
        : 0;
  }

  /**
   * @return True if any of the element in {@code cellElements} is bold or italic, else return False
   */
  public static boolean hasHighlightedElements(MutableList<Element> cellElements) {
    boolean highlightingSeen = false;
    for (Element element : cellElements) {
      highlightingSeen = highlightingSeen || (element.getAttribute(TextStyles.class) != null && (
          element.getAttribute(TextStyles.class).getValue().contains(TextStyles.BOLD) || element
              .getAttribute(TextStyles.class).getValue().contains(TextStyles.ITALIC)));
    }
    return highlightingSeen;
  }

  /**
   * @return True if any of the element in {@code cellElements} has color attribute
   */
  public static boolean hasColoredElements(MutableList<Element> cellElements) {
    boolean colorSeen = false;
    for (Element element : cellElements) {
      colorSeen = colorSeen || (element.getAttribute(Color.class) != null);
    }
    return colorSeen;
  }

  @Override
  public List getRequiredKeys() {
    return Lists.mutable.of(
        CustomScratchpadKeys.TABULAR_GROUP,
        CustomScratchpadKeys.PROCESSED_TABULAR_GROUP,
        CustomScratchpadKeys.DOCUMENT_SOURCE,
        CustomScratchpadKeys.PAGE_NUMBER,
        CustomScratchpadKeys.TABLE_INDEX,
        CustomScratchpadKeys.SPLIT_ROW_INDEX
    );
  }

  @Override
  public List getStoredKeys() {
    return Lists.mutable.of(
        CustomScratchpadKeys.TABULAR_GROUP,
        CustomScratchpadKeys.HEADER_CONFIDENCE
    );
  }

  @Override
  public void execute(Scratchpad scratchpad) {
    this.scratchpad = scratchpad;
    TabularElementGroup<Element> tabularGroup = CustomScratchpadKeys.TABULAR_GROUP
        .retrieveFrom(scratchpad);
    TabularElementGroup<Element> processedTabularGroup = CustomScratchpadKeys.PROCESSED_TABULAR_GROUP
        .retrieveFrom(scratchpad);
    double originalHeaderConfidence = computeHeaderConfidence(tabularGroup);
    double processedHeaderConfidence = computeHeaderConfidence(processedTabularGroup);
    TabularElementGroup<Element> finalTabularGroup = this
        .setFinalTable(tabularGroup, processedTabularGroup, originalHeaderConfidence,
            processedHeaderConfidence);
    finalTabularGroup.setBackReferences();
    scratchpad.store(CustomScratchpadKeys.TABULAR_GROUP, finalTabularGroup);
    scratchpad.store(CustomScratchpadKeys.HEADER_CONFIDENCE,
        finalTabularGroup.getConfidence(TableDetectionConfidenceFeatures.HEADER_CONFIDENCE));
  }

  /**
   * @return original table if header confidence of processed table is too low else, return
   * processed table
   */
  private TabularElementGroup<Element> setFinalTable(TabularElementGroup<Element> tabularGroup,
      TabularElementGroup<Element> processedTabularGroup, double originalHeaderConfidence,
      double processedHeaderConfidence) {
    if (processedHeaderConfidence <= TableDetectionConfidenceFeatures.HEADER_CONFIDENCE
        .getThreshold(processedTabularGroup)) {
      this.logEntry(
          "Processed table header confidence below threshold. Reverting and taking the original table instead.");
      tabularGroup.setConfidence(TableDetectionConfidenceFeatures.HEADER_CONFIDENCE,
          originalHeaderConfidence);
      // not unsetting the caption
      return tabularGroup;
    }
    this.logEntry(
        "Processed table header confidence above threshold. Continuing with the processed table itself.");
    processedTabularGroup.setConfidence(TableDetectionConfidenceFeatures.HEADER_CONFIDENCE,
        processedHeaderConfidence);
    return processedTabularGroup;
  }
}
