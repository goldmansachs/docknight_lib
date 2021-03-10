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

import static org.junit.Assert.assertTrue;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.utility.Iterate;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.testutil.GroupedBoundingBox;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import com.gs.ep.docknight.model.transformer.PositionalTextGroupingTransformer;
import com.gs.ep.docknight.model.transformer.TableDetectionTransformer;
import java.util.List;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Test;

public class ElementCollectionTest {

  @Test
  public void getEnclosingVerticalGroups() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(20, 20, 2, 2, 40, 40);
    MutableList<MutableList<String>> rows = Lists.mutable
        .of(Lists.mutable.of("A1\nB1", "A2"), Lists.mutable.of("A3", "A4"));
    tableBox.forEachCellBBox(0, 1, 0, 1,
        (row, col, bbox) -> drawer.drawTextWithBorderInside(bbox, rows.get(row).get(col)));
    drawer.addPage();
    drawer.drawHorizontalLineAt(20, 20, 100);

    Document document = new TableDetectionTransformer()
        .transform(new PositionalTextGroupingTransformer().transform(drawer.getDocument()));
    List<Element> pageList = document.getContent().getElementList().getElements();
    Element a1 = Iterate.getFirst(document
        .getContainingElements(e -> e instanceof TextElement && e.getTextStr().equals("A1")));
    Element a4 = Iterate.getFirst(document
        .getContainingElements(e -> e instanceof TextElement && e.getTextStr().equals("A4")));
    HorizontalLine hl = (HorizontalLine) Iterate
        .getFirst(document.getContainingElements(e -> e instanceof HorizontalLine));
    ElementGroup<Element> verticalGroupA1 = a1.getPositionalContext().getVerticalGroup();
    ElementGroup<Element> verticalGroupA4 = a4.getPositionalContext().getVerticalGroup();

    assertTrue(document.getEnclosingVerticalGroups().contains(verticalGroupA1));
    assertTrue(pageList.get(0).getEnclosingVerticalGroups().contains(verticalGroupA1));
    assertTrue(verticalGroupA1.getEnclosingVerticalGroups().contains(verticalGroupA1));
    assertTrue(a1.getEnclosingVerticalGroups().contains(verticalGroupA1));
    MutableList<ElementGroup<Element>> enclosedVerticalGroups = new ElementGroup<>(
        Lists.mutable.of(a1, a4)).getEnclosingVerticalGroups();
    assertTrue(enclosedVerticalGroups.contains(verticalGroupA1));
    assertTrue(enclosedVerticalGroups.contains(verticalGroupA4));
    assertTrue(pageList.get(1).getEnclosingVerticalGroups().isEmpty());
    assertTrue(hl.getEnclosingVerticalGroups().isEmpty());
  }
}
