package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelHelper {
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
