/*
 * This file is part of muCommander, http://www.mucommander.com
 * Copyright (C) 2002-2016 Maxence Bernard
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mucommander.ui.main.tabs;

import java.util.Objects;

import com.mucommander.bookmark.BookmarkManager;
import com.mucommander.commons.file.FileURL;
import com.mucommander.commons.file.protocol.local.LocalFile;
import com.mucommander.commons.file.util.PathUtils;
import com.mucommander.core.LocalLocationHistory;
import com.mucommander.text.Translator;
import com.mucommander.ui.tabs.Tab;

/**
 * Interface of tab in the {@link com.mucommander.ui.main.FolderPanel} that contains a {@link com.mucommander.ui.main.table.FileTable}
 *
 * @author Arik Hadas
 */
public class FileTableTab implements Tab {

	/** The location presented in this tab */
	private FileURL location;

	/** Flag that indicates whether the tab is locked or not */
	private boolean locked;

	/** Title that is assigned for the tab */
	private String title;

	/** History of accessed location within the tab */
	private final LocalLocationHistory locationHistory;

	/**
	 * c~tor
	 * @param location
	 * @param locked
	 * @param title
	 * @param locationHistory
	 */
	protected FileTableTab(FileURL location, boolean locked, String title, LocalLocationHistory locationHistory) {
		this.location = location;
		this.locked = locked;
		this.title = title;
		this.locationHistory = locationHistory;
	}
	
	/**
	 * Setter for the location presented in the tab
	 * 
	 * @param location the file that is going to be presented in the tab
	 */
	public void setLocation(FileURL location) {
		this.location = location;
		
		// add location to the history (See LocalLocationHistory to see how it handles the first location it gets)
		this.locationHistory.tryToAddToHistory(location);
	}

	/**
	 * Getter for the location presented in the tab
	 * 
	 * @return the file that is being presented in the tab
	 */
	public FileURL getLocation() {
		return location;
	}
	
	/**
	 * Set the tab to be locked or unlocked according to the given flag
	 * 
	 * @param locked flag that indicates whether the tab should be locked or not
	 */
	public void setLocked(boolean locked) {
		this.locked = locked;
	}
	
	/**
	 * Returns whether the tab is locked
	 * 
	 * @return indication whether the tab is locked
	 */
	public boolean isLocked() {
		 return locked;
	}

	/**
	 * Set the title of the tab to the given string
	 * 
	 * @param title - predefined title to be assigned to the tab, null for no predefined title
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * Returns the title that was assigned for the tab
	 * 
	 * @return the title that was assigned for the tab, null is returned if no title was assigned
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Returns the tracker of the last accessed locations within the tab
	 * 
	 * @return tracker of the last accessed locations within the tab
	 */
	public LocalLocationHistory getLocationHistory() {
		return locationHistory;
	}
	
	/**
	 * Returns a string representation for the tab:
	 *  the tab's fixed title will be returned if such title was assigned,
	 *  otherwise, a string representation will be created based on the tab's location:
	 *    for local file, the filename will be returned ("/" in case the root folder is presented)
	 *    for remote file, the returned pattern will be "\<host\>:\<filename\>"
	 * 
	 * @return String representation of the tab
	 */
	public String getDisplayableTitle() {
		String title = getTitle();

		return title != null ? title : createDisplayableTitleFromLocation(getLocation());
	}

	private String createDisplayableTitleFromLocation(FileURL location) {
	    if (BookmarkManager.isBookmark(location) && location.getHost() == null) {
	        return Translator.get("bookmarks_menu");
	    }

		boolean local = FileURL.LOCALHOST.equals(location.getHost());

		return getHostRepresentation(location.getHost(), local) + getFilenameRepresentation(location.getFilename(), local);
	}

	private String getHostRepresentation(String host, boolean local) {
		return local ? "" : host + ":";
	}

	private String getFilenameRepresentation(String filename, boolean local) {
		// Under for OSes with 'root drives' (Windows, OS/2), remove the leading '/' character
		if(local && LocalFile.hasRootDrives() && filename != null) {
			return PathUtils.removeLeadingSeparator(filename, "/");
		}
		// Under other OSes, if the filename is empty return "/"
		return filename == null ? "/" : filename;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof FileTableTab)) {
			return false;
		}
		FileTableTab another = (FileTableTab) obj;
		return Objects.equals(location, another.location)
				&& Objects.equals(locked,  another.locked);
	}

	@Override
	public int hashCode() {
		return Objects.hash(location, locked);
	}
	
	@Override
	public String toString() {
		return getDisplayableTitle();
	}
	
	public static FileTableTab of(LocalLocationHistory locationHistory, FileURL location) {
		Objects.requireNonNull(locationHistory, "expected locationHistory should not be null");
		Objects.requireNonNull(location, "expected location should not be null");
		
		return new FileTableTab(location, false, null, locationHistory);
	}
	
	public static FileTableTab of(LocalLocationHistory locationHistory, FileTableTab tab) {
		Objects.requireNonNull(locationHistory, "expected locationHistory should not be null");
		Objects.requireNonNull(tab, "expected tab should not be null");
		Objects.requireNonNull(tab.getLocation(), "expected location should not be null");
		
		return new FileTableTab(tab.getLocation(), tab.isLocked(), tab.getTitle(), locationHistory);
	}
}
