package de.soderer.languagepropertiesmanager.dlg;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JPopupMenu;

import de.soderer.languagepropertiesmanager.LanguagePropertiesException;
import de.soderer.languagepropertiesmanager.LanguagePropertiesManager;
import de.soderer.languagepropertiesmanager.storage.LoadedLanguageProperties;
import de.soderer.languagepropertiesmanager.worker.ImportFromCsvWorker;
import de.soderer.languagepropertiesmanager.worker.ImportFromExcelWorker;
import de.soderer.languagepropertiesmanager.worker.LoadLanguagePropertiesWorker;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Result;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.swing.ComboSelectionDialog;
import de.soderer.utilities.swing.ProgressDialog;

/**
 * Reading of language properties from all supported sources (properties file,
 * directory, recent entry, Excel, CSV) without changing the loaded data, and
 * the menu of import sources for the merge import and the reduction by a base
 * set.
 */
final class ImportSourceChooser {
	private final LanguagePropertiesManagerDialog owner;
	private final ConfigurationProperties applicationConfiguration;

	ImportSourceChooser(final LanguagePropertiesManagerDialog owner, final ConfigurationProperties applicationConfiguration) {
		this.owner = owner;
		this.applicationConfiguration = applicationConfiguration;
	}

	boolean hasLanguagePropertiesFileExtension(final String filePath) {
		return new File(filePath).getName().endsWith(applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
	}

	/**
	 * Reads a single language properties set without changing the currently loaded data.
	 *
	 * @return the read properties or null if canceled by the user
	 */
	LoadedLanguageProperties readSingleLanguagePropertiesSet(final String filePath) throws ExecutionException {
		final LoadLanguagePropertiesWorker openFilesLanguagePropertiesWorker = new LoadLanguagePropertiesWorker(null, new File(filePath), null, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
		openFilesLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
		final ProgressDialog<LoadLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(owner, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("tooltip_load_files"), openFilesLanguagePropertiesWorker);
		final Result dialogResult = progressDialog.open();
		if (dialogResult == Result.CANCELED) {
			return null;
		} else {
			// check for errors
			openFilesLanguagePropertiesWorker.get();
			showDuplicateKeysWarning(openFilesLanguagePropertiesWorker.getDuplicateKeysByFile());

			return LoadedLanguageProperties.ofPropertiesSets(openFilesLanguagePropertiesWorker.getLanguageProperties(), openFilesLanguagePropertiesWorker.getLanguagePropertiesSetNames(), filePath);
		}
	}

	/**
	 * Reads all language properties sets of a directory without changing the currently loaded data.
	 *
	 * @return the read properties or null if canceled by the user
	 */
	LoadedLanguageProperties readAllLanguagePropertiesSets(final String basicDirectoryPath) throws ExecutionException {
		final String[] excludeParts = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES).split(";");
		final LoadLanguagePropertiesWorker openFolderLanguagePropertiesWorker = new LoadLanguagePropertiesWorker(null, new File(basicDirectoryPath), excludeParts, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
		openFolderLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
		final ProgressDialog<LoadLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(owner, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("load_folder"), openFolderLanguagePropertiesWorker);
		final Result dialogResult = progressDialog.open();
		if (dialogResult == Result.CANCELED) {
			return null;
		} else {
			// check for errors
			openFolderLanguagePropertiesWorker.get();
			showDuplicateKeysWarning(openFolderLanguagePropertiesWorker.getDuplicateKeysByFile());

			return LoadedLanguageProperties.ofPropertiesSets(openFolderLanguagePropertiesWorker.getLanguageProperties(), openFolderLanguagePropertiesWorker.getLanguagePropertiesSetNames(), basicDirectoryPath);
		}
	}

	/**
	 * Informs the user about keys that occur more than once within a single properties file.
	 * Only the first value of such a key was read, the later ones would be lost on the next save.
	 */
	private void showDuplicateKeysWarning(final Map<String, Set<String>> duplicateKeysByFile) {
		if (duplicateKeysByFile == null || duplicateKeysByFile.isEmpty()) {
			return;
		}

		int duplicateKeyCount = 0;
		final StringBuilder reportText = new StringBuilder();
		for (final Map.Entry<String, Set<String>> entry : duplicateKeysByFile.entrySet()) {
			duplicateKeyCount += entry.getValue().size();
			reportText.append(entry.getKey()).append("\n");
			for (final String duplicateKey : entry.getValue()) {
				reportText.append("    ").append(duplicateKey).append("\n");
			}
			reportText.append("\n");
		}

		owner.showData(LangResources.get("duplicateKeysInFiles_title"), LangResources.get("duplicateKeysInFiles", duplicateKeysByFile.size(), duplicateKeyCount) + "\n\n" + reportText.toString().trim());
	}

	/**
	 * Reads an Excel file without changing the currently loaded data.
	 *
	 * @return the read properties or null if canceled by the user
	 */
	LoadedLanguageProperties readFromExcel(final File file) throws Exception {
		final ImportFromExcelWorker importFromExcelWorker = new ImportFromExcelWorker(null, file);
		importFromExcelWorker.setIgnoreComments(applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
		final Result dialogResult = new ProgressDialog<>(owner, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("import_file"), importFromExcelWorker).open();
		if (dialogResult == Result.CANCELED) {
			return null;
		}
		// check for errors
		importFromExcelWorker.get();

		return LoadedLanguageProperties.ofSingleSet(importFromExcelWorker.getLanguageProperties(), importFromExcelWorker.getAvailableLanguageSigns(), importFromExcelWorker.getLanguagePropertiesSetName(), file.getAbsolutePath());
	}

	/**
	 * Reads a CSV file without changing the currently loaded data.
	 *
	 * @return the read properties or null if canceled by the user
	 */
	LoadedLanguageProperties readFromCsv(final File file) throws Exception {
		final ImportFromCsvWorker importFromCsvWorker = new ImportFromCsvWorker(null, file);
		importFromCsvWorker.setIgnoreComments(applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
		final Result dialogResult = new ProgressDialog<>(owner, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("import_file"), importFromCsvWorker).open();
		if (dialogResult == Result.CANCELED) {
			return null;
		}
		// check for errors
		importFromCsvWorker.get();

		return LoadedLanguageProperties.ofSingleSet(importFromCsvWorker.getLanguageProperties(), importFromCsvWorker.getAvailableLanguageSigns(), importFromCsvWorker.getLanguagePropertiesSetName(), file.getAbsolutePath());
	}

	/**
	 * Source of an import (merge import or reduction by a base set). Returns null
	 * if the user canceled, in which case the source has already informed the user.
	 */
	@FunctionalInterface
	private interface ImportSource {
		LoadedLanguageProperties read(String title) throws Exception;
	}

	/**
	 * Shows the menu of import sources below the given button. The properties
	 * read from the chosen source are handed to the given action.
	 */
	void showImportSourceMenu(final JButton invoker, final String titleKey, final Consumer<LoadedLanguageProperties> action) {
		try {
			final String title = LangResources.get(titleKey);
			final JPopupMenu importSourceMenu = new JPopupMenu();
			DialogUtilities.addMenuItem(importSourceMenu, "clock.png", "mergeImport_fromRecent", owner.getRecentlyOpenedDirectories() != null && owner.getRecentlyOpenedDirectories().size() > 0, () -> runWithImportSource(title, this::readImportSourceFromRecent, action));
			DialogUtilities.addMenuItem(importSourceMenu, "load.png", "mergeImport_fromFile", true, () -> runWithImportSource(title, this::readImportSourceFromFile, action));
			DialogUtilities.addMenuItem(importSourceMenu, "folderLoad.png", "mergeImport_fromFolder", true, () -> runWithImportSource(title, this::readImportSourceFromFolder, action));
			DialogUtilities.addMenuItem(importSourceMenu, "excelLoad.png", "mergeImport_fromExcel", true, () -> runWithImportSource(title, sourceTitle -> readImportSourceFromDataFile(sourceTitle, this::readFromExcel, "xlsx"), action));
			DialogUtilities.addMenuItem(importSourceMenu, "csvLoad.png", "mergeImport_fromCsv", true, () -> runWithImportSource(title, sourceTitle -> readImportSourceFromDataFile(sourceTitle, this::readFromCsv, "csv", "dsv"), action));
			importSourceMenu.show(invoker, 0, invoker.getHeight());
		} catch (final Exception e) {
			owner.showError(e);
		}
	}

	private LoadedLanguageProperties readImportSourceFromRecent(final String title) throws Exception {
		final ComboSelectionDialog dialog = new ComboSelectionDialog(owner, owner.getTitle() + " " + title, LangResources.get("recent_directories_dialog_text"), owner.getRecentlyOpenedDirectories(), DialogUtilities.getLastEntryIndex(owner.getRecentlyOpenedDirectories())).withSize(600, -1);
		final String filePath = dialog.open();

		// Take over a possible reordering (drag&drop) or deletion of the recent directories done in the dialog
		owner.getRecentlyOpenedDirectories().clear();
		owner.getRecentlyOpenedDirectories().addAll(dialog.getItems());
		applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, owner.getRecentlyOpenedDirectories());
		owner.checkButtonStatus();

		if (filePath == null) {
			owner.showErrorMessage(title, LangResources.get("canceledByUser"));
			return null;
		} else if (!new File(filePath).exists()) {
			owner.showErrorMessage(title, LangResources.get("error.recentPathDoesNotExistAnymore", filePath));
			return null;
		} else if (new File(filePath).isDirectory()) {
			return readImportSourceResult(title, readAllLanguagePropertiesSets(filePath));
		} else if (!hasLanguagePropertiesFileExtension(filePath)) {
			owner.showErrorMessage(title, LangResources.get("missingMandatoryFileExtension", applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION)));
			return null;
		} else {
			return readImportSourceResult(title, readSingleLanguagePropertiesSet(filePath));
		}
	}

	private LoadedLanguageProperties readImportSourceFromFile(final String title) throws Exception {
		final File file = DialogUtilities.chooseFileToOpen(owner, owner.getTitle() + " " + title, owner.getRecentlyOpenedDirectories().getLatestAdded());
		if (file == null) {
			owner.showErrorMessage(title, LangResources.get("canceledByUser"));
			return null;
		} else if (!file.isFile()) {
			throw new Exception("Selected language properties set path is not an existing file");
		} else if (!hasLanguagePropertiesFileExtension(file.getAbsolutePath())) {
			owner.showErrorMessage(title, LangResources.get("missingMandatoryFileExtension", applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION)));
			return null;
		} else {
			return readImportSourceResult(title, readSingleLanguagePropertiesSet(file.getAbsolutePath()));
		}
	}

	private LoadedLanguageProperties readImportSourceFromFolder(final String title) throws Exception {
		final File directory = DialogUtilities.chooseDirectory(owner, owner.getTitle() + " " + title, owner.getRecentlyOpenedDirectories().getLatestAdded());
		if (directory == null) {
			owner.showErrorMessage(title, LangResources.get("canceledByUser"));
			return null;
		} else if (!directory.isDirectory()) {
			throw new Exception("Selected language properties directory is not an existing directory");
		} else {
			return readImportSourceResult(title, readAllLanguagePropertiesSets(directory.getAbsolutePath()));
		}
	}

	/**
	 * Reader of an Excel or CSV file
	 */
	@FunctionalInterface
	private interface DataFileReader {
		LoadedLanguageProperties read(File file) throws Exception;
	}

	private LoadedLanguageProperties readImportSourceFromDataFile(final String title, final DataFileReader dataFileReader, final String... fileExtensions) throws Exception {
		final File importFile = DialogUtilities.chooseFileToOpen(owner, owner.getTitle() + " " + title, Utilities.replaceUsersHome("~" + File.separator + "Downloads"), fileExtensions);
		if (importFile == null) {
			owner.showErrorMessage(title, LangResources.get("canceledByUser"));
			return null;
		} else {
			return readImportSourceResult(title, dataFileReader.read(importFile));
		}
	}

	/**
	 * Shows the cancel message, if reading was canceled in the progress dialog
	 */
	private LoadedLanguageProperties readImportSourceResult(final String title, final LoadedLanguageProperties loadedLanguageProperties) {
		if (loadedLanguageProperties == null) {
			owner.showErrorMessage(title, LangResources.get("canceledByUser"));
		}
		return loadedLanguageProperties;
	}

	/**
	 * Reads the properties of an import source and hands them to the given action
	 * (merge import or reduction by a base set). Errors while reading leave the
	 * loaded data unchanged.
	 */
	private void runWithImportSource(final String title, final ImportSource importSource, final Consumer<LoadedLanguageProperties> action) {
		// Unapplied changes in the detail fields would get lost by the changed selection afterwards
		if (!owner.confirmDiscardDetailChanges()) {
			return;
		}

		try {
			final LoadedLanguageProperties loadedLanguageProperties = importSource.read(title);
			if (loadedLanguageProperties != null) {
				action.accept(loadedLanguageProperties);
			}
		} catch (final ExecutionException e) {
			if (e.getCause() != null && e.getCause() instanceof LanguagePropertiesException) {
				owner.showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, e.getCause().getMessage());
			} else {
				owner.showError(e);
			}
		} catch (final Exception e) {
			owner.showError(e);
		}
		owner.checkButtonStatus();
	}
}
