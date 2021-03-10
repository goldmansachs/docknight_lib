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

package com.gs.ep.docknight.util;

import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.renderer.HtmlRenderer;
import com.gs.ep.docknight.model.testutil.PdfRenderer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import javax.imageio.ImageIO;

public final class DisplayUtils {

  private DisplayUtils() {
  }

  public static void displayHtml(Document document) {
    displayHtml(document, false);
  }

  public static void displayHtml(Document document, boolean debug) {
    displayHtml(document, "test", debug);
  }

  public static void displayHtml(Document document, String fileName) {
    displayHtml(document, fileName, false);
  }

  public static void displayHtml(Document document, String fileName, boolean debug) {
    displayString(new HtmlRenderer().withVisualDebuggingEnabled(debug).render(document), fileName,
        "html");
  }

  public static void displayHtml(String htmlDocument) {
    displayHtml(htmlDocument, "test");
  }

  public static void displayHtml(String htmlDocument, String fileName) {
    displayString(htmlDocument, fileName, "html");
  }

  public static void displayPdf(Document document) {
    displayPdf(document, "test");
  }

  public static void displayPdf(Document document, String fileName) {
    displayPdf(new PdfRenderer().render(document), fileName);
  }

  public static void displayPdf(byte[] pdfDocument) {
    displayPdf(pdfDocument, "test");
  }

  public static void displayPdf(byte[] pdfDocument, String fileName) {
    displayBinary(pdfDocument, fileName, "pdf");
  }

  public static void displayImage(BufferedImage image) {
    displayImage(image, "test");
  }

  public static void displayImage(BufferedImage image, String fileName) {
    displayImage(image, fileName, 1.0);
  }

  public static void displayExcel(byte[] excelDocument) {
    displayExcel(excelDocument, "test");
  }

  public static void displayExcel(byte[] excelDocument, boolean isXlsx) {
    displayExcel(excelDocument, "test", isXlsx);
  }

  public static void displayExcel(byte[] excelDocument, String fileName) {
    displayExcel(excelDocument, fileName, true);
  }

  public static void displayExcel(byte[] excelDocument, String fileName, boolean isXlsx) {
    displayBinary(excelDocument, fileName, isXlsx ? "xlsx" : "xls");
  }

  public static void displayImage(BufferedImage image, String fileName, double displayFactor) {
    try {
      BufferedImage displayImage =
          displayFactor != 1.0 ? ImageUtils.resizeImage(image, displayFactor) : image;
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ImageIO.write(image, "png", bos);
      displayBinary(bos.toByteArray(), fileName, "png");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void displayString(String data, String fileName, String extension) {
    try {
      File file = new File(System.getProperty("java.io.tmpdir") + "/" + fileName + "." + extension);
      PrintWriter out = new PrintWriter(file, "UTF-8");
      out.println(data);
      out.close();
      java.awt.Desktop.getDesktop().browse(file.toURI());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void displayBinary(byte[] data, String fileName, String extension) {
    try {
      File file = new File(System.getProperty("java.io.tmpdir") + "/" + fileName + "." + extension);
      OutputStream oos = new FileOutputStream(file);
      oos.write(data);
      oos.close();
      java.awt.Desktop.getDesktop().open(file);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
