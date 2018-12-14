package com.mucommander.ui.main.folderpanel;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.dnd.DropTarget;
import java.util.Objects;

import javax.swing.JPanel;

import com.mucommander.ui.dnd.FileDropTargetListener;
import com.mucommander.ui.event.LocationManager;

public class LocationPanel extends JPanel {
	private static final long serialVersionUID = 2435588655096462175L;

	private final LocationManager locationManager;
    
    /*  We're NOT using JComboBox anymore because of its strange behavior:
    it calls actionPerformed() each time an item is highlighted with the arrow (UP/DOWN) keys,
    so there is no way to tell if it's the final selection (ENTER) or not.
    */    
	private DrivePopupButton driveButton;
	private LocationTextField locationTextField;
	
	public LocationPanel(LocationManager locationManager) {
		super(new GridBagLayout(), true);
		
		this.locationManager = Objects.requireNonNull(locationManager);
		initComponents();
		initListeners();
	}
	
	private void initComponents() {
        // Create drive button
        this.driveButton = new DrivePopupButton(locationManager);
        // Create location text field
        this.locationTextField = new LocationTextField(locationManager);
        
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;

        c.weightx = 0;
        c.gridx = 0;        
        add(driveButton, c);

        // Give location field all the remaining space until the PoupupsButton
        c.weightx = 1;
        c.gridx = 1;
        // Add some space between drive button and location combo box (none by default)
        c.insets = new Insets(0, 4, 0, 0);
        add(locationTextField, c);        
	}
	
	private void initListeners() {
        // Listen to location events to update the button when the current folder changes
		locationManager.addLocationListener(driveButton);
        
		locationTextField.addFocusListener(locationManager.getFolderPanel());
        // Listen to location changes to update popup menu choices and disable this component while the location is
        // being changed
		locationManager.addLocationListener(locationTextField);
		
		// Drag and Drop support
        // Allow the location field to change the current directory when a file/folder is dropped on it
        FileDropTargetListener dropTargetListener = new FileDropTargetListener(locationManager.getFolderPanel(), true);
        locationTextField.setDropTarget(new DropTarget(locationTextField, dropTargetListener));
        driveButton.setDropTarget(new DropTarget(driveButton, dropTargetListener));		
	}
	
    /**
     * Returns the LocationTextField contained by this panel.
     *
     * @return the LocationTextField contained by this panel
     */
    public LocationTextField getLocationTextField() {
        return locationTextField;
    }

    /**
     * Returns the DrivePopupButton contained by this panel.
     *
     * @return the DrivePopupButton contained by this panel
     */
    public DrivePopupButton getDriveButton() {
        return driveButton; 
    }

    /**
     * Allows the user to easily change the current folder and type a new one: requests focus 
     * on the location field and selects the folder string.
     */
    public void changeCurrentLocation() {
    	locationTextField.selectAll();
    	locationTextField.requestFocus();
    }
    
    public void setProgressValue(int value) {
    	locationTextField.setProgressValue(value);
    }
    
	@Override
	public void setEnabled(boolean enabled) {
		locationTextField.setEnabled(enabled);
		driveButton.setEnabled(enabled);
		super.setEnabled(enabled);
	}
    
    
}
