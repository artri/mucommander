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

package com.mucommander.ui.event;

import java.awt.EventQueue;
import java.net.MalformedURLException;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mucommander.auth.CredentialsManager;
import com.mucommander.auth.CredentialsMapping;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileFactory;
import com.mucommander.commons.file.FileURL;
import com.mucommander.commons.file.protocol.FileProtocols;
import com.mucommander.commons.file.protocol.local.LocalFile;
import com.mucommander.commons.file.util.FileSet;
import com.mucommander.core.ChangeFolderTask;
import com.mucommander.core.FolderChangeMonitor;
import com.mucommander.core.GlobalLocationHistory;
import com.mucommander.core.NullableFile;
import com.mucommander.text.Translator;
import com.mucommander.ui.dialog.InformationDialog;
import com.mucommander.ui.dialog.QuestionDialog;
import com.mucommander.ui.dialog.auth.AuthDialog;
import com.mucommander.ui.dialog.file.DownloadDialog;
import com.mucommander.ui.main.ConfigurableFolderFilter;
import com.mucommander.ui.main.FolderPanel;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.utils.Callback;
import com.mucommander.utils.EventListenerSet;
import com.mucommander.utils.ExecutorManager;

/**
 * @author Maxence Bernard
 */
public class LocationManager implements ChangeFolderTask.Listener {
	private static final Logger LOGGER = LoggerFactory.getLogger(LocationManager.class);
	
	protected static final int CANCEL_ACTION = 0;
	protected static final int BROWSE_ACTION = 1;
	protected static final int DOWNLOAD_ACTION = 2;

	private static final String CANCEL_TEXT = Translator.get("cancel");
	private static final String BROWSE_TEXT = Translator.get("browse");
	private static final String DOWNLOAD_TEXT = Translator.get("download");
	
	private static final BiConsumer<LocationListener, LocationListener.Event> FIRE_LOCATION_CHANGING = (listener, event) -> { listener.locationChanging(event); };
	private static final BiConsumer<LocationListener, LocationListener.Event> FIRE_LOCATION_CHANGED = (listener, event) -> { listener.locationChanged(event); };
	private static final BiConsumer<LocationListener, LocationListener.Event> FIRE_LOCATION_CANCELLED = (listener, event) -> { listener.locationCancelled(event); };
	private static final BiConsumer<LocationListener, LocationListener.Event> FIRE_LOCATION_FAILED = (listener, event) -> { listener.locationFailed(event); };
	   
    /** The FolderPanel instance this LocationManager manages location events for */
    private final FolderPanel folderPanel;

    /** Contains all registered location listeners, stored as weak references */
    private EventListenerSet<LocationListener> locationListeners = EventListenerSet.weakListenerSet();
    
    /** Filters out unwanted files when listing folder contents */
	private ConfigurableFolderFilter configurableFolderFilter = new ConfigurableFolderFilter();    
    
    /** Current location presented in the FolderPanel */
    private AbstractFile currentFolder;
    /** Last time folder has changed */
    private long lastFolderChangeTime;

	/** The lock object used to prevent simultaneous folder change operations */
	private final Object FOLDER_CHANGE_LOCK = new Object();

	/** Current active task on folder change */
	private ChangeFolderTask changeFolderTask;
	
	private FolderChangeMonitor folderChangeMonitor;

    /**
     * Creates a new LocationManager that manages location events listeners and broadcasts for the specified FolderPanel.
     *
     * @param folderPanel the FolderPanel instance this LocationManager manages location events for
     */
    public LocationManager(FolderPanel folderPanel) {
		this.folderPanel = Objects.requireNonNull(folderPanel);
        
        addLocationListener(GlobalLocationHistory.Instance());
    }

    public FolderPanel getFolderPanel() {
    	return this.folderPanel;
    }
    
    private MainFrame getMainFrame() {
    	return this.folderPanel.getMainFrame();
    }
    
    /**
     * Set the given {@link AbstractFile} as the folder presented in the {@link FolderPanel}.
     * This method saves the given {@link AbstractFile}, and notify the {@link LocationListener}s that
     * the location was changed to it.
     * 
     * @param folder the {@link AbstractFile} that is going to be presented in the {@link FolderPanel}
     */
    public void setCurrentFolder(AbstractFile folder, AbstractFile fileToSelect, boolean changeLockedTab) {
    	LOGGER.trace("calling ls()");
    	
    	// Update the timestamp right before the folder is set in case FolderChangeMonitor checks the timestamp
        // while FileTable#setCurrentFolder is being called.    	
    	this.lastFolderChangeTime = System.currentTimeMillis();
    	
    	AbstractFile[] children;
		try {
			children = folder.ls(configurableFolderFilter);
		} catch (Exception e) {
			LOGGER.debug("Couldn't ls children of " + folder.getAbsolutePath() + ", error: " + e.getMessage());
			children = new AbstractFile[0];
		}

    	folderPanel.setCurrentFolder(folder, children, fileToSelect, changeLockedTab);

    	this.currentFolder = folder;

    	// Notify listeners that the location has changed
    	fireLocationChanged(folder.getURL());

    	// After the initial folder is set, initialize the monitoring thread
    	if (folderChangeMonitor == null) {
    		folderChangeMonitor = new FolderChangeMonitor(folderPanel);
    	}
    }

    /**
     * Return the folder presented in the {@link FolderPanel}
     * 
     * @return the {@link AbstractFile} presented in the {@link FolderPanel}
     */
    public AbstractFile getCurrentFolder() {
    	return currentFolder;
    }

    public FolderChangeMonitor getFolderChangeMonitor() {
        return folderChangeMonitor;
    }

	/**
	 * This method is triggered internally (i.e not by user request) to change the current
	 * folder to the given folder
	 *
	 * @param folderURL the URL of the folder to switch to
	 * @param callback the {@link Callback#call()} method will be called when folder has changed
	 */
	public void tryChangeCurrentFolderInternal(final FileURL folderURL, final Callback callback) {
		
		Runnable changeCurrentFolderInternalTask = new Runnable() {
			@Override
			public void run() {
				getMainFrame().disableEventsMode();
				
				AbstractFile folder = getWorkableLocation(folderURL);
				try {
					setCurrentFolder(folder, null, true);
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				} finally {
					getMainFrame().enableEventsMode();
					
					// Notify callback that the folder has been set 
					callback.call();
		    	}
			}			
		};
		
    	if (EventQueue.isDispatchThread()) {
    		ExecutorManager.execute(changeCurrentFolderInternalTask);
    	} else {
    		changeCurrentFolderInternalTask.run();
    	}
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
			ChangeFolderTask thread = new ChangeFolderTask(this, folder, findWorkableFolder, changeLockedTab);

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

	public ChangeFolderTask tryChangeCurrentFolder(FileURL folderURL,CredentialsMapping credentialsMapping) {
		return tryChangeCurrentFolder(folderURL, credentialsMapping, false);
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
			ChangeFolderTask thread = new ChangeFolderTask(this, folderURL, credentialsMapping, changeLockedTab);
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
		folderPanel.getFoldersTreePanel().refreshFolder(this.getCurrentFolder());
		return tryChangeCurrentFolder(this.getCurrentFolder(), selectThisFileAfter, true, true);
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
	 * Return a workable location according the following logic:
	 * - If the given folder exists, return it
	 * - if the given folder is local file, find workable location
	 *   according to the logic used for inaccessible local files
	 * - Otherwise, return the non-exist remote location
	 */
	private AbstractFile getWorkableLocation(FileURL folderURL) {
		AbstractFile folder = FileFactory.getFile(folderURL);
		if (folder != null && folder.exists()) {
			return folder;
		}
		if (folder == null) {
			folder = NullableFile.of(folderURL);
		}
		return FileProtocols.FILE.equals(folderURL.getScheme()) ? getWorkableFolder(folder) : folder;
	}
	
	/**
	 * Returns a 'workable' folder as a substitute for the given non-existing folder. This method will return the
	 * first existing parent if there is one, to the first existing local volume otherwise. In the unlikely event
	 * that no local volume exists, <code>null</code> will be returned.
	 *
	 * @param folder folder for which to find a workable folder
	 * @return a 'workable' folder for the given non-existing folder, <code>null</code> if there is none.
	 */
	public static AbstractFile getWorkableFolder(AbstractFile folder) {
		// Look for an existing parent
		AbstractFile newFolder = folder;
		do {
			newFolder = newFolder.getParent();
			if(newFolder!=null && newFolder.exists()) {
				return newFolder;
			}
		} while (newFolder!=null);

		// Fall back to the first existing volume
		AbstractFile[] localVolumes = LocalFile.getVolumes();
		for(AbstractFile volume : localVolumes) {
			if(volume.exists()) {
				return volume;
			}
		}

		// No volume could be found, return null
		return null;
	}
	
	/**
	 * Restore default cursor
	 */
	private void setNormalCursor() {
		getMainFrame().setNormalCursor();
	}
	
	/**
	 * Set cursor to hourglass/wait
	 */
	private void setWaitCursor() {
		getMainFrame().setWaitCursor();
	}
	
    //==============================================================================
    // ChangeFolderTask.Listener
    //==============================================================================
    @Override
    public void enableEventsMode() {
    	getMainFrame().enableEventsMode();
	}

    @Override
	public void disableEventsMode() {
    	getMainFrame().disableEventsMode();
	}
    
    @Override
    public void setSelectedFolder(AbstractFile folder, AbstractFile fileToSelect, boolean changeLockedTab) {
    	setCurrentFolder(folder, fileToSelect, changeLockedTab);
    }
    
    @Override
    public void locationChangeStarted(FileURL targetFileURL) {
    	// Notify listeners that location is changing
    	fireLocationChanging(targetFileURL);
    }
    
    @Override
    public void locationChangeProgress(int progress) {
    	folderPanel.setProgressValue(progress);
    }
        
    @Override
    public void locationChangeCompleted() {
		synchronized(FOLDER_CHANGE_LOCK) {
			changeFolderTask = null;
		}
    }
    
    @Override
	public void locationChangeCancelled(FileURL cancelledURL) {
    	fireLocationCancelled(cancelledURL);
    }
    
    @Override
	public void locationChangeFailed(FileURL failedURL) {
    	fireLocationFailed(failedURL);
    }
    
    /**
     * Pops up an {@link AuthDialog authentication dialog} prompting the user to select or enter credentials in order to
     * be granted the access to the file or folder represented by the given {@link FileURL}.
     * The <code>AuthDialog</code> instance is returned, allowing to retrieve the credentials that were selected
     * by the user (if any).
     *
     * @param fileURL the file or folder to ask credentials for
     * @param errorMessage optional (can be null), an error message describing a prior authentication failure
     * @return the AuthDialog that contains the credentials selected by the user (if any)
     */
    public AuthDialog popAuthDialog(FileURL fileURL, boolean authFailed, String errorMessage) {
        AuthDialog authDialog = new AuthDialog(getMainFrame(), fileURL, authFailed, errorMessage);
        authDialog.showDialog();
        return authDialog;
    }    
    
    /**
     * Displays a popup dialog informing the user that the requested folder doesn't exist or isn't available.
     */
    public void handleFolderDoesNotExist() {
    	// Restore default cursor
    	setNormalCursor();
        InformationDialog.showErrorDialog(getMainFrame(), Translator.get("table.folder_access_error_title"), Translator.get("folder_does_not_exist"));
    }

    public void handleFailedToReadFolder() {
    	setNormalCursor();
        InformationDialog.showErrorDialog(getMainFrame(), Translator.get("table.folder_access_error_title"), Translator.get("failed_to_read_folder"));
    }
    
    /**
     * Displays a popup dialog informing the user that the requested folder couldn't be opened.
     *
     * @param e the Exception that was caught while changing the folder
     */
    public void handleAccessError(Exception e) {
        InformationDialog.showErrorDialog(getMainFrame(), Translator.get("table.folder_access_error_title"), Translator.get("table.folder_access_error"), e==null?null:e.getMessage(), e);
    }
    
    /**
     * Displays a download dialog box where the user can choose where to download the given file or cancel
     * the operation.
     *
     * @param file the file to download
     */
    public void handleDownload(AbstractFile file) {
        FileSet fileSet = new FileSet(getCurrentFolder());
        fileSet.add(file);
		
        // Show confirmation/path modification dialog
        new DownloadDialog(getMainFrame(), fileSet).showDialog();
    }
    
    public int popDownloadOrBrowseDialog() {
    	setNormalCursor();
    	// Download or browse file ?
		QuestionDialog dialog = new QuestionDialog(getMainFrame(),
				null,
				Translator.get("table.download_or_browse"),
				getMainFrame(),
				new String[] {BROWSE_TEXT, DOWNLOAD_TEXT, CANCEL_TEXT},
				new int[] {BROWSE_ACTION, DOWNLOAD_ACTION, CANCEL_ACTION},
				0);
		int ret = dialog.getActionValue();
		if (ret == BROWSE_ACTION) {
			setWaitCursor();
		}
		return ret;    	
    }
    
    
    //==============================================================================
	
    /**
     * Registers a LocationListener to receive notifications whenever the current folder of the associated FolderPanel
     * has or is being changed.
     *
     * <p>Listeners are stored as weak references so {@link #removeLocationListener(LocationListener)}
     * doesn't need to be called for listeners to be garbage collected when they're not used anymore.</p>
     *
     * @param listener the LocationListener to register
     */
    public void addLocationListener(LocationListener listener) {
        locationListeners.put(listener);
    }

    /**
     * Removes the LocationListener from the list of listeners that receive notifications when the current folder of the
     * associated FolderPanel has or is being changed.
     *
     * @param listener the LocationListener to remove
     */
    public void removeLocationListener(LocationListener listener) {
        locationListeners.remove(listener);
    }

    /**
     * Notifies all registered listeners that the current folder is being changed on the associated FolderPanel.
     *
     * @param folderURL url of the folder that will become the new location if the folder change is successful
     */
    private void fireLocationChanging(FileURL folderURL) {
    	locationListeners.notify(FIRE_LOCATION_CHANGING, new LocationListener.Event(folderURL));
    }
    
    /**
     * Notifies all registered listeners that the current folder has changed on associated FolderPanel.
     *
     * @param folderURL url of the new current folder in the associated FolderPanel
     */
    private void fireLocationChanged(FileURL folderURL) {
    	locationListeners.notify(FIRE_LOCATION_CHANGED, new LocationListener.Event(folderURL));
    }

    /**
     * Notifies all registered listeners that the folder change as notified by {@link #fireLocationChanging(FileURL)}
     * has been cancelled by the user.
     *
     * @param folderURL url of the folder for which a failed attempt was made to make it the current folder
     */
    private void fireLocationCancelled(FileURL folderURL) {
    	locationListeners.notify(FIRE_LOCATION_CANCELLED, new LocationListener.Event(folderURL));
    }

    /**
     * Notifies all registered listeners that the folder change as notified by {@link #fireLocationChanging(FileURL)}
     * could not be changed, as a result of the folder not existing or failing to list its contents.
     *
     * @param folderURL url of the folder for which a failed attempt was made to make it the current folder
     */
    private void fireLocationFailed(FileURL folderURL) {
    	locationListeners.notify(FIRE_LOCATION_FAILED, new LocationListener.Event(folderURL));
    }
}
