package de.soderer.languagepropertiesmanager.dlg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.apache.commons.text.StringEscapeUtils;

import de.soderer.languagepropertiesmanager.LanguagePropertiesException;
import de.soderer.languagepropertiesmanager.LanguagePropertiesManager;
import de.soderer.languagepropertiesmanager.TranslationConstants;
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
import de.soderer.utilities.Utilities;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.collection.UniqueFifoQueuedList;
import de.soderer.utilities.csv.CsvFormat;
import de.soderer.utilities.csv.CsvReader;
import de.soderer.utilities.csv.CsvWriter;
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
 * Main Class
 */
public class LanguagePropertiesManagerDialog extends UpdateableGuiApplication {
	private static final long serialVersionUID = 3371684406925137014L;

	/*
	 * Fixed model columns of the properties table. The language columns follow
	 * after COLUMN_FIRST_LANGUAGE in the order of "availableLanguageSigns".
	 * (The SWT variant needed an invisible dummy first column as a workaround
	 * for a Windows alignment bug, which JTable does not have.)
	 */
	private static final int COLUMN_NR = 0;
	private static final int COLUMN_PATH = 1;
	private static final int COLUMN_ORIGINAL_INDEX = 2;
	private static final int COLUMN_KEY = 3;
	private static final int COLUMN_FIRST_LANGUAGE = 4;

	private static final int ICON_BUTTON_SIZE = 28;

	private boolean showStorageTexts = false;
	private boolean dataWasModified = false;
	private boolean hasUnsavedChanges = false;

	/** Suppresses the "data was modified" tracking while the detail fields are filled programmatically */
	private boolean technicalDataChange = false;

	/** Suppresses the user selection handling while the table selection is changed programmatically */
	private boolean technicalSelectionChange = false;

	/** Whether the detail fields currently show an existing property ("change") or a new one ("add") */
	private boolean detailShowsExistingProperty = false;

	private JLabel propertiesLabel;
	private JButton removeButton;
	private JButton saveButton;
	private JButton folderSaveButton;
	private JButton exportToExcelButton;
	private JButton exportToCsvButton;
	private JButton addButton;
	private JTextField pathTextfield;
	private JTextField keyTextfield;
	private JTextField commentTextfield;
	private JPanel detailFieldsPart;
	private final Map<String, JTextArea> languageTextFields = new LinkedHashMap<>();
	private JTable propertiesTable;
	private LanguagePropertiesTableModel propertiesTableModel;
	private int sortColumnModelIndex = COLUMN_NR;
	private boolean sortAscending = true;

	/**
	 * Currently selected properties, tracked by object identity. This keeps the
	 * selection stable across sorting and key renames and also works for
	 * duplicate path/key combinations.
	 */
	private List<LanguageProperty> currentSelectedProperties = new ArrayList<>();

	private List<LanguageProperty> languageProperties;
	private List<String> availableLanguageSigns;
	private String languagePropertySetName;
	private String searchText;
	private boolean searchCaseInsensitivePreference = true;
	private boolean searchInKeysPreference = true;
	private boolean searchInValuesPreference = false;
	private boolean searchInPathPreference = false;
	private JButton checkUsageButton;
	private JButton checkUsageButtonPrevious;
	private JButton addLanguageButton;
	private JButton deleteLanguageButton;
	private JButton translateButton;
	private JButton transferButton;
	private JButton clearIdenticalButton;
	private JButton removeDuplicatesButton;
	private JButton checkErrorsButton;
	private JButton showStatisticsButton;

	private JButton okButton;
	private JButton cancelButton;
	private JButton textConversionButton;
	private JButton loadRecentButton;
	private final List<JComponent> searchComponents = new ArrayList<>();

	private UniqueFifoQueuedList<String> recentlyOpenedDirectories;
	private UniqueFifoQueuedList<String> recentlyCheckUsages;
	private final ConfigurationProperties applicationConfiguration;

	public LanguagePropertiesManagerDialog(final ConfigurationProperties applicationConfiguration) throws Exception {
		super(LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.KEYSTORE_FILE);

		this.applicationConfiguration = applicationConfiguration;
		loadConfiguration();

		setIconImage(ImageManager.getImage("LanguagePropertiesManager.png").getImage());
		setTitle(LangResources.get("window_title"));

		final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createLeftPart(), createRightPart());
		splitPane.setResizeWeight(0.5);
		splitPane.setContinuousLayout(true);
		setContentPane(splitPane);

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
					ApplicationUpdateUtilities.executeUpdate(this, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, null, true, false);
				}
			} catch (final Exception e) {
				showErrorMessage(LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", e.getMessage()));
			}
		}
	}

	private void loadConfiguration() {
		recentlyOpenedDirectories = new UniqueFifoQueuedList<>(5);
		recentlyOpenedDirectories.addAll(applicationConfiguration.getList(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES));

		recentlyCheckUsages = new UniqueFifoQueuedList<>(5);
		for (final String checkUsageSetting : applicationConfiguration.getList(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE)) {
			// Converts entries of older versions (with backslash escaping) into the plain format
			try {
				recentlyCheckUsages.add(CsvWriter.getCsvLine(createCheckUsageCsvFormat(), parseCheckUsageSetting(checkUsageSetting)));
			} catch (final Exception e) {
				System.err.println("Cannot read recent check usage setting '" + checkUsageSetting + "': " + e.getMessage());
				recentlyCheckUsages.add(checkUsageSetting);
			}
		}

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
		saveButton = createIconButton(buttonSection1, "save.png", "tooltip_save_files", e -> saveFiles());
		folderSaveButton = createIconButton(buttonSection1, "folderSave.png", "tooltip_save_folder", e -> saveFolder());
		exportToExcelButton = createIconButton(buttonSection1, "excelSave.png", "tooltip_exportExcel", e -> exportToExcel());
		exportToCsvButton = createIconButton(buttonSection1, "csvSave.png", "tooltip_exportCsv", e -> exportToCsv());
		createIconButton(buttonSection1, "wrench.png", "configuration", e -> openConfiguration());
		createIconButton(buttonSection1, "question.png", "help", e -> new HelpDialog(this, LanguagePropertiesManager.APPLICATION_NAME + " (" + LanguagePropertiesManager.VERSION.toString() + ") " + LangResources.get("help"), applicationConfiguration).open());

		addButton = createIconButton(buttonSection2, "newProperty.png", "tooltip_create_new_property", e -> addNewProperty());
		removeButton = createIconButton(buttonSection2, "trash.png", "tooltip_delete_properties", e -> removeSelectedProperties());
		removeButton.setEnabled(false);
		checkUsageButton = createIconButton(buttonSection2, "puzzle.png", "checkusage", e -> checkUsageNew());
		checkUsageButton.setEnabled(false);
		checkUsageButtonPrevious = createIconButton(buttonSection2, "puzzleClock.png", "checkusageprevious", e -> checkUsagePrevious());
		checkUsageButtonPrevious.setEnabled(false);
		addLanguageButton = createIconButton(buttonSection2, "plus.png", "tooltip_AddLanguage", e -> addLanguage());
		deleteLanguageButton = createIconButton(buttonSection2, "minus.png", "tooltip_DeleteLanguage", e -> deleteLanguage());
		translateButton = createIconButton(buttonSection2, "translate.png", "tooltip_Translate", e -> translate());
		transferButton = createIconButton(buttonSection2, "transfer.png", "tooltip_Transfer", e -> transfer());
		clearIdenticalButton = createIconButton(buttonSection2, "clearIdentical.png", "tooltip_ClearIdentical", e -> clearIdentical());
		removeDuplicatesButton = createIconButton(buttonSection2, "clean.png", "tooltip_removeDuplicates", e -> removeDuplicates());
		checkErrorsButton = createIconButton(buttonSection2, "lightning.png", "tooltip_checkErrors", e -> checkErrors());
		showStatisticsButton = createIconButton(buttonSection2, "info.png", "tooltip_showStatistics", e -> showStatistics());

		final JPanel searchBox = createSearchBox();
		searchBox.setAlignmentX(LEFT_ALIGNMENT);
		topPart.add(searchBox);

		leftPart.add(topPart, BorderLayout.NORTH);

		propertiesTableModel = new LanguagePropertiesTableModel();
		propertiesTable = new JTable(propertiesTableModel);
		propertiesTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		propertiesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		propertiesTable.setShowGrid(true);
		propertiesTable.setGridColor(new Color(220, 220, 220));
		propertiesTable.setBackground(Color.WHITE);
		propertiesTable.setFillsViewportHeight(true);
		propertiesTable.getTableHeader().setReorderingAllowed(true);
		propertiesTable.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting() && !technicalSelectionChange) {
				// Deferred, because the selection change may open a modal question dialog
				javax.swing.SwingUtilities.invokeLater(this::handleUserSelectionChange);
			}
		});

		// Same DEL-key deletion as the trash button
		propertiesTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeSelectedProperties");
		propertiesTable.getActionMap().put("removeSelectedProperties", new AbstractAction() {
			private static final long serialVersionUID = -4385407786618180237L;

			@Override
			public void actionPerformed(final ActionEvent event) {
				if (propertiesTable.getSelectedRowCount() > 0) {
					removeSelectedProperties();
				}
			}
		});

		installSortableHeader(propertiesTable.getTableHeader());

		final JScrollPane propertiesTableScrollPane = new JScrollPane(propertiesTable);
		// Area right of the last column (AUTO_RESIZE_OFF) shows the viewport background
		propertiesTableScrollPane.getViewport().setBackground(Color.WHITE);
		leftPart.add(propertiesTableScrollPane, BorderLayout.CENTER);

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

	private JPanel createSearchBox() throws Exception {
		final JPanel searchBox = new JPanel(new GridBagLayout());
		searchBox.setBorder(BorderFactory.createEtchedBorder());

		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.insets = new Insets(1, 2, 1, 2);
		constraints.gridy = 0;

		final JTextField searchTextField = new JTextField(LangResources.get("search"), 12);
		// GridBagLayout shrinks components to their minimum size when space gets tight, keep the field usable
		searchTextField.setMinimumSize(searchTextField.getPreferredSize());
		searchTextField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(final FocusEvent e) {
				if (searchTextField.getText().equals(LangResources.get("search"))) {
					searchTextField.setText("");
				}
			}

			@Override
			public void focusLost(final FocusEvent e) {
				if (Utilities.isEmpty(searchTextField.getText())) {
					searchTextField.setText(LangResources.get("search"));
				}
			}
		});
		searchTextField.getDocument().addDocumentListener(new SimpleDocumentListener(() -> {
			final String text = searchTextField.getText();
			if (Utilities.isNotEmpty(text) && !text.equals(LangResources.get("search")) && languageProperties != null) {
				searchText = text;
				searchFromCurrentSelection();
			}
		}));
		constraints.gridx = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		searchBox.add(searchTextField, constraints);
		searchComponents.add(searchTextField);

		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;

		final JButton searchDownButton = new JButton(ImageManager.getImage("down.png"));
		searchDownButton.setToolTipText(LangResources.get("search_down"));
		searchDownButton.setMargin(new Insets(1, 1, 1, 1));
		searchDownButton.setPreferredSize(new Dimension(25, 25));
		searchDownButton.addActionListener(e -> {
			if (Utilities.isNotEmpty(searchText)) {
				selectSearch(searchText, propertiesTable.getSelectedRow() + 1, true, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference);
			}
		});
		constraints.gridx++;
		searchBox.add(searchDownButton, constraints);
		searchComponents.add(searchDownButton);

		final JButton searchUpButton = new JButton(ImageManager.getImage("up.png"));
		searchUpButton.setToolTipText(LangResources.get("search_up"));
		searchUpButton.setMargin(new Insets(1, 1, 1, 1));
		searchUpButton.setPreferredSize(new Dimension(25, 25));
		searchUpButton.addActionListener(e -> {
			if (Utilities.isNotEmpty(searchText)) {
				selectSearch(searchText, propertiesTable.getSelectedRow() - 1, false, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference);
			}
		});
		constraints.gridx++;
		searchBox.add(searchUpButton, constraints);
		searchComponents.add(searchUpButton);

		constraints.gridx++;
		searchBox.add(createSearchCheckBox("Aa", LangResources.get("case_sensitive"), !searchCaseInsensitivePreference, selected -> searchCaseInsensitivePreference = !selected), constraints);
		constraints.gridx++;
		searchBox.add(createSearchCheckBox(LangResources.get("columnheader_key"), LangResources.get("columnheader_key"), searchInKeysPreference, selected -> searchInKeysPreference = selected), constraints);
		constraints.gridx++;
		searchBox.add(createSearchCheckBox(LangResources.get("value"), LangResources.get("value"), searchInValuesPreference, selected -> searchInValuesPreference = selected), constraints);
		constraints.gridx++;
		searchBox.add(createSearchCheckBox(LangResources.get("columnheader_path"), LangResources.get("columnheader_path"), searchInPathPreference, selected -> searchInPathPreference = selected), constraints);

		return searchBox;
	}

	private JCheckBox createSearchCheckBox(final String text, final String toolTipText, final boolean initialSelection, final Consumer<Boolean> preferenceSetter) {
		final JCheckBox checkBox = new JCheckBox(text, initialSelection);
		checkBox.setToolTipText(toolTipText);
		checkBox.addActionListener(e -> {
			preferenceSetter.accept(checkBox.isSelected());
			if (Utilities.isNotEmpty(searchText) && !searchText.equals(LangResources.get("search")) && languageProperties != null) {
				searchFromCurrentSelection();
			}
		});
		searchComponents.add(checkBox);
		return checkBox;
	}

	/**
	 * Searches forward, starting at (and including) the currently selected row, or
	 * at the first row if nothing is selected.
	 */
	private void searchFromCurrentSelection() {
		final int startIndex = propertiesTable.getSelectedRow() >= 0 ? propertiesTable.getSelectedRow() : 0;
		selectSearch(searchText, startIndex, true, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference);
	}

	/**
	 * Makes the table header clickable for sorting and shows the look and feel's
	 * sort icon on the current sort column.
	 */
	private void installSortableHeader(final JTableHeader header) {
		header.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent event) {
				if (!javax.swing.SwingUtilities.isLeftMouseButton(event) || languageProperties == null) {
					return;
				}

				final int viewColumn = header.columnAtPoint(event.getPoint());
				if (viewColumn >= 0) {
					sortByColumn(propertiesTable.convertColumnIndexToModel(viewColumn));
				}
			}
		});

		final TableCellRenderer defaultHeaderRenderer = header.getDefaultRenderer();
		header.setDefaultRenderer((table, value, isSelected, hasFocus, row, column) -> {
			final Component component = defaultHeaderRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (component instanceof JLabel) {
				final JLabel label = (JLabel) component;
				// The default header renderer is a shared component, so the icon must be reset for every other column
				Icon sortIcon = null;
				if (table != null && table.convertColumnIndexToModel(column) == sortColumnModelIndex) {
					sortIcon = UIManager.getIcon(sortAscending ? "Table.ascendingSortIcon" : "Table.descendingSortIcon");
				}
				label.setIcon(sortIcon);
				label.setHorizontalTextPosition(SwingConstants.LEADING);
			}
			return component;
		});
	}

	private void sortByColumn(final int modelColumn) {
		if (modelColumn == COLUMN_NR) {
			// The row number is not sortable, it always shows the current display order
			return;
		}

		if (modelColumn == sortColumnModelIndex) {
			sortAscending = !sortAscending;
		} else {
			sortAscending = true;
		}
		sortColumnModelIndex = modelColumn;

		Comparator<LanguageProperty> comparator;
		if (modelColumn == COLUMN_KEY) {
			comparator = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getKey);
		} else if (modelColumn == COLUMN_ORIGINAL_INDEX || modelColumn == COLUMN_PATH) {
			comparator = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getOriginalIndex);
		} else {
			final String languageSign = availableLanguageSigns.get(modelColumn - COLUMN_FIRST_LANGUAGE);
			comparator = Comparator.comparing(languageProperty -> getEmptyForNull(languageProperty.getLanguageValue(languageSign)));
		}
		if (!sortAscending) {
			comparator = comparator.reversed();
		}

		languageProperties = languageProperties.stream().sorted(comparator).collect(Collectors.toList());

		refreshTable();
		propertiesTable.getTableHeader().repaint();
	}

	/**
	 * Recreates the table columns and the language detail fields after the set of
	 * properties or languages changed.
	 */
	public void setupTable() {
		sortColumnModelIndex = COLUMN_NR;
		sortAscending = true;

		technicalSelectionChange = true;
		try {
			propertiesTableModel.fireTableStructureChanged();
			applyColumnLayout();
		} finally {
			technicalSelectionChange = false;
		}

		detailFieldsPart.removeAll();
		languageTextFields.clear();

		if (languageProperties != null) {
			final GridBagConstraints labelConstraints = new GridBagConstraints();
			labelConstraints.gridx = 0;
			labelConstraints.anchor = GridBagConstraints.WEST;
			labelConstraints.insets = new Insets(2, 2, 2, 4);

			final GridBagConstraints fieldConstraints = new GridBagConstraints();
			fieldConstraints.gridx = 1;
			fieldConstraints.weightx = 1;
			fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
			fieldConstraints.insets = new Insets(2, 0, 2, 2);

			int row = 0;
			for (final String sign : availableLanguageSigns) {
				labelConstraints.gridy = row;
				fieldConstraints.gridy = row;

				final String labelText = LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT.equals(sign) ? LangResources.get("columnheader_default") : sign;
				detailFieldsPart.add(new JLabel(labelText + ":"), labelConstraints);

				final JTextArea languageTextfield = createLanguageValueTextArea();
				languageTextfield.getDocument().addDocumentListener(new DetailModifyListener());
				detailFieldsPart.add(languageTextfield, fieldConstraints);
				languageTextFields.put(sign, languageTextfield);

				row++;
			}

			// Filler, keeps the fields at the top
			final GridBagConstraints fillerConstraints = new GridBagConstraints();
			fillerConstraints.gridy = row;
			fillerConstraints.weighty = 1;
			detailFieldsPart.add(new JPanel(), fillerConstraints);
		}

		detailFieldsPart.revalidate();
		detailFieldsPart.repaint();

		// Keep the selection (e.g. after translating selected rows) as far as those properties still exist
		currentSelectedProperties.removeIf(property -> languageProperties == null || indexOfIdentical(languageProperties, property) < 0);
		restoreSelection(currentSelectedProperties);
		if (languageProperties != null) {
			refreshDetailView();
		}
	}

	private void applyColumnLayout() {
		final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

		for (int viewIndex = 0; viewIndex < propertiesTable.getColumnCount(); viewIndex++) {
			final TableColumn column = propertiesTable.getColumnModel().getColumn(viewIndex);
			final int modelIndex = column.getModelIndex();
			switch (modelIndex) {
				case COLUMN_NR:
					column.setPreferredWidth(50);
					break;
				case COLUMN_PATH:
					column.setPreferredWidth(100);
					break;
				case COLUMN_ORIGINAL_INDEX:
					column.setPreferredWidth(60);
					break;
				case COLUMN_KEY:
					column.setPreferredWidth(175);
					break;
				default:
					final String sign = availableLanguageSigns.get(modelIndex - COLUMN_FIRST_LANGUAGE);
					final int headerTextWidth = propertiesTable.getFontMetrics(propertiesTable.getTableHeader().getFont()).stringWidth(propertiesTableModel.getColumnName(modelIndex)) + 16;
					column.setPreferredWidth(Math.max(sign.length() > 3 ? 50 : 25, headerTextWidth));
					column.setCellRenderer(centerRenderer);
					break;
			}
		}
	}

	private JPanel createRightPart() {
		final JPanel rightPart = new JPanel(new BorderLayout(0, 3));
		rightPart.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		final JPanel keyBereich = new JPanel(new GridBagLayout());

		final GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.gridx = 0;
		labelConstraints.anchor = GridBagConstraints.WEST;
		labelConstraints.insets = new Insets(2, 2, 2, 4);

		final GridBagConstraints fieldConstraints = new GridBagConstraints();
		fieldConstraints.gridx = 1;
		fieldConstraints.weightx = 1;
		fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
		fieldConstraints.insets = new Insets(2, 0, 2, 2);

		labelConstraints.gridy = 0;
		fieldConstraints.gridy = 0;
		keyBereich.add(new JLabel(LangResources.get("columnheader_path") + ":"), labelConstraints);
		pathTextfield = new JTextField();
		pathTextfield.setEditable(false);
		pathTextfield.getDocument().addDocumentListener(new DetailModifyListener());
		keyBereich.add(pathTextfield, fieldConstraints);

		labelConstraints.gridy = 1;
		fieldConstraints.gridy = 1;
		keyBereich.add(new JLabel(LangResources.get("columnheader_key") + ":"), labelConstraints);
		keyTextfield = new JTextField();
		keyTextfield.getDocument().addDocumentListener(new DetailModifyListener());
		keyBereich.add(keyTextfield, fieldConstraints);

		labelConstraints.gridy = 2;
		fieldConstraints.gridy = 2;
		keyBereich.add(new JLabel(LangResources.get("comment") + ":"), labelConstraints);
		commentTextfield = new JTextField();
		commentTextfield.getDocument().addDocumentListener(new DetailModifyListener());
		keyBereich.add(commentTextfield, fieldConstraints);

		final GridBagConstraints separatorConstraints = new GridBagConstraints();
		separatorConstraints.gridx = 0;
		separatorConstraints.gridy = 3;
		separatorConstraints.gridwidth = 2;
		separatorConstraints.fill = GridBagConstraints.HORIZONTAL;
		separatorConstraints.insets = new Insets(4, 0, 0, 0);
		keyBereich.add(new JSeparator(SwingConstants.HORIZONTAL), separatorConstraints);

		rightPart.add(keyBereich, BorderLayout.NORTH);

		detailFieldsPart = new JPanel(new GridBagLayout());
		final JScrollPane scrolledPart = new JScrollPane(detailFieldsPart);
		scrolledPart.setBorder(BorderFactory.createEmptyBorder());
		scrolledPart.setMinimumSize(new Dimension(200, 100));
		scrolledPart.getVerticalScrollBar().setUnitIncrement(16);
		rightPart.add(scrolledPart, BorderLayout.CENTER);

		final JPanel buttonBereich = new JPanel(new GridBagLayout());

		final GridBagConstraints buttonConstraints = new GridBagConstraints();
		buttonConstraints.fill = GridBagConstraints.HORIZONTAL;
		buttonConstraints.weightx = 1;
		buttonConstraints.insets = new Insets(2, 2, 2, 2);

		buttonConstraints.gridx = 0;
		buttonConstraints.gridy = 0;
		buttonConstraints.gridwidth = 2;
		buttonBereich.add(new JSeparator(SwingConstants.HORIZONTAL), buttonConstraints);

		textConversionButton = new JButton(showStorageTexts ? LangResources.get("change_to_show_visble_texts") : LangResources.get("change_to_show_storage_texts"));
		textConversionButton.addActionListener(e -> {
			showStorageTexts = !showStorageTexts;
			textConversionButton.setText(showStorageTexts ? LangResources.get("change_to_show_visble_texts") : LangResources.get("change_to_show_storage_texts"));
			changeDisplayMode(showStorageTexts);
		});
		buttonConstraints.gridy = 1;
		buttonBereich.add(textConversionButton, buttonConstraints);

		okButton = new JButton(LangResources.get("button_text_add"));
		okButton.addActionListener(e -> applyDetailChanges());
		buttonConstraints.gridy = 2;
		buttonConstraints.gridwidth = 1;
		buttonBereich.add(okButton, buttonConstraints);

		cancelButton = new JButton(LangResources.get("button_text_discard"));
		cancelButton.addActionListener(e -> refreshDetailView());
		buttonConstraints.gridx = 1;
		buttonBereich.add(cancelButton, buttonConstraints);

		rightPart.add(buttonBereich, BorderLayout.SOUTH);

		checkButtonStatus();

		return rightPart;
	}

	/**
	 * OK button of the detail view: changes the selected property or adds a new
	 * one.
	 */
	private void applyDetailChanges() {
		try {
			hasUnsavedChanges = true;

			if (detailShowsExistingProperty) {
				// Change existing property
				if (currentSelectedProperties.isEmpty()) {
					throw new Exception("Cannot find property to change");
				}
				final LanguageProperty propertyToChange = currentSelectedProperties.get(0);

				propertyToChange.setKey(getPlainFieldValue(keyTextfield.getText()));
				propertyToChange.setComment(Utilities.isNotEmpty(commentTextfield.getText()) ? commentTextfield.getText() : null);
				for (final Map.Entry<String, JTextArea> languageTextField : languageTextFields.entrySet()) {
					propertyToChange.setLanguageValue(languageTextField.getKey(), getPlainFieldValue(languageTextField.getValue().getText()));
				}

				refreshTable();
				dataWasModified = false;
				checkButtonStatus();
			} else {
				final LanguageProperty newValues = new LanguageProperty(pathTextfield.getText(), getPlainFieldValue(keyTextfield.getText()));
				for (final Map.Entry<String, JTextArea> languageTextField : languageTextFields.entrySet()) {
					newValues.setLanguageValue(languageTextField.getKey(), getPlainFieldValue(languageTextField.getValue().getText()));
				}

				if (Utilities.isNotEmpty(commentTextfield.getText())) {
					newValues.setComment(commentTextfield.getText());
				} else {
					newValues.setComment(null);
				}

				if (languageProperties == null) {
					languageProperties = new ArrayList<>();
					setLanguagePropertiesSetName(new SimpleInputDialog(this, getTitle(), LangResources.get("enterNewLanguagePropertiesName")).open());
					availableLanguageSigns = new ArrayList<>();
					availableLanguageSigns.add(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);

					hasUnsavedChanges = false;
					setupTable();
					checkButtonStatus();
				}

				// Add new property
				newValues.setOriginalIndex(languageProperties.size() + 1);
				languageProperties.add(newValues);
				currentSelectedProperties = new ArrayList<>();
				currentSelectedProperties.add(newValues);
				refreshTable();
				refreshDetailView();
				removeButton.setEnabled(true);
				dataWasModified = false;
				checkButtonStatus();
			}
		} catch (final Exception ex) {
			showError(ex);
		}
	}

	/**
	 * Called (deferred) after the user changed the table selection.
	 */
	private void handleUserSelectionChange() {
		final List<LanguageProperty> newSelection = getSelectedProperties();
		if (isSameSelection(newSelection, currentSelectedProperties)) {
			// Nothing changed (e.g. a second event for the same user action)
			return;
		}

		technicalDataChange = true;
		try {
			if (!dataWasModified || askForDiscardChanges()) {
				// Take over the new selection
				currentSelectedProperties = newSelection;
				removeButton.setEnabled(!newSelection.isEmpty());
				refreshDetailView();
			} else {
				// Reselect the old entries
				restoreSelection(currentSelectedProperties);
			}
		} finally {
			technicalDataChange = false;
		}

		checkButtonStatus();
	}

	private void addNewProperty() {
		if (!dataWasModified || askForDiscardChanges()) {
			technicalSelectionChange = true;
			try {
				propertiesTable.clearSelection();
			} finally {
				technicalSelectionChange = false;
			}
			currentSelectedProperties = new ArrayList<>();
			refreshDetailView();
		}
	}

	/** Shared by the trash button and the DEL key on the properties table */
	private void removeSelectedProperties() {
		try {
			if (propertiesTable.getSelectedRowCount() > 0 && askForDropProperties()) {
				// Remove by identity, so also exactly the selected one of several duplicates is removed
				final Set<LanguageProperty> propertiesToRemove = Collections.newSetFromMap(new IdentityHashMap<>());
				propertiesToRemove.addAll(getSelectedProperties());
				languageProperties.removeIf(propertiesToRemove::contains);

				currentSelectedProperties = new ArrayList<>();
				setupTable();
				refreshDetailView();
				hasUnsavedChanges = true;
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
				for (final LanguageProperty languageProperty : languageProperties) {
					if (!languageProperty.getAvailableLanguageSigns().contains(newLanguageSign)) {
						languageProperty.setLanguageValue(newLanguageSign, null);
					}
				}
				availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);
				setupTable();
			}
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	private void deleteLanguage() {
		try {
			final List<String> availableLanguageSignsToDelete = new ArrayList<>(availableLanguageSigns);
			final String languageSignToDelete = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectLanguageSignToDelete"), availableLanguageSignsToDelete).open();
			if (Utilities.isNotBlank(languageSignToDelete)) {
				for (final LanguageProperty languageProperty : languageProperties) {
					languageProperty.removeLanguageValue(languageSignToDelete);
				}
				availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);
				setupTable();
			}
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
	}

	/**
	 * The selected properties, or all properties if nothing is selected.
	 */
	private List<LanguageProperty> getSelectedOrAllProperties() {
		if (propertiesTable.getSelectedRowCount() > 0) {
			return getSelectedProperties();
		} else {
			return languageProperties;
		}
	}

	private void translate() {
		try {
			if (Utilities.isBlank(applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY))) {
				final String deeplApiKey = new SimpleInputDialog(this, getTitle(), LangResources.get("enterDeeplApiKey")).open();
				if (deeplApiKey != null) {
					applicationConfiguration.set(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY, deeplApiKey);
				}
			}

			if (Utilities.isBlank(applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY))) {
				showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("missingDeeplApiKey"));
				return;
			}

			TranslationConstants translationConstants = null;
			final String translationConstantsFilePath = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_TRANSLATION_CONSTANTS_FILE);
			if (Utilities.isNotBlank(translationConstantsFilePath)) {
				try {
					translationConstants = TranslationConstants.read(new File(translationConstantsFilePath.trim()));
				} catch (final Exception e) {
					showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("errorReadingTranslationConstantsFile", translationConstantsFilePath, e.getMessage()));
					return;
				}
			}

			final String deeplBaseUrl = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_BASEURL);
			final DeepLHelper deepLHelper = new DeepLHelper(deeplBaseUrl, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY), applicationConfiguration.getProxyConfiguration().getProxy(deeplBaseUrl));

			final String languageSignTranslateSource = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectSourceLanguageSignToTranslate"), availableLanguageSigns, 0).open();
			if (Utilities.isBlank(languageSignTranslateSource)) {
				return;
			}
			String sourceLanguage = languageSignTranslateSource;
			if ("Default".equalsIgnoreCase(sourceLanguage)) {
				sourceLanguage = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectDefaultLanguageToTranslate"), deepLHelper.getSupportedLanguages(), deepLHelper.getSupportedLanguages().indexOf("EN")).open();
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
				languageSignTranslateTarget = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectTargetLanguageSignToTranslate"), availableOtherLanguageSigns).open();
				if (Utilities.isBlank(languageSignTranslateTarget)) {
					return;
				}
			}
			String targetLanguage = languageSignTranslateTarget;
			if ("Default".equalsIgnoreCase(targetLanguage)) {
				targetLanguage = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectDefaultLanguageToTranslate"), deepLHelper.getSupportedLanguages(), deepLHelper.getSupportedLanguages().indexOf("EN")).open();
				if (Utilities.isBlank(targetLanguage)) {
					return;
				}
			}
			if (targetLanguage.contains("_")) {
				targetLanguage = targetLanguage.substring(0, targetLanguage.indexOf("_"));
			}

			// Only restrict to the selected rows if any are selected, otherwise translate all properties
			final List<LanguageProperty> languagePropertiesToTranslate = getSelectedOrAllProperties();

			final TranslateLanguagePropertiesWorker translateLanguagePropertiesWorker = new TranslateLanguagePropertiesWorker(null, languagePropertiesToTranslate, deepLHelper, languageSignTranslateSource, languageSignTranslateTarget, sourceLanguage, targetLanguage, translationConstants);
			final ProgressDialog<TranslateLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(this, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("translatingLanguageProperties"), translateLanguagePropertiesWorker);
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
			showError(ex);
		}
		checkButtonStatus();
	}

	private void transfer() {
		try {
			final String languageSignTransferSource = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectSourceLanguageSignToTransfer"), availableLanguageSigns, 0).open();
			if (Utilities.isBlank(languageSignTransferSource)) {
				return;
			}

			final List<String> availableOtherLanguageSigns = new ArrayList<>(availableLanguageSigns);
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
			int countTransfers = 0;
			for (final LanguageProperty languageProperty : getSelectedOrAllProperties()) {
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
			showError(ex);
		}
		checkButtonStatus();
	}

	private void clearIdentical() {
		try {
			final String languageSignClearSource = new ComboSelectionDialog(this, getTitle(), LangResources.get("selectSourceLanguageSignToClearIdentical"), availableLanguageSigns, 0).open();
			if (Utilities.isBlank(languageSignClearSource)) {
				return;
			}

			final List<String> availableOtherLanguageSigns = new ArrayList<>(availableLanguageSigns);
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
			int countCleared = 0;
			for (final LanguageProperty languageProperty : getSelectedOrAllProperties()) {
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
			showError(ex);
		}
		checkButtonStatus();
	}

	private void removeDuplicates() {
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
				final QuestionDialog dialog = new QuestionDialog(this, LangResources.get("question_title_remove_duplicates"), LangResources.get("question_content_remove_duplicates", duplicatesToRemove.size()) + "\n\n" + reportText.toString(), LangResources.get("yes"), LangResources.get("no"));
				final Integer returncode = dialog.open();
				if (returncode != null && returncode == 0) {
					final Set<LanguageProperty> propertiesToRemove = Collections.newSetFromMap(new IdentityHashMap<>());
					propertiesToRemove.addAll(duplicatesToRemove);
					languageProperties.removeIf(propertiesToRemove::contains);

					currentSelectedProperties = new ArrayList<>();
					setupTable();
					refreshDetailView();
					hasUnsavedChanges = true;
					showMessage(LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("duplicatesRemoved", duplicatesToRemove.size()));
				}
			}
		} catch (final Exception ex) {
			showError(ex);
		}
		checkButtonStatus();
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

	private void checkErrors() {
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
			showError(ex);
		}
		checkButtonStatus();
	}

	private void showStatistics() {
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
			showError(ex);
		}
	}

	private void openConfiguration() {
		try {
			byte[] iconData;
			try (InputStream inputStream = ImageManager.class.getResourceAsStream("LanguagePropertiesManager.ico")) {
				iconData = IoUtilities.toByteArray(inputStream);
			}

			final ApplicationConfigurationDialog dialog = new ApplicationConfigurationDialog(this, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.APPLICATION_STARTUPCLASS_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.VERSION_BUILDTIME, applicationConfiguration, iconData, ImageManager.getImage("LanguagePropertiesManager.png").getImage(), LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null, null);
			if (dialog.open() == Result.OK) {
				applicationConfiguration.save();

				loadConfiguration();
			}
		} catch (final Exception ex) {
			showError(ex);
		}
	}

	private void checkUsageNew() {
		try {
			final File directory = chooseDirectory(getTitle() + " " + LangResources.get("directory_dialog_title"), null);
			if (directory == null) {
				showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
			} else if (directory.exists() && directory.isDirectory()) {
				final SimpleInputDialog filePatternDialog = new SimpleInputDialog(this, getTitle(), LangResources.get("enterfilepattern"));
				filePatternDialog.setDefaultText(".*\\.java");
				final String filePattern = filePatternDialog.open();
				if (filePattern == null) {
					showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
				} else {
					final SimpleInputDialog usagePatternDialog = new SimpleInputDialog(this, getTitle(), LangResources.get("enterusagepattern"));
					usagePatternDialog.setDefaultText("LangResources.get(\"<property>\"");
					final String usagePattern = usagePatternDialog.open();
					if (usagePattern != null) {
						recentlyCheckUsages.add(CsvWriter.getCsvLine(createCheckUsageCsvFormat(), directory.getAbsolutePath(), filePattern, usagePattern));
						applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE, recentlyCheckUsages);
						checkUsage(languageProperties, directory.getAbsolutePath(), filePattern, usagePattern);
						checkButtonStatus();
					}
				}
			}
		} catch (final Exception ex) {
			showError(ex);
		}
	}

	/**
	 * CSV format of a single recent check usage setting (directory, file pattern, usage pattern).
	 * Plain RFC 4180 csv: backslashes in paths and regular expressions are stored as they are.
	 */
	private static CsvFormat createCheckUsageCsvFormat() {
		return new CsvFormat()
				.withSeparator(';')
				.withStringQuote('"')
				.withEscapeLineBreaks(false);
	}

	/**
	 * Parses a recent check usage setting (directory, file pattern, usage pattern).
	 * Settings of older versions were stored with backslash escaping. Such a setting is only
	 * accepted as legacy setting, if it is readable with backslash escaping and its directory exists.
	 * A plain setting with Windows paths is practically never readable with backslash escaping
	 * (e.g. "\U" in "C:\Users" is no valid escape sequence).
	 */
	private static List<String> parseCheckUsageSetting(final String setting) throws Exception {
		try {
			final List<String> legacySettings = CsvReader.parseCsvLine(createCheckUsageCsvFormat().withEscapeLineBreaks(true), setting);
			if (legacySettings.size() == 3 && new File(legacySettings.get(0)).isDirectory()) {
				return legacySettings;
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			// Not a legacy setting
		}

		final List<String> settings = CsvReader.parseCsvLine(createCheckUsageCsvFormat(), setting);
		if (settings.size() != 3) {
			throw new Exception("Invalid recent check usage setting: " + setting);
		}
		return settings;
	}

	private void checkUsagePrevious() {
		try {
			final ComboSelectionDialog dialog = new ComboSelectionDialog(this, getTitle() + " " + LangResources.get("recentsettingsdialogtitle"), LangResources.get("recent_settings_dialog_text"), recentlyCheckUsages);
			final String setting = dialog.open();

			// Take over a possible reordering (drag&drop) or deletion of the recent
			// settings done in the dialog, regardless of whether an entry was selected
			// or the dialog was canceled
			recentlyCheckUsages.clear();
			recentlyCheckUsages.addAll(dialog.getItems());
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE, recentlyCheckUsages);

			if (setting != null) {
				recentlyCheckUsages.add(setting); // put selected as latest used
				applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PREVIOUS_CHECK_USAGE, recentlyCheckUsages);
				final List<String> settings = parseCheckUsageSetting(setting);
				final String directory = settings.get(0);
				final String filePattern = settings.get(1);
				final String usagePattern = settings.get(2);
				checkUsage(languageProperties, directory, filePattern, usagePattern);
			}
			checkButtonStatus();
		} catch (final Exception ex) {
			showError(ex);
		}
	}

	private class DetailModifyListener implements DocumentListener {
		@Override
		public void insertUpdate(final DocumentEvent event) {
			changed();
		}

		@Override
		public void removeUpdate(final DocumentEvent event) {
			changed();
		}

		@Override
		public void changedUpdate(final DocumentEvent event) {
			// Attribute changes only, no text change
		}

		private void changed() {
			if (!technicalDataChange) {
				dataWasModified = true;
			}
			checkButtonStatus();
		}
	}

	private void refreshDetailView() {
		final boolean previousTechnicalDataChange = technicalDataChange;
		technicalDataChange = true;
		try {
			final LanguageProperty property = currentSelectedProperties.isEmpty() ? null : currentSelectedProperties.get(0);
			if (property != null) {
				pathTextfield.setText(property.getPath());
				keyTextfield.setText(showStorageTexts ? StringEscapeUtils.escapeJava(property.getKey()) : property.getKey());
				commentTextfield.setText(Utilities.isNotEmpty(property.getComment()) ? property.getComment() : "");
				for (final Map.Entry<String, JTextArea> languageTextField : languageTextFields.entrySet()) {
					final String value = property.getLanguageValue(languageTextField.getKey());
					if (value == null) {
						languageTextField.getValue().setText("");
					} else if (showStorageTexts) {
						languageTextField.getValue().setText(StringEscapeUtils.escapeJava(value));
					} else {
						languageTextField.getValue().setText(value);
					}
				}

				detailShowsExistingProperty = true;
				okButton.setText(LangResources.get("button_text_change"));
				removeButton.setEnabled(true);
			} else {
				pathTextfield.setText("");
				keyTextfield.setText("");
				commentTextfield.setText("");
				for (final JTextArea languageTextfield : languageTextFields.values()) {
					languageTextfield.setText("");
				}

				detailShowsExistingProperty = false;
				okButton.setText(LangResources.get("button_text_add"));
				removeButton.setEnabled(false);
			}

			dataWasModified = false;
			checkButtonStatus();
		} catch (final Exception e) {
			showError(e);
		} finally {
			technicalDataChange = previousTechnicalDataChange;
		}
	}

	public void checkButtonStatus() {
		final int rowCount = propertiesTableModel == null ? 0 : propertiesTableModel.getRowCount();
		final boolean hasProperties = languageProperties != null && languageProperties.size() > 0;
		final boolean hasMultipleLanguages = hasProperties && availableLanguageSigns != null && availableLanguageSigns.size() > 1;

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
			addButton.setEnabled(rowCount > 0);
		}
		if (loadRecentButton != null) {
			loadRecentButton.setEnabled(recentlyOpenedDirectories != null && recentlyOpenedDirectories.size() > 0);
		}
		if (propertiesTable != null) {
			propertiesTable.setEnabled(rowCount > 0);
		}
		for (final JComponent searchComponent : searchComponents) {
			searchComponent.setEnabled(rowCount > 0);
		}
		if (checkUsageButton != null) {
			checkUsageButton.setEnabled(hasProperties);
		}
		if (checkUsageButtonPrevious != null) {
			checkUsageButtonPrevious.setEnabled(hasProperties && recentlyCheckUsages != null && recentlyCheckUsages.size() > 0);
		}
		if (textConversionButton != null) {
			textConversionButton.setEnabled(true);
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

	/**
	 * Creates the input component for a language value.
	 * A JTextField can not be used here, because its document silently replaces
	 * line breaks by blanks ("filterNewlines"), so values like "a\nb" would lose
	 * their line break in the plain text display mode and on writing back.
	 * The text area grows in height with the number of lines, but is styled and
	 * behaves (font, border, Tab focus traversal) like a single line text field.
	 */
	private static JTextArea createLanguageValueTextArea() {
		final JTextArea textArea = new JTextArea();
		// No line wrap: only real line breaks create new lines, like in the stored value
		textArea.setLineWrap(false);
		final Font textFieldFont = UIManager.getFont("TextField.font");
		if (textFieldFont != null) {
			textArea.setFont(textFieldFont);
		}
		final javax.swing.border.Border textFieldBorder = UIManager.getBorder("TextField.border");
		if (textFieldBorder != null) {
			textArea.setBorder(textFieldBorder);
		} else {
			textArea.setBorder(BorderFactory.createEtchedBorder());
		}
		// Let Tab / Shift+Tab move the focus instead of inserting a tab character
		textArea.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
		textArea.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
		return textArea;
	}

	private void changeDisplayMode(final boolean changeToShowStorageTexts) {
		technicalDataChange = true;
		try {
			if (changeToShowStorageTexts) {
				keyTextfield.setText(StringEscapeUtils.escapeJava(keyTextfield.getText()));
				for (final JTextArea field : languageTextFields.values()) {
					field.setText(StringEscapeUtils.escapeJava(field.getText()));
				}
			} else {
				keyTextfield.setText(StringEscapeUtils.unescapeJava(keyTextfield.getText()));
				for (final JTextArea field : languageTextFields.values()) {
					field.setText(StringEscapeUtils.unescapeJava(field.getText()));
				}
			}
		} finally {
			technicalDataChange = false;
		}
	}

	/**
	 * Common handling after one of the load/import actions: resets the model on
	 * errors and refreshes the table.
	 */
	private void afterLoad() {
		hasUnsavedChanges = false;
		currentSelectedProperties = new ArrayList<>();
		setupTable();
		refreshDetailView();
		checkButtonStatus();
	}

	private void resetLoadedData() {
		languageProperties = null;
		availableLanguageSigns = null;
		setLanguagePropertiesSetName(null);
	}

	private void openFiles() {
		if (hasUnsavedChanges && !askForDiscardChanges()) {
			return;
		}

		try {
			final File file = chooseFileToOpen(getTitle() + " " + LangResources.get("open_file_dialog_text"), null);
			if (file == null) {
				showErrorMessage(LangResources.get("open_file_dialog_text"), LangResources.get("canceledByUser"));
			} else if (file.exists() && file.isFile()) {
				if (loadSingleLanguagePropertiesSet(file.getAbsolutePath())) {
					recentlyOpenedDirectories.add(file.getAbsolutePath()); // put selected as latest used
					applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
				}
			} else {
				throw new Exception("Selected language properties set path is not an existing file");
			}
		} catch (final Exception e) {
			resetLoadedData();
			showError(e);
		}
		afterLoad();
	}

	private boolean loadSingleLanguagePropertiesSet(final String filePath) throws ExecutionException {
		final File languagePropertiesFile = new File(filePath);
		if (!languagePropertiesFile.getName().endsWith(applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION))) {
			showErrorMessage(LangResources.get("open_file_dialog_text"), LangResources.get("missingMandatoryFileExtension", applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION)));
			return false;
		} else {
			final LoadLanguagePropertiesWorker openFilesLanguagePropertiesWorker = new LoadLanguagePropertiesWorker(null, languagePropertiesFile, null, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
			openFilesLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
			final ProgressDialog<LoadLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(this, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("openFilesResult"), openFilesLanguagePropertiesWorker);
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

	private void openFolder() {
		if (hasUnsavedChanges && !askForDiscardChanges()) {
			return;
		}

		try {
			final File basicDirectory = chooseDirectory(LangResources.get("open_directory_dialog_text"), null);
			if (basicDirectory == null) {
				showErrorMessage(LangResources.get("open_directory_dialog_text"), LangResources.get("canceledByUser"));
			} else if (basicDirectory.exists()) {
				if (openAllLanguagePropertiesSets(basicDirectory.getAbsolutePath())) {
					recentlyOpenedDirectories.add(basicDirectory.getAbsolutePath()); // put selected as latest used
					applicationConfiguration.set(LanguagePropertiesManager.CONFIG_RECENT_PROPERTIES, recentlyOpenedDirectories);
				}
			}
		} catch (final Exception e) {
			resetLoadedData();
			showError(e);
		}
		afterLoad();
	}

	private boolean openAllLanguagePropertiesSets(final String basicDirectoryPath) throws ExecutionException {
		final String[] excludeParts = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES).split(";");
		final LoadLanguagePropertiesWorker openFolderLanguagePropertiesWorker = new LoadLanguagePropertiesWorker(null, new File(basicDirectoryPath), excludeParts, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
		openFolderLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
		final ProgressDialog<LoadLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(this, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("load_folder"), openFolderLanguagePropertiesWorker);
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
		propertiesLabel.revalidate();
	}

	private void openRecent() {
		if (hasUnsavedChanges && !askForDiscardChanges()) {
			return;
		}

		try {
			final ComboSelectionDialog dialog = new ComboSelectionDialog(this, getTitle() + " " + LangResources.get("recent_directories_dialog_title"), LangResources.get("recent_directories_dialog_text"), recentlyOpenedDirectories).withSize(600, -1);
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
			} else if (new File(filePath).isDirectory()) {
				openAllLanguagePropertiesSets(filePath);
			} else {
				loadSingleLanguagePropertiesSet(filePath);
			}
		} catch (final Exception e) {
			resetLoadedData();
			showError(e);
		}
		afterLoad();
	}

	private void saveFiles() {
		try {
			String defaultLanguagePropertiesPath = null;
			final Set<String> languagePropertiesPaths = languageProperties.stream().map(o -> o.getPath()).collect(Collectors.toSet());
			if (languagePropertiesPaths.contains("")) {
				if (languagePropertiesPaths.size() == 2) {
					languagePropertiesPaths.remove("");
					defaultLanguagePropertiesPath = Utilities.replaceUsersHome(new ArrayList<>(languagePropertiesPaths).get(0));
				} else {
					final String propertiesFileExtension = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION);
					final File file = chooseFileToSave(getTitle() + " " + LangResources.get("save_file_dialog_text"), recentlyOpenedDirectories.getLatestAdded(), "MyLanguageProperties" + propertiesFileExtension);
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
				for (final LanguageProperty languageProperty : languageProperties) {
					if (Utilities.isBlank(languageProperty.getPath())) {
						languageProperty.setPath(defaultLanguagePropertiesPath);
					}
				}
			}

			final Integer returncode = new QuestionDialog(this, getTitle(), LangResources.get("question.keepExistingProperties"), LangResources.get("yes"), LangResources.get("no")).open();
			final boolean extendAndKeepExistingProperties = returncode != null && returncode == 0;

			final WriteLanguagePropertiesWorker writeLanguagePropertiesWorker = new WriteLanguagePropertiesWorker(null, languageProperties, languagePropertySetName, null, null, extendAndKeepExistingProperties, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
			writeLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
			final ProgressDialog<WriteLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(this, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("save_files"), writeLanguagePropertiesWorker);
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
			showError(e);
		}
	}

	private void saveFolder() {
		try {
			final String[] excludeParts = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES).split(";");

			// A directory selection is only needed to place properties that don't have a path of their own yet.
			// If every property already has a path, it can be saved directly to those paths without asking.
			final boolean hasPropertiesWithoutPath = languageProperties.stream().anyMatch(o -> Utilities.isBlank(o.getPath()));

			File directory = null;
			String newPropertiesSetName = "Multiple";
			if (hasPropertiesWithoutPath) {
				directory = chooseDirectory(LangResources.get("save_directory_dialog_text"), null);
				if (directory == null) {
					showErrorMessage(LangResources.get("save_directory_dialog_text"), LangResources.get("canceledByUser"));
					return;
				} else if (!directory.exists() || !directory.isDirectory()) {
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
			}

			final Integer returncode = new QuestionDialog(this, getTitle(), LangResources.get("question.keepExistingProperties"), LangResources.get("yes"), LangResources.get("no")).open();
			final boolean extendAndKeepExistingProperties = returncode != null && returncode == 0;

			final WriteLanguagePropertiesWorker writeLanguagePropertiesWorker = new WriteLanguagePropertiesWorker(null, languageProperties, newPropertiesSetName, directory, excludeParts, extendAndKeepExistingProperties, applicationConfiguration.get(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION));
			writeLanguagePropertiesWorker.setReadComments(!applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
			final ProgressDialog<WriteLanguagePropertiesWorker> progressDialog = new ProgressDialog<>(this, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("save_files"), writeLanguagePropertiesWorker);
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
			showError(e);
		}
	}

	private void importFromExcel() {
		importFromFile(new String[] { "xlsx" }, file -> {
			final ImportFromExcelWorker importFromExcelWorker = new ImportFromExcelWorker(null, file);
			importFromExcelWorker.setIgnoreComments(applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
			final Result dialogResult = new ProgressDialog<>(this, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("import_file"), importFromExcelWorker).open();
			if (dialogResult == Result.CANCELED) {
				return false;
			}
			// check for errors
			importFromExcelWorker.get();

			setLanguagePropertiesSetName(importFromExcelWorker.getLanguagePropertiesSetName());
			languageProperties = importFromExcelWorker.getLanguageProperties();
			availableLanguageSigns = importFromExcelWorker.getAvailableLanguageSigns();
			return true;
		});
	}

	private void importFromCsv() {
		importFromFile(new String[] { "csv", "dsv" }, file -> {
			final ImportFromCsvWorker importFromCsvWorker = new ImportFromCsvWorker(null, file);
			importFromCsvWorker.setIgnoreComments(applicationConfiguration.getBoolean(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS));
			final Result dialogResult = new ProgressDialog<>(this, LanguagePropertiesManager.APPLICATION_NAME, LangResources.get("import_file"), importFromCsvWorker).open();
			if (dialogResult == Result.CANCELED) {
				return false;
			}
			// check for errors
			importFromCsvWorker.get();

			setLanguagePropertiesSetName(importFromCsvWorker.getLanguagePropertiesSetName());
			languageProperties = importFromCsvWorker.getLanguageProperties();
			availableLanguageSigns = importFromCsvWorker.getAvailableLanguageSigns();
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
		if (hasUnsavedChanges && !askForDiscardChanges()) {
			return;
		}

		final File importFile = chooseFileToOpen(getTitle() + " " + LangResources.get("import_file"), Utilities.replaceUsersHome("~" + File.separator + "Downloads"), fileExtensions);
		if (importFile == null) {
			showErrorMessage(LangResources.get("import_file"), LangResources.get("canceledByUser"));
			return;
		}

		try {
			if (importAction.process(importFile)) {
				hasUnsavedChanges = true;
				currentSelectedProperties = new ArrayList<>();
				setupTable();
				refreshDetailView();
				showMessage(LangResources.get("import_file"), LangResources.get("actionSuccessfullyCompleted"));
			} else {
				showErrorMessage(LangResources.get("import_file"), LangResources.get("canceledByUser"));
			}
		} catch (final ExecutionException e) {
			resetLoadedData();
			hasUnsavedChanges = false;
			if (e.getCause() != null && e.getCause() instanceof LanguagePropertiesException) {
				showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, e.getCause().getMessage());
			} else {
				showError(e);
			}
			setupTable();
		} catch (final Exception e) {
			resetLoadedData();
			hasUnsavedChanges = false;
			showError(e);
			setupTable();
		}
		checkButtonStatus();
	}

	private void exportToExcel() {
		exportToFile("xlsx", exportFile -> {
			final List<String> languagePropertySetNames = new ArrayList<>();
			languagePropertySetNames.add(languagePropertySetName);
			final ExportToExcelWorker exportToExcelWorker = new ExportToExcelWorker(null, languageProperties, languagePropertySetNames, exportFile, false);
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
			languagePropertySetNames.add(languagePropertySetName);
			final ExportToCsvWorker exportToCsvWorker = new ExportToCsvWorker(null, languageProperties, languagePropertySetNames, exportFile, false);
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
			final File exportFile = chooseFileToSave(getTitle() + " " + LangResources.get("export_file"), Utilities.replaceUsersHome("~" + File.separator + "Downloads"), languagePropertySetName + "_Export_" + DateUtilities.formatDate("yyyy-MM-dd_HH-mm", LocalDateTime.now()) + "." + fileExtension, fileExtension);
			if (exportFile == null) {
				showErrorMessage(LangResources.get("export_file"), LangResources.get("canceledByUser"));
				return;
			}

			if (exportFile.exists()) {
				if (!askForOverwriteFile(exportFile.getAbsolutePath())) {
					throw new Exception(LangResources.get("error.destinationFileAlreadyExists", exportFile.getAbsolutePath()));
				} else {
					exportFile.delete();
				}
			}

			try {
				if (exportAction.process(exportFile)) {
					hasUnsavedChanges = false;
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
	 * Shows a file chooser to open a file.
	 *
	 * @param fileExtensions optional extension filters (without dot), e.g. "xlsx"
	 * @return the selected file or null if canceled
	 */
	private File chooseFileToOpen(final String title, final String initialDirectory, final String... fileExtensions) {
		final JFileChooser fileChooser = createFileChooser(title, initialDirectory, fileExtensions);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		return fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? fileChooser.getSelectedFile() : null;
	}

	/**
	 * Shows a file chooser to select a file to save to.
	 *
	 * @return the selected file or null if canceled
	 */
	private File chooseFileToSave(final String title, final String initialDirectory, final String proposedFileName, final String... fileExtensions) {
		final JFileChooser fileChooser = createFileChooser(title, initialDirectory, fileExtensions);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (Utilities.isNotBlank(proposedFileName)) {
			fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), proposedFileName));
		}
		return fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION ? fileChooser.getSelectedFile() : null;
	}

	/**
	 * Shows a file chooser to select a directory.
	 *
	 * @return the selected directory or null if canceled
	 */
	private File chooseDirectory(final String title, final String initialDirectory) {
		final JFileChooser fileChooser = createFileChooser(title, initialDirectory);
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		return fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? fileChooser.getSelectedFile() : null;
	}

	private static JFileChooser createFileChooser(final String title, final String initialDirectory, final String... fileExtensions) {
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle(title);

		if (Utilities.isNotBlank(initialDirectory)) {
			File directory = new File(initialDirectory);
			// Recent entries may also be files, start in their directory then
			if (directory.isFile()) {
				directory = directory.getParentFile();
			}
			if (directory != null && directory.isDirectory()) {
				fileChooser.setCurrentDirectory(directory);
			}
		}

		if (fileExtensions != null && fileExtensions.length > 0) {
			final FileNameExtensionFilter filter = new FileNameExtensionFilter("*." + String.join(", *.", fileExtensions), fileExtensions);
			fileChooser.addChoosableFileFilter(filter);
			fileChooser.setFileFilter(filter);
		}

		return fileChooser;
	}

	private static String getEmptyForNull(final String string) {
		return string == null ? "" : string;
	}

	/**
	 * Table model directly backed by "languageProperties". The language columns
	 * only show whether a value exists.
	 */
	private class LanguagePropertiesTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1418240627893557421L;

		@Override
		public int getRowCount() {
			return languageProperties == null ? 0 : languageProperties.size();
		}

		@Override
		public int getColumnCount() {
			if (languageProperties == null || availableLanguageSigns == null) {
				return COLUMN_FIRST_LANGUAGE;
			} else {
				return COLUMN_FIRST_LANGUAGE + availableLanguageSigns.size();
			}
		}

		@Override
		public String getColumnName(final int column) {
			switch (column) {
				case COLUMN_NR:
					return LangResources.get("columnheader_nr");
				case COLUMN_PATH:
					return LangResources.get("columnheader_path");
				case COLUMN_ORIGINAL_INDEX:
					return LangResources.get("columnheader_original_index");
				case COLUMN_KEY:
					return LangResources.get("columnheader_key");
				default:
					final String sign = availableLanguageSigns.get(column - COLUMN_FIRST_LANGUAGE);
					return LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT.equals(sign) ? LangResources.get("columnheader_default") : sign;
			}
		}

		@Override
		public Class<?> getColumnClass(final int column) {
			// Integer columns are right aligned by JTable's default renderer
			return column == COLUMN_NR || column == COLUMN_ORIGINAL_INDEX ? Integer.class : String.class;
		}

		@Override
		public boolean isCellEditable(final int row, final int column) {
			return false;
		}

		@Override
		public Object getValueAt(final int row, final int column) {
			final LanguageProperty languageProperty = languageProperties.get(row);
			switch (column) {
				case COLUMN_NR:
					return row + 1;
				case COLUMN_PATH:
					return languageProperty.getPath();
				case COLUMN_ORIGINAL_INDEX:
					return languageProperty.getOriginalIndex();
				case COLUMN_KEY:
					return languageProperty.getKey();
				default:
					final String value = languageProperty.getLanguageValue(availableLanguageSigns.get(column - COLUMN_FIRST_LANGUAGE));
					return Utilities.isEmpty(value) ? LangResources.get("value_not_found_sign") : LangResources.get("value_found_sign");
			}
		}
	}

	/**
	 * Redisplays the table content (e.g. after sorting or changing a property) and
	 * restores the selection of "currentSelectedProperties".
	 */
	private void refreshTable() {
		technicalSelectionChange = true;
		try {
			propertiesTableModel.fireTableDataChanged();
		} finally {
			technicalSelectionChange = false;
		}
		restoreSelection(currentSelectedProperties);
	}

	/**
	 * Selects the given properties (by identity) in the table and scrolls to the
	 * first one, without triggering the user selection handling.
	 */
	private void restoreSelection(final List<LanguageProperty> propertiesToSelect) {
		technicalSelectionChange = true;
		try {
			propertiesTable.clearSelection();
			if (languageProperties != null && propertiesToSelect != null) {
				int firstSelectedIndex = -1;
				for (final LanguageProperty property : propertiesToSelect) {
					final int index = indexOfIdentical(languageProperties, property);
					if (index >= 0) {
						propertiesTable.addRowSelectionInterval(index, index);
						if (firstSelectedIndex < 0 || index < firstSelectedIndex) {
							firstSelectedIndex = index;
						}
					}
				}
				if (firstSelectedIndex >= 0) {
					propertiesTable.scrollRectToVisible(propertiesTable.getCellRect(firstSelectedIndex, 0, true));
				}
			}
		} finally {
			technicalSelectionChange = false;
		}
	}

	private List<LanguageProperty> getSelectedProperties() {
		final List<LanguageProperty> returnList = new ArrayList<>();
		if (languageProperties != null) {
			for (final int selectedRow : propertiesTable.getSelectedRows()) {
				if (selectedRow < languageProperties.size()) {
					returnList.add(languageProperties.get(selectedRow));
				}
			}
		}
		return returnList;
	}

	private static int indexOfIdentical(final List<LanguageProperty> list, final LanguageProperty property) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == property) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isSameSelection(final List<LanguageProperty> selection1, final List<LanguageProperty> selection2) {
		if (selection1.size() != selection2.size()) {
			return false;
		}
		for (int i = 0; i < selection1.size(); i++) {
			if (selection1.get(i) != selection2.get(i)) {
				return false;
			}
		}
		return true;
	}

	private void selectSearch(final String text, int startIndex, final boolean searchUp, final boolean searchCaseInsensitive,
			final boolean searchInKeys, final boolean searchInValues, final boolean searchInPath) {
		if (Utilities.isNotEmpty(text) && languageProperties != null && !languageProperties.isEmpty() && (searchInKeys || searchInValues || searchInPath)) {
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
					final LanguageProperty foundProperty = languageProperties.get(currentIndex);
					if (currentSelectedProperties.size() == 1 && currentSelectedProperties.get(0) == foundProperty) {
						// Already selected, nothing to do
						return;
					}

					// Jumping to the search result must not silently drop unsaved edits of the detail view
					if (dataWasModified && !askForDiscardChanges()) {
						return;
					}

					currentSelectedProperties = new ArrayList<>();
					currentSelectedProperties.add(foundProperty);
					restoreSelection(currentSelectedProperties);
					refreshDetailView();
					return;
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

		showData(LangResources.get("usagereport"), reportText);
	}

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

	protected boolean dailyUpdateCheckIsPending() {
		return applicationConfiguration.getBoolean(ConfigurationProperties.CONFIG_KEY_DAILY_UPDATE_CHECK)
				&& (applicationConfiguration.getDate(ConfigurationProperties.CONFIG_KEY_NEXT_DAILY_UPDATE_CHECK) == null || applicationConfiguration.getDate(ConfigurationProperties.CONFIG_KEY_NEXT_DAILY_UPDATE_CHECK).isBefore(LocalDateTime.now()))
				&& NetworkUtilities.checkForNetworkConnection();
	}

	private void showError(final Exception exception) {
		new ErrorDialog(this, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, exception).open();
	}

	public void showData(final String title, final String text) {
		new ShowDataDialog(this, title, text).withResizable(true).open();
	}

	public void showMessage(final String title, final String text) {
		new QuestionDialog(this, title, text, LangResources.get("ok")).open();
	}

	public void showErrorMessage(final String title, final String text) {
		new QuestionDialog(this, title, text, LangResources.get("ok")).setBackgroundColor(SwingColor.LightRed).open();
	}

	/**
	 * DocumentListener that runs the same action for every text change.
	 */
	private static class SimpleDocumentListener implements DocumentListener {
		private final Runnable action;

		SimpleDocumentListener(final Runnable action) {
			this.action = action;
		}

		@Override
		public void insertUpdate(final DocumentEvent event) {
			action.run();
		}

		@Override
		public void removeUpdate(final DocumentEvent event) {
			action.run();
		}

		@Override
		public void changedUpdate(final DocumentEvent event) {
			// Attribute changes only, no text change
		}
	}
}
