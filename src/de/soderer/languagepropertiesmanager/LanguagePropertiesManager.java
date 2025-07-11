package de.soderer.languagepropertiesmanager;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import org.eclipse.swt.widgets.Display;

import de.soderer.languagepropertiesmanager.dlg.LanguagePropertiesManagerDialog;
import de.soderer.languagepropertiesmanager.storage.ExcelHelper;
import de.soderer.languagepropertiesmanager.worker.ExportToExcelWorker;
import de.soderer.languagepropertiesmanager.worker.ImportFromExcelWorker;
import de.soderer.languagepropertiesmanager.worker.LoadLanguagePropertiesWorker;
import de.soderer.languagepropertiesmanager.worker.WriteLanguagePropertiesWorker;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.ParameterException;
import de.soderer.utilities.UpdateableConsoleApplication;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.Version;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.console.ConsoleType;
import de.soderer.utilities.console.ConsoleUtilities;
import de.soderer.utilities.swing.ApplicationConfigurationDialog;
import de.soderer.utilities.swt.ErrorDialog;
import de.soderer.utilities.worker.WorkerParentDual;

/**
 * TODO:
 * Display of multiline value and comments (escape linebreaks in textfield)
 * cleanup errors
 * Excel file Diff
 * Add configuration button to change app display language
 */

public class LanguagePropertiesManager extends UpdateableConsoleApplication implements WorkerParentDual {
	/** The Constant APPLICATION_NAME. */
	public static final String APPLICATION_NAME = "LanguagePropertiesManager";
	public static final String APPLICATION_STARTUPCLASS_NAME = "de-soderer-LanguagePropertiesManager";
	public static final String APPLICATION_ERROR_EMAIL_ADRESS = "LanguagePropertiesManager.Error@soderer.de";

	public static final File KEYSTORE_FILE = new File(System.getProperty("user.home") + File.separator + "." + APPLICATION_NAME + File.separator + "." + APPLICATION_NAME + ".keystore");
	public static final String HOME_URL = "https://soderer.de/index.php?menu=tools";

	/** The Constant VERSION_RESOURCE_FILE, which contains version number and versioninfo download url. */
	public static final String VERSION_RESOURCE_FILE = "/application_version.txt";

	/** The version is filled in at application start from the application_version.txt file. */
	public static Version VERSION = null;

	/** The version build time is filled in at application start from the application_version.txt file */
	public static LocalDateTime VERSION_BUILDTIME = null;

	/** The versioninfo download url is filled in at application start from the application_version.txt file. */
	public static String VERSIONINFO_DOWNLOAD_URL = null;

	/** Trusted CA certificate for updates **/
	public static String TRUSTED_UPDATE_CA_CERTIFICATES = null;

	public static final String HELP_RESOURCE_FILE_DEFAULT = "/help.txt";
	public static final String HELP_RESOURCE_FILE_DE = "/help.txt";

	/** The Constant CONFIGURATION_FILE. */
	public static final File CONFIGURATION_FILE = new File(System.getProperty("user.home") + File.separator + "." + APPLICATION_NAME + ".config");

	public static final String CONFIG_VERSION = "Application.Version";
	public static final String CONFIG_CLEANUP_REPAIRPUNCTUATION = "Cleanup.RepairPunctuation";
	public static final String CONFIG_LANGUAGE = "Application.Language";
	public static final String CONFIG_PREVIOUS_CHECK_USAGE = "CheckUsage.Previous";
	public static final String CONFIG_RECENT_PROPERTIES = "Recent";
	public static final String CONFIG_DAILY_UPDATE_CHECK = "DailyUpdateCheck";
	public static final String CONFIG_NEXT_DAILY_UPDATE_CHECK = "NextDailyUpdateCheck";
	public static final String CONFIG_PROXY_CONFIGURATION_TYPE = ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE;
	public static final String CONFIG_PROXY_URL = ApplicationConfigurationDialog.CONFIG_PROXY_URL;
	public static final String CONFIG_OPEN_DIR_EXCLUDES = "OpenDirExcludes";

	private int previousTerminalWidth = 0;

	private ActionDefinition actionDefinitionToExecute;

	public static void setupDefaultConfig(final ConfigurationProperties applicationConfiguration) {
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_CLEANUP_REPAIRPUNCTUATION)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_CLEANUP_REPAIRPUNCTUATION, true);
		}
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_LANGUAGE)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_LANGUAGE, Locale.getDefault().getLanguage());
		}

		applicationConfiguration.set(ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE + ConfigurationProperties.ENUM_EXTENSION, "None,System,Proxy-URL,WPAD,PAC-URL");
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_DAILY_UPDATE_CHECK)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_DAILY_UPDATE_CHECK, false);
		}
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_NEXT_DAILY_UPDATE_CHECK)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_NEXT_DAILY_UPDATE_CHECK, "");
		}
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_PROXY_CONFIGURATION_TYPE)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PROXY_CONFIGURATION_TYPE, ProxyConfiguration.ProxyConfigurationType.None.name());
		}
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_PROXY_URL)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PROXY_URL, "");
		}
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES, "__;/src/test/;\\src\\test\\;/bin/;\\bin\\");
		}
	}

	/** The usage message. */
	private static String getUsageMessage() {
		try (InputStream helpInputStream = LanguagePropertiesManager.class.getResourceAsStream(LanguagePropertiesManager.HELP_RESOURCE_FILE_DEFAULT)) {
			return "LanguagePropertiesManager (by Andreas Soderer, mail: languagepropertiesmanager@soderer.de)\n"
					+ "VERSION: " + VERSION.toString() + " (" + DateUtilities.formatDate(DateUtilities.YYYY_MM_DD_HHMMSS, VERSION_BUILDTIME) + ")" + "\n\n"
					+ new String(IoUtilities.toByteArray(helpInputStream), StandardCharsets.UTF_8);
		} catch (@SuppressWarnings("unused") final Exception e) {
			return "Help info is missing";
		}
	}

	/**
	 * The main method.
	 *
	 * @param arguments the arguments
	 */
	public static void main(final String[] arguments) {
		final int returnCode = _main(arguments);
		if (returnCode >= 0) {
			System.exit(returnCode);
		}
	}

	/**
	 * Method used for main but with no System.exit call to make it junit testable
	 *
	 * @param arguments
	 * @return
	 */
	protected static int _main(final String[] args) {
		try (InputStream resourceStream = LanguagePropertiesManager.class.getResourceAsStream(VERSION_RESOURCE_FILE)) {
			// Try to fill the version and versioninfo download url
			final List<String> versionInfoLines = Utilities.readLines(resourceStream, StandardCharsets.UTF_8);
			VERSION = new Version(versionInfoLines.get(0));
			if (versionInfoLines.size() >= 2) {
				VERSION_BUILDTIME = DateUtilities.parseLocalDateTime(DateUtilities.YYYY_MM_DD_HHMMSS, versionInfoLines.get(1));
			}
			if (versionInfoLines.size() >= 3) {
				VERSIONINFO_DOWNLOAD_URL = versionInfoLines.get(2);
			}
			if (versionInfoLines.size() >= 4) {
				TRUSTED_UPDATE_CA_CERTIFICATES = versionInfoLines.get(3);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			// Without the application_version.txt file we may not go on
			System.err.println("Invalid application_version.txt");
			return 1;
		}

		ConfigurationProperties applicationConfiguration;
		try {
			applicationConfiguration = new ConfigurationProperties(LanguagePropertiesManager.APPLICATION_NAME, true);
			LanguagePropertiesManager.setupDefaultConfig(applicationConfiguration);
			if ("de".equalsIgnoreCase(applicationConfiguration.get(LanguagePropertiesManager.CONFIG_LANGUAGE))) {
				Locale.setDefault(Locale.GERMAN);
			} else {
				Locale.setDefault(Locale.ENGLISH);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			System.err.println("Invalid application configuration");
			return 1;
		}

		final ProxyConfigurationType proxyConfigurationType = ProxyConfigurationType.getFromString(applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE));
		final String proxyUrl = applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_URL);
		final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(proxyConfigurationType, proxyUrl);

		try {
			String[] arguments = args;

			boolean openGui = false;

			if (arguments.length == 0) {
				// If started without any parameter we check for headless mode and show the GUI or help
				if (GraphicsEnvironment.isHeadless()) {
					System.out.println(getUsageMessage());
				} else {
					openGui = true;
				}
			} else {
				for (int i = 0; i < arguments.length; i++) {
					if ("help".equalsIgnoreCase(arguments[i]) || "-help".equalsIgnoreCase(arguments[i]) || "--help".equalsIgnoreCase(arguments[i]) || "-h".equalsIgnoreCase(arguments[i]) || "--h".equalsIgnoreCase(arguments[i])
							|| "-?".equalsIgnoreCase(arguments[i]) || "--?".equalsIgnoreCase(arguments[i])) {
						System.out.println(getUsageMessage());
						return 1;
					} else if ("version".equalsIgnoreCase(arguments[i]) && arguments.length == 1) {
						System.out.println(VERSION.toString());
						return 1;
					} else if ("update".equalsIgnoreCase(arguments[i]) && arguments.length == 1) {
						final LanguagePropertiesManager languagePropertiesManager = new LanguagePropertiesManager();
						if (arguments.length > i + 2) {
							ApplicationUpdateUtilities.executeUpdate(languagePropertiesManager, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, arguments[i + 1], arguments[i + 2].toCharArray(), null, false);
						} else if (arguments.length > i + 1) {
							ApplicationUpdateUtilities.executeUpdate(languagePropertiesManager, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, arguments[i + 1], null, null, false);
						} else {
							ApplicationUpdateUtilities.executeUpdate(languagePropertiesManager, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, false);
						}
						return 1;
					} else if ("gui".equalsIgnoreCase(arguments[i])) {
						if (GraphicsEnvironment.isHeadless()) {
							throw new Exception("GUI can only be shown on a non-headless environment");
						}
						openGui = true;
						arguments = Utilities.removeItemAtIndex(arguments, i--);
					}
				}
			}

			final ActionDefinition actionDefinition = new ActionDefinition();

			// Read the parameters
			for (int i = 0; i < arguments.length; i++) {
				boolean wasAllowedParam = false;

				if ("-importFromExcel".equalsIgnoreCase(arguments[i])) {
					i++;
					if (i >= arguments.length) {
						throw new ParameterException(arguments[i - 1], "Missing parameter for importFromExcel");
					} else if (Utilities.isBlank(arguments[i])) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Invalid parameter for importFromExcel");
					} else if (actionDefinition.getImportFromExcel() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter importFromExcel");
					} else if (actionDefinition.getExportToExcel() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Only one of parameters importToExcel and exportFromExcel is allowed");
					} else {
						actionDefinition.setImportFromExcel(arguments[i]);
					}
					wasAllowedParam = true;
				} else if ("-exportToExcel".equalsIgnoreCase(arguments[i])) {
					i++;
					if (i >= arguments.length) {
						throw new ParameterException(arguments[i - 1], "Missing parameter for exportToExcel");
					} else if (Utilities.isBlank(arguments[i])) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Invalid parameter for exportToExcel");
					} else if (actionDefinition.getExportToExcel() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter exportToExcel");
					} else if (actionDefinition.getImportFromExcel() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Only one of parameters importToExcel and exportFromExcel is allowed");
					} else {
						actionDefinition.setExportToExcel(arguments[i]);
					}
					wasAllowedParam = true;
				} else if ("-excelFile".equalsIgnoreCase(arguments[i])) {
					i++;
					if (i >= arguments.length) {
						throw new ParameterException(arguments[i - 1], "Missing parameter for excelFile");
					} else if (Utilities.isBlank(arguments[i])) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Invalid parameter for excelFile");
					} else if (actionDefinition.getExcelFile() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter excelFile");
					} else {
						actionDefinition.setExcelFile(arguments[i]);
					}
					wasAllowedParam = true;
				} else if ("-outputDirectory".equalsIgnoreCase(arguments[i])) {
					i++;
					if (i >= arguments.length) {
						throw new ParameterException(arguments[i - 1], "Missing parameter for outputDirectory");
					} else if (Utilities.isBlank(arguments[i])) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Invalid parameter for outputDirectory");
					} else if (actionDefinition.getOutputDirectory() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter outputDirectory");
					} else {
						actionDefinition.setOutputDirectory(arguments[i]);
					}
					wasAllowedParam = true;
				} else if ("-overwrite".equalsIgnoreCase(arguments[i])) {
					if (actionDefinition.isOverwrite()) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter 'overwrite'");
					} else {
						actionDefinition.setOverwrite(true);
					}
					wasAllowedParam = true;
				} else if ("-v".equalsIgnoreCase(arguments[i])) {
					if (actionDefinition.isVerbose()) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter 'v'");
					} else {
						actionDefinition.setVerbose(true);
					}
					wasAllowedParam = true;
				}

				if (!wasAllowedParam) {
					throw new ParameterException(arguments[i], "Invalid parameter");
				}
			}

			if (openGui) {
				Display display = null;
				try {
					display = new Display();
					final LanguagePropertiesManagerDialog mainDialog = new LanguagePropertiesManagerDialog(display, applicationConfiguration);
					mainDialog.run();
					return -1;
				} catch (final Exception ex) {
					if (display != null) {
						new ErrorDialog(display.getActiveShell(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
					} else {
						System.out.println(ex.toString());
						ex.printStackTrace();
					}
					return 1;
				} finally {
					if (display != null) {
						display.dispose();
					}
				}
			} else {
				LangResources.enforceDefaultLocale();

				// Validate all given parameters
				actionDefinition.checkParameters();

				// Start the worker for terminal output
				try {
					new LanguagePropertiesManager().execute(actionDefinition, applicationConfiguration);
					return 0;
				} catch (final LanguagePropertiesException e) {
					System.err.println(e.getMessage());
					return 1;
				} catch (final Exception e) {
					e.printStackTrace();
					return 1;
				}
			}
		} catch (final ParameterException e) {
			System.err.println(e.getMessage());
			System.err.println();
			System.err.println(getUsageMessage());
			return 1;
		} catch (final Exception e) {
			System.err.println(e.getMessage());
			return 1;
		}
	}

	public LanguagePropertiesManager() throws Exception {
		super(APPLICATION_NAME, VERSION);
	}

	private void execute(final ActionDefinition actionDefinition, final ConfigurationProperties applicationConfiguration) throws Exception {
		try {
			actionDefinitionToExecute = actionDefinition;

			final String[] configuredExcludeParts = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES).split(";");

			if (actionDefinition.getExportToExcel() != null) {
				final File excelFile = new File(actionDefinition.getExportToExcel());
				if (excelFile.exists() && !actionDefinition.isOverwrite()) {
					throw new LanguagePropertiesException("Export excel file '" + excelFile.getAbsolutePath() + "' already exists. Use 'overwrite' to replace existing file.");
				}

				final LoadLanguagePropertiesWorker loadLanguagePropertiesWorker = new LoadLanguagePropertiesWorker(this, excelFile, configuredExcludeParts);

				loadLanguagePropertiesWorker.setProgressDisplayDelayMilliseconds(2000);
				loadLanguagePropertiesWorker.run();

				// Get result to trigger possible Exception
				if (loadLanguagePropertiesWorker.get()) {
					// Load success
					System.out.println("Loaded language properties sets: " + loadLanguagePropertiesWorker.getLanguagePropertiesSetNames().size());
					System.out.println("Loaded language properties set names: " + Utilities.join(loadLanguagePropertiesWorker.getLanguagePropertiesSetNames(), ", "));
					System.out.println("Loaded language properties keys overall: " + loadLanguagePropertiesWorker.getLanguageProperties().size());
					System.out.println("Loaded language properties language signs: " + Utilities.join(loadLanguagePropertiesWorker.getAvailableLanguageSigns(), ", "));
					System.out.println("Comments in language properties: " + (loadLanguagePropertiesWorker.isCommentsFound() ? "Yes" : "No"));
				} else {
					throw new LanguagePropertiesException("Cancelled by user");
				}

				System.out.println();

				final ExportToExcelWorker exportToExcelWorker = new ExportToExcelWorker(this, loadLanguagePropertiesWorker.getLanguageProperties(), loadLanguagePropertiesWorker.getLanguagePropertiesSetNames(), new File(actionDefinition.getExcelFile()), actionDefinition.isOverwrite());

				exportToExcelWorker.setProgressDisplayDelayMilliseconds(2000);
				exportToExcelWorker.run();

				// Get result to trigger possible Exception
				if (exportToExcelWorker.get()) {
					// Export success
					System.out.println("Successfully exported in excel file: \"" + new File(actionDefinition.getExcelFile()).getAbsolutePath() + "\"");
				} else {
					throw new LanguagePropertiesException("Cancelled by user");
				}

				System.out.println();
			} else {
				final File excelFile = new File(actionDefinition.getImportFromExcel());
				if (!excelFile.exists() || !excelFile.isFile()) {
					throw new LanguagePropertiesException("Import excel file '" + excelFile.getAbsolutePath() + "' does not exist.");
				}

				final ImportFromExcelWorker importFromExcelWorker = new ImportFromExcelWorker(this, excelFile);

				importFromExcelWorker.setProgressDisplayDelayMilliseconds(2000);
				importFromExcelWorker.run();

				// Get result to trigger possible Exception
				if (importFromExcelWorker.get()) {
					// Import success
					System.out.println("Imported language properties sets: " + importFromExcelWorker.getLanguagePropertiesSetNames().size());
					System.out.println("Imported language properties set names: " + Utilities.join(importFromExcelWorker.getLanguagePropertiesSetNames(), ", "));
					System.out.println("Imported language properties keys overall: " + importFromExcelWorker.getLanguageProperties().size());
					System.out.println("Imported language properties language signs: " + Utilities.join(importFromExcelWorker.getAvailableLanguageSigns(), ", "));
					System.out.println("Comments in language properties: " + (importFromExcelWorker.isCommentsFound() ? "Yes" : "No"));
				} else {
					throw new LanguagePropertiesException("Cancelled by user");
				}

				System.out.println();

				File outputDirectory = null;
				if (actionDefinition.getOutputDirectory() != null) {
					outputDirectory = new File(actionDefinition.getOutputDirectory());
				}

				String languagePropertiesSetName;
				final List<String> languagePropertiesSetNames = ExcelHelper.getExcelSheetNames(excelFile);
				if (languagePropertiesSetNames.size() == 1) {
					languagePropertiesSetName = languagePropertiesSetNames.get(0);
				} else {
					languagePropertiesSetName = "Multiple";
				}

				final WriteLanguagePropertiesWorker writeLanguagePropertiesWorker = new WriteLanguagePropertiesWorker(this, importFromExcelWorker.getLanguageProperties(), languagePropertiesSetName, outputDirectory, configuredExcludeParts);

				writeLanguagePropertiesWorker.setProgressDisplayDelayMilliseconds(2000);
				writeLanguagePropertiesWorker.run();

				// Get result to trigger possible Exception
				if (writeLanguagePropertiesWorker.get()) {
					// Write properties success
					if (outputDirectory != null) {
						System.out.println("Successfully stored language properties in output directory: \"" + outputDirectory.getAbsolutePath() + "\"");
					} else {
						System.out.println("Successfully stored language properties in their defined directories");
					}
				} else {
					throw new LanguagePropertiesException("Cancelled by user");
				}

				System.out.println();
			}
		} catch (final ExecutionException e) {
			if (e.getCause() instanceof Exception) {
				throw (Exception) e.getCause();
			} else {
				throw e;
			}
		} catch (final Exception e) {
			throw e;
		}
	}

	/* (non-Javadoc)
	 * @see de.soderer.utilities.WorkerParentSimple#showUnlimitedProgress()
	 */
	@Override
	public void receiveUnlimitedProgressSignal() {
		// Do nothing
	}

	/* (non-Javadoc)
	 * @see de.soderer.utilities.WorkerParentSimple#showProgress(java.util.Date, long, long)
	 */
	@Override
	public void receiveProgressSignal(final LocalDateTime start, final long itemsToDo, final long itemsDone, final String itemsUnitSign) {
		if (actionDefinitionToExecute.isVerbose()) {
			printProgressBar(start, itemsToDo, itemsDone, itemsUnitSign);
		}
	}

	/* (non-Javadoc)
	 * @see de.soderer.utilities.WorkerParentSimple#showDone(java.util.Date, java.util.Date, long)
	 */
	@Override
	public void receiveDoneSignal(final LocalDateTime start, final LocalDateTime end, final long itemsDone, final String itemsUnitSign, final String resultText) {
		if (actionDefinitionToExecute.isVerbose()) {
			@SuppressWarnings("unused")
			int currentTerminalWidth;
			try {
				currentTerminalWidth = ConsoleUtilities.getTerminalSize().getWidth();
			} catch (@SuppressWarnings("unused") final Exception e) {
				currentTerminalWidth = 80;
			}

			if (Utilities.isNotBlank(resultText)) {
				System.out.println("Result: \n" + resultText);
			}

			System.out.println();
			System.out.println();
		}
	}

	/* (non-Javadoc)
	 * @see de.soderer.utilities.WorkerParentSimple#cancel()
	 */
	@Override
	public boolean cancel() {
		System.out.println("Canceled");
		return true;
	}

	@Override
	public void changeTitle(final String text) {
		if (actionDefinitionToExecute.isVerbose()) {
			System.out.println(text);
		}
	}

	@Override
	public void receiveUnlimitedSubProgressSignal() {
		// Do nothing
	}

	@Override
	public void receiveItemStartSignal(final String itemName, final String description) {
		if (actionDefinitionToExecute.isVerbose()) {
			System.out.println(description);
		}
	}

	@Override
	public void receiveItemProgressSignal(final LocalDateTime itemStart, final long subItemToDo, final long subItemDone, final String itemsUnitSign) {
		if (actionDefinitionToExecute.isVerbose() && subItemToDo > 0) {
			printProgressBar(itemStart, subItemToDo, subItemDone, itemsUnitSign);
		}
	}

	private void printProgressBar(final LocalDateTime itemStart, final long subItemToDo, final long subItemDone, final String itemsUnitSign) {
		try {
			if (ConsoleUtilities.getConsoleType() == ConsoleType.ANSI) {
				int currentTerminalWidth;
				try {
					currentTerminalWidth = ConsoleUtilities.getTerminalSize().getWidth();
				} catch (@SuppressWarnings("unused") final Exception e) {
					currentTerminalWidth = 80;
				}

				ConsoleUtilities.saveCurrentCursorPosition();

				if (currentTerminalWidth < previousTerminalWidth) {
					System.out.print("\r" + Utilities.repeat(" ", currentTerminalWidth));
				}
				previousTerminalWidth = currentTerminalWidth;

				ConsoleUtilities.moveCursorToSavedPosition();

				System.out.print(ConsoleUtilities.getConsoleProgressString(currentTerminalWidth - 1, itemStart, subItemToDo, subItemDone, itemsUnitSign));

				ConsoleUtilities.moveCursorToSavedPosition();
			} else if (ConsoleUtilities.getConsoleType() == ConsoleType.TEST) {
				System.out.print(ConsoleUtilities.getConsoleProgressString(80 - 1, itemStart, subItemToDo, subItemDone, itemsUnitSign) + "\n");
			} else {
				System.out.print("\r" + ConsoleUtilities.getConsoleProgressString(80 - 1, itemStart, subItemToDo, subItemDone, itemsUnitSign) + "\r");
			}
		} catch (final Throwable e) {
			// Do nothing => no progress bar
			e.printStackTrace();
		}
	}

	@Override
	public void receiveItemDoneSignal(final LocalDateTime itemStart, final LocalDateTime itemEnd, final long subItemsDone, final String itemsUnitSign, final String resultText) {
		if (actionDefinitionToExecute.isVerbose()) {
			if (subItemsDone > 0) {
				printProgressBar(itemStart, subItemsDone, subItemsDone, itemsUnitSign);
			}
			System.out.println();
			if (itemsUnitSign != null) {
				System.out.println("End (" + Utilities.getHumanReadableNumber(subItemsDone, itemsUnitSign, true, 5, true, Locale.ENGLISH) + " done in " + DateUtilities.getHumanReadableTimespanEnglish(Duration.between(itemStart, itemEnd), true) + ")");
			} else {
				System.out.println("End (" + NumberFormat.getNumberInstance(Locale.ENGLISH).format(subItemsDone) + " data items done in " + DateUtilities.getHumanReadableTimespanEnglish(Duration.between(itemStart, itemEnd), true) + ")");
			}

			if (Utilities.isNotBlank(resultText)) {
				System.out.println("Result: \n" + resultText);
			}

			System.out.println();
		}
	}
}
