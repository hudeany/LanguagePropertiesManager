package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import de.soderer.utilities.Utilities;
import de.soderer.utilities.WildcardFilenameFilter;
import de.soderer.utilities.collection.IndexedLinkedHashMap;
import de.soderer.utilities.collection.MapUtilities;
import de.soderer.utilities.collection.UniqueFifoQueuedList;

public class PropertiesHelper {
	public static final String PROPERTIES_FILEEXTENSION = "properties";
	public static final String PROPERTIES_FILEPATTERN = "*." + PROPERTIES_FILEEXTENSION;

	private static final Pattern LANGUAGEANDCOUNTRY_FILENAMEPATTERN = Pattern.compile("^.+(_[a-zA-Z]{2}){2}$");
	private static final Pattern LANGUAGE_FILENAMEPATTERN = Pattern.compile("^.+_[a-zA-Z]{2}$");

	private static final Pattern LANGUAGEANDCOUNTRYPATTERN = Pattern.compile("^(_[a-zA-Z]{2}){2}$");
	private static final Pattern LANGUAGEPATTERN = Pattern.compile("^[a-zA-Z]{2}$");

	//	public boolean cleanUp(final boolean repairpunctuation) throws Exception {
	//		boolean dataWasChanged = false;
	//
	//		for (final LanguageProperty property : properties.values()) {
	//			// Store original values for later change check
	//			final HashMap<String, String> originalData = new HashMap<>();
	//			for (final String sign : getLanguageSigns()) {
	//				originalData.put(sign, property.getLanguageValue(sign));
	//			}
	//
	//			for (final String sign : getLanguageSigns()) {
	//				String value = property.getLanguageValue(sign);
	//				if (value != null) {
	//					// clear blank before and after "<br>"
	//					value = value.replace(" <br>", "<br>").replace("<br> ", "<br>");
	//					// remove "<br>" at end
	//					if (value.endsWith("<br>")) {
	//						value = value.substring(0, value.length() - 4).trim();
	//					}
	//					// remove "<br />" at end
	//					if (value.endsWith("<br />")) {
	//						value = value.substring(0, value.length() - 6).trim();
	//					}
	//					property.setLanguageValue(sign, value);
	//				}
	//
	//				// Clear empty values
	//				if (Utilities.isEmpty(property.getLanguageValue(sign))) {
	//					property.removeLanguageValue(sign);
	//				}
	//			}
	//
	//			if (repairpunctuation) {
	//				// Check exclamationmark, questionmark and fullstop unification
	//				boolean hasExclamationEnd = false;
	//				boolean hasQuestionEnd = false;
	//				boolean hasFullstopEnd = false;
	//				boolean hasColonEnd = false;
	//				for (final String sign : getLanguageSigns()) {
	//					final String value = property.getLanguageValue(sign);
	//					if (value != null) {
	//						if (!sign.equalsIgnoreCase("zh")) {
	//							if (value.endsWith("!")) {
	//								hasExclamationEnd = true;
	//							} else if (value.endsWith("?")) {
	//								hasQuestionEnd = true;
	//							} else if (value.endsWith(":")) {
	//								hasColonEnd = true;
	//							} else if (value.endsWith(".")) {
	//								hasFullstopEnd = true;
	//							}
	//						} else {
	//							if (value.endsWith("\\uFF01")) { // "!"
	//								hasExclamationEnd = true;
	//							} else if (value.endsWith("\\uFF1F")) { // "?"
	//								hasQuestionEnd = true;
	//							} else if (value.endsWith("\\uFF1A")) { // ":"
	//								hasColonEnd = true;
	//							} else if (value.endsWith("\\u3002")) { // "."
	//								hasFullstopEnd = true;
	//							}
	//						}
	//					}
	//				}
	//				for (final String sign : getLanguageSigns()) {
	//					String value = property.getLanguageValue(sign);
	//					if (value != null) {
	//						if (!sign.equalsIgnoreCase("zh")) {
	//							if (hasExclamationEnd) {
	//								if (value.endsWith("?") || value.endsWith(":") || value.endsWith(".")) {
	//									value = value.substring(0, value.length() - 1);
	//								}
	//								if (!value.endsWith("!")) {
	//									value += "!";
	//								}
	//							} else if (hasQuestionEnd) {
	//								if (value.endsWith(":") || value.endsWith(".")) {
	//									value = value.substring(0, value.length() - 1);
	//								}
	//								if (!value.endsWith("?")) {
	//									value += "?";
	//								}
	//							} else if (hasColonEnd) {
	//								if (value.endsWith(".")) {
	//									value = value.substring(0, value.length() - 1);
	//								}
	//								if (!value.endsWith(":")) {
	//									value += ":";
	//								}
	//							} else if (hasFullstopEnd) {
	//								if (!value.endsWith(".")) {
	//									value += ".";
	//								}
	//							}
	//						} else {
	//							if (hasExclamationEnd) {
	//								if (value.endsWith("\\uFF1F") || value.endsWith("\\uFF1A") || value.endsWith("\\u3002")) {
	//									value = value.substring(0, value.length() - 6);
	//								}
	//								if (!value.endsWith("\\uFF01")) {
	//									value += "\\uFF01";
	//								}
	//							} else if (hasQuestionEnd) {
	//								if (value.endsWith("\\uFF1A") || value.endsWith("\\u3002")) {
	//									value = value.substring(0, value.length() - 6);
	//								}
	//								if (!value.endsWith("\\uFF1F")) {
	//									value += "\\uFF1F";
	//								}
	//							} else if (hasQuestionEnd) {
	//								if (value.endsWith("\\u3002")) {
	//									value = value.substring(0, value.length() - 6);
	//								}
	//								if (!value.endsWith("\\uFF1A")) {
	//									value += "\\uFF1A";
	//								}
	//							} else if (hasFullstopEnd) {
	//								if (!value.endsWith("\\u3002")) {
	//									value += "\\u3002";
	//								}
	//							}
	//						}
	//
	//						property.setLanguageValue(sign, value);
	//					}
	//				}
	//			}
	//
	//			final List<String> signs = getLanguageSigns();
	//			signs.remove(LANGUAGE_SIGN_DEFAULT);
	//			for (final String sign : signs) {
	//				if (property.getLanguageValue(LANGUAGE_SIGN_DEFAULT) != null
	//						&& Utilities.isNotEmpty(property.getLanguageValue(sign))
	//						&& property.getLanguageValue(LANGUAGE_SIGN_DEFAULT).equals(property.getLanguageValue(sign))) {
	//					property.removeLanguageValue(sign);
	//				} else if (sign.contains("_")) {
	//					final String firstSignPart = sign.substring(0, sign.indexOf("_"));
	//					if (property.getLanguageValue(firstSignPart) != null
	//							&& Utilities.isNotEmpty(property.getLanguageValue(sign))
	//							&& property.getLanguageValue(firstSignPart).equals(property.getLanguageValue(sign))) {
	//						property.removeLanguageValue(sign);
	//					}
	//				}
	//			}
	//
	//			// Languagespecific changes
	//			for (final String sign : getLanguageSigns()) {
	//				if (sign.equalsIgnoreCase("es") && property.getLanguageValue(sign) != null) {
	//					String value = property.getLanguageValue(sign);
	//					if (value.endsWith("!") && !value.startsWith("\\u00A1")) {
	//						value = "\\u00A1" + value;
	//					} else if (value.endsWith("?") && !value.startsWith("\\u00BF")) {
	//						value = "\\u00BF" + value;
	//					}
	//					property.setLanguageValue(sign, value);
	//				}
	//
	//				if (sign.equals("fr") && property.getLanguageValue(sign) != null) {
	//					String value = property.getLanguageValue(sign);
	//					value = value.replace("!", " !").replace("  !", " !").replace("?", " ?").replace("  ?", " ?").replace(":", " :").replace("  :", " :");
	//					property.setLanguageValue(sign, value);
	//				} else if (!sign.equalsIgnoreCase("fr") && property.getLanguageValue(sign) != null) {
	//					String value = property.getLanguageValue(sign);
	//					value = value.replace(" !", "!").replace(" ?", "?").replace(" :", ":");
	//					property.setLanguageValue(sign, value);
	//				}
	//			}
	//
	//			// Change check
	//			for (final String sign : getLanguageSigns()) {
	//				if (originalData.get(sign) == null && property.getLanguageValue(sign) != null) {
	//					dataWasChanged = true;
	//				} else if (originalData.get(sign) != null && !originalData.get(sign).equals(property.getLanguageValue(sign))) {
	//					dataWasChanged = true;
	//				}
	//			}
	//		}
	//
	//		return dataWasChanged;
	//	}

	public static List<String> readAvailablePropertySets(final String directoryPath) {
		final List<String> returnList = new UniqueFifoQueuedList<>(10);
		for (final File propertyFile : new File(directoryPath).listFiles((FileFilter) new WildcardFilenameFilter(PROPERTIES_FILEPATTERN))) {
			final String fileName = propertyFile.getName().substring(0, propertyFile.getName().lastIndexOf('.'));
			if (LANGUAGEANDCOUNTRY_FILENAMEPATTERN.matcher(fileName).matches()) {
				returnList.add(fileName.substring(0, fileName.length() - 6));
			} else if (LANGUAGE_FILENAMEPATTERN.matcher(fileName).matches()) {
				returnList.add(fileName.substring(0, fileName.length() - 3));
			} else {
				returnList.add(fileName);
			}
		}
		return returnList;
	}

	public static void exportToExcel(final IndexedLinkedHashMap<String, LanguageProperty> languagePropertiesByKey, final File exportExcelFile, final String languagePropertySetName, List<String> languageSigns) throws Exception {
		final IndexedLinkedHashMap<String, LanguageProperty> sortedLanguagePropertiesByKey = MapUtilities.sortEntries(languagePropertiesByKey, new LanguageProperty.OriginalIndexComparator(true));

		boolean commentsFound = false;
		for (final Entry<String, LanguageProperty> entry : languagePropertiesByKey.entrySet()) {
			if (Utilities.isNotEmpty(entry.getValue().getComment())) {
				commentsFound = true;
				break;
			}
		}

		try (final XSSFWorkbook workbook = new XSSFWorkbook()) {
			try (final FileOutputStream outputStream = new FileOutputStream(exportExcelFile)) {
				final XSSFSheet sheet = workbook.createSheet(languagePropertySetName);

				final XSSFCellStyle cellStyle = workbook.createCellStyle();
				cellStyle.setWrapText(true);

				// Write header row
				final Row headerRow = sheet.createRow(0);
				int headerColumnIndex = 0;

				Cell headerCell = headerRow.createCell(headerColumnIndex++);
				headerCell.setCellValue("Index");

				headerCell = headerRow.createCell(headerColumnIndex++);
				headerCell.setCellValue("Key");

				if (commentsFound) {
					headerCell = headerRow.createCell(headerColumnIndex++);
					headerCell.setCellValue("Comment");
				}

				if (languageSigns == null) {
					languageSigns = new ArrayList<>(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languagePropertiesByKey));
				}
				final List<String> languageSignsInOutputOrder = Utilities.sortButPutItemsFirst(languageSigns, "default");

				for (final String languageSign : languageSignsInOutputOrder) {
					headerCell = headerRow.createCell(headerColumnIndex++);
					headerCell.setCellValue(languageSign);
				}

				// Write data rows
				int dataRowIndex = 1;
				for (final Entry<String, LanguageProperty> languageproperty : sortedLanguagePropertiesByKey.entrySet()) {
					int dataColumnIndex = 0;
					final Row dataRow = sheet.createRow(dataRowIndex++);

					Cell dataCell = dataRow.createCell(dataColumnIndex++);
					dataCell.setCellValue(languageproperty.getValue().getOriginalIndex());

					dataCell = dataRow.createCell(dataColumnIndex++);
					dataCell.setCellValue(languageproperty.getValue().getKey());

					if (commentsFound) {
						dataCell = dataRow.createCell(dataColumnIndex++);
						dataCell.setCellValue(languageproperty.getValue().getComment() == null ? "" : languageproperty.getValue().getComment());
					}

					for (final String languageSign : languageSignsInOutputOrder) {
						dataCell = dataRow.createCell(dataColumnIndex++);
						dataCell.setCellStyle(cellStyle);
						final String languageValue = languageproperty.getValue().getLanguageValue(languageSign);
						if (languageValue != null) {
							dataCell.setCellValue(languageValue);
						} else {
							dataCell.setCellValue("");
						}
					}
				}

				// Resize columns for optimal width
				for (int i = 0; i < languageSignsInOutputOrder.size() + 2; i++) {
					sheet.autoSizeColumn(i);
				}

				workbook.write(outputStream);
			}
		}
	}

	public static List<String> getExcelSheetNames(final File importExcelFile) throws Exception {
		try (FileInputStream inputStream = new FileInputStream(importExcelFile);
				final XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
			final List<String> sheetNames = new ArrayList<>();
			for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
				sheetNames.add(workbook.getSheetAt(i).getSheetName());
			}
			return sheetNames;
		}
	}

	public static IndexedLinkedHashMap<String, LanguageProperty> importFromExcel(final File importExcelFile, final String languagePropertySetName) throws Exception {
		try (FileInputStream inputStream = new FileInputStream(importExcelFile);
				final XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
			XSSFSheet sheet = null;
			if (workbook.getNumberOfSheets() == 1) {
				sheet = workbook.getSheetAt(0);
			} else {
				for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
					sheet = workbook.getSheetAt(i);
					if (languagePropertySetName.equals(sheet.getSheetName())) {
						break;
					} else {
						sheet = null;
					}
				}
			}
			if (sheet == null) {
				throw new Exception("Excel file does not contain expected sheet: " + languagePropertySetName);
			}

			// Read headers
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
					if ("key".equalsIgnoreCase(cellValue.trim())
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
			final IndexedLinkedHashMap<String, LanguageProperty> languagePropertiesByKey = new IndexedLinkedHashMap<>();
			int rowIndex = -1;
			for (final Row row : sheet) {
				rowIndex++;
				if (rowIndex > 0) {
					String key;
					final Cell keyCell = row.getCell(columnIndex_Keys);
					if (keyCell.getCellType() == CellType.STRING) {
						key = keyCell.getStringCellValue().trim();
					} else {
						throw new Exception("Excel file contains invalid key value in sheet '" + sheet.getSheetName() + "' at row index " + rowIndex + " and column index " + columnIndex_Keys);
					}

					final LanguageProperty languageProperty = new LanguageProperty(key);

					if (columnIndex_Index >= 0) {
						final Cell indexCell = row.getCell(columnIndex_Index);
						try {
							if (indexCell.getCellType() == CellType.NUMERIC) {
								languageProperty.setOriginalIndex(Double.valueOf(indexCell.getNumericCellValue()).intValue());
							} else if (indexCell.getCellType() == CellType.STRING) {
								languageProperty.setOriginalIndex(Integer.parseInt(indexCell.getStringCellValue().trim()));
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
						} else {
							throw new Exception("Excel file contains invalid data type in sheet '" + sheet.getSheetName() + "' at row index " + rowIndex + " and column index " + entry.getKey());
						}
					}

					languagePropertiesByKey.put(key, languageProperty);
				}
			}

			return languagePropertiesByKey;
		}
	}
}
