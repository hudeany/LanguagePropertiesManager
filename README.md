# 🌍 Language Properties Manager

**Edit, compare, translate and convert Java language `.properties` files – all languages of a set side by side in one table.**

Language Properties Manager is a desktop tool (with an additional command line interface) for maintaining I18N resource bundles. Instead of juggling `Messages.properties`, `Messages_en.properties`, `Messages_de.properties`, … in separate editor tabs, you see every key with all its translations in a single view – and can round-trip everything through Excel or CSV for translators.

![Language Properties Manager](https://github.com/hudeany/LanguagePropertiesManager/blob/master/LanguagePropertiesManager.png?raw=true)

---

## ✨ Features

| | |
|---|---|
| 📂 **Load whole property sets** | Open one file and all languages of that set are loaded together – or scan a complete directory tree for all property sets at once |
| 🗂️ **One table for all languages** | Key, default value and every language (`en`, `de`, `de_AT`, `fr`, …) side by side |
| 🔍 **Flexible search** | Search in keys, values and paths – freely combinable, optionally as filter of the table |
| 🤖 **Automatic translation** | Fill in missing values via the [DeepL API](https://www.deepl.com/pro-api/) – into one or all other languages, with an optional list of constants that must not be translated |
| 🔁 **Transfer & clean up** | Copy values between languages, clear values identical to their source, remove duplicates |
| 🧩 **Merge import** | Add properties from another file, directory, Excel or CSV file to the loaded data, with a choice how conflicts are resolved |
| ✂️ **Reduce by base set** | Remove all values identical to a base set, so only the deviations (e.g. customer-specific texts) remain |
| 🩺 **Error check** | Detect encoding problems (mojibake, replacement characters, unresolved Unicode escapes, control characters …), invalid keys, inconsistent placeholders (`{0}`, `%s`) and formal deviations between languages |
| 📈 **Statistics & usage check** | Completeness per language, and which keys are used or missing in your source code |
| 🏷️ **Manage language tags** | Add or remove a language for all properties in one step |
| 📊 **Excel & CSV import/export** | Hand all texts to translators in one spreadsheet and import them back |
| 🔤 **Unicode-safe** | Switch between displayed text and the stored Java-escaped form |
| 🕑 **Recently used paths** | Quick access to previously opened files and directories |
| ⌨️ **Command line interface** | Automate Excel/CSV export and import in build scripts |
| 🔄 **Built-in updater** | Checks for and installs new versions |

---

## 🚀 Quick Start

**Run the GUI**

```bash
java -jar LanguagePropertiesManager.jar gui
```

Started without any parameter, the GUI opens as well (on headless systems the help is shown instead).

**Build from source**

All dependencies are downloaded automatically by the Ant build script:

```bash
ant -f build.xml
```

---

## 📖 Concepts

### LanguagePropertiesSetPath

A *LanguagePropertiesSetPath* is the storage path of all language files of one property set, including its base name (without language suffix and extension).

Example: `C:\Project\I18N\ProjectLanguageProperty` stands for

```
C:\Project\I18N\ProjectLanguageProperty.properties
C:\Project\I18N\ProjectLanguageProperty_en.properties
C:\Project\I18N\ProjectLanguageProperty_*.properties
```

Only files with a valid locale suffix (`_en`, `_de_AT`, `_en_US_POSIX`, …) belong to a set, so e.g. `ProjectLanguageProperty-customer.properties` is not mixed in. The user's home directory is shown as `~`.

### Missing vs. empty values

A language value can be *missing* (the key is not written into that language file, so `ResourceBundle` falls back to the default value) or *explicitly empty* (written as `key=`). The table and the detail view show both states with different signs; the context menu of a language column or field switches between them.

---

## 🧭 User Manual

<details>
<summary><b>1. Loading language properties</b></summary>

#### Load a single property set
Opens a file selection dialog. Select any one language file of a set – all files of that set are loaded together and shown in the data table on the left.

#### Load all property sets from subdirectories
Opens a folder selection dialog. All subdirectories are scanned for property sets, i.e. files with the configured extension (default `.properties`) of which at least one has a language suffix like `_en` or `_de`. Standalone files without any language file next to them (e.g. configuration properties) are ignored, as are paths containing one of the configured exclude parts (default: `__`, `/src/test/`, `/bin/`). For every set found, all available languages are loaded. Depending on the size of the directory tree this may take a moment.

If a file contains a key more than once, only its first value is loaded and a warning lists the affected files and keys.

#### Import from a single Excel or CSV file
Loads all property sets stored in one spreadsheet. See [Spreadsheet format](#spreadsheet-format) below.

#### Recently used paths
Opened files and directories are remembered. Use **"Open recently opened files"** to reopen them in the same mode as before. Entries can be reordered by drag & drop or removed with the DEL key.

#### Merge import
**Import additional properties** adds properties from a properties file, a directory, a recently used path, an Excel or a CSV file to the loaded data. Properties are matched by key (the path only resolves ambiguities between several loaded sets).

- New keys are added to the matching loaded set.
- Empty values are filled.
- If imported values differ from existing ones, you choose: *add new and keep existing*, *overwrite*, or *only fill empty values*.

Changed and added properties are selected afterwards, and a report including an error check of the imported values is shown.

#### Reduce by base set
**Compare with base set** removes all values of the selected (or all displayed) properties that are identical to the same key and language in a base set. Properties without any remaining value can optionally be deleted completely. A report lists cleared, deleted and still differing values.

</details>

<details>
<summary><b>2. Editing property values</b></summary>

#### Create and change values
Selecting a row in the data table shows its details on the right: LanguagePropertiesSetPath, key, optional comment, default value and all language values. Everything except the LanguagePropertiesSetPath can be edited.

- **Change** – apply the edit (saved later with *Save Files*)
- **Create New Property** – add a new key with its language values, then confirm with **Add**
- **Discard** – revert all unsaved edits of the detail view
- **Switch to Saved Text View / Displayed Text View** – toggle between the normal display and the Java-escaped storage form, e.g. to enter complex Unicode characters

#### Delete properties
**Delete Selected Properties** (or the DEL key) removes all selected rows after a confirmation prompt.

#### Context menus of the table
- Right click on rows: copy the selected rows as CSV into the clipboard.
- Language column: set the selected values to *empty* or *missing*, delete all values of the language, or delete the language.
- Path / comment column: delete all paths or all comments.

#### Add or delete language tags
- **Add a New Language Tag** adds a new language column for all properties.
- **Delete an existing language tag** removes a language completely; a selection dialog lets you choose which one.

</details>

<details>
<summary><b>3. Translating missing values (DeepL)</b></summary>

**Translate** creates missing language values by translating the value of another language via [DeepL](https://www.deepl.com).

- A DeepL API key is required – get one at <https://www.deepl.com/pro-api/>. Limited test accounts are free but require credit card registration.
- Enter the key in the application configuration dialog or when using *Translate* for the first time.
- When the *Default* language is used as source, you must select which language it represents.
- If only one other language is available, it is selected as target automatically.
- *Translate into all target languages* translates into every other language supported by DeepL in one go.
- If rows are selected, only those are translated – otherwise all properties shown in the table (with an active search filter only the hits). The same applies to *Transfer*, *Clear identical* and *Compare with base set*; the status bar below the table shows which properties are affected.
- Only empty target values are filled, existing translations are never overwritten.
- Values listed in the optional translation constants CSV file (configuration `TranslationConstantsFile`, header with language tags, e.g. `de;en;it`) and plain numbers are taken over without DeepL.

</details>

<details>
<summary><b>4. Saving</b></summary>

#### Save files
**Save Files** becomes available as soon as something has changed.

- Values with an assigned LanguagePropertiesSetPath are saved to exactly that location.
- New values are saved to the only loaded LanguagePropertiesSetPath – or, if several are loaded, you are asked for the file to save them to.
- Missing target directories can be created after a confirmation.
- Before saving you choose whether keys that exist in the files but not in the loaded data are **kept** or **removed**. Closing this question cancels the save.

#### Save property sets to a directory based on set name
You choose a base directory, which is searched for existing property sets. Each loaded set is saved to the location with the matching set name.

- A set that already exists at its own LanguagePropertiesSetPath within the base directory is saved there, so equally named sets in different subdirectories (e.g. `messages` of several modules) are no problem.
- Otherwise the set is matched by its name; if this name exists more than once in the base directory, the process is aborted with an error message.
- Sets without a match are saved directly into the selected base directory.

#### Export to a single Excel or CSV file
Writes all loaded property sets into one spreadsheet. See [Spreadsheet format](#spreadsheet-format) below. An existing file is only replaced after the export succeeded. A successful export counts as saving, so there are no unsaved changes afterwards.

</details>

---

## Spreadsheet Format

Excel files must consist of a single sheet. CSV files use `;` as separator and `"` as quote (RFC 4180, line breaks within quoted values are allowed). Both formats use these columns (header names are case-insensitive):

| Column | Alternative names | Required | Description |
|---|---|:---:|---|
| `Path` | `Pfad`, `File`, `Datei` | – | LanguagePropertiesSetPath of the set. Placeholders `~` and `$HOME` are allowed (export uses `~` where possible). Without paths, the name of the imported file is used as set name |
| `Key` | `Keys`, `Schlüssel`, `Schluessel`, `Bezeichner` | ✔ | Key of the property |
| `Index` | `Org.Idx`, `Idx` | – | Original position within the set, used to keep the order |
| `Comment` | `Kommentar` | – | Comment written above the property (ignored if comments are disabled in the configuration) |
| `Default` | | – | Value of the default (language-less) file |
| `en`, `de`, `de_AT`, `fr`, … | | – | One column per language; tags have two letters, optionally followed by `_` and a two-letter country |

If a `Default` column exists, empty cells mean "missing" in the language columns and "empty" in the `Default` column. Without a `Default` column, empty cells are imported as empty values.

---

## ⌨️ Command Line Interface

```bash
# Export properties to Excel / CSV
java -jar LanguagePropertiesManager.jar -exportToExcel <properties file or directory> -excelFile <output.xlsx> [options]
java -jar LanguagePropertiesManager.jar -exportToCsv   <properties file or directory> -csvFile   <output.csv>  [options]

# Import properties from Excel / CSV
java -jar LanguagePropertiesManager.jar -importFromExcel <input.xlsx> [-outputDirectory <directory>] [options]
java -jar LanguagePropertiesManager.jar -importFromCsv   <input.csv>  [-outputDirectory <directory>] [options]
```

### Parameters

| Parameter | Used with | Description |
|---|---|---|
| `-exportToExcel <path>` | Excel export | Properties file or directory to export (**mandatory**) |
| `-excelFile <path>` | Excel export | Output Excel file (**mandatory**) |
| `-exportToCsv <path>` | CSV export | Properties file or directory to export (**mandatory**) |
| `-csvFile <path>` | CSV export | Output CSV file (**mandatory**) |
| `-importFromExcel <path>` | Excel import | Input Excel file (**mandatory**) |
| `-importFromCsv <path>` | CSV import | Input CSV file (**mandatory**) |
| `-outputDirectory <path>` | Import | Without this option, sets are written to the original paths stored in the file. With it, existing sets in that directory are matched by set name; unmatched sets are created in the directory itself |
| `-overwrite` | Export | Replace an existing output file |
| `-propertiesFileExtension <ext>` | all | Use a custom file extension instead of `.properties`, e.g. `_mytext.properties` (a missing leading dot is added) |
| `-v` | all | Verbose output with progress bar |

Exactly one of `-exportToExcel`, `-exportToCsv`, `-importFromExcel` and `-importFromCsv` must be given. When importing, keys that exist in the target files but not in the imported data are kept.

### Standalone commands

| Command | Description |
|---|---|
| `help` | Show the help manual |
| `gui` | Open the graphical user interface |
| `version` | Show the installed version |
| `update [username [password]]` | Check for an online update and ask whether to install it |

---

## ⚙️ Configuration

The configuration dialog (wrench button) stores its settings in `~/.LanguagePropertiesManager.config`. Besides language, proxy and update settings it contains:

| Setting | Default | Description |
|---|---|---|
| `PropertiesFileExtension` | `.properties` | File extension of the language files |
| `OpenDirExcludes` | `__;/src/test/;\src\test\;/bin/;\bin\` | Path parts (separated by `;`) of files ignored when scanning or saving directories |
| `IgnoreComments` | `false` | Do not load, show and export comments |
| `DeepL_BaseUrl` / `DeepL_ApiKey` | free API URL / empty | Access to the DeepL API |
| `TranslationConstantsFile` | empty | Optional CSV file with values that must not be translated by DeepL |

---

## 📦 Dependencies

<details>
<summary>All dependencies are downloaded automatically by <code>build.xml</code></summary>

| Group | Library | Version |
|---|---|---|
| Apache | commons-collections4 | 4.4 |
| Apache | commons-compress | 1.24.0 |
| Apache | commons-io | 2.14.0 |
| Apache | commons-lang3 | 3.11 |
| Apache | commons-text | 1.9 |
| Apache | poi / poi-ooxml / poi-ooxml-full | 5.2.4 |
| Apache | xmlbeans | 5.1.1 |
| Log4j | log4j-1.2-api / log4j-api / log4j-core | 2.20.0 |
| Sun | mailapi | 2.0.1 |
| Java | jna / jna-platform | 5.6.0 |
| [hudeany](https://github.com/hudeany) | csv | 25.1.1 |
| [hudeany](https://github.com/hudeany) | json | 25.1.2 |
| [hudeany](https://github.com/hudeany) | proxyautoconfig | 25.1.8 |
| [hudeany](https://github.com/hudeany) | network | 25.1.2 |
| [hudeany](https://github.com/hudeany) | soderer-utilities | 25.1.13 |

</details>

---

## ⚠️ Disclaimer

Language Properties Manager is provided for experimental text editing. **Use at your own risk.** The developer assumes **no warranty**, neither for correct functionality nor for damages resulting from the use of this program.

## 💬 Feedback

Suggestions and bug reports are welcome – via [GitHub Issues](https://github.com/hudeany/LanguagePropertiesManager/issues) or by mail to **languagepropertiesmanager@soderer.de**.
