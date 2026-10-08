package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.soderer.utilities.PropertiesWriter;
import de.soderer.utilities.Utilities;

/**
 * Writes {@link LanguageProperty} items into sets of language properties files
 * ("name.properties", "name_de.properties", ...), one file per language.
 */
public class LanguagePropertiesFileSetWriter {
	/** Language sign of the default language file, which has no locale suffix */
	public static final String LANGUAGE_SIGN_DEFAULT = "default";
	/** File extension of language properties files if none is configured */
	public static final String DEFAULT_PROPERTIES_FILE_EXTENSION = ".properties";

	/**
	 * Misspelled name of {@link #DEFAULT_PROPERTIES_FILE_EXTENSION}, kept for compatibility.
	 *
	 * @deprecated Misspelled, use DEFAULT_PROPERTIES_FILE_EXTENSION
	 */
	@Deprecated
	public static final String DEFAULT_POPERTIES_FILE_EXTENSION = DEFAULT_PROPERTIES_FILE_EXTENSION;

	private LanguagePropertiesFileSetWriter() {
		// Utility class, no instances
	}

	/**
	 * Writes language properties with the default file extension ".properties".
	 *
	 * @param languageProperties
	 *            properties to write, grouped into sets by their paths
	 * @param directory
	 *            directory for properties without a path
	 * @param languagePropertySetName
	 *            set name for properties without a path
	 * @param extendAndKeepExistingProperties
	 *            whether keys existing in the files, but not in the given properties, are kept
	 * @param readComments
	 *            whether comments of kept existing properties are read
	 * @throws Exception
	 *             if a directory does not exist or a file cannot be written
	 */
	public static void write(final List<LanguageProperty> languageProperties, final File directory, final String languagePropertySetName, final boolean extendAndKeepExistingProperties, final boolean readComments) throws Exception {
		write(languageProperties, directory, languagePropertySetName, extendAndKeepExistingProperties, DEFAULT_PROPERTIES_FILE_EXTENSION, readComments);
	}

	/**
	 * Writes language properties. Every distinct path of the properties is
	 * written as its own set of files, properties without a path are written
	 * into the given directory with the given set name.
	 *
	 * @param languageProperties
	 *            properties to write, grouped into sets by their paths
	 * @param directory
	 *            directory for properties without a path
	 * @param languagePropertySetName
	 *            set name for properties without a path
	 * @param extendAndKeepExistingProperties
	 *            whether keys existing in the files, but not in the given properties, are kept
	 * @param propertiesFileExtension
	 *            file extension of the properties files, a missing leading dot is added
	 * @param readComments
	 *            whether comments of kept existing properties are read
	 * @throws Exception
	 *             if a directory does not exist or a file cannot be written
	 */
	public static void write(final List<LanguageProperty> languageProperties, final File directory, final String languagePropertySetName, final boolean extendAndKeepExistingProperties, final String propertiesFileExtension, final boolean readComments) throws Exception {
		// Same normalization as the reader, otherwise a configured "properties" (without dot) would create files like "nameproperties"
		final String normalizedPropertiesFileExtension = LanguagePropertiesFileSetReader.normalizePropertiesFileExtension(propertiesFileExtension);
		final Set<String> languagePropertiesPaths = languageProperties.stream().map(o -> getEmptyForNull(o.getPath())).collect(Collectors.toSet());
		final Comparator<LanguageProperty> compareByPathAndIndex = Comparator.comparing((final LanguageProperty o) -> getEmptyForNull(o.getPath())).thenComparing(LanguageProperty::getOriginalIndex);
		final List<LanguageProperty> sortedLanguageProperties = languageProperties.stream().sorted(compareByPathAndIndex).collect(Collectors.toList());
		for (final String nextLanguagePropertiesPath : languagePropertiesPaths) {
			// Must be a modifiable list, because existing properties may be added below
			final List<LanguageProperty> filteredLanguageProperties = sortedLanguageProperties.stream().filter(o -> Objects.equals(getEmptyForNull(o.getPath()), nextLanguagePropertiesPath)).collect(Collectors.toCollection(ArrayList::new));

			File propertiesDirectory;
			String propertySetName;
			final String languagePropertiesPath = Utilities.isBlank(nextLanguagePropertiesPath) ? "" : Utilities.replaceUsersHome(nextLanguagePropertiesPath);
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
				final List<LanguageProperty> existingProperties = LanguagePropertiesFileSetReader.read(propertiesDirectory, propertySetName, normalizedPropertiesFileExtension, false, readComments);
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
					filename = propertySetName + normalizedPropertiesFileExtension;
				} else {
					filename = propertySetName + "_" + languageSign + normalizedPropertiesFileExtension;
				}

				try (PropertiesWriter propertiesWriter = new PropertiesWriter(new FileOutputStream(new File(propertiesDirectory, filename)))) {
					// Empty lines of properties missing in this language are carried over, so block boundaries are kept
					int pendingEmptyLines = 0;
					for (final LanguageProperty languageProperty : filteredLanguageProperties) {
						pendingEmptyLines = Math.max(pendingEmptyLines, languageProperty.getEmptyLinesBefore());
						if (languageProperty.containsLanguage(languageSign) && languageProperty.getLanguageValue(languageSign) != null) {
							// No empty lines at the beginning of the file
							if (propertiesWriter.getWrittenProperties() > 0) {
								for (int i = 0; i < pendingEmptyLines; i++) {
									propertiesWriter.writeEmptyLine();
								}
							}
							pendingEmptyLines = 0;

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

	private static String getEmptyForNull(final String value) {
		return value == null ? "" : value;
	}

	/**
	 * Collects the language signs registered in any of the given properties.
	 *
	 * @param languageProperties
	 *            properties to check
	 * @return all language signs, unsorted
	 */
	public static Set<String> getAvailableLanguageSignsOfProperties(final List<LanguageProperty> languageProperties) {
		final Set<String> availableLanguageSigns = new HashSet<>();
		for (final LanguageProperty languageProperty : languageProperties) {
			availableLanguageSigns.addAll(languageProperty.getAvailableLanguageSigns());
		}
		return availableLanguageSigns;
	}
}
