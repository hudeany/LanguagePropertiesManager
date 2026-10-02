package de.soderer.languagepropertiesmanager.storage;

import java.util.ArrayList;
import java.util.List;

import de.soderer.utilities.Utilities;

/**
 * Language properties read by one of the load/import sources, not yet taken
 * over into the currently loaded data.
 */
public class LoadedLanguageProperties {
	private final List<LanguageProperty> languageProperties;
	private final List<String> availableLanguageSigns;
	private final List<String> languagePropertiesSetNames;
	/** File or directory the properties were read from, only for display */
	private final String sourceDescription;

	private LoadedLanguageProperties(final List<LanguageProperty> languageProperties, final List<String> availableLanguageSigns, final List<String> languagePropertiesSetNames, final String sourceDescription) {
		this.languageProperties = languageProperties == null ? new ArrayList<>() : languageProperties;
		this.availableLanguageSigns = availableLanguageSigns == null ? new ArrayList<>() : availableLanguageSigns;
		this.languagePropertiesSetNames = languagePropertiesSetNames == null ? new ArrayList<>() : languagePropertiesSetNames;
		this.sourceDescription = sourceDescription;
	}

	/**
	 * For properties read from properties files, the language signs are determined from the properties
	 */
	public static LoadedLanguageProperties ofPropertiesSets(final List<LanguageProperty> languageProperties, final List<String> languagePropertiesSetNames, final String sourceDescription) {
		final List<LanguageProperty> properties = languageProperties == null ? new ArrayList<>() : languageProperties;
		final List<String> languageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(properties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);
		return new LoadedLanguageProperties(properties, languageSigns, languagePropertiesSetNames, sourceDescription);
	}

	/**
	 * For properties read from an Excel or CSV file, which delivers its language signs and set name itself
	 */
	public static LoadedLanguageProperties ofSingleSet(final List<LanguageProperty> languageProperties, final List<String> availableLanguageSigns, final String languagePropertiesSetName, final String sourceDescription) {
		final List<String> setNames = new ArrayList<>();
		setNames.add(languagePropertiesSetName);
		return new LoadedLanguageProperties(languageProperties, availableLanguageSigns, setNames, sourceDescription);
	}

	public List<LanguageProperty> getLanguageProperties() {
		return languageProperties;
	}

	public List<String> getAvailableLanguageSigns() {
		return availableLanguageSigns;
	}

	public List<String> getLanguagePropertiesSetNames() {
		return languagePropertiesSetNames;
	}

	public String getSourceDescription() {
		return sourceDescription;
	}

	public String getCombinedSetName() {
		if (languagePropertiesSetNames.isEmpty()) {
			return null;
		} else if (languagePropertiesSetNames.size() == 1) {
			return languagePropertiesSetNames.get(0);
		} else {
			return "Multiple";
		}
	}
}
