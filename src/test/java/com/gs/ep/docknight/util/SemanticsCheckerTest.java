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

package com.gs.ep.docknight.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.collections.impl.factory.Lists;
import com.gs.ep.docknight.model.converter.pdfparser.IndexedWord;
import com.gs.ep.docknight.util.SemanticsChecker.IndexMarkerType;
import com.gs.ep.docknight.util.SemanticsChecker.RegexType;
import org.junit.Test;

public class SemanticsCheckerTest {

  @Test
  public void testSemanticIncompletenessChecker() {
    assertTrue(SemanticsChecker.isSemanticallyIncomplete("$"));
    assertFalse(SemanticsChecker.isSemanticallyIncomplete("USD"));
    assertFalse(SemanticsChecker.isSemanticallyIncomplete("$ 1"));
  }

  @Test
  public void testSemanticallyConnectedTextChecker() {
    assertTrue(SemanticsChecker.canBeConnected("Name:", "Foolan Barik"));
    assertTrue(SemanticsChecker.canBeConnected("This,", "that are connected"));
    assertFalse(SemanticsChecker.canBeConnected("Name", "Age"));
  }

  @Test
  public void isLastLineOfParagraph() {
    assertFalse(SemanticsChecker.isLastLineOfParagraph("Name: Foolan Barik"));
    assertFalse(SemanticsChecker.isLastLineOfParagraph("Company:A.B."));
    assertTrue(SemanticsChecker.isLastLineOfParagraph("Line end. Hence forth."));
  }

  @Test
  public void canBeHeader() {
    assertFalse(SemanticsChecker.canBeHeader(null));
    assertFalse(SemanticsChecker.canBeHeader(Lists.mutable.empty()));
    assertFalse(SemanticsChecker.canBeHeader(Lists.mutable.of("9")));
    assertFalse(SemanticsChecker.canBeHeader(Lists.mutable.of("this is last line of paragraph.")));
    assertFalse(SemanticsChecker.canBeHeader(Lists.mutable.of("9", "Amount", "9")));
    assertTrue(SemanticsChecker.canBeHeader(Lists.mutable.of("9", "Amount 9")));
    assertTrue(SemanticsChecker.canBeHeader(Lists.mutable.of("9", "Header")));
  }

  @Test
  public void testRegexType() {
    assertEquals(RegexType.DATE, RegexType.getFor("1906"));
    assertEquals(RegexType.ALPHANUMERIC,
        RegexType.getFor("27 Jun 2017"));  // since, we consider only numeric dates in this case
    assertEquals(RegexType.ALPHA, RegexType.getFor("January"));
    assertEquals(RegexType.NUMERIC, RegexType.getFor("5000"));
  }

  @Test
  public void containsOnlyIndex() {
    assertTrue(SemanticsChecker.containsOnlyIndex("[3]"));
    assertTrue(SemanticsChecker.containsOnlyIndex("XII,"));
    assertTrue(SemanticsChecker.containsOnlyIndex("\u25A5"));
    assertFalse(SemanticsChecker.containsOnlyIndex("\u25A5 But contains more text too."));
    assertFalse(SemanticsChecker.containsOnlyIndex("\u25A5 And more text without full stop"));
  }

  @Test
  public void isValidIndexMarkerType() {
    assertTrue(IndexMarkerType.ALPHANUM.isValid("AB"));
    assertTrue(IndexMarkerType.ALPHANUM.isValid("VI"));
    assertTrue(IndexMarkerType.ALPHANUM.isValid("12"));
    assertFalse(IndexMarkerType.ALPHANUM.isValid("aB"));

    assertTrue(IndexMarkerType.ROMAN.isValid("vi"));
    assertTrue(IndexMarkerType.ROMAN.isValid("M"));
    assertFalse(IndexMarkerType.ROMAN.isValid("iX"));

    assertTrue(IndexMarkerType.BULLETED.isValid("\u25A0"));
    assertFalse(IndexMarkerType.BULLETED.isValid("\u26A0"));
  }

  @Test
  public void alphabetsCaseCheck() {
    assertTrue(SemanticsChecker.isUpperCaseAlphabeticString("WARNING:12"));
    assertFalse(SemanticsChecker.isUpperCaseAlphabeticString("WARNing:()"));
    assertFalse(SemanticsChecker.isUpperCaseAlphabeticString("123"));

    assertTrue(SemanticsChecker.isLowerCaseAlphabeticString("warning:?;"));
    assertFalse(SemanticsChecker.isLowerCaseAlphabeticString("WARNing:*&"));
    assertFalse(SemanticsChecker.isLowerCaseAlphabeticString("123"));
  }

  @Test
  public void testIsNumber() {
    assertTrue(SemanticsChecker.isNumber("123"));
    assertFalse(SemanticsChecker.isNumber("a123"));
    assertFalse(SemanticsChecker.isNumber("abc"));
  }

  @Test
  public void testIsNumberInAnySystem() {
    assertTrue(SemanticsChecker.isNumberInAnySystem("123"));
    assertTrue(SemanticsChecker.isNumberInAnySystem("iv"));
    assertFalse(SemanticsChecker.isNumberInAnySystem("iv1"));
    assertFalse(SemanticsChecker.isNumberInAnySystem("ivx"));
    assertFalse(SemanticsChecker.isNumberInAnySystem("a123"));
    assertFalse(SemanticsChecker.isNumberInAnySystem("abc"));
  }

  @Test
  public void testSplitIntoWords() {
    assertEquals(Lists.mutable
            .of("http", "://", "www", ".", "temp", ".", "com", "/", "v1", "/", "123", "?", "p", "=#"),
        SemanticsChecker.splitIntoWords("http://www.temp.com/v1/123?p=#", false));
    assertEquals(Lists.mutable.of("v", "1"), SemanticsChecker.splitIntoWords("v1", true));
    assertEquals(Lists.mutable.of(new IndexedWord("v", 0, true), new IndexedWord("1", 1, true)),
        SemanticsChecker.splitIntoIndexedWords("v1", true, true));
    assertEquals(Lists.mutable.of("123"), SemanticsChecker.splitIntoWords("123", false));
    assertEquals(Lists.mutable.of("xx"), SemanticsChecker.splitIntoWords("xx", false));
    assertEquals(Lists.mutable.of("##"), SemanticsChecker.splitIntoWords("##", false));
    assertEquals(Lists.mutable.empty(), SemanticsChecker.splitIntoWords("", false));
  }

  @Test
  public void testAreStringsNonNumericallyInvariant() {
    assertTrue(SemanticsChecker.areStringsNonNumericallyInvariant("", ""));
    assertTrue(SemanticsChecker.areStringsNonNumericallyInvariant("abc/xyz", "abc/xyz"));
    assertTrue(SemanticsChecker.areStringsNonNumericallyInvariant("Page 1 of 5", "Page 2 of 5"));
    assertTrue(SemanticsChecker.areStringsNonNumericallyInvariant("IV", "V"));
    assertFalse(SemanticsChecker.areStringsNonNumericallyInvariant("abc1/xyz", "abc/xyz"));
  }

  @Test
  public void isAmountOrPercentage() {
    assertTrue(SemanticsChecker.isAmountOrPercentage("(1,234,567)"));
    assertTrue(SemanticsChecker.isAmountOrPercentage("1.75"));
    assertTrue(SemanticsChecker.isAmountOrPercentage("-1"));
    assertTrue(SemanticsChecker.isAmountOrPercentage("10%"));
    assertFalse(SemanticsChecker.isAmountOrPercentage("10abc"));
    assertFalse(SemanticsChecker.isAmountOrPercentage(""));
    assertFalse(SemanticsChecker.isAmountOrPercentage("()"));
  }

}

