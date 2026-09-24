package de.soderer.languagepropertiesmanager.image;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;

import de.soderer.utilities.VisibleException;

/**
 * Loads and caches the application's icons from "/images/icons/".
 *
 * <p>
 * Unlike the SWT variant, Swing images need no Display/Shell and no disposal,
 * so there is no instance to initialize anymore.
 * </p>
 */
public class ImageManager {
	private static final Map<String, ImageIcon> STORE = new HashMap<>();

	private ImageManager() {
		// Static access only
	}

	public static synchronized ImageIcon getImage(final String name) throws VisibleException {
		ImageIcon image = STORE.get(name);
		if (image == null) {
			try (InputStream inputStream = ImageManager.class.getResourceAsStream("/images/icons/" + name)) {
				if (inputStream == null) {
					throw new VisibleException("Image not found: " + name);
				}
				image = new ImageIcon(inputStream.readAllBytes());
			} catch (final VisibleException e) {
				throw e;
			} catch (final Exception e) {
				throw new VisibleException("Cannot load image '" + name + "': " + e.getMessage());
			}
			STORE.put(name, image);
		}
		return image;
	}
}
