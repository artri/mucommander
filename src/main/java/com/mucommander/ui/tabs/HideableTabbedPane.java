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

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mucommander.commons.conf.ConfigurationEvent;
import com.mucommander.commons.conf.ConfigurationListener;
import com.mucommander.conf.MuConfigurations;
import com.mucommander.conf.MuPreference;
import com.mucommander.conf.MuPreferences;
import com.mucommander.utils.EventListenerSet;

/**
 * This component acts like a tabbedpane in which multiple tabs are presented in a JTabbedPane layout 
 * and single tab is presented without the JTabbedPane layout, only the tab's data.
 * 
 * When a single tab is presented and new tab is added this component makes a switch to JTabbedPane layout,
 * and when two tabs are presented and there is a removal of one of the tabs this component makes a switch
 * to JPanel layout that contains the data of the tab that is left.
 * 
 * This component also provides an interface for the other parts of the application to make operations
 * that influence the tabs layout.
 *  
 * @author Arik Hadas
 */
public class HideableTabbedPane<T extends Tab> extends JComponent implements TabsEventListener, ConfigurationListener, ChangeListener {
	private static final long serialVersionUID = 7195980259970006078L;
	private static final Logger LOGGER = LoggerFactory.getLogger(HideableTabbedPane.class);
	private static final Consumer<ActiveTabListener> FIRE_ACTIVE_TAB_CHANGED = (listener) -> { listener.activeTabChanged(); };
	
	/* The tabs which are being displayed */
	private TabsList<T> tabsCollection;
	/* The tabs display type (with/without tabs headers)
	 * It is initialize as nullable so that it can be destroyed when it's replaced for the first time (see @{link tabAdded()})*/
	private TabsViewer<T> tabsViewer;
	
	/* The factory that will be used to create the viewers for tabs with no headers */
	private Function<TabsList<T>, TabsViewer<T>> tabsWithoutHeadersViewerProvider;
	/* The factory that will be used to create the viewers for tabs with headers */
	private Function<TabsList<T>, TabsViewer<T>> tabsWithHeadersViewerProvider;

	/* Contains all registered active tab change listeners, stored as weak references */
	private EventListenerSet<ActiveTabListener> activeTabChangedListenerSet = EventListenerSet.weakListenerSet();
	
	/**
	 * Constructor
	 *  
	 * @param tabsDisplayFactory - factory of tabs-display
	 */
	public HideableTabbedPane() {
		setBorder(BorderFactory.createEtchedBorder());
		setLayout(new BorderLayout());

		// Initialize the tabs collection
		this.tabsCollection = new TabsList<T>();
		// Register for tabs changes
		this.tabsCollection.addTabsListener(this);
		this.tabsViewer = TabsViewer.nullableTabsViewer();
		
		MuConfigurations.addPreferencesListener(this);
	}

	/**
     */
    public void addActiveTabListener(ActiveTabListener listener) {
    	activeTabChangedListenerSet.put(listener);
    }

    /**
     */
    public void removeActiveTabChangedListener(ActiveTabListener listener) {
    	activeTabChangedListenerSet.remove(listener);
    }

    /**
     */
    protected void fireActiveTabChanged() {
    	activeTabChangedListenerSet.notify(FIRE_ACTIVE_TAB_CHANGED);
    }

	/**
	 * This function returns an iterator that points to the current Tabs contained in the TabbedPane
	 * 
	 * @return Iterator that points to current Tabs
	 */
	public Iterator<T> iterator() {
		return tabsCollection.iterator();
	}

	/**
	 * Select the given tab
	 * 
	 * @param tab the tab to be selected
	 */
	public void selectTab(T tab) {
		int index = tabsCollection.indexOf(tab);

		if (index != -1) {
			selectTab(index);
		} else {
			LOGGER.warn("Was requested to change to non-existing tab, ignoring");
		}
	}

	/**
	 * Select the tab at the given index
	 * An exception will be thrown if no tab exists in the given index
	 * 
	 * @param index of the tab to be selected
	 */
	public void selectTab(int index) {
		tabsViewer.setSelectedTabIndex(index);
	}

	/**
	 * Return the index of the selected tab
	 * 
	 * @return index of the selected tab
	 */
	public int getSelectedIndex() {
		return tabsViewer.getSelectedTabIndex();
	}

	/**
	 * Return how many tabs the panel contains
	 * 
	 * @return number of tabs contained in the panel
	 */
	public int getTabsCount() {
		return tabsCollection.count();
	}

	protected TabsList<T> getTabs() {
		return tabsCollection;
	}

	/***********************
	 * Tabs Actions Support
	 ***********************/

	/* Actions which are not depended on the display type (single/multiple tabs) */

	/**
	 * Add new tab
	 * 
	 * @param tab - new tab's data
	 */
	protected void addTab(T tab) {
		tabsCollection.add(tab);
	}

	/**
	 * Add new tab and select it
	 * 
	 * @param tab - new tab's data
	 */
	protected void addAndSelectTab(T tab) {
		addTab(tab);
		tabsViewer.setSelectedTabIndex(tabsCollection.count()-1);
	}

	/**
	 * Update the current displayed tab's data with the given {@link TabUpdater}
	 * 
	 * @param updater - object that will be used to update the tab
	 */
	protected void updateCurrentTab(TabUpdater<T> updater) {
		tabsCollection.updateTab(getSelectedIndex(), updater);
	}

	/* Actions that depended on the display type (single/multiple tabs) */

	/**
	 * Remove tab with the given header
	 */
	protected void removeTab(Component header) {
		tabsViewer.removeTab(header);
	}

	/**
	 * Remove current displayed tab
	 */
	protected T removeTab() {
		return tabsCollection.remove(getSelectedIndex());
	}

	/**
	 * Remove duplicate tabs
	 */
	protected void removeDuplicateTabs() {
		tabsViewer.removeDuplicateTabs();
	}

	/**
	 * Remove all tabs except the current displayed tab
	 */
	protected void removeOtherTabs() {
		tabsViewer.removeOtherTabs();
	}

	/**
	 * Change the current displayed tab to the tab which is located to the right of the
	 * current displayed tab.
	 * If the current displayed tab is the rightmost tab, the leftmost tab will be displayed.
	 */
	public void nextTab() {
		tabsViewer.nextTab();
	}

	/**
	 * Change the current displayed tab to the tab which is located to the left of the
	 * current displayed tab.
	 * If the current displayed tab is the leftmost tab, the rightmost tab will be displayed.
	 */
	public void previousTab() {
		tabsViewer.previousTab();
	}

	/******************
	 * Private Methods
	 ******************/
	private void switchToTabsWithHeaders() {
		setTabsViewer(tabsWithHeadersViewerProvider);
	}

	private void switchToTabWithoutHeader() {
		setTabsViewer(tabsWithoutHeadersViewerProvider);
	}

	private void setTabsViewer(Function<TabsList<T>, TabsViewer<T>> tabViewerProvider) {
		if (null != tabsViewer) {
			tabsViewer.removeChangeListener(this);
		}
		
		if (null != tabViewerProvider) {
			tabsViewer = tabViewerProvider.apply(tabsCollection);
		} else {
			tabsViewer = TabsViewer.nullableTabsViewer();
		}
		
		tabsViewer.addChangeListener(this);

		removeAll();
		add(tabsViewer);
		validate();

		tabsViewer.requestFocus();
	}

	/********************
	 * Protected Methods
	 ********************/

	protected Function<TabsList<T>, TabsViewer<T>> getTabsWithoutHeadersViewerProvider() {
		return tabsWithoutHeadersViewerProvider;
	}

	protected void setTabsWithoutHeadersViewerProvider(
			Function<TabsList<T>, TabsViewer<T>> tabsWithoutHeadersViewerProvider) {
		this.tabsWithoutHeadersViewerProvider = tabsWithoutHeadersViewerProvider;
		refreshViewer();
	}

	protected Function<TabsList<T>, TabsViewer<T>> getTabsWithHeadersViewerProvider() {
		return tabsWithHeadersViewerProvider;
	}

	protected void setTabsWithHeadersViewerProvider(
			Function<TabsList<T>, TabsViewer<T>> tabsWithHeadersViewerProvider) {
		this.tabsWithHeadersViewerProvider = tabsWithHeadersViewerProvider;
		refreshViewer();		
	}
	
	/**
	 * Returns the tab at the given index
	 * An exception will be thrown if no tab exists in the given index
	 * 
	 * @param index of the requested tab
	 * @return tab in the given index
	 */
	protected T getTab(int index) {
		return tabsCollection.get(index);
	}

	protected boolean refreshViewer() {
		int nbTabs = tabsCollection.count();

		switch (nbTabs) {
		case 2:
			switchToTabsWithHeaders();
			
			return true;
		case 1:
			if (showSingleTabHeader()) {
				switchToTabsWithHeaders();
			} else {
				switchToTabWithoutHeader();
			}
			return true;
		default:
			return false;
		}
	}

	protected boolean showSingleTabHeader() {
		return MuConfigurations.getPreferences().getVariable(MuPreference.SHOW_TAB_HEADER, MuPreferences.DEFAULT_SHOW_TAB_HEADER);
	}

	protected void show(int tabIndex) {
	}

	/************************************
	 * TabsChangeListener Implementation
	 ************************************/

	public void tabAdded(TabsEventListener.Event event) {
		int index = event.getTabIndex();
		if (!refreshViewer()) {
			tabsViewer.add(tabsCollection.get(index), index);
		}
		if (isDisplayable()) {
			tabsViewer.setSelectedTabIndex(index);
		}
	}

	public void tabRemoved(TabsEventListener.Event event) {
		int index = event.getTabIndex();
		int previouslySelectedIndex = tabsViewer.getSelectedTabIndex();

		if (!refreshViewer()) {
			tabsViewer.removeTab(index);
		} else {
			selectTab(Math.max(previouslySelectedIndex-1, 0));
		}
	}
	
	public void tabUpdated(TabsEventListener.Event event) {
		int index = event.getTabIndex();
		tabsViewer.update(tabsCollection.get(index), index);
		
		fireActiveTabChanged();
	}

	/***************************************
	 * ConfigurationListener Implementation
	 ***************************************/

	public void configurationChanged(ConfigurationEvent event) {
		String var = event.getVariable();

        // Update the button's icon if the system file icons policy has changed
        if (var.equals(MuPreferences.SHOW_SINGLE_TAB_HEADER)) {
            refreshViewer();
        }
	}

	public void stateChanged(ChangeEvent e) {
		final int selectedIndex = tabsViewer.getSelectedTabIndex();

		if (selectedIndex != -1) {
			show(selectedIndex);
		}

		SwingUtilities.invokeLater(() -> tabsViewer.requestFocus());
	}
}
