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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import com.gs.ep.docknight.model.converter.PdfParser.UnDigitizedPdfException;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.testutil.JsonRenderer;
import com.gs.ep.docknight.model.testutil.DocUtils;
import com.gs.ep.docknight.util.BetaMode;
import java.io.File;
import java.util.List;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

public class ParserBetaModeTest {

  @Rule
  public ErrorCollector collector = new ErrorCollector();
  private int successes;
  private int failures;
  private int exceptions;

  private static Document parseDocument(File file) {
    Document document = null;
    try {
      document = DocUtils.parseAsDocument(file);
    } catch (Exception e) {
      if (e.getCause() instanceof UnDigitizedPdfException) {
        System.out.println(e.getMessage() + " -> " + e.getCause().getMessage());
      } else {
        e.printStackTrace();
      }
    }
    return document;
  }

  @Test
  @Ignore
  public void test() throws Exception {
    String rootDir = "RegressionTestDataDirectory";
    List<File> files = DocUtils.getFiles(new File(rootDir), DocUtils.POSSIBLE_EXTENSIONS);
    //MutableList<String> inputExtensions = DocUtils.VALID_INPUT_EXTENSIONS;
    MutableList<String> inputExtensions = Lists.mutable.of(DocUtils.PDF);

    for (File file : files) {
      if (inputExtensions
          .contains(DocUtils.getValidExtension(file, DocUtils.POSSIBLE_EXTENSIONS))) {
        long initialIsEnabledCount = BetaMode.getIsEnabledCounter();
        Document document = parseDocument(file);
        if (document != null && BetaMode.getIsEnabledCounter() > initialIsEnabledCount) {
          String expectedJson = new JsonRenderer().render(document);
          BetaMode.run(() ->
          {
            Document betaDocument = parseDocument(file);
            if (betaDocument != null) {
              try {
                DocUtils.assertDocumentJson(expectedJson, betaDocument);
                this.successes++;
              } catch (Throwable t) {
                this.collector.addError(t);
                this.failures++;
              }
            } else {
              this.exceptions++;
            }
          });
        } else {
          this.exceptions++;
        }
      }
    }
    if (BetaMode.getIsEnabledCounter() == 0) {
      throw new Exception("No part of the code was run in Beta Mode!");
    }
    System.out.println(String
        .format("Success: %d/%d, Exceptions: %d", this.successes, this.successes + this.failures,
            this.exceptions));
  }
}
