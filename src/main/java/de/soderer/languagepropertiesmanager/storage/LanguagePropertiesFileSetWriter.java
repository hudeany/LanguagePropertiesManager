package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.soderer.utilities.PropertiesWriter;
import de.soderer.utilities.Utilities;

public class LanguagePropertiesFileSetWriter {
	public static final String LANGUAGE_SIGN_DEFAULT = "default";
	public static final String DEFAULT_PROPERTIES_FILE_EXTENSION = ".properties";

	/**
	 * @deprecated Misspelled, use DEFAULT_PROPERTIES_FILE_EXTENSION
	 */
	@Deprecated
	public static final String DEFAULT_POPERTIES_FILE_EXTENSION = DEFAULT_PROPERTIES_FILE_EXTENSION;

	public static void write(final List<LanguageProperty> languageProperties, final File directory, final String languagePropertySetName, final boolean extendAndKeepExistingProperties, final boolean readComments) throws Exception {
		write(languageProperties, directory, languagePropertySetName, extendAndKeepExistingProperties, DEFAULT_PROPERTIES_FILE_EXTENSION, readComments);
	}

	public static void write(final List<LanguageProperty> languageProperties, final File directory, final String languagePropertySetName, final boolean extendAndKeepExistingProperties, final String propertiesFileExtension, final boolean readComments) throws Exception {
		final Set<String> languagePropertiesPaths = languageProperties.stream().map(o -> o.getPath()).collect(Collectors.toSet());
		final Comparator<LanguageProperty> compareByPathAndIndex = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getOriginalIndex);
		final List<LanguageProperty> sortedLanguageProperties = languageProperties.stream().sorted(compareByPathAndIndex).collect(Collectors.toList());
		for (final String nextLanguagePropertiesPath : languagePropertiesPaths) {
			// Must be a modifiable list, because existing properties may be added below
			final List<LanguageProperty> filteredLanguageProperties = sortedLanguageProperties.stream().filter(o -> o.getPath().equals(nextLanguagePropertiesPath)).collect(Collectors.toCollection(ArrayList::new));

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
				throw new Exception("Properties directory path is invalid, no parent directory found (Path: " + nextLanguagePropertiesPath + ")");
			}

			if (!propertiesDirectory.exists()) {
				throw new Exception("Properties directory '" + propertiesDirectory + "' does not exist");
			} else if (!propertiesDirectory.isDirectory()) {
				throw new Exception("Properties directory '" + propertiesDirectory + "' is not a directory");
			}

			final List<String> availableLanguageSigns = Utilities.sortButPutItemsFirst(getAvailableLanguageSignsOfProperties(filteredLanguageProperties), LANGUAGE_SIGN_DEFAULT);
			if (extendAndKeepExistingProperties) {
				final List<LanguageProperty> existingProperties = LanguagePropertiesFileSetReader.read(propertiesDirectory, propertySetName, propertiesFileExtension, false, readComments);
				if (existingProperties != null) {
					final Set<String> keysToStore = filteredLanguageProperties.stream().map(LanguageProperty::getKey).collect(Collectors.toSet());
					for (final LanguageProperty existingProperty : existingProperties) {
						if (!keysToStore.contains(existingProperty.getKey())) {
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

	public static Set<String> getAvailableLanguageSignsOfProperties(final List<LanguageProperty> languageProperties) {
		final Set<String> availableLanguageSigns = new HashSet<>();
		for (final LanguageProperty languageProperty : languageProperties) {
			availableLanguageSigns.addAll(languageProperty.getAvailableLanguageSigns());
		}
		return availableLanguageSigns;
	}
}
