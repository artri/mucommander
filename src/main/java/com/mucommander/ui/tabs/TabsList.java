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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;

import com.mucommander.utils.EventListenerSet;

/**
* Collection of tabs
* The collection is iterable, and can notify listeners about added/removed/updated tabs
* 
* @author Arik Hadas
*/
public class TabsList<T extends Tab> implements java.lang.Iterable<T> {
	
	private static final BiConsumer<TabsEventListener, TabsEventListener.Event> FIRE_TAB_ADDED = (listener, event) -> { listener.tabAdded(event); };
	private static final BiConsumer<TabsEventListener, TabsEventListener.Event> FIRE_TAB_REMOVED = (listener, event) -> { listener.tabRemoved(event); };
	private static final BiConsumer<TabsEventListener, TabsEventListener.Event> FIRE_TAB_UPDATED = (listener, event) -> { listener.tabUpdated(event); };
	
	/** Internal list of tabs which does not accept null elements*/
	@SuppressWarnings("serial")
	private final List<T> tabsList = new ArrayList<T>() {
		private static final int DEFAULT_TRIM_FACTOR = 10;
		private int trimCounter = 0;
		
		@Override
		public boolean add(T element) {
			boolean result = false;
			if (null != element) { 
				result = super.add(element);
				trimToSize();
			}
			
			return result;
		}

		// stupid implementation that does not allow adding nulls from the source collection
		// this might be improved in future via bulk checking for nulls
		//TODO: improve according to comment above
		@Override
		@SuppressWarnings("unchecked")
		public boolean addAll(Collection<? extends T> collection) {
			if (null == collection) {
				return false;
			}
			boolean result = false;
			Object[] elements = collection.toArray();
			for (Object element : elements) {
				result |= add((T) element);
			}
			return result;
		}

		@Override
		public void add(int index, T element) {
			if (null != element) {
				super.add(index, element);
				trimToSize();
			}
		}

		@Override
		public T remove(int index) {
			T element = super.remove(index);
			trimToSize();
			return element;
		}

		@Override
		public void trimToSize() {
			trimCounter++;
			if (trimCounter / DEFAULT_TRIM_FACTOR > 0) {
				trimCounter = 0;
				super.trimToSize();
			}
		}
	};
	
	/** Listeners that were registered to be notified when tabs are added/removed/updated */
	private EventListenerSet<TabsEventListener> tabsListeners = EventListenerSet.weakListenerSet();
	
	/**
	 * Empty constructor
	 */
	public TabsList() {
	}
	
	/**
	 * Constructor that creates the collection with a single given tab
	 * 
	 * @param tab - a tab
	 */
	public TabsList(T tab) {
		tabsList.add(tab);
	}
	
	/**
	 * Constructor that creates the collection with multiple given tabs
	 * 
	 * @param tabs - list of tabs
	 */
	public TabsList(List<T> tabs) {
		tabsList.addAll(tabs);
	}
	
	/**
	 * Add the given tab to the collection
	 * The tab would be inserted in the last index in the collection
	 * 
	 * @param tab - tab
	 */
	public void add(T tab) {
		add(tab, count());
	}
	
	/**
	 * Add the given tab to the collection in a given index
	 * 
	 * @param tab - tab
	 * @param index - the index in which the tab would be inserted in the collection
	 */
	public void add(T tab, int index) {
		tabsList.add(index, tab);
		fireTabAdded(count() - 1);
	}
	
	/**
	 * Update the tab in the given index with the given {@link TabUpdater}
	 * 
	 * @param index - the index of the tab to be updated
	 * @param updater - the object that will be used to update the tab
	 */
	public void updateTab(int index, TabUpdater<T> updater) {
		updater.update(get(index));
		fireTabUpdated(index);
	}
	
	/**
	 * Remove the tab in the given index
	 * If there are less than two tabs in the collection, the tab
	 * won't be removed in order to prevent a situation in which
	 * we remain with no tabs at all
	 * 
	 * @param index - the index of the tab to be removed
	 */
	public T remove(int index) {
		if (tabsList.size() > 1) {
			T tab = tabsList.remove(index);
			fireTabRemoved(index);
			return tab;
		}
		return null;
	}
	
	/**
	 * Return the tab in the given index
	 * 
	 * @param index - the index of the tab to be returned
	 * @return the tab in the given index
	 */
	public T get(int index) {
		return tabsList.get(index);
	}
	
	/**
	 * Return the number of tabs contained in the collection
	 * 
	 * @return the number of tabs contained in the collection
	 */
	public int count() {
		return tabsList.size();
	}
	
	/**
	 * Return the index of the given tab in the collection
	 * 
	 * @return the index of the given tab or -1 if the tab is not exist in the collection
	 */
	public int indexOf(T tab) {
		return tabsList.indexOf(tab);
	}
	
	/********************
	 * Listeners support
	 ********************/
	
	/**
	 * Add a given listener to the listeners to be notified about tabs-changes
	 * 
	 * @param listener - object that implements TabsChangeListener interface
	 */
	public void addTabsListener(TabsEventListener listener) {
        tabsListeners.put(listener);
    }

	/**
	 * Remove a given listener from the listeners to be notifies about tabs-changes
	 * 
	 * @param listener - object that implements TabsChangeListener interface
	 */
    public void removeTabsListener(TabsEventListener listener) {
    	tabsListeners.remove(listener);
    }
    
    /**
     * Notify the registered listeners about addition of tab in the given index
     * 
     * @param index - the index of the added tab
     */
    public void fireTabAdded(int index) {
    	tabsListeners.notify(FIRE_TAB_ADDED, new TabsEventListener.Event(index));
    }
    
    /**
     * Notify the registered listeners about removal of tab in the given index
     * 
     * @param index - the index in which the removed tab was located
     */
    public void fireTabRemoved(int index) {
    	tabsListeners.notify(FIRE_TAB_REMOVED, new TabsEventListener.Event(index));
    }
    
    /**
     * Notify the registered listeners about tab that was updated in the given index
     * 
     * @param index - the index of the updated tab
     */
    public synchronized void fireTabUpdated(int index) {
    	tabsListeners.notify(FIRE_TAB_UPDATED, new TabsEventListener.Event(index));
    }
    
	/**************************
	 * Iterable implementation
	 **************************/

	public Iterator<T> iterator() {
		return tabsList.iterator();
	}
}
