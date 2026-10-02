package de.soderer.languagepropertiesmanager.model;

import java.util.ArrayList;
import java.util.List;

import de.soderer.languagepropertiesmanager.storage.LanguageProperty;

/**
 * Duplicates of path/key combinations found by
 * LanguagePropertiesModel.findDuplicates(). Nothing is changed until the plan
 * is applied by LanguagePropertiesModel.removeDuplicates().
 */
public class DuplicatesPlan {
	/** Groups of properties with the same path and key, each sorted by original index, so the first one is kept */
	final List<List<LanguageProperty>> duplicateGroups = new ArrayList<>();

	DuplicatesPlan() {
		// Only created by LanguagePropertiesModel
	}

	public boolean hasDuplicates() {
		return !duplicateGroups.isEmpty();
	}

	/** Number of properties, which would be removed */
	public int getDuplicateCount() {
		int duplicateCount = 0;
		for (final List<LanguageProperty> group : duplicateGroups) {
			duplicateCount += group.size() - 1;
		}
		return duplicateCount;
	}

	/** One line per duplicate path/key combination with the number of its occurrences */
	public String getReportText() {
		final StringBuilder reportText = new StringBuilder();
		for (final List<LanguageProperty> group : duplicateGroups) {
			final LanguageProperty keptProperty = group.get(0);
			reportText.append("\"").append(keptProperty.getPath()).append("\" / \"").append(keptProperty.getKey()).append("\": ").append(group.size()).append("\n");
		}
		return reportText.toString();
	}
}
