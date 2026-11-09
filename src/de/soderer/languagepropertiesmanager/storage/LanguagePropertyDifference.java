package de.soderer.languagepropertiesmanager.storage;

/**
 * A single difference between existing language properties (e.g. loaded from the properties files) and
 * changed language properties (e.g. imported from an Excel file returned by a translator).
 */
public class LanguagePropertyDifference {
	/**
	 * Kind of a difference.
	 */
	public enum Type {
		/** Key exists in the changed properties only */
		KEY_ADDED,
		/** Key exists in the existing properties only */
		KEY_REMOVED,
		/** Language value was empty or missing and is filled now, e.g. a new translation */
		VALUE_ADDED,
		/** Language value was changed */
		VALUE_CHANGED,
		/** Language value was filled and is empty or missing now */
		VALUE_REMOVED,
		/** Comment was changed */
		COMMENT_CHANGED
	}

	private final Type type;
	private final LanguageProperty existingProperty;
	private final LanguageProperty changedProperty;
	private final String path;
	private final String key;
	private final String languageSign;
	private final String oldValue;
	private final String newValue;

	/**
	 * Creates a difference.
	 *
	 * @param type kind of the difference
	 * @param existingProperty existing property, null for {@link Type#KEY_ADDED} and its values
	 * @param changedProperty changed property, null for {@link Type#KEY_REMOVED}
	 * @param languageSign language sign for value differences, null for key and comment differences
	 * @param oldValue existing value or comment, null if not existing
	 * @param newValue changed value or comment, null if not existing
	 */
	public LanguagePropertyDifference(final Type type, final LanguageProperty existingProperty, final LanguageProperty changedProperty, final String languageSign, final String oldValue, final String newValue) {
		this.type = type;
		this.existingProperty = existingProperty;
		this.changedProperty = changedProperty;
		// Path and key are shown like in the existing properties, if available
		final LanguageProperty property = existingProperty != null ? existingProperty : changedProperty;
		path = property.getPath();
		key = property.getKey();
		this.languageSign = languageSign;
		this.oldValue = oldValue;
		this.newValue = newValue;
	}

	/**
	 * Kind of the difference.
	 *
	 * @return the kind of the difference
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Existing property, e.g. to select it in the GUI.
	 *
	 * @return the existing property, null for {@link Type#KEY_ADDED} and its values
	 */
	public LanguageProperty getExistingProperty() {
		return existingProperty;
	}

	/**
	 * Changed property.
	 *
	 * @return the changed property, null for {@link Type#KEY_REMOVED}
	 */
	public LanguageProperty getChangedProperty() {
		return changedProperty;
	}

	/**
	 * Path of the properties set.
	 *
	 * @return the path, may be empty
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Property key.
	 *
	 * @return the key
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Language sign of a value difference.
	 *
	 * @return the language sign, null for key and comment differences
	 */
	public String getLanguageSign() {
		return languageSign;
	}

	/**
	 * Existing value or comment.
	 *
	 * @return the old value, null if not existing
	 */
	public String getOldValue() {
		return oldValue;
	}

	/**
	 * Changed value or comment.
	 *
	 * @return the new value, null if not existing
	 */
	public String getNewValue() {
		return newValue;
	}

	@Override
	public String toString() {
		final StringBuilder text = new StringBuilder();
		text.append(type);
		if (languageSign != null) {
			text.append(" [").append(languageSign).append("]");
		}
		text.append(" ");
		if (path != null && !path.isEmpty()) {
			text.append(path).append(" : ");
		}
		text.append(key);
		switch (type) {
			case VALUE_ADDED:
				text.append(" = ").append(quote(newValue));
				break;
			case VALUE_CHANGED:
			case COMMENT_CHANGED:
				text.append(" : ").append(quote(oldValue)).append(" -> ").append(quote(newValue));
				break;
			case VALUE_REMOVED:
				text.append(" (was ").append(quote(oldValue)).append(")");
				break;
			default:
				break;
		}
		return text.toString();
	}

	private static String quote(final String value) {
		if (value == null) {
			return "<none>";
		} else {
			// Keep the report on one line per difference
			return "\"" + value.replace("\r", "\\r").replace("\n", "\\n") + "\"";
		}
	}
}
