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
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;

import de.soderer.languagepropertiesmanager.LanguagePropertiesException;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetWriter;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

public class WriteLanguagePropertiesWorker extends WorkerSimple<Boolean> {
	/**
	 *  Only used if not defined in LanguageProperty Path
	 */
	private final String languagePropertySetName;

	private final List<LanguageProperty> languageProperties;
	private final File outputDirectory;
	private final String[] excludeParts;
	private final boolean extendAndKeepExistingProperties;
	private final String propertiesFileExtension;

	private List<String> listOfStoredProperties;

	public WriteLanguagePropertiesWorker(final WorkerParentSimple parent, final List<LanguageProperty> languageProperties, final String languagePropertySetName, final File outputDirectory, final String[] excludeParts, final boolean extendAndKeepExistingProperties, final String propertiesFileExtension) {
		super(parent);

		this.languageProperties = languageProperties;
		this.languagePropertySetName = languagePropertySetName;
		this.outputDirectory = outputDirectory;
		this.excludeParts = excludeParts;
		this.extendAndKeepExistingProperties = extendAndKeepExistingProperties;
		this.propertiesFileExtension = propertiesFileExtension;
	}

	@Override
	public Boolean work() throws Exception {
		parent.changeTitle("Writing language properties");

		signalUnlimitedProgress();

		if (outputDirectory != null) {
			if (!outputDirectory.exists() || !outputDirectory.isDirectory()) {
				throw new LanguagePropertiesException("Output directory for language properties set does not exist: " + outputDirectory.getAbsolutePath());
			}

			final Set<String> languagePropertiesPaths = new HashSet<>();
			for (final LanguageProperty languageProperty : languageProperties) {
				if (Utilities.isNotBlank(languageProperty.getPath())) {
					languagePropertiesPaths.add(languageProperty.getPath());
				}
			}

			if (languagePropertiesPaths.size() > 0) {
				final List<String> existingPropertiesPaths = getAllPropertiesPaths(outputDirectory);

				final Comparator<LanguageProperty> compareByIndex = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getOriginalIndex);

				itemsToDo = languagePropertiesPaths.size();
				itemsDone = 0;

				listOfStoredProperties = new ArrayList<>();
				for (final String languagePropertiesPath : languagePropertiesPaths) {
					int foundAmount = 0;
					String foundPath = null;
					final String propertySetName = new File(languagePropertiesPath).getName();
					for (final String existingPropertiesPath : existingPropertiesPaths) {
						final String existingPropertieSetName = new File(existingPropertiesPath).getName();
						if (existingPropertieSetName.equals(propertySetName)) {
							foundPath = existingPropertiesPath;
							foundAmount++;
						}
					}
					if (foundAmount > 1) {
						throw new LanguagePropertiesException("Found multiple storage paths for language properties set: " + propertySetName);
					} else {
						final List<LanguageProperty> languagePropertiesForStorage = languageProperties.stream().filter(o -> o.getPath().equals(languagePropertiesPath)).sorted(compareByIndex).collect(Collectors.toList());

						if (foundAmount == 1) {
							// Update existing properties set files
							for (final LanguageProperty languageProperty : languagePropertiesForStorage) {
								languageProperty.setPath(Utilities.replaceUsersHomeByTilde(new File(foundPath).getAbsolutePath()));
							}
							LanguagePropertiesFileSetWriter.write(languagePropertiesForStorage, new File(foundPath).getParentFile(), new File(foundPath).getName(), extendAndKeepExistingProperties, propertiesFileExtension);
							listOfStoredProperties.add(foundPath);
						} else {
							// Create new properties set files
							for (final LanguageProperty languageProperty : languagePropertiesForStorage) {
								languageProperty.setPath(Utilities.replaceUsersHomeByTilde(new File(outputDirectory, propertySetName).getAbsolutePath()));
							}
							LanguagePropertiesFileSetWriter.write(languagePropertiesForStorage, outputDirectory, propertySetName, extendAndKeepExistingProperties, propertiesFileExtension);
							listOfStoredProperties.add(new File(outputDirectory, propertySetName).getAbsolutePath());
						}
					}

					itemsDone++;
					signalProgress(false);
				}
			} else {
				// Store only one language properties set which has no file path defined in LanguageProperty objects
				LanguagePropertiesFileSetWriter.write(languageProperties, outputDirectory, languagePropertySetName, extendAndKeepExistingProperties, propertiesFileExtension);
			}
		} else {
			final Set<String> languagePropertiesPaths = new HashSet<>();
			for (final LanguageProperty languageProperty : languageProperties) {
				languagePropertiesPaths.add(languageProperty.getPath());
			}

			final Comparator<LanguageProperty> compareByIndex = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getOriginalIndex);

			itemsToDo = languagePropertiesPaths.size();
			itemsDone = 0;

			listOfStoredProperties = new ArrayList<>();
			for (final String languagePropertiesPath : languagePropertiesPaths) {
				String languagePropertiesPathToStore;
				if (Utilities.isBlank(languagePropertiesPath)) {
					languagePropertiesPathToStore = outputDirectory.getAbsolutePath();
				} else {
					languagePropertiesPathToStore = languagePropertiesPath;
				}

				// Update existing properties set files
				for (final LanguageProperty languageProperty : languageProperties) {
					if (Utilities.isBlank(languageProperty.getPath())) {
						languageProperty.setPath(Utilities.replaceUsersHomeByTilde(languagePropertiesPathToStore));
					}
				}

				final String propertySetName = new File(languagePropertiesPathToStore).getName();
				final List<LanguageProperty> languagePropertiesForStorage = languageProperties.stream().filter(o -> Utilities.replaceUsersHome(o.getPath()).equals(Utilities.replaceUsersHome(languagePropertiesPathToStore))).sorted(compareByIndex).collect(Collectors.toList());

				LanguagePropertiesFileSetWriter.write(languagePropertiesForStorage, new File(languagePropertiesPathToStore).getParentFile(), propertySetName, extendAndKeepExistingProperties, propertiesFileExtension);
				listOfStoredProperties.add(languagePropertiesPathToStore);

				itemsDone++;
				signalProgress(false);
			}
		}

		itemsDone = itemsToDo;
		signalProgress(true);

		return !cancel;
	}

	private List<String> getAllPropertiesPaths(final File basicDirectory) {
		final Collection<File> propertiesFiles = FileUtils.listFiles(basicDirectory, new RegexFileFilter("^.*_en" + Pattern.quote(propertiesFileExtension) + "$||^.*_de" + Pattern.quote(propertiesFileExtension) + "$"), DirectoryFileFilter.DIRECTORY);
		final Set<String> propertiesSetsPaths = new HashSet<>();
		for (final File propertiesFile : propertiesFiles) {
			boolean excluded = false;
			if (excludeParts != null) {
				for (final String excludePart : excludeParts) {
					if (propertiesFile.getAbsolutePath().contains(excludePart.replace("\\\\", "\\"))) {
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
		final List<String> returnList = new ArrayList<>(propertiesSetsPaths);
		Collections.sort(returnList);
		return returnList;
	}

	public List<String> getListOfStoredProperties() {
		return listOfStoredProperties;
	}

	@Override
	public String getResultText() {
		return null;
	}
}
