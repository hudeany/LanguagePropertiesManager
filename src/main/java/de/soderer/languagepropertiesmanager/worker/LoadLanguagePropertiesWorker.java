package de.soderer.languagepropertiesmanager.worker;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;

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
	private final String propertiesFileExtension;
	private boolean readComments = true;

	public LoadLanguagePropertiesWorker(final WorkerParentSimple parent, final File languagePropertiesFileOrBasicDirectory, final String[] excludeParts, final String propertiesFileExtension) {
		super(parent);

		this.languagePropertiesFileOrBasicDirectory = languagePropertiesFileOrBasicDirectory;
		this.excludeParts = excludeParts;
		this.propertiesFileExtension = propertiesFileExtension;
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
			if (filename.endsWith(propertiesFileExtension)) {
				if (filename.contains("_")) {
					languagePropertiesSetName = getLanguagePropertiesSetName(filename);
				} else {
					languagePropertiesSetName = filename.substring(0, filename.indexOf(propertiesFileExtension));
				}
			} else {
				throw new Exception("Missing mandatory file extension '" + propertiesFileExtension + "'");
			}

			languageProperties = LanguagePropertiesFileSetReader.read(languagePropertiesFileOrBasicDirectory.getParentFile(), languagePropertiesSetName, propertiesFileExtension, false, readComments);
			languagePropertiesSetNames.add(languagePropertiesSetName);
		} else {
			parent.changeTitle(LangResources.get("searchingLanguageProperties"));
			signalUnlimitedProgress();

			final Collection<File> propertiesFiles = FileUtils.listFiles(languagePropertiesFileOrBasicDirectory, languagePropertiesFilter, DirectoryFileFilter.DIRECTORY);

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
					final String propertySetName = getLanguagePropertiesSetName(propertiesFile.getName());
					final String propertiesSetsPath = propertiesFile.getParentFile().getAbsolutePath() + File.separator + propertySetName;
					propertiesSetsPaths.add(propertiesSetsPath);
				}
			}

			final List<String> propertiesPaths = new ArrayList<>(propertiesSetsPaths);
			Collections.sort(propertiesPaths);

			if (cancel) {
				return !cancel;
			}

			parent.changeTitle(LangResources.get("loadingLanguageProperties"));
			itemsToDo = propertiesSetsPaths.size();
			itemsDone = 0;
			signalProgress(true);

			languageProperties = new ArrayList<>();
			for (final String propertiesPath : propertiesPaths) {
				final String layoutPropertySetName = new File(propertiesPath).getName();
				final List<LanguageProperty> nextLanguageProperties = LanguagePropertiesFileSetReader.read(new File(propertiesPath).getParentFile(), layoutPropertySetName, propertiesFileExtension, false, readComments);
				languageProperties.addAll(nextLanguageProperties);
				languagePropertiesSetNames.add(layoutPropertySetName);

				itemsDone++;
				signalProgress(false);
			}
		}

		itemsDone = itemsToDo;
		signalProgress(true);

		availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);

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

	private String getLanguagePropertiesSetName(final String languagePropertiesFileName) {
		String baseName = languagePropertiesFileName.replaceFirst(Pattern.quote(propertiesFileExtension) + "$", "");
		baseName = baseName.replaceFirst("_[a-z]{2}(_[A-Z]{2}(_[A-Za-z0-9]+)?)?$", "");

		return baseName;
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

	public boolean isReadComments() {
		return readComments;
	}

	public void setReadComments(final boolean readComments) {
		this.readComments = readComments;
	}

	final IOFileFilter languagePropertiesFilter = new AbstractFileFilter() {
		@Override
		public boolean accept(final File file) {
			final String name = file.getName();
			if (!name.endsWith(propertiesFileExtension)) {
				return false;
			}
			final String baseName = name.substring(0, name.length() - propertiesFileExtension.length());
			final String[] parts = baseName.split("_");
			if (parts.length < 2) {
				return false;
			}

			final String lastPart = parts[parts.length - 1];
			final String secondLastPart = parts.length >= 3 ? parts[parts.length - 2] : null;

			if (lastPart.matches("[A-Z]{2}") && secondLastPart != null && secondLastPart.matches("[a-z]{2}")) {
				return true; // e.g. _de_AT
			} else if (lastPart.matches("[a-z]{2}")) {
				return true; // e.g. _de
			} else {
				return false;
			}
		}
	};
}
