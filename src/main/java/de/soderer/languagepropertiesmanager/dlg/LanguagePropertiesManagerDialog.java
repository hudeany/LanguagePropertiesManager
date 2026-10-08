package de.soderer.languagepropertiesmanager.dlg;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.WindowConstants;

import de.soderer.languagepropertiesmanager.LanguagePropertiesException;
import de.soderer.languagepropertiesmanager.LanguagePropertiesManager;
import de.soderer.languagepropertiesmanager.check.ErrorReport;
import de.soderer.languagepropertiesmanager.check.LanguagePropertiesChecker;
import de.soderer.languagepropertiesmanager.image.ImageManager;
import de.soderer.languagepropertiesmanager.merge.BaseSetReducer;
import de.soderer.languagepropertiesmanager.merge.LanguagePropertiesMerger;
import de.soderer.languagepropertiesmanager.merge.MergeMode;
import de.soderer.languagepropertiesmanager.merge.MergePlan;
import de.soderer.languagepropertiesmanager.merge.MergeResult;
import de.soderer.languagepropertiesmanager.merge.ReducePlan;
import de.soderer.languagepropertiesmanager.merge.ReduceResult;
import de.soderer.languagepropertiesmanager.model.DuplicatesPlan;
import de.soderer.languagepropertiesmanager.model.LanguagePropertiesModel;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.languagepropertiesmanager.storage.LoadedLanguageProperties;
import de.soderer.languagepropertiesmanager.worker.ExportToCsvWorker;
import de.soderer.languagepropertiesmanager.worker.ExportToExcelWorker;
import de.soderer.languagepropertiesmanager.worker.WriteLanguagePropertiesWorker;
import de.soderer.network.NetworkUtilities;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Result;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.collection.UniqueFifoQueuedList;
import de.soderer.utilities.swing.ApplicationConfigurationDialog;
import de.soderer.utilities.swing.ComboSelectionDialog;
import de.soderer.utilities.swing.ErrorDialog;
import de.soderer.utilities.swing.ProgressDialog;
import de.soderer.utilities.swing.QuestionDialog;
import de.soderer.utilities.swing.ShowDataDialog;
import de.soderer.utilities.swing.SimpleInputDialog;
import de.soderer.utilities.swing.SwingColor;
import de.soderer.utilities.swing.UpdateableGuiApplication;

/**
 * Main window of the GUI: buttons for all actions, the properties table (left)
 * and the detail view of the selected property (right).
 */
public class LanguagePropertiesManagerDialog extends UpdateableGuiApplication {
	private static final long serialVersionUID = 3371684406925137014L;

	private static final int ICON_BUTTON_SIZE = 28;

	/** Title above the table, shows the name of the loaded properties set */
	private JLabel propertiesLabel;
	/** Deletes the selected properties */
	private JButton removeButton;
	/** Saves the properties into their own paths */
	private JButton saveButton;
	/** Saves the properties into a directory */
	private JButton folderSaveButton;
	/** Exports the properties into an Excel file */
	private JButton exportToExcelButton;
	/** Exports the properties into a CSV file */
	private JButton exportToCsvButton;
	/** Clears the selection, so the detail view can be used to add a new property */
	private JButton addButton;
	/** Loaded data and the state shared by table and detail view (selection, unsaved changes, set name) */
	private final LanguagePropertiesModel model = new LanguagePropertiesModel();

	/** Search box and properties table (left part below the buttons) */
	private PropertiesTablePanel tablePanel;
	/** Detail view of the selected property (right part) */
	private PropertyDetailPanel detailPanel;
	/** Split pane between table (left) and detail view (right) */
	private final JSplitPane mainSplitPane;
	/** Divider location before the detail view was hidden, to restore it when shown again */
	private int dividerLocationBeforeHidingDetail = -1;

	/** Checks the usage of the properties in source files with new settings */
	private JButton checkUsageButton;
	/** Checks the usage of the properties in source files with recent settings */
	private JButton checkUsageButtonPrevious;
	/** Adds a language */
	private JButton addLanguageButton;
	/** Deletes a language */
	private JButton deleteLanguageButton;
	/** Translates values with DeepL */
	private JButton translateButton;
	/** Copies values from one language into another */
	private JButton transferButton;
	/** Clears values identical to another language */
	private JButton clearIdenticalButton;
	/** Removes properties with the same path and key */
	private JButton removeDuplicatesButton;
	/** Checks the properties for errors */
	private JButton checkErrorsButton;
	/** Shows statistics of the loaded properties */
	private JButton showStatisticsButton;

	/** Loads a recently opened file or directory */
	private JButton loadRecentButton;
	/** Removes values identical to a base set */
	private JButton reduceByBaseSetButton;

	/** Recently opened files and directories, the latest used is the last entry */
	private UniqueFifoQueuedList<String> recentlyOpenedDirectories;
	/** Configuration of the application, saved on changes */
	private final ConfigurationProperties applicationConfiguration;

	/** Reads properties from all supported sources (load, merge import, reduction by base set) */
	private final ImportSourceChooser importSourceChooser;
	/** Translation with DeepL */
	private final TranslationAction translationAction;
	/** Statistics and usage check */
	private final StatisticsAndUsage statisticsAndUsage;

	/**
	 * Creates the main window. It is shown by the caller.
	 *
	 * @param applicationConfiguration
	 *            configuration of the application
	 * @throws Exception
	 *             if the window cannot be created (e.g. a missing icon)
	 */
	public LanguagePropertiesManagerDialog(final ConfigurationProperties applicationConfiguration) throws Exception {
		super(LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.KEYSTORE_FILE);

		this.applicationConfiguration = applicationConfiguration;
		importSourceChooser = new ImportSourceChooser(this, applicationConfiguration);
		translationAction = new TranslationAction(this, model, applicationConfiguration);
		statisticsAndUsage = new StatisticsAndUsage(this, model, applicationConfiguration);
		loadConfiguration();

		setIconImage(ImageManager.getImage("LanguagePropertiesManager.png").getImage());
		setTitle(LangResources.get("window_title"));

		mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createLeftPart(), createRightPart());
		mainSplitPane.setResizeWeight(0.5);
		mainSplitPane.setContinuousLayout(true);
		setContentPane(mainSplitPane);

		// The label above the table and the save button follow the model
		model.addPropertyChangeListener(event -> {
			if (LanguagePropertiesModel.PROPERTY_LANGUAGE_PROPERTIES_SET_NAME.equals(event.getPropertyName())) {
				updatePropertiesLabel((String) event.getNewValue());
			} else if (LanguagePropertiesModel.PROPERTY_UNSAVED_CHANGES.equals(event.getPropertyName())) {
				checkButtonStatus();
			}
		});

		setSize(1000, 450);
		setMinimumSize(new Dimension(450, 300));
		setLocationRelativeTo(null);

		/*
		 * Closing is handled in closeApplication(), which may veto it if there are
		 * unsaved changes.
		 */
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent event) {
				closeApplication();
			}

			@Override
			public void windowOpened(final WindowEvent event) {
				// Deferred, so the main window is completely shown before a possible update dialog appears
				javax.swing.SwingUtilities.invokeLater(() -> runDailyUpdateCheck());
			}
		});

		setupTable();
		checkButtonStatus();
	}

	private void runDailyUpdateCheck() {
		if (Utilities.isNotBlank(LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL) && dailyUpdateCheckIsPending()) {
			setDailyUpdateCheckStatus(true);
			try {
				if (ApplicationUpdateUtilities.checkForNewVersionAvailable(LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION) != null) {
					ApplicationUpdateUtilities.executeUpdate(this, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, true, false);
				}
			} catch (final Exception e) {
				showErrorMessage(LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", e.getMessage()));
			}
		}
	}

	/**
	 * Recently opened files and directories, the latest used is the last entry
	 */
	UniqueFifoQueuedList<String> getRecentlyOpenedDirectories() {
		return recentlyOpenedDirectories;
	}

	/**
	 * Checks, which properties are used in the source files of a directory (kept for external callers)
	 *
	 * @param storageToCheck
	 *            properties to check
	 * @param directory
	 *            directory with the source files, searched recursively
	 * @param filePattern
	 *            regular expression for the names of the source files, e.g. ".*\.java"
	 * @param usagePatternString
	 *            usage of a property in the source files, "&lt;property&gt;" stands for the key
	 * @throws Exception
	 *             if the source files cannot be read
	 */
	public void checkUsage(final List<LanguageProperty> storageToCheck, final String directory, final String filePattern, final String usagePatternString) throws Exception {
		statisticsAndUsage.checkUsage(storageToCheck, directory, filePattern, usagePatternString);
	}

	private void loadConfiguration() {
		recentlyOpenedDirectories = new UniqueFifoQueuedList<>(5);
		recentlyOpenedDirectories.addAll(applicationConfiguration.getList(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES));

		statisticsAndUsage.loadRecentCheckUsages();

		checkButtonStatus();
	}

	private JPanel createLeftPart() throws Exception {
		final JPanel leftPart = new JPanel(new BorderLayout(0, 3));
		leftPart.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		final JPanel topPart = new JPanel();
		topPart.setLayout(new BoxLayout(topPart, BoxLayout.PAGE_AXIS));

		propertiesLabel = new JLabel(LangResources.get("table_title"));
		propertiesLabel.setFont(propertiesLabel.getFont().deriveFont(Font.BOLD, propertiesLabel.getFont().getSize2D() + 4));
		propertiesLabel.setAlignmentX(LEFT_ALIGNMENT);
		propertiesLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 3, 0));
		topPart.add(propertiesLabel);

		final JPanel buttonSection1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
		buttonSection1.setAlignmentX(LEFT_ALIGNMENT);
		topPart.add(buttonSection1);

		final JPanel buttonSection2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
		buttonSection2.setAlignmentX(LEFT_ALIGNMENT);
		topPart.add(buttonSection2);

		loadRecentButton = createIconButton(buttonSection1, "clock.png", "tooltip_load_recent_files", e -> openRecent());
		createIconButton(buttonSection1, "load.png", "tooltip_load_files", e -> openFiles());
		createIconButton(buttonSection1, "folderLoad.png", "tooltip_load_folder", e -> openFolder());
		createIconButton(buttonSection1, "excelLoad.png", "tooltip_importExcel", e -> importFromExcel());
		createIconButton(buttonSection1, "csvLoad.png", "tooltip_importCsv", e -> importFromCsv());
		final JButton mergeImportButton = createIconButton(buttonSection1, "merge.png", "tooltip_mergeImport", null);
		mergeImportButton.addActionListener(e -> importSourceChooser.showImportSourceMenu(mergeImportButton, "mergeImport_title", this::mergeLoadedLanguageProperties));
		reduceByBaseSetButton = createIconButton(buttonSection1, "reduce.png", "tooltip_reduceByBaseSet", null);
		reduceByBaseSetButton.addActionListener(e -> importSourceChooser.showImportSourceMenu(reduceByBaseSetButton, "reduceByBaseSet_title", this::reduceByBaseSet));
		saveButton = createIconButton(buttonSection1, "save.png", "tooltip_save_files", e -> saveFiles());
		folderSaveButton = createIconButton(buttonSection1, "folderSave.png", "tooltip_save_folder", e -> saveFolder());
		exportToExcelButton = createIconButton(buttonSection1, "excelSave.png", "tooltip_exportExcel", e -> exportToExcel());
		exportToCsvButton = createIconButton(buttonSection1, "csvSave.png", "tooltip_exportCsv", e -> exportToCsv());
		createIconButton(buttonSection1, "wrench.png", "configuration", e -> openConfiguration());
		createIconButton(buttonSection1, "question.png", "help", e -> new HelpDialog(this, LanguagePropertiesManager.APPLICATION_NAME + " (" + LanguagePropertiesManager.VERSION.toString() + ") " + LangResources.get("help"), applicationConfiguration).open());

		addButton = createIconButton(buttonSection2, "newProperty.png", "tooltip_create_new_property", e -> addNewProperty());
		removeButton = createIconButton(buttonSection2, "trash.png", "tooltip_delete_properties", e -> removeSelectedProperties());
		removeButton.setEnabled(false);
		checkUsageButton = createIconButton(buttonSection2, "puzzle.png", "checkusage", e -> statisticsAndUsage.checkUsageNew());
		checkUsageButton.setEnabled(false);
		checkUsageButtonPrevious = createIconButton(buttonSection2, "puzzleClock.png", "checkusageprevious", e -> statisticsAndUsage.checkUsagePrevious());
		checkUsageButtonPrevious.setEnabled(false);
		addLanguageButton = createIconButton(buttonSection2, "plus.png", "tooltip_AddLanguage", e -> addLanguage());
		deleteLanguageButton = createIconButton(buttonSection2, "minus.png", "tooltip_DeleteLanguage", e -> deleteLanguage(null));
		translateButton = createIconButton(buttonSection2, "translate.png", "tooltip_Translate", null);
		translateButton.addActionListener(e -> translationAction.showTranslateMenu(translateButton));
		transferButton = createIconButton(buttonSection2, "transfer.png", "tooltip_Transfer", e -> transfer());
		clearIdenticalButton = createIconButton(buttonSection2, "clearIdentical.png", "tooltip_ClearIdentical", e -> clearIdentical());
		removeDuplicatesButton = createIconButton(buttonSection2, "clean.png", "tooltip_removeDuplicates", e -> removeDuplicates());
		checkErrorsButton = createIconButton(buttonSection2, "lightning.png", "tooltip_checkErrors", e -> checkErrors());
		showStatisticsButton = createIconButton(buttonSection2, "info.png", "tooltip_showStatistics", e -> statisticsAndUsage.showStatistics());

		leftPart.add(topPart, BorderLayout.NORTH);

		tablePanel = new PropertiesTablePanel(model, new TableCallback());
		leftPart.add(tablePanel, BorderLayout.CENTER);

		return leftPart;
	}

	private static JButton createIconButton(final JPanel parent, final String imageName, final String toolTipKey, final ActionListener actionListener) throws Exception {
		final JButton button = new JButton(ImageManager.getImage(imageName));
		button.setToolTipText(LangResources.get(toolTipKey));
		button.setMargin(new Insets(2, 2, 2, 2));
		button.setPreferredSize(new Dimension(ICON_BUTTON_SIZE, ICON_BUTTON_SIZE));
		button.addActionListener(actionListener);
		parent.add(button);
		return button;
	}

	private PropertyDetailPanel createRightPart() {
		detailPanel = new PropertyDetailPanel(model, new DetailCallback());
		return detailPanel;
	}

	/**
	 * Recreates the table columns and the language detail fields after the set of
	 * properties or languages changed.
	 */
	public void setupTable() {
		tablePanel.setupColumns(isCommentColumnWanted());
		detailPanel.rebuildLanguageFields();

		// Keep the selection (e.g. after translating selected rows) as far as those properties still exist
		model.retainExistingSelection();
		tablePanel.restoreSelection(model.getCurrentSelection());
		if (model.isLoaded()) {
			refreshDetailView();
		}
	}

	/**
	 * Redisplays the table content (e.g. after sorting or changing a property) and
	 * restores the current selection of the model.
	 */
	private void refreshTable() {
		tablePanel.refresh();
	}

	/**
	 * Shows the current selection of the model in the detail view
	 */
	private void refreshDetailView() {
		detailPanel.refresh();
		updateDetailViewVisibility();
		checkButtonStatus();
	}

	/**
	 * Hides the detail view while more than one property is selected, because it
	 * can only show and edit a single property. The table then uses the full width.
	 */
	private void updateDetailViewVisibility() {
		final boolean showDetailView = model.getCurrentSelection().size() <= 1;
		if (detailPanel.isVisible() == showDetailView) {
			return;
		}

		if (showDetailView) {
			detailPanel.setVisible(true);
			if (dividerLocationBeforeHidingDetail > 0) {
				mainSplitPane.setDividerLocation(dividerLocationBeforeHidingDetail);
			} else {
				mainSplitPane.setDividerLocation(0.5);
			}
		} else {
			// Remember the user's divider position, because hiding the right component moves the divider to the edge
			dividerLocationBeforeHidingDetail = mainSplitPane.getDividerLocation();
			detailPanel.setVisible(false);
		}
		mainSplitPane.revalidate();
		mainSplitPane.repaint();
	}

	/**
	 * @return true if the detail view has no unapplied changes or the user agreed to discard them
	 */
	boolean confirmDiscardDetailChanges() {
		return !detailPanel.isModified() || askForDiscardChanges();
	}

	/**
	 * Actions of the properties table, which need the main window
	 */
	private class TableCallback implements PropertiesTablePanel.Callback {
		@Override
		public boolean confirmDiscardDetailChanges() {
			return LanguagePropertiesManagerDialog.this.confirmDiscardDetailChanges();
		}

		@Override
		public void currentSelectionChanged() {
			refreshDetailView();
		}

		@Override
		public void tableStateChanged() {
			checkButtonStatus();
		}

		@Override
		public void removeSelectedProperties() {
			LanguagePropertiesManagerDialog.this.removeSelectedProperties();
		}

		@Override
		public void deleteAllComments() {
			LanguagePropertiesManagerDialog.this.deleteAllComments();
		}

		@Override
		public void deleteAllPaths() {
			LanguagePropertiesManagerDialog.this.deleteAllPaths();
		}

		@Override
		public void deleteAllLanguageValues(final String languageSign, final String languageDisplayName) {
			LanguagePropertiesManagerDialog.this.deleteAllLanguageValues(languageSign, languageDisplayName);
		}

		@Override
		public void setSelectedLanguageValues(final String languageSign, final String languageDisplayName, final String newValue) {
			LanguagePropertiesManagerDialog.this.setSelectedLanguageValues(languageSign, languageDisplayName, newValue);
		}

		@Override
		public void deleteLanguage(final String languageSign) {
			LanguagePropertiesManagerDialog.this.deleteLanguage(languageSign);
		}

		@Override
		public void showError(final Exception exception) {
			LanguagePropertiesManagerDialog.this.showError(exception);
		}
	}

	/**
	 * Actions of the detail view, which need the main window
	 */
	private class DetailCallback implements PropertyDetailPanel.Callback {
		@Override
		public String askForNewLanguagePropertiesSetName() {
			return new SimpleInputDialog(LanguagePropertiesManagerDialog.this, getTitle(), LangResources.get("enterNewLanguagePropertiesName")).open();
		}

		@Override
		public void languagesChanged() {
			setupTable();
			checkButtonStatus();
		}

		@Override
		public void propertyChanged() {
			refreshTable();
			checkButtonStatus();
		}

		@Override
		public void propertyAdded() {
			refreshTable();
			refreshDetailView();
		}

		@Override
		public void showError(final Exception exception) {
			LanguagePropertiesManagerDialog.this.showError(exception);
		}

		@Override
		public void showErrorMessage(final String title, final String text) {
			LanguagePropertiesManagerDialog.this.showErrorMessage(title, text);
		}
	}

	private void addNewProperty() {
		if (confirmDiscardDetailChanges()) {
			tablePanel.clearSelection();
			model.clearCurrentSelection();
			refreshDetailView();
		}
	}

	/** Shared by the trash button and the DEL key on the properties table */
	private void removeSelectedProperties() {
		try {
			if (tablePanel.getSelectedRowCount() > 0 && askForDropProperties()) {
				// Removed by identity, so also exactly the selected one of several duplicates is removed
				model.removeProperties(tablePanel.getSelectedProperties());
				setupTable();
				refreshDetailView();
				checkButtonStatus();
			}
		} catch (final Exception ex) {
			showError(ex);
		}
	}

	private void addLanguage() {
		try {
			final String newLanguageSign = new SimpleInputDialog(this, getTitle(), LangResources.get("enterLanguageSign")).open();
			if (Utilities.isNotBlank(newLanguageSign)) {
				model.addLanguage(newLanguageSign);
				setupTable();
			}
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	/**
	 * The comment column is shown, if comments are not ignored by configuration
	 */
	private boolean isCommentColumnWanted() {
		return model.isLoaded() && !applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS);
	}

	/**
	 * Removes the comments of all properties (context menu of the comment column)
	 */
	private void deleteAllComments() {
		try {
			final long commentCount = model.countComments();
			if (commentCount > 0) {
				// All comments are removed at once, so ask the user before doing it
				final Integer returncode = new QuestionDialog(this, LangResources.get("question_title_delete_comments"), LangResources.get("question_content_delete_comments", commentCount), LangResources.get("yes"), LangResources.get("no")).open();
				if (returncode != null && returncode == 0) {
					model.deleteAllComments();
					setupTable();
				}
			}
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	/**
	 * Removes the paths of all properties (context menu of the path column).
	 * Properties without a path get a new path on the next save.
	 */
	private void deleteAllPaths() {
		try {
			final long pathCount = model.countPaths();
			if (pathCount > 0) {
				// All paths are removed at once, so ask the user before doing it
				final Integer returncode = new QuestionDialog(this, LangResources.get("question_title_delete_paths"), LangResources.get("question_content_delete_paths", pathCount), LangResources.get("yes"), LangResources.get("no")).open();
				if (returncode != null && returncode == 0) {
					model.deleteAllPaths();
					setupTable();
				}
			}
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	/**
	 * Removes the values of one language from all properties, but keeps the
	 * language itself (context menu of a language column).
	 *
	 * @param languageSign
	 *            the language whose values are removed
	 * @param languageDisplayName
	 *            the name of the language as shown in the column header
	 */
	private void deleteAllLanguageValues(final String languageSign, final String languageDisplayName) {
		try {
			final long valueCount = LanguagePropertiesModel.countNonEmptyValues(model.getLanguageProperties(), languageSign);
			if (valueCount > 0) {
				// All values of this language are removed at once, so ask the user before doing it
				final Integer returncode = new QuestionDialog(this, LangResources.get("question_title_delete_language_values"), LangResources.get("question_content_delete_language_values", valueCount, languageDisplayName), LangResources.get("yes"), LangResources.get("no")).open();
				if (returncode != null && returncode == 0) {
					model.deleteAllLanguageValues(languageSign);
					setupTable();
				}
			}
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	/**
	 * Sets the value of one language of the selected properties to an explicitly
	 * empty value ("" is written as "key=") or to a missing value (null, the key is
	 * not written into this language file and ResourceBundle falls back to the
	 * default value). Existing non-empty values are only overwritten after asking.
	 */
	private void setSelectedLanguageValues(final String languageSign, final String languageDisplayName, final String newValue) {
		try {
			final List<LanguageProperty> selectedProperties = tablePanel.getSelectedProperties();
			if (selectedProperties.isEmpty()) {
				return;
			}

			final long nonEmptyValueCount = LanguagePropertiesModel.countNonEmptyValues(selectedProperties, languageSign);
			if (nonEmptyValueCount > 0) {
				final Integer returncode = new QuestionDialog(this, LangResources.get("question_title_delete_language_values"), LangResources.get("question_content_delete_language_values", nonEmptyValueCount, languageDisplayName), LangResources.get("yes"), LangResources.get("no")).open();
				if (returncode == null || returncode != 0) {
					return;
				}
			}

			if (model.setLanguageValues(selectedProperties, languageSign, newValue)) {
				model.setCurrentSelection(selectedProperties);
				refreshTable();
				refreshDetailView();
			}
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	/**
	 * Deletes a language from all properties.
	 *
	 * @param languageSign
	 *            the language to delete, or null to let the user select it (delete language button)
	 */
	private void deleteLanguage(final String languageSign) {
		try {
			String languageSignToDelete = languageSign;
			if (languageSignToDelete == null) {
				final List<String> availableLanguageSignsToDelete = new ArrayList<>(model.getAvailableLanguageSigns());
				languageSignToDelete = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectLanguageSignToDelete"), availableLanguageSignsToDelete).open();
			}
			if (model.deleteLanguage(languageSignToDelete)) {
				setupTable();
			}
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	/**
	 * The selected properties, or all properties shown in the table if nothing is selected
	 * (so with active search filter only the search hits).
	 */
	List<LanguageProperty> getSelectedOrAllProperties() {
		if (tablePanel.getSelectedRowCount() > 0) {
			return tablePanel.getSelectedProperties();
		} else {
			return tablePanel.getDisplayedProperties();
		}
	}

	private void transfer() {
		try {
			final String languageSignTransferSource = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectSourceLanguageSignToTransfer"), model.getAvailableLanguageSigns(), 0).open();
			if (Utilities.isBlank(languageSignTransferSource)) {
				return;
			}

			final List<String> availableOtherLanguageSigns = new ArrayList<>(model.getAvailableLanguageSigns());
			availableOtherLanguageSigns.remove(languageSignTransferSource);
			String languageSignTransferTarget;
			if (availableOtherLanguageSigns.size() == 1) {
				languageSignTransferTarget = availableOtherLanguageSigns.get(0);
			} else {
				languageSignTransferTarget = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectTargetLanguageSignToTransfer"), availableOtherLanguageSigns).open();
				if (Utilities.isBlank(languageSignTransferTarget)) {
					return;
				}
			}

			// Only restrict to the selected rows if any are selected, otherwise transfer all properties
			final int countTransfers = model.transferValues(getSelectedOrAllProperties(), languageSignTransferSource, languageSignTransferTarget);
			setupTable();

			showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("addedTransfers", countTransfers));
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	private void clearIdentical() {
		try {
			final String languageSignClearSource = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectSourceLanguageSignToClearIdentical"), model.getAvailableLanguageSigns(), 0).open();
			if (Utilities.isBlank(languageSignClearSource)) {
				return;
			}

			final List<String> availableOtherLanguageSigns = new ArrayList<>(model.getAvailableLanguageSigns());
			availableOtherLanguageSigns.remove(languageSignClearSource);
			String languageSignClearTarget;
			if (availableOtherLanguageSigns.size() == 1) {
				languageSignClearTarget = availableOtherLanguageSigns.get(0);
			} else {
				languageSignClearTarget = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectTargetLanguageSignToClearIdentical"), availableOtherLanguageSigns).open();
				if (Utilities.isBlank(languageSignClearTarget)) {
					return;
				}
			}

			// Only restrict to the selected rows if any are selected, otherwise check all properties
			final int countCleared = model.clearIdenticalValues(getSelectedOrAllProperties(), languageSignClearSource, languageSignClearTarget);
			setupTable();

			showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("clearedIdenticalValues", countCleared));
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	/**
	 * Removes properties with the same combination of path and key. The values
	 * and comments of the removed duplicates are only taken over into the kept
	 * property after the user confirmed (see LanguagePropertiesModel.removeDuplicates()).
	 */
	private void removeDuplicates() {
		try {
			final DuplicatesPlan duplicatesPlan = model.findDuplicates();
			if (!duplicatesPlan.hasDuplicates()) {
				showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("noDuplicatesFound"));
			} else {
				final QuestionDialog dialog = new QuestionDialog(this, LangResources.get("question_title_remove_duplicates"), LangResources.get("question_content_remove_duplicates", duplicatesPlan.getDuplicateCount()) + "\n\n" + duplicatesPlan.getReportText(), LangResources.get("yes"), LangResources.get("no"));
				final Integer returncode = dialog.open();
				if (returncode != null && returncode == 0) {
					final int removedCount = model.removeDuplicates(duplicatesPlan);
					setupTable();
					refreshDetailView();
					showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("duplicatesRemoved", removedCount));
				}
			}
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	private void checkErrors() {
		try {
			final ErrorReport errorReport = LanguagePropertiesChecker.createErrorReport(model.getLanguageProperties());
			if (errorReport.getIssueCount() == 0) {
				showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("noErrorsFound"));
			} else {
				showData(LangResources.get("checkErrorsReportTitle"), LangResources.get("checkErrorsFound", errorReport.getIssueCount()) + "\n\n" + errorReport.getReportText());
			}
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	private void openConfiguration() {
		try {
			byte[] iconData = null;
			try (InputStream inputStream = ImageManager.class.getClassLoader().getResourceAsStream("images/icons/LanguagePropertiesManager.ico")) {
				if (inputStream != null) {
					iconData = IoUtilities.toByteArray(inputStream);
				} else {
					// Missing in the jar (e.g. not included by the build), the desktop link then gets a fallback icon
					System.err.println("Resource images/icons/LanguagePropertiesManager.ico not found");
				}
			}

			final ApplicationConfigurationDialog dialog = new ApplicationConfigurationDialog(this, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.APPLICATION_STARTUPCLASS_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.VERSION_BUILDTIME, applicationConfiguration, iconData, ImageManager.getImage("LanguagePropertiesManager.png").getImage(), LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null);
			if (dialog.open() == Result.OK) {
				applicationConfiguration.save();

				loadConfiguration();

				// Show or hide the comment column according to the changed "ignore comments" option
				if (model.isLoaded() && tablePanel.isCommentColumnShown() != isCommentColumnWanted()) {
					setupTable();
				}
			}
		} catch (final Exception ex) {
			showError(ex);
		}
	}

	/**
	 * Enables or disables the buttons according to the loaded data and the selection.
	 */
	public void checkButtonStatus() {
		final int rowCount = tablePanel == null ? 0 : tablePanel.getRowCount();
		final boolean hasProperties = model.hasProperties();
		final boolean hasMultipleLanguages = hasProperties && model.hasMultipleLanguages();

		if (saveButton != null) {
			saveButton.setEnabled(model.hasUnsavedChanges());
		}
		if (exportToExcelButton != null) {
			exportToExcelButton.setEnabled(model.isLoaded());
		}
		if (exportToCsvButton != null) {
			exportToCsvButton.setEnabled(model.isLoaded());
		}
		if (addButton != null) {
			addButton.setEnabled(rowCount > 0);
		}
		if (loadRecentButton != null) {
			loadRecentButton.setEnabled(recentlyOpenedDirectories != null && recentlyOpenedDirectories.size() > 0);
		}
		if (tablePanel != null) {
			tablePanel.setInteractionEnabled(rowCount > 0);
		}
		if (removeButton != null) {
			removeButton.setEnabled(model.getFirstSelectedProperty() != null);
		}
		if (checkUsageButton != null) {
			checkUsageButton.setEnabled(hasProperties);
		}
		if (checkUsageButtonPrevious != null) {
			checkUsageButtonPrevious.setEnabled(hasProperties && statisticsAndUsage.hasRecentCheckUsages());
		}
		if (addLanguageButton != null) {
			addLanguageButton.setEnabled(hasProperties);
		}
		if (deleteLanguageButton != null) {
			deleteLanguageButton.setEnabled(hasMultipleLanguages);
		}
		if (folderSaveButton != null) {
			folderSaveButton.setEnabled(hasProperties);
		}
		if (translateButton != null) {
			translateButton.setEnabled(hasMultipleLanguages);
		}
		if (transferButton != null) {
			transferButton.setEnabled(hasMultipleLanguages);
		}
		if (clearIdenticalButton != null) {
			clearIdenticalButton.setEnabled(hasMultipleLanguages);
		}
		if (removeDuplicatesButton != null) {
			removeDuplicatesButton.setEnabled(hasProperties);
		}
		if (checkErrorsButton != null) {
			checkErrorsButton.setEnabled(hasProperties);
		}
		if (showStatisticsButton != null) {
			showStatisticsButton.setEnabled(hasProperties);
		}
		if (reduceByBaseSetButton != null) {
			reduceByBaseSetButton.setEnabled(hasProperties);
		}
	}

	private boolean askForDropProperties() {
		final Integer returncode = new QuestionDialog(this, LangResources.get("question_title_delete_property"), LangResources.get("question_content_delete_property"), LangResources.get("yes"), LangResources.get("no")).open();
		return returncode != null && returncode == 0;
	}

	private boolean askForDiscardChanges() {
		final Integer returncode = new QuestionDialog(this, LangResources.get("question_title_discard_changes"), LangResources.get("question_content_discard_changes"), LangResources.get("yes"), LangResources.get("no")).open();
		return returncode != null && returncode == 0;
	}

	/**
	 * Closes the application window, unless the user decides to keep unsaved
	 * changes.
	 */
	public void closeApplication() {
		if (!model.hasUnsavedChanges() || askForDiscardChanges()) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE, statisticsAndUsage.getRecentCheckUsages());
			applicationConfiguration.save();
			model.setUnsavedChanges(false);
			dispose();
		}
	}

	/**
	 * Common handling after one of the load/import actions: resets the model on
	 * errors and refreshes the table.
	 */
	private void afterLoad() {
		model.setUnsavedChanges(false);
		model.clearCurrentSelection();
		setupTable();
		refreshDetailView();
		checkButtonStatus();
	}

	private void resetLoadedData() {
		model.reset();
	}

	private void openFiles() {
		if (model.hasUnsavedChanges() && !askForDiscardChanges()) {
			return;
		}

		try {
			final File file = DialogUtilities.chooseFileToOpen(this, getTitle() + " " + LangResources.get("open_file_dialog_text"), null);
			if (file == null) {
				// The loaded data stays as it is, including its unsaved changes state
				showErrorMessage(LangResources.get("open_file_dialog_text"), LangResources.get("canceledByUser"));
			} else if (file.exists() && file.isFile()) {
				if (loadSingleLanguagePropertiesSet(file.getAbsolutePath())) {
					DialogUtilities.moveToEnd(recentlyOpenedDirectories, file.getAbsolutePath()); // put selected as latest used
					applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
					afterLoad();
				}
			} else {
				throw new Exception("Selected language properties set path is not an existing file");
			}
		} catch (final Exception e) {
			resetLoadedData();
			showError(e);
			afterLoad();
		}
		checkButtonStatus();
	}

	private boolean loadSingleLanguagePropertiesSet(final String filePath) throws ExecutionException {
		if (!importSourceChooser.hasLanguagePropertiesFileExtension(filePath)) {
			showErrorMessage(LangResources.get("open_file_dialog_text"), LangResources.get("missingMandatoryFileExtension", applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION)));
			return false;
		}

		final LoadedLanguageProperties loadedLanguageProperties = importSourceChooser.readSingleLanguagePropertiesSet(filePath);
		if (loadedLanguageProperties == null) {
			showErrorMessage(LangResources.get("open_file_dialog_text"), LangResources.get("canceledByUser"));
			return false;
		} else {
			takeOverLoadedLanguageProperties(loadedLanguageProperties);
			showMessage(LangResources.get("directory_dialog_title"), LangResources.get("openFilesResult", filePath, model.getLanguageProperties().size(), Utilities.join(model.getAvailableLanguageSigns(), ", ")));
			return true;
		}
	}

	private void openFolder() {
		if (model.hasUnsavedChanges() && !askForDiscardChanges()) {
			return;
		}

		try {
			final File basicDirectory = DialogUtilities.chooseDirectory(this, LangResources.get("open_directory_dialog_text"), null);
			if (basicDirectory == null) {
				// The loaded data stays as it is, including its unsaved changes state
				showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
			} else if (basicDirectory.exists()) {
				if (openAllLanguagePropertiesSets(basicDirectory.getAbsolutePath())) {
					DialogUtilities.moveToEnd(recentlyOpenedDirectories, basicDirectory.getAbsolutePath()); // put selected as latest used
					applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
					afterLoad();
				}
			}
		} catch (final Exception e) {
			resetLoadedData();
			showError(e);
			afterLoad();
		}
		checkButtonStatus();
	}

	private boolean openAllLanguagePropertiesSets(final String basicDirectoryPath) throws ExecutionException {
		final LoadedLanguageProperties loadedLanguageProperties = importSourceChooser.readAllLanguagePropertiesSets(basicDirectoryPath);
		if (loadedLanguageProperties == null) {
			showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
			return false;
		} else {
			takeOverLoadedLanguageProperties(loadedLanguageProperties);
			showMessage(LangResources.get("directory_dialog_title"), LangResources.get("openDirectoryResult", basicDirectoryPath, loadedLanguageProperties.getLanguagePropertiesSetNames().size(), model.getLanguageProperties().size(), Utilities.join(model.getAvailableLanguageSigns(), ", ")));
			return true;
		}
	}

	/**
	 * Replaces the currently loaded data by the given loaded data.
	 */
	private void takeOverLoadedLanguageProperties(final LoadedLanguageProperties loadedLanguageProperties) {
		model.load(loadedLanguageProperties);
	}

	/**
	 * Shows the name of the loaded properties set above the table (listener of the model)
	 */
	private void updatePropertiesLabel(final String newLanguagePropertySetName) {
		if (Utilities.isNotEmpty(newLanguagePropertySetName)) {
			propertiesLabel.setText(LangResources.get("table_title") + " \"" + newLanguagePropertySetName + "\"");
		} else {
			propertiesLabel.setText(LangResources.get("table_title"));
		}
		propertiesLabel.revalidate();
	}

	private void openRecent() {
		if (model.hasUnsavedChanges() && !askForDiscardChanges()) {
			return;
		}

		try {
			final ComboSelectionDialog dialog = new ComboSelectionDialog(this, getTitle() + " " + LangResources.get("recent_directories_dialog_title"), LangResources.get("recent_directories_dialog_text"), recentlyOpenedDirectories, DialogUtilities.getLastEntryIndex(recentlyOpenedDirectories)).withSize(600, -1);
			final String filePath = dialog.open();

			// Take over a possible reordering (drag&drop) or deletion of the recent directories done in the dialog,
			// regardless of whether an entry was selected or the dialog was canceled
			recentlyOpenedDirectories.clear();
			recentlyOpenedDirectories.addAll(dialog.getItems());
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);

			if (filePath == null) {
				showErrorMessage(LangResources.get("recent_directories_dialog_title"), LangResources.get("canceledByUser"));
			} else if (!new File(filePath).exists()) {
				showErrorMessage(LangResources.get("recent_directories_dialog_title"), LangResources.get("error.recentPathDoesNotExistAnymore", filePath));
			} else {
				final boolean loaded;
				if (new File(filePath).isDirectory()) {
					loaded = openAllLanguagePropertiesSets(filePath);
				} else {
					loaded = loadSingleLanguagePropertiesSet(filePath);
				}
				if (loaded) {
					DialogUtilities.moveToEnd(recentlyOpenedDirectories, filePath); // put selected as latest used
					applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
					afterLoad();
				}
			}
		} catch (final Exception e) {
			resetLoadedData();
			showError(e);
			afterLoad();
		}
		// The recent list may have been changed in the dialog
		checkButtonStatus();
	}

	private void saveFiles() {
		try {
			String defaultLanguagePropertiesPath = null;
			final Set<String> languagePropertiesPaths = model.getLanguageProperties().stream().map(o -> o.getPath()).collect(Collectors.toSet());
			if (languagePropertiesPaths.contains("")) {
				if (languagePropertiesPaths.size() == 2) {
					languagePropertiesPaths.remove("");
					defaultLanguagePropertiesPath = Utilities.replaceUsersHome(new ArrayList<>(languagePropertiesPaths).get(0));
				} else {
					final String propertiesFileExtension = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION);
					final File file = DialogUtilities.chooseFileToSave(this, getTitle() + " " + LangResources.get("save_file_dialog_text"), recentlyOpenedDirectories.getLatestAdded(), "MyLanguageProperties" + propertiesFileExtension);
					if (file == null) {
						showErrorMessage(LangResources.get("save_file_dialog_text"), LangResources.get("canceledByUser"));
						return;
					}
					final String filePath = file.getAbsolutePath();
					// The dialog path still has the file extension, but a LanguagePropertiesSetPath must not include it
					defaultLanguagePropertiesPath = filePath.endsWith(propertiesFileExtension) ? filePath.substring(0, filePath.length() - propertiesFileExtension.length()) : filePath;
				}

				// Assign the determined path to all properties that don't have one yet,
				// so the worker can treat every property by its own (now always non-blank) path.
				for (final LanguageProperty languageProperty : model.getLanguageProperties()) {
					if (Utilities.isBlank(languageProperty.getPath())) {
						languageProperty.setPath(defaultLanguagePropertiesPath);
					}
				}
			}

			// Offer to create missing target directories instead of letting the save fail
			if (!ensureDirectoriesExist(model.getLanguageProperties().stream().map(LanguageProperty::getPath).collect(Collectors.toSet()), null)) {
				showErrorMessage(LangResources.get("save_file_dialog_text"), LangResources.get("canceledByUser"));
				return;
			}

			final Integer returncode = new QuestionDialog(this, getTitle(), LangResources.get("question.keepExistingProperties"), LangResources.get("yes"), LangResources.get("no")).open();
			if (returncode == null) {
				// Closing the question is no "no", which would drop the existing keys of the files
				showErrorMessage(LangResources.get("save_file_dialog_text"), LangResources.get("canceledByUser"));
				return;
			}
			final boolean extendAndKeepExistingProperties = returncode == 0;

			final WriteLanguagePropertiesWorker writeLanguagePropertiesWorker = new WriteLanguagePropertiesWorker(null, model.getLanguageProperties(), model.getLanguagePropertiesSetName(), null, null, extendAndKeepExistingProperties, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
			writeLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
			final ProgressDialog<WriteLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(this, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("save_files"), writeLanguagePropertiesWorker);
			final Result dialogResult = progressDialog.open();
			if (dialogResult == Result.CANCELED || !Boolean.TRUE.equals(writeLanguagePropertiesWorker.get())) {
				// A canceled save may have written only a part of the files, so the data still counts as unsaved
				showErrorMessage(LangResources.get("save_file_dialog_text"), LangResources.get("canceledByUser"));
			} else {
				model.setUnsavedChanges(false);
				showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("saveSuccess"));
			}

			setupTable();
			checkButtonStatus();
		} catch (final Exception e) {
			showError(e);
		}
	}

	private void saveFolder() {
		try {
			final String[] excludeParts = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES).split(";");

			// A directory selection is only needed to place properties that don't have a path of their own yet.
			// If every property already has a path, it can be saved directly to those paths without asking.
			final boolean hasPropertiesWithoutPath = model.getLanguageProperties().stream().anyMatch(o -> Utilities.isBlank(o.getPath()));

			File directory = null;
			String newPropertiesSetName = "Multiple";
			if (hasPropertiesWithoutPath) {
				directory = DialogUtilities.chooseDirectory(this, LangResources.get("save_directory_dialog_text"), null);
				if (directory == null) {
					showErrorMessage(LangResources.get("save_directory_dialog_text"), LangResources.get("canceledByUser"));
					return;
				} else if (!ensureDirectoriesExist(null, directory)) {
					showErrorMessage(LangResources.get("save_directory_dialog_text"), LangResources.get("canceledByUser"));
					return;
				}

				final SimpleInputDialog nameDialog = new SimpleInputDialog(this, getTitle(), LangResources.get("enterNewLanguagePropertiesName"));
				nameDialog.setDefaultText(newPropertiesSetName);
				final String enteredName = nameDialog.open();
				if (Utilities.isBlank(enteredName)) {
					showErrorMessage(LangResources.get("save_directory_dialog_text"), LangResources.get("canceledByUser"));
					return;
				}
				newPropertiesSetName = enteredName;
			} else if (!ensureDirectoriesExist(model.getLanguageProperties().stream().map(LanguageProperty::getPath).collect(Collectors.toSet()), null)) {
				// All properties are saved to their own paths, so their directories must exist
				showErrorMessage(LangResources.get("save_directory_dialog_text"), LangResources.get("canceledByUser"));
				return;
			}

			final Integer returncode = new QuestionDialog(this, getTitle(), LangResources.get("question.keepExistingProperties"), LangResources.get("yes"), LangResources.get("no")).open();
			if (returncode == null) {
				// Closing the question is no "no", which would drop the existing keys of the files
				showErrorMessage(LangResources.get("save_directory_dialog_text"), LangResources.get("canceledByUser"));
				return;
			}
			final boolean extendAndKeepExistingProperties = returncode == 0;

			final WriteLanguagePropertiesWorker writeLanguagePropertiesWorker = new WriteLanguagePropertiesWorker(null, model.getLanguageProperties(), newPropertiesSetName, directory, excludeParts, extendAndKeepExistingProperties, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
			writeLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
			final ProgressDialog<WriteLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(this, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("save_files"), writeLanguagePropertiesWorker);
			final Result dialogResult = progressDialog.open();
			if (dialogResult == Result.CANCELED || !Boolean.TRUE.equals(writeLanguagePropertiesWorker.get())) {
				// A canceled save may have written only a part of the files, so the data still counts as unsaved
				showErrorMessage(LangResources.get("save_directory_dialog_text"), LangResources.get("canceledByUser"));
			} else {
				model.setUnsavedChanges(false);
				showData(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("saveDirectoryResult", Utilities.join(writeLanguagePropertiesWorker.getListOfStoredProperties(), "\n")));
			}

			setupTable();
			checkButtonStatus();
		} catch (final Exception e) {
			showError(e);
		}
	}

	/**
	 * Checks whether the target directories exist and offers to create the missing ones.
	 *
	 * @param languagePropertiesSetPaths paths of language properties sets (without language sign and file extension), their parent directories are checked; may be null
	 * @param directory additional directory, which is checked itself; may be null
	 * @return true if all directories exist or were created, false if the user declined their creation
	 * @throws Exception if a directory could not be created
	 */
	private boolean ensureDirectoriesExist(final Collection<String> languagePropertiesSetPaths, final File directory) throws Exception {
		final Set<File> missingDirectories = new TreeSet<>();
		if (directory != null && !directory.isDirectory()) {
			missingDirectories.add(directory.getAbsoluteFile());
		}
		if (languagePropertiesSetPaths != null) {
			for (final String languagePropertiesSetPath : languagePropertiesSetPaths) {
				if (!Utilities.isBlank(languagePropertiesSetPath)) {
					final File parentDirectory = new File(Utilities.replaceUsersHome(languagePropertiesSetPath)).getAbsoluteFile().getParentFile();
					if (parentDirectory != null && !parentDirectory.isDirectory()) {
						missingDirectories.add(parentDirectory);
					}
				}
			}
		}

		if (missingDirectories.isEmpty()) {
			return true;
		}

		final String directoryList = missingDirectories.stream().map(File::getAbsolutePath).collect(Collectors.joining("\n"));
		final Integer returncode = new QuestionDialog(this, getTitle(), LangResources.get("question.createMissingDirectories", directoryList), LangResources.get("yes"), LangResources.get("no")).open();
		if (returncode == null || returncode != 0) {
			return false;
		}

		for (final File missingDirectory : missingDirectories) {
			// mkdirs() also returns false if another entry already created this directory as parent
			if (!missingDirectory.mkdirs() && !missingDirectory.isDirectory()) {
				throw new Exception("Cannot create directory: " + missingDirectory.getAbsolutePath());
			}
		}
		return true;
	}

	private void importFromExcel() {
		importFromFile(new String[] { "xlsx" }, file -> {
			final LoadedLanguageProperties loadedLanguageProperties = importSourceChooser.readFromExcel(file);
			if (loadedLanguageProperties == null) {
				return false;
			}
			takeOverLoadedLanguageProperties(loadedLanguageProperties);
			return true;
		});
	}

	private void importFromCsv() {
		importFromFile(new String[] { "csv", "dsv" }, file -> {
			final LoadedLanguageProperties loadedLanguageProperties = importSourceChooser.readFromCsv(file);
			if (loadedLanguageProperties == null) {
				return false;
			}
			takeOverLoadedLanguageProperties(loadedLanguageProperties);
			return true;
		});
	}

	/**
	 * Body of an import or export action on a file, returns false if the action
	 * was canceled by the user.
	 */
	@FunctionalInterface
	private interface FileAction {
		boolean process(File file) throws Exception;
	}

	/**
	 * Common frame of the Excel and CSV imports: file selection, error handling
	 * and table refresh.
	 */
	private void importFromFile(final String[] fileExtensions, final FileAction importAction) {
		if (model.hasUnsavedChanges() && !askForDiscardChanges()) {
			return;
		}

		final File importFile = DialogUtilities.chooseFileToOpen(this, getTitle() + " " + LangResources.get("import_file"), Utilities.replaceUsersHome("~" + File.separator + "Downloads"), fileExtensions);
		if (importFile == null) {
			showErrorMessage(LangResources.get("import_file"), LangResources.get("canceledByUser"));
			return;
		}

		try {
			if (importAction.process(importFile)) {
				// Imported data is not saved as properties files yet
				model.setUnsavedChanges(true);
				setupTable();
				refreshDetailView();
				showMessage(LangResources.get("import_file"), LangResources.get("actionSuccessfullyCompleted"));
			} else {
				showErrorMessage(LangResources.get("import_file"), LangResources.get("canceledByUser"));
			}
		} catch (final ExecutionException e) {
			resetLoadedData();
			model.setUnsavedChanges(false);
			if (e.getCause() != null && e.getCause() instanceof LanguagePropertiesException) {
				showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, e.getCause().getMessage());
			} else {
				showError(e);
			}
			setupTable();
		} catch (final Exception e) {
			resetLoadedData();
			model.setUnsavedChanges(false);
			showError(e);
			setupTable();
		}
		checkButtonStatus();
	}

	private void mergeLoadedLanguageProperties(final LoadedLanguageProperties loadedLanguageProperties) {
		final List<LanguageProperty> importedProperties = loadedLanguageProperties.getLanguageProperties();
		if (importedProperties.isEmpty()) {
			showMessage(LangResources.get("mergeImport_title"), LangResources.get("mergeImport_nothingFound", loadedLanguageProperties.getSourceDescription()));
			return;
		}

		final MergePlan mergePlan = LanguagePropertiesMerger.createMergePlan(model.isLoaded() ? model.getLanguageProperties() : new ArrayList<>(), importedProperties);
		if (mergePlan.hasNothingToDo()) {
			String message = LangResources.get("mergeImport_nothingToImport", loadedLanguageProperties.getSourceDescription());
			if (!mergePlan.getSkippedEntries().isEmpty()) {
				message += "\n\n" + LangResources.get("mergeImport_section_skipped") + ":\n" + Utilities.join(mergePlan.getSkippedEntries(), "\n");
			}
			showData(LangResources.get("mergeImport_title"), message);
			return;
		}

		// Only ask, if there are real conflicts. Without conflicts, all modes except FILL_EMPTY_ONLY have the same result.
		MergeMode mergeMode = MergeMode.ADD_NEW;
		if (mergePlan.getDifferingValueCount() > 0) {
			final Integer returncode = new QuestionDialog(this, LangResources.get("mergeImport_title"),
					LangResources.get("mergeImport_question", loadedLanguageProperties.getSourceDescription(), mergePlan.getNewPropertyCount(), mergePlan.getMatchedPropertyCount(), mergePlan.getPropertiesWithDifferencesCount(), mergePlan.getDifferingValueCount(), mergePlan.getFillableValueCount(), mergePlan.getSkippedEntries().size()),
					LangResources.get("mergeImport_mode_addNew"),
					LangResources.get("mergeImport_mode_overwrite"),
					LangResources.get("mergeImport_mode_fillEmptyOnly"),
					LangResources.get("cancel")).open();
			if (returncode != null && returncode == 0) {
				mergeMode = MergeMode.ADD_NEW;
			} else if (returncode != null && returncode == 1) {
				mergeMode = MergeMode.OVERWRITE;
			} else if (returncode != null && returncode == 2) {
				mergeMode = MergeMode.FILL_EMPTY_ONLY;
			} else {
				showErrorMessage(LangResources.get("mergeImport_title"), LangResources.get("canceledByUser"));
				return;
			}
		}

		// Every property knows every language sign afterwards and the added or changed properties are selected
		final MergeResult mergeResult = model.applyMerge(loadedLanguageProperties, mergePlan, mergeMode);
		setupTable();
		refreshDetailView();
		checkButtonStatus();

		// The imported file may use another encoding, so check the imported part right away
		final ErrorReport errorReport = LanguagePropertiesChecker.createErrorReport(importedProperties);

		showData(LangResources.get("mergeImport_resultTitle"), LanguagePropertiesMerger.createMergeReport(loadedLanguageProperties.getSourceDescription(), mergeMode, mergePlan, mergeResult, errorReport));
	}

	/**
	 * Compares the loaded properties (the selected ones, or all if nothing is
	 * selected) with a base set and removes the values that are identical in the
	 * base set, so only the deviations from the base set remain.
	 * Properties without any remaining value can be deleted completely.
	 */
	private void reduceByBaseSet(final LoadedLanguageProperties baseLanguageProperties) {
		if (!model.hasProperties()) {
			return;
		}

		final String title = LangResources.get("reduceByBaseSet_title");
		if (baseLanguageProperties.getLanguageProperties().isEmpty()) {
			showMessage(title, LangResources.get("mergeImport_nothingFound", baseLanguageProperties.getSourceDescription()));
			return;
		}

		final List<LanguageProperty> propertiesToCheck = new ArrayList<>(getSelectedOrAllProperties());
		final ReducePlan reducePlan = BaseSetReducer.createReducePlan(propertiesToCheck, baseLanguageProperties.getLanguageProperties());
		if (reducePlan.hasNothingToReduce()) {
			String message = LangResources.get("reduceByBaseSet_nothingIdentical", baseLanguageProperties.getSourceDescription(), propertiesToCheck.size());
			if (!reducePlan.getSkippedEntries().isEmpty()) {
				message += "\n\n" + LangResources.get("mergeImport_section_skipped") + ":\n" + Utilities.join(reducePlan.getSkippedEntries(), "\n");
			}
			showData(title, message);
			return;
		}

		final Integer returncode = new QuestionDialog(this, title,
				LangResources.get("reduceByBaseSet_question", baseLanguageProperties.getSourceDescription(), propertiesToCheck.size(), reducePlan.getIdenticalValueCount(), reducePlan.getReduciblePropertyCount(), reducePlan.getEmptyPropertyCount(), reducePlan.getPropertiesWithoutBaseCount(), reducePlan.getSkippedEntries().size()),
				LangResources.get("reduceByBaseSet_mode_removeEmpty"),
				LangResources.get("reduceByBaseSet_mode_clearOnly"),
				LangResources.get("cancel")).open();
		final boolean removeEmptyProperties;
		if (returncode != null && returncode == 0) {
			removeEmptyProperties = true;
		} else if (returncode != null && returncode == 1) {
			removeEmptyProperties = false;
		} else {
			showErrorMessage(title, LangResources.get("canceledByUser"));
			return;
		}

		// The reduced properties that still exist are selected afterwards, so they can be reviewed directly
		final ReduceResult reduceResult = model.applyReduction(reducePlan, removeEmptyProperties);
		setupTable();
		refreshDetailView();
		checkButtonStatus();

		showData(LangResources.get("reduceByBaseSet_resultTitle"), BaseSetReducer.createReduceReport(baseLanguageProperties.getSourceDescription(), propertiesToCheck.size(), reducePlan, reduceResult));
	}

	private void exportToExcel() {
		exportToFile("xlsx", exportFile -> {
			final List<String> languagePropertySetNames = new ArrayList<>();
			languagePropertySetNames.add(model.getLanguagePropertiesSetName());
			// Overwriting an existing file was already confirmed, the worker replaces it only after a successful export
			final ExportToExcelWorker exportToExcelWorker = new ExportToExcelWorker(null, model.getLanguageProperties(), languagePropertySetNames, exportFile, true);
			final Result dialogResult = new ProgressDialog<>(this, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("export_file"), exportToExcelWorker).open();
			if (dialogResult == Result.CANCELED) {
				return false;
			}
			// check for errors
			exportToExcelWorker.get();
			return true;
		});
	}

	private void exportToCsv() {
		exportToFile("csv", exportFile -> {
			final List<String> languagePropertySetNames = new ArrayList<>();
			languagePropertySetNames.add(model.getLanguagePropertiesSetName());
			// Overwriting an existing file was already confirmed, the worker replaces it only after a successful export
			final ExportToCsvWorker exportToCsvWorker = new ExportToCsvWorker(null, model.getLanguageProperties(), languagePropertySetNames, exportFile, true);
			final Result dialogResult = new ProgressDialog<>(this, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("export_file"), exportToCsvWorker).open();
			if (dialogResult == Result.CANCELED) {
				return false;
			}
			// check for errors
			exportToCsvWorker.get();
			return true;
		});
	}

	/**
	 * Common frame of the Excel and CSV exports: file selection, overwrite
	 * confirmation and error handling. The export action returns false if it was
	 * canceled by the user.
	 */
	private void exportToFile(final String fileExtension, final FileAction exportAction) {
		try {
			final String exportBaseName = Utilities.isNotBlank(model.getLanguagePropertiesSetName()) ? model.getLanguagePropertiesSetName() : "LanguageProperties";
			final File exportFile = DialogUtilities.chooseFileToSave(this, getTitle() + " " + LangResources.get("export_file"), Utilities.replaceUsersHome("~" + File.separator + "Downloads"), exportBaseName + "_Export_" + DateUtilities.formatDate("yyyy-MM-dd_HH-mm", LocalDateTime.now()) + "." + fileExtension, fileExtension);
			if (exportFile == null) {
				showErrorMessage(LangResources.get("export_file"), LangResources.get("canceledByUser"));
				return;
			}

			// The existing file is not deleted here: the export workers replace it only after a successful export
			if (exportFile.exists() && !askForOverwriteFile(exportFile.getAbsolutePath())) {
				throw new Exception(LangResources.get("error.destinationFileAlreadyExists", exportFile.getAbsolutePath()));
			}

			try {
				if (exportAction.process(exportFile)) {
					// An export into Excel or CSV also counts as saved data
					model.setUnsavedChanges(false);
					showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("exportSuccess"));
				} else {
					showErrorMessage(LangResources.get("export_file"), LangResources.get("canceledByUser"));
				}
			} catch (final Exception e) {
				showError(e);
			}
			checkButtonStatus();
		} catch (final Exception e) {
			showError(e);
		}
	}

	/**
	 * Asks the user whether an existing file may be overwritten.
	 *
	 * @param filePath
	 *            path of the existing file
	 * @return true if the file may be overwritten
	 */
	public boolean askForOverwriteFile(final String filePath) {
		final Integer returncode = new QuestionDialog(this, getTitle(), LangResources.get("question.overwritefile", filePath), LangResources.get("overwrite"), LangResources.get("cancel")).setBackgroundColor(SwingColor.LightRed).open();
		return returncode != null && returncode == 0;
	}

	@Override
	protected void setDailyUpdateCheckStatus(final boolean checkboxStatus) {
		applicationConfiguration.set(ConfigurationProperties.CONFIG_KEY_DAILY_UPDATE_CHECK, checkboxStatus);
		applicationConfiguration.set(ConfigurationProperties.CONFIG_KEY_NEXT_DAILY_UPDATE_CHECK, LocalDateTime.now().plusDays(1));
		applicationConfiguration.save();
	}

	@Override
	protected Boolean isDailyUpdateCheckActivated() {
		return applicationConfiguration.getBoolean(ConfigurationProperties.CONFIG_KEY_DAILY_UPDATE_CHECK);
	}

	/**
	 * Whether the daily update check is activated and due, and a network connection exists.
	 *
	 * @return true if the update check should be done now
	 */
	protected boolean dailyUpdateCheckIsPending() {
		return applicationConfiguration.getBoolean(ConfigurationProperties.CONFIG_KEY_DAILY_UPDATE_CHECK)
				&& (applicationConfiguration.getDate(ConfigurationProperties.CONFIG_KEY_NEXT_DAILY_UPDATE_CHECK) == null || applicationConfiguration.getDate(ConfigurationProperties.CONFIG_KEY_NEXT_DAILY_UPDATE_CHECK).isBefore(LocalDateTime.now()))
				&& NetworkUtilities.checkForNetworkConnection();
	}

	void showError(final Exception exception) {
		new ErrorDialog(this, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, exception).open();
	}

	/**
	 * Shows a longer text (e.g. a report) in a resizable dialog.
	 *
	 * @param title
	 *            title of the dialog
	 * @param text
	 *            text to show
	 */
	public void showData(final String title, final String text) {
		new ShowDataDialog(this, title, text).withResizable(true).open();
	}

	/**
	 * Shows a short message with an OK button.
	 *
	 * @param title
	 *            title of the dialog
	 * @param text
	 *            message to show
	 */
	public void showMessage(final String title, final String text) {
		new QuestionDialog(this, title, text, LangResources.get("ok")).open();
	}

	/**
	 * Shows a short error message with an OK button and a red background.
	 *
	 * @param title
	 *            title of the dialog
	 * @param text
	 *            error message to show
	 */
	public void showErrorMessage(final String title, final String text) {
		new QuestionDialog(this, title, text, LangResources.get("ok")).setBackgroundColor(SwingColor.LightRed).open();
	}
}
