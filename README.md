# 🌍 Language Properties Manager

**Edit, compare, translate and convert Java language `.properties` files – all languages of a set side by side in one table.**

Language Properties Manager is a desktop tool (with an additional command line interface) for maintaining I18N resource bundles. Instead of juggling `Messages.properties`, `Messages_en.properties`, `Messages_de.properties`, … in separate editor tabs, you see every key with all its translations in a single view – and can round-trip everything through Excel or CSV for translators.

![Language Properties Manager](https://github.com/hudeany/LanguagePropertiesManager/blob/master/LanguagePropertiesManager.png?raw=true)

---

## ✨ Features

| | |
| 📂 **Load whole property sets** | Open one file and all languages of that set are loaded together – or scan a complete directory tree for all property sets at once |
| 🗂️ **One table for all languages** | Key, default value and every language (`en`, `de`, `de_AT`, `fr`, …) side by side |
| 🔍 **Flexible search** | Search in keys, values and paths – freely combinable |
| 🤖 **Automatic translation** | Fill in missing values via the [DeepL API](https://www.deepl.com/pro-api/) |
| 🔁 **Transfer & clean up** | Copy values between languages, clear values identical to their source, remove duplicates |
| 🩺 **Error check** | Detect encoding problems (mojibake, replacement characters, unresolved Unicode escapes, control characters …) and invalid keys |
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

---

## 🧭 User Manual

<details>
<summary><b>1. Loading language properties</b></summary>

#### Load a single property set
Opens a file selection dialog. Select any one language file of a set – all files of that set are loaded together and shown in the data table on the left.

#### Load all property sets from subdirectories
Opens a folder selection dialog. All subdirectories are scanned for files with the language identifier `_en` or `_de` and the extension `.properties`. For every set found, all other available languages are loaded as well. Depending on the size of the directory tree this may take a moment.

#### Import from a single Excel or CSV file
Loads all property sets stored in one spreadsheet. See [Spreadsheet format](#spreadsheet-format) below.

#### Recently used paths
Opened files and directories are remembered. Use **"Open recently opened files"** to reopen them in the same mode as before.

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
**Delete Selected Properties** removes all selected rows after a confirmation prompt.

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
- If rows are selected, only those are translated – otherwise all properties.

</details>

<details>
<summary><b>4. Saving</b></summary>

#### Save files
**Save Files** becomes available as soon as something has changed.

- Values with an assigned LanguagePropertiesSetPath are saved to exactly that location.
- New values are saved to the only loaded LanguagePropertiesSetPath – or, if several are loaded, you are asked which one to use.

#### Save property sets to a directory based on set name
You choose a base directory, which is searched for existing property sets. Each loaded set is saved to the location with the matching set name.

- Set names must be unique – ambiguous names abort the process with an error message.
- Sets without a match are saved directly into the selected base directory.

#### Export to a single Excel or CSV file
Writes all loaded property sets into one spreadsheet. See [Spreadsheet format](#spreadsheet-format) below.

</details>

---

## Spreadsheet Format

Excel files must consist of a single sheet. Both formats use these columns:

| Column | Alternative names | Required | Description |
|---|---|:---:|---|
| `Path` | `Pfad` | – | LanguagePropertiesSetPath of the set. Placeholders `~` and `$HOME` are allowed (export uses `~` where possible) |
| `Key` | `Schlüssel` | ✔ | Key of the property |
| `Org.Idx` | `Index`, `Idx` | – | Original position within the set, used to keep the order |
| `Default` | | – | Value of the default (language-less) file |
| `en`, `de`, `de_AT`, `fr`, … | | – | One column per language; country-specific tags with underscore are allowed |

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
| `-propertiesFileExtension <ext>` | all | Use a custom file extension instead of `.properties`, e.g. `_mytext.properties` |
| `-v` | all | Verbose output with progress bar |

### Standalone commands

| Command | Description |
|---|---|
| `help` | Show the help manual |
| `gui` | Open the graphical user interface |
| `version` | Show the installed version |
| `update [username [password]]` | Check for an online update and ask whether to install it |

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
