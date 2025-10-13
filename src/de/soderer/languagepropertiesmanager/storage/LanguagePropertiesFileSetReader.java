package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import de.soderer.utilities.PropertiesReader;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.WildcardFilenameFilter;

public class LanguagePropertiesFileSetReader {
	public static final String LANGUAGE_SIGN_DEFAULT = "default";
	public static final String DEFAULT_PROPERTIES_FILE_EXTENSION = ".properties";

	/**
	 * Reads a set of language properties files into a map with values of item name strings as keys, where each of them is referencing a map of language signs and their value string for display
	 * @param basePropertiesFilePath
	 * @return
	 * @throws Exception
	 */
	public static List<LanguageProperty> read(final File propertiesDirectory, final String propertySetName, final boolean readKeysCaseInsensitive) throws Exception {
		return read(propertiesDirectory, propertySetName, DEFAULT_PROPERTIES_FILE_EXTENSION, readKeysCaseInsensitive);
	}

	/**
	 * Reads a set of language properties files into a map with values of item name strings as keys, where each of them is referencing a map of language signs and their value string for display
	 * @param basePropertiesFilePath
	 * @return
	 * @throws Exception
	 */
	public static List<LanguageProperty> read(final File propertiesDirectory, final String propertySetName, final String propertiesFileExtension, final boolean readKeysCaseInsensitive) throws Exception {
		if (!propertiesDirectory.exists()) {
			throw new Exception("Properties directory '" + propertiesDirectory + "' does not exist");
		} else if (!propertiesDirectory.isDirectory()) {
			throw new Exception("Properties directory '" + propertiesDirectory + "' is not a directory");
		}

		final List<LanguageProperty> languageProperties = new ArrayList<>();

		final FilenameFilter fileFilter = new WildcardFilenameFilter(propertySetName + "*" + propertiesFileExtension);

		for (final File propertyFile : propertiesDirectory.listFiles(fileFilter)) {
			final String languageSign = getLanguageSignOfFilename(propertyFile.getName());
			if (languageSign != null) {
				try (PropertiesReader propertiesReader = new PropertiesReader(new FileInputStream(propertyFile))) {
					propertiesReader.setReadKeysCaseInsensitive(readKeysCaseInsensitive);
					final Map<String, String> languageEntries = propertiesReader.read();
					final String path = Utilities.replaceUsersHomeByTilde(propertyFile.getAbsolutePath().replace("_" + languageSign, "").replace(propertiesFileExtension, ""));
					for (final Entry<String, String> entry : languageEntries.entrySet()) {
						LanguageProperty property = null;
						for (final LanguageProperty languageProperty : languageProperties) {
							if (languageProperty.getPath().equals(path) && languageProperty.getKey().equals(entry.getKey())) {
								property = languageProperty;
								break;
							}
						}
						if (property == null) {
							property = new LanguageProperty(Utilities.replaceUsersHomeByTilde(new File(propertiesDirectory, propertySetName).getAbsolutePath()), entry.getKey());
							property.setOriginalIndex(languageProperties.size() + 1);
							languageProperties.add(property);
						}
						if (Utilities.isNotEmpty(propertiesReader.getComments().get(entry.getKey())) && Utilities.isEmpty(property.getComment())) {
							property.setComment(propertiesReader.getComments().get(entry.getKey()));
						}
						property.setLanguageValue(languageSign, entry.getValue());
					}
				} catch (final Exception e) {
					throw new Exception("Error when reading file: " + propertyFile.getAbsolutePath(), e);
				}
			}
		}

		return languageProperties;
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

	public static Set<String> getAvailableLanguageSignsOfProperties(final List<LanguageProperty> languageProperties) {
		return languageProperties.stream().map(o -> o.getAvailableLanguageSigns()).flatMap(Set::stream).collect(Collectors.toSet());
	}

	public static List<String> getLanguagePropertiesSetNames(final List<LanguageProperty> languageProperties) {
		final Set<String> languagePropertiesSetPaths = new HashSet<>();
		for (final LanguageProperty languageProperty : languageProperties) {
			languagePropertiesSetPaths.add(languageProperty.getPath());
		}

		final List<String> languagePropertiesSetNames = new ArrayList<>();
		for (final String languagePropertiesSetPath : languagePropertiesSetPaths) {
			final String filename = new File(languagePropertiesSetPath).getName();
			languagePropertiesSetNames.add(filename);
		}

		return languagePropertiesSetNames;
	}
}
