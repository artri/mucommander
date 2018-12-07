package com.mucommander.utils;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * EventListenerSet class.
 * 
 * @param <T>
 */
public class EventListenerSet<T extends EventListener> {
	
	public static class Event {
		public static final Event EMPTY = new Event(0L);
		
		private final long timestamp;

		public Event() {
			this(System.nanoTime());
		}
		
		public Event(long timestamp) {
			this.timestamp = timestamp;
		}

		public long getTimestamp() {
			return timestamp;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.timestamp);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if(!(o instanceof Event)) {
				return false;
			}
			Event another = (Event) o;
			return Objects.equals(this.timestamp, another.timestamp);
		}
	}
	
	/** Contains registered listeners, stored as weak references */
	private final Map<T, ?> listenerMap;

	private EventListenerSet(Map<T, ?> listenerMap) {
		this.listenerMap = listenerMap;
	}
	
	/**
	 * Add a listener
     */
    public void put(T listener) {
    	Objects.requireNonNull(listener, "listener");
    	synchronized (listenerMap) {
    		listenerMap.put(listener, null);
    	}
    }

    /**
     * remove a listener
     */
    public void remove(T listener) {
    	if (null == listener) {
    		return;
    	}
    	
    	synchronized (listenerMap) {
    		listenerMap.remove(listener);
    	}
    }	
	
    /**
     * clear the registered listeners
     */
    public void clear() {
    	synchronized (listenerMap) {
    		listenerMap.clear();
    	}    	
    }
    
    public void notify(Consumer<T> handler) {
    	Objects.requireNonNull(handler);
    	EventListener[] listeners;
    	synchronized (listenerMap) {
    		listeners = listenerMap.keySet().toArray(new EventListener[] {});
		}
        for(EventListener listener : listeners) {
            handler.accept((T) listener);
        }
    }
    
    public <E extends Event> void notify(BiConsumer<T, E> handler, E event) {
    	Objects.requireNonNull(handler);
    	Objects.requireNonNull(event);
    	EventListener[] listeners;
    	synchronized (listenerMap) {
    		listeners = listenerMap.keySet().toArray(new EventListener[] {});
		}
        for(EventListener listener : listeners) {
            handler.accept((T) listener, event);
        }    	
    }
    
    public static <V extends EventListener> EventListenerSet<V> listenerSet() {
    	return new EventListenerSet<>(new WeakHashMap<V, Object>());
    }
    
    public static <V extends EventListener> EventListenerSet<V> weakListenerSet() {
    	return new EventListenerSet<>(new WeakHashMap<V, Object>());    	
    }
}
