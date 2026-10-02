package de.soderer.languagepropertiesmanager.merge;

import java.util.List;

import de.soderer.languagepropertiesmanager.storage.LanguageProperty;

/**
 * Planned reduction of one loaded property by its counterpart in the base set
 */
class ReducePlanEntry {
	final LanguageProperty languageProperty;
	/** Language signs whose values are identical to the base set */
	final List<String> identicalLanguageSigns;
	/** Whether the property has no language value left after the reduction */
	final boolean becomesEmpty;

	ReducePlanEntry(final LanguageProperty languageProperty, final List<String> identicalLanguageSigns, final boolean becomesEmpty) {
		this.languageProperty = languageProperty;
		this.identicalLanguageSigns = identicalLanguageSigns;
		this.becomesEmpty = becomesEmpty;
	}
}
