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

import com.gs.ep.docknight.model.Length.Unit;

/**
 * Abstract class defined for attribute representing length (magnitude + unit) of an element
 */
public abstract class LengthAttribute extends Attribute<Length> {

  private static final long serialVersionUID = -1684776632429905805L;

  public double getMagnitude() {
    return this.getValue().getMagnitude();
  }

  public void setMagnitude(double magnitude) {
    this.setValue(new Length(magnitude, this.getUnit()));
  }

  public Unit getUnit() {
    return this.getValue().getUnit();
  }
}
