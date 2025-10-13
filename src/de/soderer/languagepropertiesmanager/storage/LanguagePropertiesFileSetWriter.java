package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.soderer.utilities.PropertiesWriter;
import de.soderer.utilities.Utilities;

public class LanguagePropertiesFileSetWriter {
	public static final String LANGUAGE_SIGN_DEFAULT = "default";
	public static final String DEFAULT_POPERTIES_FILE_EXTENSION = ".properties";

	public static void write(final List<LanguageProperty> languageProperties, final File directory, final String languagePropertySetName, final boolean extendAndKeepExistingProperties) throws Exception {
		write(languageProperties, directory, languagePropertySetName, extendAndKeepExistingProperties, DEFAULT_POPERTIES_FILE_EXTENSION);
	}

	public static void write(final List<LanguageProperty> languageProperties, final File directory, final String languagePropertySetName, final boolean extendAndKeepExistingProperties, final String propertiesFileExtension) throws Exception {
		final Set<String> languagePropertiesPaths = languageProperties.stream().map(o -> o.getPath()).collect(Collectors.toSet());
		final Comparator<LanguageProperty> compareByPathAndIndex = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getOriginalIndex);
		final List<LanguageProperty> sortedLanguageProperties = languageProperties.stream().sorted(compareByPathAndIndex).collect(Collectors.toList());
		for (final String nextLanguagePropertiesPath : languagePropertiesPaths) {
			final List<LanguageProperty> filteredLanguageProperties = sortedLanguageProperties.stream().filter(o -> o.getPath().equals(nextLanguagePropertiesPath)).collect(Collectors.toList());

			File propertiesDirectory;
			String propertySetName;
			final String languagePropertiesPath = Utilities.replaceUsersHome(nextLanguagePropertiesPath);
			if (Utilities.isNotBlank(languagePropertiesPath)) {
				try {
					Paths.get(languagePropertiesPath);
				} catch (@SuppressWarnings("unused") final InvalidPathException e) {
					throw new Exception("Properties directory path is not valid (" + nextLanguagePropertiesPath + ")");
				}

				propertiesDirectory = new File(languagePropertiesPath).getParentFile();
				propertySetName = new File(languagePropertiesPath).getName();
			} else {
				propertiesDirectory = directory;
				propertySetName = languagePropertySetName;
			}

			if (propertiesDirectory == null) {
				throw new Exception("Properties directory path '" + propertiesDirectory + "' is invalid (Path: " + nextLanguagePropertiesPath + ")");
			}

			if (!propertiesDirectory.exists()) {
				throw new Exception("Properties directory '" + propertiesDirectory + "' does not exist");
			} else if (!propertiesDirectory.isDirectory()) {
				throw new Exception("Properties directory '" + propertiesDirectory + "' is not a directory");
			}

			final List<String> availableLanguageSigns = Utilities.sortButPutItemsFirst(getAvailableLanguageSignsOfProperties(filteredLanguageProperties), LANGUAGE_SIGN_DEFAULT);
			if (extendAndKeepExistingProperties) {
				final List<LanguageProperty> existingProperties = LanguagePropertiesFileSetReader.read(propertiesDirectory, propertySetName, false);
				if (existingProperties != null) {
					for (final LanguageProperty existingProperty : existingProperties) {
						boolean foundExistingProperty = false;
						for (final LanguageProperty propertyToStore : filteredLanguageProperties) {
							if (propertyToStore.getKey().equals(existingProperty.getKey())) {
								foundExistingProperty = true;
								break;
							}
						}

						if (!foundExistingProperty) {
							filteredLanguageProperties.add(existingProperty);
						}
					}
				}
			}

			for (final String languageSign : availableLanguageSigns) {
				String filename;
				if (LANGUAGE_SIGN_DEFAULT.equals(languageSign)) {
					filename = propertySetName + propertiesFileExtension;
				} else {
					filename = propertySetName + "_" + languageSign + propertiesFileExtension;
				}

				try (PropertiesWriter propertiesWriter = new PropertiesWriter(new FileOutputStream(new File(propertiesDirectory, filename)))) {
					for (final LanguageProperty languageProperty : filteredLanguageProperties) {
						if (languageProperty.containsLanguage(languageSign) && languageProperty.getLanguageValue(languageSign) != null) {
							if (Utilities.isNotEmpty(languageProperty.getComment())) {
								propertiesWriter.writeComment(languageProperty.getComment());
							}
							propertiesWriter.writeProperty(languageProperty.getKey(), languageProperty.getLanguageValue(languageSign));
						}
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

	public static Set<String> getAvailableLanguageSignsOfProperties(final List<LanguageProperty> languageProperties) {
		final Set<String> availableLanguageSigns = new HashSet<>();
		for (final LanguageProperty languageProperty : languageProperties) {
			availableLanguageSigns.addAll(languageProperty.getAvailableLanguageSigns());
		}
		return availableLanguageSigns;
	}
}
