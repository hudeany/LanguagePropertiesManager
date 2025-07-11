package de.soderer.languagepropertiesmanager.worker;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;

import de.soderer.languagepropertiesmanager.LanguagePropertiesException;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

public class LoadLanguagePropertiesWorker extends WorkerSimple<Boolean> {
	private final File languagePropertiesFileOrBasicDirectory;
	private final String[] excludeParts;

	private List<String> languagePropertiesSetNames;
	private List<LanguageProperty> languageProperties;
	private List<String> availableLanguageSigns;
	private boolean commentsFound;

	public LoadLanguagePropertiesWorker(final WorkerParentSimple parent, final File languagePropertiesFileOrBasicDirectory, final String[] excludeParts) {
		super(parent);

		this.languagePropertiesFileOrBasicDirectory = languagePropertiesFileOrBasicDirectory;
		this.excludeParts = excludeParts;
	}

	@Override
	public Boolean work() throws Exception {
		languagePropertiesSetNames = new ArrayList<>();

		if (languagePropertiesFileOrBasicDirectory == null) {
			throw new LanguagePropertiesException("Language properties file or basic directory parameter is empty");
		} else if (!languagePropertiesFileOrBasicDirectory.exists()) {
			throw new LanguagePropertiesException("Language properties file or basic directory '" + languagePropertiesFileOrBasicDirectory + "' does not exist");
		} else if (languagePropertiesFileOrBasicDirectory.isFile()) {
			parent.changeTitle(LangResources.get("loadingLanguageProperties"));

			final String filename = languagePropertiesFileOrBasicDirectory.getName();
			itemsToDo = 1;
			itemsDone = 0;
			String languagePropertiesSetName;
			if (filename.endsWith(".properties")) {
				if (filename.contains("_")) {
					languagePropertiesSetName = filename.substring(0, filename.indexOf("_"));
				} else {
					languagePropertiesSetName = filename.substring(0, filename.indexOf(".properties"));
				}
			} else {
				throw new Exception("Missing mandatory file extension '.properties'");
			}

			languageProperties = LanguagePropertiesFileSetReader.read(languagePropertiesFileOrBasicDirectory.getParentFile(), languagePropertiesSetName, false);
			languagePropertiesSetNames.add(languagePropertiesSetName);
		} else {
			parent.changeTitle(LangResources.get("searchingLanguageProperties"));
			signalUnlimitedProgress();

			final Collection<File> propertiesFiles = FileUtils.listFiles(languagePropertiesFileOrBasicDirectory, new RegexFileFilter("^.*_en.properties$||^.*_de.properties$"), DirectoryFileFilter.DIRECTORY);
			final Set<String> propertiesSetsPaths = new HashSet<>();
			for (final File propertiesFile : propertiesFiles) {
				boolean excluded = false;
				if (excludeParts != null) {
					for (final String excludePart : excludeParts) {
						if (propertiesFile.getAbsolutePath().contains(excludePart)) {
							excluded = true;
							break;
						}
					}
				}
				if (!excluded) {
					final String propertySetName = propertiesFile.getName().substring(0, propertiesFile.getName().indexOf("_"));
					final String propertiesSetsPath = propertiesFile.getParentFile().getAbsolutePath() + File.separator + propertySetName;
					propertiesSetsPaths.add(propertiesSetsPath);
				}
			}

			final List<String> propertiesPaths = new ArrayList<>(propertiesSetsPaths);
			Collections.sort(propertiesPaths);

			if (cancel()) {
				return !cancel;
			}

			parent.changeTitle(LangResources.get("loadingLanguageProperties"));
			itemsToDo = propertiesSetsPaths.size();
			itemsDone = 0;
			signalProgress(true);

			languageProperties = new ArrayList<>();
			for (final String propertiesPath : propertiesPaths) {
				final String layoutPropertySetName = new File(propertiesPath).getName();
				final List<LanguageProperty> nextLanguageProperties = LanguagePropertiesFileSetReader.read(new File(propertiesPath).getParentFile(), layoutPropertySetName, false);
				languageProperties.addAll(nextLanguageProperties);
				languagePropertiesSetNames.add(layoutPropertySetName);

				itemsDone++;
				signalProgress(false);
			}
		}

		itemsDone = itemsToDo;
		signalProgress(true);

		availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), "default");

		final Comparator<LanguageProperty> compareByPathAndIndex = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getOriginalIndex);
		languageProperties = languageProperties.stream().sorted(compareByPathAndIndex).collect(Collectors.toList());

		commentsFound = false;
		for (final LanguageProperty languageProperty : languageProperties) {
			if (Utilities.isNotEmpty(languageProperty.getComment())) {
				commentsFound = true;
				break;
			}
		}

		return !cancel;
	}

	public List<String> getLanguagePropertiesSetNames() {
		return languagePropertiesSetNames;
	}

	public List<LanguageProperty> getLanguageProperties() {
		return languageProperties;
	}

	public List<String> getAvailableLanguageSigns() {
		return availableLanguageSigns;
	}

	public boolean isCommentsFound() {
		return commentsFound;
	}

	@Override
	public String getResultText() {
		return null;
	}
}
