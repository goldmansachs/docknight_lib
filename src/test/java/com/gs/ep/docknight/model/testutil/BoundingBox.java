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

package com.gs.ep.docknight.model.testutil;

import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import com.gs.ep.docknight.model.RectangleProperties;
import java.util.function.Function;

public class BoundingBox extends RectangleProperties<Double> {

  public BoundingBox(double top, double left, double width, double height) {
    super(top, left + width, top + height, left);
  }

  public double getWidth() {
    return this.getRight() - this.getLeft();
  }

  public double getHeight() {
    return this.getBottom() - this.getTop();
  }

  public Pair<Double, Double> getTopRightPlus(double topSep, double rightSep) {
    return Tuples.pair(this.getRight() + rightSep, this.getTop() + topSep);
  }

  public Pair<Double, Double> getTopLeftPlus(double topSep, double leftSep) {
    return Tuples.pair(this.getLeft() + leftSep, this.getTop() + topSep);
  }

  public Pair<Double, Double> getLeftBottomPlus(double leftSep, double bottomSep) {
    return Tuples.pair(this.getLeft() + leftSep, this.getBottom() + bottomSep);
  }

  public Pair<Double, Double> getRightBottomPlus(double rightSep, double bottomSep) {
    return Tuples.pair(this.getRight() + rightSep, this.getBottom() + bottomSep);
  }

  public Pair<Double, Double> getTopRightPlus(Function<Double, Double> topSep,
      Function<Double, Double> rightSep) {
    return Tuples.pair(this.getRight() + rightSep.apply(this.getWidth()),
        this.getTop() + topSep.apply(this.getHeight()));
  }

  public Pair<Double, Double> getTopLeftPlus(Function<Double, Double> topSep,
      Function<Double, Double> leftSep) {
    return Tuples.pair(this.getLeft() + leftSep.apply(this.getWidth()),
        this.getTop() + topSep.apply(this.getHeight()));
  }

  public Pair<Double, Double> getLeftBottomPlus(Function<Double, Double> leftSep,
      Function<Double, Double> bottomSep) {
    return Tuples.pair(this.getLeft() + leftSep.apply(this.getWidth()),
        this.getBottom() + bottomSep.apply(this.getHeight()));
  }

  public Pair<Double, Double> getRightBottomPlus(Function<Double, Double> rightSep,
      Function<Double, Double> bottomSep) {
    return Tuples.pair(this.getRight() + rightSep.apply(this.getWidth()),
        this.getBottom() + bottomSep.apply(this.getHeight()));
  }

  public Pair<Double, Double> getTopRightPlus(double topSep, Function<Double, Double> rightSep) {
    return Tuples.pair(this.getRight() + rightSep.apply(this.getWidth()), this.getTop() + topSep);
  }

  public Pair<Double, Double> getTopLeftPlus(double topSep, Function<Double, Double> leftSep) {
    return Tuples.pair(this.getLeft() + leftSep.apply(this.getWidth()), this.getTop() + topSep);
  }

  public Pair<Double, Double> getLeftBottomPlus(double leftSep,
      Function<Double, Double> bottomSep) {
    return Tuples
        .pair(this.getLeft() + leftSep, this.getBottom() + bottomSep.apply(this.getHeight()));
  }

  public Pair<Double, Double> getRightBottomPlus(double rightSep,
      Function<Double, Double> bottomSep) {
    return Tuples
        .pair(this.getRight() + rightSep, this.getBottom() + bottomSep.apply(this.getHeight()));
  }

  public Pair<Double, Double> getTopRightPlus(Function<Double, Double> topSep, double rightSep) {
    return Tuples.pair(this.getRight() + rightSep, this.getTop() + topSep.apply(this.getHeight()));
  }

  public Pair<Double, Double> getTopLeftPlus(Function<Double, Double> topSep, double leftSep) {
    return Tuples.pair(this.getLeft() + leftSep, this.getTop() + topSep.apply(this.getHeight()));
  }

  public Pair<Double, Double> getLeftBottomPlus(Function<Double, Double> leftSep,
      double bottomSep) {
    return Tuples
        .pair(this.getLeft() + leftSep.apply(this.getWidth()), this.getBottom() + bottomSep);
  }

  public Pair<Double, Double> getRightBottomPlus(Function<Double, Double> rightSep,
      double bottomSep) {
    return Tuples
        .pair(this.getRight() + rightSep.apply(this.getWidth()), this.getBottom() + bottomSep);
  }
}
