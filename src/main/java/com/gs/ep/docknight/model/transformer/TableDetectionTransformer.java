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

package com.gs.ep.docknight.model.transformer;

import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ModelCustomizationKey;
import com.gs.ep.docknight.model.ModelCustomizations;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup.GridType;
import com.gs.ep.docknight.model.Transformer;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.transformer.tabledetection.TableDetectionProcessModel;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import java.util.ListIterator;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.set.mutable.SetAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transformer to enhance tables found in document model {@see Document}
 */
public class TableDetectionTransformer implements Transformer<Document, Document> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TableDetectionTransformer.class);
  private final GridType gridType;

  public TableDetectionTransformer(ModelCustomizations modelCustomizations) {
    this.gridType = modelCustomizations
        .retrieveOrDefault(ModelCustomizationKey.ENABLE_GRID_BASED_TABLE_DETECTION, GridType.NONE);
  }

  public TableDetectionTransformer() {
    this(new ModelCustomizations());
  }

  /**
   * Remove entries from list iterator
   *
   * @param listIterator list iterator currently pointing at some index i
   * @param listOfEntriesToDelete indices to remove from the list iterator such that the index in
   * the list will be less than equals i
   */
  protected static <E> ListIterator<E> removeEntriesFromList(ListIterator<E> listIterator,
      MutableList<Integer> listOfEntriesToDelete) {
    int currNextIndex = listIterator.nextIndex();
    int deletionCount = 0;
    MutableList<Integer> descendingSortedListOfEntriesToDelete = listOfEntriesToDelete
        .sortThis((e1, e2) -> e1 < e2 ? 1 : -1);
    for (Integer deletionIndex : descendingSortedListOfEntriesToDelete) {
      while (listIterator.nextIndex() != deletionIndex && listIterator.hasPrevious()) {
        listIterator.previous();
      }
      if (listIterator.nextIndex() == deletionIndex) {
        deletionCount++;
        listIterator.remove();
        listIterator.next();
      } else {
        // Check TextElement back-referencing for this table.
        throw new RuntimeException(
            "Invalid table deletion index: '" + deletionIndex + "', when next table index is: '"
                + currNextIndex + "'.");
      }
    }
    while (listIterator.nextIndex() < currNextIndex - deletionCount) {
      listIterator.next();
    }
    return listIterator;
  }

  @Override
  public Document transform(Document document) {
    LOGGER.info("[{}][{}] Enriching tables in document.", document.getDocumentSource(),
        this.getClass().getSimpleName());
    long startTime = System.currentTimeMillis();

    TableDetectionProcessModel processModel = new TableDetectionProcessModel();
    String documentSource = document.getDocumentSource();
    int currPageNum = 0;
    for (Element docElement : document.getContent().getElementList().getElements()) {
      Page page = (Page) docElement;
      PositionalElementList<Element> positionalElementList = page.getPositionalContent().getValue();
      MutableList<TabularElementGroup<Element>> tabularGroupsInPage = positionalElementList
          .getTabularGroups();
      ListIterator<TabularElementGroup<Element>> tabularElementGroupListIterator = tabularGroupsInPage
          .listIterator();
      int currTableIndex = 1;
      while (tabularElementGroupListIterator.hasNext()) {
        // Process each tabular group separately
        LOGGER.debug(
            "[{}: Table No. {} on Page. {} of document {}] Initiating enriched table detection.",
            this.getClass().getSimpleName(), currTableIndex, currPageNum + 1, documentSource);
        TabularElementGroup<Element> tabularGroup = tabularElementGroupListIterator.next();
        tabularGroup.setColumnHeaderCount(AbstractProcessNode.DEFAULT_COLUMN_HEADER_COUNT);
        tabularGroup.setBackReferences();
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.store(CustomScratchpadKeys.TABULAR_GROUP, tabularGroup);
        scratchpad.store(CustomScratchpadKeys.DOCUMENT_SOURCE, documentSource);
        scratchpad.store(CustomScratchpadKeys.PAGE_NUMBER, currPageNum);
        scratchpad.store(CustomScratchpadKeys.TABLE_INDEX, currTableIndex);
        scratchpad.store(CustomScratchpadKeys.SPLIT_ROW_INDEX, 0);
        scratchpad.store(CustomScratchpadKeys.IS_PARENT_TABLE, true);
        scratchpad.store(CustomScratchpadKeys.PREV_TABLES_TO_DELETE, Sets.mutable.empty());
        scratchpad.store(CustomScratchpadKeys.IS_GRID_BASED_TABLE_DETECTION_ENABLED, this.gridType);
        MutableList<TabularElementGroup<Element>> processedTabularGroups = (MutableList<TabularElementGroup<Element>>) processModel
            .execute(scratchpad);
        MutableList<Integer> indicesOfTablesToDelete = SetAdapter
            .adapt(CustomScratchpadKeys.PREV_TABLES_TO_DELETE.retrieveFrom(scratchpad)).toList();
        tabularElementGroupListIterator = removeEntriesFromList(tabularElementGroupListIterator,
            indicesOfTablesToDelete);
        tabularElementGroupListIterator.remove();
        processedTabularGroups.each(tabularElementGroupListIterator::add);
        currTableIndex++;
      }
      currPageNum++;
    }

    float timeTaken = (System.currentTimeMillis() - startTime) / 1000.0f;
    LOGGER
        .info("[{}][{}][{}s] Returning enriched tables in document.", document.getDocumentSource(),
            this.getClass().getSimpleName(), timeTaken);

    return document;
  }
}
