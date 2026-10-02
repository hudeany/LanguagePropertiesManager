package de.soderer.languagepropertiesmanager.dlg;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import de.soderer.languagepropertiesmanager.LanguagePropertiesManager;
import de.soderer.languagepropertiesmanager.model.LanguagePropertiesModel;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.PropertiesReader;
import de.soderer.utilities.PropertiesWriter;
import de.soderer.utilities.Utilities;

/**
 * Right part of the main window: path, key, comment and the language values of
 * the first selected property of the LanguagePropertiesModel, or empty fields
 * for a new property. Changes are applied to the model with the OK button.
 */
public class PropertyDetailPanel extends JPanel {
	private static final long serialVersionUID = -2914760518329417765L;

	/**
	 * Actions of the main window triggered by the detail view
	 */
	public interface Callback {
		/** Asks the user for the name of a new properties set, when the first property is added without loaded data */
		String askForNewLanguagePropertiesSetName();

		/** The available languages changed (new empty properties set), so table and language fields have to be rebuilt */
		void languagesChanged();

		/** The selected property was changed */
		void propertyChanged();

		/** A new property was added and is selected */
		void propertyAdded();

		void showError(Exception exception);

		void showErrorMessage(String title, String text);
	}

	private final LanguagePropertiesModel model;
	private final Callback callback;

	private boolean showStorageTexts = false;
	private boolean dataWasModified = false;

	/** Suppresses the "data was modified" tracking while the detail fields are filled programmatically */
	private boolean technicalDataChange = false;

	/** Whether the detail fields currently show an existing property ("change") or a new one ("add") */
	private boolean detailShowsExistingProperty = false;

	private JTextField pathTextfield;
	private JTextField keyTextfield;
	private JTextArea commentTextfield;
	private JPanel detailFieldsPart;
	private final Map<String, JTextArea> languageTextFields = new LinkedHashMap<>();
	/** Labels of the language fields, they show whether an empty field means "missing" or "explicitly empty" */
	private final Map<String, JLabel> languageLabels = new LinkedHashMap<>();
	/** Language signs whose empty field stands for an explicitly empty value ("key=") instead of a missing key */
	private final Set<String> explicitlyEmptyLanguageSigns = new HashSet<>();

	private JButton okButton;
	private JButton cancelButton;
	private JButton textConversionButton;

	public PropertyDetailPanel(final LanguagePropertiesModel model, final Callback callback) {
		super(new BorderLayout(0, 3));
		this.model = model;
		this.callback = callback;
		setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

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
		// Align the label with the first line, because a multi-line comment makes the field higher
		labelConstraints.anchor = GridBagConstraints.NORTHWEST;
		labelConstraints.insets = new Insets(4, 2, 2, 4);
		keyBereich.add(new JLabel(LangResources.get("comment") + ":"), labelConstraints);
		labelConstraints.anchor = GridBagConstraints.WEST;
		labelConstraints.insets = new Insets(2, 2, 2, 4);
		// Text area instead of text field, so multi-line comments are shown with all their lines
		commentTextfield = createMultiLineTextArea();
		commentTextfield.getDocument().addDocumentListener(new DetailModifyListener());
		keyBereich.add(commentTextfield, fieldConstraints);

		final GridBagConstraints separatorConstraints = new GridBagConstraints();
		separatorConstraints.gridx = 0;
		separatorConstraints.gridy = 3;
		separatorConstraints.gridwidth = 2;
		separatorConstraints.fill = GridBagConstraints.HORIZONTAL;
		separatorConstraints.insets = new Insets(4, 0, 0, 0);
		keyBereich.add(new JSeparator(SwingConstants.HORIZONTAL), separatorConstraints);

		add(keyBereich, BorderLayout.NORTH);

		detailFieldsPart = new JPanel(new GridBagLayout());
		final JScrollPane scrolledPart = new JScrollPane(detailFieldsPart);
		scrolledPart.setBorder(BorderFactory.createEmptyBorder());
		scrolledPart.setMinimumSize(new Dimension(200, 100));
		scrolledPart.getVerticalScrollBar().setUnitIncrement(16);
		add(scrolledPart, BorderLayout.CENTER);

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
			// Only switch the mode, if all field contents could be converted
			if (changeDisplayMode(!showStorageTexts)) {
				showStorageTexts = !showStorageTexts;
				textConversionButton.setText(showStorageTexts ? LangResources.get("change_to_show_visble_texts") : LangResources.get("change_to_show_storage_texts"));
			}
		});
		buttonConstraints.gridy = 1;
		buttonBereich.add(textConversionButton, buttonConstraints);

		okButton = new JButton(LangResources.get("button_text_add"));
		okButton.addActionListener(e -> applyChanges());
		buttonConstraints.gridy = 2;
		buttonConstraints.gridwidth = 1;
		buttonBereich.add(okButton, buttonConstraints);

		cancelButton = new JButton(LangResources.get("button_text_discard"));
		cancelButton.addActionListener(e -> refresh());
		buttonConstraints.gridx = 1;
		buttonBereich.add(cancelButton, buttonConstraints);

		add(buttonBereich, BorderLayout.SOUTH);

		updateButtonStatus();
	}

	/**
	 * @return true if the fields contain changes, which are not yet applied with the OK button
	 */
	public boolean isModified() {
		return dataWasModified;
	}

	private void updateButtonStatus() {
		if (okButton != null) {
			okButton.setEnabled(dataWasModified);
		}
		if (cancelButton != null) {
			cancelButton.setEnabled(dataWasModified);
		}
		if (textConversionButton != null) {
			textConversionButton.setEnabled(true);
		}
	}

	/**
	 * Recreates the language fields after the set of languages changed
	 */
	public void rebuildLanguageFields() {
		detailFieldsPart.removeAll();
		languageTextFields.clear();
		languageLabels.clear();
		explicitlyEmptyLanguageSigns.clear();

		if (model.isLoaded()) {
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
			for (final String sign : model.getAvailableLanguageSigns()) {
				labelConstraints.gridy = row;
				fieldConstraints.gridy = row;

				final String labelText = LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT.equals(sign) ? LangResources.get("columnheader_default") : sign;
				final JLabel languageLabel = new JLabel(labelText + ":");
				detailFieldsPart.add(languageLabel, labelConstraints);
				languageLabels.put(sign, languageLabel);

				final JTextArea languageTextfield = createMultiLineTextArea();
				languageTextfield.getDocument().addDocumentListener(new DetailModifyListener());
				languageTextfield.getDocument().addDocumentListener(new DocumentListener() {
					@Override
					public void insertUpdate(final DocumentEvent event) {
						// Entered text replaces the explicitly empty value
						explicitlyEmptyLanguageSigns.remove(sign);
						updateLanguageLabel(sign);
					}

					@Override
					public void removeUpdate(final DocumentEvent event) {
						updateLanguageLabel(sign);
					}

					@Override
					public void changedUpdate(final DocumentEvent event) {
						// Attribute changes only, no text change
					}
				});
				languageTextfield.setComponentPopupMenu(createLanguageFieldContextMenu(sign, languageTextfield));
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
	}

	/**
	 * Shows the first selected property of the model, or empty fields for a new property
	 */
	public void refresh() {
		final boolean previousTechnicalDataChange = technicalDataChange;
		technicalDataChange = true;
		try {
			final LanguageProperty property = model.getFirstSelectedProperty();
			if (property != null) {
				pathTextfield.setText(property.getPath());
				keyTextfield.setText(showStorageTexts ? PropertiesWriter.escapeKey(property.getKey()) : property.getKey());
				commentTextfield.setText(Utilities.isNotEmpty(property.getComment()) ? property.getComment() : "");
				for (final Map.Entry<String, JTextArea> languageTextField : languageTextFields.entrySet()) {
					final String value = property.getLanguageValue(languageTextField.getKey());
					if (value == null) {
						languageTextField.getValue().setText("");
					} else if (showStorageTexts) {
						languageTextField.getValue().setText(PropertiesWriter.escapeValue(value));
					} else {
						languageTextField.getValue().setText(value);
					}
					// Set after the text, because entering text resets this state
					if ("".equals(value)) {
						explicitlyEmptyLanguageSigns.add(languageTextField.getKey());
					} else {
						explicitlyEmptyLanguageSigns.remove(languageTextField.getKey());
					}
					updateLanguageLabel(languageTextField.getKey());
				}

				detailShowsExistingProperty = true;
				okButton.setText(LangResources.get("button_text_change"));
			} else {
				pathTextfield.setText("");
				keyTextfield.setText("");
				commentTextfield.setText("");
				for (final JTextArea languageTextfield : languageTextFields.values()) {
					languageTextfield.setText("");
				}
				explicitlyEmptyLanguageSigns.clear();
				for (final String languageSign : languageLabels.keySet()) {
					updateLanguageLabel(languageSign);
				}

				detailShowsExistingProperty = false;
				okButton.setText(LangResources.get("button_text_add"));
			}

			dataWasModified = false;
			updateButtonStatus();
		} catch (final Exception e) {
			callback.showError(e);
		} finally {
			technicalDataChange = previousTechnicalDataChange;
		}
	}

	/**
	 * OK button of the detail view: changes the selected property or adds a new
	 * one.
	 */
	private void applyChanges() {
		try {
			if (detailShowsExistingProperty) {
				// Change existing property
				final LanguageProperty propertyToChange = model.getFirstSelectedProperty();
				if (propertyToChange == null) {
					throw new Exception("Cannot find property to change");
				}

				propertyToChange.setKey(getPlainKey(keyTextfield.getText()));
				propertyToChange.setComment(Utilities.isNotEmpty(commentTextfield.getText()) ? commentTextfield.getText() : null);
				for (final Map.Entry<String, JTextArea> languageTextField : languageTextFields.entrySet()) {
					final String languageSign = languageTextField.getKey();
					propertyToChange.setLanguageValue(languageSign, getDetailLanguageValue(languageSign, languageTextField.getValue()));
				}
				model.setUnsavedChanges(true);

				dataWasModified = false;
				updateButtonStatus();
				callback.propertyChanged();
			} else {
				final LanguageProperty newValues = new LanguageProperty(pathTextfield.getText(), getPlainKey(keyTextfield.getText()));
				for (final Map.Entry<String, JTextArea> languageTextField : languageTextFields.entrySet()) {
					newValues.setLanguageValue(languageTextField.getKey(), getDetailLanguageValue(languageTextField.getKey(), languageTextField.getValue()));
				}

				if (Utilities.isNotEmpty(commentTextfield.getText())) {
					newValues.setComment(commentTextfield.getText());
				} else {
					newValues.setComment(null);
				}

				if (!model.isLoaded()) {
					model.createEmpty(callback.askForNewLanguagePropertiesSetName());
					// Rebuilds the language fields, their content was already taken over into "newValues"
					callback.languagesChanged();
				}

				// Add new property, it is selected afterwards
				model.addProperty(newValues);
				dataWasModified = false;
				callback.propertyAdded();
			}
		} catch (final Exception ex) {
			callback.showError(ex);
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
			updateButtonStatus();
		}
	}

	/**
	 * Converts the key field's current content back to its plain (unescaped) form.
	 * When showStorageTexts is active the fields display the escaped storage representation
	 * (see changeDisplayMode), so it needs to be unescaped before it is written back into the model.
	 * PropertiesReader is used, so the result is the same as after writing and reloading the file.
	 */
	private String getPlainKey(final String fieldText) throws Exception {
		return showStorageTexts ? PropertiesReader.unescapeKey(fieldText) : fieldText;
	}

	/**
	 * Converts a language value field's current content back to its plain (unescaped) form.
	 * See getPlainKey().
	 */
	private String getPlainValue(final String fieldText) throws Exception {
		return showStorageTexts ? PropertiesReader.unescapeValue(fieldText) : fieldText;
	}

	/**
	 * Value of a language field of the detail view for storing. An empty field is
	 * a missing value (null), unless it was marked as explicitly empty ("").
	 */
	private String getDetailLanguageValue(final String languageSign, final JTextArea languageTextfield) throws Exception {
		final String value = LanguageProperty.toStorageValue(languageSign, getPlainValue(languageTextfield.getText()));
		if (value == null && explicitlyEmptyLanguageSigns.contains(languageSign)) {
			return "";
		} else {
			return value;
		}
	}

	/**
	 * Context menu of a language field in the detail view to choose between an
	 * explicitly empty value and a missing value. The change is applied with the
	 * OK button like any other change of the detail view.
	 */
	private JPopupMenu createLanguageFieldContextMenu(final String languageSign, final JTextArea languageTextfield) {
		final JPopupMenu contextMenu = new JPopupMenu();

		final JMenuItem setValueEmptyItem = new JMenuItem(LangResources.get("contextmenu_setValueEmpty"));
		setValueEmptyItem.addActionListener(e -> setDetailLanguageFieldEmpty(languageSign, languageTextfield, true));
		contextMenu.add(setValueEmptyItem);

		final JMenuItem setValueMissingItem = new JMenuItem(LangResources.get("contextmenu_setValueMissing"));
		setValueMissingItem.addActionListener(e -> setDetailLanguageFieldEmpty(languageSign, languageTextfield, false));
		contextMenu.add(setValueMissingItem);

		return contextMenu;
	}

	private void setDetailLanguageFieldEmpty(final String languageSign, final JTextArea languageTextfield, final boolean explicitlyEmpty) {
		final boolean wasExplicitlyEmpty = explicitlyEmptyLanguageSigns.contains(languageSign);
		final boolean hadText = !languageTextfield.getText().isEmpty();

		// Clearing the text first, because entering or removing text changes the state of the field
		languageTextfield.setText("");
		if (explicitlyEmpty) {
			explicitlyEmptyLanguageSigns.add(languageSign);
		} else {
			explicitlyEmptyLanguageSigns.remove(languageSign);
		}
		updateLanguageLabel(languageSign);

		if (hadText || wasExplicitlyEmpty != explicitlyEmpty) {
			dataWasModified = true;
		}
		updateButtonStatus();
	}

	/**
	 * Shows the state of an empty language field in its label: the same signs as
	 * in the table for a missing value and for an explicitly empty value
	 */
	private void updateLanguageLabel(final String languageSign) {
		final JLabel languageLabel = languageLabels.get(languageSign);
		final JTextArea languageTextfield = languageTextFields.get(languageSign);
		if (languageLabel == null) {
			return;
		}

		final String labelText = LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT.equals(languageSign) ? LangResources.get("columnheader_default") : languageSign;
		if (languageTextfield == null || !languageTextfield.getText().isEmpty() || !model.isLoaded()) {
			languageLabel.setText(labelText + ":");
			languageLabel.setToolTipText(null);
		} else if (explicitlyEmptyLanguageSigns.contains(languageSign)) {
			languageLabel.setText(labelText + " " + LangResources.get("value_empty_sign") + ":");
			languageLabel.setToolTipText(null);
		} else {
			languageLabel.setText(labelText + " " + LangResources.get("value_not_found_sign") + ":");
			languageLabel.setToolTipText(LangResources.get("value_not_found_tooltip"));
		}
	}

	/**
	 * Creates the input component for a language value or the comment.
	 * A JTextField can not be used here, because its document silently replaces
	 * line breaks by blanks ("filterNewlines"), so values like "a\nb" would lose
	 * their line break in the plain text display mode and on writing back.
	 * The text area grows in height with the number of lines, but is styled and
	 * behaves (font, border, Tab focus traversal) like a single line text field.
	 */
	private static JTextArea createMultiLineTextArea() {
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

	/**
	 * Switches the detail fields between the plain display texts and their storage representation
	 * (escaped like in the .properties file).
	 * All conversions are done before any field is changed, so an invalid escape sequence entered in the
	 * storage view leaves all fields unchanged.
	 *
	 * @return true if the display mode was changed, false if a field content could not be converted
	 */
	private boolean changeDisplayMode(final boolean changeToShowStorageTexts) {
		final String convertedKey;
		final Map<JTextArea, String> convertedValues = new LinkedHashMap<>();
		try {
			if (changeToShowStorageTexts) {
				convertedKey = PropertiesWriter.escapeKey(keyTextfield.getText());
				for (final JTextArea field : languageTextFields.values()) {
					convertedValues.put(field, PropertiesWriter.escapeValue(field.getText()));
				}
			} else {
				convertedKey = PropertiesReader.unescapeKey(keyTextfield.getText());
				for (final JTextArea field : languageTextFields.values()) {
					convertedValues.put(field, PropertiesReader.unescapeValue(field.getText()));
				}
			}
		} catch (final Exception e) {
			callback.showErrorMessage(LanguagePropertiesManager.APPLICATION_NAME, e.getMessage());
			return false;
		}

		technicalDataChange = true;
		try {
			keyTextfield.setText(convertedKey);
			for (final Map.Entry<JTextArea, String> convertedValue : convertedValues.entrySet()) {
				convertedValue.getKey().setText(convertedValue.getValue());
			}
		} finally {
			technicalDataChange = false;
		}
		return true;
	}
}
