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

import static org.junit.Assert.assertEquals;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import java.util.regex.Pattern;
import org.junit.Test;

public class ModelCustomizationsTest {

  @Test
  public void testScratchpadFunctioning() {
    ModelCustomizations testCustomizations = new ModelCustomizations();
    MutableList<String> testNoisePatternsList = Lists.mutable.of("Note:.*", "A[a-z]*");
    assertEquals(testNoisePatternsList, testCustomizations
        .retrieveOrDefault(ModelCustomizationKey.TABULAR_NOISE_PATTERNS,
            ModelCustomizationKey.TABULAR_NOISE_PATTERNS.parse(testNoisePatternsList))
        .collect(Pattern::pattern));
    testCustomizations.add(ModelCustomizationKey.TABULAR_NOISE_PATTERNS,
        ModelCustomizationKey.TABULAR_NOISE_PATTERNS.parse(testNoisePatternsList));
    assertEquals(testNoisePatternsList, testCustomizations
        .retrieveOrDefault(ModelCustomizationKey.TABULAR_NOISE_PATTERNS, Lists.mutable.empty())
        .collect(Pattern::pattern));
    testCustomizations.remove(ModelCustomizationKey.TABULAR_NOISE_PATTERNS);
    assertEquals(Lists.mutable.empty(), testCustomizations
        .retrieveOrDefault(ModelCustomizationKey.TABULAR_NOISE_PATTERNS, Lists.mutable.empty())
        .collect(Pattern::pattern));
  }
}
