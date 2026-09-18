package de.soderer.languagepropertiesmanager.worker;

import java.util.List;

import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.DeepLHelper;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

/**
 * Translates the language values of a set of {@link LanguageProperty} items from one language
 * sign to another via DeepL, running in the background so a {@code ProgressDialog} can show
 * progress and allow cancellation.
 */
public class TranslateLanguagePropertiesWorker extends WorkerSimple<Boolean> {
	private final List<LanguageProperty> languagePropertiesToTranslate;
	private final DeepLHelper deepLHelper;
	private final String languageSignSource;
	private final String languageSignTarget;
	private final String sourceLanguage;
	private final String targetLanguage;

	private int countTranslations = 0;
	private String translateErrorMessage = null;

	public TranslateLanguagePropertiesWorker(final WorkerParentSimple parent, final List<LanguageProperty> languagePropertiesToTranslate, final DeepLHelper deepLHelper,
			final String languageSignSource, final String languageSignTarget, final String sourceLanguage, final String targetLanguage) {
		super(parent);

		this.languagePropertiesToTranslate = languagePropertiesToTranslate;
		this.deepLHelper = deepLHelper;
		this.languageSignSource = languageSignSource;
		this.languageSignTarget = languageSignTarget;
		this.sourceLanguage = sourceLanguage;
		this.targetLanguage = targetLanguage;
	}

	@Override
	public Boolean work() throws Exception {
		parent.changeTitle(LangResources.get("translatingLanguageProperties"));

		itemsToDo = languagePropertiesToTranslate.size();
		itemsDone = 0;
		signalProgress(true);

		for (final LanguageProperty languageProperty : languagePropertiesToTranslate) {
			if (cancel) {
				break;
			}

			final String sourceValue = languageProperty.getLanguageValue(languageSignSource);
			if (Utilities.isNotBlank(sourceValue)) {
				String targetValue = languageProperty.getLanguageValue(languageSignTarget);
				if (Utilities.isEmpty(targetValue)) {
					try {
						targetValue = deepLHelper.translate(sourceLanguage, sourceValue, targetLanguage);
					} catch (final Exception e) {
						// Maybe license limits are reached: stop translating, but keep what was done so far
						translateErrorMessage = "Translate error: " + e.getMessage();
						break;
					}
					languageProperty.setLanguageValue(languageSignTarget, targetValue);
					countTranslations++;
				}
			}

			itemsDone++;
			signalProgress(false);
		}

		itemsDone = itemsToDo;
		signalProgress(true);

		return !cancel;
	}

	public int getCountTranslations() {
		return countTranslations;
	}

	public String getTranslateErrorMessage() {
		return translateErrorMessage;
	}
}
