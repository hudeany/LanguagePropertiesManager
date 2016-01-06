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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.swt.widgets.Text;

import de.soderer.utilities.collection.IndexedLinkedHashMap;
import de.soderer.utilities.collection.MapUtils;
import de.soderer.utilities.collection.UniqueFifoQueuedList;

public class PropertiesStorage {
	public static final String LANGUAGE_SIGN_DEFAULT = "default";
	
	public static final String SORT_SIGN_DEFAULT = "default";
	public static final String SORT_SIGN_ORIGINAL_INDEX = "index";
	public static final String SORT_SIGN_KEY = "key";
	
	public static final String PROPERTIES_FILEEXTENSION = "properties";
	public static final String PROPERTIES_FILEPATTERN = "*." + PROPERTIES_FILEEXTENSION;
	
	private static final Pattern LANGUAGEANDCOUNTRYPATTERN = Pattern.compile("^.*[a-zA-Z0-9‰ˆ¸ƒ÷‹_]+(_[a-zA-Z]{2}){2}$");
	private static final Pattern LANGUAGEPATTERN = Pattern.compile("^.*[a-zA-Z0-9‰ˆ¸ƒ÷‹_]+_[a-zA-Z]{2}$");
	
	public static final String DEFAULT_STORAGE_SEPARATOR = " = ";
	
	private File directory;
	private FileFilter fileFilter;
	private boolean readKeysCaseInsensitive;
	private Map<String, PropertiesLanguageFileReader> languageFiles;
	private IndexedLinkedHashMap<String, Property> properties;
	private int nextOrderIndex = 0;
	
	public PropertiesStorage(String directory, String popertySetName, boolean readKeysCaseInsensitive) throws Exception {
		this.directory = new File(directory);
		try {
			this.fileFilter = new WildcardFileFilter(popertySetName + PROPERTIES_FILEPATTERN);
		} catch (Exception e) {
			throw new Exception("Given filePattern is invalid");
		}
		this.readKeysCaseInsensitive = readKeysCaseInsensitive;
		
		if (!this.directory.isDirectory())
			throw new Exception("Given path is not a directory");
		else if (!this.directory.exists())
			throw new Exception("Given directory does not exist");
	}
	
	public void load() throws Exception {
		languageFiles = new HashMap<String, PropertiesLanguageFileReader>();
		
		for (File propertyFile : directory.listFiles(fileFilter)) {
			FileInputStream fileInputStream = null;
			PropertiesLanguageFileReader propertiesLanguageFile;
			try {
				fileInputStream = new FileInputStream(propertyFile);
				propertiesLanguageFile = new PropertiesLanguageFileReader();
				propertiesLanguageFile.load(fileInputStream, readKeysCaseInsensitive);
			} catch (Exception e) {
				throw new Exception("Error when reading file: " + propertyFile.getAbsolutePath(), e);
			}
			finally {
				IOUtils.closeQuietly(fileInputStream);
			}
			languageFiles.put(propertyFile.getAbsolutePath(), propertiesLanguageFile);
		}
		
		languageFiles = MapUtils.sort(languageFiles, new Comparator<String>(){
			@Override
			public int compare(String arg0, String arg1) {
				if (arg0.length() != arg1.length())
					return new Integer(arg0.length()).compareTo(arg1.length());
				else return arg0.compareTo(arg1);
			}
		});

		IndexedLinkedHashMap<String, Property> returnValues = new IndexedLinkedHashMap<String, Property>();
		for (Entry<String, PropertiesLanguageFileReader> propertiesLanguageFileEntry : languageFiles.entrySet()) {
			String languageSign = PropertiesLanguageFileReader.getLanguageSignOfFilename(propertiesLanguageFileEntry.getKey());
			for (Entry<String, String> entry : propertiesLanguageFileEntry.getValue().getEntries().entrySet()) {
				Property property = returnValues.get(entry.getKey());
				if (property == null) {
					property = new Property(entry.getKey());
					property.setOriginalIndex(nextOrderIndex++);
					returnValues.put(entry.getKey(), property);
				}
				property.setLanguageValue(languageSign, entry.getValue());
			}
		}
		
		properties = MapUtils.sort(returnValues, new Property.KeyComparator(true));
	}
	
	public void remove(String key) throws Exception {
		properties.remove(key);
	}
	
	public void add(Property property) throws Exception {
		properties.put(property.getKey(), property);
		properties = MapUtils.sort(properties, new Property.KeyComparator(true));
	}
	
	public void save(String directory, String separator, boolean sortByOrgIndex) throws Exception {
		Map<String, Property> propertiesToSave;
		if (sortByOrgIndex)
			propertiesToSave = MapUtils.sortEntries(properties, new Property.OriginalIndexComparator(true));
		else propertiesToSave = properties;
		
		if (StringUtils.isEmpty(separator))
			separator = DEFAULT_STORAGE_SEPARATOR;
		
		for (String propertiesLanguageFileName : languageFiles.keySet()) {
			String languageSign = PropertiesLanguageFileReader.getLanguageSignOfFilename(propertiesLanguageFileName);
			String fileName = new File(propertiesLanguageFileName).getName();
			File propertyFile = new File(directory + File.separator + fileName);
			FileOutputStream fileOutputStream = null;
			try {
				if (propertyFile.exists()) propertyFile.delete();
				fileOutputStream = new FileOutputStream(propertyFile);
				for (Entry<String, Property> entry : propertiesToSave.entrySet()) {
					if (entry.getValue().getLanguageValue(languageSign) != null) {
						StringBuilder line = new StringBuilder();
						line.append(entry.getValue().getKey());
						line.append(separator);
						line.append(entry.getValue().getLanguageValue(languageSign));
						line.append("\n");
						fileOutputStream.write(line.toString().getBytes("UTF-8"));
					}
				}
			} catch (Exception e) {
				throw new Exception("Error when writing file: " + propertyFile.getAbsolutePath(), e);
			} finally {
				if (fileOutputStream != null) {
					fileOutputStream.flush();
					fileOutputStream.close();
				}
			}
		}
	}

	public Map<String, PropertiesLanguageFileReader> getLanguageFiles() {
		return languageFiles;
	}
	
	public List<String> getLanguageSigns() {
		List<String> returnValues = new ArrayList<String>();
		
		if (directory != null) {
			for (String fileName : languageFiles.keySet()) {
				returnValues.add(PropertiesLanguageFileReader.getLanguageSignOfFilename(fileName));
			}
			
			Collections.sort(returnValues, new Comparator<String>() {
				@Override
				public int compare(String value1, String value2) {
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
	
	public void removeLanguage(String languageSign) throws Exception {
		for (Property property : properties.values()) {
			property.removeLanguageValue(languageSign);
		}
		languageFiles.remove(languageSign);
	}
	
	public void addLanguage(String languageSign) throws Exception {
		String defaultFileName = null;
		for (String fileName : languageFiles.keySet()) {
			if (LANGUAGE_SIGN_DEFAULT.equalsIgnoreCase(PropertiesLanguageFileReader.getLanguageSignOfFilename(fileName))) {
				defaultFileName = fileName;
			}
		}
		new File(defaultFileName.substring(0, defaultFileName.length() - 11) + languageSign + ".properties").createNewFile();
		load();
	}
	
	public boolean cleanUp(boolean repairpunctuation) throws Exception {
		boolean dataWasChanged = false;
		
		for (Property property : properties.values()) {
			// Store original values for later change check
			HashMap<String, String> originalData = new HashMap<String, String>();
			for (String sign : getLanguageSigns()) {
				originalData.put(sign, property.getLanguageValue(sign));
			}
			
			for (String sign : getLanguageSigns()) {
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
				if (StringUtils.isEmpty(property.getLanguageValue(sign))) {
					property.removeLanguageValue(sign);
				}
			}
			
			if (repairpunctuation) {
				// Check exclamationmark, questionmark and fullstop unification
				boolean hasExclamationEnd = false;
				boolean hasQuestionEnd = false;
				boolean hasFullstopEnd = false;
				boolean hasColonEnd = false;
				for (String sign : getLanguageSigns()) {
					String value = property.getLanguageValue(sign);
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
				for (String sign : getLanguageSigns()) {
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

			List<String> signs = getLanguageSigns();
			signs.remove(LANGUAGE_SIGN_DEFAULT);
			for (String sign : signs) {
				if (property.getLanguageValue(LANGUAGE_SIGN_DEFAULT) != null
						&& StringUtils.isNotEmpty(property.getLanguageValue(sign))
						&& property.getLanguageValue(LANGUAGE_SIGN_DEFAULT).equals(property.getLanguageValue(sign))) {
					property.removeLanguageValue(sign);
				} else if (sign.contains("_")) {
					String firstSignPart = sign.substring(0, sign.indexOf("_"));
					if (property.getLanguageValue(firstSignPart) != null
							&& StringUtils.isNotEmpty(property.getLanguageValue(sign))
							&& property.getLanguageValue(firstSignPart).equals(property.getLanguageValue(sign))) {
						property.removeLanguageValue(sign);
					}
				}
			}
			
			// Languagespecific changes
			for (String sign : getLanguageSigns()) {
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
			for (String sign : getLanguageSigns()) {
				if (originalData.get(sign) == null && property.getLanguageValue(sign) != null)
					dataWasChanged = true;
				else if (originalData.get(sign) != null && !originalData.get(sign).equals(property.getLanguageValue(sign)))
					dataWasChanged = true;
			}
		}
		
		return dataWasChanged;
	}
	
	public void sort(String column, boolean ascending) {
		if (column.equalsIgnoreCase(SORT_SIGN_KEY)) {
			properties = MapUtils.sort(properties, new Property.KeyComparator(ascending));
		} else if (column.equalsIgnoreCase(SORT_SIGN_ORIGINAL_INDEX)) {
			properties = MapUtils.sortEntries(properties, new Property.OriginalIndexComparator(ascending));
		} else if (column.equalsIgnoreCase(SORT_SIGN_DEFAULT)) {
			properties = MapUtils.sortEntries(properties, new Property.EntryValueExistsComparator(LANGUAGE_SIGN_DEFAULT, ascending));
		} else {
			properties = MapUtils.sortEntries(properties, new Property.EntryValueExistsComparator(column, ascending));
		}
	}
	
	public int getIndexOfKey(String key) {
		return properties.getKeyList().indexOf(key);
	}
	
	public void add(boolean isStorageText, Text keyTextfield, Map<String, Text> languageTextFields) throws Exception {
		// Check duplicate key
		if (properties.containsKey(keyTextfield.getText()))
			throw new Exception("New key " + keyTextfield.getText() + " already exists");
		
		Property property = new Property(isStorageText ? keyTextfield.getText() : StringEscapeUtils.escapeJava(keyTextfield.getText()));

		property.setOriginalIndex(nextOrderIndex++);
		
		// Set values
		for (String languageKey : languageTextFields.keySet()) {
			property.setLanguageValue(languageKey, isStorageText ? languageTextFields.get(languageKey).getText() : StringEscapeUtils.escapeJava(languageTextFields.get(languageKey).getText()));
		}
		
		add(property);
	}
	
	public void change(boolean isStorageText, String oldKey, Text keyTextfield, Map<String, Text> languageTextFields) throws Exception {
		Property property = properties.get(oldKey);

		if (!oldKey.equals(keyTextfield.getText())) {
			// Check duplicate key
			if (properties.containsKey(keyTextfield.getText()))
				throw new Exception("New key already exists");
		
			property.setKey(keyTextfield.getText());
		}

		// Set values
		property.setKey(isStorageText ? keyTextfield.getText() : StringEscapeUtils.escapeJava(keyTextfield.getText()));
		for (String languageKey : languageTextFields.keySet()) {
			property.setLanguageValue(languageKey, isStorageText ? languageTextFields.get(languageKey).getText() : StringEscapeUtils.escapeJava(languageTextFields.get(languageKey).getText()));
		}
	}
	
	public static List<String> readAvailablePropertySets(String directoryPath) {
		List<String> returnList = new UniqueFifoQueuedList<String>(10);
		for (File propertyFile : new File(directoryPath).listFiles((FileFilter) new WildcardFileFilter(PROPERTIES_FILEPATTERN))) {
			String fileName = propertyFile.getName().substring(0, propertyFile.getName().lastIndexOf('.'));
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
