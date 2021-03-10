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
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.utility.Iterate;
import com.gs.ep.docknight.model.converter.pdfparser.IndexedWord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to handle semantics of text
 */
public final class SemanticsChecker {

  public static final Pattern URL_PATTERN = Pattern
      .compile("(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");
  public static final Pattern PUNCTUATION_PATTERN = Pattern.compile("\\p{Punct}");
  public static final String SINGLE_SPACE = "\\p{Space}";
  public static final String NUMERIC_QUALIFIERS = "\\$|%|USD|EUR|\\u20AC|-|_|\\u2212";  // Non numeric content that might be present with actual number in amount cell
  private static final Pattern CONTAINS_ALPHA_PATTERN = Pattern
      .compile(".*[a-zA-Z]+.*", Pattern.DOTALL);
  private static final Pattern CONTAINS_NUM_PATTERN = Pattern.compile(".*[0-9]+.*", Pattern.DOTALL);
  private static final Pattern NUM_PATTERN = Pattern.compile("[0-9]+");
  private static final Pattern CONTAINS_TOTAL_PHRASE_PATTERN = Pattern
      .compile("(.*\\s)?(?i)(net|(sub)?total|estimated)(\\s.*)?:?");
  private static final MutableList<String> CURRENCY_SYMBOLS = Lists.mutable.of("$", "\u20AC");
  private static final Pattern INDEX_MARKER_START_DELIM = Pattern.compile("[(\\[]");
  private static final Pattern INDEX_MARKER_END_DELIM = Pattern.compile("[\\.,\\)\\]]");
  private static final MutableList<String> ALPHA_NUM_REGEXES = Lists.mutable
      .of("[A-Z]+", "[a-z]+", "[0-9]+");
  private static final Pattern ALPHA_NUM_PATTERN = Pattern
      .compile(ALPHA_NUM_REGEXES.makeString("(", "|", ")"));
  private static final Pattern ROMAN_NUM_PATTERN = Pattern
      .compile("M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})");
  private static final Pattern BULLET_PATTERN = Pattern.compile("([\\u25A0-\\u25FF])");

  private static final MutableList<String> GENERAL_WORD_SEPARATORS = Lists.mutable
      .of("\\p{Punct}", "\\p{Cf}", "\\p{Space}",
          "\\u00A0", "\\u2007", "\\u202F", "\\u3010", "\\u3011", "\\u007C", "\\u00F7", "\\u0002",
          "\\u0001", "\\u2022", "\\u201E", "\\uE010", "\\uE011");
  private static final String WORD_SEP_REGEX = GENERAL_WORD_SEPARATORS.makeString("[", "", "]+");
  private static final Pattern WORD_SEP_PATTERN = Pattern.compile(WORD_SEP_REGEX);
  private static final String SEPARATOR = " ";
  private static final Pattern PUNCTUATION_OR_SPACE_PATTERN = Pattern.compile("[\\s\\p{P}]+");
  private static MutableSet<String> commonNonLocationalTokens;
  private static MutableSet<String> cityNames;

  private SemanticsChecker() {
  }

  /**
   * Split the text into words
   *
   * @param text text which is to split
   * @param splitOnNumbers boolean indicating whether to split on numbers
   * @return split words
   */
  public static MutableList<String> splitIntoWords(String text, boolean splitOnNumbers) {
    return splitIntoWords(text, splitOnNumbers, true);
  }

  /**
   * Split the text into words
   *
   * @param text text which is to split
   * @param splitOnNumbers boolean indicating whether to split on numbers
   * @param includePunctuation boolean indicating whether to include punctuations as separate
   * elements in the output list
   * @return split words
   */
  public static MutableList<String> splitIntoWords(String text, boolean splitOnNumbers,
      boolean includePunctuation) {
    return splitIntoIndexedWords(text, splitOnNumbers, includePunctuation)
        .collect(IndexedWord::getString);
  }

  /**
   * Split the {@code text} into indexed words
   */
  public static MutableList<IndexedWord> splitIntoIndexedWords(String text, boolean splitOnNumbers,
      boolean includePunctuation) {
    MutableList<IndexedWord> words = splitIntoIndexedWords(text, 0, WORD_SEP_PATTERN,
        includePunctuation);
    return splitOnNumbers ? words.flatCollect(s -> s.isWord() ?
        splitIntoIndexedWords(s.getString(), s.getIndex(), NUM_PATTERN, true, true)
        : Lists.mutable.of(s)) : words;
  }


  private static MutableList<IndexedWord> splitIntoIndexedWords(String text, int zeroOfIndex,
      Pattern diffrentiator, boolean includeDelim) {
    return splitIntoIndexedWords(text, zeroOfIndex, diffrentiator, includeDelim, false);
  }

  private static MutableList<IndexedWord> splitIntoIndexedWords(String text, int zeroOfIndex,
      Pattern diffrentiator, boolean includeDelim, boolean isDelimWord) {
    int index = 0;
    MutableList<IndexedWord> words = Lists.mutable.empty();
    Matcher m = diffrentiator.matcher(text);
    while (m.find()) {
      IndexedWord match1 = getIndexedWord(text.substring(index, m.start()), zeroOfIndex + index,
          true);
      IndexedWord match2 = getIndexedWord(m.group(), zeroOfIndex + m.start(), isDelimWord);
      if (match1 != null) {
        words.add(match1);
      }
      if (includeDelim && match2 != null) {
        words.add(match2);
      }
      index = m.end();
    }
    IndexedWord remaining = getIndexedWord(text.substring(index, text.length()),
        zeroOfIndex + index, true);
    if (remaining != null) {
      words.add(remaining);
    }
    return words;
  }

  /**
   * @return Indexed word created after trimming {@code match}
   */
  private static IndexedWord getIndexedWord(String match, int zeroOfIndex, boolean isWord) {
    int startIndex = 0;
    while (startIndex < match.length() && isWhiteSpace(match.charAt(startIndex))) {
      startIndex++;
    }
    if (startIndex == match.length()) {
      return null;
    }
    int endIndex = match.length();
    while (endIndex > 0 && isWhiteSpace(match.charAt(endIndex - 1))) {
      endIndex--;
    }
    return new IndexedWord(match.substring(startIndex, endIndex), zeroOfIndex + startIndex, isWord);
  }

  /**
   * @return True if the character {@code ch} is white space
   */
  public static boolean isWhiteSpace(char ch) {
    return Character.isWhitespace(ch) || ch == '\u00A0' || ch == '\u2007' || ch
        == '\u202F'; //extra chars to deal with non breaking space not included in isWhiteSpace
  }

  /**
   * @return True if the {@code text} contains alphabets
   */
  public static boolean hasAlphabets(String text) {
    return CONTAINS_ALPHA_PATTERN.matcher(text).matches();
  }

  /**
   * Check whether {@code text} contains number or not.
   *
   * @param text text in which the check has to be made.
   * @return boolean indicating whether {@code text} contains number or not.
   */
  public static boolean hasNumericContent(String text) {
    return CONTAINS_NUM_PATTERN.matcher(text).matches();
  }

  /**
   * @return True if text in itself does not make sense. For example- currency, else return False
   */
  public static boolean isSemanticallyIncomplete(String textStr) {
    return CURRENCY_SYMBOLS.contains(textStr);
  }

  /**
   * Check whether non numerical content in input strings is exactly same or not
   *
   * @param first first string
   * @param second second string
   * @return boolean flag indicating whether strings are non numerically invariant
   */
  public static boolean areStringsNonNumericallyInvariant(String first, String second) {
    MutableList<String> words1 = splitIntoWords(first, false);
    MutableList<String> words2 = splitIntoWords(second, false);
    if (words1.size() == words2.size()) {
      for (int i = 0; i < words1.size(); i++) {
        if (!words1.get(i).equals(words2.get(i)) && !(isNumberInAnySystem(words1.get(i))
            && isNumberInAnySystem(words2.get(i)))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * @return True if text can be considered as an numerical entity with optional percentage symbol,
   * brackets, negative sign, else return False
   */
  public static boolean isAmountOrPercentage(String text) {
    String textTrimmed = text.trim();
    if (textTrimmed.isEmpty()) {
      return false;
    }
    String textTrimmedWithoutBraces = textTrimmed;
    try {
      // Removing opening and closing brackets
      if (textTrimmed.charAt(0) == '(' && textTrimmed.charAt(textTrimmed.length() - 1) == ')') {
        textTrimmedWithoutBraces = textTrimmed.substring(1, textTrimmed.length() - 1);
      }
      // Removing negative sign
      if (textTrimmedWithoutBraces.charAt(0) == '-'
          || textTrimmedWithoutBraces.charAt(0) == '\u2212') {
        textTrimmedWithoutBraces = textTrimmedWithoutBraces.substring(1);
      }
      // Remove percentage  symbol
      if (textTrimmedWithoutBraces.charAt(textTrimmedWithoutBraces.length() - 1) == '%') {
        textTrimmedWithoutBraces = textTrimmedWithoutBraces
            .substring(0, textTrimmedWithoutBraces.length() - 1).trim();
      }
    } catch (StringIndexOutOfBoundsException ignored) {
      return false;
    }

    // Remove punctuations like . and ,
    textTrimmedWithoutBraces = textTrimmedWithoutBraces.replaceAll("[\\.,]", "");
    return !textTrimmedWithoutBraces.isEmpty() && isNumber(textTrimmedWithoutBraces);
  }

  /**
   * Check whether the {@code text} is a number in any system like roman system, etc.
   *
   * @param text Text which is to be checked
   * @return boolean flag indicating whether {@code text} is number in any system
   */
  public static boolean isNumberInAnySystem(String text) {
    return isNumber(text) || isRomanNumber(text);
  }

  /**
   * @return True if {@code text} contains only numbers
   */
  public static boolean isNumber(String text) {
    return NUM_PATTERN.matcher(text).matches();
  }

  /**
   * @return True if {@code text} is alpha numeric
   */
  public static boolean isAlphaNumeric(String text) {
    return !text.isEmpty() && ALPHA_NUM_PATTERN.matcher(text).matches();
  }

  /**
   * @return True if {@code text} is a bullet symbol like black square box.
   */
  public static boolean isBullet(String text) {
    return !text.isEmpty() && BULLET_PATTERN.matcher(text).matches();
  }

  /**
   * @return True if {@code text} is a roman number
   */
  public static boolean isRomanNumber(String text) {
    if (isLowerCaseAlphabeticString(text)) {
      text = text.toUpperCase();
    }
    return !text.isEmpty() && isUpperCaseAlphabeticString(text) && ROMAN_NUM_PATTERN.matcher(text)
        .matches();
  }

  /**
   * @return True if text is not empty and contains lowercase alphabets in {@code text}
   */
  public static boolean isLowerCaseAlphabeticString(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    boolean containsAlphabets = false;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (Character.isAlphabetic(ch)) {
        containsAlphabets = true;
        if (Character.isUpperCase(ch)) {
          return false;
        }
      }
    }
    return containsAlphabets;
  }

  /**
   * @return True if text is not empty and contains uppercase alphabets in {@code text}
   */
  public static boolean isUpperCaseAlphabeticString(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    boolean containsAlphabets = false;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (Character.isAlphabetic(ch)) {
        containsAlphabets = true;
        if (Character.isLowerCase(ch)) {
          return false;
        }
      }
    }
    return containsAlphabets;
  }

  /**
   * @return True if {@code text} is valid index after removing index starting and end delimiters
   */
  public static boolean containsOnlyIndex(String text) {
    if (!text.isEmpty()) {
      if (INDEX_MARKER_END_DELIM.matcher(text).find()) {
        String[] split = INDEX_MARKER_END_DELIM.split(text);
        if (split.length == 1) {
          String index = split[0];
          if (INDEX_MARKER_START_DELIM.matcher(index.substring(0, 1)).matches()) {
            index = index.substring(1);
          }
          for (IndexMarkerType indexMarkerType : IndexMarkerType.values()) {
            if (indexMarkerType.isValid(index)) {
              return true;
            }
          }
        }
      } else {
        return text.trim().length() == 1 && isBullet(text.trim());
      }
    }
    return false;
  }

  /**
   * Checks whether {@code text} contains total keywords like total, net, estimated, etc
   *
   * @param text text which is being checked
   * @return boolean flag indicating whether {@code text} contains total keywords or not.
   */
  public static boolean meansTotal(String text) {
    return CONTAINS_TOTAL_PHRASE_PATTERN.matcher(text).matches();
  }

  /**
   * Checks whether sentence by connecting text1 and text2 makes sense semantically or not. Example:
   * <ol> <li> text1: "Key:",  text2: "Value"</> <li> text1: "This,",  text2: "can be connected"</>
   * <li> text1: "2nd.",  text2: "sentence"</> </ol> {"Key:", "Value"), ()
   *
   * @param text1 first part of sentence
   * @param text2 second part of sentence
   * @return boolean flag indicating whether {@code text1} can be connected to {@code text2}
   */
  public static boolean canBeConnected(String text1, String text2) {
    // Currently inferring only from semantics of 1st element
    return text1.endsWith(":")
        || text1.endsWith(",")
        || (CONTAINS_NUM_PATTERN.matcher(text1).matches() && text1.endsWith("."));
  }

  /**
   * Checks whether the {@code strings} represent header. It is header if it contains all alpha
   * numeric strings except the first string
   *
   * @param strings text components of string
   * @return boolean flag indicating whether {@code strings} represent header or not.
   */
  public static boolean canBeHeader(MutableList<String> strings) {
    if (Iterate.isEmpty(strings)) {
      return false;
    }
    String textStr = strings.get(0);
    if (strings.size() == 1) {
      return RegexType.getFor(textStr).getPriority() >= RegexType.ALPHA.getPriority()
          && !SemanticsChecker.isLastLineOfParagraph(textStr);
    }
    MutableList<String> stringsExcludingFirst = strings.subList(1, strings.size());
    return stringsExcludingFirst
        .allSatisfy(s -> RegexType.getFor(s).getPriority() >= RegexType.ALPHA.getPriority());
  }

  /**
   * Checks whether {@code textStr} can be considered a last line in a paragraph.
   *
   * @param textStr text string which is being checked.
   * @return boolean flag if {@code textStr} is actually a last line of paragraph.
   */
  public static boolean isLastLineOfParagraph(String textStr) {
    if (textStr.endsWith(".")) {
      String substringExcludingLastPeriod = textStr.substring(0, textStr.length() - 1);
      int prevPeriodIndex = substringExcludingLastPeriod.indexOf('.');
      // If there is only one full stop at the end of sentence, then textStr is last paragraph
      if (prevPeriodIndex == -1) {
        return true;
      }

      // If there is one more full stop and sentence after it, then textStr is last paragraph
      String[] words = substringExcludingLastPeriod.substring(prevPeriodIndex + 1).split("\\s+");
      return words.length > 1;
    }
    return false;
  }

  /**
   * Checks whether {@code textStr} represents named entity like city. Currently added support for
   * only city.
   *
   * @param textStr text string which is being checked
   * @return boolean flag indicating whether {@code textStr} is named entity
   */
  public static boolean isNamedEntity(String textStr) {
    return !textStr.isEmpty() && exactlyMatchesCity(textStr);
  }

  /**
   * Check whether the input is city or not
   *
   * @param searchText text string which will be checked for city match
   * @return boolean flag indicating whether {@code searchText} is city or not
   */
  public static boolean exactlyMatchesCity(String searchText) {
    String normalizedSearchText = normalizeLocationalText(searchText);
    return !getCommonNonLocationalTokens().contains(normalizedSearchText.toLowerCase())
        && getCityNames().contains(normalizedSearchText);
  }

  /**
   * Normalize the location text by replacing different variations of punctuation or space with " "
   * character
   *
   * @param text text string which will be normalized
   * @return normalized text
   */
  public static String normalizeLocationalText(String text) {
    return PUNCTUATION_OR_SPACE_PATTERN.matcher(text.toLowerCase()).replaceAll(SEPARATOR)
        .trim();     // to handle justified inputs 'George   Town' -> 'George Town', 'Wicklow.' -> 'Wicklow'
  }

  /**
   * Retrieve tokens which can not represent location
   *
   * @return common not locational tokens
   */
  public static MutableSet<String> getCommonNonLocationalTokens() {
    if (commonNonLocationalTokens == null) {
      commonNonLocationalTokens = FileUtils.readListFromResourceFile("CommonNonLocationTokens.txt")
          .toSet();
    }
    return commonNonLocationalTokens;
  }

  /**
   * Retrieve tokens which represent city names
   *
   * @return city names
   */
  public static MutableSet<String> getCityNames() {
    if (cityNames == null) {
      cityNames = FileUtils.readListFromResourceFile("cityNames.txt").toSet();
    }
    return cityNames;
  }

  /**
   * Class to present index like alpha
   */
  public enum IndexMarkerType {
    ALPHANUM {
      @Override
      public boolean isValid(String index) {
        return index.length() <= 3 && SemanticsChecker.isAlphaNumeric(index);
      }
    },
    ROMAN {
      @Override
      public boolean isValid(String index) {
        return SemanticsChecker.isRomanNumber(index);
      }
    },
    BULLETED {
      @Override
      public boolean isValid(String index) {
        return SemanticsChecker.isBullet(index);
      }
    };

    /**
     * @return if {@code index} is of valid index format
     */
    public abstract boolean isValid(String index);
  }

  /**
   * Enum to represent regex type
   */
  public enum RegexType {
    NON_ALPHANUMERIC(0), NUMERIC(1), DATE(2), ALPHA(3), ALPHANUMERIC(4);

    private final int priority;

    RegexType(int priority) {
      this.priority = priority;
    }

    /**
     * Compute regex type for {@code text}
     *
     * @param text String for which regex type is computed
     * @return computed regex type
     */
    public static RegexType getFor(String text) {
      if (hasAlphabets(text)) {
        if (hasNumericContent(text)) {
          return ALPHANUMERIC;
        }
        return ALPHA;
      }
      if (isRelaxedNumericDate(text)) {
        return DATE;
      }
      if (hasNumericContent(text)) {
        return NUMERIC;
      }
      return NON_ALPHANUMERIC;
    }

    static boolean isRelaxedNumericDate(String text) {
      MutableList<Pair<String, String>> dateFormatsList = Lists.mutable
          .ofAll(DateFormatConstants.MIDDLE_ENDIAN_REGEX_AND_FORMAT_TUPLES);
      dateFormatsList.addAll(DateFormatConstants.LITTLE_ENDIAN_REGEX_AND_FORMAT_TUPLES);
      dateFormatsList.addAll(DateFormatConstants.BIG_ENDIAN_REGEX_AND_FORMAT_TUPLES);
      dateFormatsList.addAll(DateFormatConstants.ONLY_YEAR_REGEX_AND_FORMAT_TUPLES);
      return ParsedDate.getParsedDate(text, dateFormatsList).isMatchFound();
    }

    public int getPriority() {
      return this.priority;
    }
  }
}
