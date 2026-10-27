package com.vocagate.demo.core;

public final class ResponsiveControlLayout {
    private static final int LANDSCAPE_SIDE_PANEL_DP = 300;
    private static final int LANDSCAPE_MIN_PANEL_DP = 260;
    private static final float LANDSCAPE_MAX_WIDTH_FRACTION = 0.32f;

    private ResponsiveControlLayout() {
    }

    public static Spec forViewport(int widthPx, int heightPx, float density) {
        int safeWidth = Math.max(1, widthPx);
        int safeHeight = Math.max(1, heightPx);
        float safeDensity = Math.max(1.0f, density);
        boolean landscape = safeWidth > safeHeight;
        if (!landscape) {
            return new Spec(false, false, false, safeWidth);
        }

        int preferred = Math.round(LANDSCAPE_SIDE_PANEL_DP * safeDensity);
        int minimum = Math.round(LANDSCAPE_MIN_PANEL_DP * safeDensity);
        int maximum = Math.round(safeWidth * LANDSCAPE_MAX_WIDTH_FRACTION);
        int panelWidth = Math.max(Math.min(preferred, maximum), Math.min(minimum, safeWidth));
        return new Spec(true, true, true, panelWidth);
    }

    public static final class Spec {
        public final boolean landscape;
        public final boolean sidePanel;
        public final boolean compactStatus;
        public final int panelWidthPx;

        private Spec(boolean landscape, boolean sidePanel, boolean compactStatus, int panelWidthPx) {
            this.landscape = landscape;
            this.sidePanel = sidePanel;
            this.compactStatus = compactStatus;
            this.panelWidthPx = panelWidthPx;
        }
    }
}
