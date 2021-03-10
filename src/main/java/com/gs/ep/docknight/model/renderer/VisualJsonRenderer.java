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

package com.gs.ep.docknight.model.renderer;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.collections.impl.factory.Maps;
import com.gs.ep.docknight.model.Renderer;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.extractor.PhraseExtractor;
import com.gs.ep.docknight.model.extractor.TableExtractor;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Render to generate visual json from {@see com.gs.ep.docknight.model.element.Document document
 * model}
 */
public class VisualJsonRenderer implements Renderer<String> {

  protected static final Logger LOGGER = LoggerFactory.getLogger(VisualJsonRenderer.class);

  /**
   * Render the {@code document} into map object
   *
   * @param document document model
   * @return map representation of document model
   */
  public Map<String, Object> renderAsMap(Document document) {
    //Assumes PTGT  and MPTSPT (as well) has been run on the document
    long startTime = System.currentTimeMillis();
    Map<String, Object> phrases = new PhraseExtractor().extractInVGOrder(document);
    Map<String, Object> tables = (Map<String, Object>) TableExtractor
        .extract(document, document.getName(), false);
    Map<String, Object> combined = Maps.mutable.of("phrases", phrases, "tables", tables);
    float timeTaken = (System.currentTimeMillis() - startTime) / 1000.0f;
    LOGGER
        .info("[{}] : {} took : {}s", document.getDocumentSource(), this.getClass().getSimpleName(),
            timeTaken);
    return combined;
  }

  @Override
  public String render(Document document) {
    try {
      return PhraseExtractor.jsonify(this.renderAsMap(document));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
