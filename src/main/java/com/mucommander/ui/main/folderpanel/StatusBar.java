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

package com.mucommander.ui.main.folderpanel;

import java.awt.Dimension;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mucommander.commons.conf.ConfigurationEvent;
import com.mucommander.commons.conf.ConfigurationListener;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.conf.MuConfigurations;
import com.mucommander.conf.MuPreference;
import com.mucommander.conf.MuPreferences;
import com.mucommander.text.SizeFormat;
import com.mucommander.text.Translator;
import com.mucommander.ui.components.VolumeSpaceLabel;
import com.mucommander.ui.event.LocationEvent;
import com.mucommander.ui.event.LocationListener;
import com.mucommander.ui.event.TableSelectionListener;
import com.mucommander.ui.icon.SpinningDial;
import com.mucommander.ui.main.FolderPanel;
import com.mucommander.ui.main.table.FileTable;
import com.mucommander.ui.main.table.FileTableModel;
import com.mucommander.ui.theme.ColorChangedEvent;
import com.mucommander.ui.theme.FontChangedEvent;
import com.mucommander.ui.theme.Theme;
import com.mucommander.ui.theme.ThemeListener;
import com.mucommander.ui.theme.ThemeManager;


/**
 * StatusBar is the component that sits at the bottom of each MainFrame, between the folder panels and command bar.
 * There is one and only one StatusBar per MainFrame, created by the associated MainFrame. It can be hidden, 
 * but the instance will always remain, until the MainFrame is disposed. 
 *
 * <p>StatusBar is used to display info about the total/selected number of files in the current folder and current volume's
 * free/total space. When a folder is being changed, a waiting message is displayed. When quick search is being used,
 * the current quick search string is displayed.
 *
 * <p>StatusBar receives LocationListener events when the folder has or is being changed, and automatically updates
 * selected files and volume info, and display the waiting message when the folder is changing. Quick search info
 * is set by FileTable.QuickSearch.
 *
 * <p>When StatusBar is visible, a Thread runs in the background to periodically update free/total space volume info.
 * This thread stops when the StatusBar is hidden.
 *
 * @author Maxence Bernard
 */
public class StatusBar extends JPanel implements MouseListener, TableSelectionListener, LocationListener, ComponentListener, ThemeListener {
	private static final long serialVersionUID = -5923401367766817893L;
	private static final Logger LOGGER = LoggerFactory.getLogger(StatusBar.class);

	private FolderPanel folderPanel;
	
	private boolean foregroundActive = true;
	private boolean noEventsMode = false;
	
    /** Label that displays info about current selected file(s) */
    private JLabel selectedFilesLabel;

    /** Icon used while loading is in progress. */
    private SpinningDial dial;
	
    /** Label that displays info about current volume (free/total space) */
    private VolumeSpaceLabel volumeSpaceLabel;
	
    /** Icon that is displayed when folder is changing */
    public final static String WAITING_ICON = "waiting.png";

    /** Listens to configuration changes and updates static fields accordingly */
    public final static ConfigurationListener CONFIGURATION_ADAPTER;

    /** SizeFormat format used to create the selected file(s) size string */
    private static int selectedFileSizeFormat;


    static {
        // Initialize the size column format based on the configuration
        setSelectedFileSizeFormat(MuConfigurations.getPreferences().getVariable(MuPreference.DISPLAY_COMPACT_FILE_SIZE,
                                                  MuPreferences.DEFAULT_DISPLAY_COMPACT_FILE_SIZE));

        // Listens to configuration changes and updates static fields accordingly.
        // Note: a reference to the listener must be kept to prevent it from being garbage-collected.
        CONFIGURATION_ADAPTER = new ConfigurationListener() {
            public synchronized void configurationChanged(ConfigurationEvent event) {
                String var = event.getVariable();

                if (var.equals(MuPreferences.DISPLAY_COMPACT_FILE_SIZE))
                    setSelectedFileSizeFormat(event.getBooleanValue());
            }
        };
        MuConfigurations.addPreferencesListener(CONFIGURATION_ADAPTER);
    }


    /**
     * Sets the SizeFormat format used to create the selected file(s) size string.
     *
     * @param compactSize true to use a compact size format, false for full size in bytes
     */
    private static void setSelectedFileSizeFormat(boolean compactSize) {
        if (compactSize) {
            selectedFileSizeFormat = SizeFormat.DIGITS_MEDIUM | SizeFormat.UNIT_SHORT | SizeFormat.ROUND_TO_KB;
        } else {
            selectedFileSizeFormat = SizeFormat.DIGITS_FULL | SizeFormat.UNIT_LONG;
        }
        selectedFileSizeFormat |= SizeFormat.INCLUDE_SPACE;
    }


    /**
     * Creates a new StatusBar instance.
     */
    public StatusBar(FolderPanel folderPanel) {
        this.folderPanel = folderPanel;
        
    	// Create and add status bar
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		
        selectedFilesLabel = new JLabel("");
        dial               = new SpinningDial();
        add(selectedFilesLabel);

        add(Box.createHorizontalGlue());

        JobsPopupButton jobsButton = new JobsPopupButton();
        jobsButton.setPopupMenuLocation(SwingConstants.TOP);

        add(jobsButton);
        add(Box.createRigidArea(new Dimension(2, 0)));

        volumeSpaceLabel = new VolumeSpaceLabel();
        add(volumeSpaceLabel);

        // Show/hide this status bar based on user preferences
        // Note: setVisible has to be called even with true for the auto-update thread to be initialized
        setVisible(MuConfigurations.getPreferences().getVariable(MuPreference.STATUS_BAR_VISIBLE, MuPreferences.DEFAULT_STATUS_BAR_VISIBLE));
        
        // Catch location events to update status bar info when folder is changed
        folderPanel.getLocationManager().addLocationListener(this);
        
        // Catch table selection change events to update the selected files info 
        // when the selected files have changed on one of the file tables
        folderPanel.getFileTable().addTableSelectionListener(this);
		
        // Catch mouse events to pop up a menu on right-click
        selectedFilesLabel.addMouseListener(this);
        volumeSpaceLabel.addMouseListener(this);
        addMouseListener(this);
		
        // Catch component events to be notified when this component is made visible
        // and update status info
        addComponentListener(this);

        // Initialize theme.
        selectedFilesLabel.setFont(ThemeManager.getCurrentFont(Theme.STATUS_BAR_FONT));
        selectedFilesLabel.setForeground(ThemeManager.getCurrentColor(Theme.STATUS_BAR_FOREGROUND_COLOR));
        volumeSpaceLabel.setFont(ThemeManager.getCurrentFont(Theme.STATUS_BAR_FONT));
        volumeSpaceLabel.setForeground(ThemeManager.getCurrentColor(Theme.STATUS_BAR_FOREGROUND_COLOR));
        ThemeManager.addCurrentThemeListener(this);
    }


    /**
     * Updates info displayed on the status bar: currently selected files and volume info.
     */
    public void updateStatusInfo() {
        // No need to waste precious cycles if status bar is not visible
        if(!isVisible()) {
            return;
        }
        updateSelectedFilesInfo();
        updateVolumeSpaceLabel();
    }
	
    private void updateVolumeSpaceLabel() {
    	volumeSpaceLabel.setCurrentFolder(folderPanel.getLocationManager().getCurrentFolder());
    }

    /**
     * Updates info about currently selected files ((nb of selected files, combined size), displayed on the left-side of this status bar.
     */
// Making this method synchronized creates a deadlock with FileTable
//    public synchronized void updateSelectedFilesInfo() {
    private void updateSelectedFilesInfo() {
        FileTable currentFileTable = folderPanel.getFileTable();

        // Currently select file, can be null
        AbstractFile selectedFile = currentFileTable.getSelectedFile(false, true);
        FileTableModel tableModel = currentFileTable.getFileTableModel();
        // Number of marked files, can be 0
        int nbMarkedFiles = tableModel.getNbMarkedFiles();
        // Combined size of marked files, 0 if no file has been marked
        long markedTotalSize = tableModel.getTotalMarkedSize();
        // number of files in folder
        int fileCount = tableModel.getFileCount();

        // Update files info based on marked files if there are some, or currently selected file otherwise
        int nbSelectedFiles;
        if (nbMarkedFiles == 0 && selectedFile != null) {
            nbSelectedFiles = 1;
        } else {
            nbSelectedFiles = nbMarkedFiles;
        }
        
        String filesInfo;
		
        if (fileCount==0) {
            // Set status bar to a space character, not an empty string
            // otherwise it will disappear
            filesInfo = " ";
        } else {
            filesInfo = Translator.get("status_bar.selected_files", ""+nbSelectedFiles, ""+fileCount);
			
            if (nbMarkedFiles > 0) {
                filesInfo += " - "+ SizeFormat.format(markedTotalSize, selectedFileSizeFormat);
            }
            if (selectedFile != null) {
                filesInfo += " - "+selectedFile.getName();
            }
        }		

        // Update label
        setStatusInfo(filesInfo);
    }

    /**
     * Displays the specified text and icon on the left-side of the status bar, 
     * replacing any previous information.
     *
     * @param text the piece of text to display
     * @param icon the icon to display next to the text
     * @param iconBeforeText if true, icon will be placed on the left side of the text, if not on the right side
     */
    public void setStatusInfo(String text, Icon icon, boolean iconBeforeText) {
        selectedFilesLabel.setText(text);

        if (icon == null) {
            // What we don't want here is the label's height to change depending on whether it has an icon or not.
            // This would result in having to revalidate the status bar and in turn the whole MainFrame.
            // A label's height is roughly the max of the text's font height and the icon (if any). So if there is no
            // icon for the label, we use a transparent image for padding in case the text's font height is smaller
            // than a 'standard' (16x16) icon. This ensures that the label's height remains constant.
            BufferedImage bi = new BufferedImage(1, 16, BufferedImage.TYPE_INT_ARGB);
            icon = new ImageIcon(bi);
        }
        selectedFilesLabel.setIcon(icon);
        selectedFilesLabel.setHorizontalTextPosition(iconBeforeText?JLabel.TRAILING:JLabel.LEADING);
    }

	
    /**
     * Displays the specified text on the left-side of the status bar, 
     * replacing any previous text and icon.
     *
     * @param infoMessage the piece of text to display
     */
    public void setStatusInfo(String infoMessage) {
        setStatusInfo(infoMessage, null, false);
    }
    
    /**
     * Overrides JComponent.setVisible(boolean) to start/stop volume info auto-update thread.
     */
    @Override
    public void setVisible(boolean visible) {
    	super.setVisible(visible);
    	updateStatusInfo();
    }
    
    ////////////////////////////////////////
    // ActivePanelListener implementation //
    ////////////////////////////////////////
	
    public void activePanelChanged(FolderPanel folderPanel) {
        updateStatusInfo();
    }


    ///////////////////////////////////////////
    // TableSelectionListener implementation //
    ///////////////////////////////////////////

    public void selectedFileChanged(FileTable source) {
        // No need to update if the originating FileTable is not the currently active one
        if (foregroundActive) {
            updateStatusInfo();
        }
    }

    public void markedFilesChanged(FileTable source) {
        // No need to update if the originating FileTable is not the currently active one
        if (foregroundActive) {
        	updateStatusInfo();
        }
    }


    /////////////////////////////////////
    // LocationListener implementation //
    /////////////////////////////////////

    public void locationChanged(LocationEvent e) {
        dial.setAnimated(false);
        updateStatusInfo();
    }

    public void locationChanging(LocationEvent e) {
        // Show a message in the status bar saying that folder is being changed
        setStatusInfo(Translator.get("status_bar.connecting_to_folder"), dial, true);
        dial.setAnimated(true);
    }
	
    public void locationCancelled(LocationEvent e) {
        dial.setAnimated(false);
        updateStatusInfo();
    }

    public void locationFailed(LocationEvent e) {
        dial.setAnimated(false);
        updateStatusInfo();
    }


    //////////////////////////////////
    // MouseListener implementation //
    //////////////////////////////////
	
    public void mouseClicked(MouseEvent e) {
        // Discard mouse events while in 'no events mode'
        if(noEventsMode) {
            return;
        }
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
    }
	
    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }	
	
	
    //////////////////////////////////////
    // ComponentListener implementation //
    //////////////////////////////////////
	
    public void componentShown(ComponentEvent e) {
        // Invoked when the component has been made visible (apparently not called when just created)
        // Status bar needs to be updated since it is not updated when not visible
        updateStatusInfo();
    }     

    public void componentHidden(ComponentEvent e) {
    }

    public void componentMoved(ComponentEvent e) {
    }

    public void componentResized(ComponentEvent e) {
    	Dimension maximumSize = new Dimension(this.getWidth() - volumeSpaceLabel.getWidth() - 5, selectedFilesLabel.getHeight());
    	selectedFilesLabel.setPreferredSize(maximumSize);
    	selectedFilesLabel.setMaximumSize(maximumSize);
    	revalidate();
    }


    public void fontChanged(FontChangedEvent event) {
        if(event.getFontId() == Theme.STATUS_BAR_FONT) {
            selectedFilesLabel.setFont(event.getFont());
            volumeSpaceLabel.setFont(event.getFont());
            repaint();
        }
    }

    public void colorChanged(ColorChangedEvent event) {
        if(event.getColorId() == Theme.STATUS_BAR_FOREGROUND_COLOR) {
            selectedFilesLabel.setForeground(event.getColor());
            volumeSpaceLabel.setForeground(event.getColor());
            repaint();
        }
    }
}
