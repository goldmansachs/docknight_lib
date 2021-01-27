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

package com.gs.ep.docknight.model.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.test.Verify;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import com.gs.ep.docknight.model.ComparableBufferedImage;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementIterable;
import com.gs.ep.docknight.model.Form;
import com.gs.ep.docknight.model.Form.FormType;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.FormData;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.LetterSpacing;
import com.gs.ep.docknight.model.attribute.PageColor;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.converter.PdfParser.UnDigitizedPdfException;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.FormElement;
import com.gs.ep.docknight.model.element.GraphicalElement;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.Image;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.element.VerticalLine;
import com.gs.ep.docknight.model.testutil.BoundingBox;
import com.gs.ep.docknight.model.testutil.DocUtils;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import com.gs.ep.docknight.util.ImageUtils;
import com.gs.ep.docknight.util.OpenCvUtils;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.junit.Test;

public class PdfParserTest {

  private static void createAndAssertMiniPdfInsidePdfAsFormXObject(int textAngle, int top)
      throws Exception {
    // mini pdf
    PositionalDocDrawer drawer1 = new PositionalDocDrawer(new PDRectangle(841.9f, 595.3f));
    drawer1.setFont(PDType1Font.HELVETICA_BOLD, 30);
    drawer1.setTextAngle(textAngle);
    String message = "message";
    drawer1.drawTextAt(700, top, message);
    byte[] pdfDocument = drawer1.getPdfDocument();
    PDDocument pdDocument = PDDocument.load(pdfDocument);
    // CropBox becomes the bounding box of FormXObject
    pdDocument.getPage(0)
        .setCropBox(new PDRectangle(58.1f, 21.536f, 808.65f - 58.1f, 531.71f - 21.536f));

    //Landscape document with mini pdf
    PositionalDocDrawer drawer = new PositionalDocDrawer(new PDRectangle(841.9f, 595.3f));
    drawer.addFormXObject(pdDocument, 0);
    Document document = drawer.getDocument();
    MutableList<TextElement> elems = Lists.mutable
        .ofAll(document.getContainingElements(TextElement.class)).collect(te -> (TextElement) te);
    Verify.assertSize(1, elems);
    assertEquals(message, elems.get(0).getTextStr());
  }

  private static double getRenderedSize(Element textElement) throws Exception {
    double letterSpacing = textElement.getAttribute(LetterSpacing.class).getValue().getMagnitude();
    double fontSize = textElement.getAttribute(FontSize.class).getValue().getMagnitude();
    String text = textElement.getTextStr();
    return PDType1Font.TIMES_ROMAN.getStringWidth(text) * fontSize / 1000
        + letterSpacing * fontSize * text.length();
  }

  private static ComparableBufferedImage getTestPngImage() {
    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.GREEN);
    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    return new ComparableBufferedImage(image);
  }

  @Test
  public void testGetTextElementsFromJustifiedText() throws Exception {
    // Test when spurious space width is more than threshold
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer
        .drawTextAt(10, 10, "PURSUANT  TO  AN  EXEMPTION  FROM  THE  COMMODITY  FUTURES  TRADING");
    drawer.drawTextAt(10, 25,
        "COMMISSION   IN   CONNECTION   WITH      POOLS   WHOSE   PARTICIPANTS   ARE");
    drawer.drawTextAt(10, 40, "LIMITED TO QUALIFIED ELIGIBLE PERSONS, AN OFFERING MEMORANDUM FOR");
    drawer.drawTextAt(10, 55,
        "THIS POOL IS NOT REQUIRED TO BE, AND HAS     NOT BEEN, FILED WITH     THE");
    InputStream pdfDocumentStream = drawer.getPdfDocumentStream();
    Document document = new PdfParser().withDynamicSpaceWidthComputationEnabled(true)
        .parse(pdfDocumentStream);
    ElementIterable textElementsIterable = document.getContainingElements(TextElement.class);
    MutableList<String> textElementsStr = Iterate
        .collect(textElementsIterable, Element::getTextStr, Lists.mutable.empty());
    Verify.assertListsEqual(
        Lists.mutable.of("PURSUANT  TO  AN  EXEMPTION  FROM  THE  COMMODITY  FUTURES  TRADING",
            "COMMISSION   IN   CONNECTION   WITH      POOLS   WHOSE   PARTICIPANTS   ARE",
            "LIMITED TO QUALIFIED ELIGIBLE PERSONS, AN OFFERING MEMORANDUM FOR",
            "THIS POOL IS NOT REQUIRED TO BE, AND HAS",
            "NOT BEEN, FILED WITH",
            "THE"), textElementsStr);

    // Test when spurious space width is less than threshold
    drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer
        .drawTextAt(10, 10, "PURSUANT  TO  AN  EXEMPTION  FROM  THE  COMMODITY  FUTURES  TRADING");
    drawer.drawTextAt(10, 25,
        "COMMISSION   IN   CONNECTION   WITH      POOLS   WHOSE   PARTICIPANTS   ARE");
    drawer.drawTextAt(10, 40, "LIMITED TO QUALIFIED ELIGIBLE PERSONS, AN OFFERING MEMORANDUM FOR");
    drawer.drawTextAt(10, 55,
        "THIS POOL IS NOT REQUIRED TO BE, AND HAS   NOT BEEN, FILED WITH   THE");
    pdfDocumentStream = drawer.getPdfDocumentStream();
    document = new PdfParser().withDynamicSpaceWidthComputationEnabled(true)
        .parse(pdfDocumentStream);
    textElementsIterable = document.getContainingElements(TextElement.class);
    textElementsStr = Iterate
        .collect(textElementsIterable, Element::getTextStr, Lists.mutable.empty());

    Verify.assertListsEqual(
        Lists.mutable.of("PURSUANT  TO  AN  EXEMPTION  FROM  THE  COMMODITY  FUTURES  TRADING",
            "COMMISSION   IN   CONNECTION   WITH      POOLS   WHOSE   PARTICIPANTS   ARE",
            "LIMITED TO QUALIFIED ELIGIBLE PERSONS, AN OFFERING MEMORANDUM FOR",
            "THIS POOL IS NOT REQUIRED TO BE, AND HAS   NOT BEEN, FILED WITH   THE"),
        textElementsStr);
  }

  @Test
  public void testParse() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);

    // create scanned page almost fully filled with images and some annotated non renderable text
    drawer.drawImageAt(0, 12, new BufferedImage(612, 390, BufferedImage.TYPE_INT_RGB));
    drawer.drawImageAt(0, 402, new BufferedImage(612, 390, BufferedImage.TYPE_INT_RGB));

    drawer.setTextRenderable(RenderingMode.NEITHER);
    drawer.drawTextAt(10, 10, "annotated scanned text");

    // create scanned page with non readable images and some annotated non renderable text
    drawer.addPage();
    String one120Times = String.join("", Collections.nCopies(120, "1"));
    drawer.drawTextAt(10, 10, one120Times);

    // create normal page to with text, images, lines etc
    drawer.addPage();

    // annotate large text at beginning, which should not be recognized by parser
    drawer.drawTextAt(10, 10, String.join("", Collections.nCopies(1000, "5")));
    drawer.setTextRenderable(RenderingMode.FILL);

    PDFont font1 = PDType1Font.HELVETICA_BOLD;
    PDFont font2 = PDType1Font.COURIER_OBLIQUE;
    PDFont font3 = PDType1Font.TIMES_ROMAN;

    drawer.setFont(font1, 11);
    BoundingBox bold = drawer.drawTextAt(60, 92, "   This is bold text   ");
    // overlapping text should be ingnored by parser
    drawer.drawTextAt(60, 92, "   This is bold text   ");

    drawer.setFont(font2, 12);
    BoundingBox italic = drawer.drawTextAt(bold.getLeftBottomPlus(0, 20), "This is italic text   ");

    drawer.setFont(font3, 14);
    BoundingBox right = drawer
        .drawTextAt(410, italic.getBottom() + 20, "This is right aligned text");

    drawer.setFont(font3, 6);
    BoundingBox revenue = drawer
        .drawTextAt(300, right.getBottom() + 20, "    This year's revenue  :   1000 ");

    drawer.setFont(font3, 22);
    BoundingBox previous = drawer.drawTextAt(200, revenue.getBottom() + 20, "    Previous year's");
    BoundingBox revenue555 = drawer
        .drawTextAt(previous.getTopRightPlus(0, drawer.getWidthOfSpace()), "revenue  :     555 ");

    drawer.setFont(font3, 10);
    BoundingBox text12345 = drawer.drawTextAt(revenue555.getTopRightPlus(0, 0), "   12345");

    // tilted text not available in parsed output
    drawer.setTextAngle(45);
    drawer.drawTextAt(text12345.getLeftBottomPlus(0, 10), "tilted text");
    drawer.setTextAngle(0);

    // annotated non renderable text, not available in parsed output
    drawer.setTextRenderable(RenderingMode.NEITHER);
    drawer.drawTextAt(text12345.getLeftBottomPlus(0, 40), "invisible");
    drawer.setTextRenderable(RenderingMode.FILL);

    drawer.drawVerticalLineAt(100, 592, 100);
    drawer.drawHorizontalLineAt(100, 682, 100);

    ComparableBufferedImage testPngImage = getTestPngImage();
    drawer.drawImageAt(50, 741, testPngImage.getBufferedImage());

    drawer.drawTextField(10, 752, 100, 30);
    drawer.drawPushButton(150, 752, 100, 30, "PushButton");

    // add image only page
    drawer.addPage();
    drawer.drawImageAt(0, 12, new BufferedImage(612, 780, BufferedImage.TYPE_INT_RGB));

    Document document = drawer.getDocument();

    // parse scanned page
    Page scannedPage = (Page) document.getContent().getValue().getElements().get(0);
    assertEquals(792, scannedPage.getHeight().getValue().getMagnitude(), 0);
    assertEquals(612, scannedPage.getWidth().getValue().getMagnitude(), 0);

    List<Element> scannedElements = scannedPage.getPositionalContent().getValue().getElements();
    assertEquals(1, scannedElements.size());

    // no images parsed from scanned page, only annotated text
    TextElement scannedTextElement = (TextElement) scannedElements.get(0);
    assertEquals("annotated scanned text", scannedTextElement.getTextStr());
    assertEquals(10, scannedTextElement.getLeft().getValue().getMagnitude(), 0);
    assertEquals(10, scannedTextElement.getTop().getValue().getMagnitude(), 0.1);

    // parse scanned page without detectable images
    Page scannedPage2 = (Page) document.getContent().getValue().getElements().get(1);
    assertEquals(792, scannedPage2.getHeight().getValue().getMagnitude(), 0);
    assertEquals(612, scannedPage2.getWidth().getValue().getMagnitude(), 0);

    List<Element> scannedElements2 = scannedPage2.getPositionalContent().getValue().getElements();
    assertEquals(1, scannedElements2.size());

    // no images parsed from scanned page, only annotated text
    TextElement scannedTextElement2 = (TextElement) scannedElements2.get(0);
    assertEquals(one120Times, scannedTextElement2.getTextStr());
    assertEquals(10, scannedTextElement2.getLeft().getValue().getMagnitude(), 0);
    assertEquals(10, scannedTextElement2.getTop().getValue().getMagnitude(), 0.1);

    // parse normal page
    Page page = (Page) document.getContent().getValue().getElements().get(2);
    assertEquals(792, page.getHeight().getValue().getMagnitude(), 0);
    assertEquals(612, page.getWidth().getValue().getMagnitude(), 0);

    List<Element> elements = page.getPositionalContent().getValue().getElements();
    assertEquals(12, elements.size());

    TextElement textElement1 = (TextElement) elements.get(0);
    assertEquals(11, textElement1.getFontSize().getValue().getMagnitude(), 0);
    assertEquals("This is bold text", textElement1.getTextStr());
    assertEquals(bold.getLeft() + font1.getSpaceWidth() * 11 * 3 / 1000,
        textElement1.getLeft().getValue().getMagnitude(), 0.1);
    assertEquals(bold.getTop(), textElement1.getTop().getValue().getMagnitude(), 0.1);
    assertEquals(bold.getHeight(), textElement1.getHeight().getValue().getMagnitude(), 0.1);
    assertEquals(Lists.mutable.of(TextStyles.BOLD), textElement1.getTextStyles().getValue());

    TextElement textElement2 = (TextElement) elements.get(1);
    assertEquals(12, textElement2.getFontSize().getValue().getMagnitude(), 0);
    assertEquals("This is italic text", textElement2.getTextStr());
    assertEquals(italic.getLeft(), textElement2.getLeft().getValue().getMagnitude(), 0);
    assertEquals(italic.getTop(), textElement2.getTop().getValue().getMagnitude(), 0.1);
    assertEquals(italic.getHeight(), textElement2.getHeight().getValue().getMagnitude(), 0.1);
    assertEquals(Lists.mutable.of(TextStyles.ITALIC), textElement2.getTextStyles().getValue());

    TextElement textElement3 = (TextElement) elements.get(2);
    assertEquals(14, textElement3.getFontSize().getValue().getMagnitude(), 0);
    assertEquals("This is right aligned text", textElement3.getTextStr());
    assertEquals(right.getLeft(), textElement3.getLeft().getValue().getMagnitude(), 0);
    assertEquals(right.getTop(), textElement3.getTop().getValue().getMagnitude(), 0.1);
    assertEquals(right.getWidth(), textElement3.getWidth().getValue().getMagnitude(), 0.1);
    assertEquals(right.getHeight(), textElement3.getHeight().getValue().getMagnitude(), 0.1);
    assertNull(textElement3.getTextStyles());

    TextElement textElement4 = (TextElement) elements.get(3);
    assertEquals(6, textElement4.getFontSize().getValue().getMagnitude(), 0);
    assertEquals("This year's revenue  :", textElement4.getTextStr());

    TextElement textElement5 = (TextElement) elements.get(4);
    assertEquals(6, textElement5.getFontSize().getValue().getMagnitude(), 0);
    assertEquals("1000", textElement5.getTextStr());

    TextElement textElement6 = (TextElement) elements.get(5);
    assertEquals(22, textElement6.getFontSize().getValue().getMagnitude(), 0);
    assertEquals("Previous year's revenue  :", textElement6.getTextStr());

    TextElement textElement7 = (TextElement) elements.get(6);
    assertEquals(22, textElement7.getFontSize().getValue().getMagnitude(), 0);
    assertEquals("555", textElement7.getTextStr());

    TextElement textElement8 = (TextElement) elements.get(7);
    assertEquals(10, textElement8.getFontSize().getValue().getMagnitude(), 0);
    assertEquals("12345", textElement8.getTextStr());

    VerticalLine verticalLine = (VerticalLine) elements.get(8);
    assertEquals(100, verticalLine.getStretch().getValue().getMagnitude(), 0);
    assertEquals(100, verticalLine.getLeft().getValue().getMagnitude(), 0);
    assertEquals(592, verticalLine.getTop().getValue().getMagnitude(), 0);

    HorizontalLine horizontalLine = (HorizontalLine) elements.get(9);
    assertEquals(100, horizontalLine.getStretch().getValue().getMagnitude(), 0);
    assertEquals(100, horizontalLine.getLeft().getValue().getMagnitude(), 0);
    assertEquals(682, horizontalLine.getTop().getValue().getMagnitude(), 0);

    Image image = (Image) elements.get(10);
    assertEquals(50, image.getLeft().getValue().getMagnitude(), 0);
    assertEquals(741, image.getTop().getValue().getMagnitude(), 0);
    assertEquals(testPngImage, image.getImageData().getValue());
    assertEquals(testPngImage.getBufferedImage().getWidth(),
        image.getWidth().getValue().getMagnitude(), 0);
    assertEquals(testPngImage.getBufferedImage().getHeight(),
        image.getHeight().getValue().getMagnitude(), 0);

    FormElement formElement = (FormElement) elements.get(11);
    assertEquals(10, formElement.getLeft().getValue().getMagnitude(), 0);
    assertEquals(752, formElement.getTop().getValue().getMagnitude(), 0);
    assertEquals(100, formElement.getWidth().getValue().getMagnitude(), 0);
    assertEquals(30, formElement.getHeight().getValue().getMagnitude(), 0);
    assertEquals(12, formElement.getFontSize().getValue().getMagnitude(), 0);
    Form form = formElement.getFormData().getValue();
    assertEquals(FormType.TextField, form.getFormType());
    assertEquals("", form.getValue());

    // image only page should retrieve no elements
    Page imageOnlyPage = (Page) document.getContent().getValue().getElements().get(3);
    assertTrue(imageOnlyPage.getPositionalContent().getElementList().isEmpty());
  }

  @Test
  public void testRotatedPdf() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);

    drawer.drawVerticalLineAt(100, 592, 100);

    ComparableBufferedImage testPngImage = getTestPngImage();
    drawer.drawImageAt(50, 741, testPngImage.getBufferedImage());

    drawer.drawTextField(10, 752, 100, 30);
    drawer.rotatePage(90);

    Document document = drawer.getDocument();

    Page page = (Page) document.getContent().getValue().getElements().get(0);
    assertEquals(612, page.getHeight().getValue().getMagnitude(), 0);
    assertEquals(792, page.getWidth().getValue().getMagnitude(), 0);

    List<Element> elements = page.getPositionalContent().getValue().getElements();
    assertEquals(3, elements.size());

    FormElement formElement = (FormElement) elements.get(0);
    assertEquals(40, formElement.getLeft().getValue().getMagnitude(), 0);
    assertEquals(10, formElement.getTop().getValue().getMagnitude(), 0);
    assertEquals(100, formElement.getWidth().getValue().getMagnitude(), 0);
    assertEquals(30, formElement.getHeight().getValue().getMagnitude(), 0);
    assertEquals(12, formElement.getFontSize().getValue().getMagnitude(), 0);
    Form form = formElement.getFormData().getValue();
    assertEquals(FormType.TextField, form.getFormType());
    assertEquals("", form.getValue());

    Image image = (Image) elements.get(1);
    assertEquals(41, image.getLeft().getValue().getMagnitude(), 0);
    assertEquals(40, image.getTop().getValue().getMagnitude(), 0);
    assertEquals(testPngImage, image.getImageData().getValue());
    assertEquals(testPngImage.getBufferedImage().getWidth(),
        image.getWidth().getValue().getMagnitude(), 0);
    assertEquals(testPngImage.getBufferedImage().getHeight(),
        image.getHeight().getValue().getMagnitude(), 0);

    HorizontalLine horizontalLine = (HorizontalLine) elements.get(2);
    assertEquals(100, horizontalLine.getStretch().getValue().getMagnitude(), 0);
    assertEquals(100, horizontalLine.getLeft().getValue().getMagnitude(), 0);
    assertEquals(100, horizontalLine.getTop().getValue().getMagnitude(), 0);
  }

  @Test
  public void testRawScannedPdf() throws Exception {
    String filePath = Thread.currentThread().getContextClassLoader()
        .getResource("parser/ScannedTest.pdf").getPath();
    Verify.assertThrows(UnDigitizedPdfException.class,
        () -> new PdfParser().parse(new FileInputStream(new File(filePath))));

    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawTextAt(0, 0, "A");
    drawer.setTextRenderable(RenderingMode.NEITHER);
    drawer.drawTextAt(100, 100, "B");
    Document document = new PdfParser().withScanned(true).parse(drawer.getPdfDocumentStream());
    assertEquals("B", document.getTextStr());
  }

  @Test
  public void testSubAndSuperTextAndLineChange() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);

    BoundingBox a = drawer.drawTextAt(100, 100, "A");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 6);
    BoundingBox two = drawer.drawTextAt(a.getRightBottomPlus(0, h -> -h / 4), "2");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    BoundingBox o = drawer.drawTextAt(two.getRight(), a.getTop(), "O");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 6);
    BoundingBox five = drawer.drawTextAtTop(o.getTopRightPlus(h -> h / 4, 0), "5");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    BoundingBox x = drawer.drawTextAt(five.getRight(), a.getTop(), "X");
    drawer.drawTextAt(x.getRightBottomPlus(2, 5), "NaCl");

    Document document = drawer.getDocument();
    Page page = (Page) document.getContent().getValue().getElements().get(0);
    List<Element> elements = page.getPositionalContent().getValue().getElements();

    assertEquals(2, elements.size());
    assertEquals("A2O5X", elements.get(0).getTextStr());
    assertEquals("NaCl", elements.get(1).getTextStr());
  }

  @Test
  public void testThickLines() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawHorizontalLineAt(100, 100, 100);
    drawer.drawHorizontalLineAt(99, 101, 100);
    drawer.drawVerticalLineAt(200, 200, 100);
    drawer.drawVerticalLineAt(201, 199, 100);

    Document document = drawer.getDocument();
    Page page = (Page) document.getContent().getValue().getElements().get(0);
    List<Element> elements = page.getPositionalContent().getValue().getElements();

    assertEquals(2, elements.size());
    HorizontalLine horizontalLine = (HorizontalLine) elements.get(0);
    assertEquals(101, horizontalLine.getStretch().getValue().getMagnitude(), 0);
    assertEquals(99, horizontalLine.getLeft().getValue().getMagnitude(), 0);
    assertEquals(100, horizontalLine.getTop().getValue().getMagnitude(), 1);

    VerticalLine verticalLine = (VerticalLine) elements.get(1);
    assertEquals(101, verticalLine.getStretch().getValue().getMagnitude(), 0);
    assertEquals(200, verticalLine.getLeft().getValue().getMagnitude(), 1);
    assertEquals(199, verticalLine.getTop().getValue().getMagnitude(), 0);
  }

  @Test
  public void testFilledPolygonsToBeInterpretedAsLines() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setWriteAndFillColor(Color.GREEN);
    drawer.drawRectangleAt(100, 50, 100, 1, RenderingMode.FILL);
    drawer.drawRectangleAt(100, 150, 1, 100, RenderingMode.FILL);
    Document document = drawer.getDocument();
    Page page = (Page) document.getContent().getValue().getElements().get(0);
    List<Element> elements = page.getPositionalContent().getValue().getElements();

    assertEquals(2, elements.size());
    HorizontalLine horizontalLine = (HorizontalLine) elements.get(0);
    assertEquals(100, horizontalLine.getStretch().getValue().getMagnitude(), 0);
    assertEquals(100, horizontalLine.getLeft().getValue().getMagnitude(), 0);
    assertEquals(50, horizontalLine.getTop().getValue().getMagnitude(), 1);

    VerticalLine verticalLine = (VerticalLine) elements.get(1);
    assertEquals(100, verticalLine.getStretch().getValue().getMagnitude(), 0);
    assertEquals(100, verticalLine.getLeft().getValue().getMagnitude(), 1);
    assertEquals(150, verticalLine.getTop().getValue().getMagnitude(), 0);

    drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setWriteAndFillColor(Color.GREEN);
    drawer.drawRectangleAt(100, 50, 100, 2, RenderingMode.FILL);
    drawer.drawRectangleAt(100, 150, 2, 100, RenderingMode.FILL);
    document = drawer.getDocument();
    page = (Page) document.getContent().getValue().getElements().get(0);
    elements = page.getPositionalContent().getValue().getElements();

    assertEquals(4, elements.size());

    document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest4.pdf"));
    assertTrue(
        document.getContent().getElementList().getFirst().getAttribute(PageColor.class).getValue()
            .isEmpty());
  }

  @Test
  public void testColorsAndVisibility() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);

    drawer.setWriteAndFillColor(Color.WHITE);
    drawer.drawTextAt(110, 100, "Invisible text because it is white on white");

    drawer.setWriteAndFillColor(Color.BLUE);
    drawer.drawRectangleAt(100, 300, 300, 50, RenderingMode.FILL_STROKE);
    drawer.drawTextAt(110, 310, "Invisible text because it is blue on blue");

    // very small colored area, must be ignored, both in terms of lines and page color
    drawer.drawRectangleAt(100, 370, 2, 2, RenderingMode.FILL_STROKE);

    drawer.setWriteAndFillColor(Color.GREEN);
    drawer.drawRectangleAt(100, 400, 300, 50, RenderingMode.FILL);
    drawer.setWriteAndFillColor(Color.WHITE);
    drawer.drawTextAt(110, 410, "Visible text because it is white on green");

    drawer.setWriteAndFillColor(Color.BLACK);
    drawer.drawTextAt(110, 600, "Visible text because it is black on white");

    Document document = drawer.getDocument();
    Page page = (Page) document.getContent().getValue().getElements().get(0);
    List<Element> elements = page.getPositionalContent().getValue().getElements();
    List<Element> textElems = ListIterate.select(elements, e -> e instanceof TextElement);

    assertEquals(2, textElems.size());
    TextElement textElement1 = (TextElement) textElems.get(0);
    assertEquals("Visible text because it is white on green", textElement1.getTextStr());
    assertEquals(Color.WHITE, textElement1.getColor().getValue());
    assertEquals("Visible text because it is black on white", textElems.get(1).getTextStr());

    List<Pair<Rectangle, Integer>> coloredAreas = page.getPageColor().getValue();
    assertEquals(2, coloredAreas.size());
    assertEquals(new Rectangle(100, 300, 300, 50), coloredAreas.get(0).getOne());
    assertEquals(Color.BLUE, new Color(coloredAreas.get(0).getTwo()));
    assertEquals(new Rectangle(100, 400, 300, 50), coloredAreas.get(1).getOne());
    assertEquals(Color.GREEN, new Color(coloredAreas.get(1).getTwo()));

    List<Element> lines = ListIterate.select(elements, e -> e instanceof GraphicalElement);
    assertEquals(4, lines.size());
  }

  @Test
  public void testBadColorStream() throws Exception {
    Document document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "ColorTest1.pdf"));
    assertTrue(document.getContent().getElements().get(0)
        .getAttributeValue(PageColor.class, Lists.mutable.empty()).isEmpty());
  }

  @Test
  public void testNonRectangularShapes() throws Exception {
    PDDocument pdfDocument = new PDDocument();
    PDPage pdfPage = new PDPage(PDRectangle.LETTER);
    pdfDocument.addPage(pdfPage);
    PDPageContentStream contentStream = new PDPageContentStream(pdfDocument, pdfPage);
    contentStream.setNonStrokingColor(Color.BLUE);
    contentStream.moveTo(100, 100);
    contentStream.lineTo(200, 115);
    contentStream.lineTo(115, 200);
    contentStream.lineTo(100, 100);
    contentStream.fill();
    contentStream.close();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    pdfDocument.save(byteArrayOutputStream);
    Document document = new PdfParser()
        .parse(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));

    Page page = (Page) document.getContent().getValue().getElements().get(0);
    List<Class> elementClasses = ListIterate
        .collect(page.getPositionalContent().getValue().getElements(), Object::getClass);
    assertEquals(Lists.mutable.of(VerticalLine.class, HorizontalLine.class), elementClasses);
    assertTrue(
        document.getContent().getElementList().getFirst().getAttribute(PageColor.class).getValue()
            .isEmpty());
  }

  @Test
  public void testCollapsedSpaces() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    BoundingBox foo = drawer.drawTextAt(10, 10, "Foo");
    BoundingBox space1 = drawer.drawTextAt(foo.getTopRightPlus(0, 0), " ");
    drawer.drawTextAt(space1.getTopRightPlus(0, w -> -w / 2), " ");
    BoundingBox space3 = drawer.drawTextAt(space1.getTopRightPlus(0, 0), " ");
    BoundingBox bar = drawer.drawTextAt(space3.getTopRightPlus(0, 0), "Bar");
    BoundingBox lol = drawer
        .drawTextAt(bar.getTopRightPlus(0, drawer.getWidthOfSpace() * 3 / 2), "Lol");
    BoundingBox space4 = drawer.drawTextAt(lol.getTopRightPlus(0, 0), " ");
    drawer.drawTextAt(space4.getTopRightPlus(0, -drawer.getWidthOfSpace() * 0.6), "Hi");
    List<String> textElements = Lists.mutable
        .ofAll(drawer.getDocument().getContainingElements(TextElement.class))
        .collect(Element::getTextStr);
    assertEquals(Lists.mutable.of("Foo  Bar", "Lol Hi"), textElements);
  }

  @Test
  public void testFontRecognition() throws Exception {
    Document document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest1.pdf"));
    assertNotNull(
        document.getContainingElements(e -> e.getTextStr().equals("Income Statement")).iterator()
            .next());

    document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest2.pdf"));
    Element textElement = document.getContainingElements(TextElement.class).iterator().next();
    assertTrue(textElement.getAttribute(LetterSpacing.class).getValue().getMagnitude() < 0);
    assertEquals(textElement.getAttribute(Width.class).getValue().getMagnitude(),
        getRenderedSize(textElement), 5);

    document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest3.pdf"));
    textElement = document.getContainingElements(TextElement.class).iterator().next();
    assertTrue(textElement.getAttribute(LetterSpacing.class).getValue().getMagnitude() > 0);
    //TODO: kushwv - Uncomment this when we can find a way to increase font safely
    //assertEquals(textElement.getAttribute(Width.class).getValue().getMagnitude(), getRenderedSize(textElement), 5);

    document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest4.pdf"));
    assertEquals("Address:",
        document.getContainingElements(TextElement.class).iterator().next().getTextStr());

    document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest5.pdf"));
    textElement = document.getContainingElements(
        e -> e instanceof TextElement && e.getTextStr().contains("ASBE ASSOCIATED")).iterator()
        .next();
    assertTrue(textElement.getAttribute(Height.class).getValue().getMagnitude() > 0);
    assertEquals("ASBE ASSOCIATED BRITISH ENGINEERING PLC ORD 2.5P", textElement.getTextStr());

    // test for zero width
    document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest6.pdf"));
    assertNotNull(
        document.getContainingElements(e -> e.getTextStr().equals("W")).iterator().next());

    // test for bold fonts without bold in their name
    Document boldFontDoc = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest7.pdf"));
    List<Element> textElements = Lists.mutable
        .ofAll(boldFontDoc.getContainingElements(TextElement.class));
    assertFalse(textElements.get(0).hasAttribute(TextStyles.class));
    assertTrue(textElements.get(1).getAttributeValue(TextStyles.class, Lists.mutable.empty())
        .contains(TextStyles.BOLD));

    document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest8.pdf"));
    assertEquals(5, Lists.mutable.ofAll(document.getContainingElements(TextElement.class)).size());

    // test unicode normalization
    document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest9.pdf"));
    assertEquals("Offer for officer", document.getTextStr());

    // testing type 0 composite font where isLastCharWhiteSpace is true
    document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest10.pdf"));
    textElements = Lists.mutable.ofAll(document.getContainingElements(TextElement.class))
        .select(element -> element.getTextStr().contains("in Russia"));
    assertEquals(2, textElements.size());
    assertEquals("Investments in Russia", textElements.get(0).getTextStr());
    assertEquals("in Russia", textElements.get(1).getTextStr());

    // Empty cmap subtable array - throws IndexArrayOutOfBoundException in pdfbox 2.0.0. Fixed in 2.0.19
    document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FontTest11.pdf"));
    textElements = Lists.mutable.ofAll(document.getContainingElements(TextElement.class))
        .select(element -> element.getTextStr().contains("Status"));
    assertEquals(1, textElements.size());
    assertEquals("Status", textElements.get(0).getTextStr());
  }

  @Test
  public void testFormElements() throws Exception {
    Document document = new PdfParser()
        .parse(DocUtils.getInputStreamForResourceFile("parser", "FormsWithRadio.pdf"));
    MutableList<Element> formElements = Lists.mutable
        .ofAll(document.getContainingElements(FormElement.class));
    assertEquals(24, formElements.size());
    assertEquals(3, formElements.count(e -> e.getAttribute(FormData.class).getValue().isChecked()));
    assertTrue(formElements.get(1).getAttribute(FormData.class).getValue().isChecked());
    assertTrue(formElements.get(2).getAttribute(FormData.class).getValue().isChecked());
    assertTrue(formElements.get(5).getAttribute(FormData.class).getValue().isChecked());
  }

  @Test
  public void testSpaceCompression() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 50);

    //widthofSpace = 12.5
    //chars separated by distance > ALLOWED_SPACE_COMPRESSION_FACTOR * widthOfSpace ,
    BoundingBox text1 = drawer.drawTextAt(10, 10, "A ");
    drawer.drawTextAt(text1.getRight() - 2, 10, "B");

    //chars separated by distance < ALLOWED_SPACE_COMPRESSION_FACTOR * widthOfSpace
    BoundingBox text2a = drawer.drawTextAt(10, 50, "A");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    BoundingBox text2b = drawer.drawTextAt(text2a.getRight(), 50, " "); // widthofSpace = 3
    drawer.setFont(PDType1Font.TIMES_ROMAN, 50);
    drawer.drawTextAt(text2b.getRight() + .5, 40, "B");

    Document document = drawer.getDocument();
    assertEquals(Lists.mutable.of("A B", "AB"),
        Lists.mutable.ofAll(document.getContainingElements(TextElement.class))
            .collect(Element::getTextStr));
  }

  @Test
  public void testContentOutsidePdf() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);

    // stuff outside page
    drawer.drawTextAt(-100, 100, "a");
    drawer.drawTextAt(100, -100, "b");
    drawer.drawTextAt(PDRectangle.LETTER.getWidth() + 100, 100, "c");
    drawer.drawTextAt(100, PDRectangle.LETTER.getHeight() + 100, "d");
    drawer.drawHorizontalLineAt(-100, 100, 50);
    drawer.drawHorizontalLineAt(100, -100, 50);
    drawer.drawHorizontalLineAt(PDRectangle.LETTER.getWidth() + 100, 100, 50);
    drawer.drawHorizontalLineAt(100, PDRectangle.LETTER.getHeight() + 100, 50);
    drawer.drawVerticalLineAt(-100, 100, 50);
    drawer.drawVerticalLineAt(100, -100, 50);
    drawer.drawVerticalLineAt(PDRectangle.LETTER.getWidth() + 100, 100, 50);
    drawer.drawVerticalLineAt(100, PDRectangle.LETTER.getHeight() + 100, 50);

    // stuff inside page
    drawer.drawTextAt(100, 100, "i");
    drawer.drawHorizontalLineAt(100, 100, 50);
    drawer.drawVerticalLineAt(100, 100, 50);

    Document document = drawer.getDocument();
    assertEquals(3, Iterate.sizeOf(document.getContainingElements(element -> element.isTerminal()
        && element.getAttribute(Left.class).getMagnitude() > 0
        && element.getAttribute(Top.class).getMagnitude() > 0)));
  }

  @Test
  public void testTextRotatedPdf() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setTextAngle(90);
    drawer.drawTextAt(100, 100, "Flipped");
    drawer.setTextAngle(0);
    drawer.drawTextAt(400, 400, "X");
    Document document = drawer.getDocument();
    assertEquals("Flipped", document.getTextStr());
    Page page = (Page) Iterate.getFirst(document.getContainingElements(Page.class));
    assertEquals(PDRectangle.LETTER.getHeight(), page.getWidth().getMagnitude(), 0.1);
    assertEquals(PDRectangle.LETTER.getWidth(), page.getHeight().getMagnitude(), 0.1);
  }

  @Test
  public void testTextWatermark() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawTextAt(100, 100, "Text1");
    drawer.setWriteAndFillColor(new Color(193, 193, 193)); //Lighter Gray than Color.LIGHT_GRAY
    drawer.drawTextAt(100, 100, "Watermark");
    drawer.drawTextAt(100, 300, "Text2");
    drawer.setWriteAndFillColor(Color.RED);
    drawer.drawRectangleAt(100, 300, 200, 100, RenderingMode.FILL);
    assertEquals("Text1", drawer.getDocument().getTextStr());
  }

  @Test
  public void testWhiteWashedText() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawTextAt(100, 100, "Washed");
    drawer.setWriteAndFillColor(Color.WHITE);
    drawer.drawRectangleAt(100, 100, 200, 100, RenderingMode.FILL);
    drawer.setWriteAndFillColor(Color.BLACK);
    drawer.drawTextAt(100, 100, "Text");

    Document document = drawer.getDocument();
    assertTrue(document.getContent().getElements().get(0).getAttribute(PageColor.class).getValue()
        .isEmpty());
    assertEquals("Text", document.getTextStr());

    drawer.setWriteAndFillColor(Color.GREEN);
    drawer.drawRectangleAt(100, 100, 200, 100, RenderingMode.FILL);
    drawer.setWriteAndFillColor(Color.BLACK);
    drawer.drawTextAt(100, 100, "New");

    document = drawer.getDocument();
    assertEquals(1,
        document.getContent().getElements().get(0).getAttribute(PageColor.class).getValue().size());
    assertEquals("New", document.getTextStr());
  }

  @Test
  public void testImagePunctuationInNumericStrings() throws Exception {
    BufferedImage dot = ImageUtils.resizeImage(ImageUtils.parseBase64PngBinary(
        "iVBORw0KGgoAAAANSUhEUgAAABAAAAARCAIAAABbzbuTAAAAG0lEQVR42mP4TyJgGEIaGJDAUNUwHOJhOGsAACdC/R+MWTkeAAAAAElFTkSuQmCC"),
        0.3);
    BufferedImage dash = ImageUtils.resizeImage(ImageUtils.parseBase64PngBinary(
        "iVBORw0KGgoAAAANSUhEUgAAABAAAAAZCAIAAAC3njn+AAAAH0lEQVR42mP4TyJgGJ4aGHCDgdIwIuNhVMOoBrI0AAAmI1reSwmbTAAAAABJRU5ErkJggg=="),
        0.3);
    BufferedImage comma = ImageUtils.resizeImage(ImageUtils.parseBase64PngBinary(
        "iVBORw0KGgoAAAANSUhEUgAAABAAAAAUCAIAAAALACogAAAAJklEQVR42mP4TyJgGNVAiQYGJDBUNSBro5kGTNVE+YEEDcM28QEAKa9uvKtu2NYAAAAASUVORK5CYII="),
        0.3);
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);

    // numerical text with image based punctuation
    BoundingBox box = drawer.drawImageAt(100, 100, dash);
    box = drawer.drawTextAt(box.getRight(), 96, "123");
    box = drawer.drawImageAt(box.getRight(), 100, comma);
    box = drawer.drawTextAt(box.getRight(), 96, "45");
    box = drawer.drawImageAt(box.getRight(), 101, dot);
    drawer.drawTextAt(box.getRight(), 96, "67");

    // alphabetic text with image punctuation (ignored)
    box = drawer.drawImageAt(100, 300, dash);
    drawer.drawTextAt(box.getRight(), 296, "abcde");

    // images far from text (ignored)
    drawer.drawImageAt(100, 500, dash);
    drawer.drawTextAt(400, 496, "999");

    assertEquals("-123 ,45 .67\nabcde\n999", drawer.getDocument().getTextStr());
  }

  @Test
  public void testLuminosity() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawTextAt(100, 100, "X");
    drawer.setWriteAndFillColor(Color.GREEN);
    drawer.drawRectangleAt(100, 200, 50, 50, RenderingMode.FILL);
    drawer.setLuminosity(true);  // following colored areas will not be exported
    drawer.drawRectangleAt(100, 300, 50, 50, RenderingMode.FILL);
    assertEquals(1,
        drawer.getDocument().getContent().getElements().get(0).getAttribute(PageColor.class)
            .getValue().size());
  }

  @Test
  public void testNullLigatures() throws Exception {
    PDDocument pdfDocument = new PDDocument();
    PDPage pdfPage = new PDPage(PDRectangle.LETTER);
    pdfDocument.addPage(pdfPage);
    PDPageContentStream contentStream = new PDPageContentStream(pdfDocument, pdfPage);
    contentStream.beginText();
    contentStream.newLineAtOffset(100, 500);
    contentStream.setFont(new PDType1Font(PDType1Font.TIMES_ROMAN.getCOSObject()) {
      @Override
      protected byte[] encode(int unicode) throws IOException {
        return unicode == 0 ? new byte[]{0} : super.encode(unicode);
      }
    }, 12);
    contentStream.showText("My o\0ce has a O\0cer.");
    contentStream.close();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    pdfDocument.save(byteArrayOutputStream);
    Document document = new PdfParser()
        .parse(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
    assertEquals("My office has a Officer.", document.getTextStr());
  }

  @Test
  public void testHomoglyphsNormalization() throws Exception {
    String homoglyphsText = "Punctuations on the keyboard with homoglyphs include \u00B4 \u02C6 \u201C \u201D \u0060";
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawTextAt(100, 100, homoglyphsText);
    assertEquals("Punctuations on the keyboard with homoglyphs include ' ^ \" \" '",
        drawer.getDocument().getTextStr());
  }

  @Test
  public void testImageBasedFormElements() throws Exception {
    String selectedImage = Thread.currentThread().getContextClassLoader()
        .getResource("formImages/1_selectedCheckbox.png").getPath();
    String emptyImage = Thread.currentThread().getContextClassLoader()
        .getResource("formImages/4_emptyRadio.png").getPath();
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);

    drawer.drawTextAt(100, 60, "DocKnight  Text  Extraction");
    drawer.drawImageAt(20, 12, ImageIO.read(new File(selectedImage)));
    drawer.drawImageAt(20, 40, ImageIO.read(new File(emptyImage)));
    drawer.drawImageAt(150, 100, new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB));

    Document document = drawer.getDocument();
    Page page1 = (Page) document.getContent().getValue().getElements().get(0);
    List<Element> elements = page1.getPositionalContent().getValue().getElements();

    assertEquals(4, elements.size());
    if (OpenCvUtils.isLibraryLoaded()) {
      assertTrue(elements.get(0) instanceof Image);
      assertTrue(elements.get(1) instanceof Image);

      assertEquals(1, ((Image) elements.get(0)).getAlternateRepresentations().getValue().size());
      assertTrue(((Image) elements.get(0)).getAlternateRepresentations().getValue()
          .get(0) instanceof FormElement);
      assertEquals(1, ((Image) elements.get(1)).getAlternateRepresentations().getValue().size());
      assertTrue(((Image) elements.get(1)).getAlternateRepresentations().getValue()
          .get(0) instanceof FormElement);

      FormElement selectedRadioButton = (FormElement) ((Image) elements.get(0))
          .getAlternateRepresentations().getValue().get(0);
      assertEquals(12, selectedRadioButton.getTop().getValue().getMagnitude(), 0);
      assertEquals(20, selectedRadioButton.getLeft().getValue().getMagnitude(), 0);
      assertEquals(FormType.RadioButton,
          selectedRadioButton.getFormData().getValue().getFormType());
      assertEquals("yes", selectedRadioButton.getFormData().getValue().getValuesString());

      FormElement emptyRadioButton = (FormElement) ((Image) elements.get(1))
          .getAlternateRepresentations().getValue().get(0);
      assertEquals(40, emptyRadioButton.getTop().getValue().getMagnitude(), 0);
      assertEquals(20, emptyRadioButton.getLeft().getValue().getMagnitude(), 0);
      assertEquals(FormType.RadioButton, emptyRadioButton.getFormData().getValue().getFormType());
      assertEquals("no", emptyRadioButton.getFormData().getValue().getValuesString());
    }
    assertTrue(elements.get(2) instanceof TextElement);
    assertTrue(elements.get(3) instanceof Image);
  }

  @Test
  public void testTextElementBreakageDueToVerticalLines() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.drawTextAt(100, 100, "DocKnight  Text  Extraction");

    Document documentWithoutVerticalLine = drawer.getDocument();
    Page page1 = (Page) documentWithoutVerticalLine.getContent().getValue().getElements().get(0);
    List<Element> elements = page1.getPositionalContent().getValue().getElements();
    assertEquals(1, elements.size());

    drawer.drawVerticalLineAt(150, 80, 50);

    Document documentWithVerticalLine = drawer.getDocument();
    page1 = (Page) documentWithVerticalLine.getContent().getValue().getElements().get(0);
    elements = page1.getPositionalContent().getValue().getElements();
    assertEquals(2, elements.size());
  }

  @Test
  public void testRenderingModeFillStroke() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 10);
    drawer.drawTextAt(100, 100, "Should not be Bold.");
    drawer.setTextRenderable(RenderingMode.FILL_STROKE);
    drawer.drawTextAt(100, 200, "Should be Bold.");
    drawer.setTextRenderable(RenderingMode.FILL);
    drawer.drawTextAt(100, 300, "Should not be Bold.");

    Document document = drawer.getDocument();
    Page page1 = (Page) document.getContent().getValue().getElements().get(0);
    List<Element> elements = page1.getPositionalContent().getValue().getElements();
    assertEquals(3, elements.size());

    elements.forEach(elem -> assertTrue(elem instanceof TextElement));
    assertNull(((TextElement) elements.get(0)).getTextStyles());
    assertEquals(Lists.mutable.of(TextStyles.BOLD),
        ((TextElement) elements.get(1)).getTextStyles().getValue());
    assertNull(((TextElement) elements.get(0)).getTextStyles());
  }

  @Test
  public void testSegmentationRatio() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 5);

    drawer.drawTextAt(10, 10, "TE1");
    drawer.drawTextAt(60, 10, "TE2");
    drawer.drawTextAt(110, 10, "TE3");
    drawer.drawTextAt(160, 10, "TE4");
    assertEquals(4.0, PdfParser.getTextSegmentationRatio(drawer.getDocument()), 0.0);

    drawer.drawTextAt(10, 20, "TE5");
    drawer.drawTextAt(60, 20, "TE6");
    drawer.drawTextAt(110, 20, "TE7");
    assertEquals(3.5, PdfParser.getTextSegmentationRatio(drawer.getDocument()), 0.0);

    drawer.drawTextAt(10, 30, "TE8");
    drawer.drawTextAt(60, 30, "TE9");
    assertEquals(3, PdfParser.getTextSegmentationRatio(drawer.getDocument()), 0.0);
  }

  @Test
  public void testIterativePdfParser() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 5);

    drawer.drawTextAt(10, 10, "TE01");
    drawer.drawTextAt(24, 10, "TE02");
    drawer.drawTextAt(38, 10, "TE03");
    drawer.drawTextAt(52, 10, "TE04");

    drawer.drawTextAt(10, 20, "TE05");
    drawer.drawTextAt(24, 20, "TE06");
    drawer.drawTextAt(38, 20, "TE07");
    drawer.drawTextAt(52, 20, "TE08");
    drawer.drawTextAt(66, 20, "TE09");

    drawer.drawTextAt(10, 30, "TE10");
    drawer.drawTextAt(24, 30, "TE11");
    drawer.drawTextAt(38, 30, "TE12");
    drawer.drawTextAt(52, 30, "TE13");
    drawer.drawTextAt(66, 30, "TE14");
    drawer.drawTextAt(80, 30, "TE15");

    PdfParser pdfParser = new PdfParser().withMaxTextElementToLineCountRatio(3.5)
        .withSpacingFactor(-1);
    Document document = pdfParser.parse(drawer.getPdfDocumentStream());
    assertNotNull(document);
    Verify.assertListsEqual(
        Lists.mutable.of("TE01  TE02  TE03  TE04", "TE05  TE06  TE07  TE08  TE09",
            "TE10  TE11  TE12  TE13  TE14  TE15"),
        Lists.mutable.ofAll(document.getContainingElements(TextElement.class))
            .collect(Element::getTextStr)
    );
  }

  @Test
  public void testNonIterativePdfParser() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 5);

    drawer.drawTextAt(10, 10, "TE01");
    drawer.drawTextAt(24, 10, "TE02");
    drawer.drawTextAt(38, 10, "TE03");
    drawer.drawTextAt(52, 10, "TE04");

    drawer.drawTextAt(10, 20, "TE05");
    drawer.drawTextAt(24, 20, "TE06");
    drawer.drawTextAt(38, 20, "TE07");
    drawer.drawTextAt(52, 20, "TE08");
    drawer.drawTextAt(66, 20, "TE09");

    drawer.drawTextAt(10, 30, "TE10");
    drawer.drawTextAt(24, 30, "TE11");
    drawer.drawTextAt(38, 30, "TE12");
    drawer.drawTextAt(52, 30, "TE13");
    drawer.drawTextAt(66, 30, "TE14");
    drawer.drawTextAt(80, 30, "TE15");

    PdfParser pdfParser = new PdfParser();
    Document document = pdfParser.parse(drawer.getPdfDocumentStream());
    assertNotNull(document);
    Verify.assertListsEqual(
        Lists.mutable
            .of("TE01", "TE02", "TE03", "TE04", "TE05", "TE06", "TE07", "TE08", "TE09", "TE10",
                "TE11", "TE12", "TE13", "TE14", "TE15"),
        Lists.mutable.ofAll(document.getContainingElements(TextElement.class))
            .collect(Element::getTextStr)
    );
  }

  @Test
  public void testSpacingScaledWithMoreThanTenExtraWords() throws Exception {
    double customizedWidthOfSpace = 1.0;
    double lastCharacterXPos = 0.0;

    //This example illustrates the scenario in which out of vocab words with hard spacing constraint splits
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    lastCharacterXPos = drawer.drawTextAt(20, 20, "This").getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "text")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "is")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "to")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "test")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "the")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "page")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "level")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "spacing")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "scaling")
        .getRight();
    lastCharacterXPos = drawer
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "customization").getRight();
    lastCharacterXPos = drawer
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "on a document. ").getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "The")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "number")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20,
        "of vocab words if spacing scaled").getRight();
    lastCharacterXPos = drawer
        .drawTextAt(20, 40, "should be greater than 10 when compared with hard spacing " +
            "for customization to get activated.").getRight();

    PdfParser pdfParserWithoutCustomization = new PdfParser();
    PdfParser pdfParserWithCustomization = new PdfParser().withPageLevelSpacingScaling(true);

    Document documentWithoutCustomization = pdfParserWithoutCustomization
        .parse(drawer.getPdfDocumentStream());
    MutableList<TextElement> segmentsWithoutCustomization = Lists.mutable
        .ofAll(documentWithoutCustomization.getContainingElements(TextElement.class))
        .selectInstancesOf(TextElement.class);

    assertEquals("Thistextistotestthepagelevelspacingscalingcustomizationon a document. " +
            "Thenumberof vocab words if spacing scaled",
        segmentsWithoutCustomization.get(0).getTextStr());
    assertEquals(
        "should be greater than 10 when compared with hard spacing for customization to get activated.",
        segmentsWithoutCustomization.get(1).getTextStr());
//        DisplayUtils.displayPdf(documentWithoutCustomization, "testWithoutCust");

    Document documentWithCustomization = pdfParserWithCustomization
        .parse(drawer.getPdfDocumentStream());
    MutableList<TextElement> segmentsWithCustomization = Lists.mutable
        .ofAll(documentWithCustomization.getContainingElements(TextElement.class))
        .selectInstancesOf(TextElement.class);

    assertEquals(
        "This text is to test the page level spacing scaling customization on a document. " +
            "The number of vocab words if spacing scaled",
        segmentsWithCustomization.get(0).getTextStr());
    assertEquals(
        "should be greater than 10 when compared with hard spacing for customization to get activated.",
        segmentsWithCustomization.get(1).getTextStr());
//        DisplayUtils.displayPdf(documentWithoutCustomization, "testWithCust");
  }

  @Test
  public void testSpacingScaledWithTenExtraWords() throws Exception {
    double customizedWidthOfSpace = 1.0;
    double lastCharacterXPos = 0.0;

    //This example illustrates the scenario in which out of vocab words with hard spacing does not split because the threshold is not breached
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    lastCharacterXPos = drawer.drawTextAt(20, 20, "This").getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "text")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "is")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "to")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "test")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "the")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "page")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "level")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "spacing")
        .getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "scaled")
        .getRight();
    lastCharacterXPos = drawer
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "customization").getRight();
    lastCharacterXPos = drawer.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20,
        "The number of vocab words if spacing scaled").getRight();
    lastCharacterXPos = drawer
        .drawTextAt(20, 40, "should be greater than 10 when compared with hard spacing " +
            "for customization to get activated.").getRight();

    PdfParser pdfParserWithoutCustomization = new PdfParser();
    PdfParser pdfParserWithCustomization = new PdfParser().withPageLevelSpacingScaling(true);

    Document documentWithoutCustomization = pdfParserWithoutCustomization
        .parse(drawer.getPdfDocumentStream());
    MutableList<TextElement> segmentsWithoutCustomization = Lists.mutable
        .ofAll(documentWithoutCustomization.getContainingElements(TextElement.class))
        .selectInstancesOf(TextElement.class);

    assertEquals(
        "ThistextistotestthepagelevelspacingscaledcustomizationThe number of vocab words if spacing scaled",
        segmentsWithoutCustomization.get(0).getTextStr());
    assertEquals(
        "should be greater than 10 when compared with hard spacing for customization to get activated.",
        segmentsWithoutCustomization.get(1).getTextStr());
//        DisplayUtils.displayPdf(documentWithoutCustomization, "testWithoutCust");

    Document documentWithCustomization = pdfParserWithCustomization
        .parse(drawer.getPdfDocumentStream());
    MutableList<TextElement> segmentsWithCustomization = Lists.mutable
        .ofAll(documentWithCustomization.getContainingElements(TextElement.class))
        .selectInstancesOf(TextElement.class);

    assertEquals(
        "ThistextistotestthepagelevelspacingscaledcustomizationThe number of vocab words if spacing scaled",
        segmentsWithCustomization.get(0).getTextStr());
    assertEquals(
        "should be greater than 10 when compared with hard spacing for customization to get activated.",
        segmentsWithCustomization.get(1).getTextStr());
//        DisplayUtils.displayPdf(documentWithoutCustomization, "testWithCust");
  }

  @Test
  public void testSpacingScaledWithUnicodeQuotes() throws Exception {
    double customizedWidthOfSpace = 1.0;
    double lastCharacterXPos = 0.0;

    //This example illustrates the scenario in which words with hard spacing constraint do not split if they belong to vocab
    PositionalDocDrawer drawer2 = new PositionalDocDrawer(PDRectangle.LETTER);
    lastCharacterXPos = drawer2.drawTextAt(20, 20, "\u201C").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "after")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "noon")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "\u201D")
        .getRight();
    lastCharacterXPos = drawer2
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, " \u201C").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "after")
        .getRight();
    lastCharacterXPos = drawer2
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "thought").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "\u201D")
        .getRight();
    lastCharacterXPos = drawer2
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, " \u201C").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "air")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "bag")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "\u201D")
        .getRight();
    lastCharacterXPos = drawer2
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, " \u201C").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "air")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "craft")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "\u201D")
        .getRight();
    lastCharacterXPos = drawer2
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, " \u201C").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "air")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "line")
        .getRight();
    lastCharacterXPos = drawer2
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, " \u201C").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "back")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "ache")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "\u201D")
        .getRight();
    lastCharacterXPos = drawer2
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, " \u201C").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "back")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "bone")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "\u201D")
        .getRight();
    lastCharacterXPos = drawer2
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, " \u201C").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "back")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "drop")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "\u201D")
        .getRight();
    lastCharacterXPos = drawer2
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, " \u201C").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "back")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "ground")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 20, "\u201D")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(20, 40, "\u201C").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 40, "back")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 40, "hand")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 40, "\u201D")
        .getRight();
    lastCharacterXPos = drawer2
        .drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 40, " \u201C").getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 40, "back")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 40, "log")
        .getRight();
    lastCharacterXPos = drawer2.drawTextAt(lastCharacterXPos + customizedWidthOfSpace, 40, "\u201D")
        .getRight();

    PdfParser pdfParserWithoutCustomization = new PdfParser();
    PdfParser pdfParserWithCustomization = new PdfParser().withPageLevelSpacingScaling(true);

    Document documentWithoutCustomization2 = pdfParserWithoutCustomization
        .parse(drawer2.getPdfDocumentStream());
    MutableList<TextElement> segmentsWithoutCustomization2 = Lists.mutable
        .ofAll(documentWithoutCustomization2.getContainingElements(TextElement.class))
        .selectInstancesOf(TextElement.class);
    assertEquals(
        "\"afternoon\" \"afterthought\" \"airbag\" \"aircraft\" \"airline \"backache\" \"backbone\" \"backdrop\" "
            +
            "\"background\"", segmentsWithoutCustomization2.get(0).getTextStr());
    assertEquals("\"backhand\" \"backlog\"", segmentsWithoutCustomization2.get(1).getTextStr());

    Document documentWithCustomization2 = pdfParserWithCustomization
        .parse(drawer2.getPdfDocumentStream());
    MutableList<TextElement> segmentsWithCustomization2 = Lists.mutable
        .ofAll(documentWithCustomization2.getContainingElements(TextElement.class))
        .selectInstancesOf(TextElement.class);
    assertEquals(
        "\"afternoon\" \"afterthought\" \"airbag\" \"aircraft\" \"airline \"backache\" \"backbone\" \"backdrop\" "
            +
            "\"background\"", segmentsWithCustomization2.get(0).getTextStr());
    assertEquals("\"backhand\" \"backlog\"", segmentsWithCustomization2.get(1).getTextStr());
  }

  @Test
  public void testDoOperatorPdFormXObject() throws Exception {
    createAndAssertMiniPdfInsidePdfAsFormXObject(0, 300);
  }

  @Test
  public void testDoOperatorPdFormXObjectRotatedPdf() throws Exception {
    createAndAssertMiniPdfInsidePdfAsFormXObject(90, 200);
  }
}
