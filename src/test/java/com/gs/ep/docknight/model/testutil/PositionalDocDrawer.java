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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import com.gs.ep.docknight.model.converter.PdfParser;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.renderer.HtmlRenderer;
import com.gs.ep.docknight.util.DisplayUtils;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.PDSoftMask;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.util.Matrix;

public class PositionalDocDrawer {

  public static final double DEFAULT_LINE_SPACING = 5;
  public static final double DEFAULT_MARGIN = 5;

  private final PDDocument pdfDocument;
  private final PDRectangle defaultPageSize;
  private PDPageContentStream contentStream;
  private boolean inTextMode;
  private PDPage pdfPage;
  private double pageHeight;
  private PDFont font;
  private double fontSize;
  private double bottom;
  private double left;
  private RenderingMode textRenderable;
  private double textAngle;
  private double lineSpacing;
  private double topMargin;
  private double leftMargin;
  private double bottomMargin;
  private double rightMargin;

  public PositionalDocDrawer(PDRectangle defaultPageSize) {
    this.pdfDocument = new PDDocument();
    this.defaultPageSize = defaultPageSize;
    this.textRenderable = RenderingMode.FILL;
    this.textAngle = 0;
    this.lineSpacing = DEFAULT_LINE_SPACING;
    this.topMargin = DEFAULT_MARGIN;
    this.leftMargin = DEFAULT_MARGIN;
    this.bottomMargin = DEFAULT_MARGIN;
    this.rightMargin = DEFAULT_MARGIN;
    this.addPage();
  }

  public static void setFieldCosAttributes(PDField field, double left, double top, double width,
      double height, String value,
      double pageHeight, String type) {
    COSDictionary cosDict = field.getCOSObject();
    cosDict.setItem(COSName.RECT,
        new PDRectangle((float) left, (float) (pageHeight - top - height), (float) width,
            (float) height).getCOSArray());
    cosDict.setItem(COSName.TYPE, COSName.ANNOT);
    cosDict.setItem(COSName.SUBTYPE, COSName.getPDFName("Widget"));
    cosDict.setItem(COSName.T, new COSString(type));
    cosDict.setString(COSName.V, value);
  }

  private static void unchecked(ThrowsRunnable runnable) {
    try {
      runnable.run();
    } catch (Exception e) {
      throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
    }
  }

  private static <K> K unchecked(ThrowsCallable<K> callable) {
    try {
      return callable.call();
    } catch (Exception e) {
      throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
    }
  }

  public void addPage(PDRectangle pageSize) {
    unchecked(() ->
    {
      if (this.contentStream != null) {
        this.finalizeContent();
      }
      this.pdfPage = new PDPage(pageSize);
      this.pageHeight = this.pdfPage.getBBox().getHeight();
      this.pdfDocument.addPage(this.pdfPage);
      this.contentStream = new PDPageContentStream(this.pdfDocument, this.pdfPage);
      this.setFont(PDType1Font.TIMES_ROMAN, 12);
    });
  }

  public void addFormXObject(PDDocument sourceDoc, int pageNo) throws IOException {
    this.ensureNotInTextMode();
    LayerUtility layerUtility = new LayerUtility(this.pdfDocument);
    PDFormXObject pdFormXObject = layerUtility.importPageAsForm(sourceDoc, pageNo);
    this.contentStream.drawForm(pdFormXObject);
  }

  private void ensureTextMode() throws IOException {
    if (!this.inTextMode) {
      this.contentStream.beginText();
      this.contentStream.newLineAtOffset(0, (float) this.pageHeight);
      this.left = 0;
      this.bottom = 0;
      this.inTextMode = true;
    }
  }

  private void finalizeContent() throws IOException {
    this.ensureNotInTextMode();
    this.contentStream.close();
  }

  private void reInitializeContent() throws IOException {
    this.contentStream = new PDPageContentStream(this.pdfDocument, this.pdfPage, AppendMode.APPEND,
        true);
    this.contentStream.setFont(this.font, (float) this.fontSize);
  }

  private void ensureNotInTextMode() throws IOException {
    if (this.inTextMode) {
      this.contentStream.endText();
      this.inTextMode = false;
    }
  }

  public void addPage() {
    this.addPage(this.defaultPageSize);
  }

  public void rotatePage(int degree) {
    this.pdfPage.setRotation(this.pdfPage.getRotation() + degree);
  }

  private PDAcroForm getAcroForm() {
    PDDocumentCatalog documentCatalog = this.pdfDocument.getDocumentCatalog();
    PDAcroForm acroForm = documentCatalog.getAcroForm();
    if (acroForm == null) {
      acroForm = new PDAcroForm(this.pdfDocument);
      documentCatalog.setAcroForm(acroForm);
    }
    return acroForm;
  }

  public BoundingBox drawTextField(double left, double top, double width, double height) {
    return this.drawTextField(left, top, width, height, "");
  }

  public BoundingBox drawTextField(double left, double top, double width, double height,
      String value) {
    return unchecked(() ->
    {
      PDAcroForm acroForm = this.getAcroForm();
      PDTextField textField = new PDTextField(acroForm);
      setFieldCosAttributes(textField, left, top, width, height, value, this.pageHeight, "textBox");
      this.addFieldToDocument(textField);
      return new BoundingBox(top, left, width, height);
    });
  }

  public BoundingBox drawPushButton(double left, double top, double width, double height,
      String value) {
    return unchecked(() ->
    {
      PDAcroForm acroForm = this.getAcroForm();
      PDPushButton pushButton = new PDPushButton(acroForm);
      setFieldCosAttributes(pushButton, left, top, width, height, value, this.pageHeight,
          "pushButton");
      this.addFieldToDocument(pushButton);
      return new BoundingBox(top, left, width, height);
    });
  }

  public BoundingBox drawCheckbox(double left, double top, double width, double height,
      boolean checked) {
    return unchecked(() ->
    {
      PDAcroForm acroForm = this.getAcroForm();
      PDCheckBox checkBox = new PDCheckBox(acroForm);

      setFieldCosAttributes(checkBox, left, top, width, height, "", this.pageHeight, "checkBox");

      this.addFieldToDocument(checkBox);
      checkBox.getWidget().setAppearance(this.getAppearanceDictionary(Lists.mutable.of("On")));

      checkBox.setValue(checked ? "On" : "Off");
      return new BoundingBox(top, left, width, height);
    });
  }

  public BoundingBox drawRadioButton(double left, double top, double width, double height,
      boolean checked) {
    return unchecked(() ->
    {
      PDAcroForm acroForm = this.getAcroForm();
      PDRadioButton radioButton = new PDRadioButton(acroForm);

      setFieldCosAttributes(radioButton, left, top, width, height, "", this.pageHeight, "radioBox");

      this.addFieldToDocument(radioButton);
      radioButton.getWidget().setAppearance(this.getAppearanceDictionary(Lists.mutable.of("On")));

      radioButton.setValue(checked ? "On" : "Off");
      return new BoundingBox(top, left, width, height);
    });
  }

  public PDAppearanceDictionary getAppearanceDictionary(List<String> appearanceNames) {
    COSDictionary normalAppearances = new COSDictionary();
    PDAppearanceDictionary pdAppearanceDictionary = new PDAppearanceDictionary();
    pdAppearanceDictionary.setNormalAppearance(new PDAppearanceEntry(normalAppearances));
    appearanceNames
        .forEach(s -> normalAppearances.setItem(s, new PDAppearanceStream(this.pdfDocument)));
    return pdAppearanceDictionary;
  }

  public void addFieldToDocument(PDField field) throws IOException {
    this.getAcroForm().getFields().add(field);
    this.pdfPage.getAnnotations().add(field.getWidgets().get(0));
  }

  public void setTextRenderable(RenderingMode textRenderable) {
    this.textRenderable = textRenderable;
  }

  public void setTextAngle(double textAngle) {
    this.textAngle = textAngle;
  }

  public void setFont(PDFont font, double fontSize) {
    unchecked(() ->
    {
      this.font = font;
      this.fontSize = fontSize;
      this.contentStream.setFont(font, (float) fontSize);
    });
  }

  public void setWriteAndFillColor(Color color) {
    unchecked(() -> this.contentStream.setNonStrokingColor(color));
  }

  public void setStrokeColor(Color color) {
    unchecked(() -> this.contentStream.setStrokingColor(color));
  }

  public double getWidthOfSpace() {
    return this.font.getSpaceWidth() * this.fontSize / 1000;
  }

  public BoundingBox drawImageAt(double left, double top, BufferedImage image) {
    return unchecked(() ->
    {
      this.ensureNotInTextMode();
      PDImageXObject pdfImage = LosslessFactory.createFromImage(this.pdfDocument, image);
      this.contentStream.drawImage(pdfImage, (float) left,
          (float) (this.pageHeight - top - pdfImage.getHeight()));
      return new BoundingBox(top, left, pdfImage.getWidth(), pdfImage.getHeight());
    });
  }

  public void setLuminosity(boolean isLuminous) {
    unchecked(() ->
    {
      COSDictionary graphicsStateDict = new COSDictionary();
      if (isLuminous) {
        COSDictionary softMaskDict = new COSDictionary();
        softMaskDict.setItem(COSName.S, COSName.LUMINOSITY);
        graphicsStateDict.setItem(COSName.SMASK, new PDSoftMask(softMaskDict));
      }
      this.contentStream.setGraphicsStateParameters(new PDExtendedGraphicsState(graphicsStateDict));
    });
  }

  public BoundingBox drawRectangleAt(double left, double top, double width, double height,
      RenderingMode renderingMode) {
    return unchecked(() ->
    {
      if (!renderingMode.isFill() && !renderingMode.isStroke()) {
        throw new RuntimeException("Invalid rendering mode to create a rectangle");
      }
      this.ensureNotInTextMode();
      this.contentStream
          .addRect((float) left, (float) (this.pageHeight - top - height), (float) width,
              (float) height);
      if (renderingMode.isFill() && renderingMode.isStroke()) {
        this.contentStream.fillAndStroke();
      } else if (renderingMode.isFill()) {
        this.contentStream.fill();
      } else {
        this.contentStream.stroke();
      }
      return new BoundingBox(top, left, width, height);
    });
  }

  public void drawHorizontalLineAt(double left, double top, double stretch) {
    unchecked(() ->
    {
      this.ensureNotInTextMode();
      this.contentStream.moveTo((float) left, (float) (this.pageHeight - top));
      this.contentStream.lineTo((float) (left + stretch), (float) (this.pageHeight - top));
      this.contentStream.stroke();
    });
  }

  public void drawVerticalLineAt(double left, double top, double stretch) {
    unchecked(() ->
    {
      this.ensureNotInTextMode();
      this.contentStream.moveTo((float) left, (float) (this.pageHeight - top));
      this.contentStream.lineTo((float) left, (float) (this.pageHeight - top - stretch));
      this.contentStream.stroke();
    });
  }

  public BoundingBox drawTextAt(double left, double top, String text) {
    return unchecked(() ->
    {
      double width = this.font.getStringWidth(text) * this.fontSize / 1000;
      double height = this.getFontHeight(this.font) * 1.25 * this.fontSize / 1000;
      this.moveTo(left, top + height);
      return this.drawText(text, width, height);
    });
  }

  public BoundingBox drawTextAtTop(double left, double bottom, String text) {
    return unchecked(() ->
    {
      double width = this.font.getStringWidth(text) * this.fontSize / 1000;
      double height = this.getFontHeight(this.font) * 1.25 * this.fontSize / 1000;
      this.moveTo(left, bottom);
      return this.drawText(text, width, height);
    });
  }

  public BoundingBox drawTextAt(Pair<Double, Double> position, String text) {
    return this.drawTextAt(position.getOne(), position.getTwo(), text);
  }

  public BoundingBox drawTextAtTop(Pair<Double, Double> position, String text) {
    return this.drawTextAtTop(position.getOne(), position.getTwo(), text);
  }

  private BoundingBox drawText(String text, double width, double height) throws IOException {
    if (this.textRenderable != RenderingMode.FILL) {
      this.contentStream.appendRawCommands(this.textRenderable.intValue() + " Tr ");
    }
    if (this.textAngle != 0) {
      this.contentStream.setTextMatrix(Matrix
          .getRotateInstance(Math.toRadians(this.textAngle), (float) this.left,
              (float) (this.pageHeight - this.bottom)));
    }
    this.contentStream.showText(text);
    if (this.textRenderable != RenderingMode.FILL) {
      this.contentStream.appendRawCommands("0 Tr ");
    }
    if (this.textAngle != 0) {
      this.contentStream.setTextMatrix(Matrix
          .getRotateInstance(Math.toRadians(0), (float) this.left,
              (float) (this.pageHeight - this.bottom)));
    }
    return new BoundingBox(this.bottom - height, this.left, width, height);
  }

  private float getFontHeight(PDFont font) throws IOException {
    org.apache.fontbox.util.BoundingBox bbox = font.getBoundingBox();
    if (bbox.getLowerLeftY() < Short.MIN_VALUE) {
      bbox.setLowerLeftY(-(bbox.getLowerLeftY() + 65536));
    }
    float glyphHeight = bbox.getHeight() / 2;

    PDFontDescriptor fontDescriptor = font.getFontDescriptor();
    if (fontDescriptor != null) {
      float capHeight = fontDescriptor.getCapHeight();
      if (capHeight != 0 && capHeight < glyphHeight) {
        glyphHeight = capHeight;
      }
    }

    float height;
    if (font instanceof PDType3Font) {
      height = font.getFontMatrix().transformPoint(0, glyphHeight).y * 1000;
    } else {
      height = glyphHeight;
    }
    return height;
  }

  public void setLineSpacing(double lineSpacing) {
    this.lineSpacing = lineSpacing;
  }

  public void setMargin(double margin) {
    this.setMargin(margin, margin, margin, margin);
  }

  public void setMargin(double topMargin, double leftMargin, double bottomMargin,
      double rightMargin) {
    this.topMargin = topMargin;
    this.leftMargin = leftMargin;
    this.bottomMargin = bottomMargin;
    this.rightMargin = rightMargin;
  }

  public void drawTextWithBorderInside(BoundingBox bbox, String text) {
    this.drawTextInside(bbox, text);
    this.drawBorderOn(bbox);
  }

  public void drawTextInside(BoundingBox bbox, String text) {
    unchecked(() ->
    {
      if (text.isEmpty()) {
        return;
      }
      MutableList<MutableList<String>> words = Lists.mutable.of(text.split("\n"))
          .collect(s -> Lists.mutable.of(s.split(" ")));
      double fontHeight = this.getFontHeight(this.font) * 1.25 * this.fontSize / 1000;
      double spaceWidth = this.getWidthOfSpace();
      double totalHeight = 0.0;
      double lineWidth = Integer.MAX_VALUE;
      MutableList<String> lineWords = Lists.mutable.empty();
      MutableMap<Double, String> allLines = Maps.mutable.empty();

      double textBoxTop = bbox.getTop() + this.topMargin;
      double textBoxLeft = bbox.getLeft() + this.leftMargin;
      double textBoxWidth = bbox.getWidth() - this.leftMargin - this.rightMargin;
      double textBoxHeight = bbox.getHeight() - this.topMargin - this.bottomMargin;

      for (MutableList<String> wordsSplitByNewLine : words) {
        int i = 0;
        for (String word : wordsSplitByNewLine) {
          double width = this.font.getStringWidth(word) * this.fontSize / 1000;
          if (width > textBoxWidth) {
            throw new RuntimeException("Cannot accomodate within given bounding box width");
          }
          double lineWidthOnContinuation = lineWidth + spaceWidth + width;
          if (lineWidthOnContinuation > textBoxWidth || i == 0) {
            totalHeight += (lineWords.isEmpty() ? 0.0 : this.lineSpacing) + fontHeight;
            if (totalHeight > textBoxHeight) {
              throw new RuntimeException("Cannot accomodate within given bounding box height");
            }
            if (lineWords.notEmpty()) {
              allLines.put(textBoxTop + totalHeight - 2 * fontHeight - this.lineSpacing,
                  lineWords.makeString(" "));
            }
            lineWidth = width;
            lineWords = Lists.mutable.of(word);
          } else {
            lineWidth = lineWidthOnContinuation;
            lineWords.add(word);
          }
          i++;
        }
      }
      allLines.put(textBoxTop + totalHeight - fontHeight, lineWords.makeString(" "));

      for (Pair<Double, String> line : allLines.keyValuesView()) {
        this.drawTextAt(textBoxLeft, line.getOne(), line.getTwo());
      }
    });
  }

  public void drawBorderOn(BoundingBox bbox) {
    this.drawHorizontalLineAt(bbox.getLeft(), bbox.getTop(), bbox.getWidth());
    this.drawHorizontalLineAt(bbox.getLeft(), bbox.getBottom(), bbox.getWidth());
    this.drawVerticalLineAt(bbox.getLeft(), bbox.getTop(), bbox.getHeight());
    this.drawVerticalLineAt(bbox.getRight(), bbox.getTop(), bbox.getHeight());
  }

  private void moveTo(double left, double bottom) throws IOException {
    this.ensureTextMode();
    this.contentStream.newLineAtOffset((float) (left - this.left), (float) (this.bottom - bottom));
    this.left = left;
    this.bottom = bottom;
  }

  public byte[] getPdfDocument() {
    return unchecked(() ->
    {
      this.finalizeContent();
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      this.pdfDocument.save(byteArrayOutputStream);
      this.reInitializeContent();
      return byteArrayOutputStream.toByteArray();
    });
  }

  public InputStream getPdfDocumentStream() {
    return new ByteArrayInputStream(this.getPdfDocument());
  }

  public String getHtmlDocument() throws Exception {
    return new HtmlRenderer().render(this.getDocument());
  }

  public InputStream getHtmlDocumentStream() throws Exception {
    return new ByteArrayInputStream(this.getHtmlDocument().getBytes(StandardCharsets.UTF_8));
  }

  public Document getDocument() throws Exception {
    return new PdfParser().parse(this.getPdfDocumentStream());
  }

  public void displayAsHtml(String fileName) throws Exception {
    DisplayUtils.displayHtml(this.getHtmlDocument(), fileName);
  }

  public void displayAsPdf(String fileName) {
    DisplayUtils.displayPdf(this.getPdfDocument(), fileName);
  }

  private interface ThrowsRunnable {

    void run() throws Exception;
  }

  private interface ThrowsCallable<K> {

    K call() throws Exception;
  }
}
