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

public class ImportFromCsvWorker extends WorkerSimple<Boolean> {
	private static final Pattern LANGUAGEANDCOUNTRYPATTERN = Pattern.compile("^[a-zA-Z]{2}_[a-zA-Z]{2}$");
	private static final Pattern LANGUAGEPATTERN = Pattern.compile("^[a-zA-Z]{2}$");

	private final File importCsvFile;

	private List<String> languagePropertiesSetNames;
	private List<LanguageProperty> languageProperties;
	private List<String> availableLanguageSigns;
	private boolean ignoreComments = false;
	private boolean commentsFound;

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
					// Missing trailing cells (short rows) are treated as empty values
					final String valueCell = getCell(valuesRow, entry.getKey(), "");
					languageProperty.setLanguageValue(entry.getValue(), valueCell);
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

		final Comparator<LanguageProperty> compareByPathAndIndex = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getOriginalIndex);
		languageProperties = languageProperties.stream().sorted(compareByPathAndIndex).collect(Collectors.toList());

		// TODO
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

	public List<String> getLanguagePropertiesSetNames() {
		return languagePropertiesSetNames;
	}

	public List<LanguageProperty> getLanguageProperties() {
		return languageProperties;
	}

	public List<String> getAvailableLanguageSigns() {
		return availableLanguageSigns;
	}

	public boolean isCommentsFound() {
		return commentsFound;
	}

	public boolean isIgnoreComments() {
		return ignoreComments;
	}

	public void setIgnoreComments(final boolean ignoreComments) {
		this.ignoreComments = ignoreComments;
	}

	public String getLanguagePropertiesSetName() {
		if (languagePropertiesSetNames.size() == 1) {
			return languagePropertiesSetNames.get(0);
		} else {
			return "Multiple";
		}
	}
}
