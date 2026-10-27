package com.vocagate.desktop.app;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;

public final class DesktopRoundedPanel extends JPanel {
    private final int radius;
    private final Color borderColor;

    public DesktopRoundedPanel(int radius, Color background, Color borderColor) {
        this.radius = radius;
        this.borderColor = borderColor;
        setOpaque(false);
        setBackground(background);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getBackground());
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        if (borderColor != null) {
            g.setColor(borderColor);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
