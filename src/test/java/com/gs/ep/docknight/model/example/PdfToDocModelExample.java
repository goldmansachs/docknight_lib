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

import com.gs.ep.docknight.model.Transformer;
import com.gs.ep.docknight.model.converter.PdfParser;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.transformer.MultiPageToSinglePageTransformer;
import com.gs.ep.docknight.model.transformer.PositionalTextGroupingTransformer;
import com.gs.ep.docknight.model.transformer.TableDetectionTransformer;
import java.io.File;
import java.io.FileInputStream;

public final class PdfToDocModelExample {

  private PdfToDocModelExample() {
  }

  public static void main(String[] args) throws Exception {
    String filePath = Thread.currentThread().getContextClassLoader().getResource("Grouping.pdf")
        .getPath();

    Document document = new PdfParser().parse(new FileInputStream(new File(filePath)));
    document = applyTransformersOnDocument(document, new MultiPageToSinglePageTransformer(),
        new PositionalTextGroupingTransformer(), new TableDetectionTransformer());
  }

  @SafeVarargs
  public static Document applyTransformersOnDocument(Document document,
      Transformer<Document, Document>... transformers) {
    for (Transformer<Document, Document> transformer : transformers) {
      document = transformer.transform(document);
    }
    return document;
  }
}
