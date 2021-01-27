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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.tuple.Tuples;

public final class DateFormatConstants {

  public static final String MMM_REGEX = "(?i)(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)";
  public static final String MMMM_REGEX = "(?i)(?:January|February|March|April|May|June|July|August|September|October|November|December)";
  public static final MutableList<Pair<String, String>> MIDDLE_ENDIAN_REGEX_AND_FORMAT_TUPLES =
      Lists.mutable.of(
          /*Middle-endian*/
          Tuples.pair("[0-3][0-9]/[0-3][0-9]/[0-9]{2}", "MM/dd/yy"),
          Tuples.pair("[0-3][0-9]/[0-3][0-9]/[0-9]{4}", "MM/dd/yyyy"),
          Tuples.pair("[0-3][0-9]\\s[0-3][0-9]\\s[0-9]{2}", "MM dd yy"),
          Tuples.pair("[0-3][0-9]\\s[0-3][0-9]\\s[0-9]{4}", "MM dd yyyy"),
          Tuples.pair("[0-3][0-9]\\.[0-3][0-9]\\.[0-9]{2}", "MM.dd.yy"),
          Tuples.pair("[0-3][0-9]\\.[0-3][0-9]\\.[0-9]{4}", "MM.dd.yyyy"),
          Tuples.pair("[0-3][0-9]\\-[0-3][0-9]\\-[0-9]{2}", "MM-dd-yy"),
          Tuples.pair("[0-3][0-9]\\-[0-3][0-9]\\-[0-9]{4}", "MM-dd-yyyy"),

          /*Middle-endian without leading zeroes*/
          Tuples.pair("[1-3]?[0-9]/[1-3]?[0-9]/[0-9]{2}", "M/d/yy"),
          Tuples.pair("[1-3]?[0-9]/[1-3]?[0-9]/[0-9]{4}", "M/d/yyyy"),
          Tuples.pair("[1-3]?[0-9]\\s[1-3]?[0-9]\\s[0-9]{2}", "M d yy"),
          Tuples.pair("[1-3]?[0-9]\\s[1-3]?[0-9]\\s[0-9]{4}", "M d yyyy"),
          Tuples.pair("[1-3]?[0-9]\\.[1-3]?[0-9]\\.[0-9]{2}", "M.d.yy"),
          Tuples.pair("[1-3]?[0-9]\\.[1-3]?[0-9]\\.[0-9]{4}", "M.d.yyyy"),
          Tuples.pair("[1-3]?[0-9]\\-[1-3]?[0-9]\\-[0-9]{2}", "M-d-yy"),
          Tuples.pair("[1-3]?[0-9]\\-[1-3]?[0-9]\\-[0-9]{4}", "M-d-yyyy"),

          Tuples.pair("[0-3][0-9]/[1-3]?[0-9]/[0-9]{2}", "MM/d/yy"),
          Tuples.pair("[0-3][0-9]/[1-3]?[0-9]/[0-9]{4}", "MM/d/yyyy"),
          Tuples.pair("[0-3][0-9]\\s[1-3]?[0-9]\\s[0-9]{2}", "MM d yy"),
          Tuples.pair("[0-3][0-9]\\s[1-3]?[0-9]\\s[0-9]{4}", "MM d yyyy"),
          Tuples.pair("[0-3][0-9]\\.[1-3]?[0-9]\\.[0-9]{2}", "MM.d.yy"),
          Tuples.pair("[0-3][0-9]\\.[1-3]?[0-9]\\.[0-9]{4}", "MM.d.yyyy"),
          Tuples.pair("[0-3][0-9]\\-[1-3]?[0-9]\\-[0-9]{2}", "MM-d-yy"),
          Tuples.pair("[0-3][0-9]\\-[1-3]?[0-9]\\-[0-9]{4}", "MM-d-yyyy"),

          Tuples.pair("[1-3]?[0-9]/[0-3][0-9]/[0-9]{2}", "M/dd/yy"),
          Tuples.pair("[1-3]?[0-9]/[0-3][0-9]/[0-9]{4}", "M/dd/yyyy"),
          Tuples.pair("[1-3]?[0-9]\\s[0-3][0-9]\\s[0-9]{2}", "M dd yy"),
          Tuples.pair("[1-3]?[0-9]\\s[0-3][0-9]\\s[0-9]{4}", "M dd yyyy"),
          Tuples.pair("[1-3]?[0-9]\\.[0-3][0-9]\\.[0-9]{2}", "M.dd.yy"),
          Tuples.pair("[1-3]?[0-9]\\.[0-3][0-9]\\.[0-9]{4}", "M.dd.yyyy"),
          Tuples.pair("[1-3]?[0-9]\\-[0-3][0-9]\\-[0-9]{2}", "M-dd-yy"),
          Tuples.pair("[1-3]?[0-9]\\-[0-3][0-9]\\-[0-9]{4}", "M-dd-yyyy")
      );
  public static final MutableList<Pair<String, String>> LITTLE_ENDIAN_REGEX_AND_FORMAT_TUPLES =
      Lists.mutable.of(
          /*Little-endian*/
          Tuples.pair("[0-3][0-9]/[0-3][0-9]/[0-9]{2}", "dd/MM/yy"),
          Tuples.pair("[0-3][0-9]/[0-3][0-9]/[0-9]{4}", "dd/MM/yyyy"),
          Tuples.pair("[0-3][0-9]\\s[0-3][0-9]\\s[0-9]{2}", "dd MM yy"),
          Tuples.pair("[0-3][0-9]\\s[0-3][0-9]\\s[0-9]{4}", "dd MM yyyy"),
          Tuples.pair("[0-3][0-9]\\.[0-3][0-9]\\.[0-9]{2}", "dd.MM.yy"),
          Tuples.pair("[0-3][0-9]\\.[0-3][0-9]\\.[0-9]{4}", "dd.MM.yyyy"),
          Tuples.pair("[0-3][0-9]\\-[0-3][0-9]\\-[0-9]{2}", "dd-MM-yy"),
          Tuples.pair("[0-3][0-9]\\-[0-3][0-9]\\-[0-9]{4}", "dd-MM-yyyy"),

          /*Little-endian without leading zeroes*/
          Tuples.pair("[1-3]?[0-9]/[1-3]?[0-9]/[0-9]{2}", "d/M/yy"),
          Tuples.pair("[1-3]?[0-9]/[1-3]?[0-9]/[0-9]{4}", "d/M/yyyy"),
          Tuples.pair("[1-3]?[0-9]\\s[1-3]?[0-9]\\s[0-9]{2}", "d M yy"),
          Tuples.pair("[1-3]?[0-9]\\s[1-3]?[0-9]\\s[0-9]{4}", "d M yyyy"),
          Tuples.pair("[1-3]?[0-9]\\.[1-3]?[0-9]\\.[0-9]{2}", "d.M.yy"),
          Tuples.pair("[1-3]?[0-9]\\.[1-3]?[0-9]\\.[0-9]{4}", "d.M.yyyy"),
          Tuples.pair("[1-3]?[0-9]\\-[1-3]?[0-9]\\-[0-9]{2}", "d-M-yy"),
          Tuples.pair("[1-3]?[0-9]\\-[1-3]?[0-9]\\-[0-9]{4}", "d-M-yyyy")
      );
  public static final MutableList<Pair<String, String>> BIG_ENDIAN_REGEX_AND_FORMAT_TUPLES =
      Lists.mutable.of(
          /*Big-endian*/
          Tuples.pair("[0-9]{2}/[0-3][0-9]/[0-3][0-9]", "yy/MM/dd"),
          Tuples.pair("[0-9]{4}/[0-3][0-9]/[0-3][0-9]", "yyyy/MM/dd"),
          Tuples.pair("[0-9]{2}\\s[0-3][0-9]\\s[0-3][0-9]", "yy MM dd"),
          Tuples.pair("[0-9]{4}\\s[0-3][0-9]\\s[0-3][0-9]", "yyyy MM dd"),
          Tuples.pair("[0-9]{2}\\.[0-3][0-9]\\.[0-3][0-9]", "yy.MM.dd"),
          Tuples.pair("[0-9]{4}\\.[0-3][0-9]\\.[0-3][0-9]", "yyyy.MM.dd"),
          Tuples.pair("[0-9]{2}\\-[0-3][0-9]\\-[0-3][0-9]", "yy-MM-dd"),
          Tuples.pair("[0-9]{4}\\-[0-3][0-9]\\-[0-3][0-9]", "yyyy-MM-dd"),

          /*Big-endian without leading zeroes*/
          Tuples.pair("[0-9]{2}/[1-3]?[0-9]/[1-3]?[0-9]", "yy/M/d"),
          Tuples.pair("[0-9]{4}/[1-3]?[0-9]/[1-3]?[0-9]", "yyyy/M/d"),
          Tuples.pair("[0-9]{2}\\s[1-3]?[0-9]\\s[1-3]?[0-9]", "yy M d"),
          Tuples.pair("[0-9]{4}\\s[1-3]?[0-9]\\s[1-3]?[0-9]", "yyyy M d"),
          Tuples.pair("[0-9]{2}\\.[1-3]?[0-9]\\.[1-3]?[0-9]", "yy.M.d"),
          Tuples.pair("[0-9]{4}\\.[1-3]?[0-9]\\.[1-3]?[0-9]", "yyyy.M.d"),
          Tuples.pair("[0-9]{2}\\-[1-3]?[0-9]\\-[1-3]?[0-9]", "yy-M-d"),
          Tuples.pair("[0-9]{4}\\-[1-3]?[0-9]\\-[1-3]?[0-9]", "yyyy-M-d")
      );
  public static final MutableList<Pair<String, String>> MONTH_ABBREVIATED_REGEX_AND_FORMAT_TUPLES =
      Lists.mutable.of(
          /*With three-letter abbreviation for month*/
          Tuples.pair(String.format("[0-3][0-9]/%s/[0-9]{2}", MMM_REGEX), "dd/MMM/yy"),
          Tuples.pair(String.format("[0-3][0-9]/%s/[0-9]{4}", MMM_REGEX), "dd/MMM/yyyy"),
          Tuples.pair(String.format("[0-3][0-9]\\-%s\\-[0-9]{2}", MMM_REGEX), "dd-MMM-yy"),
          Tuples.pair(String.format("[0-3][0-9]\\-%s\\-[0-9]{4}", MMM_REGEX), "dd-MMM-yyyy"),
          Tuples.pair(String.format("[0-3][0-9]\\s%s\\s[0-9]{2}", MMM_REGEX), "dd MMM yy"),
          Tuples.pair(String.format("[0-3][0-9]\\s%s\\s[0-9]{4}", MMM_REGEX), "dd MMM yyyy"),
          Tuples.pair(String.format("[0-3][0-9]\\s%s,\\s{0,2}\\d{4}", MMM_REGEX), "dd MMM, yyyy"),
          Tuples.pair(String.format("%s\\s[0-3][0-9],\\s{0,2}\\d{4}", MMM_REGEX), "MMM dd, yyyy"),

          /*Month Abbr. without leading zeroes*/
          Tuples.pair(String.format("[1-3]?[0-9]/%s/[0-9]{2}", MMM_REGEX), "d/MMM/yy"),
          Tuples.pair(String.format("[1-3]?[0-9]/%s/[0-9]{4}", MMM_REGEX), "d/MMM/yyyy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\-%s\\-[0-9]{2}", MMM_REGEX), "d-MMM-yy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\-%s\\-[0-9]{4}", MMM_REGEX), "d-MMM-yyyy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s%s\\s[0-9]{2}", MMM_REGEX), "d MMM yy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s%s\\s[0-9]{4}", MMM_REGEX), "d MMM yyyy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s%s,\\s{0,2}\\d{4}", MMM_REGEX), "d MMM, yyyy"),
          Tuples.pair(String.format("%s\\s[1-3]?[0-9],\\s{0,2}\\d{4}", MMM_REGEX), "MMM d, yyyy")
      );
  public static final MutableList<Pair<String, String>> MONTH_FULL_REGEX_AND_FORMAT_TUPLES =
      Lists.mutable.of(
          /*Month spelled out in full*/
          Tuples.pair(String.format("[0-3][0-9]/%s/[0-9]{2}", MMMM_REGEX), "dd/MMMM/yy"),
          Tuples.pair(String.format("[0-3][0-9]/%s/[0-9]{4}", MMMM_REGEX), "dd/MMMM/yyyy"),
          Tuples.pair(String.format("[0-3][0-9]\\-%s\\-[0-9]{2}", MMMM_REGEX), "dd-MMMM-yy"),
          Tuples.pair(String.format("[0-3][0-9]\\-%s\\-[0-9]{4}", MMMM_REGEX), "dd-MMMM-yyyy"),
          Tuples.pair(String.format("[0-3][0-9]\\s%s\\s[0-9]{2}", MMMM_REGEX), "dd MMMM yy"),
          Tuples.pair(String.format("[0-3][0-9]\\s%s\\s[0-9]{4}", MMMM_REGEX), "dd MMMM yyyy"),
          Tuples
              .pair(String.format("[0-3][0-9]\\s%s,\\s{0,2}[0-9]{4}", MMMM_REGEX), "dd MMMM, yyyy"),
          Tuples.pair(String.format("%s\\s[0-3][0-9],\\s{0,2}\\d{4}", MMMM_REGEX), "MMMM dd, yyyy"),

          /*Month Full without leading zeroes*/
          Tuples.pair(String.format("[1-3]?[0-9]/%s/[0-9]{2}", MMMM_REGEX), "d/MMMM/yy"),
          Tuples.pair(String.format("[1-3]?[0-9]/%s/[0-9]{4}", MMMM_REGEX), "d/MMMM/yyyy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\-%s\\-[0-9]{2}", MMMM_REGEX), "d-MMMM-yy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\-%s\\-[0-9]{4}", MMMM_REGEX), "d-MMMM-yyyy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s%s\\s[0-9]{2}", MMMM_REGEX), "d MMMM yy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s%s\\s[0-9]{4}", MMMM_REGEX), "d MMMM yyyy"),
          Tuples
              .pair(String.format("[1-3]?[0-9]\\s%s,\\s{0,2}[0-9]{4}", MMMM_REGEX), "d MMMM, yyyy"),
          Tuples.pair(String.format("%s\\s[1-3]?[0-9],\\s{0,2}\\d{4}", MMMM_REGEX), "MMMM d, yyyy")
      );
  public static final MutableList<Pair<String, String>> DATE_FORMATS_WITH_NO_COMMA =
      Lists.mutable.of(
          Tuples.pair(String.format("%s\\s[0-3][0-9]\\s{0,2}\\d{4}", MMM_REGEX), "MMM dd yyyy"),
          Tuples.pair(String.format("%s\\s[1-3]?[0-9]\\s{0,2}\\d{4}", MMM_REGEX), "MMM d yyyy"),
          Tuples.pair(String.format("%s\\s[0-3][0-9]\\s{0,2}\\d{4}", MMMM_REGEX), "MMMM dd yyyy"),
          Tuples.pair(String.format("%s\\s[1-3]?[0-9]\\s{0,2}\\d{4}", MMMM_REGEX), "MMMM d yyyy"),
          Tuples.pair("((?i)SEPT)\\s[0-3][0-9]\\s*(st|nd|rd|th)\\s{0,2}\\d{4}", "MMM* dd* yyyy"),
          Tuples.pair("((?i)SEPT)\\s[1-3]?[0-9]\\s*(st|nd|rd|th)\\s{0,2}\\d{4}", "MMM* d* yyyy"),
          Tuples.pair("((?i)SEPT)\\s[0-3][0-9]\\s{0,2}\\d{4}", "MMM* dd yyyy"),
          Tuples.pair("((?i)SEPT)\\s[1-3]?[0-9]\\s{0,2}\\d{4}", "MMM* d yyyy"),
          Tuples.pair(String.format("%s\\s[0-3][0-9]\\s*(st|nd|rd|th)\\s{0,2}\\d{4}", MMM_REGEX),
              "MMM dd* yyyy"),
          Tuples.pair(String.format("%s\\s[1-3]?[0-9]\\s*(st|nd|rd|th)\\s{0,2}\\d{4}", MMM_REGEX),
              "MMM d* yyyy"),
          Tuples.pair(String.format("%s\\s[0-3][0-9]\\s*(st|nd|rd|th)\\s{0,2}\\d{4}", MMMM_REGEX),
              "MMMM dd* yyyy"),
          Tuples.pair(String.format("%s\\s[1-3]?[0-9]\\s*(st|nd|rd|th)\\s{0,2}\\d{4}", MMMM_REGEX),
              "MMMM d* yyyy")
      );
  public static final MutableList<Pair<String, String>> NATIVELY_UNSUPPORTED_FORMAT_TUPLES =
      Lists.mutable.of(
          /*With "Sept" and day of month as ordinal numbers & leading zeroes */
          Tuples.pair("[0-3][0-9]\\s*(st|nd|rd|th)/((?i)SEPT)/[0-9]{2}", "dd*/MMM*/yy"),
          Tuples.pair("[0-3][0-9]\\s*(st|nd|rd|th)/((?i)SEPT)/[0-9]{4}", "dd*/MMM*/yyyy"),
          Tuples.pair("[0-3][0-9]\\s*(st|nd|rd|th)\\-((?i)SEPT)\\-[0-9]{2}", "dd*-MMM*-yy"),
          Tuples.pair("[0-3][0-9]\\s*(st|nd|rd|th)\\-((?i)SEPT)\\-[0-9]{4}", "dd*-MMM*-yyyy"),
          Tuples.pair("[0-3][0-9]\\s*(st|nd|rd|th)\\s((?i)SEPT)\\s[0-9]{2}", "dd* MMM* yy"),
          Tuples.pair("[0-3][0-9]\\s*(st|nd|rd|th)\\s((?i)SEPT)\\s[0-9]{4}", "dd* MMM* yyyy"),
          Tuples
              .pair("[0-3][0-9]\\s*(st|nd|rd|th)\\s((?i)SEPT),\\s{0,2}[0-9]{4}", "dd* MMM*, yyyy"),
          Tuples.pair("((?i)SEPT)\\s[0-3][0-9]\\s*(st|nd|rd|th),\\s{0,2}\\d{4}", "MMM* dd*, yyyy"),

          /*With "Sept" and day of month as ordinal numbers & without leading zeroes */
          Tuples.pair("[1-3]?[0-9]\\s*(st|nd|rd|th)/((?i)SEPT)/[0-9]{2}", "d*/MMM*/yy"),
          Tuples.pair("[1-3]?[0-9]\\s*(st|nd|rd|th)/((?i)SEPT)/[0-9]{4}", "d*/MMM*/yyyy"),
          Tuples.pair("[1-3]?[0-9]\\s*(st|nd|rd|th)\\-((?i)SEPT)\\-[0-9]{2}", "d*-MMM*-yy"),
          Tuples.pair("[1-3]?[0-9]\\s*(st|nd|rd|th)\\-((?i)SEPT)\\-[0-9]{4}", "d*-MMM*-yyyy"),
          Tuples.pair("[1-3]?[0-9]\\s*(st|nd|rd|th)\\s((?i)SEPT)\\s[0-9]{2}", "d* MMM* yy"),
          Tuples.pair("[1-3]?[0-9]\\s*(st|nd|rd|th)\\s((?i)SEPT)\\s[0-9]{4}", "d* MMM* yyyy"),
          Tuples
              .pair("[1-3]?[0-9]\\s*(st|nd|rd|th)\\s((?i)SEPT),\\s{0,2}[0-9]{4}", "d* MMM*, yyyy"),
          Tuples.pair("((?i)SEPT)\\s[1-3]?[0-9]\\s*(st|nd|rd|th),\\s{0,2}\\d{4}", "MMM* d*, yyyy"),

          /*With "Sept" and day of month with leading zeroes*/
          Tuples.pair("[0-3][0-9]/((?i)SEPT)/[0-9]{2}", "dd/MMM*/yy"),
          Tuples.pair("[0-3][0-9]/((?i)SEPT)/[0-9]{4}", "dd/MMM*/yyyy"),
          Tuples.pair("[0-3][0-9]\\-((?i)SEPT)\\-[0-9]{2}", "dd-MMM*-yy"),
          Tuples.pair("[0-3][0-9]\\-((?i)SEPT)\\-[0-9]{4}", "dd-MMM*-yyyy"),
          Tuples.pair("[0-3][0-9]\\s((?i)SEPT)\\s[0-9]{2}", "dd MMM* yy"),
          Tuples.pair("[0-3][0-9]\\s((?i)SEPT)\\s[0-9]{4}", "dd MMM* yyyy"),
          Tuples.pair("[0-3][0-9]\\s((?i)SEPT),\\s{0,2}[0-9]{4}", "dd MMM*, yyyy"),
          Tuples.pair("((?i)SEPT)\\s[0-3][0-9],\\s{0,2}\\d{4}", "MMM* dd, yyyy"),

          /*With "Sept" and day of month without leading zeroes*/
          Tuples.pair("[1-3]?[0-9]/((?i)SEPT)/[0-9]{2}", "d/MMM*/yy"),
          Tuples.pair("[1-3]?[0-9]/((?i)SEPT)/[0-9]{4}", "d/MMM*/yyyy"),
          Tuples.pair("[1-3]?[0-9]\\-((?i)SEPT)\\-[0-9]{2}", "d-MMM*-yy"),
          Tuples.pair("[1-3]?[0-9]\\-((?i)SEPT)\\-[0-9]{4}", "d-MMM*-yyyy"),
          Tuples.pair("[1-3]?[0-9]\\s((?i)SEPT)\\s[0-9]{2}", "d MMM* yy"),
          Tuples.pair("[1-3]?[0-9]\\s((?i)SEPT)\\s[0-9]{4}", "d MMM* yyyy"),
          Tuples.pair("[1-3]?[0-9]\\s((?i)SEPT),\\s{0,2}[0-9]{4}", "d MMM*, yyyy"),
          Tuples.pair("((?i)SEPT)\\s[1-3]?[0-9],\\s{0,2}\\d{4}", "MMM* d, yyyy"),

          /*With three-letter abbreviation for month and day of month as ordinal number & leading zeroes*/
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)/%s/[0-9]{2}", MMM_REGEX),
              "dd*/MMM/yy"),
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)/%s/[0-9]{4}", MMM_REGEX),
              "dd*/MMM/yyyy"),
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)\\-%s\\-[0-9]{2}", MMM_REGEX),
              "dd*-MMM-yy"),
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)\\-%s\\-[0-9]{4}", MMM_REGEX),
              "dd*-MMM-yyyy"),
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)\\s%s\\s[0-9]{2}", MMM_REGEX),
              "dd* MMM yy"),
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)\\s%s\\s[0-9]{4}", MMM_REGEX),
              "dd* MMM yyyy"),
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)\\s%s,\\s{0,2}\\d{4}", MMM_REGEX),
              "dd* MMM, yyyy"),
          Tuples.pair(String.format("%s\\s[0-3][0-9]\\s*(st|nd|rd|th),\\s{0,2}\\d{4}", MMM_REGEX),
              "MMM dd*, yyyy"),

          /*Month Abbr. and day of month as ordinal number without leading zeroes*/
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)/%s/[0-9]{2}", MMM_REGEX),
              "d*/MMM/yy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)/%s/[0-9]{4}", MMM_REGEX),
              "d*/MMM/yyyy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)\\-%s\\-[0-9]{2}", MMM_REGEX),
              "d*-MMM-yy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)\\-%s\\-[0-9]{4}", MMM_REGEX),
              "d*-MMM-yyyy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)\\s%s\\s[0-9]{2}", MMM_REGEX),
              "d* MMM yy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)\\s%s\\s[0-9]{4}", MMM_REGEX),
              "d* MMM yyyy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)\\s%s,\\s{0,2}\\d{4}", MMM_REGEX),
              "d* MMM, yyyy"),
          Tuples.pair(String.format("%s\\s[1-3]?[0-9]\\s*(st|nd|rd|th),\\s{0,2}\\d{4}", MMM_REGEX),
              "MMM d*, yyyy"),

          /*Month spelled out in full and day of month as ordinal number with leading zeroes*/
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)/%s/[0-9]{2}", MMMM_REGEX),
              "dd*/MMMM/yy"),
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)/%s/[0-9]{4}", MMMM_REGEX),
              "dd*/MMMM/yyyy"),
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)\\-%s\\-[0-9]{2}", MMMM_REGEX),
              "dd*-MMMM-yy"),
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)\\-%s\\-[0-9]{4}", MMMM_REGEX),
              "dd*-MMMM-yyyy"),
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)\\s%s\\s[0-9]{2}", MMMM_REGEX),
              "dd* MMMM yy"),
          Tuples.pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)\\s%s\\s[0-9]{4}", MMMM_REGEX),
              "dd* MMMM yyyy"),
          Tuples
              .pair(String.format("[0-3][0-9]\\s*(st|nd|rd|th)\\s%s,\\s{0,2}[0-9]{4}", MMMM_REGEX),
                  "dd* MMMM, yyyy"),
          Tuples.pair(String.format("%s\\s[0-3][0-9]\\s*(st|nd|rd|th),\\s{0,2}\\d{4}", MMMM_REGEX),
              "MMMM dd*, yyyy"),

          /*Month Full and day of month as ordinal number without leading zeroes*/
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)/%s/[0-9]{2}", MMMM_REGEX),
              "d*/MMMM/yy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)/%s/[0-9]{4}", MMMM_REGEX),
              "d*/MMMM/yyyy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)\\-%s\\-[0-9]{2}", MMMM_REGEX),
              "d*-MMMM-yy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)\\-%s\\-[0-9]{4}", MMMM_REGEX),
              "d*-MMMM-yyyy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)\\s%s\\s[0-9]{2}", MMMM_REGEX),
              "d* MMMM yy"),
          Tuples.pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)\\s%s\\s[0-9]{4}", MMMM_REGEX),
              "d* MMMM yyyy"),
          Tuples
              .pair(String.format("[1-3]?[0-9]\\s*(st|nd|rd|th)\\s%s,\\s{0,2}[0-9]{4}", MMMM_REGEX),
                  "d* MMMM, yyyy"),
          Tuples.pair(String.format("%s\\s[1-3]?[0-9]\\s*(st|nd|rd|th),\\s{0,2}\\d{4}", MMMM_REGEX),
              "MMMM d*, yyyy")
      );
  public static final MutableList<Pair<String, String>> ONLY_YEAR_REGEX_AND_FORMAT_TUPLES = Lists.mutable
      .of(
          /*Not considering years < 1000 and years >= 3000*/
          Tuples.pair("[1-2][0-9]{3}", "yyyy")
      );

  private DateFormatConstants() {
  }

  public static MutableList<Pair<String, String>> getAllRegexAndFormatTuples() {
    MutableList<Pair<String, String>> entireList = Lists.mutable
        .ofAll(MIDDLE_ENDIAN_REGEX_AND_FORMAT_TUPLES);
    entireList.addAll(LITTLE_ENDIAN_REGEX_AND_FORMAT_TUPLES);
    entireList.addAll(BIG_ENDIAN_REGEX_AND_FORMAT_TUPLES);
    entireList.addAll(MONTH_ABBREVIATED_REGEX_AND_FORMAT_TUPLES);
    entireList.addAll(MONTH_FULL_REGEX_AND_FORMAT_TUPLES);
    entireList.addAll(NATIVELY_UNSUPPORTED_FORMAT_TUPLES);
    entireList.addAll(DATE_FORMATS_WITH_NO_COMMA);

    return entireList;
  }
}
