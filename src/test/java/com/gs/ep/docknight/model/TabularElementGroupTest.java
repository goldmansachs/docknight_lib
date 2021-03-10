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

package com.gs.ep.docknight.model;

import static org.junit.Assert.assertEquals;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.test.Verify;
import org.eclipse.collections.impl.tuple.Tuples;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Text;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.testutil.GroupedBoundingBox;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import com.gs.ep.docknight.model.transformer.AbstractTransformerTest;
import com.gs.ep.docknight.model.transformer.PositionalTextGroupingTransformer;
import com.gs.ep.docknight.model.transformer.TableDetectionTransformer;
import com.gs.ep.docknight.model.transformer.TableDetectionTransformerTest;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TabularElementGroupTest {

  private MutableList<TabularElementGroup<Element>> testTables;

  private static void assertMergedRowContent(MutableList<MutableList<String>> expectedContent,
      TabularElementGroup<Element> actualMergedTable, int rowIndex) {
    MutableList<MutableList<String>> actualContent = actualMergedTable.getCells().get(rowIndex)
        .collect(cell -> cell.getElements().collect(Element::getTextStr));
    assertEquals(expectedContent, actualContent);
  }

  private static void assertTableDimensions(int numRows, int numColumns,
      TabularElementGroup<Element> actualTable) {
    assertEquals(numRows, actualTable.numberOfRows());
    assertEquals(numColumns, actualTable.numberOfColumns());
  }

  private static TabularCellElementGroup<Element> makeTableCell(MutableList<String> elementList) {
    TabularCellElementGroup<Element> cell = new TabularCellElementGroup<>();
    for (String entry : elementList) {
      cell = cell.add(new TextElement().add(new Text(entry)));
    }
    return cell;
  }

  @Before
  public void setUp() throws Exception {
    this.testTables = AbstractTransformerTest.getTablesFromDocument(TableDetectionTransformerTest
        .getDocumentWithoutEnrichedTables("TabularElementGroupTest.pdf"));
    this.testTables.get(0).setColumnHeaderCount(3);
  }

  @Test
  public void testDeleteRow() throws Exception {
    TabularElementGroup<Element> table = this.testTables.get(0).clone();
    assertTableDimensions(7, 3, table);
    MutableList<Pair<Integer, Element>> elementsToDelete = table.getCells().get(4)
        .flatCollect(ElementGroup::getElements).collect(e -> Tuples.pair(4, e))
        .withAll(table.getCells().get(1).flatCollect(ElementGroup::getElements)
            .collect(e -> Tuples.pair(1, e)));
    assertEquals(6, elementsToDelete.size());
    elementsToDelete.each(entry ->
    {
      PositionalContext<Element> elementListContext = entry.getTwo().getPositionalContext();
      assertEquals(table, elementListContext.getTabularGroup());
      assertEquals(entry.getOne(), elementListContext.getTabularRow());
      Assert.assertNotNull(elementListContext.getTabularColumn());
    });

    table.deleteRow(4);
    assertTableDimensions(6, 3, table);
    assertEquals(3, table.getColumnHeaderCount());

    table.deleteRow(1);
    assertTableDimensions(5, 3, table);
    assertEquals(2, table.getColumnHeaderCount());

    elementsToDelete.each(entry ->
    {
      PositionalContext<Element> elementListContext = entry.getTwo().getPositionalContext();
      Assert.assertNull(elementListContext.getTabularGroup());
      Assert.assertNull(elementListContext.getTabularRow());
      Assert.assertNull(elementListContext.getTabularColumn());
    });
  }

  @Test
  public void testSetBackReferences() {
    assertTableDimensions(7, 3, this.testTables.get(0));
    TabularElementGroup<Element> newTable = new TabularElementGroup<>(1, 1, 1);
    this.testTables.get(0).getCell(4, 2).getElements()
        .each(element -> newTable.addElement(0, 0, element.clone()));

    MutableList<PositionalContext<Element>> elementContexts = newTable.getCell(0, 0).getElements()
        .collect(element -> element.getPositionalContext());
    elementContexts.each(elementContext ->
    {
      Assert.assertNotEquals(newTable, elementContext.getTabularGroup());
      Assert.assertNotEquals(0, elementContext.getTabularColumn().intValue());
      Assert.assertNotEquals(0, elementContext.getTabularColumn().intValue());
    });
    newTable.setBackReferences();
    elementContexts.each(elementContext ->
    {
      assertEquals(newTable, elementContext.getTabularGroup());
      assertEquals(0, elementContext.getTabularColumn().intValue());
      assertEquals(0, elementContext.getTabularColumn().intValue());
    });
  }

  @Test
  public void testGetColumnBoundaries() {
    TabularElementGroup<Element> testTable = this.testTables.get(0);
    assertTableDimensions(7, 3, testTable);
    Pair<double[], double[]> columnBoundaries = testTable.getColumnBoundaries();

    // For column with no header
    Element colWithoutHeaderTestCell = testTable.getCell(5, 0).getElements().get(0);
    assertEquals(columnBoundaries.getOne()[0],
        colWithoutHeaderTestCell.getAttribute(Left.class).getValue().getMagnitude(), 0.01);
    assertEquals(columnBoundaries.getTwo()[0],
        colWithoutHeaderTestCell.getAttribute(Left.class).getValue().getMagnitude()
            + colWithoutHeaderTestCell.getAttribute(Width.class).getValue().getMagnitude(), 0.01);

    // For column with header
    MutableList<Element> colWithHeaderTestCell = testTable.getCell(4, 1).getElements();
    Assert.assertTrue(
        columnBoundaries.getOne()[1] > colWithHeaderTestCell.get(0).getAttribute(Left.class)
            .getValue().getMagnitude());
    Assert.assertTrue(columnBoundaries.getTwo()[1]
        < colWithHeaderTestCell.get(1).getAttribute(Left.class).getValue().getMagnitude()
        + colWithHeaderTestCell.get(1).getAttribute(Width.class).getValue().getMagnitude());
  }

  @Test
  public void testIsRowBasedTable() {
    TabularElementGroup<Element> nonRowBasedTable = this.testTables.get(0);
    assertTableDimensions(7, 3, nonRowBasedTable);
    Assert.assertFalse(nonRowBasedTable.isRowBasedTable());

    TabularElementGroup<Element> nonNullRowBasedTable = this.testTables.get(1);
    assertTableDimensions(2, 2, nonNullRowBasedTable);
    Assert.assertTrue(nonNullRowBasedTable.isRowBasedTable());

    TabularElementGroup<Element> nullColumnRowBasedTable = new TabularElementGroup<>(1, 3);
    nullColumnRowBasedTable.addElement(0, 0, new TextElement().add(new Text("Foo")));
    assertTableDimensions(1, 3, nullColumnRowBasedTable);
    Assert.assertTrue(nullColumnRowBasedTable.isRowBasedTable());
  }

  @Test
  public void testRowMerging() {
    TabularElementGroup<Element> testTable = this.testTables.get(0);
    assertTableDimensions(7, 3, testTable);

    // assert that for invalid input params, should not create new copy of table
    assertEquals(testTable, testTable.getNewRowMergedTable(1, 0));
    assertEquals(testTable, testTable.getNewRowMergedTable(1, 100));
    assertEquals(testTable, testTable.getNewRowMergedTable(-1, 3));

    TabularElementGroup<Element> headerMergedTable = testTable.getNewRowMergedTable(0, 1);
    assertEquals(6, headerMergedTable.numberOfRows());
    assertEquals(2, headerMergedTable.getColumnHeaderCount());
    assertMergedRowContent(Lists.mutable
        .of(Lists.mutable.empty(), Lists.mutable.of("Name of", "Account"),
            Lists.mutable.of("Place of", "Work")), headerMergedTable, 0);

    TabularElementGroup<Element> internalRowMergedTable = testTable.getNewRowMergedTable(5, 6);
    assertEquals(6, internalRowMergedTable.numberOfRows());
    assertEquals(3, internalRowMergedTable.getColumnHeaderCount());
    assertMergedRowContent(Lists.mutable
        .of(Lists.mutable.of("Cat-Priority"), Lists.mutable.of("Asterix &", "Obelix"),
            Lists.mutable.of("Gaul Co.")), internalRowMergedTable, 5);

    // merging header row with non-header row
    TabularElementGroup<Element> mixedMergedTable = testTable.getNewRowMergedTable(2, 3);
    assertEquals(6, mixedMergedTable.numberOfRows());
    assertEquals(3, mixedMergedTable.getColumnHeaderCount());
    assertMergedRowContent(Lists.mutable
        .of(Lists.mutable.of("Cat-1"), Lists.mutable.of("Holder", "Asterix"),
            Lists.mutable.of("Gaul Co.")), mixedMergedTable, 2);
  }

  @Test
  public void testGetColumn() {
    TabularElementGroup<Element> testTable = new TabularElementGroup<>(2, 2);
    testTable.getCells().zipWithIndex().each(row -> row.getOne().zipWithIndex().each(
        cell -> cell.getOne().getElements()
            .add(new TextElement().add(new Text("Cell" + row.getTwo() + cell.getTwo())))));
    assertEquals(Lists.mutable.of("Cell01", "Cell11"),
        testTable.getColumn(1).collect(cell -> cell.getFirst().getTextStr()));
    Verify.assertThrows(ArrayIndexOutOfBoundsException.class, () -> testTable.getColumn(-1));
    Verify.assertThrows(IndexOutOfBoundsException.class, () -> testTable.getColumn(10));
  }

  @Test
  public void testGetTextStr() throws Exception {
    MutableList<MutableList<String>> verticallyMergedTable = Lists.mutable.of(
        Lists.mutable.of("Head A1", "Head A2", "Head A3"),
        Lists.mutable.of("Merged Cell A11", "Cell A12", "Cell A13"),
        Lists.mutable.of("Cell A21", "Cell A22", "Cell A23"));
    MutableList<MutableList<String>> horizontallyMergedTable = Lists.mutable.of(
        Lists.mutable.of("Head B1", "Head B2", "Head B3", "Head B4"),
        Lists.mutable.of("Cell B11", "Cell B12", "Cell B13", "Cell B14"),
        Lists.mutable.of("Cell B21", "Cell B22", "Cell B23", "Cell B24"));
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox verticallyMergedTableBox = new GroupedBoundingBox(25, 50, 3, 3, 60, 35);
    verticallyMergedTableBox.forEachCellBBox(0, 2, 0, 2, (row, col, bbox) -> drawer
        .drawTextWithBorderInside(bbox, verticallyMergedTable.get(row).get(col)));
    GroupedBoundingBox horizontallyMergedTableBox = new GroupedBoundingBox(25, 315, 4, 3, 60, 20);
    horizontallyMergedTableBox.forEachCellBBox(0, 2, 0, 3,
        (row, col, bbox) -> drawer.drawTextInside(bbox, horizontallyMergedTable.get(row).get(col)));
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    drawer.drawTextAt(40, 300, "Parent Header 1");
    drawer.drawTextAt(160, 300, "Parent Header 2");
    MutableList<TabularElementGroup<Element>> tables = AbstractTransformerTest
        .getTablesFromDocument(new TableDetectionTransformer()
            .transform(new PositionalTextGroupingTransformer().transform(drawer.getDocument())));
    assertEquals(
        "Head A1\tHead A2\tHead A3\nMerged\nCell A11\nCell A12\tCell A13\nCell A21\tCell A22\tCell A23",
        tables.get(0).getTextStr());
    assertEquals(
        "Parent Header 1\tParent Header 2\nHead B1\tHead B2\tHead B3\tHead B4\nCell B11\tCell B12\tCell B13\tCell B14\nCell B21\tCell B22\tCell B23\tCell B24",
        tables.get(1).getTextStr());
  }

  @Test
  public void testElementCollectionFns() throws Exception {
    TabularElementGroup<Element> table = new TabularElementGroup<>(2, 3);
    TextElement textElement1 = new TextElement();
    TextElement textElement2 = new TextElement();
    table.addElement(0, 1, textElement1);
    table.addElement(1, 0, textElement2);
    assertEquals(textElement1, table.getFirst());
    assertEquals(textElement2, table.getLast());
    assertEquals(2, table.size());
  }

  @Test
  public void testPositionalContextClone(){
    Element firstElement = this.testTables.get(0).getElements().getFirst();
    PositionalContext<Element> positionalContext = firstElement
        .getPositionalContext();
    positionalContext.setShadowedAboveElement(firstElement);
    positionalContext.setShadowedLeftElement(firstElement);
    PositionalContext<Element> clonedPositionalContext = positionalContext.clone();
    assertEquals(positionalContext.getTabularGroup(), clonedPositionalContext.getTabularGroup());
    assertEquals(positionalContext.getVerticalGroup().toString(), clonedPositionalContext.getVerticalGroup().toString());
    assertEquals(positionalContext.getTabularColumn().toString(), clonedPositionalContext.getTabularColumn().toString());
    assertEquals(positionalContext.getTabularRow().toString(), clonedPositionalContext.getTabularRow().toString());
    assertEquals(positionalContext.getBoundingRectangle().toString(), clonedPositionalContext.getBoundingRectangle().toString());
    assertEquals(positionalContext.getPagePartitionType().toString(), clonedPositionalContext.getPagePartitionType().toString());
    assertEquals(positionalContext.getShadowedRightElement().toString(), clonedPositionalContext.getShadowedRightElement().toString());
    assertEquals(positionalContext.getShadowedLeftElement().toString(), clonedPositionalContext.getShadowedLeftElement().toString());
    assertEquals(positionalContext.getShadowedAboveElement().toString(), clonedPositionalContext.getShadowedAboveElement().toString());
    assertEquals(positionalContext.getShadowedBelowElement().toString(), clonedPositionalContext.getShadowedBelowElement().toString());
    assertEquals(positionalContext.getVisualLeft(), clonedPositionalContext.getVisualLeft(), 0.01);
    assertEquals(positionalContext.getVisualRight(), clonedPositionalContext.getVisualRight(), 0.01);
    assertEquals(positionalContext.getVisualTop(), clonedPositionalContext.getVisualTop(), 0.01);
    assertEquals(positionalContext.getVisualBottom(), clonedPositionalContext.getVisualBottom(), 0.01);
    assertEquals(positionalContext.getLeftElements().toString(), clonedPositionalContext.getLeftElements().toString());
    assertEquals(positionalContext.getRightElements().toString(), clonedPositionalContext.getRightElements().toString());
    assertEquals(positionalContext.getAboveElements().toString(), clonedPositionalContext.getAboveElements().toString());
    assertEquals(positionalContext.getPageBreakNumber(), clonedPositionalContext.getPageBreakNumber());
    assertEquals(positionalContext.getAlignmentRight(), clonedPositionalContext.getAlignmentRight(), 0.01);
    assertEquals(positionalContext.getAlignmentLeft(), clonedPositionalContext.getAlignmentLeft(), 0.01);
  }
}
