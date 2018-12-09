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

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileURL;
import com.mucommander.core.LocalLocationHistory;
import com.mucommander.desktop.DesktopManager;
import com.mucommander.ui.action.ActionManager;
import com.mucommander.ui.event.LocationEvent;
import com.mucommander.ui.event.LocationListener;
import com.mucommander.ui.main.FolderPanel;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.ui.tabs.HideableTabbedPane;
import com.mucommander.ui.tabs.TabUpdater;
import com.mucommander.ui.tabs.TabsList;
import com.mucommander.ui.tabs.TabsViewer;
import com.mucommander.ui.tabs.TabsWithHeaderViewer;
import com.mucommander.utils.Callback;

/**
* HideableTabbedPane of {@link com.mucommander.ui.main.tabs.FileTableTab} instances.
* 
* @author Arik Hadas
*/
public class FileTableTabs extends HideableTabbedPane<FileTableTab> implements LocationListener {
	private static final long serialVersionUID = 2589072027342237464L;
	
    /** Frame containing this file table. */
    private MainFrame   mainFrame;	
	/** FolderPanel containing those tabs */
	private FolderPanel folderPanel;
	
	public FileTableTabs(MainFrame mainFrame, FolderPanel folderPanel, ConfFileTableTab[] initialTabs) {
		super();
		
		this.mainFrame = mainFrame;
		this.folderPanel = folderPanel;
		//this.setBorder(BorderFactory.createEtchedBorder());

		setTabsWithHeadersViewerProvider(this::createTabsViewerWithHeaders);
		setTabsWithoutHeadersViewerProvider(this::createTabsViewerWithoutHeaders);
		
		// Register to location change events
		folderPanel.getLocationManager().addLocationListener(this);

		// Add the initial folders
		for (FileTableTab tab : initialTabs) {
			addTab(createTab(tab));
		}
	}

	@Override
	public void selectTab(int index) {
		super.selectTab(index);

		show(index);
	}

	@Override
	protected void show(final int tabIndex) {
		folderPanel.tryChangeCurrentFolderInternal(getTab(tabIndex).getLocation(), new Callback() {
			public void call() {
				fireActiveTabChanged();
			}
		});
	};

	/**
	 * Return the currently selected tab
	 * 
	 * @return currently selected tab
	 */
	public FileTableTab getCurrentTab() {
		return getTab(getSelectedIndex());
	}

	private void updateTabLocation(final FileURL location) {
		updateCurrentTab(new TabUpdater<FileTableTab>() {

			public void update(FileTableTab tab) {
				tab.setLocation(location);
			}
		});
	}
	
	private void updateTabLocking(final boolean lock) {
		updateCurrentTab(new TabUpdater<FileTableTab>() {
			
			public void update(FileTableTab tab) {
				tab.setLocked(lock);
			}
		});
	}

	private void updateTabTitle(final String title) {
		updateCurrentTab(new TabUpdater<FileTableTab>() {

			public void update(FileTableTab tab) {
				tab.setTitle(title);
			}
		});
	}

	@Override
	protected boolean showSingleTabHeader() {
		int nbTabs = getTabs().count();
		
		if (nbTabs == 1) {
			FileTableTab tab = getTab(0);
			
			// If there's just single tab that is locked don't remove his header
			if (tab.isLocked()) {
				return true;
			}
		}
		
		return super.showSingleTabHeader();
	}
	
	@Override
	protected FileTableTab removeTab() {
		return !getCurrentTab().isLocked() ? super.removeTab() : null;
	}
	
	public FileTableTab createTab(FileURL location) {
		LocalLocationHistory locationHistory = new LocalLocationHistory(folderPanel);
		return FileTableTab.of(locationHistory, location);
	}
	
	private FileTableTab createTab(FileTableTab tab) {
		LocalLocationHistory locationHistory = new LocalLocationHistory(folderPanel);
		return FileTableTab.of(locationHistory, tab);
	}
	
	public TabsViewer<FileTableTab> createTabsViewerWithoutHeaders(TabsList<FileTableTab> tabsList) {
		return TabsViewer.headerlessTabsViewer(tabsList, folderPanel.getFileTable().getAsUIComponent());
	}
	
	public TabsViewer<FileTableTab> createTabsViewerWithHeaders(TabsList<FileTableTab> tabs) {
		FileTableTabbedPane fileTableTabbedPane;
		if (tabs.count() == 1) {
			fileTableTabbedPane = new FileTableTabbedPane(folderPanel.getFileTable().getAsUIComponent(), this::createNonCloseableFileTableTabHeader);
		} else {
			fileTableTabbedPane = new FileTableTabbedPane(folderPanel.getFileTable().getAsUIComponent(), this::createDefaultFileTableTabHeader);
		}
		fileTableTabbedPane.addFocusListener(new FileTableTabbedPaneFocusListener(fileTableTabbedPane));
		fileTableTabbedPane.addMouseListener(new FileTableTabbedPaneMouseListener(fileTableTabbedPane));
		return new TabsWithHeaderViewer<FileTableTab>(tabs, fileTableTabbedPane);
	}
	
	public FileTableTabHeader createDefaultFileTableTabHeader(FileTableTab tab) {
		FileTableTabHeader fileTableTabHeader = new FileTableTabHeader(true, tab);
		fileTableTabHeader.setTabCloseListener(new FileTableTabHeaderClosedActionListener());
		return fileTableTabHeader;
	}
	
	public FileTableTabHeader createNonCloseableFileTableTabHeader(FileTableTab tab) {
		FileTableTabHeader fileTableTabHeader = new FileTableTabHeader(false, tab);
		fileTableTabHeader.setTabCloseListener(new FileTableTabHeaderClosedActionListener());		
		return fileTableTabHeader;
	}
	
	
	/********************
	 * MuActions support
	 ********************/
	
	public void add(AbstractFile file) {
		addTab(createTab(file.getURL()));
	}

	public void add(FileURL fileURL) {
		addTab(createTab(fileURL));
	}

	public void add(FileTableTab tab) {
		addAndSelectTab(tab);
	}
	
	public FileTableTab closeCurrentTab() {
		return removeTab();
	}
	
	public void closeDuplicateTabs() {
		removeDuplicateTabs();
	}
	
	public void closeOtherTabs() {
		removeOtherTabs();
	}
	
	public void duplicate() {
		add(createTab(getCurrentTab()));
	}
	
	public void lock() {
		updateTabLocking(true);
	}
	
	public void unlock() {
		updateTabLocking(false);
	}

	public void setTitle(String title) {
		updateTabTitle(title);
	}

	/****************
	 * Other Actions
	 ****************/
	
	public void close(FileTableTabHeader fileTableTabHeader) {
		removeTab(fileTableTabHeader);
	}
	
	/**********************************
	 * LocationListener Implementation
	 **********************************/
	
	public void locationChanged(LocationEvent locationEvent) {
		updateTabLocation(folderPanel.getCurrentFolder().getURL());
	}

	public void locationCancelled(LocationEvent locationEvent) {
		updateTabLocation(folderPanel.getCurrentFolder().getURL());
	}

	public void locationFailed(LocationEvent locationEvent) {
		updateTabLocation(folderPanel.getCurrentFolder().getURL());
	}
	
	public void locationChanging(LocationEvent locationEvent) { }
	
	
	class FileTableTabbedPaneFocusListener extends FocusAdapter {
		private final FileTableTabbedPane fileTableTabbedPane;
		
		public FileTableTabbedPaneFocusListener(FileTableTabbedPane fileTableTabbedPane) {
			this.fileTableTabbedPane = fileTableTabbedPane;
		}
		
		public void focusGained(FocusEvent e) {
			FileTableTabs.this.requestFocus();
		}
		
		public void focusLost(FocusEvent e) { }		
	};
	
	class FileTableTabbedPaneMouseListener extends MouseAdapter {
		private final FileTableTabbedPane fileTableTabbedPane;
		
		public FileTableTabbedPaneMouseListener(FileTableTabbedPane fileTableTabbedPane) {
			this.fileTableTabbedPane = fileTableTabbedPane;
		}

		public void mouseClicked(MouseEvent e) {
			final Point clickedPoint = e.getPoint();
			int selectedTabIndex = fileTableTabbedPane.indexAtLocation(clickedPoint.x, clickedPoint.y);
			if (selectedTabIndex != -1) {
				// Allow tabs switching only when no-events-mode is disabled
				if (!mainFrame.getNoEventsMode()) {
					fileTableTabbedPane.setSelectedIndex(selectedTabIndex);
					fileTableTabbedPane.requestFocusInWindow();
				}				

				if (DesktopManager.isRightMouseButton(e)) {
					// Open the popup menu only after all swing events are finished, to ensure that when the popup menu is shown
					// and asks for the currently selected tab in the active panel, it'll get the right one
					SwingUtilities.invokeLater(() -> new FileTableTabPopupMenu(mainFrame).show(fileTableTabbedPane, clickedPoint.x, clickedPoint.y));
				}

				if (DesktopManager.isMiddleMouseButton(e)) {
					ActionManager.performAction(com.mucommander.ui.action.impl.CloseTabAction.Descriptor.ACTION_ID, mainFrame);
				}
			}
		}
	};
	
	class FileTableTabHeaderClosedActionListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent event) {
			if (event.getSource() instanceof FileTableTabHeader) {
				FileTableTabs.this.close((FileTableTabHeader) event.getSource());
			}
		}
	}
}
