package de.soderer.languagepropertiesmanager.dlg;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.VersionInfo;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.swt.ApplicationConfigurationDialog;
import de.soderer.utilities.swt.ModalDialog;
import de.soderer.utilities.swt.ShowDataDialog;
import de.soderer.utilities.swt.SwtUtilities;

public class HelpDialog extends ModalDialog<Boolean> {
	private final ConfigurationProperties applicationConfiguration;

	public HelpDialog(final Shell parentShell, final String title, final ConfigurationProperties applicationConfiguration) {
		super(parentShell, title);
		this.applicationConfiguration = applicationConfiguration;
	}

	@Override
	protected void createComponents(final Shell shell) throws Exception {
		shell.setLayout(new FillLayout());

		final Composite buttonSection = new Composite(shell, SWT.NONE);
		buttonSection.setLayout(SwtUtilities.createNoMarginGridLayout(1, false));

		final Button versionInfoButton = new Button(buttonSection, SWT.PUSH);
		versionInfoButton.setText(LangResources.get("versionInfo"));
		versionInfoButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		versionInfoButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				final StringBuilder text = new StringBuilder();

				text.append(VersionInfo.getVersionInfoText());

				new ShowDataDialog(getParent(), LanguagePropertiesManagerDialog.APPLICATION_NAME + "(" + LanguagePropertiesManagerDialog.VERSION + ") " + LangResources.get("versionInfo"), text.toString()).open();
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

					ApplicationUpdateUtilities.executeUpdate((LanguagePropertiesManagerDialog) getParent(), LanguagePropertiesManagerDialog.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, LanguagePropertiesManagerDialog.APPLICATION_NAME, LanguagePropertiesManagerDialog.VERSION, LanguagePropertiesManagerDialog.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, true);
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
