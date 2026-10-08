package de.soderer.languagepropertiesmanager;

/**
 * Parameters of a command line call (import from or export to Excel or CSV)
 */
public class ActionDefinition {
	private String importFromExcel = null;
	private String exportToExcel = null;
	private String importFromCsv = null;
	private String exportToCsv = null;
	private String excelFile = null;
	private String csvFile = null;
	private String outputDirectory = null;
	private boolean extendAndKeepExistingProperties = true;
	private boolean overwrite = false;
	private String propertiesFileExtension;
	private boolean verbose = false;

	/**
	 * Creates an empty action definition, which is filled from the command line parameters
	 */
	public ActionDefinition() {
		// Filled by the setters
	}

	/**
	 * Excel file to import ("-importFromExcel").
	 *
	 * @return the Excel file to import, or null if this action is not requested
	 */
	public String getImportFromExcel() {
		return importFromExcel;
	}

	/**
	 * Sets the Excel file to import ("-importFromExcel").
	 *
	 * @param importFromExcel
	 *            path of the Excel file
	 */
	public void setImportFromExcel(final String importFromExcel) {
		this.importFromExcel = importFromExcel;
	}

	/**
	 * Sets the Excel file to import ("-importFromExcel") (fluent variant of {@link #setImportFromExcel(String)}).
	 *
	 * @param newImportFromExcel
	 *            path of the Excel file
	 * @return this action definition for chaining
	 */
	public ActionDefinition withImportFromExcel(final String newImportFromExcel) {
		setImportFromExcel(newImportFromExcel);
		return this;
	}

	/**
	 * Properties file or directory to export into an Excel file ("-exportToExcel").
	 *
	 * @return the properties file or directory, or null if this action is not requested
	 */
	public String getExportToExcel() {
		return exportToExcel;
	}

	/**
	 * Sets the properties file or directory to export into an Excel file ("-exportToExcel").
	 *
	 * @param exportToExcel
	 *            path of a properties file or directory
	 */
	public void setExportToExcel(final String exportToExcel) {
		this.exportToExcel = exportToExcel;
	}

	/**
	 * Sets the properties file or directory to export into an Excel file ("-exportToExcel") (fluent variant of {@link #setExportToExcel(String)}).
	 *
	 * @param newExportToExcel
	 *            path of a properties file or directory
	 * @return this action definition for chaining
	 */
	public ActionDefinition withExportToExcel(final String newExportToExcel) {
		setExportToExcel(newExportToExcel);
		return this;
	}

	/**
	 * CSV file to import ("-importFromCsv").
	 *
	 * @return the CSV file to import, or null if this action is not requested
	 */
	public String getImportFromCsv() {
		return importFromCsv;
	}

	/**
	 * Sets the CSV file to import ("-importFromCsv").
	 *
	 * @param importFromCsv
	 *            path of the CSV file
	 */
	public void setImportFromCsv(final String importFromCsv) {
		this.importFromCsv = importFromCsv;
	}

	/**
	 * Sets the CSV file to import ("-importFromCsv") (fluent variant of {@link #setImportFromCsv(String)}).
	 *
	 * @param newImportFromCsv
	 *            path of the CSV file
	 * @return this action definition for chaining
	 */
	public ActionDefinition withImportFromCsv(final String newImportFromCsv) {
		setImportFromCsv(newImportFromCsv);
		return this;
	}

	/**
	 * Properties file or directory to export into a CSV file ("-exportToCsv").
	 *
	 * @return the properties file or directory, or null if this action is not requested
	 */
	public String getExportToCsv() {
		return exportToCsv;
	}

	/**
	 * Sets the properties file or directory to export into a CSV file ("-exportToCsv").
	 *
	 * @param exportToCsv
	 *            path of a properties file or directory
	 */
	public void setExportToCsv(final String exportToCsv) {
		this.exportToCsv = exportToCsv;
	}

	/**
	 * Sets the properties file or directory to export into a CSV file ("-exportToCsv") (fluent variant of {@link #setExportToCsv(String)}).
	 *
	 * @param newExportToCsv
	 *            path of a properties file or directory
	 * @return this action definition for chaining
	 */
	public ActionDefinition withExportToCsv(final String newExportToCsv) {
		setExportToCsv(newExportToCsv);
		return this;
	}

	/**
	 * Excel file to create by "-exportToExcel" ("-excelFile").
	 *
	 * @return the Excel output file or null
	 */
	public String getExcelFile() {
		return excelFile;
	}

	/**
	 * Sets the Excel file to create by "-exportToExcel" ("-excelFile").
	 *
	 * @param excelFile
	 *            path of the Excel output file
	 */
	public void setExcelFile(final String excelFile) {
		this.excelFile = excelFile;
	}

	/**
	 * Sets the Excel file to create by "-exportToExcel" ("-excelFile") (fluent variant of {@link #setExcelFile(String)}).
	 *
	 * @param newExcelFile
	 *            path of the Excel output file
	 * @return this action definition for chaining
	 */
	public ActionDefinition withExcelFile(final String newExcelFile) {
		setExcelFile(newExcelFile);
		return this;
	}

	/**
	 * CSV file to create by "-exportToCsv" ("-csvFile").
	 *
	 * @return the CSV output file or null
	 */
	public String getCsvFile() {
		return csvFile;
	}

	/**
	 * Sets the CSV file to create by "-exportToCsv" ("-csvFile").
	 *
	 * @param csvFile
	 *            path of the CSV output file
	 */
	public void setCsvFile(final String csvFile) {
		this.csvFile = csvFile;
	}

	/**
	 * Sets the CSV file to create by "-exportToCsv" ("-csvFile") (fluent variant of {@link #setCsvFile(String)}).
	 *
	 * @param newCsvFile
	 *            path of the CSV output file
	 * @return this action definition for chaining
	 */
	public ActionDefinition withCsvFile(final String newCsvFile) {
		setCsvFile(newCsvFile);
		return this;
	}

	/**
	 * Directory to write imported properties into ("-outputDirectory").
	 *
	 * @return the output directory, or null to write into the paths defined in the imported file
	 */
	public String getOutputDirectory() {
		return outputDirectory;
	}

	/**
	 * Sets the directory to write imported properties into ("-outputDirectory").
	 *
	 * @param outputDirectory
	 *            path of the output directory
	 */
	public void setOutputDirectory(final String outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	/**
	 * Sets the directory to write imported properties into ("-outputDirectory") (fluent variant of {@link #setOutputDirectory(String)}).
	 *
	 * @param newOutputDirectory
	 *            path of the output directory
	 * @return this action definition for chaining
	 */
	public ActionDefinition withOutputDirectory(final String newOutputDirectory) {
		setOutputDirectory(newOutputDirectory);
		return this;
	}

	/**
	 * Whether keys existing in the properties files, but not in the imported data, are kept on import.
	 *
	 * @return true if existing keys are kept (default)
	 */
	public boolean isExtendAndKeepExistingProperties() {
		return extendAndKeepExistingProperties;
	}

	/**
	 * Sets whether keys existing in the properties files, but not in the imported data, are kept on import.
	 *
	 * @param extendAndKeepExistingProperties
	 *            true to keep existing keys
	 */
	public void setExtendAndKeepExistingProperties(final boolean extendAndKeepExistingProperties) {
		this.extendAndKeepExistingProperties = extendAndKeepExistingProperties;
	}

	/**
	 * Sets whether keys existing in the properties files, but not in the imported data, are kept on import (fluent variant of {@link #setExtendAndKeepExistingProperties(boolean)}).
	 *
	 * @param newExtendAndKeepExistingProperties
	 *            true to keep existing keys
	 * @return this action definition for chaining
	 */
	public ActionDefinition withExtendAndKeepExistingProperties(final boolean newExtendAndKeepExistingProperties) {
		setExtendAndKeepExistingProperties(newExtendAndKeepExistingProperties);
		return this;
	}

	/**
	 * Whether an existing export file may be replaced ("-overwrite").
	 *
	 * @return true if existing files are overwritten
	 */
	public boolean isOverwrite() {
		return overwrite;
	}

	/**
	 * Sets whether an existing export file may be replaced ("-overwrite").
	 *
	 * @param overwrite
	 *            true to overwrite existing files
	 */
	public void setOverwrite(final boolean overwrite) {
		this.overwrite = overwrite;
	}

	/**
	 * Sets whether an existing export file may be replaced ("-overwrite") (fluent variant of {@link #setOverwrite(boolean)}).
	 *
	 * @param newOverwrite
	 *            true to overwrite existing files
	 * @return this action definition for chaining
	 */
	public ActionDefinition withOverwrite(final boolean newOverwrite) {
		setOverwrite(newOverwrite);
		return this;
	}

	/**
	 * File extension of the properties files ("-propertiesFileExtension").
	 *
	 * @return the file extension, or null for the default ".properties"
	 */
	public String getPropertiesFileExtension() {
		return propertiesFileExtension;
	}

	/**
	 * Sets the file extension of the properties files ("-propertiesFileExtension").
	 *
	 * @param propertiesFileExtension
	 *            the file extension, a missing leading dot is added
	 */
	public void setPropertiesFileExtension(final String propertiesFileExtension) {
		this.propertiesFileExtension = propertiesFileExtension;
	}

	/**
	 * Sets the file extension of the properties files ("-propertiesFileExtension") (fluent variant of {@link #setPropertiesFileExtension(String)}).
	 *
	 * @param newPropertiesFileExtension
	 *            the file extension, a missing leading dot is added
	 * @return this action definition for chaining
	 */
	public ActionDefinition withPropertiesFileExtension(final String newPropertiesFileExtension) {
		setPropertiesFileExtension(newPropertiesFileExtension);
		return this;
	}

	/**
	 * Whether progress and result texts are printed ("-v").
	 *
	 * @return true for verbose output
	 */
	public boolean isVerbose() {
		return verbose;
	}

	/**
	 * Sets whether progress and result texts are printed ("-v").
	 *
	 * @param verbose
	 *            true for verbose output
	 */
	public void setVerbose(final boolean verbose) {
		this.verbose = verbose;
	}

	/**
	 * Sets whether progress and result texts are printed ("-v") (fluent variant of {@link #setVerbose(boolean)}).
	 *
	 * @param newVerbose
	 *            true for verbose output
	 * @return this action definition for chaining
	 */
	public ActionDefinition withVerbose(final boolean newVerbose) {
		setVerbose(newVerbose);
		return this;
	}

	/**
	 * Checks that exactly one action is requested and only parameters allowed for it are set.
	 *
	 * @return this action definition for chaining
	 * @throws LanguagePropertiesException
	 *             if the parameters are inconsistent
	 */
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
			throw new LanguagePropertiesException(
					"One of parameters importFromExcel, exportToExcel, importFromCsv, exportToCsv must be used");
		} else if (actionModesCount > 1) {
			throw new LanguagePropertiesException(
					"Only one of parameters importFromExcel, exportToExcel, importFromCsv, exportToCsv may be used at a time");
		} else if (exportToExcel == null && excelFile != null) {
			throw new LanguagePropertiesException("Parameter excelFile is allowed for exportToExcel only");
		} else if (exportToExcel != null && excelFile == null) {
			throw new LanguagePropertiesException("Parameter excelFile is mandatory for exportToExcel");
		} else if (exportToCsv == null && csvFile != null) {
			throw new LanguagePropertiesException("Parameter csvFile is allowed for exportToCsv only");
		} else if (exportToCsv != null && csvFile == null) {
			throw new LanguagePropertiesException("Parameter csvFile is mandatory for exportToCsv");
		} else if (outputDirectory != null && importFromExcel == null && importFromCsv == null) {
			throw new LanguagePropertiesException("Parameter outputDirectory is allowed for importFromExcel or importFromCsv only");
		} else {
			return this;
		}
	}
}
