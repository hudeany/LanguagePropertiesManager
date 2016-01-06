package de.soderer.languagepropertiesmanager.dlg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import de.soderer.languagepropertiesmanager.image.ImageManager;
import de.soderer.languagepropertiesmanager.storage.PropertiesStorage;
import de.soderer.languagepropertiesmanager.storage.Property;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.CsvReader;
import de.soderer.utilities.CsvWriter;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.collection.UniqueFifoQueuedList;
import de.soderer.utilities.swt.ApplicationConfigurationDialog;
import de.soderer.utilities.swt.ComboSelectionDialog;
import de.soderer.utilities.swt.ErrorDialog;
import de.soderer.utilities.swt.MainDialog;
import de.soderer.utilities.swt.ShowDataDialog;
import de.soderer.utilities.swt.SimpleInputDialog;
import de.soderer.utilities.swt.SwtUtilities;

/**
 * Main Class
 * @author Andreas
 */
// TODO Scrolling and searching flickers in linux
public class LanguagePropertiesManagerDialog extends MainDialog {
	public static final String APPLICATION_NAME = "LanguagePropertiesManager";
	public static final String APPLICATION_VERSION = "1.3.0";
	public static final String APPLICATION_ERROR_EMAIL_ADRESS = "LanguagePropertiesManager.Error@soderer.de";
	public static final String HOME_URL = "http://www.soderer.de/index.php?menu=tools";
	public static final String VERSIONINFO_DOWNLOAD = "http://www.soderer.de/index.php?download=Versions.xml";
	public static final String APPLICATION_DOWNLOAD = "http://www.soderer.de/index.php?download=LanguagePropertiesManager_Offline_<system>_x<bitmode>.jar&username=<username>&password=<password>";
	public static final String CONFIG_VERSION = "Application.Version";
	public static final String CONFIG_CLEANUP_REPAIRPUNCTUATION = "Cleanup.RepairPunctuation";
	public static final String CONFIG_OUTPUT_SEPARATOR = "Output.Separator";
	public static final String CONFIG_SORT_ORG_INDEX = "Output.SortByOrgIndex";
	public static final String CONFIG_LANGUAGE = "Application.Language";
	public static final String CONFIG_PREVIOUS_CHECK_USAGE = "CheckUsage.Previous";
	public static final String CONFIG_RECENT_PROPERTIES = "Recent";
	
	private boolean showStorageTexts = false;
	private boolean dataWasModified = false;
	private boolean hasUnsavedChanges = false;
	private boolean technicalDataChange = false;

	private Composite rightPart = null;
	private Button removeButton;
	private Button cleanupButton;
	private Button saveButton;
	private Button addButton;
	private Text keyTextfield;
	private Composite detailFieldsPart;
	private Map<String, Text> languageTextFields;
	private Table propertiesTable;
	private int columnKeyIndex;
	private Listener currentFillDataListener;
	private Listener columnSortListener;
	private List<String> currentSelectedKeys;
	private PropertiesStorage storage;
	private String searchText;
	private boolean searchCaseInsensitive = true;
	private Button checkUsageButton;
	private Button checkUsageButtonPrevious;

	private Button okButton;
	private Button cancelButton;
	private Button textConversionButton;
	private Button loadRecentButton;
	private Composite searchBox;
	
	private UniqueFifoQueuedList<String> recentlyOpenedDirectories;
	private UniqueFifoQueuedList<String> recentlyCheckUsages;
	private ConfigurationProperties applicationConfiguration;

	public static void main(String[] args) {
		Display display = null;
		
		try {
			display = new Display();

			ConfigurationProperties applicationConfiguration = new ConfigurationProperties(APPLICATION_NAME, true);
			LanguagePropertiesManagerDialog.setupDefaultConfig(applicationConfiguration);
			if ("de".equalsIgnoreCase(applicationConfiguration.get(LanguagePropertiesManagerDialog.CONFIG_LANGUAGE)))
				Locale.setDefault(Locale.GERMAN);
			else 
				Locale.setDefault(Locale.ENGLISH);
			
			LanguagePropertiesManagerDialog mainDialog = new LanguagePropertiesManagerDialog(display, applicationConfiguration);
			mainDialog.run();
		}
		catch (Throwable ex) {
			if (display != null) {
				Shell shell = new Shell(display);
				new ErrorDialog(shell, APPLICATION_NAME, APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			} else {
				System.out.println(ex.toString());
				ex.printStackTrace();
			}
		} finally {
			if (display != null) {
				display.dispose();
			}
		}
	}
	
	public LanguagePropertiesManagerDialog(Display display, ConfigurationProperties applicationConfiguration) throws Exception {
		super(display);
		
		this.applicationConfiguration = applicationConfiguration;
		loadConfiguration();

		Monitor[] monitorArray = display.getMonitors();
		if (monitorArray != null)
			getShell().setLocation((monitorArray[0].getClientArea().width - this.getSize().x) / 2,
					(monitorArray[0].getClientArea().height - this.getSize().y) / 2);
		
		@SuppressWarnings("unused")
		ImageManager imageManager = new ImageManager(getShell());
		SashForm sashForm = new SashForm(this, SWT.SMOOTH | SWT.HORIZONTAL);
		setImage(ImageManager.getImage("plus.png"));
		setText(LangResources.get("window_title"));
		setLayout(new FillLayout());
		createLeftPart(sashForm);
		createRightPart(sashForm);
		setSize(720, 450);
		setMinimumSize(450, 300);
		
		addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(Event event) {
				close();
				event.doit = !hasUnsavedChanges;
			}
		});
		
		checkButtonStatus();
	}

	private void loadConfiguration() {
		recentlyOpenedDirectories = new UniqueFifoQueuedList<String>(5);
		recentlyOpenedDirectories.addAll(applicationConfiguration.getList(CONFIG_RECENT_PROPERTIES));

		recentlyCheckUsages = new UniqueFifoQueuedList<String>(5);
		recentlyCheckUsages.addAll(applicationConfiguration.getList(CONFIG_PREVIOUS_CHECK_USAGE));
		
		checkButtonStatus();
	}

	private void createLeftPart(SashForm parent) throws Exception {
		Composite leftPart = new Composite(parent, SWT.BORDER);
		leftPart.setLayout(SwtUtilities.createNoMarginGridLayout(1, false));
		leftPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));

		Label propertiesLabel = new Label(leftPart, SWT.NONE);
		propertiesLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		propertiesLabel.setText(LangResources.get("table_title"));
		propertiesLabel.setFont(new Font(getDisplay(), "Arial", 12, SWT.BOLD));
		
		Composite buttonSection = new Composite(leftPart, SWT.NONE);
		buttonSection.setLayout(SwtUtilities.createNoMarginGridLayout(10, false));
		buttonSection.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));

		loadRecentButton = new Button(buttonSection, SWT.PUSH);
		loadRecentButton.setImage(ImageManager.getImage("clock.png"));
		loadRecentButton.setToolTipText(LangResources.get("tooltip_load_recent_files"));
		loadRecentButton.addSelectionListener(new OpenRecentSelectionListener());
		
		Button loadButton = new Button(buttonSection, SWT.PUSH);
		loadButton.setImage(ImageManager.getImage("load.png"));
		loadButton.setToolTipText(LangResources.get("tooltip_load_files"));
		loadButton.addSelectionListener(new OpenDirectorySelectionListener());
		
		saveButton = new Button(buttonSection, SWT.PUSH);
		saveButton.setImage(ImageManager.getImage("save.png"));
		saveButton.setToolTipText(LangResources.get("tooltip_save_files"));
		saveButton.addSelectionListener(new SaveFilesSelectionListener(this));

		addButton = new Button(buttonSection, SWT.PUSH);
		addButton.setImage(ImageManager.getImage("plus.png"));
		addButton.setToolTipText(LangResources.get("tooltip_create_new_property"));
		addButton.addSelectionListener(new AddButtonSelectionListener());

		removeButton = new Button(buttonSection, SWT.PUSH);
		removeButton.setImage(ImageManager.getImage("trash.png"));
		removeButton.setToolTipText(LangResources.get("tooltip_delete_properties"));
		removeButton.setEnabled(false);
		removeButton.addSelectionListener(new RemoveButtonSelectionListener());
		
		cleanupButton = new Button(buttonSection, SWT.PUSH);
		cleanupButton.setImage(ImageManager.getImage("clean.png"));
		cleanupButton.setToolTipText(LangResources.get("tooltip_cleanup_values"));
		cleanupButton.addSelectionListener(new CleanupButtonSelectionListener(this));
		
		checkUsageButton = new Button(buttonSection, SWT.PUSH);
		checkUsageButton.setImage(ImageManager.getImage("puzzle.png"));
		checkUsageButton.setToolTipText(LangResources.get("checkusage"));
		checkUsageButton.addSelectionListener(new CheckUsageButtonSelectionListener(this));

		checkUsageButtonPrevious = new Button(buttonSection, SWT.PUSH);
		checkUsageButtonPrevious.setImage(ImageManager.getImage("puzzleClock.png"));
		checkUsageButtonPrevious.setToolTipText(LangResources.get("checkusageprevious"));
		checkUsageButtonPrevious.addSelectionListener(new CheckUsageButtonPreviousSelectionListener());
		
		Button configButton = new Button(buttonSection, SWT.PUSH);
		configButton.setImage(ImageManager.getImage("wrench.png"));
		configButton.setToolTipText(LangResources.get("configuration"));
		configButton.addSelectionListener(new ConfigButtonSelectionListener(this));
		
		Button helpButton = new Button(buttonSection, SWT.PUSH);
		helpButton.setImage(ImageManager.getImage("question.png"));
		helpButton.setToolTipText(LangResources.get("help"));
		helpButton.addSelectionListener(new HelpButtonSelectionListener(this));
		
		// Searching
		searchBox = new Composite(leftPart, SWT.BORDER);
		searchBox.setLayout(SwtUtilities.createNoMarginGridLayout(4, false));
		searchBox.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));
		
		Text searchTextField = new Text(searchBox, SWT.NONE);
		searchTextField.setText(LangResources.get("search"));
		searchTextField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		searchTextField.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				if (((Text) e.widget).getText().equals(LangResources.get("search")))
					((Text) e.widget).setText("");
			}

			@Override
			public void focusLost(FocusEvent e) {
				if (StringUtils.isEmpty(((Text) e.widget).getText()))
					((Text) e.widget).setText(LangResources.get("search"));
			}
		});
		searchTextField.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				Text textItem = ((Text) e.widget);
				if (StringUtils.isNotEmpty(textItem.getText()) && !textItem.getText().equals(LangResources.get("search")) && storage != null) {
					if (propertiesTable.getSelectionCount() == 0)
						propertiesTable.setSelection(0);
					searchText = textItem.getText();
					selectSearch(searchText, propertiesTable.getSelectionIndex(), true, searchCaseInsensitive);
				}
			}
		});
		
		Button searchDownButton = new Button(searchBox, SWT.PUSH);
		searchDownButton.setImage(ImageManager.getImage("down.png"));
		searchDownButton.setToolTipText(LangResources.get("search_down"));
		searchDownButton.setLayoutData(new GridData(25, 25));
		searchDownButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (StringUtils.isNotEmpty(searchText))
					selectSearch(searchText, propertiesTable.getSelectionIndex() + 1, true, searchCaseInsensitive);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				if (StringUtils.isNotEmpty(searchText))
					selectSearch(searchText, propertiesTable.getSelectionIndex() + 1, true, searchCaseInsensitive);
			}
		});
		
		Button searchUpButton = new Button(searchBox, SWT.PUSH);
		searchUpButton.setImage(ImageManager.getImage("up.png"));
		searchUpButton.setToolTipText(LangResources.get("search_up"));
		searchUpButton.setLayoutData(new GridData(25, 25));
		searchUpButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectSearch(searchText, propertiesTable.getSelectionIndex() - 1, false, searchCaseInsensitive);
			}
		});
		
		Button caseButton = new Button(searchBox, SWT.CHECK);
		caseButton.setText("Aa");
		caseButton.setToolTipText(LangResources.get("case_sensitive"));
		caseButton.setLayoutData(new GridData(40, 20));
		caseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				searchCaseInsensitive = !((Button)e.widget).getSelection();
				if (StringUtils.isNotEmpty(searchText) && !searchText.equals(LangResources.get("search")) && storage != null) {
					if (propertiesTable.getSelectionCount() == 0)
						propertiesTable.setSelection(0);
					selectSearch(searchText, propertiesTable.getSelectionIndex(), true, searchCaseInsensitive);
				}
			}
		});
		
		propertiesTable = new Table(leftPart, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
		propertiesTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 7, 1));
		propertiesTable.setHeaderVisible(true);
		propertiesTable.setLinesVisible(true);
		propertiesTable.addSelectionListener(new TableItemSelectionListener());
		
		columnSortListener = new ColumnSortListener();
		
		// WindowsBug: Erste Spalte kann man nicht Right-Align setzen
		TableColumn column = new TableColumn(propertiesTable, SWT.RIGHT);
		column.setWidth(0);
		column.setText(LangResources.get("columnheader_dummy"));
				
		TableColumn columnNr = new TableColumn(propertiesTable, SWT.RIGHT);
		columnNr.setMoveable(false);
		columnNr.setWidth(50);
		columnNr.setText(LangResources.get("columnheader_nr"));

		TableColumn columnOrigialIndex = new TableColumn(propertiesTable, SWT.RIGHT);
		columnOrigialIndex.setMoveable(false);
		columnOrigialIndex.setWidth(50);
		columnOrigialIndex.setText(LangResources.get("columnheader_original_index"));
		columnOrigialIndex.addListener(SWT.Selection, columnSortListener);
		
		TableColumn columnKey = new TableColumn(propertiesTable, SWT.LEFT);
		columnKey.setMoveable(true);
		columnKey.setWidth(150);
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
		for (TableColumn column : propertiesTable.getColumns()) {
			if (!column.getText().equals(LangResources.get("columnheader_dummy")) 
					&& !column.getText().equals(LangResources.get("columnheader_nr")) 
					&& !column.getText().equals(LangResources.get("columnheader_original_index")) 
					&& !column.getText().equals(LangResources.get("columnheader_key")))
				column.dispose();
		}
		
		if (storage != null) {
			for (String sign : storage.getLanguageSigns()) {
				TableColumn column = new TableColumn(propertiesTable, SWT.CENTER);
				column.setMoveable(true);
				column.setWidth(sign.length() > 3 ? 50 : 25);
				if (sign.equals(PropertiesStorage.LANGUAGE_SIGN_DEFAULT))
					column.setText(LangResources.get("columnheader_default"));
				else column.setText(sign);
				column.addListener(SWT.Selection, columnSortListener);
			}
			
			currentFillDataListener = new FillDataListener();
			propertiesTable.addListener(SWT.SetData, currentFillDataListener);
			propertiesTable.setItemCount(storage.getProperties().size());
			
			propertiesTable.setSortColumn(propertiesTable.getColumn(1));
			propertiesTable.setSortDirection(SWT.UP);
			
			for (Control field : detailFieldsPart.getChildren()) {
				field.dispose();
			}
			
			for (String sign : storage.getLanguageSigns()) {
				Label languageLabel = new Label(detailFieldsPart, SWT.NONE);
				if (PropertiesStorage.LANGUAGE_SIGN_DEFAULT.equals(sign))
					languageLabel.setText(LangResources.get("columnheader_default") + ":");
				else languageLabel.setText(sign + ":");
				Text languageTextfield = new Text(detailFieldsPart, SWT.BORDER);
				languageTextfield.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
				languageTextfield.addModifyListener(new DetailModifyListener());
				languageTextFields.put(sign, languageTextfield);
			}
		}
		else {
			for (Control field : detailFieldsPart.getChildren()) {
				field.dispose();
			}
		}
		detailFieldsPart.layout();
	}

	private void createRightPart(Composite parent) throws Exception {	
		rightPart = new Composite(parent, SWT.NONE);
		rightPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));
		rightPart.setLayout(SwtUtilities.createNoMarginGridLayout(1, false));
		
		Composite keyBereich = new Composite(rightPart, SWT.NONE);
		keyBereich.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));
		keyBereich.setLayout(SwtUtilities.createNoMarginGridLayout(2, false));
		
		Label keyLabel = new Label(keyBereich, SWT.NONE);
		keyLabel.setText(LangResources.get("columnheader_key") + ":");
		keyTextfield = new Text(keyBereich, SWT.BORDER);
		keyTextfield.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		keyTextfield.addModifyListener(new DetailModifyListener());
		
		Label keySeparatorLabel = new Label(keyBereich, SWT.SEPARATOR | SWT.HORIZONTAL);
		keySeparatorLabel.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false, 2, 1));
		
		ScrolledComposite scrolledPart = new ScrolledComposite(rightPart, SWT.H_SCROLL | SWT.V_SCROLL);
		scrolledPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));
		scrolledPart.setLayout(SwtUtilities.createNoMarginGridLayout(1, false));
		
		detailFieldsPart = new Composite(scrolledPart, SWT.NONE);
		detailFieldsPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false, 1, 1));
		detailFieldsPart.setLayout(SwtUtilities.createNoMarginGridLayout(2, false));
		
		languageTextFields = new HashMap<String, Text>();
		
		scrolledPart.setContent(detailFieldsPart);
		scrolledPart.setMinSize(200, 280);
		scrolledPart.setExpandHorizontal(true);
		scrolledPart.setExpandVertical(true);
		
		Composite buttonBereich = new Composite(rightPart, SWT.NONE);
		buttonBereich.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));
		buttonBereich.setLayout(SwtUtilities.createNoMarginGridLayout(2, true));

		Label buttonSeparatorLabel = new Label(buttonBereich, SWT.SEPARATOR | SWT.HORIZONTAL);
		buttonSeparatorLabel.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, true, 2, 1));
		
		textConversionButton = new Button(buttonBereich, SWT.PUSH);
		textConversionButton.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false, 2, 1));
		textConversionButton.setText(showStorageTexts ? LangResources.get("change_to_show_visble_texts") : LangResources.get("change_to_show_storage_texts"));
		textConversionButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showStorageTexts = !showStorageTexts;
				textConversionButton.setText(showStorageTexts ? LangResources.get("change_to_show_visble_texts") : LangResources.get("change_to_show_storage_texts"));
				changeDisplayMode(showStorageTexts, keyTextfield, languageTextFields);
			}
		});

		okButton = new Button(buttonBereich, SWT.PUSH);
		okButton.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false));
		okButton.setText(LangResources.get("button_text_add"));
		okButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					hasUnsavedChanges = true;
					if (okButton.getText().equals(LangResources.get("button_text_change"))) {
						// Change existing property
						storage.change(showStorageTexts, propertiesTable.getSelection()[0].getText(columnKeyIndex), keyTextfield, languageTextFields);
						refreshTable();
						dataWasModified = false;
						checkButtonStatus();
					} else {
						// Add new property
						storage.add(showStorageTexts, keyTextfield, languageTextFields);
						propertiesTable.setItemCount(propertiesTable.getItemCount() + 1);
						currentSelectedKeys = Arrays.asList(keyTextfield.getText());
						refreshTable();
						removeButton.setEnabled(true);
						dataWasModified = false;
						okButton.setText(LangResources.get("button_text_change"));
						checkButtonStatus();
					}
				} catch (Exception ex) {
					new ErrorDialog(getShell(), "OKButton error", ex).open();
				}
			}
		});

		cancelButton = new Button(buttonBereich, SWT.PUSH);
		cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false));
		cancelButton.setText(LangResources.get("button_text_discard"));
		cancelButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				refreshDetailView();
			}
		});

		checkButtonStatus();
	}

	private class TableItemSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(SelectionEvent e) {
			technicalDataChange = true;
			if (!dataWasModified || askForDiscardChanges()) {
				// make a new selection
				removeButton.setEnabled(true);
				refreshDetailView();
				currentSelectedKeys = getSelectedKeys();
			} else {
				// reselect old entry
				propertiesTable.deselectAll();
				propertiesTable.select(storage.getIndexOfKey(currentSelectedKeys.get(0)));
			}
			technicalDataChange = false;

			checkButtonStatus();
		}
	}

	private class AddButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(SelectionEvent e) {
			try {
				if (!dataWasModified || askForDiscardChanges()) {
					propertiesTable.deselectAll();
					currentSelectedKeys = null;
					refreshDetailView();
				}
			}
			catch (Exception ex) {
				new ErrorDialog(getShell(), "AddButton error", ex).open();
			}
		}
	}

	private class RemoveButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(SelectionEvent e) {
			try {
				if (askForDropProperties()) {
					propertiesTable.setItemCount(propertiesTable.getItemCount() - propertiesTable.getSelectionCount());
					for (TableItem item : propertiesTable.getSelection()) {
						storage.remove(item.getText(columnKeyIndex));
					}
	
					refreshTable();
					propertiesTable.deselectAll();
					refreshDetailView();
				}
			}
			catch (Exception ex) {
				new ErrorDialog(getShell(), "RemoveButton error", ex).open();
			}
		}
	}

	private class CleanupButtonSelectionListener extends SelectionAdapter {
		private Shell shell;
		public CleanupButtonSelectionListener(Shell shell) {
			this.shell = shell;
		}
		
		@Override
		public void widgetSelected(SelectionEvent e) {
			try {
				if (askForDropDuplicateProperties()) {
					boolean cleanupChangedData = storage.cleanUp(applicationConfiguration.getBoolean(CONFIG_CLEANUP_REPAIRPUNCTUATION)) ;
					hasUnsavedChanges = cleanupChangedData || hasUnsavedChanges;
					refreshTable();
					refreshDetailView();
					if (!cleanupChangedData) {
						MessageBox messageBox = new MessageBox(shell);
						messageBox.setText(LangResources.get("nochange_needed"));
						messageBox.setMessage(LangResources.get("nochange_needed"));
						messageBox.open();
					}
				}
			}
			catch (Exception ex) {
				new ErrorDialog(getShell(), "CleanupButton error", ex).open();
			}
		}
	}

	private class ConfigButtonSelectionListener extends SelectionAdapter {
		private Shell shell;
		public ConfigButtonSelectionListener(Shell shell) {
			this.shell = shell;
		}
		
		@Override
		public void widgetSelected(SelectionEvent e) {
			try {
				ApplicationConfigurationDialog dialog = new ApplicationConfigurationDialog(shell, applicationConfiguration, LangResources.get("window_title"));
				if (dialog.open()) {
					applicationConfiguration.save();
					
					loadConfiguration();
				}
			}
			catch (Exception ex) {
				new ErrorDialog(getShell(), "ConfigButton error", ex).open();
			}
		}
	}
	
	private class HelpButtonSelectionListener extends SelectionAdapter {
		private Shell shell;
		public HelpButtonSelectionListener(Shell shell) {
			this.shell = shell;
		}
		
		@Override
		public void widgetSelected(SelectionEvent e) {
			new HelpDialog(shell, APPLICATION_NAME + " (" + APPLICATION_VERSION + ") " + LangResources.get("help")).open();
		}
	}

	private class CheckUsageButtonSelectionListener extends SelectionAdapter {
		private Shell shell;
		public CheckUsageButtonSelectionListener(Shell shell) {
			this.shell = shell;
		}
		
		@Override
		public void widgetSelected(SelectionEvent e) {
			try {
				DirectoryDialog dialog = new DirectoryDialog(getShell());
		        dialog.setText(getText() + " " + LangResources.get("directory_dialog_title"));
		        dialog.setMessage(LangResources.get("open_directory_dialog_text"));
		        String directory = dialog.open();
		        if (directory != null && new File(directory).exists() && new File(directory).isDirectory()) {
		        	SimpleInputDialog dialog2 = new SimpleInputDialog(shell, getText(), LangResources.get("enterfilepattern"));
		        	String filePattern = dialog2.open();
		        	if (filePattern != null) {
		        		SimpleInputDialog dialog3 = new SimpleInputDialog(shell, getText(), LangResources.get("enterusagepattern"));
			        	String usagePattern = dialog3.open();
			        	if (usagePattern != null) {
			        		recentlyCheckUsages.add(CsvWriter.getCsvLine(';', '"', directory, filePattern, usagePattern));
			        		applicationConfiguration.set(CONFIG_PREVIOUS_CHECK_USAGE, recentlyCheckUsages);
			        		checkUsage(storage, directory, filePattern, usagePattern);
			        		checkButtonStatus();
			        	}
		        	}
		        }
			}
			catch (Exception ex) {
				new ErrorDialog(getShell(), "CheckUsageButton error", ex).open();
			}
		}
	}

	private class CheckUsageButtonPreviousSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(SelectionEvent e) {
			try {
				ComboSelectionDialog dialog = new ComboSelectionDialog(getShell(), getText() + " " + LangResources.get("recentsettingsdialogtitle"), LangResources.get("recent_settings_dialog_text"), recentlyCheckUsages);
		        String setting = dialog.open();
		        if (setting != null) {
		        	recentlyCheckUsages.add(setting); //put selected as latest used
		        	List<String> settings = CsvReader.parseCsvLine(';', '"', setting);
		        	String directory = settings.get(0);
		        	String filePattern = settings.get(1);
		        	String usagePattern = settings.get(2);
		        	checkUsage(storage, directory, filePattern, usagePattern);
		        	checkButtonStatus();
		        }
			}
			catch (Exception ex) {
				new ErrorDialog(getShell(), "CheckUsageButton error", ex).open();
			}
		}
	}

	private class DetailModifyListener implements ModifyListener {
		@Override
		public void modifyText(ModifyEvent e) {
			if (!technicalDataChange) dataWasModified = true;
			checkButtonStatus();
		}
	}

	private void refreshDetailView() {
		try {
			if (propertiesTable.getSelection().length > 0) {
				Property propertyByKey = storage.getProperties().get(propertiesTable.getSelection()[0].getText(columnKeyIndex));
				
				if (propertyByKey == null) {
					throw new Exception("No property for key: " + propertiesTable.getSelection()[0].getText(columnKeyIndex));
				} else {
					keyTextfield.setText(propertyByKey.getKey());
					for (String sign : storage.getLanguageSigns()) {
						Text languageTextfield = languageTextFields.get(sign);
						String value = propertyByKey.getLanguageValue(sign);
						if (value == null)
							languageTextfield.setText("");
						else if (showStorageTexts)
							languageTextfield.setText(value);
						else
							languageTextfield.setText(StringEscapeUtils.unescapeJava(value));
					}
				}				
				
				okButton.setText(LangResources.get("button_text_change"));
				removeButton.setEnabled(true);
			}
			else {
				keyTextfield.setText("");
				for (String sign : storage.getLanguageSigns()) {
					Text languageTextfield = languageTextFields.get(sign);
					languageTextfield.setText("");
				}
				
				okButton.setText(LangResources.get("button_text_add"));
				removeButton.setEnabled(false);
			}
	
			dataWasModified = false;
			checkButtonStatus();
		}
		catch (Exception ex) {
			new ErrorDialog(getShell(), "RefreshDetail error", ex).open();
		}
	}

	public void checkButtonStatus() {
		if (okButton != null) okButton.setEnabled(dataWasModified);
		if (cancelButton != null) cancelButton.setEnabled(dataWasModified);
		if (saveButton != null) saveButton.setEnabled(hasUnsavedChanges);
		if (addButton != null) addButton.setEnabled(propertiesTable.getItemCount() > 0);
		if (cleanupButton != null) cleanupButton.setEnabled(propertiesTable.getItemCount() > 0);
		if (loadRecentButton != null) loadRecentButton.setEnabled(recentlyOpenedDirectories != null && recentlyOpenedDirectories.size() > 0);
		if (propertiesTable != null) propertiesTable.setEnabled(propertiesTable.getItemCount() > 0);
		if (searchBox != null) searchBox.setEnabled(propertiesTable.getItemCount() > 0);

		if (checkUsageButton != null)
			checkUsageButton.setEnabled(storage != null && storage.getProperties().size() > 0);
		if (checkUsageButtonPrevious != null)
			checkUsageButtonPrevious.setEnabled(checkUsageButton.isEnabled() && recentlyCheckUsages != null && recentlyCheckUsages.size() > 0);
	}

	private boolean askForDropProperties() {
		MessageBox messageBox = new MessageBox(LanguagePropertiesManagerDialog.this, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
		messageBox.setText(LangResources.get("question_title_delete_property"));
		messageBox.setMessage(LangResources.get("question_content_delete_property"));
		int returncode = messageBox.open();

		return (returncode == SWT.YES);
	}

	private boolean askForDropDuplicateProperties() {
		MessageBox messageBox = new MessageBox(LanguagePropertiesManagerDialog.this, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
		messageBox.setText(LangResources.get("question_title_cleanup_properties"));
		messageBox.setMessage(LangResources.get("question_content_cleanup_properties"));
		int returncode = messageBox.open();

		return (returncode == SWT.YES);
	}
	
	private boolean askForDiscardChanges() {
		MessageBox messageBox = new MessageBox(LanguagePropertiesManagerDialog.this, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
		messageBox.setText(LangResources.get("question_title_discard_changes"));
		messageBox.setMessage(LangResources.get("question_content_discard_changes"));
		int returncode = messageBox.open();

		return (returncode == SWT.YES);
	}

	@Override
	public void close() {
		if (!hasUnsavedChanges || askForDiscardChanges()) {
			applicationConfiguration.set(CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
			applicationConfiguration.set(CONFIG_PREVIOUS_CHECK_USAGE, recentlyCheckUsages);
			applicationConfiguration.set(CONFIG_VERSION, APPLICATION_VERSION);
			applicationConfiguration.save();
			hasUnsavedChanges = false;
			dispose();
		}
	}
	
	private void changeDisplayMode(boolean changeToShowStorageTexts, Text keyTextfield, Map<String, Text> languageTextFields) {
		technicalDataChange = true;
		if (changeToShowStorageTexts) {
			keyTextfield.setText(StringEscapeUtils.escapeJava(keyTextfield.getText()));
			for (Text field : languageTextFields.values()) {
				field.setText(StringEscapeUtils.escapeJava(field.getText()));
			}
		} else  {
			keyTextfield.setText(StringEscapeUtils.unescapeJava(keyTextfield.getText()));
			for (Text field : languageTextFields.values()) {
				field.setText(StringEscapeUtils.unescapeJava(field.getText()));
			}
		}
		technicalDataChange = false;
	}
	
	private class OpenDirectorySelectionListener extends SelectionAdapter {		
		@Override
		public void widgetSelected(SelectionEvent event) {
			try {
				DirectoryDialog dialog = new DirectoryDialog(getShell());
		        dialog.setText(getText() + " " + LangResources.get("directory_dialog_title"));
		        dialog.setMessage(LangResources.get("open_directory_dialog_text"));
		        String directory = dialog.open();
		        if (directory != null && new File(directory).exists() && new File(directory).isDirectory()) {
		        	recentlyOpenedDirectories.add(directory); //put selected as latest used
		    		applicationConfiguration.set(CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
		        	List<String> propertySets = PropertiesStorage.readAvailablePropertySets(directory);
		        	if (propertySets.size() == 1) {
			        	storage = new PropertiesStorage(directory, propertySets.get(0), false);
			        	storage.load();
		        	} else if (propertySets.size() > 1) {
		        		ComboSelectionDialog dialog2 = new ComboSelectionDialog(getShell(), getText() + " " + LangResources.get("propertysets_dialog_title"), LangResources.get("propertysets_dialog_text"), propertySets);
				        String propertySetName = dialog2.open();
			        	if (StringUtils.isNotEmpty(propertySetName)) {
			        		storage = new PropertiesStorage(directory, propertySetName, false);
				        	storage.load();
			        	}
		        	}
		        }
			} catch (Exception e) {
				e.printStackTrace();
				storage = null;
			}
			setupTable();
        	checkButtonStatus();
		}
	}
	
	private class OpenRecentSelectionListener extends SelectionAdapter {	
		@Override
		public void widgetSelected(SelectionEvent event) {
			try {
				ComboSelectionDialog dialog = new ComboSelectionDialog(getShell(), getText() + " " + LangResources.get("recent_directories_dialog_title"), LangResources.get("recent_directories_dialog_text"), recentlyOpenedDirectories);
		        String directory = dialog.open();
		        if (directory != null && new File(directory).exists() && new File(directory).isDirectory()) {
		        	recentlyOpenedDirectories.add(directory); //put selected as latest used
		    		applicationConfiguration.set(CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
		        	List<String> propertySets = PropertiesStorage.readAvailablePropertySets(directory);
		        	if (propertySets.size() == 1) {
			        	storage = new PropertiesStorage(directory, propertySets.get(0), false);
			        	storage.load();
		        	} else if (propertySets.size() > 1) {
		        		ComboSelectionDialog dialog2 = new ComboSelectionDialog(getShell(), getText() + " " + LangResources.get("propertysets_dialog_title"), LangResources.get("propertysets_dialog_text"), propertySets);
				        String propertySetName = dialog2.open();
			        	if (StringUtils.isNotEmpty(propertySetName)) {
			        		storage = new PropertiesStorage(directory, propertySetName, false);
				        	storage.load();
			        	}
		        	}
		        }
			} catch (Exception e) {
				e.printStackTrace();
				storage = null;
			}
			setupTable();
        	checkButtonStatus();
		}
	}
	
	private class SaveFilesSelectionListener extends SelectionAdapter {
		private LanguagePropertiesManagerDialog dialog;
		
		public SaveFilesSelectionListener(LanguagePropertiesManagerDialog dialog) {
			this.dialog = dialog;
		}
		
		@Override
		public void widgetSelected(SelectionEvent event) {
			DirectoryDialog dlg = new DirectoryDialog(dialog);
	        dlg.setText(dialog.getText() + " " + LangResources.get("directory_dialog_title"));
	        dlg.setMessage(LangResources.get("save_directory_dialog_text"));
	        dlg.setFilterPath(recentlyOpenedDirectories.getLatestAdded());
	        String directory = dlg.open();
	        if (StringUtils.isNotEmpty(directory)) {
	        	try {
	        		storage.save(directory, applicationConfiguration.get(CONFIG_OUTPUT_SEPARATOR), applicationConfiguration.getBoolean(CONFIG_SORT_ORG_INDEX));
				} catch (Exception e) {
					new ErrorDialog(getShell(), "Error when saving files", e).open();
				}
				hasUnsavedChanges = false;
				checkButtonStatus();
	        }
		}
	}
		
	private class ColumnSortListener implements Listener {
		@Override
		public void handleEvent(Event event) {
			TableColumn columnToSort = (TableColumn) event.widget;
			Table table = columnToSort.getParent();
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
			
			if (columnToSort.getText().equals(LangResources.get("columnheader_key")))
				storage.sort(PropertiesStorage.SORT_SIGN_KEY, table.getSortDirection() == SWT.UP);
			else if (columnToSort.getText().equals(LangResources.get("columnheader_original_index")))
				storage.sort(PropertiesStorage.SORT_SIGN_ORIGINAL_INDEX, table.getSortDirection() == SWT.UP);
			else if (columnToSort.getText().equals(LangResources.get("columnheader_default")))
				storage.sort(PropertiesStorage.SORT_SIGN_DEFAULT, table.getSortDirection() == SWT.UP);
			else
				storage.sort(columnToSort.getText(), table.getSortDirection() == SWT.UP);

			refreshTable();
		}
	}
	
	private class FillDataListener implements Listener {		
		@Override
		public void handleEvent(Event event) {
			TableItem item = (TableItem) event.item;
			int index = propertiesTable.indexOf(item);
			fillPropertyDataInTableItem(index, item);
		}
	}
	
	public void fillPropertyDataInTableItem(int index, TableItem item) {
		Property property = storage.getProperties().getValue(index);
		int i = 1; // 0 = DummyColumn
		item.setText(i++, Integer.toString(index + 1));
		item.setText(i++, Integer.toString(property.getOriginalIndex() + 1));
		item.setText(i++, property.getKey());
		
		for (String languageSign : storage.getLanguageSigns()) {
			String value = property.getLanguageValue(languageSign);
			item.setText(i++, StringUtils.isEmpty(value) ? LangResources.get("value_not_found_sign") : LangResources.get("value_found_sign"));
		}
	}
	
	private void refreshTable() {
		propertiesTable.deselectAll();
		
		propertiesTable.setRedraw(false);
		propertiesTable.clearAll();
		propertiesTable.setRedraw(true);
		
		if (currentSelectedKeys != null && currentSelectedKeys.size() > 0) {
			int[] indices = new int[currentSelectedKeys.size()];
			for (int i = 0; i < currentSelectedKeys.size(); i++) {
				indices[i] = storage.getIndexOfKey(currentSelectedKeys.get(i));
			}
			propertiesTable.setSelection(indices);
			propertiesTable.showSelection();
		}
	}
	
	private List<String> getSelectedKeys() {
		List<String> returnList = new ArrayList<String>();
		for (TableItem item : propertiesTable.getSelection()) {
			returnList.add(item.getText(columnKeyIndex));
		}
		return returnList;
	}
	
	public static String[] getTextValues(TableItem item) {
		String[] returnValue = new String[item.getParent().getColumnCount()];
		for (int i = 0; i < returnValue.length; i++) {
			returnValue[i] = item.getText(i);
		}
		return returnValue;
	}
	
	private void selectSearch(String text, int startIndex, boolean searchUp, boolean searchCaseInsensitive) {
		propertiesTable.deselectAll();
		
		if (StringUtils.isNotEmpty(text) && storage != null) {
			if (startIndex < 0) {
				startIndex = storage.getProperties().size() - 1;
			} else if (startIndex >= storage.getProperties().size()) {
				startIndex = 0;
			}
			
			int currentIndex = -1;
			while (currentIndex != startIndex) {
				if (currentIndex == -1)
					currentIndex = startIndex;
				
				if (!searchCaseInsensitive && storage.getProperties().getKey(currentIndex).contains(text)) {
					propertiesTable.setSelection(currentIndex);
					refreshDetailView();
					break;
				} else if (searchCaseInsensitive && storage.getProperties().getKey(currentIndex).toLowerCase().contains(text.toLowerCase())) {
					propertiesTable.setSelection(currentIndex);
					refreshDetailView();
					break;
				}
				
				if (searchUp) currentIndex++;
				else currentIndex--;
				
				if (currentIndex < 0)
					currentIndex = storage.getProperties().size() - 1;
				else if (currentIndex >= storage.getProperties().size())
					currentIndex = 0;
			}
		}
	}
	
	public static void setupDefaultConfig(ConfigurationProperties applicationConfiguration) {
		if (!applicationConfiguration.containsKey(CONFIG_CLEANUP_REPAIRPUNCTUATION)) applicationConfiguration.set(CONFIG_CLEANUP_REPAIRPUNCTUATION, true);
		if (!applicationConfiguration.containsKey(CONFIG_OUTPUT_SEPARATOR)) applicationConfiguration.set(CONFIG_OUTPUT_SEPARATOR, " = ");
		if (!applicationConfiguration.containsKey(CONFIG_SORT_ORG_INDEX)) applicationConfiguration.set(CONFIG_SORT_ORG_INDEX, true);
		if (!applicationConfiguration.containsKey(CONFIG_LANGUAGE)) applicationConfiguration.set(CONFIG_LANGUAGE, Locale.getDefault().getLanguage());
	}
	
	public void checkUsage(PropertiesStorage storage, String directory, String filePattern, String usagePatternString) throws IOException {
		Set<String> existingDefaultProperties = new HashSet<String>();
		Set<String> existingOverallProperties = new HashSet<String>();
		Set<String> missingDefaultProperties = new HashSet<String>();
		Set<String> missingOverallProperties = new HashSet<String>();
		Set<String> usedProperties = new HashSet<String>();
		Set<String> unusedProperties = new HashSet<String>();
		Set<File> filesWithMissingValues = new HashSet<File>();
		
		for (Entry<String, Property> propertyEntry : storage.getProperties().entrySet()) {
			if (propertyEntry.getValue().getLanguageValue("") == null) {
				existingDefaultProperties.add(propertyEntry.getKey());
			}
			existingOverallProperties.add(propertyEntry.getKey());
		}
		
		Pattern usagePattern = Pattern.compile(
		"("
		+ usagePatternString
			.replace("\\", "\\\\")
			.replace("(", "\\(")
			.replace(")", "\\)")
			.replace("<property>", ")([a-zA-Z0-9._]+)(")
		+ ")");
		List<File> fileList = Utilities.getFilesByPattern(new File(directory), filePattern, true);
		for (File file : fileList) {
			String fileDataString = FileUtils.readFileToString(file);
			Matcher matcher = usagePattern.matcher(fileDataString);
			while (matcher.find()) {
		    	String propertyName = matcher.group(2);
		    	
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
		for (String propertyName : existingOverallProperties) {
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
			reportText += StringUtils.join(filesWithMissingValues, "\n") + "\n";
		}
		if (missingDefaultProperties.size() > 0) {
			reportText += "\nProperties missing in default languages:\n";
			reportText += StringUtils.join(missingDefaultProperties, "\n") + "\n";
		}
		if (unusedProperties.size() > 0) {
			reportText += "\nProperties unused in all languages:\n";
			reportText += StringUtils.join(unusedProperties, "\n");
		}
		
		new ShowDataDialog(getShell(), LangResources.get("usagereport"), reportText, true).open();
	}
}
