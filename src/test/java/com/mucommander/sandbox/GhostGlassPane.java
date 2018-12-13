package com.mucommander.sandbox;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

class GhostGlassPane extends JPanel {
    public static final long serialVersionUID = 1L;
    private final AlphaComposite m_composite;

    private Point m_location = new Point(0, 0);

    private BufferedImage m_draggingGhost = null;

    public GhostGlassPane() {
        setOpaque(false);
        m_composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f);
    }

    public void setImage(BufferedImage draggingGhost) {
        m_draggingGhost = draggingGhost;
    }

    public void setPoint(Point a_location) {
        m_location.x = a_location.x;
        m_location.y = a_location.y;
    }

    public int getGhostWidth() {
        if (m_draggingGhost == null) {
            return 0;
        } // if

        return m_draggingGhost.getWidth(this);
    }

    public int getGhostHeight() {
        if (m_draggingGhost == null) {
            return 0;
        } // if

        return m_draggingGhost.getHeight(this);
    }

    public void paintComponent(Graphics g) {
        if (m_draggingGhost == null) {
            return;
        } // if 

        Graphics2D g2 = (Graphics2D) g;
        g2.setComposite(m_composite);

        g2.drawImage(m_draggingGhost, (int) m_location.getX(), (int) m_location.getY(), null);
    }
}
