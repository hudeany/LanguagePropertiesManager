package de.soderer.languagepropertiesmanager.dlg;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import de.soderer.applicationbase.ApplicationConfigurationProperties;
import de.soderer.applicationbase.ApplicationUpdateUtilities;
import de.soderer.languagepropertiesmanager.LanguagePropertiesManager;
import de.soderer.swing.modern.Button;
import de.soderer.swing.modern.ButtonStyle;
import de.soderer.swing.modern.IconKind;
import de.soderer.swing.modern.ModalDialog;
import de.soderer.swing.modern.ShowDataDialog;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.StringUtilities;
import de.soderer.utilities.VersionInfo;

/**
 * Help dialog with buttons for the version info, the manual and the update check
 */
public class HelpDialog extends ModalDialog<Boolean> {
	private static final long serialVersionUID = -2514371208541305317L;

	/**
	 * Extra width for the OS-drawn title bar parts (icon, window buttons, frame),
	 * added to the title text width when making sure the title is not truncated.
	 */
	private static final int TITLE_BAR_PADDING = 120;

	/** Main window, parent of the dialogs opened from here */
	private final LanguagePropertiesManagerDialog applicationDialog;
	/** Configuration of the application, e.g. for the proxy of the update check */
	private final ApplicationConfigurationProperties applicationConfiguration;

	/**
	 * Creates the help dialog. It is shown by open().
	 *
	 * @param applicationDialog
	 *            main window
	 * @param title
	 *            title of the dialog
	 * @param applicationConfiguration
	 *            configuration of the application
	 */
	public HelpDialog(final LanguagePropertiesManagerDialog applicationDialog, final String title, final ApplicationConfigurationProperties applicationConfiguration) {
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

		final Button versionInfoButton = new Button(LangResources.get("versionInfo"), IconKind.INFO, ButtonStyle.SECONDARY);
		versionInfoButton.addActionListener(e -> new ShowDataDialog(this, LanguagePropertiesManager.APPLICATION_NAME + " (" + LanguagePropertiesManager.VERSION + ") " + LangResources.get("versionInfo"), VersionInfo.getVersionInfoText()).withResizable(true).open());
		buttonSection.add(versionInfoButton);

		final Button manualButton = new Button(LangResources.get("manual"), IconKind.FILE, ButtonStyle.SECONDARY);
		manualButton.addActionListener(e -> new ShowDataDialog(this, LanguagePropertiesManager.APPLICATION_NAME + " (" + LanguagePropertiesManager.VERSION + ") " + LangResources.get("manual"), readManualText()).withResizable(true).withSize(800, 400).open());
		buttonSection.add(manualButton);

		final Button checkUpdateButton = new Button(LangResources.get("checkUpdate"), IconKind.REFRESH, ButtonStyle.SECONDARY);
		checkUpdateButton.addActionListener(e -> {
			try {
				ApplicationUpdateUtilities.executeUpdate(applicationDialog, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, true, false);
			} catch (final Exception ex) {
				applicationDialog.showErrorMessage(LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", ex.getMessage()));
			}
		});
		buttonSection.add(checkUpdateButton);

		final Button closeButton = new Button(LangResources.get("close"), null, ButtonStyle.SECONDARY);
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
		if (StringUtilities.isBlank(title)) {
			return;
		}

		final int requiredWidth = getFontMetrics(getFont()).stringWidth(title) + TITLE_BAR_PADDING;
		if (getWidth() < requiredWidth) {
			setSize(new Dimension(requiredWidth, getHeight()));
		}
	}

	private static String readManualText() {
		final Locale locale = Locale.getDefault();
		final String language = locale.getLanguage().toLowerCase();
		final String country = locale.getCountry().toUpperCase();

		// Most specific first: language with country (e.g. "de_BY"), then language only, then default manual
		final List<String> resourceNames = new ArrayList<>();
		if (!language.isEmpty() && !country.isEmpty()) {
			resourceNames.add("/manual_" + language + "_" + country + ".txt");
		}
		if (!language.isEmpty()) {
			resourceNames.add("/manual_" + language + ".txt");
		}
		resourceNames.add("/manual.txt");

		for (final String resourceName : resourceNames) {
			try (InputStream resourceStream = LanguagePropertiesManager.class.getResourceAsStream(resourceName)) {
				if (resourceStream != null) {
					return IoUtilities.toString(resourceStream, StandardCharsets.UTF_8);
				}
			} catch (@SuppressWarnings("unused") final Exception e) {
				// Try the next, less specific manual
			}
		}

		return "Manual not available";
	}
}
