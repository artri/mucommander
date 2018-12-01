package com.mucommander.core;

import java.awt.Cursor;
import java.util.Objects;
import java.util.concurrent.Future;

import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.protocol.local.LocalFile;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.utils.MuExecutorManager;

public abstract class LocationChangerTask implements Runnable {
	private static final Cursor WAIT_CURSOR = new Cursor(Cursor.WAIT_CURSOR);

	private final MainFrame mainFrame;

	public LocationChangerTask(MainFrame mainFrame) {
		this.mainFrame = Objects.requireNonNull(mainFrame);
	}

	public MainFrame getMainFrame() {
		return mainFrame;
	}

	@Override
	public abstract void run();
	
	public Future<?> execute() {
		return MuExecutorManager.submit(this);
	}
	
	protected void enableEventsMode() {
		mainFrame.setNoEventsMode(false);
		// Restore default cursor
		mainFrame.setCursor(Cursor.getDefaultCursor());
	}

	protected void disableEventsMode() {
		mainFrame.setNoEventsMode(true);
		// Set cursor to hourglass/wait
		mainFrame.setCursor(WAIT_CURSOR);
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
