package com.mucommander.sandbox;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mucommander.ui.main.folderpanel.LocationPanel;
import com.mucommander.ui.main.folderpanel.StatusBar;
import com.mucommander.ui.main.table.FileTable;
import com.mucommander.ui.tabs.ActiveTabListener;

public class SimpleFolderPanel extends JPanel implements FocusListener, ActiveTabListener {
	private static final long serialVersionUID = -1241860178448560061L;
	private static final Logger LOGGER = LoggerFactory.getLogger(SimpleFolderPanel.class);
	
    /** LocationPanel component */
    private LocationPanel locationPanel;
    
    /** FileTable component */
    private FileTable fileTable;
    
    /** StatusBar component instance */
    private StatusBar statusBar;    
	
    
	/**
	 * @see java.awt.event.FocusListener#focusGained(FocusEvent)
	 */
	@Override
	public void focusGained(FocusEvent e) {
        // Notify MainFrame that we are in control now! (our table/location field is active)
//        mainFrame.setActiveTable(fileTable);
//        statusBar.updateStatusInfo();
	}
	
	/**
	 * @see java.awt.event.FocusListener#focusLost(FocusEvent)
	 */	
	@Override
	public void focusLost(FocusEvent e) {
//        fileTable.getQuickSearch().stop();		
	}
	
	/**
	 * @see com.mucommander.ui.tabs.ActiveTabListener#activeTabChanged()
	 */
	@Override
	public void activeTabChanged() {
//		boolean isCurrentTabLocked = tabs.getCurrentTab().isLocked();
//		
//		locationPanel.setEnabled(!isCurrentTabLocked);
//		statusBar.updateStatusInfo();
	}	
	
}
