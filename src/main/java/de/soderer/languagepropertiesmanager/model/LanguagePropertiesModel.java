package de.soderer.languagepropertiesmanager.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.soderer.languagepropertiesmanager.merge.BaseSetReducer;
import de.soderer.languagepropertiesmanager.merge.LanguagePropertiesMerger;
import de.soderer.languagepropertiesmanager.merge.MergeMode;
import de.soderer.languagepropertiesmanager.merge.MergePlan;
import de.soderer.languagepropertiesmanager.merge.MergeResult;
import de.soderer.languagepropertiesmanager.merge.ReducePlan;
import de.soderer.languagepropertiesmanager.merge.ReduceResult;
import de.soderer.languagepropertiesmanager.storage.LanguagePropertiesFileSetReader;
import de.soderer.languagepropertiesmanager.storage.LanguageProperty;
import de.soderer.languagepropertiesmanager.storage.LoadedLanguageProperties;
import de.soderer.utilities.Utilities;

/**
 * The currently loaded language properties and the state shared by the views:
 * available language signs, name of the properties set, current selection and
 * whether there are unsaved changes.
 *
 * All changing operations of the data are done here. They never ask the user,
 * the GUI asks before and reports afterwards (most operations return counts for
 * that). Changes of "unsavedChanges" and "languagePropertiesSetName" are
 * reported to registered PropertyChangeListeners.
 */
public final class LanguagePropertiesModel {
	public static final String PROPERTY_UNSAVED_CHANGES = "unsavedChanges";
	public static final String PROPERTY_LANGUAGE_PROPERTIES_SET_NAME = "languagePropertiesSetName";

	private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

	/** Loaded properties, null if nothing is loaded */
	private List<LanguageProperty> languageProperties = null;
	/** Language signs of the loaded properties, default language first, null if nothing is loaded */
	private List<String> availableLanguageSigns = null;
	private String languagePropertiesSetName = null;
	private boolean unsavedChanges = false;

	/**
	 * Currently selected properties, tracked by object identity. This keeps the
	 * selection stable across sorting and key renames and also works for
	 * duplicate path/key combinations.
	 */
	private List<LanguageProperty> currentSelection = new ArrayList<>();

	public void addPropertyChangeListener(final PropertyChangeListener listener) {
		propertyChangeSupport.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(final PropertyChangeListener listener) {
		propertyChangeSupport.removePropertyChangeListener(listener);
	}

	// ---------- State

	public boolean isLoaded() {
		return languageProperties != null;
	}

	public boolean hasProperties() {
		return languageProperties != null && !languageProperties.isEmpty();
	}

	/**
	 * The loaded properties in their current order, or null if nothing is loaded.
	 * Workers may read and change the properties themselves, but the list should
	 * only be changed by the methods of this model.
	 */
	public List<LanguageProperty> getLanguageProperties() {
		return languageProperties;
	}

	/**
	 * Language signs of the loaded properties, default language first, or null if nothing is loaded
	 */
	public List<String> getAvailableLanguageSigns() {
		return availableLanguageSigns;
	}

	public boolean hasMultipleLanguages() {
		return availableLanguageSigns != null && availableLanguageSigns.size() > 1;
	}

	public String getLanguagePropertiesSetName() {
		return languagePropertiesSetName;
	}

	public void setLanguagePropertiesSetName(final String languagePropertiesSetName) {
		final String oldValue = this.languagePropertiesSetName;
		this.languagePropertiesSetName = languagePropertiesSetName;
		propertyChangeSupport.firePropertyChange(PROPERTY_LANGUAGE_PROPERTIES_SET_NAME, oldValue, languagePropertiesSetName);
	}

	public boolean hasUnsavedChanges() {
		return unsavedChanges;
	}

	public void setUnsavedChanges(final boolean unsavedChanges) {
		final boolean oldValue = this.unsavedChanges;
		this.unsavedChanges = unsavedChanges;
		propertyChangeSupport.firePropertyChange(PROPERTY_UNSAVED_CHANGES, oldValue, unsavedChanges);
	}

	// ---------- Selection

	public List<LanguageProperty> getCurrentSelection() {
		return Collections.unmodifiableList(currentSelection);
	}

	/**
	 * First selected property, which is shown in the detail view, or null if nothing is selected
	 */
	public LanguageProperty getFirstSelectedProperty() {
		return currentSelection.isEmpty() ? null : currentSelection.get(0);
	}

	public void setCurrentSelection(final Collection<LanguageProperty> selection) {
		currentSelection = selection == null ? new ArrayList<>() : new ArrayList<>(selection);
	}

	public void clearCurrentSelection() {
		currentSelection = new ArrayList<>();
	}

	/**
	 * Removes properties from the selection, which do not exist anymore (e.g. after deleting or reloading)
	 */
	public void retainExistingSelection() {
		if (languageProperties == null) {
			currentSelection = new ArrayList<>();
		} else {
			final Set<LanguageProperty> existingProperties = Collections.newSetFromMap(new IdentityHashMap<>());
			existingProperties.addAll(languageProperties);
			currentSelection.removeIf(property -> !existingProperties.contains(property));
		}
	}

	// ---------- Loading and resetting

	/**
	 * Replaces the loaded data. The selection is cleared and there are no unsaved changes afterwards.
	 */
	public void load(final LoadedLanguageProperties loadedLanguageProperties) {
		languageProperties = loadedLanguageProperties.getLanguageProperties();
		availableLanguageSigns = loadedLanguageProperties.getAvailableLanguageSigns();
		setLanguagePropertiesSetName(loadedLanguageProperties.getCombinedSetName());
		clearCurrentSelection();
		setUnsavedChanges(false);
	}

	/**
	 * Starts with empty data, which only knows the default language
	 */
	public void createEmpty(final String newLanguagePropertiesSetName) {
		languageProperties = new ArrayList<>();
		availableLanguageSigns = new ArrayList<>();
		availableLanguageSigns.add(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);
		setLanguagePropertiesSetName(newLanguagePropertiesSetName);
		clearCurrentSelection();
		setUnsavedChanges(false);
	}

	/**
	 * Removes all loaded data
	 */
	public void reset() {
		languageProperties = null;
		availableLanguageSigns = null;
		setLanguagePropertiesSetName(null);
		clearCurrentSelection();
		setUnsavedChanges(false);
	}

	// ---------- Properties

	/**
	 * Appends a new property, selects it and marks the data as changed
	 */
	public void addProperty(final LanguageProperty newProperty) {
		newProperty.setOriginalIndex(languageProperties.size() + 1);
		languageProperties.add(newProperty);
		setCurrentSelection(Collections.singletonList(newProperty));
		setUnsavedChanges(true);
	}

	/**
	 * Removes the given properties by identity, so also exactly the given one of
	 * several duplicates is removed. The selection is cleared.
	 *
	 * @return number of removed properties
	 */
	public int removeProperties(final Collection<LanguageProperty> propertiesToRemove) {
		final Set<LanguageProperty> propertiesToRemoveSet = Collections.newSetFromMap(new IdentityHashMap<>());
		propertiesToRemoveSet.addAll(propertiesToRemove);
		final int sizeBefore = languageProperties.size();
		languageProperties.removeIf(propertiesToRemoveSet::contains);
		final int removedCount = sizeBefore - languageProperties.size();
		clearCurrentSelection();
		if (removedCount > 0) {
			setUnsavedChanges(true);
		}
		return removedCount;
	}

	/**
	 * Changes only the order of the properties, which is no change of the data
	 */
	public void sortProperties(final Comparator<LanguageProperty> comparator) {
		languageProperties = languageProperties.stream().sorted(comparator).collect(Collectors.toList());
	}

	// ---------- Languages

	/**
	 * Determines the available language signs from the properties and gives every
	 * property every language sign (missing ones with a null value)
	 *
	 * @return language signs, which did not exist before
	 */
	private List<String> completeLanguageSigns() {
		final Set<String> previousLanguageSigns = availableLanguageSigns == null ? new HashSet<>() : new HashSet<>(availableLanguageSigns);
		availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);
		for (final LanguageProperty languageProperty : languageProperties) {
			for (final String languageSign : availableLanguageSigns) {
				if (!languageProperty.getAvailableLanguageSigns().contains(languageSign)) {
					languageProperty.setLanguageValue(languageSign, null);
				}
			}
		}
		final List<String> newLanguageSigns = new ArrayList<>();
		for (final String languageSign : availableLanguageSigns) {
			if (!previousLanguageSigns.contains(languageSign)) {
				newLanguageSigns.add(languageSign);
			}
		}
		return newLanguageSigns;
	}

	/**
	 * Adds a language. All properties get a missing (null) value for it, so the
	 * language column exists, but nothing is written into its file yet.
	 */
	public void addLanguage(final String newLanguageSign) {
		for (final LanguageProperty languageProperty : languageProperties) {
			if (!languageProperty.getAvailableLanguageSigns().contains(newLanguageSign)) {
				languageProperty.setLanguageValue(newLanguageSign, null);
			}
		}
		updateAvailableLanguageSigns();
	}

	/**
	 * Removes a language from all properties. The last language can not be deleted.
	 *
	 * @return true if the language was deleted
	 */
	public boolean deleteLanguage(final String languageSign) {
		if (Utilities.isBlank(languageSign) || availableLanguageSigns == null || availableLanguageSigns.size() <= 1) {
			return false;
		}
		for (final LanguageProperty languageProperty : languageProperties) {
			languageProperty.removeLanguageValue(languageSign);
		}
		updateAvailableLanguageSigns();
		setUnsavedChanges(true);
		return true;
	}

	private void updateAvailableLanguageSigns() {
		availableLanguageSigns = Utilities.sortButPutItemsFirst(LanguagePropertiesFileSetReader.getAvailableLanguageSignsOfProperties(languageProperties), LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT);
	}

	// ---------- Bulk changes of values, comments and paths

	public long countComments() {
		return languageProperties.stream().filter(languageProperty -> Utilities.isNotEmpty(languageProperty.getComment())).count();
	}

	public void deleteAllComments() {
		for (final LanguageProperty languageProperty : languageProperties) {
			languageProperty.setComment(null);
		}
		setUnsavedChanges(true);
	}

	public long countPaths() {
		return languageProperties.stream().filter(languageProperty -> Utilities.isNotEmpty(languageProperty.getPath())).count();
	}

	/**
	 * Properties without a path get a new path on the next save
	 */
	public void deleteAllPaths() {
		for (final LanguageProperty languageProperty : languageProperties) {
			// Empty string (not null) is the representation of "no path", which saving relies on
			languageProperty.setPath("");
		}
		setUnsavedChanges(true);
	}

	public static long countNonEmptyValues(final Collection<LanguageProperty> properties, final String languageSign) {
		return properties.stream().filter(languageProperty -> Utilities.isNotEmpty(languageProperty.getLanguageValue(languageSign))).count();
	}

	/**
	 * Removes the values of one language from all properties, but keeps the language itself
	 */
	public void deleteAllLanguageValues(final String languageSign) {
		for (final LanguageProperty languageProperty : languageProperties) {
			// A null value keeps the language sign registered (same as in addLanguage()), so the column stays
			languageProperty.setLanguageValue(languageSign, null);
		}
		setUnsavedChanges(true);
	}

	/**
	 * Sets the value of one language of the given properties to an explicitly
	 * empty value ("" is written as "key=") or to a missing value (null, the key is
	 * not written into this language file and ResourceBundle falls back to the
	 * default value) or any other value.
	 *
	 * @return true if any value was changed
	 */
	public boolean setLanguageValues(final Collection<LanguageProperty> properties, final String languageSign, final String newValue) {
		boolean changed = false;
		for (final LanguageProperty languageProperty : properties) {
			final String oldValue = languageProperty.getLanguageValue(languageSign);
			if (oldValue == null ? newValue != null : !oldValue.equals(newValue)) {
				languageProperty.setLanguageValue(languageSign, newValue);
				changed = true;
			}
		}
		if (changed) {
			setUnsavedChanges(true);
		}
		return changed;
	}

	/**
	 * Copies the non blank values of the source language into the target language
	 *
	 * @return number of transferred values
	 */
	public int transferValues(final Collection<LanguageProperty> properties, final String sourceLanguageSign, final String targetLanguageSign) {
		int countTransfers = 0;
		for (final LanguageProperty languageProperty : properties) {
			final String sourceValue = languageProperty.getLanguageValue(sourceLanguageSign);
			if (Utilities.isNotBlank(sourceValue)) {
				languageProperty.setLanguageValue(targetLanguageSign, sourceValue);
				countTransfers++;
			}
		}
		if (countTransfers > 0) {
			setUnsavedChanges(true);
		}
		return countTransfers;
	}

	/**
	 * Removes the values of the target language, which are identical to the source language
	 *
	 * @return number of cleared values
	 */
	public int clearIdenticalValues(final Collection<LanguageProperty> properties, final String sourceLanguageSign, final String targetLanguageSign) {
		int countCleared = 0;
		for (final LanguageProperty languageProperty : properties) {
			final String sourceValue = languageProperty.getLanguageValue(sourceLanguageSign);
			final String targetValue = languageProperty.getLanguageValue(targetLanguageSign);
			if (Utilities.isNotBlank(targetValue) && targetValue.equals(sourceValue)) {
				languageProperty.setLanguageValue(targetLanguageSign, null);
				countCleared++;
			}
		}
		if (countCleared > 0) {
			setUnsavedChanges(true);
		}
		return countCleared;
	}

	// ---------- Duplicates

	/**
	 * Finds properties with the same combination of path and key. Nothing is changed here.
	 */
	public DuplicatesPlan findDuplicates() {
		final Map<String, List<LanguageProperty>> groupedByPathAndKey = new LinkedHashMap<>();
		for (final LanguageProperty languageProperty : languageProperties) {
			final String groupKey = languageProperty.getPath() + "\u0000" + languageProperty.getKey();
			groupedByPathAndKey.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(languageProperty);
		}

		final DuplicatesPlan duplicatesPlan = new DuplicatesPlan();
		for (final List<LanguageProperty> group : groupedByPathAndKey.values()) {
			if (group.size() > 1) {
				duplicatesPlan.duplicateGroups.add(group.stream()
						.sorted(Comparator.comparing(LanguageProperty::getOriginalIndex))
						.collect(Collectors.toList()));
			}
		}
		return duplicatesPlan;
	}

	/**
	 * For every duplicate group, keeps the property with the lowest original index.
	 * Before discarding the rest, it adopts any language value (and comment) it is
	 * still missing from the duplicates, in order of original index, so the first
	 * available value wins. The selection is cleared.
	 *
	 * @return number of removed properties
	 */
	public int removeDuplicates(final DuplicatesPlan duplicatesPlan) {
		final List<LanguageProperty> duplicatesToRemove = new ArrayList<>();
		for (final List<LanguageProperty> group : duplicatesPlan.duplicateGroups) {
			final LanguageProperty keptProperty = group.get(0);
			final List<LanguageProperty> duplicates = group.subList(1, group.size());
			for (final LanguageProperty duplicate : duplicates) {
				for (final String languageSign : duplicate.getAvailableLanguageSigns()) {
					final String duplicateValue = duplicate.getLanguageValue(languageSign);
					if (Utilities.isNotEmpty(duplicateValue) && Utilities.isEmpty(keptProperty.getLanguageValue(languageSign))) {
						keptProperty.setLanguageValue(languageSign, duplicateValue);
					}
				}
				if (Utilities.isEmpty(keptProperty.getComment()) && Utilities.isNotEmpty(duplicate.getComment())) {
					keptProperty.setComment(duplicate.getComment());
				}
			}
			duplicatesToRemove.addAll(duplicates);
		}
		return removeProperties(duplicatesToRemove);
	}

	// ---------- Merge import and reduction by a base set

	/**
	 * Applies a merge import. If nothing was loaded before, the imported data
	 * becomes the loaded data. Afterwards every property knows every language
	 * sign and the added or changed properties are selected.
	 */
	public MergeResult applyMerge(final LoadedLanguageProperties importSource, final MergePlan mergePlan, final MergeMode mergeMode) {
		final boolean noDataLoadedYet = languageProperties == null;
		if (noDataLoadedYet) {
			languageProperties = new ArrayList<>();
			availableLanguageSigns = new ArrayList<>();
			setLanguagePropertiesSetName(importSource.getCombinedSetName());
		}

		final MergeResult mergeResult = LanguagePropertiesMerger.applyMergePlan(languageProperties, mergePlan, mergeMode);

		final List<String> newLanguageSigns = completeLanguageSigns();
		if (!noDataLoadedYet) {
			for (final String languageSign : newLanguageSigns) {
				mergeResult.addNewLanguageSign(languageSign);
			}
		}

		if (mergeResult.hasChanges()) {
			setUnsavedChanges(true);
		}

		// Select the added and changed properties, so they can be reviewed or translated directly
		setCurrentSelection(mergeResult.getChangedProperties());
		return mergeResult;
	}

	/**
	 * Applies a reduction by a base set. The reduced properties, which still
	 * exist, are selected afterwards.
	 */
	public ReduceResult applyReduction(final ReducePlan reducePlan, final boolean removeEmptyProperties) {
		final ReduceResult reduceResult = BaseSetReducer.applyReducePlan(languageProperties, reducePlan, removeEmptyProperties);
		setUnsavedChanges(true);

		// Select the reduced properties that still exist, so they can be reviewed directly
		setCurrentSelection(reduceResult.getChangedProperties());
		return reduceResult;
	}
}
