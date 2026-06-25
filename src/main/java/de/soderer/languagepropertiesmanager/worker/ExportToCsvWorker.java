package de.soderer.languagepropertiesmanager.worker;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import de.soderer.languagepropertiesmanager.LanguagePropertiesException;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.csv.CsvFormat;
import de.soderer.utilities.csv.CsvWriter;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

public class ExportToCsvWorker extends WorkerSimple<Boolean> {
	private final List<LanguageProperty> languageProperties;
	private final File csvOutputFile;
	private final boolean overwrite;

	public ExportToCsvWorker(final WorkerParentSimple parent, final List<LanguageProperty> languageProperties, @SuppressWarnings("unused") final List<String> languagePropertiesSetNames, final File csvOutputFile, final boolean overwrite) {
		super(parent);

		this.languageProperties = languageProperties;
		this.csvOutputFile = csvOutputFile;
		this.overwrite = overwrite;
	}

	@Override
	public Boolean work() throws Exception {
		parent.changeTitle("CSV export");
		itemsToDo = languageProperties.size();

		if (csvOutputFile.exists() && !overwrite) {
			throw new LanguagePropertiesException("Export CSV file '" + csvOutputFile.getAbsolutePath() + "' already exists. Use 'overwrite' to replace existing file.");
		}

		final List<String> availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);

		final Comparator<LanguageProperty> compareByPathAndIndex = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getOriginalIndex);
		final List<LanguageProperty> sortedLanguageProperties = languageProperties.stream().sorted(compareByPathAndIndex).collect(Collectors.toList());

		boolean commentsFound = false;
		for (final LanguageProperty languageProperty : sortedLanguageProperties) {
			if (Utilities.isNotEmpty(languageProperty.getComment())) {
				commentsFound = true;
				break;
			}
		}

		try (final FileOutputStream outputStream = new FileOutputStream(csvOutputFile)) {
			try (final CsvWriter csvWriter = new CsvWriter(outputStream, new CsvFormat().setSeparator(';').setStringQuote('"').setStringQuoteEscapeCharacter('\\'))) {
				// Write header row
				final List<String> headerList = new ArrayList<>();
				headerList.add("Path");
				headerList.add("Index");
				headerList.add("Key");

				if (commentsFound) {
					headerList.add("Comment");
				}

				final List<String> languageSignsInOutputOrder = Utilities.sortButPutItemsFirst(availableLanguageSigns, LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);

				for (final String languageSign : languageSignsInOutputOrder) {
					headerList.add(languageSign);
				}

				csvWriter.writeValues(headerList);

				// Write data rows
				for (final LanguageProperty languageproperty : sortedLanguageProperties) {
					final List<String> dataRow = new ArrayList<>();

					dataRow.add(languageproperty.getPath());
					dataRow.add(Integer.toString(languageproperty.getOriginalIndex()));
					dataRow.add(languageproperty.getKey());

					if (commentsFound) {
						dataRow.add(languageproperty.getComment() == null ? "" : languageproperty.getComment());
					}

					for (final String languageSign : languageSignsInOutputOrder) {
						final String languageValue = languageproperty.getLanguageValue(languageSign);
						if (languageValue != null) {
							dataRow.add(languageValue);
						} else {
							dataRow.add("");
						}
					}

					csvWriter.writeValues(dataRow);

					itemsDone++;
					signalProgress(false);
				}

				itemsDone = itemsToDo;
				signalProgress(true);
			}
		}

		return !cancel;
	}

	@Override
	public String getResultText() {
		// TODO
		return null;
	}
}
