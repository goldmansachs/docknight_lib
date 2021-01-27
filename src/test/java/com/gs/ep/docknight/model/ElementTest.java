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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.utility.Iterate;
import com.gs.ep.docknight.model.Length.Unit;
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.Content;
import com.gs.ep.docknight.model.attribute.FontFamily;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.InlineContent;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.PositionalContent;
import com.gs.ep.docknight.model.attribute.Text;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.InlineBlock;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.TextElement;
import org.junit.Test;

public class ElementTest {

  private static String createElementId(int contentIndex, int positionalContentIndex) {
    return String.format("Content-%d_PositionalContent-%d", contentIndex, positionalContentIndex);
  }

  @Test
  public void testEquals() {
    FontFamily fontFamily = new FontFamily("Times New Roman");
    Text text = new Text("Times New Roman");
    assertFalse(fontFamily.equals(text));

    InlineBlock inlineBlock1 = new InlineBlock()
        .add(new InlineContent(
            new TextElement()
                .add(new FontSize(new Length(16, Unit.pt)))
                .add(new Text("abcde")),
            new TextElement()
                .add(new Color(new java.awt.Color(20, 20, 20)))
        ));
    InlineBlock inlineBlock2 = new InlineBlock()
        .add(new InlineContent(
            new TextElement()
                .add(new Text("abcde"))
                .add(new FontSize(new Length(16, Unit.pt))),
            new TextElement()
                .add(new Color(new java.awt.Color(20, 20, 20)))
        ));
    assertTrue(inlineBlock1.equals(inlineBlock2));
  }

  @Test
  public void testAnnotations() {
    TextElement textElementNew = new TextElement()
        .add(new Text("My name is vikas"));

    textElementNew.getText().addAnnotation("diff", "modified");

    assertFalse(textElementNew.getText().hasAnnotation("diff", "inserted"));
  }

  @Test
  public void testElementIterationWithSelf() {
    InlineBlock inlineBlock0 = new InlineBlock()
        .add(new InlineContent(
            new TextElement().add(new Text("Net Exposure in Cptyr Favor :")),
            new TextElement().add(new Text("1,000.00")),
            new TextElement().add(new Text("Threshold :")),
            new TextElement().add(new Text("0.00"))

        ));

    TextElement textElement1 = new TextElement().add(new Text("1,000.00"));

    InlineBlock inlineBlock00 = new InlineBlock()
        .add(new InlineContent(
            new TextElement().add(new Text("Limit :")),
            new TextElement().add(new Text("0.00"))
        ));

    InlineBlock inlineBlock1 = new InlineBlock()
        .add(new InlineContent(
            new TextElement().add(new Text("Return to Prin :")),
            new TextElement().add(new Text("50,000.00")),
            new TextElement().add(new Text("Variation Demand Call Amount :")),
            new TextElement().add(new Text("2,000.00"))

        ));

    InlineBlock inlineBlock2 = new InlineBlock()
        .add(new InlineContent(
            new TextElement().add(new Text("Net Margin Favor :")),
            new TextElement().add(new Text("1,000,000.00")),
            inlineBlock0,
            new TextElement().add(new Text("50.00"))

        ));

    InlineBlock inlineBlock3 = new InlineBlock()
        .add(new InlineContent(
            new InlineBlock()
                .add(new InlineContent(
                    new TextElement().add(new Text("Total Favor :")),
                    textElement1
                )),
            inlineBlock00
        ));

    Document document = new Document().add(new Content(inlineBlock1, inlineBlock2, inlineBlock3));

    Iterable<Element> documentContainingInlineBlocksNotSelf = document
        .getContainingElements(elem -> elem instanceof InlineBlock);
    assertEquals(3, Iterate.sizeOf(documentContainingInlineBlocksNotSelf));
    assertEquals(Lists.mutable.ofAll(documentContainingInlineBlocksNotSelf),
        Lists.mutable.of(inlineBlock1, inlineBlock2, inlineBlock3));

    Iterable<Element> documentContainingInlineBlocksWithSelf = document
        .getContainingElements(elem -> elem instanceof InlineBlock).withStartElement();
    assertEquals(3, Iterate.sizeOf(documentContainingInlineBlocksWithSelf));
    assertEquals(Lists.mutable.ofAll(documentContainingInlineBlocksWithSelf),
        Lists.mutable.of(inlineBlock1, inlineBlock2, inlineBlock3));

    Iterable<Element> inlineBlock2ContainingInlineBlocksNotSelf = inlineBlock2
        .getContainingElements(elem -> elem instanceof InlineBlock);
    assertEquals(1, Iterate.sizeOf(inlineBlock2ContainingInlineBlocksNotSelf));
    assertEquals(Lists.mutable.ofAll(inlineBlock2ContainingInlineBlocksNotSelf),
        Lists.mutable.of(inlineBlock0));

    Iterable<Element> inlineBlock2ContainingInlineBlocksWithSelf = inlineBlock2
        .getContainingElements(elem -> elem instanceof InlineBlock).withStartElement();
    assertEquals(2, Iterate.sizeOf(inlineBlock2ContainingInlineBlocksWithSelf));
    assertEquals(Lists.mutable.ofAll(inlineBlock2ContainingInlineBlocksWithSelf),
        Lists.mutable.of(inlineBlock2, inlineBlock0));
  }

  @Test
  public void testElementIterationWithBreakCondition() {
    TextElement textElement11 = new TextElement().add(new Text("Section 11!"));
    TextElement textElement12 = new TextElement().add(new Text("Section 12!"));
    TextElement textElement13 = new TextElement().add(new Text("Section 13!"));
    TextElement textElement14 = new TextElement().add(new Text("Section 14!"));
    TextElement textElement21 = new TextElement().add(new Text("Section 21!"));
    TextElement textElement22 = new TextElement().add(new Text("Section 22!"));
    TextElement textElement23 = new TextElement().add(new Text("Section 23!"));
    TextElement textElement24 = new TextElement().add(new Text("Section 24!"));
    TextElement textElement31 = new TextElement().add(new Text("Section 31!"));
    TextElement textElement32 = new TextElement().add(new Text("Section 32!"));
    TextElement textElement33 = new TextElement().add(new Text("Section 33!"));
    TextElement textElement34 = new TextElement().add(new Text("Section 34!"));

    InlineBlock para1 = new InlineBlock()
        .add(new InlineContent(
            textElement11,
            textElement12
        ));
    InlineBlock para2 = new InlineBlock()
        .add(new InlineContent(
            textElement13,
            textElement14
        ));
    InlineBlock para3 = new InlineBlock()
        .add(new InlineContent(
            textElement21,
            textElement22
        ));
    InlineBlock para4 = new InlineBlock()
        .add(new InlineContent(
            textElement23,
            textElement24
        ));
    InlineBlock para5 = new InlineBlock()
        .add(new InlineContent(
            textElement31,
            textElement32
        ));
    InlineBlock para6 = new InlineBlock()
        .add(new InlineContent(
            textElement33,
            textElement34
        ));

    Page page1 = new Page()
        .add(new Content(
            para1,
            para2
        ));
    Page page2 = new Page()
        .add(new Content(
            para3,
            para4
        ));
    Page page3 = new Page()
        .add(new Content(
            para5,
            para6
        ));

    Iterable<Element> containingBreakAtFirst = page2.getContainingElements(elem -> true)
        .withBreakCondition(elem -> elem.getTextStr().contains("21!"));
    assertEquals(0, Iterate.sizeOf(containingBreakAtFirst));

    Iterable<Element> containingBreakInMiddle = page2.getContainingElements(elem -> true)
        .withBreakCondition(elem -> elem.getTextStr().contains("23!"));
    assertEquals(1, Iterate.sizeOf(containingBreakInMiddle));
    assertEquals(Lists.mutable.of(para3), Lists.mutable.ofAll(containingBreakInMiddle));

    Iterable<Element> containingNoBreak = page2.getContainingElements(elem -> true)
        .withBreakCondition(elem -> elem.getTextStr().contains("00!"));
    assertEquals(2, Iterate.sizeOf(containingNoBreak));
    assertEquals(Lists.mutable.of(para3, para4), Lists.mutable.ofAll(containingNoBreak));

    Iterable<Element> containingSelfBreak = page2.getContainingElements(elem -> true)
        .withBreakCondition(elem -> elem.getTextStr().contains("3!")).withStartElement();
    assertEquals(0, Iterate.sizeOf(containingSelfBreak));

    Iterable<Element> containingSelfNoBreak = page2.getContainingElements(elem -> true)
        .withBreakCondition(elem -> elem.getTextStr().contains("0!")).withStartElement();
    assertEquals(3, Iterate.sizeOf(containingSelfNoBreak));
    assertEquals(Lists.mutable.of(page2, para3, para4), Lists.mutable.ofAll(containingSelfNoBreak));
  }

  @Test
  public void testGetTextStr() {
    InlineBlock paragraph = new InlineBlock()
        .add(new InlineContent(
            new TextElement().add(new Text("This is one very long text ")),
            new TextElement().add(new Text("which starts and finishes as well"))
        ));

    String expectedParagraphtextStr = "This is one very long text which starts and finishes as well";
    assertEquals(expectedParagraphtextStr, paragraph.getTextStr());

    Document document = new Document()
        .add(new Content(
            paragraph
        ));

    assertEquals(expectedParagraphtextStr, document.getTextStr());
  }

  @Test
  public void testEmptyTextElementToString() {
    TextElement textElement = new TextElement();
    String actual = textElement.toString();
    assertEquals("", actual);
  }

  @Test
  public void testEqualsAttributeValue() {
    TextElement textElement1 = new TextElement()
        .add(new FontSize(new Length(16, Unit.pt)));

    TextElement textElement2 = new TextElement()
        .add(new FontSize(new Length(15.95, Unit.pt)))
        .add(new Text("abcde"));

    assertFalse(textElement1.equalsAttributeValue(textElement2, Text.class, Object::equals));
    assertTrue(textElement1.equalsAttributeValue(textElement2, FontSize.class,
        (length1, length2) -> length1.getMagnitude() - length2.getMagnitude() < 0.2D));
    assertTrue(textElement1.equalsAttributeValue(textElement2, FontFamily.class, Object::equals));
  }

  @Test
  public void testElementPathFunctions() {
    TextElement textElement1 = new TextElement().add(new Text("Text Block 1"))
        .add(new Top(new Length(70, Unit.pt)))
        .add(new Left(new Length(57, Unit.pt)))
        .add(new Width(new Length(77, Unit.pt)))
        .add(new Height(new Length(11, Unit.pt)));

    TextElement textElement2 = new TextElement().add(new Text("Text Block 2"))
        .add(new Top(new Length(86, Unit.pt)))
        .add(new Left(new Length(57, Unit.pt)))
        .add(new Width(new Length(77, Unit.pt)))
        .add(new Height(new Length(11, Unit.pt)));

    TextElement textElement3 = new TextElement().add(new Text("Text Block 3"))
        .add(new Top(new Length(86, Unit.pt)))
        .add(new Left(new Length(57, Unit.pt)))
        .add(new Width(new Length(77, Unit.pt)))
        .add(new Height(new Length(11, Unit.pt)));

    Document document = new Document()
        .add(new Content(
            new Page()
                .add(new Width(new Length(200, Unit.pt)))
                .add(new Height(new Length(200, Unit.pt)))
                .add(new PositionalContent(new PositionalElementList<Element>(Lists.mutable.of(
                    textElement1,
                    textElement2
                )))),
            new Page()
                .add(new Width(new Length(200, Unit.pt)))
                .add(new Height(new Length(200, Unit.pt)))
                .add(new PositionalContent(new PositionalElementList<Element>(Lists.mutable.of(
                    textElement3
                ))))
        ));

    /*Test getElementPath*/
    assertEquals(createElementId(0, 0), textElement1.getElementPath());
    assertEquals(createElementId(0, 1), textElement2.getElementPath());
    assertEquals(createElementId(1, 0), textElement3.getElementPath());
  }

  @Test
  public void testHeaderDifferentiation() throws Exception {
    TextElement textElement1 = new TextElement().add(new Text("123"))
        .add(new TextStyles(TextStyles.BOLD));
    TextElement textElement2 = new TextElement().add(new Text("246"));
    assertTrue(textElement1.hasDifferentVisualStylesFromElement(textElement2));
    assertTrue(textElement2.hasDifferentVisualStylesFromElement(textElement1));
  }
}
