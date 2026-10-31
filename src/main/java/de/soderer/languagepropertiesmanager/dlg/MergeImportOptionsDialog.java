package de.soderer.languagepropertiesmanager.dlg;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import de.soderer.languagepropertiesmanager.merge.MergeOptions;
import de.soderer.languagepropertiesmanager.merge.MergePlan;
import de.soderer.swing.modern.Button;
import de.soderer.swing.modern.ButtonStyle;
import de.soderer.swing.modern.ModalDialog;
import de.soderer.utilities.LangResources;

/**
 * Lets the user choose the options of a merge import with independent checkboxes.
 * Every checkbox shows how many properties or values it affects, and a summary
 * below shows the resulting effect of the current choice before anything is changed.
 *
 * <p>
 * {@link #open()} returns the chosen options, or null if the user cancelled.
 * </p>
 */
public class MergeImportOptionsDialog extends ModalDialog<MergeOptions> {
	private static final long serialVersionUID = 6214790534127745312L;

	/** Text color of summary lines, whose effect loses existing data */
	private static final Color DATA_LOSS_COLOR = new Color(0xB0, 0x00, 0x20);

	private final MergePlan mergePlan;

	private final JCheckBox addNewPropertiesCheckBox;
	private final JCheckBox fillEmptyValuesCheckBox;
	private final JCheckBox overwriteDifferingValuesCheckBox;

	/** Shows the effect of the current choice, rebuilt on every change of a checkbox */
	private final JPanel summaryPanel;
	private final Button importButton;

	/**
	 * Creates the dialog. Defaults: add new properties and fill empty values, keep differing values.
	 *
	 * @param owner
	 *            owner window
	 * @param title
	 *            title of the dialog
	 * @param sourceDescription
	 *            file or directory of the import source, only for display
	 * @param mergePlan
	 *            plan of the merge import, provides the counts
	 */
	public MergeImportOptionsDialog(final Window owner, final String title, final String sourceDescription, final MergePlan mergePlan) {
		super(owner, title);
		this.mergePlan = mergePlan;

		setResizable(false);

		final JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.PAGE_AXIS));
		contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

		addLeftAligned(contentPanel, createTextArea(LangResources.get("mergeOptions_intro", sourceDescription, mergePlan.getMatchedPropertyCount(), mergePlan.getSkippedEntries().size())));
		contentPanel.add(Box.createVerticalStrut(10));

		addNewPropertiesCheckBox = createOptionCheckBox(contentPanel,
				LangResources.get("mergeOptions_addNew", mergePlan.getNewPropertyCount()),
				LangResources.get("mergeOptions_addNew_description"),
				mergePlan.getNewPropertyCount() > 0, true);
		fillEmptyValuesCheckBox = createOptionCheckBox(contentPanel,
				LangResources.get("mergeOptions_fillEmpty", mergePlan.getFillableValueCount()),
				LangResources.get("mergeOptions_fillEmpty_description"),
				mergePlan.getFillableValueCount() > 0, true);
		overwriteDifferingValuesCheckBox = createOptionCheckBox(contentPanel,
				LangResources.get("mergeOptions_overwrite", mergePlan.getDifferingValueCount(), mergePlan.getPropertiesWithDifferencesCount()),
				LangResources.get("mergeOptions_overwrite_description"),
				mergePlan.getDifferingValueCount() > 0, false);

		contentPanel.add(Box.createVerticalStrut(5));

		summaryPanel = new JPanel();
		summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.PAGE_AXIS));
		summaryPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder(LangResources.get("mergeOptions_summary_title")),
				BorderFactory.createEmptyBorder(3, 5, 5, 5)));
		addLeftAligned(contentPanel, summaryPanel);

		final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		importButton = new Button(LangResources.get("mergeOptions_import"), null, ButtonStyle.SECONDARY);
		importButton.addActionListener(event -> {
			returnValue = getSelectedOptions();
			dispose();
		});
		buttonPanel.add(importButton);
		final Button cancelButton = new Button(LangResources.get("cancel"), null, ButtonStyle.SECONDARY);
		cancelButton.addActionListener(event -> dispose());
		buttonPanel.add(cancelButton);
		addLeftAligned(contentPanel, buttonPanel);

		add(contentPanel);

		getRootPane().setDefaultButton(importButton);
		// ESC closes the dialog like the cancel button (returnValue stays null)
		getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		updateSummary();
		setLocationRelativeTo(owner);
	}

	/**
	 * Creates a checkbox with an explanation below it. A checkbox without any
	 * affected property or value is disabled and unchecked.
	 */
	private JCheckBox createOptionCheckBox(final JPanel parent, final String text, final String description, final boolean hasAffectedItems, final boolean selectedByDefault) {
		final JCheckBox checkBox = new JCheckBox(text);
		checkBox.setEnabled(hasAffectedItems);
		checkBox.setSelected(hasAffectedItems && selectedByDefault);
		checkBox.addActionListener(event -> updateSummary());
		addLeftAligned(parent, checkBox);

		final JTextArea descriptionArea = createTextArea(description);
		descriptionArea.setFont(descriptionArea.getFont().deriveFont(descriptionArea.getFont().getSize2D() - 1));
		final Color descriptionColor = UIManager.getColor("Label.disabledForeground");
		if (descriptionColor != null) {
			descriptionArea.setForeground(descriptionColor);
		}
		// Indented to the text of the checkbox
		descriptionArea.setBorder(BorderFactory.createEmptyBorder(0, 26, 6, 0));
		addLeftAligned(parent, descriptionArea);

		return checkBox;
	}

	/**
	 * Read-only, non-opaque multi-line text with the proportional dialog font
	 */
	private static JTextArea createTextArea(final String text) {
		final JTextArea textArea = new JTextArea(text);
		textArea.setEditable(false);
		textArea.setFocusable(false);
		textArea.setOpaque(false);
		final Font font = textArea.getFont();
		textArea.setFont(new Font(Font.DIALOG, font.getStyle(), font.getSize()));
		return textArea;
	}

	private static void addLeftAligned(final JPanel parent, final JComponent component) {
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		parent.add(component);
	}

	private MergeOptions getSelectedOptions() {
		return new MergeOptions(addNewPropertiesCheckBox.isSelected(), fillEmptyValuesCheckBox.isSelected(), overwriteDifferingValuesCheckBox.isSelected());
	}

	/**
	 * Shows the effect of the current choice and enables the import button only if the choice changes anything.
	 */
	private void updateSummary() {
		summaryPanel.removeAll();

		final MergeOptions options = getSelectedOptions();

		if (mergePlan.getNewPropertyCount() > 0) {
			addSummaryLine(options.isAddNewProperties()
					? LangResources.get("mergeOptions_summary_added", mergePlan.getNewPropertyCount())
					: LangResources.get("mergeOptions_summary_notAdded", mergePlan.getNewPropertyCount()), false);
		}
		if (mergePlan.getFillableValueCount() > 0) {
			addSummaryLine(options.isFillEmptyValues()
					? LangResources.get("mergeOptions_summary_filled", mergePlan.getFillableValueCount())
					: LangResources.get("mergeOptions_summary_notFilled", mergePlan.getFillableValueCount()), false);
		}
		if (mergePlan.getDifferingValueCount() > 0) {
			if (options.isOverwriteDifferingValues()) {
				// Overwriting loses the existing values, so this line is highlighted
				addSummaryLine(LangResources.get("mergeOptions_summary_overwritten", mergePlan.getDifferingValueCount()), true);
			} else {
				addSummaryLine(LangResources.get("mergeOptions_summary_kept", mergePlan.getDifferingValueCount()), false);
			}
		}
		if (!mergePlan.getSkippedEntries().isEmpty()) {
			addSummaryLine(LangResources.get("mergeOptions_summary_skipped", mergePlan.getSkippedEntries().size()), false);
		}

		final boolean hasEffect = options.hasEffectOn(mergePlan);
		if (!hasEffect) {
			addSummaryLine(LangResources.get("mergeOptions_summary_nothing"), true);
		}
		importButton.setEnabled(hasEffect);

		summaryPanel.revalidate();
		summaryPanel.repaint();
		// The number of lines may have changed
		pack();
	}

	private void addSummaryLine(final String text, final boolean highlight) {
		final JLabel label = new JLabel("\u2022 " + text);
		if (highlight) {
			label.setForeground(DATA_LOSS_COLOR);
			label.setFont(label.getFont().deriveFont(Font.BOLD));
		}
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		summaryPanel.add(label);
	}
}
