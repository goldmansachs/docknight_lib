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

package com.gs.ep.docknight.model.converter.pdfparser;

import org.eclipse.collections.impl.factory.Lists;
import com.gs.ep.docknight.model.converter.ScannedPdfParser;
import java.util.List;
import java.util.function.Consumer;

/**
 * Class to adjust the {@see com.gs.ep.docknight.model.Parser}'s behaviour
 */
public class ParserSettings {

  private ScannedPdfParser scannedPdfParser;
  private boolean fontChangeSegmentation;   // Create text element based on font changes
  private boolean underlineDetection;  // Flag to detect underlines
  private int startPage = 0;                           // Parser will only on the page starting from index startPage
  private int endPage = Integer.MAX_VALUE;      // Parser will only on the page till the index endPage
  private boolean imageBasedCharDetection = true;
  private double lineMergeEpsilon = 1.0; // Epsilon used for merging horizontal and vertical lines
  private List<Integer> pageNosToOcr = Lists.mutable
      .empty(); // page numbers which will be parsed using scanned pdf parser
  private boolean imageBasedFormDetection = true; // boolean to extract form from images or not
  private boolean handWrittenTextDetection = false; // boolearn to detect hand written areas from image
  private double spacingFactor = 1.0; // Factor is used to check whether spacing is same between consecutive words or not. If not, separate text elements are created.
  private Consumer<Integer> badPageSignaler = pageNo -> nothing();  // Consumer on how to handle pages which contain unrecognized glyphs
  private boolean ignoreNonRenderableText;
  private double maxTextElementToLineCountRatio = Double.MAX_VALUE;
  /**
   * Used to {@throws BadTextSegmentationException} if max text element in a line exceeds this
   * ratio
   */
  private boolean dynamicSpaceWidthComputationEnabled;           // Replace space width with space width that is occurring most frequently consecutively in the line if this is true
  private boolean scanned;
  private boolean pageLevelSpacingScaling;   // If space is scaled within the document, then set this to True
  private double allowedScannedness = 1.0;
  /**
   * Used to {@throws PdfParser.UnDigitizedPdfException} if total scannedness of document exceeds
   * allowed Scannedness
   */
  private int minChars = 0;
  /**
   * Used to {@throws PdfParser.UnDigitizedPdfException} if number of characters in document is
   * below minChars
   */
  private boolean pageLevelOcr;  // If true, then scanned pdf parser will work on individual pages if scanned is below allowedScannedness
  private int maxPagesAllowed = Integer.MAX_VALUE;

  /**
   * Used to {@throws MaxPagesAllowedExceededException} if number of pages in document >
   * maxPagesAllowed
   */

  private static void nothing() {
  }

  public int getMaxPagesAllowed() {
    return this.maxPagesAllowed;
  }

  public void setMaxPagesAllowed(int maxPagesAllowed) {
    this.maxPagesAllowed = maxPagesAllowed;
  }

  public boolean isDynamicSpaceWidthComputationEnabled() {
    return this.dynamicSpaceWidthComputationEnabled;
  }

  public void setDynamicSpaceWidthComputationEnabled(boolean dynamicSpaceWidthComputationEnabled) {
    this.dynamicSpaceWidthComputationEnabled = dynamicSpaceWidthComputationEnabled;
  }

  public boolean isScanned() {
    return this.scanned;
  }

  public void setScanned(boolean scanned) {
    this.scanned = scanned;
  }

  public ScannedPdfParser getScannedPdfParser() {
    return this.scannedPdfParser;
  }

  public void setScannedPdfParser(ScannedPdfParser scannedPdfParser) {
    this.scannedPdfParser = scannedPdfParser;
  }

  public boolean isFontChangeSegmentation() {
    return this.fontChangeSegmentation;
  }

  public void setFontChangeSegmentation(boolean fontChangeSegmentation) {
    this.fontChangeSegmentation = fontChangeSegmentation;
  }

  public boolean isUnderlineDetection() {
    return this.underlineDetection;
  }

  public void setUnderlineDetection(boolean underlineDetection) {
    this.underlineDetection = underlineDetection;
  }

  public int getStartPage() {
    return this.startPage;
  }

  public void setStartPage(int startPage) {
    this.startPage = startPage;
  }

  public int getEndPage() {
    return this.endPage;
  }

  public void setEndPage(int endPage) {
    this.endPage = endPage;
  }

  public boolean isImageBasedCharDetection() {
    return this.imageBasedCharDetection;
  }

  public void setImageBasedCharDetection(boolean imageBasedCharDetection) {
    this.imageBasedCharDetection = imageBasedCharDetection;
  }

  public boolean extractEmbeddedText() {
    return this.scannedPdfParser == null;
  }

  public double getLineMergeEpsilon() {
    return this.lineMergeEpsilon;
  }

  public void setLineMergeEpsilon(double lineMergeEpsilon) {
    this.lineMergeEpsilon = lineMergeEpsilon;
  }

  public List<Integer> getPageNosToOcr() {
    return this.pageNosToOcr;
  }

  public void setPageNosToOcr(List<Integer> pageNosToOcr) {
    this.pageNosToOcr = pageNosToOcr;
  }

  public boolean isImageBasedFormDetection() {
    return this.imageBasedFormDetection;
  }

  public void setImageBasedFormDetection(boolean imageBasedFormDetection) {
    this.imageBasedFormDetection = imageBasedFormDetection;
  }

  public boolean isHandWrittenTextDetection() {
    return this.handWrittenTextDetection;
  }

  public void setHandWrittenTextDetection(boolean handWrittenTextDetection) {
    this.handWrittenTextDetection = handWrittenTextDetection;
  }

  public double getSpacingFactor() {
    return this.spacingFactor;
  }

  public void setSpacingFactor(double spacingFactor) {
    this.spacingFactor = spacingFactor;
  }

  public Consumer<Integer> getBadPageSignaler() {
    return this.badPageSignaler;
  }

  public void setBadPageSignaler(Consumer<Integer> badPageSignaler) {
    if (badPageSignaler != null) {
      this.badPageSignaler = badPageSignaler;
    }
  }

  public boolean isIgnoreNonRenderableText() {
    return this.ignoreNonRenderableText;
  }

  public void setIgnoreNonRenderableText(boolean ignoreNonRenderableText) {
    this.ignoreNonRenderableText = ignoreNonRenderableText;
  }

  public double getMaxTextElementToLineCountRatio() {
    return this.maxTextElementToLineCountRatio;
  }

  public void setMaxTextElementToLineCountRatio(double maxTextElementToLineCountRatio) {
    this.maxTextElementToLineCountRatio = maxTextElementToLineCountRatio;
  }

  public void setPageLevelSpacingScaling(boolean pageLevelSpacingScaling) {
    this.pageLevelSpacingScaling = pageLevelSpacingScaling;
  }

  public boolean isPageLevelSpacingScaled() {
    return this.pageLevelSpacingScaling;
  }

  public double getAllowedScannedness() {
    return this.allowedScannedness;
  }

  public void setAllowedScannedness(double allowedScannedness) {
    this.allowedScannedness = allowedScannedness;
  }

  public int getMinChars() {
    return this.minChars;
  }

  public void setMinChars(int minChars) {
    this.minChars = minChars;
  }

  public boolean isPageLevelOcr() {
    return this.pageLevelOcr;
  }

  public void setPageLevelOcr(boolean pageLevelOcr) {
    this.pageLevelOcr = pageLevelOcr;
  }
}
