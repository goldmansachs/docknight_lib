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

package com.gs.ep.docknight.model.transformer.grouping;

import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.context.PagePartitionType;
import java.util.List;

/**
 * Class representing information related to partitioning within the page.
 */
public class PagePartition {

  public final List<Element> elements;
  public final double topBoundary;
  public final double bottomBoundary;
  public final PagePartitionType partitionType;

  PagePartition(List<Element> elements, double topBoundary, double bottomBoundary,
      PagePartitionType partitionType) {
    this.elements = elements;
    this.topBoundary = topBoundary;
    this.bottomBoundary = bottomBoundary;
    this.partitionType = partitionType;
  }
}
