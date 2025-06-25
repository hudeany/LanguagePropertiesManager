package de.soderer.languagepropertiesmanager.storage;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.swt.widgets.Text;

import de.soderer.utilities.Utilities;
import de.soderer.utilities.WildcardFilenameFilter;
import de.soderer.utilities.collection.IndexedLinkedHashMap;
import de.soderer.utilities.collection.MapUtilities;
import de.soderer.utilities.collection.UniqueFifoQueuedList;

public class PropertiesStorage {
	public static final String LANGUAGE_SIGN_DEFAULT = "default";

	public static final String SORT_SIGN_DEFAULT = "default";
	public static final String SORT_SIGN_ORIGINAL_INDEX = "index";
	public static final String SORT_SIGN_KEY = "key";

	public static final String PROPERTIES_FILEEXTENSION = "properties";
	public static final String PROPERTIES_FILEPATTERN = "*." + PROPERTIES_FILEEXTENSION;

	private static final Pattern LANGUAGEANDCOUNTRYPATTERN = Pattern.compile("^.*[a-zA-Z0-9������_]+(_[a-zA-Z]{2}){2}$");
	private static final Pattern LANGUAGEPATTERN = Pattern.compile("^.*[a-zA-Z0-9������_]+_[a-zA-Z]{2}$");

	public static final String DEFAULT_STORAGE_SEPARATOR = " = ";

	private final File propertiesDirectory;
	private FileFilter fileFilter;
	private final boolean readKeysCaseInsensitive;
	private Map<String, PropertiesLanguageFileReader> languageFiles;
	private IndexedLinkedHashMap<String, Property> properties;
	private int nextOrderIndex = 0;

	public PropertiesStorage(final String directory, final String popertySetName, final boolean readKeysCaseInsensitive) throws Exception {
		propertiesDirectory = new File(directory);
		try {
			fileFilter = new WildcardFilenameFilter(popertySetName + PROPERTIES_FILEPATTERN);
		} catch (final Exception e) {
			throw new Exception("Given filePattern is invalid", e);
		}
		this.readKeysCaseInsensitive = readKeysCaseInsensitive;

		if (!propertiesDirectory.isDirectory())
			throw new Exception("Given path is not a directory");
		else if (!propertiesDirectory.exists())
			throw new Exception("Given directory does not exist");
	}

	public void load() throws Exception {
		languageFiles = new HashMap<>();

		for (final File propertyFile : propertiesDirectory.listFiles(fileFilter)) {
			FileInputStream fileInputStream = null;
			PropertiesLanguageFileReader propertiesLanguageFile;
			try {
				fileInputStream = new FileInputStream(propertyFile);
				propertiesLanguageFile = new PropertiesLanguageFileReader();
				propertiesLanguageFile.load(fileInputStream, readKeysCaseInsensitive);
			} catch (final Exception e) {
				throw new Exception("Error when reading file: " + propertyFile.getAbsolutePath(), e);
			} finally {
				Utilities.closeQuietly(fileInputStream);
			}
			languageFiles.put(propertyFile.getAbsolutePath(), propertiesLanguageFile);
		}

		languageFiles = MapUtilities.sort(languageFiles, new Comparator<String>(){
			@Override
			public int compare(final String arg0, final String arg1) {
				if (arg0.length() != arg1.length())
					return Integer.valueOf(arg0.length()).compareTo(arg1.length());
				else return arg0.compareTo(arg1);
			}
		});

		final IndexedLinkedHashMap<String, Property> returnValues = new IndexedLinkedHashMap<>();
		for (final Entry<String, PropertiesLanguageFileReader> propertiesLanguageFileEntry : languageFiles.entrySet()) {
			final String languageSign = PropertiesLanguageFileReader.getLanguageSignOfFilename(propertiesLanguageFileEntry.getKey());
			for (final Entry<String, String> entry : propertiesLanguageFileEntry.getValue().getEntries().entrySet()) {
				Property property = returnValues.get(entry.getKey());
				if (property == null) {
					property = new Property(entry.getKey());
					property.setOriginalIndex(nextOrderIndex++);
					returnValues.put(entry.getKey(), property);
				}
				property.setLanguageValue(languageSign, entry.getValue());
			}
		}

		properties = MapUtilities.sort(returnValues, new Property.KeyComparator(true));
	}

	public void remove(final String key) throws Exception {
		properties.remove(key);
	}

	public void add(final Property property) throws Exception {
		properties.put(property.getKey(), property);
		properties = MapUtilities.sort(properties, new Property.KeyComparator(true));
	}

	public void save(final String directory, String separator, final boolean sortByOrgIndex) throws Exception {
		Map<String, Property> propertiesToSave;
		if (sortByOrgIndex)
			propertiesToSave = MapUtilities.sortEntries(properties, new Property.OriginalIndexComparator(true));
		else propertiesToSave = properties;

		if (Utilities.isEmpty(separator))
			separator = DEFAULT_STORAGE_SEPARATOR;

		for (final String propertiesLanguageFileName : languageFiles.keySet()) {
			final String languageSign = PropertiesLanguageFileReader.getLanguageSignOfFilename(propertiesLanguageFileName);
			final String fileName = new File(propertiesLanguageFileName).getName();
			final File propertyFile = new File(directory + File.separator + fileName);
			try {
				if (propertyFile.exists()) propertyFile.delete();
				try (FileOutputStream fileOutputStream = new FileOutputStream(propertyFile)) {
					for (final Entry<String, Property> entry : propertiesToSave.entrySet()) {
						if (entry.getValue().getLanguageValue(languageSign) != null) {
							final StringBuilder line = new StringBuilder();
							line.append(entry.getValue().getKey());
							line.append(separator);
							line.append(entry.getValue().getLanguageValue(languageSign));
							line.append("\n");
							fileOutputStream.write(line.toString().getBytes("UTF-8"));
						}
					}
				}
			} catch (final Exception e) {
				throw new Exception("Error when writing file: " + propertyFile.getAbsolutePath(), e);
			}
		}
	}

	public Map<String, PropertiesLanguageFileReader> getLanguageFiles() {
		return languageFiles;
	}

	public List<String> getLanguageSigns() {
		final List<String> returnValues = new ArrayList<>();

		if (propertiesDirectory != null) {
			for (final String fileName : languageFiles.keySet()) {
				returnValues.add(PropertiesLanguageFileReader.getLanguageSignOfFilename(fileName));
			}

			Collections.sort(returnValues, new Comparator<String>() {
				@Override
				public int compare(final String value1, final String value2) {
					if (LANGUAGE_SIGN_DEFAULT.equalsIgnoreCase(value1))
						return -1;
					else if (LANGUAGE_SIGN_DEFAULT.equalsIgnoreCase(value2))
						return 1;
					else
						return value1.compareTo(value2);
				}
			});
		}

		return returnValues;
	}

	public IndexedLinkedHashMap<String, Property> getProperties() {
		return properties;
	}

	public void removeLanguage(final String languageSign) throws Exception {
		for (final Property property : properties.values()) {
			property.removeLanguageValue(languageSign);
		}
		languageFiles.remove(languageSign);
	}

	public void addLanguage(final String languageSign) throws Exception {
		String defaultFileName = null;
		for (final String fileName : languageFiles.keySet()) {
			if (LANGUAGE_SIGN_DEFAULT.equalsIgnoreCase(PropertiesLanguageFileReader.getLanguageSignOfFilename(fileName))) {
				defaultFileName = fileName;
			}
		}
		if (defaultFileName != null) {
			new File(defaultFileName.substring(0, defaultFileName.length() - 11) + languageSign + ".properties").createNewFile();
		}
		load();
	}

	public boolean cleanUp(final boolean repairpunctuation) throws Exception {
		boolean dataWasChanged = false;

		for (final Property property : properties.values()) {
			// Store original values for later change check
			final HashMap<String, String> originalData = new HashMap<>();
			for (final String sign : getLanguageSigns()) {
				originalData.put(sign, property.getLanguageValue(sign));
			}

			for (final String sign : getLanguageSigns()) {
				String value = property.getLanguageValue(sign);
				if (value != null) {
					// clear blank before and after "<br>"
					value = value.replace(" <br>", "<br>").replace("<br> ", "<br>");
					// remove "<br>" at end
					if (value.endsWith("<br>")) {
						value = value.substring(0, value.length() - 4).trim();
					}
					// remove "<br />" at end
					if (value.endsWith("<br />")) {
						value = value.substring(0, value.length() - 6).trim();
					}
					property.setLanguageValue(sign, value);
				}

				// Clear empty values
				if (Utilities.isEmpty(property.getLanguageValue(sign))) {
					property.removeLanguageValue(sign);
				}
			}

			if (repairpunctuation) {
				// Check exclamationmark, questionmark and fullstop unification
				boolean hasExclamationEnd = false;
				boolean hasQuestionEnd = false;
				boolean hasFullstopEnd = false;
				boolean hasColonEnd = false;
				for (final String sign : getLanguageSigns()) {
					final String value = property.getLanguageValue(sign);
					if (value != null) {
						if (!sign.equalsIgnoreCase("zh")) {
							if (value.endsWith("!"))
								hasExclamationEnd = true;
							else if (value.endsWith("?"))
								hasQuestionEnd = true;
							else if (value.endsWith(":"))
								hasColonEnd = true;
							else if (value.endsWith("."))
								hasFullstopEnd = true;
						} else {
							if (value.endsWith("\\uFF01")) // "!"
								hasExclamationEnd = true;
							else if (value.endsWith("\\uFF1F")) // "?"
								hasQuestionEnd = true;
							else if (value.endsWith("\\uFF1A")) // ":"
								hasColonEnd = true;
							else if (value.endsWith("\\u3002")) // "."
								hasFullstopEnd = true;
						}
					}
				}
				for (final String sign : getLanguageSigns()) {
					String value = property.getLanguageValue(sign);
					if (value != null) {
						if (!sign.equalsIgnoreCase("zh")) {
							if (hasExclamationEnd) {
								if (value.endsWith("?") || value.endsWith(":") || value.endsWith(".")) value = value.substring(0, value.length() - 1);
								if (!value.endsWith("!")) value += "!";
							}
							else if (hasQuestionEnd) {
								if (value.endsWith(":") || value.endsWith(".")) value = value.substring(0, value.length() - 1);
								if (!value.endsWith("?")) value += "?";
							}
							else if (hasColonEnd) {
								if (value.endsWith(".")) value = value.substring(0, value.length() - 1);
								if (!value.endsWith(":")) value += ":";
							}
							else if (hasFullstopEnd) {
								if (!value.endsWith(".")) value += ".";
							}
						} else {
							if (hasExclamationEnd) {
								if (value.endsWith("\\uFF1F") || value.endsWith("\\uFF1A") || value.endsWith("\\u3002")) value = value.substring(0, value.length() - 6);
								if (!value.endsWith("\\uFF01")) value += "\\uFF01";
							}
							else if (hasQuestionEnd) {
								if (value.endsWith("\\uFF1A") || value.endsWith("\\u3002")) value = value.substring(0, value.length() - 6);
								if (!value.endsWith("\\uFF1F")) value += "\\uFF1F";
							}
							else if (hasQuestionEnd) {
								if (value.endsWith("\\u3002")) value = value.substring(0, value.length() - 6);
								if (!value.endsWith("\\uFF1A")) value += "\\uFF1A";
							}
							else if (hasFullstopEnd) {
								if (!value.endsWith("\\u3002")) value += "\\u3002";
							}
						}

						property.setLanguageValue(sign, value);
					}
				}
			}

			final List<String> signs = getLanguageSigns();
			signs.remove(LANGUAGE_SIGN_DEFAULT);
			for (final String sign : signs) {
				if (property.getLanguageValue(LANGUAGE_SIGN_DEFAULT) != null
						&& Utilities.isNotEmpty(property.getLanguageValue(sign))
						&& property.getLanguageValue(LANGUAGE_SIGN_DEFAULT).equals(property.getLanguageValue(sign))) {
					property.removeLanguageValue(sign);
				} else if (sign.contains("_")) {
					final String firstSignPart = sign.substring(0, sign.indexOf("_"));
					if (property.getLanguageValue(firstSignPart) != null
							&& Utilities.isNotEmpty(property.getLanguageValue(sign))
							&& property.getLanguageValue(firstSignPart).equals(property.getLanguageValue(sign))) {
						property.removeLanguageValue(sign);
					}
				}
			}

			// Languagespecific changes
			for (final String sign : getLanguageSigns()) {
				if (sign.equalsIgnoreCase("es") && property.getLanguageValue(sign) != null) {
					String value = property.getLanguageValue(sign);
					if (value.endsWith("!") && !value.startsWith("\\u00A1")) {
						value = "\\u00A1" + value;
					} else if (value.endsWith("?") && !value.startsWith("\\u00BF")) {
						value = "\\u00BF" + value;
					}
					property.setLanguageValue(sign, value);
				}

				if (sign.equals("fr") && property.getLanguageValue(sign) != null) {
					String value = property.getLanguageValue(sign);
					value = value.replace("!", " !").replace("  !", " !").replace("?", " ?").replace("  ?", " ?").replace(":", " :").replace("  :", " :");
					property.setLanguageValue(sign, value);
				} else if (!sign.equalsIgnoreCase("fr") && property.getLanguageValue(sign) != null) {
					String value = property.getLanguageValue(sign);
					value = value.replace(" !", "!").replace(" ?", "?").replace(" :", ":");
					property.setLanguageValue(sign, value);
				}
			}

			// Change check
			for (final String sign : getLanguageSigns()) {
				if (originalData.get(sign) == null && property.getLanguageValue(sign) != null)
					dataWasChanged = true;
				else if (originalData.get(sign) != null && !originalData.get(sign).equals(property.getLanguageValue(sign)))
					dataWasChanged = true;
			}
		}

		return dataWasChanged;
	}

	public void sort(final String column, final boolean ascending) {
		if (column.equalsIgnoreCase(SORT_SIGN_KEY)) {
			properties = MapUtilities.sort(properties, new Property.KeyComparator(ascending));
		} else if (column.equalsIgnoreCase(SORT_SIGN_ORIGINAL_INDEX)) {
			properties = MapUtilities.sortEntries(properties, new Property.OriginalIndexComparator(ascending));
		} else if (column.equalsIgnoreCase(SORT_SIGN_DEFAULT)) {
			properties = MapUtilities.sortEntries(properties, new Property.EntryValueExistsComparator(LANGUAGE_SIGN_DEFAULT, ascending));
		} else {
			properties = MapUtilities.sortEntries(properties, new Property.EntryValueExistsComparator(column, ascending));
		}
	}

	public int getIndexOfKey(final String key) {
		return properties.getKeyList().indexOf(key);
	}

	public void add(final boolean isStorageText, final Text keyTextfield, final Map<String, Text> languageTextFields) throws Exception {
		// Check duplicate key
		if (properties.containsKey(keyTextfield.getText()))
			throw new Exception("New key " + keyTextfield.getText() + " already exists");

		final Property property = new Property(isStorageText ? keyTextfield.getText() : StringEscapeUtils.escapeJava(keyTextfield.getText()));

		property.setOriginalIndex(nextOrderIndex++);

		// Set values
		for (final String languageKey : languageTextFields.keySet()) {
			property.setLanguageValue(languageKey, isStorageText ? languageTextFields.get(languageKey).getText() : StringEscapeUtils.escapeJava(languageTextFields.get(languageKey).getText()));
		}

		add(property);
	}

	public void change(final boolean isStorageText, final String oldKey, final Text keyTextfield, final Map<String, Text> languageTextFields) throws Exception {
		final Property property = properties.get(oldKey);

		if (!oldKey.equals(keyTextfield.getText())) {
			// Check duplicate key
			if (properties.containsKey(keyTextfield.getText()))
				throw new Exception("New key already exists");

			property.setKey(keyTextfield.getText());
		}

		// Set values
		property.setKey(isStorageText ? keyTextfield.getText() : StringEscapeUtils.escapeJava(keyTextfield.getText()));
		for (final String languageKey : languageTextFields.keySet()) {
			property.setLanguageValue(languageKey, isStorageText ? languageTextFields.get(languageKey).getText() : StringEscapeUtils.escapeJava(languageTextFields.get(languageKey).getText()));
		}
	}

	public static List<String> readAvailablePropertySets(final String directoryPath) {
		final List<String> returnList = new UniqueFifoQueuedList<>(10);
		for (final File propertyFile : new File(directoryPath).listFiles((FileFilter) new WildcardFilenameFilter(PROPERTIES_FILEPATTERN))) {
			final String fileName = propertyFile.getName().substring(0, propertyFile.getName().lastIndexOf('.'));
			if (LANGUAGEANDCOUNTRYPATTERN.matcher(fileName).matches()) {
				returnList.add(fileName.substring(0, fileName.length() - 6));
			} else if (LANGUAGEPATTERN.matcher(fileName).matches()) {
				returnList.add(fileName.substring(0, fileName.length() - 3));
			} else {
				returnList.add(fileName);
			}
		}
		return returnList;
	}
}
