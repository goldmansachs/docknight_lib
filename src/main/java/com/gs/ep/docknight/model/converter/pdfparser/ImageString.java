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

import java.awt.geom.Rectangle2D;
import org.apache.pdfbox.text.TextPosition;

/**
 * Class used to represent image containing text
 */
class ImageString implements Comparable<ImageString> {

  final double left;
  final double top;
  final double width;
  final double height;
  final String str;

  ImageString(Rectangle2D visibleImageArea, String str) {
    this(visibleImageArea.getMinX(), visibleImageArea.getMinY(), visibleImageArea.getWidth(),
        visibleImageArea.getHeight(), str);
  }

  ImageString(TextPosition text) {
    this(text.getXDirAdj(), text.getYDirAdj() - text.getHeightDir(), text.getWidthDirAdj(),
        text.getHeightDir(), text.getUnicode());
  }

  ImageString(double left, double top, double width, double height, String str) {
    this.top = top;
    this.left = left;
    this.width = width;
    this.height = height;
    this.str = str;
  }

  @Override
  public int compareTo(ImageString other) {
    double bottom = this.top + this.height;
    double otherBottom = other.top + other.height;
    boolean onSameLine = Math.abs(bottom - otherBottom) < 0.1
        || otherBottom >= this.top && otherBottom <= bottom
        || bottom >= other.top && bottom <= otherBottom;
    if (onSameLine) {
      double dist = this.left - other.left;
      return dist < 0 ? (int) Math.floor(dist) : dist > 0 ? (int) Math.ceil(dist) : 0;
    }
    return bottom < otherBottom ? Integer.MIN_VALUE : Integer.MAX_VALUE;
  }

  @Override
  public String toString() {
    return this.str;
  }
}
