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

package com.gs.ep.docknight.model.example;

import com.gs.ep.docknight.model.converter.PdfParser;
import com.gs.ep.docknight.model.converter.ScannedPdfParser;
import com.gs.ep.docknight.model.converter.ScannedPdfParser.OCREngine;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.transformer.MultiPageToSinglePageTransformer;
import com.gs.ep.docknight.model.transformer.PositionalTextGroupingTransformer;
import com.gs.ep.docknight.util.DisplayUtils;
import com.gs.ep.docknight.util.abbyy.AbbyyProperties;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Example code to use Abbyy. If Abbyy SDK is installed locally it will use local Engine calls
 * otherwise will make server call to the abby end point defined in properties/*-abbyy.properties.
 * Use this only when testing abbyy.
 */

public final class ScannedPdfToDocModelUsingAbbyyExample {

  private ScannedPdfToDocModelUsingAbbyyExample() {
  }

  public static void main(String[] args) {
    Properties properties = new Properties();
    properties.setProperty("abbyy.customerProjectId", "fvVmhweev2rCSrhk3f6X");
    properties
        .setProperty("abbyy.abbyyurl", "http://host:port/abbyy/convertScannedPdf");
    properties.setProperty("abbyy.dllFolder64Bit",
        "\\abbyyBaseDirectory\\abby\\FineReader Engine\\Bin64"); //Not used because it uses server api
    AbbyyProperties.setAbbyyProperties(properties);

    try (Stream<Path> walk = Files.walk(Paths
        .get("directory/with/files/to/be/parsed"))) {

      List<String> result = walk.filter(Files::isRegularFile)
          .map(x -> x.toString()).collect(Collectors.toList());

      for (String filePath : result) {
        FileInputStream inputStream = new FileInputStream(filePath);
        Document document = new PdfParser()
            .withScannedPdfParser(new ScannedPdfParser(OCREngine.ABBYY))
            .parse(inputStream);
        document = new MultiPageToSinglePageTransformer().transform(document);
        document = new PositionalTextGroupingTransformer().transform(document);
        DisplayUtils.displayHtml(document, true);
        inputStream.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
