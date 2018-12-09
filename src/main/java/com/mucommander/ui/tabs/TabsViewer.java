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

package com.mucommander.ui.tabs;

import java.awt.Component;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.event.ChangeListener;

/**
* Abstract class for components that display tabs.
* 
* @author Arik Hadas
*/
public abstract class TabsViewer<T extends Tab> extends JComponent {
	private static final long serialVersionUID = -6478921970628594866L;
	/** Collection of the displayed tabs */
	private TabsList<T> tabsList;
	/** The component to be displayed in the tab */	
	private JComponent viewComponent;
	
	public TabsViewer(TabsList<T> tabsList, JComponent viewComponent) {
		this.tabsList = tabsList;
		this.viewComponent = viewComponent;
		
		this.setBorder(BorderFactory.createEtchedBorder());
		setLayout(new GridLayout(1, 1));
		add(viewComponent);
	}
	
	protected JComponent getViewComponent() {
		return viewComponent;
	}

	public void addChangeListener(ChangeListener listener) { }
	
	public void removeChangeListener(ChangeListener listener) { }

	/*************** 
	 * Tabs Actions
	 ***************/
	
	public void add(T tab) {
		// do nothing by default 
	}
	
	public void add(T tab, int index) {
		// do nothing by default 
	}
	
	public void update(T tab, int index) {
		// do nothing by default
	}
	
	public int getSelectedTabIndex() {
		return 0;
	}
	
	public void setSelectedTabIndex(int index) {
		// do nothing by default 
	}

	public T removeCurrentTab() {
		return null;
	}
	
	public void removeDuplicateTabs() {
		// do nothing by default 
	}
	
	public void removeOtherTabs() {
		// do nothing by default
	}

	public void removeTab(Component header) {
		// do nothing by default		
	}
	
	public void removeTab(int index) {
		// do nothing by default		
	}
	
	public void nextTab() {
		int numberOfTabs = (null != tabsList) ? tabsList.count() : 0;
		int currentSelectedTabIndex = getSelectedTabIndex();
		int expectedSelectedTabIndex = (numberOfTabs + currentSelectedTabIndex + 1) % numberOfTabs;
		setSelectedTabIndex(expectedSelectedTabIndex);
	}
	
	public void previousTab() {
		int numberOfTabs = (null != tabsList) ? tabsList.count() : 0;
		int currentSelectedTabIndex = getSelectedTabIndex();
		int expectedSelectedTabIndex = (numberOfTabs + currentSelectedTabIndex - 1) % numberOfTabs;
		setSelectedTabIndex(expectedSelectedTabIndex);
	}
	
	public static <E extends Tab> TabsViewer<E> nullableTabsViewer() {
		return new NullableTabsViewer<E>();
	}
	
	public static <E extends Tab> TabsViewer<E> headerlessTabsViewer(TabsList<E> tabsList, JComponent viewComponent) {
		return new HeaderlessTabsViewer<E>(tabsList, viewComponent);
	}
	
	static class NullableTabsViewer<E extends Tab> extends TabsViewer<E> {
		private static final long serialVersionUID = 2637336460484189638L;

		public NullableTabsViewer() {
			super(null, new JLabel());
		}
		
		@Override
		public void remove(int index) { 
			// do nothing
		}			
	}
	
	static class HeaderlessTabsViewer<E extends Tab> extends TabsViewer<E> {
		private static final long serialVersionUID = 5602474396139288624L;

		public HeaderlessTabsViewer(TabsList<E> tabsList, JComponent viewComponent) {
			super(tabsList, viewComponent);
		}
		
		@Override
		public void add(E tab, int index) {
			if (index > 0) {
				throw new IllegalArgumentException("Unable to add tab at index > 0 to single tab display");
			}
			add(tab);
		}
		
		@Override
		public void requestFocus() {
			getViewComponent().requestFocusInWindow();
		}
	}
}
