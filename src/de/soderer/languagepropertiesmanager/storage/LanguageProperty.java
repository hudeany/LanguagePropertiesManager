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
		return Utilities.isEmpty(key) || languageValues.size() == 0;
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

	public LanguageProperty setLanguageValue(final String languageSign, final String value) {
		if (Utilities.isEmpty(value)) {
			languageValues.put(languageSign, null);
		} else {
			languageValues.put(languageSign, value);
		}
		return this;
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
