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

import java.util.function.Function;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.mucommander.commons.file.protocol.local.LocalFile;
import com.mucommander.commons.file.util.PathUtils;
import com.mucommander.commons.runtime.OsFamily;
import com.mucommander.ui.macosx.TabbedPaneUICustomizer;
import com.mucommander.ui.tabs.TabbedPane;

/**
 * TabbedPane that present the FileTable tabs.
 * 
 * This TabbedPane doesn't contain different FileTable for each tab, instead
 * it use one FileTable instance as a shared object for all tabs. when switching between
 * tabs, the FileTable instance is updated as needed according to the selected tab state.
 * 
 * @author Arik Hadas
 */
public class FileTableTabbedPane extends TabbedPane<FileTableTab> {
	private static final long serialVersionUID = -8631859671415549442L;

	/** The FileTable instance presented in each tab */
	private JComponent fileTableComponent;
	
	private Function<FileTableTab, FileTableTabHeader> fileTableTabHeaderProvider;
	

	public FileTableTabbedPane(JComponent fileTableComponent, Function<FileTableTab, FileTableTabHeader> fileTableTabHeaderProvider) {
		this.fileTableComponent = fileTableComponent;
		this.fileTableTabHeaderProvider = fileTableTabHeaderProvider;

		setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

		if (OsFamily.MAC_OS_X.isCurrent()) {
			TabbedPaneUICustomizer.customizeTabbedPaneUI(this);
		}
	}

	@Override
	public boolean requestFocusInWindow() {
		return fileTableComponent.requestFocusInWindow();
	}

	@Override
	public void removeTabAt(int index) {
		super.removeTabAt(index);

		if (index == 0 && getTabCount() > 0) {
			setComponentAt(0, fileTableComponent);
		}
	}

	/**
	 * Not in use yet
	 * 
	 * @param index
	 * @param component
	 */
	public void setTabHeader(int index, FileTableTabHeader component) {
		super.setTabComponentAt(index, component);
	}

	@Override
	public void add(FileTableTab tab) {
		add(tab, getTabCount());
	}

	@Override
	public void add(FileTableTab tab, int index) {
		add(getTabCount() == 0 ? fileTableComponent : new JLabel(), index);

		update(tab, index);
	}

	@Override
	public void update(FileTableTab tab, int index) {
		setTabHeader(index, fileTableTabHeaderProvider.apply(tab));

		String locationText = tab.getLocation().getPath();
		// For OSes with 'root drives' (Windows, OS/2), remove the leading '/' character
		if(LocalFile.hasRootDrives()) {
			locationText = PathUtils.removeLeadingSeparator(locationText, "/");
		}
		setToolTipTextAt(index, locationText);

		SwingUtilities.invokeLater(() -> validate());
	}
}
