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

package com.gs.ep.docknight.model.converter.pdfparser;

import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.map.sorted.MutableSortedMap;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.factory.SortedMaps;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.ListIterate;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementList;
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.Length.Unit;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.attribute.AlternateRepresentations;
import com.gs.ep.docknight.model.attribute.BackGroundColor;
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.Content;
import com.gs.ep.docknight.model.attribute.FontFamily;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.LetterSpacing;
import com.gs.ep.docknight.model.attribute.PageColor;
import com.gs.ep.docknight.model.attribute.PageLayout;
import com.gs.ep.docknight.model.attribute.PositionalContent;
import com.gs.ep.docknight.model.attribute.Text;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.converter.PdfParser.UnDigitizedPdfException;
import com.gs.ep.docknight.model.converter.pdfparser.GraphicsExtractor.ColoredArea;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.FormElement;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.element.VerticalLine;
import com.gs.ep.docknight.util.EnglishDictionary;
import com.gs.ep.docknight.util.ImageUtils;
import com.gs.ep.docknight.util.LRUCache;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorN;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingColorN;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingDeviceCMYKColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingDeviceGrayColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingDeviceRGBColor;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDCIDFont;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.QuickSort;
import org.apache.pdfbox.util.Vector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class is used for creation of document model and populating all its properties including text
 * elements etc.
 */
public class PDFDocumentStripper extends PDFTextStripper {

  protected static final Logger LOGGER = LoggerFactory.getLogger(PDFDocumentStripper.class);
  private static final double EPSILON = 0.5;
  private static final double MAX_SPACE_WIDTH = 50;
  private static final int MAX_SPURIOUS_SPACES_ALLOWED = 2;
  private static final int PARAGRAPH_SPACE_BREAK_THRESHOLD = 4;
  private static final int DEFAULT_SPACE_BREAK_THRESHOLD = 2;
  private static final double OP_CHANGE_SPACE_BREAK_THRESHOLD = 1.2;
  private static final int REQUIRED_ALIGNED_SPACES_COUNT = 5;
  private static final double MAX_TAN_OF_TEXT_TILT_FROM_HORIZONTAL = 0.15;
  private static final float HEIGHT_TO_FONT_RATIO = 1.25f;
  private static final double MAX_ABS_LETTER_SPACING = 0.1;
  private static final double MAX_ALLOWED_DEVIATION_IN_EXPECTED_WIDTH = 5;
  private static final int FONT_CACHE_SIZE = 5;
  private static final double ALLOWED_SPACE_COMPRESSION_FACTOR = .325;
  private static final int MAX_UNRECOGNIZED_UNICODE_COUNT = 500;
  private static final float UNDERLINE_DISTANCE_FACTOR = 0.4f;
  private static MutableMap<Character, Character> homoglyphMap;
  private final ParserSettings settings;
  private final double spacingFactor;
  private final MutableListMultimap<Integer, FormElement> formElementsByPage;
  private final LRUCache<FontInfo> fontCache;
  private final GlyphList glyphList;
  private final MutableMap<NullLigatureKey, String> ligatureCache;
  private MutableList<Element> textElements;
  private MutableList<Element> images;
  private List<ImageString> imageStrings;
  private MutableList<Element> otherElements;
  private MutableSortedMap<Float, MutableList<VerticalLine>> verticalLinesGroupedByXPos;
  private MutableSortedMap<Float, MutableList<HorizontalLine>> horizontalLinesGroupedByYPos;
  private List<Element> pages;
  private Document document;
  private PDDocument pdDocument;
  private List<Integer> pageNosToOcr;
  private int numOfPagesWithImagesOnly;
  private int numOfPagesWithBadGlyphs;
  private int textPerpendicularFlipScore;
  private int textRotation;
  private long totalExtraVocabWordsIfSpacingScaled;
  private MutableSet<TextPosition> fillStrokeTextPositions;
  private MutableList<MutableList<WhitespacePosition>> processedWhiteSpacesInCurrentPage;
  private MutableList<WhitespacePosition> processedWhitespacesInCurrentLine;
  private MutableList<MutableList<ProcessedWord>> processWordsInCurrentPage;
  private MutableList<String> wordsIfSpacingScaled;
  private MutableList<ProcessedWord> processedWordsInCurrentLine;
  private MutableList<TextPosition> processedPositions;
  private MutableList<TextPosition> unprocessedNonRenderablePositions;
  private StringBuffer processedText;
  private boolean isLastCharWhiteSpace;
  private boolean areTextElementsNonRenderable;
  private boolean isPagePerpendicularlyFlipped;
  private List<Pair<Rectangle, Integer>> coloredAreas;
  private List<Pair<Rectangle, String>> layoutAreas;
  private double scannedness;
  private double totalScannedness;
  private List<Area> textClippers;
  private MutableList<Integer> numPositionsPerOperation;
  private int textOperationIndex;
  private ColoredArea[][] colorByLocation;
  private MutableListMultimap<Integer, TextPosition> possiblyOverriddenPositions;
  private Map<Pair<Float, Float>, Integer> textColorByLocation;
  private double pageHeight;
  private double pageWidth;
  private int unrecognizedUnicodeCount;
  private Field textPositionMaxHeightField;
  private int lastSpacingSplitElementIndex;
  private int lastSpacingSplitWordIndex;
  private boolean isReportedSpaceWidthScaled;

  public PDFDocumentStripper(MutableListMultimap<Integer, FormElement> formElementsByPage,
      ParserSettings settings, double spacingFactor) throws IOException {
    this.settings = settings;
    this.spacingFactor = spacingFactor;
    this.formElementsByPage = formElementsByPage;
    this.fontCache = new LRUCache<>(FONT_CACHE_SIZE);
    this.ligatureCache = Maps.mutable.empty();
    this.isReportedSpaceWidthScaled = false;

    this.addOperator(new SetStrokingColorSpace());
    this.addOperator(new SetNonStrokingColorSpace());
    this.addOperator(new SetStrokingDeviceCMYKColor());
    this.addOperator(new SetNonStrokingDeviceCMYKColor());
    this.addOperator(new SetNonStrokingDeviceRGBColor());
    this.addOperator(new SetStrokingDeviceRGBColor());
    this.addOperator(new SetNonStrokingDeviceGrayColor());
    this.addOperator(new SetStrokingDeviceGrayColor());
    this.addOperator(new SetStrokingColor());
    this.addOperator(new SetStrokingColorN());
    this.addOperator(new SetNonStrokingColor());
    this.addOperator(new SetNonStrokingColorN());

    String path = "org/apache/pdfbox/resources/glyphlist/additional.txt";
    InputStream input = GlyphList.class.getClassLoader().getResourceAsStream(path);
    this.glyphList = new GlyphList(GlyphList.getAdobeGlyphList(), input);
  }

  private static synchronized void initializeHomoglyphsMap() {
    if (homoglyphMap != null) {
      return;
    }
    try {
      homoglyphMap = Maps.mutable.empty();
      MutableList<String> homoglyphLines = ListAdapter.adapt(IOUtils.readLines(
          PDFDocumentStripper.class.getClassLoader().getResourceAsStream("homoglyphs.txt")));
      for (int homoglyphsFileLineIndex = 1; homoglyphsFileLineIndex < homoglyphLines.size();
          homoglyphsFileLineIndex += 2) {
        Character homoglyphReplacement = homoglyphLines.get(homoglyphsFileLineIndex - 1).charAt(0);
        ArrayAdapter.adapt(homoglyphLines.get(homoglyphsFileLineIndex).split(",")).each(
            unicodeChar -> homoglyphMap.add(Tuples
                .pair(Character.toChars(Integer.decode(unicodeChar))[0], homoglyphReplacement)));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @return first alphanumeric character from the input {@code positions} if possible
   */
  private static TextPosition firstAlphabeticPositionIfPossible(
      MutableList<TextPosition> positions) {
    TextPosition position = positions.detect(p ->
    {
      char ch = p.getUnicode().charAt(0);
      return Character.isAlphabetic(ch) || Character.isDigit(ch);
    });
    return position == null ? positions.getFirst() : position;
  }

  /**
   * Homoglyphs are set of characters that look visually similar but meant different things. This
   * method normalizes these set of characters.
   */
  public static String normalizeHomoglyphs(String originalText) {
    initializeHomoglyphsMap();
    StringBuilder originalTextBuilder = new StringBuilder(originalText);
    for (int textIndex = 0; textIndex < originalText.length(); textIndex++) {
      Character replacement = homoglyphMap.get(originalTextBuilder.charAt(textIndex));
      if (replacement != null) {
        originalTextBuilder.setCharAt(textIndex, replacement);
      }
    }
    return originalTextBuilder.toString();
  }

  private static CMap getToUnicodeCMap(PDFont font) {
    Field toUnicodeCMap;
    try {
      toUnicodeCMap = PDFont.class.getDeclaredField("toUnicodeCMap");
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
    toUnicodeCMap.setAccessible(true);
    try {
      return (CMap) toUnicodeCMap.get(font);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @return width of space character as definer in pdfbox version 2.0
   */
  private static float getSpaceWidthAsPerPdfBoxV2_0_0(PDFont font) {
    float fontWidthOfSpace = -1.0f;
    CMap toUnicodeCMap = getToUnicodeCMap(font);
    try {
      if (toUnicodeCMap != null) {
        int spaceMapping = toUnicodeCMap.getSpaceMapping();
        if (spaceMapping > -1) {
          fontWidthOfSpace = font.getWidth(spaceMapping);
        }
      } else {
        fontWidthOfSpace = font.getWidth(32);
      }
    } catch (Exception e) {
      LOGGER.error("Can't determine the width of the space character, assuming 250", e);
      fontWidthOfSpace = 250f;
    }
    return fontWidthOfSpace;
  }

  /**
   * Determine letter spacing (space between letters) and adjusted font size
   *
   * @return pair of (letter spacing, adjusted font size)
   */
  private static Pair<Double, Double> getLetterSpacingAndAdjustedFontSize(
      StandardizedFont standardizedFont, double fontSize, String str, float expectedWidth) {
    try {
      double standardizedWidthPerFontSize = standardizedFont.font.getStringWidth(str) / 1000;
      double standardizedWidth = standardizedWidthPerFontSize * fontSize;
      double widthDelta = expectedWidth - standardizedWidth;
      if (Math.abs(widthDelta) > MAX_ALLOWED_DEVIATION_IN_EXPECTED_WIDTH) {
        double letterSpacing = widthDelta / (str.length() * fontSize);
        double adjustedFontSize = fontSize;
        if (letterSpacing < -MAX_ABS_LETTER_SPACING) {
          letterSpacing = -MAX_ABS_LETTER_SPACING;
          adjustedFontSize =
              expectedWidth / (standardizedWidthPerFontSize + str.length() * letterSpacing);
        } else if (letterSpacing > MAX_ABS_LETTER_SPACING) {
          letterSpacing = MAX_ABS_LETTER_SPACING;
        }
        return Tuples.pair(letterSpacing, adjustedFontSize);
      }
    } catch (Exception e) {
      return Tuples.pair(0.0, fontSize);
    }
    return Tuples.pair(0.0, fontSize);
  }

  /**
   * Construct a reverse map from X->[Y] using {@code list} and {@code keyFn}
   */
  private static <X, Y> MutableSortedMap<X, MutableList<Y>> sortedGroupBy(List<Y> list,
      Function<Y, X> keyFn) {
    MutableSortedMap<X, MutableList<Y>> result = SortedMaps.mutable.empty();
    for (Y item : list) {
      result.getIfAbsentPutWithKey(keyFn.valueOf(item), k -> Lists.mutable.empty()).add(item);
    }
    return result;
  }

  public Document getDocument() {
    return this.document;
  }

  @Override
  protected void operatorException(Operator operator, List<COSBase> operands, IOException e)
      throws IOException {
    try {
      super.operatorException(operator, operands, e);
    } catch (IOException e1) {
      if (!GraphicsExtractor
          .handleColorOperatorException(operator.getName(), this.getGraphicsState())) {
        throw e1;
      }
    }
  }

  @Override
  protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code, String unicode,
      Vector displacement) throws IOException {
    if (!(font instanceof PDSimpleFont) && font.toUnicode(code, this.glyphList) == null) {
      if (this.unrecognizedUnicodeCount == MAX_UNRECOGNIZED_UNICODE_COUNT) {
        this.numOfPagesWithBadGlyphs++;
        this.settings.getBadPageSignaler().accept(this.pages.size());
      }
      this.unrecognizedUnicodeCount++;
    }
    super.showGlyph(textRenderingMatrix, font, code, unicode, displacement);
  }

  @Override
  protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
    if (operator.getName().equals("BT")) {
      this.textOperationIndex++;
    }
    try {
      super.processOperator(operator, operands);
    } catch (UnsupportedOperationException e) {
      LOGGER.info(e.getMessage());
    }
  }

  @Override
  protected void processTextPosition(TextPosition text) {
    if (text.getHeightDir() > 2 * text.getFontSizeInPt() && text.getFontSizeInPt() > 0) {
      this.correctTextPositionHeight(text);
    }
    PDGraphicsState graphicsState = this.getGraphicsState();
    RenderingMode renderingMode = graphicsState.getTextState().getRenderingMode();
    if (!renderingMode.equals(RenderingMode.NEITHER)) {
      if (this.settings.isScanned()) {
        return;
      }
      this.areTextElementsNonRenderable = false;
      Matrix textMatrix = text.getTextMatrix();
      double tanOfTextTiltFromHorizontal = Math
          .abs(this.isPagePerpendicularlyFlipped ? textMatrix.getScaleX() / textMatrix.getShearY()
              : textMatrix.getShearY() / textMatrix.getScaleX());
      if (tanOfTextTiltFromHorizontal <= MAX_TAN_OF_TEXT_TILT_FROM_HORIZONTAL) {
        this.textPerpendicularFlipScore--;
        int textColor =
            renderingMode.isFill() ? GraphicsExtractor.getNonStrokingColor(graphicsState)
                : GraphicsExtractor.getStrokingColor(graphicsState);
        float xLoc = text.getXDirAdj();
        float yLoc = text.getYDirAdj();
        ColoredArea backGroundColoredArea = this.getBackGroundColoredAreaAtLocation(xLoc, yLoc);
        int backGroundColor =
            backGroundColoredArea == null ? GraphicsExtractor.WHITE : backGroundColoredArea.color;
        int backGroundOperationIndex =
            backGroundColoredArea == null ? 0 : backGroundColoredArea.operationIndex;
        if (textColor != GraphicsExtractor.TRANSPARENT_COLOR && textColor != backGroundColor) {
          if (textColor != GraphicsExtractor.BLACK) {
            if (this.isWaterMark(Math.min(ImageUtils.getLuminance(textColor),
                ImageUtils.getLuminance(backGroundColor)))) {
              return;
            }
            this.textColorByLocation.put(Tuples.pair(xLoc, yLoc), textColor);
          }
          while (this.numPositionsPerOperation.size() < this.textOperationIndex + 1) {
            this.numPositionsPerOperation.add(0);
          }
          this.numPositionsPerOperation.set(this.textOperationIndex,
              this.numPositionsPerOperation.get(this.textOperationIndex) + 1);
          if (this.textOperationIndex <= backGroundOperationIndex || !this
              .isInsideClipArea(xLoc, yLoc)) {
            this.possiblyOverriddenPositions.put(this.textOperationIndex, text);
            return;
          }
          super.processTextPosition(text);
        }
      } else {
        this.textPerpendicularFlipScore++;
      }
      if (renderingMode == RenderingMode.FILL_STROKE) {
        this.fillStrokeTextPositions.add(text);
      }
    } else if (this.areTextElementsNonRenderable && this.settings.extractEmbeddedText()) {
      this.unprocessedNonRenderablePositions.add(text);
    }
  }

  /**
   * Method to fix height obtained from bad glyph boxes
   */
  private void correctTextPositionHeight(TextPosition text) {
    if (this.textPositionMaxHeightField == null) {
      try {
        this.textPositionMaxHeightField = TextPosition.class.getDeclaredField("maxHeight");
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
      this.textPositionMaxHeightField.setAccessible(true);
    }
    try {
      this.textPositionMaxHeightField.set(text, 0.67f * text.getFontSizeInPt());
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void writePage() throws IOException {
    if (this.possiblyOverriddenPositions.notEmpty()) {
      for (Integer operationIndex : this.possiblyOverriddenPositions.keysView()) {
        MutableList<TextPosition> positions = this
            .trimTextPositions(this.possiblyOverriddenPositions.get(operationIndex));
        if (positions.size() < 3 && this.numPositionsPerOperation.get(operationIndex) > 2) {
          for (TextPosition position : positions) {
            super.processTextPosition(position);
          }
        }
      }
    }
    super.writePage();
  }

  /**
   * @return positions after removing positions from beginning and end of {@code positions} which
   * contain only white spaces
   */
  private MutableList<TextPosition> trimTextPositions(MutableList<TextPosition> positions) {
    int start = positions
        .detectIndex(p -> !SemanticsChecker.isWhiteSpace(p.getUnicode().charAt(0)));
    if (start < 0) {
      return Lists.mutable.empty();
    }
    int end =
        positions.detectLastIndex(p -> !SemanticsChecker.isWhiteSpace(p.getUnicode().charAt(0)))
            + 1;
    if (start > 0 || end < positions.size()) {
      return positions.subList(start, end);
    }
    return positions;
  }

  /**
   * Clipped area is a the region in which the content is visible to users and anything outside of
   * it is present in document but is hidden from the users.
   *
   * @return True if coordinates ({@code xLoc}, {@code yLoc}) is inside clip area
   */
  private boolean isInsideClipArea(float xLoc, float yLoc) {
    return this.textClippers.get(this.textOperationIndex)
        .contains(xLoc + 2, this.pageHeight - yLoc + 2);
  }

  private boolean isWaterMark(double luminance) {
    return luminance > 0.753;
  }

  /**
   * @return color present at location {@code x} and {@code y}
   */
  private ColoredArea getBackGroundColoredAreaAtLocation(float x, float y) {
    int x1 = (int) Math.ceil(x);
    int y1 = (int) Math.floor(y);

    return x1 < 0 || y1 < 0 || x1 > this.pageWidth || y1 > this.pageHeight ? null
        : this.colorByLocation[x1][y1];
  }

  private float getWidth(TextPosition textPosition) {
    float width = textPosition.getWidthDirAdj();
    if (width <= 0) {
      width = this.getFontInfo(textPosition).widthOfSpace * 0.70f;
    }
    return width;
  }

  @Override
  protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
    int i = -1;
    TextPosition lastTextPosition = this.processedPositions.getLast();
    boolean skipChars = false;
    int maxSkipChars = text.length() - textPositions.size();
    for (TextPosition textPosition : textPositions) {
      // handle the case when unicode chars like ffi (U+FB03) and ff (U+FB00) are expanded by PDFTextStripper#normalizeWord()
      char unprocessedChar = textPosition.getUnicode().charAt(0);
      int numSkipChars = 1;
      if (skipChars) {
        while (text.charAt(i + numSkipChars) != unprocessedChar && maxSkipChars > 0) {
          numSkipChars++;
          maxSkipChars--;
        }
      }
      i += numSkipChars;
      char ch = text.charAt(i);
      skipChars = ch != unprocessedChar && maxSkipChars > 0;
      String imageStringPrepend = null;
      if (!this.imageStrings.isEmpty() && Character.isDigit(ch))  // applicable in context of digits
      {
        ImageString textPositionAsImageString = new ImageString(textPosition);
        int index =
            Math.abs(Collections.binarySearch(this.imageStrings, textPositionAsImageString) + 1)
                - 1;
        if (index >= 0) {
          ImageString imageString = this.imageStrings.get(index);
          int distance = textPositionAsImageString.compareTo(imageString);
          if (distance < 2 * textPosition.getWidthDirAdj() && (lastTextPosition == null
              || imageString.left > lastTextPosition.getXDirAdj())) {
            imageStringPrepend = imageString.str;
          }
        }
      }

      if (SemanticsChecker.isWhiteSpace(ch)) {
        this.isLastCharWhiteSpace = true;
      } else {
        if (lastTextPosition != null) {
          double widthOfSpace = this.getFontInfo(lastTextPosition).widthOfSpace;
          double distanceFromLastNonSpaceChar =
              textPosition.getXDirAdj() - lastTextPosition.getXDirAdj() - this
                  .getWidth(lastTextPosition);

          //need Math.abs(textPosition.getHeightDir()) here because in some cases negative fonts are found.
          if (textPosition.getYDirAdj() - HEIGHT_TO_FONT_RATIO * Math
              .abs(textPosition.getHeightDir()) > lastTextPosition.getYDirAdj()
              || distanceFromLastNonSpaceChar < -widthOfSpace) {
            this.updateExtraVocWordCountIfSpacingScaled();
            this.processedWordsInCurrentLine
                .add(new ProcessedWord(this.processedPositions, this.processedText.toString()));
            this.processWordsInCurrentPage.add(this.processedWordsInCurrentLine);
            this.processedWordsInCurrentLine = Lists.mutable.empty();
            this.processedPositions = Lists.mutable.empty();
            this.processedText.setLength(0);
            this.wordsIfSpacingScaled = Lists.mutable.empty();

            this.processedWhiteSpacesInCurrentPage.add(this.processedWhitespacesInCurrentLine);
            this.processedWhitespacesInCurrentLine = Lists.mutable.empty();
          } else if (((this.isLastCharWhiteSpace || this.isReportedSpaceWidthScaled)
              && distanceFromLastNonSpaceChar > ALLOWED_SPACE_COMPRESSION_FACTOR * widthOfSpace)
              || distanceFromLastNonSpaceChar > Math.max(widthOfSpace - EPSILON, 0)) {
            WhitespacePosition whitespacePosition = new WhitespacePosition(
                lastTextPosition.getXDirAdj() + this.getWidth(lastTextPosition),
                textPosition.getXDirAdj(),
                widthOfSpace, !this.isLastCharWhiteSpace ? OP_CHANGE_SPACE_BREAK_THRESHOLD : -1);
            this.processedWhitespacesInCurrentLine.add(whitespacePosition);
            this.updateExtraVocWordCountIfSpacingScaled();
            this.processedWordsInCurrentLine
                .add(new ProcessedWord(this.processedPositions, this.processedText.toString()));
            this.processedText.setLength(0);
            this.processedPositions = Lists.mutable.empty();
            this.wordsIfSpacingScaled = Lists.mutable.empty();
          } else if (this.settings.isPageLevelSpacingScaled()
              && distanceFromLastNonSpaceChar > ALLOWED_SPACE_COMPRESSION_FACTOR * widthOfSpace) {
            int startIndex = (int) this.wordsIfSpacingScaled.sumOfInt(String::length);
            this.wordsIfSpacingScaled.add(this.processedText.substring(startIndex));
          }
        }
        this.processedPositions.add(textPosition);
        if (imageStringPrepend != null) {
          this.processedText.append(imageStringPrepend);
        }
        this.processedText.append(text.substring(i - numSkipChars + 1, i + 1));
        lastTextPosition = textPosition;
        this.isLastCharWhiteSpace = false;
      }
    }
  }

  /**
   * If word without scaling is not present in dictionary, then update the
   * totalExtraVocabWordsIfSpacingScaled count if scaled word is present in english dictionary
   */
  private void updateExtraVocWordCountIfSpacingScaled() {
    String probableWord = ArrayAdapter.adapt(this.processedText.toString().split("\\W"))
        .detect(word -> !word.isEmpty());
    if (probableWord != null && !EnglishDictionary.getDictionary()
        .contains(probableWord.toLowerCase())) {
      this.totalExtraVocabWordsIfSpacingScaled += this.wordsIfSpacingScaled
          .count(word -> word.length() > 2
              && EnglishDictionary.getDictionary().contains(word.toLowerCase()));
    }
  }

  /**
   * Construct justified Text info for this page
   */
  private MutableList<JustifiedTextInfo> doJustifiedTextProcessing() {
    int totalLines = this.processedWhiteSpacesInCurrentPage.size();

    MutableList<Float> interLineSpaces = Lists.mutable.empty();
    MutableList<Pair<Float, Float>> lineAlignments = Lists.mutable.empty();
    MutableList<JustifiedTextInfo> justifiedSpaceInfo = Lists.mutable.empty();

    for (int currentLineIdx = 0; currentLineIdx < totalLines; currentLineIdx++) {
      int succeedingLineIdx = currentLineIdx + 1;

      // Find Spacing between consecutive lines
      if (currentLineIdx < totalLines - 1) {
        TextPosition currentLineFirstChar = this.processWordsInCurrentPage.get(currentLineIdx)
            .get(0).wordTextPositions.get(0);
        TextPosition succeedingLineFirstChar = this.processWordsInCurrentPage.get(succeedingLineIdx)
            .get(0).wordTextPositions.get(0);
        float interLineSpace =
            succeedingLineFirstChar.getYDirAdj() - succeedingLineFirstChar.getHeightDir()
                - currentLineFirstChar.getYDirAdj();
        interLineSpaces.add(interLineSpace);
      }

      // Find the line alignments (Starting coordingate, end coordinate) of line
      TextPosition currentLinefirstChar = this.processWordsInCurrentPage.get(currentLineIdx)
          .get(0).wordTextPositions.get(0);
      TextPosition currentLinelastChar = this.processWordsInCurrentPage.get(currentLineIdx)
          .getLast().wordTextPositions.getLast();
      lineAlignments.add(Tuples.pair(currentLinefirstChar.getXDirAdj(),
          currentLinelastChar.getXDirAdj() + currentLinelastChar.getWidthDirAdj()));

      // Find the width of spaces in the current line
      MutableList<WhitespacePosition> currentLineSpaces = this.processedWhiteSpacesInCurrentPage
          .get(currentLineIdx);
      MutableList<Pair<Integer, Float>> spaceWidths = Lists.mutable.empty();
      for (int currentSpaceIdx = 0; currentSpaceIdx < currentLineSpaces.size(); currentSpaceIdx++) {
        WhitespacePosition currentWhitespace = currentLineSpaces.get(currentSpaceIdx);
        spaceWidths
            .add(Tuples.pair(currentSpaceIdx, currentWhitespace.xRight - currentWhitespace.xLeft));
      }

      MutableList<Pair<Integer, Float>> sortedSpaceWidths = spaceWidths.sortThis((entry1, entry2) ->
      {
        return Float.compare(entry1.getTwo(), entry2.getTwo());
      });

      // Find the number of spurious whitespaces and the majority space widths for this line
      float width1 = 0;
      float width2 = 0;
      if (sortedSpaceWidths.notEmpty()) {
        int maxMajorityWidthSpaceCount = 1;
        int majorityWidthSpaceCount = 1;
        float majoritySpaceWidth = sortedSpaceWidths.get(0).getTwo();
        for (int idx = 1; idx < sortedSpaceWidths.size(); idx++) {
          width1 = sortedSpaceWidths.get(idx - 1).getTwo();
          width2 = sortedSpaceWidths.get(idx).getTwo();
          if (Math.abs(width1 - width2) < EPSILON) {
            majorityWidthSpaceCount += 1;
          } else {
            if (majorityWidthSpaceCount > maxMajorityWidthSpaceCount) {
              majoritySpaceWidth = width1;
              maxMajorityWidthSpaceCount = majorityWidthSpaceCount;
            }
            majorityWidthSpaceCount = 1;
          }
        }
        if (majorityWidthSpaceCount > maxMajorityWidthSpaceCount) {
          majoritySpaceWidth = width1;
          maxMajorityWidthSpaceCount = majorityWidthSpaceCount;
        }
        justifiedSpaceInfo.add(
            new JustifiedTextInfo(sortedSpaceWidths.size() - maxMajorityWidthSpaceCount,
                majoritySpaceWidth, false));
      } else {
        justifiedSpaceInfo.add(new JustifiedTextInfo(0, 0, false));
      }
    }

    // Check whether line is left aligned and there is constant space between lines.
    for (int idx = interLineSpaces.size() - 1; idx > 0; idx--) {
      if (Math.abs(interLineSpaces.get(idx) - interLineSpaces.get(idx - 1)) < EPSILON) {
        if (Math.abs(lineAlignments.get(idx).getOne() - lineAlignments.get(idx + 1).getOne())
            < EPSILON) {
          justifiedSpaceInfo.get(idx).isLeftAlignedAndConstantInterLineSpace = true;
          justifiedSpaceInfo.get(idx + 1).isLeftAlignedAndConstantInterLineSpace = true;
        }

        if (Math.abs(lineAlignments.get(idx).getOne() - lineAlignments.get(idx - 1).getOne())
            < EPSILON) {
          justifiedSpaceInfo.get(idx - 1).isLeftAlignedAndConstantInterLineSpace = true;
          justifiedSpaceInfo.get(idx).isLeftAlignedAndConstantInterLineSpace = true;
        }
      }
    }
    return justifiedSpaceInfo;
  }

  private void processPage() {
    int totalLines = this.processedWhiteSpacesInCurrentPage.size();

    MutableList<JustifiedTextInfo> justifiedTextInfos = Lists.mutable.empty();
    if (this.settings.isDynamicSpaceWidthComputationEnabled()) {
      justifiedTextInfos = this.doJustifiedTextProcessing();
    }

    for (int currentLineIdx = 0; currentLineIdx < totalLines; currentLineIdx++) {
      MutableList<WhitespacePosition> currentLineSpaces = this.processedWhiteSpacesInCurrentPage
          .get(currentLineIdx);
      int whitespacesCount = currentLineSpaces.size();

      int startWordIndex = 0;
      int endWordIndex = 0;
      for (int currentSpaceIdx = 0; currentSpaceIdx < whitespacesCount; currentSpaceIdx++) {
        WhitespacePosition currentWhitespace = currentLineSpaces.get(currentSpaceIdx);

        int aligningSpacesCount = 0;
        int skippedLinesCount = 0;
        int consecutiveSkippedLinesCount = 1;
        int lastSkippedLineIndex = 0;
        MutableList<Pair<Integer, Integer>> intersectingSpaceIndices = Lists.mutable
            .of(Tuples.pair(currentLineIdx, currentSpaceIdx));

        int succeedingLineIdx = currentLineIdx + 1;
        Pair<Float, Float> intersectingSpaceInterval = Tuples
            .pair(currentWhitespace.xLeft, currentWhitespace.xRight);

        //check in consecutive lines (allow skipping) for the aligned spaces.
        while (succeedingLineIdx < totalLines
            && aligningSpacesCount + skippedLinesCount + currentLineIdx + 1 == succeedingLineIdx
            // there should be no line which is not skipped and not aligning
            && aligningSpacesCount < REQUIRED_ALIGNED_SPACES_COUNT
            && skippedLinesCount <= 2 * REQUIRED_ALIGNED_SPACES_COUNT
            // worst case between each row of the table there is are two lines which can be skipped.
            && consecutiveSkippedLinesCount <= 2) {
          float maxXCoordinateCurrentLine = this.getMaxXCoordinate(succeedingLineIdx);
          float minXCoordinateCurrentLine = this.getMinXCoordinate(succeedingLineIdx);

          if (maxXCoordinateCurrentLine <= intersectingSpaceInterval.getOne()
              || minXCoordinateCurrentLine >= intersectingSpaceInterval.getTwo()) {
            skippedLinesCount++;
            consecutiveSkippedLinesCount =
                lastSkippedLineIndex == succeedingLineIdx - 1 ? consecutiveSkippedLinesCount + 1
                    : 1;
            lastSkippedLineIndex = succeedingLineIdx;
          } else {
            MutableList<WhitespacePosition> succeedingLineSpaces = this.processedWhiteSpacesInCurrentPage
                .get(succeedingLineIdx);
            int l = 0;
            if (succeedingLineSpaces.notEmpty()) {
              WhitespacePosition succeedingWhitespace = succeedingLineSpaces.get(l);
              while (intersectingSpaceInterval.getTwo() >= succeedingWhitespace.xLeft + EPSILON
                  && l < succeedingLineSpaces.size()) {
                if (succeedingWhitespace.xRight > intersectingSpaceInterval.getOne() + EPSILON) {
                  intersectingSpaceIndices.add(Tuples.pair(succeedingLineIdx, l));
                  intersectingSpaceInterval = Tuples.pair(
                      Math.max(intersectingSpaceInterval.getOne(), succeedingWhitespace.xLeft),
                      Math.min(intersectingSpaceInterval.getTwo(), succeedingWhitespace.xRight));
                  aligningSpacesCount++;
                  break;
                }
                l++;

                if (l != succeedingLineSpaces.size()) {
                  succeedingWhitespace = succeedingLineSpaces.get(l);
                }
              }
            }
          }
          succeedingLineIdx++;
        }

        //set breakThreshold depending on the alignment
        if (aligningSpacesCount == REQUIRED_ALIGNED_SPACES_COUNT || (succeedingLineIdx == totalLines
            && succeedingLineIdx - skippedLinesCount - currentLineIdx - 1 == aligningSpacesCount)) {
          for (Pair<Integer, Integer> intersectingSpacesIndexPair : intersectingSpaceIndices) {
            MutableList<WhitespacePosition> whitespacesInCurrentLine = this.processedWhiteSpacesInCurrentPage
                .get(intersectingSpacesIndexPair.getOne());
            if (whitespacesInCurrentLine.notEmpty()) {
              WhitespacePosition whitespacePosition = whitespacesInCurrentLine
                  .get(intersectingSpacesIndexPair.getTwo());
              if (whitespacePosition.spaceBreakThreshold == -1) {
                whitespacePosition.spaceBreakThreshold = DEFAULT_SPACE_BREAK_THRESHOLD;
              }
            }
          }
        } else if (currentWhitespace.spaceBreakThreshold == -1) {
          currentWhitespace.spaceBreakThreshold =
              this.isCurrentWhiteSpaceAcrossLine(currentLineIdx, endWordIndex, currentWhitespace)
                  ? DEFAULT_SPACE_BREAK_THRESHOLD : PARAGRAPH_SPACE_BREAK_THRESHOLD;
        }

        //decide whether a textElement should be created or not.
        double widthOfSpace = currentWhitespace.widthOfSpace;
        if (this.settings.isDynamicSpaceWidthComputationEnabled() && justifiedTextInfos.notEmpty()
            && justifiedTextInfos.get(currentLineIdx).isLinePartofJustifiedtext()) {
          widthOfSpace = justifiedTextInfos.get(currentLineIdx).majoritySpaceWidth;
          currentWhitespace.spaceBreakThreshold = PARAGRAPH_SPACE_BREAK_THRESHOLD;
        }

        boolean isSpacingSame = currentWhitespace.xRight - currentWhitespace.xLeft
            < currentWhitespace.spaceBreakThreshold * widthOfSpace * this.spacingFactor + EPSILON;
        boolean isFontSame = this.isFontSame(currentLineIdx, endWordIndex + 1);
        if (isSpacingSame && isFontSame) {
          endWordIndex++;
        } else {
          this.createTextElement(startWordIndex, endWordIndex, currentLineIdx, isSpacingSame);
          startWordIndex = currentSpaceIdx + 1;
          endWordIndex = currentSpaceIdx + 1;
        }
      }
      this.createTextElement(startWordIndex, endWordIndex, currentLineIdx, false);
    }
  }

  /**
   * @return True if word at position {@code wordIndex} has same font (text styles) as word at
   * position {@code wordIndex}-1 in line at index {@code currentLineIdx}
   */
  private boolean isFontSame(int currentLineIdx, int wordIndex) {
    if (this.settings.isFontChangeSegmentation()) {
      MutableList<ProcessedWord> words = this.processWordsInCurrentPage.get(currentLineIdx);
      TextPosition prevPosition = firstAlphabeticPositionIfPossible(
          words.get(wordIndex - 1).wordTextPositions);
      TextPosition currPosition = firstAlphabeticPositionIfPossible(
          words.get(wordIndex).wordTextPositions);
      FontInfo prevFontInfo = this.getFontInfo(prevPosition);
      FontInfo currFontInfo = this.getFontInfo(currPosition);
      double fontSizeChangeFactor =
          Math.abs(currFontInfo.fontSize - prevFontInfo.fontSize) / prevFontInfo.fontSize;
      boolean sameStyles = prevFontInfo.standardizedFont.textStyles.size()
          == currFontInfo.standardizedFont.textStyles.size()
          && prevFontInfo.standardizedFont.textStyles
          .containsAll(currFontInfo.standardizedFont.textStyles);
      return sameStyles && fontSizeChangeFactor <= 0.2 && this.isUnderlined(prevPosition) == this
          .isUnderlined(currPosition);
    }
    return true;
  }

  /**
   * @return True if text position has underline else False
   */
  private boolean isUnderlined(TextPosition position) {
    if (!this.settings.isUnderlineDetection()) {
      return false;
    }
    float x = position.getXDirAdj() + this.getWidth(position) / 2;
    float y = position.getYDirAdj();
    float h = position.getHeightDir() == 0 ? 5 : Math.abs(position.getHeightDir());
    float u = UNDERLINE_DISTANCE_FACTOR * h;
    MutableSortedMap<Float, MutableList<HorizontalLine>> candidateUnderLines = this.horizontalLinesGroupedByYPos
        .subMap(y - u, y + u);
    return candidateUnderLines.anySatisfy(lines ->
        lines.anySatisfy(line -> line.getLeft().getMagnitude() < x
            && line.getLeft().getMagnitude() + line.getStretch().getMagnitude() > x));
  }

  private boolean isCurrentWhiteSpaceAcrossLine(int currentLineIdx, int endWordIndex,
      WhitespacePosition currentWhitespace) {
    MutableSortedMap<Float, MutableList<VerticalLine>> verticalLinesAtWhiteSpaceXPosition = this.verticalLinesGroupedByXPos
        .subMap(currentWhitespace.xLeft, currentWhitespace.xRight);
    float whiteSpaceY = this.processWordsInCurrentPage.get(currentLineIdx)
        .get(endWordIndex).wordTextPositions.getLast().getYDirAdj();
    return verticalLinesAtWhiteSpaceXPosition.anySatisfy(lines ->
        lines.anySatisfy(line -> line.getTop().getMagnitude() < whiteSpaceY
            && line.getTop().getMagnitude() + line.getStretch().getMagnitude() > whiteSpaceY));
  }

  /**
   * @return the x coordinate of first word present in line at index {@code lineNo}
   */
  private float getMinXCoordinate(int lineNo) {
    return this.processWordsInCurrentPage.get(lineNo).getFirst().wordTextPositions.getFirst()
        .getXDirAdj();
  }

  /**
   * @return the x coordinate of last word present in line at index {@code lineNo}
   */
  private float getMaxXCoordinate(int lineNo) {
    TextPosition lastTextPosition = this.processWordsInCurrentPage.get(lineNo)
        .getLast().wordTextPositions.getLast();
    return lastTextPosition.getXDirAdj() + this.getWidth(lastTextPosition);
  }

  /**
   * Replace null ligatures with actual ligatures using ligaturized dictionary
   */
  private String fixNullLigatures(MutableList<TextPosition> positions, String str) {
    if (str.contains("\0")) {
      MutableList<IndexedWord> indexedWords = SemanticsChecker
          .splitIntoIndexedWords(str, false, false);
      MutableList<Pair<Integer, String>> replacements = Lists.mutable.empty();
      for (IndexedWord indexedWord : indexedWords) {
        int nullIndex = -1;
        int nullPosIndex = -1;
        String word = indexedWord.getString().toLowerCase();
        while ((nullIndex = word.indexOf("\0", nullIndex + 1)) >= 0
            && (nullPosIndex = positions.detectIndex(p -> p.getUnicode().equals("\0"))) >= 0) {
          NullLigatureKey nullLigatureKey = new NullLigatureKey(positions.get(nullPosIndex));
          String ligature = this.ligatureCache.get(nullLigatureKey);
          if (ligature == null) {
            ligature = EnglishDictionary.getLigaturizedDictionary().get(word);
            if (ligature != null) {
              this.ligatureCache.put(nullLigatureKey, ligature);
            }
          }
          if (ligature != null) {
            replacements.add(Tuples.pair(indexedWord.getIndex() + nullIndex, ligature));
          }
          positions = positions.subList(nullPosIndex + 1, positions.size());
        }
      }

      int repIndex = 0;
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < str.length(); i++) {
        if (str.charAt(i) != '\0') {
          sb.append(str.charAt(i));
        } else if (repIndex < replacements.size() && i == replacements.get(repIndex).getOne()) {
          sb.append(replacements.get(repIndex).getTwo());
          repIndex++;
        } else {
          sb.append('\0');
        }
      }
      return sb.toString();
    }
    return str;
  }

  /**
   * Create the text element using all the words from {@code startWordIndex} to {@code endWordIndex}
   * present in line index {@code lineIndex}
   */
  private void createTextElement(int startWordIndex, int endWordIndex, int lineIndex,
      boolean isSpacingSame) {
    MutableList<TextPosition> nonNullPositions = Lists.mutable.empty();
    MutableList<ProcessedWord> currentLine = this.processWordsInCurrentPage.get(lineIndex);
    for (int wordIndex = startWordIndex; wordIndex <= endWordIndex; wordIndex++) {
      nonNullPositions.addAll(currentLine.get(wordIndex).wordTextPositions);
    }

    String textElementStr = this.getTextStrForTextElement(startWordIndex, endWordIndex, lineIndex);
    textElementStr = this.fixNullLigatures(nonNullPositions, textElementStr);
    if (textElementStr.isEmpty()) {
      return;
    }
    TextPosition firstPosition = nonNullPositions.getFirst();
    TextPosition lastPosition = nonNullPositions.getLast();

    float left = firstPosition.getXDirAdj();
    double bottom = nonNullPositions.collectFloat(TextPosition::getYDirAdj).average();
    double height =
        nonNullPositions.collectFloat(textPosition -> Math.abs(textPosition.getHeightDir()))
            .average() * HEIGHT_TO_FONT_RATIO;
    float right = lastPosition.getXDirAdj() + this.getWidth(lastPosition);
    float width = right - left;

    TextPosition positionForFont =
        this.settings.isFontChangeSegmentation() ? firstAlphabeticPositionIfPossible(
            nonNullPositions) : firstPosition;
    StandardizedFont standardizedFont = this.getFontInfo(positionForFont).standardizedFont;
    double fontSize = nonNullPositions.collectFloat(p -> this.getFontInfo(p).fontSize).average();
    Pair<Double, Double> letterSpacingAndAdjustedFontSize = getLetterSpacingAndAdjustedFontSize(
        standardizedFont, fontSize, textElementStr, width);
    Integer textColor = this.textColorByLocation.get(Tuples.pair(left, firstPosition.getYDirAdj()));
    // sometimes height is zero, try alternative guess
    height = height > 0 ? height
        : standardizedFont.font.getFontDescriptor().getCapHeight() * fontSize * HEIGHT_TO_FONT_RATIO
            / 1000.0;
    boolean isUnderlined = this.isUnderlined(positionForFont);
    ColoredArea backGroundColoredArea = this
        .getBackGroundColoredAreaAtLocation(positionForFont.getXDirAdj(),
            positionForFont.getYDirAdj());
    boolean isBoldDueToFillStroke =
        standardizedFont.textStyles.noneSatisfy(style -> style.equals(TextStyles.BOLD))
            && this.fillStrokeTextPositions.contains(firstPosition);
    MutableList<String> textStyles = standardizedFont.textStyles;
    if (isUnderlined || isBoldDueToFillStroke) {
      textStyles = Lists.mutable.ofAll(textStyles);
      if (isUnderlined) {
        textStyles = textStyles.with(TextStyles.UNDERLINE);
      }
      if (isBoldDueToFillStroke) {
        textStyles = textStyles.with(TextStyles.BOLD);
      }
    }

    TextElement textElement = new TextElement().add(new Text(textElementStr))
        .add(new Left(new Length(left, Unit.pt)))
        .add(new Top(new Length(bottom - height, Unit.pt)))
        .add(new Width(new Length(width, Unit.pt)))
        .add(new Height(new Length(height, Unit.pt)))
        .add(new FontSize(new Length(letterSpacingAndAdjustedFontSize.getTwo(), Unit.pt)))
        .add(new FontFamily(standardizedFont.fontFamily));

    if (textStyles.notEmpty()) {
      textElement.add(new TextStyles(textStyles));
    }
    if (textColor != null) {
      textElement.add(new Color(new java.awt.Color(textColor)));
    }
    if (letterSpacingAndAdjustedFontSize.getOne() != 0) {
      textElement
          .add(new LetterSpacing(new Length(letterSpacingAndAdjustedFontSize.getOne(), Unit.em)));
    }
    if (backGroundColoredArea != null && backGroundColoredArea.color != GraphicsExtractor.WHITE) {
      textElement.add(new BackGroundColor(new java.awt.Color(backGroundColoredArea.color)));
    }
    this.textElements.add(textElement);

    if (!isSpacingSame) {
      if (this.lastSpacingSplitElementIndex < this.textElements.size() - 1) {
        List<Element> styledSubTextElements = Lists.mutable.ofAll(
            this.textElements.subList(this.lastSpacingSplitElementIndex, this.textElements.size()));
        for (int i = 0; i < styledSubTextElements.size(); i++) {
          this.textElements.remove(this.textElements.size() - 1);
        }
        this.createTextElement(this.lastSpacingSplitWordIndex, endWordIndex, lineIndex, false);
        this.textElements.getLast()
            .addAttribute(new AlternateRepresentations(styledSubTextElements));
      } else {
        this.lastSpacingSplitElementIndex = this.textElements.size();
        this.lastSpacingSplitWordIndex =
            endWordIndex == this.processWordsInCurrentPage.get(lineIndex).size() - 1 ? 0
                : endWordIndex + 1;
      }
    }
  }

  /**
   * @return text generated using words from {@code startWordIndex} to {@code endWordIndex} on line
   * {@code lineIndex}
   */
  private String getTextStrForTextElement(int startWordIndex, int endWordIndex, int lineIndex) {
    StringBuffer text = new StringBuffer();
    MutableList<WhitespacePosition> whitespacePositionsInCurrentLine = this.processedWhiteSpacesInCurrentPage
        .get(lineIndex);
    MutableList<ProcessedWord> processedWords = this.processWordsInCurrentPage.get(lineIndex);
    while (startWordIndex <= endWordIndex) {
      text.append(processedWords.get(startWordIndex).wordText);
      if (startWordIndex != endWordIndex) {
        WhitespacePosition whitespacePosition = whitespacePositionsInCurrentLine
            .get(startWordIndex);
        int numSpaces = (int) ((whitespacePosition.xRight - whitespacePosition.xLeft + EPSILON)
            / whitespacePosition.widthOfSpace);
        numSpaces = numSpaces == 0 ? 1 : numSpaces;

        text.append(StringUtils.repeat(" ", numSpaces));
      }
      startWordIndex++;
    }
    return normalizeHomoglyphs(text.toString());
  }

  /**
   * Construct font info object from the input {@code position}
   */
  private FontInfo getFontInfo(TextPosition position) {
    PDFont font = position.getFont();
    float fontSizeStored = Math
        .abs(position.getFontSizeInPt());  // sometimes pdfbox gives negative values
    FontInfo fontInfo = this.fontCache
        .get(f -> f.font == font && f.storedFontSize == fontSizeStored);

    if (fontInfo == null) {
      float widthOfSpace = position.getWidthOfSpace();
      float widthOfSpacePerFontSize =
          font instanceof PDType0Font ? getSpaceWidthAsPerPdfBoxV2_0_0(font) : font.getSpaceWidth();
      widthOfSpacePerFontSize = widthOfSpacePerFontSize > 0 ? widthOfSpacePerFontSize : 250;
      float fontSize = fontSizeStored > 6 * widthOfSpace && widthOfSpace > 0 ? widthOfSpace * 1000
          / widthOfSpacePerFontSize : fontSizeStored; // font correction
      float fontSizeFromWidth;
      try {
        fontSizeFromWidth =
            position.getWidthDirAdj() * 1000 / font.getStringWidth(position.getUnicode());
      } catch (Exception e) {
        fontSizeFromWidth = Integer.MAX_VALUE;
      }
      fontSize = fontSize - 1 >= fontSizeFromWidth && fontSizeFromWidth > 0 ? fontSizeFromWidth
          : fontSize;  // another font correction
      fontSize = fontSize > 0 ? fontSize : 11;  // when everything fails
      widthOfSpace = widthOfSpacePerFontSize * fontSize / 1000;

      fontInfo = new FontInfo(font, fontSizeStored, fontSize, widthOfSpace,
          new StandardizedFont(font));
      this.fontCache.add(fontInfo);
    }
    return fontInfo;
  }

  @Override
  protected void startPage(PDPage page) throws IOException {
    this.textRotation = this.textPerpendicularFlipScore > 0 ? 90 : 0;
    this.textPerpendicularFlipScore = 0;
    this.textElements = Lists.mutable.empty();
    this.images = Lists.mutable.empty();
    this.otherElements = Lists.mutable.empty();
    this.unprocessedNonRenderablePositions = Lists.mutable.empty();
    this.processedText = new StringBuffer();
    this.wordsIfSpacingScaled = Lists.mutable.empty();
    this.totalExtraVocabWordsIfSpacingScaled = 0;
    this.areTextElementsNonRenderable = !this.settings.isIgnoreNonRenderableText();
    this.isLastCharWhiteSpace = false;
    this.processWordsInCurrentPage = Lists.mutable.empty();
    this.processedWordsInCurrentLine = Lists.mutable.empty();
    this.processedWhiteSpacesInCurrentPage = Lists.mutable.empty();
    this.processedWhitespacesInCurrentLine = Lists.mutable.empty();
    this.processedPositions = Lists.mutable.empty();
    this.possiblyOverriddenPositions = Multimaps.mutable.list.empty();
    this.unrecognizedUnicodeCount = 0;
    this.fillStrokeTextPositions = Sets.mutable.empty();
    this.lastSpacingSplitElementIndex = 0;
    this.lastSpacingSplitWordIndex = 0;

    GraphicsExtractor graphicsExtractor = new GraphicsExtractor(page, this.pdDocument,
        this.pages.size(), this.textRotation, this.settings);
    graphicsExtractor.processPage(page);
    this.layoutAreas = Lists.mutable.empty();
    this.layoutAreas.addAll(ListIterate.collect(graphicsExtractor.getHandWrittenAreas(),
        r -> Tuples.pair(r, PageLayout.HAND_WRITTEN)));
    this.coloredAreas = Lists.mutable.empty();
    this.textOperationIndex = 0;
    this.pageWidth = graphicsExtractor.getPageWidth();
    this.pageHeight = graphicsExtractor.getPageHeight();
    this.isPagePerpendicularlyFlipped = graphicsExtractor.getAdjustedPage()
        .isPagePerpendicularlyFlipped();
    this.images.addAll(graphicsExtractor.getImages());
    this.otherElements.addAll(this.formElementsByPage.get(this.pages.size()));
    List<HorizontalLine> mergedHorizontalLines = graphicsExtractor.getMergedHorizontalLines();
    this.otherElements.addAll(mergedHorizontalLines);
    this.horizontalLinesGroupedByYPos = sortedGroupBy(mergedHorizontalLines,
        line -> (float) line.getTop().getMagnitude());
    List<VerticalLine> mergedVerticalLines = graphicsExtractor.getMergedVerticalLines();
    this.otherElements.addAll(mergedVerticalLines);
    this.verticalLinesGroupedByXPos = sortedGroupBy(mergedVerticalLines,
        line -> (float) line.getLeft().getMagnitude());

    this.colorByLocation = new ColoredArea[(int) this.pageWidth + 1][(int) this.pageHeight + 1];
    this.textColorByLocation = Maps.mutable.empty();
    for (ColoredArea coloredArea : graphicsExtractor.getColoredAreas()) {
      Rectangle pathBounds = coloredArea.area;
      for (int x = pathBounds.x; x <= pathBounds.x + pathBounds.width; x++) {
        for (int y = pathBounds.y; y <= pathBounds.y + pathBounds.height; y++) {
          this.colorByLocation[x][y] = coloredArea;
        }
      }
      if (coloredArea.color != GraphicsExtractor.WHITE) {
        this.coloredAreas.add(Tuples.pair(coloredArea.area, coloredArea.color));
      }
    }
    this.imageStrings = graphicsExtractor.getImageStrings();
    this.textClippers = graphicsExtractor.getTextClippers();
    this.numPositionsPerOperation = Lists.mutable.empty();
    QuickSort.sort(this.imageStrings);

    this.scannedness = graphicsExtractor.getTotalImageArea() / (graphicsExtractor.getPageHeight()
        * graphicsExtractor.getPageWidth());
  }

  @Override
  protected void endPage(PDPage page) throws IOException {
    if (this.textRotation == 0 && this.textPerpendicularFlipScore > 0) {
      this.processPage(page);
      this.textPerpendicularFlipScore = 0;
      return;
    }
    if ((this.pages.isEmpty() || this.settings.isPageLevelSpacingScaled())
        && !this.isReportedSpaceWidthScaled) {
      this.isReportedSpaceWidthScaled = this.isReportedSpaceWidthForDocScaled();
      if (this.isReportedSpaceWidthScaled) {
        this.processPage(page);
        return;
      }
    }

    if (this.areTextElementsNonRenderable && this.unprocessedNonRenderablePositions.notEmpty()) {
      for (TextPosition position : this.unprocessedNonRenderablePositions) {
        super.processTextPosition(position);
      }
      this.writePage();
      this.scannedness = 0;
    }

    if (this.processedPositions.notEmpty()) {
      this.processedWordsInCurrentLine
          .add(new ProcessedWord(this.processedPositions, this.processedText.toString()));
    }
    if (this.processedWordsInCurrentLine.notEmpty()) {
      this.processWordsInCurrentPage.add(this.processedWordsInCurrentLine);
      this.processedWhiteSpacesInCurrentPage.add(this.processedWhitespacesInCurrentLine);
    }

    this.processPage();

    if (this.textElements.isEmpty() && this.otherElements.isEmpty()) {
      this.numOfPagesWithImagesOnly++;
      this.settings.getBadPageSignaler().accept(this.pages.size());
    }
    Page newPage = new Page()
        .add(new Height(new Length(this.pageHeight, Unit.pt)))
        .add(new Width(new Length(this.pageWidth, Unit.pt)))
        .add(new PageColor(this.coloredAreas))
        .add(new PositionalContent(new PositionalElementList<>(
            this.textElements.withAll(this.images).withAll(this.otherElements)
                .select(this::isInsidePage))));

    if (!this.layoutAreas.isEmpty()) {
      newPage.add(new PageLayout(this.layoutAreas));
    }

    if (this.scannedness > 0 && this.settings.isPageLevelOcr() && this.textElements.isEmpty()) {
      this.pageNosToOcr.add(this.pages.size());
    }

    this.pages.add(newPage);
    this.totalScannedness += (this.scannedness - this.totalScannedness) / this.pages.size();
  }

  /**
   * @return True if space width in document is larger than expected else return False
   */
  private boolean isReportedSpaceWidthForDocScaled() {
    return this.processWordsInCurrentPage.size() >= 5
        && this.getAverageNumberOfWordsPerLineInCurrentPage() <= 2
        || this.totalExtraVocabWordsIfSpacingScaled > 10;
  }

  /**
   * @return average number of words per line in current page
   */
  private double getAverageNumberOfWordsPerLineInCurrentPage() {
    int numberOfWordsInAllLines = 0;
    for (MutableList<ProcessedWord> wordsInLine : this.processWordsInCurrentPage) {
      numberOfWordsInAllLines += wordsInLine.size();
    }
    return (double) numberOfWordsInAllLines / this.processWordsInCurrentPage.size();
  }

  /**
   * @return True if the {@code element} is present within the page
   */
  private boolean isInsidePage(Element element) {
    double left = element.getAttribute(Left.class).getMagnitude();
    double top = element.getAttribute(Top.class).getMagnitude();
    double right = left + element.getAttributeValue(Width.class, Length.ZERO).getMagnitude();
    double bottom = top + element.getAttributeValue(Height.class, Length.ZERO).getMagnitude();
    return left <= this.pageWidth && top <= this.pageHeight && right >= 0 && bottom >= 0;
  }

  @Override
  protected void startDocument(PDDocument document) throws IOException {
    this.pages = Lists.mutable.empty();
    this.pdDocument = document;
    this.document = null;
    this.totalScannedness = 0;
    this.pageNosToOcr = this.settings.getPageNosToOcr();
  }

  @Override
  protected void endDocument(PDDocument document) throws IOException {
    this.document = new Document()
        .add(new Content(new ElementList<>(this.pages)));
    if (this.pages.size() == this.numOfPagesWithBadGlyphs) {
      throw new UnDigitizedPdfException("Too many unrecognized unicode glyphs.");
    }
    if (this.pages.size() == this.numOfPagesWithImagesOnly) {
      throw new UnDigitizedPdfException("Cannot parse a non digitized raw scanned pdf");
    }
    if (this.totalScannedness > this.settings.getAllowedScannedness()) {
      throw new UnDigitizedPdfException(
          "Document scannedness of " + this.totalScannedness + " breaches threshold");
    }
    int charsCount = 0;
    for (Element element : this.document.getContainingElements(TextElement.class)) {
      charsCount += element.getTextStr().length();
      if (charsCount >= this.settings.getMinChars()) {
        break;
      }
    }
    if (charsCount < this.settings.getMinChars()) {
      throw new UnDigitizedPdfException("Very few characters");
    }

    if (!this.pageNosToOcr.isEmpty()) {
      this.parseOcredPages();
    }
  }

  /**
   * Parse scanned pages using scanned pdf parser
   */
  private void parseOcredPages() {
    try (PDDocument newPdDocument = new PDDocument()) {
      this.pageNosToOcr = ListIterate.distinct(this.pageNosToOcr);
      List<Element> scannedPages = Lists.mutable.empty();
      for (int pageNo : this.pageNosToOcr) {
        newPdDocument.addPage(this.pdDocument.getPage(pageNo));
        scannedPages.add(this.pages.get(pageNo));
      }
      Document scannedDocument = new Document()
          .add(new Content(new ElementList<>(scannedPages)));
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      newPdDocument.save(bos);
      Document ocredDocument = this.settings.getScannedPdfParser()
          .parse(new ByteArrayInputStream(bos.toByteArray()), scannedDocument);
      List<Element> ocredPages = Lists.mutable
          .ofAll(ocredDocument.getContainingElements(Page.class));
      for (int i = 0; i < this.pageNosToOcr.size(); i++) {
        this.pages.set(this.pageNosToOcr.get(i), ocredPages.get(i));
      }
      this.document = new Document()
          .add(new Content(new ElementList<>(this.pages)));
      ListIterate.forEach(ocredDocument.getTransformedIntermediateSources(),
          this.document::addTransformedIntermediateSource);
    } catch (Exception e) {
      throw new RuntimeException("ocr failed", e);
    }
  }

  /**
   * Class to represent key for null ligature. Ligature occurs where two or more letters are joined
   * as a single glyph. Example:æ
   */
  private static final class NullLigatureKey {

    private final int[] code;
    private final PDFont font;

    NullLigatureKey(TextPosition position) {
      this.code = position.getCharacterCodes();
      this.font = position.getFont();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || this.getClass() != o.getClass()) {
        return false;
      }

      NullLigatureKey that = (NullLigatureKey) o;

      if (!Arrays.equals(this.code, that.code)) {
        return false;
      }
      return this.font == that.font;
    }

    @Override
    public int hashCode() {
      int result = Arrays.hashCode(this.code);
      result = 31 * result + System.identityHashCode(this.font);
      return result;
    }
  }

  /**
   * Class to represent position for white space character
   */
  private static final class WhitespacePosition {

    private final float xLeft;
    private final float xRight;
    private final double widthOfSpace;
    private double spaceBreakThreshold; // -1 represents that the threshold is yet to be determined

    WhitespacePosition(float xLeft, float xRight, double widthOfSpace, double spaceBreakThreshold) {
      this.xLeft = xLeft;
      this.xRight = xRight;
      this.widthOfSpace = widthOfSpace;
      this.spaceBreakThreshold = spaceBreakThreshold;
    }
  }

  /**
   * Class representing all the text positions corresponding to text
   */
  private static final class ProcessedWord {

    private final MutableList<TextPosition> wordTextPositions;
    private final String wordText;

    ProcessedWord(MutableList<TextPosition> wordTextPositions, String wordText) {
      this.wordTextPositions = wordTextPositions;
      this.wordText = wordText;
    }

    @Override
    public String toString() {
      return this.wordText;
    }
  }

  /**
   * Class representing font information
   */
  private static final class FontInfo {

    private final PDFont font;
    private final float storedFontSize;       // Font size stored in pdfbox
    private final float fontSize;                   // Font size after correction
    private final float widthOfSpace;
    private final StandardizedFont standardizedFont;

    FontInfo(PDFont font, float storedFontSize, float fontSize, float widthOfSpace,
        StandardizedFont standardizedFont) {
      this.font = font;
      this.storedFontSize = storedFontSize;
      this.fontSize = fontSize;
      this.widthOfSpace = widthOfSpace;
      this.standardizedFont = standardizedFont;
    }
  }

  /**
   * Class representing standardized font (font, font family, text styles)
   */
  private static final class StandardizedFont {

    private static final MutableList<Pair<String, String>> PDF_FONT_TEXTSTYLES = Lists.mutable.of(
        Tuples.pair("Bold", TextStyles.BOLD),
        Tuples.pair("Italic", TextStyles.ITALIC),
        Tuples.pair("Oblique", TextStyles.ITALIC));
    private static final Map<String, PDFont> STANDARD_FONTS = UnifiedMap.newMapWith(
        Tuples.pair(FontFamily.TIMES, PDType1Font.TIMES_ROMAN),
        Tuples.pair(FontFamily.TIMES + TextStyles.BOLD, PDType1Font.TIMES_BOLD),
        Tuples.pair(FontFamily.TIMES + TextStyles.ITALIC, PDType1Font.TIMES_ITALIC),
        Tuples.pair(FontFamily.TIMES + TextStyles.BOLD + TextStyles.ITALIC,
            PDType1Font.TIMES_BOLD_ITALIC),
        Tuples.pair(FontFamily.HELVETICA, PDType1Font.HELVETICA),
        Tuples.pair(FontFamily.HELVETICA + TextStyles.BOLD, PDType1Font.HELVETICA_BOLD),
        Tuples.pair(FontFamily.HELVETICA + TextStyles.ITALIC, PDType1Font.HELVETICA_OBLIQUE),
        Tuples.pair(FontFamily.HELVETICA + TextStyles.BOLD + TextStyles.ITALIC,
            PDType1Font.HELVETICA_BOLD_OBLIQUE),
        Tuples.pair(FontFamily.COURIER, PDType1Font.COURIER),
        Tuples.pair(FontFamily.COURIER + TextStyles.BOLD, PDType1Font.COURIER_BOLD),
        Tuples.pair(FontFamily.COURIER + TextStyles.ITALIC, PDType1Font.COURIER_OBLIQUE),
        Tuples.pair(FontFamily.COURIER + TextStyles.BOLD + TextStyles.ITALIC,
            PDType1Font.COURIER_BOLD_OBLIQUE));
    private static final MutableList<String> STANDARD_FONT_FAMILY_NAMES = Lists.mutable
        .of(FontFamily.TIMES, FontFamily.HELVETICA, FontFamily.COURIER);
    private static final String DEFAULT_FONT_FAMILY = FontFamily.TIMES;
    private final PDFont font;
    private final String fontFamily;
    private final MutableList<String> textStyles;

    StandardizedFont(PDFont font) {
      String pdfFontName = getPdfFontName(font);
      this.textStyles = PDF_FONT_TEXTSTYLES
          .collectIf(tsp -> pdfFontName.contains(tsp.getOne()), Pair::getTwo);
      this.fontFamily = STANDARD_FONT_FAMILY_NAMES
          .detectIfNone(n -> pdfFontName.contains(n), () -> DEFAULT_FONT_FAMILY);
      this.font = STANDARD_FONTS.get(this.fontFamily + this.textStyles.makeString(""));
    }

    private static String getPdfFontName(PDFont font) {
      String pdfFontName = makeNonNull(font.getName());
      if (font instanceof PDType0Font) {
        PDCIDFont descendantFont = ((PDType0Font) font).getDescendantFont();
        if (descendantFont instanceof PDCIDFontType2) {
          TrueTypeFont trueTypeFont = ((PDCIDFontType2) descendantFont).getTrueTypeFont();
          if (trueTypeFont != null) {
            try {
              pdfFontName += "," + makeNonNull(trueTypeFont.getName());
            } catch (IOException ignored) {
            }
          }
        }
      }
      PDFontDescriptor fontDescriptor = font.getFontDescriptor();
      if (fontDescriptor != null && (fontDescriptor.getFontWeight() >= 700 || fontDescriptor
          .getFontName().toLowerCase().contains("bold"))) {
        pdfFontName += ",Bold";
      }
      return pdfFontName;
    }

    /**
     * @return string if it is not null else return empty string
     */
    private static String makeNonNull(String str) {
      return str == null ? "" : str;
    }
  }

  /**
   * Class holding the information for justified text
   */
  class JustifiedTextInfo {

    private final int spuriousSpaceCount;                // Number of spaces in a line that have width different from majoritySpaceWidth
    private final float majoritySpaceWidth;             // width of space that is present consecutively most of the times in a line
    private boolean isLeftAlignedAndConstantInterLineSpace;

    JustifiedTextInfo(int spuriousSpaceCount, float majoritySpaceWidth,
        boolean isLeftAlignedAndConstantInterLineSpace) {
      this.spuriousSpaceCount = spuriousSpaceCount;
      this.majoritySpaceWidth = majoritySpaceWidth;
      this.isLeftAlignedAndConstantInterLineSpace = isLeftAlignedAndConstantInterLineSpace;
    }

    public boolean isLinePartofJustifiedtext() {
      return this.isLeftAlignedAndConstantInterLineSpace
          && this.spuriousSpaceCount <= MAX_SPURIOUS_SPACES_ALLOWED
          && this.majoritySpaceWidth < MAX_SPACE_WIDTH;
    }
  }

}
