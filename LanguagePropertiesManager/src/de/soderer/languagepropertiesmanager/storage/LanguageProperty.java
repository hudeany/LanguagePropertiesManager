package de.soderer.languagepropertiesmanager.storage;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import de.soderer.utilities.Utilities;

public class LanguageProperty {
	private int originalIndex;
	private String key;
	private final Map<String, String> languageValues = new HashMap<>();

	public LanguageProperty(final String key) {
		this.key = key;
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

	public LanguageProperty setKey(final String key) {
		this.key = key;
		return this;
	}

	public String getKey() {
		return key;
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

	public LanguageProperty setLanguageValue(final String languageSign, final String value) {
		if (Utilities.isEmpty(value)) {
			languageValues.put(languageSign, null);
		} else {
			languageValues.put(languageSign, value);
		}
		return this;
	}

	public static class KeyComparator implements Comparator<String> {
		private final boolean ascending;

		public KeyComparator(final boolean ascending) {
			this.ascending = ascending;
		}

		@Override
		public int compare(final String arg0, final String arg1) {
			return arg0.toLowerCase().compareTo(arg1.toLowerCase()) * (ascending ? 1 : -1);
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

	public static class OriginalIndexComparator implements Comparator<Map.Entry<String, LanguageProperty>> {
		private final boolean ascending;

		public OriginalIndexComparator(final boolean ascending) {
			this.ascending = ascending;
		}

		@Override
		public int compare(final Map.Entry<String, LanguageProperty> entry1, final Map.Entry<String, LanguageProperty> entry2) {
			final int result = Integer.valueOf(entry1.getValue().getOriginalIndex()).compareTo(entry2.getValue().getOriginalIndex());
			return result * (ascending ? 1 : -1);
		}
	}
}
