/*
 *   Copyright 2021 Goldman Sachs.
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

package com.gs.ep.docknight.model.transformer.tabledetection.process;

import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup.GridType;
import java.util.List;
import java.util.Set;

/**
 * Possible keys for {@see com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode#scratchpad}
 */
public class CustomScratchpadKeys<V> extends ScratchpadKey<V> {

  public static final CustomScratchpadKeys<TabularElementGroup<Element>> TABULAR_GROUP = new CustomScratchpadKeys<>(
      "tabular group");
  public static final CustomScratchpadKeys<TabularElementGroup<Element>> PROCESSED_TABULAR_GROUP = new CustomScratchpadKeys<>(
      "processed tabular group");
  public static final CustomScratchpadKeys<List<TabularElementGroup<Element>>> SPLIT_TABULAR_GROUPS = new CustomScratchpadKeys<>(
      "split tabular groups");
  public static final CustomScratchpadKeys<Double> HEADER_CONFIDENCE = new CustomScratchpadKeys<>(
      "header detection confidence");
  public static final CustomScratchpadKeys<Boolean> IS_SPLIT_PERMISSIBLE = new CustomScratchpadKeys<>(
      "is split permissible");
  public static final CustomScratchpadKeys<Boolean> IS_PARENT_TABLE = new CustomScratchpadKeys<>(
      "is parent");
  public static final CustomScratchpadKeys<Integer> TABLE_INDEX = new CustomScratchpadKeys<>(
      "table index");
  public static final CustomScratchpadKeys<Integer> PAGE_NUMBER = new CustomScratchpadKeys<>(
      "document page number");
  public static final CustomScratchpadKeys<Integer> SPLIT_ROW_INDEX = new CustomScratchpadKeys<>(
      "split row index");
  public static final CustomScratchpadKeys<String> DOCUMENT_SOURCE = new CustomScratchpadKeys<>(
      "document source");
  public static final CustomScratchpadKeys<Set<Integer>> PREV_TABLES_TO_DELETE = new CustomScratchpadKeys<>(
      "indices of previous tables to delete");
  public static final CustomScratchpadKeys<GridType> IS_GRID_BASED_TABLE_DETECTION_ENABLED = new CustomScratchpadKeys<>(
      "is grid based table detection enabled");
  public static final CustomScratchpadKeys<Object> END_RESULT = new CustomScratchpadKeys<Object>(
      "processEndResult");

  protected CustomScratchpadKeys(String name) {
    super(name);
  }

  /**
   * Retrieve the value from the {@code scratchpad} corresponding to this key
   *
   * @param scratchpad map which will be shared across all the nodes in process model
   * @return the value from the {@code scratchpad} corresponding to this key
   */
  public V retrieveFrom(Scratchpad scratchpad) {
    V retrievedValue = scratchpad.retrieve(this);
    if (retrievedValue == null) {
      throw new IllegalStateException("No " + this.getName() + " retrieved from scratchpad!");
    }
    return retrievedValue;
  }
}
