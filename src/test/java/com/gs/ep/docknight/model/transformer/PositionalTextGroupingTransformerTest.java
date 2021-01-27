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

import static com.googlecode.cqengine.query.QueryFactory.ascending;
import static com.googlecode.cqengine.query.QueryFactory.orderBy;
import static com.googlecode.cqengine.query.QueryFactory.queryOptions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.query.Query;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.test.Verify;
import org.eclipse.collections.impl.utility.Iterate;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementCollection;
import com.gs.ep.docknight.model.ModelCustomizationKey;
import com.gs.ep.docknight.model.ModelCustomizations;
import com.gs.ep.docknight.model.PositionalContext;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.testutil.DocUtils;
import com.gs.ep.docknight.model.testutil.GroupedBoundingBox;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.BeforeClass;
import org.junit.Test;

public class PositionalTextGroupingTransformerTest extends AbstractTransformerTest {

  private static Document document;

  @BeforeClass
  public static void initializeDocument() throws Exception {
    String filePath = Thread.currentThread().getContextClassLoader().getResource("Grouping.pdf")
        .getPath();
    document = DocUtils.parseAsDocument(new File(filePath), new MultiPageToSinglePageTransformer(),
        new PositionalTextGroupingTransformer());
  }

  public PositionalElementList<Element> getPositionalElementList(int index) {
    Page page = (Page) document.getContent().getValue().getElements().get(index);
    return page.getPositionalContent().getValue();
  }

  private void testTraversal(PositionalElementList<Element> positionalElementList,
      Map<String, String> traversalMap,
      Function<PositionalContext, Object> traversalOp) throws Exception {
    for (Element element : positionalElementList.getElements()) {
      String elementStr = element.getTextStr();
      if (traversalMap.containsKey(elementStr)) {
        Object traversalResult = traversalOp.apply(element.getPositionalContext());
        assertEquals(elementStr, traversalMap.get(elementStr),
            traversalResult == null ? null : traversalResult.toString());
      }
    }
  }

  @Test
  public void testBelowElements() throws Exception {
    Map<String, String> belowMap = UnifiedMap.newWithKeysValues(
        "La La Land", "Fat Elephant",
        "Bird Town", "Eagle\tPeacock\tPenguin",
        "Last Night Race Winners", "Batman\tSpiderman\nSherlock",
        "INVENTORY", "ITEM\tTYPE\tCOST\tWEIGHT").withKeysValues(
        "Theme Song", "1\t2\t4\t8\t16\t32\t64",
        "Come", "Here\nIf you are lost call:",
        "5", "1 month").withKeysValues(
        "9.0%", "Ripple Carry Adder",
        "Hades", "");
    this.testTraversal(this.getPositionalElementList(0), belowMap,
        context -> context.getBelowElements());
  }

  @Test
  public void testAboveElements() throws Exception {
    Map<String, String> aboveMap = UnifiedMap.newWithKeysValues(
        "Sherlock", "Batman\tSpiderman\nLast Night Race Winners",
        "Jupiter", "Eagle",
        "Bird Town", "");
    this.testTraversal(this.getPositionalElementList(0), aboveMap,
        context -> context.getAboveElements());
  }

  @Test
  public void testLeftElements() throws Exception {
    Map<String, String> leftMap = UnifiedMap.newWithKeysValues(
        "La La Land", "",
        "Peacock", "Eagle",
        "Penguin", "Bird Town\nPeacock",
        "Jupiter", "Neptune").withKeysValues(
        "PARK", "Here\nCome",
        "Go", "PARK");
    this.testTraversal(this.getPositionalElementList(0), leftMap,
        context -> context.getLeftElements());
  }

  @Test
  public void testRightElements() throws Exception {
    Map<String, String> rightMap = UnifiedMap.newWithKeysValues(
        "Bird Town", "",
        "Eagle", "Peacock\nBird Town",
        "Earth", "Pluto",
        "PARK", "Go\nBack");
    this.testTraversal(this.getPositionalElementList(0), rightMap,
        context -> context.getRightElements());
  }

  @Test
  public void testShadowedBelowElement() throws Exception {
    Map<String, String> belowMap = UnifiedMap.newWithKeysValues(
        "La La Land", "Fat Elephant",
        "Bird Town", "Peacock",
        "Come", "Here",
        "Rx", "Lol").withKeysValues(
        "TYPE", "Device",
        "5", "1 month").withKeysValues(
        "9.0%", "Ripple Carry Adder",
        "Hades", null
    );
    this.testTraversal(this.getPositionalElementList(0), belowMap,
        context -> context.getShadowedBelowElement());
  }

  @Test
  public void testShadowedLeftElement() throws Exception {
    Map<String, String> leftMap = UnifiedMap.newWithKeysValues(
        "La La Land", null,
        "Bird Town", "La La Land",
        "PARK", "Here");
    this.testTraversal(this.getPositionalElementList(0), leftMap,
        context -> context.getShadowedLeftElement());
  }

  @Test
  public void testShadowedRightElement() throws Exception {
    Map<String, String> rightMap = UnifiedMap.newWithKeysValues(
        "La La Land", "Bird Town",
        "Bird Town", null,
        "PARK", "Go");
    this.testTraversal(this.getPositionalElementList(0), rightMap,
        context -> context.getShadowedRightElement());
  }

  @Test
  public void testShadowedAboveElement() throws Exception {
    Map<String, String> aboveMap = UnifiedMap.newWithKeysValues(
        "La La Land", null,
        "Fat Elephant", "La La Land",
        "Peacock", "Bird Town");
    this.testTraversal(this.getPositionalElementList(0), aboveMap,
        context -> context.getShadowedAboveElement());
  }

  @Test
  public void testVerticalGroups() throws Exception {
    Verify.assertContainsAll(ListAdapter.adapt(this.getPositionalElementList(0).getVerticalGroups())
            .collect(Object::toString),
        "La La Land\nFat Elephant\nLives Here\nNeptune",
        "Eagle\nJupiter",
        "Peacock\nEarth",
        "Penguin\nPluto",
        "If you are lost call:\n123456789012345678901234567890",
        "Welcome\nTo the Animal Kingdom\nExplore at your own risk & enjoy!",
        "Propose model\nTo automate\nThe Park\nAnd win Prizes");
  }

  @Test
  public void testTabularGroups() throws Exception {
    MutableList<String> tabularGroupStrings = ListAdapter
        .adapt(this.getPositionalElementList(0).getTabularGroups()).collect(Object::toString);
    MutableList<String> expectedTabularGroupStrings = Lists.mutable.of(
        "First\t" + "Second\t" + "Third\n" +
            "Quantaland\t" + "Nueroland\t" + "Bitland",

        "Languages\t" + "Machines\n" +
            "Any\t$\t" + ":)",

        "Player\t" + "Score\n" +
            "Avogadro\t" + "120\n" +
            "Planck\t" + "120.001",

        "ITEM\t" + "TYPE\t" + "COST\t" + "WEIGHT\n" +
            "3D Printer\t" + "Device\t" + "100\t" + "2 kg\n" +
            "Genome Editor\t" + "Device\t" + "150\t" + "1 kg\n" +
            "Drone\t" + "Vehicle\t" + "500\t" + "100 kg\n" +
            "Personal AI Assistant\t" + "Software\t" + "10\t" + "Depends",

        "1\t" + "2\t" + "4\t" + "8\t" + "16\t" + "32\t" + "64\n" +
            "1\t" + "2\t" + "3\t" + "4\t" + "5\t" + "6\t" + "7",

        "1 month\t" + "3 month\t" + "Lifetime\t" + "1 month\t" + "3 month\t" + "Lifetime\n" +
            "PPR\t" + "12.1%\t" + "11.1 %\t" + "13.5%\t" + "2.4%\t" + "12.3%\t" + "9.5%\n" +
            "CPR\t" + "13.4%\t" + "12.3%\t" + "9.0%\t" + "3.6%\t" + "5.6%\t" + "11.5%",

        "Reverundum\t" + "Ripple Carry Adder\t" + "Multiplexer\n" +
            "Pagination\t" + "Virtual Address Space\t" + "Speculative Branching\n" +
            "Diffie-Hellman\t" + "Scoliosis\t" + "Tessellation",

        "WillyWonka\t" + "MakeShifters\n" +
            "DoppelGangers\n" +
            "Selenium\t" + "KDE Neon\t" + "GPL\n" +
            "Stan\t" + "Purple hills\t" + "Hades"
    );
    for (String expectedTabularGroupString : expectedTabularGroupStrings) {
      assertTrue(expectedTabularGroupString,
          tabularGroupStrings.anySatisfy(t -> t.contains(expectedTabularGroupString)));
    }
  }

  @Test
  public void testTablesAcrossPages() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.A6);

    GroupedBoundingBox tableBox1 = new GroupedBoundingBox(40, 320, 3, 4, 60, 60);
    tableBox1.forEachCellBBox(0, 3, 0, 2,
        (r, c, bbox) -> drawer.drawTextWithBorderInside(bbox, "Text" + r + c));
    drawer.addPage();
    GroupedBoundingBox tableBox2 = new GroupedBoundingBox(40, 20, 3, 2, 60, 60);
    tableBox2.forEachCellBBox(0, 1, 0, 2,
        (r, c, bbox) -> drawer.drawTextWithBorderInside(bbox, "Text" + (r + 2) + c));
    Document document = DocUtils
        .applyTransformersOnDocument(drawer.getDocument(), new MultiPageToSinglePageTransformer(),
            new PositionalTextGroupingTransformer());

    Page page = (Page) document.getContent().getValue().getElements().get(0);
    MutableList<TabularElementGroup<Element>> tabularGroups = page.getPositionalContent().getValue()
        .getTabularGroups();
    assertEquals(Lists.mutable
            .of("Text00\tText01\tText02\nText10\tText11\tText12\nText20\tText21\tText22\nText30\tText31\tText32"),
        tabularGroups.collect(ElementCollection::getTextStr));
  }

  @Test
  public void testRectangles() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.A6);
    drawer.drawTextAt(110, 30, "RECTANGLES");

    drawer.drawHorizontalLineAt(20, 60, 250);
    drawer.drawVerticalLineAt(20, 60, 100);
    drawer.drawVerticalLineAt(270, 60, 100);
    drawer.drawHorizontalLineAt(20, 160, 250);
    drawer.drawTextAt(40, 90, "Text1");
    drawer.drawTextAt(190, 90, "Text2");
    drawer.drawTextAt(120, 120, "Text3");

    drawer.drawHorizontalLineAt(20, 200, 250);
    drawer.drawVerticalLineAt(20, 200, 200);
    drawer.drawVerticalLineAt(270, 200, 200);
    drawer.drawTextAt(120, 300, "Text4");

    drawer.addPage();
    drawer.drawVerticalLineAt(20, 20, 370);
    drawer.drawVerticalLineAt(270, 20, 370);
    drawer.drawTextAt(120, 100, "Text5");
    drawer.drawTextAt(40, 250, "Text6");
    drawer.drawTextAt(190, 250, "Text7");

    drawer.addPage();
    drawer.drawVerticalLineAt(20, 20, 30);
    drawer.drawVerticalLineAt(270, 20, 30);
    drawer.drawHorizontalLineAt(20, 50, 250);

    drawer.drawHorizontalLineAt(20, 150, 250);
    drawer.drawVerticalLineAt(20, 150, 240);
    drawer.drawVerticalLineAt(270, 150, 240);
    drawer.drawTextAt(40, 200, "Text8");
    drawer.drawTextAt(190, 300, "Text9");

    drawer.addPage();
    drawer.drawVerticalLineAt(20, 50, 30);
    drawer.drawVerticalLineAt(270, 50, 30);
    drawer.drawHorizontalLineAt(20, 80, 250);
    drawer.drawTextAt(120, 30, "Text10");

//        drawer.displayAsHtml("rectangles");

    Document document = DocUtils
        .applyTransformersOnDocument(drawer.getDocument(), new MultiPageToSinglePageTransformer(),
            new PositionalTextGroupingTransformer());
    MutableMap<Rectangle2D, RichIterable<Element>> rectangleToElementMap = Iterate
        .groupBy(document.getContainingElements(TextElement.class),
            e -> e.getPositionalContext().getBoundingRectangle()).toMap();
    rectangleToElementMap.remove(null);

    MutableSet<MutableList<String>> expectedRectStrings = Sets.mutable
        .of(Lists.mutable.of("Text1", "Text2", "Text3"),
            Lists.mutable.of("Text4", "Text5", "Text6", "Text7"));
    MutableSet<RichIterable<String>> actualRectStrings = Sets.mutable
        .ofAll(rectangleToElementMap.values()).collect(list -> list.collect(Element::getTextStr));
    assertEquals(expectedRectStrings, actualRectStrings);
  }

  @Test
  public void testIntersection() throws Exception {
    SimpleAttribute<Point2D, Double> x = PositionalTextGroupingTransformer
        .simpleAttribute(Point2D.class, Double.class, "x", Point2D::getX);
    SimpleAttribute<Point2D, Double> y = PositionalTextGroupingTransformer
        .simpleAttribute(Point2D.class, Double.class, "y", Point2D::getY);
    IndexedCollection<Point2D> index = PositionalTextGroupingTransformer.createNavigableIndex(x, y);
    List<Point2D> points = Lists.mutable.of(
        new Point2D.Double(32, 38), new Point2D.Double(32, 40), new Point2D.Double(32, 42),
        new Point2D.Double(30, 38), new Point2D.Double(30, 40), new Point2D.Double(30, 42),
        new Point2D.Double(28, 38), new Point2D.Double(28, 40), new Point2D.Double(28, 42),
        new Point2D.Double(25, 29), new Point2D.Double(41, 49)
    );
    index.addAll(points);
    Query<Point2D> intersectingWith30To40 = PositionalTextGroupingTransformer
        .intersection(x, y, 30.0, 40.0);
    List<Point2D> intersectingPoints = Lists.mutable.ofAll(
        index.retrieve(intersectingWith30To40, queryOptions(orderBy(ascending(x), ascending(y)))));
    assertEquals(points.size() - 2, intersectingPoints.size());
    Verify.assertNotContains(new Point2D.Double(25, 29), intersectingPoints);
    Verify.assertNotContains(new Point2D.Double(41, 49), intersectingPoints);
  }

  @Test
  public void testTabularNoiseHandling() throws Exception {
    MutableList<MutableList<String>> table1Contents = Lists.mutable.of(
        Lists.mutable.of("Content A11", "Content A12", "Content A13"),
        Lists.mutable.of("Content A21", "Content A22", "Content A23")
    );
    MutableList<MutableList<String>> table2Contents = Lists.mutable.of(
        Lists.mutable.of("Content B11", "Content B12", "Content B13"),
        Lists.mutable.of("Content B21", "Content B22", "Content B23")
    );
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox1 = new GroupedBoundingBox(50, 50, 3, 2, 100, 20);
    tableBox1.forEachCellBBox(0, 1, 0, 2,
        (row, col, bbox) -> drawer.drawTextInside(bbox, table1Contents.get(row).get(col)));
    GroupedBoundingBox tableBox2 = new GroupedBoundingBox(50, 110, 3, 2, 100, 20);
    tableBox2.forEachCellBBox(0, 1, 0, 2,
        (row, col, bbox) -> drawer.drawTextInside(bbox, table2Contents.get(row).get(col)));
    String noiseText = "Note: This is a noise element which will break the table";
    drawer.drawTextAt(55, 95, noiseText);

    TestObjects defaultTestObjects = formTestObjects(drawer.getDocument(),
        Lists.mutable.of(makeTable(table1Contents), makeTable(table2Contents)),
        new PositionalTextGroupingTransformer());
    assertTabularGroups(defaultTestObjects.expectedTabularGroupsHtml,
        defaultTestObjects.actualTabularGroups);

    MutableList<MutableList<String>> customizedTableContents = Lists.mutable.ofAll(table1Contents);
    customizedTableContents.add(Lists.mutable.of(noiseText, "", ""));
    customizedTableContents.addAll(table2Contents);

    ModelCustomizations modelCustomizations = new ModelCustomizations()
        .add(ModelCustomizationKey.TABULAR_NOISE_PATTERNS,
            ModelCustomizationKey.TABULAR_NOISE_PATTERNS.parse(Lists.mutable.of("Note:.*")));
    TestObjects customizedTestObjects = formTestObjects(drawer.getDocument(),
        Lists.mutable.of(makeTable(customizedTableContents)),
        new PositionalTextGroupingTransformer(modelCustomizations));
    assertTabularGroups(customizedTestObjects.expectedTabularGroupsHtml,
        customizedTestObjects.actualTabularGroups);
  }

  @Test
  public void testTableDetectionDisablement() throws Exception {
    MutableList<MutableList<String>> table = Lists.mutable
        .of(Lists.mutable.of("Cell 11", "Cell 12"), Lists.mutable.of("Cell 21", "Cell 22"));
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(25, 50, 2, 2, 60, 20);
    tableBox.forEachCellBBox(0, 1, 0, 1,
        (row, col, bbox) -> drawer.drawTextWithBorderInside(bbox, table.get(row).get(col)));
    Document rawDocument = drawer.getDocument();
    ModelCustomizations customizations = new ModelCustomizations()
        .add(ModelCustomizationKey.DISABLE_TABLE_DETECTION, true);
    Document documentWithoutTables = new PositionalTextGroupingTransformer(customizations)
        .transform(rawDocument);
    assertTrue(AbstractTransformerTest.getTablesFromDocument(documentWithoutTables).isEmpty());
    Document documentWithTables1 = new PositionalTextGroupingTransformer().transform(rawDocument);
    assertFalse(AbstractTransformerTest.getTablesFromDocument(documentWithTables1).isEmpty());
    customizations.add(ModelCustomizationKey.DISABLE_TABLE_DETECTION, false);
    Document documentWithTables2 = new PositionalTextGroupingTransformer(customizations)
        .transform(rawDocument);
    assertFalse(AbstractTransformerTest.getTablesFromDocument(documentWithTables2).isEmpty());
  }

  @Test
  public void testBackgroundImages() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawTextAt(100, 100, "Top");
    drawer.drawImageAt(50, 150, DocUtils.createBufferedImage(150, 150, Color.GREEN));
    drawer.drawTextAt(100, 200, "Bottom");
    Document document = new PositionalTextGroupingTransformer().transform(drawer.getDocument());
    // testing that background images don't come into positional context
    assertEquals("Bottom",
        DocUtils.selectElementContext(document, "Top").getShadowedBelowElement().getTextStr());
  }

  @Test
  public void testVerticalGroupForImages() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawImageAt(54, 109, DocUtils.createBufferedImage(504, 25, Color.GREEN));
    drawer.drawTextAt(54, 138, "Different vertical group");
    drawer.drawTextAt(54, 146, "Same vertical grp as above");
    Document document = new PositionalTextGroupingTransformer().transform(drawer.getDocument());

    // testing that images and text don't come into same vertical group
    Page page = (Page) document.getContent().getValue().getElements().get(0);
    List<Element> elements = page.getPositionalContent().getElements();
    assertEquals(1, elements.get(0).getPositionalContext().getVerticalGroup().size());
    assertEquals(2, elements.get(1).getPositionalContext().getVerticalGroup().size());

    Verify.assertContainsAll(
        ListAdapter.adapt(page.getPositionalContent().getValue().getVerticalGroups())
            .collect(Object::toString),
        "",
        "Different vertical group\tSame vertical grp as above");
  }
}
