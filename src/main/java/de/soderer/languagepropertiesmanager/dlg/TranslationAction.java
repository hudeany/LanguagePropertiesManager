package de.soderer.languagepropertiesmanager.dlg;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JPopupMenu;

import de.soderer.languagepropertiesmanager.LanguagePropertiesManager;
import de.soderer.languagepropertiesmanager.TranslationConstants;
import de.soderer.languagepropertiesmanager.model.LanguagePropertiesModel;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.languagepropertiesmanager.worker.TranslateLanguagePropertiesWorker;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.DeepLHelper;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Result;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.swing.ComboSelectionDialog;
import de.soderer.utilities.swing.ProgressDialog;
import de.soderer.utilities.swing.SimpleInputDialog;

/**
 * Translation of language values with DeepL into one or all other languages
 * (translate button of the main window). Only the selected properties are
 * translated, or all shown properties if nothing is selected.
 */
final class TranslationAction {
	private final LanguagePropertiesManagerDialog owner;
	private final ConfigurationProperties applicationConfiguration;
	private final LanguagePropertiesModel model;

	TranslationAction(final LanguagePropertiesManagerDialog owner, final LanguagePropertiesModel model, final ConfigurationProperties applicationConfiguration) {
		this.owner = owner;
		this.model = model;
		this.applicationConfiguration = applicationConfiguration;
	}

	/**
	 * Shows the menu below the translate button to choose between translating
	 * into one selected target language or into all other available languages.
	 */
	void showTranslateMenu(final JButton invoker) {
		try {
			final JPopupMenu translateMenu = new JPopupMenu();
			DialogUtilities.addMenuItem(translateMenu, "translate.png", "translate_toOneTargetLanguage", true, () -> translate(false));
			DialogUtilities.addMenuItem(translateMenu, "translate.png", "translate_toAllTargetLanguages", true, () -> translate(true));
			translateMenu.show(invoker, 0, invoker.getHeight());
		} catch (final Exception e) {
			owner.showError(e);
		}
	}

	private void translate(final boolean allTargetLanguages) {
		try {
			if (Utilities.isBlank(applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY))) {
				final String deeplApiKey = new SimpleInputDialog(owner, owner.getTitle(), LangResources.get("enterDeeplApiKey")).open();
				if (deeplApiKey != null) {
					applicationConfiguration.set(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY, deeplApiKey);
				}
			}

			if (Utilities.isBlank(applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY))) {
				owner.showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("missingDeeplApiKey"));
				return;
			}

			TranslationConstants translationConstants = null;
			final String translationConstantsFilePath = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_TRANSLATION_CONSTANTS_FILE);
			if (Utilities.isNotBlank(translationConstantsFilePath)) {
				try {
					translationConstants = TranslationConstants.read(new File(translationConstantsFilePath.trim()));
				} catch (final Exception e) {
					owner.showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("errorReadingTranslationConstantsFile", translationConstantsFilePath, e.getMessage()));
					return;
				}
			}

			final String deeplBaseUrl = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_BASEURL);
			final DeepLHelper deepLHelper = new DeepLHelper(deeplBaseUrl, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY), applicationConfiguration.getProxyConfiguration().getProxy(deeplBaseUrl));

			// The source language is always required, also when translating into all target languages
			final String languageSignTranslateSource = new ComboSelectionDialog(owner, owner.getTitle(), LangResources.get("selectSourceLanguageSignToTranslate"), model.getAvailableLanguageSigns(), 0).open();
			if (Utilities.isBlank(languageSignTranslateSource)) {
				return;
			}
			String sourceLanguage = languageSignTranslateSource;
			if ("Default".equalsIgnoreCase(sourceLanguage)) {
				sourceLanguage = new ComboSelectionDialog(owner, owner.getTitle(), LangResources.get("selectDefaultLanguageToTranslate"), deepLHelper.getSupportedLanguages(), deepLHelper.getSupportedLanguages().indexOf("EN")).open();
				if (Utilities.isBlank(sourceLanguage)) {
					return;
				}
			}
			if (sourceLanguage.contains("_")) {
				sourceLanguage = sourceLanguage.substring(0, sourceLanguage.indexOf("_"));
			}

			final List<String> availableOtherLanguageSigns = new ArrayList<>(model.getAvailableLanguageSigns());
			availableOtherLanguageSigns.remove(languageSignTranslateSource);

			final List<String> languageSignsTranslateTarget = new ArrayList<>();
			if (allTargetLanguages || availableOtherLanguageSigns.size() == 1) {
				languageSignsTranslateTarget.addAll(availableOtherLanguageSigns);
			} else {
				final String languageSignTranslateTarget = new ComboSelectionDialog(owner, owner.getTitle(), LangResources.get("selectTargetLanguageSignToTranslate"), availableOtherLanguageSigns).open();
				if (Utilities.isBlank(languageSignTranslateTarget)) {
					return;
				}
				languageSignsTranslateTarget.add(languageSignTranslateTarget);
			}

			// Only fetched once and only when needed to filter the target languages not supported by DeepL
			final List<String> supportedLanguages = allTargetLanguages ? deepLHelper.getSupportedLanguages() : null;

			// Map of target language sign to the DeepL target language
			final Map<String, String> targetLanguages = new LinkedHashMap<>();
			final List<String> unsupportedLanguageSigns = new ArrayList<>();
			for (final String languageSignTranslateTarget : languageSignsTranslateTarget) {
				String targetLanguage = languageSignTranslateTarget;
				if ("Default".equalsIgnoreCase(targetLanguage)) {
					targetLanguage = new ComboSelectionDialog(owner, owner.getTitle(), LangResources.get("selectDefaultLanguageToTranslate"), deepLHelper.getSupportedLanguages(), deepLHelper.getSupportedLanguages().indexOf("EN")).open();
					if (Utilities.isBlank(targetLanguage)) {
						return;
					}
				}
				if (targetLanguage.contains("_")) {
					targetLanguage = targetLanguage.substring(0, targetLanguage.indexOf("_"));
				}

				if (supportedLanguages != null && !isSupportedLanguage(supportedLanguages, targetLanguage)) {
					unsupportedLanguageSigns.add(languageSignTranslateTarget);
				} else {
					targetLanguages.put(languageSignTranslateTarget, targetLanguage);
				}
			}

			if (targetLanguages.isEmpty()) {
				owner.showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("translate_skippedUnsupportedTargetLanguages", String.join(", ", unsupportedLanguageSigns)));
				return;
			}

			// Only restrict to the selected rows if any are selected, otherwise translate all properties
			final List<LanguageProperty> languagePropertiesToTranslate = owner.getSelectedOrAllProperties();

			int countTranslations = 0;
			final List<String> translateErrorMessages = new ArrayList<>();
			try {
				for (final Map.Entry<String, String> targetLanguageEntry : targetLanguages.entrySet()) {
					final TranslateLanguagePropertiesWorker translateLanguagePropertiesWorker = new TranslateLanguagePropertiesWorker(null, languagePropertiesToTranslate, deepLHelper, languageSignTranslateSource, targetLanguageEntry.getKey(), sourceLanguage, targetLanguageEntry.getValue(), translationConstants);
					String progressText = LangResources.get("translatingLanguageProperties");
					if (targetLanguages.size() > 1) {
						progressText += " (" + targetLanguageEntry.getKey() + ")";
					}
					final ProgressDialog<TranslateLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(owner, LanguagePropertiesManager.APPLICATION_NAME, progressText, translateLanguagePropertiesWorker);
					final Result dialogResult = progressDialog.open();
					try {
						if (dialogResult != Result.CANCELED) {
							// check for errors
							translateLanguagePropertiesWorker.get();
						}
					} finally {
						countTranslations += translateLanguagePropertiesWorker.getCountTranslations();
					}

					if (Utilities.isNotBlank(translateLanguagePropertiesWorker.getTranslateErrorMessage())) {
						if (targetLanguages.size() > 1) {
							translateErrorMessages.add(targetLanguageEntry.getKey() + ": " + translateLanguagePropertiesWorker.getTranslateErrorMessage());
						} else {
							translateErrorMessages.add(translateLanguagePropertiesWorker.getTranslateErrorMessage());
						}
					}

					if (dialogResult == Result.CANCELED) {
						// Canceling stops the translation into the remaining target languages too
						break;
					}
				}
			} finally {
				// Keep the translations done so far, even if a later target language failed
				owner.setupTable();
				if (countTranslations > 0) {
					model.setUnsavedChanges(true);
				}
			}

			if (!translateErrorMessages.isEmpty()) {
				owner.showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, String.join("\n", translateErrorMessages));
			}

			String resultMessage = LangResources.get("addedTranslations", countTranslations);
			if (!unsupportedLanguageSigns.isEmpty()) {
				resultMessage += "\n" + LangResources.get("translate_skippedUnsupportedTargetLanguages", String.join(", ", unsupportedLanguageSigns));
			}
			owner.showMessage(LanguagePropertiesManager.APPLICATION_NAME, resultMessage);
		} catch (final Exception ex) {
			owner.showError(ex);
		}
		owner.checkButtonStatus();
	}

	/**
	 * Checks case-insensitively whether DeepL supports the language, also accepting regional
	 * variants like "EN-GB" or "PT-BR" for a plain language code like "en" or "pt".
	 */
	private static boolean isSupportedLanguage(final List<String> supportedLanguages, final String language) {
		final String languageUpperCase = language.toUpperCase(Locale.ROOT);
		for (final String supportedLanguage : supportedLanguages) {
			final String supportedLanguageUpperCase = supportedLanguage.toUpperCase(Locale.ROOT);
			if (supportedLanguageUpperCase.equals(languageUpperCase) || supportedLanguageUpperCase.startsWith(languageUpperCase + "-")) {
				return true;
			}
		}
		return false;
	}
}
