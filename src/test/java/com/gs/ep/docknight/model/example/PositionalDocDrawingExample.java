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

package com.gs.ep.docknight.model.example;

import org.eclipse.collections.impl.factory.Lists;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.testutil.BoundingBox;
import com.gs.ep.docknight.model.testutil.GroupedBoundingBox;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public final class PositionalDocDrawingExample {

  private PositionalDocDrawingExample() {
  }

  public static void main(String[] args) throws Exception {
    example1();
    //example2();
    //example3();
    //example4();
    //example5();
  }

  private static void example1() throws Exception {
    // Shows normal text editing
    double leftPageMargin = 100;
    double topPageMargin = 100;
    double lineSpacing = 10;
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_BOLD, 14);
    BoundingBox header1 = drawer.drawTextAt(leftPageMargin, topPageMargin, "Header1");
    BoundingBox header2 = drawer.drawTextAt(header1.getTopRightPlus(0, w -> 2 * w), "Header2");
    drawer.drawTextAt(header2.getTopRightPlus(0, w -> 2 * w), "Header3");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    BoundingBox textElement11 = drawer
        .drawTextAt(header1.getLeftBottomPlus(0, 3 * lineSpacing), "TextElement11");
    BoundingBox textElement12 = drawer
        .drawTextAt(textElement11.getLeftBottomPlus(0, lineSpacing), "TextElement12");
    drawer.drawTextAt(textElement12.getLeftBottomPlus(0, lineSpacing), "TextElement13");
    BoundingBox textElement21 = drawer
        .drawTextAt(header2.getLeftBottomPlus(0, 3 * lineSpacing), "TextElement21");
    BoundingBox textElement22 = drawer
        .drawTextAt(textElement21.getLeftBottomPlus(0, lineSpacing), "TextElement22");
    drawer.drawTextAt(textElement22.getLeftBottomPlus(0, lineSpacing), "TextElement23");

    Document document = drawer.getDocument();
    System.out.println(document.getTextStr());
  }

  private static void example2() throws Exception {
    // Shows how you can draw text, images, lines etc, export the document, then add some more stuff and export again
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawTextAt(100, 100, "My name is abc");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 30);
    drawer.drawTextAt(200, 100, "LOLOL");
    drawer.setFont(PDType1Font.HELVETICA_BOLD, 70);
    drawer.drawTextAt(350, 100, "HiHi!");
    drawer.drawVerticalLineAt(100, 100, 200);
    drawer.drawHorizontalLineAt(100, 100, 500);
    BufferedImage image = new BufferedImage(50, 100, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.GREEN);
    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    drawer.drawImageAt(530, 100, image);

    drawer.displayAsPdf("example2");

    drawer.drawTextAt(200, 400, "Done");
    drawer.displayAsHtml("example2");
  }

  private static void example3() throws Exception {
    // Shows how to draw subscripts and superscripts
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);

    BoundingBox a = drawer.drawTextAt(100, 100, "A");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 6);
    BoundingBox two = drawer.drawTextAt(a.getRightBottomPlus(0, h -> -h / 4), "2");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    BoundingBox o = drawer.drawTextAt(two.getRight(), a.getTop(), "O");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 6);
    BoundingBox five = drawer.drawTextAtTop(o.getTopRightPlus(h -> h / 4, 0), "5");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    drawer.drawTextAt(five.getRight(), a.getTop(), "X");

    drawer.displayAsPdf("example3");
  }

  private static void example4() throws Exception {
    // Shows how to draw paragraphs
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);

    GroupedBoundingBox paraBox = new GroupedBoundingBox(100, 100, 300, 300);
    String text =
        "  But I must explain to you how all this mistaken idea of denouncing of a pleasure and praising pain was born"
            +
            " and I will give you a complete account of the system, and expound the actual teachings of the great explorer of the"
            +
            " truth, the master-builder of human happiness. No one rejects, dislikes, or avoids pleasure itself, because it is pleasure,"
            +
            " but because those who do not know how to pursue pleasure rationally encounter consequences that are extremely painful.\n\n"
            +

            "  On the other hand, we denounce with righteous indignation and dislike men who are so beguiled and demoralized by the charms"
            +
            " of pleasure of the moment, so blinded by desire, that they cannot foresee the pain and trouble that are bound to ensue; and"
            +
            " equal blame belongs to those who fail in their duty through weakness of will, which is the same as saying through shrinking"
            +
            " from toil and pain.";
    drawer.drawTextWithBorderInside(paraBox.getFullBBox(), text);

    drawer.displayAsPdf("example4");
  }

  private static void example5() throws Exception {
    // Shows how to draw tables
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);

    GroupedBoundingBox tableBox = new GroupedBoundingBox(100, 100, 3, 4, 40, 20);

    // draw header rows spanning multiple columns
    drawer.drawTextWithBorderInside(tableBox.getRowBBox(0), "Trade Details");
    drawer.drawTextWithBorderInside(tableBox.getBBox(1, 1, 1, 2), "Score");
    drawer.drawTextWithBorderInside(tableBox.getCellBBox(1, 0), "Type");

    // fill normal rows data
    List<List<String>> rows = Lists.mutable
        .of(Lists.mutable.of("ABC", "100", "200"), Lists.mutable.of("XYZ", "555", "777"));
    tableBox.forEachCellBBox(2, 3, 0, 2,
        (r, c, bbox) -> drawer.drawTextWithBorderInside(bbox, rows.get(r - 2).get(c)));

    drawer.displayAsPdf("example5");
  }
}
