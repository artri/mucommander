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

import com.mucommander.commons.file.FileURL;
import com.mucommander.ui.main.FolderPanel;
import com.mucommander.utils.EventListener;
import com.mucommander.utils.EventListenerSet;

/**
 * Interface to be implemented by classes that wish to be notified of location changes on a particular
 * FolderPanel. Those classes need to be registered to receive those events, this can be done by calling
 * {@link LocationManager#addLocationListener(LocationListener)}.
 *
 * @see com.mucommander.ui.main.FolderPanel
 * @author Maxence Bernard
 */
public interface LocationListener extends EventListener {
	
	/**
	 * Event used to indicate that a folder change is or has occurred. This event is passed to to every LocationListener
	 * that registered to receive those events on a particular FolderPanel.
	 *
	 * @author Maxence Bernard
	 */
	public static class Event extends EventListenerSet.Event {

	    /** FolderPanel where location has or is being changed */
	    private final FolderPanel folderPanel;

	    /** URL of the folder that has or is being changed */
	    private final FileURL folderURL;

	    /**
	     * Creates a new LocationEvent.
	     *
	     * @param folderPanel FolderPanel where location has or is being changed.
	     * @param folderURL url of the folder that has or is being changed
	     */
	    public Event(FolderPanel folderPanel, FileURL folderURL) {
	        this.folderPanel = folderPanel;
	        this.folderURL = folderURL;
	    }

	    /**
	     * Returns the FolderPanel instance where location has or is being changed.
	     */
	    public FolderPanel getFolderPanel() {
	        return folderPanel;
	    }

	    /**
	     * Returns the URL to the folder that has or is being changed.
	     */
	    public FileURL getFolderURL() {
	        return folderURL;
	    }
	}
	
	
    /**
     * This method is invoked when the current folder is being changed.
     *
     * <p>A call to either {@link #locationChanged(LocationEvent)}, {@link #locationCancelled(LocationEvent)} or
     * {@link #locationFailed(LocationEvent)} will always follow to indicate the outcome of the folder change. 
     *
     * @param locationEvent describes the location change event
     */
    public void locationChanging(Event locationEvent);


    /**
     * This method is invoked when the current folder has changed.
     *
     * @param locationEvent describes the location change event
     */
    public void locationChanged(Event locationEvent);


    /**
     * This method is invoked when the current folder has been cancelled by the user.
     *
     * @param locationEvent describes the location change event
     */
    public void locationCancelled(Event locationEvent);


    /**
     * This method is invoked when the current folder could not be changed, as a result
     * of the folder not existing or failing to list its contents.
     *
     * @param locationEvent describes the location change event
     */
    public void locationFailed(Event locationEvent);

}
