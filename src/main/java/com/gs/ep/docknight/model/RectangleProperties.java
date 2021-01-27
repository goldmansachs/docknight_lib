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

import java.io.Serializable;

/**
 * Class representing properties of rectangle. For example: bounding box
 */
public class RectangleProperties<T> implements Serializable {

  private static final long serialVersionUID = 3618894823364292219L;
  @SuppressWarnings("NonSerializableFieldInSerializableClass")
  private T top;
  @SuppressWarnings("NonSerializableFieldInSerializableClass")
  private T right;
  @SuppressWarnings("NonSerializableFieldInSerializableClass")
  private T bottom;
  @SuppressWarnings("NonSerializableFieldInSerializableClass")
  private T left;
  @SuppressWarnings("NonSerializableFieldInSerializableClass")
  private T common;

  public RectangleProperties(T top, T right, T bottom, T left) {
    this.top = top;
    this.right = right;
    this.bottom = bottom;
    this.left = left;
    this.common = null;
  }

  public RectangleProperties(T common) {
    this.top = null;
    this.right = null;
    this.bottom = null;
    this.left = null;
    this.common = common;
  }

  public T getTop() {
    return this.top;
  }

  public void setTop(T top) {
    this.top = top;
  }

  public T getRight() {
    return this.right;
  }

  public void setRight(T right) {
    this.right = right;
  }

  public T getBottom() {
    return this.bottom;
  }

  public void setBottom(T bottom) {
    this.bottom = bottom;
  }

  public T getLeft() {
    return this.left;
  }

  public void setLeft(T left) {
    this.left = left;
  }

  public T getCommon() {
    return this.common;
  }

  public void setCommon(T common) {
    this.common = common;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    RectangleProperties<?> that = (RectangleProperties<?>) o;

    if (this.top != null ? !this.top.equals(that.top) : that.top != null) {
      return false;
    }
    if (this.right != null ? !this.right.equals(that.right) : that.right != null) {
      return false;
    }
    if (this.bottom != null ? !this.bottom.equals(that.bottom) : that.bottom != null) {
      return false;
    }
    if (this.left != null ? !this.left.equals(that.left) : that.left != null) {
      return false;
    }
    return this.common != null ? this.common.equals(that.common) : that.common == null;
  }

  @Override
  public int hashCode() {
    int result = this.top != null ? this.top.hashCode() : 0;
    result = 31 * result + (this.right != null ? this.right.hashCode() : 0);
    result = 31 * result + (this.bottom != null ? this.bottom.hashCode() : 0);
    result = 31 * result + (this.left != null ? this.left.hashCode() : 0);
    result = 31 * result + (this.common != null ? this.common.hashCode() : 0);
    return result;
  }
}
