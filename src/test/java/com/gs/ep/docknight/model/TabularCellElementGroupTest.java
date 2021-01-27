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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.testutil.GroupedBoundingBox;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import com.gs.ep.docknight.model.transformer.AbstractTransformerTest;
import com.gs.ep.docknight.model.transformer.PositionalTextGroupingTransformer;
import com.gs.ep.docknight.model.transformer.TableDetectionTransformer;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Assert;
import org.junit.Test;

public class TabularCellElementGroupTest {

  private static void makeTestTable(PositionalDocDrawer drawer, String identifierText,
      GroupedBoundingBox tableBox, boolean addVisualBorder) throws Exception {
    MutableList<MutableList<String>> tableText = Lists.mutable.of(
        Lists.mutable
            .of("   Parent Header A" + identifierText, "   Parent Header B" + identifierText,
                "   Parent Header C" + identifierText),
        Lists.mutable.of("$   100." + identifierText, "$   200." + identifierText,
            "$   300." + identifierText));
    if (addVisualBorder) {
      tableBox.forEachCellBBox(0, 1, 0, 2,
          (row, col, bbox) -> drawer.drawTextWithBorderInside(bbox, tableText.get(row).get(col)));
    } else {
      tableBox.forEachCellBBox(0, 1, 0, 2,
          (row, col, bbox) -> drawer.drawTextInside(bbox, tableText.get(row).get(col)));
    }
  }

  @Test
  public void testVisualBorderExistence() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBoxWithBorder = new GroupedBoundingBox(50, 50, 3, 2, 70, 40);
    makeTestTable(drawer, "1", tableBoxWithBorder, true);
    GroupedBoundingBox tableBoxWithoutBorder = new GroupedBoundingBox(50, 200, 3, 2, 70, 40);
    makeTestTable(drawer, "2", tableBoxWithoutBorder, false);

    Document document = new TableDetectionTransformer()
        .transform(new PositionalTextGroupingTransformer().transform(drawer.getDocument()));
    MutableList<TabularElementGroup<Element>> tables = AbstractTransformerTest
        .getTablesFromDocument(document);
    tables.get(0).getCells().each(row -> row.each(cell -> Assert
        .assertEquals(new RectangleProperties<Boolean>(true, true, true, true),
            cell.getBorderExistence())));
    tables.get(1).getCells().get(0).each(cell -> Assert
        .assertEquals(new RectangleProperties<Boolean>(true, false, false, false),
            cell.getBorderExistence()));
    tables.get(1).getCells().get(1).each(cell -> Assert
        .assertEquals(new RectangleProperties<Boolean>(false, false, false, false),
            cell.getBorderExistence()));
  }
}
