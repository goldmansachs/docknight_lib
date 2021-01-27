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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gs.ep.docknight.model.ModelCustomizations;
import com.gs.ep.docknight.model.converter.ScannedPdfParser.OCREngine;
import com.gs.ep.docknight.model.extractor.PhraseExtractor;
import com.gs.ep.docknight.model.testutil.DocUtils;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.utility.Iterate;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

public class PhraseExtractorRegressionTest {

  @Rule
  public ErrorCollector collector = new ErrorCollector();

  private static String stringCleanUp(String value) {
    return value.replaceAll("(\\{|,)\\s*\\\"box\\\"\\s*:\\s*\\{[^\\}]*}", "")
        .replaceAll("(\\{|,)\\s*\\\"span\\\"\\s*:\\s*\\{[^\\}]*}", "");
  }

  @Ignore
  @Test
  public void testVisualJsonForPhraseExtractor() throws Exception {
    String inputPath = "W:\\DocViz\\RegressionData\\Expected"; // directoryWithPdfAndInputVJ
    String outputPath = "W:\\DocViz\\RegressionData\\Actual";  // outputDirectory
    int timeoutInSecondsPerDocument = 300;
    int minChars = 0;  // set the min number of parsed chars pdf should have
    boolean lookupRecursively = true;
    boolean ignoreNonRenderableText = true;  // set to true if you want to ignore any overlayed text in document
    boolean mixedLayout = true; // set to true of you want to process mixed layout documents
    boolean html = false; //set to true if you want to generate html as well for debugging
    boolean handWritten = true; // detect hand written areas
    boolean pageLevelOcr = false; // run ocr on page level
    List<Integer> pageNosToOcr = Lists.mutable.of(); // zero based page indexes
    OCREngine ocrEngine = null;  // set to OCREngine.TESSERACT or OCREngine.ABBYY or null
    ModelCustomizations modelCustomizations = new ModelCustomizations();
    boolean removeCoordinates = false;

    MutableList<String> outputExtensions = Lists.mutable.of(DocUtils.JSON, DocUtils.TABLE_JSON);
    MutableList<String> possibleExtensions = Lists.mutable.ofAll(outputExtensions)
        .withAll(DocUtils.VALID_INPUT_EXTENSIONS);

    MutableListMultimap<String, String> testCasesWithError = Multimaps.mutable.list.empty();

    MutableMap<String, Map<String, File>> evaluationTestCases = DocUtils
        .findTestCases(inputPath, possibleExtensions,
            DocUtils.VALID_INPUT_EXTENSIONS, outputExtensions);
    int totalNumberOfTestCases = evaluationTestCases.size();

    evaluationTestCases.forEachKeyValue((testCaseName, testCase) ->
    {
      if (!testCase.containsKey(DocUtils.JSON)) {
        testCasesWithError.put(testCaseName, "No visual json file found in the input path");
      }
      if (!mixedLayout && !testCase.containsKey(DocUtils.TABLE_JSON)) {
        testCasesWithError.put(testCaseName, "No visual json table file found in the input path");
      }
    });

    PhraseExtractor.extractFromMultipleFiles(inputPath, outputPath, lookupRecursively,
        timeoutInSecondsPerDocument, pageNosToOcr,
        ocrEngine, ignoreNonRenderableText, mixedLayout, html, minChars, handWritten, pageLevelOcr,
        modelCustomizations);

    Map<String, File> outputFiles = Iterate
        .toMap(DocUtils.getFiles(new File(outputPath), outputExtensions),
            File::getName, file -> file);

    for (Entry<String, Map<String, File>> fileNameToTestCaseMappings : evaluationTestCases
        .entrySet()) {
      Map<String, File> testCase = fileNameToTestCaseMappings.getValue();
      for (Entry<String, File> extensionToFileMappings : testCase.entrySet()) {
        try {
          String fileName = extensionToFileMappings.getValue().getName();

          if (extensionToFileMappings.getKey().equals(DocUtils.INPUT)) {
            continue;
          }
          if (!outputFiles.containsKey(fileName)) {
            testCasesWithError.put(fileNameToTestCaseMappings.getKey(),
                "Error generating visual json. " + fileName + " not found in the output directory");
            continue;
          }
          File outputFile = outputFiles.get(fileName);

          if (mixedLayout) {
            ObjectMapper objectMapper = new ObjectMapper();

            Map<String, Object> expectedJsonObject = objectMapper
                .readValue(extensionToFileMappings.getValue(), Map.class);
            String expectedTableJsonString = PhraseExtractor
                .jsonify(expectedJsonObject.get("tables"));
            String expectedPhraseJsonString = PhraseExtractor
                .jsonify(expectedJsonObject.get("phrases"));

            Map<String, Object> actualJsonObject = objectMapper.readValue(outputFile, Map.class);
            String actualTableJsonString = PhraseExtractor.jsonify(actualJsonObject.get("tables"));
            String actualPhraseJsonString = PhraseExtractor
                .jsonify(actualJsonObject.get("phrases"));

            if (removeCoordinates) {
              expectedPhraseJsonString = stringCleanUp(expectedPhraseJsonString);
              expectedTableJsonString = stringCleanUp(expectedTableJsonString);
              actualPhraseJsonString = stringCleanUp(actualPhraseJsonString);
              actualTableJsonString = stringCleanUp(actualTableJsonString);
            }

            if (!expectedPhraseJsonString.trim().replaceAll("\r\n", "\n")
                .equals(actualPhraseJsonString.trim().replaceAll("\r\n", "\n"))) {
              testCasesWithError.put(fileNameToTestCaseMappings.getKey(),
                  "Visual json comparison failure for phrases in file: " + fileName);
              DocUtils.assertJsonStrings(fileName + " phrase", expectedPhraseJsonString,
                  actualPhraseJsonString);
            }
            if (!expectedTableJsonString.trim().replaceAll("\r\n", "\n")
                .equals(actualTableJsonString.trim().replaceAll("\r\n", "\n"))) {
              testCasesWithError.put(fileNameToTestCaseMappings.getKey(),
                  "Visual json comparison failure for tables in file: " + fileName);
              DocUtils.assertJsonStrings(fileName + " table", expectedTableJsonString,
                  actualTableJsonString);
            }
          } else {
            String expectedValue = new String(
                Files.readAllBytes(extensionToFileMappings.getValue().toPath()));
            String actualValue = new String(Files.readAllBytes(outputFile.toPath()));
            expectedValue = stringCleanUp(expectedValue);
            actualValue = stringCleanUp(actualValue);

            if (removeCoordinates) {
              expectedValue = stringCleanUp(expectedValue);
              actualValue = stringCleanUp(actualValue);
            }

            if (!expectedValue.trim().replaceAll("\r\n", "\n")
                .equals(actualValue.trim().replaceAll("\r\n", "\n"))) {
              testCasesWithError.put(fileNameToTestCaseMappings.getKey(),
                  "Visual json comparison failure in file: " + fileName);
              DocUtils.assertJsonStrings(fileName, expectedValue, actualValue);
            }
          }
        } catch (Throwable t) {
          this.collector.addError(t);
        }
      }
    }

    int numberOfTestCaseFailure = Iterate.sizeOf(testCasesWithError.keysView());
    System.out.println("\n\nTest Case Failures:");
    testCasesWithError.forEachKey(testCaseName ->
    {
      System.out.println(String.format("\n\n\"%s\": ", testCaseName));
      testCasesWithError.get(testCaseName).each(System.out::println);
    });

    System.out.println(String
        .format("\n\nTotal number of test cases:%d passed:%d Failed:%d", totalNumberOfTestCases,
            totalNumberOfTestCases - numberOfTestCaseFailure, numberOfTestCaseFailure));
  }
}
