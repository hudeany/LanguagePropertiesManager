package de.soderer.languagepropertiesmanager.storage;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import de.soderer.utilities.collection.CaseInsensitiveOrderedMap;

public class PropertiesLanguageFileReader {
	private static final String COMMENT_INDICATOR = "#"; 
	
	private Map<String, String> entries;
	private Set<String> multipleKeys = new HashSet<String>();
	
	public void load(InputStream inputStream, boolean readKeysCaseInsensitive) throws Exception {
		if (readKeysCaseInsensitive)
			entries = new CaseInsensitiveOrderedMap<String>();
		else entries = new LinkedHashMap<String, String>();
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		String line = null;
		int lineCount = 0;
		while ((line = reader.readLine()) != null) {
			lineCount++;
			
			if (StringUtils.isNotBlank(line) && !line.startsWith(COMMENT_INDICATOR)) {
				// Separators are '=' and ':' or the first blank.
				// Blanks around '=' and ':' are ignored.
				int posSeparator = -1;
				boolean blankSeparatorFound = false;
				for (int i = 0; i < line.length(); i++) {
					char letter = line.charAt(i);
					if (letter == '=' || letter == ':') {
						posSeparator = i;
						break;
					} else if (letter == ' ') {
						blankSeparatorFound = true;
					} else if (blankSeparatorFound) {
						posSeparator = i - 1;
						break;
					}
				}
				if (posSeparator < 0)
					throw new Exception("Invalid content at line " + lineCount);
				
				// get key and value
				String key = line.substring(0, posSeparator).trim();
				String value = line.substring(posSeparator + 1).trim();
				if (StringUtils.isEmpty(value))
					value = " ";
				
				// Repair non escaped characters for later correct storage
				key = StringEscapeUtils.escapeJava(StringEscapeUtils.unescapeJava(key));
				value = StringEscapeUtils.escapeJava(StringEscapeUtils.unescapeJava(value));
				
				if (key.length() <= 0)
					throw new Exception("Invalid key at line " + lineCount);
				else if (key.length() <= 0)
					throw new Exception("Invalid value at line " + lineCount);
				
				// add value
				if (entries.containsKey(key)) {
					multipleKeys.add(key);
				} else {
					entries.put(key, value);
				}
			}
		}
	}
	
	public Map<String, String> getEntries() {
		return entries;
	}

	public Set<String> getMultipleKeys() {
		return multipleKeys;
	}
	
	/**
	 * Get matching section for key
	 * 
	 * @param key
	 * @return
	 */
	public static String getSectionOfKey(String key) {
		String section = "";
		int sectionEnd = key.lastIndexOf(".");
		if (sectionEnd >= 0)
			section = key.substring(0, sectionEnd);
		return section;
	}
	
	/**
	 * Get language sign for a filename
	 * 
	 * @param fileName
	 * @return
	 */
	public static String getLanguageSignOfFilename(String fileName) {
		String fileNamePart = fileName.replace("\\", "/");
		int lastFileSeparator = fileNamePart.lastIndexOf("/");
		if (lastFileSeparator >= 0) {
			fileNamePart = fileNamePart.substring(lastFileSeparator + 1);
		}
		int lastPoint = fileNamePart.lastIndexOf(".");
		if (lastPoint >= 0) {
			fileNamePart = fileNamePart.substring(0, lastPoint);
		}
		String [] fileNameParts = fileNamePart.split("_");
		if (fileNameParts.length == 2)
			return fileNameParts[1];
		else if (fileNameParts.length >= 3)
			return fileNameParts[fileNameParts.length - 2] + "_" + fileNameParts[fileNameParts.length - 1];
		else return PropertiesStorage.LANGUAGE_SIGN_DEFAULT;
	}
}
