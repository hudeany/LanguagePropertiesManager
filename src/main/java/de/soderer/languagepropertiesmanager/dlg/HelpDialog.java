package de.soderer.languagepropertiesmanager.dlg;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import de.soderer.languagepropertiesmanager.LanguagePropertiesManager;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.VersionInfo;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.swt.ApplicationConfigurationDialog;
import de.soderer.utilities.swt.ModalDialog;
import de.soderer.utilities.swt.ShowDataDialog;
import de.soderer.utilities.swt.SwtUtilities;

public class HelpDialog extends ModalDialog<Boolean> {
	private final LanguagePropertiesManagerDialog applicationDialog;
	private final ConfigurationProperties applicationConfiguration;

	public HelpDialog(final LanguagePropertiesManagerDialog applicationDialog, final String title, final ConfigurationProperties applicationConfiguration) {
		super(applicationDialog, title);
		this.applicationDialog = applicationDialog;
		this.applicationConfiguration = applicationConfiguration;
	}

	@Override
	protected void createComponents(final Shell shell) throws Exception {
		shell.setLayout(new FillLayout());

		final Composite buttonSection = new Composite(shell, SWT.NONE);
		buttonSection.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));

		final Button versionInfoButton = new Button(buttonSection, SWT.PUSH);
		versionInfoButton.setText(LangResources.get("versionInfo"));
		versionInfoButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		versionInfoButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				new ShowDataDialog(getParent(), LanguagePropertiesManager.APPLICATION_NAME + "(" + LanguagePropertiesManager.VERSION + ") " + LangResources.get("versionInfo"), VersionInfo.getVersionInfoText()).open();
			}
		});

		final Button manualButton = new Button(buttonSection, SWT.PUSH);
		manualButton.setText(LangResources.get("manual"));
		manualButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		manualButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent event) {
				String manualText;
				try (InputStream resourceStream = LanguagePropertiesManager.class.getResourceAsStream("/manual_" + Locale.getDefault().getLanguage().toLowerCase() + ".txt")) {
					if (resourceStream == null) {
						try (InputStream resourceStreamDefault = LanguagePropertiesManager.class.getResourceAsStream("/manual.txt")) {
							manualText = IoUtilities.toString(resourceStreamDefault, StandardCharsets.UTF_8);
						} catch (@SuppressWarnings("unused") final Exception e) {
							manualText = "Manual not available";
						}
					} else {
						manualText = IoUtilities.toString(resourceStream, StandardCharsets.UTF_8);
					}
				} catch (@SuppressWarnings("unused") final Exception e) {
					manualText = "Manual not available";
				}

				final ShowDataDialog showDataDialog = new ShowDataDialog(getParent(), LanguagePropertiesManager.APPLICATION_NAME + "(" + LanguagePropertiesManager.VERSION + ") " + LangResources.get("manual"), manualText);
				showDataDialog.setSize(800, 400);
				showDataDialog.open();
			}
		});

		final Button checkUpdateButton = new Button(buttonSection, SWT.PUSH);
		checkUpdateButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		checkUpdateButton.setText(LangResources.get("checkUpdate"));
		checkUpdateButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				try {
					final ProxyConfigurationType proxyConfigurationType = ProxyConfigurationType.getFromString(applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE));
					final String proxyUrl = applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_URL);
					final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(proxyConfigurationType, proxyUrl);

					ApplicationUpdateUtilities.executeUpdate(applicationDialog, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, null, true, false);
				} catch (final Exception e1) {
					showErrorMessage(LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", e1.getMessage()));
				}
			}
		});


		final Button closeButton = new Button(buttonSection, SWT.PUSH);
		closeButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		closeButton.setText(LangResources.get("close"));
		closeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				getParent().close();
			}
		});

		shell.pack();
		widenToFitTitle(shell);

		// Fix for Linux/GTK: the window manager sometimes enforces a minimum width
		// only after the initial pack(), so the full window title can be displayed.
		// Without a re-layout, the buttons then keep their original (small) width
		// and stick to the left.
		// This resize listener makes sure GridData(SWT.FILL, ...) is re-applied
		// whenever the size changes afterwards.
		shell.addListener(SWT.Resize, event -> shell.layout(true, true));

		// Centering must happen last, once the shell has its final size -
		// otherwise the position would be computed against a size that is
		// still about to change.
		centerOverMainShell(shell);
	}

	/**
	 * Computes the position that centers this dialog's shell over the main
	 * application shell, as far as the screen allows (i.e. clamped to stay
	 * fully on-screen if the main shell is positioned close to a screen
	 * edge), and registers it via setLocation(Point).
	 *
	 * Note: this deliberately does NOT call shell.setLocation(...) directly.
	 * ModalDialog.open() calls setPosition(getParent()) right after
	 * createComponents() returns, which would simply overwrite any direct
	 * shell.setLocation(...) call made here with its own default centering
	 * (over shell.getParent(), not necessarily the same as our main
	 * application shell). Going through setLocation(Point) instead stores
	 * the position in ModalDialog's own "location" field, which
	 * setPosition() then uses as-is instead of recomputing its default.
	 */
	private void centerOverMainShell(final Shell shell) {
		final Shell mainShell = applicationDialog.getShell();
		if (mainShell == null || mainShell.isDisposed()) {
			return;
		}

		final Rectangle mainShellBounds = mainShell.getBounds();
		final Point shellSize = shell.getSize();

		int x = mainShellBounds.x + (mainShellBounds.width - shellSize.x) / 2;
		int y = mainShellBounds.y + (mainShellBounds.height - shellSize.y) / 2;

		final Rectangle screenBounds = mainShell.getMonitor().getBounds();
		x = Math.max(screenBounds.x, Math.min(x, screenBounds.x + screenBounds.width - shellSize.x));
		y = Math.max(screenBounds.y, Math.min(y, screenBounds.y + screenBounds.height - shellSize.y));

		setLocation(new Point(x, y));
	}

	/**
	 * Widens the shell, if necessary, so that its full window title text
	 * fits into the title bar. shell.pack() only sizes the shell based on
	 * its content widgets, never based on the (often much longer) window
	 * title string, so without this the title can get truncated/ellipsized
	 * by the OS, especially since this dialog's title includes the
	 * application name, version, and "Help" suffix.
	 */
	private static void widenToFitTitle(final Shell shell) {
		final String title = shell.getText();
		if (Utilities.isBlank(title)) {
			return;
		}

		final GC gc = new GC(shell);
		try {
			// getFontMetrics()/textExtent() approximate the title bar font
			// reasonably well; add generous padding for the OS-drawn title
			// bar icon, close/min/max buttons, and window border/frame.
			final int textWidth = gc.textExtent(title).x;
			final int requiredWidth = textWidth + 120;
			if (shell.getSize().x < requiredWidth) {
				shell.setSize(requiredWidth, shell.getSize().y);
			}
		} finally {
			gc.dispose();
		}
	}
}
