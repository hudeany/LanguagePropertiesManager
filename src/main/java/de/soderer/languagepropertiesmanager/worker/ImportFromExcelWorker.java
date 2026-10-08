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

import de.soderer.languagepropertiesmanager.LanguagePropertiesException;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

/**
 * Imports language properties from an Excel file (xlsx) with exactly one
 * sheet, as written by {@link ExportToExcelWorker}. A key column is mandatory,
 * path, index, comment and language columns ("default", "de", "de_AT", ...)
 * are detected by their header.
 */
public class ImportFromExcelWorker extends WorkerSimple<Boolean> {
	private static final Pattern LANGUAGEANDCOUNTRYPATTERN = Pattern.compile("^[a-zA-Z]{2}_[a-zA-Z]{2}$");
	private static final Pattern LANGUAGEPATTERN = Pattern.compile("^[a-zA-Z]{2}$");

	private final File importExcelFile;

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
	 * @param importExcelFile
	 *            Excel file to read
	 */
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
				throw new LanguagePropertiesException("Excel file contains more than 1 expected sheet");
			} else {
				throw new LanguagePropertiesException("Excel file does not contain expected sheet");
			}

			// Read headers
			int columnIndex_Path = -1;
			int columnIndex_Keys = -1;
			int columnIndex_Index = -1;
			int columnIndex_Comment = -1;
			final Map<Integer, String> languageColumnHeaders = new HashMap<>();
			final Row headerRow = sheet.getRow(0);
			if (headerRow == null) {
				throw new LanguagePropertiesException("Excel file does not contain a header row in sheet: " + sheet.getSheetName());
			}
			for (final Cell headerCell : headerRow) {
				final int headerColumnIndex = headerCell.getColumnIndex();
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
					} else if (("comment".equalsIgnoreCase(cellValue.trim())
							|| "kommentar".equalsIgnoreCase(cellValue.trim())) && !ignoreComments) {
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
				throw new LanguagePropertiesException("Excel file does not contain mandatory column for keys in sheet: " + sheet.getSheetName());
			}

			final boolean hasDefaultLanguageColumn = languageColumnHeaders.containsValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);

			// Read data
			languageProperties = new ArrayList<>();
			int rowIndex;

			itemsToDo = sheet.getPhysicalNumberOfRows();
			itemsDone = 0;

			for (final Row row : sheet) {
				if (cancel) {
					break;
				}

				// Empty rows are not contained in the iteration, so the real row number is taken from the row itself
				rowIndex = row.getRowNum();
				if (rowIndex > 0) {
					String path;
					if (columnIndex_Path >= 0) {
						final Cell pathCell = row.getCell(columnIndex_Path);
						if (pathCell == null) {
							path = "";
						} else if (pathCell.getCellType() == CellType.STRING) {
							path = pathCell.getStringCellValue().trim();
						} else if (pathCell.getCellType() == CellType.BLANK) {
							path = "";
						} else {
							throw new LanguagePropertiesException("Excel file contains invalid path value in sheet '" + sheet.getSheetName() + "' at row " + (rowIndex + 1) + " and column " + (columnIndex_Path + 1));
						}
					} else {
						path = "";
					}

					String key;
					final Cell keyCell = row.getCell(columnIndex_Keys);
					if (keyCell == null || keyCell.getCellType() == CellType.BLANK) {
						key = null;
					} else if (keyCell.getCellType() == CellType.STRING) {
						key = keyCell.getStringCellValue().trim();
					} else if (keyCell.getCellType() == CellType.NUMERIC) {
						final Double value = Double.valueOf(keyCell.getNumericCellValue());
						if ((value % 1) == 0) {
							key = Long.toString(value.longValue());
						} else {
							key = value.toString();
						}
					} else {
						throw new LanguagePropertiesException("Excel file contains invalid key value in sheet '" + sheet.getSheetName() + "' at row " + (rowIndex + 1) + " and column " + (columnIndex_Keys + 1));
					}

					if (Utilities.isNotBlank(key)) {
						final LanguageProperty languageProperty = new LanguageProperty(path, key);

						if (columnIndex_Index >= 0) {
							final Cell indexCell = row.getCell(columnIndex_Index);
							try {
								if (indexCell == null) {
									languageProperty.setOriginalIndex(0);
								} else if (indexCell.getCellType() == CellType.NUMERIC) {
									languageProperty.setOriginalIndex(Double.valueOf(indexCell.getNumericCellValue()).intValue());
								} else if (indexCell.getCellType() == CellType.STRING) {
									languageProperty.setOriginalIndex(Integer.parseInt(indexCell.getStringCellValue().trim()));
								} else if (indexCell.getCellType() == CellType.BLANK) {
									languageProperty.setOriginalIndex(0);
								} else {
									throw new LanguagePropertiesException("Excel file contains invalid index data type '" + indexCell.getCellType().name() + "' in sheet '" + sheet.getSheetName() + "' at row " + (rowIndex + 1) + " and column " + (columnIndex_Index + 1));
								}
							} catch (final Exception e) {
								throw new LanguagePropertiesException("Excel file contains invalid index value in sheet '" + sheet.getSheetName() + "' at row " + (rowIndex + 1) + " and column " + (columnIndex_Index + 1), e);
							}
						} else {
							languageProperty.setOriginalIndex(rowIndex);
						}

						if (columnIndex_Comment >= 0) {
							final Cell commentCell = row.getCell(columnIndex_Comment);
							try {
								if (commentCell == null) {
									languageProperty.setComment(null);
								} else if (commentCell.getCellType() == CellType.NUMERIC) {
									final Double value = Double.valueOf(commentCell.getNumericCellValue());
									if ((value % 1) == 0) {
										languageProperty.setComment(Long.toString(value.longValue()));
									} else {
										languageProperty.setComment(value.toString());
									}
								} else if (commentCell.getCellType() == CellType.STRING) {
									languageProperty.setComment(commentCell.getStringCellValue());
								} else if (commentCell.getCellType() == CellType.BLANK) {
									languageProperty.setComment(null);
								} else {
									throw new LanguagePropertiesException("Excel file contains invalid comment data type '" + commentCell.getCellType().name() + "' in sheet '" + sheet.getSheetName() + "' at row " + (rowIndex + 1) + " and column " + (columnIndex_Comment + 1));
								}
							} catch (final Exception e) {
								throw new LanguagePropertiesException("Excel file contains invalid comment value in sheet '" + sheet.getSheetName() + "' at row " + (rowIndex + 1) + " and column " + (columnIndex_Comment + 1), e);
							}
						} else {
							languageProperty.setComment(null);
						}

						for (final Entry<Integer, String> entry : languageColumnHeaders.entrySet()) {
							final Cell valueCell = row.getCell(entry.getKey());
							final String cellValue;
							if (valueCell == null || valueCell.getCellType() == CellType.BLANK) {
								cellValue = null;
							} else if (valueCell.getCellType() == CellType.STRING) {
								cellValue = valueCell.getStringCellValue();
							} else if (valueCell.getCellType() == CellType.NUMERIC) {
								final Double value = Double.valueOf(valueCell.getNumericCellValue());
								if ((value % 1) == 0) {
									cellValue = Long.toString(value.longValue());
								} else {
									cellValue = value.toString();
								}
							} else {
								throw new LanguagePropertiesException("Excel file contains invalid data type '" + valueCell.getCellType().name() + "' in sheet '" + sheet.getSheetName() + "' at row " + (rowIndex + 1) + " and column " + (entry.getKey() + 1));
							}
							// Excel can not reliably distinguish between "empty" and "missing":
							// With a default language column, empty cells are kept as "" in the default language and stored as missing (null) in all other languages.
							// Without a default language column, empty cells are kept as "" in all languages, so no key is lost.
							if (hasDefaultLanguageColumn) {
								languageProperty.setLanguageValue(entry.getValue(), LanguageProperty.toStorageValue(entry.getValue(), cellValue));
							} else {
								languageProperty.setLanguageValue(entry.getValue(), cellValue == null ? "" : cellValue);
							}
						}

						languageProperties.add(languageProperty);
					}
				}

				itemsDone++;
				signalProgress(false);
			}
		}

		if (cancel) {
			return false;
		}

		itemsDone = itemsToDo;
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
	 * "Multiple" for several set names, or the name of the Excel file (without
	 * extension) if the file has no paths.
	 *
	 * @return the set name
	 */
	public String getLanguagePropertiesSetName() {
		if (languagePropertiesSetNames == null || languagePropertiesSetNames.isEmpty()) {
			// Without paths a set name derived from the file is better than an empty name, which would create files like ".properties"
			final String fileName = importExcelFile.getName();
			return fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
		} else if (languagePropertiesSetNames.size() == 1) {
			return languagePropertiesSetNames.get(0);
		} else {
			return "Multiple";
		}
	}
}
