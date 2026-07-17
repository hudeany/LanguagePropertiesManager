package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.soderer.utilities.PropertiesReader;
import de.soderer.utilities.Utilities;

public class LanguagePropertiesFileSetReader {
	public static final String LANGUAGE_SIGN_DEFAULT = "default";
	public static final String DEFAULT_PROPERTIES_FILE_EXTENSION = ".properties";
	private static final Set<String> ISO_LANGUAGES = new HashSet<>(Arrays.asList(Locale.getISOLanguages()));
	private static final Set<String> ISO_COUNTRIES = new HashSet<>(Arrays.asList(Locale.getISOCountries()));
	private static final Pattern LOCALE_SUFFIX_PATTERN = Pattern.compile("_([a-z]{2})(?:_([A-Z]{2})(?:_([A-Za-z0-9]+))?)?$");

	/**
	 * Reads a set of language properties files into a map with values of item name strings as keys, where each of them is referencing a map of language signs and their value string for display
	 * @param basePropertiesFilePath
	 * @return
	 * @throws Exception
	 */
	public static List<LanguageProperty> read(final File propertiesDirectory, final String propertySetName, final boolean readKeysCaseInsensitive, final boolean readComments) throws Exception {
		return read(propertiesDirectory, propertySetName, DEFAULT_PROPERTIES_FILE_EXTENSION, readKeysCaseInsensitive, readComments);
	}

	/**
	 * Reads a set of language properties files into a map with values of item name strings as keys, where each of them is referencing a map of language signs and their value string for display
	 * @param basePropertiesFilePath
	 * @return
	 * @throws Exception
	 */
	public static List<LanguageProperty> read(final File propertiesDirectory, final String propertySetName,
			final String propertiesFileExtension, final boolean readKeysCaseInsensitive, final boolean readComments)
			throws Exception {
		if (!propertiesDirectory.exists()) {
			throw new Exception("Properties directory '" + propertiesDirectory + "' does not exist");
		} else if (!propertiesDirectory.isDirectory()) {
			throw new Exception("Properties directory '" + propertiesDirectory + "' is not a directory");
		}

		final List<LanguageProperty> languageProperties = new ArrayList<>();
		final Map<String, Map<String, LanguageProperty>> propertyIndex = new HashMap<>();

		final FilenameFilter fileFilter = (dir, name) -> isFileOfPropertySet(name, propertySetName, propertiesFileExtension);

		for (final File propertyFile : propertiesDirectory.listFiles(fileFilter)) {
			final String languageSign = getLanguageSignOfFilename(propertyFile.getName());
			if (languageSign != null) {
				try (PropertiesReader propertiesReader = new PropertiesReader(new FileInputStream(propertyFile))) {
					propertiesReader.setReadKeysCaseInsensitive(readKeysCaseInsensitive);
					final Map<String, String> languageEntries = propertiesReader.read();
					final String path = Utilities.replaceUsersHomeByTilde(new File(propertiesDirectory, propertySetName).getAbsolutePath());
					final Map<String, LanguageProperty> keyIndex = propertyIndex.computeIfAbsent(path, p -> new HashMap<>());

					for (final Entry<String, String> entry : languageEntries.entrySet()) {
						LanguageProperty property = keyIndex.get(entry.getKey());
						if (property == null) {
							property = new LanguageProperty(path, entry.getKey());
							property.setOriginalIndex(languageProperties.size() + 1);
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

		return languageProperties;
	}

	/**
	 * Checks whether a filename belongs to the given property set:
	 * either an exact match of "propertySetName + extension" (the default language file),
	 * or "propertySetName + '_' + validLocaleSuffix + extension".
	 * A plain wildcard match on "propertySetName*extension" would also match unrelated files
	 * like "propertySetName-customer.properties", which must be excluded here.
	 */
	public static boolean isFileOfPropertySet(final String fileName, final String propertySetName, final String propertiesFileExtension) {
		if (fileName == null || !fileName.endsWith(propertiesFileExtension)) {
			return false;
		}

		final String baseName = fileName.substring(0, fileName.length() - propertiesFileExtension.length());
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

	public static String getPropertySetBaseName(final String fileName, final String propertiesFileExtension) {
		if (fileName == null || !fileName.endsWith(propertiesFileExtension)) {
			return null;
		}

		final String baseName = fileName.substring(0, fileName.length() - propertiesFileExtension.length());
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
}
