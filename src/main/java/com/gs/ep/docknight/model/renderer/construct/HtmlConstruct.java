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
 * Class representing tags used in html
 */
public final class HtmlConstruct {

  //Table Related Tags
  public static final String ROW_TAG = "tr";
  public static final String COLUMN_TAG = "td";
  public static final String TABLE_TAG = "table";

  //List Related Tags
  public static final String UL_TAG = "ul";
  public static final String OL_TAG = "ol";
  public static final String LI_TAG = "li";

  //Common Tags
  public static final String DIV_TAG = "div";
  public static final String BODY_TAG = "body";
  public static final String HTML_TAG = "html";
  public static final String PARAGRAPH_TAG = "p";
  public static final String SPAN_TAG = "span";
  public static final String HEAD_TAG = "head";
  public static final String BOLD_TAG = "b";
  public static final String STRONG_TAG = "strong";
  public static final String ITALIC_TAG = "i";
  public static final String EM_TAG = "em";
  public static final String HYPERLINK_TAG = "a";
  public static final String IMG_TAG = "img";
  public static final String SRC_TAG = "src";
  public static final String FIGURE_TAG = "figure";
  public static final String CAPTION_TAG = "figcaption";
  public static final String COMMENT_TAG = "#comment";
  public static final String FORM_TAG = "form";
  public static final String INPUT_TAG = "input";
  public static final String TEXTAREA_TAG = "textarea";
  public static final String SELECT_TAG = "select";
  public static final String OPTION_TAG = "option";
  public static final String ID = "id";
  public static final String CUSTOM_DATA_PREFIX = "data-";

  //Style Tags
  public static final String STYLE = "style";
  public static final String FONT_SIZE = "font-size";
  public static final String FONT_FAMILY = "font-family";
  public static final String COLOR = "color";
  public static final String FONT_WEIGHT = "font-weight";
  public static final String FONT_STYLE = "font-style";

  //Head tags
  public static final String META_TAG = "meta";
  public static final String LINK_TAG = "link";

  private HtmlConstruct() {
  }
}
