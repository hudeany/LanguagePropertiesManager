package de.soderer.languagepropertiesmanager.check;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;

/**
 * Read-only checks of language properties for encoding errors, invalid keys,
 * inconsistent placeholders and formal deviations from the reference language.
 * No GUI dependencies, all texts of the report come from LangResources.
 */
public final class LanguagePropertiesChecker {
	private LanguagePropertiesChecker() {
		// Utility class, no instances
	}

	/**
	 * Byte values of the Windows-1252 characters in the range 0x80-0x9F (e.g. '€' = 0x80, '“' = 0x93).
	 * All other characters up to U+00FF have the same byte value as their code point (ISO-8859-1).
	 */
	private static final Map<Character, Integer> WINDOWS_1252_SPECIAL_CHARACTER_BYTES = createWindows1252SpecialCharacterBytes();

	private static Map<Character, Integer> createWindows1252SpecialCharacterBytes() {
		final Map<Character, Integer> characterBytes = new HashMap<>();
		final Charset windows1252 = Charset.forName("windows-1252");
		for (int byteValue = 0x80; byteValue <= 0x9F; byteValue++) {
			final String decoded = new String(new byte[] { (byte) byteValue }, windows1252);
			if (decoded.length() == 1 && decoded.charAt(0) != '\uFFFD') {
				characterBytes.put(decoded.charAt(0), byteValue);
			}
		}
		return characterBytes;
	}

	/**
	 * Returns the single byte value of a character in ISO-8859-1 / Windows-1252, or -1 if the
	 * character does not exist in these charsets (then it can not be part of a mojibake sequence).
	 */
	private static int getSingleByteValue(final char character) {
		if (character <= 0xFF) {
			return character;
		} else {
			final Integer byteValue = WINDOWS_1252_SPECIAL_CHARACTER_BYTES.get(character);
			return byteValue == null ? -1 : byteValue;
		}
	}

	/**
	 * Returns the length of a valid UTF-8 byte sequence, which is represented by the characters
	 * of the text starting at the given index when those are read as ISO-8859-1 / Windows-1252
	 * (e.g. "Ã¤" for "ä", "â€ž" for "„"), or 0 if there is no such sequence at this index.
	 */
	private static int getMojibakeSequenceLength(final String text, final int index) {
		final int leadByte = getSingleByteValue(text.charAt(index));
		final int sequenceLength;
		if (leadByte >= 0xC2 && leadByte <= 0xDF) {
			sequenceLength = 2;
		} else if (leadByte >= 0xE0 && leadByte <= 0xEF) {
			sequenceLength = 3;
		} else if (leadByte >= 0xF0 && leadByte <= 0xF4) {
			sequenceLength = 4;
		} else {
			return 0;
		}
		if (index + sequenceLength > text.length()) {
			return 0;
		}

		final byte[] sequenceBytes = new byte[sequenceLength];
		sequenceBytes[0] = (byte) leadByte;
		for (int i = 1; i < sequenceLength; i++) {
			final int continuationByte = getSingleByteValue(text.charAt(index + i));
			if (continuationByte < 0x80 || continuationByte > 0xBF) {
				return 0;
			}
			sequenceBytes[i] = (byte) continuationByte;
		}

		// Strict decoding rejects overlong encodings and encoded surrogates
		try {
			StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(sequenceBytes));
			return sequenceLength;
		} catch (@SuppressWarnings("unused") final CharacterCodingException e) {
			return 0;
		}
	}

	/**
	 * Detects UTF-8 encoded text, which was mistakenly read as ISO-8859-1 / Windows-1252 ("Mojibake"),
	 * e.g. "ä" becoming "Ã¤" or "„" becoming "â€ž".
	 *
	 * <p>
	 * Instead of searching for single suspicious characters like "Â" (which is a valid letter,
	 * e.g. in the French word "Âge"), only complete and valid UTF-8 byte sequences are reported:
	 * <ul>
	 * <li>3 and 4 byte sequences (e.g. "â€ž", "ðŸ˜€"), which practically never occur in real text</li>
	 * <li>2 byte sequences starting with "Â" or "Ã" (U+0080 - U+00FF, e.g. "Ã¤", "Â°")</li>
	 * <li>other 2 byte sequences (e.g. cyrillic "Ð¿Ñ€") only if directly followed by another sequence,
	 * because a single one also occurs in real text (e.g. "Fuß“" or "ÉTÉ" followed by a no-break space)</li>
	 * </ul>
	 *
	 * @param text
	 *            text to check
	 * @return true if the text contains mojibake
	 */
	public static boolean containsMojibake(final String text) {
		for (int i = 0; i < text.length(); i++) {
			final int sequenceLength = getMojibakeSequenceLength(text, i);
			if (sequenceLength >= 3) {
				return true;
			} else if (sequenceLength == 2) {
				final char leadCharacter = text.charAt(i);
				if (leadCharacter == '\u00C2' || leadCharacter == '\u00C3') {
					return true;
				} else if (i + 2 < text.length() && getMojibakeSequenceLength(text, i + 2) > 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static final Pattern UNRESOLVED_UNICODE_ESCAPE_PATTERN = Pattern.compile("\\\\u[0-9A-Fa-f]{4}");

	/**
	 * Checks a single piece of text (key, value or comment) for signs of encoding corruption
	 * or other structural problems and returns a list of human readable problem descriptions.
	 * Returns an empty list if no problems were found.
	 *
	 * @param text
	 *            text to check, may be null
	 * @return descriptions of the found problems
	 */
	public static List<String> findTextErrors(final String text) {
		final List<String> problems = new ArrayList<>();
		if (text == null) {
			return problems;
		}

		if (text.indexOf('\uFFFD') >= 0) {
			problems.add(LangResources.get("error_replacement_char"));
		}

		if (containsMojibake(text)) {
			problems.add(LangResources.get("error_mojibake"));
		}

		if (UNRESOLVED_UNICODE_ESCAPE_PATTERN.matcher(text).find()) {
			problems.add(LangResources.get("error_unresolved_unicode_escape"));
		}

		if (text.indexOf('\uFEFF') >= 0) {
			problems.add(LangResources.get("error_bom_char"));
		}

		boolean isolatedSurrogateFound = false;
		boolean controlCharFound = false;
		for (int i = 0; i < text.length() && !(isolatedSurrogateFound && controlCharFound); i++) {
			final char currentChar = text.charAt(i);
			if (!isolatedSurrogateFound) {
				if (Character.isHighSurrogate(currentChar)) {
					if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
						isolatedSurrogateFound = true;
					}
				} else if (Character.isLowSurrogate(currentChar)) {
					if (i == 0 || !Character.isHighSurrogate(text.charAt(i - 1))) {
						isolatedSurrogateFound = true;
					}
				}
			}
			if (!controlCharFound && Character.isISOControl(currentChar) && currentChar != '\t' && currentChar != '\n' && currentChar != '\r') {
				controlCharFound = true;
			}
		}
		if (isolatedSurrogateFound) {
			problems.add(LangResources.get("error_isolated_surrogate"));
		}
		if (controlCharFound) {
			problems.add(LangResources.get("error_control_char"));
		}

		return problems;
	}

	private static final Pattern MESSAGE_FORMAT_ARGUMENT_PATTERN = Pattern.compile("\\{\\s*\\d");

	/**
	 * java.util.Formatter conversions like %s, %d, %1$s, %.2f, %tY.
	 * The space flag is deliberately not supported to avoid false positives in texts like "50% sure".
	 */
	private static final Pattern PRINTF_PLACEHOLDER_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?[-#+0,(<]*\\d*(?:\\.\\d+)?([bBhHsScCdoxXeEfgGaA%n]|[tT][a-zA-Z])");

	/**
	 * Result of the analysis of a single text as java.text.MessageFormat pattern
	 */
	private static class MessageFormatAnalysis {
		private final Set<Integer> argumentIndexes = new TreeSet<>();
		private String syntaxError = null;
	}

	private static MessageFormatAnalysis analyzeMessageFormat(final String text) {
		final MessageFormatAnalysis analysis = new MessageFormatAnalysis();

		try {
			@SuppressWarnings("unused")
			final
			MessageFormat test = new MessageFormat(text);
		} catch (final IllegalArgumentException e) {
			analysis.syntaxError = e.getMessage();
		}

		// Apostrophes are deliberately not treated as MessageFormat quotes, because they are regular characters in many languages (e.g. Italian, French)
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '{') {
				int argumentEnd = i + 1;
				while (argumentEnd < text.length() && text.charAt(argumentEnd) != ',' && text.charAt(argumentEnd) != '}') {
					argumentEnd++;
				}
				// MessageFormat allows whitespace around the argument index, e.g. "{ 0 }"
				final String argumentNumber = text.substring(i + 1, argumentEnd).trim();
				if (argumentNumber.length() > 0 && argumentNumber.length() <= 9 && argumentNumber.chars().allMatch(Character::isDigit)) {
					analysis.argumentIndexes.add(Integer.parseInt(argumentNumber));
				}
			}
		}

		return analysis;
	}

	private static List<String> findPrintfPlaceholders(final String text) {
		final List<String> placeholders = new ArrayList<>();
		final Matcher matcher = PRINTF_PLACEHOLDER_PATTERN.matcher(text);
		while (matcher.find()) {
			final String conversion = matcher.group(2);
			if ("%".equals(conversion) || "n".equals(conversion)) {
				// "%%" and "%n" consume no argument
				continue;
			}
			final String argumentIndex = matcher.group(1);
			// Upper case variants (%S, %X, %T...) only change the output case, but date/time suffixes (%tY vs. %ty) are significant
			final String normalizedConversion = conversion.substring(0, 1).toLowerCase(Locale.ROOT) + conversion.substring(1);
			placeholders.add("%" + (argumentIndex != null ? argumentIndex + "$" : "") + normalizedConversion);
		}
		return placeholders;
	}

	/**
	 * Returns the items of minuend that are not matched by an item of subtrahend, respecting duplicates
	 */
	private static List<String> subtractMultiset(final List<String> minuend, final List<String> subtrahend) {
		final List<String> result = new ArrayList<>(minuend);
		for (final String item : subtrahend) {
			result.remove(item);
		}
		return result;
	}

	/**
	 * Checks whether the placeholders of all language values of a property match the placeholders
	 * of the reference value (default language, or else the first language with a value).
	 * Supports java.text.MessageFormat arguments ({0}, {1,number}) and java.util.Formatter conversions (%s, %1$d).
	 * Empty values are skipped, because a missing translation is not a placeholder error.
	 *
	 * @param languageProperty
	 *            property to check
	 * @return descriptions of the found problems
	 */
	public static List<String> findPlaceholderErrors(final LanguageProperty languageProperty) {
		final List<String> problems = new ArrayList<>();

		final Map<String, String> values = getNonBlankValuesReferenceFirst(languageProperty);
		if (values.isEmpty()) {
			return problems;
		}

		final String referenceLanguageSign = values.keySet().iterator().next();

		// MessageFormat: If any language uses arguments, all languages of this key are expected to be formatted by MessageFormat
		if (values.values().stream().anyMatch(value -> MESSAGE_FORMAT_ARGUMENT_PATTERN.matcher(value).find())) {
			final Map<String, MessageFormatAnalysis> analyses = new LinkedHashMap<>();
			for (final Map.Entry<String, String> entry : values.entrySet()) {
				analyses.put(entry.getKey(), analyzeMessageFormat(entry.getValue()));
			}
			final Set<Integer> referenceIndexes = analyses.get(referenceLanguageSign).argumentIndexes;

			for (final Map.Entry<String, MessageFormatAnalysis> entry : analyses.entrySet()) {
				final String fieldPrefix = LangResources.get("field_value", entry.getKey()) + ": ";
				final MessageFormatAnalysis analysis = entry.getValue();

				if (analysis.syntaxError != null) {
					problems.add(fieldPrefix + LangResources.get("error_messageformat_syntax", analysis.syntaxError));
				}

				if (!entry.getKey().equals(referenceLanguageSign)) {
					final Set<Integer> missingIndexes = new TreeSet<>(referenceIndexes);
					missingIndexes.removeAll(analysis.argumentIndexes);
					final Set<Integer> additionalIndexes = new TreeSet<>(analysis.argumentIndexes);
					additionalIndexes.removeAll(referenceIndexes);

					if (!missingIndexes.isEmpty()) {
						problems.add(fieldPrefix + LangResources.get("error_placeholder_missing", referenceLanguageSign, missingIndexes.stream().map(index -> "{" + index + "}").collect(Collectors.joining(", "))));
					}
					if (!additionalIndexes.isEmpty()) {
						problems.add(fieldPrefix + LangResources.get("error_placeholder_additional", referenceLanguageSign, additionalIndexes.stream().map(index -> "{" + index + "}").collect(Collectors.joining(", "))));
					}
				}
			}
		}

		// java.util.Formatter: If any language uses conversions, compare all languages of this key
		final Map<String, List<String>> printfPlaceholders = new LinkedHashMap<>();
		for (final Map.Entry<String, String> entry : values.entrySet()) {
			printfPlaceholders.put(entry.getKey(), findPrintfPlaceholders(entry.getValue()));
		}
		if (printfPlaceholders.values().stream().anyMatch(placeholders -> !placeholders.isEmpty())) {
			final List<String> referencePlaceholders = printfPlaceholders.get(referenceLanguageSign);
			for (final Map.Entry<String, List<String>> entry : printfPlaceholders.entrySet()) {
				if (entry.getKey().equals(referenceLanguageSign)) {
					continue;
				}
				final String fieldPrefix = LangResources.get("field_value", entry.getKey()) + ": ";
				final List<String> placeholders = entry.getValue();

				final List<String> missingPlaceholders = subtractMultiset(referencePlaceholders, placeholders);
				final List<String> additionalPlaceholders = subtractMultiset(placeholders, referencePlaceholders);

				if (!missingPlaceholders.isEmpty()) {
					problems.add(fieldPrefix + LangResources.get("error_placeholder_missing", referenceLanguageSign, String.join(", ", missingPlaceholders)));
				}
				if (!additionalPlaceholders.isEmpty()) {
					problems.add(fieldPrefix + LangResources.get("error_placeholder_additional", referenceLanguageSign, String.join(", ", additionalPlaceholders)));
				}
				if (missingPlaceholders.isEmpty() && additionalPlaceholders.isEmpty() && !placeholders.equals(referencePlaceholders)) {
					// Same placeholders in a different order only matter if they are not all explicitly indexed like %1$s
					final boolean allExplicitlyIndexed = placeholders.stream().allMatch(placeholder -> placeholder.contains("$"));
					if (!allExplicitlyIndexed) {
						problems.add(fieldPrefix + LangResources.get("error_placeholder_order", referenceLanguageSign));
					}
				}
			}
		}

		return problems;
	}

	/**
	 * Returns all non blank language values of a property. The first entry is the reference value
	 * for comparisons: the default language, or else the first language with a value.
	 */
	private static Map<String, String> getNonBlankValuesReferenceFirst(final LanguageProperty languageProperty) {
		final Map<String, String> values = new LinkedHashMap<>();
		final String defaultValue = languageProperty.getLanguageValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);
		if (Utilities.isNotBlank(defaultValue)) {
			values.put(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT, defaultValue);
		}
		// Sorted, so the reference language and the order of the reported problems do not depend on the hash order
		for (final String languageSign : new TreeSet<>(languageProperty.getAvailableLanguageSigns())) {
			final String value = languageProperty.getLanguageValue(languageSign);
			if (Utilities.isNotBlank(value)) {
				values.putIfAbsent(languageSign, value);
			}
		}
		return values;
	}

	/**
	 * Returns the normalized sentence end punctuation of a text or null if the last character is no punctuation.
	 * Only the very last character is checked, so trailing whitespace, closing brackets or quotes mean "no end punctuation".
	 * Language specific variants (full width CJK punctuation, Greek/Arabic question marks, Devanagari danda)
	 * are mapped to their ASCII equivalent, "..." and "…" are both returned as "…".
	 */
	private static String getEndPunctuation(final String text) {
		if (text.isEmpty()) {
			return null;
		}

		if (text.endsWith("...") || text.endsWith("…")) {
			return "…";
		}

		switch (text.charAt(text.length() - 1)) {
			case '.':
			case '。':
			case '।':
				return ".";
			case ':':
			case '：':
				return ":";
			case '!':
			case '！':
				return "!";
			case '?':
			case '？':
			case '؟':
			case '\u037E': // Greek question mark
				return "?";
			case ';':
			case '；':
				return ";";
			case ',':
			case '，':
			case '、':
				return ",";
			default:
				return null;
		}
	}

	private static boolean startsWithWhitespace(final String text) {
		return text.length() > 0 && (Character.isWhitespace(text.charAt(0)) || Character.isSpaceChar(text.charAt(0)));
	}

	private static boolean endsWithWhitespace(final String text) {
		return text.length() > 0 && (Character.isWhitespace(text.charAt(text.length() - 1)) || Character.isSpaceChar(text.charAt(text.length() - 1)));
	}

	private static int countLineBreaks(final String text) {
		return (int) text.chars().filter(character -> character == '\n').count();
	}

	/**
	 * Checks the language values of a property for formal deviations from the reference value
	 * (default language, or else the first language with a value): different sentence end punctuation,
	 * leading or trailing whitespace only in one language and line breaks in translations of single line texts.
	 * Empty values are skipped, because a missing translation is no formal deviation.
	 *
	 * @param languageProperty
	 *            property to check
	 * @return descriptions of the found problems
	 */
	public static List<String> findFormalDeviations(final LanguageProperty languageProperty) {
		final List<String> problems = new ArrayList<>();

		final Map<String, String> values = getNonBlankValuesReferenceFirst(languageProperty);
		if (values.size() < 2) {
			return problems;
		}

		final Map.Entry<String, String> referenceEntry = values.entrySet().iterator().next();
		final String referenceLanguageSign = referenceEntry.getKey();
		final String referenceValue = referenceEntry.getValue();
		final String referenceEndPunctuation = getEndPunctuation(referenceValue);
		final int referenceLineBreaks = countLineBreaks(referenceValue);

		for (final Map.Entry<String, String> entry : values.entrySet()) {
			if (entry.getKey().equals(referenceLanguageSign)) {
				continue;
			}
			final String fieldPrefix = LangResources.get("field_value", entry.getKey()) + ": ";
			final String value = entry.getValue();

			final String endPunctuation = getEndPunctuation(value);
			if (referenceEndPunctuation != null && endPunctuation == null) {
				problems.add(fieldPrefix + LangResources.get("error_formal_end_punctuation_missing", referenceLanguageSign, referenceEndPunctuation));
			} else if (referenceEndPunctuation == null && ":".equals(endPunctuation)) {
				// Only an additional colon is reported, other additional end punctuation is often a legitimate language specific choice
				problems.add(fieldPrefix + LangResources.get("error_formal_end_punctuation_additional", referenceLanguageSign, endPunctuation));
			} else if (referenceEndPunctuation != null && !referenceEndPunctuation.equals(endPunctuation)) {
				problems.add(fieldPrefix + LangResources.get("error_formal_end_punctuation_different", referenceLanguageSign, endPunctuation, referenceEndPunctuation));
			}

			if (startsWithWhitespace(referenceValue) != startsWithWhitespace(value)) {
				problems.add(fieldPrefix + LangResources.get("error_formal_leading_whitespace", referenceLanguageSign));
			}
			if (endsWithWhitespace(referenceValue) != endsWithWhitespace(value)) {
				problems.add(fieldPrefix + LangResources.get("error_formal_trailing_whitespace", referenceLanguageSign));
			}

			final int lineBreaks = countLineBreaks(value);
			// Only reported for single line references, because multi line texts are often wrapped differently per language
			if (referenceLineBreaks == 0 && lineBreaks > 0) {
				problems.add(fieldPrefix + LangResources.get("error_formal_line_breaks", referenceLanguageSign, String.valueOf(lineBreaks), String.valueOf(referenceLineBreaks)));
			}
		}

		return problems;
	}

	/**
	 * Read-only check of the given properties for encoding errors, invalid keys, inconsistent placeholders
	 * and formal deviations from the reference language.
	 * Used by the "check errors" button and after a merge import for the imported properties.
	 *
	 * @param propertiesToCheck
	 *            properties to check, may be null
	 * @return number of issues and the report text
	 */
	public static ErrorReport createErrorReport(final Collection<LanguageProperty> propertiesToCheck) {
		final StringBuilder reportText = new StringBuilder();
		int issueCount = 0;

		if (propertiesToCheck != null) {
			for (final LanguageProperty languageProperty : propertiesToCheck) {
				final List<String> entryProblems = new ArrayList<>();

				final String key = languageProperty.getKey();
				if (Utilities.isBlank(key)) {
					entryProblems.add(LangResources.get("error_key_empty"));
				} else {
					for (final String textProblem : findTextErrors(key)) {
						entryProblems.add(LangResources.get("field_key") + ": " + textProblem);
					}
					if (!key.equals(key.trim()) || key.contains(" ")) {
						entryProblems.add(LangResources.get("field_key") + ": " + LangResources.get("error_key_whitespace"));
					}
					if (key.contains("=") || key.contains(":")) {
						entryProblems.add(LangResources.get("field_key") + ": " + LangResources.get("error_key_illegal_char"));
					}
				}

				for (final String textProblem : findTextErrors(languageProperty.getComment())) {
					entryProblems.add(LangResources.get("field_comment") + ": " + textProblem);
				}

				for (final String languageSign : new TreeSet<>(languageProperty.getAvailableLanguageSigns())) {
					final String value = languageProperty.getLanguageValue(languageSign);
					for (final String textProblem : findTextErrors(value)) {
						entryProblems.add(LangResources.get("field_value", languageSign) + ": " + textProblem);
					}
				}

				entryProblems.addAll(findPlaceholderErrors(languageProperty));
				entryProblems.addAll(findFormalDeviations(languageProperty));

				if (!entryProblems.isEmpty()) {
					issueCount += entryProblems.size();
					reportText.append("\"").append(languageProperty.getPath()).append("\" / \"").append(key).append("\":\n");
					for (final String problem : entryProblems) {
						reportText.append("  - ").append(problem).append("\n");
					}
				}
			}
		}

		return new ErrorReport(issueCount, reportText.toString());
	}
}
