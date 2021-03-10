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

import org.eclipse.collections.api.tuple.Pair;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import org.apache.commons.lang.WordUtils;

/**
 * Class to represent date and its corresponding date format. Example (dd,mm,yyyy)
 */
public final class ParsedDate {

  private String matchedDateFormat;
  private LocalDate matchedDate;

  private ParsedDate() {
  }

  /**
   * Parse the {@code dateString} into one of the {@code possibleDateFormats}
   *
   * @param dateString date text which is to be parsed
   * @param possibleDateFormats possible date formats
   * @return parsed date
   */
  public static ParsedDate getParsedDate(String dateString,
      List<Pair<String, String>> possibleDateFormats) {
    ParsedDate parsedDate = new ParsedDate();
    String normalizedDate = getNormalizedDate(dateString.trim());

    for (Pair<String, String> regexAndFormatTuple : possibleDateFormats) {
      if (normalizedDate.matches(regexAndFormatTuple.getOne())) {
        try {
          String dateFormat = regexAndFormatTuple.getTwo();

          // Handling natively unsupported date formats
          char[] delimeters = {'-', '/', ' '};
          normalizedDate = WordUtils.capitalizeFully(normalizedDate, delimeters)
              .replaceAll("(?)Sept(?![A-Za-z])", "Sep")
              .replaceAll("(?i)(?<=[0-9])\\s?(st|nd|rd|th)", "")
              .replaceAll("(?<!\\s),(?=\\d{4})", ", ");
          DateTimeFormatter formatter = new DateTimeFormatterBuilder()
              .appendPattern(dateFormat.replaceAll("\\*", ""))
              .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
              .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
              .toFormatter();
          parsedDate.matchedDate = LocalDate.parse(normalizedDate, formatter);
          parsedDate.matchedDateFormat = dateFormat;
          break;
        } catch (DateTimeParseException ignored) {
        }
      }
    }
    return parsedDate;
  }

  /**
   * Replace multiple representation of space and dash characters with uniform space and dash
   * representation
   *
   * @param date text string representing date
   * @return normalized date
   */
  private static String getNormalizedDate(String date) {
    return date.replaceAll("\\u00A0", " ").replaceAll("\\s+", " ").replaceAll("\\u2212", "-");
  }

  /**
   * Checks whether data format exists for parsed date or not
   *
   * @return boolean flag indicating whether data format exists or not
   */
  public boolean isMatchFound() {
    return this.matchedDateFormat != null;
  }

  /**
   * Getter for matchedDateFormat
   *
   * @return matchedDateFormat
   */
  public String getMatchedDateFormat() {
    return this.matchedDateFormat;
  }

  /**
   * Getter for matchedDate
   *
   * @return matchedDate
   */
  public LocalDate getMatchedDate() {
    return this.matchedDate;
  }
}

