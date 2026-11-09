package de.soderer.languagepropertiesmanager.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import de.soderer.utilities.Utilities;

/**
 * Compares existing language properties (e.g. loaded from the properties files) with changed language
 * properties (e.g. imported from an Excel or CSV file returned by a translator).
 * <p>
 * Properties are matched by path and key. If one side has no paths at all (e.g. a file without path
 * column or properties whose paths were deleted), they are matched by key only.
 * <p>
 * Empty and missing values are treated as equal, because Excel and CSV cannot distinguish them.
 */
public class LanguagePropertiesComparator {
	private LanguagePropertiesComparator() {
		// Utility class
	}

	/**
	 * Compares two lists of language properties.
	 *
	 * @param existingProperties existing properties, e.g. loaded from the properties files
	 * @param changedProperties changed properties, e.g. imported from an Excel file
	 * @param compareComments true to also report changed comments
	 * @return all differences, ordered like the existing properties, added keys at the end
	 * @throws Exception if the properties cannot be matched unambiguously (same key in several sets without paths)
	 */
	public static List<LanguagePropertyDifference> compare(final List<LanguageProperty> existingProperties, final List<LanguageProperty> changedProperties, final boolean compareComments) throws Exception {
		final boolean matchByPath = hasAnyPath(existingProperties) && hasAnyPath(changedProperties);

		final Map<String, LanguageProperty> existingByMatchKey = toMatchMap(existingProperties, matchByPath, "existing");
		final Map<String, LanguageProperty> changedByMatchKey = toMatchMap(changedProperties, matchByPath, "changed");

		final Set<String> allLanguageSigns = new LinkedHashSet<>();
		allLanguageSigns.addAll(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(existingProperties));
		allLanguageSigns.addAll(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(changedProperties));
		final List<String> languageSignsInOrder = Utilities.sortButPutItemsFirst(new ArrayList<>(allLanguageSigns), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);

		final List<LanguagePropertyDifference> differences = new ArrayList<>();
		for (final Entry<String, LanguageProperty> existingEntry : existingByMatchKey.entrySet()) {
			final LanguageProperty existingProperty = existingEntry.getValue();
			final LanguageProperty changedProperty = changedByMatchKey.get(existingEntry.getKey());
			if (changedProperty == null) {
				differences.add(new LanguagePropertyDifference(LanguagePropertyDifference.Type.KEY_REMOVED, existingProperty, null, null, null, null));
			} else {
				compareProperty(existingProperty, changedProperty, languageSignsInOrder, compareComments, differences);
			}
		}

		for (final Entry<String, LanguageProperty> changedEntry : changedByMatchKey.entrySet()) {
			if (!existingByMatchKey.containsKey(changedEntry.getKey())) {
				final LanguageProperty changedProperty = changedEntry.getValue();
				differences.add(new LanguagePropertyDifference(LanguagePropertyDifference.Type.KEY_ADDED, null, changedProperty, null, null, null));
				// Also list the values of the added key, so they can be reviewed
				for (final String languageSign : languageSignsInOrder) {
					final String newValue = changedProperty.getLanguageValue(languageSign);
					if (!isEmpty(newValue)) {
						differences.add(new LanguagePropertyDifference(LanguagePropertyDifference.Type.VALUE_ADDED, null, changedProperty, languageSign, null, newValue));
					}
				}
			}
		}

		return differences;
	}

	/**
	 * Creates a short summary of the differences: number of added and removed keys and number of
	 * added, changed and removed values per language.
	 *
	 * @param differences differences created by {@link #compare(List, List, boolean)}
	 * @return summary text with one line per topic, "No differences" if the list is empty
	 */
	public static String createSummary(final List<LanguagePropertyDifference> differences) {
		if (differences.isEmpty()) {
			return "No differences";
		}

		int keysAdded = 0;
		int keysRemoved = 0;
		int commentsChanged = 0;
		// Language sign -> counts of VALUE_ADDED, VALUE_CHANGED, VALUE_REMOVED
		final Map<String, int[]> valueCountsByLanguage = new TreeMap<>();
		for (final LanguagePropertyDifference difference : differences) {
			switch (difference.getType()) {
				case KEY_ADDED:
					keysAdded++;
					break;
				case KEY_REMOVED:
					keysRemoved++;
					break;
				case COMMENT_CHANGED:
					commentsChanged++;
					break;
				case VALUE_ADDED:
					valueCountsByLanguage.computeIfAbsent(difference.getLanguageSign(), k -> new int[3])[0]++;
					break;
				case VALUE_CHANGED:
					valueCountsByLanguage.computeIfAbsent(difference.getLanguageSign(), k -> new int[3])[1]++;
					break;
				case VALUE_REMOVED:
					valueCountsByLanguage.computeIfAbsent(difference.getLanguageSign(), k -> new int[3])[2]++;
					break;
				default:
					break;
			}
		}

		final StringBuilder summary = new StringBuilder();
		summary.append("Keys added: ").append(keysAdded).append(", keys removed: ").append(keysRemoved);
		if (commentsChanged > 0) {
			summary.append(", comments changed: ").append(commentsChanged);
		}
		for (final Entry<String, int[]> entry : valueCountsByLanguage.entrySet()) {
			summary.append("\n").append(entry.getKey()).append(": ")
					.append(entry.getValue()[0]).append(" values added, ")
					.append(entry.getValue()[1]).append(" changed, ")
					.append(entry.getValue()[2]).append(" removed");
		}
		return summary.toString();
	}

	/**
	 * Creates a report of the differences: the summary followed by one line per difference.
	 *
	 * @param differences differences created by {@link #compare(List, List, boolean)}
	 * @return report text
	 */
	public static String createReport(final List<LanguagePropertyDifference> differences) {
		final StringBuilder report = new StringBuilder(createSummary(differences));
		if (!differences.isEmpty()) {
			report.append("\n");
			for (final LanguagePropertyDifference difference : differences) {
				report.append("\n").append(difference);
			}
		}
		return report.toString();
	}

	/**
	 * Values, which were empty or missing in an existing property and are filled in the changed properties
	 * (typically new translations). Values of added keys are not included, because there is no existing property.
	 *
	 * @param differences differences created by {@link #compare(List, List, boolean)}
	 * @return the differences of type {@link LanguagePropertyDifference.Type#VALUE_ADDED} with an existing property
	 */
	public static List<LanguagePropertyDifference> getAddedValuesOfExistingProperties(final List<LanguagePropertyDifference> differences) {
		final List<LanguagePropertyDifference> result = new ArrayList<>();
		for (final LanguagePropertyDifference difference : differences) {
			if (difference.getType() == LanguagePropertyDifference.Type.VALUE_ADDED && difference.getExistingProperty() != null) {
				result.add(difference);
			}
		}
		return result;
	}

	/**
	 * Takes over added values into the existing properties. Only values, which are still empty or missing in
	 * the existing property, are set, so a value changed in the meantime is never overwritten.
	 *
	 * @param addedValues differences created by {@link #getAddedValuesOfExistingProperties(List)}
	 * @return number of values taken over
	 */
	public static int applyAddedValues(final List<LanguagePropertyDifference> addedValues) {
		int appliedCount = 0;
		for (final LanguagePropertyDifference addedValue : addedValues) {
			if (addedValue.getType() == LanguagePropertyDifference.Type.VALUE_ADDED && addedValue.getExistingProperty() != null && !isEmpty(addedValue.getNewValue())) {
				final LanguageProperty existingProperty = addedValue.getExistingProperty();
				if (isEmpty(existingProperty.getLanguageValue(addedValue.getLanguageSign()))) {
					existingProperty.setLanguageValue(addedValue.getLanguageSign(), addedValue.getNewValue());
					appliedCount++;
				}
			}
		}
		return appliedCount;
	}

	private static void compareProperty(final LanguageProperty existingProperty, final LanguageProperty changedProperty, final List<String> languageSigns, final boolean compareComments, final List<LanguagePropertyDifference> differences) {
		if (compareComments && !normalize(existingProperty.getComment()).equals(normalize(changedProperty.getComment()))) {
			differences.add(new LanguagePropertyDifference(LanguagePropertyDifference.Type.COMMENT_CHANGED, existingProperty, changedProperty, null, existingProperty.getComment(), changedProperty.getComment()));
		}

		for (final String languageSign : languageSigns) {
			final String oldValue = existingProperty.getLanguageValue(languageSign);
			final String newValue = changedProperty.getLanguageValue(languageSign);
			if (isEmpty(oldValue) && isEmpty(newValue)) {
				continue;
			} else if (isEmpty(oldValue)) {
				differences.add(new LanguagePropertyDifference(LanguagePropertyDifference.Type.VALUE_ADDED, existingProperty, changedProperty, languageSign, null, newValue));
			} else if (isEmpty(newValue)) {
				differences.add(new LanguagePropertyDifference(LanguagePropertyDifference.Type.VALUE_REMOVED, existingProperty, changedProperty, languageSign, oldValue, null));
			} else if (!normalizeLineBreaks(oldValue).equals(normalizeLineBreaks(newValue))) {
				differences.add(new LanguagePropertyDifference(LanguagePropertyDifference.Type.VALUE_CHANGED, existingProperty, changedProperty, languageSign, oldValue, newValue));
			}
		}
	}

	private static Map<String, LanguageProperty> toMatchMap(final List<LanguageProperty> properties, final boolean matchByPath, final String description) throws Exception {
		final Map<String, LanguageProperty> result = new LinkedHashMap<>();
		for (final LanguageProperty property : properties) {
			final String matchKey = matchByPath ? normalizePath(property.getPath()) + "\u0000" + property.getKey() : property.getKey();
			if (result.put(matchKey, property) != null) {
				if (matchByPath) {
					throw new Exception("Duplicate key '" + property.getKey() + "' in " + description + " properties of path '" + property.getPath() + "'");
				} else {
					throw new Exception("Key '" + property.getKey() + "' exists several times in the " + description + " properties."
							+ " Without a path column the properties sets cannot be distinguished, export the Excel file with path column.");
				}
			}
		}
		return result;
	}

	private static boolean hasAnyPath(final List<LanguageProperty> properties) {
		for (final LanguageProperty property : properties) {
			if (property.getPath() != null && !property.getPath().trim().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Paths are compared independent of the file separator, so a file edited on another operating system still matches.
	 */
	private static String normalizePath(final String path) {
		return path == null ? "" : path.trim().replace('\\', '/');
	}

	/**
	 * Excel stores line breaks as "\n", the properties files may contain "\r\n".
	 */
	private static String normalizeLineBreaks(final String value) {
		return value.replace("\r\n", "\n").replace('\r', '\n');
	}

	private static String normalize(final String value) {
		return value == null ? "" : normalizeLineBreaks(value);
	}

	private static boolean isEmpty(final String value) {
		return value == null || value.isEmpty();
	}
}
