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

package com.gs.ep.docknight.model.renderer;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import com.gs.ep.docknight.model.Attribute;
import com.gs.ep.docknight.model.AttributeVisitor;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementAttribute;
import com.gs.ep.docknight.model.ElementList;
import com.gs.ep.docknight.model.ElementVisitor;
import com.gs.ep.docknight.model.Form;
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.Length.Unit;
import com.gs.ep.docknight.model.PositionalContext;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.RectangleProperties;
import com.gs.ep.docknight.model.Renderer;
import com.gs.ep.docknight.model.attribute.Align;
import com.gs.ep.docknight.model.attribute.AlternateRepresentations;
import com.gs.ep.docknight.model.attribute.BackGroundColor;
import com.gs.ep.docknight.model.attribute.BorderColor;
import com.gs.ep.docknight.model.attribute.BorderStyle;
import com.gs.ep.docknight.model.attribute.Caption;
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.Content;
import com.gs.ep.docknight.model.attribute.FontFamily;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.FormData;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.ImageData;
import com.gs.ep.docknight.model.attribute.InlineContent;
import com.gs.ep.docknight.model.attribute.Layout;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.LetterSpacing;
import com.gs.ep.docknight.model.attribute.PageColor;
import com.gs.ep.docknight.model.attribute.PageFooter;
import com.gs.ep.docknight.model.attribute.PageHeader;
import com.gs.ep.docknight.model.attribute.PageLayout;
import com.gs.ep.docknight.model.attribute.PageMargin;
import com.gs.ep.docknight.model.attribute.PageSize;
import com.gs.ep.docknight.model.attribute.PageStructure;
import com.gs.ep.docknight.model.attribute.PositionalContent;
import com.gs.ep.docknight.model.attribute.Stretch;
import com.gs.ep.docknight.model.attribute.Text;
import com.gs.ep.docknight.model.attribute.TextAlign;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Url;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.context.PagePartitionType;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.FormElement;
import com.gs.ep.docknight.model.element.GraphicalElement;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.Image;
import com.gs.ep.docknight.model.element.InlineBlock;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.PageBreak;
import com.gs.ep.docknight.model.element.Rectangle;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.element.VerticalLine;
import com.gs.ep.docknight.model.renderer.construct.HtmlConstruct;
import com.gs.ep.docknight.model.renderer.construct.HtmlDataSuffix;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.nodes.Entities.EscapeMode;
import org.jsoup.parser.Tag;

/**
 * Html renderer to render {@see com.gs.ep.docknight.model.element.Document document model} into
 * html string. It contains implementations of all handlers for {@see
 * com.gs.ep.docknight.model.Attribute} and {@see com.gs.ep.docknight.model.Element} concrete
 * objects.
 */
public class HtmlRenderer implements Renderer<String>, AttributeVisitor<org.jsoup.nodes.Element>,
    ElementVisitor<org.jsoup.nodes.Element> {

  private static final List<String> COLORS = Lists.mutable
      .of("Blue", "Brown", "Coral", "DarkGreen", "DarkMagenta", "DarkViolet", "DeepPink",
          "FireBrick",
          "ForestGreen", "GoldenRod", "Indigo", "Olive");

  private boolean withHtmlTag = true;
  private boolean sortHtmlAttributesWhileRendering;
  private boolean isOutputMediumPrint;
  private boolean visualDebuggingEnabled;
  private boolean includeElementListData = true;
  private org.jsoup.nodes.Document docNode;
  private Length pageHeightOffset;
  private boolean isPositional;
  private boolean pageBreakNosEnabled;
  private Map<Integer, Integer> customGroupings;

  public HtmlRenderer() {
  }

  public HtmlRenderer(boolean withHtmlTag) {
    this.withHtmlTag = withHtmlTag;
  }

  private static void addCommonButtonAttrs(org.jsoup.nodes.Element buttonNode, Form button) {
    buttonNode.attr("onclick", "return false;");
    if (button.isChecked()) {
      buttonNode.attr("checked", "checked");
    }
  }

  /**
   * Convert the {@code color} to hex format
   *
   * @param color color to be converted
   * @return hex color string
   */
  private static String toHexColorString(java.awt.Color color) {
    return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
  }

  /**
   * Add the style property with {@code key} and {@code value} on {@code htmlNode}
   *
   * @param htmlNode node on which style property to add
   * @param key key of style property
   * @param value value of style property
   */
  private static void addKeyValueToHtmlNodeStyleAttr(org.jsoup.nodes.Element htmlNode, String key,
      String value) {
    String styleData = htmlNode.attr(HtmlConstruct.STYLE);
    htmlNode.attr(HtmlConstruct.STYLE, styleData.isEmpty() ? (key + ":" + value)
        : Lists.mutable.of(styleData.split(";")).with(key + ":" + value).sortThis()
            .makeString(";"));
  }

  /**
   * Sort the attributes within html node in ascending order of attribute keys.
   *
   * @param htmlNode node which has to be modified in place.
   */
  public static void sortHtmlNodeAttributes(org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Attributes attributes = htmlNode.attributes();
    List<org.jsoup.nodes.Attribute> attributeList = Lists.mutable
        .ofAll(htmlNode.attributes().asList()).sortThisBy(org.jsoup.nodes.Attribute::getKey);

    for (org.jsoup.nodes.Attribute attribute : attributeList) {
      attributes.remove(attribute.getKey());
    }
    for (org.jsoup.nodes.Attribute attribute : attributeList) {
      attributes.put(attribute);
    }

    Iterator<org.jsoup.nodes.Element> childIterator = htmlNode.children().iterator();
    while (childIterator.hasNext()) {
      sortHtmlNodeAttributes(childIterator.next());
    }
  }

  /**
   * Make the string {@code s} html friendly. (Format the text {@code s} into html format)
   *
   * @param s text which is to be converted to html format
   * @return escaped html string
   */
  public static String escapeHtml(String s) {
    StringBuilder builder = new StringBuilder();
    boolean previousWasASpace = false;
    for (char c : s.toCharArray()) {
      if (c == ' ') {
        if (previousWasASpace) {
          builder.append("&nbsp;");
          previousWasASpace = false;
          continue;
        }
        previousWasASpace = true;
      } else {
        previousWasASpace = false;
      }
      switch (c) {
        case '<':
          builder.append("&lt;");
          break;
        case '>':
          builder.append("&gt;");
          break;
        case '&':
          builder.append("&amp;");
          break;
        case '"':
          builder.append("&quot;");
          break;
        case '\n':
          builder.append("<br>");
          break;
        case '\t':
          builder.append("&nbsp; &nbsp; ");
          break;
        default:
          builder.append(c);
      }
    }
    return builder.toString();
  }

  public HtmlRenderer withSortedHtmlAttributesWhileRendering(
      boolean sortHtmlAttributesWhileRendering) {
    this.sortHtmlAttributesWhileRendering = sortHtmlAttributesWhileRendering;
    return this;
  }

  public HtmlRenderer withOutputMediumPrint(boolean isOutputMediumPrint) {
    this.isOutputMediumPrint = isOutputMediumPrint;
    return this;
  }

  public HtmlRenderer withVisualDebuggingEnabled(boolean visualDebuggingEnabled) {
    this.visualDebuggingEnabled = visualDebuggingEnabled;
    return this;
  }

  public HtmlRenderer withIncludeElementListData(boolean includeElementListData) {
    this.includeElementListData = includeElementListData;
    return this;
  }

  public HtmlRenderer withPageBreakNos(boolean pageBreakNosEnabled) {
    this.pageBreakNosEnabled = pageBreakNosEnabled;
    return this;
  }

  /**
   * Convert the {@see com.gs.ep.docknight.model.element.Document document model} into html string
   *
   * @param document document model which is to be converted
   * @return html string
   */
  @Override
  public String render(Document document) {
    return this.render(document, null);
  }

  /**
   * Convert the {@see com.gs.ep.docknight.model.element.Document document model} into html string
   *
   * @param document document model which is to be converted
   * @param customGroupings map with key as first integer in segment id and value as group id.
   * @return html string
   */
  public String render(Document document, Map<Integer, Integer> customGroupings) {
    this.customGroupings = customGroupings;
    org.jsoup.nodes.Document docNode = this.getHtmlDocumentNode();
    this.handleElement(document, docNode.body());
    org.jsoup.nodes.Element htmlNode =
        this.withHtmlTag ? docNode : docNode.body().children().first();

    if (this.sortHtmlAttributesWhileRendering) {
      sortHtmlNodeAttributes(htmlNode);
    }
    return htmlNode.toString();
  }

  /**
   * Get the empty html DOM.
   *
   * @return empty html DOM.
   */
  private org.jsoup.nodes.Document getHtmlDocumentNode() {
    org.jsoup.nodes.Document docNode = org.jsoup.nodes.Document.createShell("");
    if (this.isOutputMediumPrint) {
      docNode.outputSettings().syntax(Syntax.xml);
      docNode.outputSettings().escapeMode(EscapeMode.xhtml);
    }
    docNode.charset(docNode.charset());
    this.docNode = docNode;
    return docNode;
  }

  public void handleElement(Document document, org.jsoup.nodes.Element htmlNode) {
    this.pageHeightOffset = null;
    if (this.isOutputMediumPrint && document.getPageMargin() == null) {
      document.add(new PageMargin(new Length(0, Unit.pt)));
    }
    this.isPositional = this.isDocumentPositional(document);
    org.jsoup.nodes.Element docNode = this
        .handleElement(document, htmlNode, HtmlConstruct.DIV_TAG, Lists.mutable.of(PageSize.class,
            PageMargin.class, PageHeader.class, PageFooter.class, Content.class));
    if (this.isPositional) {
      docNode.attr(HtmlConstruct.STYLE, "position:absolute");
      if (this.includeElementListData) {
        docNode.attr(HtmlConstruct.ID, "Root");
      }
    }
  }

  /**
   * Check whether {@code document} is positional or not. Positional document is one which has pages
   * inside it and pages have positional content within it
   *
   * @param document document model which is being checked.
   * @return boolean flag indicating whether {@code document} is positional or not.
   */
  private boolean isDocumentPositional(Document document) {
    if (document.hasAttribute(Content.class)) {
      List<Element> docElements = document.getContent().getElements();
      if (!docElements.isEmpty()) {
        Element firstDocElem = docElements.get(0);
        if (firstDocElem instanceof Page && firstDocElem.hasAttribute(PositionalContent.class)) {
          return true;
        }
      }
    }
    return false;
  }

  public void handleElement(Page page, org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Element childNode = this.handleElement(page, htmlNode, HtmlConstruct.DIV_TAG,
        Lists.mutable.of(PositionalContent.class, PageColor.class));
    addKeyValueToHtmlNodeStyleAttr(childNode, "box-sizing", "border-box");
    if (!this.isOutputMediumPrint) {
      addKeyValueToHtmlNodeStyleAttr(childNode, "border-style", "solid");
      addKeyValueToHtmlNodeStyleAttr(childNode, "border-color", "#d3d3d3");
    }
    if (page.hasAttribute(Height.class)) {
      this.pageHeightOffset = page.getHeight().getValue().add(this.pageHeightOffset);
    }
    if (this.isPositional && this.includeElementListData) {
      childNode.attr(HtmlConstruct.ID, page.getElementPath());
    }
  }

  public void handleElement(InlineBlock inlineBlock, org.jsoup.nodes.Element htmlNode) {
    this.handleElement(inlineBlock, htmlNode, "span", Lists.mutable.empty());
  }

  public void handleElement(HorizontalLine horizontalLine, org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Element childNode = this
        .handleElement(horizontalLine, htmlNode, HtmlConstruct.DIV_TAG, Lists.mutable.empty());
    addKeyValueToHtmlNodeStyleAttr(childNode, "height", "1px");
    addKeyValueToHtmlNodeStyleAttr(childNode, "background-color", "#d3d3d3");
  }

  public void handleElement(VerticalLine verticalLine, org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Element childNode = this
        .handleElement(verticalLine, htmlNode, HtmlConstruct.DIV_TAG, Lists.mutable.empty());
    addKeyValueToHtmlNodeStyleAttr(childNode, "width", "1px");
    addKeyValueToHtmlNodeStyleAttr(childNode, "background-color", "#d3d3d3");
  }

  public void handleElement(PageBreak pageBreak, org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Element childNode = this
        .handleElement(pageBreak, htmlNode, HtmlConstruct.DIV_TAG, Lists.mutable.empty());
    addKeyValueToHtmlNodeStyleAttr(childNode, "width", "inherit");
    addKeyValueToHtmlNodeStyleAttr(childNode, "height", "4pt");
    addKeyValueToHtmlNodeStyleAttr(childNode, "background-color", "#d3d3d3");
  }

  public void handleAttribute(PageStructure pageStructure, org.jsoup.nodes.Element htmlNode) {
  }

  public void handleElement(Image image, org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Element node =
        image.hasAttribute(Caption.class) ? htmlNode.appendElement(HtmlConstruct.FIGURE_TAG)
            : htmlNode;
    this.handleElement(image, node, HtmlConstruct.IMG_TAG, Lists.mutable.empty());
  }

  /**
   * Method to handle url attribute
   */
  public void handleAttribute(Url url, org.jsoup.nodes.Element htmlNode) {
    Element element = url.getParentElement();
    if (element instanceof TextElement) {
      htmlNode.appendElement(HtmlConstruct.HYPERLINK_TAG).attr("href", url.getValue());
    } else if (element instanceof Image) {
      htmlNode.attr(HtmlConstruct.SRC_TAG, url.getValue());
    }
  }

  /**
   * Method to handle imageData attribute
   */
  public void handleAttribute(ImageData imageData, org.jsoup.nodes.Element htmlNode) {
    htmlNode.attr(HtmlConstruct.SRC_TAG,
        "data:image/png;base64," + imageData.getValue().toBase64PngBinary());
  }

  public void handleAttribute(FormData formData, org.jsoup.nodes.Element htmlNode) {
  }

  public void handleAttribute(AlternateRepresentations alternateRepresentations,
      org.jsoup.nodes.Element htmlNode) {
  }

  public void handleAttribute(Caption caption, org.jsoup.nodes.Element htmlNode) {
    this.handleElementAttribute(caption,
        htmlNode.parent().appendElement(HtmlConstruct.CAPTION_TAG));
  }

  public void handleElement(TextElement textElement, org.jsoup.nodes.Element htmlNode) {
    List<Class> attributeOrder = Lists.mutable.of(TextStyles.class, Url.class, Text.class);
    this.handleElement(textElement, htmlNode, "span", attributeOrder);
  }

  public void handleElement(FormElement formElement, org.jsoup.nodes.Element htmlNode) {
    Form form = formElement.getFormData().getValue();
    org.jsoup.nodes.Element childNode = null;
    switch (form.getFormType()) {
      case CheckBox:
        childNode = this
            .handleElement(formElement, htmlNode, HtmlConstruct.INPUT_TAG, Lists.mutable.empty());
        childNode.attr("type", "checkbox");
        addCommonButtonAttrs(childNode, form);
        break;
      case RadioButton:
        childNode = this
            .handleElement(formElement, htmlNode, HtmlConstruct.INPUT_TAG, Lists.mutable.empty());
        childNode.attr("type", "radio");
        addCommonButtonAttrs(childNode, form);
        break;
      case TextField:
        if (form.getFlag(Form.IS_MULTILINE, false)) {
          childNode = this.handleElement(formElement, htmlNode, HtmlConstruct.TEXTAREA_TAG,
              Lists.mutable.empty());
          childNode.text(form.getValue());
        } else {
          childNode = this
              .handleElement(formElement, htmlNode, HtmlConstruct.INPUT_TAG, Lists.mutable.empty());
          childNode.attr("type", "text");
          childNode.attr("value", form.getValue());
        }
        childNode.attr("readonly", "readonly");
        break;
      case ListBox:
      case ComboBox:
        childNode = this
            .handleElement(formElement, htmlNode, HtmlConstruct.SELECT_TAG, Lists.mutable.empty());
        if (form.getFlag(Form.IS_MULTSELECT, false)) {
          childNode.attr("multiple", "multiple");
        }
        Set<String> values = Sets.mutable.ofAll(form.getValues());
        ListIterator<String> displayOptionsIterator = form.getDisplayOptions().listIterator();
        for (String option : form.getOptions()) {
          String displayOption = displayOptionsIterator.next();
          org.jsoup.nodes.Element optionNode = childNode.appendElement(HtmlConstruct.OPTION_TAG);
          optionNode.attr("disabled", "disabled");
          optionNode.text(displayOption);
          if (values.contains(option)) {
            optionNode.attr("selected", "selected");
            values.remove(option);
          }
        }
        for (String value : values) {
          org.jsoup.nodes.Element optionNode = childNode.appendElement(HtmlConstruct.OPTION_TAG);
          optionNode.attr("disabled", "disabled");
          optionNode.attr("selected", "selected");
          optionNode.text(value);
        }
        break;
      case SignatureField:
        childNode = this
            .handleElement(formElement, htmlNode, HtmlConstruct.INPUT_TAG, Lists.mutable.empty());
        childNode.attr("type", "text");
        childNode.attr("value", "");
        childNode.attr("readonly", "readonly");
        break;
      default:
        throw new RuntimeException("Unsupported FormType " + form.getFormType());
    }
  }

  public void handleAttribute(PageHeader pageHeader, org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Element containerNode = this.docNode.body().appendElement(HtmlConstruct.DIV_TAG)
        .attr("class", "page-header").attr("style", "display:none");
    this.handleElementAttribute(pageHeader, containerNode);
  }

  public void handleAttribute(PageFooter pageFooter, org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Element containerNode = this.docNode.body().appendElement(HtmlConstruct.DIV_TAG)
        .attr("class", "page-footer").attr("style", "display:none");
    this.handleElementAttribute(pageFooter, containerNode);
  }

  public void handleAttribute(Content content, org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Element wrapperNode = this
        .getWrapperHtmlNodeForContentElement(content, htmlNode);
    for (Element element : content.getValue().getElements()) {
      org.jsoup.nodes.Element node = htmlNode;
      if (wrapperNode != null) {
        node = wrapperNode.clone();
        htmlNode.appendChild(node);
      }
      this.handleElementWithAnnotations(element, node);
    }
  }

  private org.jsoup.nodes.Element getWrapperHtmlNodeForContentElement(Content content,
      org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Element node;

    switch (htmlNode.tagName()) {
      case HtmlConstruct.UL_TAG:
      case HtmlConstruct.OL_TAG:
        node = new org.jsoup.nodes.Element(Tag.valueOf(HtmlConstruct.LI_TAG), "");
        addKeyValueToHtmlNodeStyleAttr(node, "margin-top", "10px");
        break;
      default:
        node = null;
    }
    return node;
  }

  public void handleAttribute(InlineContent inlineContent, org.jsoup.nodes.Element htmlNode) {
    this.handleElementAttribute(inlineContent, htmlNode);
  }

  public void handleAttribute(PositionalContent positionalContent,
      org.jsoup.nodes.Element htmlNode) {
    String positionalContentParentPath = positionalContent.getParentElement().getElementPath();
    String idPrefix = String
        .format("%s_%s-", positionalContentParentPath, PositionalContent.class.getSimpleName());
    PositionalElementList<Element> positionalElementList = positionalContent.getValue();

    int verticalGroupIdCtr = -1;
    Map<Integer, Integer> verticalGroupToId = Maps.mutable.empty();
    int tabularGroupIdCtr = -1;
    Map<Integer, Integer> tabularGroupToId = Maps.mutable.empty();

    for (Element element : positionalElementList.getElements()) {
      this.handleElementWithAnnotations(element, htmlNode);
      org.jsoup.nodes.Element childNode = htmlNode.children().last();
      if (this.includeElementListData && element instanceof Rectangle) {
        childNode.attr(HtmlConstruct.ID,
            String.format("%s%d", idPrefix, element.getElementListContext().getTwo()));
        PositionalContext<Element> positionalContext = element.getPositionalContext();

        if (element instanceof TextElement) {
          if (positionalContext != null) {
            Element topElement = positionalContext.getVerticalGroup().getFirst();
            childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.VERTICAL_GROUP_ID,
                String.format("%s%d", idPrefix, topElement.getElementListContext().getTwo()));

            this.addVerticalGroupDataAttr(childNode,
                topElement.getPositionalContext().getShadowedAboveElement(),
                HtmlDataSuffix.ABOVE_GROUP_ID, idPrefix);

            Element bottomElement = positionalContext.getVerticalGroup().getLast();
            this.addVerticalGroupDataAttr(childNode,
                bottomElement.getPositionalContext().getShadowedBelowElement(),
                HtmlDataSuffix.BELOW_GROUP_ID, idPrefix);

            childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.VISUAL_TOP,
                topElement.getAttribute(Top.class).getValue().add(this.pageHeightOffset)
                    .toString());
            childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.VISUAL_HEIGHT,
                new Length(bottomElement.getAttribute(Top.class).getValue()
                    .add(bottomElement.getAttribute(Height.class).getValue()
                        .add(bottomElement.getAttribute(FontSize.class).getValue())).getMagnitude()
                    - topElement.getAttribute(Top.class).getValue().getMagnitude(), Unit.pt)
                    .toString());

            MutableList<Element> elements = positionalContext.getVerticalGroup().getElements();
            double left = elements
                .collect(elem -> elem.getAttribute(Left.class).getValue().getMagnitude()).min();
            double right = elements
                .collect(elem -> elem.getAttribute(Left.class).getValue().getMagnitude()
                    + elem.getAttribute(Width.class).getValue().getMagnitude()).max();
            childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.VISUAL_LEFT,
                new Length(left, Unit.pt).toString());
            childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.VISUAL_WIDTH,
                new Length(right - left, Unit.pt).toString());

            if (positionalContext.getTabularGroup() != null) {
              Element firstElementInTabularGroup = positionalContext.getTabularGroup().getFirst();
              childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.TABULAR_GROUP_ID,
                  String.format("%s%d", idPrefix,
                      firstElementInTabularGroup.getElementListContext().getTwo()));

              MutableList<Element> cellElements = positionalContext.getTabularGroup()
                  .getCell(positionalContext.getTabularRow(), positionalContext.getTabularColumn())
                  .getElements();
              childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.TABULAR_CELL_ID,
                  String.format("%s%d", idPrefix,
                      cellElements.get(0).getElementListContext().getTwo()));

              childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.TABULAR_ROW_INDEX,
                  String.valueOf(positionalContext.getTabularRow()));
              childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.TABULAR_COL_INDEX,
                  String.valueOf(positionalContext.getTabularColumn()));

              double cellLeft = cellElements
                  .collect(elem -> elem.getAttribute(Left.class).getValue().getMagnitude()).min();
              double cellRight = cellElements.collect(
                  elem -> elem.getAttribute(Left.class).getValue().getMagnitude() + elem
                      .getAttribute(Width.class).getValue().getMagnitude()).max();
              double cellTop = cellElements
                  .collect(elem -> elem.getAttribute(Top.class).getValue().getMagnitude()).min();
              double cellBottom = cellElements.collect(
                  elem -> elem.getAttribute(Top.class).getValue().getMagnitude() + elem
                      .getAttribute(Height.class).getValue().getMagnitude()).max();
              childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.TABULAR_CELL_LEFT,
                  new Length(cellLeft, Unit.pt).toString());
              childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.TABULAR_CELL_WIDTH,
                  new Length(cellRight - cellLeft, Unit.pt).toString());
              childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.TABULAR_CELL_TOP,
                  new Length(cellTop, Unit.pt).add(this.pageHeightOffset).toString());
              childNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + HtmlDataSuffix.TABULAR_CELL_HEIGHT,
                  new Length(cellBottom - cellTop, Unit.pt).toString());
            }
          }
        }

        if (this.visualDebuggingEnabled && positionalContext != null) {
          if (positionalContext.getPagePartitionType() != PagePartitionType.CONTENT) {
            double top = element.getAttribute(Top.class).getMagnitude();
            double bottom = top + element.getAttribute(Height.class).getMagnitude();
            double width = positionalContent.getParentElement().getAttribute(Width.class)
                .getMagnitude();
            java.awt.Rectangle coloredArea = new java.awt.Rectangle(0, (int) top, (int) width,
                (int) Math.ceil(bottom) - (int) top);
            this.handleAttribute(
                new PageColor(Lists.mutable.of(Tuples.pair(coloredArea, 16776960))), htmlNode);
          }

          String rectId = String
              .format("%s%d%s", idPrefix, element.getElementListContext().getTwo(), "_rect");
          childNode.attr("onclick", "var style = document.getElementById('" + rectId
              + "').style; style.borderColor='#D70000'; style.zIndex='0';");
          childNode.attr("onmouseout", "var style = document.getElementById('" + rectId
              + "').style; style.borderColor='#98FB98'; style.zIndex='-2';");
          org.jsoup.nodes.Element rectNode = htmlNode.appendElement(HtmlConstruct.DIV_TAG);
          double top = positionalContext.getVisualTop();
          double left = positionalContext.getVisualLeft();
          double width = positionalContext.getVisualRight() - left;
          double height = positionalContext.getVisualBottom() - top;
          rectNode.attr(HtmlConstruct.ID, rectId);
          addKeyValueToHtmlNodeStyleAttr(rectNode, "position", "absolute");
          addKeyValueToHtmlNodeStyleAttr(rectNode, "top",
              new Length(top, Unit.pt).add(this.pageHeightOffset).toString());
          addKeyValueToHtmlNodeStyleAttr(rectNode, "left", new Length(left, Unit.pt).toString());
          addKeyValueToHtmlNodeStyleAttr(rectNode, "width", new Length(width, Unit.pt).toString());
          addKeyValueToHtmlNodeStyleAttr(rectNode, "height",
              new Length(height, Unit.pt).toString());
          addKeyValueToHtmlNodeStyleAttr(rectNode, "border-style", "solid");
          addKeyValueToHtmlNodeStyleAttr(rectNode, "border-color", "#98FB98");
          addKeyValueToHtmlNodeStyleAttr(rectNode, "border-width", "0.5px");
          addKeyValueToHtmlNodeStyleAttr(rectNode, "z-index", "-2");

          if (element instanceof TextElement) {
            Integer groupKey = null;
            if (this.customGroupings != null) {
              groupKey = this.customGroupings.get(element.getElementListIndex());
            } else if (positionalContext.getVerticalGroup().size() > 1) {
              groupKey = positionalContext.getVerticalGroup().getFirst().getElementListContext()
                  .getTwo();
            }

            if (groupKey != null) {
              Integer id = verticalGroupToId.get(groupKey);
              if (id == null) {
                id = ++verticalGroupIdCtr;
                verticalGroupToId.put(groupKey, id);
              }
              childNode.attr("title", "V" + id);
              addKeyValueToHtmlNodeStyleAttr(childNode, "color", COLORS.get(id % COLORS.size()));
            }

            if (positionalContext.getTabularGroup() != null) {
              Element firstElementInTabularGroup = positionalContext.getTabularGroup().getFirst();
              groupKey = firstElementInTabularGroup.getElementListContext().getTwo();
              Integer id = tabularGroupToId.get(groupKey);
              if (id == null) {
                id = ++tabularGroupIdCtr;
                tabularGroupToId.put(groupKey, id);
              }
              String title = childNode.attr("title");
              childNode.attr("title", title.isEmpty() ? "T" + id : title + ", " + "T" + id);
              addKeyValueToHtmlNodeStyleAttr(childNode, "border-style", "solid");
              addKeyValueToHtmlNodeStyleAttr(childNode, "border-color",
                  COLORS.get(id % COLORS.size()));
              addKeyValueToHtmlNodeStyleAttr(childNode, "border-width", "1px");
            }
          }
        }

        if (this.visualDebuggingEnabled && element instanceof TextElement) {
          addKeyValueToHtmlNodeStyleAttr(childNode, "text-decoration", "underline");
        }
      }
      addKeyValueToHtmlNodeStyleAttr(childNode, "position", "absolute");

      if (element instanceof PageBreak) {
        addKeyValueToHtmlNodeStyleAttr(childNode, "margin-top", "-2pt");
        if (this.pageBreakNosEnabled) {
          childNode
              .attr("id", Integer.toString(positionalElementList.getPageBreakNumber(element) + 1));
        }
      } else {
        if (element instanceof TextElement) {
          addKeyValueToHtmlNodeStyleAttr(childNode, "padding-left", "5px");
          addKeyValueToHtmlNodeStyleAttr(childNode, "margin-left", "-5px");
          addKeyValueToHtmlNodeStyleAttr(childNode, "white-space", "nowrap");
          addKeyValueToHtmlNodeStyleAttr(childNode, "line-height", "75%");
        }
      }
      addKeyValueToHtmlNodeStyleAttr(childNode, "left",
          element.getAttribute(Left.class).getValue().toString());
      addKeyValueToHtmlNodeStyleAttr(childNode, "top",
          element.getAttribute(Top.class).getValue().add(this.pageHeightOffset).toString());
      if (element instanceof GraphicalElement) {
        addKeyValueToHtmlNodeStyleAttr(childNode, "z-index", "-1");
      }
      if (element instanceof Image) {

        addKeyValueToHtmlNodeStyleAttr(childNode, "z-index", "-1");

      }
    }
  }

  private void addVerticalGroupDataAttr(org.jsoup.nodes.Element htmlNode, Element element,
      String htmlDataSuffix, String idPrefix) {
    if (element instanceof TextElement) {
      Element topElement = Iterate
          .getFirst(element.getPositionalContext().getVerticalGroup().getElements());
      htmlNode.attr(HtmlConstruct.CUSTOM_DATA_PREFIX + htmlDataSuffix,
          String.format("%s%d", idPrefix, topElement.getElementListContext().getTwo()));
    }
  }

  public void handleAttribute(PageColor pageColor, org.jsoup.nodes.Element htmlNode) {
    for (Pair<java.awt.Rectangle, Integer> coloredArea : pageColor.getValue()) {
      org.jsoup.nodes.Element coloredAreaTag = htmlNode.appendElement(HtmlConstruct.DIV_TAG);
      java.awt.Rectangle area = coloredArea.getOne();

      addKeyValueToHtmlNodeStyleAttr(coloredAreaTag, "background-color",
          toHexColorString(new java.awt.Color(coloredArea.getTwo())));
      addKeyValueToHtmlNodeStyleAttr(coloredAreaTag, "height",
          new Length(area.getHeight(), Unit.pt).toString());
      addKeyValueToHtmlNodeStyleAttr(coloredAreaTag, "left",
          new Length(area.getMinX(), Unit.pt).toString());
      addKeyValueToHtmlNodeStyleAttr(coloredAreaTag, "position", "absolute");
      addKeyValueToHtmlNodeStyleAttr(coloredAreaTag, "top",
          new Length(area.getMinY(), Unit.pt).add(this.pageHeightOffset).toString());
      addKeyValueToHtmlNodeStyleAttr(coloredAreaTag, "width",
          new Length(area.getWidth(), Unit.pt).toString());
      addKeyValueToHtmlNodeStyleAttr(coloredAreaTag, "z-index", "-1");
    }
  }

  public void handleAttribute(PageLayout pageLayout, org.jsoup.nodes.Element htmlNode) {
    if (this.visualDebuggingEnabled) {
      this.handleAttribute(new PageColor(
              ListIterate.collect(pageLayout.getValue(), x -> Tuples.pair(x.getOne(), 16776960))),
          htmlNode);
    }
  }

  public void handleAttribute(Text text, org.jsoup.nodes.Element htmlNode) {
    while (!htmlNode.children().isEmpty()) {
      htmlNode = htmlNode.children().first();
    }
    htmlNode.html(escapeHtml(text.getValue()));
  }

  public void handleAttribute(TextStyles textStyles, org.jsoup.nodes.Element htmlNode) {
    for (String textStyle : textStyles.getValue()) {
      if (textStyle.equals(TextStyles.BOLD)) {
        addKeyValueToHtmlNodeStyleAttr(htmlNode, HtmlConstruct.FONT_WEIGHT, "bold");
      } else if (textStyle.equals(TextStyles.ITALIC)) {
        addKeyValueToHtmlNodeStyleAttr(htmlNode, HtmlConstruct.FONT_STYLE, "italic");
      }
    }
  }

  public void handleAttribute(Layout layout, org.jsoup.nodes.Element data) {
  }

  public void handleAttribute(Align align, org.jsoup.nodes.Element htmlNode) {
    if (align.getValue().equals(Align.CENTER)) {
      addKeyValueToHtmlNodeStyleAttr(htmlNode, "margin-left", "auto");
      addKeyValueToHtmlNodeStyleAttr(htmlNode, "margin-right", "auto");
    } else if (align.getValue().equals(Align.LEFT)) {
      addKeyValueToHtmlNodeStyleAttr(htmlNode, "margin-right", "auto");
    } else {
      addKeyValueToHtmlNodeStyleAttr(htmlNode, "margin-left", "auto");
    }
  }

  public void handleAttribute(TextAlign textAlign, org.jsoup.nodes.Element htmlNode) {
    addKeyValueToHtmlNodeStyleAttr(htmlNode, "text-align", textAlign.getValue());
  }

  public void handleAttribute(FontSize fontSize, org.jsoup.nodes.Element htmlNode) {
    addKeyValueToHtmlNodeStyleAttr(htmlNode, "font-size", fontSize.getValue().toString());
  }

  public void handleAttribute(FontFamily fontFamily, org.jsoup.nodes.Element htmlNode) {
    addKeyValueToHtmlNodeStyleAttr(htmlNode, "font-family", fontFamily.getValue());
  }

  public void handleAttribute(BackGroundColor backGroundColor, org.jsoup.nodes.Element htmlNode) {
  }

  public void handleAttribute(BorderColor borderColor, org.jsoup.nodes.Element htmlNode) {
    RectangleProperties<java.awt.Color> value = borderColor.getValue();
    String attrValue = value.getCommon() == null ?
        toHexColorString(value.getTop()) + ' ' + toHexColorString(value.getRight()) + ' '
            + toHexColorString(value.getBottom()) + ' ' + toHexColorString(value.getLeft())
        : toHexColorString(value.getCommon());
    addKeyValueToHtmlNodeStyleAttr(htmlNode, "border-color", attrValue);
  }

  public void handleAttribute(BorderStyle borderStyle, org.jsoup.nodes.Element htmlNode) {
    RectangleProperties<String> value = borderStyle.getValue();
    String attrValue =
        value.getCommon() == null ? value.getTop() + ' ' + value.getRight() + ' ' + value
            .getBottom() + ' ' + value.getLeft() : value.getCommon();
    addKeyValueToHtmlNodeStyleAttr(htmlNode, "border-style", attrValue);
  }

  public void handleAttribute(PageMargin pageMargin, org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Element containerNode = this.docNode.head().appendElement(HtmlConstruct.STYLE)
        .attr("type", "text/css");
    RectangleProperties<Length> value = pageMargin.getValue();
    String attrValue =
        value.getCommon() == null ? value.getTop().toString() + ' ' + value.getRight().toString()
            + ' ' + value.getBottom().toString() + ' ' + value.getLeft().toString()
            : value.getCommon().toString();
    containerNode.text("@page { margin: " + attrValue + "; }");
  }

  public void handleAttribute(PageSize pageSize, org.jsoup.nodes.Element htmlNode) {
    org.jsoup.nodes.Element containerNode = this.docNode.head().appendElement(HtmlConstruct.STYLE)
        .attr("type", "text/css");
    Pair<Length, Length> value = pageSize.getValue();
    String attrValue = value.getOne().toString() + ' ' + value.getTwo().toString();
    containerNode.text("@page { size: " + attrValue + "; }");
  }

  public void handleAttribute(Color color, org.jsoup.nodes.Element htmlNode) {
    addKeyValueToHtmlNodeStyleAttr(htmlNode, "color", toHexColorString(color.getValue()));
  }

  public void handleAttribute(Top top, org.jsoup.nodes.Element htmlNode) {
  }

  public void handleAttribute(Left left, org.jsoup.nodes.Element htmlNode) {
  }

  public void handleAttribute(Width width, org.jsoup.nodes.Element htmlNode) {
    Class widthElementClass = width.getParentElement().getClass();
    if (widthElementClass == Page.class || widthElementClass == Image.class
        || widthElementClass == FormElement.class) {
      addKeyValueToHtmlNodeStyleAttr(htmlNode, "width", width.getValue().toString());
    }
  }

  public void handleAttribute(Height height, org.jsoup.nodes.Element htmlNode) {
    Class heightElementClass = height.getParentElement().getClass();
    if (heightElementClass == Page.class || heightElementClass == Image.class
        || heightElementClass == FormElement.class) {
      addKeyValueToHtmlNodeStyleAttr(htmlNode, "height", height.getValue().toString());
    }
  }

  public void handleAttribute(LetterSpacing letterSpacing, org.jsoup.nodes.Element htmlNode) {
    addKeyValueToHtmlNodeStyleAttr(htmlNode, "letter-spacing", letterSpacing.getValue().toString());
  }

  public void handleAttribute(Stretch stretch, org.jsoup.nodes.Element htmlNode) {
    String stretchDimension =
        stretch.getParentElement().getClass() == HorizontalLine.class ? "width" : "height";
    addKeyValueToHtmlNodeStyleAttr(htmlNode, stretchDimension, stretch.getValue().toString());
  }

  public void handleAnnotations(Element element, org.jsoup.nodes.Element htmlNode) {
  }

  /**
   * Handle all elements within element attribute
   */
  private <E extends Element> void handleElementAttribute(
      ElementAttribute<E, ? extends ElementList<E>> elementAttribute,
      org.jsoup.nodes.Element htmlNode) {
    for (Element element : elementAttribute.getValue().getElements()) {
      this.handleElementWithAnnotations(element, htmlNode);
    }
  }

  private org.jsoup.nodes.Element handleElement(Element element, org.jsoup.nodes.Element htmlNode,
      String htmlTag, List<Class> attributeOrder) {
    org.jsoup.nodes.Element childNode = htmlNode.appendElement(htmlTag);
    Set<Class> attrClassHandled = Sets.mutable.empty();

    for (Class attrClass : attributeOrder) {
      Attribute attr = element.getAttribute(attrClass);
      if (attr != null) {
        attrClassHandled.add(attrClass);
        this.handleAttribute(attr, childNode);
      }
    }
    for (Attribute attribute : element.getAttributes()) {
      if (!attrClassHandled.contains(attribute.getClass())) {
        this.handleAttribute(attribute, childNode);
      }
    }
    return childNode;
  }

  private void handleElementWithAnnotations(Element element, org.jsoup.nodes.Element htmlNode) {
    this.handleElement(element, htmlNode);
    if (element.hasAnnotations()) {
      this.handleAnnotations(element, htmlNode.children().last());
    }
  }

  @Override
  public Class<? extends org.jsoup.nodes.Element> getAttributeVisitorDataClass() {
    return org.jsoup.nodes.Element.class;
  }

  @Override
  public Class<? extends org.jsoup.nodes.Element> getElementVisitorDataClass() {
    return org.jsoup.nodes.Element.class;
  }
}
