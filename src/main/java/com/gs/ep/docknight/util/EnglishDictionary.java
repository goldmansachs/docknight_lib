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

package com.gs.ep.docknight.util;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.sorted.MutableSortedMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.SortedMaps;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.eclipse.collections.impl.tuple.Tuples;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.IOUtils;

/**
 * Class representing dictionary of english words
 */
public final class EnglishDictionary {

  // please maintain the ligatures sorted in decreasing order of their frequency in regression pdf texts
  private static final MutableList<String> LIGATURES = Lists.mutable
      .of("ff", "fi", "ffi", "ti", "tt", "th");
  private static Set<String> dictionary;
  // When certain characters are joined together to make a more aesthetically pleasing shape, this is called ligature
  private static MutableSortedMap<String, String> ligaturizedDictionary;

  private EnglishDictionary() {
  }

  public static synchronized Set<String> getDictionary() {
    return dictionary = dictionary == null ? getWords().toSortedSet() : dictionary;
  }

  /**
   * LigaturizedDictionary is a map where key is a word without ligature and value is ligature. For
   * example: (identi\0ed -> fi)
   *
   * @return LigaturizedDictionary
   */
  public static synchronized Map<String, String> getLigaturizedDictionary() {
    if (ligaturizedDictionary == null) {
      ligaturizedDictionary = SortedMaps.mutable.empty();
      for (String word : getWords()) {
        for (Pair<String, String> ligaturizedWord : getLigaturizedStrings(word)) {
          ligaturizedDictionary.putIfAbsent(ligaturizedWord.getOne(), ligaturizedWord.getTwo());
        }
      }
    }
    return ligaturizedDictionary;
  }

  /**
   * @return all the words present in resource english_dictionary.txt
   */
  private static MutableList<String> getWords() {
    try {
      return ListAdapter.adapt(IOUtils.readLines(
          EnglishDictionary.class.getClassLoader().getResourceAsStream("english_dictionary.txt")));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Replace ligatures from input {@code str} with \0 and return list of pair of modified str and
   * ligature. For example: input - "identified", output - [("iden\0fied, "ti"), ("identi\0ed",
   * "fi")]
   */
  private static MutableList<Pair<String, String>> getLigaturizedStrings(String str) {
    str = str.toLowerCase();
    MutableList<Pair<String, String>> result = Lists.mutable.empty();
    for (String ligature : LIGATURES) {
      if (str.contains(ligature)) {
        result.add(Tuples.pair(str.replaceFirst(ligature, "\0"), ligature));
      }
    }
    return result;
  }
}
