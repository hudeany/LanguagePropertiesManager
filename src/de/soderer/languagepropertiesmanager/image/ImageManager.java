package de.soderer.languagepropertiesmanager.image;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;

import de.soderer.utilities.VisibleException;

public class ImageManager {
	private static ImageManager instance = null;

	private final Shell shell;
	private final Map<String, Image> store = new HashMap<>();

	public ImageManager(final Shell shell) {
		this.shell = shell;
		instance = this;
	}

	private Image getImageFromString(final String name) {
		if (!store.containsKey(name)) {
			store.put(name, new Image(shell.getDisplay(), getClass().getResourceAsStream(name)));
		}

		return store.get(name);
	}

	public static Image getImage(final String name) throws VisibleException {
		if (instance == null) {
			throw new VisibleException("ImageManager needs to be initialized before usage");
		}

		return instance.getImageFromString(name);
	}
}
