package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.soderer.utilities.PropertiesReader;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.WildcardFilenameFilter;
import de.soderer.utilities.collection.IndexedLinkedHashMap;

public class LanguagePropertiesFileSetReader {
	public static final String LANGUAGE_SIGN_DEFAULT = "default";
	public static final String POPERTIES_FILE_EXTENSION = "properties";

	/**
	 * Reads a set of language properties files into a map with values of item name strings as keys, where each of them is referencing a map of language signs and their value string for display
	 * @param basePropertiesFilePath
	 * @return
	 * @throws Exception
	 */
	public static IndexedLinkedHashMap<String, LanguageProperty> read(final File propertiesDirectory, final String propertySetName, final boolean readKeysCaseInsensitive, final boolean decodeJavaEncoding) throws Exception {
		if (!propertiesDirectory.exists()) {
			throw new Exception("Properties directory '" + propertiesDirectory + "' does not exist");
		} else if (!propertiesDirectory.isDirectory()) {
			throw new Exception("Properties directory '" + propertiesDirectory + "' is not a directory");
		}

		final IndexedLinkedHashMap<String, LanguageProperty> languagePropertiesByKey = new IndexedLinkedHashMap<>();

		final FilenameFilter fileFilter = new WildcardFilenameFilter(propertySetName + "*." + POPERTIES_FILE_EXTENSION);

		for (final File propertyFile : propertiesDirectory.listFiles(fileFilter)) {
			final String languageSign = getLanguageSignOfFilename(propertyFile.getName());
			if (languageSign != null) {
				try (PropertiesReader propertiesReader = new PropertiesReader(new FileInputStream(propertyFile))) {
					propertiesReader.setReadKeysCaseInsensitive(readKeysCaseInsensitive);
					propertiesReader.setDecodeJavaEncoding(decodeJavaEncoding);
					final Map<String, String> languageEntries = propertiesReader.read();
					for (final Entry<String, String> entry : languageEntries.entrySet()) {
						final LanguageProperty languagePropertyForKey = languagePropertiesByKey.computeIfAbsent(entry.getKey(), k -> new LanguageProperty(k).setOriginalIndex(languagePropertiesByKey.size() + 1));
						if (Utilities.isNotEmpty(propertiesReader.getComments().get(entry.getKey())) && Utilities.isEmpty(languagePropertyForKey.getComment())) {
							languagePropertyForKey.setComment(propertiesReader.getComments().get(entry.getKey()));
						}
						languagePropertyForKey.setLanguageValue(languageSign, entry.getValue());
					}
				} catch (final Exception e) {
					throw new Exception("Error when reading file: " + propertyFile.getAbsolutePath(), e);
				}
			}
		}

		return languagePropertiesByKey;
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
