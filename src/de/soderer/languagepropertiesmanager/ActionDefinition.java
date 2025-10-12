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
	private String propertiesFileExtension;
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

	public String getPropertiesFileExtension() {
		return propertiesFileExtension;
	}

	public void setPropertiesFileExtension(final String propertiesFileExtension) {
		this.propertiesFileExtension = propertiesFileExtension;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public ActionDefinition setVerbose(final boolean verbose) {
		this.verbose = verbose;
		return this;
	}

	public ActionDefinition checkParameters() throws LanguagePropertiesException {
		int actionModesCount = 0;
		if (importFromExcel != null) {
			actionModesCount++;
		}
		if (exportToExcel != null) {
			actionModesCount++;
		}
		if (importFromCsv != null) {
			actionModesCount++;
		}
		if (exportToCsv != null) {
			actionModesCount++;
		}
		if (actionModesCount == 0) {
			throw new LanguagePropertiesException("One of parameters importToExcel, exportFromExcel, importFromCsv, exportToCsv must be used");
		} else if (actionModesCount > 1) {
			throw new LanguagePropertiesException("Only one of parameters importToExcel, exportFromExcel, importFromCsv, exportToCsv may be used at a time");
		} else if (exportToExcel == null && excelFile != null) {
			throw new LanguagePropertiesException("Parameter excelFile is allowed for exportToExcel only");
		} else if (importFromExcel == null && outputDirectory != null) {
			throw new LanguagePropertiesException("Parameter outputDirectory is allowed for importFromExcel only");
		} else if (exportToCsv == null && csvFile != null) {
			throw new LanguagePropertiesException("Parameter csvFile is allowed for exportToCsv only");
		} else if (importFromCsv == null && outputDirectory != null) {
			throw new LanguagePropertiesException("Parameter outputDirectory is allowed for importFromCsv only");
		} else {
			return this;
		}
	}
}
