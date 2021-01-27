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

import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.function.Function2;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.factory.Maps;
import com.gs.ep.docknight.model.TabularElementGroup.GridType;
import com.gs.ep.docknight.model.converter.ScannedPdfParser.OCREngine;
import java.util.regex.Pattern;

/**
 * Class to represent key for the {@see ModelCustomizations} object. This class also handle the
 * parsing of raw data which will be stored as value corresponding to this key in {@see
 * ModelCustomizations} object.
 */
public final class ModelCustomizationKey<V> {

  public static final ModelCustomizationKey<MutableList<Pattern>> TABULAR_NOISE_PATTERNS =
      new ModelCustomizationKey<>("TabularNoise", p -> p.collect(Pattern::compile));

  public static final ModelCustomizationKey<Function2<MutableList<String>, MutableList<String>, Double>> SEMANTIC_JUMP_CALCULATOR =
      new ModelCustomizationKey<>("SemanticJumpCalculator", e ->
      {
        throw new UnsupportedOperationException();
      });

  public static final ModelCustomizationKey<Boolean> DISABLE_TABLE_DETECTION =
      new ModelCustomizationKey<>("DisableTableDetection", p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<Boolean> DISABLE_HEADER_FOOTER_DETECTION =
      new ModelCustomizationKey<>("DisableHeaderFooterDetection",
          p -> Boolean.parseBoolean(p.get(0)));


  public static final ModelCustomizationKey<GridType> ENABLE_GRID_BASED_TABLE_DETECTION =
      new ModelCustomizationKey<>("EnableGridBasedTableDetection", p -> GridType.getEnum(p.get(0)));

  public static final ModelCustomizationKey<Double> POSITIONAL_GROUPING_MAX_DISTANCE_FACTOR =
      new ModelCustomizationKey<>("PositionalGroupingMaxDistanceFactor",
          p -> Double.parseDouble(p.get(0)));

  public static final ModelCustomizationKey<Boolean> DETECT_UNDERLINE =
      new ModelCustomizationKey<>("DetectUnderline", p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<Boolean> IS_PAGE_NUMBERED_DOC =
      new ModelCustomizationKey<>("IsPageNumberedDoc", p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<OCREngine> OCR_ENGINE =
      new ModelCustomizationKey<>("OcrEngine", p -> OCREngine.valueOf(p.get(0)));

  public static final ModelCustomizationKey<Double> LINE_MERGE_EPSILON =
      new ModelCustomizationKey<>("LineMergeEpsilon", p -> Double.parseDouble(p.get(0)));

  public static final ModelCustomizationKey<Double> MAX_TEXT_ELEMENT_TO_LINE_COUNT_RATIO =
      new ModelCustomizationKey<>("MaxTextElementToLineCountRatio",
          p -> Double.parseDouble(p.get(0)));

  public static final ModelCustomizationKey<Boolean> IGNORE_NON_RENDERABLE_TEXT =
      new ModelCustomizationKey<>("IgnoreNonRenderableText", p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<Boolean> UNDERLINE_DETECTION =
      new ModelCustomizationKey<>("UnderlineDetection", p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<Boolean> FONT_CHANGE_SEGMENTATION =
      new ModelCustomizationKey<>("FontChangeSegmentation", p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<Boolean> IMAGE_BASED_CHAR_DETECTION =
      new ModelCustomizationKey<>("ImageBasedCharDetection", p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<MutableList<Integer>> PAGES_TO_OCR =
      new ModelCustomizationKey<>("PagesToOcr", p -> p.collect(Integer::parseInt));

  public static final ModelCustomizationKey<Boolean> ENABLE_DYNAMIC_SPACE_WIDTH_COMPUTATION =
      new ModelCustomizationKey<>("EnableDynamicSpaceWidthComputation",
          p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<Boolean> SCANNED =
      new ModelCustomizationKey<>("Scanned", p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<Boolean> ENABLE_HAND_WRITTEN_TEXT_DETECTION =
      new ModelCustomizationKey<>("EnableHandWrittenTextDetection",
          p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<Boolean> PAGE_LEVEL_SPACING_SCALING =
      new ModelCustomizationKey<>("PageLevelSpacingScaling", p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<Double> ALLOWED_SCANNEDNESS =
      new ModelCustomizationKey<>("AllowedScannedness", p -> Double.parseDouble(p.get(0)));

  public static final ModelCustomizationKey<Boolean> PAGE_LEVEL_OCR =
      new ModelCustomizationKey<>("PageLevelOcr", p -> Boolean.parseBoolean(p.get(0)));

  public static final ModelCustomizationKey<Integer> MAX_PAGES_ALLOWED =
      new ModelCustomizationKey<>("MaxPagesAllowed", p -> Integer.parseInt(p.get(0)));

  private static MutableMap<String, ModelCustomizationKey> nameToKeyMap;

  private final String name;
  private final Function<MutableList<String>, V> parseFunction;

  private ModelCustomizationKey(String name, Function<MutableList<String>, V> parseFunction) {
    if (nameToKeyMap == null) {
      nameToKeyMap = Maps.mutable.empty();
    }
    this.name = name;
    this.parseFunction = parseFunction;
    nameToKeyMap.put(name, this);
  }

  public static ModelCustomizationKey getKey(String name) {
    return nameToKeyMap.get(name);
  }

  /**
   * Parse the {@code params}
   *
   * @param params string list which will be parsed. Example ['true'] -> true
   * @return parsed value
   */
  public V parse(MutableList<String> params) {
    return this.parseFunction.valueOf(params);
  }

  /**
   * Retrieve name corresponding to this key
   *
   * @return name corresponding to this key
   */
  public String getName() {
    return this.name;
  }
}
