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

package com.gs.ep.docknight.model.element;

import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.tuple.Tuples;
import com.gs.ep.docknight.model.ElementAttribute;
import com.gs.ep.docknight.model.attribute.Content;
import com.gs.ep.docknight.model.attribute.PageFooter;
import com.gs.ep.docknight.model.attribute.PageHeader;
import com.gs.ep.docknight.model.attribute.PageMargin;
import com.gs.ep.docknight.model.attribute.PageSize;
import com.gs.ep.docknight.model.attribute.PageStructure;
import java.util.List;

/**
 * Document model class representing document structure. It contains list of pages and page
 * information
 */
public class Document extends TextRectangle<Document> implements
    Content.Holder<Document>,
    PageStructure.Holder<Document>,
    PageHeader.Holder<Document>,
    PageFooter.Holder<Document>,
    PageMargin.Holder<Document>,
    PageSize.Holder<Document> {

  private static final long serialVersionUID = 1665879592179663643L;
  private String documentSource = Document.class.getSimpleName();
  private transient List<Pair<SourceType, byte[]>> transformedIntermediateSources = Lists.mutable
      .empty();

  @Override
  public List<Class<? extends ElementAttribute>> getDefaultLayout() {
    return Lists.mutable.of(Content.class);
  }

  public String getDocumentSource() {
    return this.documentSource;
  }

  public void setDocumentSource(String documentSource) {
    this.documentSource = documentSource;
  }

  public List<Pair<SourceType, byte[]>> getTransformedIntermediateSources() {
    return this.transformedIntermediateSources;
  }

  public void addTransformedIntermediateSource(SourceType sourceType,
      byte[] transformedIntermediateSource) {
    this.transformedIntermediateSources.add(Tuples.pair(sourceType, transformedIntermediateSource));
  }

  public void addTransformedIntermediateSource(
      Pair<SourceType, byte[]> transformedIntermediateSource) {
    this.transformedIntermediateSources.add(transformedIntermediateSource);
  }

  /**
   * Document can be preprocessed with different ocr engines. This enum is used to track those
   * preprocessing steps.
   */
  public enum SourceType {
    OCRED_DOC    // Used to represent document processed by Abbyy engine
  }
}
