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

package com.gs.ep.docknight.model.converter.pdfparser;

/**
 * Class representing word along with index of that word within the sentence
 */
public class IndexedWord {

  private final String string;
  private final int index;
  private final boolean isWord;

  public IndexedWord(String string, int index, boolean isWord) {
    this.string = string;
    this.index = index;
    this.isWord = isWord;
  }

  public String getString() {
    return this.string;
  }

  public int getIndex() {
    return this.index;
  }

  public boolean isWord() {
    return this.isWord;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    IndexedWord that = (IndexedWord) o;
    if (this.index != that.index) {
      return false;
    }
    if (this.isWord != that.isWord) {
      return false;
    }
    return this.string.equals(that.string);
  }

  @Override
  public int hashCode() {
    int result = this.string.hashCode();
    result = 31 * result + this.index;
    result = 31 * result + (this.isWord ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "IndexedWord{" +
        "string='" + string + '\'' +
        ", index=" + index +
        ", isWord=" + isWord +
        '}';
  }
}

