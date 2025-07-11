package de.soderer.languagepropertiesmanager.dlg;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
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
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import de.soderer.languagepropertiesmanager.LanguagePropertiesManager;
import de.soderer.languagepropertiesmanager.image.ImageManager;
import de.soderer.languagepropertiesmanager.storage.ExcelHelper;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.languagepropertiesmanager.worker.ExportToExcelWorker;
import de.soderer.languagepropertiesmanager.worker.ImportFromExcelWorker;
import de.soderer.languagepropertiesmanager.worker.LoadLanguagePropertiesWorker;
import de.soderer.languagepropertiesmanager.worker.WriteLanguagePropertiesWorker;
import de.soderer.network.NetworkUtilities;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.DateUtilities;
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
	// TODO
	public static boolean temporaryDisabled = true;

	private boolean showStorageTexts = false;
	private boolean dataWasModified = false;
	private boolean hasUnsavedChanges = false;
	private boolean technicalDataChange = false;

	private Label propertiesLabel;
	private Composite rightPart = null;
	private Button removeButton;
	private Button cleanupButton;
	private Button saveButton;
	private Button folderSaveButton;
	private Button exportToExcelButton;
	private Button importFromExcelButton;
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
	private boolean searchValuePreference = false;
	private Button checkUsageButton;
	private Button checkUsageButtonPrevious;
	private Button addLanguageButton;
	private Button deleteLanguageButton;

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

		final ProxyConfigurationType proxyConfigurationType = ProxyConfigurationType.getFromString(applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE));
		final String proxyUrl = applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_URL);
		final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(proxyConfigurationType, proxyUrl);

		if (dailyUpdateCheckIsPending()) {
			setDailyUpdateCheckStatus(true);
			try {
				if (ApplicationUpdateUtilities.checkForNewVersionAvailable(this, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION) != null) {
					ApplicationUpdateUtilities.executeUpdate(this, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, true);
				}
			} catch (final Exception e) {
				showErrorMessage(LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", e.getMessage()));
			}
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
		importFromExcelButton.setToolTipText(LangResources.get("tooltip_import"));
		importFromExcelButton.addSelectionListener(new ImportSelectionListener());

		saveButton = new Button(buttonSection1, SWT.PUSH);
		saveButton.setImage(ImageManager.getImage("save.png"));
		saveButton.setToolTipText(LangResources.get("tooltip_save_files"));
		saveButton.addSelectionListener(new SaveFilesSelectionListener());

		folderSaveButton = new Button(buttonSection1, SWT.PUSH);
		folderSaveButton.setImage(ImageManager.getImage("folderSave.png"));
		folderSaveButton.setToolTipText(LangResources.get("tooltip_save_folder"));
		folderSaveButton.addSelectionListener(new SaveFolderSelectionListener());

		exportToExcelButton = new Button(buttonSection1, SWT.PUSH);
		exportToExcelButton.setImage(ImageManager.getImage("excelSave.png"));
		exportToExcelButton.setToolTipText(LangResources.get("tooltip_export"));
		exportToExcelButton.addSelectionListener(new ExportSelectionListener());

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

		cleanupButton = new Button(buttonSection2, SWT.PUSH);
		cleanupButton.setImage(ImageManager.getImage("clean.png"));
		cleanupButton.setToolTipText(LangResources.get("tooltip_cleanup_values"));
		cleanupButton.setEnabled(false);
		cleanupButton.addSelectionListener(new CleanupButtonSelectionListener());

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

		// Searching
		searchBox = new Composite(leftPart, SWT.BORDER);
		searchBox.setLayout(SwtUtilities.createSmallMarginGridLayout(5, false));
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
					selectSearch(searchText, propertiesTable.getSelectionIndex(), true, searchCaseInsensitivePreference, searchValuePreference);
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
					selectSearch(searchText, propertiesTable.getSelectionIndex() + 1, true, searchCaseInsensitivePreference, searchValuePreference);
				}
			}

			@Override
			public void widgetDefaultSelected(final SelectionEvent e) {
				if (Utilities.isNotEmpty(searchText)) {
					selectSearch(searchText, propertiesTable.getSelectionIndex() + 1, true, searchCaseInsensitivePreference, searchValuePreference);
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
				selectSearch(searchText, propertiesTable.getSelectionIndex() - 1, false, searchCaseInsensitivePreference, searchValuePreference);
			}
		});

		final Button caseButton = new Button(searchBox, SWT.CHECK);
		caseButton.setText("Aa");
		caseButton.setToolTipText(LangResources.get("case_sensitive"));
		caseButton.setLayoutData(new GridData(35, 20));
		caseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				searchCaseInsensitivePreference = !((Button)e.widget).getSelection();
				if (Utilities.isNotEmpty(searchText) && !searchText.equals(LangResources.get("search")) && languageProperties != null) {
					if (propertiesTable.getSelectionCount() == 0) {
						propertiesTable.setSelection(0);
					}
					selectSearch(searchText, propertiesTable.getSelectionIndex(), true, searchCaseInsensitivePreference, searchValuePreference);
				}
			}
		});

		final Button valueButton = new Button(searchBox, SWT.CHECK);
		valueButton.setText(LangResources.get("value"));
		valueButton.setToolTipText(LangResources.get("value"));
		valueButton.setLayoutData(new GridData(45, 20));
		valueButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				searchValuePreference = ((Button)e.widget).getSelection();
				if (Utilities.isNotEmpty(searchText) && !searchText.equals(LangResources.get("search")) && languageProperties != null) {
					if (propertiesTable.getSelectionCount() == 0) {
						propertiesTable.setSelection(0);
					}
					selectSearch(searchText, propertiesTable.getSelectionIndex(), true, searchCaseInsensitivePreference, searchValuePreference);
				}
			}
		});

		propertiesTable = new Table(leftPart, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
		propertiesTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 7, 1));
		propertiesTable.setHeaderVisible(true);
		propertiesTable.setLinesVisible(true);
		propertiesTable.addSelectionListener(new TableItemSelectionListener());

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
						final String newKey = keyTextfield.getText();

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
							propertyToChange.setLanguageValue(languageKey, languageTextFields.get(languageKey).getText());
						}

						refreshTable();
						dataWasModified = false;
						checkButtonStatus();
					} else {
						final LanguageProperty newValues = new LanguageProperty(pathTextfield.getText(), keyTextfield.getText());
						for (final String languageKey : languageTextFields.keySet()) {
							newValues.setLanguageValue(languageKey, languageTextFields.get(languageKey).getText());
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
							availableLanguageSigns.add("default");

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
			try {
				if (askForDropProperties()) {
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
	}

	private class CleanupButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			try {
				if (askForDropDuplicateProperties()) {
					final boolean cleanupChangedData = false;

					//					public boolean cleanUp(final boolean repairpunctuation) throws Exception {
					//		boolean dataWasChanged = false;
					//
					//		for (final LanguageProperty property : properties.values()) {
					//			// Store original values for later change check
					//			final HashMap<String, String> originalData = new HashMap<>();
					//			for (final String sign : getLanguageSigns()) {
					//				originalData.put(sign, property.getLanguageValue(sign));
					//			}
					//
					//			for (final String sign : getLanguageSigns()) {
					//				String value = property.getLanguageValue(sign);
					//				if (value != null) {
					//					// clear blank before and after "<br>"
					//					value = value.replace(" <br>", "<br>").replace("<br> ", "<br>");
					//					// remove "<br>" at end
					//					if (value.endsWith("<br>")) {
					//						value = value.substring(0, value.length() - 4).trim();
					//					}
					//					// remove "<br />" at end
					//					if (value.endsWith("<br />")) {
					//						value = value.substring(0, value.length() - 6).trim();
					//					}
					//					property.setLanguageValue(sign, value);
					//				}
					//
					//				// Clear empty values
					//				if (Utilities.isEmpty(property.getLanguageValue(sign))) {
					//					property.removeLanguageValue(sign);
					//				}
					//			}
					//
					//			if (repairpunctuation) {
					//				// Check exclamationmark, questionmark and fullstop unification
					//				boolean hasExclamationEnd = false;
					//				boolean hasQuestionEnd = false;
					//				boolean hasFullstopEnd = false;
					//				boolean hasColonEnd = false;
					//				for (final String sign : getLanguageSigns()) {
					//					final String value = property.getLanguageValue(sign);
					//					if (value != null) {
					//						if (!sign.equalsIgnoreCase("zh")) {
					//							if (value.endsWith("!")) {
					//								hasExclamationEnd = true;
					//							} else if (value.endsWith("?")) {
					//								hasQuestionEnd = true;
					//							} else if (value.endsWith(":")) {
					//								hasColonEnd = true;
					//							} else if (value.endsWith(".")) {
					//								hasFullstopEnd = true;
					//							}
					//						} else {
					//							if (value.endsWith("\\uFF01")) { // "!"
					//								hasExclamationEnd = true;
					//							} else if (value.endsWith("\\uFF1F")) { // "?"
					//								hasQuestionEnd = true;
					//							} else if (value.endsWith("\\uFF1A")) { // ":"
					//								hasColonEnd = true;
					//							} else if (value.endsWith("\\u3002")) { // "."
					//								hasFullstopEnd = true;
					//							}
					//						}
					//					}
					//				}
					//				for (final String sign : getLanguageSigns()) {
					//					String value = property.getLanguageValue(sign);
					//					if (value != null) {
					//						if (!sign.equalsIgnoreCase("zh")) {
					//							if (hasExclamationEnd) {
					//								if (value.endsWith("?") || value.endsWith(":") || value.endsWith(".")) {
					//									value = value.substring(0, value.length() - 1);
					//								}
					//								if (!value.endsWith("!")) {
					//									value += "!";
					//								}
					//							} else if (hasQuestionEnd) {
					//								if (value.endsWith(":") || value.endsWith(".")) {
					//									value = value.substring(0, value.length() - 1);
					//								}
					//								if (!value.endsWith("?")) {
					//									value += "?";
					//								}
					//							} else if (hasColonEnd) {
					//								if (value.endsWith(".")) {
					//									value = value.substring(0, value.length() - 1);
					//								}
					//								if (!value.endsWith(":")) {
					//									value += ":";
					//								}
					//							} else if (hasFullstopEnd) {
					//								if (!value.endsWith(".")) {
					//									value += ".";
					//								}
					//							}
					//						} else {
					//							if (hasExclamationEnd) {
					//								if (value.endsWith("\\uFF1F") || value.endsWith("\\uFF1A") || value.endsWith("\\u3002")) {
					//									value = value.substring(0, value.length() - 6);
					//								}
					//								if (!value.endsWith("\\uFF01")) {
					//									value += "\\uFF01";
					//								}
					//							} else if (hasQuestionEnd) {
					//								if (value.endsWith("\\uFF1A") || value.endsWith("\\u3002")) {
					//									value = value.substring(0, value.length() - 6);
					//								}
					//								if (!value.endsWith("\\uFF1F")) {
					//									value += "\\uFF1F";
					//								}
					//							} else if (hasQuestionEnd) {
					//								if (value.endsWith("\\u3002")) {
					//									value = value.substring(0, value.length() - 6);
					//								}
					//								if (!value.endsWith("\\uFF1A")) {
					//									value += "\\uFF1A";
					//								}
					//							} else if (hasFullstopEnd) {
					//								if (!value.endsWith("\\u3002")) {
					//									value += "\\u3002";
					//								}
					//							}
					//						}
					//
					//						property.setLanguageValue(sign, value);
					//					}
					//				}
					//			}
					//
					//			final List<String> signs = getLanguageSigns();
					//			signs.remove(LANGUAGE_SIGN_DEFAULT);
					//			for (final String sign : signs) {
					//				if (property.getLanguageValue(LANGUAGE_SIGN_DEFAULT) != null
					//						&& Utilities.isNotEmpty(property.getLanguageValue(sign))
					//						&& property.getLanguageValue(LANGUAGE_SIGN_DEFAULT).equals(property.getLanguageValue(sign))) {
					//					property.removeLanguageValue(sign);
					//				} else if (sign.contains("_")) {
					//					final String firstSignPart = sign.substring(0, sign.indexOf("_"));
					//					if (property.getLanguageValue(firstSignPart) != null
					//							&& Utilities.isNotEmpty(property.getLanguageValue(sign))
					//							&& property.getLanguageValue(firstSignPart).equals(property.getLanguageValue(sign))) {
					//						property.removeLanguageValue(sign);
					//					}
					//				}
					//			}
					//
					//			// Languagespecific changes
					//			for (final String sign : getLanguageSigns()) {
					//				if (sign.equalsIgnoreCase("es") && property.getLanguageValue(sign) != null) {
					//					String value = property.getLanguageValue(sign);
					//					if (value.endsWith("!") && !value.startsWith("\\u00A1")) {
					//						value = "\\u00A1" + value;
					//					} else if (value.endsWith("?") && !value.startsWith("\\u00BF")) {
					//						value = "\\u00BF" + value;
					//					}
					//					property.setLanguageValue(sign, value);
					//				}
					//
					//				if (sign.equals("fr") && property.getLanguageValue(sign) != null) {
					//					String value = property.getLanguageValue(sign);
					//					value = value.replace("!", " !").replace("  !", " !").replace("?", " ?").replace("  ?", " ?").replace(":", " :").replace("  :", " :");
					//					property.setLanguageValue(sign, value);
					//				} else if (!sign.equalsIgnoreCase("fr") && property.getLanguageValue(sign) != null) {
					//					String value = property.getLanguageValue(sign);
					//					value = value.replace(" !", "!").replace(" ?", "?").replace(" :", ":");
					//					property.setLanguageValue(sign, value);
					//				}
					//			}
					//
					//			// Change check
					//			for (final String sign : getLanguageSigns()) {
					//				if (originalData.get(sign) == null && property.getLanguageValue(sign) != null) {
					//					dataWasChanged = true;
					//				} else if (originalData.get(sign) != null && !originalData.get(sign).equals(property.getLanguageValue(sign))) {
					//					dataWasChanged = true;
					//				}
					//			}
					//		}
					//
					//		return dataWasChanged;
					//	}

					hasUnsavedChanges = cleanupChangedData || hasUnsavedChanges;
					refreshTable();
					refreshDetailView();
					if (!cleanupChangedData) {
						final MessageBox messageBox = new MessageBox(getShell());
						messageBox.setText(LangResources.get("nochange_needed"));
						messageBox.setMessage(LangResources.get("nochange_needed"));
						messageBox.open();
					}
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
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
					availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), "default");
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
					availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), "default");
					setupTable();
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
			checkButtonStatus();
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
					final String filePattern = dialog2.open();
					if (filePattern == null) {
						showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
					} else {
						final SimpleInputDialog dialog3 = new SimpleInputDialog(getShell(), getText(), LangResources.get("enterusagepattern"));
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
					final List<String> settings = CsvReader.parseCsvLine(new CsvFormat().setSeparator(';').setStringQuote('"'), setting);
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
							languageTextfield.setText(value);
						} else {
							languageTextfield.setText(StringEscapeUtils.unescapeJava(value));
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
		if (addButton != null) {
			addButton.setEnabled(propertiesTable.getItemCount() > 0);
		}
		if (cleanupButton != null && !temporaryDisabled) {
			cleanupButton.setEnabled(propertiesTable.getItemCount() > 0);
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

		if (checkUsageButton != null && !temporaryDisabled) {
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
	}

	private boolean askForDropProperties() {
		final MessageBox messageBox = new MessageBox(LanguagePropertiesManagerDialog.this, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
		messageBox.setText(LangResources.get("question_title_delete_property"));
		messageBox.setMessage(LangResources.get("question_content_delete_property"));
		final int returncode = messageBox.open();

		return (returncode == SWT.YES);
	}

	private boolean askForDropDuplicateProperties() {
		final MessageBox messageBox = new MessageBox(LanguagePropertiesManagerDialog.this, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
		messageBox.setText(LangResources.get("question_title_cleanup_properties"));
		messageBox.setMessage(LangResources.get("question_content_cleanup_properties"));
		final int returncode = messageBox.open();

		return (returncode == SWT.YES);
	}

	private boolean askForDiscardChanges() {
		final MessageBox messageBox = new MessageBox(LanguagePropertiesManagerDialog.this, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
		messageBox.setText(LangResources.get("question_title_discard_changes"));
		messageBox.setMessage(LangResources.get("question_content_discard_changes"));
		final int returncode = messageBox.open();

		return (returncode == SWT.YES);
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
				fileDialog.setText(getText() + " " + LangResources.get("directory_dialog_title"));
				fileDialog.setText(LangResources.get("open_directory_dialog_text"));
				final String filePath = fileDialog.open();
				if (filePath == null) {
					showErrorMessage(LangResources.get("directory_dialog_title"), LangResources.get("canceledByUser"));
				} else if (new File(filePath).exists() && new File(filePath).isFile()) {
					recentlyOpenedDirectories.add(filePath); //put selected as latest used
					applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);

					loadSingleLanguagePropertiesSet(filePath);
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

	private void loadSingleLanguagePropertiesSet(final String filePath) throws ExecutionException {
		final LoadLanguagePropertiesWorker openFilesLanguagePropertiesWorker = new LoadLanguagePropertiesWorker(null, new File(filePath), null);
		final ProgressDialog<LoadLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("openFilesResult"), openFilesLanguagePropertiesWorker);
		final Result dialogResult = progressDialog.open();
		if (dialogResult == Result.CANCELED) {
			showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
		} else {
			// check for errors
			openFilesLanguagePropertiesWorker.get();

			languageProperties = openFilesLanguagePropertiesWorker.getLanguageProperties();
			availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), "default");
			setLanguagePropertiesSetName("Multiple");

			showMessage(LangResources.get("directory_dialog_title"), LangResources.get("openFilesResult", filePath, languageProperties.size(), Utilities.join(availableLanguageSigns, ", ")));
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
					recentlyOpenedDirectories.add(basicDirectoryPath); //put selected as latest used
					applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);

					openAllLanguagePropertiesSets(basicDirectoryPath);
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

	private void openAllLanguagePropertiesSets(final String basicDirectoryPath) throws ExecutionException {
		final String[] excludeParts = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES).split(";");
		final LoadLanguagePropertiesWorker openFolderLanguagePropertiesWorker = new LoadLanguagePropertiesWorker(null, new File(basicDirectoryPath), excludeParts);
		final ProgressDialog<LoadLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("load_folder"), openFolderLanguagePropertiesWorker);
		final Result dialogResult = progressDialog.open();
		if (dialogResult == Result.CANCELED) {
			showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
		} else {
			// check for errors
			openFolderLanguagePropertiesWorker.get();

			languageProperties = openFolderLanguagePropertiesWorker.getLanguageProperties();
			availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), "default");
			setLanguagePropertiesSetName("Multiple");

			showMessage(LangResources.get("directory_dialog_title"), LangResources.get("openDirectoryResult", basicDirectoryPath, openFolderLanguagePropertiesWorker.getLanguagePropertiesSetNames().size(), languageProperties.size(), Utilities.join(availableLanguageSigns, ", ")));
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
				final ComboSelectionDialog dialog = new ComboSelectionDialog(getShell(), getText() + " " + LangResources.get("recent_directories_dialog_title"), LangResources.get("recent_directories_dialog_text"), recentlyOpenedDirectories);
				final String filePath = dialog.open();
				if (filePath != null && new File(filePath).exists()) {
					if (new File(filePath).isDirectory()) {
						openAllLanguagePropertiesSets(filePath);
					} else {
						loadSingleLanguagePropertiesSet(filePath);
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

	private class SaveFilesSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent event) {
			try {
				String defaultLanguagePropertisPath = null;
				final Set<String> languagePropertiesPaths = languageProperties.stream().map(o -> o.getPath()).collect(Collectors.toSet());
				if (languagePropertiesPaths.contains("")) {
					if (languagePropertiesPaths.size() == 2) {
						languagePropertiesPaths.remove("");
						defaultLanguagePropertisPath = Utilities.replaceUsersHome(new ArrayList<>(languagePropertiesPaths).get(0));
					} else {
						final FileDialog dlg = new FileDialog(getShell());
						dlg.setText(getShell().getText() + " " + LangResources.get("save_file_dialog_text"));
						dlg.setFilterPath(Utilities.replaceUsersHome("~") + File.separator + "MyLanguageProperties.properties");
						dlg.setFilterPath(recentlyOpenedDirectories.getLatestAdded());
						final String filePath = dlg.open();
						if (filePath == null) {
							showErrorMessage(LangResources.get("save_file_dialog_text"), LangResources.get("canceledByUser"));
						}
					}
				}

				final WriteLanguagePropertiesWorker writeLanguagePropertiesWorker = new WriteLanguagePropertiesWorker(null, languageProperties, languagePropertySetName, defaultLanguagePropertisPath == null ? null : new File(defaultLanguagePropertisPath), null);
				final ProgressDialog<WriteLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("save_files"), writeLanguagePropertiesWorker);
				final Result dialogResult = progressDialog.open();
				if (dialogResult == Result.CANCELED) {
					showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
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

				final DirectoryDialog directoryDialog = new DirectoryDialog(getShell());
				directoryDialog.setText(LangResources.get("open_directory_dialog_text"));
				final String directoryPath = directoryDialog.open();
				if (directoryPath == null) {
					showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
				} else if (new File(directoryPath).exists() && new File(directoryPath).isDirectory()) {
					final WriteLanguagePropertiesWorker writeLanguagePropertiesWorker = new WriteLanguagePropertiesWorker(null, languageProperties, "Multiple", new File(directoryPath), excludeParts);
					final ProgressDialog<WriteLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("save_files"), writeLanguagePropertiesWorker);
					final Result dialogResult = progressDialog.open();
					if (dialogResult == Result.CANCELED) {
						showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
					} else {
						// check for errors
						writeLanguagePropertiesWorker.get();

						showData(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("saveDirectoryResult", Utilities.join(writeLanguagePropertiesWorker.getListOfStoredProperties(), "\n")));
					}

					hasUnsavedChanges = false;
					setupTable();
					checkButtonStatus();
				}
			} catch (final Exception e) {
				new ErrorDialog(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, e).open();
			}
		}
	}

	private class ImportSelectionListener extends SelectionAdapter {
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
					String languagePropertiesSetName;
					final List<String> languagePropertiesSetNames = ExcelHelper.getExcelSheetNames(new File(importFile));
					if (languagePropertiesSetNames.size() == 1) {
						languagePropertiesSetName = languagePropertiesSetNames.get(0);
					} else {
						languagePropertiesSetName = "Multiple";
					}
					setLanguagePropertiesSetName(languagePropertiesSetName);

					final ImportFromExcelWorker importFromExcelWorker = new ImportFromExcelWorker(null, new File(importFile));
					final ProgressDialog<ImportFromExcelWorker> progressDialog = new ProgressDialog<>(getShell(), LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("import_file"), importFromExcelWorker);
					final Result dialogResult = progressDialog.open();
					if (dialogResult == Result.CANCELED) {
						showErrorMessage(LangResources.get("import_file"), LangResources.get("canceledByUser"));
					} else {
						// check for errors
						importFromExcelWorker.get();

						showMessage(LangResources.get("import_file"), LangResources.get("actionSuccessfullyCompleted"));

						languageProperties = importFromExcelWorker.getLanguageProperties();
						availableLanguageSigns = importFromExcelWorker.getAvailableLanguageSigns();
						hasUnsavedChanges = true;
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

	private class ExportSelectionListener extends SelectionAdapter {
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
				for (int searchIndex = 0; i < languageProperties.size(); searchIndex++) {
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

	private void selectSearch(final String text, int startIndex, final boolean searchUp, final boolean searchCaseInsensitive, final boolean searchValue) {
		propertiesTable.deselectAll();

		if (Utilities.isNotEmpty(text) && languageProperties != null) {
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

				final String key = languageProperties.get(currentIndex).getKey();
				if (!searchValue) {
					if (!searchCaseInsensitive && key.contains(text)) {
						propertiesTable.setSelection(currentIndex);
						refreshDetailView();
						break;
					} else if (searchCaseInsensitive && key.toLowerCase().contains(text.toLowerCase())) {
						propertiesTable.setSelection(currentIndex);
						refreshDetailView();
						break;
					}
				} else {
					if (!searchCaseInsensitive && (key.contains(text) || containsLanguageValuePart(languageProperties.get(currentIndex), text, false))) {
						propertiesTable.setSelection(currentIndex);
						refreshDetailView();
						break;
					} else if (searchCaseInsensitive && (key.toLowerCase().contains(text.toLowerCase()) || containsLanguageValuePart(languageProperties.get(currentIndex), text, true))) {
						propertiesTable.setSelection(currentIndex);
						refreshDetailView();
						break;
					}
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

	private static boolean containsLanguageValuePart(final LanguageProperty languageProperty, final String searchText, final boolean searchCaseInsensitive) {
		for (final String languageSign : languageProperty.getAvailableLanguageSigns()) {
			if (!searchCaseInsensitive && languageProperty.getLanguageValue(languageSign) != null && languageProperty.getLanguageValue(languageSign).contains(searchText)) {
				return true;
			} else if (searchCaseInsensitive && languageProperty.getLanguageValue(languageSign) != null && languageProperty.getLanguageValue(languageSign).toLowerCase().contains(searchText.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unused")
	public void checkUsage(final List<LanguageProperty> storageToCheck, final String directory, final String filePattern, final String usagePatternString) throws Exception {
		//		final Set<String> existingDefaultProperties = new HashSet<>();
		//		final Set<String> existingOverallProperties = new HashSet<>();
		//		final Set<String> missingDefaultProperties = new HashSet<>();
		//		final Set<String> missingOverallProperties = new HashSet<>();
		//		final Set<String> usedProperties = new HashSet<>();
		//		final Set<String> unusedProperties = new HashSet<>();
		//		final Set<File> filesWithMissingValues = new HashSet<>();
		//
		//		for (final Entry<String, LanguageProperty> propertyEntry : storageToCheck.getProperties().entrySet()) {
		//			if (propertyEntry.getValue().getLanguageValue("") == null) {
		//				existingDefaultProperties.add(propertyEntry.getKey());
		//			}
		//			existingOverallProperties.add(propertyEntry.getKey());
		//		}
		//
		//		final Pattern usagePattern = Pattern.compile(
		//				"("
		//						+ usagePatternString
		//						.replace("\\", "\\\\")
		//						.replace("(", "\\(")
		//						.replace(")", "\\)")
		//						.replace("<property>", ")([a-zA-Z0-9._]+)(")
		//						+ ")");
		//		final List<File> fileList = FileUtilities.getFilesByPattern(new File(directory), filePattern, true);
		//		for (final File file : fileList) {
		//			final String fileDataString = FileUtilities.readFileToString(file, StandardCharsets.UTF_8);
		//			final Matcher matcher = usagePattern.matcher(fileDataString);
		//			while (matcher.find()) {
		//				final String propertyName = matcher.group(2);
		//
		//				if (existingDefaultProperties.contains(propertyName)) {
		//					usedProperties.add(propertyName);
		//				} else {
		//					filesWithMissingValues.add(file);
		//					missingDefaultProperties.add(propertyName);
		//				}
		//
		//				if (existingOverallProperties.contains(propertyName)) {
		//					usedProperties.add(propertyName);
		//				} else {
		//					missingOverallProperties.add(propertyName);
		//				}
		//			}
		//		}
		//		for (final String propertyName : existingOverallProperties) {
		//			if (!usedProperties.contains(propertyName)) {
		//				unusedProperties.add(propertyName);
		//			}
		//		}
		//
		//		String reportText = "";
		//		reportText += LangResources.get("reportresults") + "\n";
		//		reportText += "Checked directory: " + directory + "\n";
		//		reportText += "Checked filePattern: " + filePattern + "\n";
		//		reportText += "Checked usagePattern: " + usagePatternString + "\n";
		//		reportText += "Properties in default language: " + existingDefaultProperties.size() + "\n";
		//		reportText += "Properties in all languages: " + existingOverallProperties.size() + "\n";
		//		reportText += "Missing properties in default language: " + missingDefaultProperties.size() + "\n";
		//		reportText += "Missing properties in all languages: " + missingOverallProperties.size() + "\n";
		//		reportText += "Unused properties in all languages: " + unusedProperties.size() + "\n";
		//		reportText += "Used properties in all languages: " + usedProperties.size() + "\n";
		//		reportText += "Checked files: " + fileList.size() + "\n";
		//		if (filesWithMissingValues.size() > 0) {
		//			reportText += "\nFiles with missing properties missing in default languages:\n";
		//			reportText += Utilities.join(filesWithMissingValues, "\n") + "\n";
		//		}
		//		if (missingDefaultProperties.size() > 0) {
		//			reportText += "\nProperties missing in default languages:\n";
		//			reportText += Utilities.join(missingDefaultProperties, "\n") + "\n";
		//		}
		//		if (unusedProperties.size() > 0) {
		//			reportText += "\nProperties unused in all languages:\n";
		//			reportText += Utilities.join(unusedProperties, "\n");
		//		}
		//
		//		new ShowDataDialog(getShell(), LangResources.get("usagereport"), reportText, true).open();
	}


	public boolean askForOverwriteFile(final String filePath) {
		final QuestionDialog dialog = new QuestionDialog(this, getText(), LangResources.get("question.overwritefile", filePath), LangResources.get("overwrite"), LangResources.get("cancel")).setBackgroundColor(SwtColor.LightRed);
		final int returncode = dialog.open();
		return returncode == 0;
	}

	@Override
	protected void setDailyUpdateCheckStatus(final boolean checkboxStatus) {
		applicationConfiguration.set(LanguagePropertiesManager.CONFIG_DAILY_UPDATE_CHECK, checkboxStatus);
		applicationConfiguration.set(LanguagePropertiesManager.CONFIG_NEXT_DAILY_UPDATE_CHECK, LocalDateTime.now().plusDays(1));
		applicationConfiguration.save();
	}

	@Override
	protected Boolean isDailyUpdateCheckActivated() {
		return applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_DAILY_UPDATE_CHECK);
	}

	protected boolean dailyUpdateCheckIsPending() {
		return applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_DAILY_UPDATE_CHECK)
				&& (applicationConfiguration.getDate(LanguagePropertiesManager.CONFIG_NEXT_DAILY_UPDATE_CHECK) == null || applicationConfiguration.getDate(LanguagePropertiesManager.CONFIG_NEXT_DAILY_UPDATE_CHECK).isBefore(LocalDateTime.now()))
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
