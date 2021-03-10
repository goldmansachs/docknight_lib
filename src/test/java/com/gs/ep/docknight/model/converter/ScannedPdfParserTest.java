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
import static org.junit.Assert.assertTrue;

import com.gs.ep.docknight.util.abbyy.AbbyyProperties;
import java.util.Properties;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.Parser;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.converter.ScannedPdfParser.OCREngine;
import com.gs.ep.docknight.model.converter.ScannedPdfParser.WordInfo;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.util.QuickSort;
import org.junit.Before;
import org.junit.Test;

public class ScannedPdfParserTest {

  ScannedPdfParser tesseractScannedPdfParser;
  ScannedPdfParser abbyyScannedPdfParser;

  private static void testParser(ScannedPdfParser tesseractScannedPdfParser) {
    String filePath = Thread.currentThread().getContextClassLoader()
        .getResource("parser/ScannedTest.pdf").getPath();
    for (Parser<InputStream> parser : new Parser[]{tesseractScannedPdfParser,
        new PdfParser().withScannedPdfParser(tesseractScannedPdfParser)}) {
      try (FileInputStream inputStream = new FileInputStream(new File(filePath))) {
        Document document = parser.parse(inputStream);
        Page page = (Page) document.getContent().getValue().getFirst();
        MutableList<Element> pageElements = ListAdapter
            .adapt(page.getPositionalContent().getValue().getElements());
        List<String> textStrings = pageElements.collect(Element::getTextStr);
        assertEquals(Lists.mutable
                .of("HEADING", "Group 1 Line 1", "Group 1 Line 2", "Group 2 Line 1", "Group 2 Line 2"),
            textStrings);
        for (Element pageElement : pageElements) {
          double fontSize = pageElement.getAttribute(FontSize.class).getValue().getMagnitude();
          assertTrue(pageElement.getTextStr() + " : " + fontSize, fontSize >= 8 && fontSize <= 12);
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static void testParserForMultiplePages(ScannedPdfParser scannedPdfParser)
      throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    for (int i = 0; i < 2; i++) {
      drawer.drawTextAt(100, 100, "My name is abc");
      drawer.addPage();
    }
    Document document = scannedPdfParser.parse(drawer.getPdfDocumentStream());
    assertEquals(StringUtils.repeat("My name is abc", "\n", 2) + "\n", document.getTextStr());
  }

  @Before
  public void setUp() throws Exception {
    try {
      this.tesseractScannedPdfParser = new ScannedPdfParser(Lists.mutable.of("eng"), 300,
          OCREngine.TESSERACT);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }

    try {
      Properties properties = new Properties();
      properties.setProperty("abbyy.abbyyurl", "http://temp.com");
      AbbyyProperties.setAbbyyProperties(properties);
      this.abbyyScannedPdfParser = new ScannedPdfParser(Lists.mutable.of("eng"), 300,
          OCREngine.ABBYY);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testTesseractParser() {
    if (this.tesseractScannedPdfParser != null) {
      testParser(this.tesseractScannedPdfParser);
    }
  }

  @Test
  public void testAbbyyParser() {
    if (this.abbyyScannedPdfParser != null) {
      testParser(this.abbyyScannedPdfParser);
    }
  }

  @Test
  public void testTesseractParserForMultiplePages() throws Exception {
    if (this.tesseractScannedPdfParser != null) {
      testParserForMultiplePages(this.tesseractScannedPdfParser);
    }
  }

  @Test
  public void testAbbyyParserForMultiplePages() throws Exception {
    if (this.abbyyScannedPdfParser != null) {
      testParserForMultiplePages(this.abbyyScannedPdfParser);
    }
  }

  @Test(timeout = 10000)
  public void testWordInfoComparator() {
    WordInfo word = new WordInfo();
    List<WordInfo> words = Lists.mutable.of(word, word, word, word, word);
    QuickSort.sort(words, ScannedPdfParser.getWordInfoComparator());
    assertEquals(Lists.mutable.of(word, word, word, word, word), words);
  }
}
