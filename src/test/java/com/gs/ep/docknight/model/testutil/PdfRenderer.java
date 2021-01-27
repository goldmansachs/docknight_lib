/*
 *   Copyright 2021 Goldman Sachs.
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

package com.gs.ep.docknight.model.testutil;

import com.gs.ep.docknight.model.Renderer;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.renderer.HtmlRenderer;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.xhtmlrenderer.pdf.ITextRenderer;

/**
 * Pdf renderer to render {@see com.gs.ep.docknight.model.element.Document document model} into pdf
 * byte stream
 */
public class PdfRenderer implements Renderer<byte[]> {

  private final HtmlRenderer htmlRenderer;

  public PdfRenderer() {
    this.htmlRenderer = new HtmlRenderer().withOutputMediumPrint(true);
  }

  @Override
  public byte[] render(Document document) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      FixBaseFont.fixBuiltinFonts(); //Needs to be used before every usage of PDFRenderer

      String html = this.htmlRenderer.render(document);
      ITextRenderer renderer = new ITextRenderer();
      renderer.setDocumentFromString(html);
      renderer.layout();
      renderer.createPDF(bos);
      return bos.toByteArray();
    } catch (IOException | DocumentException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @see com.lowagie.text.pdf.BaseFont <a href https://stackoverflow.com/questions/48947668/error-generating-pdf-itextfontresolver-java-lang-runtimeexception-font-couri>
   * </a>
   */
  public abstract static class FixBaseFont extends BaseFont {

    public static void fixBuiltinFonts() {
      if (BuiltinFonts14.size() != 14) {
        BuiltinFonts14.clear();

        BuiltinFonts14.put(COURIER, PdfName.COURIER);
        BuiltinFonts14.put(COURIER_BOLD, PdfName.COURIER_BOLD);
        BuiltinFonts14.put(COURIER_BOLDOBLIQUE, PdfName.COURIER_BOLDOBLIQUE);
        BuiltinFonts14.put(COURIER_OBLIQUE, PdfName.COURIER_OBLIQUE);
        BuiltinFonts14.put(HELVETICA, PdfName.HELVETICA);
        BuiltinFonts14.put(HELVETICA_BOLD, PdfName.HELVETICA_BOLD);
        BuiltinFonts14.put(HELVETICA_BOLDOBLIQUE, PdfName.HELVETICA_BOLDOBLIQUE);
        BuiltinFonts14.put(HELVETICA_OBLIQUE, PdfName.HELVETICA_OBLIQUE);
        BuiltinFonts14.put(SYMBOL, PdfName.SYMBOL);
        BuiltinFonts14.put(TIMES_ROMAN, PdfName.TIMES_ROMAN);
        BuiltinFonts14.put(TIMES_BOLD, PdfName.TIMES_BOLD);
        BuiltinFonts14.put(TIMES_BOLDITALIC, PdfName.TIMES_BOLDITALIC);
        BuiltinFonts14.put(TIMES_ITALIC, PdfName.TIMES_ITALIC);
        BuiltinFonts14.put(ZAPFDINGBATS, PdfName.ZAPFDINGBATS);
      }
    }
  }
}
