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

import static com.googlecode.cqengine.query.QueryFactory.and;
import static com.googlecode.cqengine.query.QueryFactory.applyThresholds;
import static com.googlecode.cqengine.query.QueryFactory.ascending;
import static com.googlecode.cqengine.query.QueryFactory.between;
import static com.googlecode.cqengine.query.QueryFactory.descending;
import static com.googlecode.cqengine.query.QueryFactory.greaterThan;
import static com.googlecode.cqengine.query.QueryFactory.lessThan;
import static com.googlecode.cqengine.query.QueryFactory.or;
import static com.googlecode.cqengine.query.QueryFactory.orderBy;
import static com.googlecode.cqengine.query.QueryFactory.queryOptions;
import static com.googlecode.cqengine.query.QueryFactory.threshold;

import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.index.navigable.NavigableIndex;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.logical.Or;
import com.googlecode.cqengine.query.option.EngineThresholds;
import com.googlecode.cqengine.query.option.OrderByOption;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.googlecode.cqengine.query.option.Thresholds;
import com.googlecode.cqengine.query.simple.Between;
import com.googlecode.cqengine.resultset.ResultSet;
import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.function.Function2;
import org.eclipse.collections.api.block.function.Function3;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.block.predicate.Predicate2;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.partition.list.PartitionMutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.factory.SortedSets;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementGroup;
import com.gs.ep.docknight.model.LengthAttribute;
import com.gs.ep.docknight.model.ModelCustomizationKey;
import com.gs.ep.docknight.model.ModelCustomizations;
import com.gs.ep.docknight.model.PositionalContext;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.RectangleProperties;
import com.gs.ep.docknight.model.TabularCellElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup.GridType;
import com.gs.ep.docknight.model.Transformer;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.PageStructure;
import com.gs.ep.docknight.model.attribute.Stretch;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.context.PagePartitionType;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.Image;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.Rectangle;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.element.TextRectangle;
import com.gs.ep.docknight.model.element.VerticalLine;
import com.gs.ep.docknight.model.polygon.OpenRectangle;
import com.gs.ep.docknight.model.polygon.RectangleBuilder;
import com.gs.ep.docknight.model.polygon.RectangleFinder;
import com.gs.ep.docknight.model.polygon.RectilinearPolygon;
import com.gs.ep.docknight.model.transformer.grouping.PagePartition;
import com.gs.ep.docknight.model.transformer.grouping.PagePartitioner;
import com.gs.ep.docknight.util.SemanticsChecker;
import java.awt.geom.Rectangle2D;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PositionalTextGroupingTransformer implements Transformer<Document, Document> {

  private static final SimpleAttribute<Element, Double> TOP = simpleAttribute(Element.class,
      Double.class, "top",
      element -> element.getAttribute(Top.class).getMagnitude());

  private static final SimpleAttribute<Element, Double> BOTTOM = simpleAttribute(Element.class,
      Double.class, "bottom",
      element -> element.getAttribute(Top.class).getMagnitude() + element.getAttribute(Height.class)
          .getMagnitude());

  private static final SimpleAttribute<Element, Double> LEFT = simpleAttribute(Element.class,
      Double.class, "left",
      element -> element.getAttribute(Left.class).getMagnitude());

  private static final SimpleAttribute<Element, Double> RIGHT = simpleAttribute(Element.class,
      Double.class, "right",
      element -> element.getAttribute(Left.class).getMagnitude() + element.getAttribute(Width.class)
          .getMagnitude());

  private static final SimpleAttribute<Element, Double> HORIZONTAL_CENTRE = simpleAttribute(
      Element.class, Double.class, "horizontal_centre",
      element -> element.getAttribute(Left.class).getMagnitude()
          + element.getAttribute(Width.class).getMagnitude() / 2);

  private static final SimpleAttribute<Element, Double> VERTICAL_CENTRE = simpleAttribute(
      Element.class, Double.class, "vertical_centre",
      element -> element.getAttribute(Top.class).getMagnitude()
          + element.getAttribute(Height.class).getMagnitude() / 2);

  private static final SimpleAttribute<Element, Double> VERTICAL_END = simpleAttribute(
      Element.class, Double.class, "vertical_end",
      element -> element.getAttribute(Top.class).getMagnitude() + element
          .getAttribute(Stretch.class).getMagnitude());

  private static final SimpleAttribute<Element, Double> HORIZONTAL_END = simpleAttribute(
      Element.class, Double.class, "horizontal_end",
      element -> element.getAttribute(Left.class).getMagnitude() + element
          .getAttribute(Stretch.class).getMagnitude());

  private static final List<SimpleAttribute<Element, Double>> VERTICAL_ATTRS = Lists.mutable
      .of(TOP, BOTTOM, VERTICAL_CENTRE, VERTICAL_END);

  private static final int BORDER_LINE_ADJUST_EPSILON = 2;
  private static final int VERTICAL_TRAVERSAL_THRESHOLD = 2;
  private static final int HORIZONTAL_TRAVERSAL_THRESHOLD = 2;
  private static final double DEFAULT_MAX_LINE_HEIGHT_AND_DISTANCE_FACTOR = 2.0;
  private static final double MAX_LINE_HEIGHT_VARIANCE = 0.4;
  private static final double MAX_LINE_DISTANCE_VARIANCE = 0.5;
  private static final double ALIGNMENT_EPSILON = 7;                   // if any of points coordinates are lower than this threshold, then they are considered aligned
  private static final double SEPARATION_EPSILON = 1;                  // If points coordinates are lower than this threshold, then they are considered close.
  private static final double MAX_ALIGNMENT_LINE_HEIGHT_AND_DISTANCE_FACTOR = 5;
  private static final double HORIZONTAL_ALIGNMENT_EPSILON = 0.15;
  private static final double TABLE_SMALL_ELEM_MAX_SIZE = 8;
  private static final double TABLE_COL_FIT_ALLOWANCE = 4;
  private static final double TABLE_ROW_HEIGHT_VARIANCE = 0.2;
  private static final double TABLE_SEMANTIC_BREAK_THRESHOLD = 0.6;
  private static final double MIN_COLUMN_LEVEL_TABULAR_FITNESS = 0.5;
  private static final double MIN_ROW_LEVEL_TABULAR_FITNESS = 0.5;
  private static final int MAX_INTER_ROW_DISTANCE = 100;
  private static final int ACROSS_PAGE_BREAK_FACTOR_FOR_ROW_DISTANCE = 40;
  private static final int TABLE_ROW_DISTANCE_VARIANCE = 3;
  private static final int DIRECTION_NONE = 0;
  private static final int DIRECTION_LEFT = 1;
  private static final int DIRECTION_RIGHT = 2;
  private static final int DIRECTION_LEFT_RIGHT_BOTH = 3;
  private static final double CONTEXT_LIMIT = 1000;
  private static final double FONT_SIZE_TABLE_BREAK_FACTOR = 1.4;

  private static final Comparator<Double> DOUBLE_COMPARATOR_WO_EQUALITY = (x, y) -> x < y ? -1 : 1;
  private static final Logger LOGGER = LoggerFactory
      .getLogger(PositionalTextGroupingTransformer.class);
  private final MutableSet<Element> foundNoiseElements;
  private final boolean isGridBasedTableDetectionEnabled;
  // Customizable Parameters
  private final Function2<MutableList<String>, MutableList<String>, Double> semanticJumpCalculator;
  private final MutableList<Pattern> tabularNoisePatterns;
  private final boolean isTableDetectionDisabled;
  private final boolean disableHeaderFooterDetection;
  private final double maxLineHeightAndDistanceFactor;
  private final boolean detectUnderline;
  private final boolean isPageNumberedDoc;
  private final boolean useGridForTableExtent;
  private IndexedCollection<Element> boxedElementIndex;
  private IndexedCollection<Element> verticalLineIndex;
  private IndexedCollection<Element> horizontalLineIndex;
  private PagePartition pagePartition;
  private double pageWidth;

  public PositionalTextGroupingTransformer(ModelCustomizations customizations) {
    this.semanticJumpCalculator = customizations
        .retrieveOrDefault(ModelCustomizationKey.SEMANTIC_JUMP_CALCULATOR,
            (x, y) ->           // Calculates semantic jump between two lists x and y
            {
              Pair<Integer, Integer> xStats = getDigitAndNonDigitCount(x);
              Pair<Integer, Integer> yStats = getDigitAndNonDigitCount(y);
              int nonDigitIncrease = Math.max(yStats.getTwo() - xStats.getTwo(), 0);
              int digitDecrease = Math.max(xStats.getOne() - yStats.getOne(), 0);
              int minSemanticChange = nonDigitIncrease > 1 && digitDecrease > 1 ? Math
                  .min(nonDigitIncrease, digitDecrease) : 0;
              return (double) minSemanticChange / Math.min(x.size(), y.size());
            });
    this.tabularNoisePatterns = customizations
        .retrieveOrDefault(ModelCustomizationKey.TABULAR_NOISE_PATTERNS, Lists.mutable.empty());
    this.isTableDetectionDisabled = customizations
        .retrieveOrDefault(ModelCustomizationKey.DISABLE_TABLE_DETECTION, false);
    this.disableHeaderFooterDetection = customizations
        .retrieveOrDefault(ModelCustomizationKey.DISABLE_HEADER_FOOTER_DETECTION, false);
    this.maxLineHeightAndDistanceFactor = customizations
        .retrieveOrDefault(ModelCustomizationKey.POSITIONAL_GROUPING_MAX_DISTANCE_FACTOR,
            DEFAULT_MAX_LINE_HEIGHT_AND_DISTANCE_FACTOR);
    this.detectUnderline = customizations
        .retrieveOrDefault(ModelCustomizationKey.DETECT_UNDERLINE, false);
    this.foundNoiseElements = Sets.mutable.empty();
    this.isGridBasedTableDetectionEnabled = customizations
        .retrieveOrDefault(ModelCustomizationKey.ENABLE_GRID_BASED_TABLE_DETECTION, GridType.NONE)
        != GridType.NONE;
    this.isPageNumberedDoc = customizations
        .retrieveOrDefault(ModelCustomizationKey.IS_PAGE_NUMBERED_DOC, false);
    this.useGridForTableExtent = customizations
        .retrieveOrDefault(ModelCustomizationKey.ENABLE_GRID_BASED_TABLE_DETECTION, GridType.NONE)
        == GridType.ROW_AND_COL;
  }

  public PositionalTextGroupingTransformer() {
    this(new ModelCustomizations());
  }

  /**
   * Return a pair containing (count of digit elements, count of non digit elements) in {@code
   * list}
   *
   * @param list list of strings in which elements are being counted as digit or non-digit
   * @return (count of digit elements, count of non digit elements)
   */
  private static Pair<Integer, Integer> getDigitAndNonDigitCount(MutableList<String> list) {
    PartitionMutableList<String> partition = list.partition(s -> !SemanticsChecker.hasAlphabets(s));
    int digits = partition.getSelected().size();
    int nonDigits = partition.getRejected().count(s -> s.length() > 2);
    return Tuples.pair(digits, nonDigits);
  }

  /**
   * Create indexed collection over attributes representing boxed element
   *
   * @return indexed collection
   */
  private static IndexedCollection<Element> createBoxedElementIndex() {
    return createNavigableIndex(TOP, BOTTOM, LEFT, RIGHT, HORIZONTAL_CENTRE, VERTICAL_CENTRE);
  }

  /**
   * Create indexed collection over attributes representing vertical line
   *
   * @return indexed collection
   */
  private static IndexedCollection<Element> createVerticalLineIndex() {
    return createNavigableIndex(TOP, LEFT, VERTICAL_END);
  }

  /**
   * Create indexed collection over attributes representing horizontal line
   *
   * @return indexed collection
   */
  private static IndexedCollection<Element> createHorizontalLineIndex() {
    return createNavigableIndex(TOP, LEFT, HORIZONTAL_END);
  }

  /**
   * Create indexed collection over simpleAttributes. Indexed collection helps in retrieval of items
   * quickly based on conditions like between (range query), etc.
   *
   * @param attributes simple attributes
   * @return new indexed collection
   */
  public static <O, A extends Comparable> IndexedCollection<O> createNavigableIndex(
      SimpleAttribute<O, A>... attributes) {
    IndexedCollection<O> index = new ConcurrentIndexedCollection<>();
    for (SimpleAttribute<O, A> attribute : attributes) {
      index.addIndex(NavigableIndex.onAttribute(attribute));
    }
    return index;
  }

  /**
   * Creates a new SimpleAttribute
   *
   * @param objType type of object on which the attribute is being created
   * @param attrType type of attribute value present in {@code objType}
   * @param attrName name of the attribute field
   * @param attrFunction function that will be applied on object of type {@code objType} to retrieve
   * value of type {@code attrType}
   * @return created simpleAttribute
   */
  public static <O, A> SimpleAttribute<O, A> simpleAttribute(Class<O> objType, Class<A> attrType,
      String attrName, Function<O, A> attrFunction) {
    return new SimpleAttribute<O, A>(objType, attrType, attrName) {
      @Override
      public A getValue(O element, QueryOptions queryOptions) {
        return attrFunction.valueOf(element);
      }
    };
  }

  /**
   * Construct the intersection query such that there is an intersection between [lowerAttr value,
   * upperAttr value] and [lowerValue, upperValue)
   *
   * @param lowerAttr attribute for lower bound
   * @param upperAttr attribute for upper bound
   * @param lowerValue lower bound for query
   * @param upperValue upper bound for query
   * @return the constructed query
   */
  protected static <O> Or<O> intersection(SimpleAttribute<O, Double> lowerAttr,
      SimpleAttribute<O, Double> upperAttr, double lowerValue, double upperValue) {
    return intersection(lowerAttr, upperAttr, lowerValue, upperValue, Integer.MAX_VALUE);
  }

  private static <O> Or<O> intersection(SimpleAttribute<O, Double> lowerAttr,
      SimpleAttribute<O, Double> upperAttr, double lowerValue, double upperValue,
      double maxContext) {
    return or(between(lowerAttr, lowerValue, true, upperValue, false),
        and(between(lowerAttr, lowerValue - maxContext, false, lowerValue, true),
            betweenExclusive(upperAttr, lowerValue, lowerValue + maxContext)));
  }

  /**
   * Apply the between query such that outer bounds are not included in the range query
   */
  private static <O, A extends Comparable<A>> Between<O, A> betweenExclusive(
      SimpleAttribute<O, A> attribute, A lowerValue, A upperValue) {
    return between(attribute, lowerValue, false, upperValue, false);
  }

  /**
   * Apply the {@code query} on the {@code indexedCollection}. Results obtained should be sorted
   * using the option {@code orderByOption}
   *
   * @param indexedCollection indexed collection on which is query will be executed
   * @param query Query which will be executed
   * @param orderByOption Option on how to sort the result
   * @return results after performing the query
   */
  private static <O> ResultSet<O> retrieve(IndexedCollection<O> indexedCollection, Query<O> query,
      OrderByOption<O> orderByOption) {
    return retrieve(indexedCollection, query, orderByOption, true);
  }

  /**
   * Apply the {@code query} on the {@code indexedCollection}. Results obtained should be sorted
   * using the option {@code orderByOption}
   *
   * @param indexedCollection indexed collection on which is query will be executed
   * @param query Query which will be executed
   * @param orderByOption Option on how to sort the result
   * @param enableIndexOrdering boolean indicating which strategy to use. (if true, use index
   * ordering strategy, else use materialize ordering strategy
   * @return results after performing the query
   */
  private static <O> ResultSet<O> retrieve(IndexedCollection<O> indexedCollection, Query<O> query,
      OrderByOption<O> orderByOption, boolean enableIndexOrdering) {
    QueryOptions queryOptions = queryOptions(orderByOption);
    if (enableIndexOrdering && VERTICAL_ATTRS
        .contains(orderByOption.getAttributeOrders().get(0).getAttribute())) {
      Thresholds thresholds = applyThresholds(
          threshold(EngineThresholds.INDEX_ORDERING_SELECTIVITY, 1.0));
      queryOptions.put(thresholds.getClass(), thresholds);
    }
    return indexedCollection.retrieve(query, queryOptions);
  }

  /**
   * If all elements in {@possiblePrevTablesToCurTali} is subset of elements in {@code
   * firstRowWithVerticalGroupHeads}, then expansion is valid.
   *
   * @param firstRowWithVerticalGroupHeads elements in first row with header info
   * @param possiblePrevTablesToCurtail map with key table and value indicates the row index. [row
   * index,end] are row which might be curtailed from the table
   * @return boolean flag if expansion is valid
   */
  private static boolean isExpansionValid(List<MutableList<Element>> firstRowWithVerticalGroupHeads,
      MutableMap<TabularElementGroup<Element>, Integer> possiblePrevTablesToCurtail) {
    MutableList<Element> expansionElements = ListIterate
        .flatCollect(firstRowWithVerticalGroupHeads, elemList -> elemList);
    return possiblePrevTablesToCurtail.detect((table, lastRowNumToCurtail) ->
    {
      for (int currRowIndex = lastRowNumToCurtail; currRowIndex < table.numberOfRows();
          currRowIndex++) {
        if (table.getCells().get(currRowIndex).anySatisfy(
            cell -> cell.getElements().anySatisfy(elem -> !expansionElements.contains(elem)))) {
          return true;
        }
      }
      return false;
    }) == null;
  }

  /**
   * Return element list by merging the elements from {@code unionColumn} and {@code newColumn}. All
   * the elements in the merged list should lie above {@code possibleTableBottom}. Algo: Iterate
   * from top to bottom in both the columns. If the element from union column and new column's top
   * are very close, add new column element in place of union place element If the element from
   * union column and new column are vertically intersecting, add new column element in place of
   * union place element If the element from union column is appearing in higher position than
   * element from new column, add union column element. If the element from new column is appearing
   * in higher position than element from union column, add new column element.
   *
   * @param unionColumn union column
   * @param newColumn new column
   * @param possibleTableBottom possible bottom of table
   * @return elements of merged columns.
   */
  private static MutableList<Element> mergeColumns(MutableList<Element> unionColumn,
      MutableList<Element> newColumn, double possibleTableBottom) {
    MutableList<Element> newUnionColumn = Lists.mutable.empty();
    int i = 0;
    int j = 0;
    while (i < unionColumn.size() && j < newColumn.size()) {
      Element unionElement = unionColumn.get(i);
      Element newColumnElement = newColumn.get(j);
      double unionElementTop = unionElement.getAttribute(Top.class).getMagnitude();
      double newColumnElementTop = newColumnElement.getAttribute(Top.class).getMagnitude();
      double unionElementBottom =
          unionElementTop + unionElement.getAttribute(Height.class).getMagnitude();
      double newColumnElementBottom =
          newColumnElementTop + newColumnElement.getAttribute(Height.class).getMagnitude();
      if (unionElementTop > possibleTableBottom || newColumnElementTop > possibleTableBottom) {
        break;
      }
      if (Math.abs(unionElementTop - newColumnElementTop) < SEPARATION_EPSILON
          || newColumnElementTop > unionElementTop && newColumnElementTop < unionElementBottom
          || unionElementTop > newColumnElementTop && unionElementTop < newColumnElementBottom) {
        newUnionColumn.add(newColumnElement);
        i++;
        j++;
      } else if (unionElementTop < newColumnElementTop) {
        newUnionColumn.add(unionElement);
        i++;
      } else {
        newUnionColumn.add(newColumnElement);
        j++;
      }
    }

    while (i < unionColumn.size()) {
      Element unionElement = unionColumn.get(i);
      double unionElementTop = unionElement.getAttribute(Top.class).getMagnitude();
      if (unionElementTop > possibleTableBottom) {
        break;
      } else {
        newUnionColumn.add(unionElement);
        i++;
      }
    }

    while (j < newColumn.size()) {
      Element newColumnElement = newColumn.get(j);
      double newColumnElementTop = newColumnElement.getAttribute(Top.class).getMagnitude();
      if (newColumnElementTop > possibleTableBottom) {
        break;
      } else {
        newUnionColumn.add(newColumnElement);
        j++;
      }
    }
    return newUnionColumn;
  }

  /**
   * Check if {@code otherElement} and {@code element} are horizontally aligned.
   *
   * @param element first element
   * @param otherElement second element
   * @return boolean flag indicating whether elements are aligned
   */
  private static boolean isHorizontalAligning(Element element, Element otherElement) {
    if (otherElement instanceof TextElement) {
      PositionalContext<Element> positionalContext = otherElement.getPositionalContext();
      if (positionalContext.getBoundingRectangle() != element.getPositionalContext()
          .getBoundingRectangle() || positionalContext.getTabularGroup() != null) {
        return false;
      }
      double top = element.getAttribute(Top.class).getMagnitude();
      double height = element.getAttribute(Height.class).getMagnitude();
      double bottom = top + height;
      double otherTop = otherElement.getAttribute(Top.class).getMagnitude();
      double otherHeight = otherElement.getAttribute(Height.class).getMagnitude();
      double otherBottom = otherTop + otherHeight;
      double left = element.getAttribute(Left.class).getMagnitude();
      double otherLeft = otherElement.getAttribute(Left.class).getMagnitude();
      double dis = Math.abs(left - otherLeft);
      if (Math.abs(top - otherTop) / dis > HORIZONTAL_ALIGNMENT_EPSILON
          && Math.abs(bottom - otherBottom) / dis > HORIZONTAL_ALIGNMENT_EPSILON) {
        return isVerticalGroupHorizontalAligning(element, otherElement);
      }
      return !(Math.abs(otherHeight / height - 1) > TABLE_ROW_HEIGHT_VARIANCE);
    }
    return otherElement == null;
  }

  /**
   * Check whether vertical groups of {@code element} and {@code otheElement} are horizontally
   * aligned
   *
   * @param element first element
   * @param otherElement second element
   * @return boolean flag indicating whether vertical groups of elements are aligned
   */
  private static boolean isVerticalGroupHorizontalAligning(Element element, Element otherElement) {
    RectangleProperties<Double> verticalGroupTextBoundingBox = element.getPositionalContext()
        .getVerticalGroup().getTextBoundingBox();
    RectangleProperties<Double> otherVerticalGroupTextBoundingBox = otherElement
        .getPositionalContext().getVerticalGroup().getTextBoundingBox();

    double verticalCenter =
        (verticalGroupTextBoundingBox.getTop() + verticalGroupTextBoundingBox.getBottom()) / 2.0;
    double otherVerticalCenter =
        (otherVerticalGroupTextBoundingBox.getTop() + otherVerticalGroupTextBoundingBox.getBottom())
            / 2.0;
    double left = verticalGroupTextBoundingBox.getLeft();
    double otherLeft = otherVerticalGroupTextBoundingBox.getLeft();
    double dis = Math.abs(left - otherLeft);
    return !(Math.abs(verticalCenter - otherVerticalCenter) / dis > HORIZONTAL_ALIGNMENT_EPSILON);
  }

  /**
   * Get the visual left of the table. If there is visual left border, return visual left in
   * positional context. If left element is not null, return right coordinate of left element. Find
   * all elements from below elements having width > TABLE_SMALL_ELEM_MAX_SIZE and select max width
   * if such element exists else get visual left from positional context.
   *
   * @param left left coordinate of element
   * @param elementPositionalContext positional context of element
   * @param leftElement left element of element
   * @return visual left of the table
   */
  private static double getVisualLeftForTable(double left,
      PositionalContext<Element> elementPositionalContext, Element leftElement) {
    return elementPositionalContext.isVisualLeftBorder() ? elementPositionalContext.getVisualLeft()
        : leftElement == null ?
            elementPositionalContext.getBelowElements().getElements()
                .select(e -> e.getAttribute(Width.class).getMagnitude() > TABLE_SMALL_ELEM_MAX_SIZE)
                .collectDouble(
                    e -> e.getAttribute(Left.class).getMagnitude() + e.getAttribute(Width.class)
                        .getMagnitude())
                .select(r -> r < left).maxIfEmpty(elementPositionalContext.getVisualLeft()) :
            leftElement.getAttribute(Left.class).getMagnitude() + leftElement
                .getAttribute(Width.class).getMagnitude();
  }

  /**
   * Get the visual right of the table. If there is visual right border, return visual right in
   * positional context. If right element is not null, return right coordinate of right element.
   * Find all elements from below elements having width > TABLE_SMALL_ELEM_MAX_SIZE and select max
   * width if such element exists else get visual right from positional context.
   *
   * @param right right coordinate of element
   * @param elementPositionalContext positional context of element
   * @param rightElement right element of element
   * @return visual right of the table
   */
  private static double getVisualRightForTable(double right,
      PositionalContext<Element> elementPositionalContext, Element rightElement) {
    return elementPositionalContext.isVisualRightBorder() ? elementPositionalContext
        .getVisualRight() : rightElement == null ?
        elementPositionalContext.getBelowElements().getElements()
            .select(e -> e.getAttribute(Width.class).getMagnitude() > TABLE_SMALL_ELEM_MAX_SIZE)
            .collectDouble(e -> e.getAttribute(Left.class).getMagnitude())
            .select(l -> l > right).minIfEmpty(elementPositionalContext.getVisualRight())
        : rightElement.getAttribute(Left.class).getMagnitude();
  }

  /**
   * Check if element is horizontally intersecting with range [columnLeft, columnRight] and width of
   * intersecting range is grater than TABLE_SMALL_ELEM_MAX_SIZE
   *
   * @param columnLeft left coordinate of column
   * @param columnRight right coordinate of column
   * @param elem element which is to be checked
   * @return boolean flag if it is intersecting with table column horizontally
   */
  private static boolean isTabularIntersecting(double columnLeft, double columnRight,
      Element elem) {
    if (elem != null) {
      double elem2Width = elem.getAttribute(Width.class).getMagnitude();
      if (elem2Width > TABLE_SMALL_ELEM_MAX_SIZE) {
        double elem2Left = elem.getAttribute(Left.class).getMagnitude();
        return Math.min(columnRight, elem2Left + elem2Width)
            > Math.max(columnLeft, elem2Left) + TABLE_SMALL_ELEM_MAX_SIZE;
      }
    }
    return false;
  }

  /**
   * Retrieve the first result from the {@code resultSet} if exists, else retrieve null
   *
   * @param resultSet Result set whose element will be retrieved
   * @return the first result from the {@code resultSet} if exists, else return null
   */
  private static <O> O retrieveOnlyOneResultElseNull(ResultSet<O> resultSet) {
    Iterator<O> iterator = resultSet.iterator();
    return iterator.hasNext() ? iterator.next() : null;
  }

  /**
   * Helper function to find the visual edge by executing query on borderLineIndex
   *
   * @param visualEdgeDefault default value of visual edge to return in case no result is fetched
   * @param edgeElementBoundary boundary coordinate of neighbouring/edge element
   * @param isEdgeElementBoundaryAligning boolean flag indicating whether edge element boundary is
   * aligned
   * @param elementBoundary boundary coordinate of current element
   * @param isElementBoundaryAligning boolean flag indicating whether current element boundary is
   * aligned
   * @param axisAttrClass attribute class whose fetched value from result will be used as visual
   * edge
   * @param axisQueryForBorderLinesForElem axis query to search for border lines
   * @param otherQueryForBorderLinesForElem other query to search for border lines
   * @param axisQueryForBorderLinesForEdgeElem axis query to include in case edge element boundary >
   * 0
   * @param borderLinesOrdering option to sort the retrieved result
   * @param borderLineIndex indexed collection on which to execute the query
   * @return pair of visual edge and boolean flag if the edge is border based (extracted from
   * visible line)
   */
  private static Pair<Double, Boolean> findVisualEdge(
      double visualEdgeDefault,
      double edgeElementBoundary,
      boolean isEdgeElementBoundaryAligning,
      double elementBoundary,
      boolean isElementBoundaryAligning,
      Class<? extends LengthAttribute> axisAttrClass,
      Query<Element> axisQueryForBorderLinesForElem,
      Query<Element> otherQueryForBorderLinesForElem,
      Query<Element> axisQueryForBorderLinesForEdgeElem,
      OrderByOption<Element> borderLinesOrdering,
      IndexedCollection<Element> borderLineIndex) {
    double visualEdge = visualEdgeDefault;
    boolean isBorderBased = false;
    Query<Element> queryForBorderLines = and(axisQueryForBorderLinesForElem,
        otherQueryForBorderLinesForElem);
    if (edgeElementBoundary > 0) {
      if (isEdgeElementBoundaryAligning == isElementBoundaryAligning) {
        visualEdge = Math.min(edgeElementBoundary, elementBoundary)
            + Math.abs(elementBoundary - edgeElementBoundary) / 2;
      } else {
        visualEdge = isElementBoundaryAligning ? elementBoundary : edgeElementBoundary;
      }
      queryForBorderLines = and(queryForBorderLines, axisQueryForBorderLinesForEdgeElem);
    }

    ResultSet<Element> results = retrieve(borderLineIndex, queryForBorderLines,
        borderLinesOrdering);
    Iterator<Element> resultsIterator = results.iterator();
    if (resultsIterator.hasNext()) {
      visualEdge = resultsIterator.next().getAttribute(axisAttrClass).getMagnitude();
      isBorderBased = true;
    }
    return Tuples.pair(visualEdge, isBorderBased);
  }

  private static int compareByHorizontalAlignment(Element prevToPrevElement,
      Element previousElement, Element element) {
    Element comparableElement =
        prevToPrevElement != null && calculateHorizontalIntersection(element, previousElement) == 0
            &&
            calculateHorizontalIntersection(element, prevToPrevElement) > 0 ? prevToPrevElement
            : previousElement;
    return PositionalElementList.compareByHorizontalAlignment(comparableElement, element);
  }

  /**
   * Calculate horizontal intersection between {@code element} and {@code otherElement}
   *
   * @param element first element
   * @param otherElement second element
   * @return horizontal intersection between {@code element} and {@code otherElement}
   */
  private static double calculateHorizontalIntersection(Element element, Element otherElement) {
    double elementStart = element.getAttribute(Left.class).getMagnitude();
    double elementEnd = elementStart + element.getAttribute(Width.class).getMagnitude();
    double otherElementStart = otherElement.getAttribute(Left.class).getMagnitude();
    double otherElementEnd =
        otherElementStart + otherElement.getAttribute(Width.class).getMagnitude();
    return Math
        .max(Math.min(elementEnd, otherElementEnd) - Math.max(elementStart, otherElementStart), 0);
  }

  /**
   * Check if element horizontal coordinates (abscissa) is intersecting with range (left, right]
   *
   * @param element Element whose horizontal coordinates have to be checked
   * @param left left coordinate of range
   * @param right right coordinate of range
   * @return boolean flag indicating whether element is horizontally intersecting with range [left,
   * right]
   */
  private static boolean isHorizontallyIntersecting(Element element, double left, double right) {
    if (element != null) {
      double elemLeft = element.getAttribute(Left.class).getMagnitude();
      double elemRight = elemLeft + element.getAttribute(Width.class).getMagnitude();
      return Math.min(elemRight, right) > Math.max(elemLeft, left);
    }
    return false;
  }

  /**
   * If there is a single text element previous to input {@code line} such that the text element
   * lies completely within the input {@code line}, then update the alignment for that text element
   *
   * @param line horizontal line
   */
  private static void findAlignmentWithHorizontalLine(Element line) {
    List<? extends Element> elements = line.getElementListContext().getOne().getElements();
    double left = line.getAttribute(Left.class).getMagnitude();
    double right = left + line.getAttribute(Stretch.class).getMagnitude();
    Element prevElem = null;
    Element aligningElem = null;
    for (int i = line.getElementListContext().getTwo() - 1; i >= 0; i--) {
      Element elem = elements.get(i);
      if (elem instanceof TextElement) {
        if (prevElem != null
            && PositionalElementList.compareByHorizontalAlignment(elem, prevElem) != 0) {
          break;
        }
        double elemLeft = elem.getAttribute(Left.class).getMagnitude();
        double elemRight = elemLeft + elem.getAttribute(Width.class).getMagnitude();
        if (elemLeft >= left && elemRight <= right) {
          if (aligningElem != null) {
            aligningElem = null;
            break;
          }
          aligningElem = elem;
        }
        prevElem = elem;
      }
    }

    if (aligningElem != null) {
      PositionalContext<Element> context = aligningElem.getPositionalContext();
      context.setAlignmentLeft(left);
      context.setAlignmentRight(right);
    }
  }

  @Override
  public Document transform(Document document) {
    LOGGER.info("[{}][{}] Grouping Text elements in document.", document.getDocumentSource(),
        this.getClass().getSimpleName());
    long startTime = System.currentTimeMillis();

    boolean allowContextAcrossPageBreaks = !document
        .getAttributeValue(PageStructure.class, PageStructure.FLOW_PAGE_BREAK)
        .equals(PageStructure.NO_FLOW_PAGE_BREAK);
    PagePartitioner pagePartitioner = new PagePartitioner(allowContextAcrossPageBreaks,
        !this.disableHeaderFooterDetection);
    pagePartitioner.withRegexBasedHeaderFooterDetection(true)
        .withTableBasedHeaderFooterDetection(true).withPageNumberedDoc(this.isPageNumberedDoc);
    for (Element docElement : document.getContent().getElementList().getElements()) {
      Page page = (Page) docElement;
      PositionalElementList<Element> positionalElementList = page.getPositionalContent().getValue();
      this.verticalLineIndex = createVerticalLineIndex();
      this.horizontalLineIndex = createHorizontalLineIndex();
      this.boxedElementIndex = createBoxedElementIndex();
      MutableSet<Element> horizontalLinesForRectilinearPolygons = Sets.mutable.empty();
      MutableSet<Element> verticalLinesForRectilinearPolygons = Sets.mutable.empty();
      positionalElementList.getElements().forEach(e ->
      {
        e.withIdentity(true);
        if (e instanceof Rectangle) {
          positionalElementList.initializeContext(e);
          this.boxedElementIndex.add(e);
        } else if (e instanceof VerticalLine) {
          this.verticalLineIndex.add(e);
          verticalLinesForRectilinearPolygons.add(e);
        } else if (e instanceof HorizontalLine) {
          this.horizontalLineIndex.add(e);
          horizontalLinesForRectilinearPolygons.add(e);
        }
      });
      positionalElementList.getElements().forEach(e ->
      {
        if (e instanceof HorizontalLine) {
          Rectangle2D rectangle = this.findRectangle(e, false);
          if (rectangle != null) {
            horizontalLinesForRectilinearPolygons
                .removeAll(this.findContainedHorizontalLines(rectangle));
            verticalLinesForRectilinearPolygons
                .removeAll(this.findContainedVerticalLines(rectangle));
          }
        }
      });
      List<PagePartition> pagePartitions = pagePartitioner.getPartitions(page);
      this.pageWidth = page.getWidth().getMagnitude();

      for (PagePartition pagePartition : pagePartitions) {
        this.boxedElementIndex = createBoxedElementIndex();
        this.pagePartition = pagePartition;

        for (Element elem : this.pagePartition.elements) {
          if (elem instanceof TextRectangle) {
            this.boxedElementIndex.add(elem);
            elem.getPositionalContext().setPagePartitionType(this.pagePartition.partitionType);
          }
        }

        for (Element elem : this.pagePartition.elements) {
          if (elem instanceof Image) {
            if (!this.isBackGroundImage(elem)) {
              this.boxedElementIndex.add(elem);
            }
            elem.getPositionalContext().setPagePartitionType(this.pagePartition.partitionType);
          }
        }

        for (Element elem : this.pagePartition.elements) {
          if (elem instanceof Rectangle && elem.getPositionalContext().getAlignmentRight() == 0) {
            this.findAlignmentGroup(elem);
          }
          if (elem instanceof HorizontalLine && !this.isTableDetectionDisabled) {
            findAlignmentWithHorizontalLine(elem);
          }
        }

        if (!this.isTableDetectionDisabled
            && this.pagePartition.partitionType == PagePartitionType.CONTENT) {
          this.findRectilinearPolygons(horizontalLinesForRectilinearPolygons,
              verticalLinesForRectilinearPolygons);
        }

        for (Element elem : this.pagePartition.elements) {
          if (elem instanceof Rectangle) {
            this.populatePositionalContext(elem);
          } else if (elem instanceof HorizontalLine) {
            this.findRectangle(elem, true);
          }
        }
        for (Element elem : this.pagePartition.elements) {
          if (elem instanceof Rectangle && elem.getPositionalContext().getVerticalGroup() == null) {
            this.findVerticalGroup(elem);
          }
        }

        if (!this.isTableDetectionDisabled) {
          for (Element elem : this.pagePartition.elements) {
            PositionalContext<Element> context = elem.getPositionalContext();
            if (elem instanceof TextElement && context.getTabularGroup() == null
                && (context.getPagePartitionType() == null
                || context.getPagePartitionType() == PagePartitionType.CONTENT)) {
              this.findTabularGroup(elem);
            }
          }
        }
      }
    }
    // document.getContainingElements(e -> e instanceof TextElement).forEach(element -> ((TextElement) element).addAsConcept(document.getConceptBase()));

    float timeTaken = (System.currentTimeMillis() - startTime) / 1000.0f;
    LOGGER.info("[{}][{}][{}s] Returning Text element groups found in document.",
        document.getDocumentSource(), this.getClass().getSimpleName(), timeTaken);

    return document;
  }

  /**
   * Checks if the {@code elem} is a background image or not. If the existing text element area
   * overlaps with this element's area then {@code elem} is a background image.
   *
   * @param elem Element to be checked
   * @return boolean flag indicating whether
   */
  private boolean isBackGroundImage(Element elem) {
    double top = elem.getAttribute(Top.class).getMagnitude();
    double bottom = top + elem.getAttribute(Height.class).getMagnitude();
    double left = elem.getAttribute(Left.class).getMagnitude();
    double right = left + elem.getAttribute(Width.class).getMagnitude();
    for (Element textElement : this.boxedElementIndex
        .retrieve(betweenExclusive(TOP, top, bottom))) {
      double textLeft = textElement.getAttribute(Left.class).getMagnitude();
      if (textLeft >= left && textLeft < right || left > textLeft && left < textLeft + textElement
          .getAttribute(Width.class).getMagnitude()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Populate the positional context with neighbouring element information
   *
   * @param elem element whose positional context is to be populated.
   */
  private void populatePositionalContext(Element elem) {
    double top = elem.getAttribute(Top.class).getMagnitude();
    double height = elem.getAttribute(Height.class).getMagnitude();
    double left = elem.getAttribute(Left.class).getMagnitude();
    double width = elem.getAttribute(Width.class).getMagnitude();
    double bottom = top + height;
    double right = left + width;

    PositionalContext<Element> positionalContext = elem.getPositionalContext();
    double alignmentLeft = positionalContext.getAlignmentLeft();
    double alignmentRight = positionalContext.getAlignmentRight();

    Element shadowLeftElement = this.findShadowLeftElement(top, bottom, left);
    positionalContext.setShadowedLeftElement(shadowLeftElement);

    Element shadowRightElement = this.findShadowRightElement(top, bottom, right);
    positionalContext.setShadowedRightElement(shadowRightElement);

    Pair<Double, Boolean> visualLeftInfo = this
        .findVisualLeft(top, bottom, left, alignmentLeft, shadowLeftElement);
    double visualLeft = visualLeftInfo.getOne();
    positionalContext.setVisualLeftBorder(visualLeftInfo.getTwo());
    positionalContext.setVisualLeft(visualLeft);

    Pair<Double, Boolean> visualRightInfo = this
        .findVisualRight(top, bottom, right, alignmentRight, shadowRightElement);
    double visualRight = visualRightInfo.getOne();
    positionalContext.setVisualRightBorder(visualRightInfo.getTwo());
    positionalContext.setVisualRight(visualRight);

    Element shadowedBelowElement = this
        .findShadowedBelowElement(top, visualLeft, visualRight, left, right);
    positionalContext.setShadowedBelowElement(shadowedBelowElement);

    Element shadowedAboveElement = this
        .findShadowedAboveElement(bottom, visualLeft, visualRight, left, right);
    positionalContext.setShadowedAboveElement(shadowedAboveElement);

    Pair<Double, Boolean> visualTopInfo = this
        .findVisualTop(top, left, right, shadowedAboveElement);
    double visualTop = visualTopInfo.getOne();
    positionalContext.setVisualTopBorder(visualTopInfo.getTwo());
    positionalContext.setVisualTop(visualTop);

    Pair<Double, Boolean> visualBottomInfo = this
        .findVisualBottom(bottom, left, right, shadowedBelowElement);
    double visualBottom = visualBottomInfo.getOne();
    positionalContext.setVisualBottomBorder(visualBottomInfo.getTwo());
    positionalContext.setVisualBottom(visualBottom);

    positionalContext.setBelowElements(this.findBelowElements(top, visualLeft, visualRight));
    positionalContext.setAboveElements(this.findAboveElements(bottom, visualLeft, visualRight));
    positionalContext.setRightElements(this.findRightElements(right, visualTop, visualBottom));
    positionalContext.setLeftElements(this.findLeftElements(left, visualTop, visualBottom));
  }

  /**
   * Find tabular group such that {@code elem} is present in first row
   *
   * @param elem element for which tabular group has to be found.
   */
  private void findTabularGroup(Element elem) {
    Element rightElement = elem.getPositionalContext().getShadowedRightElement();
    if (rightElement != null && isHorizontalAligning(elem, rightElement)) {
      Column startingColumn = this.findColumnElements(elem, false);
      if (startingColumn.elements.size() > 1 && startingColumn.direction != DIRECTION_NONE) {
        RectangleProperties<Double> tableBoundary = this.findTableBoundary(startingColumn);
        if (tableBoundary.getBottom() < tableBoundary.getTop()
            || tableBoundary.getRight() < tableBoundary.getLeft()) {
          return;
        }
        Rectangle2D boundingRect = elem.getPositionalContext().getBoundingRectangle();
        if (boundingRect != null && this.useGridForTableExtent
            && tableBoundary.getBottom() < boundingRect.getMaxY()) {
          tableBoundary.setBottom(boundingRect.getMaxY());
        }
        Query<Element> queryForTableElements = and(
            betweenExclusive(BOTTOM, tableBoundary.getTop(), tableBoundary.getBottom()),
            greaterThan(RIGHT, tableBoundary.getLeft()), lessThan(LEFT, tableBoundary.getRight()));

        Iterator<Element> iterator = retrieve(this.boxedElementIndex, queryForTableElements,
            orderBy(ascending(TOP)), false).iterator();
        Element prevElement = null;
        int rowNumber = 0;
        MutableList<MutableList<Element>> firstRowWithVerticalGroupHeads = Lists.mutable.empty();
        List<Element> tableElements = Lists.mutable.empty();
        MutableList<Element> prevRow = null;
        MutableList<Element> currRow = Lists.mutable.empty();
        MutableMap<TabularElementGroup<Element>, Integer> possiblePrevTablesToCurtail = Maps.mutable
            .empty();
        double tableBottom = tableBoundary.getBottom();
        int maxRowDistance = 0;
        int numProperRows = 0;
        // Determine table bottom and assign tabular row for each table element
        while (iterator.hasNext()) {
          Element element = iterator.next();
          if (!(element instanceof TextElement)) {
            continue;
          }
          int rowDistance = prevElement == null ? 0
              : PositionalElementList.compareByHorizontalAlignment(element, prevElement);

          // Set the table bottom if row distance is greater than threshold or if ratio of rowDistance/maxRowDistance greater than threshold
          // or if font ratio between current element and previous element is greater than threshold or
          // if current row is significantly different than previous row.
          if (rowDistance > 0) {
            boolean prevAndCurrElemPartOfSameBorderBox =
                element.getPositionalContext().getBoundingRectangle() != null
                    && element.getPositionalContext().getBoundingRectangle() == prevElement
                    .getPositionalContext().getBoundingRectangle();
            boolean isAccrossPageBreak =
                element.getPositionalContext().getPageBreakNumber() != prevElement
                    .getPositionalContext().getPageBreakNumber();
            if (isAccrossPageBreak) {
              if (rowDistance > MAX_INTER_ROW_DISTANCE + (this.isGridBasedTableDetectionEnabled
                  ? ACROSS_PAGE_BREAK_FACTOR_FOR_ROW_DISTANCE : 0)) {
                tableBottom = element.getAttribute(Top.class).getMagnitude() - SEPARATION_EPSILON;
                break;
              }
            } else if (!prevAndCurrElemPartOfSameBorderBox && (rowDistance > MAX_INTER_ROW_DISTANCE
                || (maxRowDistance > 0 && rowNumber > 0
                && rowDistance / maxRowDistance > TABLE_ROW_DISTANCE_VARIANCE))) {
              tableBottom = element.getAttribute(Top.class).getMagnitude() - SEPARATION_EPSILON;
              break;
            } else if (element.getAttribute(FontSize.class).getMagnitude() / prevElement
                .getAttribute(FontSize.class).getMagnitude() > FONT_SIZE_TABLE_BREAK_FACTOR) {
              tableBottom = element.getAttribute(Top.class).getMagnitude() - SEPARATION_EPSILON;
              break;
            }
            if (prevRow != null && this.isSignificant(prevRow, currRow)) {
              tableBottom =
                  currRow.getFirst().getAttribute(Top.class).getMagnitude() - SEPARATION_EPSILON;
              currRow = null;
              rowNumber--;
              break;
            }

            int rowSize = currRow.size();
            if (rowNumber == 0) {
              // Don't create the table when first row elements clearly have multi col span
              if (firstRowWithVerticalGroupHeads
                  .allSatisfy(vg -> vg.getLast().getPositionalContext().isPluralHeader())) {
                return;
              }

              // here, check if should persist with expansion. If yes, curtail, else take only the last element of each cell in header row
              if (isExpansionValid(firstRowWithVerticalGroupHeads, possiblePrevTablesToCurtail)) {
                //curtail
                possiblePrevTablesToCurtail.forEachKeyValue(TabularElementGroup::curtail);
              } else {
                // remove header expansion
                firstRowWithVerticalGroupHeads = firstRowWithVerticalGroupHeads
                    .collect(verticalGrpHead -> Lists.mutable.of(verticalGrpHead.getLast()));
              }

              // Assigning row to each header element
              int maxVerticalGrpHeadSize = firstRowWithVerticalGroupHeads
                  .collectInt(MutableList::size).max();
              for (MutableList<Element> verticalGrpHead : firstRowWithVerticalGroupHeads) {
                int i = maxVerticalGrpHeadSize - verticalGrpHead.size();
                for (Element verticalGrpElem : verticalGrpHead) {
                  verticalGrpElem.getPositionalContext().setTabularRow(i);
                  tableElements.add(verticalGrpElem);
                  i++;
                }
              }
              rowNumber = maxVerticalGrpHeadSize;
              rowSize = firstRowWithVerticalGroupHeads.size();
            } else {
              for (Element rowElement : currRow) {
                tableElements.add(rowElement);
                rowElement.getPositionalContext().setTabularRow(rowNumber);
              }
              rowNumber++;
              prevRow = currRow;
              currRow = Lists.mutable.empty();
            }
            numProperRows += rowSize > 1 ? 1 : 0;
            if (!isAccrossPageBreak) {
              maxRowDistance = Math.max(maxRowDistance, rowDistance);
            }
          }
          if (rowNumber == 0) {
            MutableList<Element> verticalGroupHead = Lists.mutable.empty();
            for (Element verticalGrpElem : element.getPositionalContext().getVerticalGroup()
                .getElements()) {
              PositionalContext<Element> verticalGrpElemContext = verticalGrpElem
                  .getPositionalContext();
              TabularElementGroup<Element> tabularGroup = verticalGrpElemContext.getTabularGroup();
              if (tabularGroup != null) {
                int curtailedNumRows = Math.min(possiblePrevTablesToCurtail
                        .getOrDefault(tabularGroup, tabularGroup.numberOfRows()),
                    verticalGrpElemContext.getTabularRow());
                possiblePrevTablesToCurtail.put(tabularGroup, curtailedNumRows);
              }

              verticalGroupHead.add(verticalGrpElem);
              if (verticalGrpElem == element) {
                break;
              }
            }

            if (verticalGroupHead.notEmpty()) {
              firstRowWithVerticalGroupHeads.add(verticalGroupHead);
            } else {
              return;
            }
          } else {
            currRow.add(element);
          }
          prevElement = element;
        }

        if (currRow != null) {
          if (prevRow != null && this.isSignificant(prevRow, currRow)) {
            tableBottom =
                currRow.getFirst().getAttribute(Top.class).getMagnitude() - SEPARATION_EPSILON;
            rowNumber--;
          } else {
            numProperRows += currRow.size() > 1 ? 1 : 0;
            for (Element rowElement : currRow) {
              tableElements.add(rowElement);
              rowElement.getPositionalContext().setTabularRow(rowNumber);
            }
          }
        }
        if (rowNumber == 0 || numProperRows < 2) {
          return;
        }

        // Assigning tabular columns for each table element
        queryForTableElements = and(betweenExclusive(BOTTOM, tableBoundary.getTop(), tableBottom),
            greaterThan(RIGHT, tableBoundary.getLeft()), lessThan(LEFT, tableBoundary.getRight()));
        List<TextElement> elementsInColOrder = Lists.mutable
            .ofAll(retrieve(this.boxedElementIndex, queryForTableElements,
                orderBy(ascending(LEFT), ascending(TOP)))).selectInstancesOf(TextElement.class);
        SortedSet<Double> prevElementRights = SortedSets.mutable.of(DOUBLE_COMPARATOR_WO_EQUALITY);
        Set<Integer> prevElementRowNums = Sets.mutable.empty();
        int columnNumber = 0;
        for (int i = 0; i < elementsInColOrder.size(); i++) {
          Element element = elementsInColOrder.get(i);
          PositionalContext<Element> posContext = element.getPositionalContext();
          if (!this.foundNoiseElements.contains(element)) {
            int rowNum = posContext.getTabularRow();
            double elementLeft = element.getAttribute(Left.class).getMagnitude();
            double elementWidth = element.getAttribute(Width.class).getMagnitude();
            double elementRight = elementLeft + elementWidth;
            double intersection = i + 1 < elementsInColOrder.size() ? elementWidth - Math
                .min(elementsInColOrder.get(i + 1)
                    .getAttribute(Left.class).getMagnitude() - elementLeft, elementWidth) :
                prevElementRights.isEmpty() || elementLeft > prevElementRights.last() ? elementWidth
                    : 0.0;
            int prevElemHeadSetSize = prevElementRights.headSet(elementLeft).size();
            double score = prevElementRights.isEmpty() ? 0.0
                : (double) prevElemHeadSetSize / prevElementRights.size();
            if (prevElemHeadSetSize == prevElementRights.size() || (intersection > 0.0
                && score > MIN_ROW_LEVEL_TABULAR_FITNESS)) {
              if (prevElementRights.size() > 1 || prevElementRowNums.contains(rowNum)) {
                columnNumber++;
                prevElementRights.clear();
                prevElementRowNums.clear();
              }
            }
            if (!SemanticsChecker.isSemanticallyIncomplete(element.getTextStr())) {
              prevElementRights.add(elementRight);
              prevElementRowNums.add(rowNum);
            }
          }
          posContext.setTabularColumn(columnNumber);
        }

        if (columnNumber == 0) {
          return;
        }

        for (MutableList<Element> verticalGrpHead : firstRowWithVerticalGroupHeads) {
          int j = verticalGrpHead.getLast().getPositionalContext().getTabularColumn();
          for (Element verticalGrpElem : verticalGrpHead) {
            verticalGrpElem.getPositionalContext().setTabularColumn(j);
          }
        }

        // Constructing new table with table elements
        TabularElementGroup<Element> table = new TabularElementGroup<>(rowNumber + 1,
            columnNumber + 1);
        for (Element tableElement : tableElements) {
          PositionalContext<Element> tableElemContext = tableElement.getPositionalContext();
          if (tableElemContext.getTabularGroup() != table
              && tableElemContext.getTabularColumn() != null) {
            tableElemContext.setTabularGroup(table);
            int col = tableElemContext.getTabularColumn();
            int row = tableElemContext.getTabularRow();
            table.addElement(row, col, tableElement);
          }
        }
        while (table.getCells().get(table.numberOfRows() - 1)
            .allSatisfy(TabularCellElementGroup::isEmpty)) {
          table.curtail(table.numberOfRows() - 1);
        }
        PositionalElementList<Element> positionalElementList = tableElements.get(0)
            .getElementList();
        positionalElementList.addTabularGroup(table);
      }
    }
  }

  /**
   * Check if current row has significant changes than previous row
   *
   * @param prevRow elements in previous row
   * @param currRow elements in current row
   * @return boolean flag indicating {@code currRow} has significant changes than {@code prevRow}
   */
  private boolean isSignificant(MutableList<Element> prevRow, MutableList<Element> currRow) {
    TextStyles textStyles = currRow.getFirst().getAttribute(TextStyles.class);
    boolean isBold = textStyles != null && textStyles.getValue().contains(TextStyles.BOLD);
    return isBold && this.semanticJumpCalculator.value(prevRow.collect(Element::getTextStr),
        currRow.collect(Element::getTextStr)) > TABLE_SEMANTIC_BREAK_THRESHOLD;
  }

  /**
   * Finds the table boundary which has column {@code startingColumn} within it. Table boundary is
   * calculated by extending the columns in left/right direction starting from column {@code
   * startingColumn}
   *
   * @param startingColumn starting column
   * @return table boundary which contains the column {@code startingColumn}
   */
  private RectangleProperties<Double> findTableBoundary(Column startingColumn) {
    MutableList<Element> unionColumn = startingColumn.elements;
    double tableTop =
        unionColumn.getFirst().getAttribute(Top.class).getMagnitude() - SEPARATION_EPSILON;
    double tableLeft = unionColumn.collect(e -> e.getAttribute(Left.class).getMagnitude()).min()
        - SEPARATION_EPSILON;
    SortedSet<Double> colBoundaries = SortedSets.mutable
        .of(DOUBLE_COMPARATOR_WO_EQUALITY, startingColumn.boundary);
    double maxColBottom = startingColumn.bottom;
    double tableRight = unionColumn.collect(e -> e.getAttribute(Left.class).getMagnitude() +
        e.getAttribute(Width.class).getMagnitude()).max();
    if (startingColumn.direction == DIRECTION_RIGHT
        || startingColumn.direction == DIRECTION_LEFT_RIGHT_BOTH) {
      Element unionColElemWithRight = unionColumn
          .detect(e -> e.getPositionalContext().getShadowedRightElement() != null);
      Element newColumnStart = unionColElemWithRight.getPositionalContext()
          .getShadowedRightElement();
      double rightLimit = Integer.MAX_VALUE;
      MutableList<Element> newColumnStarts = Lists.mutable.empty();
      while (newColumnStart != null) {
        newColumnStarts.add(newColumnStart);
        Column newColumn = this.findColumnElements(newColumnStart, true);
        if (newColumnStart.getAttribute(Left.class).getMagnitude() > tableRight) {
          colBoundaries.add(newColumn.boundary);
          maxColBottom = Math.max(maxColBottom, newColumn.bottom);
        }
        newColumnStart = null;
        unionColumn = mergeColumns(unionColumn, newColumn.elements, newColumn.boundary);

        for (Element element : unionColumn) {
          double elementLeft = element.getAttribute(Left.class).getMagnitude();
          double elementRight = elementLeft + element.getAttribute(Width.class).getMagnitude();
          tableRight = Math.max(tableRight, elementRight);
          Element rightElement = element.getPositionalContext().getShadowedRightElement();
          if (!isHorizontalAligning(element, rightElement)) {
            // If entered here and newColumnStart is null, then loop will end
            rightLimit = Math.min(rightLimit, elementLeft);
          } else if (newColumnStart == null && rightElement != null && elementRight < rightLimit
              && newColumnStarts.noneSatisfy(e -> e == rightElement)) {
            newColumnStart = rightElement;
          }
        }
      }
    }

    if (startingColumn.direction == DIRECTION_LEFT
        || startingColumn.direction == DIRECTION_LEFT_RIGHT_BOTH) {
      unionColumn = startingColumn.elements;
      Element unionColElemWithLeft = unionColumn
          .detect(e -> e.getPositionalContext().getShadowedLeftElement() != null);
      Element newColumnStart = unionColElemWithLeft.getPositionalContext().getShadowedLeftElement();
      double leftLimit = 0;
      MutableList<Element> newColumnStarts = Lists.mutable.empty();
      while (newColumnStart != null) {
        newColumnStarts.add(newColumnStart);
        Column newColumn = this.findColumnElements(newColumnStart, true);
        double newColumnStartRight = newColumnStart.getAttribute(Left.class).getMagnitude() +
            newColumnStart.getAttribute(Width.class).getMagnitude();
        if (newColumnStartRight < tableLeft) {
          colBoundaries.add(newColumn.boundary);
          maxColBottom = Math.max(maxColBottom, newColumn.bottom);
        }
        newColumnStart = null;
        unionColumn = mergeColumns(unionColumn, newColumn.elements, newColumn.boundary);

        for (Element element : unionColumn) {
          double elementLeft = element.getAttribute(Left.class).getMagnitude();
          tableLeft = Math.min(tableLeft, elementLeft);
          Element leftElement = element.getPositionalContext().getShadowedLeftElement();
          if (!isHorizontalAligning(element, leftElement)) {
            // If entered here and newColumnStart is null, then loop will end
            leftLimit = Math
                .max(leftLimit, elementLeft + element.getAttribute(Width.class).getMagnitude());
          } else if (newColumnStart == null && leftElement != null && elementLeft > leftLimit
              && newColumnStarts.noneSatisfy(e -> e == leftElement)) {
            newColumnStart = leftElement;
          }
        }
      }
    }

    int i = 1;
    double maxBadBoundaries = (1 - MIN_COLUMN_LEVEL_TABULAR_FITNESS) * colBoundaries.size();
    double tableBottom = this.pagePartition.bottomBoundary;
    for (double boundary : colBoundaries) {
      if (i >= maxBadBoundaries || boundary > maxColBottom) {
        tableBottom = boundary;
        break;
      }
      i++;
    }
    return new RectangleProperties<>(tableTop, tableRight, tableBottom, tableLeft);
  }

  /**
   * Find the column elements of table keeping {@code elem} as first element. Algo: Keep adding
   * below elements within visual bounds such that is satisfies following properties: <ol>
   * <li>Bounding rectangle of elem and below elem is same or (both elem belongs to different page
   * and below elem does not belong to table)</li> <li>Distance between below element and column
   * boundary is greater than TABLE_COL_FIT_ALLOWANCE and below element's left and right elements
   * are not intersecting with current column.</li> <li>Below element is not null</li> <li>If
   * encountered below element belongs to noise category, then ignore this element and continue
   * search for next below element</li> </ol>
   *
   * @param elem top element in the column
   * @param ignoreHorizontalAlignment boolean flag indicating to compute horizontal alignment if it
   * is false, else assume elements to be horizontally aligned if it is true
   * @return column of table
   */
  private Column findColumnElements(Element elem, boolean ignoreHorizontalAlignment) {
    boolean isAligning = true;
    MutableList<Element> columnElements = Lists.mutable.empty();
    PositionalContext<Element> elementPositionalContext = elem.getPositionalContext();
    Element rightElement = elementPositionalContext.getShadowedRightElement();
    Element leftElement = elementPositionalContext.getShadowedLeftElement();
    double elementLeft = elem.getAttribute(Left.class).getMagnitude();
    double elementTop = elem.getAttribute(Top.class).getMagnitude();
    double elementRight = elementLeft + elem.getAttribute(Width.class).getMagnitude();
    double elemVisualLeft = getVisualLeftForTable(elementLeft, elementPositionalContext,
        leftElement);
    double elemVisualRight = getVisualRightForTable(elementRight, elementPositionalContext,
        rightElement);
    boolean isLeftAligning = true;
    boolean isRightAligning = true;
    int nonNullLeftElemCount = 0;
    int nonNullRightElemCount = 1;
    double columnLeft = elementLeft;
    double columnRight = elementRight;
    ElementGroup<Element> verticalGrp = elementPositionalContext.getVerticalGroup();
    int pageBreakNumber = elementPositionalContext.getPageBreakNumber();
    while (isAligning) {
      columnElements.add(elem);
      Pair<Element, Pair<Double, Double>> belowElementWithVisualBounds = this
          .findTabularBelowElement(elementTop, elemVisualLeft, elemVisualRight, columnLeft,
              columnRight);
      Element belowElement = belowElementWithVisualBounds.getOne();
      elemVisualLeft = belowElementWithVisualBounds.getTwo().getOne();
      elemVisualRight = belowElementWithVisualBounds.getTwo().getTwo();
      Rectangle2D boundingRectangle = elementPositionalContext.getBoundingRectangle();
      isAligning = false;
      if (belowElement != null) {
        PositionalContext<Element> belowContext = belowElement.getPositionalContext();
        verticalGrp = belowContext.getVerticalGroup().getFirst() == belowElement ? belowContext
            .getVerticalGroup() : verticalGrp;
        Rectangle2D belowRectangle = belowContext.getBoundingRectangle();
        if ((belowRectangle == boundingRectangle || pageBreakNumber != belowContext
            .getPageBreakNumber()) && belowContext.getTabularGroup() == null) {
          boolean isHorizontallyAligned = true;
          Element belowLeftElem = belowContext.getShadowedLeftElement();
          Element belowRightElem = belowContext.getShadowedRightElement();

          if (!ignoreHorizontalAlignment) {
            isLeftAligning = isLeftAligning && isHorizontalAligning(belowElement, belowLeftElem);
            isRightAligning = isRightAligning && isHorizontalAligning(belowElement, belowRightElem);

            if (!isLeftAligning && !isRightAligning) {
              isHorizontallyAligned = false;
            }
          }
          if (isHorizontallyAligned) {
            double belowElementLeft = belowElement.getAttribute(Left.class).getMagnitude();
            double belowElementRight =
                belowElementLeft + belowElement.getAttribute(Width.class).getMagnitude();

            elementTop = belowElement.getAttribute(Top.class).getMagnitude();
            // If below element is within visual boundary and its left and right elements are not intersecting with current column.
            if (belowElementLeft >= elemVisualLeft - TABLE_COL_FIT_ALLOWANCE
                && belowElementRight <= elemVisualRight + TABLE_COL_FIT_ALLOWANCE
                && !isTabularIntersecting(columnLeft, columnRight, belowLeftElem)
                && !isTabularIntersecting(columnLeft, columnRight, belowRightElem)) {
              isAligning = true;
              if (belowLeftElem == null || belowLeftElem.getAttribute(Width.class).getMagnitude()
                  > TABLE_SMALL_ELEM_MAX_SIZE) {
                double belowVisualLeft = getVisualLeftForTable(belowElementLeft, belowContext,
                    belowLeftElem);
                elemVisualLeft = Math.max(elemVisualLeft, belowVisualLeft);
              }
              if (belowRightElem == null || belowRightElem.getAttribute(Width.class).getMagnitude()
                  > TABLE_SMALL_ELEM_MAX_SIZE) {
                double belowVisualRight = getVisualRightForTable(belowElementRight, belowContext,
                    belowRightElem);
                elemVisualRight = Math.min(elemVisualRight, belowVisualRight);
              }
              nonNullLeftElemCount =
                  belowLeftElem != null && isLeftAligning ? nonNullLeftElemCount + 1
                      : nonNullLeftElemCount;
              nonNullRightElemCount =
                  belowRightElem != null && isRightAligning ? nonNullRightElemCount + 1
                      : nonNullRightElemCount;
              columnLeft = Math.min(columnLeft, belowElementLeft);
              columnRight = Math.max(columnRight, belowElementRight);
            } else if (this.checkForTabularNoise(belowElement) || belowContext.getVerticalGroup()
                .getElements().anySatisfy(this.foundNoiseElements::contains)) {
              isAligning = true;
              this.foundNoiseElements.add(belowElement);
            }
          }
        }
      }
      elem = belowElement;
      if (elem == null) {
        break;
      }
      elementPositionalContext = elem.getPositionalContext();
    }
    elem =
        elem != null && elem.getPositionalContext().getVerticalGroup() == verticalGrp ? verticalGrp
            .getFirst() : elem;
    int direction = nonNullLeftElemCount > 0 ? DIRECTION_LEFT_RIGHT_BOTH : DIRECTION_RIGHT;
    double boundary = elem == null ? this.pagePartition.bottomBoundary
        : elem.getAttribute(Top.class).getMagnitude();
    Element lastElem = columnElements.getLast();
    double bottom =
        lastElem.getAttribute(Top.class).getMagnitude() + lastElem.getAttribute(Height.class)
            .getMagnitude();
    return new Column(columnElements, direction, boundary, bottom);
  }

  /**
   * Checks if passed element should be treated as a noise element for the tabular structure. If
   * yes, then the noise element does not interfere in column boundary calculation. Eg: any element
   * containing 'Note:' is a noise element.
   */
  private boolean checkForTabularNoise(Element element) {
    return this.tabularNoisePatterns
        .anySatisfy(noisePattern -> noisePattern.matcher(element.getTextStr()).matches());
  }

  /**
   * Find rectangle by considering the {@code elem} the above horizontal line for the rectangle
   *
   * @param elem top horizontal line of the generated rectangle
   * @param includeBroken boolean flag to search for rectangle spanning multiple paritions
   * @return generated rectangle
   */
  private Rectangle2D findRectangle(Element elem, boolean includeBroken) {
    double top = elem.getAttribute(Top.class).getMagnitude();
    double left = elem.getAttribute(Left.class).getMagnitude();
    double width = elem.getAttribute(Stretch.class).getMagnitude();
    double right = left + width;

    Element horizontalLine = null;
    boolean isRectangleContinued = true;
    double down = top - SEPARATION_EPSILON;
    double nextTop = top;
    while (horizontalLine == null && isRectangleContinued) {
      isRectangleContinued = false;
      // Find the left and right border vertical line of the rectangle
      Query<Element> queryForFirstVerticalLine = and(
          between(TOP, down, true, nextTop + SEPARATION_EPSILON, false),
          betweenExclusive(LEFT, left - SEPARATION_EPSILON, left + SEPARATION_EPSILON));
      Query<Element> queryForSecondVerticalLine = and(
          between(TOP, down, true, nextTop + SEPARATION_EPSILON, false),
          betweenExclusive(LEFT, right - SEPARATION_EPSILON, right + SEPARATION_EPSILON));
      Pair<Element, Element> verticalLinesForRectangle = this
          .findVerticalLinesForRectangle(queryForFirstVerticalLine, queryForSecondVerticalLine);
      if (verticalLinesForRectangle != null) {
        // Find the below horizontal line of the rectangle
        Element firstLine = verticalLinesForRectangle.getOne();
        down =
            firstLine.getAttribute(Top.class).getMagnitude() + firstLine.getAttribute(Stretch.class)
                .getMagnitude();
        Query<Element> queryForHorizontalLine = and(
            betweenExclusive(TOP, down - SEPARATION_EPSILON, down + SEPARATION_EPSILON),
            betweenExclusive(LEFT, left - SEPARATION_EPSILON, left + SEPARATION_EPSILON),
            betweenExclusive(HORIZONTAL_END, right - SEPARATION_EPSILON,
                right + SEPARATION_EPSILON));
        horizontalLine = retrieveOnlyOneResultElseNull(
            this.horizontalLineIndex.retrieve(queryForHorizontalLine));

        // If horizontal line is not found, find box element below the current line and try to find horizontal line again below the box element
        if (horizontalLine == null && includeBroken) {
          Iterator<Element> nextBoxedElementsIterator = retrieve(this.boxedElementIndex,
              betweenExclusive(TOP, down, down + CONTEXT_LIMIT), orderBy(ascending(TOP)))
              .iterator();
          if (nextBoxedElementsIterator.hasNext()) {
            Element nextBoxedElement = nextBoxedElementsIterator.next();
            nextTop = nextBoxedElement.getAttribute(Top.class).getMagnitude() + nextBoxedElement
                .getAttribute(Height.class).getMagnitude();
            isRectangleContinued = true;
          }
        }
      }
    }

    // Checks if elements inside the rectangle aligned tabularly. If aligned, then set bounding rectangle for each element to the created rectangle
    if (horizontalLine != null) {
      Rectangle2D rectangle = new Rectangle2D.Double(left, top, width, down - top);
      MutableList<Element> elementsInsideRectangle = this.findElementsWithinBoundingBox(rectangle);

      if (this.areElementsAlignedTabularly(elementsInsideRectangle)) {
        for (Element element : elementsInsideRectangle) {
          element.getPositionalContext().setBoundingRectangle(rectangle);
        }
      }
      return rectangle;
    }
    return null;
  }

  /**
   * Find the horizontal lines found within the {@code rectangle}
   *
   * @param rectangle rectangle where horizontal lines are being searched
   * @return horizontal lines
   */
  private MutableSet<Element> findContainedHorizontalLines(Rectangle2D rectangle) {
    double top = rectangle.getMinY();
    double bottom = rectangle.getMaxY();
    double left = rectangle.getMinX();
    double right = rectangle.getMaxX();

    Query<Element> queryForContainedHorizontalLines = and(
        betweenExclusive(TOP, top - SEPARATION_EPSILON, bottom + SEPARATION_EPSILON),
        greaterThan(LEFT, left - SEPARATION_EPSILON),
        lessThan(HORIZONTAL_END, right + SEPARATION_EPSILON));
    return Sets.mutable.ofAll(retrieve(this.horizontalLineIndex, queryForContainedHorizontalLines,
        orderBy(ascending(TOP))));
  }

  /**
   * Find the vertical lines found within the {@code rectangle}
   *
   * @param rectangle rectangle where vertical lines are being searched
   * @return vertical lines
   */
  private MutableSet<Element> findContainedVerticalLines(Rectangle2D rectangle) {
    double top = rectangle.getMinY();
    double bottom = rectangle.getMaxY();
    double left = rectangle.getMinX();
    double right = rectangle.getMaxX();

    Query<Element> queryForContainedVerticalLines = and(
        betweenExclusive(LEFT, left - SEPARATION_EPSILON, right + SEPARATION_EPSILON),
        greaterThan(TOP, top - SEPARATION_EPSILON),
        lessThan(VERTICAL_END, bottom + SEPARATION_EPSILON));
    return Sets.mutable.ofAll(
        retrieve(this.verticalLineIndex, queryForContainedVerticalLines, orderBy(ascending(LEFT))));
  }

  /**
   * Find rectilinear polygons using the {@code horizontalLinesOnPage} and {@code
   * verticalLinesOnPage}.
   *
   * @param horizontalLinesOnPage horizontal lines within page.
   * @param verticalLinesOnPage vertical lines within page.
   */
  private void findRectilinearPolygons(MutableSet<Element> horizontalLinesOnPage,
      MutableSet<Element> verticalLinesOnPage) {
    RectangleFinder rectangleFinder = new RectangleFinder(horizontalLinesOnPage,
        verticalLinesOnPage);
    MutableList<Rectangle2D> foundRectangles = rectangleFinder.getFoundRectangles();
    MutableList<Rectangle2D> openRectanglesClosedVertically = rectangleFinder
        .getCreatedRectangleBuilders()
        .flatCollect(RectangleBuilder::getRightSideOpenRectangles)
        .select(this.getVerticalOpenRectangleCombineCondition())
        .collect(OpenRectangle::createClosedRectangle)
        .select(RectilinearPolygon::isValidRectangle);
    MutableList<Rectangle2D> openRectanglesClosedHorizontally = OpenRectangle
        .combineHorizontallyOpenRectangles(
            rectangleFinder.getHorizontallyOpenRectangles(),
            this.getVerticalLineCombineCondition());
    MutableList<RectilinearPolygon> rectilinearPolygons = RectilinearPolygon
        .buildRectilinearPolygons(foundRectangles
            .withAll(openRectanglesClosedVertically).withAll(openRectanglesClosedHorizontally));
    this.assignBoundingBoxesForRectilinearPolygons(rectilinearPolygons);
  }

  /**
   * Construct a predicate to check whether two vertical lines can be combined or not. The vertical
   * lines can be combined if it satisfies either of the condition: <ol> <li>The lines are present
   * on same page and they are close to each other.</li> <li>The liens are present on different page
   * and there exists no text element between the lines.</li> </ol>
   *
   * @return constructed predicate
   */
  private Predicate2<VerticalLine, VerticalLine> getVerticalLineCombineCondition() {
    return (aboveLine, belowLine) ->
    {
      PositionalElementList<Element> elementList = aboveLine.getElementList();
      int aboveLinePageBreakNumber = elementList.getPageBreakNumber(aboveLine);
      int belowLinePageBreakNumber = elementList.getPageBreakNumber(belowLine);
      double aboveLineEndY =
          aboveLine.getTop().getMagnitude() + aboveLine.getStretch().getMagnitude();
      double belowLineBeginY = belowLine.getTop().getMagnitude();
      if (aboveLinePageBreakNumber == belowLinePageBreakNumber) {
        return belowLineBeginY - aboveLineEndY < SEPARATION_EPSILON;
      }
      MutableList<Element> boxedElementsBetweenBorders = Lists.mutable
          .ofAll(this.boxedElementIndex.retrieve(betweenExclusive(
              TOP, aboveLineEndY - SEPARATION_EPSILON, belowLineBeginY + SEPARATION_EPSILON)));
      return Iterate.isEmpty(boxedElementsBetweenBorders) || boxedElementsBetweenBorders
          .noneSatisfy(e -> e instanceof TextElement);
    };
  }

  /**
   * Construct a predicate to check whether vertical open rectangle can be closed or not. It can be
   * closed if there exists no boxed element at the closing border position.
   *
   * @return constructed predicate
   */
  private Predicate<OpenRectangle> getVerticalOpenRectangleCombineCondition() {
    return openRectangle ->
    {
      VerticalLine closingBorder = (VerticalLine) openRectangle.findClosingBorder();
      double closingBorderX = closingBorder.getLeft().getMagnitude();
      double closingBorderTopY = closingBorder.getTop().getMagnitude();
      double closingBorderBottomY = closingBorderTopY + closingBorder.getStretch().getMagnitude();
      Query<Element> elementsOverlappingRightBorder = and(
          greaterThan(BOTTOM, closingBorderTopY - SEPARATION_EPSILON),
          lessThan(TOP, closingBorderBottomY + SEPARATION_EPSILON),
          greaterThan(RIGHT, closingBorderX - SEPARATION_EPSILON),
          lessThan(LEFT, closingBorderX + SEPARATION_EPSILON));
      MutableList<Element> elements = Lists.mutable.ofAll(
          retrieve(this.boxedElementIndex, elementsOverlappingRightBorder,
              orderBy(ascending(TOP))));
      return elements.isEmpty();
    };
  }

  /**
   * Assign bounding box for every element within {@code rectilinearPolygons}
   *
   * @param rectilinearPolygons polygon whose element's bounding box is to assigned
   */
  private void assignBoundingBoxesForRectilinearPolygons(
      MutableList<RectilinearPolygon> rectilinearPolygons) {
    for (RectilinearPolygon rectilinearPolygon : rectilinearPolygons) {
      Rectangle2D boundingRectangle = rectilinearPolygon.getBoundingRectangle();
      MutableList<Element> elementsWithin = this.findElementsWithinBoundingBox(boundingRectangle);
      if (!this.areElementsAlignedTabularly(elementsWithin)
          || rectilinearPolygon.getEnclosedRectangles().size() == 1
          || rectilinearPolygon.getNumberOfRows() == 1
          || rectilinearPolygon.getNumberOfColumns() == 1) {
        continue;
      }

      for (Element element : elementsWithin) {
        if (element.getPositionalContext().getBoundingRectangle() == null) {
          element.getPositionalContext().setBoundingRectangle(boundingRectangle);
        }
      }
    }
  }

  /**
   * Find all the rectangular elements present in {@code bounding box}
   *
   * @param boundingBox rectangular region where elements are being searched
   * @return all the elements founnd in the region
   */
  private MutableList<Element> findElementsWithinBoundingBox(Rectangle2D boundingBox) {
    double left = boundingBox.getMinX();
    double right = boundingBox.getMaxX();
    double top = boundingBox.getMinY();
    double down = boundingBox.getMaxY();
    Query<Element> queryForElementsInsideRectangle = and(
        betweenExclusive(LEFT, left - SEPARATION_EPSILON, right - SEPARATION_EPSILON),
        betweenExclusive(TOP, top - 2 * SEPARATION_EPSILON, down - SEPARATION_EPSILON));
    return Lists.mutable.ofAll(
        retrieve(this.boxedElementIndex, queryForElementsInsideRectangle, orderBy(ascending(TOP))));
  }

  /**
   * Checks whether the elements inside the rectangle are aligned in table format. Elements are
   * aligned in tabularly if <ol> <li>there are atleast two rows</li> <li>There exists atleast two
   * items in either of the row</li> </ol>
   *
   * @param elementsInsideRectangle box elements inside the rectangle
   * @return boolean flag indicating whether elements are aligned tabularly or not
   */
  private boolean areElementsAlignedTabularly(MutableList<Element> elementsInsideRectangle) {
    int rowCount = 1;
    int colCount = 0;
    int currColCount = 0;
    Element prevElement = null;
    for (Element element : elementsInsideRectangle) {
      if (prevElement != null
          && PositionalElementList.compareByHorizontalAlignment(prevElement, element) < 0) {
        rowCount++;
        colCount = Math.max(colCount, currColCount);
        currColCount = 1;
      } else {
        currColCount++;
      }
      if (rowCount > 1 && colCount > 1)  // Breaking early to optimize the method
      {
        break;
      }
      prevElement = element;
    }
    colCount = Math.max(colCount, currColCount);
    return rowCount > 1 && colCount > 1;
  }

  /**
   * Retrieve the left and right border vertical lines if different between their lengths is less
   * than SEPARATION_EPSILOM
   *
   * @param queryForFirstVerticalLine query on how to find first vertical line
   * @param queryForSecondVerticalLine query on how to find second vertical line
   * @return pair of left and right border vertical lines
   */
  private Pair<Element, Element> findVerticalLinesForRectangle(
      Query<Element> queryForFirstVerticalLine, Query<Element> queryForSecondVerticalLine) {
    Element firstLine = retrieveOnlyOneResultElseNull(
        this.verticalLineIndex.retrieve(queryForFirstVerticalLine));
    if (firstLine != null) {
      Element secondLine = retrieveOnlyOneResultElseNull(
          this.verticalLineIndex.retrieve(queryForSecondVerticalLine));
      if (secondLine != null) {
        double firstLineStretch = firstLine.getAttribute(Stretch.class).getMagnitude();
        double secondLineStretch = secondLine.getAttribute(Stretch.class).getMagnitude();
        if (Math.abs(firstLineStretch - secondLineStretch) < SEPARATION_EPSILON) {
          return Tuples.pair(firstLine, secondLine);
        }
      }
    }
    return null;
  }

  /**
   * Method to get visual left of element
   *
   * @param top top coordinate of element
   * @param bottom bottom coordinte of element
   * @param left left coordinate of element
   * @param alignmentLeft left alignment of element
   * @param leftElement shadow left element of element
   * @return visual left of element
   */
  private Pair<Double, Boolean> findVisualLeft(double top, double bottom, double left,
      double alignmentLeft, Element leftElement) {
    double leftElementAlignmentRight =
        leftElement != null ? leftElement.getPositionalContext().getAlignmentRight() : 0;
    double leftElementRight =
        leftElement != null ? leftElement.getAttribute(Left.class).getMagnitude() +
            leftElement.getAttribute(Width.class).getMagnitude() : -1;
    boolean isLeftElementBoundaryAligning =
        leftElementAlignmentRight > 0 && leftElementAlignmentRight < left;
    boolean isElementBoundaryAligning = alignmentLeft > 0 && alignmentLeft > leftElementRight;
    return findVisualEdge(
        0,
        isLeftElementBoundaryAligning ? leftElementAlignmentRight : leftElementRight,
        isLeftElementBoundaryAligning,
        isElementBoundaryAligning ? alignmentLeft : left,
        isElementBoundaryAligning,
        Left.class,
        lessThan(LEFT, left + BORDER_LINE_ADJUST_EPSILON),
        or(and(betweenExclusive(TOP, top - CONTEXT_LIMIT, top),
            betweenExclusive(VERTICAL_END, top, top + CONTEXT_LIMIT)),
            and(betweenExclusive(TOP, top, bottom),
                betweenExclusive(VERTICAL_END, bottom, bottom + CONTEXT_LIMIT))),
        greaterThan(LEFT, leftElementRight - BORDER_LINE_ADJUST_EPSILON),
        orderBy(descending(LEFT)),
        this.verticalLineIndex);
  }

  /**
   * Method to get visual right of element
   *
   * @param top top coordinate of element
   * @param bottom bottom coordinte of element
   * @param right right coordinate of element
   * @param alignmentRight right alignment of element
   * @param rightElement shadow right element of element
   * @return visual right of element
   */
  private Pair<Double, Boolean> findVisualRight(double top, double bottom, double right,
      double alignmentRight, Element rightElement) {
    double rightElementAlignmentLeft =
        rightElement != null ? rightElement.getPositionalContext().getAlignmentLeft() : 0;
    double rightElementLeft =
        rightElement != null ? rightElement.getAttribute(Left.class).getMagnitude() : -1;
    boolean isRightElementBoundaryAligning = rightElementAlignmentLeft > right;
    boolean isElementBoundaryAligning = alignmentRight > 0 && alignmentRight < rightElementLeft;
    return findVisualEdge(
        this.pageWidth,
        isRightElementBoundaryAligning ? rightElementAlignmentLeft : rightElementLeft,
        isRightElementBoundaryAligning,
        isElementBoundaryAligning ? alignmentRight : right,
        isElementBoundaryAligning,
        Left.class,
        greaterThan(LEFT, right - BORDER_LINE_ADJUST_EPSILON),
        or(and(betweenExclusive(TOP, top - CONTEXT_LIMIT, top),
            betweenExclusive(VERTICAL_END, top, top + CONTEXT_LIMIT)),
            and(betweenExclusive(TOP, top, bottom),
                betweenExclusive(VERTICAL_END, bottom, bottom + CONTEXT_LIMIT))),
        lessThan(LEFT, rightElementLeft + BORDER_LINE_ADJUST_EPSILON),
        orderBy(ascending(LEFT)),
        this.verticalLineIndex);
  }

  /**
   * Method to get visual top of element
   *
   * @param top top coordinate of element
   * @param left left coordinate of element
   * @param right right coordinate of element
   * @param aboveElement shadow above element of element
   * @return visual top of element
   */
  private Pair<Double, Boolean> findVisualTop(double top, double left, double right,
      Element aboveElement) {
    double aboveElementBottom =
        aboveElement != null ? aboveElement.getAttribute(Top.class).getMagnitude() +
            aboveElement.getAttribute(Height.class).getMagnitude() : this.pagePartition.topBoundary;
    return findVisualEdge(
        this.pagePartition.topBoundary,
        aboveElementBottom,
        false,
        top,
        false,
        Top.class,
        betweenExclusive(TOP, top - CONTEXT_LIMIT, top),
        or(and(lessThan(LEFT, left), greaterThan(HORIZONTAL_END, left)),
            and(lessThan(LEFT, right), greaterThan(HORIZONTAL_END, right))),
        betweenExclusive(TOP, aboveElementBottom, aboveElementBottom + CONTEXT_LIMIT),
        orderBy(descending(TOP)),
        this.horizontalLineIndex);
  }

  /**
   * Method to get visual bottom of element
   *
   * @param bottom bottom coordinate of element
   * @param left left coordinate of element
   * @param right right coordinate of element
   * @param belowElement shadow below element of element
   * @return visual bottom of element
   */
  private Pair<Double, Boolean> findVisualBottom(double bottom, double left, double right,
      Element belowElement) {
    double belowElementTop =
        belowElement != null ? belowElement.getAttribute(Top.class).getMagnitude()
            : this.pagePartition.bottomBoundary;
    return findVisualEdge(
        this.pagePartition.bottomBoundary,
        belowElementTop,
        false,
        bottom,
        false,
        Top.class,
        betweenExclusive(TOP, bottom, bottom + CONTEXT_LIMIT),
        or(and(lessThan(LEFT, left), greaterThan(HORIZONTAL_END, left)),
            and(lessThan(LEFT, right), greaterThan(HORIZONTAL_END, right))),
        betweenExclusive(TOP, belowElementTop - CONTEXT_LIMIT, belowElementTop),
        orderBy(ascending(TOP)),
        this.horizontalLineIndex);
  }

  /**
   * Find elements which is present at above position than current element
   *
   * @param bottom bottom coordinate of current element
   * @param visualLeft visual left of current element
   * @param visualRight visual right of current element
   * @return above elements
   */
  private ElementGroup<Element> findAboveElements(double bottom, double visualLeft,
      double visualRight) {
    return this.findSurroundingElements(
        betweenExclusive(BOTTOM, bottom - CONTEXT_LIMIT, bottom - SEPARATION_EPSILON),
        and(greaterThan(HORIZONTAL_CENTRE, visualLeft), lessThan(HORIZONTAL_CENTRE, visualRight)),
        orderBy(descending(BOTTOM), ascending(HORIZONTAL_CENTRE)),
        Left.class,
        Width.class,
        VERTICAL_TRAVERSAL_THRESHOLD,
        (x, y) -> PositionalElementList.compareByHorizontalAlignment(x, y) != 0);
  }

  /**
   * Find elements which is present at below position than current element
   *
   * @param top top coordinate of current element
   * @param visualLeft visual left of current element
   * @param visualRight visual right of current element
   * @return below elements
   */
  private ElementGroup<Element> findBelowElements(double top, double visualLeft,
      double visualRight) {
    return this.findSurroundingElements(
        betweenExclusive(TOP, top + SEPARATION_EPSILON, top + CONTEXT_LIMIT),
        and(greaterThan(HORIZONTAL_CENTRE, visualLeft), lessThan(HORIZONTAL_CENTRE, visualRight)),
        orderBy(ascending(TOP), ascending(HORIZONTAL_CENTRE)),
        Left.class,
        Width.class,
        VERTICAL_TRAVERSAL_THRESHOLD,
        (x, y) -> PositionalElementList.compareByHorizontalAlignment(x, y) != 0);
  }

  /**
   * Find elements which is present at left position than current element
   *
   * @param left left coordinate of current element
   * @param visualTop visual top of current element
   * @param visualBottom visual bottom of current element
   * @return left elements
   */
  private ElementGroup<Element> findLeftElements(double left, double visualTop,
      double visualBottom) {
    return this.findSurroundingElements(
        lessThan(RIGHT, left),
        betweenExclusive(VERTICAL_CENTRE, visualTop, visualBottom),
        orderBy(descending(RIGHT), ascending(VERTICAL_CENTRE)),
        Top.class,
        Height.class,
        HORIZONTAL_TRAVERSAL_THRESHOLD,
        (x, y) -> PositionalElementList.compareByVerticalAlignment(x, y) != 0);
  }

  /**
   * Find elements which is present at right position than current element
   *
   * @param right right coordinate of current element
   * @param visualTop visual top of current element
   * @param visualBottom visual bottom of current element
   * @return right elements
   */
  private ElementGroup<Element> findRightElements(double right, double visualTop,
      double visualBottom) {
    return this.findSurroundingElements(
        greaterThan(LEFT, right),
        betweenExclusive(VERTICAL_CENTRE, visualTop, visualBottom),
        orderBy(ascending(LEFT), ascending(VERTICAL_CENTRE)),
        Top.class,
        Height.class,
        HORIZONTAL_TRAVERSAL_THRESHOLD,
        (x, y) -> PositionalElementList.compareByVerticalAlignment(x, y) != 0);
  }

  /**
   * Find surrounding elements
   *
   * @param axisCondition axis query
   * @param otherCondition other condition to include in query
   * @param elementOrdering option to sort by the results
   * @param otherStartAttrClass -
   * @param otherStretchAttrClass -
   * @param traversalThreshold Elements returned should be present in number of lines which is less
   * than this threshold
   * @param isElementLineCrossed predicate which returns true if current element is present on
   * different line than current element
   * @return element group consisting of surrounding elements
   */
  private ElementGroup<Element> findSurroundingElements(
      Query<Element> axisCondition,
      Query<Element> otherCondition,
      OrderByOption<Element> elementOrdering,
      Class<? extends LengthAttribute> otherStartAttrClass,
      Class<? extends LengthAttribute> otherStretchAttrClass,
      int traversalThreshold,
      Function2<Element, Element, Boolean> isElementLineCrossed) {
    Query<Element> query = and(axisCondition, otherCondition);
    ResultSet<Element> results = retrieve(this.boxedElementIndex, query, elementOrdering);
    Iterator<Element> resultsIterator = results.iterator();
    Element prevResultElement = null;
    int numOfElementLinesVisited = 0;
    ElementGroup<Element> resultElements = new ElementGroup<>();
    SortedSet<Pair<Double, Double>> elemIntervals = SortedSets.mutable
        .of(Comparator.comparingDouble(Pair::getOne));

    while (resultsIterator.hasNext()) {
      Element resultElement = resultsIterator.next();
      if (prevResultElement != null && isElementLineCrossed
          .value(prevResultElement, resultElement)) {
        numOfElementLinesVisited++;
        if (numOfElementLinesVisited >= traversalThreshold) {
          break;
        }
      }
      double resultOtherStart = resultElement.getAttribute(otherStartAttrClass).getMagnitude();
      double resultOtherEnd =
          resultOtherStart + resultElement.getAttribute(otherStretchAttrClass).getMagnitude();
      Pair<Double, Double> elemInterval = Tuples.pair(resultOtherStart, resultOtherEnd);
      SortedSet<Pair<Double, Double>> intervalsHead = elemIntervals.headSet(elemInterval);
      SortedSet<Pair<Double, Double>> intervalsTail = elemIntervals.tailSet(elemInterval);

      if ((intervalsTail.isEmpty() || resultOtherEnd < intervalsTail.first().getOne()) &&
          (intervalsHead.isEmpty() || resultOtherStart > intervalsHead.last().getTwo())) {
        resultElements.add(resultElement);
        elemIntervals.add(elemInterval);
      }
      prevResultElement = resultElement;
    }
    return resultElements;
  }

  /**
   * Find the tabular below element of current element Algo: We keep iterating below elements within
   * visual bound. We stop at the iteration when current element and that element is horizontally
   * intersecting
   *
   * @param top top coordinate of current element
   * @param visualLeft visual left of current element in table
   * @param visualRight visual right of current element in table
   * @param left left coordinate of current element
   * @param right right coordiant of current element
   * @return tabular below element
   */
  private Pair<Element, Pair<Double, Double>> findTabularBelowElement(double top, double visualLeft,
      double visualRight, double left, double right) {
    Element belowElement = null;
    double intersection = 0;
    while (intersection == 0) {
      if (belowElement != null) {
        top = belowElement.getAttribute(Top.class).getMagnitude() + SEPARATION_EPSILON;
      }
      belowElement = this.findShadowedBelowElement(top, visualLeft, visualRight, left, right);
      if (belowElement instanceof TextElement) {
        double belowElementLeft = belowElement.getAttribute(Left.class).getMagnitude();
        double belowElementWidth = belowElement.getAttribute(Width.class).getMagnitude();
        double belowElementRight = belowElementLeft + belowElementWidth;
        intersection = Math
            .max(Math.min(belowElementRight, right) - Math.max(belowElementLeft, left), 0);
        if (intersection == 0 && belowElementWidth > TABLE_SMALL_ELEM_MAX_SIZE) {
          if (belowElementRight < left) {
            visualLeft = belowElementRight;
          } else {
            visualRight = belowElementLeft;
          }
        }
      } else if (belowElement == null) {
        intersection = right - left;
      }
    }
    return Tuples.pair(belowElement, Tuples.pair(visualLeft, visualRight));
  }

  /**
   * Find shadow above element
   *
   * @param bottom bottom coordinate of element whose shadow above element has to be calculated.
   * @param visualLeft visual left coordinate of element whose shadow above element has to be
   * calculated.
   * @param visualRight visual right coordinate of element whose shadow above element has to be
   * calculated.
   * @param left left coordinate of element whose shadow above element has to be calculated.
   * @param right right coordinate of element whose shadow above element has to be calculated.
   * @return shadow above element
   */
  private Element findShadowedAboveElement(double bottom, double visualLeft, double visualRight,
      double left, double right) {
    return this.findMostIntersectingShadowElement(
        betweenExclusive(BOTTOM, bottom - CONTEXT_LIMIT, bottom - SEPARATION_EPSILON),
        intersection(LEFT, RIGHT, visualLeft, visualRight),
        orderBy(descending(BOTTOM)),
        Left.class,
        Width.class,
        left,
        right,
        (prevToPrevElement, prevElement, element) -> PositionalTextGroupingTransformer
            .compareByHorizontalAlignment(prevToPrevElement, prevElement, element) == 0);
  }

  /**
   * Find shadow below element
   *
   * @param top top coordinate of element whose shadow below element has to be calculated.
   * @param visualLeft visual left coordinate of element whose shadow below element has to be
   * calculated.
   * @param visualRight visual right coordinate of element whose shadow below element has to be
   * calculated.
   * @param left left coordinate of element whose shadow below element has to be calculated.
   * @param right right coordinate of element whose shadow below element has to be calculated.
   * @return shadow below element
   */
  private Element findShadowedBelowElement(double top, double visualLeft, double visualRight,
      double left, double right) {
    return this.findMostIntersectingShadowElement(
        betweenExclusive(TOP, top + SEPARATION_EPSILON, top + CONTEXT_LIMIT),
        intersection(LEFT, RIGHT, visualLeft, visualRight),
        orderBy(ascending(TOP)),
        Left.class,
        Width.class,
        left,
        right,
        (prevToPrevElement, prevElement, element) -> PositionalTextGroupingTransformer
            .compareByHorizontalAlignment(prevToPrevElement, prevElement, element) == 0);
  }

  /**
   * Find shadow left element
   *
   * @param top top coordinate of element whose shadow left element has to be calculated.
   * @param bottom bottom coordinate of element whose shadow left element has to be calculated.
   * @param left left coordinate of element whose shadow left element has to be calculated.
   * @return shadow left element
   */
  private Element findShadowLeftElement(double top, double bottom, double left) {
    return this.findMostIntersectingShadowElement(
        lessThan(RIGHT, left),
        intersection(TOP, BOTTOM, top, bottom, CONTEXT_LIMIT),
        orderBy(descending(RIGHT)),
        Top.class,
        Height.class,
        top,
        bottom,
        (prevToPrevElement, prevElement, element) ->
            PositionalElementList.compareByVerticalAlignment(prevElement, element) == 0);
  }

  /**
   * Find shadow right element
   *
   * @param top top coordinate of element whose shadow right element has to be calculated.
   * @param bottom bottom coordinate of element whose shadow right element has to be calculated.
   * @param right right coordinate of element whose shadow right element has to be calculated.
   * @return shadow right element
   */
  private Element findShadowRightElement(double top, double bottom, double right) {
    return this.findMostIntersectingShadowElement(
        greaterThan(LEFT, right),
        intersection(TOP, BOTTOM, top, bottom, CONTEXT_LIMIT),
        orderBy(ascending(LEFT)),
        Top.class,
        Height.class,
        top,
        bottom,
        (prevToPrevElement, prevElement, element) ->
            PositionalElementList.compareByVerticalAlignment(prevElement, element) == 0);
  }

  /**
   * Get the elements after applying the query constructed using {@code axisCondition} and {@code
   * otherCondition} on boxed elements. Find the element from above list which intersects most with
   * the range [{@code elemOtherAttrStartValue}, {@code elemOtherAttrEndValue}]. In above iteration,
   * current element range is calculated by retrieving values corresponding to attributes {@code
   * otherStartAttrClass} and {@code otherStretchAttrClass}
   *
   * @param axisCondition query on 1 dimensional axis
   * @param otherCondition query on range (2D)
   * @param elementOrdering option to order the retrieve elements
   * @param otherStartAttrClass attribute used to calculate starting value. This value will be used
   * to calculate range for element
   * @param otherStretchAttrClass attribute used to calculate stretch. Thus value will be used to
   * calculate range for element
   * @param elemOtherAttrStartValue starting point of range query
   * @param elemOtherAttrEndValue end point of range query
   * @param isElementLineNotCrossed predicate to check if element is eligible to become shadow
   * element
   * @return most intersected shadow element
   */
  private Element findMostIntersectingShadowElement(
      Query<Element> axisCondition,
      Query<Element> otherCondition,
      OrderByOption<Element> elementOrdering,
      Class<? extends LengthAttribute> otherStartAttrClass,
      Class<? extends LengthAttribute> otherStretchAttrClass,
      double elemOtherAttrStartValue,
      double elemOtherAttrEndValue,
      Function3<Element, Element, Element, Boolean> isElementLineNotCrossed) {
    Query<Element> query = and(axisCondition, otherCondition);
    ResultSet<Element> results = retrieve(this.boxedElementIndex, query, elementOrdering);
    Iterator<Element> resultsIterator = results.iterator();

    Element prevToPrevElement = null;
    Element prevResultElement = null;
    Element mostIntersectingElement = null;
    double intersectionScore = -1;

    while (resultsIterator.hasNext()) {
      Element resultElement = resultsIterator.next();
      if (prevResultElement == null || isElementLineNotCrossed
          .value(prevToPrevElement, prevResultElement, resultElement)) {
        double resultOtherStart = resultElement.getAttribute(otherStartAttrClass).getMagnitude();
        double resultOtherEnd =
            resultOtherStart + resultElement.getAttribute(otherStretchAttrClass).getMagnitude();
        double currIntersectionScore = Math.max(
            Math.min(elemOtherAttrEndValue, resultOtherEnd) - Math
                .max(elemOtherAttrStartValue, resultOtherStart), 0);

        if (currIntersectionScore > intersectionScore) {
          intersectionScore = currIntersectionScore;
          mostIntersectingElement = resultElement;
        }
      } else {
        break;
      }
      prevToPrevElement = prevResultElement;
      prevResultElement = resultElement;
    }
    return mostIntersectingElement;
  }

  /**
   * Find vertical group for the {@code elem}. Shadow below elements is being included in vertical
   * group until the any of the condition is broken: <ol> <li>Distance between the border and
   * current element is less than BORDER_LINE_ADJUST_EPSILON if there a border between the
   * elements</li> <li>Distance between the below and current element is less than
   * this.maxLineHeightAndDistanceFactor * height of current element</li> <li>Absolute((Ratio of
   * height of below element and current element)-1) is less than MAX_LINE_HEIGHT_VARIANCE</li>
   * <li>Absolute((Ratio of line distance and previous line distance)-1) is less than
   * MAX_LINE_DISTANCE_VARIANCE</li> <li>nextGroup element is directly below previous group element
   * and is not intersecting with any of left/right shadow element of previous group (and vice
   * versa)</li> </ol>
   *
   * @param elem element whose vertical group has to be found.
   */
  private void findVerticalGroup(Element elem) {
    PositionalContext<Element> prevElemPositionalContext = elem.getPositionalContext();
    double prevGroupElementHeight = elem.getAttribute(Height.class).getMagnitude();
    double prevGroupElementBottom =
        elem.getAttribute(Top.class).getMagnitude() + prevGroupElementHeight;
    double prevGroupElementLeft = elem.getAttribute(Left.class).getMagnitude();
    double prevGroupElementRight =
        prevGroupElementLeft + elem.getAttribute(Width.class).getMagnitude();
    ElementGroup<Element> verticalGroup = new ElementGroup<>();
    verticalGroup.add(elem);
    double prevLineDistance = -1;
    Element nextGroupElement = prevElemPositionalContext.getShadowedBelowElement();

    while (nextGroupElement instanceof TextElement && elem.getClass()
        .equals(nextGroupElement.getClass())) {
      PositionalContext<Element> nextElemPositionalContext = nextGroupElement
          .getPositionalContext();
      if (nextElemPositionalContext.getVerticalGroup() != null
          || nextElemPositionalContext.isVisualTopBorder()
          && (!this.detectUnderline
          || nextElemPositionalContext.getVisualTop() - prevGroupElementBottom
          > BORDER_LINE_ADJUST_EPSILON)) {
        break;
      }
      double nextGroupElementLeft = nextGroupElement.getAttribute(Left.class).getMagnitude();
      double nextGroupElementRight =
          nextGroupElementLeft + nextGroupElement.getAttribute(Width.class).getMagnitude();
      if (Math.min(prevGroupElementRight, nextGroupElementRight) > Math
          .max(prevGroupElementLeft, nextGroupElementLeft)) {
        double nextGroupElementTop = nextGroupElement.getAttribute(Top.class).getMagnitude();
        double nextGroupElementHeight = nextGroupElement.getAttribute(Height.class).getMagnitude();
        double lineDistance = nextGroupElementTop - prevGroupElementBottom;
        if (Math.abs(nextGroupElementHeight / prevGroupElementHeight - 1)
            > MAX_LINE_HEIGHT_VARIANCE) {
          break;
        }
        if (verticalGroup.size() == 1) {
          if (lineDistance > this.maxLineHeightAndDistanceFactor * prevGroupElementHeight) {
            break;
          }
        } else {
          if (Math.abs(lineDistance / prevLineDistance - 1) > MAX_LINE_DISTANCE_VARIANCE) {
            if (lineDistance < prevLineDistance) {
              verticalGroup.getElements().remove(verticalGroup.size() - 1);
            }
            break;
          }
        }

        // Extend the vertical group only if they nextGroup element is directly below previous group element
        // and is not intersecting with any of left/right shadow element of previous group (and vice versa).
        if (isHorizontallyIntersecting(prevElemPositionalContext.getShadowedLeftElement(),
            nextGroupElementLeft, nextGroupElementRight) ||
            isHorizontallyIntersecting(prevElemPositionalContext.getShadowedRightElement(),
                nextGroupElementLeft, nextGroupElementRight) ||
            isHorizontallyIntersecting(nextElemPositionalContext.getShadowedLeftElement(),
                prevGroupElementLeft, prevGroupElementRight) ||
            isHorizontallyIntersecting(nextElemPositionalContext.getShadowedRightElement(),
                prevGroupElementLeft, prevGroupElementRight)) {
          break;
        }
        verticalGroup.add(nextGroupElement);
        prevLineDistance = lineDistance;
        prevGroupElementHeight = nextGroupElementHeight;
        prevGroupElementBottom = nextGroupElementTop + nextGroupElementHeight;
        prevGroupElementLeft = nextGroupElementLeft;
        prevGroupElementRight = nextGroupElementRight;
        prevElemPositionalContext = nextElemPositionalContext;
        nextGroupElement = nextElemPositionalContext.getShadowedBelowElement();
      } else {
        break;
      }
    }

    // Update the vertical group for all elements in the created group
    PositionalElementList<Element> positionalElementList = elem.getElementList();
    positionalElementList.addVerticalGroup(verticalGroup);
    for (Element verticalGroupElem : verticalGroup.getElements()) {
      verticalGroupElem.getPositionalContext().setVerticalGroup(verticalGroup);
    }
  }

  /**
   * Find alignment group of {@code elem} and update the alignment of each element in its positional
   * context of that group
   *
   * @param elem Element whose alignment group is to be found
   */
  private void findAlignmentGroup(Element elem) {
    double top = elem.getAttribute(Top.class).getMagnitude();
    double height = elem.getAttribute(Height.class).getMagnitude();
    double left = elem.getAttribute(Left.class).getMagnitude();
    double width = elem.getAttribute(Width.class).getMagnitude();
    double bottom = top + height;
    double right = left + width;
    double centre = left + width / 2;

    Query<Element> query = and(betweenExclusive(TOP, top + ALIGNMENT_EPSILON, top + CONTEXT_LIMIT),
        intersection(LEFT, RIGHT, left - ALIGNMENT_EPSILON, right + ALIGNMENT_EPSILON));
    ResultSet<Element> results = retrieve(this.boxedElementIndex, query, orderBy(ascending(TOP)));

    MutableList<Element> alignmentGroup = Lists.mutable.empty();
    alignmentGroup.add(elem);
    double prevGroupElementBottom = bottom;
    double prevGroupElementHeight = height;

    Iterator<Element> resultsIterator = results.iterator();

    while (resultsIterator.hasNext()) {
      Element nextGroupElement = resultsIterator.next();
      if (nextGroupElement.getPositionalContext().getAlignmentRight() != 0) {
        break;
      }
      double nextGroupElementLeft = nextGroupElement.getAttribute(Left.class).getMagnitude();
      double nextGroupElementWidth = nextGroupElement.getAttribute(Width.class).getMagnitude();
      double nextGroupElementRight = nextGroupElementLeft + nextGroupElementWidth;
      double nextGroupElementCentre = nextGroupElementLeft + nextGroupElementWidth / 2;

      // If nextGroupElement left/right/center is aligned with element and
      // previous element is not separated from current element by large vertical distance,
      // then add the current element to alignmentGroup list.
      if (nextGroupElementLeft > left - ALIGNMENT_EPSILON
          && nextGroupElementLeft < left + ALIGNMENT_EPSILON ||
          nextGroupElementRight > right - ALIGNMENT_EPSILON
              && nextGroupElementRight < right + ALIGNMENT_EPSILON ||
          nextGroupElementCentre > centre - ALIGNMENT_EPSILON
              && nextGroupElementCentre < centre + ALIGNMENT_EPSILON) {
        double nextGroupElementTop = nextGroupElement.getAttribute(Top.class).getMagnitude();
        double nextGroupElementHeight = nextGroupElement.getAttribute(Height.class).getMagnitude();
        double lineDistance = nextGroupElementTop - prevGroupElementBottom;
        if (lineDistance > MAX_ALIGNMENT_LINE_HEIGHT_AND_DISTANCE_FACTOR * prevGroupElementHeight) {
          break;
        }
        alignmentGroup.add(nextGroupElement);
        prevGroupElementHeight = nextGroupElementHeight;
        prevGroupElementBottom = nextGroupElementTop + nextGroupElementHeight;
      } else {
        break;
      }
    }

    // For each element in alignmentGroups, update alignments in positional content
    if (alignmentGroup.size() > 1) {
      double alignmentLeft =
          alignmentGroup.collect(e -> e.getAttribute(Left.class).getMagnitude()).min()
              - SEPARATION_EPSILON;
      double alignmentRight =
          alignmentGroup.collect(e -> e.getAttribute(Left.class).getMagnitude() +
              e.getAttribute(Width.class).getMagnitude()).max() + SEPARATION_EPSILON;
      for (Element alignmentGroupElem : alignmentGroup) {
        PositionalContext<Element> positionalContext = alignmentGroupElem.getPositionalContext();
        positionalContext.setAlignmentLeft(alignmentLeft);
        positionalContext.setAlignmentRight(alignmentRight);
      }
    }
  }

  /**
   * Class representing column of table
   */
  private static final class Column {

    private final MutableList<Element> elements;  // Elements within column
    private final int direction;                           // direction to proceed for finding next column from this column
    private final double boundary;                  // Bottom y'th coordinate of (column + column noise)
    private final double bottom;                      // Bottom y'th coordinate of column

    Column(MutableList<Element> elements, int direction, double boundary, double bottom) {
      this.elements = elements;
      this.direction = direction;
      this.boundary = boundary;
      this.bottom = bottom;
    }
  }
}
