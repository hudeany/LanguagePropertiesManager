package de.soderer.languagepropertiesmanager.merge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;

/**
 * Reduction of language properties by a base set: values identical to the base
 * set are removed, so only the deviations from the base set remain.
 * No GUI dependencies, the caller decides whether empty properties are removed.
 */
public final class BaseSetReducer {
	private BaseSetReducer() {
		// Utility class, no instances
	}

	/**
	 * Determines for every property to check the identical values in the base set.
	 * The properties are matched by key only, the path of the base set (which
	 * usually differs) and the comments are not compared. If the base set
	 * contains the key more than once, a value counts as identical if any of
	 * these base properties has the same value for that language.
	 * Nothing is changed here.
	 */
	public static ReducePlan createReducePlan(final List<LanguageProperty> propertiesToCheck, final List<LanguageProperty> baseProperties) {
		final ReducePlan reducePlan = new ReducePlan();

		final Map<String, List<LanguageProperty>> basePropertiesByKey = new HashMap<>();
		for (final LanguageProperty baseProperty : baseProperties) {
			basePropertiesByKey.computeIfAbsent(MergeUtilities.getEmptyForNull(baseProperty.getKey()), k -> new ArrayList<>()).add(baseProperty);
		}

		for (final LanguageProperty languageProperty : propertiesToCheck) {
			final String key = languageProperty.getKey();
			if (Utilities.isBlank(key)) {
				reducePlan.skippedEntries.add(LangResources.get("mergeImport_skippedEmptyKey", MergeUtilities.getEmptyForNull(languageProperty.getPath())));
				continue;
			}

			final List<LanguageProperty> basePropertiesWithSameKey = basePropertiesByKey.get(key);
			if (basePropertiesWithSameKey == null || basePropertiesWithSameKey.isEmpty()) {
				reducePlan.propertiesWithoutBaseCount++;
				continue;
			}

			final List<String> identicalLanguageSigns = new ArrayList<>();
			boolean hasRemainingValue = false;
			for (final String languageSign : new ArrayList<>(languageProperty.getAvailableLanguageSigns())) {
				final String value = languageProperty.getLanguageValue(languageSign);
				if (Utilities.isNotEmpty(value)) {
					boolean identicalValueFound = false;
					boolean baseValueFound = false;
					for (final LanguageProperty baseProperty : basePropertiesWithSameKey) {
						final String baseValue = baseProperty.getLanguageValue(languageSign);
						if (value.equals(baseValue)) {
							identicalValueFound = true;
							break;
						} else if (Utilities.isNotEmpty(baseValue)) {
							baseValueFound = true;
						}
					}

					if (identicalValueFound) {
						identicalLanguageSigns.add(languageSign);
					} else {
						hasRemainingValue = true;
						if (baseValueFound) {
							reducePlan.differingValues.add(MergeUtilities.getPropertyDisplayName(languageProperty.getPath(), key) + " [" + languageSign + "]");
						}
					}
				}
			}

			if (!identicalLanguageSigns.isEmpty()) {
				reducePlan.entries.add(new ReducePlanEntry(languageProperty, identicalLanguageSigns, !hasRemainingValue));
				reducePlan.identicalValueCount += identicalLanguageSigns.size();
				if (!hasRemainingValue) {
					reducePlan.emptyPropertyCount++;
				}
			}
		}

		return reducePlan;
	}

	/**
	 * Clears the values identical to the base set. Properties without any
	 * remaining value are removed from the given list, if requested.
	 */
	public static ReduceResult applyReducePlan(final List<LanguageProperty> languageProperties, final ReducePlan reducePlan, final boolean removeEmptyProperties) {
		final ReduceResult reduceResult = new ReduceResult();
		final Set<LanguageProperty> propertiesToRemove = Collections.newSetFromMap(new IdentityHashMap<>());
		for (final ReducePlanEntry entry : reducePlan.entries) {
			final LanguageProperty languageProperty = entry.languageProperty;
			final String displayName = MergeUtilities.getPropertyDisplayName(languageProperty.getPath(), languageProperty.getKey());
			for (final String languageSign : entry.identicalLanguageSigns) {
				languageProperty.setLanguageValue(languageSign, null);
				reduceResult.clearedValues.add(displayName + " [" + languageSign + "]");
			}
			if (removeEmptyProperties && entry.becomesEmpty) {
				propertiesToRemove.add(languageProperty);
				reduceResult.removedProperties.add(displayName);
			} else {
				reduceResult.changedProperties.add(languageProperty);
			}
		}
		if (!propertiesToRemove.isEmpty()) {
			// Remove by identity, so also exactly the reduced one of several duplicates is removed
			languageProperties.removeIf(propertiesToRemove::contains);
		}
		return reduceResult;
	}

	public static String createReduceReport(final String sourceDescription, final int checkedPropertyCount, final ReducePlan reducePlan, final ReduceResult reduceResult) {
		final StringBuilder reportText = new StringBuilder();
		reportText.append(LangResources.get("reduceByBaseSet_resultSummary",
				sourceDescription,
				checkedPropertyCount,
				reduceResult.clearedValues.size(),
				reduceResult.removedProperties.size(),
				reducePlan.differingValues.size(),
				reducePlan.propertiesWithoutBaseCount,
				reducePlan.skippedEntries.size()));
		if (!reduceResult.removedProperties.isEmpty()) {
			// Removed keys would survive in the files, if existing properties are kept when saving
			reportText.append("\n\n").append(LangResources.get("reduceByBaseSet_saveHint"));
		}
		MergeUtilities.appendReportSection(reportText, "reduceByBaseSet_section_removed", reduceResult.removedProperties);
		MergeUtilities.appendReportSection(reportText, "reduceByBaseSet_section_cleared", reduceResult.clearedValues);
		MergeUtilities.appendReportSection(reportText, "reduceByBaseSet_section_differing", reducePlan.differingValues);
		MergeUtilities.appendReportSection(reportText, "mergeImport_section_skipped", reducePlan.skippedEntries);
		return reportText.toString();
	}
}
