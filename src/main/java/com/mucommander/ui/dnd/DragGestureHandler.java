package com.mucommander.ui.dnd;

import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.io.Serializable;
import java.util.Objects;

public class DragGestureHandler implements DragGestureListener, DragSourceListener, Serializable {
	private static final long serialVersionUID = -7712596099312724623L;
	
    private final Component component;
    private final DragGestureRecognizer dragGestureRecognizer;
    private Container componentParent;
    
    public DragGestureHandler(Component component) {
    	this.component = Objects.requireNonNull(component);
        this.dragGestureRecognizer = DragSource.getDefaultDragSource()
        		.createDefaultDragGestureRecognizer(this.component, DnDConstants.ACTION_MOVE, this);
    }

    public void release() {
    	this.dragGestureRecognizer.removeDragGestureListener(this);
    }    
    
	public Component getComponent() {
		return component;
	}
	
    public Container getComponentParent() {
		return componentParent;
	}

	public void setComponentParent(Container componentParent) {
		this.componentParent = componentParent;
	}

	@Override
    public void dragGestureRecognized(DragGestureEvent dge) {
        // When the drag begins, we need to grab a reference to the
        // parent container so we can return it if the drop
        // is rejected
        Container parent = getComponent().getParent();
        setComponentParent(parent);

        // Remove the panel from the parent.  If we don't do this, it
        // can cause serialization issues.  We could overcome this
        // by allowing the drop target to remove the component, but that's
        // an argument for another day
        // This is causing a NullPointerException on MacOS 10.13.3/Java 8
        //      parent.remove(getPanel());
        //      // Update the display
        //      parent.invalidate();
        //      parent.repaint();

        // Create our transferable wrapper
        Transferable transferable = TransferableComponent.wrap(getComponent());
        // Start the "drag" process...
        DragSource ds = dge.getDragSource();
        ds.startDrag(dge, null, transferable, this);

        parent.remove(getComponent());
        // Update the display
        parent.invalidate();
        parent.repaint();
    }

    @Override
    public void dragEnter(DragSourceDragEvent dsde) {
    }

    @Override
    public void dragOver(DragSourceDragEvent dsde) {
    }

    @Override
    public void dropActionChanged(DragSourceDragEvent dsde) {
    }

    @Override
    public void dragExit(DragSourceEvent dse) {
    }

    @Override
    public void dragDropEnd(DragSourceDropEvent dsde) {
        // If the drop was not successful, we need to
        // return the component back to it's previous parent
        if (!dsde.getDropSuccess()) {
            getComponentParent().add(getComponent());
        } else {
        	getComponentParent().remove(getComponent());
        }
        getComponentParent().invalidate();
        getComponentParent().repaint();
    }
}
