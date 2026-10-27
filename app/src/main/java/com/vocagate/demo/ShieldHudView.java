package com.vocagate.demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import com.vocagate.demo.core.ShieldHudState;

public final class ShieldHudView extends View {
    private static final long COLOR_TRANSITION_MS = 360L;
    private static final long FRAME_DELAY_MS = 50L;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringRect = new RectF();
    private final RectF pillRect = new RectF();

    private ShieldHudState hudState = ShieldHudState.from(
            "Waiting",
            "PC: off",
            com.vocagate.core.GateState.CLOSED,
            false,
            0.0f
    );
    private int colorFrom = hudState.colorArgb;
    private int colorTo = hudState.colorArgb;
    private long colorTransitionStartedAtMs = 0L;

    public ShieldHudView(Context context) {
        super(context);
        setWillNotDraw(false);
        basePaint.setStyle(Paint.Style.STROKE);
        basePaint.setStrokeJoin(Paint.Join.ROUND);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
        pillPaint.setStyle(Paint.Style.FILL);
        pillStrokePaint.setStyle(Paint.Style.STROKE);
        textPaint.setColor(Color.argb(210, 245, 248, 250));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(sp(12.0f));
    }

    public void setHudState(ShieldHudState nextState) {
        if (nextState == null) {
            return;
        }
        long nowMs = SystemClock.uptimeMillis();
        int currentColor = currentColor(nowMs);
        if (nextState.colorArgb != colorTo) {
            colorFrom = currentColor;
            colorTo = nextState.colorArgb;
            colorTransitionStartedAtMs = nowMs;
        }
        hudState = nextState;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long nowMs = SystemClock.uptimeMillis();
        int color = currentColor(nowMs);

        canvas.drawColor(Color.BLACK);
        drawRing(canvas, color, nowMs);
        drawStatusPill(canvas, color);

        if (getVisibility() == VISIBLE && (hudState.pulse || isColorTransitioning(nowMs))) {
            postInvalidateDelayed(FRAME_DELAY_MS);
        }
    }

    private void drawRing(Canvas canvas, int color, long nowMs) {
        float breath = breathAmount(nowMs);
        float margin = dp(24.0f);
        float radius = dp(24.0f);
        ringRect.set(
                margin,
                margin,
                getWidth() - margin,
                getHeight() - margin
        );

        drawGlowLayer(canvas, color, radius, dp(30.0f + breath * 9.0f), 40 + Math.round(breath * 42.0f));
        drawGlowLayer(canvas, color, radius, dp(17.0f + breath * 5.0f), 76 + Math.round(breath * 62.0f));
        drawGlowLayer(canvas, color, radius, dp(8.0f + breath * 2.5f), 132 + Math.round(breath * 64.0f));

        basePaint.setStrokeWidth(dp(3.0f + breath * 1.0f));
        basePaint.setColor(withAlpha(color, 214 + Math.round(breath * 36.0f)));
        canvas.drawRoundRect(ringRect, radius, radius, basePaint);
    }

    private void drawStatusPill(Canvas canvas, int color) {
        String text = hudState.label + " · " + hudState.caption;
        float paddingX = dp(11.0f);
        float height = dp(30.0f);
        float width = Math.min(getWidth() - dp(32.0f), textPaint.measureText(text) + paddingX * 2.0f);
        float margin = dp(18.0f);
        float left;
        if (getWidth() > getHeight()) {
            left = getWidth() - margin - width;
        } else {
            left = (getWidth() - width) * 0.5f;
        }
        float top = getHeight() - margin - height;
        pillRect.set(left, top, left + width, top + height);

        pillPaint.setColor(withAlpha(color, 58));
        canvas.drawRoundRect(pillRect, height * 0.5f, height * 0.5f, pillPaint);

        pillStrokePaint.setStrokeWidth(dp(1.0f));
        pillStrokePaint.setColor(withAlpha(color, 118));
        canvas.drawRoundRect(pillRect, height * 0.5f, height * 0.5f, pillStrokePaint);

        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = pillRect.centerY() - (metrics.ascent + metrics.descent) * 0.5f;
        canvas.drawText(text, pillRect.left + paddingX, baseline, textPaint);
    }

    private void drawGlowLayer(Canvas canvas, int color, float radius, float strokeWidth, int alpha) {
        glowPaint.setStrokeWidth(strokeWidth);
        glowPaint.setColor(withAlpha(color, Math.max(0, Math.min(255, alpha))));
        canvas.drawRoundRect(ringRect, radius, radius, glowPaint);
    }

    private float breathAmount(long nowMs) {
        long periodMs;
        switch (hudState.kind) {
            case PRE_OPEN:
                periodMs = 1100L;
                break;
            case OPEN:
                periodMs = 1550L;
                break;
            case PC_LOST:
                periodMs = 1200L;
                break;
            case NO_FACE:
                periodMs = 1400L;
                break;
            case BLOCKED:
            default:
                periodMs = 2400L;
                break;
        }
        float wave = (float) ((Math.sin((nowMs % periodMs) / (double) periodMs * Math.PI * 2.0) + 1.0) * 0.5);
        return smoothStep(wave);
    }

    private int currentColor(long nowMs) {
        if (!isColorTransitioning(nowMs)) {
            return colorTo;
        }
        float t = (nowMs - colorTransitionStartedAtMs) / (float) COLOR_TRANSITION_MS;
        t = Math.max(0.0f, Math.min(1.0f, t));
        return blend(colorFrom, colorTo, smoothStep(t));
    }

    private boolean isColorTransitioning(long nowMs) {
        return nowMs - colorTransitionStartedAtMs < COLOR_TRANSITION_MS;
    }

    private static float smoothStep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static int blend(int from, int to, float t) {
        int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
