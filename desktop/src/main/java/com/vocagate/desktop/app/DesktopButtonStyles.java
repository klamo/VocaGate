package com.vocagate.desktop.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;

public final class DesktopButtonStyles {
    private static final Color TEXT_PRIMARY = new Color(32, 36, 42);
    private static final Color CONTROL_BORDER = new Color(210, 215, 222);
    private static final Color GREEN = new Color(52, 151, 78);
    private static final Color GREEN_DARK = new Color(38, 128, 66);

    private DesktopButtonStyles() {
    }

    public static void applyPrimary(JButton button) {
        prepareFlatButton(button);
        button.setPreferredSize(new Dimension(0, 44));
        button.setForeground(Color.WHITE);
        button.setBackground(GREEN);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 16f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GREEN_DARK),
                BorderFactory.createEmptyBorder(9, 18, 9, 18)
        ));
    }

    public static void applySecondary(JButton button) {
        prepareFlatButton(button);
        button.setPreferredSize(new Dimension(0, 38));
        button.setForeground(TEXT_PRIMARY);
        button.setBackground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 14f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CONTROL_BORDER),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)
        ));
    }

    private static void prepareFlatButton(JButton button) {
        button.setUI(new BasicButtonUI());
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
    }
}
