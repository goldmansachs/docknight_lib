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

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract node which is connected to form a workflow
 */
public abstract class AbstractProcessNode {

  public static final int DEFAULT_COLUMN_HEADER_COUNT = 1;
  protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractProcessNode.class);
  /**
   * Pad or global variable which will be accessible from all the nodes of the workflow
   */
  protected Scratchpad scratchpad;

  /**
   * @return the list of the keys that are given as input to this process node
   */
  public abstract List getRequiredKeys();

  /**
   * @return the list of the keys that can be placed on the scratchpad by this process node
   */
  public abstract List getStoredKeys();

  /**
   * Main method to execute for process node.
   *
   * @param scratchpad global variable which might get updated in this method.
   */
  public abstract void execute(Scratchpad scratchpad);

  /**
   * Method to print debug logs
   */
  protected void logEntry(String logText) {
    if (this.scratchpad.retrieveBoolean(CustomScratchpadKeys.IS_PARENT_TABLE)) {
      LOGGER.debug("[{}: Table No. {} on Page. {} of document {}] {}",
          this.getClass().getSimpleName(),
          this.scratchpad.retrieveInt(CustomScratchpadKeys.TABLE_INDEX) + 1,
          this.scratchpad.retrieveInt(CustomScratchpadKeys.PAGE_NUMBER) + 1,
          this.scratchpad.retrieve(CustomScratchpadKeys.DOCUMENT_SOURCE),
          logText);
    } else {
      LOGGER.debug("[{}: Table No. {} (split at row {}) on Page. {} of document {}] {}",
          this.getClass().getSimpleName(),
          this.scratchpad.retrieveInt(CustomScratchpadKeys.TABLE_INDEX) + 1,
          this.scratchpad.retrieveInt(CustomScratchpadKeys.SPLIT_ROW_INDEX) + 1,
          this.scratchpad.retrieveInt(CustomScratchpadKeys.PAGE_NUMBER) + 1,
          this.scratchpad.retrieve(CustomScratchpadKeys.DOCUMENT_SOURCE),
          logText);
    }
  }
}
