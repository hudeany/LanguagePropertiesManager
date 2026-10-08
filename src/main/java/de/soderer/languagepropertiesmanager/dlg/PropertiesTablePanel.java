package de.soderer.languagepropertiesmanager.dlg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import de.soderer.languagepropertiesmanager.image.ImageManager;
import de.soderer.languagepropertiesmanager.model.LanguagePropertiesModel;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.csv.CsvFormat;
import de.soderer.utilities.csv.CsvWriter;

/**
 * Left part of the main window below the buttons: the search box and the
 * properties table with sorting, search filter and context menu.
 *
 * The table shows the properties of the LanguagePropertiesModel and keeps the
 * model's current selection in sync with the table selection. Everything that
 * changes data or needs the detail view is delegated to the Callback.
 */
public class PropertiesTablePanel extends JPanel {
	private static final long serialVersionUID = 6230791645807236419L;

	/**
	 * Actions of the main window triggered by the table
	 */
	public interface Callback {
		/**
		 * Asks the user, if the detail view has unapplied changes, which would get lost.
		 *
		 * @return true if the detail view has no unapplied changes or the user agreed to discard them
		 */
		boolean confirmDiscardDetailChanges();

		/** The model's current selection was changed by the user (click or search) */
		void currentSelectionChanged();

		/** Row count or filter of the table changed, so the enabled state of buttons may change */
		void tableStateChanged();

		/** Deletes the selected properties after asking the user (DEL key) */
		void removeSelectedProperties();

		/** Deletes the comments of all properties after asking the user (context menu of the comment column) */
		void deleteAllComments();

		/** Deletes the paths of all properties after asking the user (context menu of the path column) */
		void deleteAllPaths();

		/**
		 * Deletes the values of one language of all properties after asking the user (context menu of a language column)
		 *
		 * @param languageSign
		 *            the language whose values are deleted
		 * @param languageDisplayName
		 *            the name of the language as shown in the column header
		 */
		void deleteAllLanguageValues(String languageSign, String languageDisplayName);

		/**
		 * Sets the value of one language of the selected properties to an explicitly empty or a missing value
		 *
		 * @param languageSign
		 *            the language whose values are set
		 * @param languageDisplayName
		 *            the name of the language as shown in the column header
		 * @param newValue
		 *            "" for an explicitly empty value, null for a missing value
		 */
		void setSelectedLanguageValues(String languageSign, String languageDisplayName, String newValue);

		/**
		 * Deletes a language from all properties (context menu of a language column)
		 *
		 * @param languageSign
		 *            the language to delete
		 */
		void deleteLanguage(String languageSign);

		/**
		 * Shows an unexpected error with its details.
		 *
		 * @param exception
		 *            the error
		 */
		void showError(Exception exception);
	}

	/*
	 * Fixed model columns of the properties table. The language columns follow
	 * after COLUMN_FIRST_LANGUAGE in the order of the model's available language signs.
	 * An optional comment column (see "commentColumnShown") follows after the
	 * last language column.
	 * (The SWT variant needed an invisible dummy first column as a workaround
	 * for a Windows alignment bug, which JTable does not have.)
	 */
	private static final int COLUMN_NR = 0;
	private static final int COLUMN_PATH = 1;
	private static final int COLUMN_ORIGINAL_INDEX = 2;
	private static final int COLUMN_KEY = 3;
	private static final int COLUMN_FIRST_LANGUAGE = 4;

	/** Loaded data and the current selection */
	private final LanguagePropertiesModel model;
	/** Actions of the main window */
	private final Callback callback;

	/** The properties table */
	private final JTable propertiesTable;
	/** Table model backed by "displayedProperties" */
	private final LanguagePropertiesTableModel propertiesTableModel;
	/** Shows which properties the bulk actions affect */
	private final ActionScopeStatusBar actionScopeStatusBar;

	/** Suppresses the user selection handling while the table selection is changed programmatically */
	private boolean technicalSelectionChange = false;

	/** Model index of the column the table is sorted by */
	private int sortColumnModelIndex = COLUMN_NR;
	/** Sort direction of the sort column */
	private boolean sortAscending = true;

	/**
	 * Whether the table currently shows the comment column (after the language
	 * columns). Only changed in setupColumns(), so the table model stays consistent.
	 */
	private boolean commentColumnShown = false;

	/** Current search text, null if the search field is empty */
	private String searchText;
	/** Whether the search ignores the case */
	private boolean searchCaseInsensitivePreference = true;
	/** Whether the search includes the keys */
	private boolean searchInKeysPreference = true;
	/** Whether the search includes the language values */
	private boolean searchInValuesPreference = false;
	/** Whether the search includes the paths */
	private boolean searchInPathPreference = false;
	/** Whether the table only shows the search hits */
	private boolean searchFilterPreference = false;
	/** Search field, buttons and checkboxes, which are enabled together with the table */
	private final List<JComponent> searchComponents = new ArrayList<>();

	/**
	 * Properties currently shown in the table: all loaded properties, or only the search hits if the search filter is active.
	 * Only changed in updateDisplayedProperties(), all table row indexes refer to this list.
	 */
	private List<LanguageProperty> displayedProperties = new ArrayList<>();

	/**
	 * Creates the search box and the properties table.
	 *
	 * @param model
	 *            loaded data and current selection
	 * @param callback
	 *            actions of the main window
	 * @throws Exception
	 *             if an icon cannot be loaded
	 */
	public PropertiesTablePanel(final LanguagePropertiesModel model, final Callback callback) throws Exception {
		super(new BorderLayout(0, 3));
		this.model = model;
		this.callback = callback;

		add(createSearchBox(), BorderLayout.NORTH);

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
					callback.removeSelectedProperties();
				}
			}
		});

		// ESC clears the selection, so bulk actions apply to all displayed properties again.
		// The cleared selection runs through handleUserSelectionChange(), which asks for unsaved detail changes.
		propertiesTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSelectionByUser");
		propertiesTable.getActionMap().put("clearSelectionByUser", new AbstractAction() {
			private static final long serialVersionUID = 2914563870215436178L;

			@Override
			public void actionPerformed(final ActionEvent event) {
				if (propertiesTable.isEditing()) {
					// Keep the standard behavior of ESC while a cell is edited
					propertiesTable.getCellEditor().cancelCellEditing();
				} else if (propertiesTable.getSelectedRowCount() > 0) {
					propertiesTable.clearSelection();
				}
			}
		});

		installSortableHeader(propertiesTable.getTableHeader());
		installTableContextMenu();

		final JScrollPane propertiesTableScrollPane = new JScrollPane(propertiesTable);
		// Area right of the last column (AUTO_RESIZE_OFF) shows the viewport background
		propertiesTableScrollPane.getViewport().setBackground(Color.WHITE);
		add(propertiesTableScrollPane, BorderLayout.CENTER);

		// Shows which properties the bulk actions affect (selection / search filter)
		actionScopeStatusBar = new ActionScopeStatusBar(propertiesTable,
				() -> model.isLoaded() ? model.getLanguageProperties().size() : 0,
				this::isSearchFilterActive);
		add(actionScopeStatusBar, BorderLayout.SOUTH);
	}

	/**
	 * Recreates the table columns after the set of properties or languages
	 * changed. The sort order is reset to the original order.
	 *
	 * @param commentColumnWanted
	 *            whether the comment column is shown
	 */
	public void setupColumns(final boolean commentColumnWanted) {
		sortColumnModelIndex = COLUMN_NR;
		sortAscending = true;
		commentColumnShown = commentColumnWanted;

		updateDisplayedProperties(true);
		technicalSelectionChange = true;
		try {
			propertiesTableModel.fireTableStructureChanged();
			applyColumnLayout();
		} finally {
			technicalSelectionChange = false;
		}
	}

	/**
	 * Redisplays the table content (e.g. after sorting or changing a property) and
	 * restores the current selection of the model.
	 */
	public void refresh() {
		updateDisplayedProperties(true);
		technicalSelectionChange = true;
		try {
			propertiesTableModel.fireTableDataChanged();
		} finally {
			technicalSelectionChange = false;
		}
		restoreSelection(model.getCurrentSelection());
	}

	/**
	 * Clears the table selection without triggering the user selection handling
	 */
	public void clearSelection() {
		technicalSelectionChange = true;
		try {
			propertiesTable.clearSelection();
		} finally {
			technicalSelectionChange = false;
		}
	}

	/**
	 * Number of selected table rows.
	 *
	 * @return number of selected rows
	 */
	public int getSelectedRowCount() {
		return propertiesTable.getSelectedRowCount();
	}

	/**
	 * Properties currently shown in the table (all, or only the search hits with active search filter)
	 *
	 * @return unmodifiable list of the displayed properties
	 */
	public List<LanguageProperty> getDisplayedProperties() {
		return Collections.unmodifiableList(displayedProperties);
	}

	/**
	 * Number of displayed table rows.
	 *
	 * @return number of rows
	 */
	public int getRowCount() {
		return propertiesTableModel.getRowCount();
	}

	/**
	 * Whether the table currently shows the comment column.
	 *
	 * @return true if the comment column is shown
	 */
	public boolean isCommentColumnShown() {
		return commentColumnShown;
	}

	/**
	 * Enables or disables the table and the search components
	 *
	 * @param enabled
	 *            true to enable
	 */
	public void setInteractionEnabled(final boolean enabled) {
		propertiesTable.setEnabled(enabled);
		for (final JComponent searchComponent : searchComponents) {
			searchComponent.setEnabled(enabled);
		}
	}

	/**
	 * Called (deferred) after the user changed the table selection.
	 */
	private void handleUserSelectionChange() {
		final List<LanguageProperty> newSelection = getSelectedProperties();
		if (isSameSelection(newSelection, model.getCurrentSelection())) {
			// Nothing changed (e.g. a second event for the same user action)
			return;
		}

		if (callback.confirmDiscardDetailChanges()) {
			// Take over the new selection
			model.setCurrentSelection(newSelection);
			callback.currentSelectionChanged();
		} else {
			// Reselect the old entries
			restoreSelection(model.getCurrentSelection());
		}

		callback.tableStateChanged();
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
			if (Utilities.isNotEmpty(text) && !text.equals(LangResources.get("search"))) {
				searchText = text;
			} else {
				// An empty search field must also remove the search filter
				searchText = null;
			}
			searchParametersChanged();
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
		constraints.gridx++;
		searchBox.add(createSearchCheckBox(LangResources.get("search_filter"), LangResources.get("search_filter_tooltip"), searchFilterPreference, selected -> searchFilterPreference = selected), constraints);

		return searchBox;
	}

	private JCheckBox createSearchCheckBox(final String text, final String toolTipText, final boolean initialSelection, final Consumer<Boolean> preferenceSetter) {
		final JCheckBox checkBox = new JCheckBox(text, initialSelection);
		checkBox.setToolTipText(toolTipText);
		checkBox.addActionListener(e -> {
			preferenceSetter.accept(checkBox.isSelected());
			searchParametersChanged();
		});
		searchComponents.add(checkBox);
		return checkBox;
	}

	/**
	 * Called after the search text or one of the search options changed:
	 * Updates the filtered table content (if the filter is or was active) and jumps to the next hit.
	 */
	private void searchParametersChanged() {
		if (!model.isLoaded()) {
			return;
		}

		// Refilter only if needed, so typing without active filter does not redraw the whole table
		if (searchFilterPreference || displayedProperties != model.getLanguageProperties()) {
			applySearchFilter();
		}

		if (Utilities.isNotEmpty(searchText)) {
			searchFromCurrentSelection();
		}
	}

	private boolean isSearchFilterActive() {
		return searchFilterPreference && Utilities.isNotEmpty(searchText) && (searchInKeysPreference || searchInValuesPreference || searchInPathPreference);
	}

	/**
	 * Recalculates the properties shown in the table.
	 *
	 * @param keepSelectedVisible
	 *            Keep the currently selected properties visible even if they do not match the search anymore
	 *            (e.g. after editing a value), so rows do not vanish while the user works on them
	 */
	private void updateDisplayedProperties(final boolean keepSelectedVisible) {
		if (!model.isLoaded()) {
			displayedProperties = new ArrayList<>();
		} else if (!isSearchFilterActive()) {
			displayedProperties = model.getLanguageProperties();
		} else {
			final List<LanguageProperty> filteredProperties = new ArrayList<>();
			for (final LanguageProperty languageProperty : model.getLanguageProperties()) {
				if (matchesSearch(languageProperty, searchText, searchCaseInsensitivePreference, searchInKeysPreference, searchInValuesPreference, searchInPathPreference)
						|| (keepSelectedVisible && indexOfIdentical(model.getCurrentSelection(), languageProperty) >= 0)) {
					filteredProperties.add(languageProperty);
				}
			}
			displayedProperties = filteredProperties;
		}
	}

	/**
	 * Filters the table by the current search parameters, previously selected rows that do not match are hidden
	 */
	private void applySearchFilter() {
		updateDisplayedProperties(false);
		technicalSelectionChange = true;
		try {
			propertiesTableModel.fireTableDataChanged();
		} finally {
			technicalSelectionChange = false;
		}
		restoreSelection(model.getCurrentSelection());
		callback.tableStateChanged();
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
				if (!javax.swing.SwingUtilities.isLeftMouseButton(event) || !model.isLoaded()) {
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

	/**
	 * Opens a context menu on right click on the table and its header:
	 * on content rows it offers copying the selected rows as csv, additionally
	 * the path column, a language column or the comment column offer their own
	 * actions, both on the header and on the cells.
	 */
	private void installTableContextMenu() throws Exception {
		// Loaded once here, because ImageManager.getImage() throws a checked exception
		final Icon deleteIcon = ImageManager.getImage("minus.png");

		final MouseAdapter contextMenuListener = new MouseAdapter() {
			// The popup trigger is "pressed" on Linux/macOS and "released" on Windows
			@Override
			public void mousePressed(final MouseEvent event) {
				showTableContextMenu(event, deleteIcon);
			}

			@Override
			public void mouseReleased(final MouseEvent event) {
				showTableContextMenu(event, deleteIcon);
			}
		};
		propertiesTable.addMouseListener(contextMenuListener);
		propertiesTable.getTableHeader().addMouseListener(contextMenuListener);
	}

	private void showTableContextMenu(final MouseEvent event, final Icon deleteIcon) {
		if (!event.isPopupTrigger() || !model.isLoaded() || model.getAvailableLanguageSigns() == null) {
			return;
		}

		final JPopupMenu contextMenu = new JPopupMenu();

		// Content rows (not the header): copy the selected rows
		if (event.getComponent() == propertiesTable) {
			final int row = propertiesTable.rowAtPoint(event.getPoint());
			if (row >= 0) {
				// A right click outside of the current selection selects the clicked row, like in common table applications
				if (!propertiesTable.isRowSelected(row)) {
					propertiesTable.setRowSelectionInterval(row, row);
				}
				final JMenuItem copyAsCsvItem = new JMenuItem(LangResources.get("contextmenu_copySelectedRowsAsCsv", propertiesTable.getSelectedRowCount()));
				copyAsCsvItem.addActionListener(e -> copySelectedPropertiesAsCsvToClipboard());
				contextMenu.add(copyAsCsvItem);
			}
		}

		// Table and header share the same x coordinates, so this works for both components
		final int viewColumn = propertiesTable.getColumnModel().getColumnIndexAtX(event.getX());
		if (viewColumn >= 0) {
			final int modelColumn = propertiesTable.convertColumnIndexToModel(viewColumn);
			if (isCommentColumn(modelColumn)) {
				addSeparatorIfNotEmpty(contextMenu);
				final JMenuItem deleteCommentsItem = new JMenuItem(LangResources.get("contextmenu_deleteComments"));
				deleteCommentsItem.addActionListener(e -> callback.deleteAllComments());
				contextMenu.add(deleteCommentsItem);
			} else if (modelColumn == COLUMN_PATH) {
				addSeparatorIfNotEmpty(contextMenu);
				final JMenuItem deletePathsItem = new JMenuItem(LangResources.get("contextmenu_deletePaths"));
				deletePathsItem.addActionListener(e -> callback.deleteAllPaths());
				contextMenu.add(deletePathsItem);
			} else if (modelColumn >= COLUMN_FIRST_LANGUAGE && modelColumn - COLUMN_FIRST_LANGUAGE < model.getAvailableLanguageSigns().size()) {
				final String languageSign = model.getAvailableLanguageSigns().get(modelColumn - COLUMN_FIRST_LANGUAGE);
				final String languageColumnName = propertiesTableModel.getColumnName(modelColumn);

				addSeparatorIfNotEmpty(contextMenu);

				// Content rows only: the detail view cannot distinguish between "missing" and "explicitly empty", so it is set here
				if (event.getComponent() == propertiesTable && propertiesTable.getSelectedRowCount() > 0) {
					final int selectedRowCount = propertiesTable.getSelectedRowCount();

					final JMenuItem setValuesEmptyItem = new JMenuItem(LangResources.get("contextmenu_setValuesEmpty", selectedRowCount) + ": " + languageColumnName);
					setValuesEmptyItem.addActionListener(e -> callback.setSelectedLanguageValues(languageSign, languageColumnName, ""));
					contextMenu.add(setValuesEmptyItem);

					final JMenuItem setValuesMissingItem = new JMenuItem(LangResources.get("contextmenu_setValuesMissing", selectedRowCount) + ": " + languageColumnName);
					setValuesMissingItem.addActionListener(e -> callback.setSelectedLanguageValues(languageSign, languageColumnName, null));
					contextMenu.add(setValuesMissingItem);

					contextMenu.addSeparator();
				}

				// Only clears the values, the language itself (and its column) stays available
				final JMenuItem deleteLanguageValuesItem = new JMenuItem(LangResources.get("contextmenu_deleteLanguageValues") + ": " + languageColumnName);
				deleteLanguageValuesItem.addActionListener(e -> callback.deleteAllLanguageValues(languageSign, languageColumnName));
				contextMenu.add(deleteLanguageValuesItem);

				final JMenuItem deleteLanguageItem = new JMenuItem(LangResources.get("tooltip_DeleteLanguage") + ": " + languageColumnName, deleteIcon);
				// Same rule as for the delete language button: The last language cannot be deleted
				deleteLanguageItem.setEnabled(model.getAvailableLanguageSigns().size() > 1);
				deleteLanguageItem.addActionListener(e -> callback.deleteLanguage(languageSign));
				contextMenu.add(deleteLanguageItem);
			}
		}

		if (contextMenu.getComponentCount() > 0) {
			contextMenu.show(event.getComponent(), event.getX(), event.getY());
		}
	}

	private static void addSeparatorIfNotEmpty(final JPopupMenu contextMenu) {
		if (contextMenu.getComponentCount() > 0) {
			contextMenu.addSeparator();
		}
	}

	/**
	 * Copies the selected rows in display order as csv into the system clipboard.
	 * Unlike the table, the csv contains the full values of languages and comments,
	 * with the same columns as currently shown (without the row number).
	 */
	private void copySelectedPropertiesAsCsvToClipboard() {
		try {
			final List<LanguageProperty> selectedProperties = getSelectedProperties();
			if (selectedProperties.isEmpty()) {
				return;
			}

			// Line breaks within values are kept and quoted (RFC 4180), so spreadsheet applications read them as one cell
			final CsvFormat csvFormat = new CsvFormat()
					.withSeparator(';')
					.withStringQuote('"')
					.withEscapeLineBreaks(false);

			final int columnCount = propertiesTableModel.getColumnCount();
			final StringBuilder csvText = new StringBuilder();

			final List<String> headerValues = new ArrayList<>();
			for (int modelColumn = COLUMN_PATH; modelColumn < columnCount; modelColumn++) {
				headerValues.add(propertiesTableModel.getColumnName(modelColumn));
			}
			csvText.append(CsvWriter.getCsvLine(csvFormat, headerValues)).append("\n");

			for (final LanguageProperty languageProperty : selectedProperties) {
				final List<String> rowValues = new ArrayList<>();
				for (int modelColumn = COLUMN_PATH; modelColumn < columnCount; modelColumn++) {
					rowValues.add(getEmptyForNull(getCsvValue(languageProperty, modelColumn)));
				}
				csvText.append(CsvWriter.getCsvLine(csvFormat, rowValues)).append("\n");
			}

			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(csvText.toString()), null);
		} catch (final Exception e) {
			callback.showError(e);
		}
	}

	/**
	 * Full value of a property for a model column, in contrast to the table model, which only shows whether a value exists
	 */
	private String getCsvValue(final LanguageProperty languageProperty, final int modelColumn) {
		switch (modelColumn) {
			case COLUMN_PATH:
				return languageProperty.getPath();
			case COLUMN_ORIGINAL_INDEX:
				return String.valueOf(languageProperty.getOriginalIndex());
			case COLUMN_KEY:
				return languageProperty.getKey();
			default:
				if (isCommentColumn(modelColumn)) {
					return languageProperty.getComment();
				} else {
					return languageProperty.getLanguageValue(model.getAvailableLanguageSigns().get(modelColumn - COLUMN_FIRST_LANGUAGE));
				}
		}
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
		// Paths and keys may be null (e.g. imported data), which must not break the sorting
		if (modelColumn == COLUMN_KEY) {
			comparator = Comparator.comparing((final LanguageProperty languageProperty) -> getEmptyForNull(languageProperty.getPath())).thenComparing(languageProperty -> getEmptyForNull(languageProperty.getKey()));
		} else if (modelColumn == COLUMN_ORIGINAL_INDEX || modelColumn == COLUMN_PATH) {
			comparator = Comparator.comparing((final LanguageProperty languageProperty) -> getEmptyForNull(languageProperty.getPath())).thenComparing(LanguageProperty::getOriginalIndex);
		} else if (isCommentColumn(modelColumn)) {
			comparator = Comparator.comparing(languageProperty -> getEmptyForNull(languageProperty.getComment()));
		} else {
			final String languageSign = model.getAvailableLanguageSigns().get(modelColumn - COLUMN_FIRST_LANGUAGE);
			comparator = Comparator.comparing((final LanguageProperty languageProperty) -> getValueStateSortOrder(languageProperty.getLanguageValue(languageSign)))
					.thenComparing(languageProperty -> getEmptyForNull(languageProperty.getLanguageValue(languageSign)));
		}
		if (!sortAscending) {
			comparator = comparator.reversed();
		}

		model.sortProperties(comparator);

		refresh();
		propertiesTable.getTableHeader().repaint();
	}

	private void applyColumnLayout() {
		final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

		final String valueNotFoundSign = LangResources.get("value_not_found_sign");
		final String valueNotFoundTooltip = LangResources.get("value_not_found_tooltip");
		final DefaultTableCellRenderer languageValueRenderer = new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
				final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				// The renderer component is reused for all cells, so the tooltip must be reset for every cell
				setToolTipText(valueNotFoundSign.equals(value) ? valueNotFoundTooltip : null);
				return component;
			}
		};
		languageValueRenderer.setHorizontalAlignment(SwingConstants.CENTER);

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
					if (isCommentColumn(modelIndex)) {
						// Same layout as the language columns
						column.setPreferredWidth(Math.max(25, getSortableHeaderWidth(modelIndex)));
						column.setCellRenderer(centerRenderer);
						break;
					}
					final String sign = model.getAvailableLanguageSigns().get(modelIndex - COLUMN_FIRST_LANGUAGE);
					column.setPreferredWidth(Math.max(sign.length() > 3 ? 50 : 25, getSortableHeaderWidth(modelIndex)));
					column.setCellRenderer(languageValueRenderer);
					break;
			}
		}
	}

	/**
	 * Header width needed so the column name stays fully visible even when the
	 * column is the sort column and the header additionally shows the sort icon.
	 */
	private int getSortableHeaderWidth(final int modelIndex) {
		final int textWidth = propertiesTable.getFontMetrics(propertiesTable.getTableHeader().getFont()).stringWidth(propertiesTableModel.getColumnName(modelIndex));
		int sortIconWidth = 0;
		for (final String iconKey : new String[] { "Table.ascendingSortIcon", "Table.descendingSortIcon" }) {
			final Icon sortIcon = UIManager.getIcon(iconKey);
			if (sortIcon != null) {
				sortIconWidth = Math.max(sortIconWidth, sortIcon.getIconWidth());
			}
		}
		if (sortIconWidth == 0) {
			// Fallback for look and feels without sort icons
			sortIconWidth = 8;
		}
		// Text + gap between text and icon (JLabel default iconTextGap) + icon + cell padding and header border
		return textWidth + new JLabel().getIconTextGap() + sortIconWidth + 16;
	}

	/**
	 * Model index of the comment column, which follows after the language columns
	 */
	private boolean isCommentColumn(final int modelColumn) {
		return commentColumnShown && model.getAvailableLanguageSigns() != null && modelColumn == COLUMN_FIRST_LANGUAGE + model.getAvailableLanguageSigns().size();
	}

	/**
	 * Selects the given properties (by identity) in the table and scrolls to the
	 * first one, without triggering the user selection handling.
	 *
	 * @param propertiesToSelect
	 *            properties to select, null for none
	 */
	public void restoreSelection(final List<LanguageProperty> propertiesToSelect) {
		technicalSelectionChange = true;
		try {
			propertiesTable.clearSelection();
			if (model.isLoaded() && propertiesToSelect != null) {
				int firstSelectedIndex = -1;
				for (final LanguageProperty property : propertiesToSelect) {
					final int index = indexOfIdentical(displayedProperties, property);
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

	/**
	 * Properties of the selected table rows in display order.
	 *
	 * @return the selected properties, empty if nothing is selected
	 */
	public List<LanguageProperty> getSelectedProperties() {
		final List<LanguageProperty> returnList = new ArrayList<>();
		if (model.isLoaded()) {
			for (final int selectedRow : propertiesTable.getSelectedRows()) {
				if (selectedRow < displayedProperties.size()) {
					returnList.add(displayedProperties.get(selectedRow));
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
		if (Utilities.isNotEmpty(text) && model.isLoaded() && !displayedProperties.isEmpty() && (searchInKeys || searchInValues || searchInPath)) {
			if (startIndex < 0) {
				startIndex = displayedProperties.size() - 1;
			} else if (startIndex >= displayedProperties.size()) {
				startIndex = 0;
			}

			int currentIndex = -1;
			while (currentIndex != startIndex) {
				if (currentIndex == -1) {
					currentIndex = startIndex;
				}

				if (matchesSearch(displayedProperties.get(currentIndex), text, searchCaseInsensitive, searchInKeys, searchInValues, searchInPath)) {
					final LanguageProperty foundProperty = displayedProperties.get(currentIndex);
					if (model.getCurrentSelection().size() == 1 && model.getFirstSelectedProperty() == foundProperty) {
						// Already selected, nothing to do
						return;
					}

					// Jumping to the search result must not silently drop unsaved edits of the detail view
					if (!callback.confirmDiscardDetailChanges()) {
						return;
					}

					model.setCurrentSelection(Collections.singletonList(foundProperty));
					restoreSelection(model.getCurrentSelection());
					callback.currentSelectionChanged();
					return;
				}

				if (searchUp) {
					currentIndex++;
				} else {
					currentIndex--;
				}

				if (currentIndex < 0) {
					currentIndex = displayedProperties.size() - 1;
				} else if (currentIndex >= displayedProperties.size()) {
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

	/**
	 * Sort order of the state of a language value:
	 * missing values (null) first, then explicitly empty values (""), then real values.
	 */
	private static int getValueStateSortOrder(final String value) {
		if (value == null) {
			return 0;
		} else if (value.isEmpty()) {
			return 1;
		} else {
			return 2;
		}
	}

	private static String getEmptyForNull(final String string) {
		return string == null ? "" : string;
	}

	/**
	 * Table model backed by "displayedProperties". The language columns
	 * only show whether a value exists.
	 */
	private class LanguagePropertiesTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1418240627893557421L;

		@Override
		public int getRowCount() {
			return displayedProperties == null ? 0 : displayedProperties.size();
		}

		@Override
		public int getColumnCount() {
			if (!model.isLoaded() || model.getAvailableLanguageSigns() == null) {
				return COLUMN_FIRST_LANGUAGE;
			} else {
				return COLUMN_FIRST_LANGUAGE + model.getAvailableLanguageSigns().size() + (commentColumnShown ? 1 : 0);
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
					if (isCommentColumn(column)) {
						return LangResources.get("comment");
					}
					final String sign = model.getAvailableLanguageSigns().get(column - COLUMN_FIRST_LANGUAGE);
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
			final LanguageProperty languageProperty = displayedProperties.get(row);
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
					if (isCommentColumn(column)) {
						// Only show whether a comment exists, a missing comment is shown as an empty cell
						return Utilities.isEmpty(languageProperty.getComment()) ? "" : LangResources.get("value_found_sign");
					}
					final String value = languageProperty.getLanguageValue(model.getAvailableLanguageSigns().get(column - COLUMN_FIRST_LANGUAGE));
					if (value == null) {
						// Key is missing in this language file, ResourceBundle falls back to the default value
						return LangResources.get("value_not_found_sign");
					} else if (value.isEmpty()) {
						// Key exists with an explicitly empty value ("key=")
						if (Utilities.isBlank(LangResources.get("value_empty_sign"))) {
							return "";
						} else {
							return LangResources.get("value_empty_sign");
						}
					} else {
						return LangResources.get("value_found_sign");
					}
			}
		}
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
