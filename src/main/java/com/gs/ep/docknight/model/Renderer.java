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

import com.gs.ep.docknight.model.element.Document;

/**
 * Interface to represent renderer
 */
public interface Renderer<O> {

  /**
   * Convert the {@see com.gs.ep.docknight.model.element.Document document model} into an object of
   * type {@code O}
   *
   * @param document document model which is to be converted
   */
  O render(Document document);
}
