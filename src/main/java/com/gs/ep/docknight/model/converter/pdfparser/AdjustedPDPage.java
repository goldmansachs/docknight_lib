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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * PDPage object which also takes page rotation into consideration.
 */
public class AdjustedPDPage {

  private final double pageWidth;
  private final double pageHeight;
  private final int pageRotation;
  private final float lowerLeftX;
  private final float lowerLeftY;

  public AdjustedPDPage(PDPage page) {
    this(page, 0);
  }

  AdjustedPDPage(PDPage page, int textRotation) {
    this.pageRotation = page.getRotation() + textRotation;
    PDRectangle boundingBox = page.getBBox();
    this.lowerLeftX = boundingBox.getLowerLeftX();
    this.lowerLeftY = boundingBox.getLowerLeftY();
    boolean isPagePerpendicularlyFlipped = this.isPagePerpendicularlyFlipped();

    // If page is perpendicularly flipped, swap page width and height
    this.pageWidth =
        isPagePerpendicularlyFlipped ? boundingBox.getHeight() : boundingBox.getWidth();
    this.pageHeight =
        isPagePerpendicularlyFlipped ? boundingBox.getWidth() : boundingBox.getHeight();
  }

  /**
   * @return True if page is perpendicularly flipped
   */
  public boolean isPagePerpendicularlyFlipped() {
    return this.pageRotation % 180 != 0;
  }

  public double getPageWidthAdj() {
    return this.pageWidth;
  }

  public double getPageHeightAdj() {
    return this.pageHeight;
  }

  public int getPageRotation() {
    return this.pageRotation;
  }

  /**
   * @return adjusted x,y after taking page rotation into account
   */
  public Point2D getXYAdj(double x, double y) {
    double x1 = x - this.lowerLeftX;
    double y1 = y - this.lowerLeftY;

    if (this.pageRotation == 0) {
      return new Point2D.Double(x1, y1);
    } else if (this.pageRotation == 90) {
      return new Point2D.Double(y1, this.pageHeight - x1);
    } else if (this.pageRotation == 180) {
      return new Point2D.Double(this.pageWidth - x1, this.pageHeight - y1);
    } else if (this.pageRotation == 270) {
      return new Point2D.Double(this.pageWidth - y1, x1);
    }
    return new Point2D.Double(x1, y1);
  }

  /**
   * @return adjusted rectangle after taking page rotation into account
   */
  public Rectangle2D getRectangleAdj(double x, double y, double width, double height) {
    Point2D p0 = this.getXYAdj(x, y);
    Point2D p1 = this.getXYAdj(x + width, y + height);
    Rectangle2D rect = new Rectangle2D.Double(p0.getX(), p0.getY(), 0, 0);
    rect.add(p1);
    return rect;
  }
}
