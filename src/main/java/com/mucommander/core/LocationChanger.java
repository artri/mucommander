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

package com.mucommander.core;

import java.net.MalformedURLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mucommander.auth.CredentialsManager;
import com.mucommander.auth.CredentialsMapping;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileURL;
import com.mucommander.ui.event.LocationManager;
import com.mucommander.ui.main.FolderPanel;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.utils.Callback;

/**
 * 
 * @author Arik Hadas, Maxence Bernard
 */
public class LocationChanger implements ChangeFolderTask.Listener {
	private static final Logger LOGGER = LoggerFactory.getLogger(LocationChanger.class);
	
    /** Last time folder has changed */
    private long lastFolderChangeTime;

	private ChangeFolderTask changeFolderTask;

	/** The lock object used to prevent simultaneous folder change operations */
	private final Object FOLDER_CHANGE_LOCK = new Object();
	
	private MainFrame mainFrame;
	private FolderPanel folderPanel;
	
	private LocationManager locationManager;
	
	public LocationChanger(MainFrame mainFrame, FolderPanel folderPanel, LocationManager locationManager) {
		this.mainFrame = mainFrame;
		this.folderPanel = folderPanel;
		this.locationManager = locationManager;
	}
	
	/**
	 * This method is triggered internally (i.e not by user request) to change the current
	 * folder to the given folder
	 *
	 * @param folderURL the URL of the folder to switch to
	 * @param callback the {@link Callback#call()} method will be called when folder has changed
	 */
	public void tryChangeCurrentFolderInternal(final FileURL folderURL, final Callback callback) {
		new ChangeCurrentFolderInternalTask(mainFrame, locationManager, folderURL, callback).execute();
	}

	/**
	 * Tries to change the current folder to the new specified one and notifies the user in case of a problem.
	 *
	 * <p>This method spawns a separate thread that takes care of the actual folder change and returns it.
	 * It does nothing and returns <code>null</code> if another folder change is already underway.</p>
	 *
	 * <p>
	 * This method is <b>not</b> I/O-bound and returns immediately, without any chance of locking the calling thread.
	 * </p>
	 *
	 * @param folder the folder to be made current folder
	 * @param changeLockedTab - flag that indicates whether to change the presented folder in the currently selected tab although it's locked
	 * @return the thread that performs the actual folder change, null if another folder change is already underway
	 */
	public ChangeFolderTask tryChangeCurrentFolder(AbstractFile folder, boolean changeLockedTab) {
		return tryChangeCurrentFolder(folder, null, false, changeLockedTab);
	}

	/**
	 * Tries to change current folder to the new specified one, and selects the given file after the folder has been
	 * changed. The user is notified by a dialog if the folder could not be changed.
	 *
	 * <p>If the current folder could not be changed to the requested folder and <code>findWorkableFolder</code> is
	 * <code>true</code>, the current folder will be changed to the first existing parent of the request folder if there
	 * is one, to the first existing local volume otherwise. In the unlikely event that no local volume is workable,
	 * the user will be notified that the folder could not be changed.</p>
	 *
	 * <p>This method spawns a separate thread that takes care of the actual folder change and returns it.
	 * It does nothing and returns <code>null</code> if another folder change is already underway.</p>
	 *
	 * <p>
	 * This method is <b>not</b> I/O-bound and returns immediately, without any chance of locking the calling thread.
	 * </p>
	 *
	 * @param folder the folder to be made current folder
	 * @param selectThisFileAfter the file to be selected after the folder has been changed (if it exists in the folder), can be null in which case FileTable rules will be used to select current file
	 * @param changeLockedTab - flag that indicates whether to change the presented folder in the currently selected tab although it's locked
	 * @return the thread that performs the actual folder change, null if another folder change is already underway  
	 */
	public ChangeFolderTask tryChangeCurrentFolder(AbstractFile folder, AbstractFile selectThisFileAfter, boolean findWorkableFolder, boolean changeLockedTab) {
		LOGGER.debug("folder={} selectThisFileAfter={}", folder, selectThisFileAfter);

		synchronized(FOLDER_CHANGE_LOCK) {
			// Make sure a folder change is not already taking place. This can happen under rare but normal
			// circumstances, if this method is called before the folder change thread has had the time to call
			// MainFrame#setNoEventsMode.
			if(changeFolderTask != null) {
				LOGGER.debug("A folder change is already taking place ({}), returning null", changeFolderTask);
				return null;
			}

			// Important: the ChangeFolderThread instance must be kept in a local variable (as opposed to the
			// changeFolderThread field only) before being returned. The reason for this is that ChangeFolderThread
			// changes the changeFolderThread field to null when finished, and it may do so before this method has
			// returned (I've seen this happening). Relying solely on the changeFolderThread field could thus cause
			// a null value to be returned, which is particularly problematic during startup (would cause an NPE).
			ChangeFolderTask thread = new ChangeFolderTask(this, mainFrame, locationManager, folderPanel.getFileTable().getSelectedFile(),
					folder, findWorkableFolder, changeLockedTab);

			if (selectThisFileAfter != null) {
				thread.selectThisFileAfter(selectThisFileAfter);
			}
			thread.execute();

			changeFolderTask = thread;
			return thread;
		}
	}

	/**
	 * Tries to change the current folder to the specified path and notifies the user in case of a problem.
	 *
	 * <p>This method spawns a separate thread that takes care of the actual folder change and returns it.
	 * It does nothing and returns <code>null</code> if another folder change is already underway or if the given
	 * path could not be resolved.</p>
	 *
	 * <p>
	 * This method is <b>not</b> I/O-bound and returns immediately, without any chance of locking the calling thread.
	 * </p>
	 *
	 * @param folderPath path to the new current folder. If this path does not resolve into a file, an error message will be displayed.
	 * @return the thread that performs the actual folder change, null if another folder change is already underway or if the given path could not be resolved
	 * @throws MalformedURLException if FileURL could not be resolved
	 */
	public ChangeFolderTask tryChangeCurrentFolder(String folderPath) throws MalformedURLException {
		return tryChangeCurrentFolder(FileURL.getFileURL(folderPath), null, false);
	}

	/**
	 * Tries to change current folder to the new specified URL and notifies the user in case of a problem.
	 *
	 * <p>This method spawns a separate thread that takes care of the actual folder change and returns it.
	 * It does nothing and returns <code>null</code> if another folder change is already underway.</p>
	 *
	 * <p>
	 * This method is <b>not</b> I/O-bound and returns immediately, without any chance of locking the calling thread.
	 * </p>
	 *
	 * @param folderURL location to the new current folder. If this URL does not resolve into a file, an error message will be displayed.
	 * @return the thread that performs the actual folder change, null if another folder change is already underway
	 */
	public ChangeFolderTask tryChangeCurrentFolder(FileURL folderURL) {
		return tryChangeCurrentFolder(folderURL, null, false);
	}

	public ChangeFolderTask tryChangeCurrentFolder(FileURL folderURL, boolean changeLockedTab) {
		return tryChangeCurrentFolder(folderURL, null, changeLockedTab);
	}

	/**
	 * Tries to change current folder to the new specified path and notifies the user in case of a problem.
	 * If not <code>null</code>, the specified {@link com.mucommander.auth.CredentialsMapping} is used to authenticate
	 * the folder, and added to {@link CredentialsManager} if the folder has been successfully changed.</p>
	 *
	 * <p>This method spawns a separate thread that takes care of the actual folder change and returns it.
	 * It does nothing and returns <code>null</code> if another folder change is already underway.</p>
	 *
	 * <p>
	 * This method is <b>not</b> I/O-bound and returns immediately, without any chance of locking the calling thread.
	 * </p>
	 *
	 * @param folderURL folder's URL to be made current folder. If this URL does not resolve into an existing file, an error message will be displayed.
	 * @param credentialsMapping the CredentialsMapping to use for authentication, can be null
	 * @return the thread that performs the actual folder change, null if another folder change is already underway
	 */
	public ChangeFolderTask tryChangeCurrentFolder(FileURL folderURL, CredentialsMapping credentialsMapping, boolean changeLockedTab) {
		LOGGER.debug("folderURL={}", folderURL);

		synchronized(FOLDER_CHANGE_LOCK) {
			// Make sure a folder change is not already taking place. This can happen under rare but normal
			// circumstances, if this method is called before the folder change thread has had the time to call
			// MainFrame#setNoEventsMode.
			if (changeFolderTask != null) {
				LOGGER.debug("A folder change is already taking place ("+changeFolderTask+"), returning null");
				return null;
			}

			// Important: the ChangeFolderThread instance must be kept in a local variable (as opposed to the
			// changeFolderThread field only) before being returned. The reason for this is that ChangeFolderThread
			// changes the changeFolderThread field to null when finished, and it may do so before this method has
			// returned (I've seen this happening). Relying solely on the changeFolderThread field could thus cause
			// a null value to be returned, which is particularly problematic during startup (would cause an NPE).
			ChangeFolderTask thread = new ChangeFolderTask(this, mainFrame, locationManager, folderPanel.getFileTable().getSelectedFile(), 
					folderURL, credentialsMapping, changeLockedTab);
			thread.execute();

			changeFolderTask = thread;
			return thread;
		}
	}

	public void tryStopChangeFolderTask() {
		synchronized (FOLDER_CHANGE_LOCK) {
			if (null != changeFolderTask) {
				changeFolderTask.tryKill();
			}
		}               
	}

	/**
	 * Shorthand for {@link #tryRefreshCurrentFolder(AbstractFile)} called with no specific file (<code>null</code>)
	 * to select after the folder has been changed.
	 *
	 * @return the thread that performs the actual folder change, null if another folder change is already underway
	 */
	public ChangeFolderTask tryRefreshCurrentFolder() {
		return tryRefreshCurrentFolder(null);
	}

	/**
	 * Refreshes the current folder's contents. If the folder is no longer available, the folder will be changed to a
	 * 'workable' folder (see {@link #tryChangeCurrentFolder(AbstractFile, AbstractFile, boolean)}.
	 *
	 * <p>This method spawns a separate thread that takes care of the actual folder change and returns it.
	 * It does nothing and returns <code>null</code> if another folder change is already underway.</p>
	 *
	 * <p>This method is <b>not</b> I/O-bound and returns immediately, without any chance of locking the calling thread.</p>
	 *
	 * @param selectThisFileAfter file to be selected after the folder has been refreshed (if it exists in the folder),
	 * can be null in which case FileTable rules will be used to select current file
	 * @return the thread that performs the actual folder change, null if another folder change is already underway
	 * @see #tryChangeCurrentFolder(AbstractFile, AbstractFile, boolean)
	 */
	public ChangeFolderTask tryRefreshCurrentFolder(AbstractFile selectThisFileAfter) {
		folderPanel.getFoldersTreePanel().refreshFolder(locationManager.getCurrentFolder());
		return tryChangeCurrentFolder(locationManager.getCurrentFolder(), selectThisFileAfter, true, true);
	}

    /**
     * Returns the time at which the last folder change completed successfully.
     *
     * @return the time at which the last folder change completed successfully.
     */
    public long getLastFolderChangeTime() {
        return lastFolderChangeTime;
    }

    /**
     * Returns the thread that is currently changing the current folder, <code>null</code> is the folder is not being
     * changed.
     *
     * @return the thread that is currently changing the current folder, <code>null</code> is the folder is not being
     * changed
     */
    public ChangeFolderTask getChangeFolderThread() {
        return changeFolderTask;
    }

    /**
     * Returns <code>true</code> ´if the current folder is currently being changed, <code>false</code> otherwise.
     *
     * @return <code>true</code> ´if the current folder is currently being changed, <code>false</code> otherwise
     */
    public boolean isFolderChanging() {
        return changeFolderTask != null;
    }
    
	/**
	 * ChangeFolderThread.Listener
	 */
	@Override
	public void lastFolderChangeTimeChanged(long lastFolderChangeTime) {
		this.lastFolderChangeTime = lastFolderChangeTime;
	}
	
	/**
	 * ChangeFolderThread.Listener
	 */
	@Override
	public void progressChanged(int progress) {
		folderPanel.setProgressValue(progress);
	}
	
	/**
	 * ChangeFolderThread.Listener
	 */
	@Override	
	public void changeFolderCompleted() {
		synchronized(FOLDER_CHANGE_LOCK) {
			changeFolderTask = null;
		}		
	}	
}
