package de.soderer.languagepropertiesmanager.merge;

import de.soderer.languagepropertiesmanager.storage.LanguageProperty;

/**
 * Planned handling of one imported property
 */
class MergePlanEntry {
	final LanguageProperty importedProperty;
	/** Existing property to merge the values into, or null if the imported property is added as new property */
	final LanguageProperty targetProperty;
	/** Path a new property gets, only used if targetProperty is null */
	final String pathForNewProperty;
	/** Whether a new property keeps a path that does not belong to the currently loaded properties sets */
	final boolean foreignPath;

	MergePlanEntry(final LanguageProperty importedProperty, final LanguageProperty targetProperty, final String pathForNewProperty, final boolean foreignPath) {
		this.importedProperty = importedProperty;
		this.targetProperty = targetProperty;
		this.pathForNewProperty = pathForNewProperty;
		this.foreignPath = foreignPath;
	}
}
