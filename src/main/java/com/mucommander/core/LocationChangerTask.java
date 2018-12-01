package com.mucommander.core;

import java.awt.Cursor;
import java.util.Objects;
import java.util.concurrent.Future;

import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileURL;
import com.mucommander.commons.file.protocol.local.LocalFile;
import com.mucommander.commons.file.util.FileSet;
import com.mucommander.text.Translator;
import com.mucommander.ui.dialog.InformationDialog;
import com.mucommander.ui.dialog.QuestionDialog;
import com.mucommander.ui.dialog.auth.AuthDialog;
import com.mucommander.ui.dialog.file.DownloadDialog;
import com.mucommander.ui.event.LocationManager;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.utils.MuExecutorManager;

public abstract class LocationChangerTask implements Runnable {
	private static final Cursor WAIT_CURSOR = new Cursor(Cursor.WAIT_CURSOR);
	protected static final int CANCEL_ACTION = 0;
	protected static final int BROWSE_ACTION = 1;
	protected static final int DOWNLOAD_ACTION = 2;

	private static final String CANCEL_TEXT = Translator.get("cancel");
	private static final String BROWSE_TEXT = Translator.get("browse");
	private static final String DOWNLOAD_TEXT = Translator.get("download");
	
	private final MainFrame mainFrame;
	private final LocationManager locationManager;
	
	public LocationChangerTask(MainFrame mainFrame, LocationManager locationManager) {
		this.mainFrame = Objects.requireNonNull(mainFrame);
		this.locationManager = Objects.requireNonNull(locationManager);
	}

	public MainFrame getMainFrame() {
		return mainFrame;
	}

	public LocationManager getLocationManager() {
		return locationManager;
	}

	@Override
	public abstract void run();
	
	public Future<?> execute() {
		return MuExecutorManager.submit(this);
	}
	
	protected void enableEventsMode() {
		mainFrame.setNoEventsMode(false);
		setNormalCursor();
	}

	protected void disableEventsMode() {
		mainFrame.setNoEventsMode(true);
		setWaitCursor();
	}

	/**
	 * Restore default cursor
	 */
	protected void setNormalCursor() {
		mainFrame.setCursor(Cursor.getDefaultCursor());
	}
	
	/**
	 * Set cursor to hourglass/wait
	 */
	protected void setWaitCursor() {
		mainFrame.setCursor(WAIT_CURSOR);
	}
	
	/**
     * Displays a popup dialog informing the user that the requested folder doesn't exist or isn't available.
     */
    protected void showFolderDoesNotExistDialog() {
        InformationDialog.showErrorDialog(mainFrame, Translator.get("table.folder_access_error_title"), Translator.get("folder_does_not_exist"));
    }


    protected void showFailedToReadFolderDialog() {
        InformationDialog.showErrorDialog(mainFrame, Translator.get("table.folder_access_error_title"), Translator.get("failed_to_read_folder"));
    }


    /**
     * Displays a popup dialog informing the user that the requested folder couldn't be opened.
     *
     * @param e the Exception that was caught while changing the folder
     */
    protected void showAccessErrorDialog(Exception e) {
        InformationDialog.showErrorDialog(mainFrame, Translator.get("table.folder_access_error_title"), Translator.get("table.folder_access_error"), e==null?null:e.getMessage(), e);
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
    protected AuthDialog popAuthDialog(FileURL fileURL, boolean authFailed, String errorMessage) {
        AuthDialog authDialog = new AuthDialog(mainFrame, fileURL, authFailed, errorMessage);
        authDialog.showDialog();
        return authDialog;
    }


    /**
     * Displays a download dialog box where the user can choose where to download the given file or cancel
     * the operation.
     *
     * @param file the file to download
     */
    protected void showDownloadDialog(AbstractFile file) {
        FileSet fileSet = new FileSet(locationManager.getCurrentFolder());
        fileSet.add(file);
		
        // Show confirmation/path modification dialog
        new DownloadDialog(mainFrame, fileSet).showDialog();
    }
    
    protected int showDownloadOrBrowseDialog() {
    	// Download or browse file ?
		QuestionDialog dialog = new QuestionDialog(mainFrame,
				null,
				Translator.get("table.download_or_browse"),
				mainFrame,
				new String[] {BROWSE_TEXT, DOWNLOAD_TEXT, CANCEL_TEXT},
				new int[] {BROWSE_ACTION, DOWNLOAD_ACTION, CANCEL_ACTION},
				0);

		return dialog.getActionValue();    	
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
			if(newFolder!=null && newFolder.exists())
				return newFolder;
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
}
