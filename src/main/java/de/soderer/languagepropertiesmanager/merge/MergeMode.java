package de.soderer.languagepropertiesmanager.merge;

/**
 * How conflicts between existing and imported values are resolved
 */
public enum MergeMode {
	/** Add new keys and fill empty values, keep existing non-empty values */
	ADD_NEW,
	/** Add new keys, fill empty values and overwrite differing existing values */
	OVERWRITE,
	/** Do not add new keys, only fill empty values of existing keys */
	FILL_EMPTY_ONLY
}
