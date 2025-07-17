package de.soderer.languagepropertiesmanager.dlg;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
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

					ApplicationUpdateUtilities.executeUpdate(applicationDialog, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, true);
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
	}
}
