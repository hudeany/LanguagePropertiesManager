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
	private boolean commentsFound;

	public ImportFromCsvWorker(final WorkerParentSimple parent, final File importCsvFile) {
		super(parent);

		this.importCsvFile = importCsvFile;
	}

	@Override
	public Boolean work() throws Exception {
		parent.changeTitle("CSV import");
		try (FileInputStream inputStream = new FileInputStream(importCsvFile);
				CsvReader csvReader = new CsvReader(inputStream, new CsvFormat().setSeparator(';').setStringQuote('"').setStringQuoteEscapeCharacter('\\'))) {
			// Read headers
			int columnIndex_Path = -1;
			int columnIndex_Keys = -1;
			int columnIndex_Index = -1;
			int columnIndex_Comment = -1;
			final Map<Integer, String> languageColumnHeaders = new HashMap<>();
			final List<String> headerRow = csvReader.readNextCsvLine();
			int headerColumnIndex = -1;
			for (final String header : headerRow) {
				headerColumnIndex++;
				final String cellValue = header.trim();
				if ("path".equalsIgnoreCase(cellValue.trim())
						|| "pfad".equalsIgnoreCase(cellValue.trim())
						|| "datei".equalsIgnoreCase(cellValue.trim())
						|| "file".equalsIgnoreCase(cellValue.trim())) {
					columnIndex_Path = headerColumnIndex;
				} else if ("key".equalsIgnoreCase(cellValue.trim())
						|| "keys".equalsIgnoreCase(cellValue.trim())
						|| "bezeichner".equalsIgnoreCase(cellValue.trim())
						|| "schlüssel".equalsIgnoreCase(cellValue.trim())
						|| "schluessel".equalsIgnoreCase(cellValue.trim())) {
					columnIndex_Keys = headerColumnIndex;
				} else if ("index".equalsIgnoreCase(cellValue.trim())
						|| "idx".equalsIgnoreCase(cellValue.trim())
						|| "org.idx".equalsIgnoreCase(cellValue.trim())) {
					columnIndex_Index = headerColumnIndex;
				} else if ("comment".equalsIgnoreCase(cellValue.trim())
						|| "kommentar".equalsIgnoreCase(cellValue.trim())) {
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

			itemsToDo = 1000;
			itemsDone = 0;

			List<String> valuesRow;
			while ((valuesRow = csvReader.readNextCsvLine()) != null) {
				rowIndex++;
				if (rowIndex > 0) {
					String path = null;
					if (columnIndex_Path >= 0) {
						path = valuesRow.get(columnIndex_Path).trim();
					}

					final String key = valuesRow.get(columnIndex_Keys).trim();

					final LanguageProperty languageProperty = new LanguageProperty(path, key);

					if (columnIndex_Index >= 0) {
						final String indexCell = valuesRow.get(columnIndex_Index);
						try {
							languageProperty.setOriginalIndex(Integer.parseInt(indexCell.trim()));
						} catch (final Exception e) {
							throw new LanguagePropertiesException("Csv file contains invalid index value at row " + (rowIndex + 1) + " and column " + (columnIndex_Index + 1), e);
						}
					} else {
						languageProperty.setOriginalIndex(rowIndex);
					}

					if (columnIndex_Comment >= 0) {
						final String commentCell = valuesRow.get(columnIndex_Comment);
						try {
							languageProperty.setComment(commentCell);
						} catch (final Exception e) {
							throw new LanguagePropertiesException("Csv file contains invalid comment value at row " + (rowIndex + 1) + " and column " + (columnIndex_Index + 1), e);
						}
					} else {
						languageProperty.setComment(null);
					}

					for (final Entry<Integer, String> entry : languageColumnHeaders.entrySet()) {
						final String valueCell = valuesRow.get(entry.getKey());
						languageProperty.setLanguageValue(entry.getValue(), valueCell);
					}

					languageProperties.add(languageProperty);
				}


				itemsDone++;
				signalProgress(false);
			}
		}

		itemsDone = itemsToDo;
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
}
