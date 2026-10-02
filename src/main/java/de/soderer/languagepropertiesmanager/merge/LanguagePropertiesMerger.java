package de.soderer.languagepropertiesmanager.merge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.soderer.languagepropertiesmanager.check.ErrorReport;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;

/**
 * Merge import of language properties into the currently loaded data:
 * planning (createMergePlan), applying (applyMergePlan) and the final report.
 * No GUI dependencies, the merge mode is chosen by the caller.
 */
public final class LanguagePropertiesMerger {
	private LanguagePropertiesMerger() {
		// Utility class, no instances
	}

	/**
	 * Determines for every imported property, whether it matches an existing
	 * property, and counts the conflicts. Nothing is changed here.
	 */
	public static MergePlan createMergePlan(final List<LanguageProperty> existingProperties, final List<LanguageProperty> importedProperties) {
		final MergePlan mergePlan = new MergePlan();

		final Map<String, List<LanguageProperty>> existingPropertiesByKey = new HashMap<>();
		final Set<String> existingPaths = new HashSet<>();
		for (final LanguageProperty existingProperty : existingProperties) {
			existingPropertiesByKey.computeIfAbsent(MergeUtilities.getEmptyForNull(existingProperty.getKey()), k -> new ArrayList<>()).add(existingProperty);
			existingPaths.add(MergeUtilities.getEmptyForNull(existingProperty.getPath()));
		}

		final Set<String> newPathsAndKeys = new HashSet<>();
		for (final LanguageProperty importedProperty : importedProperties) {
			final String key = importedProperty.getKey();
			if (Utilities.isBlank(key)) {
				mergePlan.skippedEntries.add(LangResources.get("mergeImport_skippedEmptyKey", MergeUtilities.getEmptyForNull(importedProperty.getPath())));
				continue;
			}

			final List<LanguageProperty> candidates = findMergeCandidates(importedProperty, existingPropertiesByKey.get(key));
			if (candidates.size() > 1) {
				mergePlan.skippedEntries.add(MergeUtilities.getPropertyDisplayName(importedProperty.getPath(), key) + ": " + LangResources.get("mergeImport_skippedAmbiguous", candidates.size()));
			} else if (candidates.size() == 1) {
				final LanguageProperty targetProperty = candidates.get(0);
				mergePlan.entries.add(new MergePlanEntry(importedProperty, targetProperty, null, false));
				mergePlan.matchedPropertyCount++;

				int differingValuesOfProperty = 0;
				for (final String languageSign : importedProperty.getAvailableLanguageSigns()) {
					final String importedValue = importedProperty.getLanguageValue(languageSign);
					if (Utilities.isNotEmpty(importedValue)) {
						final String existingValue = targetProperty.getLanguageValue(languageSign);
						if (Utilities.isEmpty(existingValue)) {
							mergePlan.fillableValueCount++;
						} else if (!existingValue.equals(importedValue)) {
							differingValuesOfProperty++;
						}
					}
				}
				// Comments are not compared, only keys and values matter
				if (differingValuesOfProperty > 0) {
					mergePlan.propertiesWithDifferencesCount++;
					mergePlan.differingValueCount += differingValuesOfProperty;
				}
			} else {
				final String pathForNewProperty = determinePathForNewProperty(MergeUtilities.getEmptyForNull(importedProperty.getPath()), existingPaths);
				final boolean foreignPath = !existingPaths.isEmpty() && !existingPaths.contains(pathForNewProperty);
				mergePlan.entries.add(new MergePlanEntry(importedProperty, null, pathForNewProperty, foreignPath));
				// Duplicates within the imported data are merged into the first occurrence later
				if (newPathsAndKeys.add(pathForNewProperty + "\u0000" + key)) {
					mergePlan.newPropertyCount++;
				}
			}
		}

		return mergePlan;
	}

	/**
	 * Existing properties, an imported property with the same key is merged into.
	 * Matching is done by key only, because the path of an import source (e.g. a
	 * copy or export of the set in another directory) usually differs.
	 * Only if the key exists in more than one loaded properties set, an existing
	 * property with exactly the same path is used to resolve the ambiguity.
	 * More than one returned candidate means the match is ambiguous.
	 */
	private static List<LanguageProperty> findMergeCandidates(final LanguageProperty importedProperty, final List<LanguageProperty> existingPropertiesWithSameKey) {
		if (existingPropertiesWithSameKey == null || existingPropertiesWithSameKey.isEmpty()) {
			return new ArrayList<>();
		}

		final List<LanguageProperty> candidates = reduceToFirstOfSinglePath(existingPropertiesWithSameKey);
		if (candidates.size() <= 1) {
			return candidates;
		}

		final String importedPath = MergeUtilities.getEmptyForNull(importedProperty.getPath());
		final List<LanguageProperty> samePathCandidates = existingPropertiesWithSameKey.stream()
				.filter(existingProperty -> importedPath.equals(MergeUtilities.getEmptyForNull(existingProperty.getPath())))
				.collect(Collectors.toList());
		if (!samePathCandidates.isEmpty()) {
			return reduceToFirstOfSinglePath(samePathCandidates);
		} else {
			return candidates;
		}
	}

	/**
	 * Duplicates of a key within the same path are no ambiguity: like in
	 * removeDuplicates(), the one with the lowest original index is used.
	 */
	private static List<LanguageProperty> reduceToFirstOfSinglePath(final List<LanguageProperty> candidates) {
		final Set<String> candidatePaths = candidates.stream().map(candidate -> MergeUtilities.getEmptyForNull(candidate.getPath())).collect(Collectors.toSet());
		if (candidatePaths.size() == 1) {
			final List<LanguageProperty> result = new ArrayList<>();
			result.add(candidates.stream().min(Comparator.comparing(LanguageProperty::getOriginalIndex)).get());
			return result;
		} else {
			return candidates;
		}
	}

	/**
	 * A new property is put into the matching loaded properties set, so saving
	 * does not write into the files of the import source. If no unique matching
	 * set exists, the property keeps the path of the import source.
	 */
	private static String determinePathForNewProperty(final String importedPath, final Set<String> existingPaths) {
		if (existingPaths.contains(importedPath)) {
			return importedPath;
		} else if (existingPaths.size() == 1) {
			// Only one properties set is loaded, so the path of the import source does not matter
			return existingPaths.iterator().next();
		}

		final String importedSetName = getLanguagePropertiesSetNameOfPath(importedPath);
		final List<String> matchingPaths = existingPaths.stream()
				.filter(existingPath -> importedSetName.isEmpty() || importedSetName.equals(getLanguagePropertiesSetNameOfPath(existingPath)))
				.collect(Collectors.toList());
		if (matchingPaths.size() == 1) {
			return matchingPaths.get(0);
		} else {
			return importedPath;
		}
	}

	/**
	 * Name of a properties set, which is the last part of its path (the path has no language sign and no file extension)
	 */
	private static String getLanguagePropertiesSetNameOfPath(final String path) {
		if (Utilities.isBlank(path)) {
			return "";
		}
		final String normalizedPath = path.replace('\\', '/');
		return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
	}

	/**
	 * Applies a merge plan to the target properties (the currently loaded data).
	 * New properties are appended to the target properties.
	 */
	public static MergeResult applyMergePlan(final List<LanguageProperty> targetProperties, final MergePlan mergePlan, final MergeMode mergeMode) {
		final MergeResult mergeResult = new MergeResult();
		final Map<String, LanguageProperty> addedPropertiesByPathAndKey = new HashMap<>();

		for (final MergePlanEntry entry : mergePlan.entries) {
			final LanguageProperty importedProperty = entry.importedProperty;
			if (entry.targetProperty != null) {
				mergeValues(importedProperty, entry.targetProperty, mergeMode == MergeMode.OVERWRITE, mergeResult);
			} else if (mergeMode == MergeMode.FILL_EMPTY_ONLY) {
				mergeResult.notAddedProperties.add(MergeUtilities.getPropertyDisplayName(importedProperty.getPath(), importedProperty.getKey()));
			} else {
				final String pathAndKey = entry.pathForNewProperty + "\u0000" + importedProperty.getKey();
				final LanguageProperty alreadyAddedProperty = addedPropertiesByPathAndKey.get(pathAndKey);
				if (alreadyAddedProperty != null) {
					// Duplicate within the imported data: only take over values the first occurrence is still missing
					mergeValues(importedProperty, alreadyAddedProperty, false, mergeResult);
					// The newly added property is completely taken from the import, so it also takes over a comment it is still missing
					if (Utilities.isEmpty(alreadyAddedProperty.getComment()) && Utilities.isNotEmpty(importedProperty.getComment())) {
						alreadyAddedProperty.setComment(importedProperty.getComment());
					}
				} else {
					importedProperty.setPath(entry.pathForNewProperty);
					importedProperty.setOriginalIndex(targetProperties.size() + 1);
					targetProperties.add(importedProperty);
					addedPropertiesByPathAndKey.put(pathAndKey, importedProperty);
					mergeResult.markChanged(importedProperty);

					final String displayName = MergeUtilities.getPropertyDisplayName(importedProperty.getPath(), importedProperty.getKey());
					mergeResult.addedProperties.add(displayName);
					if (entry.foreignPath) {
						mergeResult.addedPropertiesWithForeignPath.add(displayName);
					}
				}
			}
		}

		return mergeResult;
	}

	/**
	 * Takes over the non-empty values of the source property into the target
	 * property. Empty target values are always filled, differing non-empty target
	 * values are only overwritten if requested. Comments are left untouched.
	 */
	private static void mergeValues(final LanguageProperty sourceProperty, final LanguageProperty targetProperty, final boolean overwriteDifferingValues, final MergeResult mergeResult) {
		final String displayName = MergeUtilities.getPropertyDisplayName(targetProperty.getPath(), targetProperty.getKey());
		boolean changed = false;

		for (final String languageSign : sourceProperty.getAvailableLanguageSigns()) {
			final String importedValue = sourceProperty.getLanguageValue(languageSign);
			if (Utilities.isNotEmpty(importedValue)) {
				final String existingValue = targetProperty.getLanguageValue(languageSign);
				if (Utilities.isEmpty(existingValue)) {
					targetProperty.setLanguageValue(languageSign, importedValue);
					mergeResult.filledValues.add(displayName + " [" + languageSign + "]");
					changed = true;
				} else if (!existingValue.equals(importedValue)) {
					if (overwriteDifferingValues) {
						targetProperty.setLanguageValue(languageSign, importedValue);
						mergeResult.overwrittenValues.add(displayName + " [" + languageSign + "]");
						changed = true;
					} else {
						mergeResult.keptDifferingValues.add(displayName + " [" + languageSign + "]");
					}
				}
			}
		}

		// Comments are not compared or taken over, the comment of the target property stays as it is

		if (changed) {
			mergeResult.markChanged(targetProperty);
		}
	}

	public static String createMergeReport(final String sourceDescription, final MergeMode mergeMode, final MergePlan mergePlan, final MergeResult mergeResult, final ErrorReport errorReport) {
		final StringBuilder reportText = new StringBuilder();
		reportText.append(LangResources.get("mergeImport_resultSummary",
				sourceDescription,
				LangResources.get("mergeImport_mode_" + getMergeModeResourceSuffix(mergeMode)),
				mergeResult.addedProperties.size(),
				mergeResult.filledValues.size(),
				mergeResult.overwrittenValues.size(),
				mergeResult.keptDifferingValues.size(),
				mergeResult.notAddedProperties.size(),
				mergePlan.skippedEntries.size(),
				mergeResult.newLanguageSigns.isEmpty() ? "-" : Utilities.join(mergeResult.newLanguageSigns, ", ")));

		if (!mergeResult.addedPropertiesWithForeignPath.isEmpty()) {
			reportText.append("\n\n").append(LangResources.get("mergeImport_warningForeignPath", mergeResult.addedPropertiesWithForeignPath.size()));
		}

		MergeUtilities.appendReportSection(reportText, "mergeImport_section_added", mergeResult.addedProperties);
		MergeUtilities.appendReportSection(reportText, "mergeImport_section_filled", mergeResult.filledValues);
		MergeUtilities.appendReportSection(reportText, "mergeImport_section_overwritten", mergeResult.overwrittenValues);
		MergeUtilities.appendReportSection(reportText, "mergeImport_section_keptDiffering", mergeResult.keptDifferingValues);
		MergeUtilities.appendReportSection(reportText, "mergeImport_section_notAdded", mergeResult.notAddedProperties);
		MergeUtilities.appendReportSection(reportText, "mergeImport_section_skipped", mergePlan.skippedEntries);
		MergeUtilities.appendReportSection(reportText, "mergeImport_section_foreignPath", mergeResult.addedPropertiesWithForeignPath);

		reportText.append("\n\n").append(LangResources.get("mergeImport_section_errorCheck")).append(":\n");
		if (errorReport.getIssueCount() == 0) {
			reportText.append(LangResources.get("noErrorsFound"));
		} else {
			reportText.append(LangResources.get("checkErrorsFound", errorReport.getIssueCount())).append("\n\n").append(errorReport.getReportText());
		}

		return reportText.toString();
	}

	private static String getMergeModeResourceSuffix(final MergeMode mergeMode) {
		switch (mergeMode) {
			case OVERWRITE:
				return "overwrite";
			case FILL_EMPTY_ONLY:
				return "fillEmptyOnly";
			case ADD_NEW:
			default:
				return "addNew";
		}
	}
}
