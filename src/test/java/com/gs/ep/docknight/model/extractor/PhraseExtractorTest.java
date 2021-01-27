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

package com.gs.ep.docknight.model.extractor;

import static com.gs.ep.docknight.model.extractor.PhraseExtractor.jsonify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Sets;
import com.gs.ep.docknight.model.ModelCustomizationKey;
import com.gs.ep.docknight.model.ModelCustomizations;
import com.gs.ep.docknight.model.TabularElementGroup.GridType;
import com.gs.ep.docknight.model.converter.ScannedPdfParser;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.testutil.BoundingBox;
import com.gs.ep.docknight.model.testutil.DocUtils;
import com.gs.ep.docknight.model.testutil.GroupedBoundingBox;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import com.gs.ep.docknight.model.transformer.MultiPageToSinglePageTransformer;
import com.gs.ep.docknight.model.transformer.PositionalTextGroupingTransformer;
import com.gs.ep.docknight.model.transformer.TableDetectionTransformer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import mockit.Mock;
import mockit.MockUp;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;

public class PhraseExtractorTest {

  @Test
  public void testExtractionWithMixedLayout() throws IOException {
    String inputFilePath = Thread.currentThread().getContextClassLoader()
        .getResource("Grouping.pdf")
        .getPath();
    Path tempOutputDir = Files.createTempDirectory(null);
    String[] arguments = {inputFilePath, tempOutputDir.toString(), "--mixed-layout", "--html"};
    new PhraseExtractor().main(arguments);
    File expectedJsonFile = new File(Thread.currentThread().getContextClassLoader()
        .getResource("GroupingWithMixedLayout.json").getPath());
    String expectedJson = new String(
        Files.readAllBytes(expectedJsonFile.toPath()));
    File actualJsonFile = new File(tempOutputDir.toString() + "/vj/Grouping.json");
    String actualJson = new String(
        Files.readAllBytes(actualJsonFile.toPath()));
    assertEquals(DocUtils.prettifyAndSortJson(expectedJson),
        DocUtils.prettifyAndSortJson(actualJson));

    File expectedHtmlFile = new File(Thread.currentThread().getContextClassLoader()
        .getResource("GroupingWithMixedLayout.html").getPath());
    String expectedHtml = new String(
        Files.readAllBytes(expectedHtmlFile.toPath()));
    File actualHtmlFile = new File(tempOutputDir.toString() + "/html/Grouping.html");
    String actualHtml = new String(
        Files.readAllBytes(actualHtmlFile.toPath()));
    assertEquals(expectedHtml, actualHtml);
    FileUtils.deleteDirectory(new File(tempOutputDir.toString()));
  }

  @Test
  public void testExtractionWithoutMixedLayout() throws IOException {
    String inputFilePath = Thread.currentThread().getContextClassLoader()
        .getResource("Grouping.pdf")
        .getPath();
    Path tempOutputDir = Files.createTempDirectory(null);
    String[] arguments = {inputFilePath, tempOutputDir.toString(), "--html"};
    new PhraseExtractor().main(arguments);
    // TODO - mock getVersion in PhraseExtractor then uncomment below lines
//    File expectedJsonFile = new File(Thread.currentThread().getContextClassLoader()
//        .getResource("GroupingPhraseWithoutMixedLayout.json").getPath());
//    String expectedJson = new String(
//        Files.readAllBytes(expectedJsonFile.toPath()));
//    File actualJsonFile = new File(tempOutputDir.toString() + "/phrase/Grouping.json");
//    String actualJson = new String(
//        Files.readAllBytes(actualJsonFile.toPath()));
//    assertEquals(DocUtils.prettifyAndSortJson(expectedJson),
//        DocUtils.prettifyAndSortJson(actualJson));

    File expectedJsonFile = new File(Thread.currentThread().getContextClassLoader()
        .getResource("GroupingTableWithoutMixedLayout.json").getPath());
    String expectedJson = new String(
        Files.readAllBytes(expectedJsonFile.toPath()));
    File actualJsonFile = new File(tempOutputDir.toString() + "/table/Grouping.table.json");
    String actualJson = new String(
        Files.readAllBytes(actualJsonFile.toPath()));
    assertEquals(DocUtils.prettifyAndSortJson(expectedJson),
        DocUtils.prettifyAndSortJson(actualJson));

    File expectedHtmlFile = new File(Thread.currentThread().getContextClassLoader()
        .getResource("GroupingWithoutMixedLayout.html").getPath());
    String expectedHtml = new String(
        Files.readAllBytes(expectedHtmlFile.toPath()));
    File actualHtmlFile = new File(tempOutputDir.toString() + "/html/Grouping.html");
    String actualHtml = new String(
        Files.readAllBytes(actualHtmlFile.toPath()));
    assertEquals(expectedHtml, actualHtml);
    FileUtils.deleteDirectory(new File(tempOutputDir.toString()));
  }

  @Test
  public void testTableExtractor() throws IOException {
    String filePath = Thread.currentThread().getContextClassLoader()
        .getResource("TableEnrichmentTest.pdf")
        .getPath();
    Document document = DocUtils
        .parseAsDocument(new File(filePath), new MultiPageToSinglePageTransformer(),
            new PositionalTextGroupingTransformer(), new TableDetectionTransformer());
    Map<String, Object> tables = (Map<String, Object>) TableExtractor
        .extract(document, "TableEnrichmentTest.pdf");
    File expectedJsonFileForTableExtractor = new File(Thread.currentThread().getContextClassLoader()
        .getResource("parser/ExpectedJsonForTableExtractor.json").getPath());
    String expectedJsonForTableExtractor = new String(
        Files.readAllBytes(expectedJsonFileForTableExtractor.toPath()));
    assertEquals(DocUtils.prettifyAndSortJson(expectedJsonForTableExtractor),
        DocUtils.prettifyAndSortJson(jsonify(tables)));
  }

  @Test
  public void testExtractor() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    GroupedBoundingBox paraBox = new GroupedBoundingBox(100, 100, 300, 300);
    String text =
        "  This is phrase extractor. It is test for testing that it works. In case it does not then, this test will fail.\n\n"
            +
            "  This is phrase extractor. It is test for testing that it works. In case it does not then, this test will fail.";
    drawer.drawTextWithBorderInside(paraBox.getFullBBox(), text);
    drawer.addPage();
    BoundingBox bbox = drawer.drawTextAt(100, 100, "This text is ");
    drawer.setFont(PDType1Font.TIMES_BOLD, 12);
    bbox = drawer.drawTextAt(bbox.getTopRightPlus(0, 0), "bold");
    drawer.setFont(PDType1Font.TIMES_ROMAN, 12);
    drawer.drawTextAt(bbox.getTopRightPlus(0, 0), " sometimes");

    Document pdfDocument = PhraseExtractor.parseDocument(drawer.getPdfDocumentStream(), "test.pdf");
    Map<String, Object> pdfPhraseExtract = new PhraseExtractor()
        .extract(pdfDocument, Maps.mutable.empty(), Maps.mutable.empty());
    File expectedJsonFileForPdfParser = new File(Thread.currentThread().getContextClassLoader()
        .getResource("parser/ExpectedJsonForPdfParser.json").getPath());
    String expectedJsonForPdfParser = new String(
        Files.readAllBytes(expectedJsonFileForPdfParser.toPath()));
    assertEquals(DocUtils.prettifyAndSortJson(expectedJsonForPdfParser),
        DocUtils.prettifyAndSortJson(jsonify(pdfPhraseExtract)));
  }

  @Test
  public void testCreateModelCustomizationsFromFile() throws IOException {
    String filePath = Thread.currentThread().getContextClassLoader()
        .getResource("modelCustomizations.json").getPath();
    ModelCustomizations modelCustomizations = PhraseExtractor
        .getModelCustomizationsFromFile(filePath);
    assertEquals(Sets.mutable.of(ModelCustomizationKey.OCR_ENGINE,
        ModelCustomizationKey.ENABLE_GRID_BASED_TABLE_DETECTION),
        modelCustomizations.getStoredKeys());
    assertEquals(ScannedPdfParser.OCREngine.TESSERACT,
        modelCustomizations.retrieveOrDefault(ModelCustomizationKey.OCR_ENGINE, null));
    assertEquals(GridType.ROW_AND_MAYBE_COL,
        modelCustomizations
            .retrieveOrDefault(ModelCustomizationKey.ENABLE_GRID_BASED_TABLE_DETECTION, null));

    modelCustomizations = PhraseExtractor.getModelCustomizationsFromFile("test");
    assertEquals("NONE", modelCustomizations.toString());
  }

  @Test
  @Ignore("Disk operation")
  public void testCreateOutputFile() throws IOException {
    // test ocr file with file extension
//        assertFalse(Files.exists(Paths.get("H:\\phraseExtractor", "ocr", "sample.pdf")));
//        assertFalse(Files.exists(Paths.get("H:\\phraseExtractor", "ocr")));
    String testData = "test Data";
    PhraseExtractor
        .createOutputFile(Paths.get("H:\\phraseExtractor", "ocr"), new File("sample.pdf"), "pdf",
            testData.getBytes());
    Path ocrPath = Paths.get("H:\\phraseExtractor", "ocr", "sample.pdf");
    assertTrue(Files.exists(ocrPath));
    assertTrue(Files.exists(Paths.get("H:\\phraseExtractor", "ocr")));
    assertEquals(Lists.mutable.of(testData), Files.readAllLines(ocrPath));

    // test ocr file with null bytes
    PhraseExtractor
        .createOutputFile(Paths.get("H:\\phraseExtractor", "ocr"), new File("sample.pdf"), "pdf",
            null);
    assertFalse(Files.exists(ocrPath));

    // test ocr file with empty bytes
    PhraseExtractor
        .createOutputFile(Paths.get("H:\\phraseExtractor", "ocr"), new File("sample.pdf"), "pdf",
            new byte[]{});
    assertFalse(Files.exists(ocrPath));

    // test ocr file without file extension
    PhraseExtractor
        .createOutputFile(Paths.get("H:\\phraseExtractor", "ocr"), new File("sample"), "pdf",
            new byte[]{42});
    assertTrue(Files.exists(ocrPath));
    Files.delete(ocrPath);
  }
}
