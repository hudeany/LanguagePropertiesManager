package de.soderer.languagepropertiesmanager.worker;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.soderer.languagepropertiesmanager.LanguagePropertiesException;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.csv.CsvFormat;
import de.soderer.utilities.csv.CsvReader;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

/**
 * Imports language properties from a CSV file (separator ';', RFC 4180
 * quoting) as written by {@link ExportToCsvWorker}. A key column is mandatory,
 * path, index, comment and language columns ("default", "de", "de_AT", ...)
 * are detected by their header.
 */
public class ImportFromCsvWorker extends WorkerSimple<Boolean> {
	private static final Pattern LANGUAGEANDCOUNTRYPATTERN = Pattern.compile("^[a-zA-Z]{2}_[a-zA-Z]{2}$");
	private static final Pattern LANGUAGEPATTERN = Pattern.compile("^[a-zA-Z]{2}$");

	private final File importCsvFile;

	private List<String> languagePropertiesSetNames;
	private List<LanguageProperty> languageProperties;
	private List<String> availableLanguageSigns;
	private boolean ignoreComments = false;
	private boolean commentsFound;

	/**
	 * Creates the import worker.
	 *
	 * @param parent
	 *            receiver of the progress signals, may be null
	 * @param importCsvFile
	 *            CSV file to read
	 */
	public ImportFromCsvWorker(final WorkerParentSimple parent, final File importCsvFile) {
		super(parent);

		this.importCsvFile = importCsvFile;
	}

	@Override
	public Boolean work() throws Exception {
		parent.changeTitle("CSV import");
		// Shared RFC 4180 format, identical to the one used by ExportToCsvWorker.
		// Backslashes are plain characters, so Windows paths are read as they are.
		final CsvFormat csvFormat = new CsvFormat()
				.withSeparator(';')
				.withStringQuote('"')
				.withStringQuoteEscapeCharacter('"')
				.withEscapeLineBreaks(false);
		try (FileInputStream inputStream = new FileInputStream(importCsvFile);
				CsvReader csvReader = new CsvReader(inputStream, csvFormat)) {
			// Read headers
			int columnIndex_Path = -1;
			int columnIndex_Keys = -1;
			int columnIndex_Index = -1;
			int columnIndex_Comment = -1;
			final Map<Integer, String> languageColumnHeaders = new HashMap<>();
			final List<String> headerRow = csvReader.readNextCsvLine();
			if (headerRow == null) {
				throw new LanguagePropertiesException("Csv file is empty");
			}
			int headerColumnIndex = -1;
			for (final String header : headerRow) {
				headerColumnIndex++;
				final String cellValue = header == null ? "" : header.trim();
				if ("path".equalsIgnoreCase(cellValue)
						|| "pfad".equalsIgnoreCase(cellValue)
						|| "datei".equalsIgnoreCase(cellValue)
						|| "file".equalsIgnoreCase(cellValue)) {
					columnIndex_Path = headerColumnIndex;
				} else if ("key".equalsIgnoreCase(cellValue)
						|| "keys".equalsIgnoreCase(cellValue)
						|| "bezeichner".equalsIgnoreCase(cellValue)
						|| "schlüssel".equalsIgnoreCase(cellValue)
						|| "schluessel".equalsIgnoreCase(cellValue)) {
					columnIndex_Keys = headerColumnIndex;
				} else if ("index".equalsIgnoreCase(cellValue)
						|| "idx".equalsIgnoreCase(cellValue)
						|| "org.idx".equalsIgnoreCase(cellValue)) {
					columnIndex_Index = headerColumnIndex;
				} else if (("comment".equalsIgnoreCase(cellValue)
						|| "kommentar".equalsIgnoreCase(cellValue)) && !ignoreComments) {
					columnIndex_Comment = headerColumnIndex;
				} else if ("default".equalsIgnoreCase(cellValue)) {
					languageColumnHeaders.put(headerColumnIndex, cellValue.toLowerCase());
				} else if (LANGUAGEANDCOUNTRYPATTERN.matcher(cellValue).matches()
						|| LANGUAGEPATTERN.matcher(cellValue).matches()) {
					languageColumnHeaders.put(headerColumnIndex, cellValue);
				}
			}

			if (columnIndex_Keys == -1) {
				throw new LanguagePropertiesException("Csv file does not contain mandatory column for keys");
			}

			final boolean hasDefaultLanguageColumn = languageColumnHeaders.containsValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);

			// Read data
			languageProperties = new ArrayList<>();
			int rowIndex = -1;

			itemsDone = 0;
			signalUnlimitedProgress();

			List<String> valuesRow;
			while ((valuesRow = csvReader.readNextCsvLine()) != null) {
				if (cancel) {
					break;
				}

				// Header row was already consumed before the loop, so every row here is data.
				// rowIndex is the 0-based index of the data row, the line number in the
				// file (1-based, including the header row) is rowIndex + 2.
				rowIndex++;
				final int fileRowNumber = rowIndex + 2;

				String path = "";
				if (columnIndex_Path >= 0) {
					path = getCell(valuesRow, columnIndex_Path, "").trim();
				}

				final String key = getCell(valuesRow, columnIndex_Keys, "").trim();
				if (key.isEmpty()) {
					throw new LanguagePropertiesException("Csv file contains empty key at row " + fileRowNumber + " and column " + (columnIndex_Keys + 1));
				}

				final LanguageProperty languageProperty = new LanguageProperty(path, key);

				if (columnIndex_Index >= 0) {
					final String indexCell = getCell(valuesRow, columnIndex_Index, "");
					try {
						languageProperty.setOriginalIndex(Integer.parseInt(indexCell.trim()));
					} catch (final Exception e) {
						throw new LanguagePropertiesException("Csv file contains invalid index value at row " + fileRowNumber + " and column " + (columnIndex_Index + 1), e);
					}
				} else {
					languageProperty.setOriginalIndex(rowIndex);
				}

				if (columnIndex_Comment >= 0) {
					final String commentCell = getCell(valuesRow, columnIndex_Comment, null);
					try {
						languageProperty.setComment(commentCell);
					} catch (final Exception e) {
						throw new LanguagePropertiesException("Csv file contains invalid comment value at row " + fileRowNumber + " and column " + (columnIndex_Comment + 1), e);
					}
				} else {
					languageProperty.setComment(null);
				}

				for (final Entry<Integer, String> entry : languageColumnHeaders.entrySet()) {
					// Missing trailing cells (short rows) are treated as empty values.
					// CSV can not distinguish between "empty" and "missing":
					// With a default language column, empty cells are kept as "" in the default language and stored as missing (null) in all other languages.
					// Without a default language column, empty cells are kept as "" in all languages, so no key is lost.
					final String valueCell = getCell(valuesRow, entry.getKey(), "");
					languageProperty.setLanguageValue(entry.getValue(), hasDefaultLanguageColumn ? LanguageProperty.toStorageValue(entry.getValue(), valueCell) : valueCell);
				}

				languageProperties.add(languageProperty);

				itemsDone++;
				signalProgress(false);
			}
		}

		if (cancel) {
			return false;
		}

		itemsToDo = itemsDone;
		signalProgress(true);

		availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);

		final Comparator<LanguageProperty> compareByPathAndIndex = Comparator.comparing(LanguageProperty::getPath, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(LanguageProperty::getOriginalIndex);
		languageProperties = languageProperties.stream().sorted(compareByPathAndIndex).collect(Collectors.toList());

		languagePropertiesSetNames = LanguagePropertiesFileSetReader.getLanguagePropertiesSetNames(languageProperties);

		commentsFound = false;
		for (final LanguageProperty languageProperty : languageProperties) {
			if (Utilities.isNotEmpty(languageProperty.getComment())) {
				commentsFound = true;
				break;
			}
		}

		return !cancel;
	}

	/**
	 * Returns the cell value at the given column index, or the default value
	 * if the row is shorter than expected or the cell is null.
	 */
	private static String getCell(final List<String> row, final int columnIndex, final String defaultValue) {
		if (row == null || columnIndex < 0 || columnIndex >= row.size()) {
			return defaultValue;
		}
		final String value = row.get(columnIndex);
		return value == null ? defaultValue : value;
	}

	@Override
	public String getResultText() {
		return null;
	}

	/**
	 * Names of the properties sets found in the path column, available after the import.
	 *
	 * @return the set names, empty if the file has no paths
	 */
	public List<String> getLanguagePropertiesSetNames() {
		return languagePropertiesSetNames;
	}

	/**
	 * The imported properties, sorted by path and original index, available after the import.
	 *
	 * @return the imported properties
	 */
	public List<LanguageProperty> getLanguageProperties() {
		return languageProperties;
	}

	/**
	 * Language signs of the imported properties, default language first, available after the import.
	 *
	 * @return the language signs
	 */
	public List<String> getAvailableLanguageSigns() {
		return availableLanguageSigns;
	}

	/**
	 * Whether any imported property has a comment.
	 *
	 * @return true if comments were found
	 */
	public boolean isCommentsFound() {
		return commentsFound;
	}

	/**
	 * Whether a comment column is ignored.
	 *
	 * @return true if comments are ignored
	 */
	public boolean isIgnoreComments() {
		return ignoreComments;
	}

	/**
	 * Sets whether a comment column is ignored. Must be set before the import is started.
	 *
	 * @param ignoreComments
	 *            true to ignore comments
	 */
	public void setIgnoreComments(final boolean ignoreComments) {
		this.ignoreComments = ignoreComments;
	}

	/**
	 * Name of the imported properties set: the only set name of the path column,
	 * "Multiple" for several set names, or the name of the CSV file (without
	 * extension) if the file has no paths.
	 *
	 * @return the set name
	 */
	public String getLanguagePropertiesSetName() {
		if (languagePropertiesSetNames == null || languagePropertiesSetNames.isEmpty()) {
			// Without paths a set name derived from the file is better than an empty name, which would create files like ".properties"
			final String fileName = importCsvFile.getName();
			return fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
		} else if (languagePropertiesSetNames.size() == 1) {
			return languagePropertiesSetNames.get(0);
		} else {
			return "Multiple";
		}
	}
}
