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

import static com.gs.ep.docknight.util.SemanticsChecker.RegexType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementGroup;
import com.gs.ep.docknight.model.ModelCustomizationKey;
import com.gs.ep.docknight.model.ModelCustomizations;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup.GridType;
import com.gs.ep.docknight.model.TabularElementGroup.VectorTag;
import com.gs.ep.docknight.model.attribute.PositionalContent;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.testutil.DocUtils;
import com.gs.ep.docknight.model.testutil.GroupedBoundingBox;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import com.gs.ep.docknight.model.transformer.tabledetection.ColumnHeaderExpansionProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.ColumnHeaderMergingProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.ColumnSplittingProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.HeaderConfidenceCalculationProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.InternalColumnMergingProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.InternalRowMergingProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.TableDetectionConfidenceFeatures;
import com.gs.ep.docknight.model.transformer.tabledetection.TableSplittingProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.TotalRowDetectionProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import java.io.File;
import java.nio.file.Files;
import java.util.ListIterator;
import java.util.regex.Pattern;
import mockit.Mock;
import mockit.MockUp;
import mockit.integration.junit4.JMockit;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.tuple.Tuples;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMockit.class)
public class TableDetectionTransformerTest extends AbstractTransformerTest {

  public static final Pattern TABLE_UNIQUE_CELL_PATTERN = Pattern.compile(":$|^\\(.{1,4}\\)$|^-$");
  private static final Pattern CR_PATTERN1 = Pattern.compile("\r");
  private static final Pattern CR_PATTERN2 = Pattern.compile("\\R(\\s|\\R)+");
  private static final Pattern CR_PATTERN3 = Pattern.compile("\\R+#\\R+");

  public static Document getDocumentWithTables(String documentName) {
    String filePath = Thread.currentThread().getContextClassLoader().getResource(documentName)
        .getPath();
    return DocUtils.parseAsDocument(new File(filePath), new PositionalTextGroupingTransformer(),
        new TableDetectionTransformer());
  }

  public static Document getDocumentWithoutEnrichedTables(String documentName) {
    String filePath = Thread.currentThread().getContextClassLoader().getResource(documentName)
        .getPath();
    return DocUtils.parseAsDocument(new File(filePath), new PositionalTextGroupingTransformer());
  }

  @Test
  public void testEnrichedTableDetection() throws Exception {
    TestObjects testObjects = this
        .fetchSplitTabularGroupsToTest("TableEnrichmentExpected.html", "TableEnrichmentTest.pdf",
            true);
    testObjects.expectedSplitTabularGroupsHtml.each(
        splitTabularGroups -> testObjects.expectedTabularGroupsHtml.addAll(splitTabularGroups));
    assertTabularGroups(testObjects.expectedTabularGroupsHtml, testObjects.actualTabularGroups);
  }

  @Test
  public void testColumnHeaderMerging() throws Exception {
    TestObjects testObjects = this.fetchTabularGroupsToTest("ColumnHeaderMergingExpected.html",
        "ColumnHeaderMergingTest.pdf");
    assertEquals(testObjects.expectedTabularGroupsHtml.size(),
        testObjects.actualTabularGroups.size());
    Scratchpad scratchpad = new Scratchpad();
    int currTableIndex = 0;
    for (Pair<String, TabularElementGroup<Element>> tabularGroupPair : testObjects.expectedTabularGroupsHtml
        .zip(testObjects.actualTabularGroups)) {
      tabularGroupPair.getTwo()
          .setColumnHeaderCount(AbstractProcessNode.DEFAULT_COLUMN_HEADER_COUNT);
      tabularGroupPair.getTwo().setBackReferences();
      setMandatoryScratchpadAttributes(scratchpad, currTableIndex, "ColumnHeaderMergingTest.pdf");
      scratchpad.store(CustomScratchpadKeys.TABULAR_GROUP, tabularGroupPair.getTwo());
      this.executeTestProcessModel(scratchpad, new ColumnHeaderMergingProcessNode(),
          new AssertionNode().assertProcessedTabularGroup(tabularGroupPair.getOne()));
      currTableIndex++;
    }
  }

  @Test
  public void testHeaderExpansion() throws Exception {
    TestObjects testObjects = this
        .fetchTabularGroupsToTest("HeaderExpandExpected.html", "HeaderExpandTest.pdf");
    assertEquals(testObjects.expectedTabularGroupsHtml.size(),
        testObjects.actualTabularGroups.size());
    Scratchpad scratchpad = new Scratchpad();
    int currTableIndex = 0;
    for (Pair<String, TabularElementGroup<Element>> tabularGroupPair : testObjects.expectedTabularGroupsHtml
        .zip(testObjects.actualTabularGroups)) {
      tabularGroupPair.getTwo()
          .setColumnHeaderCount(AbstractProcessNode.DEFAULT_COLUMN_HEADER_COUNT);
      tabularGroupPair.getTwo().setBackReferences();
      setMandatoryScratchpadAttributes(scratchpad, currTableIndex, "HeaderExpandTest.pdf");
      scratchpad.store(CustomScratchpadKeys.PROCESSED_TABULAR_GROUP, tabularGroupPair.getTwo());
      this.executeTestProcessModel(scratchpad, new ColumnHeaderExpansionProcessNode(),
          new AssertionNode().setTableCaptionNullBeforeAssert()
              .assertProcessedTabularGroup(tabularGroupPair.getOne()));
      currTableIndex++;
    }
  }

  @Test
  public void testHeaderExpansionDownwards() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(100, 100, 5, 5, 50, 25);
    tableBox.forEachCellBBox(0, 0, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(0, 0, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B"));
    tableBox.forEachCellBBox(0, 0, 2, 2, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(0, 0, 3, 3, (r, c, bbox) -> drawer.drawTextInside(bbox, "D"));
    tableBox.forEachCellBBox(0, 0, 4, 4, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(1, 1, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A"));
    tableBox.forEachCellBBox(1, 1, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(1, 1, 2, 2, (r, c, bbox) -> drawer.drawTextInside(bbox, "C"));
    tableBox.forEachCellBBox(1, 1, 3, 3, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(1, 1, 4, 4, (r, c, bbox) -> drawer.drawTextInside(bbox, "E"));
    tableBox.forEachCellBBox(2, 4, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A" + r));
    tableBox.forEachCellBBox(2, 4, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B" + r));
    tableBox.forEachCellBBox(2, 4, 2, 2, (r, c, bbox) -> drawer.drawTextInside(bbox, "C" + r));
    tableBox.forEachCellBBox(2, 4, 3, 3, (r, c, bbox) -> drawer.drawTextInside(bbox, "D" + r));
    tableBox.forEachCellBBox(2, 4, 4, 4, (r, c, bbox) -> drawer.drawTextInside(bbox, "E" + r));
    Document document = DocUtils
        .applyTransformersOnDocument(drawer.getDocument(), new PositionalTextGroupingTransformer(),
            new TableDetectionTransformer());
    String tableHtml = DocUtils.toHtmlString(
        document.getContent().getElements().get(0).getAttribute(PositionalContent.class)
            .getElementList().getTabularGroups().get(0));
    assertEquals("<table border='1px' style='margin-top:25px'>\n" +
        "<tr><th></th><th>B</th><th></th><th>D</th><th></th></tr>\n" +
        "<tr><th>A</th><th></th><th>C</th><th></th><th>E</th></tr>\n" +
        "<tr><td>A2</td><td>B2</td><td>C2</td><td>D2</td><td>E2</td></tr>\n" +
        "<tr><td>A3</td><td>B3</td><td>C3</td><td>D3</td><td>E3</td></tr>\n" +
        "<tr><td>A4</td><td>B4</td><td>C4</td><td>D4</td><td>E4</td></tr>\n" +
        "</table>", tableHtml);
  }

  @Test
  public void testHeaderRestrictionOnNamedEntity() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(100, 100, 5, 5, 100, 25);
    tableBox.forEachCellBBox(0, 0, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A"));
    tableBox.forEachCellBBox(0, 0, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(0, 0, 2, 2, (r, c, bbox) -> drawer.drawTextInside(bbox, "C"));
    tableBox.forEachCellBBox(0, 0, 3, 3, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(0, 0, 4, 4, (r, c, bbox) -> drawer.drawTextInside(bbox, "E"));
    tableBox.forEachCellBBox(1, 1, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A1"));
    tableBox.forEachCellBBox(1, 1, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B"));
    tableBox.forEachCellBBox(1, 1, 2, 2, (r, c, bbox) -> drawer.drawTextInside(bbox, "C1"));
    tableBox.forEachCellBBox(1, 1, 3, 3, (r, c, bbox) -> drawer.drawTextInside(bbox, "D"));
    tableBox.forEachCellBBox(1, 1, 4, 4, (r, c, bbox) -> drawer.drawTextInside(bbox, "E1"));
    tableBox.forEachCellBBox(2, 2, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(2, 2, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(2, 2, 2, 2, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(2, 2, 3, 3, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(2, 2, 4, 4, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(3, 4, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A" + r));
    tableBox.forEachCellBBox(3, 4, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B" + r));
    tableBox.forEachCellBBox(3, 4, 2, 2, (r, c, bbox) -> drawer.drawTextInside(bbox, "C" + r));
    tableBox.forEachCellBBox(3, 4, 3, 3, (r, c, bbox) -> drawer.drawTextInside(bbox, "D" + r));
    tableBox.forEachCellBBox(3, 4, 4, 4, (r, c, bbox) -> drawer.drawTextInside(bbox, "E" + r));
    Document document = DocUtils
        .applyTransformersOnDocument(drawer.getDocument(), new PositionalTextGroupingTransformer(),
            new TableDetectionTransformer());
    String tableHtml = DocUtils.toHtmlString(
        document.getContent().getElements().get(0).getAttribute(PositionalContent.class)
            .getElementList().getTabularGroups().get(0));
    assertEquals("<table border='1px' style='margin-top:25px'>\n" +
        "<tr><th><span>A</span> <span>A1</span></th><th>B</th><th><span>C</span> <span>C1</span></th><th>D</th><th><span>E</span> <span>E1</span></th></tr>\n"
        +
        "<tr><td>A3</td><td>B3</td><td>C3</td><td>D3</td><td>E3</td></tr>\n" +
        "<tr><td>A4</td><td>B4</td><td>C4</td><td>D4</td><td>E4</td></tr>\n" +
        "</table>", tableHtml);
    // TODO: Expectations is giving ExceptionInInitializer error on gitlab
//    new Expectations(SemanticsChecker.class) {{
//      isNamedEntity("D");
//      this.result = true;
//    }};
//    document = DocUtils
//        .applyTransformersOnDocument(drawer.getDocument(), new PositionalTextGroupingTransformer(),
//            new TableDetectionTransformer());
//    tableHtml = DocUtils.toHtmlString(
//        document.getContent().getElements().get(0).getAttribute(PositionalContent.class)
//            .getElementList().getTabularGroups().get(0));
//    assertEquals("<table border='1px' style='margin-top:25px'>\n" +
//        "<tr><th>A</th><th></th><th>C</th><th></th><th>E</th></tr>\n" +
//        "<tr><td>A1</td><td>B</td><td>C1</td><td>D</td><td>E1</td></tr>\n" +
//        "<tr><td>A3</td><td>B3</td><td>C3</td><td>D3</td><td>E3</td></tr>\n" +
//        "<tr><td>A4</td><td>B4</td><td>C4</td><td>D4</td><td>E4</td></tr>\n" +
//        "</table>", tableHtml);
  }

  @Test
  public void testInternalRowsMerging() throws Exception {
    TestObjects testObjects = this.fetchTabularGroupsToTest("InternalRowsMergingExpected.html",
        "InternalRowsMergingTest.pdf");
    assertEquals(testObjects.expectedTabularGroupsHtml.size(),
        testObjects.actualTabularGroups.size());
    Scratchpad scratchpad = new Scratchpad();
    int currTableIndex = 0;
    for (Pair<String, TabularElementGroup<Element>> tabularGroupPair : testObjects.expectedTabularGroupsHtml
        .zip(testObjects.actualTabularGroups)) {
      tabularGroupPair.getTwo()
          .setColumnHeaderCount(AbstractProcessNode.DEFAULT_COLUMN_HEADER_COUNT);
      tabularGroupPair.getTwo().setBackReferences();
      setMandatoryScratchpadAttributes(scratchpad, currTableIndex, "InternalRowsMergingTest.pdf");
      scratchpad.store(CustomScratchpadKeys.TABULAR_GROUP, tabularGroupPair.getTwo());
      scratchpad.store(CustomScratchpadKeys.IS_GRID_BASED_TABLE_DETECTION_ENABLED, GridType.NONE);
      this.executeTestProcessModel(scratchpad, new InternalRowMergingProcessNode(),
          new AssertionNode().assertTabularGroup(tabularGroupPair.getOne()));
      currTableIndex++;
    }
  }

  @Test
  public void testRowMergingForFullyPopulatedTable() throws Exception {
    int rowSize = 25;
    int colSize = 100;
    int numberOfColumns = 5;
    int numberOfRows = 5;
    Pair<Integer, Integer> topLeftCoordinates = Tuples.pair(100, 100);
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawRectangleAt(topLeftCoordinates.getOne(), topLeftCoordinates.getTwo(),
        numberOfColumns * colSize, numberOfRows * rowSize, RenderingMode.STROKE);
    for (int i = 1; i < numberOfRows - 1; ++i) {
      drawer.drawHorizontalLineAt(topLeftCoordinates.getOne(),
          topLeftCoordinates.getTwo() + i * rowSize, numberOfColumns * colSize);
    }
    for (int i = 1; i < numberOfColumns; ++i) {
      drawer.drawVerticalLineAt(topLeftCoordinates.getOne() + i * colSize,
          topLeftCoordinates.getTwo(), numberOfRows * rowSize);
    }

    GroupedBoundingBox tableBox = new GroupedBoundingBox(topLeftCoordinates.getOne(),
        topLeftCoordinates.getTwo(), numberOfColumns, numberOfRows, colSize, rowSize);
    tableBox.forEachCellBBox(0, 2, 0, 4, (r, c, bbox) -> drawer.drawTextInside(bbox, "A" + r + c));
    tableBox.forEachCellBBox(3, 3, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "B"));
    tableBox.forEachCellBBox(4, 4, 0, 4, (r, c, bbox) -> drawer.drawTextInside(bbox, "C" + r + c));
    Document document = DocUtils
        .applyTransformersOnDocument(drawer.getDocument(), new PositionalTextGroupingTransformer(),
            new TableDetectionTransformer());
    String tableHtml = DocUtils.toHtmlString(
        document.getContent().getElements().get(0).getAttribute(PositionalContent.class)
            .getElementList().getTabularGroups().get(0));
    assertEquals("<table border='1px' style='margin-top:25px'>\n" +
        "<tr><th>A00</th><th>A01</th><th>A02</th><th>A03</th><th>A04</th></tr>\n" +
        "<tr><td>A10</td><td>A11</td><td>A12</td><td>A13</td><td>A14</td></tr>\n" +
        "<tr><td>A20</td><td>A21</td><td>A22</td><td>A23</td><td>A24</td></tr>\n" +
        "<tr><td><span>B</span> <span>C40</span></td><td>C41</td><td>C42</td><td>C43</td><td>C44</td></tr>\n"
        +
        "</table>", tableHtml);
  }

  @Test
  public void testRowMergingForKeyValuePair() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawRectangleAt(100, 100, 200, 125, RenderingMode.STROKE);
    drawer.drawHorizontalLineAt(100, 150, 200);
    drawer.drawVerticalLineAt(200, 100, 125);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(100, 100, 2, 5, 100, 25);
    tableBox
        .forEachCellBBox(0, 0, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A" + r + ":"));
    tableBox.forEachCellBBox(0, 0, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B" + r));
    tableBox.forEachCellBBox(1, 1, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, ""));
    tableBox.forEachCellBBox(1, 1, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B" + r));
    tableBox
        .forEachCellBBox(2, 2, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "C" + r + ":"));
    tableBox.forEachCellBBox(2, 2, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "D" + r));
    Document document = DocUtils
        .applyTransformersOnDocument(drawer.getDocument(), new PositionalTextGroupingTransformer(),
            new TableDetectionTransformer());
    String tableHtml = DocUtils.toHtmlString(
        document.getContent().getElements().get(0).getAttribute(PositionalContent.class)
            .getElementList().getTabularGroups().get(0));
    assertEquals("<table border='1px' style='margin-top:25px'>\n" +
        "<tr><td>A0:</td><td><span>B0</span> <span>B1</span></td></tr>\n" +
        "<tr><td>C2:</td><td>D2</td></tr>\n" +
        "</table>", tableHtml);
  }

  @Test
  public void testGridBasedTableDetection() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawRectangleAt(100, 100, 200, 125, RenderingMode.STROKE);
    drawer.drawHorizontalLineAt(100, 125, 200);
    drawer.drawHorizontalLineAt(100, 150, 200);
    drawer.drawVerticalLineAt(200, 100, 125);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(100, 100, 2, 5, 100, 25);
    tableBox.forEachCellBBox(0, 0, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A" + r));
    tableBox.forEachCellBBox(0, 0, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B" + r));
    tableBox.forEachCellBBox(1, 1, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A" + r));
    tableBox.forEachCellBBox(1, 1, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B" + r));
    tableBox.forEachCellBBox(3, 3, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "C" + r));
    tableBox.forEachCellBBox(2, 2, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "D" + r));
    tableBox.forEachCellBBox(4, 4, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "E" + r));
    Document document = DocUtils
        .applyTransformersOnDocument(drawer.getDocument(), new PositionalTextGroupingTransformer(),
            new TableDetectionTransformer());
    String tableHtml = DocUtils.toHtmlString(
        document.getContent().getElements().get(0).getAttribute(PositionalContent.class)
            .getElementList().getTabularGroups().get(0));
    assertEquals("<table border='1px' style='margin-top:25px'>\n" +
        "<tr><td>A0</td><td>B0</td></tr>\n" +
        "<tr><td>A1</td><td>B1</td></tr>\n" +
        "<tr><td></td><td>D2</td></tr>\n" +
        "<tr><td>C3</td><td></td></tr>\n" +
        "<tr><td></td><td>E4</td></tr>\n" +
        "</table>", tableHtml);
    document = DocUtils
        .applyTransformersOnDocument(drawer.getDocument(), new PositionalTextGroupingTransformer(),
            new TableDetectionTransformer(new ModelCustomizations()
                .add(ModelCustomizationKey.ENABLE_GRID_BASED_TABLE_DETECTION,
                    GridType.ROW_AND_MAYBE_COL)));
    tableHtml = DocUtils.toHtmlString(
        document.getContent().getElements().get(0).getAttribute(PositionalContent.class)
            .getElementList().getTabularGroups().get(0));
    assertEquals("<table border='1px' style='margin-top:25px'>\n" +
        "<tr><td>A0</td><td>B0</td></tr>\n" +
        "<tr><td>A1</td><td>B1</td></tr>\n" +
        "<tr><td>C3</td><td><span>D2</span> <span>E4</span></td></tr>\n" +
        "</table>", tableHtml);
  }

  @Test
  public void testIsTableGridBased() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawRectangleAt(100, 100, 200, 125, RenderingMode.STROKE);
    drawer.drawHorizontalLineAt(100, 125, 200);
    drawer.drawHorizontalLineAt(100, 150, 200);
    drawer.drawVerticalLineAt(200, 100, 125);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(100, 100, 2, 5, 100, 25);
    tableBox.forEachCellBBox(0, 0, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A" + r));
    tableBox.forEachCellBBox(0, 0, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B" + r));
    tableBox.forEachCellBBox(1, 1, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A" + r));
    tableBox.forEachCellBBox(1, 1, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B" + r));
    tableBox.forEachCellBBox(3, 3, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "C" + r));
    tableBox.forEachCellBBox(2, 2, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "D" + r));
    tableBox.forEachCellBBox(4, 4, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "E" + r));

    GroupedBoundingBox tableBox2 = new GroupedBoundingBox(100, 300, 2, 5, 100, 25);
    tableBox2.forEachCellBBox(0, 0, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A" + r));
    tableBox2.forEachCellBBox(0, 0, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B" + r));
    tableBox2.forEachCellBBox(1, 1, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "A" + r));
    tableBox2.forEachCellBBox(1, 1, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "B" + r));
    tableBox2.forEachCellBBox(3, 3, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "C" + r));
    tableBox2.forEachCellBBox(2, 2, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "D" + r));
    tableBox2.forEachCellBBox(4, 4, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "E" + r));

    Document document = DocUtils
        .applyTransformersOnDocument(drawer.getDocument(), new PositionalTextGroupingTransformer(),
            new TableDetectionTransformer());
    TabularElementGroup<Element> tableWithBorders = document.getContent().getElements().get(0)
        .getAttribute(PositionalContent.class).getElementList().getTabularGroups().get(0);
    TabularElementGroup<Element> tableWithoutBorders = document.getContent().getElements().get(0)
        .getAttribute(PositionalContent.class).getElementList().getTabularGroups().get(1);
    assertTrue(tableWithBorders.isRowAndColumnGrid());
    assertFalse(tableWithoutBorders.isRowAndColumnGrid());
  }

  @Test
  public void testRowMergingInSpaceSeparatedRows() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(100, 100, 4, 3, 100, 100);
    tableBox.forEachCellBBox(0, 2, 0, 0, (r, c, bbox) -> drawer.drawTextInside(bbox, "\n\nA" + r));
    tableBox.forEachCellBBox(0, 2, 1, 1, (r, c, bbox) -> drawer.drawTextInside(bbox, "\n\nB" + r));
    tableBox.forEachCellBBox(0, 2, 2, 2, (r, c, bbox) -> drawer.drawTextInside(bbox, "\n\nC" + r));
    tableBox.forEachCellBBox(0, 2, 3, 3,
        (r, c, bbox) -> drawer.drawTextInside(bbox, "D\nD\nD\nD\nD" + r));
    Document document = DocUtils
        .applyTransformersOnDocument(drawer.getDocument(), new PositionalTextGroupingTransformer(),
            new TableDetectionTransformer());
    String tableHtml = DocUtils.toHtmlString(
        document.getContent().getElements().get(0).getAttribute(PositionalContent.class)
            .getElementList().getTabularGroups().get(0));
    assertEquals("<table border='1px' style='margin-top:25px'>\n" +
        "<tr><th>A0</th><th>B0</th><th>C0</th><th><span>D</span> <span>D</span> <span>D</span> <span>D</span> <span>D0</span></th></tr>\n"
        +
        "<tr><td>A1</td><td>B1</td><td>C1</td><td><span>D</span> <span>D</span> <span>D</span> <span>D</span> <span>D1</span></td></tr>\n"
        +
        "<tr><td>A2</td><td>B2</td><td>C2</td><td><span>D</span> <span>D</span> <span>D</span> <span>D</span> <span>D2</span></td></tr>\n"
        +
        "</table>", tableHtml);
  }

  @Test
  public void testHeaderConfidence() {
    Document document = getDocumentWithoutEnrichedTables("HeaderConfidenceCalcTest.pdf");
    MutableList<TabularElementGroup<Element>> actualTabularGroups = getTablesFromDocument(document);
    assertEquals(1, actualTabularGroups.size());
    TabularElementGroup<Element> goodHeaderTabularGroup = actualTabularGroups.get(0);
    goodHeaderTabularGroup.setBackReferences();
    goodHeaderTabularGroup.setColumnHeaderCount(AbstractProcessNode.DEFAULT_COLUMN_HEADER_COUNT);
    Scratchpad scratchpad = new Scratchpad();
    setMandatoryScratchpadAttributes(scratchpad, 0, "HeaderConfidenceCalcTest.pdf");
    scratchpad.store(CustomScratchpadKeys.TABULAR_GROUP, goodHeaderTabularGroup);
    scratchpad.store(CustomScratchpadKeys.PROCESSED_TABULAR_GROUP, goodHeaderTabularGroup);
    this.executeTestProcessModel(scratchpad, new HeaderConfidenceCalculationProcessNode(),
        new AssertionNode().setTableCaptionNullBeforeAssert().assertHeaderConfidenceAboveThreshold(
            TableDetectionConfidenceFeatures.HEADER_CONFIDENCE
                .getThreshold(goodHeaderTabularGroup)));
  }

  @Test
  public void testTableSplitting() throws Exception {
    TestObjects testObjects = this
        .fetchSplitTabularGroupsToTest("SplitTablesExpected.html", "SplitTablesTest.pdf", false);
    assertEquals(testObjects.expectedSplitTabularGroupsHtml.size(),
        testObjects.actualTabularGroups.size());
    Scratchpad scratchpad = new Scratchpad();
    int currTableIndex = 0;
    for (Pair<MutableList<String>, TabularElementGroup<Element>> tabularGroupPair : testObjects.expectedSplitTabularGroupsHtml
        .zip(testObjects.actualTabularGroups)) {
      tabularGroupPair.getTwo()
          .setColumnHeaderCount(AbstractProcessNode.DEFAULT_COLUMN_HEADER_COUNT);
      tabularGroupPair.getTwo().setBackReferences();
      setMandatoryScratchpadAttributes(scratchpad, currTableIndex, "SplitTablesTest.pdf");
      scratchpad.store(CustomScratchpadKeys.TABULAR_GROUP, tabularGroupPair.getTwo());
      scratchpad.store(CustomScratchpadKeys.HEADER_CONFIDENCE, 0.0);
      this.executeTestProcessModel(scratchpad, new TableSplittingProcessNode(),
          new AssertionNode().assertSplitTabularGroups(tabularGroupPair.getOne()));
      currTableIndex++;
    }
  }

  @Test
  public void testInternalColumnMerging() throws Exception {
    MutableList<MutableList<String>> inputTable = Lists.mutable.of(
        Lists.mutable.of("A", "B", "", "C", "", "D", ""),
        Lists.mutable.of("1", "-", "*", "5", "", "5", ""),
        Lists.mutable.of("2", "-", "*", "$", "5", "$", "5000"),
        Lists.mutable.of("3", "", "*", "$", "0", "$", "0"));
    TabularElementGroup<Element> expectedTable = makeTable(Lists.mutable.of(
        Lists.mutable.of("A", "B", "", "C", "D", ""),
        Lists.mutable.of("1", "-", "*", "5", "5", ""),
        Lists.mutable.of("2", "-", "*", Lists.mutable.of("$", "5"), "$", "5000"),
        Lists.mutable.of("3", "", "*", Lists.mutable.of("$", "0"), "$", "0")));

    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(50, 50, 7, 4, 40, 20);
    tableBox.forEachCellBBox(0, 3, 0, 6,
        (row, col, bbox) -> drawer.drawTextInside(bbox, inputTable.get(row).get(col)));
    this.testSplitTablesInProcessNode(drawer.getDocument(), new InternalColumnMergingProcessNode(),
        Lists.mutable.of(expectedTable));
  }

  @Test
  public void testColumnSplitting() throws Exception {
    MutableList<MutableList<String>> tableWithColumnHeadersToSplit = Lists.mutable.of(
        Lists.mutable.of("SNo.", "Price", "Amt.          Tick", "Order"),
        Lists.mutable.of("1.", "$1", "Not applicable", "3"),
        Lists.mutable.of("2.", "$2", "$ 1", "1"),
        Lists.mutable.of("3.", "$3", "$ 10", "2"));
    TabularElementGroup<Element> expectedTable = makeTable(Lists.mutable.of(
        Lists.mutable.of("SNo.", "Price", "Amt.", "Tick", "Order"),
        Lists.mutable.of("1.", "$1", "Not applicable", "", "3"),
        Lists.mutable.of("2.", "$2", "$ 1", "", "1"),
        Lists.mutable.of("3.", "$3", "$ 10", "", "2")));

    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(50, 50, 4, 4, 100, 20);
    tableBox.forEachCellBBox(0, 3, 0, 3, (row, col, bbox) -> drawer
        .drawTextInside(bbox, tableWithColumnHeadersToSplit.get(row).get(col)));
    this.testSplitTablesInProcessNode(drawer.getDocument(), new ColumnSplittingProcessNode(),
        Lists.mutable.of(expectedTable));
  }

  @Test
  public void testSecondPrevTableDeletion() throws Exception {
    new MockUp<ColumnHeaderExpansionProcessNode>() {
      @Mock
      void populateAboveLine(Element[] aboveLine, TabularElementGroup<Element> tabularGroup) {
        if (tabularGroup.getCells().get(0).collect(ElementGroup::getTextStr)
            .equals(Lists.mutable.of("#POD1\n100", "#POD2\n200", "#POD3\n300", "#POD4\n400"))) {
          TabularElementGroup<Element> prevTable = ((PositionalElementList<Element>) tabularGroup
              .getFirst().getElementListContext().getOne()).getTabularGroups()
              .select(table -> table.getCells().get(0).collect(ElementGroup::getTextStr).equals(
                  Lists.mutable.of("#Super Header 1\n#Parent Header 1",
                      "#Super Header 2\n#Parent Header 2"))).get(0);
          MutableList<Element> prevFirstCellElements = prevTable.getCell(0, 0).getElements();
          MutableList<Element> prevSecondCellElements = prevTable.getCell(0, 1).getElements();
          aboveLine[0] = prevFirstCellElements.get(0);
          aboveLine[1] = prevSecondCellElements.get(0);
          aboveLine[2] = prevFirstCellElements.get(1);
          aboveLine[3] = prevSecondCellElements.get(1);
        }
      }
    };
    MutableList<MutableList<String>> topLeftTable = Lists.mutable.of(
        Lists.mutable.of("#Super Header 1", "#Super Header 2"),
        Lists.mutable.of("#Parent Header 1", "#Parent Header 2")
    );
    MutableList<MutableList<String>> bottomLeftTable = Lists.mutable.of(
        Lists.mutable.of("#POD1", "#POD2", "#POD3", "#POD4"),
        Lists.mutable.of("100", "200", "300", "400")
    );
    MutableList<MutableList<String>> rightTable = Lists.mutable.of(
        Lists.mutable.of("Content 101", "Content 102", "Content 103"),
        Lists.mutable.of("Content 104", "Content 105", "Content 106"),
        Lists.mutable.of("Content 107", "Content 108", "Content 109")
    );
    TabularElementGroup<Element> expectedTable1 = makeTable(rightTable);
    TabularElementGroup<Element> expectedTable2 = makeTable(Lists.mutable.of(
        Lists.mutable.of(Lists.mutable.of("#Super Header 1", "#Parent Header 1"), "",
            Lists.mutable.of("#Super Header 2", "#Parent Header 2"), ""),
        Lists.mutable.of(Lists.mutable.of("#POD1", "100"), Lists.mutable.of("#POD2", "200"),
            Lists.mutable.of("#POD3", "300"), Lists.mutable.of("#POD4", "400"))
    ));
    expectedTable2.getCell(0, 1).setHorizontallyMerged(true);
    expectedTable2.getCell(0, 3).setHorizontallyMerged(true);
    MutableList<TabularElementGroup<Element>> expectedTabularGroups = Lists.mutable
        .of(expectedTable1, expectedTable2);
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    GroupedBoundingBox topLeftBox = new GroupedBoundingBox(25, 50, 2, 2, 120, 18);
    topLeftBox.forEachCellBBox(0, 1, 0, 1,
        (row, col, bbox) -> drawer.drawTextInside(bbox, topLeftTable.get(row).get(col)));
    GroupedBoundingBox bottomLeftBox = new GroupedBoundingBox(20, 90, 4, 2, 60, 18);
    bottomLeftBox.forEachCellBBox(0, 1, 0, 3,
        (row, col, bbox) -> drawer.drawTextInside(bbox, bottomLeftTable.get(row).get(col)));
    drawer.setFont(PDType1Font.TIMES_ROMAN, 10);
    GroupedBoundingBox rightBox = new GroupedBoundingBox(300, 60, 3, 3, 90, 30);
    rightBox.forEachCellBBox(0, 2, 0, 2,
        (row, col, bbox) -> drawer.drawTextWithBorderInside(bbox, rightTable.get(row).get(col)));
    TestObjects testObjects = formTestObjects(drawer.getDocument(), expectedTabularGroups,
        new PositionalTextGroupingTransformer(), new TableDetectionTransformer());
    assertTabularGroups(testObjects.expectedTabularGroupsHtml, testObjects.actualTabularGroups);
  }

  @Test
  public void testRegexType() {
    assertEquals(RegexType.ALPHANUMERIC, RegexType.getFor("06Sep2107 - "));
    assertEquals(RegexType.ALPHANUMERIC, RegexType.getFor("11 Oct 2017"));
    assertEquals(RegexType.ALPHANUMERIC, RegexType.getFor("On 10/7/17"));
    assertEquals(RegexType.ALPHANUMERIC, RegexType.getFor("USD 10.77"));
    assertEquals(RegexType.ALPHA, RegexType.getFor("This should be alpha "));
    assertEquals(RegexType.DATE, RegexType.getFor("10/11/2017"));
    assertEquals(RegexType.DATE, RegexType.getFor("10-11-2017"));
    assertEquals(RegexType.NUMERIC, RegexType.getFor("100"));
    assertEquals(RegexType.NUMERIC, RegexType.getFor("100.71"));
    assertEquals(RegexType.DATE, RegexType.getFor("2017"));
    assertEquals(RegexType.DATE, RegexType.getFor("10.11.2017"));
    assertEquals(RegexType.NUMERIC, RegexType.getFor("$129,323 - "));
    assertEquals(RegexType.NUMERIC, RegexType.getFor("129,323"));
    assertEquals(RegexType.NON_ALPHANUMERIC, RegexType.getFor("@&*&* "));
    assertTrue(TABLE_UNIQUE_CELL_PATTERN.matcher("Unique Cell:").find());
  }

  @Test
  public void testPreviousTableDeletion() {
    MutableList<TabularElementGroup<Element>> tables = Lists.mutable.of(
        new TabularElementGroup<>(1, 1),
        new TabularElementGroup<>(2, 2),
        new TabularElementGroup<>(3, 3),
        new TabularElementGroup<>(4, 4),
        new TabularElementGroup<>(5, 5),
        new TabularElementGroup<>(6, 6),
        new TabularElementGroup<>(7, 7)
    );
    MutableList<Integer> expectedTableSizes = Lists.mutable.of(2, 4, 6, 7);
    ListIterator<TabularElementGroup<Element>> tableIterator = tables.listIterator();
    for (int i = 0; i < tables.size() - 2; i++) {
      tableIterator.next();
    }
    TabularElementGroup<Element> currTable = tableIterator.next();
    assertEquals(6, currTable.numberOfRows());
    MutableList<Integer> indicesToDelete = Lists.mutable.of(2, 4, 0);
    TableDetectionTransformer.removeEntriesFromList(tableIterator, indicesToDelete);
    tableIterator.previous();
    TabularElementGroup<Element> newCurrTable = tableIterator.next();
    assertEquals(6, newCurrTable.numberOfRows());
    assertEquals(expectedTableSizes, tables.collect(TabularElementGroup::numberOfRows));
  }

  @Test
  public void testTableDetectionDisablement() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawTextAt(25, 25, "This is a document with no tables.");
    Document rawDocument = drawer.getDocument();
    Document documentWithoutTables = new TableDetectionTransformer()
        .transform(new PositionalTextGroupingTransformer().transform(rawDocument));
    assertTrue(AbstractTransformerTest.getTablesFromDocument(documentWithoutTables).isEmpty());
  }

  @Test
  public void testTotalRowDetection() throws Exception {
    MutableList<MutableList<String>> tableContent = Lists.mutable.of(
        Lists.mutable.of("Item", "Bought", "Sold"),
        Lists.mutable.of("Item1", "1", ""),
        Lists.mutable.of("Item2", "2", "1"),
        Lists.mutable.of("Item3", "3", "10"),
        Lists.mutable.of("", "$ 6", "$ 11"));
    TabularElementGroup<Element> expectedTable = makeTable(tableContent);
    expectedTable.addVectorTag(VectorTag.TOTAL_ROW, 4);
    PositionalDocDrawer drawerWithBorderedTotalRow = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(50, 10, 3, 5, 100, 20);
    tableBox.forEachCellBBox(0, 4, 0, 2, (row, col, bbox) -> drawerWithBorderedTotalRow
        .drawTextInside(bbox, tableContent.get(row).get(col)));
    drawerWithBorderedTotalRow.drawHorizontalLineAt(150, 90, 50);
    drawerWithBorderedTotalRow.drawHorizontalLineAt(250, 90, 50);
    TestObjects testObjects = formTestObjects(drawerWithBorderedTotalRow.getDocument(),
        Lists.mutable.of(expectedTable), new PositionalTextGroupingTransformer());
    Scratchpad scratchpad = new Scratchpad();
    setMandatoryScratchpadAttributes(scratchpad, 0, null);
    scratchpad.store(CustomScratchpadKeys.END_RESULT, testObjects.actualTabularGroups);
    this.executeTestProcessModel(scratchpad, new TotalRowDetectionProcessNode(),
        new AssertionNode().assertSplitTabularGroups(testObjects.expectedTabularGroupsHtml));

    tableContent.get(2).set(0, "");
    PositionalDocDrawer drawerWithBoldTotalRow = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox2 = new GroupedBoundingBox(50, 10, 3, 5, 100, 20);
    tableBox2.forEachCellBBox(0, 3, 0, 2, (row, col, bbox) -> drawerWithBoldTotalRow
        .drawTextInside(bbox, tableContent.get(row).get(col)));
    drawerWithBoldTotalRow.setFont(PDType1Font.TIMES_BOLD, 10);
    tableBox2.forEachCellBBox(4, 4, 0, 2, (row, col, bbox) -> drawerWithBoldTotalRow
        .drawTextInside(bbox, tableContent.get(row).get(col)));
    drawerWithBoldTotalRow.drawTextInside(tableBox2.getCellBBox(2, 0), "Item2");
    testObjects = formTestObjects(drawerWithBoldTotalRow.getDocument(),
        Lists.mutable.of(expectedTable), new PositionalTextGroupingTransformer());
    scratchpad.store(CustomScratchpadKeys.END_RESULT, testObjects.actualTabularGroups);
    this.executeTestProcessModel(scratchpad, new TotalRowDetectionProcessNode(),
        new AssertionNode().assertSplitTabularGroups(testObjects.expectedTabularGroupsHtml));
  }

  @Test
  public void testTotalRowDetectionWithGridBasedTable() throws Exception {
    MutableList<MutableList<String>> tableContent = Lists.mutable.of(
        Lists.mutable.of("Item", "Bought", "Sold"),
        Lists.mutable.of("Item1", "1", ""),
        Lists.mutable.of("Item2", "2", "1"),
        Lists.mutable.of("Item3", "3", "10"),
        Lists.mutable.of("", "$ 6", "$ 11"));
    TabularElementGroup<Element> expectedTable = makeTable(tableContent);
    PositionalDocDrawer drawerWithGridBasedTable = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(50, 10, 3, 5, 100, 20);
    tableBox.forEachCellBBox(0, 4, 0, 2, (row, col, bbox) -> drawerWithGridBasedTable
        .drawTextInside(bbox, tableContent.get(row).get(col)));
    for (int top = 10; top <= 90; top += 20) {
      drawerWithGridBasedTable.drawHorizontalLineAt(150, top, 50);
      drawerWithGridBasedTable.drawHorizontalLineAt(250, top, 50);
    }
    TestObjects testObjects = formTestObjects(drawerWithGridBasedTable.getDocument(),
        Lists.mutable.of(expectedTable), new PositionalTextGroupingTransformer());
    Scratchpad scratchpad = new Scratchpad();
    setMandatoryScratchpadAttributes(scratchpad, 0, null);
    scratchpad.store(CustomScratchpadKeys.END_RESULT, testObjects.actualTabularGroups);
    this.executeTestProcessModel(scratchpad, new TotalRowDetectionProcessNode(),
        new AssertionNode().assertSplitTabularGroups(testObjects.expectedTabularGroupsHtml));
  }

  @Test
  public void testRemoveLargeContinuousRowGroups() {
    assertEquals(Lists.mutable.of(1, 2, 5, 6, 7, 15), TotalRowDetectionProcessNode
        .removeLargeContinuousRowGroups(Lists.mutable.of(1, 2, 5, 6, 7, 9, 10, 11, 12, 15)));
  }

  private void testSplitTablesInProcessNode(Document document,
      AbstractProcessNode processNodeToTest,
      MutableList<TabularElementGroup<Element>> expectedSplitTabularGroups) throws Exception {
    TestObjects testObjects = formTestObjects(document, expectedSplitTabularGroups,
        new PositionalTextGroupingTransformer());
    Scratchpad scratchpad = new Scratchpad();
    setMandatoryScratchpadAttributes(scratchpad, 0, null);
    scratchpad.store(CustomScratchpadKeys.SPLIT_TABULAR_GROUPS, testObjects.actualTabularGroups);
    this.executeTestProcessModel(scratchpad, processNodeToTest,
        new AssertionNode().assertSplitTabularGroups(testObjects.expectedTabularGroupsHtml));
  }

  private TestObjects fetchTabularGroupsToTest(String expectedHtmlFilePath, String actualDocPath)
      throws Exception {
    Document document = getDocumentWithoutEnrichedTables(actualDocPath);
    TestObjects testObjects = new TestObjects();
    testObjects.actualTabularGroups = getTablesFromDocument(document);
    testObjects.actualTabularGroups.each(table -> table.setCaption(null));
    File input = new File(
        Thread.currentThread().getContextClassLoader().getResource(expectedHtmlFilePath).getPath());
    testObjects.expectedTabularGroupsHtml = Lists.mutable.of(CR_PATTERN2
        .split(CR_PATTERN1.matcher(new String(Files.readAllBytes(input.toPath()))).replaceAll("")));
    return testObjects;
  }

  private TestObjects fetchSplitTabularGroupsToTest(String expectedHtmlFilePath,
      String actualDocPath, boolean applyTableDetectionTransformer) throws Exception {
    Document document = applyTableDetectionTransformer ? getDocumentWithTables(actualDocPath)
        : getDocumentWithoutEnrichedTables(actualDocPath);
    TestObjects testObjects = new TestObjects();
    testObjects.actualTabularGroups = getTablesFromDocument(document);
    testObjects.actualTabularGroups.each(table -> table.setCaption(null));
    File input = new File(
        Thread.currentThread().getContextClassLoader().getResource(expectedHtmlFilePath).getPath());
    Lists.mutable.of(CR_PATTERN3
        .split(CR_PATTERN1.matcher(new String(Files.readAllBytes(input.toPath()))).replaceAll("")))
        .each(cumulativeTablesString -> testObjects.expectedSplitTabularGroupsHtml
            .add(Lists.mutable.of(CR_PATTERN2.split(cumulativeTablesString))));
    return testObjects;
  }
}
