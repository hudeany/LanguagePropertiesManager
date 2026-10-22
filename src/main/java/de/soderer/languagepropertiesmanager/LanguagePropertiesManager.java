package de.soderer.languagepropertiesmanager;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import de.soderer.applicationbase.ApplicationConfigurationProperties;
import de.soderer.applicationbase.ApplicationUpdateUtilities;
import de.soderer.applicationbase.UpdateableConsoleApplication;
import de.soderer.console.ConsoleType;
import de.soderer.console.ConsoleUtilities;
import de.soderer.languagepropertiesmanager.dlg.LanguagePropertiesManagerDialog;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.worker.DeepLHelper;
import de.soderer.languagepropertiesmanager.worker.ExportToCsvWorker;
import de.soderer.languagepropertiesmanager.worker.ExportToExcelWorker;
import de.soderer.languagepropertiesmanager.worker.ImportFromCsvWorker;
import de.soderer.languagepropertiesmanager.worker.ImportFromExcelWorker;
import de.soderer.languagepropertiesmanager.worker.LoadLanguagePropertiesWorker;
import de.soderer.languagepropertiesmanager.worker.WriteLanguagePropertiesWorker;
import de.soderer.swing.ErrorDialog;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.ParameterException;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.Version;
import de.soderer.utilities.YesNoAsk;
import de.soderer.utilities.worker.WorkerParentDual;

// TODO: Excel file Diff

/**
 * Main class of the LanguagePropertiesManager: starts the GUI or executes the
 * command line actions (import from or export to Excel or CSV) and prints
 * their progress on the console.
 */
public class LanguagePropertiesManager extends UpdateableConsoleApplication implements WorkerParentDual {
	/** The Constant APPLICATION_NAME. */
	public static final String APPLICATION_NAME = "LanguagePropertiesManager";
	/** Name of the start class used as window class, e.g. for pinning the application to the Linux Gnome dock */
	public static final String APPLICATION_STARTUPCLASS_NAME = "de-soderer-LanguagePropertiesManager";
	/** Email address for error reports */
	public static final String APPLICATION_ERROR_EMAIL_ADRESS = "LanguagePropertiesManager.Error@soderer.de";

	/** Keystore of the application, e.g. for trusted certificates */
	public static final File KEYSTORE_FILE = new File(System.getProperty("user.home") + File.separator + "." + APPLICATION_NAME + File.separator + "." + APPLICATION_NAME + ".keystore");

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

	/** Resource file with the command line help */
	public static final String HELP_RESOURCE_FILE_DEFAULT = "/help.txt";

	/** Resource file with the German command line help (currently the same as the default) */
	public static final String HELP_RESOURCE_FILE_DE = "/help.txt";

	/** The Constant CONFIGURATION_FILE. */
	public static final File CONFIGURATION_FILE = new File(System.getProperty("user.home") + File.separator + "." + APPLICATION_NAME + ".config");

	/** Configuration key: repair punctuation on cleanup */
	public static final String CONFIG_CLEANUP_REPAIRPUNCTUATION = "Cleanup.RepairPunctuation";

	/** Configuration key: recent check usage settings (directory, file pattern, usage pattern) */
	public static final String CONFIG_PREVIOUS_CHECK_USAGE = "CheckUsage.Previous";

	/** Configuration key: recently opened properties files and directories */
	public static final String CONFIG_RECENT_PROPERTIES = "Recent";

	/** Configuration key: path parts (separated by ';') of files to ignore when loading or saving a directory */
	public static final String CONFIG_OPEN_DIR_EXCLUDES = "OpenDirExcludes";

	/** Configuration key: base URL of the DeepL API */
	public static final String CONFIG_DEEPL_BASEURL = "DeepL_BaseUrl";

	/** Configuration key: API key for DeepL */
	public static final String CONFIG_DEEPL_APIKEY = "DeepL_ApiKey";

	/** Configuration key: file extension of the properties files */
	public static final String CONFIG_PROPERTIES_FILE_EXTENSION = "PropertiesFileExtension";

	/** Configuration key ({@link YesNoAsk}): whether comments are ignored when loading and importing */
	public static final String CONFIG_IGNORE_COMMENTS = "IgnoreComments";

	/** Configuration key: optional CSV file with values, which must not be translated by DeepL (see {@link TranslationConstants}) */
	public static final String CONFIG_TRANSLATION_CONSTANTS_FILE = "TranslationConstantsFile";

	/**
	 * Configuration key ({@link YesNoAsk}): whether apostrophes are escaped by doubling ("''") in the properties files, as
	 * needed by java.text.MessageFormat. Only the properties files contain the doubled apostrophes, the GUI, the CSV and
	 * the Excel files work with single apostrophes.
	 */
	public static final String CONFIG_ESCAPE_APOSTROPHES_BY_DOUBLING = "EscapeApostrophesByDoubling";

	/**
	 * Configuration key ({@link YesNoAsk}): whether the properties files are stored as UTF-8 files (without BOM) without
	 * escaping non-ASCII characters as "\\uXXXX" (supported by java.util.PropertyResourceBundle since Java 9). If not set,
	 * the files are stored as ISO-8859-1 files with all non-ASCII characters escaped.
	 */
	public static final String CONFIG_STORE_AS_UTF8 = "StoreAsUtf8";

	/** Whether keys existing only in the files on disk are kept when saving (yes, no or ask) */
	public static final String CONFIG_KEEP_EXISTING_KEYS = "KeepExistingKeysOnSave";

	/** Storage of last recently used filepath for open and save files **/
	public static final String CONFIG_LAST_IMPORT_EXPORT_DIRECTORY = "LastImportExportDirectory";

//	/** Storage of last recently used filepath for merge import files **/
	public static final String CONFIG_LAST_IMPORT_SOURCE = "LastImportSource";

	private int previousTerminalWidth = 0;

	private ActionDefinition actionDefinitionToExecute;

	/**
	 * Adds the default values of all missing configuration entries.
	 * Must be called on every application start, because it also registers the {@link YesNoAsk} keys.
	 *
	 * @param applicationConfiguration
	 *            the configuration to complete
	 */
	public static void setupDefaultConfig(final ConfigurationProperties applicationConfiguration) {
		applicationConfiguration.setupDefaultConfig();

		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_CLEANUP_REPAIRPUNCTUATION)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_CLEANUP_REPAIRPUNCTUATION, true);
		}
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES, "__;/src/test/;\\src\\test\\;/bin/;\\bin\\");
		}
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_DEEPL_BASEURL)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_DEEPL_BASEURL, DeepLHelper.DEEPL_BASEURL_FREE_DEFAULT);
		}
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_DEEPL_APIKEY, "");
		}
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION)) {
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_PROPERTIES_FILE_EXTENSION, LanguagePropertiesFileSetReader.DEFAULT_PROPERTIES_FILE_EXTENSION);
		}
		if (!applicationConfiguration.containsKey(LanguagePropertiesManager.CONFIG_TRANSLATION_CONSTANTS_FILE)) {
			// Optional CSV file with values that must not be altered by DeepL translation
			applicationConfiguration.set(LanguagePropertiesManager.CONFIG_TRANSLATION_CONSTANTS_FILE, "");
		}

		// Yes / no / ask values, off by default, so existing properties files are read and written unchanged.
		// Existing boolean values ("true" / "false") stay valid.
		applicationConfiguration.ensureYesNoAsk(LanguagePropertiesManager.CONFIG_IGNORE_COMMENTS, YesNoAsk.NO);
		applicationConfiguration.ensureYesNoAsk(LanguagePropertiesManager.CONFIG_ESCAPE_APOSTROPHES_BY_DOUBLING, YesNoAsk.NO);
		applicationConfiguration.ensureYesNoAsk(LanguagePropertiesManager.CONFIG_STORE_AS_UTF8, YesNoAsk.NO);

		// Ask by default, as before this option existed
		applicationConfiguration.ensureYesNoAsk(LanguagePropertiesManager.CONFIG_KEEP_EXISTING_KEYS, YesNoAsk.ASK);

		applicationConfiguration.set(LanguagePropertiesManager.CONFIG_LAST_IMPORT_EXPORT_DIRECTORY, "");
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
	 * @param args
	 *            the command line arguments
	 * @return exit code of the application, or -1 if the GUI was opened and keeps running
	 */
	protected static int _main(final String[] args) {
		ApplicationUpdateUtilities.removeUpdateLeftovers();

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

		ApplicationConfigurationProperties applicationConfiguration;
		try {
			applicationConfiguration = new ApplicationConfigurationProperties(LanguagePropertiesManager.APPLICATION_NAME, true);
			LanguagePropertiesManager.setupDefaultConfig(applicationConfiguration);
			Locale.setDefault(toLocale(applicationConfiguration.get(ConfigurationProperties.CONFIG_KEY_LANGUAGE), Locale.ENGLISH));
		} catch (@SuppressWarnings("unused") final Exception e) {
			System.err.println("Invalid application configuration");
			return 1;
		}

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
					} else if ("update".equalsIgnoreCase(arguments[i]) && i == 0 && arguments.length <= 3) {
						final LanguagePropertiesManager languagePropertiesManager = new LanguagePropertiesManager();
						if (arguments.length > i + 2) {
							ApplicationUpdateUtilities.executeUpdate(languagePropertiesManager, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, arguments[i + 1], arguments[i + 2].toCharArray(), null, false, false);
						} else if (arguments.length > i + 1) {
							ApplicationUpdateUtilities.executeUpdate(languagePropertiesManager, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, arguments[i + 1], null, null, false, false);
						} else {
							ApplicationUpdateUtilities.executeUpdate(languagePropertiesManager, LanguagePropertiesManager.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION, LanguagePropertiesManager.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, false, false);
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
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Only one of parameters importFromExcel and exportToExcel is allowed");
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
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Only one of parameters importFromExcel and exportToExcel is allowed");
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
				} else if ("-importFromCsv".equalsIgnoreCase(arguments[i])) {
					i++;
					if (i >= arguments.length) {
						throw new ParameterException(arguments[i - 1], "Missing parameter for importFromCsv");
					} else if (Utilities.isBlank(arguments[i])) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Invalid parameter for importFromCsv");
					} else if (actionDefinition.getImportFromCsv() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter importFromCsv");
					} else if (actionDefinition.getExportToCsv() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Only one of parameters importFromCsv and exportToCsv is allowed");
					} else {
						actionDefinition.setImportFromCsv(arguments[i]);
					}
					wasAllowedParam = true;
				} else if ("-exportToCsv".equalsIgnoreCase(arguments[i])) {
					i++;
					if (i >= arguments.length) {
						throw new ParameterException(arguments[i - 1], "Missing parameter for exportToCsv");
					} else if (Utilities.isBlank(arguments[i])) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Invalid parameter for exportToCsv");
					} else if (actionDefinition.getExportToCsv() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter exportToCsv");
					} else if (actionDefinition.getImportFromCsv() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Only one of parameters importFromCsv and exportToCsv is allowed");
					} else {
						actionDefinition.setExportToCsv(arguments[i]);
					}
					wasAllowedParam = true;
				} else if ("-csvFile".equalsIgnoreCase(arguments[i])) {
					i++;
					if (i >= arguments.length) {
						throw new ParameterException(arguments[i - 1], "Missing parameter for csvFile");
					} else if (Utilities.isBlank(arguments[i])) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Invalid parameter for csvFile");
					} else if (actionDefinition.getCsvFile() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter csvFile");
					} else {
						actionDefinition.setCsvFile(arguments[i]);
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
				} else if ("-propertiesFileExtension".equalsIgnoreCase(arguments[i])) {
					i++;
					if (i >= arguments.length) {
						throw new ParameterException(arguments[i - 1], "Missing parameter for propertiesFileExtension");
					} else if (Utilities.isBlank(arguments[i])) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Invalid parameter for propertiesFileExtension");
					} else if (actionDefinition.getPropertiesFileExtension() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter propertiesFileExtension");
					} else {
						actionDefinition.setPropertiesFileExtension(arguments[i]);
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
				// Allows pinning the application to the Linux Gnome dock (needs "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
				de.soderer.swing.SwingUtilities.setAwtWmClass(APPLICATION_STARTUPCLASS_NAME);
				de.soderer.swing.SwingUtilities.setSystemLookAndFeel();

				final ApplicationConfigurationProperties guiApplicationConfiguration = applicationConfiguration;
				final int[] guiReturnCode = new int[] { -1 };
				javax.swing.SwingUtilities.invokeAndWait(() -> {
					try {
						final LanguagePropertiesManagerDialog mainDialog = new LanguagePropertiesManagerDialog(guiApplicationConfiguration);
						mainDialog.setVisible(true);
					} catch (final Exception ex) {
						guiReturnCode[0] = 1;
						try {
							new ErrorDialog(null, LanguagePropertiesManager.APPLICATION_NAME, LanguagePropertiesManager.VERSION.toString(), LanguagePropertiesManager.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
						} catch (final Exception dialogException) {
							System.out.println(ex.toString());
							ex.printStackTrace();
							dialogException.printStackTrace();
						}
					}
				});

				// -1: The GUI keeps running on the Swing event dispatch thread, so main() must not call System.exit()
				return guiReturnCode[0];
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

	/**
	 * Converts a language code like "de", "de_AT" or "de-AT" into a Locale.
	 *
	 * @param languageCode
	 *            language code with optional region and variant, separated by '_' or '-'
	 * @param defaultLocale
	 *            locale returned for an empty or invalid language code
	 * @return the locale
	 */
	public static Locale toLocale(final String languageCode, final Locale defaultLocale) {
		if (languageCode == null || languageCode.isBlank()) {
			return defaultLocale;
		}

		final String[] parts = languageCode.trim().split("[_-]", 3);
		try {
			final Locale.Builder builder = new Locale.Builder().setLanguage(parts[0]);
			if (parts.length > 1) {
				builder.setRegion(parts[1]);
			}
			if (parts.length > 2) {
				builder.setVariant(parts[2]);
			}
			return builder.build();
		} catch (@SuppressWarnings("unused") final IllformedLocaleException e) {
			return defaultLocale;
		}
	}

	/**
	 * Resolves a {@link YesNoAsk} configuration value for a command line action. For "ask" the user is asked on the
	 * console.
	 *
	 * @param configuration
	 *            configuration of the application
	 * @param configKey
	 *            configuration key of the YesNoAsk value
	 * @param question
	 *            question shown on the console for "ask"
	 * @return the decision
	 * @throws LanguagePropertiesException
	 *             if "ask" is configured but no interactive console is available, or if the user cancelled
	 */
	private static boolean resolveConfigurationChoice(final ConfigurationProperties configuration, final String configKey, final String question) throws LanguagePropertiesException {
		final YesNoAsk configuredValue = configuration.getYesNoAsk(configKey, YesNoAsk.NO);
		if (configuredValue == YesNoAsk.ASK && System.console() == null) {
			throw new LanguagePropertiesException("Configuration value '" + configKey + "' is set to 'ask', but no interactive console is available");
		}
		final Boolean decision = configuredValue.resolve(() -> askOnConsole(question));
		if (decision == null) {
			throw new LanguagePropertiesException("Cancelled by user");
		}
		return decision;
	}

	/**
	 * Asks a yes/no question on the console until a valid answer is given.
	 *
	 * @param question
	 *            question to show
	 * @return the decision, or null on end of input (cancel)
	 */
	private static Boolean askOnConsole(final String question) {
		while (true) {
			final String answer = System.console().readLine("%s [y/n]: ", question);
			if (answer == null) {
				return null;
			}
			final String trimmedAnswer = answer.trim();
			if ("y".equalsIgnoreCase(trimmedAnswer) || "yes".equalsIgnoreCase(trimmedAnswer)) {
				return Boolean.TRUE;
			} else if ("n".equalsIgnoreCase(trimmedAnswer) || "no".equalsIgnoreCase(trimmedAnswer)) {
				return Boolean.FALSE;
			}
		}
	}

	/**
	 * Creates the application instance for command line actions and updates.
	 *
	 * @throws Exception
	 *             if the application cannot be initialized
	 */
	public LanguagePropertiesManager() throws Exception {
		super(APPLICATION_NAME, VERSION);
	}

	private void execute(final ActionDefinition actionDefinition, final ConfigurationProperties applicationConfiguration) throws Exception {
		try {
			actionDefinitionToExecute = actionDefinition;
			if (actionDefinitionToExecute.getPropertiesFileExtension() == null) {
				actionDefinitionToExecute.setPropertiesFileExtension(LanguagePropertiesFileSetReader.DEFAULT_PROPERTIES_FILE_EXTENSION);
			}

			final String[] configuredExcludeParts = applicationConfiguration.get(LanguagePropertiesManager.CONFIG_OPEN_DIR_EXCLUDES).split(";");

			if (actionDefinition.getExportToExcel() != null) {
				final File propertiesFile = new File(actionDefinition.getExportToExcel());

				final File excelFile = new File(actionDefinition.getExcelFile());
				if (excelFile.exists() && !actionDefinition.isOverwrite()) {
					throw new LanguagePropertiesException("Export Excel file '" + excelFile.getAbsolutePath() + "' already exists. Use 'overwrite' to replace existing file.");
				}

				// Resolved once for the whole action, before any work is done
				final boolean ignoreComments = resolveConfigurationChoice(applicationConfiguration, CONFIG_IGNORE_COMMENTS, "Ignore comments of the properties files?");
				final boolean escapeApostrophesByDoubling = resolveConfigurationChoice(applicationConfiguration, CONFIG_ESCAPE_APOSTROPHES_BY_DOUBLING, "Are apostrophes escaped by doubling in the properties files?");

				final LoadLanguagePropertiesWorker loadLanguagePropertiesWorker = new LoadLanguagePropertiesWorker(this, propertiesFile, configuredExcludeParts, actionDefinition.getPropertiesFileExtension());
				loadLanguagePropertiesWorker.setReadComments(!ignoreComments);
				// Doubled apostrophes are only used in the properties files, the export gets single apostrophes
				loadLanguagePropertiesWorker.setEscapeApostrophesByDoubling(escapeApostrophesByDoubling);

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
					System.out.println("Successfully exported in Excel file: \"" + new File(actionDefinition.getExcelFile()).getAbsolutePath() + "\"");
				} else {
					throw new LanguagePropertiesException("Cancelled by user");
				}

				System.out.println();
			} else if (actionDefinition.getImportFromExcel() != null) {
				final File excelFile = new File(actionDefinition.getImportFromExcel());
				if (!excelFile.exists() || !excelFile.isFile()) {
					throw new LanguagePropertiesException("Import Excel file '" + excelFile.getAbsolutePath() + "' does not exist.");
				}

				// Resolved once for the whole action (import and write), before any work is done
				final boolean ignoreComments = resolveConfigurationChoice(applicationConfiguration, CONFIG_IGNORE_COMMENTS, "Ignore comments?");
				final boolean escapeApostrophesByDoubling = resolveConfigurationChoice(applicationConfiguration, CONFIG_ESCAPE_APOSTROPHES_BY_DOUBLING, "Escape apostrophes by doubling in the properties files?");
				final boolean storeAsUtf8 = resolveConfigurationChoice(applicationConfiguration, CONFIG_STORE_AS_UTF8, "Store properties files as UTF-8 (instead of ISO-8859-1 with unicode escapes)?");

				final ImportFromExcelWorker importFromExcelWorker = new ImportFromExcelWorker(this, excelFile);
				importFromExcelWorker.setIgnoreComments(ignoreComments);

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

				final String languagePropertiesSetName = importFromExcelWorker.getLanguagePropertiesSetName();

				final WriteLanguagePropertiesWorker writeLanguagePropertiesWorker = new WriteLanguagePropertiesWorker(this, importFromExcelWorker.getLanguageProperties(), languagePropertiesSetName, outputDirectory, configuredExcludeParts, actionDefinition.isExtendAndKeepExistingProperties(), actionDefinition.getPropertiesFileExtension());
				writeLanguagePropertiesWorker.setReadComments(!ignoreComments);
				writeLanguagePropertiesWorker.setEscapeApostrophesByDoubling(escapeApostrophesByDoubling);
				writeLanguagePropertiesWorker.setStoreAsUtf8(storeAsUtf8);

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
			} else if (actionDefinition.getExportToCsv() != null) {
				final File propertiesFile = new File(actionDefinition.getExportToCsv());

				final File csvFile = new File(actionDefinition.getCsvFile());
				if (csvFile.exists() && !actionDefinition.isOverwrite()) {
					throw new LanguagePropertiesException("Export CSV file '" + csvFile.getAbsolutePath() + "' already exists. Use 'overwrite' to replace existing file.");
				}

				// Resolved once for the whole action, before any work is done
				final boolean ignoreComments = resolveConfigurationChoice(applicationConfiguration, CONFIG_IGNORE_COMMENTS, "Ignore comments of the properties files?");
				final boolean escapeApostrophesByDoubling = resolveConfigurationChoice(applicationConfiguration, CONFIG_ESCAPE_APOSTROPHES_BY_DOUBLING, "Are apostrophes escaped by doubling in the properties files?");

				final LoadLanguagePropertiesWorker loadLanguagePropertiesWorker = new LoadLanguagePropertiesWorker(this, propertiesFile, configuredExcludeParts, actionDefinition.getPropertiesFileExtension());
				loadLanguagePropertiesWorker.setReadComments(!ignoreComments);
				// Doubled apostrophes are only used in the properties files, the export gets single apostrophes
				loadLanguagePropertiesWorker.setEscapeApostrophesByDoubling(escapeApostrophesByDoubling);

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

				final ExportToCsvWorker exportToCsvWorker = new ExportToCsvWorker(this, loadLanguagePropertiesWorker.getLanguageProperties(), loadLanguagePropertiesWorker.getLanguagePropertiesSetNames(), new File(actionDefinition.getCsvFile()), actionDefinition.isOverwrite());

				exportToCsvWorker.setProgressDisplayDelayMilliseconds(2000);
				exportToCsvWorker.run();

				// Get result to trigger possible Exception
				if (exportToCsvWorker.get()) {
					// Export success
					System.out.println("Successfully exported in CSV file: \"" + new File(actionDefinition.getCsvFile()).getAbsolutePath() + "\"");
				} else {
					throw new LanguagePropertiesException("Cancelled by user");
				}

				System.out.println();
			} else if (actionDefinition.getImportFromCsv() != null) {
				final File csvFile = new File(actionDefinition.getImportFromCsv());
				if (!csvFile.exists() || !csvFile.isFile()) {
					throw new LanguagePropertiesException("Import CSV file '" + csvFile.getAbsolutePath() + "' does not exist.");
				}

				// Resolved once for the whole action (import and write), before any work is done
				final boolean ignoreComments = resolveConfigurationChoice(applicationConfiguration, CONFIG_IGNORE_COMMENTS, "Ignore comments?");
				final boolean escapeApostrophesByDoubling = resolveConfigurationChoice(applicationConfiguration, CONFIG_ESCAPE_APOSTROPHES_BY_DOUBLING, "Escape apostrophes by doubling in the properties files?");
				final boolean storeAsUtf8 = resolveConfigurationChoice(applicationConfiguration, CONFIG_STORE_AS_UTF8, "Store properties files as UTF-8 (instead of ISO-8859-1 with unicode escapes)?");

				final ImportFromCsvWorker importFromCsvWorker = new ImportFromCsvWorker(this, csvFile);
				importFromCsvWorker.setIgnoreComments(ignoreComments);

				importFromCsvWorker.setProgressDisplayDelayMilliseconds(2000);
				importFromCsvWorker.run();

				// Get result to trigger possible Exception
				if (importFromCsvWorker.get()) {
					// Import success
					System.out.println("Imported language properties sets: " + importFromCsvWorker.getLanguagePropertiesSetNames().size());
					System.out.println("Imported language properties set names: " + Utilities.join(importFromCsvWorker.getLanguagePropertiesSetNames(), ", "));
					System.out.println("Imported language properties keys overall: " + importFromCsvWorker.getLanguageProperties().size());
					System.out.println("Imported language properties language signs: " + Utilities.join(importFromCsvWorker.getAvailableLanguageSigns(), ", "));
					System.out.println("Comments in language properties: " + (importFromCsvWorker.isCommentsFound() ? "Yes" : "No"));
				} else {
					throw new LanguagePropertiesException("Cancelled by user");
				}

				System.out.println();

				File outputDirectory = null;
				if (actionDefinition.getOutputDirectory() != null) {
					outputDirectory = new File(actionDefinition.getOutputDirectory());
				}

				final String languagePropertiesSetName = importFromCsvWorker.getLanguagePropertiesSetName();

				final WriteLanguagePropertiesWorker writeLanguagePropertiesWorker = new WriteLanguagePropertiesWorker(this, importFromCsvWorker.getLanguageProperties(), languagePropertiesSetName, outputDirectory, configuredExcludeParts, actionDefinition.isExtendAndKeepExistingProperties(), actionDefinition.getPropertiesFileExtension());
				writeLanguagePropertiesWorker.setReadComments(!ignoreComments);
				writeLanguagePropertiesWorker.setEscapeApostrophesByDoubling(escapeApostrophesByDoubling);
				writeLanguagePropertiesWorker.setStoreAsUtf8(storeAsUtf8);

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
			if (e.getCause() != null && e.getCause() instanceof Exception) {
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
