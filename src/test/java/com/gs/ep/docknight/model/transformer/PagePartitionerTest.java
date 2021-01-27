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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.ListIterate;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementAttribute;
import com.gs.ep.docknight.model.ElementList;
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.Length.Unit;
import com.gs.ep.docknight.model.ModelCustomizations;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Text;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.context.PagePartitionType;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.testutil.GroupedBoundingBox;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import com.gs.ep.docknight.model.transformer.grouping.PagePartition;
import com.gs.ep.docknight.model.transformer.grouping.PagePartitioner;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Test;

public class PagePartitionerTest {

  private static Set<Pair<PagePartitionType, List<String>>> getPartitionsInfo(Page page,
      boolean allowContextAcrossPageBreaks, boolean excludeHeaderFooter) {
    return getPartitionsInfo(page, allowContextAcrossPageBreaks, excludeHeaderFooter, false, false);
  }

  private static Set<Pair<PagePartitionType, List<String>>> getPartitionsInfo(
      Page page,
      boolean allowContextAcrossPageBreaks,
      boolean excludeHeaderFooter,
      boolean allowTables,
      boolean allowRegexMatch) {
    PagePartitioner partitioner = new PagePartitioner(allowContextAcrossPageBreaks,
        excludeHeaderFooter)
        .withTableBasedHeaderFooterDetection(allowTables)
        .withRegexBasedHeaderFooterDetection(allowRegexMatch);
    List<PagePartition> partitions = partitioner.getPartitions(page);
    return Sets.mutable.ofAll(ListIterate.collect(partitions, p -> Tuples.pair(p.partitionType,
        ListIterate.collect(p.elements, Element::getTextStr).reject(String::isEmpty))));
  }

  @Test
  public void testPartitioning() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 8);
    drawer.drawTextAt(10, 20, "http://www.temp.com/1");
    drawer.drawTextAt(400, 20, "TEMP");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    drawer.drawTextAt(300, 300, "Page 1 Content");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 8);
    drawer.drawTextAt(300, 750, "Page 1 of 2");
    drawer.addPage();
    drawer.setFont(PDType1Font.TIMES_ROMAN, 8);
    drawer.drawTextAt(20, 15, "http://www.temp.com/2");
    drawer.drawTextAt(410, 15, "TEMP");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    drawer.drawTextAt(310, 310, "Page 2 Content");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 8);
    drawer.drawTextAt(310, 740, "Page 2 of 2");
    //drawer.displayAsHtml("lol");

    Page page = (Page) new MultiPageToSinglePageTransformer().transform(drawer.getDocument())
        .getContent().getElements().get(0);
    PositionalElementList<Element> pageElementList = page.getPositionalContent().getElementList();
    Pair<PagePartitionType, List<String>> header1 = Tuples
        .pair(PagePartitionType.HEADER, Lists.mutable.of("http://www.temp.com/1", "TEMP"));
    Pair<PagePartitionType, List<String>> header2 = Tuples
        .pair(PagePartitionType.HEADER, Lists.mutable.of("http://www.temp.com/2", "TEMP"));
    Pair<PagePartitionType, List<String>> footer1 = Tuples
        .pair(PagePartitionType.FOOTER, Lists.mutable.of("Page 1 of 2"));
    Pair<PagePartitionType, List<String>> footer2 = Tuples
        .pair(PagePartitionType.FOOTER, Lists.mutable.of("Page 2 of 2"));
    Pair<PagePartitionType, List<String>> content1 = Tuples
        .pair(PagePartitionType.CONTENT, Lists.mutable.of("Page 1 Content"));
    Pair<PagePartitionType, List<String>> content2 = Tuples
        .pair(PagePartitionType.CONTENT, Lists.mutable.of("Page 2 Content"));
    Pair<PagePartitionType, List<String>> allContent = Tuples
        .pair(PagePartitionType.CONTENT, Lists.mutable.of("Page 1 Content", "Page 2 Content"));
    Pair<PagePartitionType, List<String>> pagePart1 = Tuples.pair(PagePartitionType.CONTENT,
        ListIterate.collect(pageElementList.getElementsTillPageBreak(1), Element::getTextStr));
    Pair<PagePartitionType, List<String>> pagePart2 = Tuples.pair(PagePartitionType.CONTENT,
        ListIterate.collect(pageElementList.getElementsFromPageBreak(1), Element::getTextStr));
    Pair<PagePartitionType, List<String>> everything = Tuples
        .pair(PagePartitionType.CONTENT, ListIterate.reject(getTextStrList(page), String::isEmpty));

    assertEquals(Sets.mutable.of(allContent, header1, header2, footer1, footer2),
        getPartitionsInfo(page, true, true));
    assertEquals(Sets.mutable.of(everything), getPartitionsInfo(page, true, false));
    assertEquals(Sets.mutable.of(content1, content2, header1, header2, footer1, footer2),
        getPartitionsInfo(page, false, true));
    assertEquals(Sets.mutable.of(pagePart1, pagePart2), getPartitionsInfo(page, false, false));

    page = (Page) drawer.getDocument().getContent().getElements().get(0);
    assertEquals(Sets.mutable.of(pagePart1), getPartitionsInfo(page, true, true));
    assertEquals(Sets.mutable.of(pagePart1), getPartitionsInfo(page, true, false));
    assertEquals(Sets.mutable.of(pagePart1), getPartitionsInfo(page, false, true));
    assertEquals(Sets.mutable.of(pagePart1), getPartitionsInfo(page, false, false));
  }

  @Test
  public void testTabularHeader() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    for (int i = 0; i < 2; i++) {
      GroupedBoundingBox box = new GroupedBoundingBox(10, 10, 3, 5, 195, 20);
      box.forEachCellBBox(0, 4, 0, 2,
          (r, c, bbox) -> drawer.drawTextWithBorderInside(bbox, "h" + r));
      drawer.drawTextAt(100, 500, "Content");
      drawer.addPage();
    }
    //drawer.displayAsHtml("lol");

    Page page = (Page) new PositionalTextGroupingTransformer(new ModelCustomizations())
        .transform(new MultiPageToSinglePageTransformer().transform(drawer.getDocument()))
        .getContent().getElements().get(0);
    Pair<PagePartitionType, List<String>> header = Tuples.pair(PagePartitionType.HEADER,
        Lists.mutable.withNValues(15, () -> "h").zipWithIndex()
            .collect((pair) -> pair.getOne() + (pair.getTwo() / 3)));
    Pair<PagePartitionType, List<String>> content = Tuples
        .pair(PagePartitionType.CONTENT, Lists.mutable.of("Content", "Content"));
    assertEquals(Sets.mutable.of(header, content),
        getPartitionsInfo(page, true, true, true, false));
  }

  @Test
  public void testRegexHeaderFooter() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawTextAt(10, 10, "01/01/2018               Regex and Company");
    drawer.drawTextAt(100, 500, "Content");
    drawer.drawTextAt(10, 770, "http://www.temp.com");
    //drawer.displayAsHtml("lol");

    Page page = (Page) drawer.getDocument().getContent().getElements().get(0);
    Pair<PagePartitionType, List<String>> header = Tuples
        .pair(PagePartitionType.HEADER, Lists.mutable.of("01/01/2018", "Regex and Company"));
    Pair<PagePartitionType, List<String>> content = Tuples
        .pair(PagePartitionType.CONTENT, Lists.mutable.of("Content"));
    Pair<PagePartitionType, List<String>> footer = Tuples
        .pair(PagePartitionType.FOOTER, Lists.mutable.of("http://www.temp.com"));
    assertEquals(Sets.mutable.of(header, content, footer),
        getPartitionsInfo(page, true, true, false, true));

    drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawTextAt(10, 10, "My name is XYZ");
    drawer.drawTextAt(100, 500, "Content");
    drawer.drawTextAt(10, 770, "My company is PQR");
    //drawer.displayAsHtml("lol");

    page = (Page) drawer.getDocument().getContent().getElements().get(0);
    assertEquals(1, getPartitionsInfo(page, true, true, false, true).size());
  }

  @Test
  public void testElementSimilarity() throws Exception {
    TextElement textElement1 = new TextElement()
        .add(new Text("Page 1 of 2"))
        .add(new Left(new Length(100, Unit.pt)))
        .add(new Top(new Length(100, Unit.pt)))
        .add(new Height(new Length(10, Unit.pt)));

    TextElement textElement2 = new TextElement()
        .add(new Text("Page 2 of 2"))
        .add(new Left(new Length(110, Unit.pt)))
        .add(new Top(new Length(1110, Unit.pt)))
        .add(new Height(new Length(11, Unit.pt)));

    assertTrue(PagePartitioner.areElementsSimilar(textElement1, textElement2, 0, 1000));

    textElement2.getText().setValue("Page 2 out of 2");
    assertFalse(PagePartitioner.areElementsSimilar(textElement1, textElement2, 0, 1000));

    textElement2.getText().setValue("Page 2 of 2");
    textElement2.getTop().setMagnitude(1300);
    assertFalse(PagePartitioner.areElementsSimilar(textElement1, textElement2, 0, 1000));

    textElement2.getTop().setMagnitude(1110);
    textElement2.getLeft().setMagnitude(300);
    assertFalse(PagePartitioner.areElementsSimilar(textElement1, textElement2, 0, 1000));

    textElement2.getLeft().setMagnitude(110);
    textElement2.getHeight().setMagnitude(15);
    assertFalse(PagePartitioner.areElementsSimilar(textElement1, textElement2, 0, 1000));
  }

  @Test
  public void testCommonPattern() throws Exception {
    TextElement textElement11 = new TextElement()
        .add(new Text("text1"))
        .add(new Left(new Length(100, Unit.pt)))
        .add(new Top(new Length(100, Unit.pt)))
        .add(new Height(new Length(10, Unit.pt)));

    TextElement textElement12 = new TextElement()
        .add(new Text("text2"))
        .add(new Left(new Length(500, Unit.pt)))
        .add(new Top(new Length(111, Unit.pt)))
        .add(new Height(new Length(10, Unit.pt)));

    TextElement textElement13 = new TextElement()
        .add(new Text("text3"))
        .add(new Left(new Length(100, Unit.pt)))
        .add(new Top(new Length(500, Unit.pt)))
        .add(new Height(new Length(10, Unit.pt)));

    TextElement textElement21 = new TextElement()
        .add(new Text("text1"))
        .add(new Left(new Length(100, Unit.pt)))
        .add(new Top(new Length(1111, Unit.pt)))
        .add(new Height(new Length(10, Unit.pt)));

    TextElement textElement22 = new TextElement()
        .add(new Text("text2"))
        .add(new Left(new Length(500, Unit.pt)))
        .add(new Top(new Length(1100, Unit.pt)))
        .add(new Height(new Length(10, Unit.pt)));

    TextElement textElement23 = new TextElement()
        .add(new Text("text3"))
        .add(new Left(new Length(500, Unit.pt)))
        .add(new Top(new Length(1500, Unit.pt)))
        .add(new Height(new Length(10, Unit.pt)));

    assertEquals(2, PagePartitioner
        .findCommonCount(Lists.mutable.of(textElement11, textElement12, textElement13),
            Lists.mutable.of(textElement22, textElement21, textElement23), 0, 1000));
    assertEquals(2, PagePartitioner
        .findCommonCount(Lists.mutable.of(textElement11, textElement12, textElement13),
            Lists.mutable.of(textElement23, textElement21, textElement22), 0, 1000));
    assertEquals(0, PagePartitioner
        .findCommonCount(Lists.mutable.of(textElement13), Lists.mutable.of(textElement23), 0, 100));
    assertEquals(0, PagePartitioner
        .findCommonCount(Lists.mutable.of(textElement13), Lists.mutable.of(), 0, 100));
    assertEquals(0,
        PagePartitioner.findCommonCount(Lists.mutable.of(), Lists.mutable.of(), 0, 100));
  }

  @Test
  public void testHeaderFooterCandidatesRepeatedWithinPage() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 8);
    drawer.drawTextAt(10, 20, "http://www.actualHeader.com/1");
    drawer.drawTextAt(10, 40, "Repeated Text");
    drawer.drawTextAt(80, 40, "from Content 1");
    drawer.drawTextAt(15, 150, "Repeated Text");
    drawer.drawTextAt(75, 150, "from Content 1");
    drawer.setFont(PDType1Font.TIMES_BOLD, 8);
    drawer.drawTextAt(250, 220, "1 of 2");
    drawer.drawTextAt(280, 220, "Sections");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 8);
    drawer.drawTextAt(250, 740, "1 of 2");
    drawer.drawTextAt(280, 740, "Pages");
    drawer.addPage();
    drawer.setFont(PDType1Font.TIMES_ROMAN, 8);
    drawer.drawTextAt(10, 20, "http://www.actualHeader.com/2");
    drawer.drawTextAt(10, 40, "Repeated Text");
    drawer.drawTextAt(80, 40, "from Content 2");
    drawer.drawTextAt(15, 150, "Repeated Text");
    drawer.drawTextAt(75, 150, "from Content 2");
    drawer.setFont(PDType1Font.TIMES_BOLD, 8);
    drawer.drawTextAt(250, 220, "2 of 2");
    drawer.drawTextAt(280, 220, "Sections");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 8);
    drawer.drawTextAt(250, 740, "2 of 2");
    drawer.drawTextAt(280, 740, "Pages");
    //drawer.displayAsHtml("demoDoc");

    Page page = (Page) new MultiPageToSinglePageTransformer().transform(drawer.getDocument())
        .getContent().getElements().get(0);
    PositionalElementList<Element> pageElementList = page.getPositionalContent().getElementList();
    Pair<PagePartitionType, List<String>> header1 = Tuples
        .pair(PagePartitionType.HEADER, Lists.mutable.of("http://www.actualHeader.com/1"));
    Pair<PagePartitionType, List<String>> header2 = Tuples
        .pair(PagePartitionType.HEADER, Lists.mutable.of("http://www.actualHeader.com/2"));
    Pair<PagePartitionType, List<String>> footer1 = Tuples
        .pair(PagePartitionType.FOOTER, Lists.mutable.of("1 of 2", "Pages"));
    Pair<PagePartitionType, List<String>> footer2 = Tuples
        .pair(PagePartitionType.FOOTER, Lists.mutable.of("2 of 2", "Pages"));
    Pair<PagePartitionType, List<String>> content1 = Tuples.pair(PagePartitionType.CONTENT,
        Lists.mutable
            .of("Repeated Text", "from Content 1", "Repeated Text", "from Content 1", "1 of 2",
                "Sections"));
    Pair<PagePartitionType, List<String>> content2 = Tuples.pair(PagePartitionType.CONTENT,
        Lists.mutable
            .of("Repeated Text", "from Content 2", "Repeated Text", "from Content 2", "2 of 2",
                "Sections"));
    Pair<PagePartitionType, List<String>> allContent = Tuples.pair(PagePartitionType.CONTENT,
        Lists.mutable
            .of("Repeated Text", "from Content 1", "Repeated Text", "from Content 1", "1 of 2",
                "Sections",
                "Repeated Text", "from Content 2", "Repeated Text", "from Content 2", "2 of 2",
                "Sections"));
    Pair<PagePartitionType, List<String>> pagePart1 = Tuples.pair(PagePartitionType.CONTENT,
        ListIterate.collect(pageElementList.getElementsTillPageBreak(1), Element::getTextStr));
    Pair<PagePartitionType, List<String>> pagePart2 = Tuples.pair(PagePartitionType.CONTENT,
        ListIterate.collect(pageElementList.getElementsFromPageBreak(1), Element::getTextStr));
    Pair<PagePartitionType, List<String>> everything = Tuples
        .pair(PagePartitionType.CONTENT, ListIterate.reject(getTextStrList(page), String::isEmpty));

    assertEquals(Sets.mutable.of(allContent, header1, header2, footer1, footer2),
        getPartitionsInfo(page, true, true));
    assertEquals(Sets.mutable.of(everything), getPartitionsInfo(page, true, false));
    assertEquals(Sets.mutable.of(content1, content2, header1, header2, footer1, footer2),
        getPartitionsInfo(page, false, true));
    assertEquals(Sets.mutable.of(pagePart1, pagePart2), getPartitionsInfo(page, false, false));
  }

  private List<String> getTextStrList(Element element) {
    List<String> textStrList = Lists.mutable.empty();
    for (Class<? extends ElementAttribute> elementAttributeClass : element.getFinalLayout()) {
      if (element.hasAttribute(elementAttributeClass)) {
        textStrList
            .addAll(getTextStrList(element.getAttribute(elementAttributeClass).getElementList()));
      }
    }
    return textStrList;
  }

  public List<String> getTextStrList(ElementList elementList) {
    List<String> textStrList = Lists.mutable.empty();
    for (Object element : elementList.getElements()) {
      textStrList.add(((Element) element).getTextStr());
    }
    return textStrList;
  }
}
