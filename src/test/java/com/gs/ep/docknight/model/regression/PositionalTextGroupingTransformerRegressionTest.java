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

package com.gs.ep.docknight.model.regression;

import static org.junit.Assert.assertEquals;

import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.ElementGroup;
import com.gs.ep.docknight.model.ModelCustomizationKey;
import com.gs.ep.docknight.model.ModelCustomizations;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.TabularElementGroup;
import com.gs.ep.docknight.model.context.PagePartitionType;
import com.gs.ep.docknight.model.converter.PdfParser.UnDigitizedPdfException;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.Rectangle;
import com.gs.ep.docknight.model.testutil.DocUtils;
import com.gs.ep.docknight.model.testutil.DocUtils.ModelCustomizationConfiguration;
import com.gs.ep.docknight.model.transformer.MultiPageToSinglePageTransformer;
import com.gs.ep.docknight.model.transformer.PositionalTextGroupingTransformer;
import com.gs.ep.docknight.model.transformer.TableDetectionTransformer;
import com.gs.ep.docknight.util.BetaMode;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PositionalTextGroupingTransformerRegressionTest {

  // A test case of name xyz consists of file xyz.pdf and optionally files xyz.vertical.html and xyz.tabular.html containing
  // expected elements separated by blank lines to be found by the PositionalTextGroupingTransformer.
  // Multiple such test cases can be present in the test directories.
  private static final Logger LOGGER = LoggerFactory
      .getLogger(PositionalTextGroupingTransformerRegressionTest.class);
  private static final String CUSTOMIZATION_FILE_EXTENSION = ".model.yml";
  private static final MutableList<String> TESTS_REQUIRING_ETABULAR_DETECTION = Lists.mutable
      .of(DocUtils.ETABULAR_TEST);
  private static final MutableList<String> TESTS_REQUIRING_TABULAR_DETECTION = Lists.mutable
      .ofAll(TESTS_REQUIRING_ETABULAR_DETECTION).with(DocUtils.TABULAR_TEST);

  private static String displayTestType(String testType) {
    return testType.substring(0, testType.indexOf('.'));
  }

  private static void populateActualInstances(Map<String, File> files,
      MutableList<Pair<String, ModelCustomizations>> allCustomizations,
      MutableListMultimap<String, String> actualInstances) {
    ModelCustomizations modelCustomizations = getCustomizationsForFile(allCustomizations,
        files.get(DocUtils.INPUT));
    modelCustomizations.add(ModelCustomizationKey.DISABLE_TABLE_DETECTION,
        TESTS_REQUIRING_TABULAR_DETECTION.noneSatisfy(files::containsKey));
    Document document = DocUtils
        .parseAsDocument(files.get(DocUtils.INPUT), new MultiPageToSinglePageTransformer(),
            new PositionalTextGroupingTransformer(modelCustomizations));

    int pageNum = 1;
    for (Element elem : document.getContent().getValue().getElements()) {
      Page page = (Page) elem;
      PositionalElementList<Element> positionalElementList = page.getPositionalContent().getValue();
      if (files.containsKey(DocUtils.VERTICAL_TEST)) {
        for (ElementGroup<Element> verticalGroup : positionalElementList.getVerticalGroups()) {
          if (verticalGroup.size() > 1) {
            actualInstances.put(DocUtils.VERTICAL_TEST, DocUtils.toHtmlString(verticalGroup));
          }
        }
      }
      if (files.containsKey(DocUtils.TABULAR_TEST)) {
        for (TabularElementGroup<Element> tabularGroup : positionalElementList.getTabularGroups()) {
          actualInstances.put(DocUtils.TABULAR_TEST, DocUtils.toHtmlString(tabularGroup));
        }
      }

      if (positionalElementList.getNumberOfPageBreaks() > 0 && (
          files.containsKey(DocUtils.HEADER_TEST) || files.containsKey(DocUtils.FOOTER_TEST))) {
        Predicate<Element> isNotFooter = e -> e.hasPositionalContext()
            && e.getPositionalContext().getPagePartitionType() != PagePartitionType.FOOTER;
        Predicate<Element> isNotHeader = e -> e.hasPositionalContext()
            && e.getPositionalContext().getPagePartitionType() != PagePartitionType.HEADER;
        for (int pageNo = 0; pageNo <= positionalElementList.getNumberOfPageBreaks(); ++pageNo) {
          MutableList<Element> elements = ListAdapter
              .adapt(positionalElementList.getElementsBetweenPageBreaks(pageNo, pageNo + 1));
          if (files.containsKey(DocUtils.HEADER_TEST)) {
            int headerEnd = elements.detectIndex(isNotHeader);
            actualInstances.put(DocUtils.HEADER_TEST, "<b>Header:&nbsp;" + pageNum + "</b>\n"
                + DocUtils.toHtmlString(new ElementGroup<>(
                elements.subList(0, headerEnd >= 0 ? headerEnd : elements.size())
                    .selectInstancesOf(Rectangle.class))));
          }
          if (files.containsKey(DocUtils.FOOTER_TEST)) {
            int footerStart = elements.detectLastIndex(isNotFooter) + 1;
            actualInstances.put(DocUtils.FOOTER_TEST, "<b>Footer:&nbsp;" + pageNum + "</b>\n"
                + DocUtils.toHtmlString(new ElementGroup<>(
                elements.subList(footerStart, elements.size())
                    .selectInstancesOf(Rectangle.class))));
          }
          pageNum++;
        }
      }
    }

    if (TESTS_REQUIRING_ETABULAR_DETECTION.anySatisfy(files::containsKey)) {
      document = new TableDetectionTransformer(modelCustomizations).transform(document);

      for (Element elem : document.getContent().getValue().getElements()) {
        Page page = (Page) elem;
        PositionalElementList<Element> positionalElementList = page.getPositionalContent()
            .getValue();
        for (TabularElementGroup<Element> tabularGroup : positionalElementList.getTabularGroups()) {
          actualInstances.put(DocUtils.ETABULAR_TEST, DocUtils.toHtmlString(tabularGroup));
        }
      }
    }


  }

  private static Diff compareStrings(String testCaseName, String testCaseType,
      MutableList<String> actualStrings, MutableList<String> expectedStrings) {
    int numMatches = 0;
    MutableList<String> expectedUnMatchedSide = Lists.mutable.empty();
    actualStrings = Lists.mutable.ofAll(actualStrings);
    for (String str : expectedStrings) {
      if (actualStrings.remove(str)) {
        numMatches++;
      } else {
        expectedUnMatchedSide.add(str);
      }
    }
    return new Diff(testCaseName, testCaseType, expectedUnMatchedSide.makeString("\n\n"),
        actualStrings.makeString("\n\n"), numMatches);
  }

  private static MutableList<Pair<String, ModelCustomizations>> readCustomizationFileIfExists(
      String rootDirName) throws Exception {
    File root = new File(rootDirName);
    MutableList<File> allCustomizationConfigFiles = (root.isDirectory() ? Lists.mutable
        .ofAll(FileUtils.listFiles(root, null, true)) : Lists.mutable.of(root))
        .select(file -> file.getAbsolutePath().endsWith(CUSTOMIZATION_FILE_EXTENSION));
    MutableMap<String, ModelCustomizations> allCustomizations = Maps.mutable.empty();
    for (File file : allCustomizationConfigFiles) {
      String configPath = file.getParentFile().getAbsolutePath();
      ModelCustomizationConfigurationsList modelCustomizationConfigurationsList = DocUtils
          .readTestCasesFromYaml(file, ModelCustomizationConfigurationsList.class);
      if (modelCustomizationConfigurationsList != null) {
        modelCustomizationConfigurationsList.customizations.forEach(customization ->
        {
          String path = configPath + File.separator + customization.path;
          ModelCustomizations modelCustomizations = allCustomizations
              .getOrDefault(path, new ModelCustomizations());
          ModelCustomizationKey customizationKey = ModelCustomizationKey.getKey(customization.type);
          modelCustomizations.add(customizationKey,
              customizationKey.parse(Lists.mutable.ofAll(customization.params)));
          allCustomizations.put(path, modelCustomizations);
        });
      }
    }
    return allCustomizations.keyValuesView().toSortedListBy(p -> p.getOne().length());
  }

  private static ModelCustomizations getCustomizationsForFile(
      List<Pair<String, ModelCustomizations>> allCustomizations, File file) {
    String filePath = file.getAbsolutePath();
    ModelCustomizations fileCustomizations = new ModelCustomizations();
    for (Pair<String, ModelCustomizations> customizationPair : allCustomizations) {
      if (filePath.startsWith(customizationPair.getOne())) {
        ModelCustomizations currentCustomizations = customizationPair.getTwo();
        currentCustomizations.getStoredKeys().forEach(key ->
            fileCustomizations.add(key, currentCustomizations.retrieveOrDefault(key, null)));
      }
    }
    return fileCustomizations;
  }

  @Test
  @Ignore
  public void test() throws Exception {
    String rootDir = "RegressionTestDataDirectory";
    MutableList<String> testTypes = Lists.mutable
        .of(DocUtils.VERTICAL_TEST, DocUtils.TABULAR_TEST, DocUtils.ETABULAR_TEST,
            DocUtils.HEADER_TEST, DocUtils.FOOTER_TEST);
    boolean regressBetweenBetaModes = false;
    boolean inplaceUpdate = false;
    MutableMap<String, Map<String, File>> testCases = DocUtils
        .findTestCases(rootDir, DocUtils.POSSIBLE_EXTENSIONS, DocUtils.VALID_INPUT_EXTENSIONS,
            regressBetweenBetaModes ? Lists.mutable.of(DocUtils.INPUT) : testTypes);
    //testCases = testCases.select((k, v) -> k.equals("PPM EARF"));

    MutableList<Pair<String, ModelCustomizations>> allCustomizations = readCustomizationFileIfExists(
        rootDir);
    MutableList<TestData> testDataList = Lists.mutable.empty();
    MutableList<String> filterInputLocations = Lists.mutable.of();

    int i = 1;
    for (Entry<String, Map<String, File>> testCase : testCases.entrySet()) {
      String inputLocation = testCase.getValue().get(DocUtils.INPUT).getAbsolutePath();
      if (filterInputLocations.notEmpty() && filterInputLocations
          .noneSatisfy(inputLocation::contains)) {
        continue;
      }

      System.out.println("Case " + i + "/" + testCases.size() + "  [" + testCase.getKey() + "]");
      i++;
      TestData testData = new TestData(testCase);
      testDataList.add(testData);
      try {
        if (regressBetweenBetaModes) {
          testData.testFiles.putAll(Lists.mutable.ofAll(testTypes).toMap(x -> x, x -> null));
          BetaMode.run(() -> populateActualInstances(testData.testFiles, allCustomizations,
              testData.actualInstances));
        } else {
          populateActualInstances(testData.testFiles, allCustomizations, testData.actualInstances);
        }
      } catch (Exception e) {
        testData.hasException = true;
        e.printStackTrace();
      }

      if (regressBetweenBetaModes && !testData.hasException) {
        try {
          populateActualInstances(testData.testFiles, allCustomizations,
              testData.expectedInstances);
        } catch (Exception e) {
          testData.hasException = true;
          e.printStackTrace();
        }
      }

      for (String testType : testTypes) {
        if (inplaceUpdate) {
          this.updateFileWithInstances(testData.testFiles.get(testType), testData.actualInstances,
              testType);
        }
        Diff diff = compareStrings(testData.testCaseName, displayTestType(testType),
            testData.actualInstances.get(testType), testData.expectedInstances.get(testType));
        testData.diffs.put(testType, diff);
      }
    }

    System.out.println(String.format("TotalTestCases : %d\n", testCases.size()));

    MutableList<String> testCasesWithException = testDataList
        .collectIf(t -> t.hasException, t -> t.testCaseName);
    if (testCasesWithException.notEmpty()) {
      System.out.println("TestCasesWithException : " + testCasesWithException.makeString() + "\n");
    }

    for (String testType : testTypes) {
      long total = testDataList.sumOfInt(t -> t.expectedInstances.get(testType).size());
      long found = testDataList.sumOfInt(t -> t.diffs.get(testType).numMatches);
      if (total > 0) {
        System.out.println(String
            .format(displayTestType(testType) + "(s) : %.2f%% (%d / %d)\n", found * 100.0 / total,
                found, total));
      }
    }

    MutableList<MutableList<Diff>> allMissedItems = Lists.mutable.empty();
    for (String testType : testTypes) {
      MutableList<Diff> missed = testDataList
          .collectIf(t -> !t.diffs.get(testType).expectedUnMatchedSide.isEmpty(),
              t -> t.diffs.get(testType));
      if (!missed.isEmpty()) {
        System.out.println(
            "Missed " + displayTestType(testType) + "(s):\n" + missed.collect(d -> d.testCaseName)
                .makeString("\n") + "\n");
        allMissedItems.add(missed);
      }
    }

    String expectedAssertionString = allMissedItems
        .flatCollect(diffs -> diffs.collect(Diff::getExpectedAssertionString)).makeString("\n\n");
    String actualAssertionString = allMissedItems
        .flatCollect(diffs -> diffs.collect(Diff::getActualAssertionString)).makeString("\n\n");

    assertEquals(expectedAssertionString, actualAssertionString);
  }

  @Test
  @Ignore
  public void generateTestFiles() throws Exception {
    MutableList<String> directoryPrefixes = Lists.mutable
        .of();
    for (int k = 0; k < directoryPrefixes.size(); k++) {
      String instance = directoryPrefixes.get(k);
      String directorySuffix = "_OrgAlias";
      LOGGER.info("Running for directory {}/{} :  {}{}", k + 1, directoryPrefixes.size(), instance,
          directorySuffix);
      String inputLocation = "H:\\projects\\docknightregression\\src\\main\\resources\\testdata\\*"
          .replace("*", instance);
      String outputDir = ("H:\\projects\\docknightregression\\src\\main\\resources\\testdata\\*"
          + directorySuffix).replace("*", instance);
      String customizationsRootDir = "H:\\projects\\docknightregression\\src\\main\\resources\\testdata";

      String testType = DocUtils.ETABULAR_TEST;
      boolean update = false;

      File outDirFile = new File(outputDir);
      if (!outDirFile.exists()) {
        outDirFile.mkdirs();
      }

      File inputFile = new File(inputLocation);
      MutableList<Pair<String, ModelCustomizations>> allCustomizations = readCustomizationFileIfExists(
          customizationsRootDir);
      List<File> files = DocUtils.getFiles(inputFile, DocUtils.VALID_INPUT_EXTENSIONS);
      for (int j = 0; j < files.size(); j++) {
        File f = files.get(j);
        LOGGER.info("Running for file {}/{} : {} in directory {}/{} : {}{}", j + 1, files.size(),
            f.getName(),
            k + 1, directoryPrefixes.size(), instance, directorySuffix);
        String name = f.getName();
        int i = name.lastIndexOf('.');
        String testCaseName = i > 0 ? name.substring(0, i) : "";
        try {
          File outFile = new File(outputDir, testCaseName + "." + testType);
          MutableListMultimap<String, String> actualInstances = Multimaps.mutable.list.empty();
          populateActualInstances(Maps.mutable.of(DocUtils.INPUT, f, testType, outFile),
              allCustomizations, actualInstances);
          MutableList<String> newStrings = actualInstances.get(testType);

          if (update) {
            MutableList<String> existingStrings =
                outFile.exists() ? DocUtils.readTestCasesSeparatedByBlankLines(outFile)
                    : Lists.mutable.empty();
            newStrings = DocUtils.findHtmlStringsToUpdate(newStrings, existingStrings);
          }
          String newString = newStrings.makeString("\n\n").trim();

          if (!newString.isEmpty()) {
            PrintWriter out = new PrintWriter(outFile, "UTF-8");
            out.println(newString);
            out.close();
            //java.awt.Desktop.getDesktop().browse(outFile.toURI());
          } else if (!update) {
            System.out.println(name + ": No instances detected");
          }
        } catch (Exception e) {
          if (e.getCause() instanceof UnDigitizedPdfException) {
            System.out.println(e.getMessage() + " -> " + e.getCause().getMessage());
          } else {
            e.printStackTrace();
          }
        }
      }
    }
  }

  private void updateFileWithInstances(File outFile,
      MutableListMultimap<String, String> actualInstances, String testType) throws IOException {
    if (outFile != null) {
      MutableList<String> newStrings = actualInstances.get(testType);
      MutableList<String> existingStrings =
          outFile.exists() ? DocUtils.readTestCasesSeparatedByBlankLines(outFile)
              : Lists.mutable.empty();
      newStrings = DocUtils.findHtmlStringsToUpdate(newStrings, existingStrings);

      String newString = newStrings.makeString("\n\n").trim();
      if (!newString.isEmpty()) {
        try (PrintWriter out = new PrintWriter(outFile, "UTF-8")) {
          out.println(newString);
        }
      }
    }
  }

  private static class TestData {

    String testCaseName;
    Map<String, File> testFiles;
    MutableListMultimap<String, String> expectedInstances = Multimaps.mutable.list.empty();
    MutableListMultimap<String, String> actualInstances = Multimaps.mutable.list.empty();
    MutableMap<String, Diff> diffs = Maps.mutable.empty();
    boolean hasException;

    TestData(Entry<String, Map<String, File>> testCase) throws IOException {
      this.testCaseName = testCase.getKey();
      this.testFiles = testCase.getValue();
      for (Entry<String, File> testFile : this.testFiles.entrySet()) {
        if (!testFile.getKey().equals(DocUtils.INPUT)) {
          this.expectedInstances.putAll(testFile.getKey(),
              DocUtils.readTestCasesSeparatedByBlankLines(testFile.getValue()));
        }
      }
    }
  }

  private static class Diff {

    private final String testCaseName;
    private final String testCaseType;
    private final String expectedUnMatchedSide;
    private final String actualUnMatchedSide;
    private final int numMatches;

    Diff(String testCaseName, String testCaseType, String expectedUnMatchedSide,
        String actualUnMatchedSide, int numMatches) {
      this.testCaseName = testCaseName;
      this.testCaseType = testCaseType;
      this.expectedUnMatchedSide = expectedUnMatchedSide;
      this.actualUnMatchedSide = actualUnMatchedSide;
      this.numMatches = numMatches;
    }

    private String getActualAssertionString() {
      return "[" + this.testCaseType + "]  " + this.testCaseName + ":\n\n"
          + this.actualUnMatchedSide;
    }

    private String getExpectedAssertionString() {
      return "[" + this.testCaseType + "]  " + this.testCaseName + ":\n\n"
          + this.expectedUnMatchedSide;
    }
  }

  private static class ModelCustomizationConfigurationsList {

    public List<ModelCustomizationConfiguration> customizations = Lists.mutable.empty();
  }

}
