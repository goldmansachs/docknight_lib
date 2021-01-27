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

package com.gs.ep.docknight.model.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.collections.impl.factory.Lists;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.attribute.Content;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.PageMargin;
import com.gs.ep.docknight.model.attribute.PageSize;
import com.gs.ep.docknight.model.attribute.Text;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.converter.PdfParser;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.testutil.PdfRenderer;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.Test;

public class PdfRendererTest {

  @Test
  public void testRender() throws Exception {
    Document document = new Document()
        .add(PageSize.createA4())
        .add(new PageMargin(new Length(25, Length.Unit.pt)))
        .add(new Content(
            new TextElement()
                .add(new FontSize(new Length(16, Length.Unit.pt)))
                .add(new TextStyles(TextStyles.BOLD))
                .add(new Text("Hello World"))));

    byte[] pdfData = new PdfRenderer().render(document);
    Document parsedDocument = new PdfParser().parse(new ByteArrayInputStream(pdfData));

    List<Element> docElements = parsedDocument.getContent().getValue().getElements();
    assertEquals(1, docElements.size());

    Page page = (Page) docElements.get(0);
    assertEquals(842, page.getHeight().getValue().getMagnitude(), 0.1);
    assertEquals(595, page.getWidth().getValue().getMagnitude(), 0.1);

    List<Element> pageElements = page.getPositionalContent().getValue().getElements();
    assertEquals(1, pageElements.size());

    TextElement textElement = (TextElement) pageElements.get(0);
    assertTrue(textElement.getLeft().getValue().getMagnitude() >= 25);
    assertTrue(textElement.getTop().getValue().getMagnitude() >= 25);
    assertEquals("Hello World", textElement.getText().getValue());
    assertEquals(Lists.mutable.of(TextStyles.BOLD), textElement.getTextStyles().getValue());
    assertEquals(16, textElement.getFontSize().getValue().getMagnitude(), 0);
  }
}
