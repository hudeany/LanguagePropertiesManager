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

	public boolean hasNothingToDo() {
		return newPropertyCount == 0 && differingValueCount == 0 && fillableValueCount == 0;
	}

	public List<String> getSkippedEntries() {
		return skippedEntries;
	}

	public int getNewPropertyCount() {
		return newPropertyCount;
	}

	public int getMatchedPropertyCount() {
		return matchedPropertyCount;
	}

	public int getPropertiesWithDifferencesCount() {
		return propertiesWithDifferencesCount;
	}

	public int getDifferingValueCount() {
		return differingValueCount;
	}

	public int getFillableValueCount() {
		return fillableValueCount;
	}
}
