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

package com.gs.ep.docknight.model.transformer.grouping;

import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.lazy.ReverseIterable;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.PositionalContext;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.context.PagePartitionType;
import com.gs.ep.docknight.model.element.FormElement;
import com.gs.ep.docknight.model.element.GraphicalElement;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.Rectangle;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.util.DateFormatConstants;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.awt.geom.Rectangle2D;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;
import ru.lanwen.verbalregex.VerbalExpression;

/**
 * Page Partitioner to partition the page into (header,content,footer) partitions
 */
public class PagePartitioner {

  private static final double PAGE_HEADER_RATIO = 0.15;
  private static final double PAGE_FOOTER_RATIO = 0.15;
  private static final double MAX_SPACE_BETWEEN_HEADER_ELEMENTS = 0.1;
  private static final double MAX_SPACE_BETWEEN_FOOTER_ELEMENTS = 0.1;
  private static final double MAX_HEADER_EXTREMITY = 0.1;
  private static final double MAX_FOOTER_EXTREMITY = 0.1;
  private static final double LEFT_SIMILARITY_THRESHOLD = 30;
  private static final double TOP_SIMILARITY_THRESHOLD = 30;
  private static final double HEIGHT_SIMILARITY_THRESHOLD = 2;
  private static final double PAGE_PARTITION_OFFSET = 2;
  private static final MutableList<Pattern> PAGE_FOOTER_PATTERNS;
  private static final int MAX_ELEMENTS_PER_LINE = 3;
  private static final double PARA_INTER_LINE_SPACE_VARIATION = 0.2;
  private static final double MAX_INTER_LINE_SPACE_TO_HEIGHT = 2;
  private static final int MAX_SUCCESSIVE_CLUSTER_SIZE = 2;

  private static final double EXTREMELY_SMALL_MARGIN_THRESHOLD = 45;
  private static final double RIGHT_ALIGNMENT_FACTOR = 2;
  private static final double CENTER_ALIGNMENT_VARIANCE = 0.1;
  private static final double CENTER_ALIGNMENT_MIN_MARGIN = 150;
  private static final double MAX_TABULAR_HEADER_SIZE = 0.2;

  private static final Pattern HEADER_FOOTER_PATTERN;

  static {
    MutableList<String> regexes = Lists.mutable.of(SemanticsChecker.URL_PATTERN.pattern())
        .withAll(DateFormatConstants.getAllRegexAndFormatTuples().collect(p -> "^" + p.getOne()));
    HEADER_FOOTER_PATTERN = Pattern.compile(
        VerbalExpression.regex().oneOf(regexes.toArray(new String[regexes.size()])).build()
            .toString());
    PAGE_FOOTER_PATTERNS = Lists.mutable.of(Pattern.compile("continues on next page"));
  }

  private final boolean allowContextAcrossPageBreaks;
  private final boolean excludeHeaderFooter;
  private boolean regexBasedHeaderFooterDetection;
  private boolean tableBasedHeaderFooterDetection;
  private boolean isPageNumberedDoc;

  public PagePartitioner(boolean allowContextAcrossPageBreaks, boolean excludeHeaderFooter) {
    this.allowContextAcrossPageBreaks = allowContextAcrossPageBreaks;
    this.excludeHeaderFooter = excludeHeaderFooter;
  }

  /**
   * Find index in list such that count of (element in (list(index:] if isReverse else
   * list[0,index)) which satisfies the {@code predicate}) > {@code reqSatisfiedElemCount}
   *
   * @param list list of elements
   * @param predicate predicate function to check on list of elements
   * @param reqSatisfiedElemCount count of elements in list which should satisfy the predicate
   * @param isReverse boolean to indicate traversal direction on list. If True, traverse from end
   * @return 0 if number of elements satisfying {@code predicate} is less than {@code
   * reqSatisfiedElemCount}, else return index in list such that count of (element in (list(index:]
   * if isReverse else list[0,index)) which satisfies the {@code predicate}) > {@code
   * reqSatisfiedElemCount}
   */
  private static <T> int detectSize(List<T> list, Predicate<T> predicate, int reqSatisfiedElemCount,
      boolean isReverse) {
    if (reqSatisfiedElemCount == 0) {
      return 0;
    }
    int satisfiedElemCount = 0;
    for (int i = 0; i < list.size(); i++) {
      if (predicate.accept(list.get(isReverse ? list.size() - 1 - i : i))) {
        satisfiedElemCount++;
      }
      if (satisfiedElemCount == reqSatisfiedElemCount) {
        return i + 1;
      }
    }
    return 0;
  }

  /**
   * Find common count corresponding to each partition. Common count is the count of elements that
   * are common in that partition and in its immediate previous partition.
   *
   * @param pagePartitions page paritions
   * @param commonCandidateFn function to retrieve header footer candidate elements
   * @param isFooter boolean indicating whether {@code commonCandidateFn} will retrieve footer
   * candidate elements or not
   * @return common count corresponding to each partition
   */
  private static MutableList<Integer> findCommonCountAcrossPartitions(
      MutableList<PagePartition> pagePartitions,
      Function<PagePartition, List<Element>> commonCandidateFn, boolean isFooter) {
    MutableList<Integer> finalCommonCountPerSubPage = Lists.mutable.empty();
    MutableList<Integer> commonCountPerSubPage = Lists.mutable.empty();
    MutableList<List<Element>> commonCandidatePerSubPage = pagePartitions
        .collect(commonCandidateFn);
    boolean newCommonSeqStarted = true;
    int commonCandidateCount;
    for (int i = 1; i < pagePartitions.size(); i++) {
      List<Element> prevPageCommonCandidates = commonCandidatePerSubPage.get(i - 1);
      List<Element> currPageCommonCandidates = commonCandidatePerSubPage.get(i);
      PagePartition prevPagePartition = pagePartitions.get(i - 1);
      int commonCount = findCommonCount(prevPageCommonCandidates, currPageCommonCandidates,
          prevPagePartition.topBoundary, pagePartitions.get(i).topBoundary);
      int prevCommonCount = newCommonSeqStarted ? 0 : commonCountPerSubPage.getLast();
      int ruleBasedMatchIndex = ListAdapter.adapt(prevPageCommonCandidates)
          .detectIndex(elem -> ruleBasedHeaderFooterMatcher(elem, isFooter)) + 1;
      commonCandidateCount = Math.max(Math.max(commonCount, prevCommonCount), ruleBasedMatchIndex);
      commonCandidateCount = commonCandidateCount - findCommonCountWithinPage(prevPagePartition,
          prevPageCommonCandidates.subList(0, commonCandidateCount), isFooter);
      finalCommonCountPerSubPage.add(commonCandidateCount);
      newCommonSeqStarted = prevCommonCount > commonCount;
      commonCountPerSubPage.add(commonCount);
    }
    commonCandidateCount = commonCountPerSubPage.isEmpty() ? findHeaderFooterRegexMatchCount(
        commonCandidatePerSubPage.getFirst()) : commonCountPerSubPage.getLast();
    commonCandidateCount = Math.max(commonCandidateCount, commonCandidatePerSubPage.isEmpty() ? 0
        : ListAdapter.adapt(commonCandidatePerSubPage.getLast())
            .detectIndex(elem -> ruleBasedHeaderFooterMatcher(elem, isFooter)) + 1);
    commonCandidateCount =
        commonCandidateCount - findCommonCountWithinPage(pagePartitions.getLast(),
            commonCandidatePerSubPage.getLast().subList(0, commonCandidateCount), isFooter);
    finalCommonCountPerSubPage.add(commonCandidateCount);
    return finalCommonCountPerSubPage;
  }

  /**
   * Find count of common elements between headerFooterElems and content elements within {@code
   * pagePartition}
   *
   * @param pagePartition partition of page
   * @param headerFooterElems header footer elements within page partition
   * @param isFooter boolean if {@code headerFooterElems} belongs to footer of the page
   * @return count of common elements
   */
  private static int findCommonCountWithinPage(PagePartition pagePartition,
      List<Element> headerFooterElems, boolean isFooter) {
    if (Iterate.isEmpty(headerFooterElems)) {
      return 0;
    }
    int commonCount = 0;
    List<Element> elements = pagePartition.elements;
    int boundaryIndex = ListAdapter.adapt(pagePartition.elements)
        .detectIndex(e -> e == headerFooterElems.get(headerFooterElems.size() - 1));
    MutableList<Element> pageNonCandidateElements =
        isFooter ? ListAdapter.adapt(elements.subList(0, boundaryIndex)) :
            ListAdapter.adapt(elements.subList(boundaryIndex + 1, elements.size()));
    MutableList<Element> elementsInSameLine = Lists.mutable.empty();
    for (int i = headerFooterElems.size() - 1; i >= 0; i--) {
      Element candidateElement = headerFooterElems.get(i);
      boolean isCandidateElementCollinear = elementsInSameLine.isEmpty() ? true
          : PositionalElementList
              .compareByHorizontalAlignment(candidateElement, elementsInSameLine.getLast()) == 0;
      if (elementsInSameLine.isEmpty() || isCandidateElementCollinear) {
        elementsInSameLine.add(candidateElement);
      }
      if (i == 0 || !isCandidateElementCollinear) {
        if (elementsInSameLine.size() > 1 && elementsInSameLine
            .allSatisfy(candidate -> candidate instanceof TextElement
                && pageNonCandidateElements
                .anySatisfy(e -> areElementsWithinPageVisuallySimilar(e, candidate)
                    && e.getTextStr().equals(candidate.getTextStr())))) {
          commonCount += elementsInSameLine.size();

          //start the next line
          elementsInSameLine = i == 0 ? Lists.mutable.empty() : Lists.mutable.of(candidateElement);
        } else {
          break;
        }
      }
    }
    return commonCount;
  }

  /**
   * Checks whether {@code elem} matches header footer patter or not
   *
   * @param elem Element which is being checked
   * @param isFooter boolean representing whether elem is part of footer
   * @return boolean flag indicating whether {@code elem} matches header footer pattern
   */
  private static boolean ruleBasedHeaderFooterMatcher(Element elem, boolean isFooter) {
    return isFooter && PAGE_FOOTER_PATTERNS
        .anySatisfy(pattern -> pattern.matcher(elem.getTextStr()).matches());
  }

  /**
   * Find the line containing the {@code elements}. If line satisfies header footer pattern, return
   * line elements size, else return 0
   *
   * @param elements header/footer elements
   * @return count of elements satisfying header footer pattern
   */
  private static int findHeaderFooterRegexMatchCount(List<Element> elements) {
    if (!elements.isEmpty()) {
      int lineEndIndex = ListIterate.detectIndex(elements,
          e -> PositionalElementList.compareByHorizontalAlignment(e, elements.get(0)) != 0);
      lineEndIndex = lineEndIndex < 0 ? elements.size() : lineEndIndex;
      String lineText = Iterate
          .makeString(elements.subList(0, lineEndIndex), Element.INTRA_LINE_SEP);
      return HEADER_FOOTER_PATTERN.matcher(lineText).find() ? lineEndIndex : 0;
    }
    return 0;
  }

  /**
   * Find count of similar elements from {@code first} and {@code second list} such that they won't
   * span incomplete lines
   *
   * @param first list of elements in first set
   * @param second list of elements in second set
   * @param firstTopOffset top coordinate of partition where elements in {@code first} are present
   * @param secondTopOffset top coordinate of partition where elements in {@code second} are
   * present
   * @return count of similar elements
   */
  public static int findCommonCount(List<Element> first, List<Element> second,
      double firstTopOffset, double secondTopOffset) {
    List<Pair<Element, Integer>> secondWithIndex = ListIterate
        .zipWithIndex(second, new LinkedList<>());
    MutableIntList matches = IntLists.mutable.empty();
    for (Element elemFromFirst : first) {
      boolean found = false;
      ListIterator<Pair<Element, Integer>> secondIterator = secondWithIndex.listIterator();
      while (secondIterator.hasNext()) {
        Pair<Element, Integer> elemAndIndexFromSecond = secondIterator.next();
        if (areElementsSimilar(elemFromFirst, elemAndIndexFromSecond.getOne(), firstTopOffset,
            secondTopOffset)) {
          matches.add(elemAndIndexFromSecond.getTwo());
          secondIterator.remove();
          found = true;
          break;
        }
      }
      if (!found) {
        break;
      }
    }

    // ensure that matches are continuous
    matches.sortThis();
    int commonCount = matches.isEmpty() ? 0 : 1;
    while (commonCount < matches.size()
        && matches.get(commonCount) == matches.get(commonCount - 1) + 1) {
      commonCount++;
    }

    // ensure that matches don't span incomplete lines
    if (commonCount > 0 && commonCount < first.size()) {
      while (commonCount > 0 && PositionalElementList
          .compareByHorizontalAlignment(first.get(commonCount), first.get(commonCount - 1)) == 0) {
        commonCount--;
      }
    }
    return commonCount;
  }

  /**
   * Checks whether {@code first} and {@code second} elements are visually similar and they are non
   * numerically invariant
   *
   * @param first first element
   * @param second second element
   * @param firstTopOffset top coordinate of partition where first element is present
   * @param secondTopOffset top coordinate of partition where second element is present
   * @return boolean flag indicating whether elements are similar
   */
  public static boolean areElementsSimilar(Element first, Element second, double firstTopOffset,
      double secondTopOffset) {
    return areElementsAcrossPagesVisuallySimilar(first, second, firstTopOffset, secondTopOffset)
        && SemanticsChecker
        .areStringsNonNumericallyInvariant(first.getTextStr(), second.getTextStr());
  }

  /**
   * Checks whether {@code first} and {@code second} elements are visually similar
   *
   * @param first first element
   * @param second second element
   * @param firstTopOffset top coordinate of partition where first element is present
   * @param secondTopOffset top coordinate of partition where second element is present
   * @return boolean flag indicating whether elements are visually similar
   */
  public static boolean areElementsAcrossPagesVisuallySimilar(Element first, Element second,
      double firstTopOffset, double secondTopOffset) {
    return doElementsHaveSimilarLeft(first, second) && doElementsHaveSimilarTop(first, second,
        firstTopOffset, secondTopOffset) && doElementsHaveSimilarHeight(first, second);
  }

  /**
   * Checks whether {@code first} and {@code second} elements are visually similar
   *
   * @param first first element
   * @param second second element
   * @return boolean flag indicating whether elements are visually similar
   */
  private static boolean areElementsWithinPageVisuallySimilar(Element first, Element second) {
    return doElementsHaveSameTextStyles(first, second) && doElementsHaveSimilarLeft(first, second)
        && doElementsHaveSimilarHeight(first, second);
  }

  /**
   * Checks whether {@code first} and {@code second} elements have similar height
   *
   * @param first first element
   * @param second second element
   * @return boolean flag indicating whether elements have similar height
   */
  private static boolean doElementsHaveSimilarHeight(Element first, Element second) {
    return first.equalsAttributeValue(second, Height.class,
        (x, y) -> Math.abs(x.getMagnitude() - y.getMagnitude()) < HEIGHT_SIMILARITY_THRESHOLD);
  }

  /**
   * Checks whether {@code first} and {@code second} elements have similar top. The offsets are used
   * to find top coordinate of element wrt partition top
   *
   * @param first first element
   * @param second second element
   * @param firstTopOffset top coordinate of partition where first element is present
   * @param secondTopOffset top coordinate of partition where second element is present
   * @return boolean flag indicating whether elements have similar top
   */
  private static boolean doElementsHaveSimilarTop(Element first, Element second,
      double firstTopOffset, double secondTopOffset) {
    return first.equalsAttributeValue(second, Top.class,
        (x, y) -> Math.abs(x.getMagnitude() - firstTopOffset - y.getMagnitude() + secondTopOffset)
            < TOP_SIMILARITY_THRESHOLD);
  }

  /**
   * Checks whether {@code first} and {@code second} elements have similar left
   *
   * @param first first element
   * @param second second element
   * @return boolean flag indicating whether elements have similar left
   */
  private static boolean doElementsHaveSimilarLeft(Element first, Element second) {
    return first.equalsAttributeValue(second, Left.class,
        (x, y) -> Math.abs(x.getMagnitude() - y.getMagnitude()) < LEFT_SIMILARITY_THRESHOLD);
  }

  /**
   * Checks whether {@code first} and {@code second} elements have similar text styles
   *
   * @param first first element
   * @param second second element
   * @return boolean flag indicating whether elements have similar text styles
   */
  private static boolean doElementsHaveSameTextStyles(Element first, Element second) {
    return first.equalsAttributeValue(second, TextStyles.class,
        (firstStyles, secondStyles) -> Sets.mutable.ofAll(firstStyles)
            .equals(Sets.mutable.ofAll(secondStyles)));
  }

  /**
   * Get the space between line where {@code first} element is present and the line where {@code
   * second} element is present.
   *
   * @param first element which is present in line1
   * @param second element which is present in line2
   * @param isReverse boolean if line1 is present below than line2 in document
   * @return space between the lines where {@code first} element and {@code second} element is
   * present.
   */
  private static double getInterLineSpace(Element first, Element second, boolean isReverse) {
    if (isReverse) {
      return getInterLineSpace(second, first, false);
    }
    double secondTop = second.getAttribute(Top.class).getMagnitude();
    double firstBottom =
        first.getAttribute(Top.class).getMagnitude() + first.getAttribute(Height.class)
            .getMagnitude();
    return secondTop - firstBottom;
  }

  /**
   * Checks whether the part of {@code str} represent page number in footer
   *
   * @param str footer text which is being checked
   * @return boolean flag indicating whether page number if present in footer text or not
   */
  private static boolean isPageNumber(String str) {
    boolean isNumber = false;
    for (String part : str.split("-")) {
      part = part.trim();
      isNumber = SemanticsChecker.isNumberInAnySystem(part);
      if (!isNumber && part.length() != 1 && !SemanticsChecker.hasAlphabets(part)) {
        return false;
      }
    }
    return isNumber;
  }

  public PagePartitioner withRegexBasedHeaderFooterDetection(
      boolean regexBasedHeaderFooterDetection) {
    this.regexBasedHeaderFooterDetection = regexBasedHeaderFooterDetection;
    return this;
  }

  public PagePartitioner withTableBasedHeaderFooterDetection(
      boolean tableBasedHeaderFooterDetection) {
    this.tableBasedHeaderFooterDetection = tableBasedHeaderFooterDetection;
    return this;
  }

  public PagePartitioner withPageNumberedDoc(boolean isPageNumberedDoc) {
    this.isPageNumberedDoc = isPageNumberedDoc;
    return this;
  }

  /**
   * Get all the partitions (Content, Header, Footer) of the {@code page}
   *
   * @param page page within document
   * @return partitions within {@code page}
   */
  public List<PagePartition> getPartitions(Page page) {
    PositionalElementList<Element> positionalElementList = page.getPositionalContent()
        .getElementList();
    double pageHeight = page.getHeight().getMagnitude();
    int numPageBreaks = positionalElementList.getNumberOfPageBreaks();
    int start = 0;
    int end =
        this.allowContextAcrossPageBreaks && !this.excludeHeaderFooter ? numPageBreaks + 1 : 1;
    MutableList<PagePartition> pagePartitions = Lists.mutable.empty();
    while (end <= numPageBreaks + 1) {
      List<Element> elements = positionalElementList.getElementsBetweenPageBreaks(start, end);
      double topBoundary =
          start > 0 ? positionalElementList.getPageBreak(start).getTop().getMagnitude() : 0;
      double bottomBoundary =
          end <= numPageBreaks ? positionalElementList.getPageBreak(end).getTop().getMagnitude()
              : pageHeight;
      pagePartitions
          .add(new PagePartition(elements, topBoundary, bottomBoundary, PagePartitionType.CONTENT));
      start = end;
      end++;
    }
    if (this.excludeHeaderFooter && (pagePartitions.size() > 1
        || this.regexBasedHeaderFooterDetection)) {
      return this.separateHeadersAndFootersAndCombineContent(pagePartitions,
          page.getWidth().getMagnitude());
    }
    return pagePartitions;
  }

  /**
   * Create a list of new partitions (header partition, footer partition, content partition) from
   * the existing {@code pageParitions}
   *
   * @param pagePartitions content partitions (without header / footer partitions)
   * @param pageWidth width of the page
   * @return new list of partitions
   */
  private List<PagePartition> separateHeadersAndFootersAndCombineContent(
      MutableList<PagePartition> pagePartitions, double pageWidth) {
    MutableList<Integer> headerSizePerSubPage = findCommonCountAcrossPartitions(pagePartitions,
        x -> this.getPageHeaderCandidate(x, pageWidth), false);
    MutableList<Integer> footerSizePerSubPage = findCommonCountAcrossPartitions(pagePartitions,
        x -> this.getPageFooterCandidate(x, pageWidth), true);
    MutableList<Element> content = Lists.mutable.empty();
    List<PagePartition> result = Lists.mutable.empty();
    if (this.allowContextAcrossPageBreaks) {
      result.add(new PagePartition(content, 0, pagePartitions.getLast().bottomBoundary,
          PagePartitionType.CONTENT));
    }
    for (int i = 0; i < pagePartitions.size(); i++) {
      PagePartition pagePartition = pagePartitions.get(i);
      int headerSize = detectSize(pagePartition.elements, e -> e instanceof Rectangle,
          headerSizePerSubPage.get(i), false);
      int footerSize = detectSize(pagePartition.elements, e -> e instanceof Rectangle,
          footerSizePerSubPage.get(i), true);
      int partitionSize = pagePartition.elements.size();
      double headerBottom = pagePartition.topBoundary;
      double footerTop = pagePartition.bottomBoundary;
      List<Element> partitionContent = pagePartition.elements
          .subList(headerSize, partitionSize - footerSize);
      if (headerSize > 0) {
        Element lastHeaderElem = pagePartition.elements.get(headerSize - 1);
        headerBottom = lastHeaderElem.getAttribute(Top.class).getMagnitude()
            + lastHeaderElem.getAttribute(Height.class).getMagnitude() + PAGE_PARTITION_OFFSET;
        result.add(new PagePartition(pagePartition.elements.subList(0, headerSize),
            pagePartition.topBoundary, headerBottom, PagePartitionType.HEADER));
      }
      if (footerSize > 0) {
        Element firstFooterElem = pagePartition.elements.get(partitionSize - footerSize);
        footerTop = firstFooterElem.getAttribute(Top.class).getMagnitude() - PAGE_PARTITION_OFFSET;
      }
      if (this.allowContextAcrossPageBreaks) {
        content.addAll(partitionContent);
      } else {
        result.add(new PagePartition(partitionContent, headerBottom, footerTop,
            PagePartitionType.CONTENT));
      }
      if (footerSize > 0) {
        result.add(new PagePartition(
            pagePartition.elements.subList(partitionSize - footerSize, partitionSize), footerTop,
            pagePartition.bottomBoundary, PagePartitionType.FOOTER));
      }
    }
    return result;
  }

  private List<Element> getPageHeaderCandidate(PagePartition pagePartition, double pageWidth) {
    return this.getPageHeaderFooterCandidate(pagePartition, pageWidth, false);
  }

  private List<Element> getPageFooterCandidate(PagePartition pagePartition, double pageWidth) {
    return this.getPageHeaderFooterCandidate(pagePartition, pageWidth, true);
  }

  /**
   * The basic algorithm for finding footer candidates is: <ol> <li>Find all elements that occur in
   * a certain lower region of the page.</li> <li> Cluster elements into groups based on criteria
   * like: <ol> <li> Whether they occur in lower extremities of the page </li> <li> Whether they are
   * divided by a graphical line </li> <li> Whether they are separated by large space </li> <li>
   * Whether the containing line has large number of elements (useful for tabular rows) </li> <li>
   * Whether they have different alignment </li> </ol> </li> <li> Select all clusters, till you find
   * one which is large or which goes across footer limits </li> </ol>
   *
   * @param pagePartition page partition
   * @param pageWidth with of the page
   * @param isFooter boolean to retrieve footer elements from the partition, else retrieve header
   * elements
   * @return list of header/footer elements from the {@code page partition}
   */
  private List<Element> getPageHeaderFooterCandidate(PagePartition pagePartition, double pageWidth,
      boolean isFooter) {
    double pageHeight = pagePartition.bottomBoundary - pagePartition.topBoundary;
    double ultimateThreshold =
        isFooter ? pagePartition.bottomBoundary - pageHeight * PAGE_FOOTER_RATIO
            : pagePartition.topBoundary + pageHeight * PAGE_HEADER_RATIO;
    double extremityThreshold =
        isFooter ? pagePartition.bottomBoundary - pageHeight * MAX_FOOTER_EXTREMITY
            : pagePartition.topBoundary + pageHeight * MAX_HEADER_EXTREMITY;
    MutableList<Element> result = Lists.mutable.empty();
    MutableList<Element> currLine = Lists.mutable.empty();
    MutableList<Element> prevLine = null;
    double interLineSpace = Integer.MAX_VALUE;
    MutableList<Integer> clusterChangeSizes = Lists.mutable
        .empty();  // represent indexes of element where cluster is changed
    Element elemAfterResultEnd = null;
    boolean graphicsInBetweenElem = false;
    double prevElemTop = isFooter ? pagePartition.topBoundary : pagePartition.bottomBoundary;
    boolean hasExtremityCluster = false;

    for (Element element : isFooter ? new ReverseIterable<>(
        ListAdapter.adapt(pagePartition.elements)) : pagePartition.elements) {
      if (element instanceof FormElement) {
        break;
      }
      if (element instanceof Rectangle) {
        if (currLine.notEmpty()
            && PositionalElementList.compareByHorizontalAlignment(currLine.getLast(), element)
            != 0) {
          if (graphicsInBetweenElem) {
            elemAfterResultEnd = element;
            break;
          }

          // If space between two lines is too much, add new cluster index to clusterChangeSizes
          interLineSpace = getInterLineSpace(currLine.getLast(), element, isFooter);
          if (interLineSpace >= element.getAttribute(Height.class).getMagnitude()
              * MAX_INTER_LINE_SPACE_TO_HEIGHT) {
            clusterChangeSizes.add(result.size());
          }

          if (prevLine == null) {
            // If current line top is within extremity threshold, add new cluster index to clusterChangeSizes
            double currLineTop = currLine.getLast().getAttribute(Top.class).getMagnitude();
            if (isFooter ? currLineTop > extremityThreshold : currLineTop < extremityThreshold) {
              clusterChangeSizes.add(result.size());
              hasExtremityCluster = true;
            }
          } else {
            // If alignments are not matching with previous line, add new cluster index to clusterChangeSizes
            if (Alignment.getAlignment(prevLine, pageWidth) != Alignment
                .getAlignment(currLine, pageWidth) ||
                isFooter
                    && prevLine.count(e -> SemanticsChecker.isNumberInAnySystem(e.getTextStr()))
                    == 1) {
              if (clusterChangeSizes.isEmpty()
                  || result.size() - currLine.size() >= clusterChangeSizes.getLast()) {
                clusterChangeSizes.add(result.size() - currLine.size());
              }
            }
          }

          prevLine = currLine;
          currLine = Lists.mutable.empty();
        } else if (currLine.reject(
            elem -> SemanticsChecker.PUNCTUATION_PATTERN.matcher(elem.getTextStr()).matches())
            .size() == MAX_ELEMENTS_PER_LINE) {
          result = result.subList(0, result.size() - currLine.size());
          break;
        }
        currLine.add(element);

        // If element is outside the threshold, set elemAfterResultEnd, otherwise, the current element is part header or footer
        double elemEnd = isFooter ? element.getAttribute(Top.class).getMagnitude()
            : element.getAttribute(Top.class).getMagnitude() + element.getAttribute(Height.class)
                .getMagnitude();
        double threshold = isFooter ? Math
            .max(prevElemTop - pageHeight * MAX_SPACE_BETWEEN_FOOTER_ELEMENTS, ultimateThreshold)
            : Math.min(prevElemTop + pageHeight * MAX_SPACE_BETWEEN_HEADER_ELEMENTS,
                ultimateThreshold);
        boolean isOutsideThreshold = isFooter ? elemEnd < threshold : elemEnd > threshold;

        if (isOutsideThreshold) {
          elemAfterResultEnd = element;
          break;
        }
        result.add(element);
        prevElemTop = elemEnd;
        graphicsInBetweenElem = false;
      } else if (element instanceof GraphicalElement) {
        graphicsInBetweenElem = true;
      }
    }

    int pageNumberIndex = -1;
    if (this.isPageNumberedDoc && isFooter) {
      pageNumberIndex = result.detectIndex(elem -> isPageNumber(elem.getTextStr()));
    }

    // one element lookahead for clusters to find if cluster goes across limits
    if (result.notEmpty() && elemAfterResultEnd != null) {
      double lastInterLineSpace = getInterLineSpace(result.getLast(), elemAfterResultEnd, isFooter);
      if (lastInterLineSpace < MAX_INTER_LINE_SPACE_TO_HEIGHT * elemAfterResultEnd
          .getAttribute(Height.class).getMagnitude()
          && Math.abs(lastInterLineSpace - interLineSpace) / interLineSpace
          < PARA_INTER_LINE_SPACE_VARIATION) {
        if (clusterChangeSizes.notEmpty()) {
          result = result.subList(0, clusterChangeSizes.getLast());
          clusterChangeSizes.remove(clusterChangeSizes.size() - 1);
        } else {
          result.clear();
        }
      }
    }

    // stop at first big cluster
    clusterChangeSizes.add(result.size());
    for (int i = hasExtremityCluster ? 2 : 1; i < clusterChangeSizes.size(); i++) {
      if (clusterChangeSizes.get(i) - clusterChangeSizes.get(i - 1) > MAX_SUCCESSIVE_CLUSTER_SIZE) {
        int ruleBasedMatchIndex = result
            .detectIndex(elem -> ruleBasedHeaderFooterMatcher(elem, isFooter));
        return result.subList(0, Math.max(Math.max(pageNumberIndex + 1, ruleBasedMatchIndex + 1),
            clusterChangeSizes.get(i - 1)));
      }
    }

    // extension for tabular headers/footers
    PositionalContext<Element> context =
        result.notEmpty() ? result.getLast().getPositionalContext() : null;
    Rectangle2D boundingBox = context != null ? context.getBoundingRectangle() : null;
    if (this.tableBasedHeaderFooterDetection && boundingBox != null
        && boundingBox.getHeight() < pageHeight * MAX_TABULAR_HEADER_SIZE) {
      Element e = result.getLast();
      while ((e = isFooter ? e.getPreviousSibling() : e.getNextSibling()) != null
          && (!(e instanceof Rectangle)
          || e.getPositionalContext().getBoundingRectangle() == boundingBox)) {
        if (e instanceof Rectangle) {
          result.add(e);
        }
      }
    }
    return result;
  }

  /**
   * Class to represent alignment of line
   */
  private enum Alignment {
    EXTREME_LEFT,
    LEFT,
    CENTER,
    RIGHT,
    EXTREME_RIGHT;

    /**
     * Determine alignment of {@code line}
     *
     * @param line Line whose alignment has to be calculated
     * @param pageWidth width of the page where the {@code line} is present
     * @return alignment type of {@code line}
     */
    private static Alignment getAlignment(MutableList<Element> line, double pageWidth) {
      double leftMargin = line.getFirst().getAttribute(Left.class).getMagnitude();
      double rightMargin =
          pageWidth - line.getLast().getAttribute(Left.class).getMagnitude() - line.getLast()
              .getAttribute(Width.class).getMagnitude();
      if (leftMargin < EXTREMELY_SMALL_MARGIN_THRESHOLD) {
        return EXTREME_LEFT;
      }
      if (rightMargin < EXTREMELY_SMALL_MARGIN_THRESHOLD) {
        return EXTREME_RIGHT;
      }
      if (leftMargin > RIGHT_ALIGNMENT_FACTOR * rightMargin) {
        return RIGHT;
      }
      if (leftMargin > CENTER_ALIGNMENT_MIN_MARGIN && rightMargin > CENTER_ALIGNMENT_MIN_MARGIN
          && Math.abs(rightMargin / leftMargin - 1) < CENTER_ALIGNMENT_VARIANCE) {
        return CENTER;
      }
      return LEFT;
    }
  }
}
