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

import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import java.util.List;
import java.util.Map;

/**
 * Class represent the builder to build {@see com.gs.ep.docknight.model.element.Document document
 * model} into map data structure. This class object stores current state of the Document in the
 * Maps. Example: page map will store the current page. When new page is to created, the current
 * page map will be wiped out
 */
public class VisualJsonBuilder {

  private Map document;
  private Map page;
  private Map group;
  private Map line;
  private Map segment;

  public static Map dict() {
    return Maps.mutable.empty();
  }

  public static List list() {
    return Lists.mutable.empty();
  }

  /**
   * Append the value to the map[key] if key exists, else assign map[key] = list(value)
   *
   * @param map map which will be updated
   * @param key key whose value has to be updated
   * @param value value which has to be appended
   */
  public static void append(Map map, Object key, Object value) {
    List keyValue = (List) map.get(key);
    if (keyValue == null) {
      keyValue = list();
      map.put(key, keyValue);
    }
    keyValue.add(value);
  }

  public void startDocument() {
    this.document = dict();
  }

  public void startPage() {
    this.page = dict();
    append(this.document, "pages", this.page);
  }

  public void startGroup(String groupName) {
    this.group = dict();
    append(this.page, groupName, this.group);
  }

  public void startLine() {
    this.line = dict();
    append(this.group, "lines", this.line);
  }

  public void startSegment() {
    this.segment = dict();
    append(this.line, "segments", this.segment);
  }

  public Map getDocument() {
    return document;
  }

  public void setDocument(Map document) {
    this.document = document;
  }

  public Map getPage() {
    return page;
  }

  public void setPage(Map page) {
    this.page = page;
  }

  public Map getGroup() {
    return group;
  }

  public void setGroup(Map group) {
    this.group = group;
  }

  public Map getLine() {
    return this.line;
  }

  public void setLine(Map line) {
    this.line = line;
  }

  public Map getSegment() {
    return this.segment;
  }

  public void setSegment(Map segment) {
    this.segment = segment;
  }
}
