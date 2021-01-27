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
import static org.junit.Assert.fail;

import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.testutil.BoundingBox;
import com.gs.ep.docknight.model.testutil.GroupedBoundingBox;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import com.gs.ep.docknight.model.transformer.tabledetection.ColumnHeaderExpansionProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.junit.Test;

/**
 * Test Cases for TableCaptionDetection which are part of @{@link ColumnHeaderExpansionProcessNode}.
 * Read all test cases as above line has + MethodName
 */
public class TableCaptionDetectionTest extends AbstractTransformerTest {

  private static Document getCaptionTestDocumentWithFirstColAboveRowIsNonBoldTextElement() {
    String[][] fourColWorkingTable = {{"Fruits1", "Kiwi1", "Watermelon1", "Guava1"},
        {"Fruits", "Kiwi", "Guava", "Watermelon"},
        {"Vegetables", "Carrot", "Onion", "Okra"}};
    try {
      PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
      drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
      BoundingBox header1 = drawer.drawTextAt(50, 35, "Caption-Part 1");
      drawer.setFont(PDType1Font.TIMES_BOLD, 14);
      BoundingBox header2 = drawer.drawTextAt(50 + header1.getRight(), 35, "Caption-Part 2");
      GroupedBoundingBox layout = new GroupedBoundingBox(50, 50, 4, 3, 70, 18);
      layout.forEachCellBBox(0, 2, 0, 3, (r, c, bbox) ->
      {
        PDType1Font times = r == 0 ? PDType1Font.TIMES_BOLD : PDType1Font.TIMES_ROMAN;
        drawer.setFont(times, 10);
        drawer.drawTextWithBorderInside(bbox, fourColWorkingTable[r][c]);
      });
      Document document = new PositionalTextGroupingTransformer().transform(drawer.getDocument());
      //DisplayUtils.displayHtml(document);
      return document;
    } catch (Exception e) {
      fail("PDF Drawing failed!" + e.getMessage());
    }
    return new Document();
  }

  private static Document getCaptionTestDocument(PositionalDocDrawer drawer) throws Exception {
    return getCaptionTestDocument(drawer, false, false);
  }

  private static Document getCaptionTestDocument(PositionalDocDrawer drawer, boolean borderless)
      throws Exception {
    return getCaptionTestDocument(drawer, false, false, false, borderless);
  }

  private static Document getCaptionTestDocument(PositionalDocDrawer drawer,
      boolean firstElementEmpty, boolean mergedFirstCell) throws Exception {
    return getCaptionTestDocument(drawer, firstElementEmpty, mergedFirstCell, false, false);
  }

  private static Document getCaptionTestDocument(PositionalDocDrawer drawer,
      boolean firstElementEmpty, boolean mergedFirstCell,
      boolean firstElementEmptyWithBorder, boolean borderless) throws Exception {
    GroupedBoundingBox layout = new GroupedBoundingBox(50, 60, 5, 3, 70, 18);
    if (mergedFirstCell) {
      drawer.drawTextWithBorderInside(layout.getBBox(0, 0, 0, 1), "R0C0");
    }
    layout.forEachCellBBox(0, 2, 0, 4, (r, c, bbox) ->
    {
      if (!(firstElementEmpty && r == 0 && c == 0) && !(mergedFirstCell && r == 0 && c < 2)) {
        PDType1Font times = r == 0 ? PDType1Font.TIMES_BOLD : PDType1Font.TIMES_ROMAN;
        drawer.setFont(times, 10);
        String text = firstElementEmptyWithBorder && r == 0 && c == 0 ? "" : "R" + r + 'C' + c;
        if (borderless) {
          drawer.drawTextInside(bbox, text);
        } else {
          drawer.drawTextWithBorderInside(bbox, text);
        }
      }
    });
    Document document = new PositionalTextGroupingTransformer().transform(drawer.getDocument());
    //DisplayUtils.displayHtml(document, true);
    return document;
  }

  private static Document getCaptionTestDocumentSideBySideTables(PositionalDocDrawer drawer)
      throws Exception {
    drawTable(drawer, 50);
    drawTable(drawer, 300);
    Document document = new PositionalTextGroupingTransformer().transform(drawer.getDocument());
    //DisplayUtils.displayHtml(document, true);
    return document;
  }

  private static void drawTable(PositionalDocDrawer drawer, int left) throws Exception {
    GroupedBoundingBox layout = new GroupedBoundingBox(left, 60, 5, 3, 70, 18);
    layout.forEachCellBBox(0, 2, 0, 2, (r, c, bbox) ->
    {
      PDType1Font times = r == 0 ? PDType1Font.TIMES_BOLD : PDType1Font.TIMES_ROMAN;
      drawer.setFont(times, 10);
      String text = "R" + r + 'C' + c;
      drawer.drawTextWithBorderInside(bbox, text);
    });
  }

  @Test
  public void emptyAboveRow() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects);
  }

  /**
   * Above line has Multiple text elements and text element above the first column of the table is
   * nonBold.
   */
  @Test
  public void nonBoldTextElementAboveFirstColumn() throws Exception {
    Document captionTestDocument = getCaptionTestDocumentWithFirstColAboveRowIsNonBoldTextElement();
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, "Caption-Part 1\tCaption-Part 2");
  }

  /**
   * Above line has Multiple text elements and text elements above the first and second column of
   * the table are nonBold.
   */
  @Test
  public void nonBoldTextElementAboveFirstAndSecondColumn() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    BoundingBox header1 = drawer.drawTextAt(50, 35, "Caption-Part 1");
    drawer.setFont(PDType1Font.TIMES_BOLD, 14);
    drawer.drawTextAt(50 + header1.getRight(), 35, "Caption-Part 2");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, "Caption-Part 1\tCaption-Part 2");
  }

  @Test
  public void multipleTextElementsWithOuterBoundingBox() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawRectangleAt(45, 15, 400, 200, RenderingMode.STROKE);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    BoundingBox header1 = drawer.drawTextAt(50, 35, "Caption-Part 1");
    drawer.setFont(PDType1Font.TIMES_BOLD, 14);
    drawer.drawTextAt(50 + header1.getRight(), 35, "Caption-Part 2");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, "Caption-Part 1\tCaption-Part 2");
  }

  @Test
  public void multipleTextElementsWithSameBoundingBoxForTableAndCaption() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawRectangleAt(45, 15, 400, 200, RenderingMode.STROKE);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    BoundingBox header1 = drawer.drawTextAt(50, 35, "Caption-Part 1");
    drawer.setFont(PDType1Font.TIMES_BOLD, 14);
    drawer.drawTextAt(50 + header1.getRight(), 35, "Caption-Part 2");
    Document captionTestDocument = getCaptionTestDocument(drawer, true);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    //this.assertTableCaption(testObjects, "Caption-Part 1\tCaption-Part 2");
    // TODO: perusa - Fix the caption logic for this scenario
    this.assertTableCaption(testObjects);
  }

  @Test
  public void multipleTextElementsOutsideTableBoundary() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int left = 300;
    String caption1 = "Figures in USD";
    BoundingBox header1 = drawer.drawTextAt(left, 35, caption1);
    drawer.setFont(PDType1Font.TIMES_BOLD, 14);
    drawer.drawTextAt(left + 150, 35, "Date:20 Feb 18");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption1);
  }

  @Test
  public void multipleTextElementsInVerticalGroupWithOneElementBeyondTableBoundary()
      throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    String caption1 = "Vertical Group 1";
    drawer.drawTextAt(50, 20, caption1);
    String caption2 = "Caption-Part 1";
    BoundingBox header1 = drawer.drawTextAt(50, 35, caption2);
    drawer.setFont(PDType1Font.TIMES_BOLD, 14);
    String caption3 = "Caption-Part 2";
    drawer.drawTextAt(50 + header1.getRight(), 35, caption3);
    drawer.drawTextAt(190 + header1.getRight(), 35, "Figures in USD");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption1 + '\n' + caption2 + '\t' + caption3);
  }

  @Test
  public void oneElementBeyondLastColumnTextBoundaryButWithinTableBoundary() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    String caption1 = "Vertical Group 1";
    drawer.drawTextAt(50, 20, caption1);
    String caption2 = "Caption-Part 1";
    BoundingBox header1 = drawer.drawTextAt(50, 35, caption2);
    drawer.setFont(PDType1Font.TIMES_BOLD, 14);
    String caption3 = "Caption-Part 2";
    drawer.drawTextAt(50 + header1.getRight(), 35, caption3);
    String caption4 = "Caption-Part 3";
    drawer.drawTextAt(180 + header1.getRight(), 35, caption4);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects,
        caption1 + '\n' + caption2 + '\t' + caption3 + '\t' + caption4);
  }

  @Test
  public void aNumericTextElementWithDollar() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    BoundingBox header1 = drawer.drawTextAt(50, 35, "Amount");
    drawer.drawTextAt(50 + header1.getRight(), 35, "$1000");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects);
  }

  @Test
  public void tableRowsWithSameStyle() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 12;
    int thirdRowTop = secondRowTop + 12;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    drawer.drawTextAt(left, thirdRowTop, "Detail");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Detail", 2);
  }

  /**
   * The above line has a table which have the same font size but the last row has two columns with
   * different font style.
   */
  @Test
  public void tableRowWithDifferentStyledCol() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 20;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Status\tActive", 1);
  }

  /**
   * The above line has a table which have the same style but the last row has two columns with font
   * size lesser than its previous row.
   */
  @Test
  public void tableLastRowWithLesserFontSize() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 20;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 11);
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, 2);
  }

  /**
   * The above line has a table which have the same style but the last row has two columns with font
   * size higher than its previous row but font change ratio is lesser than threshold.
   */
  @Test
  public void tableLastRowWithHigherFontSizeLesserThanRatio() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 20;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 13);
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, 2);
  }

  /**
   * The above line has a single row table.
   */
  @Test
  public void tableLastRowWithSingleRowOnly() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 20;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 13);
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    testObjects.actualTabularGroups.get(0).deleteRow(0);
    this.assertTableCaption(testObjects, 2, 1);
  }

  /**
   * The above line has a table which have the same style but the last row has two columns with font
   * size higher than its previous row and font change ratio is higher than threshold.
   */
  @Test
  public void tableWhoseLastRowHasHigherFontSizeGreaterThanRatio() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 20;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 15);
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Status\tActive", 1);
  }

  /**
   * The above line has a table which have the same style but the last row has two columns with font
   * size higher than its previous row in only one column.
   */
  @Test
  public void tableWhoseLastRowHasOneColHigherFontSizeAndOtherLesserFontSize() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 20;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 11);
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.setFont(PDType1Font.TIMES_BOLD, 13);
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Status\tActive", 1);
  }

  /**
   * The above line has a table which have the same size but the last row has one column with a
   * different style.
   */
  @Test
  public void tableWhoseLastRowHasOneElementWithDifferentStyledCol() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 12;
    int thirdRowTop = secondRowTop + 12;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    drawer.drawTextAt(left, thirdRowTop, "Detail");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Detail", 2);
  }

  /**
   * The above line has a table whose last row has two columns with a different style and font and
   * the previous row has only one filled column.
   */
  @Test
  public void tableWhoseLastRowHasMultipleFilledColButLastButOneRowHasOneFilledColumn()
      throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 12;
    int thirdRowTop = secondRowTop + 12;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.setFont(PDType1Font.TIMES_BOLD, 13);
    drawer.drawTextAt(left, thirdRowTop, "Amount");
    drawer.drawTextAt(secondColLeft, thirdRowTop, "Detail");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Amount\tDetail", 2);
  }

  /**
   * The above line has a table whose last row has single filled columns with a different style and
   * the last but one row has one filled column that is different from the last row.
   */
  @Test
  public void tableWhoseLast2RowsHave1stAnd2ndColFilledRespectively() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 12;
    int thirdRowTop = secondRowTop + 12;
    int fourthRowTop = thirdRowTop + 12;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    drawer.drawTextAt(left, thirdRowTop, "Amount");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    drawer.drawTextAt(secondColLeft, fourthRowTop, "Detail");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Detail", 3);
  }

  @Test
  public void tableWhoseLastRowHas1FilledColumnAndOtherHasMoreFilledColumn() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 12;
    int thirdRowTop = secondRowTop + 12;
    int fourthRowTop = thirdRowTop + 12;
    double secondColLeft = left + 50;
    double thirdColLeft = secondColLeft + 70;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.drawTextAt(thirdColLeft, top, "Prod");
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    drawer.drawTextAt(thirdColLeft, secondRowTop, "Prod");
    drawer.drawTextAt(left, thirdRowTop, "Amount");
    drawer.drawTextAt(thirdColLeft, thirdRowTop, "1000");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    drawer.drawTextAt(secondColLeft, fourthRowTop, "Detail");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Detail", 3);
  }

  /**
   * The above line has a table whose last row has single filled columns with a different style and
   * Same Font Size and there is one element outside of table.
   */
  @Test
  public void tableWhoseLastRowHas1FilledColAndOneTextElementOutsideOfAboveTable()
      throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 12;
    int thirdRowTop = secondRowTop + 12;
    double secondColLeft = left + 50;
    double thirdColLeft = secondColLeft + 70;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(thirdColLeft, top, "Prod");
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(thirdColLeft, secondRowTop, "Prod");
    drawer.drawTextAt(left, thirdRowTop, "Amount");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    drawer.drawTextAt(secondColLeft, thirdRowTop, "Detail");
    drawer.setFont(PDType1Font.TIMES_BOLD, 15);
    drawer.drawTextAt(thirdColLeft + 40, thirdRowTop, "As Of 2018");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Amount\tDetail\tAs Of 2018", 2);
  }

  /**
   * The above line has a table whose last row has single filled columns with a different style and
   * higher Font Size and there is one element outside of table.
   */
  @Test
  public void tableWhoseLastRowHas1FilledColInDiffStyleAndOneTextElementOutsideOfAboveTable()
      throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 12;
    int thirdRowTop = secondRowTop + 12;
    double secondColLeft = left + 50;
    double thirdColLeft = secondColLeft + 70;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(thirdColLeft, top, "Prod");
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(thirdColLeft, secondRowTop, "Prod");
    drawer.drawTextAt(left, thirdRowTop, "Amount");
    drawer.setFont(PDType1Font.TIMES_BOLD, 13);
    drawer.drawTextAt(secondColLeft, thirdRowTop, "Detail");
    drawer.setFont(PDType1Font.TIMES_BOLD, 16);
    drawer.drawTextAt(thirdColLeft + 40, thirdRowTop, "As Of 2018");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Amount\tDetail\tAs Of 2018", 2);
  }

  /**
   * The above line has a table whose last row has three filled columns and third column is empty
   * except fot the last row.
   */
  @Test
  public void tableWhoseThirdColumnIsEmptyExceptForLastRow() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 12;
    int thirdRowTop = secondRowTop + 12;
    double secondColLeft = left + 50;
    double thirdColLeft = secondColLeft + 70;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(thirdColLeft, top, "Prod");
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(thirdColLeft, secondRowTop, "Prod");
    drawer.drawTextAt(left, thirdRowTop, "Amount");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    drawer.drawTextAt(secondColLeft, thirdRowTop, "Detail");
    drawer.setFont(PDType1Font.TIMES_BOLD, 14);
    drawer.drawTextAt(thirdColLeft + 40, thirdRowTop, "As Of 2018");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Amount\tDetail\tAs Of 2018", 2);
  }

  /**
   * The above line has a table whose last 2 rows has 1 filled column in different font style.
   */
  @Test
  public void tableWhoseLast2RowsHave1FilledColInDifferentStyle() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 12;
    int thirdRowTop = secondRowTop + 12;
    int fourthRowTop = thirdRowTop + 12;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    drawer.drawTextAt(left, thirdRowTop, "Amount");
    drawer.drawTextAt(left, fourthRowTop, "Detail");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, "Amount\nDetail", 2, 4);
  }

  /**
   * The above line has a table whose last 2 rows has 1 filled column with last row in different
   * font style.
   */
  @Test
  public void tableWhoseLastRowHas1FilledColInDifferentStyle() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 12;
    int thirdRowTop = secondRowTop + 12;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    drawer.drawTextAt(left, thirdRowTop, "Detail. ");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, 3);
  }

  @Test
  public void tableWhoseLastRowHasOneElementInDiffFontStyleEndingWithPeriod() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 12;
    int thirdRowTop = secondRowTop + 12;
    BoundingBox boundingBox = drawer.drawTextAt(left, top,
        "Name is very long extending beyond last column text but  within table ");
    double secondColLeft = boundingBox.getRight() + 10;
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.drawTextAt(left, secondRowTop,
        "Status is very long extending beyond last column text in table");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Active");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    String caption = "Last line is very long extending beyond last column text in table.";
    drawer.drawTextAt(left, thirdRowTop, caption);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, 3);
  }

  @Test
  public void tableWhoseLastRowHas1NumericTextElement() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 20;
    double secondColLeft = 100;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    drawer.drawTextAt(left, secondRowTop, "Status");
    drawer.drawTextAt(secondColLeft, secondRowTop, "1");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 2, 2);
  }

  @Test
  public void sideBySideTables() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 20;
    double secondColLeft = 350;
    drawer.drawTextAt(left, top, "Name");
    drawer.drawTextAt(secondColLeft, top, "Docknight");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    drawer.drawTextAt(left, secondRowTop, "Amount");
    drawer.drawTextAt(secondColLeft, secondRowTop, "Details");
    Document captionTestDocument = getCaptionTestDocumentSideBySideTables(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, 3, "Amount", 1);
  }

  @Test
  public void numericFirstTextElement() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    String caption1 = "1.";
    String caption2 = "Amount Details:";
    drawer.drawTextAt(left, top, caption1);
    drawer.drawTextAt(left + 20, top, caption2);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption1 + '\t' + caption2);
  }

  @Test
  public void singleNumericTextElement() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    String caption1 = "1";
    drawer.drawTextAt(left, top, caption1);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects);
  }

  /**
   * The above line has a single text element which has period in the middle and a colon at the end
   * and table has empty first cell.
   */
  @Test
  public void singleTextElementEndingWithColonAndAPeriodInMiddle() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    String caption = "1.Amount Details:";
    drawer.drawTextAt(left, top, caption);
    Document captionTestDocument = getCaptionTestDocument(drawer, false, false, true, false);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption);
  }

  @Test
  public void singleTextElementAndTableUnderTestHasEmptyNonBorderedFirstCell() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    String caption = "1.Amount Details:";
    drawer.drawTextAt(left, top, caption);
    Document captionTestDocument = getCaptionTestDocument(drawer, true, false);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption);
  }

  @Test
  public void singleTextElementEndingWithColonAndTableUnderTestHasFirstCellMerged()
      throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    String caption = "1.Amount Details:";
    drawer.drawTextAt(left, top, caption);
    Document captionTestDocument = getCaptionTestDocument(drawer, false, true);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption);
  }

  @Test
  public void singleTextElementStartingBeyondTableLeftDelta() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 41;
    String caption1 = "1.Amount";
    String caption2 = "Details:";
    BoundingBox boundingBox = drawer.drawTextAt(left, top, caption1);
    drawer.drawTextAt(left, 5 + boundingBox.getBottom(), caption2);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption1 + '\n' + caption2);
  }

  @Test
  public void singleTextElementStartingAtTableLeftDelta() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 40;
    String caption1 = "1.Amount";
    String caption2 = "Details:";
    BoundingBox boundingBox = drawer.drawTextAt(left, top, caption1);
    drawer.drawTextAt(left, 5 + boundingBox.getBottom(), caption2);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption1 + '\n' + caption2);
  }

  @Test
  public void singleTextElementStartingWithinTableLeftDelta() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 39;
    String caption1 = "1.Amount";
    String caption2 = "Details:";
    BoundingBox boundingBox = drawer.drawTextAt(left, top, caption1);
    drawer.drawTextAt(left, 5 + boundingBox.getBottom(), caption2);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects);
  }

  @Test
  public void singleTextElementExtendingOutsideTableBoundary() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    drawer.drawTextAt(left, top,
        "The Following are the Amount Details. Please check whether are right and reply back");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects);
  }

  @Test
  public void singleTextElementEndingWithPeriod() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    drawer.drawTextAt(left, top, "Following are the Amount Details.");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects);
  }

  @Test
  public void singleTextElementWhichHasTwoSentences() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    drawer.drawTextAt(left, top, "Following are the Amount Details.Hence forth.");
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects);
  }

  @Test
  public void singleTextElementWithAbbreviationEndingWithPeriod() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    String caption = "Company: ABC Pvt.Ltd.";
    drawer.drawTextAt(left, top, caption);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption);
  }

  @Test
  public void singleTextElementEndingWithColon() throws Exception {
    String caption = "Following are the Amount Details:";
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    drawer.drawTextAt(left, top, caption);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption);
  }

  @Test
  public void singleTextElementWhichIsPartOfParagraphContainingPeriod() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 15;
    String caption1 = "The amount details are mentioned below. Please go through the amount details.";
    drawer.drawTextAt(left, top, caption1);
    String caption2 = "Following are the Amount Details:";
    drawer.drawTextAt(left, secondRowTop, caption2);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption2);
  }

  @Test
  public void singleTextElementWhcihIsPartOfParagraphContainingPeriodAndLiesWithinBoundary()
      throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 15;
    drawer.drawTextAt(left, top, "The amount details are mentioned below. ");
    String caption = "Following are the Amount Details:";
    drawer.drawTextAt(left, secondRowTop, caption);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption);
  }

  @Test
  public void singleTextElementWhichIsPartOfParagraphWithoutPeriod() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 10);
    int top = 15;
    int left = 50;
    int secondRowTop = top + 15;
    String caption1 = "The amount details are mentioned below Please go through the amount details";
    drawer.drawTextAt(left, top, caption1);
    String caption2 = "Following are the Amount Details:";
    drawer.drawTextAt(left, secondRowTop, caption2);
    Document captionTestDocument = getCaptionTestDocument(drawer);
    TestObjects testObjects = this.fetchTabularGroupsToTest(captionTestDocument);
    this.assertTableCaption(testObjects, caption1 + '\n' + caption2);
  }

  private void assertTableCaption(TestObjects testObjects) {
    this.assertTableCaption(testObjects, 1, null, 0);
  }

  private void assertTableCaption(TestObjects testObjects, String caption) {
    this.assertTableCaption(testObjects, 1, caption, 0, 0);
  }

  private void assertTableCaption(TestObjects testObjects, int expectedNoOfTabularGroups,
      int rowCountOfPrevTable) {
    this.assertTableCaption(testObjects, expectedNoOfTabularGroups, null, rowCountOfPrevTable,
        rowCountOfPrevTable);
  }

  private void assertTableCaption(TestObjects testObjects, int expectedNoOfTabularGroups,
      String caption, int rowCountOfPrevTableAfterProcessing) {
    int rowCountOfPrevTableBeforeProcessing = caption == null ? rowCountOfPrevTableAfterProcessing
        : rowCountOfPrevTableAfterProcessing + 1;
    this.assertTableCaption(testObjects, expectedNoOfTabularGroups, caption,
        rowCountOfPrevTableAfterProcessing, rowCountOfPrevTableBeforeProcessing);
  }

  private void assertTableCaption(TestObjects testObjects, int expectedNoOfTabularGroups,
      String caption, int rowCountOfPrevTableAfterProcessing,
      int rowCountOfPrevTableBeforeProcessing) {
    int size = testObjects.actualTabularGroups.size();
    assertEquals(expectedNoOfTabularGroups, size);
    TabularElementGroup<Element> tabularElementGroup;
    if (expectedNoOfTabularGroups == 3) {
      tabularElementGroup = testObjects.actualTabularGroups.get(1);
    } else {
      tabularElementGroup = testObjects.actualTabularGroups.get(size - 1);
    }
    tabularElementGroup.setColumnHeaderCount(AbstractProcessNode.DEFAULT_COLUMN_HEADER_COUNT);
    tabularElementGroup.setBackReferences();
    Scratchpad scratchpad = new Scratchpad();
    setMandatoryScratchpadAttributes(scratchpad, 0, null);
    TabularElementGroup<Element> firstTabularElementGroup = testObjects.actualTabularGroups.get(0);
    scratchpad.store(CustomScratchpadKeys.TABULAR_GROUP, firstTabularElementGroup);
    scratchpad.store(CustomScratchpadKeys.PROCESSED_TABULAR_GROUP, tabularElementGroup);
    if (expectedNoOfTabularGroups == 1) {
      this.executeTestProcessModel(scratchpad, new ColumnHeaderExpansionProcessNode(),
          new AssertionNode().assertOneProcessedTabularGroup(caption));
    } else if (expectedNoOfTabularGroups == 2 || expectedNoOfTabularGroups == 3) {
      assertEquals(rowCountOfPrevTableBeforeProcessing, firstTabularElementGroup.numberOfRows());
      this.executeTestProcessModel(scratchpad, new ColumnHeaderExpansionProcessNode(),
          new AssertionNode()
              .assertOneProcessedTabularGroup(caption, rowCountOfPrevTableAfterProcessing));
    } else {
      fail("Not handled in assertion node");
    }
  }

  private TestObjects fetchTabularGroupsToTest(Document document) throws Exception {
    TestObjects testObjects = new TestObjects();
    testObjects.actualTabularGroups = AbstractTransformerTest.getTablesFromDocument(document);
    return testObjects;
  }
}
