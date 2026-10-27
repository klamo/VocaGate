package com.vocagate.desktop.app;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

public final class KeyBindingDisplayField extends JLabel {
    private Runnable onRequestRebind;

    public KeyBindingDisplayField(String text) {
        super(text, SwingConstants.CENTER);
        setOpaque(true);
        setBackground(new Color(246, 247, 249));
        setForeground(new Color(32, 36, 42));
        setFont(getFont().deriveFont(Font.BOLD, 16f));
        setPreferredSize(new Dimension(92, 38));
        setMinimumSize(new Dimension(72, 34));
        setBorder(BorderFactory.createCompoundBorder(
                UIManager.getBorder("TextField.border"),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                requestRebind();
            }
        });
    }

    public void setOnRequestRebind(Runnable onRequestRebind) {
        this.onRequestRebind = onRequestRebind;
    }

    @Override
    public Insets getInsets() {
        Insets insets = super.getInsets();
        return new Insets(insets.top, insets.left, insets.bottom, insets.right);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        setBackground(enabled ? new Color(246, 247, 249) : new Color(238, 240, 243));
    }

    private void requestRebind() {
        if (!isEnabled() || onRequestRebind == null) {
            return;
        }
        onRequestRebind.run();
    }
}
