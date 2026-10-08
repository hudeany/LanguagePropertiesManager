package de.soderer.languagepropertiesmanager.merge;

import java.util.ArrayList;
import java.util.List;

import de.soderer.languagepropertiesmanager.storage.LanguageProperty;

/**
 * Changes done by a reduction by a base set, for the final report
 */
public class ReduceResult {
	final List<String> clearedValues = new ArrayList<>();
	final List<String> removedProperties = new ArrayList<>();
	/** Reduced properties, which still exist after the reduction */
	final List<LanguageProperty> changedProperties = new ArrayList<>();

	ReduceResult() {
		// Only created by BaseSetReducer
	}

	/**
	 * Reduced properties, which still exist after the reduction.
	 *
	 * @return the reduced and not removed properties
	 */
	public List<LanguageProperty> getChangedProperties() {
		return changedProperties;
	}
}
