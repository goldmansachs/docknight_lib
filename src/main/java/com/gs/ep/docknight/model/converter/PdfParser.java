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

import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.util.StatsDClientWrapper;
import org.apache.commons.collections4.IterableUtils;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.tuple.Tuples;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.Form;
import com.gs.ep.docknight.model.Form.FormType;
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.Length.Unit;
import com.gs.ep.docknight.model.Parser;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.attribute.FontFamily;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.FormData;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.PageStructure;
import com.gs.ep.docknight.model.attribute.TextAlign;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.converter.pdfparser.AdjustedPDPage;
import com.gs.ep.docknight.model.converter.pdfparser.PDFDocumentStripper;
import com.gs.ep.docknight.model.converter.pdfparser.ParserSettings;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.FormElement;
import com.gs.ep.docknight.model.element.TextElement;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.pdmodel.interactive.form.PDVariableText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser to convert pdf input stream into document model {@see Document}
 */
public class PdfParser implements Parser<InputStream> {

  protected static final Logger LOGGER = LoggerFactory.getLogger(PdfParser.class);
  private final ParserSettings settings = new ParserSettings();

  /**
   * Formatting decimal value such that number of digits = 2 to the right of decimal point
   */
  private static double format(double value) {
    return new BigDecimal(value).setScale(2, RoundingMode.FLOOR).doubleValue();
  }

  /**
   * @return number of text elements / number of lines
   */
  protected static double getTextSegmentationRatio(Document document) {
    Element prevElement = null;
    double lineCount = 1;
    double segmentCount = 0;
    for (Element element : document.getContainingElements(TextElement.class)) {
      segmentCount++;
      if (prevElement != null
          && PositionalElementList.compareByHorizontalAlignment(prevElement, element) != 0) {
        lineCount++;
      }
      prevElement = element;
    }
    return segmentCount / lineCount;
  }

  public ParserSettings getParserSettings() {
    return this.settings;
  }

  public PdfParser withScannedPdfParser(ScannedPdfParser scannedPdfParser) {
    this.settings.setScannedPdfParser(scannedPdfParser);
    return this;
  }

  public PdfParser withFontChangeSegmentation(boolean fontChangeSegmentation) {
    this.settings.setFontChangeSegmentation(fontChangeSegmentation);
    return this;
  }

  public PdfParser withUnderlineDetection(boolean underlineDetection) {
    this.settings.setUnderlineDetection(underlineDetection);
    return this;
  }

  // 0 based, endPage exclusive
  public PdfParser withPageRange(int startPage, int endPage) {
    this.settings.setStartPage(startPage);
    this.settings.setEndPage(endPage);
    return this;
  }

  public PdfParser withImageBasedCharDetection(boolean imageBasedCharDetection) {
    this.settings.setImageBasedCharDetection(imageBasedCharDetection);
    return this;
  }

  public PdfParser withLineMergeEpsilon(double lineMergeEpsilon) {
    this.settings.setLineMergeEpsilon(lineMergeEpsilon);
    return this;
  }

  public PdfParser withPageNosToOcr(List<Integer> pageNosToOcr) {
    this.settings.setPageNosToOcr(pageNosToOcr);
    return this;
  }

  public PdfParser withImageBasedFormDetection(boolean imageBasedFormDetection) {
    this.settings.setImageBasedFormDetection(imageBasedFormDetection);
    return this;
  }

  public PdfParser withHandWrittenTextDetection(boolean handWrittenTextDetection) {
    this.settings.setHandWrittenTextDetection(handWrittenTextDetection);
    return this;
  }

  public PdfParser withSpacingFactor(double spacingFactor) {
    this.settings.setSpacingFactor(spacingFactor);
    return this;
  }

  public PdfParser withBadPageSignaler(Consumer<Integer> badPageSignaler) {
    this.settings.setBadPageSignaler(badPageSignaler);
    return this;
  }

  public PdfParser withIgnoreNonRenderableText(boolean ignoreNonRenderableText) {
    this.settings.setIgnoreNonRenderableText(ignoreNonRenderableText);
    return this;
  }

  public PdfParser withMaxTextElementToLineCountRatio(double maxTextElementToLineCountRatio) {
    this.settings.setMaxTextElementToLineCountRatio(maxTextElementToLineCountRatio);
    return this;
  }

  public PdfParser withDynamicSpaceWidthComputationEnabled(
      boolean dynamicSpaceWidthComputationEnabled) {
    this.settings.setDynamicSpaceWidthComputationEnabled(dynamicSpaceWidthComputationEnabled);
    return this;
  }

  public PdfParser withScanned(boolean scanned) {
    this.settings.setScanned(scanned);
    return this;
  }

  public PdfParser withPageLevelSpacingScaling(boolean pageLevelSpacingScaling) {
    this.settings.setPageLevelSpacingScaling(pageLevelSpacingScaling);
    return this;
  }

  public PdfParser withAllowedScannedness(double allowedScannedness) {
    this.settings.setAllowedScannedness(allowedScannedness);
    return this;
  }

  public PdfParser withMinChars(int minChars) {
    this.settings.setMinChars(minChars);
    return this;
  }

  public PdfParser withPageLevelOcr(boolean pageLevelOcr) {
    this.settings.setPageLevelOcr(pageLevelOcr);
    return this;
  }

  public PdfParser withMaxPagesAllowed(int maxPagesAllowed) {
    this.settings.setMaxPagesAllowed(maxPagesAllowed);
    return this;
  }

  @Override
  public Document parse(InputStream input) throws Exception {
    Document document = null;
    double maxSegmentationRatio = this.settings.getMaxTextElementToLineCountRatio();
    if (this.settings.getSpacingFactor() > 0) {
      document = this.parseAndCheckScanned(input, this.settings.getSpacingFactor()).getOne();
      if (Double.compare(maxSegmentationRatio, Double.MAX_VALUE) != 0
          && getTextSegmentationRatio(document) > maxSegmentationRatio) {
        throw new BadTextSegmentationException(
            "Required Minimum Text element To Line Count Ratio not achievable");
      }
    } else {
      if (Double.compare(maxSegmentationRatio, Double.MAX_VALUE) == 0) {
        throw new RuntimeException("Invalid maxSegmentationRatio");
      }
      double segmentationRatio = 0;
      double spacingFactor = 1.0;
      byte[] data = IOUtils.toByteArray(input);
      do {
        if (spacingFactor > 2) {
          throw new BadTextSegmentationException(
              "Required Minimum Text element To Line Count Ratio not achievable");
        }
        if (document != null) {
          LOGGER.info(
              "bad segmentation " + format(segmentationRatio) + ", retrying with spacing factor "
                  + format(spacingFactor));
        }
        Pair<Document, Boolean> documentAndIsScanned = this
            .parseAndCheckScanned(new ByteArrayInputStream(data), spacingFactor);
        document = documentAndIsScanned.getOne();
        if (documentAndIsScanned.getTwo()) {
          break;
        }
        segmentationRatio = getTextSegmentationRatio(document);
        spacingFactor += 0.5;
      }
      while (segmentationRatio > maxSegmentationRatio);
    }
    StatsDClientWrapper
        .setValue("totalPagesCount", IterableUtils.size(document.getContainingElements(Page.class)));
    return document;
  }

  /**
   * Parse {@code input} into document model {@see Document} and check if the document is scanned or
   * not.
   *
   * @param input pdf input stream
   * @param spacingFactor factor which will be used in element formation (text segmentation).
   */
  private Pair<Document, Boolean> parseAndCheckScanned(InputStream input, double spacingFactor)
      throws Exception {
    try (PDDocument pdfDocument = PDDocument.load(input)) {
      pdfDocument.setAllSecurityToBeRemoved(true);
      int numberOfpagesInDoc = pdfDocument.getNumberOfPages();
      if (numberOfpagesInDoc > this.settings.getMaxPagesAllowed()) {
        throw new MaxPagesAllowedExceededException("Max parseable pages exceeded in the document");
      }

      if (this.settings.getStartPage() < 0 || this.settings.getStartPage() >= pdfDocument
          .getNumberOfPages() || this.settings.getEndPage() <= this.settings.getStartPage()) {
        throw new IllegalArgumentException("invalid startPage or endPage");
      }

      int endPageForDoc = Math.min(numberOfpagesInDoc, this.settings.getEndPage());
      while (pdfDocument.getNumberOfPages() > endPageForDoc) {
        pdfDocument.removePage(pdfDocument.getNumberOfPages() - 1);
      }
      while (pdfDocument.getNumberOfPages() > endPageForDoc - this.settings.getStartPage()) {
        pdfDocument.removePage(
            pdfDocument.getNumberOfPages() - endPageForDoc + this.settings.getStartPage() - 1);
      }

      PDFDocumentStripper pdfDocumentStripper = null;
      try {
        PDDocumentCatalog docCatalog = pdfDocument.getDocumentCatalog();
        PDAcroForm acroForm = docCatalog.getAcroForm();
        List<PDField> fields = acroForm == null ? Lists.mutable.empty() : acroForm.getFields();
        MutableListMultimap<Integer, FormElement> formElementsByPage = Multimaps.mutable.list
            .empty();
        if (!fields.isEmpty()) {
          PDPageTree pageTree = docCatalog.getPages();
          Map<COSDictionary, Integer> pageNrByAnnotDict = this
              .getPageNumberByAnnotationDictionary(pageTree);
          List<AdjustedPDPage> pages = this.getListOfPages(pageTree);
          for (PDField field : fields) {
            this.handleField(field, pageNrByAnnotDict, pages, formElementsByPage, pageTree);
          }
        }
        pdfDocumentStripper = new PDFDocumentStripper(formElementsByPage, this.settings,
            spacingFactor);
        pdfDocumentStripper.setSortByPosition(true);
        pdfDocumentStripper.getText(pdfDocument);
        Document document = pdfDocumentStripper.getDocument();
        document.add(new PageStructure(PageStructure.FLOW_PAGE_BREAK));
        return Tuples.pair(document, false);
      } catch (UnDigitizedPdfException e) {
        if (this.settings.getScannedPdfParser() == null) {
          throw e;
        }
        LOGGER.warn("pdf cannot be processed via pdfparser because: " + e.getMessage());
        ByteArrayOutputStream oos = new ByteArrayOutputStream();
        pdfDocument.save(oos);
        pdfDocument.close();
        Document document = this.settings.getScannedPdfParser()
            .parse(new ByteArrayInputStream(oos.toByteArray()),
                pdfDocumentStripper != null ? pdfDocumentStripper.getDocument() : null);
        return Tuples.pair(document, true);
      }
    } catch (IOException e) {
      throw new BadOrCorruptedPdfException("Bad Or Corrupted Pdf : " + e.getMessage(), e);
    }
  }

  /**
   * Annotations are defined in the pages that enable user-clickable actions like comment box,
   * navigation etc. They are present in the form of dictionary with atleast two key: Rect and
   * Subtype. For more information about annotations, pease {@see <a href="https://www.oreilly.com/library/view/developing-with-pdf/9781449327903/ch06.html"/>
   *
   * @return map with annotation as key and page number on which the annotation is present as value
   */
  private Map<COSDictionary, Integer> getPageNumberByAnnotationDictionary(PDPageTree pageTree)
      throws IOException {
    Iterator<PDPage> pageIterator = pageTree.iterator();
    Map<COSDictionary, Integer> pageNrByAnnotDict = Maps.mutable.empty();
    int i = 0;
    while (pageIterator.hasNext()) {
      PDPage page = pageIterator.next();
      for (PDAnnotation annotation : page.getAnnotations()) {
        pageNrByAnnotDict.put(annotation.getCOSObject(), i);
      }
      i++;
    }
    return pageNrByAnnotDict;
  }

  /**
   * @return list of pages after taking rotation into account from the pdfbox {@code pageTree}}
   */
  private List<AdjustedPDPage> getListOfPages(PDPageTree pageTree) {
    List<AdjustedPDPage> pages = Lists.mutable.empty();
    for (PDPage page : pageTree) {
      pages.add(new AdjustedPDPage(page));
    }
    return pages;
  }

  /**
   * Construct form elements  and populate map {@code formElementsByPage}
   *
   * @param field field present in interactive form  (Special type of annotation)
   * @param pageNrByAnnotDict map with key ans annotation dictionary and value as page number where
   * that annotation is present
   * @param pages adjusted pages after rotation
   * @param formElementsByPage Output argument. This argument will be populated and will contain key
   * as page index and value as list of form elements
   * @param pageTree page tree object of pdfbox
   */
  private void handleField(PDField field, Map<COSDictionary, Integer> pageNrByAnnotDict,
      List<AdjustedPDPage> pages,
      MutableListMultimap<Integer, FormElement> formElementsByPage, PDPageTree pageTree) {
    if (field instanceof PDNonTerminalField) {
      for (PDField child : ((PDNonTerminalField) field).getChildren()) {
        this.handleField(child, pageNrByAnnotDict, pages, formElementsByPage, pageTree);
      }
    } else {
      COSDictionary fieldDict = field.getCOSObject();
      Integer page = pageNrByAnnotDict.get(fieldDict);
      if (page == null) {
        for (PDAnnotationWidget widget : field.getWidgets()) {
          PDPage widgetPage = widget.getPage();
          if (widgetPage != null) {
            int widgetPageIndex = pageTree.indexOf(widgetPage);
            if (widgetPageIndex >= 0) {
              page = widgetPageIndex;
              break;
            }
          }
        }
      }
      if (page != null) {
        MutableList<float[]> fieldAreaArrays = Lists.mutable.empty();
        if (fieldDict.containsKey(COSName.RECT)) {
          fieldAreaArrays
              .add(((COSArray) fieldDict.getDictionaryObject(COSName.RECT)).toFloatArray());
        } else {
          field.getWidgets().forEach(
              widget -> fieldAreaArrays.add(widget.getRectangle().getCOSArray().toFloatArray()));
        }
        for (int i = 0; i < fieldAreaArrays.size(); i++) {
          float[] fieldAreaArray = fieldAreaArrays.get(i);
          AdjustedPDPage adjustedPage = pages.get(page);
          Point2D fieldXY = adjustedPage.getXYAdj(fieldAreaArray[0], fieldAreaArray[3]);
          double width = Math.abs(fieldAreaArray[2] - fieldAreaArray[0]);
          double height = Math.abs(fieldAreaArray[3] - fieldAreaArray[1]);
          if (width <= 0 || height <= 0 || field instanceof PDPushButton) {
            continue;
          }

          FormType formType = FormType.valueOf(field.getClass().getSimpleName().substring(2));
          FormElement formElement = new FormElement()
              .add(new Top(new Length(adjustedPage.getPageHeightAdj() - fieldXY.getY(), Unit.pt)))
              .add(new Left(new Length(fieldXY.getX(), Unit.pt)))
              .add(new Width(new Length(width, Unit.pt)))
              .add(new Height(new Length(height, Unit.pt)));
          Form form = null;

          if (field instanceof PDButton) {
            PDButton button = (PDButton) field;
            List<String> values = (!(field instanceof PDRadioButton) || !field.getWidgets().get(i)
                .getAppearanceState().equals(COSName.Off)) ?
                Lists.mutable.of(button.getValue()) : Lists.mutable.empty();
            form = new Form(formType, button.getExportValues(), null, button.getOnValues(), values,
                null);
          } else if (field instanceof PDVariableText) {
            PDVariableText variableText = (PDVariableText) field;
            Pair<PDFont, Float> fontInfo;
            try {
              fontInfo = this.findFontInfo(variableText);
            } catch (IOException e) {
              fontInfo = Tuples.pair(PDType1Font.TIMES_ROMAN, 0.0f);
            }
            formElement.add(new FontFamily(fontInfo.getOne().getFontDescriptor().getFontFamily()));
            double fontSize =
                fontInfo.getTwo() > 0 ? fontInfo.getTwo() : height < 14 ? height - 2 : 12;
            formElement.add(new FontSize(new Length(fontSize, Unit.pt)));
            int quadding = variableText.getQ();
            String textAlign =
                quadding == 0 ? TextAlign.LEFT : quadding == 1 ? TextAlign.CENTRE : TextAlign.RIGHT;
            formElement.add(new TextAlign(textAlign));

            if (variableText instanceof PDChoice) {
              PDChoice choice = (PDChoice) variableText;
              form = new Form(formType, choice.getOptionsExportValues(),
                  choice.getOptionsDisplayValues(), null,
                  choice.getValue(), Maps.mutable.of(Form.IS_MULTSELECT, choice.isMultiSelect()));
            } else if (variableText instanceof PDTextField) {
              PDTextField textField = (PDTextField) variableText;
              form = new Form(formType, null, null, null, Lists.mutable.of(textField.getValue()),
                  Maps.mutable.of(Form.IS_MULTILINE, textField.isMultiline()));
            }
          } else if (field instanceof PDSignatureField) {
            form = new Form(formType, null, null, null, Lists.mutable.of(field.getValueAsString()),
                null);
          }

          if (form != null) {
            formElement.add(new FormData(form));
            formElementsByPage.put(page, formElement);
          }
        }
      }
    }
  }

  private Pair<PDFont, Float> findFontInfo(PDVariableText variableText) throws IOException {
    PDResources defaultResources = variableText.getAcroForm().getDefaultResources();
    COSString defaultAppearance = (COSString) this
        .getInheritableAttribute(variableText, COSName.DA);
    if (defaultAppearance != null && defaultResources != null) {
      List<COSBase> arguments = Lists.mutable.empty();
      PDFStreamParser parser = new PDFStreamParser(defaultAppearance.getBytes());
      Object token = parser.parseNextToken();
      while (token != null) {
        if (token instanceof COSObject) {
          arguments.add(((COSObject) token).getObject());
        } else if (token instanceof Operator) {
          Operator operator = (Operator) token;
          if ("Tf".equals(operator.getName())) {
            return this.processFontOperator(arguments, defaultResources);
          }
          arguments = Lists.mutable.empty();
        } else {
          arguments.add((COSBase) token);
        }
        token = parser.parseNextToken();
      }
    }
    throw new IOException("Cannot parse font info from variable text");
  }

  private COSBase getInheritableAttribute(PDField field, COSName key) {
    if (field.getCOSObject().containsKey(key)) {
      return field.getCOSObject().getDictionaryObject(key);
    }
    if (field.getParent() == null) {
      return field.getAcroForm().getCOSObject().getDictionaryObject(key);
    }
    return this.getInheritableAttribute(field.getParent(), key);
  }

  private Pair<PDFont, Float> processFontOperator(List<COSBase> operands,
      PDResources defaultResources) throws IOException {
    if (operands.size() < 2) {
      throw new IOException(
          "Missing operands for set font operator " + Arrays.toString(operands.toArray()));
    }

    COSBase base0 = operands.get(0);
    COSBase base1 = operands.get(1);
    if (!(base0 instanceof COSName)) {
      throw new IOException("First operand not a COSName");
    }
    if (!(base1 instanceof COSNumber)) {
      throw new IOException("Second operand not a COSNumber");
    }

    PDFont font = defaultResources.getFont((COSName) base0);
    float fontSize = ((COSNumber) base1).floatValue();
    return Tuples.pair(font == null ? PDType1Font.TIMES_ROMAN : font, fontSize);
  }

  /**
   * Exception to throw if input pdf is not searchable or scanned
   */
  @SuppressWarnings("serial")
  public static final class UnDigitizedPdfException extends RuntimeException {

    public UnDigitizedPdfException(String message) {
      super(message);
    }
  }

  @SuppressWarnings("serial")
  public static final class BadTextSegmentationException extends RuntimeException {

    public BadTextSegmentationException(String message) {
      super(message);
    }
  }

  @SuppressWarnings("serial")
  public static final class BadOrCorruptedPdfException extends RuntimeException {

    public BadOrCorruptedPdfException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @SuppressWarnings("serial")
  public static final class MaxPagesAllowedExceededException extends RuntimeException {

    public MaxPagesAllowedExceededException(String message) {
      super(message);
    }
  }
}
