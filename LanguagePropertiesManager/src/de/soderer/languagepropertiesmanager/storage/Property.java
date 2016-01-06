package de.soderer.languagepropertiesmanager.storage;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class Property {
	private int originalIndex;
	private String key;
	private Map<String, String> languageValues = new HashMap<String, String>();

	public Property(String key) {
		this.key = key;
	}
	
	public int getOriginalIndex() {
		return originalIndex;
	}

	public void setOriginalIndex(int originalIndex) {
		this.originalIndex = originalIndex;
	}

	public boolean isEmpty() {
		return StringUtils.isEmpty(key) || languageValues.size() == 0;
	}
	
	public void setKey(String key) {
		this.key = key;
	}
	
	public String getKey() {
		return key;
	}
		
	public void removeLanguageValue(String languageSign) {
		languageValues.remove(languageSign);
	}
	
	public String getLanguageValue(String languageSign) {
		return languageValues.get(languageSign);
	}
	
	public void setLanguageValue(String languageSign, String value) {
		if (StringUtils.isEmpty(value))
			languageValues.put(languageSign, null);
		else
			languageValues.put(languageSign, value);
	}
	
	public static class KeyComparator implements Comparator<String> {
		private boolean ascending;
		
		public KeyComparator(boolean ascending) {
			this.ascending = ascending;
		}
		
		@Override
		public int compare(String arg0, String arg1) {
			return arg0.toLowerCase().compareTo(arg1.toLowerCase()) * (ascending ? 1 : -1);
		}
	}

	public static class EntryValueExistsComparator implements Comparator<Map.Entry<String, Property>> {
		private String languageSign;
		private boolean ascending;
		
		public EntryValueExistsComparator(String languageSign, boolean ascending) {
			this.languageSign = languageSign;
			this.ascending = ascending;
		}
		
		@Override
		public int compare(Map.Entry<String, Property> entry1, Map.Entry<String, Property> entry2) {
			int result;
			
			boolean value1Exists = entry1.getValue().getLanguageValue(languageSign) != null;
			boolean value2Exists = entry2.getValue().getLanguageValue(languageSign) != null;
			if (value1Exists == value2Exists) {
				result = entry1.getKey().toLowerCase().compareTo(entry2.getKey().toLowerCase());
			} else if (value1Exists) {
				result = -1;
			} else {
				result = +1;
			}
			
			return result * (ascending ? 1 : -1);
		}
	}

	public static class OriginalIndexComparator implements Comparator<Map.Entry<String, Property>> {
		private boolean ascending;
		
		public OriginalIndexComparator(boolean ascending) {
			this.ascending = ascending;
		}		
		
		@Override
		public int compare(Map.Entry<String, Property> entry1, Map.Entry<String, Property> entry2) {
			int result = new Integer(entry1.getValue().getOriginalIndex()).compareTo(entry2.getValue().getOriginalIndex());
			return result * (ascending ? 1 : -1);
		}
	}
}
