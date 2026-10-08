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
import org.apache.commons.io.filefilter.SuffixFileFilter;

import de.soderer.languagepropertiesmanager.LanguagePropertiesException;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetWriter;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

/**
 * Writes language properties into sets of language properties files.
 *
 * <p>
 * Without output directory every property is written to the set given by its
 * own path. With output directory, the sets are searched in that directory
 * (including subdirectories) by their set name and updated there, unknown sets
 * and properties without path are created directly in the output directory.
 * </p>
 */
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
	private boolean readComments = true;

	private List<String> listOfStoredProperties;

	/**
	 * Creates the write worker.
	 *
	 * @param parent
	 *            receiver of the progress signals, may be null
	 * @param languageProperties
	 *            properties to write, properties without path get a path assigned
	 * @param languagePropertySetName
	 *            set name for properties without path
	 * @param outputDirectory
	 *            directory to search the existing sets in and to create new sets in, or null to write every property to its own path
	 * @param excludeParts
	 *            path parts of existing files to ignore when searching the output directory (e.g. "/bin/"), may be null
	 * @param extendAndKeepExistingProperties
	 *            whether keys existing in the files, but not in the given properties, are kept
	 * @param propertiesFileExtension
	 *            file extension of the properties files, a missing leading dot is added
	 */
	public WriteLanguagePropertiesWorker(final WorkerParentSimple parent, final List<LanguageProperty> languageProperties, final String languagePropertySetName, final File outputDirectory, final String[] excludeParts, final boolean extendAndKeepExistingProperties, final String propertiesFileExtension) {
		super(parent);

		this.languageProperties = languageProperties;
		this.languagePropertySetName = languagePropertySetName;
		this.outputDirectory = outputDirectory;
		this.excludeParts = excludeParts;
		this.extendAndKeepExistingProperties = extendAndKeepExistingProperties;
		// Same normalization as the reader, otherwise a configured "properties" (without dot) would not find the existing files
		this.propertiesFileExtension = LanguagePropertiesFileSetReader.normalizePropertiesFileExtension(propertiesFileExtension);
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
			boolean hasPropertiesWithoutPath = false;
			for (final LanguageProperty languageProperty : languageProperties) {
				if (Utilities.isNotBlank(languageProperty.getPath())) {
					languagePropertiesPaths.add(languageProperty.getPath());
				} else {
					hasPropertiesWithoutPath = true;
				}
			}

			if (languagePropertiesPaths.size() > 0 || hasPropertiesWithoutPath) {
				final List<String> existingPropertiesPaths = getAllPropertiesPaths(outputDirectory);

				final Comparator<LanguageProperty> compareByIndex = Comparator.comparing(LanguageProperty::getPath, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(LanguageProperty::getOriginalIndex);

				// Properties without a path yet are handled as one additional group, identified by languagePropertySetName
				final List<String> groupsToProcess = new ArrayList<>(languagePropertiesPaths);
				if (hasPropertiesWithoutPath) {
					groupsToProcess.add(null);
				}

				itemsToDo = groupsToProcess.size();
				itemsDone = 0;

				listOfStoredProperties = new ArrayList<>();
				for (final String languagePropertiesPath : groupsToProcess) {
					final boolean isNewGroup = languagePropertiesPath == null;
					int foundAmount = 0;
					String foundPath = null;
					final String propertySetName = isNewGroup ? languagePropertySetName : new File(languagePropertiesPath).getName();
					if (!isNewGroup) {
						// A set existing at its own path is preferred, so sets with the same name in different
						// subdirectories (e.g. "messages" of several modules) are no ambiguity
						final String ownAbsolutePath = new File(Utilities.replaceUsersHome(languagePropertiesPath)).getAbsolutePath();
						if (existingPropertiesPaths.contains(ownAbsolutePath)) {
							foundPath = ownAbsolutePath;
							foundAmount = 1;
						}
					}
					if (foundPath == null) {
						for (final String existingPropertiesPath : existingPropertiesPaths) {
							final String existingPropertieSetName = new File(existingPropertiesPath).getName();
							if (existingPropertieSetName.equals(propertySetName)) {
								foundPath = existingPropertiesPath;
								foundAmount++;
							}
						}
					}
					if (foundAmount > 1) {
						throw new LanguagePropertiesException("Found multiple storage paths for language properties set: " + propertySetName);
					} else {
						final List<LanguageProperty> languagePropertiesForStorage = isNewGroup
								? languageProperties.stream().filter(o -> Utilities.isBlank(o.getPath())).sorted(compareByIndex).collect(Collectors.toList())
								: languageProperties.stream().filter(o -> languagePropertiesPath.equals(o.getPath())).sorted(compareByIndex).collect(Collectors.toList());

						if (foundAmount == 1) {
							// Update existing properties set files
							for (final LanguageProperty languageProperty : languagePropertiesForStorage) {
								languageProperty.setPath(Utilities.replaceUsersHomeByTilde(new File(foundPath).getAbsolutePath()));
							}
							LanguagePropertiesFileSetWriter.write(languagePropertiesForStorage, new File(foundPath).getParentFile(), new File(foundPath).getName(), extendAndKeepExistingProperties, propertiesFileExtension, readComments);
							listOfStoredProperties.add(foundPath);
						} else {
							// Create new properties set files
							for (final LanguageProperty languageProperty : languagePropertiesForStorage) {
								languageProperty.setPath(Utilities.replaceUsersHomeByTilde(new File(outputDirectory, propertySetName).getAbsolutePath()));
							}
							LanguagePropertiesFileSetWriter.write(languagePropertiesForStorage, outputDirectory, propertySetName, extendAndKeepExistingProperties, propertiesFileExtension, readComments);
							listOfStoredProperties.add(new File(outputDirectory, propertySetName).getAbsolutePath());
						}
					}

					itemsDone++;
					signalProgress(false);
				}
			} else {
				// Store only one language properties set which has no file path defined in LanguageProperty objects
				LanguagePropertiesFileSetWriter.write(languageProperties, outputDirectory, languagePropertySetName, extendAndKeepExistingProperties, propertiesFileExtension, readComments);
			}
		} else {
			// Without output directory every property must have its own path
			for (final LanguageProperty languageProperty : languageProperties) {
				if (Utilities.isBlank(languageProperty.getPath())) {
					throw new LanguagePropertiesException("Property '" + languageProperty.getKey() + "' has no path and no outputDirectory was given");
				}
			}

			final Set<String> languagePropertiesPaths = new HashSet<>();
			for (final LanguageProperty languageProperty : languageProperties) {
				languagePropertiesPaths.add(languageProperty.getPath());
			}

			final Comparator<LanguageProperty> compareByIndex = Comparator
				.comparing(LanguageProperty::getPath)
				.thenComparing(LanguageProperty::getOriginalIndex);

			itemsToDo = languagePropertiesPaths.size();
			itemsDone = 0;

			listOfStoredProperties = new ArrayList<>();
			for (final String languagePropertiesPath : languagePropertiesPaths) {
				final String propertySetName = new File(languagePropertiesPath).getName();
				final List<LanguageProperty> languagePropertiesForStorage = languageProperties.stream()
						.filter(o -> Utilities.replaceUsersHome(o.getPath()).equals(Utilities.replaceUsersHome(languagePropertiesPath)))
						.sorted(compareByIndex).collect(Collectors.toList());

				LanguagePropertiesFileSetWriter.write(languagePropertiesForStorage,
						new File(languagePropertiesPath).getParentFile(), propertySetName,
						extendAndKeepExistingProperties, propertiesFileExtension, readComments);
				listOfStoredProperties.add(languagePropertiesPath);

				itemsDone++;
				signalProgress(false);
			}
		}

		itemsDone = itemsToDo;
		signalProgress(true);

		return !cancel;
	}

	private List<String> getAllPropertiesPaths(final File basicDirectory) {
		final Collection<File> propertiesFiles = FileUtils.listFiles(basicDirectory, new SuffixFileFilter(propertiesFileExtension), DirectoryFileFilter.DIRECTORY);

		final Set<String> propertiesSetsPaths = new HashSet<>();
		for (final File propertiesFile : propertiesFiles) {
			boolean excluded = false;
			if (excludeParts != null) {
				for (final String excludePart : excludeParts) {
					// An empty part (e.g. from an empty configuration or ";;") would exclude every file
					if (Utilities.isNotEmpty(excludePart) && propertiesFile.getAbsolutePath().contains(excludePart.replace("\\\\", "\\"))) {
						excluded = true;
						break;
					}
				}
			}
			if (!excluded) {
				final String propertySetName = LanguagePropertiesFileSetReader.getPropertySetBaseName(propertiesFile.getName(), propertiesFileExtension);
				if (propertySetName != null) {
					final String propertiesSetsPath = propertiesFile.getParentFile().getAbsolutePath() + File.separator + propertySetName;
					propertiesSetsPaths.add(propertiesSetsPath);
				}
			}
		}

		final List<String> returnList = new ArrayList<>(propertiesSetsPaths);
		Collections.sort(returnList);
		return returnList;
	}

	/**
	 * Paths of the written properties sets, available after writing.
	 *
	 * @return the written set paths
	 */
	public List<String> getListOfStoredProperties() {
		return listOfStoredProperties;
	}

	@Override
	public String getResultText() {
		return null;
	}

	/**
	 * Whether comments of kept existing properties are read (see extendAndKeepExistingProperties).
	 *
	 * @return true if comments are read
	 */
	public boolean isReadComments() {
		return readComments;
	}

	/**
	 * Sets whether comments of kept existing properties are read. Must be set before writing is started.
	 *
	 * @param readComments
	 *            true to read comments
	 */
	public void setReadComments(final boolean readComments) {
		this.readComments = readComments;
	}
}
