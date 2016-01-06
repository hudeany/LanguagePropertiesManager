package de.soderer.languagepropertiesmanager.image;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;

import de.soderer.utilities.VisibleException;

public class ImageManager {
	private static ImageManager instance = null;
	
	private Shell shell;
	private Map<String, Image> store = new HashMap<String, Image>();
	
	public ImageManager(Shell shell) {
		this.shell = shell;
		instance = this;
	}
	
	private Image getImageFromString(String name) {
		if (!store.containsKey(name)) {
			store.put(name, new Image(shell.getDisplay(), getClass().getResourceAsStream(name)));
		}
			
		return store.get(name);
	}

	public static Image getImage(String name) throws VisibleException {
		if (instance == null) {
			throw new VisibleException("ImageManager needs to be initialized before usage");
		}
			
		return instance.getImageFromString(name);
	}
}
