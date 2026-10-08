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

	/**
	 * Whether no loaded value is identical to the base set.
	 *
	 * @return true if there is nothing to reduce
	 */
	public boolean hasNothingToReduce() {
		return entries.isEmpty();
	}

	/**
	 * Number of properties with at least one value identical to the base set.
	 *
	 * @return number of reducible properties
	 */
	public int getReduciblePropertyCount() {
		return entries.size();
	}

	/**
	 * Checked entries, which cannot be compared (e.g. empty keys), with the reason.
	 *
	 * @return display texts of the skipped entries
	 */
	public List<String> getSkippedEntries() {
		return skippedEntries;
	}

	/**
	 * Number of values identical to the base set, which would be cleared.
	 *
	 * @return number of identical values
	 */
	public int getIdenticalValueCount() {
		return identicalValueCount;
	}

	/**
	 * Number of properties, which have no value left after the reduction.
	 *
	 * @return number of properties becoming empty
	 */
	public int getEmptyPropertyCount() {
		return emptyPropertyCount;
	}

	/**
	 * Number of checked properties, whose key does not exist in the base set.
	 *
	 * @return number of properties without counterpart in the base set
	 */
	public int getPropertiesWithoutBaseCount() {
		return propertiesWithoutBaseCount;
	}
}
