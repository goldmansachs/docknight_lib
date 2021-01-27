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

import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface to define Abbyy Api
 */
public interface AbbyyAPI {

  Logger LOGGER = LoggerFactory.getLogger(AbbyyAPI.class);

  /**
   * Factory method to instantiate concrete abbyy api object
   *
   * @return concrete abbyy api object
   */
  static AbbyyAPI getAPI() throws Exception {
    AbbyServerAPI abbyServerAPI = new AbbyServerAPI();
    LOGGER.debug("Using Server Abbyy");
    return abbyServerAPI;
  }

  /**
   * Convert the scanned pdf input stream into editable pdf stream
   *
   * @param inputStream scanned pdf stream
   * @param abbyyParams abbyy parameters
   * @return editable pdf stream
   */
  InputStream convertPdf(InputStream inputStream, AbbyyParams abbyyParams) throws Exception;
}
