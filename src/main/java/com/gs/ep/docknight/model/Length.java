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
import java.text.DecimalFormat;
import java.util.Map;

/**
 * Class used to define length concept
 */
public class Length implements Serializable {

  public static final Length ZERO = new Length(0, Unit.pt);
  private static final long serialVersionUID = 827163152981458291L;
  private double magnitude;
  private Unit unit;
  public Length(double magnitude, Unit unit) {
    this.magnitude = magnitude;
    this.unit = unit;
  }

  public double getMagnitude() {
    return this.magnitude;
  }

  public Unit getUnit() {
    return this.unit;
  }

  public Length add(Length length) {
    if (length == null) {
      return this;
    } else if (!this.unit.equals(length.unit)) {
      throw new UnsupportedOperationException("cannot add lengths of two different units");
    }
    return new Length(this.magnitude + length.magnitude, this.unit);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    Length length = (Length) o;

    if (Double.compare(length.magnitude, this.magnitude) != 0) {
      return false;
    }
    if (this.unit != length.unit) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    long temp = Double.doubleToLongBits(this.magnitude);
    int result = (int) (temp ^ (temp >>> 32));
    result = 31 * result + this.unit.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return new DecimalFormat(this.unit.getDecimalFormat()).format(this.magnitude) + this.unit
        .getDefaultUnitStr();
  }

  /**
   * Enum representing the unit for the length
   */
  public enum Unit {
    px("0.##", "px", ""),
    pt("0.##", "pt"),
    em("0.###", "em"),
    percent("0.##", "%");

    private static Map<String, Unit> strRepToUnitMap;

    private String decimalFormat;
    private String[] strReps;

    Unit(String decimalFormat, String... strReps) {
      this.decimalFormat = decimalFormat;
      this.strReps = strReps;
    }

    public String getDecimalFormat() {
      return this.decimalFormat;
    }

    public String getDefaultUnitStr() {
      return this.strReps[0];
    }
  }
}
