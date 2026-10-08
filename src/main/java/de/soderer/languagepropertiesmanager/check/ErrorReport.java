package de.soderer.languagepropertiesmanager.check;

/**
 * Result of the error check of a set of properties
 */
public class ErrorReport {
	private final int issueCount;
	private final String reportText;

	/**
	 * Creates the result of an error check.
	 *
	 * @param issueCount
	 *            number of found issues
	 * @param reportText
	 *            human readable report of all found issues, empty if there are none
	 */
	public ErrorReport(final int issueCount, final String reportText) {
		this.issueCount = issueCount;
		this.reportText = reportText;
	}

	/**
	 * Number of found issues.
	 *
	 * @return number of found issues, 0 if the checked properties are fine
	 */
	public int getIssueCount() {
		return issueCount;
	}

	/**
	 * Human readable report of all found issues, grouped by property.
	 *
	 * @return report text, empty if no issues were found
	 */
	public String getReportText() {
		return reportText;
	}
}
