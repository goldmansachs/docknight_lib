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

import org.eclipse.collections.api.list.MutableList;

/**
 * Class to define the process model and it will execute all the process node within it.
 */
public abstract class ProcessModel {

  private MutableList<AbstractProcessNode> processNodes;

  /**
   * Method to execute all the process nodes within it in a particular order
   *
   * @param scratchpad - global variable which will be shared across all process nodes
   * @return result corresponding to end result key in scratchpad.
   * @see com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys#END_RESULT
   */
  public abstract Object execute(Scratchpad scratchpad);

  /**
   * Returns the process nodes defined in this model
   */
  public MutableList<AbstractProcessNode> getProcessNodes() {
    return processNodes;
  }

  /**
   * Method to set process nodes in this model
   *
   * @param processNodes nodes to be set
   */
  public void setProcessNodes(MutableList<AbstractProcessNode> processNodes) {
    this.processNodes = processNodes;
  }
}
