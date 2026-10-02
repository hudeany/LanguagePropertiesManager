package de.soderer.languagepropertiesmanager.merge;

import java.util.List;

import de.soderer.utilities.LangResources;

/**
 * Helpers shared by the merge import and the reduction by a base set
 */
final class MergeUtilities {
	private MergeUtilities() {
		// Utility class, no instances
	}

	static String getEmptyForNull(final String string) {
		return string == null ? "" : string;
	}

	static String getPropertyDisplayName(final String path, final String key) {
		return "\"" + getEmptyForNull(path) + "\" / \"" + getEmptyForNull(key) + "\"";
	}

	static void appendReportSection(final StringBuilder reportText, final String titleKey, final List<String> lines) {
		if (!lines.isEmpty()) {
			reportText.append("\n\n").append(LangResources.get(titleKey)).append(" (").append(lines.size()).append("):\n");
			for (final String line : lines) {
				reportText.append("  ").append(line).append("\n");
			}
		}
	}
}
