package de.soderer.languagepropertiesmanager.dlg;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.soderer.languagepropertiesmanager.LanguagePropertiesManager;
import de.soderer.languagepropertiesmanager.model.LanguagePropertiesModel;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.FileUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.collection.UniqueFifoQueuedList;
import de.soderer.utilities.csv.CsvFormat;
import de.soderer.utilities.csv.CsvReader;
import de.soderer.utilities.csv.CsvWriter;
import de.soderer.utilities.swing.ComboSelectionDialog;
import de.soderer.utilities.swing.SimpleInputDialog;

/**
 * Statistics of the loaded properties and the check of their usage in source
 * files (statistics and check usage buttons of the main window), including the
 * list of recent check usage settings.
 */
final class StatisticsAndUsage {
	private final LanguagePropertiesManagerDialog owner;
	private final ConfigurationProperties applicationConfiguration;
	private final LanguagePropertiesModel model;

	/** Recent check usage settings (directory, file pattern, usage pattern as csv line), the latest used is the last entry */
	private UniqueFifoQueuedList<String> recentlyCheckUsages = new UniqueFifoQueuedList<>(5);

	StatisticsAndUsage(final LanguagePropertiesManagerDialog owner, final LanguagePropertiesModel model, final ConfigurationProperties applicationConfiguration) {
		this.owner = owner;
		this.model = model;
		this.applicationConfiguration = applicationConfiguration;
	}

	/**
	 * (Re)reads the recent check usage settings from the configuration
	 */
	void loadRecentCheckUsages() {
		recentlyCheckUsages = new UniqueFifoQueuedList<>(5);
		for (final String checkUsageSetting : applicationConfiguration.getList(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE)) {
			// Converts entries of older versions (with backslash escaping) into the plain format
			try {
				recentlyCheckUsages.add(CsvWriter.getCsvLine(createCheckUsageCsvFormat(), parseCheckUsageSetting(checkUsageSetting)));
			} catch (final Exception e) {
				System.err.println("Cannot read recent check usage setting '" + checkUsageSetting + "': " + e.getMessage());
				recentlyCheckUsages.add(checkUsageSetting);
			}
		}
	}

	boolean hasRecentCheckUsages() {
		return recentlyCheckUsages != null && recentlyCheckUsages.size() > 0;
	}

	UniqueFifoQueuedList<String> getRecentCheckUsages() {
		return recentlyCheckUsages;
	}

	void showStatistics() {
		try {
			final int totalProperties = model.getLanguageProperties().size();

			// Number of entries per properties path (i.e. per properties file / properties set)
			final Map<String, Integer> countByPath = new LinkedHashMap<>();
			for (final LanguageProperty languageProperty : model.getLanguageProperties()) {
				countByPath.merge(languageProperty.getPath(), 1, Integer::sum);
			}

			// Duplicate groups (same path + key), without altering any data
			final Map<String, Integer> countByPathAndKey = new LinkedHashMap<>();
			for (final LanguageProperty languageProperty : model.getLanguageProperties()) {
				final String groupKey = languageProperty.getPath() + "\u0000" + languageProperty.getKey();
				countByPathAndKey.merge(groupKey, 1, Integer::sum);
			}
			int duplicateGroupCount = 0;
			int duplicateEntryCount = 0;
			for (final int count : countByPathAndKey.values()) {
				if (count > 1) {
					duplicateGroupCount++;
					duplicateEntryCount += count - 1;
				}
			}

			int propertiesWithCommentCount = 0;
			for (final LanguageProperty languageProperty : model.getLanguageProperties()) {
				if (Utilities.isNotEmpty(languageProperty.getComment())) {
					propertiesWithCommentCount++;
				}
			}

			// Per-language completeness and value length statistics
			final Map<String, Integer> filledCountByLanguage = new LinkedHashMap<>();
			final Map<String, Long> totalLengthByLanguage = new LinkedHashMap<>();
			for (final String sign : model.getAvailableLanguageSigns()) {
				filledCountByLanguage.put(sign, 0);
				totalLengthByLanguage.put(sign, 0L);
			}

			String longestValuePath = null;
			String longestValueKey = null;
			String longestValueLanguage = null;
			int longestValueLength = -1;

			for (final LanguageProperty languageProperty : model.getLanguageProperties()) {
				for (final String sign : model.getAvailableLanguageSigns()) {
					final String value = languageProperty.getLanguageValue(sign);
					if (Utilities.isNotEmpty(value)) {
						filledCountByLanguage.merge(sign, 1, Integer::sum);
						totalLengthByLanguage.merge(sign, (long) value.length(), Long::sum);
						if (value.length() > longestValueLength) {
							longestValueLength = value.length();
							longestValuePath = languageProperty.getPath();
							longestValueKey = languageProperty.getKey();
							longestValueLanguage = sign;
						}
					}
				}
			}

			final StringBuilder reportText = new StringBuilder();
			reportText.append(LangResources.get("statistics_totalProperties", totalProperties)).append("\n");
			reportText.append(LangResources.get("statistics_totalPropertySets", countByPath.size())).append("\n");
			reportText.append(LangResources.get("statistics_totalLanguages", model.getAvailableLanguageSigns().size(), Utilities.join(model.getAvailableLanguageSigns(), ", "))).append("\n");
			reportText.append(LangResources.get("statistics_propertiesWithComment", propertiesWithCommentCount)).append("\n");
			reportText.append(LangResources.get("statistics_duplicateGroups", duplicateGroupCount, duplicateEntryCount)).append("\n");

			reportText.append("\n").append(LangResources.get("statistics_perSetHeader")).append("\n");
			for (final Map.Entry<String, Integer> entry : countByPath.entrySet()) {
				reportText.append("  \"").append(entry.getKey()).append("\": ").append(entry.getValue()).append("\n");
			}

			reportText.append("\n").append(LangResources.get("statistics_perLanguageHeader")).append("\n");
			for (final String sign : model.getAvailableLanguageSigns()) {
				final int filledCount = filledCountByLanguage.get(sign);
				final int missingCount = totalProperties - filledCount;
				final double filledPercent = totalProperties == 0 ? 0 : filledCount * 100.0 / totalProperties;
				final double averageLength = filledCount == 0 ? 0 : (double) totalLengthByLanguage.get(sign) / filledCount;
				reportText.append("  ").append(sign).append(": ").append(LangResources.get("statistics_languageLine",
						filledCount, missingCount, String.format(Locale.US, "%.1f", filledPercent), String.format(Locale.US, "%.1f", averageLength))).append("\n");
			}

			if (longestValueLength >= 0) {
				reportText.append("\n").append(LangResources.get("statistics_longestValue", longestValueLength, longestValueLanguage, longestValuePath, longestValueKey)).append("\n");
			}

			owner.showData(LangResources.get("statisticsReportTitle"), reportText.toString());
		} catch (final Exception ex) {
			owner.showError(ex);
		}
	}

	void checkUsageNew() {
		try {
			final File directory = DialogUtilities.chooseDirectory(owner, owner.getTitle() + " " + LangResources.get("directory_dialog_title"), null);
			if (directory == null) {
				owner.showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
			} else if (directory.exists() && directory.isDirectory()) {
				final SimpleInputDialog filePatternDialog = new SimpleInputDialog(owner, owner.getTitle(), LangResources.get("enterfilepattern"));
				filePatternDialog.setDefaultText(".*\\.java");
				final String filePattern = filePatternDialog.open();
				if (filePattern == null) {
					owner.showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
				} else {
					final SimpleInputDialog usagePatternDialog = new SimpleInputDialog(owner, owner.getTitle(), LangResources.get("enterusagepattern"));
					usagePatternDialog.setDefaultText("LangResources.get(\"<property>\"");
					final String usagePattern = usagePatternDialog.open();
					if (usagePattern != null) {
						DialogUtilities.moveToEnd(recentlyCheckUsages, CsvWriter.getCsvLine(createCheckUsageCsvFormat(), directory.getAbsolutePath(), filePattern, usagePattern));
						applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE, recentlyCheckUsages);
						checkUsage(model.getLanguageProperties(), directory.getAbsolutePath(), filePattern, usagePattern);
						owner.checkButtonStatus();
					}
				}
			}
		} catch (final Exception ex) {
			owner.showError(ex);
		}
	}

	/**
	 * CSV format of a single recent check usage setting (directory, file pattern, usage pattern).
	 * Plain RFC 4180 csv: backslashes in paths and regular expressions are stored as they are.
	 */
	private static CsvFormat createCheckUsageCsvFormat() {
		return new CsvFormat()
				.withSeparator(';')
				.withStringQuote('"')
				.withEscapeLineBreaks(false);
	}

	/**
	 * Parses a recent check usage setting (directory, file pattern, usage pattern).
	 * Settings of older versions were stored with backslash escaping. Such a setting is only
	 * accepted as legacy setting, if it is readable with backslash escaping and its directory exists.
	 * A plain setting with Windows paths is practically never readable with backslash escaping
	 * (e.g. "\U" in "C:\Users" is no valid escape sequence).
	 */
	private static List<String> parseCheckUsageSetting(final String setting) throws Exception {
		try {
			final List<String> legacySettings = CsvReader.parseCsvLine(createCheckUsageCsvFormat().withEscapeLineBreaks(true), setting);
			if (legacySettings.size() == 3 && new File(legacySettings.get(0)).isDirectory()) {
				return legacySettings;
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			// Not a legacy setting
		}

		final List<String> settings = CsvReader.parseCsvLine(createCheckUsageCsvFormat(), setting);
		if (settings.size() != 3) {
			throw new Exception("Invalid recent check usage setting: " + setting);
		}
		return settings;
	}

	void checkUsagePrevious() {
		try {
			final ComboSelectionDialog dialog = new ComboSelectionDialog(owner, owner.getTitle() + " " + LangResources.get("recentsettingsdialogtitle"), LangResources.get("recent_settings_dialog_text"), recentlyCheckUsages, DialogUtilities.getLastEntryIndex(recentlyCheckUsages));
			final String setting = dialog.open();

			// Take over a possible reordering (drag&drop) or deletion of the recent
			// settings done in the dialog, regardless of whether an entry was selected
			// or the dialog was canceled
			recentlyCheckUsages.clear();
			recentlyCheckUsages.addAll(dialog.getItems());
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE, recentlyCheckUsages);

			if (setting != null) {
				DialogUtilities.moveToEnd(recentlyCheckUsages, setting); // put selected as latest used
				applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE, recentlyCheckUsages);
				final List<String> settings = parseCheckUsageSetting(setting);
				final String directory = settings.get(0);
				final String filePattern = settings.get(1);
				final String usagePattern = settings.get(2);
				checkUsage(model.getLanguageProperties(), directory, filePattern, usagePattern);
			}
			owner.checkButtonStatus();
		} catch (final Exception ex) {
			owner.showError(ex);
		}
	}

	/**
	 * Checks, which properties are used in the source files of a directory, and shows the report.
	 *
	 * @param storageToCheck
	 *            properties to check
	 * @param directory
	 *            directory with the source files, searched recursively
	 * @param filePattern
	 *            regular expression for the names of the source files
	 * @param usagePatternString
	 *            usage of a property in the source files, "&lt;property&gt;" stands for the key
	 * @throws Exception
	 *             if the source files cannot be read
	 */
	public void checkUsage(final List<LanguageProperty> storageToCheck, final String directory, final String filePattern, final String usagePatternString) throws Exception {
		final Set<String> existingDefaultProperties = new HashSet<>();
		final Set<String> existingOverallProperties = new HashSet<>();
		final Set<String> missingDefaultProperties = new HashSet<>();
		final Set<String> missingOverallProperties = new HashSet<>();
		final Set<String> usedProperties = new HashSet<>();
		final Set<String> unusedProperties = new HashSet<>();
		final Set<File> filesWithMissingValues = new HashSet<>();

		// The given properties are checked (before they were ignored and always all loaded properties were used)
		for (final LanguageProperty languageProperty : storageToCheck) {
			if (Utilities.isNotEmpty(languageProperty.getLanguageValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT))) {
				existingDefaultProperties.add(languageProperty.getKey());
			}
			existingOverallProperties.add(languageProperty.getKey());
		}

		final Pattern usagePattern = Pattern.compile(
				"("
						+ usagePatternString
						.replace("\\", "\\\\")
						.replace("(", "\\(")
						.replace(")", "\\)")
						.replace("<property>", ")([a-zA-Z0-9._]+)(")
						+ ")");
		final List<File> fileList = FileUtilities.getFilesByPattern(new File(directory), filePattern, true);
		for (final File file : fileList) {
			final String fileDataString = FileUtilities.readFileToString(file, StandardCharsets.UTF_8);
			final Matcher matcher = usagePattern.matcher(fileDataString);
			while (matcher.find()) {
				final String propertyName = matcher.group(2);

				if (existingDefaultProperties.contains(propertyName)) {
					usedProperties.add(propertyName);
				} else {
					filesWithMissingValues.add(file);
					missingDefaultProperties.add(propertyName);
				}

				if (existingOverallProperties.contains(propertyName)) {
					usedProperties.add(propertyName);
				} else {
					missingOverallProperties.add(propertyName);
				}
			}
		}
		for (final String propertyName : existingOverallProperties) {
			if (!usedProperties.contains(propertyName)) {
				unusedProperties.add(propertyName);
			}
		}

		String reportText = "";
		reportText += LangResources.get("reportresults") + "\n";
		reportText += "Checked directory: " + directory + "\n";
		reportText += "Checked filePattern: " + filePattern + "\n";
		reportText += "Checked usagePattern: " + usagePatternString + "\n";
		reportText += "Properties in default language: " + existingDefaultProperties.size() + "\n";
		reportText += "Properties in all languages: " + existingOverallProperties.size() + "\n";
		reportText += "Missing properties in default language: " + missingDefaultProperties.size() + "\n";
		reportText += "Missing properties in all languages: " + missingOverallProperties.size() + "\n";
		reportText += "Unused properties in all languages: " + unusedProperties.size() + "\n";
		reportText += "Used properties in all languages: " + usedProperties.size() + "\n";
		reportText += "Checked files: " + fileList.size() + "\n";
		if (filesWithMissingValues.size() > 0) {
			reportText += "\nFiles with missing properties missing in default languages:\n";
			reportText += Utilities.join(filesWithMissingValues, "\n") + "\n";
		}
		if (missingDefaultProperties.size() > 0) {
			reportText += "\nProperties missing in default languages:\n";
			reportText += Utilities.join(missingDefaultProperties, "\n") + "\n";
		}
		if (unusedProperties.size() > 0) {
			reportText += "\nProperties unused in all languages:\n";
			reportText += Utilities.join(unusedProperties, "\n");
		}

		owner.showData(LangResources.get("usagereport"), reportText);
	}
}
