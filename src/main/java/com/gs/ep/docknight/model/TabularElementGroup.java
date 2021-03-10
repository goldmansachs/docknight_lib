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

import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.transformer.tabledetection.TableDetectionConfidenceFeatures;
import com.gs.ep.docknight.model.transformer.tabledetection.process.AbstractProcessNode;
import java.io.Serializable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.multimap.MutableMultimap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.ListIterate;

/**
 * Element group representing table in the {@see Document}
 */
public class TabularElementGroup<E extends Element> implements
    CloneableWithReference<TabularElementGroup<E>>, ElementCollection<E>, Serializable {

  private static final double MINIMUM_GRID_COVERAGE_RATIO = 0.6;
  private static final long serialVersionUID = -6005468052170732594L;
  private final MutableList<MutableList<TabularCellElementGroup<E>>> cells;
  private int columnHeaderCount;
  private MutableMap<TableDetectionConfidenceFeatures, Double> confidenceMap;
  private ElementGroup<E> caption;
  private MutableMultimap<VectorTag, Integer> vectorIndicesGroupedByVectorTag;
  private transient TabularElementGroup<E> clone;

  public TabularElementGroup(int numberOfRows, int numberOfColumns) {
    this(numberOfRows, numberOfColumns, 0);
  }

  public TabularElementGroup(int numberOfRows, int numberOfColumns, int defaultColumnHeaderCount) {
    this.cells = Lists.mutable.withNValues(numberOfRows,
        () -> Lists.mutable.withNValues(numberOfColumns, TabularCellElementGroup::new));
    this.confidenceMap = Maps.mutable.empty();
    this.columnHeaderCount = defaultColumnHeaderCount;
    this.vectorIndicesGroupedByVectorTag = Multimaps.mutable.list.empty();
  }

  @Override
  public E getFirst() {
    return ListIterate.detect(this.cells.get(0), cell -> cell.getElements().notEmpty()).getFirst();
  }

  @Override
  public E getLast() {
    return this.cells.getLast().asReversed().detect(cell -> cell.getElements().notEmpty())
        .getLast();
  }

  @Override
  public int size() {
    return (int) this.cells.sumOfLong(row -> row.sumOfInt(TabularCellElementGroup::size));
  }

  /**
   * Add {@code element} at cell element group at position ({@code row}, {@code col}) of this table
   */
  public void addElement(int row, int col, E element) {
    this.cells.get(row).get(col).add(element);
  }

  /**
   * Add {@code elements} at cell element group at position ({@code row}, {@code col}) of this
   * table
   */
  public void addElements(int row, int col, MutableList<E> elements) {
    for (E ele : elements) {
      this.addElement(row, col, ele);
    }
  }

  /**
   * @return all the cells of this table
   */
  public MutableList<MutableList<TabularCellElementGroup<E>>> getCells() {
    return this.cells;
  }

  /**
   * @return cell element group at position ({@code row}, {@code col}) of this table
   */
  public TabularCellElementGroup<E> getCell(int row, int col) {
    return this.cells.get(row).get(col);
  }

  /**
   * @return number of rows present in this table
   */
  public int numberOfRows() {
    return this.cells.size();
  }

  /**
   * @return number of columns present in this table
   */
  public int numberOfColumns() {
    if (this.cells.isEmpty()) {
      return 0;
    }
    return this.cells.get(0).size();
  }

  /**
   * @return all elements between rows [{@code row1}, {@code ro2}] and columns [{@code col1}, {@code
   * col2}] of this table
   */
  public MutableList<E> getElements(int row1, int row2, int col1, int col2) {
    MutableList<E> elements = Lists.mutable.empty();

    for (int i = row1; i <= row2; i++) {
      for (int j = col1; j <= col2; j++) {
        elements.addAll(this.cells.get(i).get(j).getElements());
      }
    }
    return elements;
  }

  @Override
  public MutableList<E> getElements() {
    return this.getElements(0, this.numberOfRows() - 1, 0, this.numberOfColumns() - 1);
  }

  // a value between 0 and 1 (highest symmetry)
  public double getRowLevelSymmetry() {
    IntList rowLevelCount = this.cells.subList(this.columnHeaderCount, this.cells.size())
        .collectInt(row -> row.count(ElementCollection::isNotEmpty));
    if (rowLevelCount.isEmpty()) {
      return 1;
    }
    double avgRowLevelCount = rowLevelCount.average();
    return 1 - rowLevelCount.collect(c -> Math.abs(c - avgRowLevelCount)).sumOfDouble(x -> x) / (
        rowLevelCount.size() * avgRowLevelCount);
  }

  /**
   * Set the {@code confidence} for the {@code feature} of this table
   */
  public void setConfidence(TableDetectionConfidenceFeatures feature, double confidence) {
    this.confidenceMap.put(feature, confidence);
  }

  /**
   * @return confidence of {@code feature} of this table
   */
  public double getConfidence(TableDetectionConfidenceFeatures feature) {
    return this.confidenceMap.get(feature);
  }

  /**
   * If cell at position ({@code row}, {@code col}) is part of a cell that is spanning multiple
   * rows/columns, then we will return the content of whole span, otherwise return the cell content
   */
  public TabularCellElementGroup<E> getMergedCell(int row, int col) {
    while (row > 0 && this.getCell(row, col).isVerticallyMerged()) {
      row--;
    }
    while (col > 0 && this.getCell(row, col).isHorizontallyMerged()) {
      col--;
    }
    return this.cells.get(row).get(col);
  }

  /**
   * Create table cells which are merged.
   *
   * @return transponse of the created table cells.
   */
  public MutableList<MutableList<? extends ElementGroup<E>>> getMergedColumns() {
    MutableList<MutableList<? extends ElementGroup<E>>> listOfColumns = Lists.mutable.empty();
    int colCount = this.numberOfColumns();
    int rowCount = this.numberOfRows();
    for (int j = 0; j < colCount; j++) {
      MutableList<ElementGroup<E>> column = Lists.mutable.empty();
      for (int i = 0; i < rowCount; i++) {
        column.add(this.getMergedCell(i, j));
      }
      listOfColumns.add(column);
    }
    return listOfColumns;
  }

  /**
   * @return table cells which are merged
   */
  public MutableList<MutableList<? extends ElementGroup<E>>> getMergedRows() {
    MutableList<MutableList<? extends ElementGroup<E>>> listOfRows = Lists.mutable.empty();
    int colCount = this.numberOfColumns();
    int rowCount = this.numberOfRows();
    for (int i = 0; i < rowCount; i++) {
      MutableList<ElementGroup<E>> row = Lists.mutable.empty();
      for (int j = 0; j < colCount; j++) {
        row.add(this.getMergedCell(i, j));
      }
      listOfRows.add(row);
    }
    return listOfRows;
  }

  /**
   * Delete all rows from the Table starting from Row Index {@code curtailedNumOfRows} which is
   * 0-based.
   */
  public void curtail(int curtailedNumOfRows) {
    int colCount = this.numberOfColumns();
    PositionalElementList<E> positionalElementList = this.getCorrespondingPositionalElementList();
    for (int i = this.numberOfRows() - 1; i >= curtailedNumOfRows; i--) {
      MutableList<TabularCellElementGroup<E>> row = this.cells.get(i);
      for (int col = 0; col < colCount; col++) {
        TabularCellElementGroup<E> cell = row.get(col);
        if (!cell.isVerticallyMerged()) {
          MutableList<E> elements = cell.getElements();
          elements.select(Element::hasPositionalContext)
              .each(element -> element.getPositionalContext().deleteTableReference());
        }
      }
      this.cells.remove(i);
    }
    if (this.cells.isEmpty() && positionalElementList != null) {
      positionalElementList.removeTabularGroup(this);
    }
  }

  private PositionalElementList<E> getCorrespondingPositionalElementList() {
    E firstElement = this.getFirst();
    if (firstElement != null) {
      Pair<ElementList<Element>, Integer> elementListContext = firstElement.getElementListContext();
      if (elementListContext != null && elementListContext.getOne() != null) {
        return (PositionalElementList<E>) elementListContext.getOne();
      }
    }
    return null;
  }

  /**
   * @return the column at index {@code colIndex} of this table
   */
  public MutableList<TabularCellElementGroup<E>> getColumn(int colIndex) {
    return this.cells.collect(row -> row.get(colIndex));
  }

  /**
   * Delete the row at index {@code rowNumber} of this table
   */
  public void deleteRow(int rowNumber) {
    int colCount = this.numberOfColumns();
    int rowCount = this.numberOfRows();
    MutableList<TabularCellElementGroup<E>> row = this.cells.get(rowNumber);
    for (int col = 0; col < colCount; col++) {
      TabularCellElementGroup<E> cell = row.get(col);
      TabularCellElementGroup<E> nextCell =
          rowNumber + 1 < rowCount ? this.cells.get(rowNumber + 1).get(col) : null;
      /* delete back references from element to table before deleting the reference of the element from the table */
      if (!cell.isVerticallyMerged() && (nextCell == null || !nextCell.isVerticallyMerged())) {
        MutableList<E> elements = cell.getElements();
        elements.select(Element::hasPositionalContext)
            .each(element -> element.getPositionalContext().deleteTableReference());
      }

      /* copy the elementGroup if there is something which is vertically merged to current row*/
      if (nextCell != null && nextCell.isVerticallyMerged()) {
        this.addElements(rowNumber + 1, col, nextCell.getElements());
        nextCell.setVerticallyMerged(false);
      }
    }

    this.cells.remove(rowNumber);
    if (rowNumber < this.columnHeaderCount)  // deleted row was part of the header rows
    {
      this.columnHeaderCount--;
    }
    this.setBackReferences();   // can remove this when we do away with table back-referencing logic in TableDetectionTransformer
  }

  /**
   * Add the {@code row} at the {@code index} of this table
   */
  public void addRow(MutableList<TabularCellElementGroup<E>> row, int index) {
    this.cells.add(index, row);
  }

  public int getColumnHeaderCount() {
    return this.columnHeaderCount;
  }

  /**
   * Set the column header count of this table. Column header count represents number of rows in the
   * table which are actually column headers
   */
  public void setColumnHeaderCount(int columnHeaderCount) {
    this.columnHeaderCount = columnHeaderCount;
  }

  /**
   * Getter for table caption
   */
  public ElementGroup<E> getCaption() {
    return this.caption;
  }

  /**
   * Set the caption {@code caption} of this table
   */
  public void setCaption(ElementGroup<E> caption) {
    this.caption = caption;
  }

  /**
   * Populate positional context with tabular column, row and group for each element in this group
   */
  public void setBackReferences() {
    if (this.cells.notEmpty() && this.cells.get(0).notEmpty()) {
      this.cells.forEachWithIndex(0, this.cells.size() - 1, (row, rowIndex) ->
          row.forEachWithIndex(0, row.size() - 1, (cell, colIndex) ->
              cell.getElements().select(Element::hasPositionalContext).each(element ->
              {
                PositionalContext<E> positionalContext = element.getPositionalContext();
                positionalContext.setTabularColumn(colIndex);
                positionalContext.setTabularRow(rowIndex);
                positionalContext.setTabularGroup(this);
              })));
    }
  }

  /**
   * Get the column left and right boundaries for each column
   */
  public Pair<double[], double[]> getColumnBoundaries() {
    int colCount = this.numberOfColumns();
    double[] columnLeftBoundaries = new double[colCount];
    double[] columnRightBoundaries = new double[colCount];

    for (int j = 0; j < colCount; j++) {
      int rowCount = this.getRowDepthForColumn(j);
      columnLeftBoundaries[j] = Double.MAX_VALUE;
      for (int i = 0; i < rowCount; i++) {
        columnLeftBoundaries[j] = Math.min(columnLeftBoundaries[j], this.getCell(i, j).getElements()
            .collectDouble(e -> e.getAttribute(Left.class).getValue().getMagnitude())
            .minIfEmpty(Integer.MAX_VALUE));
        columnRightBoundaries[j] = Math
            .max(columnRightBoundaries[j], this.getCell(i, j).getElements()
                .collectDouble(e -> e.getAttribute(Left.class).getValue().getMagnitude() +
                    e.getAttribute(Width.class).getValue().getMagnitude()).maxIfEmpty(0));
      }
    }
    return Tuples.pair(columnLeftBoundaries, columnRightBoundaries);
  }

  /**
   * @return True if number of non null columns is less than 2, else return False
   */
  public boolean isRowBasedTable() {
    if (this.numberOfColumns() <= 2) {
      return true;
    }
    int nonNullColumns = 0;
    for (int i = 0; i < this.numberOfColumns(); i++) {
      for (int r = 0; r < this.numberOfRows(); r++) {
        if (this.getCell(r, i).getElements().notEmpty()) {
          nonNullColumns++;
          break;
        }
      }
    }
    boolean isRowBased = false;
    if (nonNullColumns <= 2) {
      isRowBased = true;
    }
    return isRowBased;
  }

  /**
   * @return True if horizontal lines in the grid are present in majority of the rows the table,
   * else return False.
   */
  public boolean areHorizontalLinesSignificant() {
    int gridBasedRowsCount = this.cells
        .count(rowCells -> rowCells.flatCollect(ElementGroup::getElements).anySatisfy(rowElement ->
        {
          PositionalContext<Element> context = rowElement.getPositionalContext();
          return context.isVisualTopBorder() && context.isVisualBottomBorder();
        }));
    return Double
        .compare((double) gridBasedRowsCount / this.numberOfRows(), MINIMUM_GRID_COVERAGE_RATIO)
        >= 0;
  }

  /**
   * @return True if table cells are present in a grid, else return False
   */
  public boolean isRowAndColumnGrid() {
    MutableList<MutableList<? extends ElementGroup<E>>> columns = this.getMergedColumns();
    int columnCount = columns.size();
    boolean hasColumnBorders = false;
    BorderDirection direction = BorderDirection.LEFT;
    for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
      MutableList<TabularCellElementGroup<E>> column = (MutableList<TabularCellElementGroup<E>>) columns
          .get(columnIndex);
      BorderType columnBorderType = this.getBorderType(column, direction);
      if (columnBorderType == BorderType.INCONSISTENT) {
        return false;
      }
      if (columnBorderType == BorderType.CONSISTENT_PRESENT) {
        hasColumnBorders = true;
      }
      if (columnIndex == 0 && direction == BorderDirection.LEFT) {
        columnIndex--;
        direction = BorderDirection.RIGHT;
      }
    }

    MutableList<MutableList<? extends ElementGroup<E>>> rows = this.getMergedRows();
    int rowCount = rows.size();
    boolean hasRowBorders = false;
    direction = BorderDirection.TOP;
    for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
      MutableList<TabularCellElementGroup<E>> row = (MutableList<TabularCellElementGroup<E>>) this
          .getMergedRows().get(rowIndex);
      BorderType rowBorderType = this.getBorderType(row, direction);
      if (rowBorderType == BorderType.INCONSISTENT) {
        return false;
      }
      if (rowBorderType == BorderType.CONSISTENT_PRESENT) {
        hasRowBorders = true;
      }
      if (rowIndex == 0 && direction == BorderDirection.TOP) {
        rowIndex--;
        direction = BorderDirection.BOTTOM;
      }
    }

    return hasColumnBorders && hasRowBorders;
  }

  private boolean isRowAndMaybeColGrid() {
    // Currently there is no assertion while performing grid based row mergers on whether there actually is a grid
    return true;
  }

  /**
   * @return True if table cells are present in a grid of type {@code gridType}
   */
  public boolean isGridTypeSatisfied(GridType gridType) {
    if (gridType == GridType.ROW_AND_COL) {
      return this.isRowAndColumnGrid();
    }
    if (gridType == GridType.ROW_AND_MAYBE_COL) {
      return this.isRowAndMaybeColGrid();
    }
    return false;
  }

  /**
   * @return new Table by merging all the rows which have indices between [{@code row1}, {@code
   * row2}]. Note; Both the given indices are inclusive in the merge process
   */
  public TabularElementGroup<E> getNewRowMergedTable(int row1, int row2) {
    if ((row1 > row2) || (row1 < 0) || (row2 > this.numberOfRows())) {
      return this;
    }
    int rowCount = this.numberOfRows();
    int colCount = this.numberOfColumns();
    TabularElementGroup<E> newTabularGroup = new TabularElementGroup<>(rowCount - row2 + row1,
        colCount, AbstractProcessNode.DEFAULT_COLUMN_HEADER_COUNT);
    for (int i = 0; i < rowCount; i++) {
      for (int j = 0; j < colCount; j++) {
        int newRowIndex = (i < row1) ? i : i > row2 ? i - row2 + row1 : row1;
        TabularCellElementGroup<E> oldCell = this.getCell(i, j);

        TabularCellElementGroup<E> newCell = newTabularGroup.getCell(newRowIndex, j);
        newCell.setHorizontallyMerged(oldCell.isHorizontallyMerged());
        newCell.setVerticallyMerged(oldCell.isVerticallyMerged());
        for (E ele : oldCell.getElements()) {
          if (!newCell.getElements().contains(ele)) {
            newTabularGroup.addElement(newRowIndex, j, ele);
          }
        }
      }
    }
    newTabularGroup.setCaption(this.getCaption());
    newTabularGroup.setColumnHeaderCount(Math.min(this.getColumnHeaderCount(),
        row1 + 1 + Math.max(0, this.getColumnHeaderCount() - row2 - 1)));
    return newTabularGroup;
  }

  /**
   * Insert a particular index {@code index} under tag {@code vectorTag}
   */
  public void addVectorTag(VectorTag vectorTag, int index) {
    this.vectorIndicesGroupedByVectorTag.put(vectorTag, index);
  }

  /**
   * Insert all the indices {@code indicesList} under tag {@code vectorTag}
   */
  public void addVectorTags(VectorTag vectorTag, MutableList<Integer> indicesList) {
    this.vectorIndicesGroupedByVectorTag.putAll(vectorTag, indicesList);
  }

  /**
   * Get all the row/column indices which have the tag {@code vectorTag}
   */
  public MutableSet<Integer> getVectorIndicesForTag(VectorTag vectorTag) {
    return this.vectorIndicesGroupedByVectorTag.get(vectorTag).toSet();
  }

  /**
   * If all the column header cells in the column {@code col} are empty, return number of rows in
   * table, otherwise return number of rows representing column header.
   */
  private int getRowDepthForColumn(int col) {
    int rowIndex = 0;
    while (rowIndex < this.columnHeaderCount && this.cells.get(rowIndex).get(col).getElements()
        .isEmpty()) {
      rowIndex++;
    }
    return this.columnHeaderCount > 0 && rowIndex < this.columnHeaderCount ? this.columnHeaderCount
        : this.numberOfRows();
  }

  /**
   * @return borderType of the {@code cellList} in the direction {@code borderDirection}
   */
  private BorderType getBorderType(MutableList<TabularCellElementGroup<E>> cellList,
      BorderDirection borderDirection) {
    boolean borderTypeUnknown = true;
    boolean borderPresent = false;
    int numberOfCells = cellList.size();
    for (int cellIndex = 0; cellIndex < numberOfCells; cellIndex++) {
      TabularCellElementGroup<E> cell = cellList.get(cellIndex);
      boolean cellBorder = this.getBorderPresence(cell, borderDirection);
      if (cell.getTextStr().isEmpty()) {
        continue;
      }
      if (borderDirection == BorderDirection.BOTTOM && cellIndex < numberOfCells - 1) {
        TabularCellElementGroup<E> bottomCell = cellList.get(cellIndex + 1);
        if (this.isBorderLess(bottomCell)) {
          cellBorder = false;
        } else if (this.getBorderPresence(bottomCell, BorderDirection.BOTTOM)) {
          cellBorder = true;
        }
      } else if (borderDirection == BorderDirection.TOP && cellIndex > 0) {
        TabularCellElementGroup<E> aboveCell = cellList.get(cellIndex - 1);
        if (this.isBorderLess(aboveCell)) {
          cellBorder = false;
        }
      }
      if (borderTypeUnknown) {
        borderPresent = cellBorder;
        borderTypeUnknown = false;
      }
      if (borderPresent != cellBorder) {
        return BorderType.INCONSISTENT;
      }
    }
    if (borderPresent) {
      return BorderType.CONSISTENT_PRESENT;
    }
    return BorderType.CONSISTENT_ABSENT;
  }

  /**
   * @return True if the border is present on the {@code cell} in the direction {@code
   * borderDirection}
   */
  private boolean getBorderPresence(TabularCellElementGroup<E> cell,
      BorderDirection borderDirection) {
    RectangleProperties<Boolean> borders = cell.getBorderExistence();
    switch (borderDirection) {
      case TOP:
        return borders.getTop();
      case BOTTOM:
        return borders.getBottom();
      case LEFT:
        return borders.getLeft();
      case RIGHT:
        return borders.getRight();
    }
    return false;
  }

  /**
   * @return True if the cell does not contain the border in any direction
   */
  private boolean isBorderLess(TabularCellElementGroup<E> cell) {
    RectangleProperties<Boolean> borders = cell.getBorderExistence();
    return !(borders.getTop() || borders.getLeft() || borders.getRight() || borders.getBottom());
  }

  @Override
  protected TabularElementGroup<E> clone() {
    this.clone = new TabularElementGroup<>(this.numberOfRows(), this.numberOfColumns());
    for (int i = 0; i < this.numberOfRows(); i++) {
      for (int j = 0; j < this.numberOfColumns(); j++) {
        for (E element : this.getCell(i, j).getElements()) {
          this.clone.addElement(i, j, (E) element.clone());
        }
      }
    }
    this.clone.confidenceMap = this.confidenceMap;
    this.clone.columnHeaderCount = this.columnHeaderCount;
    this.clone.caption = this.caption;
    this.clone.setBackReferences();
    return this.clone;
  }

  @Override
  public TabularElementGroup<E> getCurrentClone() {
    if (this.clone == null) {
      this.clone = this.clone();
    }
    return this.clone;
  }

  @Override
  public String toString() {
    return this.getTextStr();
  }

  /**
   * Enum used to represent tag on the rows/columns. For example: total row
   */
  public enum VectorTag {
    TOTAL_ROW("totalRow", true);

    private final String tagName;
    private final boolean isRowTag;

    VectorTag(String tagName, boolean isRowTag) {
      this.tagName = tagName;
      this.isRowTag = isRowTag;
    }

    public static VectorTag getVectorTag(String tagName) {
      return Lists.mutable.of(VectorTag.values()).detect(each -> each.getTagName().equals(tagName));
    }

    public String getTagName() {
      return this.tagName;
    }

    public boolean isRowTag() {
      return this.isRowTag;
    }

    public boolean isColumnTag() {
      return !this.isRowTag;
    }
  }

  public enum TableType {
    GRID_BASED,
    KEY_VALUE,
    FULLY_POPULATED,
    NORMAL
  }

  /**
   * Enum representing whether the borders are present or not and are also consistent across all the
   * cells in a particular direction.
   */
  private enum BorderType {
    CONSISTENT_PRESENT,
    CONSISTENT_ABSENT,
    INCONSISTENT
  }

  /**
   * Enum representing direction of border
   */
  private enum BorderDirection {
    TOP,
    LEFT,
    BOTTOM,
    RIGHT
  }

  /*
  Each table can be associated with a degree a of grid type which in turn is used to determine its internal structure
  Row and Col - A grid where both horizontal and vertical lines are present as cell delimiters
  Row and Maybe Col - A grid where vertical lines may be absent but horizontal lines are of interest and delimit rows
  None - A grid were both horizontal and vertical lines may be absent and are not of interest
   */
  public enum GridType {
    ROW_AND_COL,
    ROW_AND_MAYBE_COL,
    NONE;

    public static GridType getEnum(String value) {
      if (value.equals("true")) {
        return ROW_AND_MAYBE_COL;
      }
      if (value.equals("true-strict")) {
        return ROW_AND_COL;
      }
      return NONE;
    }
  }
}
