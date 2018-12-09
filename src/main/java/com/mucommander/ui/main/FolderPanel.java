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

package com.mucommander.ui.main;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.net.MalformedURLException;
import java.util.Objects;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mucommander.auth.CredentialsMapping;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileFactory;
import com.mucommander.commons.file.FileURL;
import com.mucommander.core.FolderChangeMonitor;
import com.mucommander.core.LocalLocationHistory;
import com.mucommander.core.ChangeFolderTask;
import com.mucommander.text.Translator;
import com.mucommander.ui.action.ActionKeymap;
import com.mucommander.ui.action.ActionManager;
import com.mucommander.ui.action.impl.FocusNextAction;
import com.mucommander.ui.action.impl.FocusPreviousAction;
import com.mucommander.ui.dialog.InformationDialog;
import com.mucommander.ui.dnd.FileDragSourceListener;
import com.mucommander.ui.event.LocationManager;
import com.mucommander.ui.main.folderpanel.DrivePopupButton;
import com.mucommander.ui.main.folderpanel.LocationPanel;
import com.mucommander.ui.main.folderpanel.LocationTextField;
import com.mucommander.ui.main.folderpanel.StatusBar;
import com.mucommander.ui.main.quicklist.BookmarksQL;
import com.mucommander.ui.main.quicklist.ParentFoldersQL;
import com.mucommander.ui.main.quicklist.RecentExecutedFilesQL;
import com.mucommander.ui.main.quicklist.RecentLocationsQL;
import com.mucommander.ui.main.quicklist.RootFoldersQL;
import com.mucommander.ui.main.quicklist.TabsQL;
import com.mucommander.ui.main.table.FileTable;
import com.mucommander.ui.main.table.FileTableConfiguration;
import com.mucommander.ui.main.tabs.ConfFileTableTab;
import com.mucommander.ui.main.tabs.FileTableTab;
import com.mucommander.ui.main.tabs.FileTableTabs;
import com.mucommander.ui.main.tree.FoldersTreePanel;
import com.mucommander.ui.quicklist.QuickList;
import com.mucommander.ui.quicklist.QuickListContainer;
import com.mucommander.ui.tabs.ActiveTabListener;
import com.mucommander.utils.Callback;

/**
 * Folder pane that contains the table that displays the contents of the current directory and allows navigation, the
 * drive button, and the location field.
 *
 * @author Maxence Bernard, Arik Hadas
 */
public class FolderPanel extends JPanel implements FocusListener, QuickListContainer, ActiveTabListener {
	private static final long serialVersionUID = 759232196212132458L;
	private static final Logger LOGGER = LoggerFactory.getLogger(FolderPanel.class);

	/** The following constants are used to identify the left and right folder panels */
	public enum FolderPanelType { LEFT, RIGHT }

	private LocationManager locationManager;
	
	/** MainFrame instance */
    private MainFrame mainFrame;
    /** LocationPanel instance */
    private LocationPanel locationPanel;

    private FileTable fileTable;
    private FileTableTabs tabs;
    private FoldersTreePanel foldersTreePanel;
    private JSplitPane treeSplitPane;

    /** Status bar instance */
    private StatusBar statusBar;
    
    private FileDragSourceListener fileDragSourceListener;

    /** Is directory tree visible */
    private boolean treeVisible = false;

    /** Saved width of a directory tree (when it's not visible) */ 
    private int oldTreeWidth = 150;

    /** Array of all the existing pop ups for this panel's FileTable **/
    private QuickList[] fileTablePopups;

    /* TODO branch private boolean branchView; */

    /**
     * Constructor
     * 
     * @param mainFrame - the MainFrame that contains this panel
     * @param initialFolders - the initial folders displayed at this panel's tabs
     * @param conf - configuration for this panel's file table
     */
    FolderPanel(MainFrame mainFrame, ConfFileTableTab[] initialTabs, int indexOfSelectedTab, FileTableConfiguration conf) {
        super(new BorderLayout());

        if (LOGGER.isTraceEnabled()) {
	        LOGGER.trace("initialTabs:");
	        for (FileTableTab tab:initialTabs) {
	        	LOGGER.trace("\t" + (tab.getLocation() != null ?  tab.getLocation().toString() : null));
	        }
        }
        this.mainFrame = Objects.requireNonNull(mainFrame);

        locationManager = new LocationManager(mainFrame, this);

        // No decoration for this panel
        setBorder(null);
        
		initComponents(initialTabs, indexOfSelectedTab, conf);
		initListeners();
    }
    
    private void initComponents(ConfFileTableTab[] initialTabs, int indexOfSelectedTab, FileTableConfiguration conf) {
        this.locationPanel = new LocationPanel(this);
        add(locationPanel, BorderLayout.NORTH);

        // Initialize quick lists
    	fileTablePopups = new QuickList[]{
    			new ParentFoldersQL(this),
    			new RecentLocationsQL(this),
    			new RecentExecutedFilesQL(this),
    			new BookmarksQL(this),
    			new RootFoldersQL(this),
                new TabsQL(this)};

        // Create the FileTable
        fileTable = new FileTable(mainFrame, this, conf);
        
        // Create the Tabs (Must be called after the fileTable was created and current folder was set)
        tabs = new FileTableTabs(mainFrame, this, initialTabs);        
		// Select the tab that was previously selected on last run
		tabs.selectTab(indexOfSelectedTab);

        // create folders tree on a JSplitPane 
        foldersTreePanel = new FoldersTreePanel(this);
        foldersTreePanel.setVisible(false);
        treeSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, foldersTreePanel, tabs);
        treeSplitPane.setDividerSize(0);
        treeSplitPane.setDividerLocation(0);
        // Remove default border
        treeSplitPane.setBorder(null);
        add(treeSplitPane, BorderLayout.CENTER);
        
        // Add status bar
        this.statusBar = new StatusBar(this);
        add(statusBar, BorderLayout.SOUTH);    	
    }
    
    private void initListeners() {
    	tabs.addActiveTabListener(this);
    	
        // Disable Ctrl+Tab and Shift+Ctrl+Tab focus traversal keys
        WindowManager.disableCtrlFocusTraversalKeys(getLocationTextField());
        WindowManager.disableCtrlFocusTraversalKeys(foldersTreePanel.getTree());
        WindowManager.disableCtrlFocusTraversalKeys(fileTable);
        WindowManager.disableCtrlFocusTraversalKeys(tabs);
        registerCycleThruFolderPanelAction(getLocationTextField());
        registerCycleThruFolderPanelAction(foldersTreePanel.getTree());
        // No need to register cycle actions for FileTable, they already are     	
    	
        // Listen to focus event in order to notify MainFrame of changes of the current active panel/table
        fileTable.addFocusListener(this);
        tabs.addFocusListener(this);
        
        // Drag and Drop support
        // Enable drag support on the FileTable
        this.fileDragSourceListener = new FileDragSourceListener(this);
        fileDragSourceListener.enableDrag(fileTable);    	
    }
    
    /**
     * Returns the status bar, where information about selected files and volume are displayed.
     * Note that a non-null instance of {@link StatusBar} is returned even if it is currently hidden.
     *
     * @return the status bar
     */
    public StatusBar getStatusBar() {
        return this.statusBar;
    }

    /**
     * Registers the {@link FocusNextAction} and {@link FocusPreviousAction} actions onto the given component's
     * input map.
     *  
     * @param component the component for which to register the cycle actions
     */
    private void registerCycleThruFolderPanelAction(JComponent component) {
        ActionKeymap.registerActionAccelerators(
                ActionManager.getActionInstance(FocusNextAction.Descriptor.ACTION_ID, mainFrame),
                component,
                JComponent.WHEN_FOCUSED);

        ActionKeymap.registerActionAccelerators(
                ActionManager.getActionInstance(FocusPreviousAction.Descriptor.ACTION_ID, mainFrame),
                component,
                JComponent.WHEN_FOCUSED);
    }


    public FileDragSourceListener getFileDragSourceListener() {
        return this.fileDragSourceListener;
    }


    /**
     * Returns the MainFrame that contains this panel.
     *
     * @return the MainFrame that contains this panel
     */
    public MainFrame getMainFrame() {
        return this.mainFrame;
    }

    /**
     * Returns the FileTable contained by this panel.
     *
     * @return the FileTable contained by this panel
     */
    public FileTable getFileTable() {
        return this.fileTable;
    }

    /**
     * Returns the FileTable tabs contained by this panel.
     *
     * @return the FileTable tabs contained by this panel
     */
    public FileTableTabs getTabs() {
        return this.tabs;
    }

    /**
     * Returns the LocationTextField contained by this panel.
     *
     * @return the LocationTextField contained by this panel
     */
    public LocationTextField getLocationTextField() {
        return locationPanel.getLocationTextField();
    }

    /**
     * Returns the DrivePopupButton contained by this panel.
     *
     * @return the DrivePopupButton contained by this panel
     */
    public DrivePopupButton getDriveButton() {
        return locationPanel.getDriveButton(); 
    }

    /**
     * Returns the visited folders history, wrapped in a FolderHistory object.
     *
     * @return the visited folders history, wrapped in a FolderHistory object
     */
    public LocalLocationHistory getFolderHistory() {
        return getTabs().getCurrentTab().getLocationHistory();
    }

    /**
     * Returns the LocationManager instance that notifies registered listeners of location changes
     * that occur in this FolderPanel.
     *
     * @return the LocationManager instance that notifies registered listeners of location changes that occur in
     * this FolderPanel
     */
    public LocationManager getLocationManager() {
        return locationManager;
    }

    /**
     * Allows the user to easily change the current folder and type a new one: requests focus 
     * on the location field and selects the folder string.
     */
    public void changeCurrentLocation() {
    	locationPanel.changeCurrentLocation();
    }
	
    /**
     * Returns the FolderChangeMonitor which monitors changes in the current folder and automatically refreshes it.
     *
     * @return the FolderChangeMonitor which monitors changes in the current folder and automatically refreshes it
     */
    public FolderChangeMonitor getFolderChangeMonitor() {
        return locationManager.getFolderChangeMonitor();
    }
    
    public void setProgressValue(int value) {
    	locationPanel.setProgressValue(value);
    }

    public void tryStopChangeFolderTask() {
    	locationManager.tryStopChangeFolderTask();
    }
    
    public void tryChangeCurrentFolderInternal(FileURL folderURL, Callback callback) {
    	locationManager.tryChangeCurrentFolderInternal(folderURL, callback);
    }

    public ChangeFolderTask tryChangeCurrentFolder(AbstractFile folder) {
    	return locationManager.tryChangeCurrentFolder(folder, false);
    }

    public ChangeFolderTask tryChangeCurrentFolder(FileURL folderURL, AbstractFile selectThisFileAfter, boolean findWorkableFolder) {
    	return locationManager.tryChangeCurrentFolder(FileFactory.getFile(folderURL), selectThisFileAfter, findWorkableFolder, false);
    }

    public ChangeFolderTask tryChangeCurrentFolder(AbstractFile folder, AbstractFile selectThisFileAfter, boolean findWorkableFolder) {
    	return locationManager.tryChangeCurrentFolder(folder, selectThisFileAfter, findWorkableFolder, false);
    }

    public ChangeFolderTask tryChangeCurrentFolder(String folderPath) {
    	try {
    		return locationManager.tryChangeCurrentFolder(folderPath);
		} catch(MalformedURLException e) {
			// FileURL could not be resolved, notify the user that the folder doesn't exist
			showFolderDoesNotExistDialog();
			return null;
		}
    }

    public ChangeFolderTask tryChangeCurrentFolder(FileURL folderURL) {
    	return locationManager.tryChangeCurrentFolder(folderURL);
    }

    public ChangeFolderTask tryChangeCurrentFolder(FileURL folderURL, CredentialsMapping credentialsMapping) {
    	return locationManager.tryChangeCurrentFolder(folderURL, credentialsMapping, false);
    }

    public ChangeFolderTask tryRefreshCurrentFolder() {
    	return locationManager.tryRefreshCurrentFolder();
    }

    public ChangeFolderTask tryRefreshCurrentFolder(AbstractFile selectThisFileAfter) {
        return locationManager.tryRefreshCurrentFolder(selectThisFileAfter);
    }

    public ChangeFolderTask getChangeFolderThread() {
        return locationManager.getChangeFolderThread();
    }

    public long getLastFolderChangeTime() {
        return locationManager.getLastFolderChangeTime();
    }

	/**
     * Displays a popup dialog informing the user that the requested folder doesn't exist or isn't available.
     */
    private void showFolderDoesNotExistDialog() {
        InformationDialog.showErrorDialog(mainFrame, Translator.get("table.folder_access_error_title"), Translator.get("folder_does_not_exist"));
    }

    /**
     * Returns the folder that is currently being displayed by this panel.
     *
     * @return the folder that is currently being displayed by this panel
     */
    public AbstractFile getCurrentFolder() {
        return locationManager.getCurrentFolder();
    }

    /**
     * This method updates the UI with the given folder 
     * If the currently selected tab is locked and the given flag "changeLockedTab" is off, 
     * then a new tab is opened with the given folder,
     * otherwise, the currently presented folder is replaces with the given folder
     * 
     * @param folder - the folder to be set
     * @param children - the children of the given folder
     * @param fileToSelect - the file that would be selected after changing the folder
     * @param changeLockedTab - flag that indicates whether to change the presented folder in 
     * the currently selected tab although it's locked (used when switching tabs)
     */
    public void setCurrentFolder(AbstractFile folder, AbstractFile children[], AbstractFile fileToSelect, boolean changeLockedTab) {
    		// Change the current folder in the table and select the given file if not null
    		if (fileToSelect == null) {
    			fileTable.setCurrentFolder(folder, children);
    		} else {
    			fileTable.setCurrentFolder(folder, children, fileToSelect);
    		}
    }

    /**
     * Shows the pop up which is located the given index in fileTablePopups.
     * 
     * @param index - index of the FileTablePopup in fileTablePopups.
     */
    public void showQuickList(int index) {
    	fileTablePopups[index].show();
    }
    
    /**
     * Returns true if a directory tree is visible.
     */
    public boolean isTreeVisible() {
        return treeVisible;
    }
    
    /**
     * Returns width of a folders tree.
     * @return a width of a folders tree
     */
    public int getTreeWidth() {
        if (!treeVisible) {
            return oldTreeWidth;
        } else {
        	return treeSplitPane.getDividerLocation();
        }
    }

    /**
     * Sets a width of a folders tree.
     * @param width new width
     */
    public void setTreeWidth(int width) {
        if (!treeVisible) {
            oldTreeWidth = width;
        } else {
        	treeSplitPane.setDividerLocation(width);
        	treeSplitPane.doLayout();
        }
    }

    /**
     * Returns a panel with a folders tree.
     * @return a panel with a folders tree
     */
    public FoldersTreePanel getFoldersTreePanel() {
        return foldersTreePanel;
    }

    /**
     * Enables/disables a directory tree visibility. Invoked by {@link com.mucommander.ui.action.impl.ToggleTreeAction}.
     */
    public void setTreeVisible(boolean treeVisible) {
    	if (this.treeVisible != treeVisible) {
	        this.treeVisible = treeVisible;
	        if (!treeVisible) {
	            // save width of a tree panel
	            oldTreeWidth = treeSplitPane.getDividerLocation();
	        }
	        foldersTreePanel.setVisible(treeVisible);
	        // hide completely divider if a tree isn't visible
	        treeSplitPane.setDividerLocation(treeVisible ? oldTreeWidth : 0);
	        treeSplitPane.setDividerSize(treeVisible ? 5 : 0);
	        foldersTreePanel.requestFocus();
    	}
    }

    

    ////////////////////////
    // Overridden methods //
    ////////////////////////

    /**
     * Overridden for debugging purposes.
     */
    @Override
    public String toString() {
        return getClass().getName() + "@" + hashCode() + " currentFolder=" + getCurrentFolder() + " hasFocus=" + hasFocus();
    }

    ///////////////////////////
    // FocusListener methods //
    ///////////////////////////
    
    public void focusGained(FocusEvent e) {
        // Notify MainFrame that we are in control now! (our table/location field is active)
        mainFrame.setActiveTable(fileTable);
        statusBar.updateStatusInfo();
    }

    public void focusLost(FocusEvent e) {
        fileTable.getQuickSearch().stop();
    }

    ////////////////////////////////
    // QuickListContainer methods //
    ////////////////////////////////
    
    public Point calcQuickListPosition(Dimension dim) {
    	return new Point(
    			Math.max((getWidth() - (int)dim.getWidth()) / 2, 0),
    			getLocationTextField().getHeight() + Math.max((getHeight() - (int)dim.getHeight()) / 3, 0)
    			);
	}

	public Component containerComponent() {
		return this;
	}

	public Component nextFocusableComponent() {
		return fileTable;
	}

	///////////////////////////////
	// ActiveTabListener methods //
	///////////////////////////////

	public void activeTabChanged() {
		boolean isCurrentTabLocked = tabs.getCurrentTab().isLocked();
		
		locationPanel.setEnabled(!isCurrentTabLocked);
		statusBar.updateStatusInfo();
	}
}
