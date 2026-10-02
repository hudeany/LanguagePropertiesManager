package de.soderer.languagepropertiesmanager.dlg;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.filechooser.FileNameExtensionFilter;

import de.soderer.languagepropertiesmanager.image.ImageManager;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.collection.UniqueFifoQueuedList;

/**
 * Small GUI helpers shared by the main window and its actions: file choosers,
 * recent lists and popup menu items
 */
final class DialogUtilities {
	private DialogUtilities() {
		// Utility class, no instances
	}

	/**
	 * Adds an item with icon to a popup menu (import source menu, translate menu)
	 */
	static void addMenuItem(final JPopupMenu menu, final String imageName, final String textKey, final boolean enabled, final Runnable action) throws Exception {
		final JMenuItem menuItem = new JMenuItem(LangResources.get(textKey), ImageManager.getImage(imageName));
		menuItem.setEnabled(enabled);
		menuItem.addActionListener(e -> action.run());
		menu.add(menuItem);
	}

	/**
	 * Index of the last entry of a recent list. The latest used entry is always
	 * moved to the end (see moveToEnd()), so this entry is preselected in the
	 * ComboSelectionDialog.
	 *
	 * @return index of the last entry, or -1 if the list is empty
	 */
	static int getLastEntryIndex(final UniqueFifoQueuedList<String> recentEntries) {
		return recentEntries == null ? -1 : recentEntries.size() - 1;
	}

	/**
	 * Moves an entry of a recent list to its end (or appends it, if it is new),
	 * so it counts as latest used and is preselected next time.
	 * The list is rebuilt explicitly, because adding an already contained entry
	 * to the unique list does not necessarily change its position. If the list
	 * is full, a new entry pushes out the first (oldest) entry.
	 */
	static void moveToEnd(final UniqueFifoQueuedList<String> recentEntries, final String entry) {
		final List<String> entries = new ArrayList<>(recentEntries);
		entries.remove(entry);
		entries.add(entry);
		recentEntries.clear();
		recentEntries.addAll(entries);
	}

	/**
	 * Shows a file chooser to open a file.
	 *
	 * @param fileExtensions optional extension filters (without dot), e.g. "xlsx"
	 * @return the selected file or null if canceled
	 */
	static File chooseFileToOpen(final Component parent, final String title, final String initialDirectory, final String... fileExtensions) {
		final JFileChooser fileChooser = createFileChooser(title, initialDirectory, fileExtensions);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		return fileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? fileChooser.getSelectedFile() : null;
	}

	/**
	 * Shows a file chooser to select a file to save to.
	 *
	 * @return the selected file or null if canceled
	 */
	static File chooseFileToSave(final Component parent, final String title, final String initialDirectory, final String proposedFileName, final String... fileExtensions) {
		final JFileChooser fileChooser = createFileChooser(title, initialDirectory, fileExtensions);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (Utilities.isNotBlank(proposedFileName)) {
			fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), proposedFileName));
		}
		return fileChooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION ? fileChooser.getSelectedFile() : null;
	}

	/**
	 * Shows a file chooser to select a directory.
	 *
	 * @return the selected directory or null if canceled
	 */
	static File chooseDirectory(final Component parent, final String title, final String initialDirectory) {
		final JFileChooser fileChooser = createFileChooser(title, initialDirectory);
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		return fileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? fileChooser.getSelectedFile() : null;
	}

	private static JFileChooser createFileChooser(final String title, final String initialDirectory, final String... fileExtensions) {
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle(title);

		if (Utilities.isNotBlank(initialDirectory)) {
			File directory = new File(initialDirectory);
			// Recent entries may also be files, start in their directory then
			if (directory.isFile()) {
				directory = directory.getParentFile();
			}
			if (directory != null && directory.isDirectory()) {
				fileChooser.setCurrentDirectory(directory);
			}
		}

		if (fileExtensions != null && fileExtensions.length > 0) {
			final FileNameExtensionFilter filter = new FileNameExtensionFilter("*." + String.join(", *.", fileExtensions), fileExtensions);
			fileChooser.addChoosableFileFilter(filter);
			fileChooser.setFileFilter(filter);
		}

		return fileChooser;
	}
}
