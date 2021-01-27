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

import static org.eclipse.collections.impl.test.Verify.assertThrows;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LengthTest {

  @Test
  public void testToString() throws Exception {
    assertEquals("45.26pt", new Length(45.26, Length.Unit.pt).toString());
    assertEquals("99.68px", new Length(99.678, Length.Unit.px).toString());
    assertEquals("0.013em", new Length(0.0134, Length.Unit.em).toString());
    assertEquals("100%", new Length(100, Length.Unit.percent).toString());
  }

  @Test
  public void testAdd() throws Exception {
    assertEquals(new Length(100, Length.Unit.pt),
        new Length(67, Length.Unit.pt).add(new Length(33, Length.Unit.pt)));
    assertEquals(new Length(100, Length.Unit.pt), new Length(100, Length.Unit.pt).add(null));
    assertThrows(UnsupportedOperationException.class,
        () -> new Length(67, Length.Unit.pt).add(new Length(33, Length.Unit.px)));
  }
}
