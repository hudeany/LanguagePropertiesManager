package de.soderer.languagepropertiesmanager.utilities.jarinjarloader;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * Factory providing the {@link JarInJarURLStreamHandler} for the "rsrc" protocol
 */
public class JarInJarURLStreamHandlerFactory implements URLStreamHandlerFactory {
	private final ClassLoader classLoader;

	/**
	 * Creates the factory.
	 *
	 * @param classLoader
	 *            class loader to read the resources with
	 */
	public JarInJarURLStreamHandlerFactory(final ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public URLStreamHandler createURLStreamHandler(final String protocol) {
		if ("rsrc".equals(protocol)) {
			return new JarInJarURLStreamHandler(classLoader);
		} else {
			return null;
		}
	}
}
