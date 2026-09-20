package de.soderer.languagepropertiesmanager.dlg;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import de.soderer.languagepropertiesmanager.LanguagePropertiesException;
import de.soderer.languagepropertiesmanager.LanguagePropertiesManager;
import de.soderer.languagepropertiesmanager.image.ImageManager;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.languagepropertiesmanager.worker.ExportToCsvWorker;
import de.soderer.languagepropertiesmanager.worker.ExportToExcelWorker;
import de.soderer.languagepropertiesmanager.worker.ImportFromCsvWorker;
import de.soderer.languagepropertiesmanager.worker.ImportFromExcelWorker;
import de.soderer.languagepropertiesmanager.worker.LoadLanguagePropertiesWorker;
import de.soderer.languagepropertiesmanager.worker.TranslateLanguagePropertiesWorker;
import de.soderer.languagepropertiesmanager.worker.WriteLanguagePropertiesWorker;
import de.soderer.network.NetworkUtilities;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.DeepLHelper;
import de.soderer.utilities.FileUtilities;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Result;
import de.soderer.utilities.Tuple;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.collection.UniqueFifoQueuedList;
import de.soderer.utilities.csv.CsvFormat;
import de.soderer.utilities.csv.CsvReader;
import de.soderer.utilities.csv.CsvWriter;
import de.soderer.utilities.swt.ApplicationConfigurationDialog;
import de.soderer.utilities.swt.ComboSelectionDialog;
import de.soderer.utilities.swt.ErrorDialog;
import de.soderer.utilities.swt.ProgressDialog;
import de.soderer.utilities.swt.QuestionDialog;
import de.soderer.utilities.swt.ShowDataDialog;
import de.soderer.utilities.swt.SimpleInputDialog;
import de.soderer.utilities.swt.SwtColor;
import de.soderer.utilities.swt.SwtUtilities;
import de.soderer.utilities.swt.UpdateableGuiApplication;

/**
 * Main Class
 */
public class LanguagePropertiesManagerDialog extends UpdateableGuiApplication {
	private boolean showStorageTexts = false;
	private boolean dataWasModified = false;
	private boolean hasUnsavedChanges = false;
	private boolean technicalDataChange = false;

	private Label propertiesLabel;
	private Composite rightPart = null;
	private Button removeButton;
	private Button saveButton;
	private Button folderSaveButton;
	private Button exportToExcelButton;
	private Button exportToCsvButton;
	private Button importFromExcelButton;
	private Button importFromCsvButton;
	private Button addButton;
	private Text pathTextfield;
	private Text keyTextfield;
	private Text commentTextfield;
	private Composite detailFieldsPart;
	private Map<String, Text> languageTextFields;
	private Table propertiesTable;
	private int columnPathIndex;
	private int columnOrigIndexIndex;
	private int columnKeyIndex;
	private Listener currentFillDataListener;
	private Listener columnSortListener;
	private List<Tuple<String, String>> currentSelectedKeys;
	private List<LanguageProperty> languageProperties;
	private List<String> availableLanguageSigns;
	private String languagePropertySetName;
	private String searchText;
	private boolean searchCaseInsensitivePreference = true;
	private boolean searchInKeysPreference = true;
	private boolean searchInValuesPreference = false;
	private boolean searchInPathPreference = false;
	private Button checkUsageButton;
	private Button checkUsageButtonPrevious;
	private Button addLanguageButton;
	private Button deleteLanguageButton;
	private Button translateButton;
	private Button transferButton;
	private Button clearIdenticalButton;
	private Button removeDuplicatesButton;
	private Button checkErrorsButton;
	private Button showStatisticsButton;

	private Button okButton;
	private Button cancelButton;
	private Button textConversionButton;
	private Button loadRecentButton;
	private Composite searchBox;

	private UniqueFifoQueuedList<String> recentlyOpenedDirectories;
	private UniqueFifoQueuedList<String> recentlyCheckUsages;
	private final ConfigurationProperties applicationConfiguration;

	public LanguagePropertiesManagerDialog(final Display display, final ConfigurationProperties applicationConfiguration) throws Exception {
		super(display, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.KEYSTORE_FILE);

		this.applicationConfiguration = applicationConfiguration;
		loadConfiguration();

		final Monitor[] monitorArray = display.getMonitors();
		if (monitorArray != null) {
			getShell().setLocation((monitorArray[0].getClientArea().width - getSize().x) / 2, (monitorArray[0].getClientArea().height - getSize().y) / 2);
		}

		@SuppressWarnings("unused")
		final
		ImageManager imageManager = new ImageManager(getShell());
		final SashForm sashForm = new SashForm(this, SWT.SMOOTH | SWT.HORIZONTAL);
		setImage(ImageManager.getImage("LanguagePropertiesManager.png"));
		setText(LangResources.get("window_title"));
		setLayout(new FillLayout());
		createLeftPart(sashForm);
		createRightPart(this, sashForm);
		setSize(1000, 450);
		setMinimumSize(450, 300);

		addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(final Event event) {
				close();
				event.doit = !hasUnsavedChanges;
			}
		});

		checkButtonStatus();

		final LanguagePropertiesManagerDialog mainDialog = this;
		getShell().addShellListener(new ShellAdapter() {
			@Override
			public void shellActivated(final ShellEvent event) {
				getShell().removeShellListener(this);

				display.asyncExec(() -> {
					if (Utilities.isNotBlank(LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL) && dailyUpdateCheckIsPending()) {
						setDailyUpdateCheckStatus(true);
						try {
							if (ApplicationUpdateUtilities.checkForNewVersionAvailable(LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION) != null) {
								ApplicationUpdateUtilities.executeUpdate(mainDialog, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, null, true, false);
							}
						} catch (final Exception e) {
							showErrorMessage(LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", e.getMessage()));
						}
					}
				});
			}
		});
	}

	private void loadConfiguration() {
		recentlyOpenedDirectories = new UniqueFifoQueuedList<>(5);
		recentlyOpenedDirectories.addAll(applicationConfiguration.getList(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES));

		recentlyCheckUsages = new UniqueFifoQueuedList<>(5);
		recentlyCheckUsages.addAll(applicationConfiguration.getList(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE));

		checkButtonStatus();
	}

	private void createLeftPart(final SashForm parent) throws Exception {
		final Composite leftPart = new Composite(parent, SWT.BORDER);
		leftPart.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));
		leftPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));

		propertiesLabel = new Label(leftPart, SWT.NONE);
		propertiesLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		propertiesLabel.setText(LangResources.get("table_title"));
		propertiesLabel.setFont(new Font(getDisplay(), "Arial", 12, SWT.BOLD));

		final Composite buttonSection1 = new Composite(leftPart, SWT.NONE);
		buttonSection1.setLayout(SwtUtilities.createSmallMarginGridLayout(14, false));
		buttonSection1.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));

		final Composite buttonSection2 = new Composite(leftPart, SWT.NONE);
		buttonSection2.setLayout(SwtUtilities.createSmallMarginGridLayout(14, false));
		buttonSection2.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));

		loadRecentButton = new Button(buttonSection1, SWT.PUSH);
		loadRecentButton.setImage(ImageManager.getImage("clock.png"));
		loadRecentButton.setToolTipText(LangResources.get("tooltip_load_recent_files"));
		loadRecentButton.addSelectionListener(new OpenRecentSelectionListener());

		final Button loadFilesButton = new Button(buttonSection1, SWT.PUSH);
		loadFilesButton.setImage(ImageManager.getImage("load.png"));
		loadFilesButton.setToolTipText(LangResources.get("tooltip_load_files"));
		loadFilesButton.addSelectionListener(new OpenFilesSelectionListener());

		final Button loadFolderButton = new Button(buttonSection1, SWT.PUSH);
		loadFolderButton.setImage(ImageManager.getImage("folderLoad.png"));
		loadFolderButton.setToolTipText(LangResources.get("tooltip_load_folder"));
		loadFolderButton.addSelectionListener(new OpenFolderSelectionListener());

		importFromExcelButton = new Button(buttonSection1, SWT.PUSH);
		importFromExcelButton.setImage(ImageManager.getImage("excelLoad.png"));
		importFromExcelButton.setToolTipText(LangResources.get("tooltip_importExcel"));
		importFromExcelButton.addSelectionListener(new ImportFromExcelSelectionListener());

		importFromCsvButton = new Button(buttonSection1, SWT.PUSH);
		importFromCsvButton.setImage(ImageManager.getImage("csvLoad.png"));
		importFromCsvButton.setToolTipText(LangResources.get("tooltip_importCsv"));
		importFromCsvButton.addSelectionListener(new ImportFromCsvSelectionListener());

		saveButton = new Button(buttonSection1, SWT.PUSH);
		saveButton.setImage(ImageManager.getImage("save.png"));
		saveButton.setToolTipText(LangResources.get("tooltip_save_files"));
		saveButton.addSelectionListener(new SaveFilesSelectionListener(this));

		folderSaveButton = new Button(buttonSection1, SWT.PUSH);
		folderSaveButton.setImage(ImageManager.getImage("folderSave.png"));
		folderSaveButton.setToolTipText(LangResources.get("tooltip_save_folder"));
		folderSaveButton.addSelectionListener(new SaveFolderSelectionListener());

		exportToExcelButton = new Button(buttonSection1, SWT.PUSH);
		exportToExcelButton.setImage(ImageManager.getImage("excelSave.png"));
		exportToExcelButton.setToolTipText(LangResources.get("tooltip_exportExcel"));
		exportToExcelButton.addSelectionListener(new ExcelExportSelectionListener());

		exportToCsvButton = new Button(buttonSection1, SWT.PUSH);
		exportToCsvButton.setImage(ImageManager.getImage("csvSave.png"));
		exportToCsvButton.setToolTipText(LangResources.get("tooltip_exportCsv"));
		exportToCsvButton.addSelectionListener(new CsvExportSelectionListener());

		final Button configButton = new Button(buttonSection1, SWT.PUSH);
		configButton.setImage(ImageManager.getImage("wrench.png"));
		configButton.setToolTipText(LangResources.get("configuration"));
		configButton.addSelectionListener(new ConfigButtonSelectionListener());

		final Button helpButton = new Button(buttonSection1, SWT.PUSH);
		helpButton.setImage(ImageManager.getImage("question.png"));
		helpButton.setToolTipText(LangResources.get("help"));
		helpButton.addSelectionListener(new HelpButtonSelectionListener(this));

		addButton = new Button(buttonSection2, SWT.PUSH);
		addButton.setImage(ImageManager.getImage("newProperty.png"));
		addButton.setToolTipText(LangResources.get("tooltip_create_new_property"));
		addButton.addSelectionListener(new AddButtonSelectionListener());

		removeButton = new Button(buttonSection2, SWT.PUSH);
		removeButton.setImage(ImageManager.getImage("trash.png"));
		removeButton.setToolTipText(LangResources.get("tooltip_delete_properties"));
		removeButton.setEnabled(false);
		removeButton.addSelectionListener(new RemoveButtonSelectionListener());

		checkUsageButton = new Button(buttonSection2, SWT.PUSH);
		checkUsageButton.setImage(ImageManager.getImage("puzzle.png"));
		checkUsageButton.setToolTipText(LangResources.get("checkusage"));
		checkUsageButton.setEnabled(false);
		checkUsageButton.addSelectionListener(new CheckUsageButtonSelectionListener());

		checkUsageButtonPrevious = new Button(buttonSection2, SWT.PUSH);
		checkUsageButtonPrevious.setImage(ImageManager.getImage("puzzleClock.png"));
		checkUsageButtonPrevious.setToolTipText(LangResources.get("checkusageprevious"));
		checkUsageButtonPrevious.setEnabled(false);
		checkUsageButtonPrevious.addSelectionListener(new CheckUsageButtonPreviousSelectionListener());

		addLanguageButton = new Button(buttonSection2, SWT.PUSH);
		addLanguageButton.setImage(ImageManager.getImage("plus.png"));
		addLanguageButton.setToolTipText(LangResources.get("tooltip_AddLanguage"));
		addLanguageButton.addSelectionListener(new AddLanguageButtonSelectionListener());

		deleteLanguageButton = new Button(buttonSection2, SWT.PUSH);
		deleteLanguageButton.setImage(ImageManager.getImage("minus.png"));
		deleteLanguageButton.setToolTipText(LangResources.get("tooltip_DeleteLanguage"));
		deleteLanguageButton.addSelectionListener(new DeleteLanguageButtonSelectionListener());

		translateButton = new Button(buttonSection2, SWT.PUSH);
		translateButton.setImage(ImageManager.getImage("translate.png"));
		translateButton.setToolTipText(LangResources.get("tooltip_Translate"));
		translateButton.addSelectionListener(new TranslateButtonSelectionListener());

		transferButton = new Button(buttonSection2, SWT.PUSH);
		transferButton.setImage(ImageManager.getImage("transfer.png"));
		transferButton.setToolTipText(LangResources.get("tooltip_Transfer"));
		transferButton.addSelectionListener(new TransferButtonSelectionListener());

		clearIdenticalButton = new Button(buttonSection2, SWT.PUSH);
		clearIdenticalButton.setImage(ImageManager.getImage("clearIdentical.png"));
		clearIdenticalButton.setToolTipText(LangResources.get("tooltip_ClearIdentical"));
		clearIdenticalButton.addSelectionListener(new ClearIdenticalButtonSelectionListener());

		removeDuplicatesButton = new Button(buttonSection2, SWT.PUSH);
		removeDuplicatesButton.setImage(ImageManager.getImage("clean.png"));
		removeDuplicatesButton.setToolTipText(LangResources.get("tooltip_removeDuplicates"));
		removeDuplicatesButton.addSelectionListener(new RemoveDuplicatesButtonSelectionListener());

		checkErrorsButton = new Button(buttonSection2, SWT.PUSH);
		checkErrorsButton.setImage(ImageManager.getImage("lightning.png"));
		checkErrorsButton.setToolTipText(LangResources.get("tooltip_checkErrors"));
		checkErrorsButton.addSelectionListener(new CheckErrorsButtonSelectionListener());

		showStatisticsButton = new Button(buttonSection2, SWT.PUSH);
		showStatisticsButton.setImage(ImageManager.getImage("info.png"));
		showStatisticsButton.setToolTipText(LangResources.get("tooltip_showStatistics"));
		showStatisticsButton.addSelectionListener(new ShowStatisticsButtonSelectionListener());

		// Searching
		searchBox = new Composite(leftPart, SWT.BORDER);
		searchBox.setLayout(SwtUtilities.createSmallMarginGridLayout(7, false));
		searchBox.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));

		final Text searchTextField = new Text(searchBox, SWT.NONE);
		searchTextField.setText(LangResources.get("search"));
		searchTextField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		searchTextField.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(final FocusEvent e) {
				if (((Text) e.widget).getText().equals(LangResources.get("search"))) {
					((Text) e.widget).setText("");
				}
			}

			@Override
			public void focusLost(final FocusEvent e) {
				if (Utilities.isEmpty(((Text) e.widget).getText())) {
					((Text) e.widget).setText(LangResources.get("search"));
				}
			}
		});
		searchTextField.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(final ModifyEvent e) {
				final Text textItem = ((Text) e.widget);
				if (Utilities.isNotEmpty(textItem.getText()) && !textItem.getText().equals(LangResources.get("search")) && languageProperties != null) {
					if (propertiesTable.getSelectionCount() == 0) {
						propertiesTable.setSelection(0);
					}
					searchText = textItem.getText();
					selectSearch(searchText, propertiesTable.getSelectionIndex(), true, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference);
				}
			}
		});

		final Button searchDownButton = new Button(searchBox, SWT.PUSH);
		searchDownButton.setImage(ImageManager.getImage("down.png"));
		searchDownButton.setToolTipText(LangResources.get("search_down"));
		searchDownButton.setLayoutData(new GridData(25, 25));
		searchDownButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				if (Utilities.isNotEmpty(searchText)) {
					selectSearch(searchText, propertiesTable.getSelectionIndex() + 1, true, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference);
				}
			}

			@Override
			public void widgetDefaultSelected(final SelectionEvent e) {
				if (Utilities.isNotEmpty(searchText)) {
					selectSearch(searchText, propertiesTable.getSelectionIndex() + 1, true, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference);
				}
			}
		});

		final Button searchUpButton = new Button(searchBox, SWT.PUSH);
		searchUpButton.setImage(ImageManager.getImage("up.png"));
		searchUpButton.setToolTipText(LangResources.get("search_up"));
		searchUpButton.setLayoutData(new GridData(25, 25));
		searchUpButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				selectSearch(searchText, propertiesTable.getSelectionIndex() - 1, false, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference);
			}
		});

		final Button caseButton = new Button(searchBox, SWT.CHECK);
		caseButton.setText("Aa");
		caseButton.setToolTipText(LangResources.get("case_sensitive"));
		caseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				searchCaseInsensitivePreference = !((Button)e.widget).getSelection();
				if (Utilities.isNotEmpty(searchText) && !searchText.equals(LangResources.get("search")) && languageProperties != null) {
					if (propertiesTable.getSelectionCount() == 0) {
						propertiesTable.setSelection(0);
					}
					selectSearch(searchText, propertiesTable.getSelectionIndex(), true, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference);
				}
			}
		});

		final Button keyButton = new Button(searchBox, SWT.CHECK);
		keyButton.setSelection(searchInKeysPreference);
		keyButton.setText(LangResources.get("columnheader_key"));
		keyButton.setToolTipText(LangResources.get("columnheader_key"));
		keyButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				searchInKeysPreference = ((Button)e.widget).getSelection();
				if (Utilities.isNotEmpty(searchText) && !searchText.equals(LangResources.get("search")) && languageProperties != null) {
					if (propertiesTable.getSelectionCount() == 0) {
						propertiesTable.setSelection(0);
					}
					selectSearch(searchText, propertiesTable.getSelectionIndex(), true, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference);
				}
			}
		});

		final Button valueButton = new Button(searchBox, SWT.CHECK);
		valueButton.setText(LangResources.get("value"));
		valueButton.setToolTipText(LangResources.get("value"));
		valueButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				searchInValuesPreference = ((Button)e.widget).getSelection();
				if (Utilities.isNotEmpty(searchText) && !searchText.equals(LangResources.get("search")) && languageProperties != null) {
					if (propertiesTable.getSelectionCount() == 0) {
						propertiesTable.setSelection(0);
					}
					selectSearch(searchText, propertiesTable.getSelectionIndex(), true, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference);
				}
			}
		});

		final Button pathButton = new Button(searchBox, SWT.CHECK);
		pathButton.setText(LangResources.get("columnheader_path"));
		pathButton.setToolTipText(LangResources.get("columnheader_path"));
		pathButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				searchInPathPreference = ((Button)e.widget).getSelection();
				if (Utilities.isNotEmpty(searchText) && !searchText.equals(LangResources.get("search")) && languageProperties != null) {
					if (propertiesTable.getSelectionCount() == 0) {
						propertiesTable.setSelection(0);
					}
					selectSearch(searchText, propertiesTable.getSelectionIndex(), true, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference);
				}
			}
		});

		propertiesTable = new Table(leftPart, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
		propertiesTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 7, 1));
		propertiesTable.setHeaderVisible(true);
		propertiesTable.setLinesVisible(true);
		propertiesTable.addSelectionListener(new TableItemSelectionListener());
		propertiesTable.addKeyListener(new PropertiesTableKeyListener());

		columnSortListener = new ColumnSortListener();

		// WindowsBug: First column can not be set to right alignment
		final TableColumn column = new TableColumn(propertiesTable, SWT.RIGHT);
		column.setWidth(0);
		column.setText(LangResources.get("columnheader_dummy"));

		final TableColumn columnNr = new TableColumn(propertiesTable, SWT.RIGHT);
		columnNr.setMoveable(false);
		columnNr.setWidth(50);
		columnNr.setText(LangResources.get("columnheader_nr"));

		final TableColumn columnPath = new TableColumn(propertiesTable, SWT.LEFT);
		columnPath.setMoveable(true);
		columnPath.setWidth(100);
		columnPath.setText(LangResources.get("columnheader_path"));
		columnPathIndex = Arrays.asList(propertiesTable.getColumns()).indexOf(columnPath);
		columnPath.addListener(SWT.Selection, columnSortListener);

		final TableColumn columnOrigialIndex = new TableColumn(propertiesTable, SWT.RIGHT);
		columnOrigialIndex.setMoveable(false);
		columnOrigialIndex.setWidth(60);
		columnOrigialIndex.setText(LangResources.get("columnheader_original_index"));
		columnOrigIndexIndex = Arrays.asList(propertiesTable.getColumns()).indexOf(columnOrigialIndex);
		columnOrigialIndex.addListener(SWT.Selection, columnSortListener);

		final TableColumn columnKey = new TableColumn(propertiesTable, SWT.LEFT);
		columnKey.setMoveable(true);
		columnKey.setWidth(175);
		columnKey.setText(LangResources.get("columnheader_key"));
		columnKeyIndex = Arrays.asList(propertiesTable.getColumns()).indexOf(columnKey);
		columnKey.addListener(SWT.Selection, columnSortListener);
	}

	public void setupTable() {
		if (currentFillDataListener != null) {
			propertiesTable.removeListener(SWT.SetData, currentFillDataListener);
			currentFillDataListener = null;
			propertiesTable.setItemCount(0);
		}
		propertiesTable.clearAll();
		for (final TableColumn column : propertiesTable.getColumns()) {
			if (!column.getText().equals(LangResources.get("columnheader_dummy"))
					&& !column.getText().equals(LangResources.get("columnheader_nr"))
					&& !column.getText().equals(LangResources.get("columnheader_original_index"))
					&& !column.getText().equals(LangResources.get("columnheader_key"))
					&& !column.getText().equals(LangResources.get("columnheader_path"))) {
				column.dispose();
			}
		}

		if (languageProperties != null) {
			for (final String sign : availableLanguageSigns) {
				final TableColumn column = new TableColumn(propertiesTable, SWT.CENTER);
				column.setMoveable(true);
				column.setWidth(sign.length() > 3 ? 50 : 25);
				if (sign.equals(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT)) {
					column.setText(LangResources.get("columnheader_default"));
				}
				else column.setText(sign);
				column.addListener(SWT.Selection, columnSortListener);
			}

			currentFillDataListener = new FillDataListener();
			propertiesTable.addListener(SWT.SetData, currentFillDataListener);

			propertiesTable.setItemCount(languageProperties.size());

			propertiesTable.setSortColumn(propertiesTable.getColumn(1));
			propertiesTable.setSortDirection(SWT.UP);

			for (final Control field : detailFieldsPart.getChildren()) {
				field.dispose();
			}
			languageTextFields.clear();

			for (final String sign : availableLanguageSigns) {
				final Label languageLabel = new Label(detailFieldsPart, SWT.NONE);
				if (LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT.equals(sign)) {
					languageLabel.setText(LangResources.get("columnheader_default") + ":");
				} else {
					languageLabel.setText(sign + ":");
				}
				final Text languageTextfield = new Text(detailFieldsPart, SWT.BORDER);
				languageTextfield.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
				languageTextfield.addModifyListener(new DetailModifyListener());
				languageTextFields.put(sign, languageTextfield);
			}
		} else {
			for (final Control field : detailFieldsPart.getChildren()) {
				field.dispose();
			}
			languageTextFields.clear();
		}
		detailFieldsPart.layout();
	}

	private void createRightPart(final LanguagePropertiesManagerDialog mainDialog, final Composite parent) throws Exception {
		rightPart = new Composite(parent, SWT.NONE);
		rightPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));
		rightPart.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));

		final Composite keyBereich = new Composite(rightPart, SWT.NONE);
		keyBereich.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));
		keyBereich.setLayout(SwtUtilities.createSmallMarginGridLayout(2, false));

		final Label pathLabel = new Label(keyBereich, SWT.NONE);
		pathLabel.setText(LangResources.get("columnheader_path") + ":");
		pathTextfield = new Text(keyBereich, SWT.BORDER);
		pathTextfield.setEditable(false);
		pathTextfield.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		pathTextfield.addModifyListener(new DetailModifyListener());

		final Label keyLabel = new Label(keyBereich, SWT.NONE);
		keyLabel.setText(LangResources.get("columnheader_key") + ":");
		keyTextfield = new Text(keyBereich, SWT.BORDER);
		keyTextfield.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		keyTextfield.addModifyListener(new DetailModifyListener());

		final Label commentLabel = new Label(keyBereich, SWT.NONE);
		commentLabel.setText(LangResources.get("comment") + ":");
		commentTextfield = new Text(keyBereich, SWT.BORDER);
		commentTextfield.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		commentTextfield.addModifyListener(new DetailModifyListener());

		final Label keySeparatorLabel = new Label(keyBereich, SWT.SEPARATOR | SWT.HORIZONTAL);
		keySeparatorLabel.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false, 2, 1));

		final ScrolledComposite scrolledPart = new ScrolledComposite(rightPart, SWT.H_SCROLL | SWT.V_SCROLL);
		scrolledPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));
		scrolledPart.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));

		detailFieldsPart = new Composite(scrolledPart, SWT.NONE);
		detailFieldsPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false, 1, 1));
		detailFieldsPart.setLayout(SwtUtilities.createSmallMarginGridLayout(2, false));

		languageTextFields = new HashMap<>();

		scrolledPart.setContent(detailFieldsPart);
		scrolledPart.setMinSize(200, 250);
		scrolledPart.setExpandHorizontal(true);
		scrolledPart.setExpandVertical(true);

		final Composite buttonBereich = new Composite(rightPart, SWT.NONE);
		buttonBereich.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));
		buttonBereich.setLayout(SwtUtilities.createSmallMarginGridLayout(2, true));

		final Label buttonSeparatorLabel = new Label(buttonBereich, SWT.SEPARATOR | SWT.HORIZONTAL);
		buttonSeparatorLabel.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, true, 2, 1));

		textConversionButton = new Button(buttonBereich, SWT.PUSH);
		textConversionButton.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false, 2, 1));
		textConversionButton.setText(showStorageTexts ? LangResources.get("change_to_show_visble_texts") : LangResources.get("change_to_show_storage_texts"));
		textConversionButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				showStorageTexts = !showStorageTexts;
				textConversionButton.setText(showStorageTexts ? LangResources.get("change_to_show_visble_texts") : LangResources.get("change_to_show_storage_texts"));
				changeDisplayMode(showStorageTexts);
			}
		});

		okButton = new Button(buttonBereich, SWT.PUSH);
		okButton.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false));
		okButton.setText(LangResources.get("button_text_add"));
		okButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				try {
					hasUnsavedChanges = true;

					if (okButton.getText().equals(LangResources.get("button_text_change"))) {
						// Change existing property
						final String oldKey = propertiesTable.getSelection()[0].getText(columnKeyIndex);
						final String newKey = getPlainFieldValue(keyTextfield.getText());

						LanguageProperty propertyToChange = null;
						for (final LanguageProperty languageProperty : languageProperties) {
							if (languageProperty.getPath().equals(pathTextfield.getText()) && languageProperty.getKey().equals(oldKey)) {
								propertyToChange = languageProperty;
								break;
							}
						}
						if (propertyToChange == null) {
							throw new Exception("Cannot find property to change");
						}

						propertyToChange.setKey(newKey);
						for (final String languageKey : languageTextFields.keySet()) {
							propertyToChange.setLanguageValue(languageKey, getPlainFieldValue(languageTextFields.get(languageKey).getText()));
						}

						refreshTable();
						dataWasModified = false;
						checkButtonStatus();
					} else {
						final LanguageProperty newValues = new LanguageProperty(pathTextfield.getText(), getPlainFieldValue(keyTextfield.getText()));
						for (final String languageKey : languageTextFields.keySet()) {
							newValues.setLanguageValue(languageKey, getPlainFieldValue(languageTextFields.get(languageKey).getText()));
						}

						if (Utilities.isNotEmpty(commentTextfield.getText())) {
							newValues.setComment(commentTextfield.getText());
						} else {
							newValues.setComment(null);
						}

						if (languageProperties == null) {
							languageProperties = new ArrayList<>();
							setLanguagePropertiesSetName(new SimpleInputDialog(mainDialog, getText(), LangResources.get("enterNewLanguagePropertiesName")).open());
							propertiesLabel.setText(LangResources.get("table_title") + " \"" + languagePropertySetName + "\"");
							propertiesLabel.requestLayout();
							availableLanguageSigns = new ArrayList<>();
							availableLanguageSigns.add(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);

							hasUnsavedChanges = false;
							setupTable();
							checkButtonStatus();
						}

						// Add new property
						newValues.setOriginalIndex(languageProperties.size() + 1);
						languageProperties.add(newValues);
						propertiesTable.setItemCount(propertiesTable.getItemCount() + 1);
						currentSelectedKeys = Arrays.asList(new Tuple<>(pathTextfield.getText(), keyTextfield.getText()));
						refreshTable();
						removeButton.setEnabled(true);
						dataWasModified = false;
						okButton.setText(LangResources.get("button_text_change"));
						checkButtonStatus();
					}
				} catch (final Exception ex) {
					new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
				}
			}
		});

		cancelButton = new Button(buttonBereich, SWT.PUSH);
		cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false));
		cancelButton.setText(LangResources.get("button_text_discard"));
		cancelButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				refreshDetailView();
			}
		});

		checkButtonStatus();
	}

	private class TableItemSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			technicalDataChange = true;
			if (!dataWasModified || askForDiscardChanges()) {
				// make a new selection
				removeButton.setEnabled(true);
				refreshDetailView();
				currentSelectedKeys = getSelectedKeys();
			} else {
				// reselect old entry
				propertiesTable.deselectAll();
				for (int i = 0; i < languageProperties.size(); i++) {
					final LanguageProperty languageProperty = languageProperties.get(i);
					if (languageProperty.getPath().equals(currentSelectedKeys.get(0).getFirst()) && languageProperty.getKey().equals(currentSelectedKeys.get(0).getSecond())) {
						propertiesTable.select(i);
						break;
					}
				}
			}
			technicalDataChange = false;

			checkButtonStatus();
		}
	}

	private class AddButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			try {
				if (!dataWasModified || askForDiscardChanges()) {
					propertiesTable.deselectAll();
					currentSelectedKeys = null;
					refreshDetailView();
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
		}
	}

	private class RemoveButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			removeSelectedProperties();
		}
	}

	/**
	 * Same DEL-key deletion as the trash button (RemoveButtonSelectionListener): asks for
	 * confirmation via {@link #askForDropProperties()} and removes the currently selected rows.
	 */
	private class PropertiesTableKeyListener extends KeyAdapter {
		@Override
		public void keyPressed(final KeyEvent e) {
			if (e.keyCode == SWT.DEL && propertiesTable.getSelectionCount() > 0) {
				removeSelectedProperties();
			}
		}
	}

	/** Shared by the trash button and the DEL key on {@link #propertiesTable} */
	private void removeSelectedProperties() {
		try {
			if (propertiesTable.getSelectionCount() > 0 && askForDropProperties()) {
				for (final TableItem item : propertiesTable.getSelection()) {
					for (int i = 0; i < languageProperties.size(); i++) {
						final LanguageProperty languageProperty = languageProperties.get(i);
						if (languageProperty.getPath().equals(item.getText(columnPathIndex)) && languageProperty.getKey().equals(item.getText(columnKeyIndex))) {
							languageProperties.remove(languageProperty);
							break;
						}
					}
				}

				setupTable();
				propertiesTable.deselectAll();
				refreshDetailView();
				hasUnsavedChanges = true;
				checkButtonStatus();
			}
		} catch (final Exception ex) {
			new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
		}
	}

	private class AddLanguageButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			try {
				final String newLanguageSign = new SimpleInputDialog(getShell(), getText(), LangResources.get("enterLanguageSign")).open();
				if (Utilities.isNotBlank(newLanguageSign)) {
					for (final LanguageProperty languageProperty : languageProperties) {
						if (!languageProperty.getAvailableLanguageSigns().contains(newLanguageSign)) {
							languageProperty.setLanguageValue(newLanguageSign, null);
						}
					}
					availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);
					setupTable();
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
			checkButtonStatus();
		}
	}

	private class DeleteLanguageButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			try {
				final List<String> availableLanguageSignsToDelete = new ArrayList<>(availableLanguageSigns);
				final String languageSignToDelete = new ComboSelectionDialog(getShell(), getText(), LangResources.get("selectLanguageSignToDelete"), availableLanguageSignsToDelete).open();
				if (Utilities.isNotBlank(languageSignToDelete)) {
					for (final LanguageProperty languageProperty : languageProperties) {
						languageProperty.removeLanguageValue(languageSignToDelete);
					}
					availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);
					setupTable();
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
			checkButtonStatus();
		}
	}

	private class TranslateButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				if (Utilities.isBlank(applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY))) {
					final SimpleInputDialog dialog = new SimpleInputDialog(getShell(), getText(), LangResources.get("enterDeeplApiKey"));
					final String deeplApiKey = dialog.open();
					if (deeplApiKey != null) {
						applicationConfiguration.set(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY, deeplApiKey);
					}
				}

				if (Utilities.isBlank(applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY))) {
					showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("missingDeeplApiKey"));
					return;
				}

				final String deeplBaseUrl = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_BASEURL);
				final DeepLHelper deepLHelper = new DeepLHelper(deeplBaseUrl, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY), applicationConfiguration.getProxyConfiguration().getProxy(deeplBaseUrl));

				final String languageSignTranslateSource = new ComboSelectionDialog(getShell(), getText(), LangResources.get("selectSourceLanguageSignToTranslate"), availableLanguageSigns, 0).open();
				if (Utilities.isBlank(languageSignTranslateSource)) {
					return;
				}
				String sourceLanguage = languageSignTranslateSource;
				if ("Default".equalsIgnoreCase(sourceLanguage)) {
					sourceLanguage = new ComboSelectionDialog(getShell(), getText(), LangResources.get("selectDefaultLanguageToTranslate"), deepLHelper.getSupportedLanguages(), deepLHelper.getSupportedLanguages().indexOf("EN")).open();
					if (Utilities.isBlank(sourceLanguage)) {
						return;
					}
				}
				if (sourceLanguage.contains("_")) {
					sourceLanguage = sourceLanguage.substring(0, sourceLanguage.indexOf("_"));
				}

				final List<String> availableOtherLanguageSigns = new ArrayList<>(availableLanguageSigns);
				availableOtherLanguageSigns.remove(languageSignTranslateSource);
				String languageSignTranslateTarget;
				if (availableOtherLanguageSigns.size() == 1) {
					languageSignTranslateTarget = availableOtherLanguageSigns.get(0);
				} else {
					languageSignTranslateTarget = new ComboSelectionDialog(getShell(), getText(), LangResources.get("selectTargetLanguageSignToTranslate"), availableOtherLanguageSigns).open();
					if (Utilities.isBlank(languageSignTranslateTarget)) {
						return;
					}
				}
				String targetLanguage = languageSignTranslateTarget;
				if ("Default".equalsIgnoreCase(targetLanguage)) {
					targetLanguage = new ComboSelectionDialog(getShell(), getText(), LangResources.get("selectDefaultLanguageToTranslate"), deepLHelper.getSupportedLanguages(), deepLHelper.getSupportedLanguages().indexOf("EN")).open();
					if (Utilities.isBlank(targetLanguage)) {
						return;
					}
				}
				if (targetLanguage.contains("_")) {
					targetLanguage = targetLanguage.substring(0, targetLanguage.indexOf("_"));
				}

				// Only restrict to the selected rows if any are selected, otherwise translate all properties
				final List<LanguageProperty> languagePropertiesToTranslate;
				if (propertiesTable.getSelectionCount() > 0) {
					languagePropertiesToTranslate = new ArrayList<>();
					for (final TableItem item : propertiesTable.getSelection()) {
						for (final LanguageProperty languageProperty : languageProperties) {
							if (languageProperty.getPath().equals(item.getText(columnPathIndex)) && languageProperty.getKey().equals(item.getText(columnKeyIndex))) {
								languagePropertiesToTranslate.add(languageProperty);
								break;
							}
						}
					}
				} else {
					languagePropertiesToTranslate = languageProperties;
				}

				final TranslateLanguagePropertiesWorker translateLanguagePropertiesWorker = new TranslateLanguagePropertiesWorker(null, languagePropertiesToTranslate, deepLHelper, languageSignTranslateSource, languageSignTranslateTarget, sourceLanguage, targetLanguage);
				final ProgressDialog<TranslateLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("translatingLanguageProperties"), translateLanguagePropertiesWorker);
				final Result dialogResult = progressDialog.open();
				if (dialogResult != Result.CANCELED) {
					// check for errors
					translateLanguagePropertiesWorker.get();
				}

				final int countTranslations = translateLanguagePropertiesWorker.getCountTranslations();
				setupTable();

				if (Utilities.isNotBlank(translateLanguagePropertiesWorker.getTranslateErrorMessage())) {
					showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, translateLanguagePropertiesWorker.getTranslateErrorMessage());
				}

				showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("addedTranslations", countTranslations));
				if (countTranslations > 0) {
					hasUnsavedChanges = true;
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
			checkButtonStatus();
		}
	}

	private class TransferButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				final String languageSignTransferSource = new ComboSelectionDialog(getShell(), getText(), LangResources.get("selectSourceLanguageSignToTransfer"), availableLanguageSigns, 0).open();
				if (Utilities.isBlank(languageSignTransferSource)) {
					return;
				}

				final List<String> availableOtherLanguageSigns = new ArrayList<>(availableLanguageSigns);
				availableOtherLanguageSigns.remove(languageSignTransferSource);
				String languageSignTransferTarget;
				if (availableOtherLanguageSigns.size() == 1) {
					languageSignTransferTarget = availableOtherLanguageSigns.get(0);
				} else {
					languageSignTransferTarget = new ComboSelectionDialog(getShell(), getText(), LangResources.get("selectTargetLanguageSignToTransfer"), availableOtherLanguageSigns).open();
					if (Utilities.isBlank(languageSignTransferTarget)) {
						return;
					}
				}

				// Only restrict to the selected rows if any are selected, otherwise transfer all properties
				final List<LanguageProperty> languagePropertiesToTransfer;
				if (propertiesTable.getSelectionCount() > 0) {
					languagePropertiesToTransfer = new ArrayList<>();
					for (final TableItem item : propertiesTable.getSelection()) {
						for (final LanguageProperty languageProperty : languageProperties) {
							if (languageProperty.getPath().equals(item.getText(columnPathIndex)) && languageProperty.getKey().equals(item.getText(columnKeyIndex))) {
								languagePropertiesToTransfer.add(languageProperty);
								break;
							}
						}
					}
				} else {
					languagePropertiesToTransfer = languageProperties;
				}

				int countTransfers = 0;
				for (final LanguageProperty languageProperty : languagePropertiesToTransfer) {
					final String sourceValue = languageProperty.getLanguageValue(languageSignTransferSource);
					if (Utilities.isNotBlank(sourceValue)) {
						languageProperty.setLanguageValue(languageSignTransferTarget, sourceValue);
						countTransfers++;
					}
				}
				setupTable();

				showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("addedTransfers", countTransfers));
				if (countTransfers > 0) {
					hasUnsavedChanges = true;
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
			checkButtonStatus();
		}
	}

	private class ClearIdenticalButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				final String languageSignClearSource = new ComboSelectionDialog(getShell(), getText(), LangResources.get("selectSourceLanguageSignToClearIdentical"), availableLanguageSigns, 0).open();
				if (Utilities.isBlank(languageSignClearSource)) {
					return;
				}

				final List<String> availableOtherLanguageSigns = new ArrayList<>(availableLanguageSigns);
				availableOtherLanguageSigns.remove(languageSignClearSource);
				String languageSignClearTarget;
				if (availableOtherLanguageSigns.size() == 1) {
					languageSignClearTarget = availableOtherLanguageSigns.get(0);
				} else {
					languageSignClearTarget = new ComboSelectionDialog(getShell(), getText(), LangResources.get("selectTargetLanguageSignToClearIdentical"), availableOtherLanguageSigns).open();
					if (Utilities.isBlank(languageSignClearTarget)) {
						return;
					}
				}

				// Only restrict to the selected rows if any are selected, otherwise check all properties
				final List<LanguageProperty> languagePropertiesToCheck;
				if (propertiesTable.getSelectionCount() > 0) {
					languagePropertiesToCheck = new ArrayList<>();
					for (final TableItem item : propertiesTable.getSelection()) {
						for (final LanguageProperty languageProperty : languageProperties) {
							if (languageProperty.getPath().equals(item.getText(columnPathIndex)) && languageProperty.getKey().equals(item.getText(columnKeyIndex))) {
								languagePropertiesToCheck.add(languageProperty);
								break;
							}
						}
					}
				} else {
					languagePropertiesToCheck = languageProperties;
				}

				int countCleared = 0;
				for (final LanguageProperty languageProperty : languagePropertiesToCheck) {
					final String sourceValue = languageProperty.getLanguageValue(languageSignClearSource);
					final String targetValue = languageProperty.getLanguageValue(languageSignClearTarget);
					if (Utilities.isNotBlank(targetValue) && targetValue.equals(sourceValue)) {
						languageProperty.setLanguageValue(languageSignClearTarget, null);
						countCleared++;
					}
				}
				setupTable();

				showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("clearedIdenticalValues", countCleared));
				if (countCleared > 0) {
					hasUnsavedChanges = true;
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
			checkButtonStatus();
		}
	}

	private class RemoveDuplicatesButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				// Group properties by the combination of PropertiesSetPath and PropertyKey
				final Map<String, List<LanguageProperty>> groupedByPathAndKey = new LinkedHashMap<>();
				for (final LanguageProperty languageProperty : languageProperties) {
					final String groupKey = languageProperty.getPath() + "\u0000" + languageProperty.getKey();
					groupedByPathAndKey.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(languageProperty);
				}

				// For every group with more than one entry, keep the one with the lowest original index.
				// Before discarding the rest, adopt any language value (and comment) the winner is still missing
				// from the duplicates, in order of original index, so the first available value wins.
				final List<LanguageProperty> duplicatesToRemove = new ArrayList<>();
				final StringBuilder reportText = new StringBuilder();
				for (final List<LanguageProperty> group : groupedByPathAndKey.values()) {
					if (group.size() > 1) {
						final List<LanguageProperty> sortedGroup = group.stream()
								.sorted(Comparator.comparing(LanguageProperty::getOriginalIndex))
								.collect(Collectors.toList());
						final LanguageProperty winner = sortedGroup.get(0);
						final List<LanguageProperty> losers = sortedGroup.subList(1, sortedGroup.size());

						for (final LanguageProperty loser : losers) {
							for (final String languageSign : loser.getAvailableLanguageSigns()) {
								final String loserValue = loser.getLanguageValue(languageSign);
								if (Utilities.isNotEmpty(loserValue) && Utilities.isEmpty(winner.getLanguageValue(languageSign))) {
									winner.setLanguageValue(languageSign, loserValue);
								}
							}
							if (Utilities.isEmpty(winner.getComment()) && Utilities.isNotEmpty(loser.getComment())) {
								winner.setComment(loser.getComment());
							}
						}

						reportText.append("\"").append(winner.getPath()).append("\" / \"").append(winner.getKey()).append("\": ").append(group.size()).append("\n");
						duplicatesToRemove.addAll(losers);
					}
				}

				if (duplicatesToRemove.isEmpty()) {
					showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("noDuplicatesFound"));
				} else {
					final QuestionDialog dialog = new QuestionDialog(LanguagePropertiesManagerDialog.this, LangResources.get("question_title_remove_duplicates"), LangResources.get("question_content_remove_duplicates", duplicatesToRemove.size()) + "\n\n" + reportText.toString(), LangResources.get("yes"), LangResources.get("no"));
					final int returncode = dialog.open();
					if (returncode == 0) {
						languageProperties.removeAll(duplicatesToRemove);
						propertiesTable.deselectAll();
						currentSelectedKeys = null;
						refreshDetailView();
						setupTable();
						hasUnsavedChanges = true;
						showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("duplicatesRemoved", duplicatesToRemove.size()));
					}
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
			checkButtonStatus();
		}
	}

	/**
	 * Typical byte sequences that occur when UTF-8 encoded text was mistakenly
	 * re-interpreted as ISO-8859-1 / Windows-1252 ("Mojibake"), e.g. "ä" becoming "Ã¤".
	 */
	private static final String[] MOJIBAKE_MARKERS = new String[] {
			"Ã¤", "Ã„", "Ã¶", "Ã–", "Ã¼", "Ã\u009C", "Ã\u009F",
			"â€ž", "â€œ", "â€\u009D", "â€“", "â€”", "â€¦", "Â"
	};

	private static final Pattern UNRESOLVED_UNICODE_ESCAPE_PATTERN = Pattern.compile("\\\\u[0-9A-Fa-f]{4}");

	/**
	 * Checks a single piece of text (key, value or comment) for signs of encoding corruption
	 * or other structural problems and returns a list of human readable problem descriptions.
	 * Returns an empty list if no problems were found.
	 */
	private static List<String> findTextErrors(final String text) {
		final List<String> problems = new ArrayList<>();
		if (text == null) {
			return problems;
		}

		if (text.indexOf('\uFFFD') >= 0) {
			problems.add(LangResources.get("error_replacement_char"));
		}

		for (final String marker : MOJIBAKE_MARKERS) {
			if (text.contains(marker)) {
				problems.add(LangResources.get("error_mojibake"));
				break;
			}
		}

		if (UNRESOLVED_UNICODE_ESCAPE_PATTERN.matcher(text).find()) {
			problems.add(LangResources.get("error_unresolved_unicode_escape"));
		}

		if (text.indexOf('\uFEFF') >= 0) {
			problems.add(LangResources.get("error_bom_char"));
		}

		boolean isolatedSurrogateFound = false;
		boolean controlCharFound = false;
		for (int i = 0; i < text.length() && !(isolatedSurrogateFound && controlCharFound); i++) {
			final char currentChar = text.charAt(i);
			if (!isolatedSurrogateFound) {
				if (Character.isHighSurrogate(currentChar)) {
					if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
						isolatedSurrogateFound = true;
					}
				} else if (Character.isLowSurrogate(currentChar)) {
					if (i == 0 || !Character.isHighSurrogate(text.charAt(i - 1))) {
						isolatedSurrogateFound = true;
					}
				}
			}
			if (!controlCharFound && Character.isISOControl(currentChar) && currentChar != '\t' && currentChar != '\n' && currentChar != '\r') {
				controlCharFound = true;
			}
		}
		if (isolatedSurrogateFound) {
			problems.add(LangResources.get("error_isolated_surrogate"));
		}
		if (controlCharFound) {
			problems.add(LangResources.get("error_control_char"));
		}

		return problems;
	}

	private class CheckErrorsButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				final StringBuilder reportText = new StringBuilder();
				int issueCount = 0;

				for (final LanguageProperty languageProperty : languageProperties) {
					final List<String> entryProblems = new ArrayList<>();

					final String key = languageProperty.getKey();
					if (Utilities.isBlank(key)) {
						entryProblems.add(LangResources.get("error_key_empty"));
					} else {
						for (final String textProblem : findTextErrors(key)) {
							entryProblems.add(LangResources.get("field_key") + ": " + textProblem);
						}
						if (!key.equals(key.trim()) || key.contains(" ")) {
							entryProblems.add(LangResources.get("field_key") + ": " + LangResources.get("error_key_whitespace"));
						}
						if (key.contains("=") || key.contains(":")) {
							entryProblems.add(LangResources.get("field_key") + ": " + LangResources.get("error_key_illegal_char"));
						}
					}

					for (final String textProblem : findTextErrors(languageProperty.getComment())) {
						entryProblems.add(LangResources.get("field_comment") + ": " + textProblem);
					}

					for (final String languageSign : languageProperty.getAvailableLanguageSigns()) {
						final String value = languageProperty.getLanguageValue(languageSign);
						for (final String textProblem : findTextErrors(value)) {
							entryProblems.add(LangResources.get("field_value", languageSign) + ": " + textProblem);
						}
					}

					if (!entryProblems.isEmpty()) {
						issueCount += entryProblems.size();
						reportText.append("\"").append(languageProperty.getPath()).append("\" / \"").append(key).append("\":\n");
						for (final String problem : entryProblems) {
							reportText.append("  - ").append(problem).append("\n");
						}
					}
				}

				if (issueCount == 0) {
					showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("noErrorsFound"));
				} else {
					showData(LangResources.get("checkErrorsReportTitle"), LangResources.get("checkErrorsFound", issueCount) + "\n\n" + reportText.toString());
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
			checkButtonStatus();
		}
	}

	private class ShowStatisticsButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				final int totalProperties = languageProperties.size();

				// Number of entries per properties path (i.e. per properties file / properties set)
				final Map<String, Integer> countByPath = new LinkedHashMap<>();
				for (final LanguageProperty languageProperty : languageProperties) {
					countByPath.merge(languageProperty.getPath(), 1, Integer::sum);
				}

				// Duplicate groups (same path + key), without altering any data
				final Map<String, Integer> countByPathAndKey = new LinkedHashMap<>();
				for (final LanguageProperty languageProperty : languageProperties) {
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
				for (final LanguageProperty languageProperty : languageProperties) {
					if (Utilities.isNotEmpty(languageProperty.getComment())) {
						propertiesWithCommentCount++;
					}
				}

				// Per-language completeness and value length statistics
				final Map<String, Integer> filledCountByLanguage = new LinkedHashMap<>();
				final Map<String, Long> totalLengthByLanguage = new LinkedHashMap<>();
				for (final String sign : availableLanguageSigns) {
					filledCountByLanguage.put(sign, 0);
					totalLengthByLanguage.put(sign, 0L);
				}

				String longestValuePath = null;
				String longestValueKey = null;
				String longestValueLanguage = null;
				int longestValueLength = -1;

				for (final LanguageProperty languageProperty : languageProperties) {
					for (final String sign : availableLanguageSigns) {
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
				reportText.append(LangResources.get("statistics_totalLanguages", availableLanguageSigns.size(), Utilities.join(availableLanguageSigns, ", "))).append("\n");
				reportText.append(LangResources.get("statistics_propertiesWithComment", propertiesWithCommentCount)).append("\n");
				reportText.append(LangResources.get("statistics_duplicateGroups", duplicateGroupCount, duplicateEntryCount)).append("\n");

				reportText.append("\n").append(LangResources.get("statistics_perSetHeader")).append("\n");
				for (final Map.Entry<String, Integer> entry : countByPath.entrySet()) {
					reportText.append("  \"").append(entry.getKey()).append("\": ").append(entry.getValue()).append("\n");
				}

				reportText.append("\n").append(LangResources.get("statistics_perLanguageHeader")).append("\n");
				for (final String sign : availableLanguageSigns) {
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

				showData(LangResources.get("statisticsReportTitle"), reportText.toString());
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
		}
	}

	private class ConfigButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			try {
				byte[] iconData;
				try (InputStream inputStream = ImageManager.class.getResourceAsStream("LanguagePropertiesManager.ico")) {
					iconData = IoUtilities.toByteArray(inputStream);
				}

				final ApplicationConfigurationDialog dialog = new ApplicationConfigurationDialog(getShell(), applicationConfiguration, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.APPLICATION_STARTUPCLASS_NAME, iconData, ImageManager.getImage("LanguagePropertiesManager.png"));
				if (dialog.open()) {
					applicationConfiguration.save();

					loadConfiguration();
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
		}
	}

	private class HelpButtonSelectionListener extends SelectionAdapter {
		private final LanguagePropertiesManagerDialog applicationDialog;

		public HelpButtonSelectionListener(final LanguagePropertiesManagerDialog applicationDialog) {
			this.applicationDialog = applicationDialog;
		}

		@Override
		public void widgetSelected(final SelectionEvent e) {
			new HelpDialog(applicationDialog, LanguagePropertiesManager.APPLICATION_NAME + " (" + LanguagePropertiesManager.VERSION.toString() + ") " + LangResources.get("help"), applicationConfiguration).open();
		}
	}

	private class CheckUsageButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			try {
				final DirectoryDialog dialog = new DirectoryDialog(getShell());
				dialog.setText(getText() + " " + LangResources.get("directory_dialog_title"));
				dialog.setMessage(LangResources.get("open_directory_dialog_text"));
				final String directory = dialog.open();
				if (directory == null) {
					showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
				} else if (new File(directory).exists() && new File(directory).isDirectory()) {
					final SimpleInputDialog dialog2 = new SimpleInputDialog(getShell(), getText(), LangResources.get("enterfilepattern"));
					dialog2.setDefaultText(".*\\.java");
					final String filePattern = dialog2.open();
					if (filePattern == null) {
						showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
					} else {
						final SimpleInputDialog dialog3 = new SimpleInputDialog(getShell(), getText(), LangResources.get("enterusagepattern"));
						dialog3.setDefaultText("LangResources.get(\"<property>\"");
						final String usagePattern = dialog3.open();
						if (usagePattern != null) {
							recentlyCheckUsages.add(CsvWriter.getCsvLine(';', '"', true, directory, filePattern, usagePattern));
							applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE, recentlyCheckUsages);
							checkUsage(languageProperties, directory, filePattern, usagePattern);
							checkButtonStatus();
						}
					}
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
		}
	}

	private class CheckUsageButtonPreviousSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			try {
				final ComboSelectionDialog dialog = new ComboSelectionDialog(getShell(), getText() + " " + LangResources.get("recentsettingsdialogtitle"), LangResources.get("recent_settings_dialog_text"), recentlyCheckUsages);
				final String setting = dialog.open();
				if (setting != null) {
					recentlyCheckUsages.add(setting); //put selected as latest used
					final List<String> settings = CsvReader.parseCsvLine(new CsvFormat().withSeparator(';').withStringQuote('"'), setting);
					final String directory = settings.get(0);
					final String filePattern = settings.get(1);
					final String usagePattern = settings.get(2);
					checkUsage(languageProperties, directory, filePattern, usagePattern);
					checkButtonStatus();
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
		}
	}

	private class DetailModifyListener implements ModifyListener {
		@Override
		public void modifyText(final ModifyEvent e) {
			if (!technicalDataChange) dataWasModified = true;
			checkButtonStatus();
		}
	}

	private void refreshDetailView() {
		try {
			if (propertiesTable.getSelection().length > 0) {
				final String path = propertiesTable.getSelection()[0].getText(columnPathIndex);
				final String key = propertiesTable.getSelection()[0].getText(columnKeyIndex);
				LanguageProperty property = null;
				for (final LanguageProperty languageProperty : languageProperties) {
					if (languageProperty.getPath().equals(path) && languageProperty.getKey().equals(key)) {
						property = languageProperty;
						break;
					}
				}

				if (property == null) {
					throw new Exception("No property for key: " + propertiesTable.getSelection()[0].getText(columnKeyIndex));
				} else {
					pathTextfield.setText(propertiesTable.getSelection()[0].getText(columnPathIndex));
					keyTextfield.setText(key);
					if (Utilities.isNotEmpty(property.getComment())) {
						commentTextfield.setText(property.getComment());
					} else {
						commentTextfield.setText("");
					}
					for (final String sign : availableLanguageSigns) {
						final Text languageTextfield = languageTextFields.get(sign);
						final String value = property.getLanguageValue(sign);
						if (value == null) {
							languageTextfield.setText("");
						} else if (showStorageTexts) {
							languageTextfield.setText(StringEscapeUtils.escapeJava(value));
						} else {
							languageTextfield.setText(value);
						}
					}
				}

				okButton.setText(LangResources.get("button_text_change"));
				removeButton.setEnabled(true);
			} else {
				pathTextfield.setText("");
				keyTextfield.setText("");
				commentTextfield.setText("");
				for (final String sign : availableLanguageSigns) {
					final Text languageTextfield = languageTextFields.get(sign);
					languageTextfield.setText("");
				}

				okButton.setText(LangResources.get("button_text_add"));
				removeButton.setEnabled(false);
			}

			dataWasModified = false;
			checkButtonStatus();
		} catch (final Exception e) {
			new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
		}
	}

	public void checkButtonStatus() {
		if (okButton != null) {
			okButton.setEnabled(dataWasModified);
		}
		if (cancelButton != null) {
			cancelButton.setEnabled(dataWasModified);
		}
		if (saveButton != null) {
			saveButton.setEnabled(hasUnsavedChanges);
		}
		if (exportToExcelButton != null) {
			exportToExcelButton.setEnabled(languageProperties != null);
		}
		if (exportToCsvButton != null) {
			exportToCsvButton.setEnabled(languageProperties != null);
		}
		if (addButton != null) {
			addButton.setEnabled(propertiesTable.getItemCount() > 0);
		}
		if (loadRecentButton != null) {
			loadRecentButton.setEnabled(recentlyOpenedDirectories != null && recentlyOpenedDirectories.size() > 0);
		}
		if (propertiesTable != null) {
			propertiesTable.setEnabled(propertiesTable.getItemCount() > 0);
		}
		if (searchBox != null) {
			searchBox.setEnabled(propertiesTable.getItemCount() > 0);
		}

		if (checkUsageButton != null) {
			checkUsageButton.setEnabled(languageProperties != null && languageProperties.size() > 0);
		}
		if (checkUsageButtonPrevious != null) {
			checkUsageButtonPrevious.setEnabled(checkUsageButton.isEnabled() && recentlyCheckUsages != null && recentlyCheckUsages.size() > 0);
		}
		if (textConversionButton != null) {
			textConversionButton.setEnabled(true);
		}
		if (addLanguageButton != null) {
			addLanguageButton.setEnabled(languageProperties != null && languageProperties.size() > 0);
		}
		if (deleteLanguageButton != null) {
			deleteLanguageButton.setEnabled(languageProperties != null && languageProperties.size() > 0 && availableLanguageSigns != null && availableLanguageSigns.size() > 1);
		}
		if (folderSaveButton != null) {
			folderSaveButton.setEnabled(languageProperties != null && languageProperties.size() > 0);
		}
		if (translateButton != null) {
			translateButton.setEnabled(languageProperties != null && languageProperties.size() > 0 && availableLanguageSigns != null && availableLanguageSigns.size() > 1);
		}
		if (transferButton != null) {
			transferButton.setEnabled(languageProperties != null && languageProperties.size() > 0 && availableLanguageSigns != null && availableLanguageSigns.size() > 1);
		}
		if (clearIdenticalButton != null) {
			clearIdenticalButton.setEnabled(languageProperties != null && languageProperties.size() > 0 && availableLanguageSigns != null && availableLanguageSigns.size() > 1);
		}
		if (removeDuplicatesButton != null) {
			removeDuplicatesButton.setEnabled(languageProperties != null && languageProperties.size() > 0);
		}
		if (checkErrorsButton != null) {
			checkErrorsButton.setEnabled(languageProperties != null && languageProperties.size() > 0);
		}
		if (showStatisticsButton != null) {
			showStatisticsButton.setEnabled(languageProperties != null && languageProperties.size() > 0);
		}
	}

	private boolean askForDropProperties() {
		final QuestionDialog dialog = new QuestionDialog(this, LangResources.get("question_title_delete_property"), LangResources.get("question_content_delete_property"), LangResources.get("yes"), LangResources.get("no"));
		final int returncode = dialog.open();
		return returncode == 0;
	}

	private boolean askForDiscardChanges() {
		final QuestionDialog dialog = new QuestionDialog(this, LangResources.get("question_title_discard_changes"), LangResources.get("question_content_discard_changes"), LangResources.get("yes"), LangResources.get("no"));
		final int returncode = dialog.open();
		return returncode == 0;
	}

	@Override
	public void close() {
		if (!hasUnsavedChanges || askForDiscardChanges()) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE, recentlyCheckUsages);
			applicationConfiguration.save();
			hasUnsavedChanges = false;
			dispose();
		}
	}

	/**
	 * Converts a text field's current content back to its plain (unescaped) form.
	 * When showStorageTexts is active the fields display the escaped storage representation
	 * (see changeDisplayMode), so it needs to be unescaped before it is written back into the model.
	 */
	private String getPlainFieldValue(final String fieldText) {
		return showStorageTexts ? StringEscapeUtils.unescapeJava(fieldText) : fieldText;
	}

	private void changeDisplayMode(final boolean changeToShowStorageTexts) {
		technicalDataChange = true;
		if (changeToShowStorageTexts) {
			keyTextfield.setText(StringEscapeUtils.escapeJava(keyTextfield.getText()));
			for (final Text field : languageTextFields.values()) {
				field.setText(StringEscapeUtils.escapeJava(field.getText()));
			}
		} else  {
			keyTextfield.setText(StringEscapeUtils.unescapeJava(keyTextfield.getText()));
			for (final Text field : languageTextFields.values()) {
				field.setText(StringEscapeUtils.unescapeJava(field.getText()));
			}
		}
		technicalDataChange = false;
	}

	private class OpenFilesSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				final FileDialog fileDialog = new FileDialog(getShell());
				fileDialog.setText(getText() + " " + LangResources.get("open_file_dialog_text"));
				final String filePath = fileDialog.open();
				if (filePath == null) {
					showErrorMessage(LangResources.get("open_file_dialog_text"), LangResources.get("canceledByUser"));
				} else if (new File(filePath).exists() && new File(filePath).isFile()) {
					if (loadSingleLanguagePropertiesSet(filePath)) {
						recentlyOpenedDirectories.add(filePath); //put selected as latest used
						applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
					}
				} else {
					throw new Exception("Selected language properties set path is not an existing file");
				}
			} catch (final Exception e) {
				languageProperties = null;
				availableLanguageSigns = null;
				setLanguagePropertiesSetName(null);
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
			}
			hasUnsavedChanges = false;
			setupTable();
			checkButtonStatus();
		}
	}

	private boolean loadSingleLanguagePropertiesSet(final String filePath) throws ExecutionException {
		final File languagePropertiesFile = new File(filePath);
		if (!languagePropertiesFile.getName().endsWith(applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION))) {
			showErrorMessage(LangResources.get("open_file_dialog_text"), LangResources.get("missingMandatoryFileExtension", applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION)));
			return false;
		} else {
			final LoadLanguagePropertiesWorker openFilesLanguagePropertiesWorker = new LoadLanguagePropertiesWorker(null, languagePropertiesFile, null, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
			openFilesLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
			final ProgressDialog<LoadLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("openFilesResult"), openFilesLanguagePropertiesWorker);
			final Result dialogResult = progressDialog.open();
			if (dialogResult == Result.CANCELED) {
				showErrorMessage(LangResources.get("open_file_dialog_text"), LangResources.get("canceledByUser"));
				return false;
			} else {
				// check for errors
				openFilesLanguagePropertiesWorker.get();

				languageProperties = openFilesLanguagePropertiesWorker.getLanguageProperties();
				availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);
				final List<String> loadedLanguagePropertiesSetNames = openFilesLanguagePropertiesWorker.getLanguagePropertiesSetNames();
				if (loadedLanguagePropertiesSetNames.size() == 1) {
					setLanguagePropertiesSetName(loadedLanguagePropertiesSetNames.get(0));
				} else {
					setLanguagePropertiesSetName("Multiple");
				}

				showMessage(LangResources.get("directory_dialog_title"), LangResources.get("openFilesResult", filePath, languageProperties.size(), Utilities.join(availableLanguageSigns, ", ")));
				return true;
			}
		}
	}

	private class OpenFolderSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				final DirectoryDialog directoryDialog = new DirectoryDialog(getShell());
				directoryDialog.setText(LangResources.get("open_directory_dialog_text"));
				final String basicDirectoryPath = directoryDialog.open();
				if (basicDirectoryPath == null) {
					showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
				} else if (new File(basicDirectoryPath).exists()) {
					if (openAllLanguagePropertiesSets(basicDirectoryPath)) {
						recentlyOpenedDirectories.add(basicDirectoryPath); //put selected as latest used
						applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
					}
				}
			} catch (final Exception e) {
				languageProperties = null;
				availableLanguageSigns = null;
				setLanguagePropertiesSetName(null);
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
			}
			hasUnsavedChanges = false;
			setupTable();
			checkButtonStatus();
		}
	}

	private boolean openAllLanguagePropertiesSets(final String basicDirectoryPath) throws ExecutionException {
		final String[] excludeParts = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES).split(";");
		final LoadLanguagePropertiesWorker openFolderLanguagePropertiesWorker = new LoadLanguagePropertiesWorker(null, new File(basicDirectoryPath), excludeParts, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
		openFolderLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
		final ProgressDialog<LoadLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("load_folder"), openFolderLanguagePropertiesWorker);
		final Result dialogResult = progressDialog.open();
		if (dialogResult == Result.CANCELED) {
			showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
			return false;
		} else {
			// check for errors
			openFolderLanguagePropertiesWorker.get();

			languageProperties = openFolderLanguagePropertiesWorker.getLanguageProperties();
			availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);
			final List<String> loadedLanguagePropertiesSetNames = openFolderLanguagePropertiesWorker.getLanguagePropertiesSetNames();
			if (loadedLanguagePropertiesSetNames.size() == 1) {
				setLanguagePropertiesSetName(loadedLanguagePropertiesSetNames.get(0));
			} else {
				setLanguagePropertiesSetName("Multiple");
			}

			showMessage(LangResources.get("directory_dialog_title"), LangResources.get("openDirectoryResult", basicDirectoryPath, loadedLanguagePropertiesSetNames.size(), languageProperties.size(), Utilities.join(availableLanguageSigns, ", ")));
			return true;
		}
	}

	private void setLanguagePropertiesSetName(final String newLanguagePropertySetName) {
		languagePropertySetName = newLanguagePropertySetName;
		if (Utilities.isNotEmpty(newLanguagePropertySetName)) {
			propertiesLabel.setText(LangResources.get("table_title") + " \"" + newLanguagePropertySetName + "\"");
		} else {
			propertiesLabel.setText(LangResources.get("table_title"));
		}
		propertiesLabel.requestLayout();
	}

	private class OpenRecentSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				final ComboSelectionDialog dialog = new ComboSelectionDialog(getShell(), getText() + " " + LangResources.get("recent_directories_dialog_title"), LangResources.get("recent_directories_dialog_text"), recentlyOpenedDirectories).withSize(600, -1);
				final String filePath = dialog.open();

				// Take over a possible reordering (drag&drop) of the recent directories done in the dialog,
				// regardless of whether an entry was selected or the dialog was canceled
				recentlyOpenedDirectories.clear();
				recentlyOpenedDirectories.addAll(dialog.getItems());
				applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);

				if (filePath == null) {
					showErrorMessage(LangResources.get("recent_directories_dialog_title"), LangResources.get("canceledByUser"));
				} else if (!new File(filePath).exists()) {
					showErrorMessage(LangResources.get("recent_directories_dialog_title"), LangResources.get("error.recentPathDoesNotExistAnymore", filePath));
				} else if (new File(filePath).isDirectory()) {
					openAllLanguagePropertiesSets(filePath);
				} else {
					loadSingleLanguagePropertiesSet(filePath);
				}
			} catch (final Exception e) {
				languageProperties = null;
				availableLanguageSigns = null;
				setLanguagePropertiesSetName(null);
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
			}
			hasUnsavedChanges = false;
			setupTable();
			checkButtonStatus();
		}
	}

	private class SaveFilesSelectionListener extends SelectionAdapter {
		private final LanguagePropertiesManagerDialog mainDialog;

		public SaveFilesSelectionListener(final LanguagePropertiesManagerDialog mainDialog) {
			this.mainDialog = mainDialog;
		}

		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				String defaultLanguagePropertiesPath = null;
				final Set<String> languagePropertiesPaths = languageProperties.stream().map(o -> o.getPath()).collect(Collectors.toSet());
				if (languagePropertiesPaths.contains("")) {
					if (languagePropertiesPaths.size() == 2) {
						languagePropertiesPaths.remove("");
						defaultLanguagePropertiesPath = Utilities.replaceUsersHome(new ArrayList<>(languagePropertiesPaths).get(0));
					} else {
						final String propertiesFileExtension = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION);
						final FileDialog dlg = new FileDialog(getShell(), SWT.SAVE);
						dlg.setText(getShell().getText() + " " + LangResources.get("save_file_dialog_text"));
						dlg.setFilterPath(recentlyOpenedDirectories.getLatestAdded());
						dlg.setFileName("MyLanguageProperties" + propertiesFileExtension);
						final String filePath = dlg.open();
						if (filePath == null) {
							showErrorMessage(LangResources.get("save_file_dialog_text"), LangResources.get("canceledByUser"));
							return;
						}
						// The dialog path still has the file extension, but a LanguagePropertiesSetPath must not include it
						defaultLanguagePropertiesPath = filePath.endsWith(propertiesFileExtension) ? filePath.substring(0, filePath.length() - propertiesFileExtension.length()) : filePath;
					}

					// Assign the determined path to all properties that don't have one yet,
					// so the worker can treat every property by its own (now always non-blank) path.
					for (final LanguageProperty languageProperty : languageProperties) {
						if (Utilities.isBlank(languageProperty.getPath())) {
							languageProperty.setPath(defaultLanguagePropertiesPath);
						}
					}
				}

				final QuestionDialog dialog = new QuestionDialog(mainDialog, getText(), LangResources.get("question.keepExistingProperties"), LangResources.get("yes"), LangResources.get("no"));
				final int returncode = dialog.open();
				final boolean extendAndKeepExistingProperties = (returncode == 0);

				final WriteLanguagePropertiesWorker writeLanguagePropertiesWorker = new WriteLanguagePropertiesWorker(null, languageProperties, languagePropertySetName, null, null, extendAndKeepExistingProperties, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
				writeLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
				final ProgressDialog<WriteLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("save_files"), writeLanguagePropertiesWorker);
				final Result dialogResult = progressDialog.open();
				if (dialogResult == Result.CANCELED) {
					showErrorMessage(LangResources.get("save_file_dialog_text"), LangResources.get("canceledByUser"));
				} else {
					// check for errors
					writeLanguagePropertiesWorker.get();

					showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("saveSuccess"));
				}

				hasUnsavedChanges = false;
				setupTable();
				checkButtonStatus();
			} catch (final Exception e) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
			}
		}
	}

	private class SaveFolderSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				final String[] excludeParts = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES).split(";");

				// A directory selection is only needed to place properties that don't have a path of their own yet.
				// If every property already has a path, it can be saved directly to those paths without asking.
				final boolean hasPropertiesWithoutPath = languageProperties.stream().anyMatch(o -> Utilities.isBlank(o.getPath()));

				File directory = null;
				String newPropertiesSetName = "Multiple";
				if (hasPropertiesWithoutPath) {
					final DirectoryDialog directoryDialog = new DirectoryDialog(getShell());
					directoryDialog.setText(LangResources.get("save_directory_dialog_text"));
					final String directoryPath = directoryDialog.open();
					if (directoryPath == null) {
						showErrorMessage(LangResources.get("save_directory_dialog_text"), LangResources.get("canceledByUser"));
						return;
					} else if (!new File(directoryPath).exists() || !new File(directoryPath).isDirectory()) {
						return;
					}
					directory = new File(directoryPath);

					final SimpleInputDialog nameDialog = new SimpleInputDialog(getShell(), getText(), LangResources.get("enterNewLanguagePropertiesName"));
					nameDialog.setDefaultText(newPropertiesSetName);
					final String enteredName = nameDialog.open();
					if (Utilities.isBlank(enteredName)) {
						showErrorMessage(LangResources.get("save_directory_dialog_text"), LangResources.get("canceledByUser"));
						return;
					}
					newPropertiesSetName = enteredName;
				}

				final QuestionDialog dialog = new QuestionDialog(LanguagePropertiesManagerDialog.this, getText(), LangResources.get("question.keepExistingProperties"), LangResources.get("yes"), LangResources.get("no"));
				final int returncode = dialog.open();
				final boolean extendAndKeepExistingProperties = (returncode == 0);

				final WriteLanguagePropertiesWorker writeLanguagePropertiesWorker = new WriteLanguagePropertiesWorker(null, languageProperties, newPropertiesSetName, directory, excludeParts, extendAndKeepExistingProperties, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
				writeLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
				final ProgressDialog<WriteLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("save_files"), writeLanguagePropertiesWorker);
				final Result dialogResult = progressDialog.open();
				if (dialogResult == Result.CANCELED) {
					showErrorMessage(LangResources.get("save_directory_dialog_text"), LangResources.get("canceledByUser"));
				} else {
					// check for errors
					writeLanguagePropertiesWorker.get();

					showData(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("saveDirectoryResult", Utilities.join(writeLanguagePropertiesWorker.getListOfStoredProperties(), "\n")));
				}

				hasUnsavedChanges = false;
				setupTable();
				checkButtonStatus();
			} catch (final Exception e) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
			}
		}
	}

	private class ImportFromExcelSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			final FileDialog fileDialog = new FileDialog(getShell(), SWT.OPEN);
			fileDialog.setText(getShell().getText() + " " + LangResources.get("import_file"));
			fileDialog.setFilterPath(Utilities.replaceUsersHome("~" + File.separator + "Downloads" + File.separator + ""));
			fileDialog.setFilterExtensions(new String[] { "*.xlsx", "*" });
			final String importFile = fileDialog.open();
			if (importFile == null) {
				showErrorMessage(LangResources.get("import_file"), LangResources.get("canceledByUser"));
			} else {
				try {
					final ImportFromExcelWorker importFromExcelWorker = new ImportFromExcelWorker(null, new File(importFile));
					importFromExcelWorker.setIgnoreComments(applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
					final ProgressDialog<ImportFromExcelWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("import_file"), importFromExcelWorker);
					final Result dialogResult = progressDialog.open();
					if (dialogResult == Result.CANCELED) {
						showErrorMessage(LangResources.get("import_file"), LangResources.get("canceledByUser"));
					} else {
						// check for errors
						importFromExcelWorker.get();

						setLanguagePropertiesSetName(importFromExcelWorker.getLanguagePropertiesSetName());
						languageProperties = importFromExcelWorker.getLanguageProperties();
						availableLanguageSigns = importFromExcelWorker.getAvailableLanguageSigns();
						hasUnsavedChanges = true;

						setupTable();
						showMessage(LangResources.get("import_file"), LangResources.get("actionSuccessfullyCompleted"));
					}
				} catch (final ExecutionException e) {
					languageProperties = null;
					availableLanguageSigns = null;
					languagePropertySetName = null;
					hasUnsavedChanges = false;
					if (e.getCause() != null && e.getCause() instanceof LanguagePropertiesException) {
						showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, e.getCause().getMessage());
					} else {
						new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
					}
					setupTable();
				} catch (final Exception e) {
					languageProperties = null;
					availableLanguageSigns = null;
					languagePropertySetName = null;
					hasUnsavedChanges = false;
					new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
					setupTable();
				}
				checkButtonStatus();
			}
		}
	}

	private class ImportFromCsvSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			final FileDialog fileDialog = new FileDialog(getShell(), SWT.OPEN);
			fileDialog.setText(getShell().getText() + " " + LangResources.get("import_file"));
			fileDialog.setFilterPath(Utilities.replaceUsersHome("~" + File.separator + "Downloads" + File.separator + ""));
			fileDialog.setFilterExtensions(new String[] { "*.csv", "*.dsv", "*" });
			final String importFile = fileDialog.open();
			if (importFile == null) {
				showErrorMessage(LangResources.get("import_file"), LangResources.get("canceledByUser"));
			} else {
				try {
					final ImportFromCsvWorker importFromCsvWorker = new ImportFromCsvWorker(null, new File(importFile));
					importFromCsvWorker.setIgnoreComments(applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
					final ProgressDialog<ImportFromCsvWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("import_file"), importFromCsvWorker);
					final Result dialogResult = progressDialog.open();
					if (dialogResult == Result.CANCELED) {
						showErrorMessage(LangResources.get("import_file"), LangResources.get("canceledByUser"));
					} else {
						// check for errors
						importFromCsvWorker.get();

						showMessage(LangResources.get("import_file"), LangResources.get("actionSuccessfullyCompleted"));

						setLanguagePropertiesSetName(importFromCsvWorker.getLanguagePropertiesSetName());
						languageProperties = importFromCsvWorker.getLanguageProperties();
						availableLanguageSigns = importFromCsvWorker.getAvailableLanguageSigns();
						hasUnsavedChanges = true;
					}
				} catch (final ExecutionException e) {
					languageProperties = null;
					availableLanguageSigns = null;
					languagePropertySetName = null;
					hasUnsavedChanges = false;
					if (e.getCause() != null && e.getCause() instanceof LanguagePropertiesException) {
						showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, e.getCause().getMessage());
					} else {
						new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
					}
				} catch (final Exception e) {
					languageProperties = null;
					availableLanguageSigns = null;
					languagePropertySetName = null;
					hasUnsavedChanges = false;
					new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
				}
				setupTable();
				checkButtonStatus();
			}
		}
	}

	private class ExcelExportSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				final FileDialog fileDialog = new FileDialog(getShell(), SWT.SAVE);
				fileDialog.setText(getShell().getText() + " " + LangResources.get("export_file"));
				fileDialog.setFilterPath(Utilities.replaceUsersHome("~" + File.separator + "Downloads"));
				fileDialog.setFileName(languagePropertySetName + "_Export_" + DateUtilities.formatDate("yyyy-MM-dd_HH-mm", LocalDateTime.now()) + ".xlsx");
				final String exportFile = fileDialog.open();
				if (exportFile == null) {
					showErrorMessage(LangResources.get("export_file"), LangResources.get("canceledByUser"));
				} else {
					if (new File(exportFile).exists()) {
						if (!askForOverwriteFile(exportFile)) {
							throw new Exception(LangResources.get("error.destinationFileAlreadyExists", exportFile));
						} else {
							new File(exportFile).delete();
						}
					}

					try {
						final List<String> languagePropertySetNames = new ArrayList<>();
						languagePropertySetNames.add(languagePropertySetName);
						final ExportToExcelWorker exportToExcelWorker = new ExportToExcelWorker(null, languageProperties, languagePropertySetNames, new File(exportFile), false);
						final ProgressDialog<ExportToExcelWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("export_file"), exportToExcelWorker);
						final Result dialogResult = progressDialog.open();
						if (dialogResult == Result.CANCELED) {
							showErrorMessage(LangResources.get("export_file"), LangResources.get("canceledByUser"));
						} else {
							// check for errors
							exportToExcelWorker.get();

							hasUnsavedChanges = false;
							showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("exportSuccess"));
						}
					} catch (final Exception e) {
						new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
					}
					checkButtonStatus();
				}
			} catch (final Exception e) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
			}
		}
	}

	private class CsvExportSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				final FileDialog fileDialog = new FileDialog(getShell(), SWT.SAVE);
				fileDialog.setText(getShell().getText() + " " + LangResources.get("export_file"));
				fileDialog.setFilterPath(Utilities.replaceUsersHome("~" + File.separator + "Downloads"));
				fileDialog.setFileName(languagePropertySetName + "_Export_" + DateUtilities.formatDate("yyyy-MM-dd_HH-mm", LocalDateTime.now()) + ".csv");
				final String exportFile = fileDialog.open();
				if (exportFile == null) {
					showErrorMessage(LangResources.get("export_file"), LangResources.get("canceledByUser"));
				} else {
					if (new File(exportFile).exists()) {
						if (!askForOverwriteFile(exportFile)) {
							throw new Exception(LangResources.get("error.destinationFileAlreadyExists", exportFile));
						} else {
							new File(exportFile).delete();
						}
					}

					try {
						final List<String> languagePropertySetNames = new ArrayList<>();
						languagePropertySetNames.add(languagePropertySetName);
						final ExportToCsvWorker exportToCsvWorker = new ExportToCsvWorker(null, languageProperties, languagePropertySetNames, new File(exportFile), false);
						final ProgressDialog<ExportToCsvWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("export_file"), exportToCsvWorker);
						final Result dialogResult = progressDialog.open();
						if (dialogResult == Result.CANCELED) {
							showErrorMessage(LangResources.get("export_file"), LangResources.get("canceledByUser"));
						} else {
							// check for errors
							exportToCsvWorker.get();

							hasUnsavedChanges = false;
							showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("exportSuccess"));
						}
					} catch (final Exception e) {
						new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
					}
					checkButtonStatus();
				}
			} catch (final Exception e) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
			}
		}
	}

	private class ColumnSortListener implements Listener {
		@Override
		public void handleEvent(final Event event) {
			final TableColumn columnToSort = (TableColumn) event.widget;
			final Table table = columnToSort.getParent();
			if (columnToSort == table.getSortColumn()) {
				if (table.getSortDirection() == SWT.UP) {
					table.setSortDirection(SWT.DOWN);
				} else {
					table.setSortDirection(SWT.UP);
				}
			} else {
				table.setSortDirection(SWT.UP);
			}
			table.setSortColumn(columnToSort);

			if (columnToSort.getText().equals(LangResources.get("columnheader_key"))) {
				final Comparator<LanguageProperty> compareByName = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getKey);
				if (table.getSortDirection() == SWT.UP) {
					languageProperties = languageProperties.stream().sorted(compareByName).collect(Collectors.toList());
				} else {
					languageProperties = languageProperties.stream().sorted(compareByName.reversed()).collect(Collectors.toList());
				}
			} else if (columnToSort.getText().equals(LangResources.get("columnheader_original_index")) || columnToSort.getText().equals(LangResources.get("columnheader_path"))) {
				final Comparator<LanguageProperty> compareByIndex = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getOriginalIndex);
				if (table.getSortDirection() == SWT.UP) {
					languageProperties = languageProperties.stream().sorted(compareByIndex).collect(Collectors.toList());
				} else {
					languageProperties = languageProperties.stream().sorted(compareByIndex.reversed()).collect(Collectors.toList());
				}
			} else if (columnToSort.getText().equals(LangResources.get("columnheader_default"))) {
				if (table.getSortDirection() == SWT.UP) {
					languageProperties = languageProperties.stream().sorted((o1, o2) -> (getEmptyForNull(o1.getLanguageValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT))).compareTo(getEmptyForNull(o2.getLanguageValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT)))).collect(Collectors.toList());
				} else {
					languageProperties = languageProperties.stream().sorted((o1, o2) -> (getEmptyForNull(o1.getLanguageValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT))).compareTo(getEmptyForNull(o2.getLanguageValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT))) * -1).collect(Collectors.toList());
				}
			} else {
				if (table.getSortDirection() == SWT.UP) {
					languageProperties = languageProperties.stream().sorted((o1, o2) -> (getEmptyForNull(o1.getLanguageValue(columnToSort.getText()))).compareTo(getEmptyForNull(o2.getLanguageValue(columnToSort.getText())))).collect(Collectors.toList());
				} else {
					languageProperties = languageProperties.stream().sorted((o1, o2) -> (getEmptyForNull(o1.getLanguageValue(columnToSort.getText()))).compareTo(getEmptyForNull(o2.getLanguageValue(columnToSort.getText()))) * -1).collect(Collectors.toList());
				}
			}

			refreshTable();
		}
	}

	private static String getEmptyForNull(final String string) {
		return string == null ? "" : string;
	}

	private class FillDataListener implements Listener {
		@Override
		public void handleEvent(final Event event) {
			final TableItem item = (TableItem) event.item;
			final int index = propertiesTable.indexOf(item);
			fillPropertyDataInTableItem(index, item);
		}
	}

	public void fillPropertyDataInTableItem(final int index, final TableItem item) {
		final String key = languageProperties.get(index).getKey();
		final LanguageProperty languageProperty = languageProperties.get(index);
		// 0 = DummyColumn
		item.setText(1, Integer.toString(index + 1));
		item.setText(columnPathIndex, languageProperty.getPath());
		item.setText(columnOrigIndexIndex, Integer.toString(languageProperty.getOriginalIndex()));
		item.setText(columnKeyIndex, key);

		int i = 5;
		for (final String languageSign : availableLanguageSigns) {
			final String value = languageProperty.getLanguageValue(languageSign);
			item.setText(i++, Utilities.isEmpty(value) ? LangResources.get("value_not_found_sign") : LangResources.get("value_found_sign"));
		}
	}

	private void refreshTable() {
		propertiesTable.deselectAll();

		propertiesTable.setRedraw(false);
		propertiesTable.clearAll();
		propertiesTable.setRedraw(true);

		if (currentSelectedKeys != null && currentSelectedKeys.size() > 0) {
			final int[] indices = new int[currentSelectedKeys.size()];
			for (int i = 0; i < currentSelectedKeys.size(); i++) {
				for (int searchIndex = 0; searchIndex < languageProperties.size(); searchIndex++) {
					final LanguageProperty languageProperty = languageProperties.get(searchIndex);
					if (languageProperty.getPath().equals(currentSelectedKeys.get(i).getFirst()) && languageProperty.getKey().equals(currentSelectedKeys.get(i).getSecond())) {
						indices[i] = searchIndex;
						break;
					}
				}
			}
			propertiesTable.setSelection(indices);
			propertiesTable.showSelection();
		}
	}

	private List<Tuple<String, String>> getSelectedKeys() {
		final List<Tuple<String, String>> returnList = new ArrayList<>();
		for (final TableItem item : propertiesTable.getSelection()) {
			returnList.add(new Tuple<>(item.getText(columnPathIndex), item.getText(columnKeyIndex)));
		}
		return returnList;
	}

	public static String[] getTextValues(final TableItem item) {
		final String[] returnValue = new String[item.getParent().getColumnCount()];
		for (int i = 0; i < returnValue.length; i++) {
			returnValue[i] = item.getText(i);
		}
		return returnValue;
	}

	private void selectSearch(final String text, int startIndex, final boolean searchUp, final boolean searchCaseInsensitive,
			final boolean searchInKeys, final boolean searchInValues, final boolean searchInPath) {
		propertiesTable.deselectAll();

		if (Utilities.isNotEmpty(text) && languageProperties != null && (searchInKeys || searchInValues || searchInPath)) {
			if (startIndex < 0) {
				startIndex = languageProperties.size() - 1;
			} else if (startIndex >= languageProperties.size()) {
				startIndex = 0;
			}

			int currentIndex = -1;
			while (currentIndex != startIndex) {
				if (currentIndex == -1) {
					currentIndex = startIndex;
				}

				if (matchesSearch(languageProperties.get(currentIndex), text, searchCaseInsensitive, searchInKeys, searchInValues, searchInPath)) {
					propertiesTable.setSelection(currentIndex);
					refreshDetailView();
					break;
				}

				if (searchUp) {
					currentIndex++;
				} else {
					currentIndex--;
				}

				if (currentIndex < 0) {
					currentIndex = languageProperties.size() - 1;
				} else if (currentIndex >= languageProperties.size()) {
					currentIndex = 0;
				}
			}
		}
	}

	private static boolean matchesSearch(final LanguageProperty languageProperty, final String searchText, final boolean searchCaseInsensitive,
			final boolean searchInKeys, final boolean searchInValues, final boolean searchInPath) {
		if (searchInKeys && containsIgnoringCase(languageProperty.getKey(), searchText, searchCaseInsensitive)) {
			return true;
		} else if (searchInPath && containsIgnoringCase(languageProperty.getPath(), searchText, searchCaseInsensitive)) {
			return true;
		} else if (searchInValues && containsLanguageValuePart(languageProperty, searchText, searchCaseInsensitive)) {
			return true;
		} else {
			return false;
		}
	}

	private static boolean containsIgnoringCase(final String haystack, final String needle, final boolean searchCaseInsensitive) {
		if (haystack == null) {
			return false;
		} else if (searchCaseInsensitive) {
			return haystack.toLowerCase().contains(needle.toLowerCase());
		} else {
			return haystack.contains(needle);
		}
	}

	private static boolean containsLanguageValuePart(final LanguageProperty languageProperty, final String searchText, final boolean searchCaseInsensitive) {
		for (final String languageSign : languageProperty.getAvailableLanguageSigns()) {
			if (containsIgnoringCase(languageProperty.getLanguageValue(languageSign), searchText, searchCaseInsensitive)) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unused")
	public void checkUsage(final List<LanguageProperty> storageToCheck, final String directory, final String filePattern, final String usagePatternString) throws Exception {
		final Set<String> existingDefaultProperties = new HashSet<>();
		final Set<String> existingOverallProperties = new HashSet<>();
		final Set<String> missingDefaultProperties = new HashSet<>();
		final Set<String> missingOverallProperties = new HashSet<>();
		final Set<String> usedProperties = new HashSet<>();
		final Set<String> unusedProperties = new HashSet<>();
		final Set<File> filesWithMissingValues = new HashSet<>();

		for (final LanguageProperty languageProperty : languageProperties) {
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

		new ShowDataDialog(getShell(), LangResources.get("usagereport"), reportText, true).open();
	}


	public boolean askForOverwriteFile(final String filePath) {
		final QuestionDialog dialog = new QuestionDialog(this, getText(), LangResources.get("question.overwritefile", filePath), LangResources.get("overwrite"), LangResources.get("cancel")).setBackgroundColor(SwtColor.LightRed);
		final int returncode = dialog.open();
		return returncode == 0;
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

	protected boolean dailyUpdateCheckIsPending() {
		return applicationConfiguration.getBoolean(ConfigurationProperties.CONFIG_KEY_DAILY_UPDATE_CHECK)
				&& (applicationConfiguration.getDate(ConfigurationProperties.CONFIG_KEY_NEXT_DAILY_UPDATE_CHECK) == null || applicationConfiguration.getDate(ConfigurationProperties.CONFIG_KEY_NEXT_DAILY_UPDATE_CHECK).isBefore(LocalDateTime.now()))
				&& NetworkUtilities.checkForNetworkConnection();
	}

	public void showData(final String title, final String text) {
		new ShowDataDialog(getShell(), title, text, true).open();
	}

	public void showMessage(final String title, final String text) {
		new QuestionDialog(getShell(), title, text, LangResources.get("ok")).open();
	}

	public void showErrorMessage(final String title, final String text) {
		new QuestionDialog(getShell(), title, text, LangResources.get("ok")).setBackgroundColor(SwtColor.LightRed).open();
	}
}
