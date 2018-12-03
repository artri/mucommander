package com.mucommander.core;

import java.io.IOException;
import java.net.MalformedURLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mucommander.auth.CredentialsManager;
import com.mucommander.auth.CredentialsMapping;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.AuthException;
import com.mucommander.commons.file.AuthenticationType;
import com.mucommander.commons.file.CachedFile;
import com.mucommander.commons.file.FileFactory;
import com.mucommander.commons.file.FileURL;
import com.mucommander.commons.file.UnsupportedFileOperationException;
import com.mucommander.commons.file.protocol.FileProtocols;
import com.mucommander.conf.MuConfigurations;
import com.mucommander.conf.MuPreference;
import com.mucommander.conf.MuPreferences;
import com.mucommander.ui.dialog.auth.AuthDialog;
import com.mucommander.ui.event.LocationManager;
import com.mucommander.ui.main.MainFrame;

/**
 * This thread takes care of changing current folder without locking the main
 * thread. The folder change can be cancelled.
 *
 * <p>A little note out of nowhere: never ever call JComponent.paintImmediately() from a thread
 * other than the Event Dispatcher Thread, as will create nasty repaint glitches that
 * then become very hard to track. Sun's Javadoc doesn't make it clear enough... just don't!</p>
 *
 * @author Maxence Bernard
 */
public class ChangeFolderTask extends LocationChangerTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(ChangeFolderTask.class);
	
	public interface Listener {
		void lastFolderChangeTimeChanged(long lastFolderChangeTime);
		void progressChanged(int progress);
		void changeFolderCompleted();
	}

	private final Listener listener;
	private final AbstractFile currentSelectedFile;
	
	private GlobalLocationHistory globalHistory = GlobalLocationHistory.Instance();
	
	private AbstractFile folder;
	private boolean findWorkableFolder;
	private boolean changeLockedTab;
	private FileURL folderURL;
	private AbstractFile fileToSelect;
	private CredentialsMapping credentialsMapping;

	/** True if this thread has been interrupted by the user using #tryKill */
	private boolean killed;
	/** True if it is unsafe to kill this thread */
	private boolean doNotKill;

	/** Lock object used to ensure consistency and thread safeness when killing the thread */
	private final Object KILL_LOCK = new Object();

	public ChangeFolderTask(Listener listener, MainFrame mainFrame, LocationManager locationManager, AbstractFile currentSelectedFile, 
			AbstractFile folder, boolean findWorkableFolder, boolean changeLockedTab) {
		
		super(mainFrame, locationManager);
		this.listener = listener;
		this.currentSelectedFile = currentSelectedFile;
		
		// Ensure that we work on a raw file instance and not a cached one
		this.folder = (folder instanceof CachedFile)?((CachedFile)folder).getProxiedFile():folder;
		this.folderURL = folder.getURL();
		this.findWorkableFolder = findWorkableFolder;
		this.changeLockedTab = changeLockedTab;
	}

	/**
	 * 
	 * @param folderURL
	 * @param credentialsMapping the CredentialsMapping to use for accessing the folder, <code>null</code> for none
	 * @param changeLockedTab
	 */
	public ChangeFolderTask(Listener listener, MainFrame mainFrame, LocationManager locationManager, AbstractFile currentSelectedFile,
			FileURL folderURL, CredentialsMapping credentialsMapping, boolean changeLockedTab) {
		
		super(mainFrame, locationManager);
		
		this.listener = listener;
		this.currentSelectedFile = currentSelectedFile;
		
		this.folderURL = folderURL;
		this.changeLockedTab = changeLockedTab;
		this.credentialsMapping = credentialsMapping;
	}
	
	/**
	 * Sets the file to be selected after the folder has been changed, <code>null</code> for none.
	 *
	 * @param fileToSelect the file to be selected after the folder has been changed
	 */
	public void selectThisFileAfter(AbstractFile fileToSelect) {
		this.fileToSelect = fileToSelect;
	}

	/**
	 * Returns <code>true</code> if the given file should have its canonical path followed. In that case, the
	 * AbstractFile instance must be resolved again.
	 *
	 * <p>HTTP files MUST have their canonical path followed. For all other file protocols, this is an option in
	 * the preferences.</p>
	 *
	 * @param file the file to test
	 * @return <code>true</code> if the given file should have its canonical path followed
	 */
	private boolean followCanonicalPath(AbstractFile file) {
		return (MuConfigurations.getPreferences().getVariable(MuPreference.CD_FOLLOWS_SYMLINKS, MuPreferences.DEFAULT_CD_FOLLOWS_SYMLINKS)
				|| file.getURL().getScheme().equals(FileProtocols.HTTP))
				&& !file.getAbsolutePath(false).equals(file.getCanonicalPath(false));
	}

	/**
	 * Attempts to stop this thread and returns <code>true</code> if an attempt was made.
	 * An attempt to stop this thread will be made using one of the methods detailed hereunder, only if
	 * it is still safe to do so: if the thread is too far into the process of changing the current folder,
	 * this method will have no effect and return <code>false</code>.
	 *
	 * <p>The first time this method is called, {@link #interrupt()} is called, giving the thread a chance to stop
	 * gracefully should it be waiting for a thread or blocked in an interruptible operation such as an
	 * InterruptibleChannel. This may have no immediate effect if the thread is blocked in a non-interruptible
	 * operation. This thread will however be marked as 'killed' which will sooner or later cause {@link #run()}
	 * to stop the thread by simply returning.</p> 
	 *
	 * <p>The second time this method is called, the deprecated (and unsafe) {@link #stop()} method is called,
	 * forcing the thread to abort.</p>
	 *
	 * <p>Any subsequent calls to this method will have no effect and return <code>false</code>.</p>
	 *
	 * @return true if an attempt was made to stop this thread.
	 */
	public boolean tryKill() {
		synchronized(KILL_LOCK) {
			if(doNotKill) {
				LOGGER.debug("Can't kill thread now, it's too late, returning");
				return false;
			}

			// Call Thread#interrupt() the first time this method is called to give the thread a chance to stop
			// gracefully if it is waiting in Thread#sleep() or Thread#wait() or Thread#join() or in an
			// interruptible operation such as java.nio.channel.InterruptibleChannel. If this is the case,
			// InterruptedException or ClosedByInterruptException will be thrown and thus need to be catched by
			// #run().
			if (hasCompletionTaskFuture()) {
				LOGGER.debug("Killing thread using #interrupt()");
				
				// This field needs to be set before actually killing the thread, #run() relies on it
				killed = true;
				
				cancelCompletionTaskFuture(true);
			} else {
				LOGGER.debug("Thread already killed by #interrupt() and #stop(), there's nothing we can do, returning");
				return false;
			}
			
			return true;
		} //synchronized
	}

	@Override
	public void execute() {
		// Notify listeners that location is changing
		getLocationManager().fireLocationChanging(getFolderURL());
		
		super.execute();
	}

	@Override
	public void run() {
		LOGGER.debug("starting folder change...");
		boolean folderChangedSuccessfully = false;

		// Show some progress in the progress bar to give hope
		listener.progressChanged(10);

		boolean userCancelled = false;
		CredentialsMapping newCredentialsMapping = null;
		// True if Guest authentication was selected in the authentication dialog (guest credentials must not be
		// added to CredentialsManager)
		boolean guestCredentialsSelected = false;

		AuthenticationType authenticationType = folderURL.getAuthenticationType();
		if(credentialsMapping!=null) {
			newCredentialsMapping = credentialsMapping;
			CredentialsManager.authenticate(folderURL, newCredentialsMapping);
		}
		// If the URL doesn't contain any credentials and authentication for this file protocol is required, or
		// optional and CredentialsManager has credentials for this location, popup the authentication dialog to
		// avoid waiting for an AuthException to be thrown.
		else if (!folderURL.containsCredentials() &&
				(  (authenticationType==AuthenticationType.AUTHENTICATION_REQUIRED)
						|| (authenticationType==AuthenticationType.AUTHENTICATION_OPTIONAL && CredentialsManager.getMatchingCredentials(folderURL).length>0))) {
			AuthDialog authDialog = popAuthDialog(folderURL, false, null);
			newCredentialsMapping = authDialog.getCredentialsMapping();
			guestCredentialsSelected = authDialog.guestCredentialsSelected();

			// User cancelled the authentication dialog, stop
			if(newCredentialsMapping ==null)
				userCancelled = true;
			// Use the provided credentials and invalidate the folder AbstractFile instance (if any) so that
			// it gets recreated with the new credentials
			else {
				CredentialsManager.authenticate(folderURL, newCredentialsMapping);
				folder = null;
			}
		}

		if(!userCancelled) {
			boolean canonicalPathFollowed = false;

			do {
				disableEventsMode();

				try {
					// 2 cases here :
					// - Thread was created using an AbstractFile instance
					// - Thread was created using a FileURL, corresponding AbstractFile needs to be resolved

					// Thread was created using a FileURL
					if(folder==null) {
						AbstractFile file = FileFactory.getFile(folderURL, true);

						synchronized(KILL_LOCK) {
							if(killed) {
								LOGGER.debug("this thread has been killed, returning");
								break;
							}
						}

						// File resolved -> 25% complete
						listener.progressChanged(25);

						// Popup an error dialog and abort folder change if the file could not be resolved
						// or doesn't exist
						if (file==null || !file.exists()) {
							// Restore default cursor
							setNormalCursor();
							showFolderDoesNotExistDialog();
							break;
						}

						if (!file.canRead()) {
						    // Restore default cursor
							setNormalCursor();
						    showFailedToReadFolderDialog();
						    break;
						}

						// File is a regular directory, all good
						if (file.isDirectory()) {
							// Just continue
						}
						// File is a browsable file (Zip archive for instance) but not a directory : Browse or Download ? => ask the user
						else if (file.isBrowsable()) {
							// If history already contains this file, do not ask the question again and assume
							// the user wants to 'browse' the file. In particular, this prevent the 'Download or browse'
							// dialog from popping up when going back or forward in history.
							// The dialog is also not displayed if the file corresponds to the currently selected file,
							// which is a weak (and not so accurate) way to know if the folder change is the result
							// of the OpenAction (enter pressed on the file). This works well enough in practice.
							if (!globalHistory.historyContains(folderURL) && !file.equals(currentSelectedFile)) {
								// Restore default cursor
								setNormalCursor();
								
								int ret = showDownloadOrBrowseDialog();
								
								if (ret==-1 || ret==CANCEL_ACTION) {
									break;
								}

								// Download file
								if (ret == DOWNLOAD_ACTION) {
									showDownloadDialog(file);
									break;
								}
								// Continue if BROWSE_ACTION
								// Set cursor to hourglass/wait
								setWaitCursor();
							}
							// else just continue and browse file's contents
						}
						// File is a regular file: show download dialog which allows to download (copy) the file
						// to a directory specified by the user
						else {
							showDownloadDialog(file);
							break;
						}

						this.folder = file;
					}
					// Thread was created using an AbstractFile instance, check file existence
					else if (!folder.exists()) {
						// Find a 'workable' folder if the requested folder doesn't exist anymore
						if (findWorkableFolder) {
							AbstractFile newFolder = getWorkableFolder(folder);
							if (newFolder.equals(folder)) {
								// If we've already tried the returned folder, give up (avoids a potentially endless loop)
								showFolderDoesNotExistDialog();
								break;
							}

							// Try again with the new folder
							folder = newFolder;
							folderURL = folder.getURL();
							// Discard the file to select, if any
							fileToSelect = null;

							continue;
						} else {
							showFolderDoesNotExistDialog();
							break;
						}
					} else if (!folder.canRead()) {
					    showFailedToReadFolderDialog();
					    break;
					}

					// Checks if canonical should be followed. If that is the case, the file is invalidated
					// and resolved again. This happens only once at most, to avoid a potential infinite loop
					// in the event that the absolute path still didn't match canonical one after the file is
					// resolved again.
					if (!canonicalPathFollowed && followCanonicalPath(folder)) {
						try {
							// Recreate the FileURL using the file's canonical path
							FileURL newURL = FileURL.getFileURL(folder.getCanonicalPath());
							// Keep the credentials and properties (if any)
							newURL.setCredentials(folderURL.getCredentials());
							newURL.importProperties(folderURL);
							this.folderURL = newURL;
							// Invalidate the AbstractFile instance
							this.folder = null;
							// There won't be any further attempts after this one
							canonicalPathFollowed = true;

							// Loop the resolve the file
							continue;
						}
						catch(MalformedURLException e) {
							// In the unlikely event of the canonical path being malformed, the AbstractFile
							// and FileURL instances are left untouched
						}
					}

					synchronized(KILL_LOCK) {
						if(killed) {
							LOGGER.debug("this thread has been killed, returning");
							break;
						}
					}

					// File tested -> 50% complete
					listener.progressChanged(50);

					synchronized(KILL_LOCK) {
						if(killed) {
							LOGGER.debug("this thread has been killed, returning");
							break;
						}
						// From now on, thread cannot be killed (would comprise table integrity)
						doNotKill = true;
					}

					// files listed -> 75% complete
					listener.progressChanged(75);

					LOGGER.trace("calling setCurrentFolder");

					// Change the file table's current folder and select the specified file (if any)
					setCurrentFolder(folder, fileToSelect, changeLockedTab);

					// folder set -> 95% complete
					listener.progressChanged(95);

					// If new credentials were entered by the user, these can now be considered valid
					// (folder was changed successfully), so we add them to the CredentialsManager.
					// Do not add the credentials if guest credentials were selected by the user.
					if(newCredentialsMapping!=null && !guestCredentialsSelected)
						CredentialsManager.addCredentials(newCredentialsMapping);

					// All good !
					folderChangedSuccessfully = true;

					break;
				} catch(Exception e) {
					LOGGER.debug("Caught exception", e);

					if(killed) {
						// If #tryKill() called #interrupt(), the exception we just caught was most likely
						// thrown as a result of the thread being interrupted.
						//
						// The exception can be a java.lang.InterruptedException (Thread throws those),
						// a java.nio.channels.ClosedByInterruptException (InterruptibleChannel throws those)
						// or any other exception thrown by some code that swallowed the original exception
						// and threw a new one.

						LOGGER.debug("Thread was interrupted, ignoring exception");
						break;
					}

					// Restore default cursor
					setNormalCursor();

					if(e instanceof AuthException) {
						AuthException authException = (AuthException)e;
						// Retry (loop) if user provided new credentials, if not stop
						AuthDialog authDialog = popAuthDialog(authException.getURL(), true, authException.getMessage());
						newCredentialsMapping = authDialog.getCredentialsMapping();
						guestCredentialsSelected = authDialog.guestCredentialsSelected();

						if(newCredentialsMapping!=null) {
							// Invalidate the existing AbstractFile instance
							folder = null;
							// Use the provided credentials
							CredentialsManager.authenticate(folderURL, newCredentialsMapping);
							continue;
						}
					} else {
						// Find a 'workable' folder if the requested folder doesn't exist anymore
						if(findWorkableFolder) {
							AbstractFile newFolder = getWorkableFolder(folder);
							if(newFolder.equals(folder)) {
								// If we've already tried the returned folder, give up (avoids a potentially endless loop)
								showFolderDoesNotExistDialog();
								break;
							}

							// Try again with the new folder
							folder = newFolder;
							folderURL = folder.getURL();
							// Discard the file to select, if any
							fileToSelect = null;

							continue;
						}

						showAccessErrorDialog(e);
					}

					// Stop looping!
					break;
				}
			} while(true);
		}

		synchronized(KILL_LOCK) {
			// Clean things up
			cleanup(folderChangedSuccessfully);
		}
	}

	public void cleanup(boolean folderChangedSuccessfully) {
		// Ensures that this method is called only once
		synchronized(KILL_LOCK) {
			if (!hasCompletionTaskFuture()) {
				LOGGER.debug("already called, returning");
				return;
			}
			
			resetCompletionTaskFuture();
		}

		LOGGER.trace("cleaning up, folderChangedSuccessfully="+folderChangedSuccessfully);

		// Clear the interrupted flag in case this thread has been killed using #interrupt().
		// Not doing this could cause some of the code called by this method to be interrupted (because this thread
		// is interrupted) and throw an exception
		Thread.interrupted();

		// Reset location field's progress bar
		listener.progressChanged(0);
		enableEventsMode();

		if (!folderChangedSuccessfully) {
			FileURL failedURL = getFolderURL();
			// Notifies listeners that location change has been cancelled by the user or has failed
			if (killed) {
				getLocationManager().fireLocationCancelled(failedURL);
			} else {
				getLocationManager().fireLocationFailed(failedURL);
			}
		}
		
		listener.changeFolderCompleted();
	}
	
	private FileURL getFolderURL() {
		return folder == null ? folderURL : folder.getURL();
	}
	
	 /**
     * Changes current folder using the given folder and children files.
     *
     * <p>
     * This method <b>is</b> I/O-bound and locks the calling thread until the folder has been changed. It may under
     * certain circumstances lock indefinitely, for example when accessing network-based filesystems.
     * </p>
     *
     * @param folder folder to be made current folder
     * @param fileToSelect file to be selected after the folder has been refreshed (if it exists in the folder), can be null in which case FileTable rules will be used to select current file
     * @param changeLockedTab - flag that indicates whether to change the presented folder in the currently selected tab although it's locked
	 * @throws IOException 
	 * @throws UnsupportedFileOperationException 
     */
    private void setCurrentFolder(AbstractFile folder, AbstractFile fileToSelect, boolean changeLockedTab) throws UnsupportedFileOperationException, IOException {
    	// Update the timestamp right before the folder is set in case FolderChangeMonitor checks the timestamp
        // while FileTable#setCurrentFolder is being called.
    	long lastFolderChangeTime = System.currentTimeMillis();
    	listener.lastFolderChangeTimeChanged(lastFolderChangeTime);
        
    	getLocationManager().setCurrentFolder(folder, fileToSelect, changeLockedTab);
    }
    
	// For debugging purposes
	public String toString() {
		return super.toString()+" folderURL="+folderURL+" folder="+folder;
	}
}

