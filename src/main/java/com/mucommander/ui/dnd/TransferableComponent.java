package com.mucommander.ui.dnd;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

public class TransferableComponent<T extends Component> implements Transferable {
	
	private static final WeakHashMap<Class<? extends Component>, DataFlavor> CLASS_TO_DATAFLAVOR = new WeakHashMap<>();
	
	private final T component;
	private final DataFlavor dataFlavor;
	private DataFlavor[] dataFlavors;
		
    private TransferableComponent(T component) {
		this.component = component;
		this.dataFlavor = toDataFlavor(component.getClass());
	}

	public T getComponent() {
		return component;
	}

	@Override
    public DataFlavor[] getTransferDataFlavors() {
		if (null == this.dataFlavors) {
			this.dataFlavors = new DataFlavor[] {this.dataFlavor};
		}
		return this.dataFlavors;
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
    	return Objects.equals(this.dataFlavor, flavor);
    }
    
    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (isDataFlavorSupported(flavor)) {
            return getComponent();
        }
        throw new UnsupportedFlavorException(flavor);
    }
    
	public static <V extends Component> TransferableComponent<V> wrap(V component) {
		return new TransferableComponent<V>(Objects.requireNonNull(component));
	}
	
	public static <V extends Component> Set<DataFlavor> toDataFlavor(Class<? extends V> ... clazzCollection) {
		return toDataFlavor(Arrays.asList(clazzCollection));
	}
	
	public static <V extends Component> Set<DataFlavor> toDataFlavor(Collection<Class<? extends V>> clazzCollection) {
		Set<DataFlavor> dataFlavors = new HashSet<>();
		for (Class<? extends V> clazz : clazzCollection) {
			dataFlavors.add(toDataFlavor(clazz));
		}
		return Collections.unmodifiableSet(dataFlavors);
	}
	
	public static <V extends Component> DataFlavor toDataFlavor(Class<? extends V> clazz) {
		DataFlavor df = CLASS_TO_DATAFLAVOR.get(clazz);
		if (null == df) {
			df = new DataFlavor(clazz, null);
			CLASS_TO_DATAFLAVOR.put(clazz, df);
		}
		return df;
	}
}
