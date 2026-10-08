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

/**
 * One key of a language properties set with its values in all languages.
 *
 * <p>
 * The path identifies the properties set (directory and set name, without
 * language sign and file extension, the user's home directory replaced by
 * "~"). A language value of null means the key is missing in that language
 * file, an empty string means the key exists with an explicitly empty value.
 * </p>
 */
public class LanguageProperty {
	private String path;
	private String key;
	private String comment;
	private int originalIndex;
	private int emptyLinesBefore = 0;
	private final Map<String, String> languageValues = new HashMap<>();

	/**
	 * Creates a property without any language value.
	 *
	 * @param path
	 *            path of the properties set (without language sign and file extension), the user's home directory is replaced by "~"
	 * @param key
	 *            key of the property
	 */
	public LanguageProperty(final String path, final String key) {
		this.path = Utilities.replaceUsersHomeByTilde(path);
		this.key = key;
	}

	/**
	 * Sets the key.
	 *
	 * @param key
	 *            new key of the property
	 */
	public void setKey(final String key) {
		this.key = key;
	}

	/**
	 * Sets the key (fluent variant of {@link #setKey(String)}).
	 *
	 * @param newKey
	 *            new key of the property
	 * @return this property for chaining
	 */
	public LanguageProperty withKey(final String newKey) {
		setKey(newKey);
		return this;
	}

	/**
	 * Key of the property.
	 *
	 * @return the key
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Path of the properties set this property belongs to (without language sign and file extension).
	 *
	 * @return the path, empty if the property has no path yet
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Sets the path of the properties set this property belongs to.
	 *
	 * @param path
	 *            path without language sign and file extension, empty for "no path yet"
	 */
	public void setPath(final String path) {
		this.path = path;
	}

	/**
	 * Sets the path of the properties set this property belongs to (fluent variant of {@link #setPath(String)}).
	 *
	 * @param newPath
	 *            path without language sign and file extension, empty for "no path yet"
	 * @return this property for chaining
	 */
	public LanguageProperty withPath(final String newPath) {
		setPath(newPath);
		return this;
	}

	/**
	 * Comment written above the property in the properties files.
	 *
	 * @return the comment or null
	 */
	public String getComment() {
		return comment;
	}

	/**
	 * Sets the comment written above the property in the properties files.
	 *
	 * @param comment
	 *            the comment or null for none
	 */
	public void setComment(final String comment) {
		this.comment = comment;
	}

	/**
	 * Sets the comment written above the property in the properties files (fluent variant of {@link #setComment(String)}).
	 *
	 * @param newComment
	 *            the comment or null for none
	 * @return this property for chaining
	 */
	public LanguageProperty withComment(final String newComment) {
		setComment(newComment);
		return this;
	}

	/**
	 * Position of the property within its properties set, which defines the order on saving.
	 *
	 * @return the original index
	 */
	public int getOriginalIndex() {
		return originalIndex;
	}

	/**
	 * Sets the position of the property within its properties set.
	 *
	 * @param originalIndex
	 *            the original index
	 */
	public void setOriginalIndex(final int originalIndex) {
		this.originalIndex = originalIndex;
	}

	/**
	 * Sets the position of the property within its properties set (fluent variant of {@link #setOriginalIndex(int)}).
	 *
	 * @param newOriginalIndex
	 *            the original index
	 * @return this property for chaining
	 */
	public LanguageProperty withOriginalIndex(final int newOriginalIndex) {
		setOriginalIndex(newOriginalIndex);
		return this;
	}

	/**
	 * Number of empty lines written before this property (and its comment).
	 *
	 * @return number of empty lines
	 */
	public int getEmptyLinesBefore() {
		return emptyLinesBefore;
	}

	/**
	 * Sets the number of empty lines written before this property (and its comment) to keep blocks of properties visually grouped.
	 *
	 * @param emptyLinesBefore
	 *            number of empty lines, negative values are treated as 0
	 */
	public void setEmptyLinesBefore(final int emptyLinesBefore) {
		this.emptyLinesBefore = Math.max(0, emptyLinesBefore);
	}

	/**
	 * Sets the number of empty lines written before this property (and its comment) to keep blocks of properties visually grouped (fluent variant of {@link #setEmptyLinesBefore(int)}).
	 *
	 * @param newEmptyLinesBefore
	 *            number of empty lines, negative values are treated as 0
	 * @return this property for chaining
	 */
	public LanguageProperty withEmptyLinesBefore(final int newEmptyLinesBefore) {
		setEmptyLinesBefore(newEmptyLinesBefore);
		return this;
	}

	/**
	 * Whether the property has no key or no non-empty value in any language.
	 *
	 * @return true if the property is empty
	 */
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

	/**
	 * Removes a language completely from this property (in contrast to a null value, which keeps the language registered).
	 *
	 * @param languageSign
	 *            language to remove
	 * @return this property for chaining
	 */
	public LanguageProperty removeLanguageValue(final String languageSign) {
		languageValues.remove(languageSign);
		return this;
	}

	/**
	 * Language signs registered for this property, including those with a null (missing) value.
	 * This is a live view, it must not be changed while values are added or removed.
	 *
	 * @return the registered language signs
	 */
	public Set<String> getAvailableLanguageSigns() {
		return languageValues.keySet();
	}

	/**
	 * Value of a language.
	 *
	 * @param languageSign
	 *            language of the value
	 * @return the value, "" for an explicitly empty value, null if the key is missing in this language
	 */
	public String getLanguageValue(final String languageSign) {
		return languageValues.get(languageSign);
	}

	/**
	 * Whether the language is registered for this property (its value may still be null).
	 *
	 * @param languageSign
	 *            language to check
	 * @return true if the language is registered
	 */
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
	 *
	 * @param languageSign
	 *            language of the value
	 * @param value
	 *            the value, "" or null
	 */
	public void setLanguageValue(final String languageSign, final String value) {
		languageValues.put(languageSign, value);
	}

	/**
	 * Sets the value of a language (fluent variant of {@link #setLanguageValue(String, String)}).
	 * The value is stored as it is:
	 * <ul>
	 * <li>null: the key is missing in this language (it is omitted in that language file, so ResourceBundle falls back to the default value).
	 * The language sign stays registered, so the language is still shown as a column.</li>
	 * <li>"" (empty string): the key exists with an explicitly empty value (written as "key=").</li>
	 * </ul>
	 * For values entered by the user or read from import files use {@link #toStorageValue(String, String)} first.
	 *
	 * @param newLanguageSign
	 *            language of the value
	 * @param newValue
	 *            the value, "" or null
	 * @return this property for chaining
	 */
	public LanguageProperty withLanguageValue(final String newLanguageSign, final String newValue) {
		setLanguageValue(newLanguageSign, newValue);
		return this;
	}

	/**
	 * Maps an input value (UI field, import cell), which can not distinguish between "empty" and "missing", to the stored value:
	 * <ul>
	 * <li>non-empty values are kept as they are</li>
	 * <li>empty values become "" for the default language, so the key is not lost on saving</li>
	 * <li>empty values become null for all other languages, so the key is omitted in that language file and ResourceBundle falls back to the default value</li>
	 * </ul>
	 *
	 * @param languageSign
	 *            language of the value
	 * @param inputValue
	 *            value as entered or read, may be null
	 * @return the value to store
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
	 *
	 * @param languageProperties
	 *            existing properties, may be null
	 * @return highest original index plus 1
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
	 *
	 * @param allProperties
	 *            all properties, still including the properties to remove
	 * @param propertiesToRemove
	 *            properties, which are going to be removed
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

	/**
	 * Sorts map entries (key to property) by whether the property has a value in
	 * a language, entries with value first, then by key case-insensitively.
	 */
	public static class EntryValueExistsComparator implements Comparator<Map.Entry<String, LanguageProperty>> {
		private final String languageSign;
		private final boolean ascending;

		/**
		 * Creates the comparator.
		 *
		 * @param languageSign
		 *            language whose values are checked
		 * @param ascending
		 *            false to reverse the order
		 */
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
