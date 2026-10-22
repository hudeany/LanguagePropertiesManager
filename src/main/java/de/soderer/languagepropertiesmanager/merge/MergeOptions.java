package de.soderer.languagepropertiesmanager.merge;

/**
 * Independent options of a merge import, chosen by the user. Replaces the former
 * fixed merge modes, so every effect of the import can be switched on or off by itself.
 */
public final class MergeOptions {
	/** Imported properties, which do not exist yet, are added */
	private final boolean addNewProperties;
	/** Empty values of existing properties are filled with the imported value */
	private final boolean fillEmptyValues;
	/** Non-empty values of existing properties, which differ from the imported value, are overwritten */
	private final boolean overwriteDifferingValues;

	/**
	 * Creates the options of a merge import.
	 *
	 * @param addNewProperties
	 *            whether imported properties, which do not exist yet, are added
	 * @param fillEmptyValues
	 *            whether empty values of existing properties are filled
	 * @param overwriteDifferingValues
	 *            whether differing non-empty values of existing properties are overwritten
	 */
	public MergeOptions(final boolean addNewProperties, final boolean fillEmptyValues, final boolean overwriteDifferingValues) {
		this.addNewProperties = addNewProperties;
		this.fillEmptyValues = fillEmptyValues;
		this.overwriteDifferingValues = overwriteDifferingValues;
	}

	/**
	 * Whether imported properties, which do not exist yet, are added.
	 *
	 * @return true if new properties are added
	 */
	public boolean isAddNewProperties() {
		return addNewProperties;
	}

	/**
	 * Whether empty values of existing properties are filled with the imported value.
	 *
	 * @return true if empty values are filled
	 */
	public boolean isFillEmptyValues() {
		return fillEmptyValues;
	}

	/**
	 * Whether non-empty values of existing properties, which differ from the imported value, are overwritten.
	 *
	 * @return true if differing values are overwritten
	 */
	public boolean isOverwriteDifferingValues() {
		return overwriteDifferingValues;
	}

	/**
	 * Whether applying these options to the given plan would change anything.
	 *
	 * @param mergePlan
	 *            plan of the merge import
	 * @return true if at least one selected option affects at least one property or value
	 */
	public boolean hasEffectOn(final MergePlan mergePlan) {
		return (addNewProperties && mergePlan.getNewPropertyCount() > 0)
				|| (fillEmptyValues && mergePlan.getFillableValueCount() > 0)
				|| (overwriteDifferingValues && mergePlan.getDifferingValueCount() > 0);
	}
}
