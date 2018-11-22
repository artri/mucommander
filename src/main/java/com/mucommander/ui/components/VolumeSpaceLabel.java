package com.mucommander.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mucommander.cache.FastLRUCache;
import com.mucommander.cache.LRUCache;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.text.SizeFormat;
import com.mucommander.text.Translator;
import com.mucommander.ui.border.MutableLineBorder;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.ui.theme.ColorChangedEvent;
import com.mucommander.ui.theme.FontChangedEvent;
import com.mucommander.ui.theme.Theme;
import com.mucommander.ui.theme.ThemeListener;
import com.mucommander.ui.theme.ThemeManager;
import com.mucommander.utils.MuExecutorManager;

/**
 * VolumeSpaceLabel class.
 * 
 * This label displays the amount of free and/or total space on a volume.
 */
public class VolumeSpaceLabel extends JLabel implements ThemeListener {
	private static final long serialVersionUID = -4035825813961455050L;
	private static final Logger LOGGER = LoggerFactory.getLogger(VolumeSpaceLabel.class);
	
    /** SizeFormat's format used to display volume info in status bar */
    private static final int VOLUME_INFO_SIZE_FORMAT = SizeFormat.DIGITS_MEDIUM | SizeFormat.UNIT_SHORT | SizeFormat.INCLUDE_SPACE | SizeFormat.ROUND_TO_KB;

    /** Number of volume info strings that can be temporarily cached */
    private static final int VOLUME_INFO_CACHE_CAPACITY = 50;

    /** Number of milliseconds before cached volume info strings expire */
    private static final int VOLUME_INFO_TIME_TO_LIVE = 60000;

    /** Number of milliseconds between each volume info update by auto-update thread */
    private static final int AUTO_UPDATE_PERIOD = 6000;
    
    /** 
     * Caches volume info strings (free/total space) for a while, 
     * since this information is expensive to retrieve
     * (I/O bound). This map uses folders' volume path as its key.
     */
    private static final LRUCache<String, Long[]> volumeInfoCache = new FastLRUCache<String, Long[]>(VOLUME_INFO_CACHE_CAPACITY);    
    
    private ScheduledFuture<?> autoUpdateFuture;
    
    private volatile long freeSpace;
    private volatile long totalSpace;

    private final MainFrame mainFrame;    
    private Color backgroundColor;
    private Color okColor;
    private Color warningColor;
    private Color criticalColor;

    private final static float SPACE_WARNING_THRESHOLD = 0.1f;
    private final static float SPACE_CRITICAL_THRESHOLD = 0.05f;

    public VolumeSpaceLabel(MainFrame mainFrame) {
        super("");
        this.mainFrame = mainFrame;

        setHorizontalAlignment(CENTER);
        backgroundColor = ThemeManager.getCurrentColor(Theme.STATUS_BAR_BACKGROUND_COLOR);
        //            borderColor     = ThemeManager.getCurrentColor(Theme.STATUS_BAR_BORDER_COLOR);
        okColor         = ThemeManager.getCurrentColor(Theme.STATUS_BAR_OK_COLOR);
        warningColor    = ThemeManager.getCurrentColor(Theme.STATUS_BAR_WARNING_COLOR);
        criticalColor   = ThemeManager.getCurrentColor(Theme.STATUS_BAR_CRITICAL_COLOR);
        setBorder(new MutableLineBorder(ThemeManager.getCurrentColor(Theme.STATUS_BAR_BORDER_COLOR)));
        ThemeManager.addCurrentThemeListener(this);
        triggerAutoUpdate(true);
    }

    @Override
	public void setVisible(boolean visible) {
    	super.setVisible(visible);
    	triggerAutoUpdate(visible);
	}

    private void triggerAutoUpdate(boolean trigger) {
    	if (trigger)  {
    		if (null == autoUpdateFuture)  {
    			autoUpdateFuture = MuExecutorManager.scheduleWithFixedDelay(new VolumeSpaceLabelUpdateCommand(this.mainFrame), 5, AUTO_UPDATE_PERIOD, TimeUnit.MILLISECONDS);
    		}    		
    	} else {
    		if (null != autoUpdateFuture)  {
	    		autoUpdateFuture.cancel(true);
	    		autoUpdateFuture = null;
    		}    		
    	}
    }
    
	/**
     * Sets the new volume total and free space, and updates the label's text to show the new values and,
     * only if both total and free space are available (different from -1), paint a graphical representation
     * of the amount of free space available and set a tooltip showing the percentage of free space on the volume.
     *
     * @param totalSpace total volume space, -1 if not available
     * @param freeSpace free volume space, -1 if not available
     */
    private void updateVolumeSpace(long totalSpace, long freeSpace) {
        this.freeSpace = freeSpace;
        this.totalSpace = totalSpace;
        
    	SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setText(buildVolumeInfoText());
				setToolTipText(buildToolTipText());
			}    		
    	});
    }

    private String buildVolumeInfoText() {
        String volumeInfo = "";
        if (freeSpace != -1) {
            volumeInfo = SizeFormat.format(freeSpace, VOLUME_INFO_SIZE_FORMAT);
            if ( totalSpace != -1) {
                volumeInfo += " / "+ SizeFormat.format(totalSpace, VOLUME_INFO_SIZE_FORMAT);
            }
            volumeInfo = Translator.get("status_bar.volume_free", volumeInfo);
        } else if(totalSpace != -1) {
            volumeInfo = SizeFormat.format(totalSpace, VOLUME_INFO_SIZE_FORMAT);
            volumeInfo = Translator.get("status_bar.volume_capacity", volumeInfo);
        }
        
        return volumeInfo;
    }
    
    private String buildToolTipText() {
        if (freeSpace == -1 || totalSpace == -1) {
            // Removes any previous tooltip
        	return null;
        }
    	return "" + (int)(100 * freeSpace/(float) totalSpace) + "%";
    }
    
    /**
     * Adds some empty space around the label.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        //TODO: remove magic numbers
        return new Dimension(d.width + 4, d.height + 2);
    }

    /**
     * Returns an interpolated color value, located at percent between c1 and c2 in the RGB space.
     *
     * @param c1 first color
     * @param c2 end color
     * @param percent distance between c1 and c2, comprised between 0 and 1.
     * @return an interpolated color value, located at percent between c1 and c2 in the RGB space.
     */
    private Color interpolateColor(Color c1, Color c2, float percent) {
        return new Color(
                (int)(c1.getRed()+(c2.getRed()-c1.getRed())*percent),
                (int)(c1.getGreen()+(c2.getGreen()-c1.getGreen())*percent),
                (int)(c1.getBlue()+(c2.getBlue()-c1.getBlue())*percent)
        );
    }

    @Override
    public void paint(Graphics g) {

        // If free or total space is not available, this label will just be painted as a normal JLabel
        if (freeSpace!=-1 && totalSpace!=-1) {
            int width = getWidth();
            int height = getHeight();

            // Paint amount of free volume space if both free and total space are available
            float freeSpacePercentage = freeSpace/(float)totalSpace;

            Color c;
            if (freeSpacePercentage <= SPACE_CRITICAL_THRESHOLD) {
                c = criticalColor;
            } else if(freeSpacePercentage<=SPACE_WARNING_THRESHOLD) {
                c = interpolateColor(warningColor, criticalColor, (SPACE_WARNING_THRESHOLD-freeSpacePercentage)/SPACE_WARNING_THRESHOLD);
            } else {
                c = interpolateColor(okColor, warningColor, (1-freeSpacePercentage)/(1-SPACE_WARNING_THRESHOLD));
            }

            g.setColor(c);

            int freeSpaceWidth = Math.max(Math.round(freeSpacePercentage*(float)(width-2)), 1);
            g.fillRect(1, 1, freeSpaceWidth + 1, height - 2);

            // Fill background
            g.setColor(backgroundColor);
            g.fillRect(freeSpaceWidth + 1, 1, width - freeSpaceWidth - 1, height - 2);
        }

        super.paint(g);
    }

    public void fontChanged(FontChangedEvent event) {}

    public void colorChanged(ColorChangedEvent event) {
        switch(event.getColorId()) {
        case Theme.STATUS_BAR_BACKGROUND_COLOR:
            backgroundColor = event.getColor();
            break;
        case Theme.STATUS_BAR_BORDER_COLOR:
            // Some (rather evil) look and feels will change borders outside of muCommander's control,
            // this check is necessary to ensure no exception is thrown.
            if(getBorder() instanceof MutableLineBorder)
                ((MutableLineBorder)getBorder()).setLineColor(event.getColor());
            break;
        case Theme.STATUS_BAR_OK_COLOR:
            okColor = event.getColor();
            break;
        case Theme.STATUS_BAR_WARNING_COLOR:
            warningColor = event.getColor();
            break;
        case Theme.STATUS_BAR_CRITICAL_COLOR:
            criticalColor = event.getColor();
            break;
        default:
            return;
        }
        repaint();
    }
    
    /**
     * Updates info about current volume (free space, total space), displayed on the right-side of this status bar.
     */    
    private class VolumeSpaceLabelUpdateCommand implements Runnable {
    	private final MainFrame mainFrame;
    	
		public VolumeSpaceLabelUpdateCommand(MainFrame mainFrame) {
			this.mainFrame = mainFrame;
		}

		@Override
		public void run() {
            // No need to waste precious cycles if status bar is not visible
            if (!VolumeSpaceLabel.this.isVisible()) {
                return;
            }
            
            final AbstractFile currentFolder = mainFrame.getActivePanel().getCurrentFolder();
            // Resolve the current folder's volume and use its path as a key for the volume info cache
            final String volumePath = currentFolder.exists() ? currentFolder.getVolume().getAbsolutePath(true) : "";

            Long cachedVolumeInfo[] = volumeInfoCache.get(volumePath);
            if (cachedVolumeInfo != null) {
                LOGGER.debug("Cache hit!");
                VolumeSpaceLabel.this.updateVolumeSpace(cachedVolumeInfo[0], cachedVolumeInfo[1]);
            } else {
                // Retrieves free and total volume space.
                // Perform volume info retrieval in a separate thread as this method may be called
                // by the event thread and it can take a while, we want to return as soon as possible
            	
                // Free space on current volume, -1 if this information is not available 
                long volumeFree;
                // Total space on current volume, -1 if this information is not available 
                long volumeTotal;

                try { volumeFree = currentFolder.getFreeSpace(); }
                catch(IOException e) { volumeFree = -1; }

                try { volumeTotal = currentFolder.getTotalSpace(); }
                catch(IOException e) { volumeTotal = -1; }

    			// For testing the free space indicator 
    			//volumeFree = (long)(volumeTotal * Math.random());
                VolumeSpaceLabel.this.updateVolumeSpace(volumeTotal, volumeFree);

                LOGGER.debug("Adding to cache");
                volumeInfoCache.add(volumePath, new Long[]{volumeTotal, volumeFree}, VOLUME_INFO_TIME_TO_LIVE);        	
            }
			
		}    	
    }    
}
