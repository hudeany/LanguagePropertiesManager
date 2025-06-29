package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.soderer.utilities.PropertiesWriter;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.collection.IndexedLinkedHashMap;
import de.soderer.utilities.collection.MapUtilities;

public class LanguagePropertiesFileSetWriter {
	public static final String LANGUAGE_SIGN_DEFAULT = "default";
	public static final String POPERTIES_FILE_EXTENSION = "properties";

	public static void write(final IndexedLinkedHashMap<String, LanguageProperty> languagePropertiesByKey, final File propertiesDirectory, final String propertySetName, final boolean encodeJavaEncoding) throws Exception {
		if (!propertiesDirectory.exists()) {
			throw new Exception("Properties directory '" + propertiesDirectory + "' does not exist");
		} else if (!propertiesDirectory.isDirectory()) {
			throw new Exception("Properties directory '" + propertiesDirectory + "' is not a directory");
		}

		final List<String> availableLanguageSigns = Utilities.sortButPutItemsFirst(getAvailableLanguageSignsOfProperties(languagePropertiesByKey), "default");
		for (final String languageSign : availableLanguageSigns) {
			String filename;
			if ("default".equals(languageSign)) {
				filename = propertySetName + ".properties";
			} else {
				filename = propertySetName + "_" + languageSign + ".properties";
			}
			try (PropertiesWriter propertiesWriter = new PropertiesWriter(new FileOutputStream(new File(propertiesDirectory, filename)))) {
				propertiesWriter.setEncodeJavaEncoding(encodeJavaEncoding);
				final IndexedLinkedHashMap<String, LanguageProperty> sortedLanguagePropertiesByKey = MapUtilities.sortEntries(languagePropertiesByKey, new LanguageProperty.OriginalIndexComparator(true));
				for (final Entry<String, LanguageProperty> entry : sortedLanguagePropertiesByKey.entrySet()) {
					final String languageValue = entry.getValue().getLanguageValue(languageSign);
					if (languageValue != null) {
						propertiesWriter.writeProperty(entry.getValue().getKey(), languageValue);
					}
				}
			}
		}
	}

	/**
	 * Get language sign of a language properties filename
	 */
	public static String getLanguageSignOfFilename(final String fileName) {
		String fileNamePart = fileName.replace("\\", "/");
		final int lastFileSeparator = fileNamePart.lastIndexOf("/");
		if (lastFileSeparator >= 0) {
			fileNamePart = fileNamePart.substring(lastFileSeparator + 1);
		}
		final int lastPoint = fileNamePart.lastIndexOf(".");
		if (lastPoint >= 0) {
			fileNamePart = fileNamePart.substring(0, lastPoint);
		}
		final String[] fileNameParts = fileNamePart.split("_");
		if (fileNameParts.length == 2) {
			return fileNameParts[1];
		} else if (fileNameParts.length >= 3) {
			return fileNameParts[fileNameParts.length - 2] + "_" + fileNameParts[fileNameParts.length - 1];
		} else {
			return LANGUAGE_SIGN_DEFAULT;
		}
	}

	public static Set<String> getAvailableLanguageSignsOfProperties(final Map<String, LanguageProperty> languagePropertiesByKey) {
		final Set<String> availableLanguageSigns = new HashSet<>();
		for (final Entry<String, LanguageProperty> itemKeyEntry : languagePropertiesByKey.entrySet()) {
			availableLanguageSigns.addAll(itemKeyEntry.getValue().getAvailableLanguageSigns());
		}
		return availableLanguageSigns;
	}
}
