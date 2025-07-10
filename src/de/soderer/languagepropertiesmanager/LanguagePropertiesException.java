package de.soderer.languagepropertiesmanager;

public class LanguagePropertiesException extends Exception {
	private static final long serialVersionUID = -7240533232921526907L;

	public LanguagePropertiesException(final String errorMessage) {
		super(errorMessage);
	}

	public LanguagePropertiesException(final String errorMessage, final Exception e) {
		super(errorMessage, e);
	}
}
