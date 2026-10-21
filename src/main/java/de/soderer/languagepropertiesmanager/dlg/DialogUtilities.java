package de.soderer.languagepropertiesmanager.dlg;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import de.soderer.languagepropertiesmanager.image.ImageManager;
import de.soderer.utilities.LangResources;

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
}
