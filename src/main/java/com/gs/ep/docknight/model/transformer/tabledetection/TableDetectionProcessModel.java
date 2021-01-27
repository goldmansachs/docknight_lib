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

package com.gs.ep.docknight.model.transformer.tabledetection;

import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import com.gs.ep.docknight.model.transformer.tabledetection.process.CustomScratchpadKeys;
import com.gs.ep.docknight.model.transformer.tabledetection.process.ProcessModel;
import com.gs.ep.docknight.model.transformer.tabledetection.process.Scratchpad;
import org.eclipse.collections.impl.factory.Lists;

/**
 * Process model to define nodes in a table detection workflow
 */
public class TableDetectionProcessModel extends ProcessModel {

  public Object execute(Scratchpad scratchpad) {
    //Nodes need to be added in the desired order of execution
    this.setProcessNodes(Lists.mutable.of(
        new ColumnHeaderMergingProcessNode(),
        new ColumnHeaderExpansionProcessNode(),
        new HeaderConfidenceCalculationProcessNode(),
        new InternalRowMergingProcessNode(),
        new TableSplittingProcessNode(),
        new InternalColumnMergingProcessNode(),
        new ColumnSplittingProcessNode(),
        new TotalRowDetectionProcessNode()
    ));

    for (AbstractProcessNode processNode : this.getProcessNodes()) {
      processNode.execute(scratchpad);
    }
    return scratchpad.retrieve(CustomScratchpadKeys.END_RESULT);
  }
}
