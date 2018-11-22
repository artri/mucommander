package com.mucommander.ui.dnd;

import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import javax.swing.JComponent;

public class DropTargetHandler implements DropTargetListener, Serializable {
	private static final long serialVersionUID = 3913321787541531356L;
	
	private final Component component;
	private final DropTarget dropTarget;
	private final Set<DataFlavor> supportedDrops;
	
	public <V extends Component> DropTargetHandler(Component component, Class<? extends V> supportedDrop) {
		this.component = Objects.requireNonNull(component);
		this.dropTarget = new DropTarget(this.component, DnDConstants.ACTION_MOVE, this, true);
		this.supportedDrops = TransferableComponent.toDataFlavor(supportedDrop, supportedDrop);
	}

	public void release() {
		this.dropTarget.removeDropTargetListener(this);
	}
	
	private boolean isDataFlavorSupported(DataFlavor[] flavors) {
		return supportedDrops.containsAll(Arrays.asList(flavors));
	}
	
	@Override
    public void dragEnter(DropTargetDragEvent dtde) {
        // Determine if we can actually process the contents coming in.
        // You could try and inspect the transferable as well, but 
        // there is an issue on the MacOS under some circumstances
        // where it does not actually bundle the data until you accept the drop.
        if (isDataFlavorSupported(dtde.getCurrentDataFlavors())) {
        	dtde.acceptDrag(DnDConstants.ACTION_MOVE);
        } else {
        	dtde.rejectDrag();
        }
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
    	DataFlavor[] transferableDataFlavors = dtde.getCurrentDataFlavors();
    	
        // Basically, we want to unwrap the present...
        if (!(dtde.getDropTargetContext().getComponent() instanceof JComponent)) {
        	dtde.rejectDrop();
        	dtde.dropComplete(false);
        	return;
        } else if (!isDataFlavorSupported(transferableDataFlavors)) {
        	dtde.rejectDrop();
        	dtde.dropComplete(false);        	
        } else {
        	boolean success = false;
        	
            JComponent toComponent = (JComponent) dtde.getDropTargetContext().getComponent();
            Transferable transferable = dtde.getTransferable();
            for (DataFlavor df : transferableDataFlavors) {
                try {
                	Object data = transferable.getTransferData(df);
                    if (data instanceof Component) {
                    	dropComponent((Component) data, toComponent);
                    	success = true;
                    } else {
                        success = false;
                        break;
                    }
                } catch (Exception exp) {
                    success = false;
                    exp.printStackTrace();
                }            	
            }

            if (success) {
            	dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                ((JComponent) toComponent).invalidate();
                ((JComponent) toComponent).repaint();
            } else {
            	dtde.rejectDrop();
            }
            dtde.dropComplete(success);        	
        }
    }
    
    private void dropComponent(Component dropComponent, JComponent toComponent) {
        Container parent = dropComponent.getParent();
        if (parent != null) {
            parent.remove(dropComponent);
            parent.revalidate();
            parent.repaint();
        }
        toComponent.add(dropComponent);
    }
}
