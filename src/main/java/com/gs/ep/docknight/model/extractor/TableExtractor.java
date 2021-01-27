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

package com.gs.ep.docknight.model.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.multimap.MutableMultimap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementGroup;
import com.gs.ep.docknight.model.RectangleProperties;
import com.gs.ep.docknight.model.TabularCellElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup.VectorTag;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.context.PagePartitionType;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.extractor.tableextraction.TableExpander;
import com.gs.ep.docknight.model.extractor.tableextraction.TableExtractorUtils;
import java.util.List;

/**
 * Utility class to generate table map representation from {@see com.gs.ep.docknight.model.element.Document
 * document model}
 */
public final class TableExtractor {

  public static final String MAP_KEY_PARENT = "parent";
  public static final String MAP_KEY_CHILD = "child";
  private static final char PATH_DELIMITER = '/';

  private TableExtractor() {
  }

  /**
   * Generate table map representation for {@code document}
   *
   * @param document document model
   * @param documentName name of the document
   * @return table map representation
   */
  public static Object extract(Document document, String documentName)
      throws JsonProcessingException {
    return extract(document, documentName, true);
  }

  /**
   * Generate table map representation for {@code document}
   *
   * @param document document model
   * @param documentName name of the document
   * @param performEnrichment boolean to perform refinement on the tables within document
   * @return table map representation
   */
  public static Object extract(Document document, String documentName, boolean performEnrichment) {
    // Assumption : MultiPageToSinglePage Transformer
    MutableList<TabularElementGroup<Element>> tablesInDocument = ((Page) document.getContent()
        .getElementList().getElements().get(0)).getPositionalContent().getValue()
        .getTabularGroups();
    if (performEnrichment) {
      tablesInDocument = TableExtractorUtils.getEnrichedTables(tablesInDocument);
    }
    return extractAsMap(tablesInDocument, documentName);
  }

  /**
   * Generate table map representation for {@code tablesInDocument}
   *
   * @param tablesInDocument tables in document
   * @param documentName name of the document
   * @return table map representation
   */
  private static MutableMap<String, Object> extractAsMap(
      MutableList<TabularElementGroup<Element>> tablesInDocument, String documentName) {
    MutableList<MutableMap<String, Object>> tables = Lists.mutable.empty();
    MutableMap<String, Object> result = Maps.mutable.empty();
    result.put(TableAttribute.DOCUMENT.getKey(), documentName);
    result.put(TableAttribute.TABLES.getKey(), tables);

    for (int tableIndex = 0; tableIndex < tablesInDocument.size(); tableIndex++) {
      TabularElementGroup<Element> tabularGroup = tablesInDocument.get(tableIndex);

      MutableMap<String, Object> table = Maps.mutable.empty();
      tables.add(table);

      // Add metadata for table
      MutableMap<String, Object> metadata = Maps.mutable.empty();
      table.put(TableAttribute.ID.getKey(), tableIndex);
      table.put(TableAttribute.METADATA.getKey(), metadata);
      if (tabularGroup.getCaption() != null) {
        metadata.put(TableAttribute.CAPTION.getKey(), tabularGroup.getCaption().getTextStr());
      }
      metadata.put(TableAttribute.ROWS.getKey(), tabularGroup.numberOfRows());
      metadata.put(TableAttribute.COLUMNS.getKey(), tabularGroup.numberOfColumns());
      metadata
          .put(TableAttribute.COLUMN_HEADER_COUNT.getKey(), tabularGroup.getColumnHeaderCount());
      MutableList<Integer> spanningPages = tabularGroup.getElements()
          .collect(e -> e.getPositionalContext().getPageBreakNumber()).distinct();
      metadata.put(TableAttribute.SPANNING_PAGES.getKey(), spanningPages);

      Pair<String, String> currency = TableExpander.getAssociatedCurrency(tabularGroup);
      if (currency != null) {
        metadata.put(TableAttribute.CURRENCY_SYMBOL.getKey(), currency.getOne());
        metadata.put(TableAttribute.CURRENCY_NAME.getKey(), currency.getTwo());
      }
      Integer multiplicativeFactor = TableExpander.getMultiplicativeFactor(tabularGroup);
      if (multiplicativeFactor != 1) {
        metadata.put(TableAttribute.MULTIPLICATIVE_FACTOR.getKey(), multiplicativeFactor);
      }

      MutableList<MutableList<? extends ElementGroup<Element>>> tabularGroupRows = tabularGroup
          .getMergedRows();
      MutableSet<Integer> totalRows = tabularGroup.getVectorIndicesForTag(VectorTag.TOTAL_ROW);
      int headerRows = tabularGroup.getColumnHeaderCount();
      MutableList<Pair<Integer, MutableMultimap<String, Integer>>> rowHierarchy = TableExtractorUtils
          .getRowHierarchy(tabularGroup);

      // Add rows information for the table
      MutableList<MutableMap<String, Object>> rows = Lists.mutable.empty();
      table.put(TableAttribute.ROWS.getKey(), rows);
      for (int rowIndex = 0; rowIndex < tabularGroupRows.size(); rowIndex++) {
        MutableMap<String, Object> row = Maps.mutable.empty();
        rows.add(row);
        row.put(TableAttribute.INDEX.getKey(), rowIndex);
        row.put(TableAttribute.TITLE.getKey(),
            tabularGroup.getMergedCell(rowIndex, 0).getTextStr());
        row.put(TableAttribute.HIERARCHY_LEVEL.getKey(), rowHierarchy.get(rowIndex).getOne());
        if (rowHierarchy.get(rowIndex).getTwo().containsKey(MAP_KEY_PARENT)) {
          row.put(TableAttribute.IMMEDIATE_PARENT.getKey(),
              rowHierarchy.get(rowIndex).getTwo().get(MAP_KEY_PARENT).getFirst());
          row.put(TableAttribute.PARENTS.getKey(),
              rowHierarchy.get(rowIndex).getTwo().get(MAP_KEY_PARENT));
          StringBuilder pathBuilder = new StringBuilder();
          for (Integer parentRowIndex : rowHierarchy.get(rowIndex).getTwo().get(MAP_KEY_PARENT)) {
            pathBuilder.insert(0, PATH_DELIMITER)
                .insert(0, tabularGroup.getMergedCell(parentRowIndex, 0));
          }
          pathBuilder.append(tabularGroup.getMergedCell(rowIndex, 0).getTextStr());
          row.put(TableAttribute.PATH.getKey(), pathBuilder.toString());
        } else {
          row.put(TableAttribute.IMMEDIATE_PARENT.getKey(), null);
          row.put(TableAttribute.PARENTS.getKey(), Lists.mutable.empty());
        }
        row.put(TableAttribute.CHILDREN.getKey(),
            rowHierarchy.get(rowIndex).getTwo().containsKey(MAP_KEY_CHILD) ? rowHierarchy
                .get(rowIndex).getTwo().get(MAP_KEY_CHILD) : Lists.mutable.empty());
        row.put(TableAttribute.TOTAL_ROW.getKey(), totalRows.contains(rowIndex));
        row.put(TableAttribute.HEADER_ROW.getKey(), rowIndex < headerRows);
      }

      // Add column information for the table
      MutableList<MutableMap<String, Object>> columns = Lists.mutable.empty();
      table.put(TableAttribute.COLUMNS.getKey(), columns);
      for (int columnIndex = 0; columnIndex < tabularGroup.numberOfColumns(); columnIndex++) {
        MutableMap<String, Object> column = Maps.mutable.empty();
        columns.add(column);
        column.put(TableAttribute.INDEX.getKey(), columnIndex);
        if (headerRows > 0) {
          column.put(TableAttribute.TITLE.getKey(),
              tabularGroup.getMergedCell(headerRows - 1, columnIndex).getTextStr());
          StringBuilder pathBuilder = new StringBuilder();
          for (int rowIndex = 0; rowIndex < headerRows; rowIndex++) {
            pathBuilder.append(PATH_DELIMITER)
                .append(tabularGroup.getMergedCell(rowIndex, columnIndex).getTextStr());
          }
          column.put(TableAttribute.PATH.getKey(), pathBuilder.replace(0, 1, ""));
        }
      }

      // Add data in table
      MutableList<MutableMap<String, Object>> data = Lists.mutable.empty();
      table.put(TableAttribute.DATA.getKey(), data);
      for (int rowIndex = 0; rowIndex < tabularGroupRows.size(); rowIndex++) {
        MutableMap<String, Object> row = Maps.mutable.empty();
        data.add(row);
        MutableList<MutableMap<String, Object>> cells = Lists.mutable.empty();
        row.put(TableAttribute.CELLS.getKey(), cells);
        MutableList<? extends ElementGroup<Element>> columnsInRow = tabularGroupRows.get(rowIndex);
        for (int columnIndex = 0; columnIndex < columnsInRow.size(); columnIndex++) {
          MutableMap<String, Object> cell = Maps.mutable.empty();
          cells.add(cell);
          TabularCellElementGroup<Element> tabularCell = (TabularCellElementGroup<Element>) columnsInRow
              .get(columnIndex);
          cell.put(TableAttribute.SEGMENT_IDS.getKey(), tabularCell.getElements().select(
              element -> element instanceof TextElement
                  && element.getPositionalContext().getPagePartitionType()
                  == PagePartitionType.CONTENT).flatCollect(PhraseExtractor::segmentIds));
          cell.put(TableAttribute.TEXT.getKey(), tabularCell.getTextStr());
          cell.put(TableAttribute.POSITION.getKey(), Maps.mutable
              .of(TableAttribute.ROW.getKey(), rowIndex, TableAttribute.COLUMN.getKey(),
                  columnIndex));
          cell.put(TableAttribute.SPAN.getKey(), Maps.mutable.of(TableAttribute.ROWS.getKey(),
              TableExtractorUtils.getRowSpan(tabularGroup, rowIndex, columnIndex),
              TableAttribute.COLUMNS.getKey(),
              TableExtractorUtils.getColSpan(tabularGroup, rowIndex, columnIndex)));
          if (tabularCell.getElements() != null && tabularCell.getElements().notEmpty()) {
            RectangleProperties<Double> boundary = tabularCell.getTextBoundingBox();
            cell.put(TableAttribute.BOX.getKey(), Maps.mutable
                .of(TableAttribute.LEFT.getKey(), PhraseExtractor.format(boundary.getLeft()),
                    TableAttribute.TOP.getKey(), PhraseExtractor.format(boundary.getTop()),
                    TableAttribute.RIGHT.getKey(), PhraseExtractor.format(boundary.getRight()),
                    TableAttribute.BOTTOM.getKey(), PhraseExtractor.format(boundary.getBottom())));
          }
          RectangleProperties<Boolean> cellBorder = tabularCell.getBorderExistence();
          cell.put(TableAttribute.BORDER.getKey(), Maps.mutable
              .of(TableAttribute.LEFT.getKey(), cellBorder.getLeft(), TableAttribute.TOP.getKey(),
                  cellBorder.getTop(),
                  TableAttribute.RIGHT.getKey(), cellBorder.getRight(),
                  TableAttribute.BOTTOM.getKey(), cellBorder.getBottom()));
          MutableList<Object> textStyles = Lists.mutable.empty();
          tabularCell.getElements().each(e ->
          {
            if (e.getAttribute(TextStyles.class) != null) {
              textStyles.addAll(e.getAttribute(TextStyles.class).getValue());
            }
          });
          List<Object> distinctTextStyles = textStyles.distinct();
          cell.put(TableAttribute.TEXT_STYLES.getKey(), distinctTextStyles);
        }
      }
    }
    return result;
  }
}
