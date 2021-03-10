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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.tuple.Tuples;
import com.gs.ep.docknight.model.ComparableBufferedImage;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.Form;
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.Length.Unit;
import com.gs.ep.docknight.model.attribute.AlternateRepresentations;
import com.gs.ep.docknight.model.attribute.FormData;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.ImageData;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Stretch;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.element.FormElement;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.Image;
import com.gs.ep.docknight.model.element.VerticalLine;
import com.gs.ep.docknight.util.ImageUtils;
import com.gs.ep.docknight.util.LRUCache;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.QuickSort;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.tesseract;
import org.bytedeco.javacpp.tesseract.ETEXT_DESC;
import org.bytedeco.javacpp.tesseract.TessBaseAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Class to extract graphics likes lines, colored areas, images etc
 */
class GraphicsExtractor extends PDFGraphicsStreamEngine {

  protected static final Logger LOGGER = LoggerFactory.getLogger(GraphicsExtractor.class);
  static final int WHITE = 16777215;
  static final int TRANSPARENT_COLOR = -1;
  static final int BLACK = 0;
  private static final double DISTINCT_EPSILON = 0.01;
  private static final double THRESHOLD = 5;
  private static final double MAX_THRESHOLD_FOR_FORM_IMAGE = 30;
  private static final double MAX_IMAGE_BY_PAGE_AREA_RATIO = 0.25;
  private static final double SCANNED_PAGE_IMAGE_BY_PAGE_AREA_RATIO = 0.9;
  private static final double MIN_SLOPE_VERTICAL_LINE = 5.67;  // tan(80 degree)
  private static final double MAX_SLOPE_HORIZONTAL_LINE = 0.18;  // tan(10 degree)
  private static final PDColor WHITE_PDCOLOR = new PDColor(new float[]{1}, PDDeviceGray.INSTANCE);
  private static final LRUCache<Pair<PDColor, Integer>> RGB_CACHE = new LRUCache<>(4);
  private final PDDocument document;
  private final int pageNo;
  private final ParserSettings settings;
  private final GeneralPath linePath;
  private final double pageArea;
  private final Rectangle pageRectangle;
  // Variables that will be used in PDFDocumentStripper
  private final MutableList<ExtractedLines> extractedLinesList;            // Contains all the lines extracted
  private final MutableList<Image> images;
  private final double pageHeight;
  private final double pageWidth;
  private final AdjustedPDPage adjustedPage;
  private final List<ColoredArea> coloredAreas;
  private final List<Rectangle> handWrittenAreas;
  private final MutableList<Area> textClippers;
  private final MutableList<ImageString> imageStrings;
  private MutableMap<ComparableBufferedImage, ImageString> imageStringMap;
  private TessBaseAPI tesseractAPI;
  private int textOperationIndex;
  private int clipWindingRule = -1;
  private ExtractedLines extractedLines;
  private double totalImageArea;

  GraphicsExtractor(PDPage page, PDDocument document, int pageNo, int textRotation,
      ParserSettings settings) {
    super(page);
    this.document = document;
    this.pageNo = pageNo;
    this.settings = settings;
    this.extractedLinesList = Lists.mutable.empty();
    this.images = Lists.mutable.empty();
    this.linePath = new GeneralPath();
    this.adjustedPage = new AdjustedPDPage(page, textRotation);
    this.pageHeight = this.adjustedPage.getPageHeightAdj();
    this.pageWidth = this.adjustedPage.getPageWidthAdj();
    this.pageRectangle = new Rectangle((int) this.pageWidth, (int) this.pageHeight);
    this.pageArea = this.pageHeight * this.pageWidth;
    this.totalImageArea = 0;
    this.coloredAreas = Lists.mutable.empty();
    this.textClippers = Lists.mutable.of(new Area(this.pageRectangle));
    this.imageStrings = Lists.mutable.empty();
    this.handWrittenAreas = Lists.mutable.empty();
  }

  /**
   * Convert the {@code box} coordinates by taking {@code pageBBox} bottom left coordinate as
   * origin. Also apply rotation to this {@code box} using {@code rotationCount}
   */
  private static PDRectangle getRotatedAndTranslatedRectangle(int rotationsCount, PDRectangle box,
      PDRectangle pageBBox) {
    box = new PDRectangle(box.getLowerLeftX() - pageBBox.getLowerLeftX(),
        box.getLowerLeftY() - pageBBox.getLowerLeftY(), box.getWidth(), box.getHeight());
    if (rotationsCount == 0) {
      return box;
    }
    Area boxArea = new Area(
        new Rectangle2D.Double(box.getLowerLeftX(), box.getLowerLeftY(), box.getWidth(),
            box.getHeight()));
    double pageWidth = pageBBox.getWidth();
    double pageHeight = pageBBox.getHeight();
    double rotationRadians = Math.toRadians(90.0 * rotationsCount);
    AffineTransform rotateInstance;
    switch (rotationsCount) {
      case 1:
        rotateInstance = AffineTransform
            .getRotateInstance(rotationRadians, pageHeight / 2, pageHeight / 2);
        break;
      case 2:
        rotateInstance = AffineTransform
            .getRotateInstance(rotationRadians, pageHeight / 2, pageWidth / 2);
        break;
      case 3:
        rotateInstance = AffineTransform
            .getRotateInstance(rotationRadians, pageWidth / 2, pageWidth / 2);
        break;
      default:
        rotateInstance = AffineTransform.getRotateInstance(0);
    }
    boxArea = new Area(rotateInstance.createTransformedShape(boxArea));
    Rectangle2D boxBounds = boxArea.getBounds2D();
    return new PDRectangle((float) boxBounds.getX(), (float) boxBounds.getY(),
        (float) boxBounds.getWidth(), (float) boxBounds.getHeight());
  }

  /**
   * @return True if length of horizontal line is above threshold
   */
  private static boolean isHorizontalLineAboveThreshold(Line2D line) {
    double stretch = line.getX2() - line.getX1();
    return stretch > THRESHOLD;
  }

  /**
   * @return True if length of vertical line is above threshold
   */
  private static boolean isVerticalLineAboveThreshold(Line2D line) {
    double stretch = line.getY2() - line.getY1();
    return stretch > THRESHOLD;
  }

  /**
   * @return True if {@code color} is not white and transparent color
   */
  private static boolean isVisible(int color) {
    return color != WHITE && color != TRANSPARENT_COLOR;
  }

  /**
   * @return stroking color from {@code graphicsState}
   */
  public static int getStrokingColor(PDGraphicsState graphicsState) {
    if (graphicsState.getAlphaConstant() == 0) {
      return TRANSPARENT_COLOR;
    } else {
      try {
        return toRGB(graphicsState.getStrokingColor());
      } catch (Exception e) {
        return BLACK;
      }
    }
  }

  /**
   * @return fill color from {@code graphicsState}
   */
  public static int getNonStrokingColor(PDGraphicsState graphicsState) {
    boolean isLuminous = graphicsState.getSoftMask() != null
        && graphicsState.getSoftMask().getSubType() == COSName.LUMINOSITY;
    if (isLuminous || graphicsState.getNonStrokeAlphaConstants() == 0) {
      return TRANSPARENT_COLOR;
    } else {
      try {
        return toRGB(graphicsState.getNonStrokingColor());
      } catch (Exception e) {
        return WHITE;
      }
    }
  }

  /**
   * @return RGB representation of color {@code pdfColor}
   */
  private static int toRGB(PDColor pdColor) throws IOException {
    Pair<PDColor, Integer> pdColorAndRgb = RGB_CACHE.get(x -> x.getOne() == pdColor);
    if (pdColorAndRgb == null) {
      pdColorAndRgb = Tuples.pair(pdColor, pdColor.toRGB());
      RGB_CACHE.add(pdColorAndRgb);
    }
    return pdColorAndRgb.getTwo();
  }

  /**
   * If operator name is: - "RG" - Change the stroke color space to RGB and set the stroke color -
   * "rg" - Change the fill color space to RGB and set the fille color
   */
  public static boolean handleColorOperatorException(String operatorName,
      PDGraphicsState graphicsState) {
    // default to white on exception reading the color from pdf stream
    if (operatorName.equals("rg")) {
      graphicsState.setNonStrokingColor(WHITE_PDCOLOR);
      return true;
    } else if (operatorName.equals("RG")) {
      graphicsState.setStrokingColor(WHITE_PDCOLOR);
      return true;
    }
    return false;
  }

  /**
   * Populate output arguments {@code horizontalLines} and {@code verticalLines} using the lines
   * formed by coordinates ({@code prevX}, @{@code prevY}) and ({@code x}, {@code y})
   */
  private static void extractAndAppendLine(double prevX, double prevY, double x, double y,
      List<Line2D> horizontalLines, List<Line2D> verticalLines) {
    double slope = Math.abs(y - prevY) / Math.abs(x - prevX);
    if (Double.isNaN(slope) || slope >= MIN_SLOPE_VERTICAL_LINE) {
      if (prevY < y) {
        verticalLines.add(new Line2D.Double(prevX, prevY, x, y));
      } else {
        verticalLines.add(new Line2D.Double(x, y, prevX, prevY));
      }
    } else if (slope <= MAX_SLOPE_HORIZONTAL_LINE) {
      if (prevX < x) {
        horizontalLines.add(new Line2D.Double(prevX, prevY, x, y));
      } else {
        horizontalLines.add(new Line2D.Double(x, y, prevX, prevY));
      }
    }
  }

  @Override
  public void processPage(PDPage page) throws IOException {
    int rotationsCount = (this.adjustedPage.getPageRotation() / 90) % 4;
    PDRectangle pageBBox = page.getBBox();         // Bounding box of page
    PDRectangle cropBox = page
        .getCropBox();     // Size of page containing actual visible area. It is primarily used to hide the content outside this area.
    PDRectangle mediaBox = page
        .getMediaBox();   // Size off page on which the drawing will take place. Example: A4
    page.setCropBox(getRotatedAndTranslatedRectangle(rotationsCount, cropBox, pageBBox));
    page.setMediaBox(getRotatedAndTranslatedRectangle(rotationsCount, mediaBox, pageBBox));
    super.processPage(page);
    if ((this.images.notEmpty()
        || this.totalImageArea / this.pageArea >= SCANNED_PAGE_IMAGE_BY_PAGE_AREA_RATIO)
        && this.settings.isHandWrittenTextDetection()) {
      LOGGER.debug("Converting page to image for pageNo " + this.pageNo);
      BufferedImage image = new PDFRenderer(this.document).renderImage(this.pageNo);
      this.handWrittenAreas.addAll(ImageUtils.findHandWrittenAreas(image));
    }
    page.setCropBox(cropBox);
    page.setMediaBox(mediaBox);
  }

  @Override
  public Point2D.Float transformedPoint(float x, float y) {
    Point2D.Float transformedPoint = super.transformedPoint(x, y);
    Point2D transformedXY = this.adjustedPage
        .getXYAdj(transformedPoint.getX(), transformedPoint.getY());
    transformedPoint.setLocation(transformedXY.getX(), transformedXY.getY());
    return transformedPoint;
  }

  @Override
  protected void operatorException(Operator operator, List<COSBase> operands, IOException e)
      throws IOException {
    try {
      super.operatorException(operator, operands, e);
    } catch (IOException e1) {
      if (!handleColorOperatorException(operator.getName(), this.getGraphicsState())) {
        throw e1;
      }
    }
  }

  @Override
  protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
    PDRectangle bBox = null;
    PDFormXObject pdFormXObject = null;
    if (operator.getName().equals("BT")) {
      this.textOperationIndex++;
      this.textClippers.add(this.getGraphicsState().getCurrentClippingPath());
    } else if (operator.getName().equals("Do")) {

      COSBase base0 = operands.get(0);
      COSName objectName = (COSName) base0;
      PDXObject xobject = this.getResources().getXObject(objectName);
      if (xobject instanceof PDFormXObject) {
        pdFormXObject = (PDFormXObject) xobject;
        bBox = pdFormXObject.getBBox();
        Rectangle2D adjBbox = this.adjustedPage
            .getRectangleAdj(bBox.getLowerLeftX(), bBox.getLowerLeftY(), bBox.getWidth(),
                bBox.getHeight());
        pdFormXObject.setBBox(new PDRectangle((float) adjBbox.getMinX(), (float) adjBbox.getMinY(),
            (float) adjBbox.getWidth(), (float) adjBbox.getHeight()));
      }
    }
    try {
      super.processOperator(operator, operands);
      if (bBox != null) {
        pdFormXObject.setBBox(bBox);
      }
    } catch (UnsupportedOperationException e) {
      LOGGER.info(e.getMessage());
    }
  }

  private ExtractedLines getExtractedLines() {
    if (this.extractedLines == null) {
      this.extractedLines = new ExtractedLines(this.extractedLinesList,
          this.settings.getLineMergeEpsilon());
    }
    return this.extractedLines;
  }

  public AdjustedPDPage getAdjustedPage() {
    return this.adjustedPage;
  }

  public List<VerticalLine> getMergedVerticalLines() {
    return this.getExtractedLines().verticalLines.collect(this::clipLine)
        .collectIf(GraphicsExtractor::isVerticalLineAboveThreshold, this::toVerticalLine);
  }

  public List<HorizontalLine> getMergedHorizontalLines() {
    return this.getExtractedLines().horizontalLines.collect(this::clipLine)
        .collectIf(GraphicsExtractor::isHorizontalLineAboveThreshold, this::toHorizontalLine);
  }

  private Line2D clipLine(Line2D line) {
    Line2D l = new Line2D.Double(Math.max(0, line.getX1()), Math.max(0, line.getY1()),
        Math.min(this.pageWidth, line.getX2()), Math.min(this.pageHeight, line.getY2()));
    return
        l.getX1() > this.pageWidth || l.getX2() < 0 || l.getY1() > this.pageHeight || l.getY2() < 0
            ? new Line2D.Double() : l;
  }

  public List<ColoredArea> getColoredAreas() {
    return this.coloredAreas;
  }

  public List<Rectangle> getHandWrittenAreas() {
    return this.handWrittenAreas;
  }

  public List<Area> getTextClippers() {
    return this.textClippers;
  }

  public double getPageHeight() {
    return this.pageHeight;
  }

  public double getPageWidth() {
    return this.pageWidth;
  }

  private HorizontalLine toHorizontalLine(Line2D line) {
    double stretch = line.getX2() - line.getX1();
    return new HorizontalLine()
        .add(new Top(new Length(this.pageHeight - line.getY1(), Unit.pt)))
        .add(new Left(new Length(line.getX1(), Unit.pt)))
        .add(new Stretch(new Length(stretch, Unit.pt)));
  }

  private VerticalLine toVerticalLine(Line2D line) {
    double stretch = line.getY2() - line.getY1();
    return new VerticalLine()
        .add(new Top(new Length(this.pageHeight - line.getY2(), Unit.pt)))
        .add(new Left(new Length(line.getX2(), Unit.pt)))
        .add(new Stretch(new Length(stretch, Unit.pt)));
  }

  public List<Image> getImages() {
    return this.totalImageArea / this.pageArea < SCANNED_PAGE_IMAGE_BY_PAGE_AREA_RATIO ? this.images
        : Lists.mutable.empty();
  }

  public double getTotalImageArea() {
    return this.totalImageArea;
  }

  public List<ImageString> getImageStrings() {
    return this.imageStrings;
  }

  /**
   * Add the close path connecting points {@code p0}, {@code p1}, {@code p2} and {@code p3}
   */
  @Override
  public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
    this.linePath.moveTo((float) p0.getX(), (float) p0.getY());
    this.linePath.lineTo((float) p1.getX(), (float) p1.getY());
    this.linePath.lineTo((float) p2.getX(), (float) p2.getY());
    this.linePath.lineTo((float) p3.getX(), (float) p3.getY());
    this.linePath.closePath();
  }

  /**
   * Populate class variables images and imageStrings
   */
  @Override
  public void drawImage(PDImage pdImage) throws IOException {
    Matrix matrix = this.getGraphicsState().getCurrentTransformationMatrix();
    double imageHeight = matrix.getScalingFactorY();
    double imageWidth = matrix.getScalingFactorX();
    double imageArea = imageHeight * imageWidth;
    Point2D imageXY = this.adjustedPage.getXYAdj(matrix.getTranslateX(), matrix.getTranslateY());
    this.totalImageArea += imageArea;

    if (imageArea / this.pageArea <= MAX_IMAGE_BY_PAGE_AREA_RATIO && imageHeight >= THRESHOLD
        && imageWidth >= THRESHOLD) {
      ComparableBufferedImage imageData = new ComparableBufferedImage(pdImage.getImage());
      Image image = this
          .setRectangularElementAttributes(new Image(), imageXY, imageWidth, imageHeight)
          .add(new ImageData(imageData));

      if (this.settings.isImageBasedFormDetection() && imageHeight <= MAX_THRESHOLD_FOR_FORM_IMAGE
          && imageWidth <= MAX_THRESHOLD_FOR_FORM_IMAGE) {
        Form form = ImageUtils.parseAsForm(imageData.getBufferedImage());
        if (form != null) {
          image.add(new AlternateRepresentations(Lists.mutable.of(
              this.setRectangularElementAttributes(new FormElement(), imageXY, imageWidth,
                  imageHeight)
                  .add(new FormData(form)))));
        }
      }
      this.images.add(image);
    } else if (imageHeight < 2 * THRESHOLD && imageWidth < 2 * THRESHOLD && this.settings
        .isImageBasedCharDetection())  // look for image based characters
    {
      BufferedImage image = pdImage.getImage();
      ImageString imageString = null;
      try {
        imageString = this.detectCharInImage(new ComparableBufferedImage(image));
      } catch (Exception e) {
        LOGGER.warn("Error encountered while detecting character in image", e);
      }

      if (imageString != null) {
        double hFactor = imageWidth / image.getWidth();
        double vFactor = imageHeight / image.getHeight();
        this.imageStrings.add(new ImageString(imageString.left * hFactor + imageXY.getX(),
            imageString.top * vFactor + this.pageHeight - imageXY.getY() - imageHeight,
            imageString.width * hFactor,
            imageString.height * vFactor,
            imageString.str));
      }
    }
  }

  /**
   * Add attributes Top, Left, Height and Width to the {@code element}
   */
  private <E extends Element, K extends com.gs.ep.docknight.model.element.Rectangle<E>> K setRectangularElementAttributes(
      K element, Point2D coordinatesXY,
      double width, double height) {
    element.add(new Top(new Length(this.pageHeight - coordinatesXY.getY() - height, Unit.pt)));
    element.add(new Left(new Length(coordinatesXY.getX(), Unit.pt)));
    element.add(new Height(new Length(height, Unit.pt)));
    element.add(new Width(new Length(width, Unit.pt)));
    return element;
  }

  /**
   * @return text from {@code image} using tesseract and if that failed, try to extract using
   * heuristics else return null
   */
  private ImageString detectCharInImage(ComparableBufferedImage image) throws IOException {
    if (this.imageStringMap == null) {
      this.imageStringMap = Maps.mutable.empty();
      try {
        this.tesseractAPI = ImageUtils
            .getTesseractAPI(Lists.mutable.of("eng"), tesseract.PSM_SINGLE_CHAR);
      } catch (Exception ignored) {
      }
    }
    ImageString imageString = this.imageStringMap.get(image);
    if (imageString == null) {
      Rectangle visibleImageArea = ImageUtils.extractVisibleAreaFromImage(image.getBufferedImage());
      if (visibleImageArea == null) {
        return null;
      }
      String text = "";
      if (this.tesseractAPI != null) {
        this.tesseractAPI.SetImage(ImageUtils.toPIXImage(image.getBufferedImage()));
        this.tesseractAPI.Recognize(new ETEXT_DESC());
        BytePointer textPointer = this.tesseractAPI.GetUTF8Text();
        text = textPointer.getString().trim();
        textPointer.deallocate();
      }
      if (text.isEmpty()) {
        // if tesseract fails, try heuristic based on char's bounding rectangle
        double widthHeightRatio = (double) visibleImageArea.width / visibleImageArea.height;
        text = widthHeightRatio > 1.5 ? "-"
            : widthHeightRatio < 0.5 ? "," : widthHeightRatio < 0.8 ? "$" : ".";
      }
      imageString = new ImageString(visibleImageArea, text);
      this.imageStringMap.put(image, imageString);
    }
    return imageString;
  }

  @Override
  public void clip(int windingRule) {
    this.clipWindingRule = windingRule;
  }

  @Override
  public void moveTo(float x, float y) {
    this.linePath.moveTo(x, y);
  }

  @Override
  public void lineTo(float x, float y) {
    this.linePath.lineTo(x, y);
  }

  @Override
  public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
  }

  @Override
  public Point2D getCurrentPoint() {
    return this.linePath.getCurrentPoint();
  }

  @Override
  public void closePath() {
    this.linePath.closePath();
  }

  @Override
  public void endPath() {
    // Cling winding rules is assigned, then fill the path using the given winding rule (even-odd or nonzero winding number)
    if (this.clipWindingRule != -1) {
      this.linePath.setWindingRule(this.clipWindingRule);
      this.getGraphicsState().intersectClippingPath(this.linePath);
      this.clipWindingRule = -1;
    }
    this.linePath.reset();
  }

  @Override
  public void strokePath() {
    int strokingColor = getStrokingColor(this.getGraphicsState());
    if (isVisible(strokingColor)) {
      ExtractedLines extractedLines = this.extractPath();
      this.extractedLinesList.add(extractedLines);
    }
    this.linePath.reset();
  }

  @Override
  public void fillPath(int windingRule) {
    int nonStrokingColor = getNonStrokingColor(this.getGraphicsState());
    if (nonStrokingColor != TRANSPARENT_COLOR) {
      ExtractedLines extractedLines = this.extractPath();
      if (this.addLinesForFillingAndCheckIfStrokingRequired(extractedLines, nonStrokingColor)) {
        this.extractedLinesList.add(extractedLines);
      }
    }
    this.linePath.reset();
  }

  @Override
  public void fillAndStrokePath(int windingRule) {
    int nonStrokingColor = getNonStrokingColor(this.getGraphicsState());
    int strokingColor = getStrokingColor(this.getGraphicsState());
    if (nonStrokingColor != TRANSPARENT_COLOR || isVisible(strokingColor)) {
      ExtractedLines extractedLines = this.extractPath();
      boolean isStrokingRequired = isVisible(strokingColor);
      if (nonStrokingColor != TRANSPARENT_COLOR) {
        isStrokingRequired |= this
            .addLinesForFillingAndCheckIfStrokingRequired(extractedLines, nonStrokingColor);
      }
      if (isStrokingRequired) {
        this.extractedLinesList.add(extractedLines);
      }
    }
    this.linePath.reset();
  }

  @Override
  public void shadingFill(COSName shadingName) {
  }

  /**
   * Populate colored areas class variable and {@return true if lines can be added to
   * extracedLinesList class variable, else False}
   */
  private boolean addLinesForFillingAndCheckIfStrokingRequired(ExtractedLines lines, int color) {
    boolean hasHorizontalLineAboveThreshold = lines.horizontalLines
        .anySatisfy(GraphicsExtractor::isHorizontalLineAboveThreshold);
    boolean hasVerticalLineAboveThreshold = lines.verticalLines
        .anySatisfy(GraphicsExtractor::isVerticalLineAboveThreshold);
    Rectangle coloredArea = this.getRectangleIfExists(lines);
    if (coloredArea != null && hasHorizontalLineAboveThreshold && hasVerticalLineAboveThreshold) {
      if (!coloredArea.isEmpty()) {
        this.coloredAreas.add(new ColoredArea(coloredArea, color, this.textOperationIndex));
      }
      return false;
    }
    return color != WHITE && (hasHorizontalLineAboveThreshold || hasVerticalLineAboveThreshold);
  }

  /**
   * Check if rectangle can be formed from the lines present in {@code lines} and if it is present,
   * {@return the rectangle created from those lines}
   */
  private Rectangle getRectangleIfExists(ExtractedLines lines) {
    if (lines.horizontalLines.size() == 2 && lines.verticalLines.size() == 2) {
      Line2D h1 = lines.horizontalLines.getFirst();
      Line2D v1 = lines.verticalLines.getFirst();
      Line2D h2 = lines.horizontalLines.getLast();
      Line2D v2 = lines.verticalLines.getLast();
      if (this.arePointsNearBy(h1.getP1(), v1.getP1()) && this
          .arePointsNearBy(h2.getP1(), v1.getP2())
          && this.arePointsNearBy(h1.getP2(), v2.getP1()) && this
          .arePointsNearBy(h2.getP2(), v2.getP2())) {
        double width = h1.getX2() - h1.getX1();
        double height = v1.getY2() - v1.getY1();
        Rectangle rect = new Rectangle();
        rect.setRect(h1.getX1(), this.pageHeight - v1.getY1() - height, width, height);
        rect = rect.intersection(this.getGraphicsState().getCurrentClippingPath().getBounds());
        return rect.intersection(this.pageRectangle);
      }
    }
    return null;
  }

  /**
   * @return True if the points {@code p1} and {@code p2} are close.
   */
  private boolean arePointsNearBy(Point2D p1, Point2D p2) {
    double mergeEpsilon = this.settings.getLineMergeEpsilon();
    return Math.abs(p1.getX() - p2.getX()) <= mergeEpsilon
        && Math.abs(p1.getY() - p2.getY()) <= mergeEpsilon;
  }

  /**
   * @return extractedLines present in linePath class variable
   */
  private ExtractedLines extractPath() {
    List<Line2D> horizontalLines = Lists.mutable.empty();
    List<Line2D> verticalLines = Lists.mutable.empty();
    PathIterator pathIterator = this.linePath.getPathIterator(null);
    double prevX = 0;
    double prevY = 0;
    double lastMoveToX = 0;
    double lastMoveToY = 0;
    double[] coords = new double[6];
    while (!pathIterator.isDone()) {
      switch (pathIterator.currentSegment(coords)) {
        case PathIterator.SEG_MOVETO:
          prevX = coords[0];
          prevY = coords[1];
          lastMoveToX = coords[0];
          lastMoveToY = coords[1];
          break;
        case PathIterator.SEG_LINETO:
          extractAndAppendLine(prevX, prevY, coords[0], coords[1], horizontalLines, verticalLines);
          prevX = coords[0];
          prevY = coords[1];
          break;
        case PathIterator.SEG_CLOSE:
          extractAndAppendLine(prevX, prevY, lastMoveToX, lastMoveToY, horizontalLines,
              verticalLines);
          prevX = lastMoveToX;
          prevY = lastMoveToY;
          break;
      }
      pathIterator.next();
    }
    return new ExtractedLines(horizontalLines, verticalLines, DISTINCT_EPSILON);
  }

  /**
   * Class to represent rectangular colored portion in the document
   */
  static final class ColoredArea {

    public Rectangle area;
    public int color;
    public int operationIndex;

    private ColoredArea(Rectangle area, int color, int operationIndex) {
      this.area = area;
      this.color = color;
      this.operationIndex = operationIndex;
    }
  }

  /**
   * Class to represent all the horizontal and vertical lines
   */
  private static class ExtractedLines {

    private final MutableList<Line2D> horizontalLines;
    private final MutableList<Line2D> verticalLines;

    ExtractedLines(List<Line2D> horizontalLines, List<Line2D> verticalLines, double epsilon) {
      this.horizontalLines = sortAndMergeHorizontalLines(horizontalLines, epsilon);
      this.verticalLines = sortAndMergeVerticalLines(verticalLines, epsilon);
    }

    ExtractedLines(List<ExtractedLines> extractedLinesList, double epsilon) {
      List<Line2D> horizontalLines = Lists.mutable.empty();
      List<Line2D> verticalLines = Lists.mutable.empty();
      for (ExtractedLines extractedLines : extractedLinesList) {
        horizontalLines.addAll(extractedLines.horizontalLines);
        verticalLines.addAll(extractedLines.verticalLines);
      }
      this.horizontalLines = sortAndMergeHorizontalLines(horizontalLines, epsilon);
      this.verticalLines = sortAndMergeVerticalLines(verticalLines, epsilon);
    }

    /**
     * Sort the horizontal lines in order of coordinates (y, x1, x2) and merge lines if length of
     * line is less than epsilon or if difference in y-coordinates is less than epsilon
     */
    private static MutableList<Line2D> sortAndMergeHorizontalLines(List<Line2D> horizontalLines,
        double epsilon) {
      QuickSort.sort(horizontalLines, (line1, line2) ->
      {
        return Math.abs(line1.getY1() - line2.getY1()) <= epsilon ?
            Math.abs(line1.getX1() - line2.getX1()) <= epsilon ? Double
                .compare(line1.getX2(), line2.getX2())
                : Double.compare(line1.getX1(), line2.getX1())
            : Double.compare(line1.getY1(), line2.getY1());
      });
      MutableList<Line2D> mergedHorizontalLines = Lists.mutable.empty();
      Line2D mergedLine = null;
      for (Line2D line : horizontalLines) {
        if (mergedLine != null) {
          if (line.getY1() > mergedLine.getY1() + epsilon
              || line.getX1() > mergedLine.getX2() + epsilon) {
            mergedHorizontalLines.add(mergedLine);
            mergedLine = null;
          }
        }
        if (mergedLine == null) {
          mergedLine = line;
        } else {
          double mergedY =
              (mergedLine.getY2() + mergedLine.getY1() + line.getY2() + line.getY1()) / 4;
          double minX = Math.min(mergedLine.getX1(), line.getX1());
          double maxX = Math.max(mergedLine.getX2(), line.getX2());
          mergedLine = new Line2D.Double(minX, mergedY, maxX, mergedY);
        }
      }

      if (mergedLine != null) {
        mergedHorizontalLines.add(mergedLine);
      }
      return mergedHorizontalLines;
    }

    /**
     * Sort the vertical lines in order of coordinates (x, y1, y2) and merge lines if length of line
     * is less than epsilon or if difference in x-coordinates is less than epsilon
     */
    private static MutableList<Line2D> sortAndMergeVerticalLines(List<Line2D> verticalLines,
        double epsilon) {
      QuickSort.sort(verticalLines, (line1, line2) ->
      {
        return Math.abs(line1.getX1() - line2.getX1()) <= epsilon ?
            Math.abs(line1.getY1() - line2.getY1()) <= epsilon ? Double
                .compare(line1.getY2(), line2.getY2())
                : Double.compare(line1.getY1(), line2.getY1())
            : Double.compare(line1.getX1(), line2.getX1());
      });
      MutableList<Line2D> mergedVerticalLines = Lists.mutable.empty();
      Line2D mergedLine = null;
      for (Line2D line : verticalLines) {
        if (mergedLine != null) {
          if (line.getX1() > mergedLine.getX1() + epsilon
              || line.getY1() > mergedLine.getY2() + epsilon) {
            mergedVerticalLines.add(mergedLine);
            mergedLine = null;
          }
        }
        if (mergedLine == null) {
          mergedLine = line;
        } else {
          double mergedX =
              (mergedLine.getX2() + mergedLine.getX1() + line.getX2() + line.getX1()) / 4;
          double minY = Math.min(mergedLine.getY1(), line.getY1());
          double maxY = Math.max(mergedLine.getY2(), line.getY2());
          mergedLine = new Line2D.Double(mergedX, minY, mergedX, maxY);
        }
      }

      if (mergedLine != null) {
        mergedVerticalLines.add(mergedLine);
      }
      return mergedVerticalLines;
    }
  }
}
