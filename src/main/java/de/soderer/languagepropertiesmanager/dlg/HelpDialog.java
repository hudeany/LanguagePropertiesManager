package de.soderer.languagepropertiesmanager.dlg;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

import de.soderer.languagepropertiesmanager.LanguagePropertiesManager;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.VersionInfo;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.swing.ModalDialog;
import de.soderer.utilities.swing.ShowDataDialog;

public class HelpDialog extends ModalDialog<Boolean> {
	private static final long serialVersionUID = -2514371208541305317L;

	/**
	 * Extra width for the OS-drawn title bar parts (icon, window buttons, frame),
	 * added to the title text width when making sure the title is not truncated.
	 */
	private static final int TITLE_BAR_PADDING = 120;

	private final LanguagePropertiesManagerDialog applicationDialog;
	private final ConfigurationProperties applicationConfiguration;

	public HelpDialog(final LanguagePropertiesManagerDialog applicationDialog, final String title, final ConfigurationProperties applicationConfiguration) {
		super(applicationDialog, title);

		this.applicationDialog = applicationDialog;
		this.applicationConfiguration = applicationConfiguration;

		createComponents();
	}

	private void createComponents() {
		final int margin = 5;

		// One column, all buttons equally wide and filling the dialog's width
		final JPanel buttonSection = new JPanel(new GridLayout(0, 1, margin, margin));
		buttonSection.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));

		final JButton versionInfoButton = new JButton(LangResources.get("versionInfo"));
		versionInfoButton.addActionListener(e -> new ShowDataDialog(this, LanguagePropertiesManager.APPLICATION_NAME + " (" + LanguagePropertiesManager.VERSION + ") " + LangResources.get("versionInfo"), VersionInfo.getVersionInfoText()).withResizable(true).open());
		buttonSection.add(versionInfoButton);

		final JButton manualButton = new JButton(LangResources.get("manual"));
		manualButton.addActionListener(e -> new ShowDataDialog(this, LanguagePropertiesManager.APPLICATION_NAME + " (" + LanguagePropertiesManager.VERSION + ") " + LangResources.get("manual"), readManualText()).withResizable(true).withSize(800, 400).open());
		buttonSection.add(manualButton);

		final JButton checkUpdateButton = new JButton(LangResources.get("checkUpdate"));
		checkUpdateButton.addActionListener(e -> {
			try {
				ApplicationUpdateUtilities.executeUpdate(applicationDialog, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, true, false);
			} catch (final Exception ex) {
				applicationDialog.showErrorMessage(LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", ex.getMessage()));
			}
		});
		buttonSection.add(checkUpdateButton);

		final JButton closeButton = new JButton(LangResources.get("close"));
		closeButton.addActionListener(e -> {
			returnValue = true;
			dispose();
		});
		buttonSection.add(closeButton);

		add(buttonSection);

		getRootPane().setDefaultButton(closeButton);

		setResizable(false);

		pack();

		widenToFitTitle();

		// Centering must happen last, once the dialog has its final size
		setLocationRelativeTo(applicationDialog);
	}

	/**
	 * pack() only considers the content, never the (often much longer) window
	 * title, so without this the title may get truncated by the window manager.
	 * The GridLayout above stretches the buttons to the new width.
	 */
	private void widenToFitTitle() {
		final String title = getTitle();
		if (Utilities.isBlank(title)) {
			return;
		}

		final int requiredWidth = getFontMetrics(getFont()).stringWidth(title) + TITLE_BAR_PADDING;
		if (getWidth() < requiredWidth) {
			setSize(new Dimension(requiredWidth, getHeight()));
		}
	}

	private static String readManualText() {
		try (InputStream resourceStream = LanguagePropertiesManager.class.getResourceAsStream("/manual_" + Locale.getDefault().getLanguage().toLowerCase() + ".txt")) {
			if (resourceStream != null) {
				return IoUtilities.toString(resourceStream, StandardCharsets.UTF_8);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			// Fall back to the default manual
		}

		try (InputStream resourceStreamDefault = LanguagePropertiesManager.class.getResourceAsStream("/manual.txt")) {
			if (resourceStreamDefault != null) {
				return IoUtilities.toString(resourceStreamDefault, StandardCharsets.UTF_8);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			// Handled below
		}

		return "Manual not available";
	}
}
