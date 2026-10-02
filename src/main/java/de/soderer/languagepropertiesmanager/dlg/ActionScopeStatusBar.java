package de.soderer.languagepropertiesmanager.dlg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelListener;

import de.soderer.utilities.LangResources;

/**
 * Status bar shown below the properties table.
 * Displays how many rows are selected and whether the search filter restricts the visible rows,
 * so the user always knows which properties the bulk actions (translate, transfer, clear identical,
 * reduce by base set) will affect.
 */
public class ActionScopeStatusBar extends JPanel {
	private static final long serialVersionUID = 4172369027834617250L;

	private static final Color HIGHLIGHT_COLOR = new Color(0xB0, 0x5A, 0x00);

	private final JTable table;
	private final IntSupplier totalCountSupplier;
	private final BooleanSupplier filterActiveSupplier;

	private final JLabel scopeLabel = new JLabel(" ");
	private final JLabel filterLabel = new JLabel(" ");

	private final Color defaultForeground;
	private final Font defaultFont;
	private final Font boldFont;

	/** Several table events for one user action are combined into a single refresh */
	private boolean refreshPending = false;

	private final ListSelectionListener selectionListener = e -> {
		if (!e.getValueIsAdjusting()) {
			scheduleRefresh();
		}
	};
	private final TableModelListener tableModelListener = e -> scheduleRefresh();

	/**
	 * @param table
	 *            the table whose selection and visible rows are observed
	 * @param totalCountSupplier
	 *            number of all loaded properties, independent of the search filter
	 * @param filterActiveSupplier
	 *            true if the search filter currently restricts the visible rows
	 */
	public ActionScopeStatusBar(final JTable table, final IntSupplier totalCountSupplier, final BooleanSupplier filterActiveSupplier) {
		super(new BorderLayout(10, 0));
		this.table = table;
		this.totalCountSupplier = totalCountSupplier;
		this.filterActiveSupplier = filterActiveSupplier;

		Color separatorColor = UIManager.getColor("Separator.foreground");
		if (separatorColor == null) {
			separatorColor = Color.GRAY;
		}
		setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, separatorColor),
				BorderFactory.createEmptyBorder(2, 4, 2, 4)));

		defaultForeground = scopeLabel.getForeground();
		defaultFont = scopeLabel.getFont();
		boldFont = defaultFont.deriveFont(Font.BOLD);

		add(scopeLabel, BorderLayout.WEST);
		add(filterLabel, BorderLayout.EAST);

		attachListeners();

		// Re-attach when the table gets a new model or selection model
		final PropertyChangeListener reattachListener = e -> {
			attachListeners();
			scheduleRefresh();
		};
		table.addPropertyChangeListener("model", reattachListener);
		table.addPropertyChangeListener("selectionModel", reattachListener);

		refresh();
	}

	private void attachListeners() {
		table.getSelectionModel().removeListSelectionListener(selectionListener);
		table.getSelectionModel().addListSelectionListener(selectionListener);

		table.getModel().removeTableModelListener(tableModelListener);
		table.getModel().addTableModelListener(tableModelListener);
	}

	/**
	 * Refreshes deferred, after the table has processed all events of the current action.
	 * Table model listeners are notified before the JTable itself, so an immediate refresh
	 * could read an outdated selection.
	 */
	public void scheduleRefresh() {
		if (!refreshPending) {
			refreshPending = true;
			SwingUtilities.invokeLater(() -> {
				refreshPending = false;
				refresh();
			});
		}
	}

	/**
	 * Updates the displayed texts immediately. Must be called on the EDT.
	 */
	public void refresh() {
		final int totalCount = totalCountSupplier.getAsInt();
		final int visibleCount = table.getRowCount();
		final int selectedCount = table.getSelectedRowCount();
		final boolean filterActive = filterActiveSupplier.getAsBoolean();

		if (totalCount == 0 && visibleCount == 0) {
			// Nothing loaded
			scopeLabel.setText(" ");
			filterLabel.setText(" ");
			applyHighlight(scopeLabel, false);
			applyHighlight(filterLabel, false);
			return;
		}

		final boolean partialSelection = selectedCount > 0 && selectedCount < visibleCount;

		if (partialSelection) {
			scopeLabel.setText(LangResources.get("actionScope_selected", selectedCount, visibleCount));
		} else if (filterActive) {
			scopeLabel.setText(LangResources.get("actionScope_allDisplayed", visibleCount));
		} else {
			scopeLabel.setText(LangResources.get("actionScope_all", totalCount));
		}

		if (filterActive) {
			filterLabel.setText(LangResources.get("actionScope_filterActive", visibleCount, totalCount));
		} else {
			filterLabel.setText(" ");
		}

		// Highlight states in which actions do not affect all properties
		applyHighlight(scopeLabel, partialSelection);
		applyHighlight(filterLabel, filterActive);
	}

	private void applyHighlight(final JLabel label, final boolean highlight) {
		label.setForeground(highlight ? HIGHLIGHT_COLOR : defaultForeground);
		label.setFont(highlight ? boldFont : defaultFont);
	}
}