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
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Stack;

/**
 * Iterable to iterate over elements in DFS order
 */
public class ElementIterable implements Iterable<Element> {

  private final Predicate<Element> elementPredicate;
  private final Element startElement;
  private final IterationDirection iterationDirection;
  private boolean includeSelf;
  private Predicate<Element> breakCondition;

  public ElementIterable(Element startElement, Predicate<Element> elementPredicate,
      IterationDirection iterationDirection) {
    this.elementPredicate = elementPredicate;
    this.startElement = startElement;
    this.iterationDirection = iterationDirection;
    this.breakCondition = element -> false;
  }

  public ElementIterable withBreakCondition(Predicate<Element> breakCondition) {
    this.breakCondition = breakCondition;
    return this;
  }

  public ElementIterable withStartElement() {
    this.includeSelf = true;
    return this;
  }

  @Override
  public Iterator<Element> iterator() {
    return new ElementIterator(this.startElement, this.elementPredicate, this.breakCondition,
        this.iterationDirection, this.includeSelf);
  }

  public enum IterationDirection {
    CONTAINING,
    SUCCEEDING,
    PRECEDING
  }

  private static class ElementIterator implements Iterator<Element> {

    private final Predicate<Element> elementPredicate;
    private final Element startElement;
    private final boolean iterateInReverse;
    private final boolean excludeTopOfStartElement;
    private final boolean excludeBottomOfStartElement;
    private final Stack<ListIterator<Class<? extends ElementAttribute>>> layoutIterators;
    private final Predicate<Element> breakCondition;
    private Element nextElement;
    private Element currentElement;
    private boolean revisiting;

    ElementIterator(Element startElement, Predicate<Element> elementPredicate,
        Predicate<Element> breakCondition, IterationDirection iterationDirection,
        boolean includeSelf) {
      this.elementPredicate = elementPredicate;
      this.breakCondition = breakCondition;
      boolean breakAtStart = this.breakCondition.accept(startElement);
      this.nextElement =
          includeSelf && this.elementPredicate.accept(startElement) && !breakAtStart ? startElement
              : null;
      this.currentElement = includeSelf && breakAtStart ? null : startElement;
      this.startElement = startElement;
      this.layoutIterators = new Stack<>();
      this.iterateInReverse = iterationDirection.equals(IterationDirection.PRECEDING);
      this.excludeTopOfStartElement = iterationDirection.equals(IterationDirection.CONTAINING);
      this.excludeBottomOfStartElement = !iterationDirection.equals(IterationDirection.CONTAINING);
      this.revisiting = false;

      if (!this.excludeTopOfStartElement) {
        this.initializeLayoutIterators(startElement);
      }
    }

    private static Element getNextElementFromElementList(
        Pair<ElementList<Element>, Integer> elementListContext) {
      ListIterator<? extends Element> elementListIterator = elementListContext.getOne()
          .getElements().listIterator(elementListContext.getTwo() + 1);
      return elementListIterator.hasNext() ? elementListIterator.next() : null;
    }

    private static Element getPreviousElementFromElementList(
        Pair<ElementList<Element>, Integer> elementListContext) {
      ListIterator<? extends Element> elementListIterator = elementListContext.getOne()
          .getElements().listIterator(elementListContext.getTwo());
      return elementListIterator.hasPrevious() ? elementListIterator.previous() : null;
    }

    private void initializeLayoutIterators(Element startElement) {
      Pair<ElementList<Element>, Integer> elementListContext = startElement.getElementListContext();
      List<ListIterator<Class<? extends ElementAttribute>>> layoutIteratorsInReverse = Lists.mutable
          .empty();

      while (elementListContext != null) {
        ElementAttribute elementAttr = elementListContext.getOne().getEnclosingAttribute();
        Class<? extends ElementAttribute> elementAttrClass = elementAttr.getClass();
        Element parentElement = elementAttr.getParentElement();
        List<Class<? extends ElementAttribute>> layout = parentElement.getFinalLayout();
        ListIterator<Class<? extends ElementAttribute>> layoutIterator = layout
            .listIterator(layout.indexOf(elementAttrClass) + (this.iterateInReverse ? 0 : 1));
        layoutIteratorsInReverse.add(layoutIterator);
        elementListContext = parentElement.getElementListContext();
      }

      ListIterator<ListIterator<Class<? extends ElementAttribute>>> layoutIteratorsInReverseIterator = layoutIteratorsInReverse
          .listIterator(layoutIteratorsInReverse.size());
      while (layoutIteratorsInReverseIterator.hasPrevious()) {
        this.layoutIterators.push(layoutIteratorsInReverseIterator.previous());
      }
    }

    private void visitNextSiblingOrParent() {
      if (this.currentElement == this.startElement && this.excludeTopOfStartElement) {
        this.currentElement = null;
      } else {
        Pair<ElementList<Element>, Integer> elementListContext = this.currentElement
            .getElementListContext();
        if (elementListContext == null) {
          this.currentElement = null;
        } else {
          Element nextElemOrNull =
              this.iterateInReverse ? getPreviousElementFromElementList(elementListContext)
                  : getNextElementFromElementList(elementListContext);
          if (nextElemOrNull == null) {
            this.currentElement = elementListContext.getOne().getEnclosingAttribute()
                .getParentElement();
            this.revisiting = true;
          } else {
            this.currentElement = nextElemOrNull;
          }
        }
      }
    }

    private boolean visitFirstChildAtNextElemAttr(
        ListIterator<Class<? extends ElementAttribute>> layoutIterator) {
      boolean newCurrentElementFound = false;
      while (layoutIterator.hasNext() && !newCurrentElementFound) {
        Class<? extends ElementAttribute> elementAttributeClass = layoutIterator.next();
        if (this.currentElement.hasAttribute(elementAttributeClass)) {
          ElementList<? extends Element> elementList = this.currentElement
              .getAttribute(elementAttributeClass).getElementList();
          if (!elementList.isEmpty()) {
            this.layoutIterators.push(layoutIterator);
            this.currentElement = elementList.getFirst();
            newCurrentElementFound = true;
          }
        }
      }
      return newCurrentElementFound;
    }

    private boolean visitLastChildAtPrevElemAttr(
        ListIterator<Class<? extends ElementAttribute>> layoutIterator) {
      boolean newCurrentElementFound = false;
      while (layoutIterator.hasPrevious() && !newCurrentElementFound) {
        Class<? extends ElementAttribute> elementAttributeClass = layoutIterator.previous();
        if (this.currentElement.hasAttribute(elementAttributeClass)) {
          ElementList<? extends Element> elementList = this.currentElement
              .getAttribute(elementAttributeClass).getElementList();
          if (!elementList.isEmpty()) {
            this.layoutIterators.push(layoutIterator);
            this.currentElement = elementList.getLast();
            newCurrentElementFound = true;
          }
        }
      }
      return newCurrentElementFound;
    }

    private void visitNextElement() {
      if (this.revisiting) {
        this.revisiting = false;
        ListIterator<Class<? extends ElementAttribute>> layoutIterator = this.layoutIterators.pop();
        boolean newCurrentElementFound =
            this.iterateInReverse ? this.visitLastChildAtPrevElemAttr(layoutIterator)
                : this.visitFirstChildAtNextElemAttr(layoutIterator);
        if (!newCurrentElementFound) {
          this.visitNextSiblingOrParent();
        }
      } else if (this.currentElement.getFinalLayout().isEmpty() || (
          this.elementPredicate.accept(this.currentElement)
              && this.currentElement != this.startElement)) {
        this.visitNextSiblingOrParent();
      } else {
        if (this.currentElement == this.startElement && this.excludeBottomOfStartElement) {
          this.visitNextSiblingOrParent();
        } else {
          List<Class<? extends ElementAttribute>> layout = this.currentElement.getFinalLayout();
          boolean newCurrentElementFound = this.iterateInReverse ? this
              .visitLastChildAtPrevElemAttr(layout.listIterator(layout.size()))
              : this.visitFirstChildAtNextElemAttr(layout.listIterator());
          if (!newCurrentElementFound) {
            this.visitNextSiblingOrParent();
          }
        }
      }
    }

    @Override
    public boolean hasNext() {
      while (this.nextElement == null && this.currentElement != null) {
        this.visitNextElement();

        if (!this.revisiting && this.currentElement != null) {
          if (this.breakCondition.accept(this.currentElement)) {
            this.currentElement = null;
          } else if (this.elementPredicate.accept(this.currentElement)) {
            this.nextElement = this.currentElement;
          }
        }
      }
      return this.nextElement != null;
    }

    @Override
    public Element next() {
      if (this.nextElement == null && this.currentElement != null) {
        this.hasNext();
      }
      Element elem = this.nextElement;
      this.nextElement = null;
      return elem;
    }
  }
}
