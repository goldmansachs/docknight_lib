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

import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.block.predicate.Predicate2;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.tuple.Tuples;
import com.gs.ep.docknight.model.ElementIterable.IterationDirection;
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.FontFamily;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.Layout;
import com.gs.ep.docknight.model.attribute.TextStyles;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Smallest unit within document model.
 */
public abstract class Element extends AnnotableBase implements CloneableWithReference<Element>,
    ElementCollection<Element>, DerivedCastable<Element> {

  public static final String INTRA_LINE_SEP = "\t";
  public static final String INTER_LINE_SEP = "\n";
  private static final double MAX_FONT_SIZE_CHANGE_RATIO = 0.15;
  private static final long serialVersionUID = 2434870611400888428L;

  private MutableMap<Class<? extends Attribute>, Attribute> attributesMap;
  private Pair<ElementList<? extends Element>, Integer> elementListContext;
  private transient PositionalContext<? extends Element> positionalContext;
  private transient Element currentClone;
  private boolean isIdentityBased;

  protected Element() {
    this.attributesMap = Maps.mutable.empty();
  }

  public String getName() {
    return this.getClass().getSimpleName();
  }

  public List<Attribute> getAttributes() {
    return this.attributesMap.toList();
  }

  /**
   * @return pair of element list and index of this element within that element list. (Element list
   * contains children of its parent element) / (Siblings including self)
   */
  public <E extends Element, L extends ElementList<E>> Pair<L, Integer> getElementListContext() {
    return (Pair<L, Integer>) this.elementListContext;
  }

  public <E extends Element, L extends ElementList<E>> L getElementList() {
    return (L) this.elementListContext.getOne();
  }

  public int getElementListIndex() {
    return this.elementListContext.getTwo();
  }

  public void setElementListContext(ElementList<? extends Element> elementList, int index) {
    this.elementListContext = Tuples.pair(elementList, index);
  }

  public boolean hasPositionalContext() {
    return this.positionalContext != null;
  }

  public <E extends Element> PositionalContext<E> getPositionalContext() {
    return (PositionalContext<E>) this.positionalContext;
  }

  public void setPositionalContext(PositionalContext<? extends Element> positionalContext) {
    this.positionalContext = positionalContext;
  }

  /**
   * Setter for isIdentityBased which is used to optimize hashing
   */
  public Element withIdentity(boolean isIdentityBased) {
    this.isIdentityBased = isIdentityBased;
    return this;
  }

  @Override
  public MutableList<Element> getElements() {
    return Lists.mutable.of(this);
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public Element getFirst() {
    return this;
  }

  @Override
  public Element getLast() {
    return this;
  }

  @Override
  public Element getCurrentClone() {
    if(this.currentClone == null){
      this.currentClone = this.clone();
    }
    return this.currentClone;
  }

  /**
   * @return attribute corresponding to attribute class {@code attributeClass}
   */
  public <A extends Attribute> A getAttribute(Class<A> attributeClass) {
    return (A) this.attributesMap.get(attributeClass);
  }

  /**
   * @return attribute value corresponding to attribute class {@code attributeClass} if it is
   * present in this element, otherwise return {@code defaultValue}
   */
  public <K> K getAttributeValue(Class<? extends Attribute<K>> attributeClass, K defaultValue) {
    Attribute<K> attribute = this.attributesMap.get(attributeClass);
    return attribute == null ? defaultValue : attribute.getValue();
  }

  /**
   * Remove the {@code attributeClass} from this element
   */
  public <A extends Attribute> void removeAttribute(Class<A> attributeClass) {
    this.attributesMap.removeKey(attributeClass);
  }

  /**
   * @return True if this element has attribute {@code attributeClass}
   */
  public <A extends Attribute> boolean hasAttribute(Class<A> attributeClass) {
    return this.getAttribute(attributeClass) != null;
  }

  /**
   * @return True if this element implements the {@code attribute} holder interface
   */
  public boolean isAllowedAttribute(Attribute attribute) {
    return attribute.getHolderInterface().isAssignableFrom(this.getClass());
  }

  /**
   * Method to add {@code attribute} in this element
   */
  public void addAttribute(Attribute attribute) {
    Class attributeClass = attribute.getClass();
    Attribute existingAttribute = this.attributesMap.get(attributeClass);
    if (existingAttribute != null) {
      throw new RuntimeException(
          "Attribute " + attribute.getName() + " already exists in element " + this.getName());
    }
    if (!this.isAllowedAttribute(attribute)) {
      throw new RuntimeException(
          "Attribute " + attribute.getName() + " not allowed in element " + this.getName());
    }
    this.attributesMap.put(attributeClass, attribute);
    attribute.setParentElement(this);
  }

  @Override
  public Element clone() {
    try {
      Element element = (Element) super.clone();
      element.attributesMap = Maps.mutable.empty();
      for (Attribute attribute : this.getAttributes()) {
        element.attributesMap.put(attribute.getClass(), attribute.clone(element));
      }
      this.currentClone = element;
      return this.currentClone;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Return iterable containing all elements that satisfies the predicate {@code elementPredicate}
   * under the tree where root is this element
   */
  public ElementIterable getContainingElements(Predicate<Element> elementPredicate) {
    return new ElementIterable(this, elementPredicate, IterationDirection.CONTAINING);
  }

  /**
   * Return iterable containing all elements whose class type is {@code acceptedElementClass} under
   * the tree where root is this element
   */
  public ElementIterable getContainingElements(Class<? extends Element> acceptedElementClass) {
    return new ElementIterable(this, elem -> elem.getClass().equals(acceptedElementClass),
        IterationDirection.CONTAINING);
  }

  public abstract List<Class<? extends ElementAttribute>> getDefaultLayout();

  public List<Class<? extends ElementAttribute>> getFinalLayout() {
    return this.hasAttribute(Layout.class) ? this.getAttribute(Layout.class).getValue()
        : this.getDefaultLayout();
  }

  /**
   * @return next sibling ot this element in document model tree structure
   */
  public Element getNextSibling() {
    if (this.elementListContext != null) {
      int index = this.getElementListIndex() + 1;
      List<Element> elements = this.getElementList().getElements();
      return index >= elements.size() ? null : elements.get(index);
    }
    return null;
  }

  /**
   * @return previous sibling of this element in document model tree structure
   */
  public Element getPreviousSibling() {
    if (this.elementListContext != null) {
      int index = this.getElementListIndex() - 1;
      List<Element> elements = this.getElementList().getElements();
      return index < 0 ? null : elements.get(index);
    }
    return null;
  }

  /**
   * @return parent element of this element in document model tree structure
   */
  public Element getParentElement() {
    return this.elementListContext == null ? null
        : this.elementListContext.getOne().getEnclosingAttribute().getParentElement();
  }

  /**
   * @return true if this element is leaf element in the document model tree structure
   */
  public boolean isTerminal() {
    return this.getFinalLayout().isEmpty();
  }

  @Override
  public String getTextStr() {
    MutableList<String> textStrList = Lists.mutable.empty();
    Element prevElement = null;
    String textStrDelimForNonInlineElems = this.getTextStrDelim().getOne();
    String textStrDelimForInlineElems = this.getTextStrDelim().getTwo();

    for (Class<? extends ElementAttribute> elementAttributeClass : this.getFinalLayout()) {
      if (this.hasAttribute(elementAttributeClass)) {
        ElementList<?> elementList = this.getAttribute(elementAttributeClass).getElementList();

        if (elementList.isPositionBased()) {
          for (Element element : elementList.getElements()) {
            if (element.isTextConvertible()) {
              if (prevElement != null) {
                textStrList.add(
                    PositionalElementList.compareByHorizontalAlignment(prevElement, element) == 0
                        ? INTRA_LINE_SEP : INTER_LINE_SEP);
              }
              textStrList.add(element.getTextStr());
              prevElement = element;
            }
          }
        } else {
          for (Element element : elementList.getElements()) {
            if (element.isTextConvertible()) {
              if (prevElement != null) {
                if (element.isInline() && prevElement.isInline()) {
                  if (!textStrDelimForInlineElems.isEmpty()) {
                    textStrList.add(textStrDelimForInlineElems);
                  }
                } else if (!textStrDelimForNonInlineElems.isEmpty()) {
                  textStrList.add(textStrDelimForNonInlineElems);
                }
              }
              textStrList.add(element.getTextStr());
              prevElement = element;
            }
          }
        }
      }
    }
    return textStrList.makeString("");
  }

  /**
   * @return text string delim for non-inline and inline elements
   */
  public Pair<String, String> getTextStrDelim() {
    return Tuples.pair(INTER_LINE_SEP, "");
  }

  /**
   * @return True if this element is inline.
   */
  public boolean isInline() {
    return false;
  }

  /**
   * @return True if this element can be converted to text
   */
  public boolean isTextConvertible() {
    return true;
  }

  /**
   * Utility method to compare the attribute values of two elements based on the predicate {@code
   * equalityCriterion} that takes two arguments, first being the value of this element and the
   * second being the value of {@code other}.
   */
  public <T, V extends Attribute<T>> boolean equalsAttributeValue(Element other,
      Class<V> attributeClass, Predicate2<T, T> equalityCriterion) {
    Attribute<T> attributeThis = this.getAttribute(attributeClass);
    Attribute<T> attributeOther = other.getAttribute(attributeClass);

    return (attributeThis == null) == (attributeOther == null)
        && (attributeThis == null || equalityCriterion.accept(attributeThis.getValue(),
        attributeOther
            .getValue())); // lgtm attributeOther cannot be null when we do getValue() as per the conditions
  }

  /**
   * Utility method to compare the attribute values of two elements based on the predicate {@code
   * equalityCriterion} that takes two arguments, first being the value of this element and the
   * second being the value of {@code other}. If attribute class is not found within this element or
   * {@code other}, then use the {@code defaultValue} as attribute value.
   */
  public <T, V extends Attribute<T>> boolean equalsAttributeValue(Element other,
      Class<V> attributeClass, Predicate2<T, T> equalityCriterion, T defaultValue) {
    Attribute<T> attributeThis = this.getAttribute(attributeClass);
    Attribute<T> attributeOther = other.getAttribute(attributeClass);
    T attributeThisVal = attributeThis == null ? defaultValue : attributeThis.getValue();
    T attributeOtherVal = attributeOther == null ? defaultValue : attributeOther.getValue();

    return attributeThisVal == attributeOtherVal || equalityCriterion
        .accept(attributeThisVal, attributeOtherVal);
  }

  /**
   * Construct the path of this element to the root element in the pdf structure hierarchy
   */
  public String getElementPath() {
    Pair<ElementList<Element>, Integer> context = this.getElementListContext();
    MutableList<String> contextPath = Lists.mutable.empty();
    while (context != null) {
      contextPath.add(String
          .format("%s-%d", context.getOne().getEnclosingAttribute().getClass().getSimpleName(),
              context.getTwo()));
      context = context.getOne().getEnclosingAttribute().getParentElement().getElementListContext();
    }
    contextPath.reverseThis();
    return String.join("_", contextPath);
  }

  /**
   * @return True if the input element has different visual style (font size, font family, color and
   * text style) than the self element
   */
  public boolean hasDifferentVisualStylesFromElement(Element other) {
    return !this.equalsAttributeValue(other, FontSize.class, (firstFontSize, nextFontSize) ->
        Math.abs(firstFontSize.getMagnitude() - nextFontSize.getMagnitude())
            < MAX_FONT_SIZE_CHANGE_RATIO * firstFontSize.getMagnitude())
        || !this.equalsAttributeValue(other, FontFamily.class,
        (firstFontFamily, nextFontFamily) -> firstFontFamily == null || firstFontFamily
            .equals(nextFontFamily))
        || !this
        .equalsAttributeValue(other, Color.class, java.awt.Color::equals, java.awt.Color.BLACK)
        || !this.equalsAttributeValue(other, TextStyles.class,
        (firstTextStyles, nextTextStyles) -> CollectionUtils
            .disjunction(nextTextStyles, firstTextStyles).isEmpty());
  }

  @Override
  public boolean equals(Object o) {
    if (this.isIdentityBased) {
      return this == o;
    }
    if (this == o) {
      return true;
    }
    if (!(o instanceof Element)) {
      return false;
    }

    Element element = (Element) o;

    if (!this.getClass().equals(element.getClass())) {
      return false;
    }
    return this.attributesMap.equals(element.attributesMap);
  }

  @Override
  public int hashCode() {
    if (this.isIdentityBased) {
      return this.getElementPath().hashCode();
    }
    int result = this.attributesMap.hashCode();
    result = 31 * result + this.getClass().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return this.getTextStr();
  }

}
