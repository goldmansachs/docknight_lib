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
import com.gs.ep.docknight.model.Length.Unit;
import com.gs.ep.docknight.model.attribute.FormData;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Text;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.element.FormElement;
import com.gs.ep.docknight.model.element.PageBreak;
import com.gs.ep.docknight.model.element.TextElement;
import org.junit.Test;

public class PositionalElementListTest {

  @Test
  public void testPageBreaks() throws Exception {
    TextElement textA = new TextElement().add(new Text("A"))
        .add(new Top(new Length(86, Unit.pt)))
        .add(new Left(new Length(57, Unit.pt)))
        .add(new Width(new Length(11, Unit.pt)))
        .add(new Height(new Length(11, Unit.pt)));

    PageBreak pageBreak1 = new PageBreak().add(new Top(new Length(186, Unit.pt)));

    TextElement textB = new TextElement().add(new Text("B"))
        .add(new Top(new Length(286, Unit.pt)))
        .add(new Left(new Length(57, Unit.pt)))
        .add(new Width(new Length(11, Unit.pt)))
        .add(new Height(new Length(11, Unit.pt)));

    PageBreak pageBreak2 = new PageBreak().add(new Top(new Length(386, Unit.pt)));

    TextElement textC = new TextElement().add(new Text("C"))
        .add(new Top(new Length(486, Unit.pt)))
        .add(new Left(new Length(57, Unit.pt)))
        .add(new Width(new Length(11, Unit.pt)))
        .add(new Height(new Length(11, Unit.pt)));

    PositionalElementList<Element> positionalElementList = new PositionalElementList<>(textA,
        pageBreak1, textB, pageBreak2, textC);
    assertEquals(2, positionalElementList.getNumberOfPageBreaks());
    assertEquals(pageBreak1, positionalElementList.getPageBreak(1));
    assertEquals(pageBreak2, positionalElementList.getPageBreak(2));
    assertEquals(0, positionalElementList.getPageBreakNumber(textA));
    assertEquals(1, positionalElementList.getPageBreakNumber(pageBreak1));
    assertEquals(1, positionalElementList.getPageBreakNumber(textB));
    assertEquals(2, positionalElementList.getPageBreakNumber(pageBreak2));
    assertEquals(2, positionalElementList.getPageBreakNumber(textC));
    assertEquals(Lists.mutable.of(textB), positionalElementList.getElementsBetweenPageBreaks(1, 2));
    assertEquals(Lists.mutable.of(textB, pageBreak2, textC),
        positionalElementList.getElementsFromPageBreak(1));
    assertEquals(Lists.mutable.of(textA, pageBreak1, textB),
        positionalElementList.getElementsTillPageBreak(2));
    assertEquals(positionalElementList.getElements(),
        positionalElementList.getElementsFromPageBreak(0));
    assertEquals(positionalElementList.getElements(),
        positionalElementList.getElementsTillPageBreak(3));

    positionalElementList = new PositionalElementList<>(textA);
    assertEquals(0, positionalElementList.getNumberOfPageBreaks());
    assertEquals(0, positionalElementList.getPageBreakNumber(textA));
    assertEquals(positionalElementList.getElements(),
        positionalElementList.getElementsFromPageBreak(0));
  }

  @Test
  public void testCompareByHorizontalAlignment() {
    MutableList<TextElement> textElements = Lists.mutable.of(1, 2, 3)
        .collect(index -> new TextElement().add(new Text("Element " + index)));
    textElements.get(0).add(new Top(new Length(5, Unit.em)))
        .add(new Height(new Length(5, Unit.em)));
    textElements.get(1).add(new Top(new Length(7, Unit.em)));

    assertEquals(0, PositionalElementList
        .compareByHorizontalAlignment(textElements.get(0), textElements.get(0)));
    assertEquals(0, PositionalElementList
        .compareByHorizontalAlignment(textElements.get(1), textElements.get(1)));
    assertEquals(1, PositionalElementList
        .compareByHorizontalAlignment(textElements.get(2), textElements.get(2)));
    assertEquals(0, PositionalElementList
        .compareByHorizontalAlignment(textElements.get(0), textElements.get(1)));
    assertEquals(0, PositionalElementList
        .compareByHorizontalAlignment(textElements.get(1), textElements.get(0)));
    assertEquals(1, PositionalElementList
        .compareByHorizontalAlignment(textElements.get(1), textElements.get(2)));
    assertEquals(1, PositionalElementList
        .compareByHorizontalAlignment(textElements.get(2), textElements.get(1)));
    assertEquals(1, PositionalElementList
        .compareByHorizontalAlignment(textElements.get(0), textElements.get(2)));
    assertEquals(1, PositionalElementList
        .compareByHorizontalAlignment(textElements.get(2), textElements.get(0)));
  }

  @Test
  public void testCompareByHorizontalAlignmentForForm() {
    MutableList<Element> elements = Lists.mutable.of(1, 2)
        .collect(index -> new TextElement().add(new Text("Element " + index)));
    elements.add(new FormElement().add(new FormData(
        new Form(Form.FormType.CheckBox, Lists.mutable.empty(), null, null, Lists.mutable.empty(),
            null))));

    elements.add(new FormElement().add(new FormData(
        new Form(Form.FormType.CheckBox, Lists.mutable.empty(), null, null, Lists.mutable.empty(),
            null))));
    elements.add(new FormElement().add(new FormData(
        new Form(Form.FormType.CheckBox, Lists.mutable.empty(), null, null, Lists.mutable.empty(),
            null))));

    elements.get(0).addAttribute(new Top(new Length(5, Unit.em)));
    elements.get(0).addAttribute(new Height(new Length(5, Unit.em)));
    elements.get(1).addAttribute(new Top(new Length(7, Unit.em)));
    elements.get(2).addAttribute(new Top(new Length(6, Unit.em)));
    elements.get(2).addAttribute(new Height(new Length(5, Unit.em)));

    elements.get(3).addAttribute(new Top(new Length(11, Unit.em)));
    elements.get(3).addAttribute(new Height(new Length(1, Unit.em)));
    elements.get(4).addAttribute(new Top(new Length(11.982, Unit.em)));

    assertEquals(0,
        PositionalElementList.compareByHorizontalAlignment(elements.get(0), elements.get(0)));
    assertEquals(0,
        PositionalElementList.compareByHorizontalAlignment(elements.get(1), elements.get(1)));
    assertEquals(0,
        PositionalElementList.compareByHorizontalAlignment(elements.get(2), elements.get(2)));
    assertEquals(0,
        PositionalElementList.compareByHorizontalAlignment(elements.get(0), elements.get(1)));
    assertEquals(0,
        PositionalElementList.compareByHorizontalAlignment(elements.get(1), elements.get(0)));
    assertEquals(0,
        PositionalElementList.compareByHorizontalAlignment(elements.get(1), elements.get(2)));
    assertEquals(0,
        PositionalElementList.compareByHorizontalAlignment(elements.get(2), elements.get(1)));
    assertEquals(0,
        PositionalElementList.compareByHorizontalAlignment(elements.get(0), elements.get(2)));
    assertEquals(0,
        PositionalElementList.compareByHorizontalAlignment(elements.get(2), elements.get(0)));

    assertEquals(-1,
        PositionalElementList.compareByHorizontalAlignment(elements.get(3), elements.get(4)));
    assertEquals(1,
        PositionalElementList.compareByHorizontalAlignment(elements.get(4), elements.get(3)));
  }
}
