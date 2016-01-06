package de.soderer.languagepropertiesmanager.dlg;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import de.soderer.utilities.LangResources;
import de.soderer.utilities.NetworkUtilities;
import de.soderer.utilities.SystemUtilities;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.swt.ModalDialog;
import de.soderer.utilities.swt.QuestionDialog;
import de.soderer.utilities.swt.ShowDataDialog;
import de.soderer.utilities.swt.SwtUtilities;
import de.soderer.utilities.swt.UpdateApplicationDialog;
import de.soderer.utilities.xml.XmlUtilities;

public class HelpDialog extends ModalDialog<Boolean> {
	public HelpDialog(Shell parentShell, String title) {
		super(parentShell, title);
	}

	@Override
	protected void createComponents(Shell shell) throws Exception {
		shell.setLayout(new FillLayout());
	
		Composite buttonSection = new Composite(shell, SWT.NONE);
		buttonSection.setLayout(SwtUtilities.createNoMarginGridLayout(1, false));
				
		Button versionInfoButton = new Button(buttonSection, SWT.PUSH);
		versionInfoButton.setText(LangResources.get("versionInfo"));
		versionInfoButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		versionInfoButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				StringBuilder text = new StringBuilder();

				text.append("Version 1.3.0 (2013-03-19):\n\tDependencies update");
				text.append("\n\n");
				text.append("Version 1.2.0 (2013-03-17):\n\tImproved Errordialog");
				text.append("\n\n");
				text.append("Version 1.1.0 (2013-03-11):\n\tAdded update function");
				text.append("\n\n");
				text.append("Version 1.0.0 (2013-01-01):\n\tFirst released version");
				
				new ShowDataDialog(getParent(), LanguagePropertiesManagerDialog.APPLICATION_NAME + "(" + LanguagePropertiesManagerDialog.APPLICATION_VERSION + ") " + LangResources.get("versionInfo"), text.toString()).open();
			}
		});
		
		Button checkUpdateButton = new Button(buttonSection, SWT.PUSH);
		checkUpdateButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		checkUpdateButton.setText(LangResources.get("checkUpdate"));
		checkUpdateButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					if (!NetworkUtilities.checkForNetworkConnection()) {
						throw new Exception(LangResources.get("error.missingNetworkConnection"));
					} else if (!NetworkUtilities.ping(LanguagePropertiesManagerDialog.HOME_URL)) {
						throw new Exception(LangResources.get("error.missingInternetConnection"));
					}
					
					Document versionsDocument = XmlUtilities.downloadAndParseXmlFile(LanguagePropertiesManagerDialog.VERSIONINFO_DOWNLOAD);
					Node startFileNameNode = XmlUtilities.getSingleXPathNode(versionsDocument, "ApplicationVersions/" + LanguagePropertiesManagerDialog.APPLICATION_NAME + "/StartFileName");
					Node versionNode = startFileNameNode.getAttributes().getNamedItem("currentVersion");
					String version = versionNode.getNodeValue();
					
					if (Utilities.compareVersion(LanguagePropertiesManagerDialog.APPLICATION_VERSION, version) < 0) {
						if (SystemUtilities.isJavaWebstartApp()) {
							new QuestionDialog(getParent(), LangResources.get("updateCheck"), LangResources.get("updateCheckTextNewVersionWebstart", LanguagePropertiesManagerDialog.APPLICATION_VERSION, version, LanguagePropertiesManagerDialog.HOME_URL), LangResources.get("ok")).open();
						} else {
							UpdateApplicationDialog dialog = new UpdateApplicationDialog(getParent(), LanguagePropertiesManagerDialog.APPLICATION_NAME + " Update", LangResources.get("updateCheckTextNewVersionAvailable", LanguagePropertiesManagerDialog.APPLICATION_VERSION, version), LanguagePropertiesManagerDialog.APPLICATION_DOWNLOAD, false);
							dialog.open();
						}
					} else {
						new QuestionDialog(getParent(), LangResources.get("updateCheck"), LangResources.get("updateCheckTextNoVersionAvailable", LanguagePropertiesManagerDialog.APPLICATION_VERSION), LangResources.get("ok")).open();
					}
				} catch (Exception e1) {
					new QuestionDialog(getParent(), LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", e1.getMessage()), LangResources.get("ok")).open();
				}
			}
		});

		
		Button closeButton = new Button(buttonSection, SWT.PUSH);
		closeButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		closeButton.setText(LangResources.get("close"));
		closeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getParent().close();
			}
		});
		
		shell.pack();
	}
}
