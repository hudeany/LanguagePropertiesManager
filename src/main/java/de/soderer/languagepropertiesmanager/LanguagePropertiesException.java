package de.soderer.languagepropertiesmanager;

/**
 * Exception for expected errors of the LanguagePropertiesManager (e.g. invalid
 * import files or parameters), whose message is meant to be shown to the user
 * without a stack trace.
 */
public class LanguagePropertiesException extends Exception {
	private static final long serialVersionUID = -7240533232921526907L;

	/**
	 * Creates an exception with a message for the user.
	 *
	 * @param errorMessage
	 *            message describing the error
	 */
	public LanguagePropertiesException(final String errorMessage) {
		super(errorMessage);
	}

	/**
	 * Creates an exception with a message for the user and the causing exception.
	 *
	 * @param errorMessage
	 *            message describing the error
	 * @param e
	 *            the causing exception
	 */
	public LanguagePropertiesException(final String errorMessage, final Exception e) {
		super(errorMessage, e);
	}
}
