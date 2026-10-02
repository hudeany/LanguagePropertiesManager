package de.soderer.languagepropertiesmanager.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.soderer.utilities.Utilities;

public class LanguageProperty {
	private String path;
	private String key;
	private String comment;
	private int originalIndex;
	private int emptyLinesBefore = 0;
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

	public int getEmptyLinesBefore() {
		return emptyLinesBefore;
	}

	/**
	 * Number of empty lines written before this property (and its comment) to keep blocks of properties visually grouped.
	 */
	public LanguageProperty setEmptyLinesBefore(final int emptyLinesBefore) {
		this.emptyLinesBefore = Math.max(0, emptyLinesBefore);
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

	/**
	 * Next free original index for a property appended to the given properties.
	 * The list size is not sufficient, because after removing properties a new index could collide with an existing one.
	 */
	public static int getNextOriginalIndex(final Collection<LanguageProperty> languageProperties) {
		int maxOriginalIndex = 0;
		if (languageProperties != null) {
			for (final LanguageProperty languageProperty : languageProperties) {
				maxOriginalIndex = Math.max(maxOriginalIndex, languageProperty.getOriginalIndex());
			}
		}
		return maxOriginalIndex + 1;
	}

	/**
	 * Before properties are removed, their empty lines are passed on to the next remaining property of the same path (by original index),
	 * so the visual grouping of blocks in the properties files is kept, also if the first property of a block is removed.
	 * Must be called while the properties to remove are still contained in allProperties (they are skipped as targets).
	 */
	public static void passOnEmptyLinesOfPropertiesToRemove(final Collection<LanguageProperty> allProperties, final Collection<LanguageProperty> propertiesToRemove) {
		if (allProperties == null || propertiesToRemove == null || propertiesToRemove.isEmpty()) {
			return;
		}

		final Set<LanguageProperty> propertiesToRemoveSet = Collections.newSetFromMap(new IdentityHashMap<>());
		propertiesToRemoveSet.addAll(propertiesToRemove);

		final List<LanguageProperty> remainingProperties = new ArrayList<>();
		for (final LanguageProperty languageProperty : allProperties) {
			if (!propertiesToRemoveSet.contains(languageProperty)) {
				remainingProperties.add(languageProperty);
			}
		}

		for (final LanguageProperty propertyToRemove : propertiesToRemoveSet) {
			if (propertyToRemove.getEmptyLinesBefore() > 0) {
				LanguageProperty nextProperty = null;
				for (final LanguageProperty remainingProperty : remainingProperties) {
					if (Objects.equals(remainingProperty.getPath(), propertyToRemove.getPath())
							&& remainingProperty.getOriginalIndex() > propertyToRemove.getOriginalIndex()
							&& (nextProperty == null || remainingProperty.getOriginalIndex() < nextProperty.getOriginalIndex())) {
						nextProperty = remainingProperty;
					}
				}
				if (nextProperty != null) {
					nextProperty.setEmptyLinesBefore(Math.max(nextProperty.getEmptyLinesBefore(), propertyToRemove.getEmptyLinesBefore()));
				}
			}
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
