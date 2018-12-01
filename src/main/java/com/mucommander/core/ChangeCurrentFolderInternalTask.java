package com.mucommander.core;

import java.awt.Cursor;
import java.awt.EventQueue;
import java.util.concurrent.Future;

import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileFactory;
import com.mucommander.commons.file.FileURL;
import com.mucommander.commons.file.protocol.FileProtocols;
import com.mucommander.ui.event.LocationManager;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.utils.Callback;
import com.mucommander.utils.MuExecutorManager;

public class ChangeCurrentFolderInternalTask extends LocationChangerTask {
	
	private final MainFrame mainFrame;
	private final LocationManager locationManager;
	
	private final FileURL folderURL;
	private final Callback callback;
	
	public ChangeCurrentFolderInternalTask(MainFrame mainFrame, LocationManager locationManager, FileURL folderURL, Callback callback) {
		this.mainFrame = mainFrame;
		this.locationManager = locationManager;
		this.folderURL = folderURL;
		this.callback = callback;
	}

	@Override
	public Future<?> execute() {
    	if (EventQueue.isDispatchThread()) {
    		return MuExecutorManager.submit(this);
    	}
    	
		this.run();
		return null;
	}

	@Override
	public void run() {
		AbstractFile folder = getWorkableLocation(folderURL);
		try {
			locationManager.setCurrentFolder(folder, null, true);
		} finally {
			mainFrame.setNoEventsMode(false);
			// Restore default cursor
			mainFrame.setCursor(Cursor.getDefaultCursor());
			// Notify callback that the folder has been set 
			callback.call();
    	}
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
			folder = new NullableFile(folderURL);
		}
		return FileProtocols.FILE.equals(folderURL.getScheme()) ? getWorkableFolder(folder) : folder;
	}	
}
