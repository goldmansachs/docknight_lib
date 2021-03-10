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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;

/**
 * Interface to define element list
 */
public interface ElementCollection<E extends Element> extends TextSerializable {

  MutableList<E> getElements();

  int size();

  E getFirst();

  E getLast();

  @Override
  default String getTextStr() {
    E prevElement = null;
    MutableList<String> textStrList = Lists.mutable.empty();
    for (E element : this.getElements()) {
      if (prevElement != null) {
        textStrList.add(
            PositionalElementList.compareByHorizontalAlignment(prevElement, element) == 0
                ? Element.INTRA_LINE_SEP : Element.INTER_LINE_SEP);
      }
      textStrList.add(element.getTextStr());
      prevElement = element;
    }
    return textStrList.makeString("");
  }

  /**
   * @return all vertical groups associated with elements and their children in this collection
   */
  default MutableList<ElementGroup<Element>> getEnclosingVerticalGroups() {
    return this.getElements().flatCollect(
        element -> element.getContainingElements(Element::isTerminal).withStartElement())
        .select(Element::hasPositionalContext)
        .collect(e -> e.getPositionalContext().getVerticalGroup()).distinct();
  }

  /**
   * @return True if there is an common element between this elmeent collection hierarchy and {@code
   * otherElementCollection} hierarchy
   */
  default boolean hasIntersectionWith(ElementCollection<E> otherElementCollection) {
    MutableList<Element> terminalElementsInThis = this.getElements().flatCollect(
        element -> element.getContainingElements(Element::isTerminal).withStartElement());
    MutableList<Element> terminalElementsInOther = otherElementCollection.getElements().flatCollect(
        element -> element.getContainingElements(Element::isTerminal).withStartElement());
    return terminalElementsInThis.anySatisfy(terminalElementsInOther::contains);
  }

  /**
   * @return True if collection has no element, otherwise return False
   */
  default boolean isEmpty() {
    return this.size() == 0;
  }

  /**
   * @return True if collection contains elements, otherwise return False
   */
  default boolean isNotEmpty() {
    return this.size() > 0;
  }
}
