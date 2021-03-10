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

package com.gs.ep.docknight.model.renderer.construct;

/**
 * Class representing data that is used as suffix in formation of keys of html nodes.
 */
public final class HtmlDataSuffix {

  /*Vertical Group based IDs*/
  public static final String VERTICAL_GROUP_ID = "vertical-group-id";
  public static final String LEFT_GROUP_ID = "left-group-id";
  public static final String RIGHT_GROUP_ID = "right-group-id";
  public static final String ABOVE_GROUP_ID = "above-group-id";
  public static final String BELOW_GROUP_ID = "below-group-id";
  public static final String VISUAL_TOP = "visual-top";
  public static final String VISUAL_HEIGHT = "visual-height";
  public static final String VISUAL_LEFT = "visual-left";
  public static final String VISUAL_WIDTH = "visual-width";

  /*Tabular Group based IDs*/
  public static final String TABULAR_GROUP_ID = "tabular-group-id";
  public static final String TABULAR_CELL_ID = "tabular-cell-id";
  public static final String TABULAR_COL_INDEX = "tabular-col-index";
  public static final String TABULAR_ROW_INDEX = "tabular-row-index";
  public static final String TABULAR_CELL_TOP = "tabular-cell-top";
  public static final String TABULAR_CELL_HEIGHT = "tabular-cell-height";
  public static final String TABULAR_CELL_LEFT = "tabular-cell-left";
  public static final String TABULAR_CELL_WIDTH = "tabular-cell-width";

  private HtmlDataSuffix() {
  }
}
