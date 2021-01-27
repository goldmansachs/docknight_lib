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
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to handle operations on files
 */
public class FileUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

  /**
   * Read the list from the file with name {@code resourceFileName} in resources folder
   *
   * @param resourceFileName filename of file present in resources folder
   * @return content of resource file in list format
   */
  public static MutableList<String> readListFromResourceFile(String resourceFileName) {
    try {
      InputStream inputStream = Thread.currentThread().getContextClassLoader()
          .getResourceAsStream(resourceFileName);
      return ArrayAdapter.adapt(new BufferedReader(new InputStreamReader(inputStream)).lines()
          .collect(Collectors.joining("\n")).split("\\R")).toList();
    } catch (RuntimeException e) {
      LOGGER.warn("Unable to read dictionary file: {}", resourceFileName, e);
      return Lists.mutable.empty();
    }
  }

}
