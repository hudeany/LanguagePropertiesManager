package de.soderer.languagepropertiesmanager.worker;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import de.soderer.languagepropertiesmanager.LanguagePropertiesException;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

public class ExportToExcelWorker extends WorkerSimple<Boolean> {
	private final List<String> languagePropertiesSetNames;
	private final List<LanguageProperty> languageProperties;
	private final File excelOutputFile;
	private final boolean overwrite;

	public ExportToExcelWorker(final WorkerParentSimple parent, final List<LanguageProperty> languageProperties, final List<String> languagePropertiesSetNames, final File excelOutputFile, final boolean overwrite) {
		super(parent);

		this.languagePropertiesSetNames = languagePropertiesSetNames;
		this.languageProperties = languageProperties;
		this.excelOutputFile = excelOutputFile;
		this.overwrite = overwrite;
	}

	@Override
	public Boolean work() throws Exception {
		parent.changeTitle("Excel export");
		itemsToDo = languageProperties.size();

		if (excelOutputFile.exists() && !overwrite) {
			throw new LanguagePropertiesException("Export excel file '" + excelOutputFile.getAbsolutePath() + "' already exists. Use 'overwrite' to replace existing file.");
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

		try (final XSSFWorkbook workbook = new XSSFWorkbook()) {
			try (final FileOutputStream outputStream = new FileOutputStream(excelOutputFile)) {
				final XSSFSheet sheet = workbook.createSheet(languagePropertiesSetNames.size() == 1 ? languagePropertiesSetNames.get(0) : "Multiple");

				final XSSFCellStyle cellStyle = workbook.createCellStyle();
				cellStyle.setWrapText(true);

				// Write header row
				final Row headerRow = sheet.createRow(0);
				int headerColumnIndex = 0;

				Cell headerCell = headerRow.createCell(headerColumnIndex++);
				headerCell.setCellValue("Path");

				headerCell = headerRow.createCell(headerColumnIndex++);
				headerCell.setCellValue("Index");

				headerCell = headerRow.createCell(headerColumnIndex++);
				headerCell.setCellValue("Key");

				if (commentsFound) {
					headerCell = headerRow.createCell(headerColumnIndex++);
					headerCell.setCellValue("Comment");
				}

				final List<String> languageSignsInOutputOrder = Utilities.sortButPutItemsFirst(availableLanguageSigns, LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);

				for (final String languageSign : languageSignsInOutputOrder) {
					headerCell = headerRow.createCell(headerColumnIndex++);
					headerCell.setCellValue(languageSign);
				}

				// Write data rows
				int dataRowIndex = 1;
				for (final LanguageProperty languageproperty : sortedLanguageProperties) {
					int dataColumnIndex = 0;
					final Row dataRow = sheet.createRow(dataRowIndex++);

					Cell dataCell = dataRow.createCell(dataColumnIndex++);
					dataCell.setCellValue(languageproperty.getPath());

					dataCell = dataRow.createCell(dataColumnIndex++);
					dataCell.setCellValue(languageproperty.getOriginalIndex());

					dataCell = dataRow.createCell(dataColumnIndex++);
					dataCell.setCellValue(languageproperty.getKey());

					if (commentsFound) {
						dataCell = dataRow.createCell(dataColumnIndex++);
						dataCell.setCellValue(languageproperty.getComment() == null ? "" : languageproperty.getComment());
					}

					for (final String languageSign : languageSignsInOutputOrder) {
						dataCell = dataRow.createCell(dataColumnIndex++);
						dataCell.setCellStyle(cellStyle);
						final String languageValue = languageproperty.getLanguageValue(languageSign);
						if (languageValue != null) {
							dataCell.setCellValue(languageValue);
						} else {
							dataCell.setCellValue("");
						}
					}

					itemsDone++;
					signalProgress(false);
				}

				itemsDone = itemsToDo;
				signalProgress(true);

				// Resize columns for optimal width
				for (int i = 0; i < languageSignsInOutputOrder.size() + 3; i++) {
					sheet.autoSizeColumn(i);
				}

				workbook.write(outputStream);
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
