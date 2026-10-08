package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Helper methods for Excel (xlsx) files
 */
public class ExcelHelper {
	private ExcelHelper() {
		// Utility class, no instances
	}

	/**
	 * Reads the names of all sheets of an Excel file.
	 *
	 * @param importExcelFile
	 *            Excel file (xlsx) to read
	 * @return names of the sheets in their order within the file
	 * @throws Exception
	 *             if the file cannot be read or is no valid xlsx file
	 */
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
}
