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

package com.gs.ep.docknight.model.testutil;

import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Random;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public final class PdfRedactor {

  private PdfRedactor() {
  }

  private static char randomize(char ch, Random randomGenerator) {
    if (Character.isUpperCase(ch)) {
      return (char) ('A' + randomGenerator.nextInt(26));
    } else if (Character.isDigit(ch)) {
      return (char) ('0' + randomGenerator.nextInt(10));
    } else if (Character.isLowerCase(ch)) {
      return (char) ('a' + randomGenerator.nextInt(26));
    } else {
      return ch;
    }
  }

  private static float getStringWidth(String str, PDFont font) throws IOException {
    float strWidth = 0.0f;
    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);
      strWidth += font.getWidth((int) ch);
    }
    return strWidth;
  }

  private static String getRedactedString(String str, PDFont font, Random randomGenerator)
      throws IOException {
    float strWidth = getStringWidth(str, font);
    float epsilon = font.getWidth((int) 'i');
    String redactedStr = "";
    float redactedStrWidth = 0.0f;
    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);
      char redactedCh = randomize(ch, randomGenerator);
      float redactedChWidth = font.getWidth((int) redactedCh);
      if (redactedStrWidth + redactedChWidth < strWidth + epsilon / 2) {
        redactedStr += redactedCh;
        redactedStrWidth += redactedChWidth;
      } else {
        break;
      }
    }
    if (strWidth > 0 && epsilon > 0) {
      while (redactedStrWidth < strWidth - epsilon / 2) {
        redactedStr += 'i';
        redactedStrWidth += epsilon;
      }
    }
    return redactedStr;
  }

  private static void redactCOSString(COSString cosString, PDFont font, Random randomGenerator)
      throws IOException {
    cosString.setValue(
        new COSString(getRedactedString(cosString.getString(), font, randomGenerator)).getBytes());
  }

  public static void redact(PDDocument document) throws IOException {
    Random randomGenerator = new Random(0);
    for (PDPage page : document.getPages()) {
      PDResources pageResources = page.getResources();
      PDFStreamParser parser = new PDFStreamParser(page);
      parser.parse();
      List<Object> tokens = parser.getTokens();
      Object prevToken = null;
      Object prevPrevToken = null;
      PDFont font = null;
      for (Object token : tokens) {
        if (token instanceof Operator) {
          String opName = ((Operator) token).getName();
          // (Tj), ('), (") and (TJ) are the four operators that display strings in a PDF
          if (opName.equals("Tj") || opName.equals("'") || opName.equals("\"")) {
            // (Tj), (') and (") have first operator as a string
            redactCOSString((COSString) prevToken, font, randomGenerator);
          } else if (opName.equals("TJ") && prevToken != null) {
            // (TJ) takes one operator of array of possible strings
            for (COSBase prevTokenSubPart : (COSArray) prevToken) {
              if (prevTokenSubPart instanceof COSString) {
                redactCOSString((COSString) prevTokenSubPart, font, randomGenerator);
              }
            }
          } else if (opName.equals("Tf")) {
            // (Tf) takes two operators fontSize and fontName
            font = pageResources.getFont((COSName) prevPrevToken);
            font = font == null ? PDType1Font.TIMES_ROMAN : font;
          } else if (opName.equals("Do")) {
            // (Do) takes a resource name to display which can be an image
            PDXObject xObject = pageResources.getXObject((COSName) prevToken);
            if (xObject instanceof PDImageXObject) {
              BufferedImage image = ((PDImageXObject) xObject).getImage();
              Graphics2D graphics = image.createGraphics();
              graphics.setColor(Color.LIGHT_GRAY);
              graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
              pageResources
                  .put((COSName) prevToken, LosslessFactory.createFromImage(document, image));
            }
          }
        }
        prevPrevToken = prevToken;
        prevToken = token;
      }
      PDStream updatedStream = new PDStream(document);
      OutputStream streamOutput = updatedStream.createOutputStream();
      ContentStreamWriter tokenWriter = new ContentStreamWriter(streamOutput);
      tokenWriter.writeTokens(tokens);
      page.setContents(updatedStream);
      streamOutput.close();
    }
  }

  public static void redact(String inputPdfPath, String outputPdfPath) throws IOException {
    redact(inputPdfPath, outputPdfPath, IntLists.mutable.empty());
  }

  public static void redact(String inputPdfPath, String outputPdfPath, IntList pagesToKeep)
      throws IOException {
    PDDocument document = PDDocument.load(new File(inputPdfPath));
    document.setAllSecurityToBeRemoved(true);
    if (!pagesToKeep.isEmpty()) {
      IntList pagesToRemove = IntLists.mutable.of(range(0, document.getNumberOfPages()))
          .select(i -> !pagesToKeep.contains(i));
      pagesToRemove.collect(p -> document.getPage(p)).each(p -> document.removePage(p));
    }
    redact(document);
    document.save(outputPdfPath);
    document.close();
  }

  public static int[] range(int start, int end) {
    int[] result = new int[end - start];
    for (int i = start; i < end; i++) {
      result[i - start] = i;
    }
    return result;
  }

  public static void main(String[] args) throws IOException {
    redact("H:\\DCL.pdf", "H:\\Redacted.pdf", IntLists.mutable.of());
  }
}
