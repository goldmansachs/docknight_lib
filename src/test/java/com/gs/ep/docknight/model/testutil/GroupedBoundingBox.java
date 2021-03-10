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

import org.eclipse.collections.api.list.primitive.MutableDoubleList;
import org.eclipse.collections.impl.factory.primitive.DoubleLists;

public class GroupedBoundingBox {

  private final MutableDoubleList columnEndLocations;
  private final MutableDoubleList rowEndLocations;
  private final double left;
  private final double top;

  public GroupedBoundingBox(double left, double top, double horizontalSize, double verticalSize) {
    this(left, top, new double[]{horizontalSize}, new double[]{verticalSize});
  }

  public GroupedBoundingBox(double left, double top, int numCols, int numRows, double colSize,
      double rowSize) {
    this(left, top, repeat(numCols, colSize), repeat(numRows, rowSize));
  }

  public GroupedBoundingBox(double left, double top, double[] columnSizes, double[] rowSizes) {
    this.left = left;
    this.top = top;
    this.columnEndLocations = DoubleLists.mutable.empty();
    this.rowEndLocations = DoubleLists.mutable.empty();

    for (double columnSize : columnSizes) {
      this.columnEndLocations.add(columnSize + (this.columnEndLocations.isEmpty() ? left
          : this.columnEndLocations.getLast()));
    }
    for (double rowSize : rowSizes) {
      this.rowEndLocations
          .add(rowSize + (this.rowEndLocations.isEmpty() ? top : this.rowEndLocations.getLast()));
    }
  }

  private static double[] repeat(int count, double value) {
    double[] arr = new double[count];
    for (int i = 0; i < count; i++) {
      arr[i] = value;
    }
    return arr;
  }

  private double getRowStart(int row) {
    return row == 0 ? this.top : this.rowEndLocations.get(row - 1);
  }

  private double getRowEnd(int row) {
    return this.rowEndLocations.get(row);
  }

  private double getColStart(int col) {
    return col == 0 ? this.left : this.columnEndLocations.get(col - 1);
  }

  private double getColEnd(int col) {
    return this.columnEndLocations.get(col);
  }

  public int numRows() {
    return this.rowEndLocations.size();
  }

  public int numColumns() {
    return this.columnEndLocations.size();
  }

  public BoundingBox getFullBBox() {
    return this.getBBox(0, this.numRows() - 1, 0, this.numColumns() - 1);
  }

  public BoundingBox getCellBBox(int row, int col) {
    return this.getBBox(row, row, col, col);
  }

  public BoundingBox getRowBBox(int row) {
    return this.getBBox(row, row, 0, this.numColumns() - 1);
  }

  public BoundingBox getColBBox(int col) {
    return this.getBBox(0, this.numRows() - 1, col, col);
  }

  public BoundingBox getBBox(int row1, int row2, int col1, int col2) {
    double top = this.getRowStart(row1);
    double left = this.getColStart(col1);
    return new BoundingBox(top, left, this.getColEnd(col2) - left, this.getRowEnd(row2) - top);
  }

  public void forEachCellBBox(int row1, int row2, int col1, int col2, BoundingBoxOperation runnable)
      throws Exception {
    for (int r = row1; r <= row2; r++) {
      for (int c = col1; c <= col2; c++) {
        runnable.run(r, c, this.getCellBBox(r, c));
      }
    }
  }

  public interface BoundingBoxOperation {

    void run(int row, int col, BoundingBox bbox) throws Exception;
  }
}
