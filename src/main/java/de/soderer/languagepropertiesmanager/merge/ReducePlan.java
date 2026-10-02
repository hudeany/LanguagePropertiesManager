package de.soderer.languagepropertiesmanager.merge;

import java.util.ArrayList;
import java.util.List;

/**
 * Analysis of a reduction by a base set before anything is changed
 */
public class ReducePlan {
	final List<ReducePlanEntry> entries = new ArrayList<>();
	final List<String> skippedEntries = new ArrayList<>();
	/** Values that differ from the base set, they stay as they are */
	final List<String> differingValues = new ArrayList<>();
	int identicalValueCount = 0;
	int emptyPropertyCount = 0;
	int propertiesWithoutBaseCount = 0;

	ReducePlan() {
		// Only created by BaseSetReducer
	}

	public boolean hasNothingToReduce() {
		return entries.isEmpty();
	}

	/** Number of properties with at least one value identical to the base set */
	public int getReduciblePropertyCount() {
		return entries.size();
	}

	public List<String> getSkippedEntries() {
		return skippedEntries;
	}

	public int getIdenticalValueCount() {
		return identicalValueCount;
	}

	public int getEmptyPropertyCount() {
		return emptyPropertyCount;
	}

	public int getPropertiesWithoutBaseCount() {
		return propertiesWithoutBaseCount;
	}
}
