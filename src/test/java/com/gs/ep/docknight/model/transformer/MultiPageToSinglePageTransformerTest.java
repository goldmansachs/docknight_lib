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

package com.gs.ep.docknight.model.transformer;

import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.primitive.DoubleLists;
import org.eclipse.collections.impl.utility.Iterate;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.attribute.AlternateRepresentations;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.testutil.DocUtils;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import java.awt.Color;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.junit.Test;

public class MultiPageToSinglePageTransformerTest {

  @Test
  public void testSinglePageDocument() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.A0);
    drawer.drawTextAt(2000, 3000, "abc");
    Document document = new MultiPageToSinglePageTransformer().transform(drawer.getDocument());
    String expectedDocumentJson = "{\n" +
        "  \"@name\" : \"Document\",\n" +
        "  \"Content\" : [ {\n" +
        "    \"@name\" : \"Page\",\n" +
        "    \"Height\" : \"3370.39pt\",\n" +
        "    \"PageColor\" : [ ],\n" +
        "    \"PositionalContent\" : [ {\"@name\" : \"TextElement\",\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"2000pt\",\n" +
        "      \"Text\" : \"abc\",\n" +
        "      \"Top\" : \"3000pt\",\n" +
        "      \"Width\" : \"16.66pt\"\n" +
        "    } ],\n" +
        "    \"Width\" : \"2383.94pt\"\n" +
        "  } ],\n" +
        "  \"PageStructure\" : \"flow-page-break\"\n" +
        "}";
    DocUtils.assertDocumentJson(expectedDocumentJson, document);
  }

  @Test
  public void testNoPageBreaks() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(new PDRectangle(200, 300));
    drawer.drawTextAt(100, 100, "abc");
    drawer.addPage();
    drawer.drawTextAt(100, 100, "xyz");
    Document document = new MultiPageToSinglePageTransformer().withIgnoredPageBreaks()
        .transform(drawer.getDocument());
    String expectedDocumentJson = "{\n" +
        "  \"@name\" : \"Document\",\n" +
        "  \"Content\" : [ {\n" +
        "    \"@name\" : \"Page\",\n" +
        "    \"Height\" : \"792pt\",\n" +
        "    \"PageColor\" : [ ],\n" +
        "    \"PositionalContent\" : [ {\n" +
        "      \"@name\" : \"TextElement\",\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Text\" : \"abc\",\n" +
        "      \"Top\" : \"100pt\",\n" +
        "      \"Width\" : \"16.66pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"TextElement\",\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Text\" : \"xyz\",\n" +
        "      \"Top\" : \"400pt\",\n" +
        "      \"Width\" : \"17.33pt\"\n" +
        "    } ],\n" +
        "    \"Width\" : \"612pt\"\n" +
        "  } ],\n" +
        "  \"PageStructure\" : \"flow-page-break\"\n" +
        "}";
    DocUtils.assertDocumentJson(expectedDocumentJson, document);
  }

  @Test
  public void testAlternateRepresentations() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(new PDRectangle(200, 300));
    drawer.addPage();
    drawer.drawTextAt(100, 100, "xyz");
    Document document = drawer.getDocument();
    Element xyz = Iterate.getFirst(document.getContainingElements(TextElement.class));
    xyz.addAttribute(new AlternateRepresentations(Lists.mutable.of(xyz.clone())));
    document = new MultiPageToSinglePageTransformer().transform(document);
    String expectedDocumentJson = "{\n" +
        "  \"@name\" : \"Document\",\n" +
        "  \"Content\" : [ {\n" +
        "    \"@name\" : \"Page\",\n" +
        "    \"Height\" : \"792pt\",\n" +
        "    \"PageColor\" : [ ],\n" +
        "    \"PositionalContent\" : [ {\n" +
        "      \"@name\" : \"PageBreak\",\n" +
        "      \"Top\" : \"300pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"TextElement\",\n" +
        "      \"AlternateRepresentations\" : [ {\n" +
        "        \"@name\" : \"TextElement\",\n" +
        "        \"FontFamily\" : \"Times\",\n" +
        "        \"FontSize\" : \"12pt\",\n" +
        "        \"Height\" : \"8.37pt\",\n" +
        "        \"Left\" : \"100pt\",\n" +
        "        \"Text\" : \"xyz\",\n" +
        "        \"Top\" : \"400pt\",\n" +
        "        \"Width\" : \"17.33pt\"\n" +
        "      } ],\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Text\" : \"xyz\",\n" +
        "      \"Top\" : \"400pt\",\n" +
        "      \"Width\" : \"17.33pt\"\n" +
        "    } ],\n" +
        "    \"Width\" : \"612pt\"\n" +
        "  } ],\n" +
        "  \"PageStructure\" : \"flow-page-break\"\n" +
        "}";
    DocUtils.assertDocumentJson(expectedDocumentJson, document);
  }

  @Test
  public void testPageBreaks() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(new PDRectangle(600, 400));
    drawer.setWriteAndFillColor(Color.YELLOW);
    drawer.drawRectangleAt(100, 100, 50, 50, RenderingMode.FILL_STROKE);
    drawer.setWriteAndFillColor(Color.BLACK);
    drawer.drawTextAt(100, 100, "abc");
    drawer.addPage(new PDRectangle(700, 400));
    drawer.setWriteAndFillColor(Color.YELLOW);
    drawer.drawRectangleAt(100, 100, 50, 50, RenderingMode.FILL_STROKE);
    drawer.setWriteAndFillColor(Color.BLACK);
    drawer.drawTextAt(100, 100, "xyz");
    drawer.drawTextAt(200, 200, "123");
    Document document = new MultiPageToSinglePageTransformer().transform(drawer.getDocument());
    String expectedDocumentJson = "{\n" +
        "  \"@name\" : \"Document\",\n" +
        "  \"Content\" : [ {\n" +
        "    \"@name\" : \"Page\",\n" +
        "    \"Height\" : \"800pt\",\n" +
        "    \"PageColor\" : [ {\n" +
        "      \"color\" : 16776960,\n" +
        "      \"rectangle\" : \"100:100:50:50\"\n" +
        "    }, {\n" +
        "      \"color\" : 16776960,\n" +
        "      \"rectangle\" : \"100:500:50:50\"\n" +
        "    } ],\n" +
        "    \"PositionalContent\" : [ {\n" +
        "      \"@name\" : \"TextElement\",\n" +
        "      \"BackGroundColor\" : \"#FFFF00\",\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Text\" : \"abc\",\n" +
        "      \"Top\" : \"100pt\",\n" +
        "      \"Width\" : \"16.66pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"HorizontalLine\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"100pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"VerticalLine\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"100pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"VerticalLine\",\n" +
        "      \"Left\" : \"150pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"100pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"HorizontalLine\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"150pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"PageBreak\",\n" +
        "      \"Top\" : \"400pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"TextElement\",\n" +
        "      \"BackGroundColor\" : \"#FFFF00\",\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Text\" : \"xyz\",\n" +
        "      \"Top\" : \"500pt\",\n" +
        "      \"Width\" : \"17.33pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"HorizontalLine\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"500pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"VerticalLine\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"500pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"VerticalLine\",\n" +
        "      \"Left\" : \"150pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"500pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"HorizontalLine\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"550pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"TextElement\",\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"200pt\",\n" +
        "      \"Text\" : \"123\",\n" +
        "      \"Top\" : \"600pt\",\n" +
        "      \"Width\" : \"18pt\"\n" +
        "    } ],\n" +
        "    \"Width\" : \"700pt\"\n" +
        "  } ],\n" +
        "  \"PageStructure\" : \"flow-page-break\"\n" +
        "}";
    DocUtils.assertDocumentJson(expectedDocumentJson, document);
  }

  @Test
  public void testBlankSpaceAdjustment() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.A0);
    drawer.setWriteAndFillColor(Color.RED);
    drawer.drawRectangleAt(100, 100, 50, 50, RenderingMode.FILL_STROKE);
    drawer.setWriteAndFillColor(Color.WHITE);
    drawer.drawTextAt(100, 100, "1");
    drawer.setWriteAndFillColor(Color.BLACK);
    drawer.drawTextAt(1000, 100, "2");
    drawer.drawTextAt(2000, 100, "3");
    drawer.drawHorizontalLineAt(1450, 200, 100);
    drawer.drawHorizontalLineAt(900, 300, 1100);
    drawer.drawTextAt(100, 1000, "4");
    drawer.addPage();
    drawer.drawTextAt(100, 100, "5");
    Document document = new MultiPageToSinglePageTransformer()
        .withBlankSpacesAdjustmentAndIgnoredPageBreaks(50, 250,
            DoubleLists.mutable.of(240, 490, 740, 1250, 1500, 1750), 50)
        .transform(drawer.getDocument());
    String expectedDocumentJson = "{\n" +
        "  \"@name\" : \"Document\",\n" +
        "  \"Content\" : [ {\n" +
        "    \"@name\" : \"Page\",\n" +
        "    \"Height\" : \"792pt\",\n" +
        "    \"PageColor\" : [ ],\n" +
        "    \"PositionalContent\" : [ {\n" +
        "      \"@name\" : \"TextElement\",\n" +
        "      \"BackGroundColor\" : \"#FF0000\",\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Text\" : \"1\",\n" +
        "      \"Top\" : \"50pt\",\n" +
        "      \"Width\" : \"6pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"VerticalLine\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"50pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"HorizontalLine\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"50pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"VerticalLine\",\n" +
        "      \"Left\" : \"150pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"50pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"TextElement\",\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"250pt\",\n" +
        "      \"Text\" : \"2\",\n" +
        "      \"Top\" : \"50pt\",\n" +
        "      \"Width\" : \"6pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"TextElement\",\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"500pt\",\n" +
        "      \"Text\" : \"3\",\n" +
        "      \"Top\" : \"50pt\",\n" +
        "      \"Width\" : \"6pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"HorizontalLine\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Stretch\" : \"50pt\",\n" +
        "      \"Top\" : \"100pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"HorizontalLine\",\n" +
        "      \"Left\" : \"150pt\",\n" +
        "      \"Stretch\" : \"350pt\",\n" +
        "      \"Top\" : \"150pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"TextElement\",\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Text\" : \"4\",\n" +
        "      \"Top\" : \"200pt\",\n" +
        "      \"Width\" : \"6pt\"\n" +
        "    }, {\n" +
        "      \"@name\" : \"TextElement\",\n" +
        "      \"FontFamily\" : \"Times\",\n" +
        "      \"FontSize\" : \"12pt\",\n" +
        "      \"Height\" : \"8.37pt\",\n" +
        "      \"Left\" : \"100pt\",\n" +
        "      \"Text\" : \"5\",\n" +
        "      \"Top\" : \"258.37pt\",\n" +
        "      \"Width\" : \"6pt\"\n" +
        "    } ],\n" +
        "    \"Width\" : \"612pt\"\n" +
        "  } ],\n" +
        "  \"PageStructure\" : \"flow-page-break\"\n" +
        "}";
    DocUtils.assertDocumentJson(expectedDocumentJson, document);
  }
}
