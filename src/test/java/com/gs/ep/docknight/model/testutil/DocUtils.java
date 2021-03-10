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

package com.gs.ep.docknight.model.testutil;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.test.Verify;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.ListIterate;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementGroup;
import com.gs.ep.docknight.model.PositionalContext;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.TabularCellElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.TabularElementGroup.VectorTag;
import com.gs.ep.docknight.model.Transformer;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.Image;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.extractor.PhraseExtractor.FileParserDecoder;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public final class DocUtils {

  public static final String PDF = "pdf";
  public static final String HTML = "html";
  public static final String HTM = "htm";
  public static final String TXT = "txt";
  public static final String XLSX = "xlsx";
  public static final String XLS = "xls";
  public static final String DOCX = "docx";
  public static final String JSON = "json";
  public static final String INPUT = "input";
  public static final String TABLE_JSON = "table.json";
  public static final String VERTICAL_TEST = "vertical.html";
  public static final String TABULAR_TEST = "tabular.html";
  public static final String ETABULAR_TEST = "etabular.html";
  public static final String HEADER_TEST = "header.html";
  public static final String FOOTER_TEST = "footer.html";

  public static final MutableList<String> VALID_INPUT_EXTENSIONS = Lists.mutable
      .of(PDF, HTML, HTM, TXT, XLSX, XLS, DOCX);
  public static final MutableList<String> VALID_TEST_EXTENSIONS = Lists.mutable
      .of(VERTICAL_TEST, TABULAR_TEST, ETABULAR_TEST,
          HEADER_TEST, FOOTER_TEST);
  public static final MutableList<String> POSSIBLE_EXTENSIONS = Lists.mutable
      .ofAll(VALID_INPUT_EXTENSIONS).withAll(VALID_TEST_EXTENSIONS);

  private DocUtils() {
  }

  public static <E extends Element> String toHtmlString(ElementGroup<E> elementGroup) {
    E prevElement = null;
    MutableList<String> textStrList = Lists.mutable.empty();
    for (E element : elementGroup.getElements()) {
      if (prevElement != null) {
        textStrList.add(
            PositionalElementList.compareByHorizontalAlignment(prevElement, element) == 0 ? "&emsp;"
                : "<br>\n");
      }
      textStrList.add(element instanceof Image ? "<img>" : element.getTextStr());
      prevElement = element;
    }
    return textStrList.isEmpty() ? "<p>\n</p>" : textStrList.makeString("<p>\n", "", "\n</p>");
  }

  public static <E extends Element> String toHtmlString(
      TabularElementGroup<E> tabularElementGroup) {
    MutableList<String> rowStrList = Lists.mutable.empty();
    MutableSet<Integer> totalRowIndices = tabularElementGroup
        .getVectorIndicesForTag(VectorTag.TOTAL_ROW);
    if (tabularElementGroup.getCaption() != null) {
      rowStrList.add(
          "<caption>" + DocUtils.toHtmlString(tabularElementGroup.getCaption()) + "</caption>");
    }
    for (int i = 0; i < tabularElementGroup.numberOfRows(); i++) {
      MutableList<String> colStrList = Lists.mutable.empty();
      String tag = (i < tabularElementGroup.getColumnHeaderCount()) ? "th" : "td";
      for (int j = 0; j < tabularElementGroup.numberOfColumns(); j++) {
        TabularCellElementGroup<E> cell = tabularElementGroup.getCells().get(i).get(j);
        if (!(cell.isHorizontallyMerged() || cell.isVerticallyMerged())) {
          int colSpan = getColSpan(tabularElementGroup, i, j);
          int rowSpan = getRowSpan(tabularElementGroup, i, j);
          StringBuilder elemTag = new StringBuilder(tag);
          String endTag = "</" + elemTag + ">";
          elemTag.append((colSpan > 1) ? " colspan='" + Integer.toString(colSpan) + "'" : "");
          elemTag.append((rowSpan > 1) ? " rowspan='" + Integer.toString(rowSpan) + "'" : "");
          String startTag = "<" + elemTag + ">";
          Function<Element, String> cellElemToStrFn =
              cell.size() > 1 ? e -> "<span>" + e + "</span>" : Object::toString;
          colStrList
              .add(cell.getElements().collect(cellElemToStrFn).makeString(startTag, " ", endTag));
        }
      }
      rowStrList.add(colStrList
          .makeString(totalRowIndices.contains(i) ? "<tr bgcolor=\"yellow\">" : "<tr>", "",
              "</tr>"));
    }
    return rowStrList.makeString("<table border='1px' style='margin-top:25px'>\n", "\n",
        (rowStrList.isEmpty() ? "" : "\n") + "</table>");
  }

  private static <E extends Element> int getColSpan(TabularElementGroup<E> tabularElementGroup,
      int rowIndex, int colIndex) {
    int currIndex = colIndex + 1;
    while (currIndex < tabularElementGroup.numberOfColumns() && tabularElementGroup.getCells()
        .get(rowIndex).get(currIndex).isHorizontallyMerged()) {
      currIndex++;
    }
    return currIndex - colIndex;
  }

  private static <E extends Element> int getRowSpan(TabularElementGroup<E> tabularElementGroup,
      int rowIndex, int colIndex) {
    int currIndex = rowIndex + 1;
    while (currIndex < tabularElementGroup.numberOfRows() && tabularElementGroup.getCells()
        .get(currIndex).get(colIndex).isVerticallyMerged()) {
      currIndex++;
    }
    return currIndex - rowIndex;
  }

  public static BufferedImage createBufferedImage(int width, int height, Color color) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(color);
    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    return image;
  }

  public static PositionalContext<Element> selectElementContext(Document document,
      String elementStr) {
    Element elem = document
        .getContainingElements(e -> e instanceof TextElement && e.getTextStr().equals(elementStr))
        .iterator().next();
    return elem.getPositionalContext();
  }

  public static PositionalContext<Element> selectElementContext(Document document, String selection,
      List<String> selectionMarkers, int selectionInstances) {
    int selectionInstancesFound = 0;
    int indexOfMarkerToCheck = 0;
    for (Element element : document.getContainingElements(e -> e instanceof TextElement)) {
      if (indexOfMarkerToCheck < selectionMarkers.size()) {
        if (element.getTextStr().equals(selectionMarkers.get(indexOfMarkerToCheck))) {
          indexOfMarkerToCheck++;
        }
      } else {
        String textStr = element.getTextStr();
        while (textStr.contains(selection)) {
          int firstIndex = textStr.indexOf(selection);
          textStr = textStr.substring(firstIndex + selection.length());
          selectionInstancesFound++;
          if (selectionInstancesFound == selectionInstances) {
            return element.getPositionalContext();
          }
        }
      }
    }
    return null;
  }

  public static MutableMap<String, Map<String, File>> findTestCases(String rootDir,
      List<String> possibleExtensions, List<String> inputExtensions, List<String> testExtensions) {
    MutableMap<String, Map<String, File>> testCases = Maps.mutable.empty();
    List<File> files = DocUtils.getFiles(new File(rootDir), possibleExtensions);
    for (File f : files) {
      String extension = getValidExtension(f, possibleExtensions);
      String testCaseName = f.getName().substring(0, f.getName().length() - extension.length() - 1);
      extension = inputExtensions.contains(extension) ? DocUtils.INPUT
          : testExtensions.contains(extension) ? extension : null;
      if (extension != null) {
        Map<String, File> testCase = testCases.getIfAbsentPut(testCaseName, Maps.mutable.empty());
        if (testCase.containsKey(extension) && testCase.get(extension).length() != f.length()) {
          String path1 = testCase.get(extension).getAbsolutePath();
          String path2 = f.getAbsolutePath();
          testCase.put(extension, f);
          throw new RuntimeException(
              "Duplicates files (" + path1 + ", " + path2 + ") with different sizes");
        }
        testCase.put(extension, f);
      }
    }

    return testCases
        .select((testCaseName, testCase) -> testCase.keySet().contains(DocUtils.INPUT) &&
            !CollectionUtils.intersection(testExtensions, testCase.keySet()).isEmpty());
  }

  public static List<File> getFiles(File path, List<String> possibleExtensions) {
    return path.isDirectory() ? Lists.mutable.ofAll(FileUtils.listFiles(path, null, true))
        .select(file -> !getValidExtension(file, possibleExtensions).isEmpty())
        : getValidExtension(path, possibleExtensions).isEmpty() ? Lists.mutable.empty()
            : Lists.mutable.of(path);
  }

  public static String getValidExtension(File file, List<String> possibleExtensions) {
    MutableList<String> validExtensionForFile = ListIterate
        .select(possibleExtensions, ext -> file.getName().toLowerCase().endsWith("." + ext));
    return validExtensionForFile.isEmpty() ? "" : validExtensionForFile.maxBy(String::length);
  }

  @SafeVarargs
  public static Document parseAsDocument(File file,
      Transformer<Document, Document>... transformers) {
    try (InputStream inputStream = new FileInputStream(file)) {
      Document document = FileParserDecoder.getParsedDocument(file.getName(), inputStream);
      return applyTransformersOnDocument(document, transformers);
    } catch (Exception e) {
      throw new RuntimeException("Error getting docmodel for file " + file.getAbsolutePath(), e);
    }
  }

  @SafeVarargs
  public static Document applyTransformersOnDocument(Document document,
      Transformer<Document, Document>... transformers) {
    for (Transformer<Document, Document> transformer : transformers) {
      document = transformer.transform(document);
    }
    return document;
  }

  public static MutableList<String> readTestCasesSeparatedByBlankLines(File file)
      throws IOException {
    return file == null ? Lists.mutable.empty()
        : splitTextByBlankLines(new String(Files.readAllBytes(file.toPath())));
  }

  public static MutableList<String> splitTextByBlankLines(String text) {
    MutableList<String> splitStrings = Lists.mutable.of(text.split("\\R(\\s*\\R)+"))
        .collect(s -> s.trim().replaceAll("\r", ""));
    return splitStrings.reject(String::isEmpty);
  }

  public static <K> K readTestCasesFromYaml(File file, Class<K> testCasesClass) throws Exception {
    Yaml yaml = new Yaml(new Constructor(testCasesClass));
    try (InputStream inputStream = new FileInputStream(file)) {
      return (K) yaml.load(inputStream);
    }
  }

  public static String getYamlRepresentation(Object object) {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(
        FlowStyle.BLOCK);   // to make each entry in list of values print as a bullet point in a new line
    options.setWidth(10000);                        // to prevent wrap-around to new line
    Yaml yaml = new Yaml(options);
    return yaml.dump(object).trim();
  }

  public static String prettifyAndSortJson(String jsonString) throws IOException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
      mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
      return mapper.writeValueAsString(mapper.readValue(jsonString, Object.class));
    } catch (JsonProcessingException e) {
      return jsonString;
    }
  }

  public static void assertDocumentJson(String expectedJson, Document document) throws IOException {
    String actualJson = new JsonRenderer().render(document);
    try {
      assertEquals(document.getDocumentSource(), prettifyAndSortJson(expectedJson),
          prettifyAndSortJson(actualJson));
    } catch (AssertionError e) {
      Verify.throwMangledException(e);
    }
  }

  public static void assertJsonStrings(String fileName, String expectedJson, String actualJson)
      throws IOException {
    try {
      assertEquals(fileName, prettifyAndSortJson(expectedJson), prettifyAndSortJson(actualJson));
    } catch (AssertionError e) {
      Verify.throwMangledException(e);
    }
  }

  public static MutableList<String> findHtmlStringsToUpdate(MutableList<String> newStrings,
      MutableList<String> existingStrings) {
    if (existingStrings.isEmpty()) {
      return Lists.mutable.empty();
    }

    if (newStrings.size() <= existingStrings.size()) {
      return newStrings;
    }

    MutableList<String> existingStringsProcessed = existingStrings
        .collect(DocUtils::htmlToContentString);
    MutableList<Pair<Integer, String>> possibleUpdates = Lists.mutable.empty();

    for (String newString : newStrings) {
      String newStringProcessed = htmlToContentString(newString);
      int updateDistance = existingStringsProcessed.collect(
          existingString -> LevenshteinDistance.getDefaultInstance()
              .apply(newStringProcessed, existingString)).min();
      possibleUpdates.add(Tuples.pair(updateDistance, newString));
    }

    possibleUpdates.sortThisBy(Pair::getOne);
    return possibleUpdates.take(existingStrings.size()).collect(Pair::getTwo);
  }

  public static InputStream getInputStreamForResourceFile(String relativePath, String fileName)
      throws FileNotFoundException {
    String filePath = Thread.currentThread().getContextClassLoader()
        .getResource(relativePath + "/" + fileName).getPath();
    return new FileInputStream(new File(filePath));
  }

  private static String htmlToContentString(String html) {
    return html.replaceAll("\\<.*?>", "").replaceAll(" ", "");
  }

  public static class ModelCustomizationConfiguration {

    public String path = "";
    public String type;
    public List<String> params = Lists.mutable.empty();
  }
}
