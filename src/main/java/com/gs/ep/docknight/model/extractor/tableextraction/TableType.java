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

package com.gs.ep.docknight.model.extractor.tableextraction;

import java.util.regex.Pattern;

/**
 * Enum representing table categorization or type of table.
 */
public enum TableType {
  INCOME_STATEMENT("Income Statement",
      "(?:statement|result)(?:s)?\\p{Space}+of\\p{Space}+(?:operations|income)"),
  BALANCE_SHEET("Balance Sheet", "balance\\p{Space}+sheet(?:s)?"),
  CASH_FLOWS_STATEMENT("Cash Flows Statement",
      "statement(?:s)?\\p{Space}+of\\p{Space}+cash\\p{Space}+flow(?:s)?");

  public static final String CONSOLIDATED_MARKER = "CONSOLIDATED";
  private static final String REGEX_PREFIX_CAPTION =
      "(?<" + CONSOLIDATED_MARKER + ">consolidated)?(?:combined)?\\p{Space}*";
  private final String name;
  private final Pattern captionPatternToMatch;

  TableType(String name, String captionRegexToMatch) {
    this.name = name;
    this.captionPatternToMatch = Pattern
        .compile(REGEX_PREFIX_CAPTION + captionRegexToMatch, Pattern.CASE_INSENSITIVE);
  }

  /**
   * Getter for name of table type
   *
   * @return table type name
   */
  public String getName() {
    return this.name;
  }

  /**
   * Getter for caption pattern
   *
   * @return caption pattern
   */
  public Pattern getCaptionPatternToMatch() {
    return this.captionPatternToMatch;
  }
}
