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

package com.gs.ep.docknight.model.extractor;

/**
 * Enum to represent keys in table map representation of {@see com.gs.ep.docknight.model.element.Document
 * document mode}
 */
public enum TableAttribute {
  BORDER("border"),
  BOTTOM("bottom"),
  BOX("box"),
  CAPTION("caption"),
  CELLS("cells"),
  CHILDREN("children"),
  COLUMN("column"),
  COLUMNS("columns"),
  CURRENCY_SYMBOL("currencySymbol"),
  CURRENCY_NAME("currencyName"),
  DATA("data"),
  DOCUMENT("document"),
  HEADER_ROW("headerRow"),
  COLUMN_HEADER_COUNT("columnHeaderCount"),
  HIERARCHY_LEVEL("hierarchyLevel"),
  ID("id"),
  IMMEDIATE_PARENT("immediateParent"),
  INDEX("index"),
  LEFT("left"),
  METADATA("metadata"),
  MULTIPLICATIVE_FACTOR("multiplicativeFactor"),
  PARENTS("parents"),
  PATH("path"),
  POSITION("position"),
  RIGHT("right"),
  ROW("row"),
  ROWS("rows"),
  SEGMENT_IDS("segmentIds"),
  SPAN("span"),
  SPANNING_PAGES("spanningPages"),
  TABLES("tables"),
  TITLE("title"),
  TEXT("text"),
  TEXT_STYLES("textStyles"),
  TOP("top"),
  TOTAL_ROW("totalRow");

  private final String key;

  TableAttribute(String key) {
    this.key = key;
  }

  public String getKey() {
    return this.key;
  }
}
