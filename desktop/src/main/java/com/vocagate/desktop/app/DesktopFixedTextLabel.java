package com.vocagate.desktop.app;

import java.awt.Dimension;

import javax.swing.JLabel;

public final class DesktopFixedTextLabel extends JLabel {
    private final Dimension fixedSize;
    private final int maxVisibleChars;

    public DesktopFixedTextLabel(String text, Dimension fixedSize) {
        this.fixedSize = new Dimension(fixedSize);
        this.maxVisibleChars = Math.max(8, fixedSize.width / 9);
        setPreferredSize(this.fixedSize);
        setMinimumSize(this.fixedSize);
        setMaximumSize(this.fixedSize);
        setText(text);
    }

    @Override
    public void setText(String text) {
        String value = text == null ? "" : text;
        setToolTipText(value);
        super.setText(ellipsize(value));
    }

    private String ellipsize(String value) {
        if (value.length() <= maxVisibleChars) {
            return value;
        }
        return value.substring(0, Math.max(1, maxVisibleChars - 3)) + "...";
    }
}
