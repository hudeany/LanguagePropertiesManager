package de.soderer.languagepropertiesmanager.storage;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import de.soderer.utilities.Utilities;

public class LanguageProperty {
	private String path;
	private String key;
	private String comment;
	private int originalIndex;
	private final Map<String, String> languageValues = new HashMap<>();

	public LanguageProperty(final String path, final String key) {
		this.path = Utilities.replaceUsersHomeByTilde(path);
		this.key = key;
	}

	public LanguageProperty setKey(final String key) {
		this.key = key;
		return this;
	}

	public String getKey() {
		return key;
	}

	public String getPath() {
		return path;
	}

	public LanguageProperty setPath(final String path) {
		this.path = path;
		return this;
	}

	public String getComment() {
		return comment;
	}

	public LanguageProperty setComment(final String comment) {
		this.comment = comment;
		return this;
	}

	public int getOriginalIndex() {
		return originalIndex;
	}

	public LanguageProperty setOriginalIndex(final int originalIndex) {
		this.originalIndex = originalIndex;
		return this;
	}

	public boolean isEmpty() {
		if (Utilities.isEmpty(key)) {
			return true;
		}
		for (final String value : languageValues.values()) {
			if (Utilities.isNotEmpty(value)) {
				return false;
			}
		}
		return true;
	}

	public LanguageProperty removeLanguageValue(final String languageSign) {
		languageValues.remove(languageSign);
		return this;
	}

	public Set<String> getAvailableLanguageSigns() {
		return languageValues.keySet();
	}

	public String getLanguageValue(final String languageSign) {
		return languageValues.get(languageSign);
	}

	public boolean containsLanguage(final String languageSign) {
		return languageValues.containsKey(languageSign);
	}

	/**
	 * Sets the value of a language. The value is stored as it is:
	 * <ul>
	 * <li>null: the key is missing in this language (it is omitted in that language file, so ResourceBundle falls back to the default value).
	 * The language sign stays registered, so the language is still shown as a column.</li>
	 * <li>"" (empty string): the key exists with an explicitly empty value (written as "key=").</li>
	 * </ul>
	 * For values entered by the user or read from import files use {@link #toStorageValue(String, String)} first.
	 */
	public LanguageProperty setLanguageValue(final String languageSign, final String value) {
		languageValues.put(languageSign, value);
		return this;
	}

	/**
	 * Maps an input value (UI field, import cell), which can not distinguish between "empty" and "missing", to the stored value:
	 * <ul>
	 * <li>non-empty values are kept as they are</li>
	 * <li>empty values become "" for the default language, so the key is not lost on saving</li>
	 * <li>empty values become null for all other languages, so the key is omitted in that language file and ResourceBundle falls back to the default value</li>
	 * </ul>
	 */
	public static String toStorageValue(final String languageSign, final String inputValue) {
		if (Utilities.isNotEmpty(inputValue)) {
			return inputValue;
		} else if (LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT.equals(languageSign)) {
			return "";
		} else {
			return null;
		}
	}

	public static class EntryValueExistsComparator implements Comparator<Map.Entry<String, LanguageProperty>> {
		private final String languageSign;
		private final boolean ascending;

		public EntryValueExistsComparator(final String languageSign, final boolean ascending) {
			this.languageSign = languageSign;
			this.ascending = ascending;
		}

		@Override
		public int compare(final Map.Entry<String, LanguageProperty> entry1, final Map.Entry<String, LanguageProperty> entry2) {
			int result;

			final boolean value1Exists = entry1.getValue().getLanguageValue(languageSign) != null;
			final boolean value2Exists = entry2.getValue().getLanguageValue(languageSign) != null;
			if (value1Exists == value2Exists) {
				result = entry1.getKey().toLowerCase().compareTo(entry2.getKey().toLowerCase());
			} else if (value1Exists) {
				result = -1;
			} else {
				result = +1;
			}

			return result * (ascending ? 1 : -1);
		}
	}
}
