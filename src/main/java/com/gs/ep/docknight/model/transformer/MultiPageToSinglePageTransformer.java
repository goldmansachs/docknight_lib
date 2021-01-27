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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.DoubleList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.primitive.DoubleLists;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.Length.Unit;
import com.gs.ep.docknight.model.LengthAttribute;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.Transformer;
import com.gs.ep.docknight.model.attribute.AlternateRepresentations;
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.Content;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.PageColor;
import com.gs.ep.docknight.model.attribute.PageLayout;
import com.gs.ep.docknight.model.attribute.PositionalContent;
import com.gs.ep.docknight.model.attribute.Stretch;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.GraphicalElement;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.PageBreak;
import com.gs.ep.docknight.model.element.Rectangle;
import com.gs.ep.docknight.model.element.VerticalLine;
import java.util.List;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transformer to convert multiple pages into single page of document model
 */
public class MultiPageToSinglePageTransformer implements Transformer<Document, Document> {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(MultiPageToSinglePageTransformer.class);
  private double maxVerticalSpace = Integer.MAX_VALUE;
  private DoubleList horizontalShiftLocations = DoubleLists.mutable.empty();
  private double horizontalShiftSize;
  private double pageExtremityMargin = 50;
  private boolean ignorePageBreaks;
  private boolean adjustBlankSpaces;

  public MultiPageToSinglePageTransformer withBlankSpacesAdjustmentAndIgnoredPageBreaks(
      double maxVerticalSpace, double horizontalShiftSize,
      DoubleList horizontalShiftLocations, double pageExtremityMargin) {
    this.maxVerticalSpace = maxVerticalSpace;
    this.horizontalShiftSize = horizontalShiftSize;
    this.horizontalShiftLocations = horizontalShiftLocations;
    this.pageExtremityMargin = pageExtremityMargin;
    this.ignorePageBreaks = true;
    this.adjustBlankSpaces = true;
    return this;
  }

  public MultiPageToSinglePageTransformer withIgnoredPageBreaks() {
    this.ignorePageBreaks = true;
    return this;
  }

  @Override
  public Document transform(Document document) {
    LOGGER.info("[{}][{}] Merging pages in document.", document.getDocumentSource(),
        this.getClass().getSimpleName());
    long startTime = System.currentTimeMillis();

    List<Element> docContent = document.getContent().getElementList().getElements();
    if (!this.adjustBlankSpaces && docContent.size() < 2) {
      return document;
    }

    MutableList<Element> elements = Lists.mutable.empty();
    List<Pair<java.awt.Rectangle, Integer>> combinedPageColor = Lists.mutable.empty();
    List<Pair<java.awt.Rectangle, String>> combinedPageLayout = Lists.mutable.empty();
    double pageTop = 0;
    double pageRight = 0;
    double shiftY = 0;
    double contentTop = 0;
    double contentRight = 0;

    for (Element page : docContent) {
      double pageHeight = page.getAttribute(Height.class).getMagnitude();
      double pageWidth = page.getAttribute(Width.class).getMagnitude();

      for (Element element : page.getAttribute(PositionalContent.class).getElementList()
          .getElements()) {
        Top topAttribute = element.getAttribute(Top.class);
        double top = topAttribute.getMagnitude();
        if (this.adjustBlankSpaces) {
          element.removeAttribute(Color.class);
          Left leftAttribute = element.getAttribute(Left.class);
          double left = leftAttribute.getMagnitude();
          double stretch = element instanceof GraphicalElement ? element.getAttribute(Stretch.class)
              .getMagnitude() : 0;
          double height =
              element instanceof Rectangle ? element.getAttribute(Height.class).getMagnitude() : 0;
          double bottom =
              height > 0 ? top + height : element instanceof VerticalLine ? top + stretch : top;
          double width =
              element instanceof Rectangle ? element.getAttribute(Width.class).getMagnitude() : 0;
          double right =
              width > 0 ? left + width : element instanceof HorizontalLine ? left + stretch : left;
          double newLeft = this.shiftHorizontalCoordinate(left);
          if (element instanceof HorizontalLine) {
            right = this.shiftHorizontalCoordinate(right);
            if (right < newLeft + 1) {
              continue;
            }
            if (Math.abs(right - newLeft - stretch) > 1) {
              element.getAttribute(Stretch.class).setMagnitude(right - newLeft);
            }
          } else {
            right = newLeft + width;
          }
          if (newLeft != left) {
            leftAttribute.setMagnitude(newLeft);
            shiftAlternateRepresentations(element, Left.class, newLeft - left);
          }
          contentRight = Math.max(contentRight, right);
          double newTop = top + shiftY;
          if (newTop > contentTop + this.maxVerticalSpace) {
            shiftY -= newTop - contentTop - this.maxVerticalSpace;
          }
          contentTop = Math.max(contentTop, bottom + shiftY);
        }
        if (shiftY != 0) {
          topAttribute.setMagnitude(top + shiftY);
          shiftAlternateRepresentations(element, Top.class, shiftY);
        }
        elements.add(element);
      }
      if (this.adjustBlankSpaces) {
        pageTop = contentTop + this.pageExtremityMargin;
        pageRight = contentRight + this.pageExtremityMargin;
      } else {
        // Update the rectangle coordinates of colored areas and page layout
        for (Pair<java.awt.Rectangle, Integer> coloredArea : page
            .getAttributeValue(PageColor.class, Lists.mutable.empty())) {
          java.awt.Rectangle area = coloredArea.getOne();
          area.setLocation((int) area.getX(), (int) (area.getY() + shiftY));
          combinedPageColor.add(coloredArea);
        }

        for (Pair<java.awt.Rectangle, String> layoutArea : page
            .getAttributeValue(PageLayout.class, Lists.mutable.empty())) {
          java.awt.Rectangle area = layoutArea.getOne();
          area.setLocation((int) area.getX(), (int) (area.getY() + shiftY));
          combinedPageLayout.add(layoutArea);
        }
        contentTop = pageHeight + shiftY;
        if (!this.ignorePageBreaks) {
          if (page.getElementListIndex() < docContent.size() - 1) {
            elements.add(new PageBreak().add(new Top(new Length(contentTop, Unit.pt))));
          }
        }
        pageTop = contentTop;
        pageRight = Math.max(pageRight, pageWidth);
      }
      shiftY = contentTop;
    }

    // Creating a final page
    int finalPageHeight = (int) Math.max(pageTop, PDRectangle.LETTER.getHeight());
    int finalPageWidth = (int) Math.max(pageRight, PDRectangle.LETTER.getWidth());

    Page finalPage = new Page()
        .add(new Height(new Length(finalPageHeight, Unit.pt)))
        .add(new Width(new Length(finalPageWidth, Unit.pt)))
        .add(new PositionalContent(new PositionalElementList<>(elements, false)))
        .add(new PageColor(combinedPageColor));

    if (!combinedPageLayout.isEmpty()) {
      finalPage.add(new PageLayout(combinedPageLayout));
    }
    document.removeAttribute(Content.class);
    document.add(new Content(finalPage));

    float timeTaken = (System.currentTimeMillis() - startTime) / 1000.0f;
    LOGGER.info("[{}][{}][{}s] Merged pages in document.", document.getDocumentSource(),
        this.getClass().getSimpleName(), timeTaken);

    return document;
  }

  private void shiftAlternateRepresentations(Element element,
      Class<? extends LengthAttribute> attrClass, double shift) {
    AlternateRepresentations alternateReps = element.getAttribute(AlternateRepresentations.class);
    if (alternateReps != null) {
      for (Element elem : alternateReps.getValue()) {
        LengthAttribute attr = elem.getAttribute(attrClass);
        if (attr != null && shift != 0) {
          attr.setMagnitude(attr.getMagnitude() + shift);
        }
      }
    }
  }

  private double shiftHorizontalCoordinate(double coordinate) {
    for (int i = 0; i < this.horizontalShiftLocations.size(); i++) {
      if (coordinate < this.horizontalShiftLocations.get(i)) {
        return coordinate - i * this.horizontalShiftSize;
      }
    }
    return coordinate - this.horizontalShiftLocations.size() * this.horizontalShiftSize;
  }
}
