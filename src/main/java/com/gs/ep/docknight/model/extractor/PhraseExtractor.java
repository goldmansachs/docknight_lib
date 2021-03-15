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

package com.gs.ep.docknight.model.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementGroup;
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.ModelCustomizationKey;
import com.gs.ep.docknight.model.ModelCustomizations;
import com.gs.ep.docknight.model.Parser;
import com.gs.ep.docknight.model.PositionalContext;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.RectangleProperties;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup.GridType;
import com.gs.ep.docknight.model.attribute.AlternateRepresentations;
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.FontFamily;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.LetterSpacing;
import com.gs.ep.docknight.model.attribute.PageLayout;
import com.gs.ep.docknight.model.attribute.Stretch;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.context.PagePartitionType;
import com.gs.ep.docknight.model.converter.PdfParser;
import com.gs.ep.docknight.model.converter.PdfParser.BadTextSegmentationException;
import com.gs.ep.docknight.model.converter.PdfParser.UnDigitizedPdfException;
import com.gs.ep.docknight.model.converter.ScannedPdfParser;
import com.gs.ep.docknight.model.converter.ScannedPdfParser.OCREngine;
import com.gs.ep.docknight.model.converter.pdfparser.ParserSettings;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.Document.SourceType;
import com.gs.ep.docknight.model.element.GraphicalElement;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.Image;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.PageBreak;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.renderer.HtmlRenderer;
import com.gs.ep.docknight.model.renderer.VisualJsonRenderer;
import com.gs.ep.docknight.model.transformer.MultiPageToSinglePageTransformer;
import com.gs.ep.docknight.model.transformer.PositionalTextGroupingTransformer;
import com.gs.ep.docknight.model.transformer.TableDetectionTransformer;
import com.gs.ep.docknight.util.StatsDClientWrapper;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.docopt.Docopt;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class PhraseExtractor {

  public static final String PDF = "pdf";
  public static final String HTML = "html";
  public static final String HTM = "htm";
  public static final String TXT = "txt";
  public static final String XLSX = "xlsx";
  public static final String XLS = "xls";
  public static final String DOCX = "docx";
  protected static final Logger LOGGER = LoggerFactory.getLogger(PhraseExtractor.class);
  private static final int CALL_ERROR = 1;
  private static final int ARG_VALUE_ERROR = 2;
  private static final int INTERNAL_ERROR = 3;
  private static final int SCANNED_OR_BROKEN_PDF_ERROR = 4;
  private static final int BAD_TEXT_SEGMENTATION_ERROR = 5;
  private static final String DOC =
      "java -cp path/to/docknight.jar com.gs.ep.docknight.model.extractor.PhraseExtractor\n"
          + "\n"
          + "Usage:\n"
          + "  PhraseExtractor [--no-recurse] [--timeout=SEC] [--ignore-overlay] [--tesseract] [--abbyy] [--mixed-layout] [--html] [--min-chars=CNT] [--hand-written] [--page-level-ocr] [--customizations-path=<path>] [--enable-statsd] [--statsd-tags=<tags>] [--statsd-host=<host>] [--statsd-port=<port>] [--statsd-prefix=<prefix>] <input-path> <output-path>\n"
          + "\n"
          + "Options:\n"
          + "  -h --help                     Show this screen.\n"
          + "  --no-recurse                  Don't run recursively on the input_path, if it is a directory.\n"
          + "  --timeout=SEC                 Timeout per document in seconds [default: 300].\n"
          + "  --ignore-overlay              Ignore text overlayed on image.\n"
          + "  --tesseract                   Enable tesseract based OCR, if normal processing fails.\n"
          + "  --abbyy                       Enable abbyy based OCR, if normal processing fails.\n"
          + "  --mixed-layout                Process mixed layouts.\n"
          + "  --html                        Generate html as well for debugging.\n"
          + "  --min-chars=CNT               Minimum number of parsed chars document should have [default: 0].\n"
          + "  --hand-written                Detect hand written areas in the document.\n"
          + "  --page-level-ocr              Run ocr if some pages are scanned.\n"
          + "  --customizations-path=<path>  Use customizations on this path in transformers [default: ].\n"
          + "  --enable-statsd               Public the metrics to statsD server.\n"
          + "  --statsd-tags=<tags>          Set the tags. These will be associated with metric sent to statsD. [default: ].\n"
          + "  --statsd-host=<host>          Host where the metrics will be sent [default: ].\n"
          + "  --statsd-port=<port>          Port on the host where the metrics will be sent [default: -1].\n"
          + "  --statsd-prefix=<prefix>      Name which will be used as prefix in all statsd metric names. [default: ].\n"
          + "\n";
  private static String version;
  private static ScannedPdfParser scannedPdfParser;
  private static boolean isScannedPdfParserInitialized;

  static {
    Map<String, Integer> optionCount = Maps.mutable.empty();
    for (String s : DOC.split("[^a-z0-9A-Z-]+")) {
      if (s.startsWith("--") && !s.equals("--help")) {
        optionCount.compute(s, (k, v) -> v == null ? 1 : v + 1);
      }
    }
    for (String s : optionCount.keySet()) {
      if (optionCount.get(s) != 2) {
        throw new RuntimeException(s + " needs to be present in both usage and options");
      }
    }
  }

  /**
   * Start new group if there is a group change, start new line if there is a line change and create
   * segments corresponding to its alternate representation of elements. Group change and line
   * change is decided using {@code paragraphSpaceFactor}
   */
  private static void addSegment(VisualJsonBuilder builder, Element element, Element prevElement,
      double paragraphSpaceFactor) {
    boolean isLineChange = prevElement == null
        || PositionalElementList.compareByHorizontalAlignment(prevElement, element) != 0;
    boolean isGroupChange =
        prevElement == null || isLineChange && isParaChange(element, prevElement,
            paragraphSpaceFactor);
    PagePartitionType partitionType = element.getPositionalContext().getPagePartitionType();
    String groupName = partitionType == PagePartitionType.HEADER ? "headerGroups"
        : partitionType == PagePartitionType.FOOTER ? "footerGroups" : "groups";
    if (isGroupChange) {
      builder.startGroup(groupName);
    }
    if (isLineChange) {
      builder.startLine();
    }
    List<Element> elements = element
        .getAttributeValue(AlternateRepresentations.class, Lists.mutable.of(element));
    for (int i = 0; i < elements.size(); i++) {
      addSegment(builder, elements.get(i), getOrNull(elements, i - 1), getOrNull(elements, i + 1),
          element, i);
    }
  }

  /**
   * Add the new segment in visual json.
   *
   * @param builder Visual json builder
   * @param element alternate representation element at index {@code index} for element {@code
   * parent}
   * @param leftSibling alternate representation element at index {@code index}-1 for element {@code
   * parent}
   * @param rightSibling alternate representation element at index {@code index}+1 for element
   * {@code parent}
   * @param parent element whose segments we are adding to visual json
   * @param index represent position of segment in alternate representation list of element {@code
   * parent}
   */
  public static void addSegment(VisualJsonBuilder builder, Element element, Element leftSibling,
      Element rightSibling, Element parent, int index) {
    builder.startSegment();
    builder.getSegment().put("text", element.getTextStr());
    RectangleProperties<Double> boundary = new ElementGroup<>(Lists.mutable.of(element))
        .getTextBoundingBox();
    builder.getSegment().put("box", Maps.mutable.of(
        "left", format(boundary.getLeft()),
        "top", format(boundary.getTop()),
        "right", format(boundary.getRight()),
        "bottom", format(boundary.getBottom())));
    builder.getSegment().put("id", segmentId(parent, index));
    builder.getSegment()
        .put("styles", element.getAttributeValue(TextStyles.class, Lists.mutable.empty()));
    PositionalContext context = parent.getPositionalContext();
    builder.getSegment().put("span", Maps.mutable.of(
        "left",
        format(leftSibling == null ? context.getVisualLeft() : getMid(leftSibling, element)),
        "top", format(context.getVisualTop()),
        "right",
        format(rightSibling == null ? context.getVisualRight() : getMid(element, rightSibling)),
        "bottom", format(context.getVisualBottom())));
    builder.getSegment().put("neighbour", Maps.mutable.of(
        "left", leftSibling == null ? segmentId(context.getShadowedLeftElement(), 0)
            : segmentId(parent, index - 1),
        "top", segmentId(context.getShadowedAboveElement(), 0),
        "right", rightSibling == null ? segmentId(context.getShadowedRightElement(), 0)
            : segmentId(parent, index + 1),
        "bottom", segmentId(context.getShadowedBelowElement(), 0)));
    Map<String, Boolean> borders = getBordersIndependentOfUnderline(context);
    builder.getSegment().put("border", Maps.mutable.of(
        "left", leftSibling == null && borders.get("left"),
        "top", borders.get("top"),
        "right", rightSibling == null && borders.get("right"),
        "bottom", borders.get("bottom")));
    builder.getSegment().put("fontFamily", element.getAttribute(FontFamily.class).getValue());
    builder.getSegment()
        .put("fontSize", format(element.getAttribute(FontSize.class).getMagnitude()));
    java.awt.Color color = element.getAttributeValue(Color.class, java.awt.Color.BLACK);
    String colorString = String
        .format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    builder.getSegment().put("color", colorString);
    builder.getSegment().put("letterSpacing",
        format(element.getAttributeValue(LetterSpacing.class, Length.ZERO).getMagnitude()));
  }

  /**
   * @return mid point (x coordinate between the elements {@code elem1} and {@code elem2}
   */
  private static double getMid(Element elem1, Element elem2) {
    return (elem1.getAttribute(Left.class).getMagnitude() + elem1.getAttribute(Width.class)
        .getMagnitude() +
        elem2.getAttribute(Left.class).getMagnitude()) / 2;
  }

  /**
   * Get the item at {@code index} from the {@code list}
   *
   * @param list list from where the item is to be retrieved
   * @param index position of item in the {@code list}
   * @param <K> type of item in the list
   * @return item if index is less than list size, else null
   */
  public static <K> K getOrNull(List<K> list, int index) {
    return index >= 0 && index < list.size() ? list.get(index) : null;
  }

  /**
   * Generate the segment id. Algo: segment id = the position of {@element} among its siblings + "_"
   * + {@code index}
   *
   * @param index position of segment in alternate representation list of element {@code parent}
   * @return the segment id for the element
   */
  private static String segmentId(Element element, int index) {
    return element instanceof TextElement ? Integer.toString(element.getElementListIndex()) + "_"
        + index : null;
  }

  /**
   * Get all the segment ids of alternate representation list of {@code element}
   *
   * @param element element for which to generate segment ids.
   * @return segment ids
   */
  public static List<String> segmentIds(Element element) {
    if (element instanceof TextElement) {
      String idPrefix = Integer.toString(element.getElementListIndex());
      List<String> result = Lists.mutable.empty();
      List<Element> elements = element
          .getAttributeValue(AlternateRepresentations.class, Lists.mutable.of(element));
      for (int i = 0; i < elements.size(); i++) {
        result.add(idPrefix + "_" + i);
      }
      return result;
    }
    return null;
  }

  /**
   * Format the {@code value} such that it has only 2 decimal places. Example: 2.134 -> 2.13, 2.1 ->
   * 2.10
   *
   * @param value value which is to be formatted
   * @return formatted value
   */
  public static double format(double value) {
    return new BigDecimal(value).setScale(2, RoundingMode.FLOOR).doubleValue();
  }

  /**
   * Get borders assuming underline is not part of border.
   *
   * @param context positional context of element
   * @return boolean flag indicating whether the element has border or not in left, right, top and
   * bottom direction.
   */
  private static Map<String, Boolean> getBordersIndependentOfUnderline(
      PositionalContext<Element> context) {
    boolean hasLeft = context.isVisualLeftBorder();
    boolean hasRight = context.isVisualRightBorder();
    boolean hasBottom = context.isVisualBottomBorder()
        && Math.abs(context.getSelf().getAttribute(Top.class).getMagnitude()
        + context.getSelf().getAttribute(Height.class).getMagnitude() - context.getVisualBottom())
        > 2;
    boolean hasTop = context.isVisualTopBorder() && (context.getShadowedAboveElement() == null
        || Math.abs(context.getShadowedAboveElement().getAttribute(Top.class).getMagnitude()
        + context.getShadowedAboveElement().getAttribute(Height.class).getMagnitude() - context
        .getVisualTop()) > 2);
    return Maps.mutable.of("left", hasLeft, "top", hasTop, "right", hasRight, "bottom", hasBottom);
  }

  /**
   * @return True if element and prevElement present in different paragraphs, otherwise return False
   */
  private static boolean isParaChange(Element element, Element prevElement,
      double paragraphSpaceFactor) {
    double lineHeight = prevElement.getAttribute(Height.class).getMagnitude();
    double lineDistance =
        element.getAttribute(Top.class).getMagnitude() - prevElement.getAttribute(Top.class)
            .getMagnitude() - lineHeight;
    return lineDistance > paragraphSpaceFactor * lineHeight;
  }

  private static void execute(ExecutorService executor, int timeoutInSeconds, boolean exitOnTimeout,
      Runnable runnable) throws Exception {
    Future task = executor.submit(runnable);
    for (int i = 0; i < timeoutInSeconds && !task.isDone(); i++) {
      Thread.sleep(1000);
    }
    if (!task.isDone()) {
      LOGGER.info("exceeded timeout " + timeoutInSeconds + " sec");
      if (exitOnTimeout) {
        System.exit(0);
      }
      task.cancel(true);
    }
  }

  private static synchronized String getVersion() {
    if (version == null) {
      try {
        String file = PhraseExtractor.class.getProtectionDomain().getCodeSource().getLocation()
            .getFile();
        SimpleDateFormat sdf = new SimpleDateFormat("yy.MM.dd");
        version = sdf.format(new File(file).lastModified());
      } catch (Exception e) {
        version = "";
      }
    }
    return version;
  }

  /**
   * Generate {@see com.gs.ep.docknight.model.element.Document document model} from the document
   * stream {@code input} for the file {@code filename}
   *
   * @param input document in stream
   * @param filename name of the document
   * @return document model
   */
  public static Document parseDocument(InputStream input, String filename) {
    return parseDocument(input, Lists.mutable.empty(), null, Maps.mutable.empty(), null, false, 0,
        false, false, new AtomicInteger(), new ModelCustomizations(), filename);
  }

  /**
   * Generate {@see com.gs.ep.docknight.model.element.Document document model} from the document
   * stream {@code input} for the file {@code filename}
   *
   * @param input document in stream
   * @param pageNosToOcr page number on which the scanned pdf parser will execute
   * @param badPageSignaler // Consumer on how to handle pages which contain unrecognized glyphs
   * @param widthByPage // Output variable to populate pageIndex and its width
   * @param ocrEngine ocr engine for scanned pdf parser
   * @param ignoreNonRenderableText boolean to ignore non renderable text
   * @param minChars minimum characters present in document for visual vson creation
   * @param handWritten boolean to detect hand written areas
   * @param pageLevelOcr boolean to apply scanned pdf scanned only on scanned pages and not on whole
   * document
   * @param errorCode output variable to populate with appropriate error in case failure happens
   * @param modelCustomizations customizations to adjust document parsing behaviour
   * @param filename name of the document
   * @return document model
   */
  public static Document parseDocument(InputStream input, List<Integer> pageNosToOcr,
      Consumer<Integer> badPageSignaler,
      MutableMap<Integer, Double> widthByPage, OCREngine ocrEngine, boolean ignoreNonRenderableText,
      int minChars,
      boolean handWritten, boolean pageLevelOcr, AtomicInteger errorCode,
      ModelCustomizations modelCustomizations, String filename) {
    try {
      Pattern noisePattern = Pattern
          .compile("(?:[(]amounts\\p{Space}in\\p{Space}[a-z]+[)])", Pattern.CASE_INSENSITIVE);
      ModelCustomizations customizations = new ModelCustomizations()
          .add(ModelCustomizationKey.TABULAR_NOISE_PATTERNS, Lists.mutable.of(noisePattern))
          .add(ModelCustomizationKey.ENABLE_GRID_BASED_TABLE_DETECTION, GridType.ROW_AND_COL)
          .add(ModelCustomizationKey.IS_PAGE_NUMBERED_DOC, true)
          .add(ModelCustomizationKey.FONT_CHANGE_SEGMENTATION, true)
          .add(ModelCustomizationKey.UNDERLINE_DETECTION, true)
          .add(ModelCustomizationKey.IMAGE_BASED_CHAR_DETECTION, false)
          .add(ModelCustomizationKey.LINE_MERGE_EPSILON, 10.0)
          .add(ModelCustomizationKey.PAGES_TO_OCR, ListAdapter.adapt(pageNosToOcr))
          .add(ModelCustomizationKey.IGNORE_NON_RENDERABLE_TEXT, ignoreNonRenderableText)
          .add(ModelCustomizationKey.MAX_TEXT_ELEMENT_TO_LINE_COUNT_RATIO, 3.5)
          .add(ModelCustomizationKey.ENABLE_HAND_WRITTEN_TEXT_DETECTION, handWritten)
          .add(ModelCustomizationKey.ALLOWED_SCANNEDNESS, 0.6)
          .add(ModelCustomizationKey.PAGE_LEVEL_SPACING_SCALING, true)
          .add(ModelCustomizationKey.PAGE_LEVEL_OCR, pageLevelOcr)
          .add(ModelCustomizationKey.OCR_ENGINE, ocrEngine)
          .combineAndOverrideWith(modelCustomizations);

      Document document = null;
      byte[] docContents = IOUtils.toByteArray(input);

      try {
        Parser parser = FileParserDecoder.getFileParserDecoder(filename).getParser(customizations);
        if (parser instanceof PdfParser) {
          parser = ((PdfParser) parser)
              .withImageBasedFormDetection(false)
              .withSpacingFactor(-1)
              .withBadPageSignaler(badPageSignaler)
              .withMinChars(minChars);
        }
        document = parser.parse(new ByteArrayInputStream(docContents));
      } catch (UnDigitizedPdfException e) {
        errorCode.set(SCANNED_OR_BROKEN_PDF_ERROR);
        return null;
      } catch (BadTextSegmentationException e) {
        LOGGER.info("bad segmentation even after too many tries");
        errorCode.set(BAD_TEXT_SEGMENTATION_ERROR);
        return null;
      }

      for (Element page : document.getContainingElements(Page.class)) {
        widthByPage.put(page.getElementListIndex(), page.getAttribute(Width.class).getMagnitude());
      }
      document = new MultiPageToSinglePageTransformer().transform(document);
      document = new PositionalTextGroupingTransformer(customizations).transform(document);
      document = new TableDetectionTransformer(customizations).transform(document);
      return document;
    } catch (Exception e) {
      LOGGER.info("encountered exception", e);
      errorCode.set(INTERNAL_ERROR);
      return null;
    }
  }

  /**
   * Generate {@see com.gs.ep.docknight.model.element.Document document model} from the document
   * stream {@code input} for the file {@code filename}
   *
   * @param input document in stream
   * @param ocrEngine ocr engine for scanned pdf parser
   * @param ignoreNonRenderableText boolean to ignore non renderable text
   * @param minChars minimum characters present in document for visual vson creation
   * @param handWritten boolean to detect hand written areas
   * @param pageLevelOcr boolean to apply scanned pdf scanned only on scanned pages and not on whole
   * document
   * @param errorCode output variable to populate with appropriate error in case failure happens
   * @param modelCustomizations customizations to adjust document parsing behaviour
   * @param filename name of the document
   * @return document model
   */
  private static Document parseMixedLayoutDocument(InputStream input, OCREngine ocrEngine,
      boolean ignoreNonRenderableText,
      int minChars, boolean handWritten, boolean pageLevelOcr, AtomicInteger errorCode,
      ModelCustomizations modelCustomizations, String filename) {
    try {
      Document document;
      byte[] docContents = IOUtils.toByteArray(input);

      try {
        ModelCustomizations customizations = new ModelCustomizations()
            .add(ModelCustomizationKey.FONT_CHANGE_SEGMENTATION, true)
            .add(ModelCustomizationKey.UNDERLINE_DETECTION, true)
            .add(ModelCustomizationKey.IMAGE_BASED_CHAR_DETECTION, false)
            .add(ModelCustomizationKey.IGNORE_NON_RENDERABLE_TEXT, ignoreNonRenderableText)
            .add(ModelCustomizationKey.ENABLE_HAND_WRITTEN_TEXT_DETECTION, handWritten)
            .add(ModelCustomizationKey.ENABLE_DYNAMIC_SPACE_WIDTH_COMPUTATION, true)
            .add(ModelCustomizationKey.ALLOWED_SCANNEDNESS, 0.6)
            .add(ModelCustomizationKey.PAGE_LEVEL_SPACING_SCALING, true)
            .add(ModelCustomizationKey.PAGE_LEVEL_OCR, pageLevelOcr)
            .add(ModelCustomizationKey.OCR_ENGINE, ocrEngine)
            .combineAndOverrideWith(modelCustomizations);
        Parser parser = FileParserDecoder.getFileParserDecoder(filename).getParser(customizations);

        if (parser instanceof PdfParser) {
          parser = ((PdfParser) parser)
              .withImageBasedFormDetection(false)
              .withMinChars(minChars);
        }
        document = parser.parse(new ByteArrayInputStream(docContents));
      } catch (UnDigitizedPdfException e) {
        errorCode.set(SCANNED_OR_BROKEN_PDF_ERROR);
        return null;
      } catch (BadTextSegmentationException e) {
        LOGGER.info("bad segmentation even after too many tries");
        errorCode.set(BAD_TEXT_SEGMENTATION_ERROR);
        return null;
      }

      document = new MultiPageToSinglePageTransformer().transform(document);
      document = new PositionalTextGroupingTransformer(modelCustomizations).transform(document);
      document = new TableDetectionTransformer(modelCustomizations).transform(document);
      return document;
    } catch (Exception e) {
      LOGGER.info("encountered exception", e);
      errorCode.set(INTERNAL_ERROR);
      return null;
    }
  }

  /**
   * Getter to get scanned pdf parser
   *
   * @param ocrEngine ocr engine to use for scanned pdf parser (Example: tesseract)
   * @return scanned pdf parser
   */
  private static ScannedPdfParser getScannedPdfParser(OCREngine ocrEngine) {
    try {
      if (ocrEngine != null) {
        if (!isScannedPdfParserInitialized) {
          isScannedPdfParserInitialized = true;
          scannedPdfParser = new ScannedPdfParser(ocrEngine);
        }
        return scannedPdfParser;
      }
      return null;
    } catch (Exception e) {
      LOGGER.error("scanned pdf parser init failed", e);
      return null;
    }
  }

  /**
   * Extract all pdf files from the {@code path}
   *
   * @param path path to directory from where the pdf files have to be extracted
   * @param lookupRecursively boolean to include files from nested directories also
   * @return list of pdf files
   */
  private static List<File> getPdfFiles(String path, boolean lookupRecursively) {
    return new File(path).isDirectory() ?
        Lists.mutable.ofAll(
            FileUtils.listFiles(new File(path), new String[]{"pdf", "PDF"}, lookupRecursively))
        : Lists.mutable.of(new File(path));
  }

  /**
   * Get json representation of {@code object}
   *
   * @param object object to jsonify
   * @return json representation
   * @throws JsonProcessingException exception to throw in case processing failed
   */
  public static String jsonify(Object object) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    return mapper.writeValueAsString(object);
  }

  /**
   * Create the outputfile with data {@code output}. If output file already exists, delete that
   * file.
   *
   * @param outputDir directory where the output file will be created
   * @param inputFile file from which the filename will be taken for output file
   * @param outExtension This extension will be used as extension of output file
   * @param output data that will be written in output file
   * @throws IOException exception to throw if file creation failed
   */
  private static void createOutputFile(File outputDir, File inputFile, String outExtension,
      String output) throws FileNotFoundException {
    String fileNameWithoutExtn = inputFile.getName().substring(0, inputFile.getName().length() - 3);
    outputDir.mkdirs();
    File outFile = new File(outputDir, fileNameWithoutExtn + outExtension);
    if (output != null && !output.isEmpty()) {
      try (PrintWriter pw = new PrintWriter(outFile)) {
        pw.print(output);
      }
    } else if (outFile.exists()) {
      outFile.delete();
    }
  }

  /**
   * Remove the extension from the {@code filename}
   *
   * @param fileName name of the file
   * @return file name after removing the extension
   */
  private static String getFileNameWithoutExtension(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index == -1 ? fileName : fileName.substring(0, index);
  }

  /**
   * Create the outputfile with data {@code output}. If output file already exists, delete that
   * file.
   *
   * @param outputDir directory where the output file will be created
   * @param inputFile file from which the filename will be taken for output file
   * @param outExtension This extension will be used as extension of output file
   * @param output data that will be written in output file
   * @throws IOException exception to throw if file creation failed
   */
  protected static void createOutputFile(Path outputDir, File inputFile, String outExtension,
      byte[] output) throws IOException {
    String fileNameWithoutExtn = getFileNameWithoutExtension(inputFile.getName());
    Files.createDirectories(outputDir);
    Path path = Paths.get(outputDir.toString(), fileNameWithoutExtn + "." + outExtension);
    if (output != null && output.length != 0) {
      Files.write(path, output);
    } else if (Files.exists(path)) {
      Files.delete(path);
    }
  }

  /**
   * Generate visual json in {@code ouputPath} for all the documents in {@code inputPath}
   *
   * @param inputPath path to directory containing documents
   * @param outputPath path to directory where visual json will be generated
   * @param lookupRecursively boolean indicating whether to look for documents in nested directories
   * of {@code inputPath}
   * @param timeoutInSecondsPerDocument Visual generation will stop for document if execution time
   * exceeds this variable
   * @param pageNosToOcr page number on which the scanned pdf parser will execute
   * @param ocrEngine ocr engine for scanned pdf parser
   * @param ignoreNonRenderableText boolean to ignore non renderable text
   * @param mixedLayout boolean whether document contain both paragraphs and tables
   * @param html boolean to generate html view of documents
   * @param minChars minimum characters present in document for visual vson creation
   * @param handWritten boolean to detect hand written areas
   * @param pageLevelOcr boolean to apply scanned pdf scanned only on scanned pages and not on whole
   * document
   * @param modelCustomizations customizations to adjust document parsing behaviour
   */
  public static void extractFromMultipleFiles(String inputPath, String outputPath,
      boolean lookupRecursively,
      int timeoutInSecondsPerDocument, List<Integer> pageNosToOcr, OCREngine ocrEngine,
      boolean ignoreNonRenderableText, boolean mixedLayout, boolean html, int minChars,
      boolean handWritten, boolean pageLevelOcr, ModelCustomizations modelCustomizations)
      throws Exception {
    PhraseExtractor phraseExtractor = new PhraseExtractor();
    ExecutorService executor = Executors.newFixedThreadPool(1);
    List<File> files = getPdfFiles(inputPath, lookupRecursively);
    int fileIndex = 1;
    int inputPathLength = new File(inputPath).getAbsolutePath().length();
    boolean singleFile = files.size() == 1;
    AtomicInteger errorCode = new AtomicInteger();
    for (File f : files) {
      LOGGER.info(f.getName() + " (" + fileIndex + "/" + files.size() + ")");
      fileIndex++;
      String fileDirPath = f.getParentFile().getAbsolutePath();
      File outputDir = new File(outputPath, fileDirPath
          .substring(Math.min(inputPathLength + 1, fileDirPath.length()), fileDirPath.length()));
      execute(executor, timeoutInSecondsPerDocument, singleFile && ocrEngine == OCREngine.TESSERACT,
          () ->
          {
            try {
              Document document;
              Map<String, Object> phrases;
              if (mixedLayout) {
                try (FileInputStream fileInputStream = new FileInputStream(f)) {
                  document = parseMixedLayoutDocument(fileInputStream, ocrEngine,
                      ignoreNonRenderableText,
                      minChars, handWritten, pageLevelOcr, errorCode, modelCustomizations,
                      f.getName());
                }
                if (document == null) {
                  return;
                }
                Map<String, Object> result = new VisualJsonRenderer().renderAsMap(document);
                String json = jsonify(result);
                phrases = (Map<String, Object>) result.get("phrases");
                createOutputFile(new File(outputDir, "vj"), f, "json", json);
              } else {
                MutableMap<Integer, Double> widthByPage = Maps.mutable.empty();
                MutableMap<Integer, Double> scannedness = Maps.mutable.empty();
                try (FileInputStream fileInputStream = new FileInputStream(f)) {
                  document = parseDocument(fileInputStream, pageNosToOcr,
                      pageNo -> scannedness.put(pageNo, 1.0), widthByPage, ocrEngine,
                      ignoreNonRenderableText, minChars, handWritten, pageLevelOcr, errorCode,
                      modelCustomizations, f.getName());
                }

                if (document == null) {
                  return;
                }

                getDocumentScannedness(document, scannedness);

                phrases = phraseExtractor.extract(document, scannedness, widthByPage);
                phrases.put("version", getVersion());
                String phrasesJson = jsonify(phrases);
                createOutputFile(new File(outputDir, "phrase"), f, "json", phrasesJson);

                String fileNameWithoutExtn = f.getName().substring(0, f.getName().length() - 3);
                Map<String, Object> tables = (Map<String, Object>) TableExtractor
                    .extract(document, fileNameWithoutExtn);
                String tablesJson = jsonify(tables);
                createOutputFile(new File(outputDir, "table"), f, "table.json", tablesJson);
              }

              if (html) {
                Map<Integer, Integer> groupings = getGroupings(phrases);
                createOutputFile(new File(outputDir, "html"), f, "html",
                    new HtmlRenderer().withVisualDebuggingEnabled(true).withPageBreakNos(true)
                        .render(document, groupings));
                byte[] pdfAbbyyBytes = ListIterate
                    .collectIf(document.getTransformedIntermediateSources(),
                        x -> x.getOne() == SourceType.OCRED_DOC, Pair::getTwo).getFirst();
                createOutputFile(Paths.get(outputDir.getAbsolutePath(), "ocr"), f, "pdf",
                    pdfAbbyyBytes);
              }
            } catch (Exception e) {
              LOGGER.info("encountered exception", e);
              errorCode.set(INTERNAL_ERROR);
            }
          });
    }
    executor.shutdown();
    if (singleFile && errorCode.get() != 0) {
      System.exit(errorCode.get());
    }
  }

  /**
   * Get the map with key as first integer in segment id and value as group id. Group id is
   * calculated by taking first integer in first segment of that group.
   *
   * @param phrases phrase representation of document model
   * @return map with key as first integer in segment id and value as group id.
   */
  private static Map<Integer, Integer> getGroupings(Map<String, Object> phrases) {
    Map<Integer, Integer> groupings = Maps.mutable.empty();
    for (Map page : (List<Map>) phrases.get("pages")) {
      for (Map group : (List<Map>) page.getOrDefault("groups", Lists.mutable.empty())) {
        MutableList<Integer> grouping = Lists.mutable.empty();
        for (Map line : (List<Map>) group.get("lines")) {
          for (Map segment : (List<Map>) line.get("segments")) {
            grouping.add(Integer.parseInt(((String) segment.get("id")).split("_")[0]));
          }
        }
        grouping = grouping.distinct();
        if (grouping.size() > 1) {
          for (Integer g : grouping) {
            groupings.put(g, grouping.get(0));
          }
        }
      }
    }
    return groupings;
  }

  /**
   * Compute scannedness of each page within the document. <p>Scannedness of page is computed by sum
   * of number of images that lies between two text elements / 20. </p> This number is bounded by
   * 0.5 so if scannedness > 0.5, then return 0.5
   *
   * @param document document model from which to extract the pages
   * @param scannedness output variable to populate with scannedness of each page
   */
  public static void getDocumentScannedness(Document document,
      MutableMap<Integer, Double> scannedness) {
    Element prevTextElement = null;
    Element prevImage = null;
    MutableMap<Integer, Integer> intraTextImages = Maps.mutable.empty();
    for (Element element : document.getContainingElements(
        e -> e instanceof Image || e instanceof TextElement || e instanceof PageBreak)) {
      if (element instanceof PageBreak) {
        prevTextElement = null;
        prevImage = null;
      } else if (element instanceof TextElement) {
        if (prevImage != null && prevTextElement != null) {
          PositionalElementList<Element> elementList = element.getElementList();
          // Increment intra text images page count if we encounter the image between two text elements
          intraTextImages
              .compute(elementList.getPageBreakNumber(element), (k, v) -> v == null ? 1 : v + 1);
        }
        prevImage = null;
        prevTextElement = element;
      } else if (element instanceof Image) {
        if (prevImage != null) {
          prevTextElement = null;
        }
        prevImage = element;
      }
    }
    for (int pageNo : intraTextImages.keySet()) {
      scannedness.put(pageNo, Math.min(intraTextImages.get(pageNo), 10) / 20.0);
    }
  }

  /**
   * Construct the customizations by reading the file at path {@code filePath}
   *
   * @param filePath path to file containing customizations
   * @return constructed customizations
   * @throws IOException exception to throw in case file is not able to read properly
   */
  protected static ModelCustomizations getModelCustomizationsFromFile(String filePath)
      throws IOException {
    ModelCustomizations modelCustomizations = new ModelCustomizations();
    File customizationFile = new File(filePath);
    if (customizationFile.exists()) {
      MutableList<Object> customizationList = Lists.mutable
          .ofAll(new ObjectMapper().readValue(customizationFile, List.class));
      for (Object entity : customizationList) {
        Map<String, Object> customization = (Map<String, Object>) entity;
        String customizationType = customization.get("customization_type").toString();
        MutableList<Object> params = ListAdapter.adapt((List) customization.get("params"));
        MutableList<String> deserializeParams = params.collect(String::valueOf);
        modelCustomizations
            .parseAndAdd(ModelCustomizationKey.getKey(customizationType), deserializeParams);
      }
    }
    return modelCustomizations;
  }

  public static void main(String[] args) {
    try {
      // inputs
      String inputPath = "";
      String customizationsFilePath = "";
      boolean lookupRecursively = true;
      String outputPath = "";
      int timeoutInSecondsPerDocument = 300;
      List<Integer> pageNosToOcr = Lists.mutable.of(); // zero based page indexes
      OCREngine ocrEngine = null;  // set to OCREngine.TESSERACT or OCREngine.ABBYY or null
      boolean ignoreNonRenderableText = false;  // set to true if you want to ignore any overlayed text in document
      boolean mixedLayout = false; // set to true of you want to process mixed layout documents
      boolean html = false; //set to true if you want to generate html as well for debugging
      int minChars = 0;  // set the min number of parsed chars pdf should have
      boolean handWritten = false; // detect hand written areas
      boolean pageLevelOcr = false; // run ocr on page level
      boolean isStatsDEnabled = false;  // publish metric to statsd server
      String statsDHost = "";
      String statsDPrefix = "";
      int statsDPort = -1;
      MutableList<String> statsDTags = Lists.mutable.empty();
      try {
        if (args.length > 0 || inputPath.isEmpty()) {
          Map<String, Object> opts = new Docopt(DOC).parse(args);
          inputPath = (String) opts.get("<input-path>");
          outputPath = (String) opts.get("<output-path>");
          customizationsFilePath = (String) opts.get("--customizations-path");
          lookupRecursively = !(Boolean) opts.get("--no-recurse");
          timeoutInSecondsPerDocument = Integer.parseInt((String) opts.get("--timeout"));
          boolean isTesseract = (Boolean) opts.get("--tesseract");
          boolean isAbbyy = (Boolean) opts.get("--abbyy");
          ocrEngine = isTesseract ? OCREngine.TESSERACT : isAbbyy ? OCREngine.ABBYY : null;
          ignoreNonRenderableText = (Boolean) opts.get("--ignore-overlay");
          mixedLayout = (Boolean) opts.get("--mixed-layout");
          html = (Boolean) opts.get("--html");
          minChars = Integer.parseInt((String) opts.get("--min-chars"));
          handWritten = (Boolean) opts.get("--hand-written");
          pageLevelOcr = (Boolean) opts.get("--page-level-ocr");
          isStatsDEnabled = (Boolean) opts.get("--enable-statsd");
          statsDTags = ArrayAdapter.adapt(((String)opts.get("--statsd-tags")).trim()
              .replaceAll("(^\\[)|(\\]$)", "").split(",")).toList()
              .reject(String::isEmpty);
          statsDPrefix = (String) opts.get("--statsd-prefix");
          statsDHost = (String) opts.get("--statsd-host");
          statsDPort = Integer.parseInt((String) opts.get("--statsd-port"));
        }
      } catch (Throwable e) {
        e.printStackTrace();
        System.exit(CALL_ERROR);
      }

      if (!new File(inputPath).exists() || !new File(outputPath).exists()) {
        System.exit(ARG_VALUE_ERROR);
      }
      ModelCustomizations modelCustomizations = getModelCustomizationsFromFile(
          customizationsFilePath);

      boolean isStatsDConfigured = !(statsDHost.isEmpty() || statsDPort == -1) && isStatsDEnabled;
      if (isStatsDConfigured) {
        StatsDClientWrapper.initializeClient(statsDPrefix, statsDTags, statsDHost, statsDPort);
      }

      extractFromMultipleFiles(inputPath, outputPath, lookupRecursively,
          timeoutInSecondsPerDocument, pageNosToOcr,
          ocrEngine, ignoreNonRenderableText, mixedLayout, html, minChars, handWritten,
          pageLevelOcr, modelCustomizations);

      if(isStatsDConfigured){
        StatsDClientWrapper.closeClient();
      }
    } catch (Throwable e) {
      e.printStackTrace();
      System.exit(INTERNAL_ERROR);
    }
  }

  /**
   * Create the phrase map representation for {@code document}
   *
   * @param document document model for which to create phrase map representation
   * @return phrase map representation
   */
  public Map<String, Object> extractInVGOrder(Document document) {
    return this.extract(document, true, null, null);
  }

  /**
   * Create the phrase map representation for {@code document}
   *
   * @param document document model for which to create phrase map representation
   * @param scannedness map containing scannedness of each page
   * @param widthByPage map with key page index to page width
   * @return phrase map representation
   */
  public Map<String, Object> extract(Document document, MutableMap<Integer, Double> scannedness,
      MutableMap<Integer, Double> widthByPage) {
    return this.extract(document, false, scannedness, widthByPage);
  }

  /**
   * Create the phrase map representation for {@code document}
   *
   * @param document document model for which to create phrase map representation
   * @param isVGOrder boolean flag to process elements in vertical group order
   * @param scannedness map containing scannedness of each page
   * @param widthByPage map with key page index to page width
   * @return phrase map representation
   */
  private Map<String, Object> extract(Document document, boolean isVGOrder,
      MutableMap<Integer, Double> scannedness, MutableMap<Integer, Double> widthByPage) {
    if (scannedness == null) {
      scannedness = Maps.mutable.empty();
      getDocumentScannedness(document, scannedness);
    }

    if (widthByPage == null) {
      widthByPage = Maps.mutable.empty();
    }

    VisualJsonBuilder builder = new VisualJsonBuilder();
    builder.startDocument();
    Page page = (Page) document.getContent().getElements().get(0);
    double pageWidth = page.getWidth().getMagnitude();
    double pageHeight = page.getHeight().getMagnitude();
    builder.getDocument().put("box", Maps.mutable.of("left", format(0), "top", format(0),
        "right", format(pageWidth), "bottom", format(pageHeight)));
    builder.getDocument().put("handWrittenAreas", this.getHandWrittenAreas(page));

    int pageNo = 0;
    double pageTop = 0.0;
    double prevPageBottom = 0;

    Set<ElementGroup<Element>> vgsInPage = new LinkedHashSet<>();
    List<Element> elemsInPage = Lists.mutable.empty();

    for (Element element : this.getTerminalElemIteratorEndingWithNull(document)) {
      if (element instanceof PageBreak || element == null) {
        builder.startPage();
        pageTop = element == null ? pageHeight : element.getAttribute(Top.class).getMagnitude();
        builder.getPage()
            .put("box", Maps.mutable.of("left", format(0), "top", format(prevPageBottom),
                "right", format(pageWidth), "bottom", format(pageTop)));
        builder.getPage().put("width", format(widthByPage.getOrDefault(pageNo, pageWidth)));
        builder.getPage().put("scannedness", format(scannedness.getOrDefault(pageNo, 0.0)));

        if (!isVGOrder || this.isSingleColLayout(elemsInPage, prevPageBottom, pageTop)) {
          this.processInAppearanceOrder(builder, elemsInPage);
        } else {
          this.processInVGOrder(builder, vgsInPage);
        }
        vgsInPage = new LinkedHashSet<>();
        elemsInPage = Lists.mutable.empty();
        pageNo++;
        prevPageBottom = pageTop;
      } else if (element instanceof TextElement) {
        if (isVGOrder) {
          vgsInPage.add(element.getPositionalContext().getVerticalGroup());
        }
        elemsInPage.add(element);
      } else if (element instanceof GraphicalElement) {
        String linesKey = element instanceof HorizontalLine ? "horizontalLines" : "verticalLines";
        List lines = (List) builder.getDocument()
            .computeIfAbsent(linesKey, x -> Lists.mutable.empty());
        lines.add(Lists.mutable.of(format(element.getAttribute(Left.class).getMagnitude()),
            format(element.getAttribute(Top.class).getMagnitude()),
            format(element.getAttribute(Stretch.class).getMagnitude())));
      }
    }
    return builder.getDocument();
  }

  /**
   * @return all terminal elements in {@code document} structure hierarchy along with null element
   * at the end.
   */
  private Iterable<Element> getTerminalElemIteratorEndingWithNull(Document document) {
    return IterableUtils.chainedIterable(document.getContainingElements(Element::isTerminal),
        Collections.singleton(null));
  }

  /**
   * @return all the hand written areas from the {@code page}
   */
  private List<Map> getHandWrittenAreas(Element page) {
    return ListIterate.collectIf(page.getAttributeValue(PageLayout.class, Lists.mutable.empty()),
        x -> Objects.equals(x.getTwo(), PageLayout.HAND_WRITTEN),
        x -> Maps.mutable
            .of("left", format(x.getOne().getMinX()), "top", format(x.getOne().getMinY()), "right",
                format(x.getOne().getMaxX()), "bottom", format(x.getOne().getMaxY())));
  }

  /**
   * Check if the page is in a single column layout. Algo: Page is single column if average max gap
   * of line is less than <0.2 and shifted lines are not more than or equal to 40% of lines
   *
   * @param elements Element in the page
   * @param pageTop yth coordinate of page top
   * @param pageBottom yth coordinate of page bottom
   * @return True if the page is single column layout otherwise return False
   */
  private boolean isSingleColLayout(List<Element> elements, double pageTop, double pageBottom) {
    double gapCount = 0; // sum of maximum gap between words in each line
    double shiftedLineCount = 0;  // Number of lines where left margin > 150pts
    int lineCount = 0; // Numbers of lines
    double lineGapCount = 0; // Maximum gap between words
    Element prevElement = null;
    double prevRight = 0;
    for (Element element : elements) {
      double top = element.getAttribute(Top.class).getMagnitude();
      if (top > pageTop + 150 && top < pageBottom - 150) {
        double left = element.getAttribute(Left.class).getMagnitude();
        double right = left + element.getAttribute(Width.class).getMagnitude();
        boolean sameLine = prevElement != null
            && PositionalElementList.compareByHorizontalAlignment(prevElement, element) == 0;
        if (sameLine) {
          lineGapCount = Math.max(Math.min((left - prevRight) / 70, 2), lineGapCount);
        } else {
          lineCount += 1;
          gapCount += lineGapCount;
          lineGapCount = 0;
          if (left > 150) {
            shiftedLineCount += 1;
          }
        }
        prevRight = right;
        prevElement = element;
      }
    }
    gapCount += lineGapCount;
    return gapCount / lineCount < 0.2 && shiftedLineCount / lineCount < 0.4;
  }

  /**
   * Populate groups, lines and segments in visual json using {@code pagesVG}
   */
  private void processInVGOrder(VisualJsonBuilder builder, Set<ElementGroup<Element>> pagesVG) {
    for (ElementGroup<Element> vg : pagesVG) {
      PagePartitionType pagePartitionType = Iterate.getFirst(vg.getElements())
          .getPositionalContext().getPagePartitionType();
      String groupName = pagePartitionType == PagePartitionType.HEADER ? "headerGroups"
          : pagePartitionType == PagePartitionType.FOOTER ? "footerGroups" : "groups";
      builder.startGroup(groupName);
      List<List<Element>> linesInVG = this.getLinesInVG(vg);
      for (List<Element> line : linesInVG) {
        builder.startLine();
        for (Element elem : line) {
          List<Element> alternateElems = elem
              .getAttributeValue(AlternateRepresentations.class, Lists.mutable.of(elem));
          for (int i = 0; i < alternateElems.size(); i++) {
            addSegment(builder, alternateElems.get(i), getOrNull(alternateElems, i - 1),
                getOrNull(alternateElems, i + 1), elem, i);
          }
        }
      }
    }
  }

  /**
   * Populate visual json for {@code elements} as it is appearing in the document
   *
   * @param builder visual json builder
   * @param elements elements in the page
   */
  private void processInAppearanceOrder(VisualJsonBuilder builder, List<Element> elements) {
    double defaultParagraphSpaceFactor = 1.5;
    Element prevTextElement = null;
    double paragraphSpaceFactor = this
        .findParagraphSpaceFactor(elements, defaultParagraphSpaceFactor);
    for (Element element : elements) {
      PagePartitionType prevPartition = prevTextElement == null ? null
          : prevTextElement.getPositionalContext().getPagePartitionType();
      PagePartitionType currPartition = element.getPositionalContext().getPagePartitionType();
      prevTextElement = prevPartition != currPartition ? null : prevTextElement;
      TextElement textElement = (TextElement) element;
      PositionalContext<Element> context = textElement.getPositionalContext();
      if (context.getBoundingRectangle() != null && context.getTabularGroup() != null) {
        TabularElementGroup<Element> table = context.getTabularGroup();
        MutableList<Element> mergedCell = table
            .getMergedCell(context.getTabularRow(), context.getTabularColumn()).getElements();
        if (mergedCell.getFirst() == textElement) {
          for (int i = 0; i < mergedCell.size(); i++) {
            addSegment(builder, mergedCell.get(i), i > 0 ? mergedCell.get(i - 1) : null,
                defaultParagraphSpaceFactor);
          }
        }
      } else {
        addSegment(builder, element, prevTextElement, paragraphSpaceFactor);
        prevTextElement = element;
      }
    }
  }

  /**
   * Compute the spacing factor between two paragraphs
   */
  private double findParagraphSpaceFactor(List<Element> elements,
      double defaultParagraphSpaceFactor) {
    double paragraphSpaceFactor = defaultParagraphSpaceFactor;
    double delta = -0.25;
    while (paragraphSpaceFactor >= 1 && paragraphSpaceFactor <= 2.5) {
      Element prevElement = null;
      int lineCount = 0;
      int groupCount = 0;
      for (Element element : elements) {
        if (element.getPositionalContext().getPagePartitionType() != PagePartitionType.CONTENT) {
          continue;
        }
        boolean isLineChange = prevElement == null
            || PositionalElementList.compareByHorizontalAlignment(prevElement, element) != 0;
        boolean isGroupChange =
            prevElement == null || isLineChange && isParaChange(element, prevElement,
                paragraphSpaceFactor);
        if (isLineChange) {
          lineCount += 1;
        }
        if (isGroupChange) {
          groupCount += 1;
        }
        prevElement = element;
      }
      if (lineCount <= 5) {
        return paragraphSpaceFactor;
      }
      boolean tooManyGroups = groupCount >= lineCount * 0.5;
      if (groupCount > 2 && !tooManyGroups) {
        return paragraphSpaceFactor;
      }
      if (tooManyGroups) {
        if (paragraphSpaceFactor == defaultParagraphSpaceFactor) {
          delta = 0.5;
        } else if (delta < 0) {
          return paragraphSpaceFactor - delta;
        }
      }
      paragraphSpaceFactor += delta;
    }
    return paragraphSpaceFactor;
  }

  /**
   * @return lines present in the vertical group {@code vg}
   */
  public List<List<Element>> getLinesInVG(ElementGroup<Element> vg) {
    List<List<Element>> linesInVG = Lists.mutable.empty();
    List<Element> line = Lists.mutable.empty();
    Element prevElem = null;
    //This element here is a TextElement
    for (Element elem : vg.getElements()) {
      if (prevElem != null
          && PositionalElementList.compareByHorizontalAlignment(prevElem, elem) != 0) {
        linesInVG.add(line);
        line = Lists.mutable.empty();
      }
      line.add(elem);
      prevElem = elem;
    }
    linesInVG.add(line);
    return linesInVG;
  }

  /**
   * Enum representing extension. The enum contains method to get appropriate parsed based on
   * extension.
   */
  public enum FileParserDecoder {
    pdf(Lists.mutable.of(PDF)) {
      @Override
      public Parser getParser(ModelCustomizations modelCustomizations) {
        PdfParser pdfParser = new PdfParser();
        ParserSettings pdfParserSettings = pdfParser.getParserSettings();
        // make sure to attach the scanned pdf parser for all cases, unless explicitly disabled
        OCREngine ocrEngine = modelCustomizations
            .retrieveOrDefault(ModelCustomizationKey.OCR_ENGINE, null);
        pdfParser.withScannedPdfParser(getScannedPdfParser(ocrEngine))
            .withPageNosToOcr(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.PAGES_TO_OCR,
                    ListAdapter.adapt(pdfParserSettings.getPageNosToOcr())))
            .withLineMergeEpsilon(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.LINE_MERGE_EPSILON,
                    pdfParserSettings.getLineMergeEpsilon()))
            .withIgnoreNonRenderableText(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.IGNORE_NON_RENDERABLE_TEXT,
                    pdfParserSettings.isIgnoreNonRenderableText()))
            .withUnderlineDetection(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.UNDERLINE_DETECTION,
                    pdfParserSettings.isUnderlineDetection()))
            .withImageBasedCharDetection(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.IMAGE_BASED_CHAR_DETECTION,
                    pdfParserSettings.isImageBasedCharDetection()))
            .withFontChangeSegmentation(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.FONT_CHANGE_SEGMENTATION,
                    pdfParserSettings.isFontChangeSegmentation()))
            .withMaxTextElementToLineCountRatio(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.MAX_TEXT_ELEMENT_TO_LINE_COUNT_RATIO,
                    pdfParserSettings.getMaxTextElementToLineCountRatio()))
            .withDynamicSpaceWidthComputationEnabled(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.ENABLE_DYNAMIC_SPACE_WIDTH_COMPUTATION,
                    pdfParserSettings.isDynamicSpaceWidthComputationEnabled()))
            .withScanned(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.SCANNED, pdfParserSettings.isScanned()))
            .withPageLevelSpacingScaling(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.PAGE_LEVEL_SPACING_SCALING,
                    pdfParserSettings.isPageLevelSpacingScaled()))
            .withHandWrittenTextDetection(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.ENABLE_HAND_WRITTEN_TEXT_DETECTION,
                    pdfParserSettings.isHandWrittenTextDetection()))
            .withAllowedScannedness(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.ALLOWED_SCANNEDNESS,
                    pdfParserSettings.getAllowedScannedness()))
            .withPageLevelOcr(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.PAGE_LEVEL_OCR,
                    pdfParserSettings.isPageLevelOcr()))
            .withMaxPagesAllowed(modelCustomizations
                .retrieveOrDefault(ModelCustomizationKey.MAX_PAGES_ALLOWED,
                    pdfParserSettings.getMaxPagesAllowed()));
        return pdfParser;
      }
    },
    html(Lists.mutable.of(HTML, HTM)) {
      @Override
      public Parser getParser(ModelCustomizations modelCustomizations) {
        throw new NotImplementedException();
      }
    },
    xlsx(Lists.mutable.of(XLSX, XLS)) {
      @Override
      public Parser getParser(ModelCustomizations modelCustomizations) {
        throw new NotImplementedException();

      }
    },
    txt(Lists.mutable.of(TXT)) {
      @Override
      public Parser getParser(ModelCustomizations modelCustomizations) {
        throw new NotImplementedException();

      }
    },
    docx(Lists.mutable.of(DOCX)) {
      @Override
      public Parser getParser(ModelCustomizations modelCustomizations) {
        throw new NotImplementedException();

      }
    };

    private final MutableList<String> extensions;

    FileParserDecoder(MutableList<String> extensions) {
      this.extensions = extensions;
    }

    /**
     * Parse the document with stream {@code inputStream} and file {@code filename}
     *
     * @param fileName name of the file which is to be parsed
     * @param inputStream document stream which is to be parsed
     * @return document model
     * @throws Exception exception to throw in case extension of file is invalid
     */
    public static Document getParsedDocument(String fileName, InputStream inputStream)
        throws Exception {
      FileParserDecoder decoder = getFileParserDecoder(fileName);
      if (decoder == null) {
        throw new RuntimeException("No parser found for file");
      }
      Document document = decoder.parse(inputStream, new ModelCustomizations());
      document.setDocumentSource(fileName);
      return document;
    }

    /**
     * Getter to get file parser depending on extension of file {@code filename}
     *
     * @param fileName name of the file which is to be parsed
     * @return corresponding file parser
     */
    public static FileParserDecoder getFileParserDecoder(String fileName) {
      String lowerCaseName = fileName.toLowerCase();
      return Lists.mutable.of(FileParserDecoder.values())
          .detect(fileExtension -> fileExtension.extensions
              .anySatisfy(extension -> lowerCaseName.endsWith("." + extension.toLowerCase())));
    }

    /**
     * Getter for parser
     *
     * @param modelCustomizations customizations to adjust parser's behaviour
     * @return parser object
     */
    public abstract Parser getParser(ModelCustomizations modelCustomizations);

    /**
     * Parse the document with stream {@code inputStream} and file {@code filename}
     *
     * @param modelCustomizations customizations to adjust parser's behaviour
     * @param inputStream document stream which is to be parsed
     * @return document model
     * @throws Exception exception to throw in case extension of file is invalid
     */
    public Document parse(InputStream inputStream, ModelCustomizations modelCustomizations)
        throws Exception {
      return this.getParser(modelCustomizations).parse(inputStream);
    }

    public MutableList<String> getExtensions() {
      return this.extensions;
    }
  }
}
