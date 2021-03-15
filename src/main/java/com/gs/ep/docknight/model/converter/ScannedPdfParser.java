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

import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementList;
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.Length.Unit;
import com.gs.ep.docknight.model.Parser;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.attribute.Content;
import com.gs.ep.docknight.model.attribute.FontFamily;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.PageLayout;
import com.gs.ep.docknight.model.attribute.PageStructure;
import com.gs.ep.docknight.model.attribute.PositionalContent;
import com.gs.ep.docknight.model.attribute.Text;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.util.ImageUtils;
import com.gs.ep.docknight.util.StatsDClientWrapper;
import com.gs.ep.docknight.util.abbyy.AbbyyAPI;
import com.gs.ep.docknight.util.abbyy.AbbyyParams;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.QuickSort;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.lept.PIX;
import org.bytedeco.javacpp.tesseract;
import org.bytedeco.javacpp.tesseract.ETEXT_DESC;
import org.bytedeco.javacpp.tesseract.ResultIterator;
import org.bytedeco.javacpp.tesseract.TessBaseAPI;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.tuple.Tuples;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser to convert scanned pdf input stream into document model {@see Document}
 */
public class ScannedPdfParser implements Parser<InputStream> {

  protected static final Logger LOGGER = LoggerFactory.getLogger(ScannedPdfParser.class);
  private static final int SPACE_BREAK_THRESHOLD = 4;
  private final int ocrResolution;
  private final double ocrFactor;
  private final OCREngine ocrEngine;
  private AbbyyAPI abbyyAPI;
  private TessBaseAPI tesseractAPI;

  /**
   * @param languages the languages should recognize in the given documents for parsing. For each
   * lang, we expect lang.traineddata file in tessdata dir
   * @param ocrResolution resolution in dpi to use, when converting pdf to images for applying OCR.
   * 300 gives good results.
   */
  public ScannedPdfParser(List<String> languages, int ocrResolution, OCREngine ocrEngine)
      throws Exception {
    if (ocrEngine == OCREngine.TESSERACT) {
      this.tesseractAPI = ImageUtils.getTesseractAPI(languages, tesseract.PSM_AUTO_OSD);
    } else {
      this.abbyyAPI = AbbyyAPI.getAPI();
    }
    this.ocrResolution = ocrResolution;
    this.ocrFactor = ocrResolution / 72.0; // 1 pt is 72 dpi
    this.ocrEngine = ocrEngine;
  }

  public ScannedPdfParser(OCREngine ocrEngine) throws Exception {
    this(Lists.mutable.of("eng"), 300, ocrEngine);
  }

  /**
   * Comparator to sort the words in left to right and then top to bottom reading order
   */
  protected static Comparator<WordInfo> getWordInfoComparator() {
    return (wordInfo1, wordInfo2) ->
    {
      int top1 = wordInfo1.getTop();
      int top2 = wordInfo2.getTop();
      int bottom1 = wordInfo1.getBottom();
      int bottom2 = wordInfo2.getBottom();
      boolean onSameLine = (top1 >= top2 && top1 <= bottom2) || (top2 >= top1 && top2 <= bottom1);
      return onSameLine ? Integer.compare(wordInfo1.getLeft(), wordInfo2.getLeft())
          : Integer.compare(top1, top2);
    };
  }

  /**
   * Populate bounding box and font related information in output argument {@code wordinfo}
   */
  private static void populateWordInfo(ResultIterator resultIterator, WordInfo wordInfo) {
    BytePointer bytePointer = resultIterator.GetUTF8Text(tesseract.RIL_WORD);
    if (bytePointer == null) {
      return;
    }
    wordInfo.word = bytePointer.getString();
    bytePointer.deallocate();

    resultIterator
        .BoundingBox(tesseract.RIL_WORD, wordInfo.leftRef, wordInfo.topRef, wordInfo.rightRef,
            wordInfo.bottomRef);
    String fontFamily = resultIterator
        .WordFontAttributes(wordInfo.isBoldRef, wordInfo.isItalicRef, wordInfo.isUnderlineRef,
            wordInfo.isMonoSpaceRef,
            wordInfo.isSerifRef, wordInfo.isSmallCapsRef, wordInfo.pointSizeRef,
            wordInfo.fontIdRef);

    wordInfo.fontFamily = fontFamily == null ? "" : fontFamily.replaceAll("_", " ");

    PDFont font = null;
    if (wordInfo.fontFamily.contains("Courier")) {
      font = wordInfo.fontFamily.contains("Bold") ? PDType1Font.COURIER : PDType1Font.COURIER_BOLD;
    } else if (wordInfo.fontFamily.contains("Helvetica")) {
      font =
          wordInfo.fontFamily.contains("Bold") ? PDType1Font.HELVETICA : PDType1Font.HELVETICA_BOLD;
    } else if (wordInfo.fontFamily.contains("Times")) {
      font =
          wordInfo.fontFamily.contains("Bold") ? PDType1Font.TIMES_ROMAN : PDType1Font.TIMES_BOLD;
    }

    try {
      if (font == null) {
        throw new RuntimeException("Font can not be Null!");
      }
      wordInfo.fontSize =
          (wordInfo.getRight() - wordInfo.getLeft()) * 1000.0f / font.getStringWidth(wordInfo.word);
    } catch (Exception e) {
      wordInfo.fontSize = wordInfo.getPointSize();
    }
    wordInfo.widthOfSpace =
        (font == null ? PDType1Font.TIMES_ROMAN : font).getSpaceWidth() * wordInfo.fontSize
            / 1000.0f;
  }

  @Override
  public Document parse(InputStream input) throws Exception {
    return this.parse(input, null);
  }

  /**
   * @return digitized document model from scanned pdf input stream {@code input} and scanned pdf
   * document model {@code unDigitizedDocument} using OcrEngine
   */
  public Document parse(InputStream input, Document unDigitizedDocument) throws Exception {
    Document document = null;
    if (this.ocrEngine == OCREngine.TESSERACT) {
      List<Element> pages = Lists.mutable.empty();
      ImageUtils.processImagesFromPdfUsingPdfBox(input, this.ocrResolution, image ->
      {
        List<Element> pageElements = Lists.mutable.empty();
        try {
          pageElements.addAll(this.findTextElements(image));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        Page newPage = new Page()
            .add(new Height(new Length(image.getHeight() / this.ocrFactor, Unit.pt)))
            .add(new Width(new Length(image.getWidth() / this.ocrFactor, Unit.pt)))
            .add(new PositionalContent(new PositionalElementList<>(pageElements)));
        pages.add(newPage);
      });
      StatsDClientWrapper.increment("tesseract_processed_pages_total", pages.size());
      document = new Document()
          .add(new PageStructure(PageStructure.FLOW_PAGE_BREAK))
          .add(new Content(new ElementList<>(pages)));
    } else if (this.ocrEngine == OCREngine.ABBYY) {
      LOGGER.info("Using Abbyy Engine");
      long startTime = System.currentTimeMillis();
      InputStream ocredDoc = this.abbyyAPI.convertPdf(input, new AbbyyParams());
      float timeTaken = (System.currentTimeMillis() - startTime) / 1000.0f;
      LOGGER.info("[{}][{}s] Ocr Done.", this.getClass().getSimpleName(), timeTaken);
      byte[] transformedPdf = IOUtils.toByteArray(ocredDoc);
      document = new PdfParser().withDynamicSpaceWidthComputationEnabled(true)
          .parse(new ByteArrayInputStream(transformedPdf));
      document.addTransformedIntermediateSource(Document.SourceType.OCRED_DOC, transformedPdf);
      StatsDClientWrapper.increment("abbyy_processed_pages_total",
          IterableUtils.size(document.getContainingElements(Page.class)));
    }

    // Populating the digitized document model with page layout information from undigitized document model
    if (document != null) {
      for (Element page : document.getContainingElements(Page.class)) {
        Element unDigitizedPage =
            unDigitizedDocument != null ? unDigitizedDocument.getContent().getElements()
                .get(page.getElementListIndex()) : null;
        List<Pair<Rectangle, String>> pageLayout = unDigitizedPage != null ? unDigitizedPage
            .getAttributeValue(PageLayout.class, Lists.mutable.empty()) : Lists.mutable.empty();
        pageLayout.add(Tuples.pair(
            new Rectangle((int) page.getAttribute(Width.class).getMagnitude(),
                (int) page.getAttribute(Height.class).getMagnitude()), PageLayout.SCANNED));
        page.addAttribute(new PageLayout(pageLayout));
      }
      return document;
    }
    throw new UnsupportedOperationException("Invalid OCR Engine");
  }

  /**
   * @return text elements present in {@code image}
   */
  private List<Element> findTextElements(BufferedImage image) throws IOException {
    PIX pixImage = ImageUtils.toPIXImage(image);
    this.tesseractAPI.SetImage(pixImage);
    this.tesseractAPI.Recognize(new ETEXT_DESC());
    ResultIterator resultIterator = this.tesseractAPI.GetIterator();
    boolean hasText = resultIterator != null;
    List<WordInfo> words = Lists.mutable.empty();

    while (hasText) {
      WordInfo wordInfo = new WordInfo();
      populateWordInfo(resultIterator, wordInfo);

      if (wordInfo.getWord() != null && !wordInfo.getWord().trim().isEmpty()) {
        words.add(wordInfo);
      }
      hasText = resultIterator.Next(
          tesseract.RIL_WORD); // lgtm resultIterator cannot be null, as previous loop does a check
    }

    pixImage.deallocate();
    if (resultIterator != null) {
      resultIterator.deallocate();
    }
    this.tesseractAPI.Clear();

    QuickSort.sort(words, this.getWordInfoComparator());

    List<Element> textElements = Lists.mutable.empty();

    String phrase = "";
    String phraseFontFamily = "";
    boolean isPhraseBold = false;
    boolean isPhraseItalic = false;
    int phraseLeft = 0;
    int phraseRight = 0;
    double phraseTop = 0;
    double phraseFontSize = 0;
    double phraseHeight = 0;
    int wordCount = 0;
    double sumWordTops = 0;
    double sumWordHeights = 0;
    double sumWordFontSizes = 0;

    for (WordInfo wordInfo : words) {
      double numSpacesSinceLastWord =
          (wordInfo.getLeft() - phraseRight) / wordInfo.getWidthOfSpace();

      // Create new text element if the word is starting from new line or existing phrase has number of spaces greated than space break threshold
      if (phraseLeft == 0 || numSpacesSinceLastWord > SPACE_BREAK_THRESHOLD
          || wordInfo.getTop() > phraseTop + phraseHeight) {
        if (wordCount > 0) {
          textElements.add(
              this.createTextElement(phrase, phraseLeft, phraseTop, phraseRight, phraseHeight,
                  phraseFontSize,
                  phraseFontFamily, isPhraseBold, isPhraseItalic));
        }

        phrase = wordInfo.getWord();
        phraseFontFamily = wordInfo.getFontFamily();
        isPhraseBold = wordInfo.isBold();
        isPhraseItalic = wordInfo.isItalic();
        phraseLeft = wordInfo.getLeft();
        phraseRight = wordInfo.getRight();
        phraseTop = wordInfo.getTop();
        phraseFontSize = wordInfo.getFontSize();
        phraseHeight = wordInfo.getHeight();
        wordCount = 1;
        sumWordTops = phraseTop;
        sumWordFontSizes = phraseFontSize;
        sumWordHeights = phraseHeight;
      } else {
        int spaceCount =
            Double.isFinite(numSpacesSinceLastWord) ? new BigDecimal(numSpacesSinceLastWord)
                .setScale(0, BigDecimal.ROUND_DOWN).intValue() : 0;
        String spaceString = StringUtils.repeat(" ", Math.max(1, spaceCount));
        phrase += spaceString + wordInfo.getWord();
        phraseRight = wordInfo.getRight();
        wordCount++;
        sumWordTops += wordInfo.getTop();
        sumWordFontSizes += wordInfo.getFontSize();
        sumWordHeights += wordInfo.getHeight();
        phraseTop = sumWordTops / wordCount;
        phraseFontSize = sumWordFontSizes / wordCount;
        phraseHeight = sumWordHeights / wordCount;
      }
    }

    if (wordCount > 0) {
      textElements.add(
          this.createTextElement(phrase, phraseLeft, phraseTop, phraseRight, phraseHeight,
              phraseFontSize,
              phraseFontFamily, isPhraseBold, isPhraseItalic));
    }
    return textElements;
  }

  /**
   * Creates the text element with content {@code str} and bounding box coordinates ({@code left},
   * {@code top}, {@code right}) and height {@code height} Also, populate font size, font family and
   * text styles associated with text element.
   *
   * @return - the created text element
   */
  private TextElement createTextElement(String str, int left, double top, int right, double height,
      double fontSize, String fontFamily,
      boolean isBold, boolean isItalic) {
    double factoredFontSize = fontSize / this.ocrFactor;
    double roundedToMultiplesOfPointFiveFontSize =
        new BigDecimal(factoredFontSize * 2).setScale(0, RoundingMode.HALF_UP).doubleValue() / 2;
    roundedToMultiplesOfPointFiveFontSize =
        roundedToMultiplesOfPointFiveFontSize > 0 ? roundedToMultiplesOfPointFiveFontSize : 0.5;
    TextElement textElement = new TextElement().add(new Text(str))
        .add(new Left(new Length(left / this.ocrFactor, Unit.pt)))
        .add(new Top(new Length(top / this.ocrFactor, Unit.pt)))
        .add(new Width(new Length(Math.abs(right - left) / this.ocrFactor, Unit.pt)))
        .add(new Height(new Length(height / this.ocrFactor, Unit.pt)))
        .add(new FontSize(new Length(roundedToMultiplesOfPointFiveFontSize, Unit.pt)))
        .add(new FontFamily(fontFamily));

    MutableList<String> textStyles = Lists.mutable.empty();
    if (isBold) {
      textStyles.add(TextStyles.BOLD);
    }
    if (isItalic) {
      textStyles.add(TextStyles.ITALIC);
    }
    if (textStyles.notEmpty()) {
      textElement.add(new TextStyles(textStyles));
    }
    return textElement;
  }

  /**
   * Enum representing different types of OCR Engine available
   */
  public enum OCREngine {
    TESSERACT, ABBYY
  }

  /**
   * Class containing information related to a word
   */
  protected static class WordInfo {

    private final int[] leftRef = {0};
    private final int[] rightRef = {0};
    private final int[] topRef = {0};
    private final int[] bottomRef = {0};
    private final int[] pointSizeRef = {0};
    private final int[] fontIdRef = {0};
    private final boolean[] isBoldRef = {true};
    private final boolean[] isItalicRef = {true};
    private final boolean[] isUnderlineRef = {true};
    private final boolean[] isMonoSpaceRef = {true};
    private final boolean[] isSerifRef = {true};
    private final boolean[] isSmallCapsRef = {true};
    private String word;
    private String fontFamily;
    private float fontSize;
    private float widthOfSpace;

    private int getLeft() {
      return this.leftRef[0];
    }

    private int getRight() {
      return this.rightRef[0];
    }

    private int getTop() {
      return this.topRef[0];
    }

    private int getBottom() {
      return this.bottomRef[0];
    }

    private int getHeight() {
      return this.bottomRef[0] - this.topRef[0];
    }

    private int getPointSize() {
      return this.pointSizeRef[0];
    }

    private boolean isBold() {
      return this.isBoldRef[0];
    }

    private boolean isItalic() {
      return this.isItalicRef[0];
    }

    private String getWord() {
      return this.word;
    }

    private String getFontFamily() {
      return this.fontFamily;
    }

    private float getFontSize() {
      return this.fontSize;
    }

    private float getWidthOfSpace() {
      return this.widthOfSpace;
    }
  }
}
