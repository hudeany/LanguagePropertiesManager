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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

public class ImportFromExcelWorker extends WorkerSimple<Boolean> {
	private static final Pattern LANGUAGEANDCOUNTRYPATTERN = Pattern.compile("^(_[a-zA-Z]{2}){2}$");
	private static final Pattern LANGUAGEPATTERN = Pattern.compile("^[a-zA-Z]{2}$");

	private final File importExcelFile;

	private List<String> languagePropertiesSetNames;
	private List<LanguageProperty> languageProperties;
	private List<String> availableLanguageSigns;
	private boolean commentsFound;

	public ImportFromExcelWorker(final WorkerParentSimple parent, final File importExcelFile) {
		super(parent);

		this.importExcelFile = importExcelFile;
	}

	@Override
	public Boolean work() throws Exception {
		parent.changeTitle("Excel import");
		try (FileInputStream inputStream = new FileInputStream(importExcelFile);
				final XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
			XSSFSheet sheet = null;
			if (workbook.getNumberOfSheets() == 1) {
				sheet = workbook.getSheetAt(0);
			} else if (workbook.getNumberOfSheets() > 1) {
				throw new Exception("Excel file contains more than 1 expected sheet");
			} else {
				throw new Exception("Excel file does not contain expected sheet");
			}

			// Read headers
			int columnIndex_Path = -1;
			int columnIndex_Keys = -1;
			int columnIndex_Index = -1;
			int columnIndex_Comment = -1;
			final Map<Integer, String> languageColumnHeaders = new HashMap<>();
			final Row headerRow = sheet.getRow(0);
			int headerColumnIndex = -1;
			for (final Cell headerCell : headerRow) {
				headerColumnIndex++;
				if (headerCell.getCellType() == CellType.STRING) {
					final String cellValue = headerCell.getStringCellValue().trim();
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
			}

			if (columnIndex_Keys == -1) {
				throw new Exception("Excel file does not contain mandatory column for keys in sheet: " + sheet.getSheetName());
			}

			// Read data
			languageProperties = new ArrayList<>();
			int rowIndex = -1;

			itemsToDo = sheet.getLastRowNum();
			itemsDone = 0;

			for (final Row row : sheet) {
				rowIndex++;
				if (rowIndex > 0) {
					String path = null;
					if (columnIndex_Path >= 0) {
						final Cell pathCell = row.getCell(columnIndex_Path);
						if (pathCell.getCellType() == CellType.STRING) {
							path = pathCell.getStringCellValue().trim();
						} else if (pathCell.getCellType() == CellType.BLANK) {
							path = "";
						} else {
							throw new Exception("Excel file contains invalid path value in sheet '" + sheet.getSheetName() + "' at row index " + rowIndex + " and column index " + columnIndex_Keys);
						}
					}

					String key;
					final Cell keyCell = row.getCell(columnIndex_Keys);
					if (keyCell.getCellType() == CellType.STRING) {
						key = keyCell.getStringCellValue().trim();
					} else {
						throw new Exception("Excel file contains invalid key value in sheet '" + sheet.getSheetName() + "' at row index " + rowIndex + " and column index " + columnIndex_Keys);
					}

					final LanguageProperty languageProperty = new LanguageProperty(path, key);

					if (columnIndex_Index >= 0) {
						final Cell indexCell = row.getCell(columnIndex_Index);
						try {
							if (indexCell.getCellType() == CellType.NUMERIC) {
								languageProperty.setOriginalIndex(Double.valueOf(indexCell.getNumericCellValue()).intValue());
							} else if (indexCell.getCellType() == CellType.STRING) {
								languageProperty.setOriginalIndex(Integer.parseInt(indexCell.getStringCellValue().trim()));
							} else if (indexCell.getCellType() == CellType.BLANK) {
								languageProperty.setOriginalIndex(0);
							} else {
								throw new Exception("Excel file contains invalid index data type in sheet '" + sheet.getSheetName() + "' at row index " + rowIndex + " and column index " + columnIndex_Index);
							}
						} catch (final Exception e) {
							throw new Exception("Excel file contains invalid index value in sheet '" + sheet.getSheetName() + "' at row index " + rowIndex + " and column index " + columnIndex_Index, e);
						}
					} else {
						languageProperty.setOriginalIndex(rowIndex);
					}

					if (columnIndex_Comment >= 0) {
						final Cell commentCell = row.getCell(columnIndex_Comment);
						try {
							if (commentCell.getCellType() == CellType.NUMERIC) {
								languageProperty.setComment(Double.valueOf(commentCell.getNumericCellValue()).toString());
							} else if (commentCell.getCellType() == CellType.STRING) {
								languageProperty.setComment(commentCell.getStringCellValue());
							} else if (commentCell.getCellType() == CellType.BLANK) {
								languageProperty.setComment(null);
							} else {
								throw new Exception("Excel file contains invalid comment data type in sheet '" + sheet.getSheetName() + "' at row index " + rowIndex + " and column index " + columnIndex_Index);
							}
						} catch (final Exception e) {
							throw new Exception("Excel file contains invalid comment value in sheet '" + sheet.getSheetName() + "' at row index " + rowIndex + " and column index " + columnIndex_Index, e);
						}
					} else {
						languageProperty.setComment(null);
					}

					for (final Entry<Integer, String> entry : languageColumnHeaders.entrySet()) {
						final Cell valueCell = row.getCell(entry.getKey());
						if (valueCell.getCellType() == CellType.STRING) {
							languageProperty.setLanguageValue(entry.getValue(), valueCell.getStringCellValue());
						} else if (valueCell.getCellType() == CellType.BLANK) {
							languageProperty.setLanguageValue(entry.getValue(), null);
						} else {
							throw new Exception("Excel file contains invalid data type in sheet '" + sheet.getSheetName() + "' at row index " + rowIndex + " and column index " + entry.getKey());
						}
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
