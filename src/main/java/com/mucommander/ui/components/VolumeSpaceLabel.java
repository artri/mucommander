package com.mucommander.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.io.IOException;
import java.util.Objects;
import java.util.WeakHashMap;
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
public class VolumeSpaceLabel extends JLabel implements Runnable, ThemeListener {
	private static final long serialVersionUID = -4035825813961455050L;
	private static final Logger LOGGER = LoggerFactory.getLogger(VolumeSpaceLabel.class);
	private static final long UNDEFINED_SPACE_VALUE = -1L;
	private static final VolumeSpaceInfo EMPTY_VOLUME_SPACE_INFO = new VolumeSpaceInfo("", UNDEFINED_SPACE_VALUE, UNDEFINED_SPACE_VALUE);
    /** SizeFormat's format used to display volume info in status bar */
    private static final int VOLUME_INFO_SIZE_FORMAT = SizeFormat.DIGITS_MEDIUM | SizeFormat.UNIT_SHORT | SizeFormat.INCLUDE_SPACE | SizeFormat.ROUND_TO_KB;

    /** Number of volume info strings that can be temporarily cached */
    private static final int VOLUME_INFO_CACHE_CAPACITY = 50;
    
    /** 
     * Caches volume info strings (free/total space) for a while, 
     * since this information is expensive to retrieve
     * (I/O bound). This map uses folders' volume path as its key.
     */
    private static final LRUCache<String, VolumeSpaceInfo> VOLUME_INFO_CACHE = new FastLRUCache<>(VOLUME_INFO_CACHE_CAPACITY);    
    
    /** Number of milliseconds before cached volume info strings expire */
    private static final int VOLUME_INFO_TIME_TO_LIVE = 60000;	
    
    /** Number of milliseconds between each volume info update by auto-update thread */
    private static final int AUTO_UPDATE_PERIOD = 6000;
    private static final VolumeSpaceLabelUpdateTask SPACE_LABEL_UPDATE_TASK;
    
    private AbstractFile currentFolder;
    private VolumeSpaceInfo volumeSpaceInfo = EMPTY_VOLUME_SPACE_INFO;
   
    private Color backgroundColor;
    private Color okColor;
    private Color warningColor;
    private Color criticalColor;

    private final static float SPACE_WARNING_THRESHOLD = 0.1f;
    private final static float SPACE_CRITICAL_THRESHOLD = 0.05f;
    
    static {
    	SPACE_LABEL_UPDATE_TASK = new VolumeSpaceLabelUpdateTask();
    }
    
    public VolumeSpaceLabel() {
        super("");

        setHorizontalAlignment(CENTER);
        backgroundColor = ThemeManager.getCurrentColor(Theme.STATUS_BAR_BACKGROUND_COLOR);
        //            borderColor     = ThemeManager.getCurrentColor(Theme.STATUS_BAR_BORDER_COLOR);
        okColor         = ThemeManager.getCurrentColor(Theme.STATUS_BAR_OK_COLOR);
        warningColor    = ThemeManager.getCurrentColor(Theme.STATUS_BAR_WARNING_COLOR);
        criticalColor   = ThemeManager.getCurrentColor(Theme.STATUS_BAR_CRITICAL_COLOR);
        setBorder(new MutableLineBorder(ThemeManager.getCurrentColor(Theme.STATUS_BAR_BORDER_COLOR)));
        ThemeManager.addCurrentThemeListener(this);
        SPACE_LABEL_UPDATE_TASK.registerVolumeSpaceLabel(this);
    }

	/**
     * Sets the new volume total and free space, and updates the label's text to show the new values and,
     * only if both total and free space are available (different from -1), paint a graphical representation
     * of the amount of free space available and set a tooltip showing the percentage of free space on the volume.
     *
     * @param totalSpace total volume space, -1 if not available
     * @param freeSpace free volume space, -1 if not available
     */
    private synchronized void updateVolumeSpace(VolumeSpaceInfo volumeSpaceInfo) {
        this.volumeSpaceInfo = volumeSpaceInfo;
        
    	SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				updateToolTipText();
				updateText();
			}    		
    	});
    }

    public synchronized void updateToolTipText() {
        if (null == this.volumeSpaceInfo || !volumeSpaceInfo.isValid()) {
            // Removes any previous tooltip
        	setToolTipText(null);
        }
        setToolTipText("" + (int)(100 * volumeSpaceInfo.getVolumeFree()/(float) volumeSpaceInfo.getVolumeTotal()) + "%");    	
    }
    
	private synchronized void updateText() {
		if (null == this.volumeSpaceInfo || !volumeSpaceInfo.isValid()) {
    		setText("");
    	}
    	
        String volumeInfoText = "";
        if (volumeSpaceInfo.isValidVolumeFree()) {
            volumeInfoText = SizeFormat.format(volumeSpaceInfo.getVolumeFree(), VOLUME_INFO_SIZE_FORMAT);
            if (volumeSpaceInfo.isValidVolumeTotal()) {
                volumeInfoText += " / "+ SizeFormat.format(volumeSpaceInfo.getVolumeTotal(), VOLUME_INFO_SIZE_FORMAT);
            }
            volumeInfoText = Translator.get("status_bar.volume_free", volumeInfoText);
        } else if(volumeSpaceInfo.isValidVolumeTotal()) {
            volumeInfoText = SizeFormat.format(volumeSpaceInfo.getVolumeTotal(), VOLUME_INFO_SIZE_FORMAT);
            volumeInfoText = Translator.get("status_bar.volume_capacity", volumeInfoText);
        }
        
        setText(volumeInfoText);
    }
    
    /**
     * Adds some empty space around the label.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
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
        if (volumeSpaceInfo.isValidVolumeFree() && volumeSpaceInfo.isValidVolumeTotal()) {
            int width = getWidth();
            int height = getHeight();

            // Paint amount of free volume space if both free and total space are available
            float freeSpacePercentage = volumeSpaceInfo.getVolumeFree()/(float) volumeSpaceInfo.getVolumeTotal();

            Color c;
            if (freeSpacePercentage <= SPACE_CRITICAL_THRESHOLD) {
                c = criticalColor;
            } else if(freeSpacePercentage <= SPACE_WARNING_THRESHOLD) {
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
            if(getBorder() instanceof MutableLineBorder) {
                ((MutableLineBorder)getBorder()).setLineColor(event.getColor());
            }
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

    public synchronized AbstractFile getCurrentFolder() {
		return currentFolder;
	}

	public synchronized void setCurrentFolder(AbstractFile currentFolder) {
		LOGGER.debug("currentFolder changed {} -> {}", this.currentFolder, currentFolder);
		this.currentFolder = currentFolder;
		MuExecutorManager.execute(this);
	}
    
	private synchronized void updateVolumeInfo() {
		AbstractFile currentFolder = null;
		String volumePath;
		
        currentFolder = getCurrentFolder();
        if (null == currentFolder) {
        	LOGGER.debug("No folder set for VolumeSpaceLabel: break");
        	return;
        }
        
        LOGGER.debug("Updating volume info for: {} - {}", currentFolder.getClass(), currentFolder);
        
        synchronized (currentFolder) {
            // Resolve the current folder's volume and use its path as a key for the volume info cache
            volumePath = currentFolder.exists() ? currentFolder.getAbsolutePath(true) : "";

            VolumeSpaceInfo volumeSpaceInfo = VOLUME_INFO_CACHE.get(volumePath);
            if (null != volumeSpaceInfo && volumeSpaceInfo.isValid()) {
            	LOGGER.debug("VolumeSpaceInfo cache hit: {}", volumeSpaceInfo);
                updateVolumeSpace(volumeSpaceInfo);
            } else {
                // Retrieves free and total volume space.
                // Perform volume info retrieval in a separate thread as this method may be called
                // by the event thread and it can take a while, we want to return as soon as possible
            	
                // Free space on current volume, -1 if this information is not available 
                long volumeFree;
                // Total space on current volume, -1 if this information is not available 
                long volumeTotal;

                try { volumeFree = currentFolder.getFreeSpace(); }
                catch(IOException e) { volumeFree = UNDEFINED_SPACE_VALUE; }

                try { volumeTotal = currentFolder.getTotalSpace(); }
                catch(IOException e) { volumeTotal = UNDEFINED_SPACE_VALUE; }
                
                volumeSpaceInfo = new VolumeSpaceInfo(volumePath, volumeFree, volumeTotal);
    			// For testing the free space indicator 
    			//volumeFree = (long)(volumeTotal * Math.random());
                updateVolumeSpace(volumeSpaceInfo);

                if (volumeSpaceInfo.isValid()) {
                    LOGGER.debug("Adding VolumeSpaceInfo to cache: {}", volumeSpaceInfo);
                    VOLUME_INFO_CACHE.add(volumePath, volumeSpaceInfo, VOLUME_INFO_TIME_TO_LIVE);
                }
            }
        } // synchronized (currentFolder)		
	}
	
	@Override
	public void run() {
		updateVolumeInfo();
	}
	
    /**
     * Updates info about current volume (free space, total space), displayed on the right-side of this status bar.
     */    
    private static class VolumeSpaceLabelUpdateTask implements Runnable {
    	private ScheduledFuture<?> autoUpdateFeature;
    	private WeakHashMap<VolumeSpaceLabel, Object> volumeSpaceLabelMap = new WeakHashMap<>();
    	
    	public VolumeSpaceLabelUpdateTask() {
    		this.autoUpdateFeature = MuExecutorManager.scheduleWithFixedDelay(this, 5, AUTO_UPDATE_PERIOD, TimeUnit.MILLISECONDS);
		}
    	
    	public void registerVolumeSpaceLabel(VolumeSpaceLabel volumeSpaceLabel) {
    		Objects.requireNonNull(volumeSpaceLabel);
    		synchronized (volumeSpaceLabelMap) {
    			volumeSpaceLabelMap.put(volumeSpaceLabel, null);
    		}
    	}
    	
    	public void unregisterVolumeSpaceLabel(VolumeSpaceLabel volumeSpaceLabel) {
    		if (null == volumeSpaceLabel) {
    			return;
    		}
    		synchronized (volumeSpaceLabelMap) {
    			volumeSpaceLabelMap.remove(volumeSpaceLabel);
    		}
    	}
    	
		@Override
		public void run() {
			VolumeSpaceLabel[] volumeSpaceLabels;
			synchronized (volumeSpaceLabelMap) {
				volumeSpaceLabels = volumeSpaceLabelMap.keySet().toArray(new VolumeSpaceLabel[] {});
			}
			
			LOGGER.debug("Updating {} labels", volumeSpaceLabels.length);
			for (VolumeSpaceLabel volumeSpaceLabel : volumeSpaceLabels) {
				volumeSpaceLabel.run();
			}
		}
    }
    
    private static class VolumeSpaceInfo {
    	private final String volumePath;
    	private final long volumeFree;
    	private final long volumeTotal;
    	
    	public VolumeSpaceInfo(String volumePath, long volumeFree, long volumeTotal) {
			this.volumePath = volumePath;
			this.volumeFree = volumeFree;
			this.volumeTotal = volumeTotal;
		}

		public String getVolumePath() {
			return volumePath;
		}

		public long getVolumeFree() {
			return volumeFree;
		}

		public long getVolumeTotal() {
			return volumeTotal;
		}

		public boolean isValidVolumeTotal() {
			return volumeTotal != UNDEFINED_SPACE_VALUE;	
		}
		public boolean isValidVolumeFree() {
			return volumeFree != UNDEFINED_SPACE_VALUE;	
		}
		
		public boolean isValid() {
    		return volumePath != null 
    				&& volumeFree != UNDEFINED_SPACE_VALUE
    				&& volumeTotal != UNDEFINED_SPACE_VALUE;
    	}
    	
		@Override
		public int hashCode() {
			return Objects.hash(volumePath, volumeFree, volumeTotal);
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			
			if (!(obj instanceof VolumeSpaceInfo)) {
				return false;
			}
			VolumeSpaceInfo another = (VolumeSpaceInfo) obj;
			return Objects.equals(volumePath, another.volumePath)
					&& Objects.equals(volumeFree, another.volumeFree)
					&& Objects.equals(volumeTotal, another.volumeTotal);
		}

		@Override
		public String toString() {
			return "VolumeSpaceInfo [volumePath=" + volumePath + ", volumeFree=" + volumeFree + ", volumeTotal=" + volumeTotal + "]";
		}
    }
}
