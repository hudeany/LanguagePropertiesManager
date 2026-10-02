package de.soderer.languagepropertiesmanager.check;

/**
 * Result of the error check of a set of properties
 */
public class ErrorReport {
	private final int issueCount;
	private final String reportText;

	public ErrorReport(final int issueCount, final String reportText) {
		this.issueCount = issueCount;
		this.reportText = reportText;
	}

	public int getIssueCount() {
		return issueCount;
	}

	public String getReportText() {
		return reportText;
	}
}
