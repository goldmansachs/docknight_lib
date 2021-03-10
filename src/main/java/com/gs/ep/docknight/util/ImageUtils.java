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

import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import com.gs.ep.docknight.model.Form;
import com.gs.ep.docknight.model.Form.FormType;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.lept;
import org.bytedeco.javacpp.lept.PIX;
import org.bytedeco.javacpp.tesseract;
import org.bytedeco.javacpp.tesseract.StringGenericVector;
import org.bytedeco.javacpp.tesseract.TessBaseAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class containing utility methods for handling images
 */
public final class ImageUtils {

  protected static final Logger LOGGER = LoggerFactory.getLogger(ImageUtils.class);
  private static final Form SELECTED_RADIO = new Form(FormType.RadioButton,
      Lists.mutable.of("yes", "no"), null, Sets.mutable.of("yes"), Lists.mutable.of("yes"), null);
  private static final Form EMPTY_RADIO = new Form(FormType.RadioButton,
      Lists.mutable.of("yes", "no"), null, Sets.mutable.of("yes"), Lists.mutable.of("no"), null);
  private static String tessDataDir = "/opt/worlddb-data/tessdata/custom/tessdata";

  private ImageUtils() {
  }


  /**
   * Render each page of pdf as image of resolution {@code resolution} using pdfbox and apply {@code
   * processor} on the image.
   */
  public static void processImagesFromPdfUsingPdfBox(InputStream pdfInputStream, int resolution,
      Consumer<BufferedImage> processor) throws Exception {
    try (PDDocument document = PDDocument.load(pdfInputStream)) {
      PDFRenderer pdfRenderer = new PDFRenderer(document);
      for (int page = 0; page < document.getNumberOfPages(); page++) {
        LOGGER.info("processing image " + (page + 1) + "/" + document.getNumberOfPages());
        processor.accept(pdfRenderer.renderImageWithDPI(page, resolution, ImageType.RGB));
      }
    }
  }

  /**
   * Initialize tesseract the language models {@code languages} and page segmentation modes {@code
   * mode} Information about the arguments can be found in {@see <a href="https://github.com/tesseract-ocr/tesseract/blob/master/doc/tesseract.1.asc">tesseract
   * documentation</a>}
   */
  public static TessBaseAPI getTesseractAPI(List<String> languages, int mode) {
    TessBaseAPI tesseractAPI;
    try {
      tesseractAPI = new TessBaseAPI();
    } catch (UnsatisfiedLinkError e) {
      throw new RuntimeException(
          "Platform dependent jars of leptonica and tesseract should be available in classpath", e);
    }

    String availableTessDataDir = System.getenv("TESS_DATA_DIR");
    if (availableTessDataDir == null) {
      if (!new File(tessDataDir).exists()) {
        throw new RuntimeException(
            "Tesseract data dir not set. Please set using the method setTessDataDir or env var TESS_DATA_DIR.\n"
                +
                "If you don't have tessdata downloaded, you can get it from https://github.com/tesseract-ocr/tessdata/tree/3.04.00\n"
                +
                "Download these files into the directory (let's name it tessdata) and point to this directory by calling ImageUtils.setTessDataDir(<path_to_your_tessdatadir>) or setting env var TESS_DATA_DIR.\n"
                +
                "If setting via env var and using an ide, pls restart the ide, for it to take effect.");
      }
      availableTessDataDir = tessDataDir;
    }
    if (tesseractAPI.Init(availableTessDataDir, String.join("+", languages), tesseract.OEM_DEFAULT,
        new BytePointer(),
        0, new StringGenericVector(), new StringGenericVector(), false) != 0) {
      throw new RuntimeException("Could not initialize tesseract");
    }
    tesseractAPI.SetPageSegMode(mode);
    return tesseractAPI;
  }

  public static PIX toPIXImage(BufferedImage image) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ImageIO.write(image, "png", outputStream);
    byte[] imageBytes = outputStream.toByteArray();
    ByteBuffer buf = ByteBuffer.allocateDirect(imageBytes.length);
    buf.order(ByteOrder.nativeOrder());
    buf.put(imageBytes);
    buf.flip();
    PIX pixImage = lept.pixReadMem(buf, buf.capacity());
    PIX pixImage1 = lept.pixConvert32To8(pixImage, lept.L_MS_TWO_BYTES, lept.L_MS_BYTE);
    pixImage.deallocate();
    return pixImage1;
  }

  /**
   * Resize the {@code image}. Multiple image width and height by {@code factor}
   *
   * @param image image to be resized
   * @param factor factor by which width and height of image need to be multiplied
   * @return resized image
   */
  public static BufferedImage resizeImage(BufferedImage image, double factor) {
    int displayWidth = (int) (image.getWidth() * factor);
    int displayHeight = (int) (image.getHeight() * factor);
    BufferedImage resizedImage = new BufferedImage(displayWidth, displayHeight,
        BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = resizedImage.createGraphics();
    graphics.drawImage(image, 0, 0, displayWidth, displayHeight, null);
    graphics.dispose();
    return resizedImage;
  }

  public static double getLuminance(int color) {
    // this formula is based on https://www.w3.org/TR/AERT/#color-contrast
    return (0.299 * ((color >> 16) & 0xFF) + 0.587 * ((color >> 8) & 0xFF) + 0.114 * (color & 0xFF))
        / 255;
  }

  /**
   * Create the image representation in base 64 from the {@code image}
   *
   * @param image image
   * @return image representation in base 64
   */
  public static String toBase64PngBinary(BufferedImage image) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(image, "png", baos);
      baos.flush();
      byte[] imageInByteArray = baos.toByteArray();
      baos.close();
      return DatatypeConverter.printBase64Binary(imageInByteArray);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create the buffered image from the image representation in base 64
   *
   * @param pngImageInBase64 image representation in base 64
   * @return buffered image
   */
  public static BufferedImage parseBase64PngBinary(String pngImageInBase64) {
    try {
      BufferedImage image = ImageIO
          .read(new ByteArrayInputStream(DatatypeConverter.parseBase64Binary(pngImageInBase64)));
      return image;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Extract visible rectangular regions from the {@code image}
   */
  public static Rectangle extractVisibleAreaFromImage(BufferedImage image) {
    int minX = image.getWidth();
    int maxX = 0;
    int minY = image.getHeight();
    int maxY = 0;
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        if (getLuminance(image.getRGB(x, y)) < 0.5) {
          minX = Math.min(minX, x);
          maxX = Math.max(maxX, x);
          minY = Math.min(minY, y);
          maxY = Math.max(maxY, y);
        }
      }
    }
    if (maxX >= minX && maxY >= minY) {
      int width = maxX - minX;
      int height = maxY - minY;
      Rectangle rectangle = new Rectangle();
      rectangle.setBounds(minX, minY, width, height);
      return rectangle;
    }
    return null;
  }

  /**
   * Extracts Form object from the {@code image}
   */
  public static Form parseAsForm(BufferedImage image) {
    if (!OpenCvUtils.isLibraryLoaded()) {
      return null;
    }
    Double result;
    try {
      result = OpenCvUtils.getImageCenterColor(image);
    } catch (Exception e) {
      LOGGER.warn("Error encountered while parsing Form", e);
      return null;
    }
    if (result == -1) {
      return null;
    }
    return result.compareTo(255.0) == 0 ? EMPTY_RADIO : SELECTED_RADIO;
  }

  /**
   * @return bounding box of hand written area in the {@code image} if OpenCV library is loaded
   */
  public static List<Rectangle> findHandWrittenAreas(BufferedImage image) {
    if (!OpenCvUtils.isLibraryLoaded()) {
      return Lists.mutable.empty();
    }
    return OpenCvUtils.getHandWrittenAreas(image);
  }
}
