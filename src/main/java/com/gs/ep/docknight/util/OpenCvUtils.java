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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.tuple.Tuples;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class containing utility methods for OpenCV library
 */
public final class OpenCvUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenCvUtils.class);
  private static boolean libraryLoaded;

  static {
    try {
      nu.pattern.OpenCV.loadShared();
      System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
      libraryLoaded = true;
    } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
      libraryLoaded = false;
      LOGGER.error("Error initializing openCv shared library", e);
    }
  }

  private OpenCvUtils() {
  }

  /**
   * @throws RuntimeException if OpenCV library is not loaded.
   */
  private static void checkLibraryLoaded() {
    if (!libraryLoaded) {
      throw new RuntimeException("OpenCv library not loaded.");
    }
  }

  /**
   * @return True if OpenCV library is loaded, else return False
   */
  public static boolean isLibraryLoaded() {
    return libraryLoaded;
  }

  protected static Mat getBorderedImg(Mat src) {
    checkLibraryLoaded();
    int row = src.rows();
    if (row < 2) {
      return src;
    }
    Mat bottomLines = src.rowRange(row - 2, row);
    Mat borderedImage = new Mat();
    double mean = Core.mean(bottomLines).val[0];
    Core.copyMakeBorder(src, borderedImage, 10, 10, 10, 10, Core.BORDER_CONSTANT,
        new Scalar(mean, mean, mean));
    return borderedImage;
  }

  /**
   * @return matrix data structure from input image {@code image}
   */
  private static Mat getMatFromBufferedImage(BufferedImage image) {
    checkLibraryLoaded();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", byteArrayOutputStream);
      byteArrayOutputStream.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return Imgcodecs.imdecode(new MatOfByte(byteArrayOutputStream.toByteArray()),
        Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
  }

  /**
   * @return image center color from {@code image}
   */
  public static double getImageCenterColor(BufferedImage image) {
    return getImageCenterColor(getMatFromBufferedImage(image));
  }

  /**
   * Retrieve matrix datastructure from image at path {@code filePath}
   *
   * @param filePath path where image exists
   */
  public static Mat getMatFromFileImage(String filePath) {
    checkLibraryLoaded();
    return Imgcodecs.imread(filePath);
  }

  /**
   * Convert the image matrix to buffered image
   *
   * @param src image matrix
   * @return buffered image
   */
  public static BufferedImage getBufferedImageFromMat(Mat src) {
    checkLibraryLoaded();
    return (BufferedImage) HighGui.toBufferedImage(src);
  }

  /**
   * @return bounding box of hand written area in the {@code image}
   */
  public static List<Rectangle> getHandWrittenAreas(BufferedImage image) {
    Mat mat = getMatFromBufferedImage(image);
    mat = getGrayImage(mat);
    mat = getThresholdImage(mat);
    List<MatOfPoint> contours = getContours(mat);
    List<Rectangle> result = Lists.mutable.empty();
    for (MatOfPoint mp : contours) {
      Rect rect = Imgproc.boundingRect(mp);
      Set<Integer> horizontalPts = Sets.mutable.empty();
      Set<Integer> verticalPts = Sets.mutable.empty();
      for (Point p : mp.toArray()) {
        int xInt = (int) (p.x / 3);
        int yInt = (int) (p.y / 3);
        if (!horizontalPts.contains(xInt) && !verticalPts.contains(yInt)) {
          horizontalPts.add(xInt);
          verticalPts.add(yInt);
        }
      }
      if (rect.width > 25 && rect.height > 15 && (rect.width < 300 || rect.height < 125)
          && horizontalPts.size() >= 5) {
        result.add(new Rectangle(rect.x, rect.y, rect.width, rect.height));
      }
    }
    return result;
  }

  public static Mat getResizedImage(Mat src, double fx, double fy) {
    checkLibraryLoaded();
    Mat resizedImage = new Mat();
    Imgproc.resize(src, resizedImage, new Size(new Point()), fx, fy, Imgproc.INTER_LINEAR);
    return resizedImage;
  }

  /**
   * @return gray scale image from input image {@code src}
   */
  public static Mat getGrayImage(Mat src) {
    checkLibraryLoaded();
    Mat grayImage = new Mat();
    Imgproc.cvtColor(src, grayImage, Imgproc.COLOR_BGR2GRAY);
    return grayImage;
  }

  /**
   * Set the pixel to 0 if the pixel value is greater than threshold in the image {@code src}
   */
  public static Mat getThresholdImage(Mat src) {
    checkLibraryLoaded();
    Mat thresholdImage = new Mat();
    Imgproc.threshold(src, thresholdImage, 220, 255, Imgproc.THRESH_BINARY_INV);
    return thresholdImage;
  }

  /**
   * Perform bitwise not on machine specific bit on image {@code src}
   */
  public static Mat getInvertedThreshold(Mat src) {
    checkLibraryLoaded();
    Mat invertedThreshold = new Mat();
    Core.bitwise_not(src, invertedThreshold);
    return invertedThreshold;
  }

  /**
   * @param src It is used to store the values of an image in an n-dimensional array
   * @return Contours  a curve joining all the continuous points (along the boundary), having same
   * color or intensity. The contours are a useful tool for shape analysis and object detection and
   * recognition.
   */
  public static List<MatOfPoint> getContours(Mat src) {
    checkLibraryLoaded();
    Mat dummy = new Mat();
    List<MatOfPoint> contours = Lists.mutable.empty();
    Imgproc.findContours(src, contours, dummy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
    return contours;
  }

  /**
   * @return image center color for image {@code src}
   */
  protected static double getImageCenterColor(Mat src) {
    checkLibraryLoaded();
    Mat borderedImage = getBorderedImg(src);
    Mat resizedImage = getResizedImage(borderedImage, 4, 4);

    Mat grayImage = getGrayImage(resizedImage);
    Mat thresholdImage = getThresholdImage(grayImage);
    Mat invertedThreshold = getInvertedThreshold(thresholdImage);

    MutableList<Pair<Double, Integer>> areas = Lists.mutable.empty();
    List<MatOfPoint> contours = getContours(invertedThreshold);

    if (contours.isEmpty()) {
      return -1;
    }

    for (int i = 0; i < contours.size(); i++) {
      MatOfPoint contour = contours.get(i);
      Rect rect = Imgproc.boundingRect(contour);
      areas.add(Tuples.pair(rect.area(), i));
    }

    areas.sortThis().reverseThis();
    int finalIndex = areas.size() == 1 ? areas.get(0).getTwo() : areas.get(1).getTwo();

    Rect rect = Imgproc.boundingRect(contours.get(finalIndex));
    Point center = new Point(rect.tl().x + (rect.width / 2), rect.tl().y + (rect.height / 2));

    double[] colorForCenter = invertedThreshold.get((int) center.y, (int) center.x);
    return colorForCenter[0];
  }
}
