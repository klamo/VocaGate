package com.vocagate.demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.vocagate.demo.core.OverlayCoordinateMapper;
import com.vocagate.demo.core.Point2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MouthOverlayView extends View {
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Point2> mouthPoints = Collections.emptyList();
    private int imageWidth = 1;
    private int imageHeight = 1;
    private boolean mirror = true;

    public MouthOverlayView(Context context) {
        super(context);
        init();
    }

    public MouthOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setMouthPoints(List<Point2> points, int imageWidth, int imageHeight, boolean mirror) {
        this.mouthPoints = points == null ? Collections.emptyList() : new ArrayList<>(points);
        this.imageWidth = Math.max(1, imageWidth);
        this.imageHeight = Math.max(1, imageHeight);
        this.mirror = mirror;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mouthPoints.isEmpty()) {
            return;
        }

        Point2 previous = null;
        for (Point2 point : mouthPoints) {
            Point2 mapped = mapPoint(point);
            float x = mapped.x;
            float y = mapped.y;
            canvas.drawCircle(x, y, 5.0f, pointPaint);
            if (previous != null) {
                Point2 previousMapped = mapPoint(previous);
                canvas.drawLine(previousMapped.x, previousMapped.y, x, y, linePaint);
            }
            previous = point;
        }
    }

    private void init() {
        setWillNotDraw(false);
        pointPaint.setColor(Color.rgb(47, 255, 173));
        pointPaint.setStyle(Paint.Style.FILL);
        linePaint.setColor(Color.rgb(47, 255, 173));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3.0f);
    }

    private Point2 mapPoint(Point2 point) {
        return OverlayCoordinateMapper.mapFillCenter(
                point.x,
                point.y,
                imageWidth,
                imageHeight,
                getWidth(),
                getHeight(),
                mirror
        );
    }
}
