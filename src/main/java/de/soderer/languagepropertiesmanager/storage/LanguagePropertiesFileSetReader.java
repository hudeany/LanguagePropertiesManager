package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.soderer.utilities.PropertiesReader;
import de.soderer.utilities.Utilities;

/**
 * Reads sets of language properties files ("name.properties",
 * "name_de.properties", "name_en_US.properties", ...) into
 * {@link LanguageProperty} items.
 */
public class LanguagePropertiesFileSetReader {
	/** Language sign of the default language file, which has no locale suffix */
	public static final String LANGUAGE_SIGN_DEFAULT = "default";
	/** File extension of language properties files if none is configured */
	public static final String DEFAULT_PROPERTIES_FILE_EXTENSION = ".properties";
	private static final Set<String> ISO_LANGUAGES = new HashSet<>(Arrays.asList(Locale.getISOLanguages()));
	private static final Set<String> ISO_COUNTRIES = new HashSet<>(Arrays.asList(Locale.getISOCountries()));
	private static final Pattern LOCALE_SUFFIX_PATTERN = Pattern.compile("_([a-z]{2})(?:_([A-Z]{2})(?:_([A-Za-z0-9]+))?)?$");

	private LanguagePropertiesFileSetReader() {
		// Utility class, no instances
	}

	/**
	 * Reads a set of language properties files with the default file extension ".properties".
	 *
	 * @param propertiesDirectory
	 *            directory containing the files of the set
	 * @param propertySetName
	 *            name of the set, which is the file name of the default language file without extension
	 * @param readKeysCaseInsensitive
	 *            whether keys are read case-insensitively
	 * @param readComments
	 *            whether comments are read
	 * @return one property per key, in the order of the default language file
	 * @throws Exception
	 *             if the directory does not exist or a file cannot be read
	 */
	public static List<LanguageProperty> read(final File propertiesDirectory, final String propertySetName, final boolean readKeysCaseInsensitive, final boolean readComments) throws Exception {
		return read(propertiesDirectory, propertySetName, DEFAULT_PROPERTIES_FILE_EXTENSION, readKeysCaseInsensitive, readComments);
	}

	/**
	 * Reads a set of language properties files.
	 *
	 * @param propertiesDirectory
	 *            directory containing the files of the set
	 * @param propertySetName
	 *            name of the set, which is the file name of the default language file without extension
	 * @param propertiesFileExtension
	 *            file extension of the properties files, a missing leading dot is added
	 * @param readKeysCaseInsensitive
	 *            whether keys are read case-insensitively
	 * @param readComments
	 *            whether comments are read
	 * @return one property per key, in the order of the default language file
	 * @throws Exception
	 *             if the directory does not exist or a file cannot be read
	 */
	public static List<LanguageProperty> read(final File propertiesDirectory, final String propertySetName,
			final String propertiesFileExtension, final boolean readKeysCaseInsensitive, final boolean readComments)
			throws Exception {
		return read(propertiesDirectory, propertySetName, propertiesFileExtension, readKeysCaseInsensitive, readComments, null);
	}

	/**
	 * Reads a set of language properties files like read() above and additionally reports keys that occur more than once within a single file.
	 * Of such duplicates the first value is kept, the later ones are dropped and would be lost on the next save.
	 *
	 * @param propertiesDirectory
	 *            directory containing the files of the set
	 * @param propertySetName
	 *            name of the set, which is the file name of the default language file without extension
	 * @param propertiesFileExtension
	 *            file extension of the properties files, a missing leading dot is added
	 * @param readKeysCaseInsensitive
	 *            whether keys are read case-insensitively
	 * @param readComments
	 *            whether comments are read
	 * @param duplicateKeysByFile
	 *            Optional (may be null): receives the display path of every file containing duplicate keys and its duplicate keys.
	 *            Entries are added, so one map can collect the results of several sets.
	 * @return one property per key, in the order of the default language file
	 * @throws Exception
	 *             if the directory does not exist or a file cannot be read
	 */
	public static List<LanguageProperty> read(final File propertiesDirectory, final String propertySetName,
			final String propertiesFileExtension, final boolean readKeysCaseInsensitive, final boolean readComments,
			final Map<String, Set<String>> duplicateKeysByFile) throws Exception {
		final String normalizedPropertiesFileExtension = normalizePropertiesFileExtension(propertiesFileExtension);
		if (!propertiesDirectory.exists()) {
			throw new Exception("Properties directory '" + propertiesDirectory + "' does not exist");
		} else if (!propertiesDirectory.isDirectory()) {
			throw new Exception("Properties directory '" + propertiesDirectory + "' is not a directory");
		}

		final List<LanguageProperty> languageProperties = new ArrayList<>();
		final Map<String, Map<String, LanguageProperty>> propertyIndex = new HashMap<>();

		// Language signs of all files of this set, including files without any entries
		final Set<String> languageSignsOfFiles = new HashSet<>();

		final FilenameFilter fileFilter = (dir, name) -> isFileOfPropertySet(name, propertySetName, normalizedPropertiesFileExtension);

		final File[] propertyFiles = propertiesDirectory.listFiles(fileFilter);
		if (propertyFiles == null) {
			throw new Exception("Cannot list files of properties directory '" + propertiesDirectory + "'");
		}
		// Read the default file first, so its key order defines the original order of all properties
		Arrays.sort(propertyFiles, Comparator
				.comparing((final File file) -> !LANGUAGE_SIGN_DEFAULT.equals(getLanguageSignOfFilename(file.getName(), propertySetName, normalizedPropertiesFileExtension)))
				.thenComparing(File::getName));

		for (final File propertyFile : propertyFiles) {
			final String languageSign = getLanguageSignOfFilename(propertyFile.getName(), propertySetName, normalizedPropertiesFileExtension);
			if (languageSign != null) {
				languageSignsOfFiles.add(languageSign);
				try (PropertiesReader propertiesReader = new PropertiesReader(new FileInputStream(propertyFile))) {
					propertiesReader.setReadKeysCaseInsensitive(readKeysCaseInsensitive);
					// Duplicate keys within one file: the first value wins, consistent with removeDuplicates() and the import
					propertiesReader.setFirstDuplicateValueWins(true);
					final Map<String, String> languageEntries = propertiesReader.read();
					if (duplicateKeysByFile != null && !propertiesReader.getDuplicateKeys().isEmpty()) {
						duplicateKeysByFile.put(Utilities.replaceUsersHomeByTilde(propertyFile.getAbsolutePath()), propertiesReader.getDuplicateKeys());
					}
					final String path = Utilities.replaceUsersHomeByTilde(new File(propertiesDirectory, propertySetName).getAbsolutePath());
					final Map<String, LanguageProperty> keyIndex = propertyIndex.computeIfAbsent(path, p -> new HashMap<>());

					for (final Entry<String, String> entry : languageEntries.entrySet()) {
						LanguageProperty property = keyIndex.get(entry.getKey());
						if (property == null) {
							property = new LanguageProperty(path, entry.getKey());
							property.setOriginalIndex(languageProperties.size() + 1);
							// Empty lines structuring blocks are taken from the file defining the position, which is the default file if it contains the key
							property.setEmptyLinesBefore(propertiesReader.getEmptyLinesBefore().getOrDefault(entry.getKey(), 0));
							languageProperties.add(property);
							keyIndex.put(entry.getKey(), property);
						}
						if (readComments) {
							if (Utilities.isNotEmpty(propertiesReader.getComments().get(entry.getKey()))
									&& Utilities.isEmpty(property.getComment())) {
								property.setComment(propertiesReader.getComments().get(entry.getKey()));
							}
						}
						property.setLanguageValue(languageSign, entry.getValue());
					}
				} catch (final Exception e) {
					throw new Exception("Error when reading file: " + propertyFile.getAbsolutePath(), e);
				}
			}
		}

		// Register languages of empty files (e.g. newly created "_en" file) like the "add language" function does,
		// so they are shown as columns and can be filled via translate or transfer
		final Set<String> languageSignsWithValues = getAvailableLanguageSignsOfProperties(languageProperties);
		for (final String languageSign : languageSignsOfFiles) {
			if (!languageSignsWithValues.contains(languageSign)) {
				for (final LanguageProperty languageProperty : languageProperties) {
					languageProperty.setLanguageValue(languageSign, null);
				}
			}
		}

		return languageProperties;
	}

	/**
	 * Checks whether a filename belongs to the given property set:
	 * either an exact match of "propertySetName + extension" (the default language file),
	 * or "propertySetName + '_' + validLocaleSuffix + extension".
	 * A plain wildcard match on "propertySetName*extension" would also match unrelated files
	 * like "propertySetName-customer.properties", which must be excluded here.
	 *
	 * @param fileName
	 *            file name without directory
	 * @param propertySetName
	 *            name of the set
	 * @param propertiesFileExtension
	 *            file extension of the properties files, a missing leading dot is added
	 * @return true if the file belongs to the set
	 */
	public static boolean isFileOfPropertySet(final String fileName, final String propertySetName, final String propertiesFileExtension) {
		final String normalizedPropertiesFileExtension = normalizePropertiesFileExtension(propertiesFileExtension);
		if (fileName == null || !fileName.endsWith(normalizedPropertiesFileExtension)) {
			return false;
		}

		final String baseName = fileName.substring(0, fileName.length() - normalizedPropertiesFileExtension.length());
		if (baseName.equals(propertySetName)) {
			return true;
		}

		if (!baseName.startsWith(propertySetName)) {
			return false;
		}

		final String localeSuffix = baseName.substring(propertySetName.length());
		final Matcher matcher = LOCALE_SUFFIX_PATTERN.matcher(localeSuffix);
		if (matcher.matches()) {
			final String lang = matcher.group(1);
			final String country = matcher.group(2);
			return ISO_LANGUAGES.contains(lang) && (country == null || ISO_COUNTRIES.contains(country));
		} else {
			return false;
		}
	}

	/**
	 * Get language sign of a filename that belongs to the given property set (checked by isFileOfPropertySet()).
	 * Only the part after the property set name is evaluated, so set names ending with a locale-like suffix
	 * (e.g. "texts_de") are handled correctly.
	 *
	 * @param fileName
	 *            file name without directory
	 * @param propertySetName
	 *            name of the set
	 * @param propertiesFileExtension
	 *            file extension of the properties files, a missing leading dot is added
	 * @return the language sign, {@link #LANGUAGE_SIGN_DEFAULT} for the default language file
	 */
	public static String getLanguageSignOfFilename(final String fileName, final String propertySetName, final String propertiesFileExtension) {
		final String normalizedPropertiesFileExtension = normalizePropertiesFileExtension(propertiesFileExtension);
		final String baseName = fileName.substring(0, fileName.length() - normalizedPropertiesFileExtension.length());
		if (baseName.equals(propertySetName)) {
			return LANGUAGE_SIGN_DEFAULT;
		} else {
			// Suffix was already validated by isFileOfPropertySet(), strip the leading "_"
			return baseName.substring(propertySetName.length() + 1);
		}
	}

	/**
	 * Get language sign of a language properties filename, which is the valid
	 * locale suffix (e.g. "de" or "de_AT") before the file extension.
	 *
	 * @param fileName
	 *            file name, may contain a directory
	 * @return the language sign, {@link #LANGUAGE_SIGN_DEFAULT} if the file name has no valid locale suffix
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

		final Matcher matcher = LOCALE_SUFFIX_PATTERN.matcher(fileNamePart);
		if (matcher.find()) {
			final String lang = matcher.group(1);
			final String country = matcher.group(2);

			if (ISO_LANGUAGES.contains(lang) && (country == null || ISO_COUNTRIES.contains(country))) {
				return fileNamePart.substring(matcher.start() + 1);
			} else {
				return LANGUAGE_SIGN_DEFAULT;
			}
		} else {
			return LANGUAGE_SIGN_DEFAULT;
		}
	}

	/**
	 * Collects the language signs registered in any of the given properties.
	 *
	 * @param languageProperties
	 *            properties to check
	 * @return all language signs, unsorted
	 */
	public static Set<String> getAvailableLanguageSignsOfProperties(final List<LanguageProperty> languageProperties) {
		return languageProperties.stream().map(o -> o.getAvailableLanguageSigns()).flatMap(Set::stream).collect(Collectors.toSet());
	}

	/**
	 * Determines the names of the properties sets of the given properties (last part of their paths).
	 *
	 * @param languageProperties
	 *            properties to check
	 * @return distinct set names, properties without a path are ignored
	 */
	public static List<String> getLanguagePropertiesSetNames(final List<LanguageProperty> languageProperties) {
		final Set<String> languagePropertiesSetPaths = new HashSet<>();
		for (final LanguageProperty languageProperty : languageProperties) {
			// Properties without path (e.g. imported without path column) have no set name
			if (Utilities.isNotBlank(languageProperty.getPath())) {
				languagePropertiesSetPaths.add(languageProperty.getPath());
			}
		}

		// Different paths may have the same set name (e.g. "messages" in several modules), each name is listed only once
		final Set<String> languagePropertiesSetNames = new TreeSet<>();
		for (final String languagePropertiesSetPath : languagePropertiesSetPaths) {
			final String filename = new File(languagePropertiesSetPath).getName();
			languagePropertiesSetNames.add(filename);
		}

		return new ArrayList<>(languagePropertiesSetNames);
	}

	/**
	 * Determines the name of the properties set a file belongs to, which is the
	 * file name without a valid locale suffix and without the file extension.
	 *
	 * @param fileName
	 *            file name without directory
	 * @param propertiesFileExtension
	 *            file extension of the properties files, a missing leading dot is added
	 * @return the set name, or null if the file name does not end with the file extension
	 */
	public static String getPropertySetBaseName(final String fileName, final String propertiesFileExtension) {
		final String normalizedPropertiesFileExtension = normalizePropertiesFileExtension(propertiesFileExtension);
		if (fileName == null || !fileName.endsWith(normalizedPropertiesFileExtension)) {
			return null;
		}

		final String baseName = fileName.substring(0, fileName.length() - normalizedPropertiesFileExtension.length());
		final Matcher matcher = LOCALE_SUFFIX_PATTERN.matcher(baseName);
		if (matcher.find()) {
			final String lang = matcher.group(1);
			final String country = matcher.group(2);
			if (ISO_LANGUAGES.contains(lang) && (country == null || ISO_COUNTRIES.contains(country))) {
				// Strip the validated locale suffix, keep everything before it
				return baseName.substring(0, matcher.start());
			}
		}

		// No valid locale suffix found -> this file IS the base/default file itself
		return baseName;
	}

	/**
	 * Ensures the properties file extension starts with a dot (e.g. configured "properties" becomes ".properties").
	 * Without the dot, the remaining base name would end with "." and locale suffixes like "_de" would not be detected.
	 *
	 * @param propertiesFileExtension
	 *            configured file extension, may be null or empty
	 * @return the file extension starting with a dot, {@link #DEFAULT_PROPERTIES_FILE_EXTENSION} if none is configured
	 */
	public static String normalizePropertiesFileExtension(final String propertiesFileExtension) {
		if (propertiesFileExtension == null || propertiesFileExtension.trim().isEmpty()) {
			return DEFAULT_PROPERTIES_FILE_EXTENSION;
		}

		final String trimmedPropertiesFileExtension = propertiesFileExtension.trim();
		if (trimmedPropertiesFileExtension.startsWith(".")) {
			return trimmedPropertiesFileExtension;
		} else {
			return "." + trimmedPropertiesFileExtension;
		}
	}
}
