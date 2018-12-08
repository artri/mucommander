package com.mucommander.ui.main.tabs;

import com.mucommander.commons.file.FileURL;

//TODO: wrong inheritance here. This class should not be extended from FileTableTab
//TODO: it should be probably more generic class to extend
public class ConfFileTableTab extends FileTableTab {
	
	public ConfFileTableTab(FileURL location) {
		this(false, location, null);
	}

	public ConfFileTableTab(boolean locked, FileURL location, String title) {
		super(location, locked, title, null);
	}

	@Override
	public void setLocation(FileURL location) {
		throw new UnsupportedOperationException("cannot change location of configuration tab");
	}

	@Override
	public void setLocked(boolean locked) {
		throw new UnsupportedOperationException("cannot lock configuration tab");
	}

	@Override
	public void setTitle(String title) {
		throw new UnsupportedOperationException("cannot change title of configuration tab");
	}
}
