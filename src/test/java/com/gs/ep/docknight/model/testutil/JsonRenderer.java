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

package com.gs.ep.docknight.model.testutil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.collections.api.tuple.Pair;
import com.gs.ep.docknight.model.Attribute;
import com.gs.ep.docknight.model.AttributeVisitor;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementAttribute;
import com.gs.ep.docknight.model.ElementList;
import com.gs.ep.docknight.model.Form;
import com.gs.ep.docknight.model.Length;
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
import com.gs.ep.docknight.model.element.Document;
import java.awt.Rectangle;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/**
 * Json renderer to render {@see com.gs.ep.docknight.model.element.Document document model} into
 * json string. It contains implementations of all handlers for {@see
 * com.gs.ep.docknight.model.Attribute}
 */
public class JsonRenderer implements Renderer<String>, AttributeVisitor<ObjectNode> {

  private boolean prettyPrintAndSort = false;

  private static <T> void putIfNotNull(ObjectNode objectNode, String key, Collection<T> values) {
    if (values != null) {
      ArrayNode arrayContainerNode = objectNode.putArray(key);
      for (T value : values) {
        arrayContainerNode.addPOJO(value);
      }
    }
  }

  private static <T> void putIfNotNull(ObjectNode objectNode, String key, Map<String, T> values) {
    if (values != null) {
      ObjectNode objectContainerNode = objectNode.putObject(key);
      for (Map.Entry<String, T> entry : values.entrySet()) {
        objectContainerNode.putPOJO(entry.getKey(), entry.getValue());
      }
    }
  }

  private static String toHexColorString(java.awt.Color color) {
    return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
  }

  private static <T, R> JsonNode rectanglePropertiesToJsonNode(
      RectangleProperties<T> rectangleProperties, Function<T, R> propertyJsonifier) {
    ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
    if (rectangleProperties.getCommon() == null) {
      putOrSet(objectNode, "left", propertyJsonifier.apply(rectangleProperties.getLeft()));
      putOrSet(objectNode, "right", propertyJsonifier.apply(rectangleProperties.getRight()));
      putOrSet(objectNode, "top", propertyJsonifier.apply(rectangleProperties.getTop()));
      putOrSet(objectNode, "bottom", propertyJsonifier.apply(rectangleProperties.getBottom()));
    } else {
      putOrSet(objectNode, "common", propertyJsonifier.apply(rectangleProperties.getCommon()));
    }
    return objectNode;
  }

  private static void putOrSet(ObjectNode objectNode, String fieldName, Object object) {
    if (object instanceof JsonNode) {
      objectNode.set(fieldName, (JsonNode) object);
    } else {
      objectNode.putPOJO(fieldName, object);
    }
  }

  public JsonRenderer withPrettyPrintAndSort(boolean prettyPrintAndSort) {
    this.prettyPrintAndSort = prettyPrintAndSort;
    return this;
  }

  @Override
  public String render(Document document) {
    ArrayNode arrayContainerNode = JsonNodeFactory.instance.arrayNode();
    this.handleElement(document, arrayContainerNode);
    JsonNode documentNode = arrayContainerNode.get(0);
    try {
      ObjectMapper mapper = new ObjectMapper();
      if (this.prettyPrintAndSort) {
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return mapper.writeValueAsString(mapper.treeToValue(documentNode, Object.class));
      }
      return mapper.writeValueAsString(documentNode);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public void handleAttribute(Align align, ObjectNode node) {
    node.put(align.getName(), align.getValue());
  }

  public void handleAttribute(BackGroundColor backGroundColor, ObjectNode node) {
    node.put(backGroundColor.getName(), toHexColorString(backGroundColor.getValue()));
  }

  public void handleAttribute(BorderColor borderColor, ObjectNode node) {
    node.set(borderColor.getName(),
        rectanglePropertiesToJsonNode(borderColor.getValue(), JsonRenderer::toHexColorString));
  }

  public void handleAttribute(BorderStyle borderStyle, ObjectNode node) {
    node.set(borderStyle.getName(),
        rectanglePropertiesToJsonNode(borderStyle.getValue(), Function.identity()));
  }

  public void handleAttribute(Caption caption, ObjectNode node) {
    this.handleElementAttribute(caption, node);
  }

  public void handleAttribute(Color color, ObjectNode node) {
    node.put(color.getName(), toHexColorString(color.getValue()));
  }

  public void handleAttribute(Content content, ObjectNode node) {
    this.handleElementAttribute(content, node);
  }

  public void handleAttribute(FontFamily fontFamily, ObjectNode node) {
    node.put(fontFamily.getName(), fontFamily.getValue());
  }

  public void handleAttribute(FontSize fontSize, ObjectNode node) {
    node.put(fontSize.getName(), fontSize.getValue().toString());
  }

  public void handleAttribute(Height height, ObjectNode node) {
    node.put(height.getName(), height.getValue().toString());
  }

  public void handleAttribute(InlineContent inlineContent, ObjectNode node) {
    this.handleElementAttribute(inlineContent, node);
  }

  public void handleAttribute(Layout layout, ObjectNode node) {
    ArrayNode arrayNode = node.putArray(layout.getName());
    for (Class elementAttributeClass : layout.getValue()) {
      arrayNode.add(elementAttributeClass.getSimpleName());
    }
  }

  public void handleAttribute(Left left, ObjectNode node) {
    node.put(left.getName(), left.getValue().toString());
  }

  public void handleAttribute(PageFooter pageFooter, ObjectNode node) {
    this.handleElementAttribute(pageFooter, node);
  }

  public void handleAttribute(PageHeader pageHeader, ObjectNode node) {
    this.handleElementAttribute(pageHeader, node);
  }

  public void handleAttribute(PageMargin pageMargin, ObjectNode node) {
    node.set(pageMargin.getName(),
        rectanglePropertiesToJsonNode(pageMargin.getValue(), Length::toString));
  }

  public void handleAttribute(PageSize pageSize, ObjectNode node) {
    ObjectNode objectNode = node.putObject(pageSize.getName());
    objectNode.put("width", pageSize.getValue().getOne().toString());
    objectNode.put("height", pageSize.getValue().getTwo().toString());
  }

  public void handleAttribute(PositionalContent positionalContent, ObjectNode node) {
    this.handleElementAttribute(positionalContent, node);
  }

  public void handleAttribute(Text text, ObjectNode node) {
    node.put(text.getName(), text.getValue());
  }

  public void handleAttribute(TextAlign textAlign, ObjectNode node) {
    node.put(textAlign.getName(), textAlign.getValue());
  }

  public void handleAttribute(TextStyles textStyles, ObjectNode node) {
    ArrayNode arrayNode = node.putArray(textStyles.getName());
    for (String style : textStyles.getValue()) {
      arrayNode.add(style);
    }
  }

  public void handleAttribute(Top top, ObjectNode node) {
    node.put(top.getName(), top.getValue().toString());
  }

  public void handleAttribute(AlternateRepresentations alternateRepresentations, ObjectNode node) {
    ArrayNode arrayContainerNode = node.putArray(alternateRepresentations.getName());
    for (Element element : alternateRepresentations.getValue()) {
      this.handleElement(element, arrayContainerNode);
    }
  }

  public void handleAttribute(Url url, ObjectNode node) {
    node.put(url.getName(), url.getValue());
  }

  public void handleAttribute(Width width, ObjectNode node) {
    node.put(width.getName(), width.getValue().toString());
  }

  public void handleAttribute(Stretch stretch, ObjectNode node) {
    node.put(stretch.getName(), stretch.getValue().toString());
  }

  public void handleAttribute(LetterSpacing letterSpacing, ObjectNode node) {
    node.put(letterSpacing.getName(), letterSpacing.getValue().toString());
  }

  public void handleAttribute(ImageData imageData, ObjectNode node) {
    node.put(imageData.getName(), imageData.getValue().toBase64PngBinary());
  }

  public void handleAttribute(FormData formData, ObjectNode node) {
    ObjectNode childNode = node.putObject(formData.getName());
    Form form = formData.getValue();
    childNode.put("formType", form.getFormType().toString());
    putIfNotNull(childNode, "options", form.getOptions());
    putIfNotNull(childNode, "displayOptions", form.getDisplayOptions());
    putIfNotNull(childNode, "onValues", form.getOnValues());
    putIfNotNull(childNode, "values", form.getValues());
    putIfNotNull(childNode, "flags", form.getFlags());
  }

  public void handleAttribute(PageColor pageColor, ObjectNode node) {
    ArrayNode arrayNode = node.putArray(pageColor.getName());
    for (Pair<Rectangle, Integer> coloredArea : pageColor.getValue()) {
      ObjectNode objNode = arrayNode.addObject();
      Rectangle rect = coloredArea.getOne();
      objNode.put("rectangle", rect.x + ":" + rect.y + ":" + rect.width + ":" + rect.height);
      objNode.put("color", coloredArea.getTwo());
    }
  }

  public void handleAttribute(PageLayout pageLayout, ObjectNode node) {
    ArrayNode arrayNode = node.putArray(pageLayout.getName());
    for (Pair<Rectangle, String> layoutArea : pageLayout.getValue()) {
      ObjectNode objNode = arrayNode.addObject();
      Rectangle rect = layoutArea.getOne();
      objNode.put("rectangle", rect.x + ":" + rect.y + ":" + rect.width + ":" + rect.height);
      objNode.put("type", layoutArea.getTwo());
    }
  }

  public void handleAttribute(PageStructure pageStructure, ObjectNode node) {
    node.put(pageStructure.getName(), pageStructure.getValue());
  }

  private <E extends Element> void handleElementAttribute(
      ElementAttribute<E, ? extends ElementList<E>> attribute, ObjectNode node) {
    ArrayNode arrayContainerNode = node.putArray(attribute.getName());
    for (Element element : attribute.getValue().getElements()) {
      this.handleElement(element, arrayContainerNode);
    }
  }

  public void handleElement(Element element, ArrayNode node) {
    ObjectNode objectNode = node.addObject();
    objectNode.put("@name", element.getName());
    for (Attribute attribute : element.getAttributes()) {
      this.handleAttribute(attribute, objectNode);
    }
  }

  @Override
  public Class<? extends ObjectNode> getAttributeVisitorDataClass() {
    return ObjectNode.class;
  }
}
