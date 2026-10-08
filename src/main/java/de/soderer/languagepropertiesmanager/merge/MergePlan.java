package de.soderer.languagepropertiesmanager.merge;

import java.util.ArrayList;
import java.util.List;

/**
 * Analysis of a merge import before anything is changed. The counts are shown
 * to the user to decide how conflicts are resolved.
 */
public class MergePlan {
	final List<MergePlanEntry> entries = new ArrayList<>();
	final List<String> skippedEntries = new ArrayList<>();
	int newPropertyCount = 0;
	int matchedPropertyCount = 0;
	int propertiesWithDifferencesCount = 0;
	int differingValueCount = 0;
	int fillableValueCount = 0;

	MergePlan() {
		// Only created by LanguagePropertiesMerger
	}

	/**
	 * Whether the merge would change nothing: no new properties, no fillable and no differing values.
	 *
	 * @return true if there is nothing to import
	 */
	public boolean hasNothingToDo() {
		return newPropertyCount == 0 && differingValueCount == 0 && fillableValueCount == 0;
	}

	/**
	 * Imported entries, which cannot be merged (e.g. empty or ambiguous keys), with the reason.
	 *
	 * @return display texts of the skipped entries
	 */
	public List<String> getSkippedEntries() {
		return skippedEntries;
	}

	/**
	 * Number of imported properties, which do not exist yet and would be added.
	 *
	 * @return number of new properties
	 */
	public int getNewPropertyCount() {
		return newPropertyCount;
	}

	/**
	 * Number of imported properties, which match an existing property.
	 *
	 * @return number of matched properties
	 */
	public int getMatchedPropertyCount() {
		return matchedPropertyCount;
	}

	/**
	 * Number of matched properties with at least one value differing from the existing value.
	 *
	 * @return number of properties with conflicts
	 */
	public int getPropertiesWithDifferencesCount() {
		return propertiesWithDifferencesCount;
	}

	/**
	 * Number of non-empty imported values, which differ from a non-empty existing value (conflicts).
	 *
	 * @return number of differing values
	 */
	public int getDifferingValueCount() {
		return differingValueCount;
	}

	/**
	 * Number of non-empty imported values, whose existing value is empty.
	 *
	 * @return number of fillable values
	 */
	public int getFillableValueCount() {
		return fillableValueCount;
	}
}
