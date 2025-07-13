All dependencies are downloaded by ANT build script: build.xml
	APACHE commons-collections4: 4.4
	APACHE commons-compress: 1.24.0
	APACHE commons-io: 2.14.0
	APACHE commons-lang3: 3.11
	APACHE commons-text: 1.9
	APACHE poi: 5.2.4
	APACHE poi-ooxml: 5.2.4
	APACHE poi-ooxml-full: 5.2.4
	APACHE xmlbeans: 5.1.1
 
	LOG4J log4j-1.2-api: 2.20.0
	LOG4J log4j-api: 2.20.0
	LOG4J log4j-core: 2.20.0
 
	SUN mailapi: 2.0.1
 
	JAVA jna: 5.6.0
	JAVA jna-platform: 5.6.0
 
	github.com/hudeany csv: 24.1.0
	github.com/hudeany json: 25.1.2
	github.com/hudeany proxyautoconfig: 25.1.2
	github.com/hudeany network: 25.1.0
 	github.com/hudeany soderer-utilities: 25.1.6

```
Language Properties Manager User Manual

1. Disclaimer

Language Properties Manager is a tool for editing language properties files with special features that other standard programs don't offer or that aren't readily available.
This editor is intended solely for experimental text editing.
Any use is at your own risk.
The developer assumes NO WARRANTY, neither for correct functionality nor for damages resulting from the use of the program.


2. Definition of Terms

2.1 LanguagePropertiesSetPath

The storage path of a collection of language files for a language property, along with its base name, is referred to below as LanguagePropertiesSetPath.
Example: "C:\Project\I18N\ProjektLanguageProperty" serves as a placeholder for all language files of the language property:
	"C:\Project\I18N\ProjektLanguageProperty.properties",
	"C:\Project\I18N\ProjektLanguageProperty_en.properties",
	"C:\Project\I18N\ProjektLanguageProperty_*.properties"


3. Loading Language Properties

3.1 Loading a Single Property Set from a File Set

This button opens a file selection window where you can select one of the language files in a LanguagePropertiesSetPath.

All files of this language property are loaded together and displayed in the data table on the left side of the tool.

3.2 Loading All Available Property Sets from Subdirectories

This button opens a folder selection window. All available language properties from all subdirectories of this folder are then loaded. Currently, the tool searches for files with one of the language identifiers "_en" or "_de" and the file extension ".properties".

For all LanguagePropertiesSetPaths found, all other available languages are loaded and displayed in the data table on the left side of the tool.

Depending on the number of subdirectories and files and their size, this may take a moment.

3.3 Excel Import

The "Import from Single File" button allows you to specify a single Excel file to load all language property sets stored in it.

This Excel file should consist of a single sheet and can contain the following columns:
	"Path" or "Path" (more precisely, LanguagePropertiesSetPath for this language properties set; placeholders ~ and $HOME are allowed, optional)
	"Key" or "Schlüssel" (key to the property value, required)
	"Index", "Idx", or "Org.Idx" (indexing within a language properties set to maintain an order of the individual property values, optional)
	"Default" (default language value for the key, optional)
	"en", "de", "de_AT", "de_CH", "fr", ... (language values of the individual language identifiers, even country-specific identifiers with an underscore are allowed)

3.4 Recently Used Paths

Opened file paths from 3.1 and directory paths from 3.2 are saved in a list for quick reuse later.
These can be used for quick access later using the "Open recently opened files" button and will then open in the same mode as before.


4. Editing Individual Property Values

4.1 Creating and Changing Existing Property Values

Selecting a row in the data table on the left side of the tool displays the detailed list of LanguagePropertiesSetPath, Key, Optional Comment, and Default, as well as all other available language values for the property, in the details area of the tool on the right.
These can then be edited, except for the LanguagePropertiesSetPath.
After making a desired change, the new state can be applied for later saving of the property set using the "Change" button.
To add a new key with language values, the "Create New Property" button in the tool's second button bar can be used.
After making a desired change, the property set can be added using the "Add" button.
In both cases, all unwanted changes can be discarded at any time using the "Discard" button.
The "Switch to Saved Text View" button changes the display of key and language values for technical encoding in JAVA encoding.
This is used to specify complex Unicode characters if necessary.
The "Switch to Displayed Text View" button can be used to return to the normal display view.

4.2 Deleting Property Values

The "Delete Selected Properties" button can be used to delete all currently selected rows in the left toolbar at once.
A confirmation prompt will appear.

4.3 Adding Language Tags

The "Add a New Language Tag" button can be used to add an additional language tag for all rows in the left toolbar.

4.4 Deleting Language Tags

The "Delete an existing language tag" button can be used to completely remove a language tag from all rows in the left-hand toolbar.
A selection dialog appears for specifying one of the available language tags.


5. Saving Language Property Sets

5.1 Saving Files

As soon as changes have been made to the values of a language property, the "Save Files" button becomes available.
If a LanguagePropertiesSetPath has already been assigned to the values in the properties set, the data will be (re)saved to that exact location.
For newly created values and only one LanguagePropertiesSetPath is loaded, this LanguagePropertiesSetPath is used as the default path for new values.
If multiple LanguagePropertiesSetPaths are available, you will be prompted to specify the LanguagePropertiesSetPath to be used for all new values.

5.2 Save Property Sets to a Directory Based on Set Name

The "Save Property Sets to a Directory Based on Set Name" button prompts you to specify a directory path. All available LanguagePropertiesSetPaths are then searched for under this path.
The LanguagePropertiesSetPaths currently available in the tool are compared with these paths, and if a matching LanguagePropertiesSetPath name is found, the path of the LanguagePropertiesSetPath is used for saving.
This only works if the LanguagePropertiesSetPath names are unique. If ambiguous names occur, the process is aborted with an error message.
If no suitable path is found for a LanguagePropertiesSetPath currently available in the tool, the LanguagePropertiesSetPath is saved in the selected base directory.

5.3 Export to Single File

The "Export to Single File" button saves all LanguagePropertiesSetPaths currently available in the tool to a single Excel file.
For this purpose, an Excel file is created with a sheet and the following columns of data:
	"Path" (more precisely, LanguagePropertiesSetPath for this language properties set; the placeholder ~ for the user directory in the system is used where possible)
	"Key" (key to the property value)
	"Org.Idx" (indexing within a language properties set to maintain a sequence of individual property values)
	"Default" (default language value for the key, if available)
	"en", "de", "de_AT", "de_CH", "fr", ... (language values of the individual language identifiers; country-specific identifiers with an underscore are also permitted)

	
Command Line Interface (CLI)

Usage:
	java -jar LanguagePropertiesManager.jar -exportToExcel <language properties file or directory path> -excelFile <output excel file path> [-v]
	or
	java -jar LanguagePropertiesManager.jar -importFromExcel <input excel file path> [-outputDirectory <language properties output directory>] [-v]

Mandatory parameters for excel file export
	-exportToExcel <language properties file or directory path>
	-excelFile <output excel file path>

Optional parameters for excel file export
	-v: verbose output with progress bar
	
Mandatory parameters for excel file import
	-exportToExcel <language properties file or directory path>
	-excelFile <output excel file path>

Optional parameters for excel file import
	-outputDirectory <language properties output directory>:
		If outputDirectory is not defined, all language properties sets will be imported to the defined original file paths in the excelfile.
		If outputDirectory is defined, the existing language properties sets file paths within that directory will be detected and used for matching set names.
		Other language properties sets without matching file paths will be store as new language properties sets in the base directory itself.
	-v: verbose output with progress bar

Global standalone parameters
	help: Show this help manual
	gui: Open a GUI
	version: Show current local version of this tool
	update: Check for online update and ask, whether an available update shell be installed. [username [password]]
