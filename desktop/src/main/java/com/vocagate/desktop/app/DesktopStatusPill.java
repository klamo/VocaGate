package com.vocagate.desktop.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JLabel;
import javax.swing.SwingConstants;

public final class DesktopStatusPill extends JLabel {
    private static final int RADIUS = 10;
    private static final Dimension FIXED_SIZE = new Dimension(150, 32);

    public DesktopStatusPill(String text) {
        super(text, SwingConstants.CENTER);
        setOpaque(false);
        setForeground(Color.WHITE);
        setBackground(new Color(88, 91, 96));
        setFont(getFont().deriveFont(Font.BOLD, 13f));
        setPreferredSize(FIXED_SIZE);
        setMinimumSize(FIXED_SIZE);
        setMaximumSize(FIXED_SIZE);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getBackground());
        g.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS, RADIUS);
        g.dispose();
        super.paintComponent(graphics);
    }
}
