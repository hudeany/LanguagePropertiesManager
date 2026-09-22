package de.soderer.languagepropertiesmanager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Constant values that must not be altered by machine translation (e.g. codes like "AbkHüftOP").
 *
 * CSV structure:
 * <ul>
 * <li>First line: header with language signs (e.g. "de;en;it")</li>
 * <li>Line with only one non-empty value: this value is identical in all languages</li>
 * <li>Other lines: value per language column</li>
 * </ul>
 * Separator (';', ',' or TAB) is detected from the header line. Values may be quoted with '"', quotes inside quoted values are doubled.
 * Unquoted values are trimmed, quoted values are kept as they are.
 */
public class TranslationConstants {
	/** Integer and decimal numbers, optionally signed, with '.' or ',' as decimal or grouping separators (e.g. "42", "3,14", "1.234.567,89") */
	private static final Pattern NUMBER_PATTERN = Pattern.compile("[+-]?\\d+(?:[.,]\\d+)*");

	private final Map<String, Integer> columnIndexByLanguageSign = new HashMap<>();

	/** Values that are identical in all languages */
	private final Set<String> universalConstants = new HashSet<>();

	/** Per column: value in this column's language -> complete row */
	private final List<Map<String, String[]>> rowsByColumnValue = new ArrayList<>();

	private TranslationConstants() {
		// Use read(File)
	}

	public static TranslationConstants read(final File csvFile) throws Exception {
		if (!csvFile.exists()) {
			throw new Exception("File does not exist: " + csvFile.getAbsolutePath());
		} else if (!csvFile.isFile()) {
			throw new Exception("Path is not a file: " + csvFile.getAbsolutePath());
		}

		String content = Files.readString(csvFile.toPath(), StandardCharsets.UTF_8);
		if (content.startsWith("\uFEFF")) {
			content = content.substring(1);
		}

		final char separator = detectSeparator(content);
		final List<List<String>> rows = parseCsv(content, separator);
		if (rows.isEmpty()) {
			throw new Exception("File contains no header line");
		}

		final TranslationConstants translationConstants = new TranslationConstants();

		final List<String> header = rows.get(0);
		for (int columnIndex = 0; columnIndex < header.size(); columnIndex++) {
			final String languageSign = header.get(columnIndex).trim().toLowerCase(Locale.ROOT);
			if (languageSign.isEmpty()) {
				throw new Exception("Empty language sign in header column " + (columnIndex + 1));
			} else if (translationConstants.columnIndexByLanguageSign.containsKey(languageSign)) {
				throw new Exception("Duplicate language sign in header: " + languageSign);
			}
			translationConstants.columnIndexByLanguageSign.put(languageSign, columnIndex);
			translationConstants.rowsByColumnValue.add(new HashMap<>());
		}

		for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
			final List<String> row = rows.get(rowIndex);

			final List<String> nonEmptyValues = new ArrayList<>();
			for (final String value : row) {
				if (!value.isEmpty()) {
					nonEmptyValues.add(value);
				}
			}

			if (nonEmptyValues.isEmpty()) {
				// Skip empty lines
				continue;
			} else if (nonEmptyValues.size() == 1) {
				translationConstants.universalConstants.add(nonEmptyValues.get(0));
			} else {
				if (row.size() > header.size()) {
					throw new Exception("Data row " + rowIndex + " has more values (" + row.size() + ") than header columns (" + header.size() + ")");
				}
				final String[] rowValues = new String[header.size()];
				for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
					rowValues[columnIndex] = row.get(columnIndex);
				}
				for (int columnIndex = 0; columnIndex < rowValues.length; columnIndex++) {
					if (rowValues[columnIndex] != null && !rowValues[columnIndex].isEmpty()) {
						// First occurrence wins for duplicate values
						translationConstants.rowsByColumnValue.get(columnIndex).putIfAbsent(rowValues[columnIndex], rowValues);
					}
				}
			}
		}

		return translationConstants;
	}

	/**
	 * Returns the constant translation of the given source value, or null if the value is not a known constant (then normal translation is needed).
	 * Language signs are matched case-insensitive. A sign like "de_AT" falls back to the column "de" if there is no column "de_at".
	 */
	public String getTranslation(final String sourceLanguageSign, final String sourceValue, final String targetLanguageSign) {
		if (sourceValue == null || sourceValue.isEmpty()) {
			return null;
		} else if (universalConstants.contains(sourceValue)) {
			return sourceValue;
		}

		final int sourceColumnIndex = getColumnIndex(sourceLanguageSign);
		final int targetColumnIndex = getColumnIndex(targetLanguageSign);
		if (sourceColumnIndex < 0 || targetColumnIndex < 0) {
			return null;
		}

		final String[] row = rowsByColumnValue.get(sourceColumnIndex).get(sourceValue);
		if (row == null) {
			return null;
		}

		final String targetValue = row[targetColumnIndex];
		return targetValue == null || targetValue.isEmpty() ? null : targetValue;
	}

	/**
	 * Numbers like "42", "-7", "3.14", "3,14" or "1.234.567,89" must stay unchanged
	 */
	public static boolean isNumberConstant(final String value) {
		return value != null && NUMBER_PATTERN.matcher(value.trim()).matches();
	}

	private int getColumnIndex(final String languageSign) {
		if (languageSign == null) {
			return -1;
		}

		final String normalizedLanguageSign = languageSign.trim().toLowerCase(Locale.ROOT);
		final Integer columnIndex = columnIndexByLanguageSign.get(normalizedLanguageSign);
		if (columnIndex != null) {
			return columnIndex;
		}

		// Fallback from regional variant to base language (e.g. "de_at" -> "de")
		final int regionSeparatorIndex = Math.max(normalizedLanguageSign.indexOf('_'), normalizedLanguageSign.indexOf('-'));
		if (regionSeparatorIndex > 0) {
			final Integer baseColumnIndex = columnIndexByLanguageSign.get(normalizedLanguageSign.substring(0, regionSeparatorIndex));
			if (baseColumnIndex != null) {
				return baseColumnIndex;
			}
		}

		return -1;
	}

	private static char detectSeparator(final String content) {
		int headerEndIndex = content.indexOf('\n');
		if (headerEndIndex < 0) {
			headerEndIndex = content.length();
		}
		final String headerLine = content.substring(0, headerEndIndex);

		char bestSeparator = ';';
		long bestCount = 0;
		for (final char separatorCandidate : new char[] { ';', ',', '\t' }) {
			final long count = headerLine.chars().filter(c -> c == separatorCandidate).count();
			if (count > bestCount) {
				bestCount = count;
				bestSeparator = separatorCandidate;
			}
		}
		return bestSeparator;
	}

	private static List<List<String>> parseCsv(final String content, final char separator) throws Exception {
		final List<List<String>> rows = new ArrayList<>();
		List<String> currentRow = new ArrayList<>();
		final StringBuilder currentValue = new StringBuilder();
		boolean insideQuotes = false;
		boolean valueWasQuoted = false;

		for (int i = 0; i < content.length(); i++) {
			final char c = content.charAt(i);
			if (insideQuotes) {
				if (c == '"') {
					if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
						// Escaped quote
						currentValue.append('"');
						i++;
					} else {
						insideQuotes = false;
					}
				} else {
					currentValue.append(c);
				}
			} else if (c == '"' && !valueWasQuoted && currentValue.toString().trim().isEmpty()) {
				currentValue.setLength(0);
				insideQuotes = true;
				valueWasQuoted = true;
			} else if (c == separator) {
				currentRow.add(finishValue(currentValue, valueWasQuoted));
				valueWasQuoted = false;
			} else if (c == '\r' || c == '\n') {
				if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
					i++;
				}
				currentRow.add(finishValue(currentValue, valueWasQuoted));
				valueWasQuoted = false;
				rows.add(currentRow);
				currentRow = new ArrayList<>();
			} else {
				currentValue.append(c);
			}
		}

		if (insideQuotes) {
			throw new Exception("Unclosed quoted value in CSV data");
		}

		if (currentValue.length() > 0 || valueWasQuoted || !currentRow.isEmpty()) {
			currentRow.add(finishValue(currentValue, valueWasQuoted));
			rows.add(currentRow);
		}

		return rows;
	}

	private static String finishValue(final StringBuilder currentValue, final boolean valueWasQuoted) {
		final String value = valueWasQuoted ? currentValue.toString() : currentValue.toString().trim();
		currentValue.setLength(0);
		return value;
	}
}
