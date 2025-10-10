package de.soderer.languagepropertiesmanager;

public class ActionDefinition {
	private String importFromExcel = null;
	private String exportToExcel = null;
	private String importFromCsv = null;
	private String exportToCsv = null;
	private String excelFile = null;
	private String csvFile = null;
	private String outputDirectory = null;
	private boolean overwrite = false;
	private boolean verbose = false;

	public String getImportFromExcel() {
		return importFromExcel;
	}

	public ActionDefinition setImportFromExcel(final String importFromExcel) {
		this.importFromExcel = importFromExcel;
		return this;
	}

	public String getExportToExcel() {
		return exportToExcel;
	}

	public ActionDefinition setExportToExcel(final String exportToExcel) {
		this.exportToExcel = exportToExcel;
		return this;
	}

	public String getImportFromCsv() {
		return importFromCsv;
	}

	public ActionDefinition setImportFromCsv(final String importFromCsv) {
		this.importFromCsv = importFromCsv;
		return this;
	}

	public String getExportToCsv() {
		return exportToCsv;
	}

	public ActionDefinition setExportToCsv(final String exportToCsv) {
		this.exportToCsv = exportToCsv;
		return this;
	}

	public String getExcelFile() {
		return excelFile;
	}

	public ActionDefinition setExcelFile(final String excelFile) {
		this.excelFile = excelFile;
		return this;
	}

	public String getCsvFile() {
		return csvFile;
	}

	public ActionDefinition setCsvFile(final String csvFile) {
		this.csvFile = csvFile;
		return this;
	}

	public String getOutputDirectory() {
		return outputDirectory;
	}

	public ActionDefinition setOutputDirectory(final String outputDirectory) {
		this.outputDirectory = outputDirectory;
		return this;
	}

	public boolean isOverwrite() {
		return overwrite;
	}

	public ActionDefinition setOverwrite(final boolean overwrite) {
		this.overwrite = overwrite;
		return this;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public ActionDefinition setVerbose(final boolean verbose) {
		this.verbose = verbose;
		return this;
	}

	public ActionDefinition checkParameters() throws LanguagePropertiesException {
		if (importFromExcel != null && exportToExcel != null) {
			throw new LanguagePropertiesException("Only one of parameters importToExcel and exportFromExcel is allowed");
		} else if (importFromExcel != null && excelFile != null) {
			throw new LanguagePropertiesException("Parameter excelFile is allowed for exportToExcel");
		} else if (exportToExcel != null && outputDirectory != null) {
			throw new LanguagePropertiesException("Parameter outputDirectory is allowed for importFromExcel");
		} else {
			return this;
		}
	}
}
