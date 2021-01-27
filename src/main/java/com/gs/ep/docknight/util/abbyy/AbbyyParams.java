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

package com.gs.ep.docknight.util.abbyy;

/**
 * POJO to store abbyy parameters
 */
public class AbbyyParams {

  public String[] languages;
  public Boolean correctOrientation;

  public String[] getLanguages() {
    return this.languages;
  }

  public void setLanguages(String... languages) {
    this.languages = languages;
  }

  public Boolean getCorrectOrientation() {
    return this.correctOrientation;
  }

  public void setCorrectOrientation(Boolean correctOrientation) {
    this.correctOrientation = correctOrientation;
  }
}
