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

package com.gs.ep.docknight.model;

import com.gs.ep.docknight.util.ImageUtils;
import java.awt.image.BufferedImage;
import java.io.Serializable;

/**
 * Class to store buffered image.
 */
public class ComparableBufferedImage implements Serializable {

  private static final long serialVersionUID = 8802953033942202195L;
  private transient BufferedImage image;

  public ComparableBufferedImage(BufferedImage image) {
    this.image = image;
  }

  /**
   * Create the buffered image from the image representation in base 64
   *
   * @param pngImageInBase64 image representation in base 64
   * @return buffered image
   */
  public static ComparableBufferedImage parseBase64PngBinary(String pngImageInBase64) {
    return new ComparableBufferedImage(ImageUtils.parseBase64PngBinary(pngImageInBase64));
  }

  public BufferedImage getBufferedImage() {
    return this.image;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    ComparableBufferedImage that = (ComparableBufferedImage) o;

    if (this.image.getWidth() == that.image.getWidth() && this.image.getHeight() == that.image
        .getHeight()) {
      for (int x = 0; x < this.image.getWidth(); x++) {
        for (int y = 0; y < this.image.getHeight(); y++) {
          if (this.image.getRGB(x, y) != that.image.getRGB(x, y)) {
            return false;
          }
        }
      }
    } else {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return this.image.getWidth() > 0 && this.image.getHeight() > 0 ?
        this.image.getRGB(0, 0) / 2
            + this.image.getRGB(this.image.getWidth() / 2, this.image.getHeight() / 2) / 2 : 0;
  }

  @Override
  public String toString() {
    return this.toBase64PngBinary();
  }

  /**
   * Create the image representation in base 64 from the image in this object
   *
   * @return image representation in base 64
   */
  public String toBase64PngBinary() {
    return ImageUtils.toBase64PngBinary(this.image);
  }
}
