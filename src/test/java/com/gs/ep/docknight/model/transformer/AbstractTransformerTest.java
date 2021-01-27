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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementGroup;
import com.gs.ep.docknight.model.TabularCellElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.Transformer;
import com.gs.ep.docknight.model.attribute.PositionalContent;
import com.gs.ep.docknight.model.attribute.Text;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.testutil.DocUtils;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.ProcessModel;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import com.gs.ep.docknight.model.transformer.tabledetection.process.ScratchpadKey;
import java.util.List;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.utility.internal.IterableIterate;

public class AbstractTransformerTest {

  protected static TestObjects formTestObjects(Document document,
      MutableList<TabularElementGroup<Element>> expectedSplitTabularGroups,
      Transformer<Document, Document>... transformers) throws Exception {
    MutableList<TabularElementGroup<Element>> actualSplitTables = getTablesFromDocument(
        DocUtils.applyTransformersOnDocument(document, transformers));
    return formTestObjects(expectedSplitTabularGroups, actualSplitTables);
  }

  protected static TestObjects formTestObjects(
      MutableList<TabularElementGroup<Element>> expectedSplitTabularGroups,
      MutableList<TabularElementGroup<Element>> actualSplitTables) {
    actualSplitTables.each(table ->
    {
      table.setBackReferences();
      table.setColumnHeaderCount(AbstractProcessNode.DEFAULT_COLUMN_HEADER_COUNT);
    });
    MutableList<String> expectedSplitTables = expectedSplitTabularGroups.collect(tabularGroup ->
    {
      tabularGroup.setColumnHeaderCount(AbstractProcessNode.DEFAULT_COLUMN_HEADER_COUNT);
      return DocUtils.toHtmlString(tabularGroup);
    });
    TestObjects testObjects = new TestObjects();
    testObjects.actualTabularGroups = actualSplitTables;
    testObjects.expectedTabularGroupsHtml = expectedSplitTables;
    return testObjects;
  }

  public static <K> TabularElementGroup<Element> makeTable(
      MutableList<MutableList<K>> tableContents) {
    int numRows = tableContents.size();
    int numCols = 0;
    if (tableContents.notEmpty()) {
      numCols = tableContents.get(0).size();
    }
    TabularElementGroup<Element> table = new TabularElementGroup<>(numRows, numCols);
    for (int rowIndex = 0; rowIndex < tableContents.size(); rowIndex++) {
      MutableList<K> currRowContent = tableContents.get(rowIndex);
      for (int colIndex = 0; colIndex < currRowContent.size(); colIndex++) {
        TabularCellElementGroup<Element> currCell = table.getCell(rowIndex, colIndex);
        Object currCellContent = currRowContent.get(colIndex);
        if (currCellContent instanceof List) {
          ((List<String>) currCellContent).forEach(elementContent -> currCell.getElements()
              .add(new TextElement().add(new Text(elementContent))));
        } else if (currCellContent instanceof String && !((String) currCellContent).isEmpty()) {
          currCell.add(new TextElement().add(new Text((String) currCellContent)));
        }
      }
    }
    return table;
  }

  public static void setMandatoryScratchpadAttributes(Scratchpad scratchpad, int currTableIndex,
      String documentSource) {
    scratchpad.store(CustomScratchpadKeys.DOCUMENT_SOURCE, documentSource);
    scratchpad.store(CustomScratchpadKeys.PAGE_NUMBER, 0);
    scratchpad.store(CustomScratchpadKeys.TABLE_INDEX, currTableIndex);
    scratchpad.store(CustomScratchpadKeys.SPLIT_ROW_INDEX, 0);
    scratchpad.store(CustomScratchpadKeys.IS_PARENT_TABLE, true);
    scratchpad.store(CustomScratchpadKeys.PREV_TABLES_TO_DELETE, Sets.mutable.empty());
  }

  public static MutableList<TabularElementGroup<Element>> getTablesFromDocument(Document document) {
    return IterableIterate.flatCollect(document.getContainingElements(Page.class),
        elem -> elem.getAttribute(PositionalContent.class).getValue().getTabularGroups(),
        Lists.mutable.empty());
  }

  public static void assertTabularGroups(MutableList<String> expectedTabularGroups,
      MutableList<TabularElementGroup<Element>> actualTabularGroups) {
    assertEquals(expectedTabularGroups.size(), actualTabularGroups.size());
    expectedTabularGroups.zip(actualTabularGroups).each(
        tabularGroupPair -> assertEquals(tabularGroupPair.getOne(),
            DocUtils.toHtmlString(tabularGroupPair.getTwo())));
  }

  protected void executeTestProcessModel(Scratchpad scratchpad, AbstractProcessNode node,
      AbstractProcessNode assertionNode) {
    TestProcessModel model = new TestProcessModel(Lists.mutable.of(
        node,
        assertionNode
    ));
    model.execute(scratchpad);
  }

  static class TestObjects {

    MutableList<String> expectedTabularGroupsHtml;
    MutableList<MutableList<String>> expectedSplitTabularGroupsHtml;
    MutableList<TabularElementGroup<Element>> actualTabularGroups;

    TestObjects() {
      this.expectedTabularGroupsHtml = Lists.mutable.empty();
      this.actualTabularGroups = Lists.mutable.empty();
      this.expectedSplitTabularGroupsHtml = Lists.mutable.empty();
    }
  }

  class AssertionNode extends AbstractProcessNode {

    private final MutableList<String> expectedSplitTabularGroups = Lists.mutable.empty();
    private boolean checkTabularGroup;
    private boolean checkProcessedTabularGroup;
    private boolean checkSplitTabularGroups;
    private boolean checkHeaderConfidenceAboveThreshold;
    private boolean checkOneProcessedTabularGroup;
    private boolean setTableCaptionNull;
    private String caption;
    private int rowCountOfPrevTable;
    private String expectedTabularGroupHtml = "";
    private String expectedProcessedTabularGroupHtml = "";
    private double lowerBoundThreshold;

    @Override
    public List getRequiredKeys() {
      return Lists.mutable.of(
          CustomScratchpadKeys.TABULAR_GROUP,
          CustomScratchpadKeys.PROCESSED_TABULAR_GROUP
      );
    }

    @Override
    public List getStoredKeys() {
      return Lists.mutable.of(
          CustomScratchpadKeys.TABULAR_GROUP,
          CustomScratchpadKeys.PROCESSED_TABULAR_GROUP
      );
    }

    public AssertionNode assertTabularGroup(String expectedHtml) {
      this.checkTabularGroup = true;
      this.expectedTabularGroupHtml = expectedHtml;
      return this;
    }

    public AssertionNode setTableCaptionNullBeforeAssert() {
      this.setTableCaptionNull = true;
      return this;
    }

    public AssertionNode assertProcessedTabularGroup(String expectedHtml) {
      this.checkProcessedTabularGroup = true;
      this.expectedProcessedTabularGroupHtml = expectedHtml;
      return this;
    }

    public AssertionNode assertOneProcessedTabularGroup(String caption) {
      this.checkOneProcessedTabularGroup = true;
      this.caption = caption;
      return this;
    }

    public AssertionNode assertOneProcessedTabularGroup(String caption, int rowCountOfPrevTable) {
      this.checkOneProcessedTabularGroup = true;
      this.caption = caption;
      this.rowCountOfPrevTable = rowCountOfPrevTable;
      return this;
    }

    public AssertionNode assertSplitTabularGroups(MutableList<String> expectedHtml) {
      this.checkSplitTabularGroups = true;
      this.expectedSplitTabularGroups.addAll(expectedHtml);
      return this;
    }

    public AssertionNode assertHeaderConfidenceAboveThreshold(double threshold) {
      this.checkHeaderConfidenceAboveThreshold = true;
      this.lowerBoundThreshold = threshold;
      return this;
    }

    @Override
    public void execute(Scratchpad scratchpad) {
      if (this.checkTabularGroup) {
        TabularElementGroup<Element> tabularGroup = scratchpad
            .retrieve(CustomScratchpadKeys.TABULAR_GROUP);
        this.updateTableCaption(tabularGroup);
        assertEquals(this.expectedTabularGroupHtml, DocUtils.toHtmlString(tabularGroup));
      }
      if (this.checkProcessedTabularGroup) {
        TabularElementGroup<Element> processedTabularGroup = scratchpad
            .retrieve(CustomScratchpadKeys.PROCESSED_TABULAR_GROUP);
        this.updateTableCaption(processedTabularGroup);
        assertEquals(this.expectedProcessedTabularGroupHtml,
            DocUtils.toHtmlString(processedTabularGroup));
      }
      if (this.checkOneProcessedTabularGroup) {
        TabularElementGroup<Element> processedTabularGroup = scratchpad
            .retrieve(CustomScratchpadKeys.PROCESSED_TABULAR_GROUP);
        this.updateTableCaption(processedTabularGroup);
        TabularElementGroup<Element> tabularGroup = scratchpad
            .retrieve(CustomScratchpadKeys.TABULAR_GROUP);
        this.updateTableCaption(tabularGroup);
        ElementGroup<Element> caption = processedTabularGroup.getCaption();
        if (this.caption == null) {
          assertNull(caption);
        } else {
          assertNotNull(caption);
          assertEquals(this.caption, caption.getTextStr());
        }
        if (tabularGroup != processedTabularGroup) {
          assertEquals(this.rowCountOfPrevTable, tabularGroup.numberOfRows());
          assertEquals(3, processedTabularGroup.numberOfRows());
        }
      }
      if (this.checkSplitTabularGroups) {
        ScratchpadKey scratchpadKey =
            scratchpad.getStoredKeys().contains(CustomScratchpadKeys.END_RESULT)
                ? CustomScratchpadKeys.END_RESULT : CustomScratchpadKeys.SPLIT_TABULAR_GROUPS;
        MutableList<TabularElementGroup<Element>> splitTabularGroups = (MutableList<TabularElementGroup<Element>>) scratchpad
            .retrieve(scratchpadKey);
        splitTabularGroups.each(this::updateTableCaption);
        AbstractTransformerTest
            .assertTabularGroups(this.expectedSplitTabularGroups, splitTabularGroups);
      }
      if (this.checkHeaderConfidenceAboveThreshold) {
        assertTrue(scratchpad.retrieveDouble(CustomScratchpadKeys.HEADER_CONFIDENCE)
            >= this.lowerBoundThreshold);
      }
    }

    private void updateTableCaption(TabularElementGroup<Element> tabularGroup) {
      if (this.setTableCaptionNull) {
        tabularGroup.setCaption(null);
      }
    }
  }

  class TestProcessModel extends ProcessModel {

    MutableList<AbstractProcessNode> nodes;

    TestProcessModel(MutableList<AbstractProcessNode> nodes) {
      this.nodes = nodes;
    }

    @Override
    public Object execute(Scratchpad scratchpad) {
      this.setProcessNodes(this.nodes);
      for (AbstractProcessNode processNode : this.getProcessNodes()) {
        processNode.execute(scratchpad);
      }
      return scratchpad.retrieve(CustomScratchpadKeys.END_RESULT);
    }
  }
}
