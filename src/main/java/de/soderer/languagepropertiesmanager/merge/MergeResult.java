package de.soderer.languagepropertiesmanager.merge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import de.soderer.languagepropertiesmanager.storage.LanguageProperty;

/**
 * Changes done by a merge import, for the final report
 */
public class MergeResult {
	final List<String> addedProperties = new ArrayList<>();
	final List<String> addedPropertiesWithForeignPath = new ArrayList<>();
	final List<String> notAddedProperties = new ArrayList<>();
	final List<String> filledValues = new ArrayList<>();
	final List<String> overwrittenValues = new ArrayList<>();
	final List<String> keptDifferingValues = new ArrayList<>();
	final List<String> newLanguageSigns = new ArrayList<>();

	/** Added or changed properties in order of their change, tracked by identity */
	private final List<LanguageProperty> changedProperties = new ArrayList<>();
	private final Set<LanguageProperty> changedPropertiesSet = Collections.newSetFromMap(new IdentityHashMap<>());

	MergeResult() {
		// Only created by LanguagePropertiesMerger
	}

	void markChanged(final LanguageProperty languageProperty) {
		if (changedPropertiesSet.add(languageProperty)) {
			changedProperties.add(languageProperty);
		}
	}

	public boolean hasChanges() {
		return !changedProperties.isEmpty();
	}

	/**
	 * Added or changed properties in order of their change
	 */
	public List<LanguageProperty> getChangedProperties() {
		return changedProperties;
	}

	/**
	 * Language signs, which did not exist in the loaded data before the merge.
	 * They are determined by the caller after completing the language signs of all properties.
	 */
	public void addNewLanguageSign(final String languageSign) {
		newLanguageSigns.add(languageSign);
	}
}
