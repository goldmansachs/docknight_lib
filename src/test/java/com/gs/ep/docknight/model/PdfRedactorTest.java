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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.collections.impl.factory.Sets;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.ImageData;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Stretch;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.converter.PdfParser;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.GraphicalElement;
import com.gs.ep.docknight.model.element.Image;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.testutil.PdfRedactor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.Test;

public class PdfRedactorTest {

  @Test
  public void testRedactor() throws Exception {
    PDDocument pdfDocument = new PDDocument();
    PDPage pdfPage = new PDPage();
    pdfDocument.addPage(pdfPage);
    PDPageContentStream contentStream = new PDPageContentStream(pdfDocument, pdfPage);
    PDFont font1 = PDType1Font.HELVETICA_BOLD;
    contentStream.beginText();

    contentStream.setFont(font1, 11);
    contentStream.newLineAtOffset(60, 700);
    contentStream.showText("This is bold text");

    contentStream.setFont(font1, 12);
    contentStream.newLineAtOffset(0, -100);
    contentStream.showText("This is italic text");

    contentStream.setFont(font1, 14);
    contentStream.newLineAtOffset(345, -100);
    contentStream.showText("This is right aligned text");

    contentStream.endText();

    contentStream.moveTo(100, 100);
    contentStream.lineTo(100, 200);
    contentStream.stroke();

    String pngImageInBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNgAAIAAAUAAen63NgAAAAASUVORK5CYII=";
    ComparableBufferedImage comparableBufferedImage = ComparableBufferedImage
        .parseBase64PngBinary(pngImageInBase64);
    PDImageXObject pdfImage = LosslessFactory
        .createFromImage(pdfDocument, comparableBufferedImage.getBufferedImage());
    contentStream.drawImage(pdfImage, 50, 50);

    contentStream.close();

    ByteArrayOutputStream originalPdfOutputStream = new ByteArrayOutputStream();
    pdfDocument.save(originalPdfOutputStream);

    PdfRedactor.redact(pdfDocument);
    ByteArrayOutputStream redactedPdfOutputStream = new ByteArrayOutputStream();
    pdfDocument.save(redactedPdfOutputStream);

    pdfDocument.close();

    Document originalDocument = new PdfParser()
        .parse(new ByteArrayInputStream(originalPdfOutputStream.toByteArray()));
    Document redactedDocument = new PdfParser()
        .parse(new ByteArrayInputStream(redactedPdfOutputStream.toByteArray()));

    Page originalPage = (Page) originalDocument.getContent().getValue().getElements().get(0);
    Page redactedPage = (Page) redactedDocument.getContent().getValue().getElements().get(0);

    List<Element> originalElements = originalPage.getPositionalContent().getValue().getElements();
    List<Element> redactedElements = redactedPage.getPositionalContent().getValue().getElements();

    assertEquals(originalElements.size(), redactedElements.size());

    for (int i = 0; i < originalElements.size(); i++) {
      Element originalElement = originalElements.get(i);
      Element redactedElement = redactedElements.get(i);

      assertEquals(originalElement.getClass(), redactedElement.getClass());
      assertEquals(originalElement.getAttribute(Top.class).getValue().getMagnitude(),
          redactedElement.getAttribute(Top.class).getValue().getMagnitude(), 0.1);
      assertEquals(originalElement.getAttribute(Left.class).getValue().getMagnitude(),
          redactedElement.getAttribute(Left.class).getValue().getMagnitude(), 0.1);

      if (originalElement instanceof TextElement) {
        double originalHeight = originalElement.getAttribute(Height.class).getValue()
            .getMagnitude();
        double heightVariance = Math.abs(
            redactedElement.getAttribute(Height.class).getValue().getMagnitude() - originalHeight)
            / originalHeight;
        assertTrue(heightVariance < 0.05);

        double originalWidth = originalElement.getAttribute(Width.class).getValue().getMagnitude();
        double widthVariance = Math.abs(
            redactedElement.getAttribute(Width.class).getValue().getMagnitude() - originalWidth)
            / originalWidth;
        assertTrue(widthVariance < 0.05);

        Set<String> originalWords = Sets.mutable.of(originalElement.getTextStr().split("\\s+"));
        for (String redactedWord : redactedElement.getTextStr().split("\\s+")) {
          assertFalse(originalWords.contains(redactedWord));
        }
      }

      if (originalElement instanceof Image) {
        assertEquals(originalElement.getAttribute(Width.class).getValue().getMagnitude(),
            redactedElement.getAttribute(Width.class).getValue().getMagnitude(), 0.1);
        assertEquals(originalElement.getAttribute(Height.class).getValue().getMagnitude(),
            redactedElement.getAttribute(Height.class).getValue().getMagnitude(), 0.1);
        assertNotEquals(originalElement.getAttribute(ImageData.class).getValue(),
            redactedElement.getAttribute(ImageData.class).getValue());
      }

      if (originalElement instanceof GraphicalElement) {
        assertEquals(originalElement.getAttribute(Stretch.class).getValue().getMagnitude(),
            redactedElement.getAttribute(Stretch.class).getValue().getMagnitude(), 0.1);
      }
    }
  }
}
